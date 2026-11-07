#!/usr/bin/env python3
"""Prepare website brand assets from the committed app-icon design sources.

Development tool: requires Pillow. The site itself stays static and the checked-in
outputs are validated by scripts/test_product_pages.py with hashes from the manifest.
No redraw: every image is a Lanczos downscale of the committed 1254x1254 design.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DESIGN_DIR = ROOT / "docs/ui/app-icons"
OUT_DIR = ROOT / "site/assets/brand"
SOURCES = {
    "icon-release.png": "b1fe97f597673b26eebcd0d5738538b704e8b54c75488caccd3ea7ccc20534ca",
    "icon-mock.png": "15738c03c3aa8735b201d9668b27b986ac32a8fd4d24eec434ae915b29535b1e",
}
# (output file, source file, size, rounded?) — rounded corners are masked from
# the square design so the 32px favicon keeps clean edges in browser tabs.
OUTPUTS = (
    ("icon-release-256.png", "icon-release.png", (256, 256), False),
    ("icon-mock-256.png", "icon-mock.png", (256, 256), False),
    ("favicon-32.png", "icon-release.png", (32, 32), True),
    ("apple-touch-icon-180.png", "icon-release.png", (180, 180), False),
)
SQUIRCLE_RATIO = 0.2237  # iOS-style continuous corner radius, approximate.


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def load_source(name):
    path = DESIGN_DIR / name
    data = path.read_bytes()
    actual = sha256(data)
    if actual != SOURCES[name]:
        raise SystemExit(
            f"{name} changed (sha256 {actual}); do not silently rebrand the site"
        )
    with Image.open(path) as image:
        return image.convert("RGBA"), actual


def render(source, size, rounded):
    with_alpha = source.resize(size, Image.LANCZOS)
    if not rounded:
        return with_alpha
    supersampled = size[0] * 4
    art = source.resize((supersampled, supersampled), Image.LANCZOS)
    mask = Image.new("L", (supersampled, supersampled), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, supersampled - 1, supersampled - 1),
        radius=round(supersampled * SQUIRCLE_RATIO),
        fill=255,
    )
    art.putalpha(mask)
    return art.resize(size, Image.LANCZOS)


def build():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    loaded = {name: load_source(name) for name in SOURCES}
    outputs = []
    for filename, source_name, size, rounded in OUTPUTS:
        source, source_hash = loaded[source_name]
        image = render(source, size, rounded)
        path = OUT_DIR / filename
        image.save(path, optimize=True)
        outputs.append({
            "file": filename,
            "source": source_name,
            "sourceSha256": source_hash,
            "width": size[0],
            "height": size[1],
            "roundedCorners": rounded,
            "sha256": sha256(path.read_bytes()),
        })
        print(f"Prepared {filename}: {size[0]}x{size[1]}")
    manifest = {
        "kind": "site-app-icons",
        "description": "由 docs/ui/app-icons 的 1254x1254 设计稿等比缩放生成，未重绘。",
        "sources": [
            {"file": f"docs/ui/app-icons/{name}", "sha256": loaded[name][1]}
            for name in SOURCES
        ],
        "outputs": outputs,
    }
    (OUT_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def check():
    manifest = json.loads((OUT_DIR / "manifest.json").read_text(encoding="utf-8"))
    for source in manifest["sources"]:
        path = ROOT / source["file"]
        actual = sha256(path.read_bytes())
        if actual != source["sha256"]:
            raise SystemExit(f"{source['file']} sha256 mismatch: {actual}")
    for output in manifest["outputs"]:
        path = OUT_DIR / output["file"]
        if not path.is_file():
            raise SystemExit(f"missing {output['file']}; run scripts/prepare_site_brand.py")
        if sha256(path.read_bytes()) != output["sha256"]:
            raise SystemExit(f"{output['file']} is stale; run scripts/prepare_site_brand.py")
    print("Site brand assets are up to date.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify checked-in outputs")
    args = parser.parse_args()
    check() if args.check else build()
