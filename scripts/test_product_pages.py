"""Dependency-free regression checks for the ordinary-user product website."""
import hashlib
import json
import re
import struct
import unittest
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"
RELEASES = "https://github.com/boolean93/openhoyi/releases"
ISSUES = "https://github.com/boolean93/openhoyi/issues"
REPO = "https://github.com/boolean93/openhoyi"
PAGES = "https://boolean93.github.io/openhoyi/"
APP_SOURCE_SHA = "7a22e6cbee30322c066ff76a19cde08e7e96cbf4"
SCREEN_REVISION = APP_SOURCE_SHA[:8]


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.ids, self.links, self.assets, self.tags = [], [], [], []
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        self.tags.append((tag, attrs))
        if "id" in attrs:
            self.ids.append(attrs["id"])
        if tag == "a":
            self.links.append(attrs.get("href", ""))
        if tag in ("img", "script", "source"):
            self.assets.append(attrs.get("src", "") or attrs.get("srcset", ""))
        if tag == "link" and attrs.get("rel") != "canonical":
            self.assets.append(attrs.get("href", ""))

    def metas(self, key, value):
        return [a.get("content", "") for tag, a in self.tags
                if tag == "meta" and a.get(key) == value]

    def has(self, tag, attr, value):
        return any(t == tag and a.get(attr) == value for t, a in self.tags)


class ProductPagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (SITE / "index.html").read_text()
        cls.page = Page(cls.html)
        cls.css = (SITE / "styles.css").read_text()
        cls.js = (SITE / "product.js").read_text()
        cls.provenance = json.loads((SITE / "assets/screens/provenance.json").read_text())

    def section(self, value):
        return self.html.split(f'id="{value}"', 1)[1].split("</section>", 1)[0]

    def test_metadata_and_accessibility(self):
        self.assertIn(("html", {"lang": "zh-CN"}), self.page.tags)
        self.assertEqual(sum(tag == "h1" for tag, _ in self.page.tags), 1)
        self.assertTrue(any(tag == "link" and a.get("rel") == "canonical"
                            and a.get("href") == PAGES for tag, a in self.page.tags))
        for name in ("viewport", "description"):
            self.assertTrue(any(tag == "meta" and a.get("name") == name
                                and a.get("content") for tag, a in self.page.tags))
        self.assertNotIn("user-scalable=no", self.html)
        self.assertTrue(any(tag == "a" and a.get("class") == "skip-link"
                            for tag, a in self.page.tags))
        for tag, attrs in self.page.tags:
            if tag == "img":
                self.assertIn("alt", attrs)
                self.assertTrue(attrs.get("width") and attrs.get("height"), attrs)
            if tag == "button":
                self.assertEqual(attrs.get("type"), "button")
        self.assertIn(":focus-visible", self.css)
        self.assertIn(".demo-chart-mobile .chart-tick { font-size: 14px; }", self.css)
        self.assertIn("prefers-reduced-motion", self.css)

    def test_metadata_social_cards(self):
        self.assertEqual(self.page.metas("property", "og:url"), [PAGES])
        image = self.page.metas("property", "og:image")
        self.assertTrue(image and all(url.startswith(PAGES) for url in image), image)
        self.assertEqual(self.page.metas("name", "twitter:card"), ["summary_large_image"])
        alt = self.page.metas("property", "og:image:alt")
        self.assertTrue(alt and "模拟数据" in alt[0], alt)

    def test_internal_links_and_assets(self):
        self.assertEqual(len(self.page.ids), len(set(self.page.ids)))
        for href in self.page.links + self.page.assets:
            with self.subTest(href=href):
                self.assertTrue(href)
                url = urlsplit(href)
                if url.scheme or url.netloc:
                    self.assertEqual(url.scheme, "https")
                    self.assertEqual(url.netloc, "github.com")
                elif url.path:
                    asset = (SITE / unquote(url.path)).resolve()
                    self.assertTrue(asset.is_relative_to(SITE.resolve()))
                    self.assertTrue(asset.is_file())
                elif url.fragment:
                    self.assertIn(unquote(url.fragment), self.page.ids)

    def test_hero_and_four_core_features(self):
        hero = self.section("top")
        self.assertIn('class="hero-figure"', hero)
        self.assertIn("data-download-entry", hero)
        h1 = re.search(r"<h1[^>]*>(.*?)</h1>", self.html, re.S).group(1)
        self.assertIn("连上 HOYI", h1)
        self.assertIn("看清这一杯", h1)
        for text in ("独立开源 · Android · 非官方 Alpha 项目",
                     "记住常用设备，分享参数配方，把两杯萃取放在一起对照",
                     "下载 Android 版", "查看兼容情况",
                     "需要 Android 8.0 及以上和低功耗蓝牙",
                     "功能与设备支持以所下载版本的说明为准"):
            self.assertIn(text, self.html)
        features = [("connect", "记住设备，少做重复连接"),
                    ("recipes", "分享参数配方，导入前先看清"),
                    ("compare", "两杯放在一起，看清哪里不同"),
                    ("scales", "更多电子秤选择，更多过程信息")]
        for anchor, title in features:
            with self.subTest(feature=anchor):
                self.assertTrue(self.page.has("article", "id", anchor))
                self.assertIn(title, self.html)
        self.assertEqual(sum(tag == "h3" for tag, _ in self.page.tags) >= 4, True)

    def test_connection_copy_keeps_limits_visible(self):
        connect = self.section("connect")
        for text in ("在 App 前台尝试重新连接", "秤晚一点开机", "手动断开后，暂停本次使用中的自动重连",
                     "设备分别连接，状态分别看清", "按设备加密记住咖啡机密码",
                     "连接表现受设备、系统权限、距离和蓝牙环境影响",
                     "恢复连接不代表自动恢复未确认的机器操作"):
            self.assertIn(text, connect)
        self.assertNotIn("自动重连开关", connect)

    def test_recipe_copy_does_not_conflate_formats(self):
        recipes = self.section("recipes")
        for text in ("把 OpenHOYI 参数配方复制成文本，或通过 JSON 文件分享",
                     "先查看参数，再确认保存", "本机压力配方还可以调整温度、目标重量和分段压力、水量",
                     "选中配方", "分享文本或文件", "预览后导入",
                     "执行资格取决于配方模式、参数校验和设备状态",
                     "旧版曲线库导入仍为只读", "并非所有导入文件都能直接执行"):
            self.assertIn(text, recipes)
        for text in ("配方分享的是名称、温度、目标重量和分段控制参数",
                     "不含萃取遥测或私人笔记", "旧版曲线库是独立的只读导入路径"):
            self.assertIn(text, self.section("boundaries"))

    def test_comparison_demo_is_two_cups_and_labeled_example(self):
        compare = self.section("compare")
        for text in ("选出 A、B 两杯", "同一坐标轴", "每次专注一个指标",
                     "结合时长、结束时秤读数、豆子、曲线和笔记",
                     "对比用于观察过程差异，不提供自动稳定性评分或口感判断"):
            self.assertIn(text, compare)
        metrics = [a.get("data-metric") for tag, a in self.page.tags
                   if tag == "button" and "data-metric" in a]
        self.assertEqual(sorted(metrics), ["pressure", "temperature", "water", "weight"])
        # One desktop and one phone chart variant share the same four metrics.
        self.assertEqual(compare.count('data-metric-group="'), 8)
        metric_buttons = [a for tag, a in self.page.tags
                          if tag == "button" and "data-metric" in a]
        self.assertTrue(all("hidden" in a for a in metric_buttons))
        self.assertIn("button.hidden = false", self.js)
        self.assertIn('demo-chart-desktop', compare)
        self.assertIn('demo-chart-mobile', compare)
        for text in ("功能示意，非 App 截图", "示例数据", "A · 实线", "B · 虚线",
                     "同豆", "同曲线", "示例笔记", "结束时秤读数不一定等于最终出杯量",
                     "同豆、同曲线更便于对照", "缺测、未知结果和不同时间起点会明确提示",
                     "脚本未启用时，图表固定显示压力示意曲线"):
            self.assertIn(text, compare)
        self.assertNotIn("第三杯", compare)
        self.assertNotIn("稳定性评分排名", compare)
        for text in ("当前支持 A/B 两杯、一次一个指标",
                     "没有多杯批量分析、稳定性评分或自动口感判断"):
            self.assertIn(text, self.section("boundaries"))

    def test_scale_copy_names_models_and_verification(self):
        scales = self.section("scales")
        for text in ("BOOKOO", "Felicita", "TIMEMORE", "Acaia", "DiFluid",
                     "指定型号与协议", "不代表品牌所有型号兼容",
                     "仍待对应实机验证", "完整兼容列表"):
            self.assertIn(text, scales)
        self.assertNotIn("全品牌兼容", self.html)
        self.assertNotIn("全系列均兼容", self.html)

    def test_compatibility_table_keeps_all_rows_and_statuses(self):
        compatibility = self.section("compatibility")
        self.assertIn("目前没有 iPhone 或 iPad 版本", compatibility)
        for text in ("固件 1.1.3 有有限实机连接与萃取启停记录",
                     "1.1.4 已根据协议证据开放控制，启停与设置仍待实机验收",
                     "其他固件不开放控制",
                     "当前开发分支的接入范围",
                     "不同发布版本包含的型号可能不同",
                     "请以下载版本的发布说明为准"):
            self.assertIn(text, compatibility)
        self.assertEqual(compatibility.count('<th scope="row">'), 8)
        rows = {
            "BOOKOO，已采集协议": ("重量、设备流速、手动去皮", "有实机连接与归零通知记录；自动停水未完成实机验收"),
            "Felicita，指定 ASCII 协议": ("确认为克单位的重量、手动去皮", "待实机验证；旧只读绑定需重新扫描选择"),
            "TIMEMORE DOT，TES017": ("重量、设备流速、电量、手动去皮", "待实机验证"),
            "TIMEMORE Basic3 Link，TES016": ("重量、设备流速、电量、手动去皮", "待实机验证"),
            "旧款 TIMEMORE，广播名 timemore scale": ("指定协议的重量读数，实验性只读", "待实机验证"),
            "Acaia A，PROCHBT / ACAIA": ("确认为克单位的重量、手动去皮", "待实机验证"),
            "Acaia AP，LUNAR / PEARL / PYXIS": ("确认为克单位的重量，不提供去皮", "待实机验证"),
            "DiFluid，Microbalance / difluid / Mb Ti": ("帧内克单位重量、手动去皮", "待实机验证；协议最低固件 V300 / V010"),
        }
        for model, (capability, status) in rows.items():
            self.assertIn(model, compatibility)
            self.assertIn(capability, compatibility)
            self.assertIn(status, compatibility)
            self.assertIn(f'aria-label="已接入能力：{capability}"', compatibility)
            self.assertIn(f'aria-label="验证状态：{status}"', compatibility)
        for text in ("以上不代表品牌全系列兼容",
                     "只广播为 ULTRA 的 BOOKOO 旧档案使用另一协议路径",
                     "不具备自动去皮或目标重量控制资格",
                     "仍不能把它当作已验证的无人看守自动停水能力"):
            self.assertIn(text, compatibility)

    def test_extra_features_and_data_boundaries(self):
        more = self.section("more")
        for text in ("100 条工厂曲线，5 个首页快捷槽位", "按最近使用或常用程度查找",
                     "压力、温度、累计注水与秤重推导的 g/s 流速实时显示",
                     "读数缺失时明确留空", "长期复盘与近期采样分开保存",
                     "按批次记录豆子、日期和余量", "确认投粉后记录消耗",
                     "独立秤工具", "数据导出", "旧数据迁移", "布局与偏好"):
            self.assertIn(text, more)
        self.assertIn("历史 ZIP 目前只有导出，没有回导入口", more)
        self.assertNotIn("mL/s 流速实时", self.html)
        boundaries = self.section("boundaries")
        for text in ("近期历史保留最近 30 天、最多 500 条",
                     "长期复盘的元数据和笔记独立保存",
                     "原始采样仍受缓存保留范围影响"):
            self.assertIn(text, boundaries)
        for forbidden in ("一键备份恢复", "云同步", "在线配方社区", "扫码导入",
                          "自动扣豆", "OTA", "iOS 版", "秒连", "永不掉线",
                          "官方 App 没有", "比官方", "最佳萃取", "自动找出最佳"):
            self.assertTrue(forbidden not in self.html, forbidden)

    def test_screens_show_real_interface_and_provenance(self):
        screens = self.section("screens")
        for text in ("平板横屏展开设备状态和曲线", "手机竖屏把日常入口放在底部",
                     "支持深浅主题与中英文界面",
                     "首页", "萃取", "历史", "编辑曲线",
                     "以下为云端 Android 模拟器运行模拟演示包、采用平板横屏尺寸的实际界面截图", "数据为模拟数据",
                     "不代表真实设备萃取或所有场景已完成验收"):
            self.assertIn(text, screens)
        self.assertLess(screens.index("以下为云端 Android 模拟器运行模拟演示包"),
                        screens.index('class="gallery"'))
        for name in ("home.png", "extraction.png", "history.png", "editor.png"):
            with self.subTest(image=name):
                self.assertIn(f'./assets/screens/{name}?v={SCREEN_REVISION}"', self.html)
        self.assertNotIn("<picture>", self.html)
        self.assertNotIn("-detail.png", self.html)

    def test_lightbox_opens_unmodified_originals(self):
        shots = [(a.get("href", ""), int(a.get("data-focus-x", 0)), int(a.get("data-focus-y", 0)))
                 for tag, a in self.page.tags if tag == "a" and "data-view-image" in a]
        self.assertEqual(len(shots), 5)
        screens = {s["original"].split("/")[-1]: s for s in self.provenance["screens"]}
        for href, x, y in shots:
            name = urlsplit(href).path.rsplit("/", 1)[-1]
            with self.subTest(image=name):
                screen = screens[name]
                self.assertIn(f"./assets/screens/originals/{name}?v={SCREEN_REVISION}", self.page.links)
                self.assertEqual(urlsplit(href).query, f"v={SCREEN_REVISION}")
                self.assertTrue(0 <= x <= screen["width"] and 0 <= y <= screen["height"])

    def test_download_entry_points_at_releases_and_risk_is_prominent(self):
        # The page must not pin one APK: releases change, and a stale link misleads users.
        self.assertEqual([link for link in self.page.links if link.endswith(".apk")], [])
        self.assertEqual(self.page.links.count(RELEASES), 2)
        self.assertNotIn("/releases/latest", self.html)
        download = self.section("download")
        before_button = download.split(f'href="{RELEASES}"', 1)[0]
        self.assertNotIn("<details", before_button)
        self.assertIn("Alpha 阶段，不是稳定版", before_button)
        risk_at = download.index('class="risk"')
        self.assertGreater(risk_at, download.index(f'href="{RELEASES}"'))
        for text in ("无人看守", "不要依赖目标重量自动停水", "结果不明时先核对机器状态",
                     "不要反复发送指令", "不要通过卸载、清数据绕过问题",
                     "旧版数据不会自动迁移", "io.openhoyi.mobile", "SHA-256"):
            self.assertIn(text, download)
        self.assertNotIn("download=", self.html)  # Let the browser handle cross-origin files.
        for text in ("核对版本", "连接设备", "确认状态，再开始",
                     "咖啡机和电子秤分别连接", "留在机器旁"):
            self.assertIn(text, download)

    def test_support_channels_are_linked(self):
        expected = {
            "assets/sponsor/wechat-pay.png": (1180, 1607),
            "assets/community/wechat-group.png": (1090, 1523),
        }
        for relative, size in expected.items():
            with self.subTest(asset=relative):
                data = (SITE / relative).read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(struct.unpack(">II", data[16:24]), size)
                self.assertTrue(any(urlsplit(asset).path == f'./{relative}' for asset in self.page.assets))
                self.assertIn(f'width="{size[0]}" height="{size[1]}"', self.html)
        group = SITE / "assets/community/wechat-group.png"
        self.assertEqual(hashlib.sha256(group.read_bytes()).hexdigest(),
                         "21f1ce43072139b8ec784a38d8054e1f9066fe768c4c6712bab2656af9073090")
        self.assertIn('wechat-group.png?v=20261114', self.html)
        self.assertIn("11 月 22 日前有效", self.html)
        support = self.section("support")
        for text in ("请我喝杯咖啡", "OpenHOYI 交流群", "自愿赞助", "群二维码会过期",
                     "应用版本、Android 版本、设备型号与固件、复现步骤和脱敏日志"):
            self.assertIn(text, support)
        self.assertIn(ISSUES, self.page.links)
        self.assertIn(REPO, self.page.links)
        readme = (ROOT / "README.md").read_text()
        self.assertIn("## 赞助与交流", readme)
        for relative in expected:
            self.assertIn(f"site/{relative}", readme)

    def test_local_progressive_enhancement(self):
        scripts = [a for tag, a in self.page.tags if tag == "script"]
        self.assertEqual(len(scripts), 1)
        self.assertEqual(scripts[0]["src"], "./product.js")
        self.assertIn("defer", scripts[0])
        for tag, attrs in self.page.tags:
            self.assertNotIn(tag, ("iframe", "form"))
            self.assertFalse(any(key.startswith("on") for key in attrs))
        self.assertLess(len(self.js.encode()), 9000)
        for forbidden in ("fetch(", "XMLHttpRequest", "WebSocket", "navigator.bluetooth",
                          "sendBeacon", "eval(", "https://", "clipboard"):
            self.assertNotIn(forbidden, self.js)
        self.assertIn("showModal", self.js)
        self.assertIn("preventScroll: true", self.js)
        self.assertTrue(any(tag == "dialog" and a.get("aria-labelledby") == "viewer-title"
                            for tag, a in self.page.tags))
        for detail in ("alpha", "image-source", "package", "risk"):
            self.assertTrue(self.page.has("details", "id", detail)
                            or self.page.has("aside", "id", detail), detail)
        for forbidden in ("@import", "@font-face", "url("):
            self.assertNotIn(forbidden, self.css)

    def test_original_screenshot_provenance_and_readable_detail(self):
        screens = SITE / "assets/screens"
        self.assertEqual(self.provenance["schema"], 4)
        self.assertEqual(self.provenance["kind"], "actual-emulator-screenshots")
        self.assertEqual(self.provenance["sourceSha"], APP_SOURCE_SHA)
        self.assertIs(self.provenance["sourceCommitVerified"], True)
        verification = self.provenance["sourceVerification"]
        self.assertIn("GitHub Actions", verification["method"])
        self.assertIn("not a reproducible-build claim", verification["scope"])
        baseline = verification["applicationSourceObjects"]
        self.assertEqual(set(baseline), {"app", "mobile", "bean-core", "bluetooth-android", "device-session",
                                        "protocol-core", "trace-core", "localization", "build.gradle.kts",
                                        "settings.gradle.kts", "gradle.properties", "gradle", "gradlew", "gradlew.bat"})
        for identity in baseline.values():
            self.assertRegex(identity, r"^[0-9a-f]{40}$")
        self.assertTrue(verification["runs"])
        for run in verification["runs"]:
            self.assertRegex(run["headSha"], r"^[0-9a-f]{40}$")
            self.assertIs(run["applicationSourceMatchesBaseline"], True)
            self.assertEqual(run["applicationSourceObjects"], baseline)
            self.assertRegex(run["url"], r"^https://github.com/boolean93/openhoyi/actions/runs/[0-9]+$")
        self.assertEqual(self.provenance["captureMethod"], "Android UiAutomation.takeScreenshot()")
        self.assertEqual(self.provenance["device"]["androidRelease"], "14")
        self.assertEqual(self.provenance["device"]["kind"], "GitHub Actions Android emulator")
        self.assertEqual(self.provenance["appBuild"]["package"], "io.openhoyi.mobile.mock")
        self.assertIs(self.provenance["appBuild"]["binaryIdentityVerified"], False)
        self.assertIsNone(self.provenance["appBuild"]["apkSha256"])
        self.assertIn("APK", self.provenance["appBuild"]["identityNotice"])
        for text in ("云端 Android 34 模拟器", "非真实平板截图", "源码 7a22e6cb",
                     "CI 源码提交与截图来源已记录",
                     "连接、配方与两杯对比区的流程和图表为功能示意",
                     "模拟数据", "不保证与下载版本一致", "展示图仅连续裁去有明确边界记录的系统栏"):
            self.assertIn(text, self.html)
        self.assertNotIn("0.2.0-preview.76", self.html)
        self.assertEqual({s["name"] for s in self.provenance["screens"]}, {"extraction", "home", "history", "editor"})
        for screen in self.provenance["screens"]:
            runs = {run["url"]: run for run in verification["runs"]}
            self.assertIn(screen["sourceRunUrl"], runs)
            self.assertEqual(screen["captureSourceSha"], runs[screen["sourceRunUrl"]]["headSha"])
            self.assertTrue(screen["sourceArtifactPath"])
            crop = screen["displayCropPx"]
            self.assertEqual(len(crop), 4)
            self.assertEqual(crop[0], 0)
            self.assertEqual(crop[2], screen["width"])
            self.assertTrue(0 <= crop[1] < crop[3] <= screen["height"])
            self.assertEqual(screen["captureDate"], "2026-11-14")
            for file_key, hash_key, size in (
                ("original", "sha256", (screen["width"], screen["height"])),
                ("display", "displaySha256", (crop[2] - crop[0], crop[3] - crop[1])),
            ):
                asset = (screens / screen[file_key]).resolve()
                self.assertTrue(asset.is_relative_to(screens.resolve()))
                data = asset.read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(struct.unpack(">II", data[16:24]), size)
                self.assertEqual(hashlib.sha256(data).hexdigest(), screen[hash_key])
        for tag, attrs in self.page.tags:
            if tag == "img" and "/screens/" in attrs.get("src", ""):
                data = (SITE / urlsplit(attrs["src"]).path).read_bytes()
                self.assertEqual((int(attrs["width"]), int(attrs["height"])),
                                 struct.unpack(">II", data[16:24]))
                self.assertEqual(urlsplit(attrs["src"]).query, f"v={SCREEN_REVISION}")

    def test_no_unused_page_assets(self):
        screens = SITE / "assets/screens"
        referenced = {unquote(urlsplit(href).path.lstrip("./")) for href in
                      self.page.links + self.page.assets if "/screens/" in href}
        on_disk = {str(path.relative_to(SITE)) for path in screens.rglob("*.png")}
        self.assertEqual(sorted(on_disk - referenced), [])
        for name in referenced:
            self.assertTrue((SITE / name).is_file(), name)

    def test_brand_icons_follow_the_committed_design(self):
        brand = SITE / "assets/brand"
        manifest = json.loads((brand / "manifest.json").read_text())
        self.assertEqual(manifest["kind"], "site-app-icons")
        for source in manifest["sources"]:
            path = ROOT / source["file"]
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), source["sha256"])
        for output in manifest["outputs"]:
            path = brand / output["file"]
            data = path.read_bytes()
            self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
            self.assertEqual(struct.unpack(">II", data[16:24]),
                             (output["width"], output["height"]))
            self.assertEqual(hashlib.sha256(data).hexdigest(), output["sha256"])
        for href in ("./assets/brand/favicon-32.png", "./assets/brand/apple-touch-icon-180.png"):
            self.assertIn(f'href="{href}"', self.html)
        self.assertIn('src="./assets/brand/icon-release-256.png"', self.html)
        # The Mock build is a developer tool; the user-facing page must not surface it.
        self.assertNotIn("Mock", self.html)
        self.assertNotIn("mock", self.html)
        unused = {path.name for path in brand.iterdir()
                  if path.name not in ("manifest.json",)
                  and f"./assets/brand/{path.name}" not in self.html}
        self.assertEqual(unused, set())

    def test_official_curve_guide(self):
        guide = (SITE / "import-official-curves.html").read_text()
        page = Page(guide)
        js = (SITE / "import-guide.js").read_text()
        self.assertIn('./import-official-curves.html', self.html)
        self.assertEqual(len(page.ids), len(set(page.ids)))
        self.assertEqual(sum(tag == "h1" for tag, _ in page.tags), 1)
        for href in page.links + page.assets:
            # Hidden download link is populated only from a verified index.
            if not href:
                continue
            url = urlsplit(href)
            if url.scheme:
                self.assertEqual(url.scheme, "https")
                self.assertEqual(url.netloc, "github.com")
            elif url.path:
                path = (SITE / unquote(url.path)).resolve()
                self.assertTrue(path.is_relative_to(SITE.resolve()))
                self.assertTrue(path.is_file(), href)
            elif url.fragment:
                self.assertIn(url.fragment, page.ids)
        for text in ("同版本", "多张截图", "不让 AI 猜", "核对", "旧版本", "纯 JSON"):
            self.assertIn(text, guide)
        self.assertNotIn("releases/latest", guide + js)
        self.assertIn("index.available !== true", js)
        self.assertIn("index.downloadUrl !== expected", js)
        self.assertIn("requested !== index.releaseTag", js)
        self.assertIn("AbortController", js)
        self.assertNotIn("innerHTML", js)

    def test_deployment_scope(self):
        workflow = (ROOT / ".github/workflows/product-pages.yml").read_text()
        self.assertIn("github.ref == 'refs/heads/develop'", workflow)
        self.assertIn("github.event_name != 'pull_request'", workflow)
        self.assertIn("path: site", workflow)
        self.assertNotIn("path: .\n", workflow)


if __name__ == "__main__":
    unittest.main()
