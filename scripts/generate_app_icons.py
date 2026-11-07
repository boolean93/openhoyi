#!/usr/bin/env python3
"""Generate the OpenHOYI adaptive launcher icon layers from the brand source art.

Source art lives in docs/ui/app-icons/ as 1254x1254 flat-background squares:

- icon-release.png -> official app icon, mobile/src/main (io.openhoyi.mobile)
- icon-mock.png    -> Mock build icon, mobile/src/mock (io.openhoyi.mobile.mock)

Each source square is mapped to the 72dp adaptive-icon viewport (the 108dp
layer canvas keeps 18dp of background bleed on every side). Only the
foreground is cut out: the wordmark, plus the "Mock" badge on the Mock art.
The flat background colors live in mobile/src/{main,mock}/res/values/
ic_mobile.xml, so no background bitmaps are produced.

Outputs (overwritten):
  mobile/src/{main,mock}/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_mobile_foreground.png
  docs/ui/app-icons/launcher-preview.png  (approximate mask preview for review)

Usage:
  python3 scripts/generate_app_icons.py            # write outputs
  python3 scripts/generate_app_icons.py --check    # fail when outputs are stale

Requires Pillow and NumPy. Deterministic: same input art -> same pixels.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "docs" / "ui" / "app-icons"
PREVIEW = ART / "launcher-preview.png"

CANVAS_DP = 108
VIEWPORT_DP = 72
DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}
# Manhattan RGB distance from the flat background: below NOISE is compression
# grain, above OPAQUE is solid ink, in between is the anti-aliased edge.
NOISE = 18
OPAQUE = 90

ICONS = {
    "release": {
        "source": ART / "icon-release.png",
        "res": ROOT / "mobile" / "src" / "main" / "res",
    },
    "mock": {
        "source": ART / "icon-mock.png",
        "res": ROOT / "mobile" / "src" / "mock" / "res",
    },
}


def background_color(source: Image.Image) -> tuple[int, int, int]:
    """Median color of a 12px border ring, i.e. the flat design background."""
    width, height = source.size
    ring = 12
    pixels = []
    for box in (
        (0, 0, width, ring),
        (0, height - ring, width, height),
        (0, 0, ring, height),
        (width - ring, 0, width, height),
    ):
        pixels.extend(source.crop(box).getdata())
    medians = []
    for channel in range(3):
        values = sorted(pixel[channel] for pixel in pixels)
        medians.append(values[len(values) // 2])
    return tuple(medians)


def cut_out(source: Image.Image) -> tuple[Image.Image, tuple[int, int, int]]:
    """Return the RGBA foreground with the flat background removed."""
    rgb = source.convert("RGB")
    background = background_color(rgb)
    diff = ImageChops.difference(rgb, Image.new("RGB", rgb.size, background))
    distance = ImageChops.add(
        ImageChops.add(diff.getchannel("R"), diff.getchannel("G")),
        diff.getchannel("B"),
    )

    def alpha(value: int) -> int:
        if value <= NOISE:
            return 0
        if value >= OPAQUE:
            return 255
        return round((value - NOISE) * 255 / (OPAQUE - NOISE))

    foreground = rgb.convert("RGBA")
    foreground.putalpha(distance.point(alpha))
    return foreground, background


def layer_for(density: float, foreground: Image.Image) -> Image.Image:
    canvas_px = round(CANVAS_DP * density)
    design_px = round(VIEWPORT_DP * density)
    offset = (canvas_px - design_px) // 2
    layer = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    scaled = foreground.resize((design_px, design_px), Image.LANCZOS)
    layer.alpha_composite(scaled, (offset, offset))
    return layer


def mask(size: int, shape: str) -> Image.Image:
    """Supersampled launcher mask; squircle approximates HyperOS-style icons."""
    steps = size * 4
    axis = np.linspace(-1.0, 1.0, steps, dtype=np.float64)
    grid_x, grid_y = np.meshgrid(axis, axis)
    if shape == "circle":
        inside = grid_x**2 + grid_y**2 <= 1.0
    else:
        inside = np.abs(grid_x) ** 4 + np.abs(grid_y) ** 4 <= 1.0
    image = Image.fromarray((inside.astype(np.uint8) * 255), "L")
    return image.resize((size, size), Image.LANCZOS)


def masked_icon(background: tuple[int, int, int], foreground: Image.Image, shape: str) -> Image.Image:
    """Compose the adaptive layers, crop the 72dp viewport, apply a launcher mask."""
    density = DENSITIES["xxxhdpi"]
    viewport = round(VIEWPORT_DP * density)
    canvas = round(CANVAS_DP * density)
    icon = Image.new("RGB", (canvas, canvas), background)
    icon.paste(foreground, (0, 0), foreground)
    inset = (canvas - viewport) // 2
    square = icon.crop((inset, inset, inset + viewport, inset + viewport))
    result = Image.new("RGBA", (viewport, viewport), (0, 0, 0, 0))
    result.paste(square, (0, 0), mask(viewport, shape))
    return result


def build_preview(layers: dict[str, Image.Image], backgrounds: dict[str, tuple[int, int, int]]) -> Image.Image:
    cell = 288
    gap = 24
    margin = 32
    caption = 42
    row_gap = 26
    width = margin * 2 + cell * 2 + gap
    height = margin + (cell + caption) * 2 + row_gap + 8
    page = Image.new("RGB", (width, height), (238, 234, 226))
    draw = ImageDraw.Draw(page)
    font = ImageFont.load_default(size=18)
    captions = {
        ("release", "circle"): "Official · circle mask",
        ("release", "squircle"): "Official · squircle mask",
        ("mock", "circle"): "Mock · circle mask",
        ("mock", "squircle"): "Mock · squircle mask",
    }
    centers = []
    for row, name in enumerate(("release", "mock")):
        top = margin + row * (cell + caption + row_gap)
        for column, shape in enumerate(("circle", "squircle")):
            left = margin + column * (cell + gap)
            icon = masked_icon(backgrounds[name], layers[name], shape)
            page.paste(icon, (left, top), icon)
            centers.append((left, top))
            draw.text(
                (left + cell // 2, top + cell + 8),
                captions[(name, shape)],
                fill=(60, 60, 60),
                anchor="ma",
                font=font,
            )
    return page


def targets() -> list[tuple[Path, float, str]]:
    plan = []
    for name, config in ICONS.items():
        for density_name, density in DENSITIES.items():
            plan.append(
                (
                    config["res"] / f"mipmap-{density_name}" / "ic_mobile_foreground.png",
                    density,
                    name,
                )
            )
    return plan


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail when committed outputs are stale")
    args = parser.parse_args()

    cutouts = {}
    backgrounds = {}
    layers = {}
    stale = []
    for name, config in ICONS.items():
        source = Image.open(config["source"])
        if source.size != (1254, 1254):
            raise SystemExit(f"{config['source']}: expected 1254x1254, got {source.size}")
        foreground, background = cut_out(source)
        cutouts[name] = foreground
        backgrounds[name] = background
        layers[name] = layer_for(DENSITIES["xxxhdpi"], foreground)

    for path, density, name in targets():
        layer = layer_for(density, cutouts[name])
        if args.check:
            if not path.is_file():
                stale.append(str(path.relative_to(ROOT)))
                continue
            current = Image.open(path).convert("RGBA")
            if current.size != layer.size or ImageChops.difference(
                current, layer
            ).getbbox() is not None:
                stale.append(str(path.relative_to(ROOT)))
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        layer.save(path, optimize=True)

    preview = build_preview(layers, backgrounds)
    if args.check:
        if not PREVIEW.is_file():
            stale.append(str(PREVIEW.relative_to(ROOT)))
        else:
            current = Image.open(PREVIEW).convert("RGB")
            if current.size != preview.size or ImageChops.difference(current, preview).getbbox() is not None:
                stale.append(str(PREVIEW.relative_to(ROOT)))
    else:
        preview.save(PREVIEW, optimize=True)

    for name, background in backgrounds.items():
        print(f"{name}: background #{background[0]:02X}{background[1]:02X}{background[2]:02X}")

    if args.check:
        if stale:
            print("Stale icon outputs:")
            for item in stale:
                print("  " + item)
            print("Run: python3 scripts/generate_app_icons.py")
            return 1
        print("Icon outputs are up to date.")
        return 0

    print(f"Wrote {len(targets())} mipmap layers and {PREVIEW.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
