#!/usr/bin/env python3
"""Prepare original decorative geometry and a licensed, local website font subset.

Development only; the published site has no Python/runtime dependencies.
Use fonttools==4.60.1 and Brotli (WOFF2 support), e.g. the workspace-local
build/font-tools directory. Source font and OFL are already bundled by the App.
The geometry is decorative, not an image of a real device or a measurement.
"""
from pathlib import Path
import math
import shutil

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "site" / "assets"


def geometry():
    paths = []
    for i in range(17):
        rx, ry = 161 - i * 5.9, 107 - i * 3.92
        angle = math.radians(29)
        x = 173 + rx * math.cos(angle)
        top, bottom = 127 - ry * math.sin(angle), 127 + ry * math.sin(angle)
        color = "#a3452b" if i == 10 else "#526058"
        paths.append(
            f'<path d="M{x + 11:.2f} {top:.2f}H{x:.2f}'
            f'A{rx:.2f} {ry:.2f} 0 1 0 {x:.2f} {bottom:.2f}'
            f'h11" stroke="{color}"/>'
        )
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 260" '
           'fill="none" stroke-width=".65">\n'
           '<!-- Original abstract open-ring study, not a technical diagram. -->\n'
           + '\n'.join(paths) + '\n</svg>\n')
    (ASSETS / "study-ring.svg").write_text(svg, encoding="utf-8")


def font():
    from fontTools import subset
    from fontTools.ttLib import TTFont

    source = ROOT / "mobile/src/main/res/font/noto_sans_sc_variable.ttf"
    output = ASSETS / "fonts"
    output.mkdir(exist_ok=True)
    text = ''.join((ROOT / file).read_text(encoding="utf-8") for file in
                   ["site/index.html", "site/product.js"])
    options = subset.Options()
    options.hinting = False
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14, 16, 17]
    options.name_languages = [0x409]
    options.layout_features = ['*']
    item = TTFont(source)
    worker = subset.Subsetter(options=options)
    worker.populate(text=text, unicodes=range(0x20, 0x7F))
    worker.subset(item)
    names = {1: "OpenHOYI Sans", 3: "OpenHOYISans-WebsiteSubset",
             4: "OpenHOYI Sans", 6: "OpenHOYISans-WebsiteSubset",
             16: "OpenHOYI Sans"}
    for record in item["name"].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding())
    item.flavor = "woff2"
    target = output / "openhoyi-sans.woff2"
    item.save(target)
    shutil.copyfile(ROOT / "mobile/src/main/assets/licenses/NotoSansSC-OFL.txt",
                    output / "OFL.txt")
    print(f"Font: {target.relative_to(ROOT)} ({target.stat().st_size:,} bytes), "
          f"{len(item.getBestCmap())} codepoints; OFL copied unchanged")


if __name__ == "__main__":
    geometry()
    font()
