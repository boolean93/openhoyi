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
APK = "https://github.com/boolean93/openhoyi/releases/download/v0.2.0-rc.2/OpenHOYI-debug-0.2.0-rc.2.apk"


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
        if tag in ("img", "script"):
            self.assets.append(attrs.get("src", ""))
        if tag == "link" and attrs.get("rel") != "canonical":
            self.assets.append(attrs.get("href", ""))


class ProductPagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (SITE / "index.html").read_text()
        cls.page = Page(cls.html)

    def test_metadata_and_accessibility(self):
        self.assertIn(("html", {"lang": "zh-CN"}), self.page.tags)
        self.assertEqual(sum(tag == "h1" for tag, _ in self.page.tags), 1)
        self.assertTrue(any(tag == "link" and a.get("rel") == "canonical"
                            and a.get("href") == "https://boolean93.github.io/openhoyi/"
                            for tag, a in self.page.tags))
        for name in ("viewport", "description"):
            self.assertTrue(any(tag == "meta" and a.get("name") == name
                                and a.get("content") for tag, a in self.page.tags))
        self.assertNotIn("user-scalable=no", self.html)
        self.assertTrue(any(tag == "a" and a.get("class") == "skip-link"
                            for tag, a in self.page.tags))
        for tag, attrs in self.page.tags:
            if tag == "img":
                self.assertIn("alt", attrs)
            if tag == "button":
                self.assertEqual(attrs.get("type"), "button")

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
        intro = self.html.split('class="intro wrap"', 1)[1].split('</section>', 1)[0]
        self.assertIn('class="hero-product"', intro)
        self.assertIn('src="./assets/screens/home.png"', intro)
        self.assertIn('App完整首页', intro)
        self.assertLess(intro.index('class="hero-product"'), intro.index('data-download-entry'))
        self.assertNotIn('loading="lazy"', intro)
        for text in ("用安卓手机连接", "HOYI 咖啡机", "选曲线", "看过程", "查记录",
                     "Android 8.0", "没有 iPhone / iPad", "固件 1.1.3", "BOOKOO",
                     "不要卸载旧版或清除数据", "六位设备密码"):
            self.assertIn(text, self.html)
        for old in ("./gradlew", "git clone", "JDK 17", "从源码构建", "萃取标本台",
                    "一杯咖啡，不止一个结果", "THE EXTRACTION STUDY", "data-scene-tab"):
            self.assertNotIn(old, self.html)
        self.assertEqual([link for link in self.page.links if link.endswith(".apk")], [APK])
        self.assertGreaterEqual(self.page.links.count("#download"), 2)
        download = self.html.split('id="download"', 1)[1].split('</section>', 1)[0]
        before_apk = download.split(f'href="{APK}"', 1)[0]
        self.assertNotIn("<details", before_apk)
        for text in ("不是稳定版", "不要依赖按目标重量自动停水", "不要反复发送指令",
                     "设备损坏", "与这个测试版可能不同", "19,307,368", "sourceCommitVerified=false"):
            self.assertIn(text, download)
        self.assertIn("1ef3fcf474ef0ab0aa9a715f268a53d68bacf0c12954c5bac225d118bb188691", download)
        self.assertIn("https://github.com/boolean93/openhoyi/releases/tag/v0.2.0-rc.2", self.page.links)
        self.assertNotIn("download=", self.html)  # No attempt to override cross-origin browser handling.

    def test_local_progressive_enhancement(self):
        scripts = [a for tag, a in self.page.tags if tag == "script"]
        self.assertEqual(len(scripts), 1)
        self.assertEqual(scripts[0]["src"], "./product.js")
        self.assertIn("defer", scripts[0])
        for tag, attrs in self.page.tags:
            self.assertNotIn(tag, ("iframe", "form"))
            self.assertFalse(any(key.startswith("on") for key in attrs))
        js = (SITE / "product.js").read_text()
        self.assertLess(len(js.encode()), 8000)
        for forbidden in ("fetch(", "XMLHttpRequest", "WebSocket", "navigator.bluetooth",
                          "sendBeacon", "eval(", "https://", "clipboard"):
            self.assertNotIn(forbidden, js)
        self.assertIn("showModal", js)
        self.assertIn("preventScroll: true", js)
        self.assertTrue(any(tag == "dialog" and a.get("aria-labelledby") == "viewer-title"
                            for tag, a in self.page.tags))
        for detail in ("scale-models", "package-details", "image-source", "alpha"):
            self.assertTrue(any(tag == "details" and a.get("id") == detail
                                for tag, a in self.page.tags))
        css = (SITE / "styles.css").read_text()
        self.assertIn("prefers-reduced-motion", css)
        self.assertIn(":focus-visible", css)
        self.assertNotIn("@import", css)
        self.assertNotIn("@font-face", css)
        self.assertNotIn("url(", css)

    def test_original_screenshot_provenance_and_readable_detail(self):
        screens = SITE / "assets/screens"
        provenance = json.loads((screens / "provenance.json").read_text())
        self.assertEqual(provenance["schema"], 2)
        self.assertEqual(provenance["kind"], "actual-tablet-screenshots")
        self.assertIsNone(provenance["sourceSha"])
        self.assertIs(provenance["sourceCommitVerified"], False)
        self.assertEqual(provenance["captureMethod"], "adb exec-out screencap -p")
        self.assertEqual(provenance["device"]["model"], "M2105K81AC")
        self.assertEqual(provenance["installedApp"]["package"], "io.openhoyi.mobile.mock")
        for text in ("平板实际截图", "源码提交映射未核验", "0.1.0（53）", "并非手机端布局",
                     "使用模拟数据", "不保证与下载的测试版一致"):
            self.assertIn(text, self.html)
        self.assertEqual({s["name"] for s in provenance["screens"]}, {"extraction", "home", "history"})
        for screen in provenance["screens"]:
            x1, y1, x2, y2 = screen["cropPx"]
            self.assertTrue(0 <= x1 < x2 <= screen["width"])
            self.assertTrue(0 <= y1 < y2 <= screen["height"])
            self.assertEqual(screen["displayCropPx"], [0, 60, 2560, 1564])
            self.assertRegex(screen["capturedAt"], r"^2026-11-06T")
            for file_key, hash_key, size in (
                ("original", "sha256", (screen["width"], screen["height"])),
                ("display", "displaySha256", (2560, 1504)),
                ("responsiveDetail", "detailSha256", (x2 - x1, y2 - y1)),
            ):
                asset = (screens / screen[file_key]).resolve()
                self.assertTrue(asset.is_relative_to(screens.resolve()))
                data = asset.read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(struct.unpack(">II", data[16:24]), size)
                self.assertEqual(hashlib.sha256(data).hexdigest(), screen[hash_key])
                if file_key == "original":
                    self.assertIn("./assets/screens/" + screen[file_key], self.page.links)
        previews = json.loads((screens / "feature-previews.json").read_text())["previews"]
        self.assertEqual(len(previews), 2)
        self.assertEqual([p["cropPx"] for p in previews], [[250, 1025, 715, 1455], [275, 335, 875, 620]])
        for preview in previews:
            source = screens / preview["source"]
            self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), preview["sourceSha256"])
            x1, y1, x2, y2 = preview["cropPx"]
            self.assertEqual((preview["width"], preview["height"]), (x2 - x1, y2 - y1))
            self.assertEqual(hashlib.sha256((screens / preview["file"]).read_bytes()).hexdigest(), preview["sha256"])
            self.assertIn("./assets/screens/" + preview["file"], self.page.assets)
        for tag, attrs in self.page.tags:
            if tag == "img" and "/screens/" in attrs.get("src", ""):
                data = (SITE / attrs["src"]).read_bytes()
                self.assertEqual((int(attrs["width"]), int(attrs["height"])), struct.unpack(">II", data[16:24]))

    def test_deployment_scope(self):
        workflow = (ROOT / ".github/workflows/product-pages.yml").read_text()
        self.assertIn("github.ref == 'refs/heads/develop'", workflow)
        self.assertIn("github.event_name != 'pull_request'", workflow)
        self.assertIn("path: site", workflow)
        self.assertNotIn("path: .\n", workflow)


if __name__ == "__main__":
    unittest.main()
