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
PAGES = "https://boolean93.github.io/openhoyi/"


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


class ProductPagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (SITE / "index.html").read_text()
        cls.page = Page(cls.html)
        cls.css = (SITE / "styles.css").read_text()
        cls.js = (SITE / "product.js").read_text()
        cls.provenance = json.loads((SITE / "assets/screens/provenance.json").read_text())

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
        self.assertIn("prefers-reduced-motion", self.css)

    def test_metadata_social_cards(self):
        self.assertEqual(self.page.metas("property", "og:url"), [PAGES])
        image = self.page.metas("property", "og:image")
        self.assertTrue(image and all(url.startswith(PAGES) for url in image), image)
        self.assertEqual(self.page.metas("name", "twitter:card"), ["summary_large_image"])

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

    def test_ordinary_user_path_and_real_download(self):
        hero = self.html.split('class="hero"', 1)[1].split("</section>", 1)[0]
        self.assertIn('class="hero-figure"', hero)
        self.assertIn('data-download-entry', hero)
        self.assertIn("真实 App 界面", hero)
        h1 = re.search(r"<h1[^>]*>(.*?)</h1>", self.html, re.S).group(1)
        self.assertIn("更懂你的", h1)
        self.assertIn("HOYI app", h1)
        for text in ("增强控制器", "独立开发 · 非 HOYI 官方应用", "开源的安卓应用",
                     "在平板上选曲线、看实时数据、回看每一杯",
                     "Android 8.0 及以上", "为平板横屏设计", "安卓手机同样可用",
                     "没有 iPhone / iPad", "固件 1.1.3", "BOOKOO",
                     "六位设备密码", "不要卸载旧版或清除数据", "留在机器旁"):
            self.assertIn(text, self.html)
        for dropped in ("装进手机里", "把咖啡机", "一块平板", "看清这一杯"):
            self.assertNotIn(dropped, self.html)
        for jargon in ("./gradlew", "git clone", "JDK 17", "从源码构建",
                       "THE EXTRACTION STUDY", "data-scene-tab", "sourceCommitVerified",
                       "API 26"):
            self.assertNotIn(jargon, self.html)

    def test_screenshots_show_the_complete_screen(self):
        # One unframed screenshot per screen: no art-directed crops that hide UI on phones.
        for name in ("home.png", "extraction.png", "history.png"):
            with self.subTest(image=name):
                self.assertIn(f'<img src="./assets/screens/{name}" width="2560" height="1504"', self.html)
        self.assertNotIn("<picture>", self.html)
        self.assertNotIn('-detail.png', self.html)
        self.assertTrue(self.page.metas("name", "viewport"))

    def test_lightbox_opens_unmodified_originals(self):
        shots = [(a.get("href", ""), int(a.get("data-focus-x", 0)), int(a.get("data-focus-y", 0)))
                 for tag, a in self.page.tags if tag == "a" and "data-view-image" in a]
        self.assertEqual(len(shots), 3)
        screens = {s["original"].split("/")[-1]: s for s in self.provenance["screens"]}
        for href, x, y in shots:
            name = href.rsplit("/", 1)[-1]
            with self.subTest(image=name):
                screen = screens[name]
                self.assertIn(f"./assets/screens/originals/{name}", self.page.links)
                self.assertTrue(0 <= x <= screen["width"] and 0 <= y <= screen["height"])

    def test_download_entry_points_at_releases(self):
        # The page must not pin one APK: releases change, and a stale link misleads users.
        self.assertEqual([link for link in self.page.links if link.endswith(".apk")], [])
        self.assertEqual(self.page.links.count(RELEASES), 2)
        download = self.html.split('id="download"', 1)[1].split("</section>", 1)[0]
        before_button = download.split(f'href="{RELEASES}"', 1)[0]
        self.assertNotIn("<details", before_button)
        self.assertIn("不是稳定版", before_button)  # Readable before the link leaves the page.
        caveats = download.split('class="caveats"', 1)[1].split("</ul>", 1)[0]
        self.assertEqual(caveats.count("<li>"), 3)  # Keep the page short: three plain lines only.
        for text in ("支持范围会随版本变化", "留在机器旁", "不要反复发送指令", "界面可能不同"):
            self.assertIn(text, caveats)
        for text in ("io.openhoyi.mobile", "SHA-256", "不要依赖按目标重量自动停水",
                     "设备不可用或损坏", "源码提交映射由构建方声明"):
            self.assertIn(text, download)
        self.assertNotIn("download=", self.html)  # Let the browser handle cross-origin files.

    def test_support_range_follows_each_release(self):
        compatibility = self.html.split('id="compatibility"', 1)[1].split("</section>", 1)[0]
        download = self.html.split('id="download"', 1)[1].split("</section>", 1)[0]
        for text in ("开发版已接入 8 款品牌与型号", "不同版本包含的型号不同",
                     "以你下载版本的说明为准"):
            self.assertIn(text, compatibility)
        self.assertIn("以下载版本的发布说明为准", compatibility)
        self.assertIn("支持范围会随版本变化", download)
        for dropped in ("本页可下载的测试版只支持 BOOKOO", "构建于 2026-11-04，只包含 BOOKOO",
                        "releases/download/v0.2.0-rc.2"):
            self.assertNotIn(dropped, self.html)
        self.assertNotIn("完整采样曲线", self.html)  # Curves may be partial after a crash.
        for text in ("最近 30 天的", "最多 500 条"):
            self.assertIn(text, self.html)

    def test_local_progressive_enhancement(self):
        scripts = [a for tag, a in self.page.tags if tag == "script"]
        self.assertEqual(len(scripts), 1)
        self.assertEqual(scripts[0]["src"], "./product.js")
        self.assertIn("defer", scripts[0])
        for tag, attrs in self.page.tags:
            self.assertNotIn(tag, ("iframe", "form"))
            self.assertFalse(any(key.startswith("on") for key in attrs))
        self.assertLess(len(self.js.encode()), 8000)
        for forbidden in ("fetch(", "XMLHttpRequest", "WebSocket", "navigator.bluetooth",
                          "sendBeacon", "eval(", "https://", "clipboard"):
            self.assertNotIn(forbidden, self.js)
        self.assertIn("showModal", self.js)
        self.assertIn("preventScroll: true", self.js)
        self.assertTrue(any(tag == "dialog" and a.get("aria-labelledby") == "viewer-title"
                            for tag, a in self.page.tags))
        for detail in ("alpha", "image-source"):
            self.assertTrue(any(tag == "details" and a.get("id") == detail
                                for tag, a in self.page.tags))
        for forbidden in ("@import", "@font-face", "url("):
            self.assertNotIn(forbidden, self.css)

    def test_original_screenshot_provenance_and_readable_detail(self):
        screens = SITE / "assets/screens"
        self.assertEqual(self.provenance["schema"], 3)
        self.assertEqual(self.provenance["kind"], "actual-tablet-screenshots")
        self.assertIsNone(self.provenance["sourceSha"])
        self.assertIs(self.provenance["sourceCommitVerified"], False)
        self.assertEqual(self.provenance["captureMethod"], "adb exec-out screencap -p")
        self.assertEqual(self.provenance["device"]["model"], "M2105K81AC")
        self.assertEqual(self.provenance["installedApp"]["package"], "io.openhoyi.mobile.mock")
        for text in ("平板实际截图", "源码提交映射未核验", "0.1.0（53）", "并非手机端布局",
                     "使用模拟数据", "不保证与下载的测试版一致"):
            self.assertIn(text, self.html)
        self.assertEqual({s["name"] for s in self.provenance["screens"]}, {"extraction", "home", "history"})
        for screen in self.provenance["screens"]:
            self.assertEqual(screen["displayCropPx"], [0, 60, 2560, 1564])
            self.assertRegex(screen["capturedAt"], r"^2026-11-06T")
            for file_key, hash_key, size in (
                ("original", "sha256", (screen["width"], screen["height"])),
                ("display", "displaySha256", (2560, 1504)),
            ):
                asset = (screens / screen[file_key]).resolve()
                self.assertTrue(asset.is_relative_to(screens.resolve()))
                data = asset.read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(struct.unpack(">II", data[16:24]), size)
                self.assertEqual(hashlib.sha256(data).hexdigest(), screen[hash_key])
        for tag, attrs in self.page.tags:
            if tag == "img" and "/screens/" in attrs.get("src", ""):
                data = (SITE / attrs["src"]).read_bytes()
                self.assertEqual((int(attrs["width"]), int(attrs["height"])),
                                 struct.unpack(">II", data[16:24]))

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
        for src in ("./assets/brand/icon-release-256.png", "./assets/brand/icon-mock-256.png"):
            self.assertIn(f'src="{src}"', self.html)
        unused = {path.name for path in brand.iterdir()
                  if path.name not in ("manifest.json",)
                  and f"./assets/brand/{path.name}" not in self.html}
        self.assertEqual(unused, set())

    def test_deployment_scope(self):
        workflow = (ROOT / ".github/workflows/product-pages.yml").read_text()
        self.assertIn("github.ref == 'refs/heads/develop'", workflow)
        self.assertIn("github.event_name != 'pull_request'", workflow)
        self.assertIn("path: site", workflow)
        self.assertNotIn("path: .\n", workflow)


if __name__ == "__main__":
    unittest.main()
