#!/usr/bin/env python3
"""Export the pending visual matrix only. No adb, rendering, installation or acceptance."""
import json


def cases():
    result = []
    def add(name, width, height, density, theme, font):
        result.append(dict(id=f"{name}-{theme}-{font}",
                           contentPx=[width, height], densityDpi=density,
                           theme=theme, appFontSize=font, systemFontScale=1.0,
                           status="NOT_RUN", screenshot=None))
    for theme in ("light", "dark"):
        for font in ("small", "medium", "large"):
            add("tablet-80x47", 2560, 1504, 360, theme, font)
    for args in (
        ("landscape", 1600, 900, 240, "dark", "large"),
        ("tablet-portrait", 1504, 2560, 360, "light", "medium"),
        ("phone-portrait", 1080, 2160, 480, "dark", "large"),
        ("square-window", 900, 900, 320, "light", "large"),
        ("short-window", 1280, 640, 320, "dark", "medium"),
    ):
        add(*args)
    return result


if __name__ == "__main__":
    print(json.dumps(dict(schema="HISTORY_DETAIL_C_VISUAL_PLAN_V1",
                         mode="PLAN_ONLY", renderingExecuted=False,
                         dimensionMeaning="Activity content excluding system insets; not wm size",
                         cases=cases()), ensure_ascii=False, indent=2))
