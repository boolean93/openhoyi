"""Small, dependency-free checks for the public static product site."""
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


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.ids = []
        self.links = []
        self.assets = []
        self.tags = []
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
        if tag == "source":
            self.assets.append(attrs.get("srcset", ""))
        if tag == "link":
            self.assets.append(attrs.get("href", ""))


class ProductPagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (SITE / "index.html").read_text()
        cls.page = Page(cls.html)

    def test_metadata_and_accessibility(self):
        self.assertIn(("html", {"lang": "zh-CN"}), self.page.tags)
        self.assertEqual(sum(tag == "h1" for tag, _ in self.page.tags), 1)
        for name in ("viewport", "description"):
            self.assertTrue(any(tag == "meta" and a.get("name") == name
                                and a.get("content") for tag, a in self.page.tags))
        self.assertTrue(any(tag == "a" and a.get("class") == "skip-link"
                            for tag, a in self.page.tags))
        for tag, attrs in self.page.tags:
            if tag == "img":
                self.assertIn("alt", attrs)

    def test_internal_links_and_assets(self):
        self.assertEqual(len(self.page.ids), len(set(self.page.ids)))
        for href in self.page.links + self.page.assets:
            with self.subTest(href=href):
                self.assertTrue(href)
                url = urlsplit(href)
                if url.scheme or url.netloc:
                    self.assertEqual(url.scheme, "https")
                    self.assertEqual(url.netloc, "github.com")
                    # Resolve repository file links against this exact source tree.
                    prefix = "/boolean93/openhoyi/blob/develop/"
                    if url.path.startswith(prefix):
                        target = ROOT / unquote(url.path[len(prefix):])
                        self.assertTrue(target.is_file())
                        if target.suffix == ".md" and url.fragment:
                            anchors = {
                                re.sub(r"[^\w\- ]", "", line.lstrip("# ").lower()).replace(" ", "-")
                                for line in target.read_text().splitlines() if line.startswith("#")
                            }
                            self.assertIn(unquote(url.fragment), anchors)
                elif url.path:
                    asset = (SITE / url.path).resolve()
                    self.assertTrue(asset.is_relative_to(SITE.resolve()))
                    self.assertTrue(asset.is_file())
                elif url.fragment:
                    self.assertIn(unquote(url.fragment), self.page.ids)

    def test_static_only_and_explicit_risk_boundaries(self):
        for tag, attrs in self.page.tags:
            self.assertNotIn(tag, ("script", "iframe", "form"))
            self.assertFalse(any(key.startswith("on") for key in attrs))
        for text in ("Alpha", "Android 8.0", "1.1.3", "BOOKOO", "App 实际界面", "Mock 模拟数据", "非硬件验收",
                     "非实时数据", "不连接或控制设备", "目标重量自动停止",
                     "仍待完整验收", "待实机验证", "损坏", "保留机器本身的停止手段"):
            self.assertIn(text, self.html)
        css = (SITE / "styles.css").read_text()
        self.assertIn("prefers-reduced-motion", css)
        self.assertIn(":focus-visible", css)
        self.assertNotIn("@import", css)
        self.assertNotIn("url(", css)
        self.assertTrue((SITE / ".nojekyll").is_file())

    def test_product_screenshots_and_provenance(self):
        screens = SITE / "assets/screens"
        provenance = json.loads((screens / "provenance.json").read_text())
        self.assertEqual(provenance["schema"], 2)
        self.assertEqual(provenance["kind"], "actual-tablet-screenshots")
        self.assertIsNone(provenance["sourceSha"])
        self.assertIs(provenance["sourceCommitVerified"], False)
        self.assertEqual(provenance["captureMethod"], "adb exec-out screencap -p")
        self.assertEqual(provenance["device"]["model"], "M2105K81AC")
        self.assertEqual(provenance["installedApp"]["package"], "io.openhoyi.mobile.mock")
        self.assertRegex(provenance["installedApp"]["apkSha256"], r"^[0-9a-f]{64}$")
        for text in ("平板实际截图", "源码提交映射未核验", "0.1.0（53）", "只裁去系统栏"):
            self.assertIn(text, self.html)
        self.assertNotIn("截图采自开源版本的 CI Mock 环境", self.html)
        self.assertIn("并非手机端布局", self.html)
        self.assertEqual({s["name"] for s in provenance["screens"]},
                         {"extraction", "home", "history"})
        originals = set()
        for screen in provenance["screens"]:
            originals.add("./assets/screens/" + screen["original"])
            x1, y1, x2, y2 = screen["cropPx"]
            self.assertTrue(0 <= x1 < x2 <= screen["width"])
            self.assertTrue(0 <= y1 < y2 <= screen["height"])
            self.assertRegex(screen["capturedAt"], r"^2026-11-06T")
            self.assertEqual(screen["displayCropPx"], [0, 60, 2560, 1564])
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
                asset_url = "./assets/screens/" + screen[file_key]
                if file_key == "original":
                    self.assertIn(asset_url, self.page.links)
                else:
                    self.assertIn(asset_url, self.page.assets)
        self.assertTrue(originals.issubset(self.page.links))
        self.assertEqual(sum(tag == "picture" for tag, _ in self.page.tags), 3)
        for scene in ("connect", "observe", "remember", "image-source"):
            self.assertIn(scene, self.page.ids)
        # Intrinsic dimensions prevent layout shifts and distorted screenshots.
        for tag, attrs in self.page.tags:
            if tag in ("img", "source") and "/screens/" in (attrs.get("src", "") + attrs.get("srcset", "")):
                image_path = attrs.get("src") or attrs["srcset"]
                dimensions = struct.unpack(">II", (SITE / image_path).read_bytes()[16:24])
                self.assertEqual((int(attrs["width"]), int(attrs["height"])), dimensions)

    def test_deployment_scope(self):
        workflow = (ROOT / ".github/workflows/product-pages.yml").read_text()
        self.assertIn("github.ref == 'refs/heads/develop'", workflow)
        self.assertIn("github.event_name != 'pull_request'", workflow)
        self.assertIn("path: site", workflow)
        self.assertNotIn("path: .\n", workflow)


if __name__ == "__main__":
    unittest.main()
