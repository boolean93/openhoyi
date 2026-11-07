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
- 前景最大半径：正式版约 30.2dp；Mock（含徽标）约 36.9dp。圆形遮罩可见半径 36dp，
  Mock 徽标最外缘在纯圆遮罩下最多裁约 1dp；用户平板（HyperOS 类圆角方形遮罩）完整显示。
- 前景 PNG 是设计稿的直接 LANCZOS 缩放：与"把设计稿直接缩放到同一 108dp 画布"比较，
  像素平均差 0.044/255（正式）、0.30/255（Mock），p99 为 2~3，仅边缘像素有差别，
  不存在二次缩放模糊。
- `scripts/generate_app_icons.py` 固定了上面两张设计稿的 sha256；被替换成缩放或重编码
  副本时会直接失败，避免用错源图。
- OpenHOYI Lab（`io.openhoyi.lab`）未改。网站介绍页已改为使用这里的正式版 / Mock 设计稿：由 `scripts/prepare_site_brand.py` 生成 `site/assets/brand/`（favicon、apple-touch-icon、首屏与“两种构建”区块用图），不重绘。

## 预览

`launcher-preview.png` 分两部分：

- 上：设计稿原图（design source）、圆形遮罩与圆角方形遮罩预览，512px/格，直接由
  1254px 原图生成，屏幕放大或 zoom 查看时保持清晰。
- 下：按已提交 mipmap 图层合成的 168px（≈56dp @3×）launcher 实际比例行，
  用于判断桌面图标大小下的观感；168px 以下是 1:1，不是系统截图。

预览只用于人工复核，不是系统实际渲染截图。

## 重新生成

```sh
python3 scripts/generate_app_icons.py          # 重新生成前景 PNG 与 launcher-preview.png
python3 scripts/generate_app_icons.py --check  # 检查提交物是否与设计稿一致
```

需要 Pillow 与 NumPy；`--check` 同时校验源设计稿哈希、mipmap 图层与预览图。
