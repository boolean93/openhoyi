#!/usr/bin/env python3
"""Prepare readable continuous feature crops; development only, requires Pillow.

The site uses system fonts. Original screenshots and the original nine image
files are never overwritten. No redraw, stitching, rescaling or device access.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SCREENS = ROOT / "site/assets/screens"
PREVIEWS = (
    ("curve-preview.png", "home.png", (250, 1025, 715, 1455),
     "1dbe92d655d5ff0e5ca28e0eda8e1d8053da63abde3121b15756d161bbf5110f"),
    ("history-preview.png", "history.png", (275, 335, 875, 620),
     "b26ad4de1d0e30b149d5a5a302c019f73bef9fe88a5bc9af5afabb797d1f1f24"),
)


def main():
    records = []
    for filename, original_name, crop, expected_hash in PREVIEWS:
        source = SCREENS / "originals" / original_name
        source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
        if source_hash != expected_hash:
            raise ValueError("Original screenshot changed; do not reuse crop coordinates")
        target = SCREENS / filename
        with Image.open(source) as original:
            detail = original.crop(crop)
            detail.save(target, optimize=True)
            width, height = detail.size
        records.append({
            "source": "originals/" + original_name, "sourceSha256": source_hash,
            "file": filename, "cropPx": list(crop), "width": width, "height": height,
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        })
        print(f"Prepared {filename}: {width}×{height}; original unchanged")
    metadata = {"kind": "continuous-tablet-details", "description":
                "平板Mock模拟演示，非手机截图；每张均为单一原图连续矩形裁切，无重绘、拼接或缩放。",
                "previews": records}
    (SCREENS / "feature-previews.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
