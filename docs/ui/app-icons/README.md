# App icons

用户于 2026-11-07 提供的两版 launcher icon 设计稿，原图保留在本目录，供重生成与核对。

| 文件 | 用途 | 规格 |
|---|---|---|
| `icon-release.png` | 正式 App（`io.openhoyi.mobile`，debug/release） | 1254×1254 PNG，黑底白色 "Open HOYI" |
| `icon-mock.png` | Mock 构建（`io.openhoyi.mobile.mock`） | 1254×1254 PNG，橙底字标 + 深色 "Mock" 徽标 |

原始 sha256：

- `icon-release.png`：`b1fe97f597673b26eebcd0d5738538b704e8b54c75488caccd3ea7ccc20534ca`
- `icon-mock.png`：`15738c03c3aa8735b201d9668b27b986ac32a8fd4d24eec434ae915b29535b1e`

## 实现

Android adaptive icon（`minSdk 26`，无需旧版 PNG 图标）：

- 背景：`mobile/src/{main,mock}/res/values/ic_mobile.xml` 纯色 `#000000` / `#BC3F02`。
- 前景：`mobile/src/{main,mock}/res/mipmap-*/ic_mobile_foreground.png`，由 `scripts/generate_app_icons.py`
  从设计稿按纯色背景抠出字标（Mock 版含徽标）。设计稿正方形对应 72dp 可见区域，
  108dp 图层四周各留 18dp 背景出血，内容居中。
- 前景最大半径：正式版 30.2dp；Mock（含徽标）37.0dp。圆形遮罩可见半径 36dp，
  Mock 徽标最外缘在纯圆遮罩下最多裁约 1dp；用户平板（HyperOS 类圆角方形遮罩）完整显示。
- OpenHOYI Lab（`io.openhoyi.lab`）与网站 `site/assets/mark.svg` 本次未改。

## 重新生成

```sh
python3 scripts/generate_app_icons.py          # 重新生成前景 PNG 与 launcher-preview.png
python3 scripts/generate_app_icons.py --check  # 检查提交物是否与设计稿一致
```

需要 Pillow 与 NumPy。`launcher-preview.png` 是近似遮罩预览（圆 / 超椭圆），
只用于人工复核，不是系统实际渲染截图。
