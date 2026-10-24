# M6 ScaleConnection 四配置主态及既有特殊态限定图审

## 结论与原件

四配置 × 7 目标，共 28 PNG、22 个唯一像素 SHA 已实际看图。限定接受已显示的正常候选、空态、长名、40 项滚动/重建/导航返回，以及正常三行滚达；不签真实连接、硬件能力、未显示的长名第三行全文或整 M6。

- Run `40056758145`，artifact `12546056702`；tested merge/source `084a7b18ec62e2d316b2bf0df514f0d2369f31e9`，不是 PR head `8fee86b`。
- 原 ZIP `build/task-first-evidence/ci-8fee86b-scale/artifact.zip`，SHA256 `586913d2f5889a7369a357827971d6268ff8094682b486ca207efdf350152aac`；root 已核 remote digest / 3139 CRC。
- 配置均为 `tablet-user-landscape`，light/dark × font 1.0/1.3。每配置取 `connection/window.png` 与 `special-states/{empty,long-name,main-three-rows-reachable,forty-gesture,forty-recreated,forty-navigation-return}/window.png`。
- 对照 [M6 冻结规格](../plans/2026-10-19-m6-scale-visual-migration.md) §3B 与 [候选列表规格](../superpowers/plans/2026-10-16-scale-candidate-list.md)。不重复既有 ScaleActivity 短窗六帧。
- 实际打开原始 2560×1600 PNG；工具显示缩放为 1996×1248，未声称 1:1 像素放大或逐字形来源认证。字体/尺寸/触点另核同目标 typography；没有以 JSON PASS 代替图审。

## G1–G4

| 范围 | G1 布局 | G2 字体身份与字形 | G3 字号/行盒 | G4 颜色 |
|---|---|---|---|---|
| 四主态 | 状态与操作分组、列表独立滚动、底部能力说明互不覆盖；1.0 三行完整，1.3 第三行初态部分在视口外，已由对应三行滚达图补足正常行 | 固定 Noto Sans SC 2.004-H2 资产 SHA 与实际 TextView/Paint 角色核对（见补核）；实看中文、地址、dBm 无异常替代或破损；不扩大到未显示字形 | 保留标题/章节/正文/辅助自然层级；1.0 角色实值 54/45/36/31.5px，1.3 按系统非线性放大，不以简单乘 1.3 判错；连接按钮高 108px = 48dp | 浅深页底/卡片/边线层级一致；管理动作青色、扫描/连接禁用灰；只读警示独立金棕色，不靠颜色代替全文 |
| 四空态 | “发现0个候选”与空列表说明完整，留白合理，底部能力说明不移入列表 | 中文说明可读 | 空说明自然字号/行盒，无缩字填满 | 空态仍保留原卡片与主辅色，不伪装故障 |
| 四长名 | 1.0 三长名及元数据完整；1.3 前两长名、第二项实验限制完整，第三行下部位于独立滚动区外；仅接受已显示范围 | 长中文/数字与普通名称风格一致，未截成省略号 | 长名未缩字；1.3 第三项地址/RSSI/按钮完整可见未由这张图证明 | 禁用按钮/警示层级保持，不把只读提示误做可用连接 |
| 40项手势/重建/返回（12目标） | 相同滚动位置的中间候选保留；顶部/底部被视口切开的相邻行属于滚动快照，不签所有40项同时可见；固定标题/说明不随列表漂移，无Toast遮挡 | 当前显示行的名称、地址/RSSI可辨 | 中间完整行文本与48dp按钮保持；不外推至未显示候选 | 重建与返回没有主题闪回；细滚动条差别不等于行布局改变 |
| 三行滚达（4目标） | 1.0 与主图像素相同；1.3 正常第三项名称/地址/RSSI/连接按钮完整，第一项移出上沿符合独立滚动设计 | 正常第三行文字完整 | 未以压缩字号换取三行首屏全显 | 与主态同主题 |

## 资格、恢复与边界（独立于 G 编号）

逐目标直接从 ZIP 读取 proof/geometry，校验各自选取 SHA；28 个 PNG SHA 与 proof.imageSha256 相同，protocol 2、timeout 5000ms、accepted attempt 四差异均 0，comparison_complete 为 708–3658ms。并核四份特殊态 manifest 的 `completed/exactRecreateAndReturnScroll/noScanNoConnect` 均 true。只读 Mock 禁用按钮不证明真实扫描、连接或 ACK。

选取索引 `build/task-first-evidence/ci-8fee86b-scale/connection-selected/index.json` 保存原 member、SHA、大小与 storage。大 geometry/proof 只保留在原 ZIP、按需读取，未全包解压。逐目标核验 `review-audit.json` SHA256 `681c74bd65da8f78922e8b86aa80aceb220c7de4ac087323b23c1d85863108dd`。同 SHA 复用像素审查但逐目标资格独立检查；四份 forty-recreated 与相应 forty-gesture 同像素；四份 navigation-return 的 SHA 均不同，已分别实际看图，两个 1.0 三行滚达与主态同像素。

仍缺：1.3 长名第三行完整滚达视觉证据；实际扫描中/连接中/已选/重连失败/不支持与信息 Dialog（本次选取没有这些目标，不能由 synthetic READY 推断）。ScaleActivity 全高与其余特殊态本轮未看，原短窗结论不变。此次不是整页全状态、硬件或整个 run 的视觉签署。

## 已看目标与 PNG SHA256

下表路径均相对原 ZIP 的 source 前缀；四配置前缀 `tablet-user-landscape-` 省略显示。SHA 是原始 PNG，不是工具缩放图。

| 配置/目标 | SHA256 |
|---|---|
| `dark-font-1.0/empty` | `4d3a914daa7552fb2256378da51a3e8396ad7e9f5ee41ed4f74dfce0fe5d6bef` |
| `dark-font-1.0/forty-gesture` | `e5c8eb6fbce512002b7d5a0b394cb3250faa494b300da663ca6e4dbf49693018` |
| `dark-font-1.0/forty-navigation-return` | `28cc1fd32e56be1e07fa96a813216f9016aeaa9dec541096b53dd5f2dcf030ce` |
| `dark-font-1.0/forty-recreated` | `e5c8eb6fbce512002b7d5a0b394cb3250faa494b300da663ca6e4dbf49693018` |
| `dark-font-1.0/long-name` | `5df33553c9d153267bdd4c792ed2de20669b0f61db45f6721ec9910740ab9412` |
| `dark-font-1.0/main-three-rows-reachable` | `5e4b090241e3d6a7707f8752ccde6243fb50f8cecdc681e822b37d3d9cb571d3` |
| `dark-font-1.0/main` | `5e4b090241e3d6a7707f8752ccde6243fb50f8cecdc681e822b37d3d9cb571d3` |
| `dark-font-1.3/empty` | `6a5f32a782e54c068a31a84cab0a2bbc7552cc3e16f9b42debc59d780a1b3737` |
| `dark-font-1.3/forty-gesture` | `9ccf0afd4fe1a3b0e0ca739a6927fab19a7a0cd7894db7565143904eaf475c21` |
| `dark-font-1.3/forty-navigation-return` | `20b06dcca51afe40b2a4f25145c269b8654b6be3b96ac35d1fde0024eda76779` |
| `dark-font-1.3/forty-recreated` | `9ccf0afd4fe1a3b0e0ca739a6927fab19a7a0cd7894db7565143904eaf475c21` |
| `dark-font-1.3/long-name` | `fbbdd6eadeec0cb3740065a445591287c1bf8147f5b048bda24287ee8eaad583` |
| `dark-font-1.3/main-three-rows-reachable` | `9e7d0f8645aef0979e3f6609b9e3bfad8f42a38a85c2b03655d4774fe2381a6e` |
| `dark-font-1.3/main` | `9b96e07cec758cec365b2d7d85f725fce3576b9031fa48a9d81901ae18b63483` |
| `light-font-1.0/empty` | `961071363cf2149e0daa1efc2a8b3a2b6d0410a7968ef58414c81cdfc53ff7a4` |
| `light-font-1.0/forty-gesture` | `54a2a075ba5a151346ed8045c9961af4acc777bb969200543c513f58d8d8cd68` |
| `light-font-1.0/forty-navigation-return` | `6ed705f9d69e5ce873915d73011951c03f69d7e30e0852fba92430da612bad31` |
| `light-font-1.0/forty-recreated` | `54a2a075ba5a151346ed8045c9961af4acc777bb969200543c513f58d8d8cd68` |
| `light-font-1.0/long-name` | `a99ce262faac54bae8f5f9ce36052077030f38f6be850a5ab55aef725e557649` |
| `light-font-1.0/main-three-rows-reachable` | `eca7efa9f0d87063ba252148765cf32a50eae30cda1bc06639b4a99cd0ad9d38` |
| `light-font-1.0/main` | `eca7efa9f0d87063ba252148765cf32a50eae30cda1bc06639b4a99cd0ad9d38` |
| `light-font-1.3/empty` | `1ca8edfe0041134b4bb7f1e554a05db5cbdec9707f2cb9d61edbc2976a850c5a` |
| `light-font-1.3/forty-gesture` | `e6d912db80f116f4296a444b86cfb6be944f944d3af61047a58a211e4912ecd7` |
| `light-font-1.3/forty-navigation-return` | `8ab322f6f15e2bad4b7657e693f17d1ef3e7c2476b18e6cd0a14a03fee917909` |
| `light-font-1.3/forty-recreated` | `e6d912db80f116f4296a444b86cfb6be944f944d3af61047a58a211e4912ecd7` |
| `light-font-1.3/long-name` | `f97c85bda97a49671a9d27b85ff91977b847bf60db65cebc060ca36e82593277` |
| `light-font-1.3/main-three-rows-reachable` | `440177e438ab4d0558eb9f13faf41b53e2ee7af2e2fe7b78629522cd59170f7e` |
| `light-font-1.3/main` | `4cf52b71ab58fc0aff24c396610d5134514e27d8821471f0b2120a3394849dcd` |

## 原尺寸小字补核（同范围，不增加状态）

四主图及四长名图均补看无重采样的原像素裁剪 `[250,600,2200,1465]`，1950×865；`view_image(detail: original)` 输出保持这一尺寸，无缩放通知。8 个 PNG 合计 1,081,311B，保存在选取目录 `native-crops/`，其 `index.json` 记录原 PNG SHA、裁剪矩形和无重采样操作。该裁剪用于设备名称、地址、RSSI、实验只读限制及能力说明的小字检查，整体布局仍以此前完整原图为准；裁剪边缘不当作产品裁切。逐图观察中文细笔画、拉丁地址冒号/数字、负号与 dBm 均清楚，未见缺笔、乱码或字形替代；长名两档字号自然，不用压缩字宽冒充全文。原 1.3 第三长名行未显示部分依然不签。

四份主态 manifest 的实际 `fontSha256` 均为 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`，与冻结 M6 §2 的 Noto Sans SC 2.004-H2 资产一致。角色证据来自每张配套 `ScaleConnectionActivity-typography.json` 的 `textSizePx/weight/lineHeightPx` 及 `actualLayout`（`existing_TextView_layout_and_getLineBounds_APIs`），不是从图片猜 sp 或引用资源常量。

| 实际角色 | font 1.0：字号px/字重/行高px | font 1.3：字号px/字重/行高px |
|---|---|---|
| 候选名称 | 36 / 500 / 54 | 45.45 / 500 / 59 |
| 地址、RSSI、限制、能力说明 | 31.5 / 400 / 49 | 42.3 / 400 / 56 |

浅深配置相同；实际 `includeFontPadding=false`、`textScaleX=1`、`letterSpacingEm=0`。字体资产 SHA、原生角色测量与本次原像素字形观察合并支持 G2/G3 的已显示范围；不声称观察到了 renderer 内部逐 glyph fallback 或与生成式设计 PNG 二进制字体相同。重新计数仍为 28 目标、22 唯一 PNG SHA：4 对 gesture/recreated 与2对 1.0 main/reachable 重复，其余各自独立。
