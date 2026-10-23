# M7 普通三页主态四配置视觉复核

## 结论与范围

实际打开普通 Curve/History/Detail × light/dark × font1.0/1.3 共 **12张原PNG**。旧本source审查只覆盖长名fixture、短窗和轴字滚达，并未审这12主态，故无重复签收。

Curve四档已见参数分组、常显只读提示和文字层级可有限接受；Detail已见图与三组来源说明层级可有限接受。**History四档时间轴存在真实层级裁切，且四档有Toast遮底；Detail另两档Toast遮时间轴，不能签所有主态全文/整图通过。** 本结论不覆盖support全文/Dialog/全部动作及滚达，也不是整个M7或24页通过。

## 来源与核验

仅 `ci-8b52379-legacy-pr` 原ZIP：run39893290655，head `8b5237989eaae99181ff137b8873896eb96bda7c`，实际merge/source `44d34c52a3f3b18537f59cb92607bcc7c6252d09`；ZIP SHA256 `bbbf0013a3872dcc75eef9d586743fbc24c7ca3a6fbf575e9b855f03fccb331d`，160189213B。沿用全CRC记录，不重下/全展开。索引 `acceptance-index.json` 的mainFiles与otherMainPages对应普通fixture。

独立重算12组已提PNG/geometry/字体等文件SHA，与索引一致；从ZIP只读取12个frame proof，逐项重算SHA并核 accepted before.target==geometry==after.target。完整protocol像素运算未重跑。字体JSON是对应manifest引用的实际记录，不能因与同目录geometry配对就把未嵌入target的全部字体字段额外宣称同帧。PNG实际2560×1600，查看工具缩放显示，精确矩形取原geometry。

对照 [旧曲线参数分组](../superpowers/plans/2026-10-18-legacy-curve-detail-grid.md)、[旧历史只读](../superpowers/plans/2026-10-16-legacy-history-layout.md)、[详情说明分组](../superpowers/plans/2026-10-18-legacy-detail-sections.md)、[双轴](../superpowers/plans/2026-10-16-legacy-chart-axes.md) 和固定字体合同，不套用M5 History的1.3预算条款给M7自动豁免。

## G1–G4 实际观察

| 项 | Curve | History | Detail |
|---|---|---|---|
| G1 结构/间距 | 四档左选中列表、右标题/元信息/2×2参数、常显警示与详情入口层级清楚；1.3自然增高，无数字与单位碰撞。 | 来源切换、日期列表与预览清楚；下方固定警示/动作与图viewport存在实际裁切，不能仅凭构图整齐签图完整。 | 主图与右支持栏分开，名称/元信息/只读徽标在普通名下均完整；右栏按来源、缺失、限制分组。1.3限制正文超首屏，需要实际滚达。 |
| G2 实际字体 | 固定候选资产SHA与索引一致；主参数1.0为54px/700、1.3为59.399998px/700；名称54/59.399998px、500，列表36/45.45px、500；实图无明显缺字/假粗。 | 同候选层级；日期/时长/只读文字可辨，未把图中Toast系统字体混入产品字体验收。 | 分组标题实际45/53.100002px、500；只读徽标31.5/42.3px、400；中文、wFlow/wTrend、bar/ml/s可辨。 |
| G3 字号/图表/全文 | 四参数值93°C/60ml/36.0g/4与标题分层，常显“仅供浏览，不发送到咖啡机”可读。未打开完整说明Dialog。 | 压力实线与机器水流虚线、双单位可辨；**1.0 chart框底1299而visible底1249，裁50px；1.3框底1375而visible底1204，裁171px**。两主题一致，时间轴/1.3部分下轴不能签完整。这是层级裁切，与Toast是两个独立限制。 | light1.0及dark1.3主图轴字/0..30s全可见；light1.3与dark1.0图下时间刻度被Toast覆盖。1.0右侧限制说明当前可读；1.3仅上部可见，不签未见全文。 |
| G4 颜色/线型 | 浅色白卡/浅底、深色暗卡边线/辅助字层级可分，青色选中/动作与琥珀只读警示明确。 | 浅蓝压力/青虚线水流、暗色浅蓝/浅青可分，不把ml/s改释为累计水或秤流速。 | 图例和曲线线型一致、网格弱于轨迹；缺失温度/累计注水/原生秤/原生结束确认四项明确“未提供”，不是0。 |

字体资产SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da` 是候选关联，不是平台逐glyph身份认证。G4只为所见区域可读性，不宣称全屏量化对比度。

## 不可忽略的真实Toast遮挡

History四档底部都出现“导入失败：Unsupported curves format；原有数据未改变”系统Toast，覆盖部分只读说明/列表区域。Detail light1.3、dark1.0出现“旧版历史新增1条，共42条”，覆盖图的部分时间刻度；light1.0、dark1.3未见该Toast。**机械四零差和geometry稳定并不证明没有稳定的其它窗口遮挡**，原hierarchy矩形完整也不能恢复被Toast盖住的像素。本次保留原PNG，不裁Toast、不修图、不把未遮挡的其它配置搬来补签。

遮挡位置与不可签字段（位置为原图视觉定位，不冒称Toast窗口已测量bounds）：

| 原主态 | Toast位置/内容 | 本图不能签的G3区域 |
|---|---|---|
| history/light1.0、dark1.0 | 屏幕底部偏左至中央，横跨列表末行与右卡左半；“导入失败…原有数据未改变” | 底部只读说明左段及列表该行被盖文字；时间轴另因viewport裁切未完整，不将其全部归因Toast |
| history/light1.3、dark1.3 | 底部中央更宽的同一失败Toast，盖住右卡只读警示部分两行 | 警示完整全文/文字下沿；时间轴和下轴仍有独立层级裁切 |
| detail/light1.3 | 主图下边中央偏右；“旧版历史新增1条，共42条” | 15.0s附近及其右侧时间刻度/底部留白，不能据geometry宣称原PNG中完整可读 |
| detail/dark1.0 | 主图下边偏右的同一新增Toast | 22.5s附近及最右时间刻度区域，不能借light1.0无Toast图替此配置签字 |

下一最小证据工作：先在同ZIP查同source、同普通记录/窗口/配置的无Toast accepted恢复/目标帧；存在则实际审，不重采。若没有，再独立设计原测试跨页残留Toast的取证时序方案，不改产品Toast文案/持续时间，不加固定sleep、遮罩或放宽5秒。History图层级裁切需另查已有本页完整图滚达及设计约束，不能借Toast原因将真实裁切忽略，也不能在未查前猜改产品。

## 实际查看清单

路径相对 `build/task-first-evidence/ci-8b52379-legacy-pr/`，均为普通主态而非short/normal-restored长名fixture。

| 配置 | 页面 | PNG路径 | SHA256 |
|---|---|---|---|
| tablet-user-landscape-light-font-1.0 | curve | `selected/tablet-user-landscape-light-font-1.0/curve/window.png` | `49e9a1c0005ca66123e357b5725f2b90a5667feac27aef193e6bca415b1233c2` |
| tablet-user-landscape-light-font-1.0 | history | `selected/tablet-user-landscape-light-font-1.0/history/window.png` | `7dca5412431a4d22bb2642d93c4ba4b51c595b47acff1ad9a4a9c70eb92153db` |
| tablet-user-landscape-light-font-1.0 | detail | `selected/tablet-user-landscape-light-font-1.0/detail/window.png` | `7b2f097c7c0f9f2c789378d0345439a42db8f5282ffd6a2907f3a178618ca594` |
| tablet-user-landscape-light-font-1.3 | curve | `selected/tablet-user-landscape-light-font-1.3/curve/window.png` | `13d43f05637dacdff450ede9260c6816915f2875981b2da3596c42ead42bf73a` |
| tablet-user-landscape-light-font-1.3 | history | `selected/tablet-user-landscape-light-font-1.3/history/window.png` | `fdc99a35713a082d94bad38e4d858ce476d43f868986870e52b6d3be7b0e4705` |
| tablet-user-landscape-light-font-1.3 | detail | `selected/tablet-user-landscape-light-font-1.3/detail/window.png` | `6e2a1477c7b03244396e548e6dc964ff49384836f2dfa55b3afbeb7828b6e7d9` |
| tablet-user-landscape-dark-font-1.0 | curve | `selected/tablet-user-landscape-dark-font-1.0/curve/window.png` | `4503a7cfc6530e67faf7104474215475314335c33875e34c4ab3be7ce5379bf7` |
| tablet-user-landscape-dark-font-1.0 | history | `selected/tablet-user-landscape-dark-font-1.0/history/window.png` | `ea51370946926bcdede70928e11300bf3c6429a0d1475f64ba01d55b3ec18ec2` |
| tablet-user-landscape-dark-font-1.0 | detail | `selected/tablet-user-landscape-dark-font-1.0/detail/window.png` | `132d88d165451969cf84407cf5429c659b4a7d8656fdd12c81be3f2e72c090c0` |
| tablet-user-landscape-dark-font-1.3 | curve | `selected/tablet-user-landscape-dark-font-1.3/curve/window.png` | `e41e265d413b01f9932811881246e4efb8bd970e6f6b303b7dc8138abf9510f7` |
| tablet-user-landscape-dark-font-1.3 | history | `selected/tablet-user-landscape-dark-font-1.3/history/window.png` | `93f1c27abc45200c8eedebeb6e8852df64a6463a72d54337ab772079b30114fb` |
| tablet-user-landscape-dark-font-1.3 | detail | `selected/tablet-user-landscape-dark-font-1.3/detail/window.png` | `63116e91702666cd24d69e2bf83540c3e4bfb9cb2738bd1694a9aa61b6c89da3` |

## 后续边界

独立核 `git diff --name-only 8b52379..588855c -- mobile/src/main mobile/src/mock`：仅CurveActivity、MachineSettingsActivity、ScaleSettingsActivity，未涉及Legacy生产或共享字体/style。此源码连续性只作辅助，绝不把旧merge原件写成 `588855c96fdceec79bf87f0fd5950f33fa6aff1c` 实际运行证明，也不签新badge测试图。

先前短窗96dp/80轴字逐项/长名恢复有限结论独立保留；不能替代这里普通History被裁时间轴或Toast遮挡区域。当前未审全部支持区/Dialog、全部交互与恢复、其他语言/硬件。只新增本文，未改产品/测试/计划，未构建、推送或重跑CI。
