# M6 ScaleActivity 全高与既有特殊态限定图审

审查者：Astra，2026-10-24。**以下四配置、142 个既有目标（93 种 PNG SHA）限定接受；不是整 M6、真实秤或全部状态验收。** 本次不重复已签短窗及 Connection，不改变截图合同。

## 原件与范围

- 成功 run `40056758145`，artifact `12546056702`；tested merge/source `084a7b18ec62e2d316b2bf0df514f0d2369f31e9`，head `8fee86b4e3f004ed3be5811d22a5bb49c2f87b98`。不是当前 HEAD 的重新运行证明。Root 已核从该 head 至 `0396720` 的 main 变化仅 LegacyHistory/MachineSettings，本页及共享 main 未变。
- ZIP：`build/task-first-evidence/ci-8fee86b-scale/artifact.zip`；SHA256 `586913d2f5889a7369a357827971d6268ff8094682b486ca207efdf350152aac`。沿用 Root 的 remote digest/3139 CRC 核证，不重复全包解压。
- 配置：`tablet-user-landscape-{light,dark}-font-{1.0,1.3}`，每张原 PNG 2560×1600。仅选 source 下 `scale/`、排除全部 `/short-height` 路径，未使用 snapshot-scope 副本。
- 完整目标/member/PNG SHA：`ci-8fee86b-scale/scale-selected/index.json`，SHA `4aa7e08bbf2fe857a52b592864f93019febf5b46945c87a8836c56f1a48251b6`。142 张文件共 34,579,551 B；按 SHA 去重实看全部 93 张，不将相同像素的复用当成缺少目标核证。
- 实测摘要：同目录 `review-audit.json`，SHA `63395bfa7040851a86bc50e78a3ca80b66aca197041d0375df9f591630f647e5`，保留各目标 proof SHA、四差异、耗时及四主态实际角色/布局/颜色、原 disclosure/特殊态记录。各目标 PNG、geometry、proof 均分别匹配所属 manifest SHA；142 份 proof 均 accepted、timeout=5000、S1_W1/S1_W2/S1_S2/D1_D2_app=0，elapsed 831–3188ms。资格通过不是视觉结论。

依据：[M6 冻结计划 §3A](../plans/2026-10-19-m6-scale-visual-migration.md)、[严格视觉基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、原生样本冻结及其已批准响应式修订。

### 四主图定位

| 配置 | 目标 / 唯一像素数 | 主 `window.png` SHA256 |
|---|---:|---|
| dark 1.0 | 35 / 24 | `7189b79165acb8bba7584dc5e50137b0a2f3dd627af24625e91dd07db22d662e` |
| dark 1.3 | 36 / 23 | `cbc10ece726928d274ff8c9f56a232211684141b51404f8869c62ba85c10656c` |
| light 1.0 | 35 / 23 | `11ee56423e0cc4dc2bdb5abe4c7f27d6e3b78349b43001c6f3569c3407becb68` |
| light 1.3 | 36 / 23 | `dfa8115263a8ebc7fa9fcaabc300cd20a9305718da66559ba1009efd2c325c20` |

## 实际看图范围与结论

以下均覆盖浅/深 × 1.0/1.3；路径相对于各配置 `scale/`。重复 SHA 的目标仍逐项核原 proof/geometry 哈希。

| 已看原目标组 | G1 布局、间距与完整可达 | G2 字体、字形 | G3 字号、行盒 | G4 实际颜色 |
|---|---|---|---|---|
| `window`、`support-before`、`after-scroll-1..6`、`scroll-restored` | 主读数与右工具主次明确，主区不随右栏滚动；六原目标分别完整，恢复原 scroll=0。1.0 首屏有完整真趋势；1.3 预算不足退为右栏趋势入口，入口需滚达，不冒称首屏已露出 | 所见中文、数字、g、App 及操作角色层级接受；主数/计时700、操作500、说明400 | 全文自然换行；主数与单位基线相等；右栏原48dp动作完整；未用缩字换取同屏 | 浅深背景、正文/辅助、teal 操作、禁用去皮层级清楚，无 Toast 覆盖 |
| `trend-main-data` | 1.3 浅深实际 Dialog 内真趋势、完整说明及取消可见；1.0 内嵌图所见范围同样已核 | 原尺寸轴字 g、19.0/8.5/-2.0、-10/-5/0s 清晰 | 坐标/端点/轴字不裁；不将空白预览当有数据图 | 真曲线与淡填充、网格、浅深 Dialog 表面清楚 |
| `one-decimal`、`negative-offline` 及其 `disclosure/*`、`disclosure-restored` | 18.0 与 -1.25 保持读数主次；主区能力声明完整。右栏长声明按原滚达目标全文可见并恢复，1.3 初态/恢复末尾在视口外不冒称同屏全文 | Felicita、ACAIA / DIFLUID 与中文自然混排，所见无缺字 | 右侧长声明1.0五行、1.3六行；无省略替代；原尺寸核查行间距与字重 | 辅助能力说明不伪装为成功连接；禁用动作与正文有区别 |
| `mock-capture-disabled`、其 `disclosure/0..5`、`disclosure-restored` | “用当前豆重作为本次粉量”固定底部，去皮为次级且禁用；右栏滚动不覆盖底部动作。确认豆子/至少1秒/0.1g说明完整可读，恢复原位置 | 主说明/按钮字形与角色接受 | 取量说明1.0三行、1.3四行；视口边缘其它未到达元素可裁，不等于目标全文裁切 | disabled 背景与文字清晰区分，未把 Mock 取量画成可执行 |
| `battery-capability-82`、`battery-unavailable` | 82% 与“当前协议未提供电量”分别正确呈现，不以0%代未知 | 值/标签层级接受 | 长未知值及状态自然完整换行 | 无虚假成功色 |
| `disconnected`、`no-service`、`ready-null`、`expired-10001`、`stale-1501`、`weight-capability-false` | 当前无有效值显示“—”，未就绪/过期说明可读；陈旧历史与当前有效读数分开 | 所见状态文字与符号清晰 | 固定去皮/状态无覆盖；1.0保留图的轴字完整；1.3按入口变体，不强求内嵌 | 旧历史线不被误画为当前可用；状态/正文层次接受 |
| `gap-and-evidence-change`、`paused-recreated` | 有间断的数据保持分段、不跨缺口连线；重建后原00:24.6所见读数保留 | 数字/轴字/状态接受 | 图与计时完整，不由一张图独立证明全部计时语义 | 浅深真线、空段、填充接受 |

机械 business/disclosure 原值保留在审计摘要：四配置主工具 `restoredStateEqual=true`，取量与 negative-offline 两组亦 completed/restoredStateEqual=true、originalScroll=[0,0]。画面实际分别看过完整目标及 restored。截图中的平台滚动指示属于原图，没有隐藏或遮罩。

## 实际字体、间距与背景核对

整体构图先看全图；工具把2560宽整图缩为1996宽，**该缩图不作为小字最终依据**。另实看18张无重采样 crop：四主图各左/右两块、两组长说明各四配置、1.3浅深趋势 Dialog。裁图坐标与原 PNG SHA 在 `scale-selected/native-crops/index.json`（SHA `974858781e11fcd87d16d55673427e5964a951863efbbf6c20c373d15528c700`）；共1,611,773 B，最长1564px，`view_image detail=original` 显示未缩放。裁图边缘本身不是原页面裁切，完整性以全图/geometry共同核对。

四份 manifest 字体 SHA 均为冻结 Noto Sans SC 2.004-H2 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。字号/字重来自对应 `ScaleActivity-typography.json` 的当前 TextView/Paint，行数据来源 `existing_TextView_layout_and_getLineBounds_APIs`，不是资源声明。Canvas 轴字另由 geometry 中实际 draw operations 及原像素核对，不用 TextView 报告的 canvas-not-collected 字段代替。

| 实际角色 | 1.0 px / weight / lineHeightPx | 1.3 px / weight / lineHeightPx |
|---|---|---|
| 重量数值 | 144 / 700 / 216 | 144 / 700 / 216 |
| g 单位 | 49.5 / 400 / 76 | 56.25 / 400 / 76 |
| freshness、去皮说明 | 31.5 / 400 / 49 | 42.3 / 400 / 56 |
| 去皮、计时操作、连接、设置 | 36 / 500 / 54 | 45.45 / 500 / 59 |
| 电量与设备状态值 | 36 / 400 / 54 | 45.45 / 400 / 59 |
| 计时数值 | 90 / 700 / 135 | 90 / 700 / 135 |

所核角色 includeFontPadding=false、letterSpacing=0、textScaleX=1；行盒是实际 Layout，不冒称 renderer 内部 glyph provenance。数值采用既定独立大数角色，不把 Android 1.3 的实测值简单乘1.3。四主图重量/单位 screen baseline：1.0为556/556、1.3为821/821。完整原尺寸字形未见挤压、假粗或重叠。

density=2.25；左右主体宽1562/692px，栏间54px=24dp；去皮540×126px=240×56dp，计时/连接/设置动作108px=48dp；1.0去皮到状态18px=8dp，timer到device54px=24dp。1.0趋势预算574px、需求526px，内嵌；1.3可用521px、需求658px，使用批准入口变体，未牺牲自然字号硬塞图。1.0趋势组间36px=16dp，实际轴完整可见。

实际 PNG 主体像素统计与视觉一致：浅背景(249,250,249)、正文(16,24,32)、辅助(101,113,131)、操作(8,122,120)、禁用底(236,239,241)；深背景(17,25,26)、正文(237,243,242)、辅助(169,185,184)、操作(131,214,208)、禁用底(38,50,52)。曲线 drawOperation 为2dp实线、fill alphaByte31（配置0.12）；实屏浅填充(220,235,234)、深填充(31,48,48)，不是拿透明ARGB直接当屏幕色。Dialog 原生标题/正文及表面按其原窗口实际图接受，不强称产品TextView同角色。

## 关闭与保留

本次关闭该 source 的四配置**全高主态、右工具六目标滚达/恢复、上述十三个既有特殊态及长说明披露、全高1.3趋势Dialog**未审边界。旧短窗51px问题仍沿用既有[短窗六帧报告](2026-10-23-m6-short-window-three-profiles-review.md)，不倒灌本次原件。

未覆盖：本次排除的短窗披露序列（旧报告只覆盖其明确六帧）、未列出的信息Dialog/实际硬件交互及未采真实能力/ACK状态。Mock场景的视觉与原业务记录不替代真实秤验收；成功run及scope精确恢复也不扩大视觉覆盖。没有新增场景、阈值或实现修改。
