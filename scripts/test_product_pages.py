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
APP_SOURCE_SHA = "e5cd67ac54a547706b8712675fcbacb4fe935ad7"
SCREEN_REVISION = APP_SOURCE_SHA[:8]
SCREENS = ("home", "extraction", "history", "editor")


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

    @staticmethod
    def png_size(path):
        data = path.read_bytes()
        assert data[:8] == b"\x89PNG\r\n\x1a\n", path
        return struct.unpack(">II", data[16:24])

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
            if attrs.get("aria-labelledby"):
                for ref in attrs["aria-labelledby"].split():
                    self.assertIn(ref, self.page.ids)
        self.assertIn(":focus-visible", self.css)
        self.assertIn(".demo-chart-mobile .chart-tick { font-size: 14px; }", self.css)
        self.assertIn("prefers-reduced-motion", self.css)
        self.assertIn("forced-colors", self.css)

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

    def test_hero_and_core_features(self):
        hero = self.section("top")
        self.assertIn('class="hero-stage"', hero)
        self.assertIn("data-download-entry", hero)
        self.assertIn("data-view-image", hero)
        h1 = re.search(r"<h1[^>]*>(.*?)</h1>", self.html, re.S).group(1)
        self.assertIn("连上 OpenHOYI", h1)
        self.assertIn("冲好这一杯", h1)
        for text in ("开源 · ANDROID · 非官方 ALPHA",
                     "机器、电子秤、曲线和历史都在一个 App 里", "不用来回切",
                     "下载测试版", "看看界面",
                     "Android 8.0+ · 低功耗蓝牙 · GPL v2 开源", "非官方 Alpha",
                     "平板上的模拟演示版截图", "不是真机萃取或硬件验收"):
            self.assertIn(text, self.html)
        features = self.section("features")
        for title in ("曲线可以自己改。", "两杯放在一起看。", "机器和秤，分开连。",
                      "几款常见电子秤。", "把这一杯记下来。"):
            self.assertIn(title, features)
        for stat in ("100", "5 个首页快捷槽位", "30", "500"):
            self.assertIn(stat, features)

    def test_connection_copy_keeps_limits_visible(self):
        for text in ("密码按设备记住", "App 在前台时会尝试重连",
                     "手动断开后，这一次不再自动连",
                     "秤晚一点开机，也会继续找上次的设备",
                     "重连成功，不代表刚才没确认的操作会自动恢复"):
            self.assertIn(text, self.html)
        self.assertNotIn("自动重连开关", self.html)

    def test_recipe_copy_does_not_conflate_formats(self):
        html = self.html
        for text in ("本机压力／流量配方可以改首段模式、各段参数、目标重量、温度和控制逻辑",
                     "后续段跟随全局模式",
                     "保存为本机配方，复制文本或用 JSON 分享",
                     "导入时先看参数再保存",
                     "能不能执行，还要看模式、校验和设备状态",
                     "旧版曲线库导入仍为只读",
                     "也不是所有导入文件都能执行"):
            self.assertIn(text, html)
        self.assertIn("./import-official-curves.html", html)
        self.assertIn("查看导入指南", html)

    def test_ai_and_machine_features_keep_boundaries(self):
        ai = self.section("ai")
        for text in ("DeepSeek API Key", "你保存的采样", "回原生界面确认",
                     "不会自动连接、启动或控制机器", "不能改工厂曲线",
                     "每次回复最多联网搜索 4 次", "费用从你的 API 账户扣",
                     "本机统计不是账单", "会话不进云备份",
                     "自动评价默认关闭", "发当杯测量数据", "非真实 API 输出"):
            self.assertIn(text, ai)
        faq = self.section("faq")
        for text in ("每天的睡眠计划（可以逐日微调）", "杯数未知或过期会显示 —",
                     "应用内检查更新", "不等于硬件验收"):
            self.assertIn(text, faq)

    def test_comparison_demo_is_two_cups_and_labeled_example(self):
        compare = self.section("compare")
        for text in ("选 A、B 两杯", "一次只看一个指标",
                     "压力、注水、秤重或温度", "曲线看完",
                     "功能示意，不是 App 截图", "示例数据",
                     "A · 实线", "B · 虚线", "同豆", "同曲线", "示例笔记",
                     "结束时秤读数不一定等于最终出杯量",
                     "不给稳定性评分，也不判断好不好喝"):
            self.assertIn(text, compare)
        metrics = [a.get("data-metric") for tag, a in self.page.tags
                   if tag == "button" and "data-metric" in a]
        self.assertEqual(sorted(metrics), ["pressure", "temperature", "water", "weight"])
        self.assertEqual(compare.count('data-metric-group="'), 8)
        metric_buttons = [a for tag, a in self.page.tags
                          if tag == "button" and "data-metric" in a]
        self.assertTrue(all("hidden" in a for a in metric_buttons))
        self.assertIn("button.hidden = false", self.js)
        self.assertIn("demo-chart-desktop", compare)
        self.assertIn("demo-chart-mobile", compare)
        self.assertIn("脚本未启用时，图表固定显示压力示意曲线", compare)
        self.assertNotIn("第三杯", compare)
        self.assertNotIn("稳定性评分排名", compare)

    def test_scale_copy_names_models_and_verification(self):
        compatibility = self.section("compatibility")
        for text in ("BOOKOO", "Felicita", "TIMEMORE", "Acaia", "DiFluid",
                     "指定型号和协议", "不是品牌全系列", "还待实机验证",
                     "除 BOOKOO 外", "独立秤工具"):
            self.assertIn(text, compatibility)
        self.assertNotIn("全品牌兼容", self.html)
        self.assertNotIn("全系列均兼容", self.html)

    def test_compatibility_table_keeps_all_rows_and_statuses(self):
        compatibility = self.section("compatibility")
        self.assertIn("没有 iPhone 或 iPad 版", compatibility)
        for text in ("1.1.3 有实机连接和启停记录",
                     "1.1.4、1.1.5、1.1.6 根据协议证据开放控制，启停和设置还没做完实机验收",
                     "其他固件只读，不能控制",
                     "当前开发分支接入了什么",
                     "不同版本可能不一样",
                     "请以下载版本的说明为准"):
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

    def test_data_and_version_boundaries(self):
        boundaries = self.section("boundaries")
        for text in ("不要无人看守", "目标重量自动停水", "记录不会一直留着",
                     "近期历史只留最近 30 天、最多 500 条",
                     "长期复盘的元数据和笔记单独存", "原始采样仍按缓存范围清理",
                     "ZIP 只能导出，没有回导", "旧版数据也不会自动搬过来",
                     "不是每个固件都能控制", "1.1.4–1.1.6 的控制许可是根据协议证据开的",
                     "不认识的固件不给控制", "别卸载、别清数据"):
            self.assertIn(text, boundaries)
        faq = self.section("faq")
        for text in ("近期历史可以导出 ZIP", "长期复盘可以导出 JSON",
                     "App 不会去读旧 App 的私有数据"):
            self.assertIn(text, faq)
        for forbidden in ("一键备份恢复", "云同步", "在线配方社区", "扫码导入",
                          "自动扣豆", "iOS 版", "秒连", "永不掉线",
                          "官方 App 没有", "比官方", "最佳萃取", "自动找出最佳"):
            self.assertTrue(forbidden not in self.html, forbidden)

    def test_download_entry_points_at_releases_and_risk_is_prominent(self):
        self.assertEqual([link for link in self.page.links if link.endswith(".apk")], [])
        self.assertEqual(self.page.links.count(RELEASES), 1)
        self.assertNotIn("/releases/latest", self.html)
        self.assertNotIn("download=", self.html)
        download = self.section("download")
        before_button = download.split(f'href="{RELEASES}"', 1)[0]
        self.assertNotIn("<details", before_button)
        self.assertIn("Alpha 阶段，不是稳定版", before_button)
        self.assertGreater(download.index('class="risk"'), download.index(f'href="{RELEASES}"'))
        for text in ("无人看守", "不要靠目标重量自动停水", "结果不对时先看机器",
                     "别连续发指令", "别卸载旧版，也别清数据",
                     "旧版数据不会自动搬过来", "io.openhoyi.mobile", "SHA-256",
                     "先看版本", "连设备", "第一次用，留在机器旁",
                     "咖啡机和秤分开连", "机器自己的停水方式先留着"):
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

    def test_screens_are_real_interface_and_fully_traceable(self):
        screens = {s["name"]: s for s in self.provenance["screens"]}
        self.assertEqual(set(screens), set(SCREENS))
        # Five entry points open the four originals; the home screen appears in the viewer default.
        shots = [(urlsplit(a.get("href", "")).path.rsplit("/", 1)[-1],
                  int(a.get("data-focus-x", 0)), int(a.get("data-focus-y", 0)))
                 for tag, a in self.page.tags if tag == "a" and "data-view-image" in a]
        self.assertEqual(len(shots), 5)
        for name, x, y in shots:
            with self.subTest(image=name):
                screen = screens[name.replace(".png", "")]
                self.assertIn(f"./assets/screens/originals/{name}?v={SCREEN_REVISION}", self.page.links)
                self.assertTrue(0 <= x <= screen["width"] and 0 <= y <= screen["height"])
        for name in SCREENS:
            with self.subTest(display=name):
                self.assertIn(f'./assets/screens/{name}.png?v={SCREEN_REVISION}', self.html)
        self.assertNotIn("<picture>", self.html)
        self.assertNotIn("-detail.png", self.html)
        self.assertIn("系统栏已裁掉", self.html)
        self.assertIn("不是 CI 流水线或可重现构建", self.html)

    def test_local_tablet_screenshot_provenance(self):
        self.assertEqual(self.provenance["schema"], 5)
        self.assertEqual(self.provenance["kind"], "local-tablet-mock-screenshots")
        self.assertEqual(self.provenance["sourceSha"], APP_SOURCE_SHA)
        self.assertIs(self.provenance["sourceCommitVerified"], True)
        verification = self.provenance["sourceVerification"]
        self.assertIn("Local git worktree", verification["method"])
        self.assertIn("not a reproducible build", verification["scope"])
        self.assertEqual(verification["baseCommit"], APP_SOURCE_SHA)
        self.assertEqual(verification["worktreeHead"], "05053987ea5dc9b3314852595c318e10a50f87cd")
        self.assertIs(verification["appSourceDiffEmpty"], True)
        self.assertEqual(set(verification["appSourcePaths"]), {
            "app", "mobile", "bean-core", "bluetooth-android", "device-session",
            "protocol-core", "trace-core", "localization", "build.gradle.kts",
            "settings.gradle.kts", "gradle.properties", "gradle", "gradlew", "gradlew.bat"})
        self.assertIn("assembleMock", verification["buildCommand"])
        self.assertEqual(self.provenance["captureMethod"], "adb exec-out screencap -p")
        device = self.provenance["device"]
        self.assertEqual(device["model"], "M2105K81AC")
        self.assertEqual(device["androidRelease"], "13")
        self.assertEqual(device["display"], "2560x1600 landscape")
        self.assertEqual(device["densityDpi"], 360)
        app = self.provenance["appBuild"]
        self.assertEqual(app["package"], "io.openhoyi.mobile.mock")
        self.assertEqual(app["versionName"], "0.2.0-mock.164")
        self.assertEqual(app["versionCode"], 164)
        self.assertEqual(app["apkSha256"], "c3fed7072a9f0a1401461ccd8ccab43bad866697a6214cd1cd1635dc246ee254")
        self.assertEqual(app["signerSha256"], "c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54")
        self.assertIs(app["binaryIdentityVerified"], True)
        for text in ("本机 Xiaomi Pad 5（Android 13）", "versionCode 164",
                     "源码基线 e5cd67ac", "APK SHA-256 c3fed707",
                     "本地模拟采集", "不是 CI 流水线或可重现构建",
                     "不是真机萃取或硬件验收", "模拟数据"):
            self.assertIn(text, self.html)
        self.assertNotIn("7a22e6cb", self.html)
        self.assertNotIn("云端", self.html)
        self.assertNotIn("模拟器", self.html)
        self.assertIn("模拟演示版", self.html)
        for screen in self.provenance["screens"]:
            with self.subTest(screen=screen["name"]):
                self.assertIn(screen["theme"], ("light", "dark"))
                self.assertRegex(screen["sourceCapture"], r"^\d{2}-mock-.*\.png$")
                self.assertEqual(screen["captureDate"], "2026-11-20")
                self.assertEqual(screen["sourceProfile"], "M2105K81AC / Android 13 / 2560x1600 / Mock")
                self.assertIn("Contiguous crop", screen["displayTreatment"])
                crop = screen["displayCropPx"]
                self.assertEqual(crop, [0, 60, 2560, 1564])
                for file_key, hash_key, size in (
                    ("original", "sha256", (screen["width"], screen["height"])),
                    ("display", "displaySha256", (crop[2] - crop[0], crop[3] - crop[1])),
                ):
                    asset = (SITE / "assets/screens" / screen[file_key]).resolve()
                    self.assertTrue(asset.is_relative_to((SITE / "assets/screens").resolve()))
                    data = asset.read_bytes()
                    self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                    self.assertEqual(struct.unpack(">II", data[16:24]), size)
                    self.assertEqual(hashlib.sha256(data).hexdigest(), screen[hash_key])

    def test_every_page_image_matches_its_png_bytes(self):
        for tag, attrs in self.page.tags:
            if tag != "img":
                continue
            path = urlsplit(attrs["src"]).path
            asset = (SITE / unquote(path)).resolve()
            self.assertTrue(asset.is_file(), path)
            width, height = int(attrs["width"]), int(attrs["height"])
            source_width, source_height = self.png_size(asset)
            if path.startswith(("./assets/screens/", "./assets/sponsor/", "./assets/community/")):
                self.assertEqual((width, height), (source_width, source_height), path)
            else:
                # Brand icons are rendered below intrinsic size; keep the aspect ratio exact.
                self.assertAlmostEqual(width / height, source_width / source_height, places=2, msg=path)

    def test_no_unused_page_assets(self):
        screens = SITE / "assets/screens"
        referenced = {urlsplit(href).path.lstrip("./") for href in
                      self.page.links + self.page.assets if "/screens/" in href}
        on_disk = {str(path.relative_to(SITE)) for path in screens.rglob("*.png")}
        self.assertEqual(sorted(on_disk - referenced), [])
        for name in referenced:
            self.assertTrue((SITE / name).is_file(), name)
        brand = SITE / "assets/brand"
        for path in brand.iterdir():
            if path.name == "manifest.json":
                continue
            self.assertIn(f"./assets/brand/{path.name}", self.html)

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

    def test_official_curve_guide(self):
        guide = (SITE / "import-official-curves.html").read_text()
        page = Page(guide)
        js = (SITE / "import-guide.js").read_text()
        self.assertIn("./import-official-curves.html", self.html)
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
