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

The source files are pinned by sha256: --check and normal runs fail if a
resized or re-encoded copy replaced the user-provided 1254x1254 art.

Outputs (overwritten):
  mobile/src/{main,mock}/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_mobile_foreground.png
  docs/ui/app-icons/launcher-preview.png

The preview is rendered twice: the 512 px mask previews come straight from the
1254x1254 art (so a retina view or zoom stays sharp), and the 168 px row is
composed from the committed mipmap layers at ~56 dp / 3x launcher scale.

Usage:
  python3 scripts/generate_app_icons.py            # write outputs
  python3 scripts/generate_app_icons.py --check    # fail when outputs are stale

Requires Pillow and NumPy. Deterministic: same input art -> same pixels.
"""
from __future__ import annotations

import argparse
import hashlib
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

# sha256 of the 1254x1254 files the user supplied on 2026-11-07. Pinned so a
# downscaled or re-encoded stand-in cannot silently replace the design source.
SOURCE_SHA256 = {
    "icon-release.png": "b1fe97f597673b26eebcd0d5738538b704e8b54c75488caccd3ea7ccc20534ca",
    "icon-mock.png": "15738c03c3aa8735b201d9668b27b986ac32a8fd4d24eec434ae915b29535b1e",
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


def verified_source(path: Path) -> Image.Image:
    """Open a pinned design file, rejecting resized or re-encoded substitutes."""
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    expected = SOURCE_SHA256[path.name]
    if digest != expected:
        raise SystemExit(
            f"{path}: sha256 {digest} does not match the pinned design art {expected}"
        )
    image = Image.open(path)
    if image.size != (1254, 1254):
        raise SystemExit(f"{path}: expected 1254x1254, got {image.size}")
    return image


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


def masked_from_source(source: Image.Image, shape: str, size: int) -> Image.Image:
    """Mask preview straight from the 1254 px art, so zooming stays sharp."""
    design = source.convert("RGB").resize((size, size), Image.LANCZOS)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(design, (0, 0), mask(size, shape))
    return result


def masked_from_layers(
    background: tuple[int, int, int],
    layer: Image.Image,
    shape: str,
    size: int,
) -> Image.Image:
    """Launcher-scale preview composed exactly like the committed adaptive layers."""
    density = DENSITIES["xxxhdpi"]
    canvas_px = round(CANVAS_DP * density)
    viewport_px = round(VIEWPORT_DP * density)
    icon = Image.new("RGB", (canvas_px, canvas_px), background)
    icon.paste(layer, (0, 0), layer)
    inset = (canvas_px - viewport_px) // 2
    square = icon.crop((inset, inset, inset + viewport_px, inset + viewport_px))
    square = square.resize((size, size), Image.LANCZOS)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(square, (0, 0), mask(size, shape))
    return result


def build_preview(
    sources: dict[str, Image.Image],
    layers: dict[str, Image.Image],
    backgrounds: dict[str, tuple[int, int, int]],
) -> Image.Image:
    page_rgb = (238, 234, 226)
    cell = 512
    gap = 24
    margin = 32
    caption = 46
    row_gap = 28
    strip_icon = 168
    strip_gap = 44
    strip_label = 40
    columns = ("source", "circle", "squircle")
    width = margin * 2 + cell * len(columns) + gap * (len(columns) - 1)
    height = (
        margin
        + 2 * (cell + caption + row_gap)
        + strip_gap
        + strip_label
        + strip_icon
        + 40
        + margin
    )
    page = Image.new("RGB", (width, height), page_rgb)
    draw = ImageDraw.Draw(page)
    heading = ImageFont.load_default(size=24)
    labels = ImageFont.load_default(size=20)
    names = {"release": "Official app", "mock": "Mock build"}
    column_labels = {
        "source": "design source",
        "circle": "circle mask",
        "squircle": "squircle mask",
    }
    for row, name in enumerate(("release", "mock")):
        top = margin + row * (cell + caption + row_gap)
        for column, shape in enumerate(columns):
            left = margin + column * (cell + gap)
            if shape == "source":
                icon = sources[name].convert("RGB").resize((cell, cell), Image.LANCZOS)
                page.paste(icon, (left, top))
            else:
                icon = masked_from_source(sources[name], shape, cell)
                page.paste(icon, (left, top), icon)
            draw.text(
                (left + cell // 2, top + cell + 10),
                f"{names[name]} · {column_labels[shape]}",
                fill=(60, 60, 60),
                anchor="ma",
                font=labels,
            )

    strip_top = margin + 2 * (cell + caption + row_gap) + strip_gap
    draw.text(
        (margin, strip_top),
        "Launcher scale · 168 px = 56 dp at 3x · composed from the committed mipmap layers",
        fill=(60, 60, 60),
        font=heading,
    )
    left = margin
    for name in ("release", "mock"):
        for shape in ("circle", "squircle"):
            icon = masked_from_layers(backgrounds[name], layers[name], shape, strip_icon)
            page.paste(icon, (left, strip_top + strip_label), icon)
            draw.text(
                (left + strip_icon // 2, strip_top + strip_label + strip_icon + 10),
                f"{names[name]} · {shape}",
                fill=(90, 90, 90),
                anchor="ma",
                font=labels,
            )
            left += strip_icon + gap
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
    sources = {}
    backgrounds = {}
    layers = {}
    stale = []
    for name, config in ICONS.items():
        source = verified_source(config["source"])
        sources[name] = source
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

    preview = build_preview(sources, layers, backgrounds)
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
