# 三页原生候选样张成组审查 · 2026-10-18

## 结论与范围

**本批严格视觉验收 FAIL；不是产品验收，也不是 24 页通过。** 已按浅色 1.0 准备/分享/库存、深色 1.0、浅色 1.3、深色 1.3 的顺序看完 12 张真实整窗 PNG，并交叉读取原稿、锚点、字体、Layout、Canvas、颜色和可视框。

自动结果 `PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE` 不能覆盖本次发现：单位基线偏差、图表文字相交、准备说明裁切、标准库存第二流水可视高度不足，以及源稿信息层次/状态色/分隔/图标遗漏。初版锚点没有覆盖若干源稿细节，属于设计补充不完整，不归咎执行者自行偏离。

本文件只审三页隔离 Mock 样张。其余 21 页、真实产品同名页、硬件、短高窗口和 IME 均未因此获批。设计状态仍 `proposed`，`productAcceptance=false`。来源分支 `feature/task-first-ui`，PR #4 → `develop`；不发版、不安装、不操作设备。本审查不提交或推送；下一批由 root 汇总，通过同一 PR/CI 评审，回退限定 Mock 样张与取证变更，不改真实产品主题或数据。

## 1. 身份与证据链

证据根目录（下文 `E/`）：

`build/task-first-evidence/ci-79868c7-frame/task-frame-tablet-user-landscape/task-first-evidence/strict-native-samples/c9372c3a1cb7a4bb58405e6dc0a5205d835439ad/`

- CI `39122256050`，PR head `79868c766bc408e03d72098801f7f537b8eb4305`，实际测试 merge/source `c9372c3a1cb7a4bb58405e6dc0a5205d835439ad`。两者关联，不误判身份不一致。
- 12 份 `sourceDirty=false`；三个 fixture 输入在四个 profile 一致。12 个 PNG 和 tokens 文件分别与 manifest 的 `windowPngSha256`/`tokensSha256` 相符。
- manifest 报告 APK SHA `a9f148b8387e421dc47699779dc28f44139f40d4465dff60561c30496d970e2a`；此 artifact 不含 APK，未独立重算二进制，不能称源码→二进制认证。
- 全部 PNG 2560×1600；API 34、density 360/2.25、zh-Hans，窗口 origin `[0,74]`、app `[2560,1391]` px = `[1137.7778,618.2222]` dp；insets `[0,74,0,135]`，无 IME。
- 固定字体 SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da` 与真实资产一致。Noto Sans SC 2.004-H2 是可复现候选字体，不是从 ImageGen 识别出的原字体。

| E/originals 原稿 | 实际尺寸 px | 实际 SHA-256 |
|---|---:|---|
| brew-preparation-v2.png | 1586×992 | 04c50d474ff5aa3e9e925276929b220c81a332fd661a27149605a06e2b77b42d |
| curve-share-v1.png | 1585×992 | 65626531d68752343a3bf65b161ecdab6b651b26f9760cbde0752dbcfa917822 |
| bean-inventory-v3.png | 1586×992 | ef9c190d25df4243685574bf5c8c627cb93bc88bb6786eb24258494893425697 |

**封装缺口**：12 份 manifest 缺 host `fileSha256`/原稿标注哈希；12 份 `anchors.original=null`，缺 host differences。原稿副本和 contact-sheet 存在；审查使用原始 PNG、actual anchors 和保留的原稿 JSON 独立比对。EXIT trap 二次 pull 覆盖 finalizer 的修复已由另一执行者提交 `19126f6`，尚不能追认本批缺失字段；下一次实际 CI 必须验证。原 artifact 不回填伪装完整。

### 已看图清单

每格代表实读 `E/tablet-user-landscape-{theme}-font-{scale}/{fixture}/window.png`，并读取同目录 `visual.json`、`anchors.json`、`tokens.json`、`manifest.json`。

| fixture | light 1.0 | dark 1.0 | light 1.3 | dark 1.3 |
|---|---|---|---|---|
| preparation-confirmed-four-stage | 已看 | 已看 | 已看 | 已看 |
| share-four-stage-not-exported | 已看 | 已看 | 已看 | 已看 |
| inventory-three-beans-selected | 已看 | 已看 | 已看 | 已看 |

## 2. 四门判定

| 门禁 | 本次判定 | 可验证结果 / 剩余边界 |
|---|---|---|
| G1 间距、构图、完整可达 | FAIL | 108 原锚点 ×4=432 次存在，无缺失/重复；但存在实际裁切与源稿细节遗漏。原稿 1138×623 的目标不得直接硬套 618.2222 的高度，也不能把自然像素累计误差一律容忍。 |
| G2 字体、字重 | 候选可复现；原稿一致性未冻结 | 516 TextView 的实际 weight 与所请求角色一致，无 fakeBold、includeFontPadding=false、letterSpacing=0。源稿字体未知，实际每 glyph 字体来源/墨迹未知；不因此阻止修复已知布局。 |
| G3 字号、行高、单位 | FAIL | 已取得实际 Layout；但单位同屏 baseline 不一致、图表 label 相交，摘要层级需修订。`lineHeight` getter 不等于实际行盒。 |
| G4 区域背景、描边、选中态、状态色 | FAIL / 候选色值待冻结 | 取样内框颜色可复现；准备就绪点错误使用正文色，分享表头底/网格和列表分隔缺失。深色无获批原稿，属于候选映射，不可宣称原稿精确匹配。 |

### 字体与行盒实数

516 TextView 均有 `actual_layout_api`，合计 520 行。512 单行候选中心 baseline delta=0，4 个多行不套单行公式；这只证明各自居中计算，不证明不同字号的单位对齐。516 ASCII probe 均是十个正有限 advance 且该 probe 内相等；`tnumActivation` 仍 unknown，不是已启用 tnum。

| 请求 size / line sp | 1.0 实际 size / getter line px | 1.3 实际 size / getter line px |
|---|---:|---:|
| 14 / 22 | 31.5 / 49 | 42.3 / 56 |
| 16 / 24 | 36 / 54 | 45.45 / 59 |
| 20 / 30 | 45 / 67 | 53.100002 / 67 |
| 24 / 36 | 54 / 81 | 59.399998 / 81 |
| 40 / 60 | 90 / 135 | 90 / 135 |

这里是 Android 34 实际 sp 换算，不能拿 `fontScale×size` 推翻非线性结果。例如 1.0 body getter line=54，Layout 高=52；1.3 辅助文字 getter line=56，实际单行 Layout 高=61。单/多行的自然高度、baseline、padding 与可视区须分别比对，不能让 getter 代替行盒。

未知项目保持未知：516 TextView 实际 glyph 墨迹未采，516 tnum 激活未知，160 drawable 结果受尺寸或 public API 限制；isolated drawable ARGB 不等于屏幕合成色。独立 TextShaper 也不得改名为 TextView 实际逐 glyph 绘制证据。

### 屏幕颜色抽样（非抗锯齿边缘）

从分享 1.0 PNG 的已知平坦内框读取 RGB；这些是**样张实值**，不是恢复原稿色值。

| 角色 / 取样点屏幕 px | light RGB | dark RGB |
|---|---|---|
| page `(200,100)` | 249,250,249 | 17,25,26 |
| surface `(240,250)` | 255,255,255 | 27,37,38 |
| primary `(1690,370)` | 8,122,120 | 8,122,120 |
| disabled `(1690,830)` | 236,239,241 | 38,50,52 |
| stage 1 `(352,709)` | 240,247,247 | 33,48,48 |
| stage 2 `(644,709)` | 241,246,252 | 33,46,51 |
| stage 3 `(937,709)` | 250,246,240 | 39,46,42 |
| stage 4 `(1229,709)` | 247,244,250 | 37,45,50 |

stage Paint alpha 实际为 15/255（代码将 255×0.06 截断），不是精确 6%。表中 stage 值是 PNG 合成结果，不用 isolated ARGB 替代。文字边缘、细线边缘的抗锯齿差异需与平坦内框/实际路径几何分开；目前没有自定 ΔE 或像素容差，也不把原稿纹理/渐变当成必须复制的确定 token。

## 3. 明确缺陷与原稿差异

### 3.1 跨页硬缺陷

1. **单位高悬**：1.0 准备摘要数值/单位同屏 baseline=498/488 px；准备 18.0/g=788/763；库存 90/g=592/567。1.3 分别为503/495、812/792、616/596。即摘要差10/8 px，大数差25/20 px；这是真实对齐差，不是抗锯齿。分享参数同样差10/8 px，虽然单位横向靠右符合原稿，纵向仍须基线一致。
2. **图表标签相交**：准备与分享的 Canvas local 墨迹记录相同。1.3 “压力 / bar”盒 `[1,40.563,185,84.563]` 与“12” `[29,60.693,71,92.693]` 相交约42×23.87 px，PNG 清楚可见。1.0 包围盒也有约1.28 px 垂直交集；不能按肉眼小而放行。旧门禁只检查盒是否在 chart 内，没有检查文字对之间是否相交。
3. **准备 1.3 说明被截**：confirmation View 693×117，local visible 693×63；baseline77、实际 Layout61 高，不是只截空白。两主题均见“开始前将再次确认。”下部被固定 dock 截掉。
4. **库存 1.0 第二流水差2 px**：三列 View 高54，仅52可见；实际行盒 `[0,1,w,53]` 下缘也越1 px。1.3 第一条流水仅10/66 px 可见、第二条不可见。允许大字局部滚动，但现证据未逐行验证；仅 `height>0` 不是可达门禁。
5. **分享 1.3**：第四阶段行首屏不可见，第三行部分可见，privacy View117高仅93可见。此布局可使用既定局部滚动，不要求强塞首屏；但本批没有完整滚达截图/严格可视断言，不能关闭可达性。

### 3.2 准备页

源稿对照：设备就绪/秤实时卡与演示识别缺失，变成“连接设备”；配方副说明缺失；摘要图标、目标液重标签、竖分隔缺失，数值与配方名同级过重；就绪圆点取正文色而非 accent；确认 info / primary play 缺失；最近行副说明和分隔缺失；图表左/底实轴与竖网格缺失。当前两行最近可见、第三行滚动属于明确的较短窗口适配，不要求恢复原图三行强塞。

### 3.3 分享页

“经典浓缩 · 压力模式”变为同字号标题，丢失模式徽标层次；参数值过重。返回箭头变成小字符 `‹`；动作图标、设计示意标识缺失。表格没有表头浅底、行列线和阶段色点；两卡之间额外竖线不在原稿。兼容性/完整参数入口移到右栏是既定候选适配，不伪称原稿原位置；保留且验证滚达。图高/刻度密度是明确短高适配，不恢复拥挤刻度。

### 3.4 库存页

豆列表缺图标、chevron、未选行分隔；批次标签飘到右端而非跟随名字；日期列分隔和流水表头/行分隔缺失；使用豆款/调整/详情/确认的图标缺失。标准两条流水肉眼能识别不等于完整可视（上述2 px）。右侧常显滚动条应作为可视区域预算的一部分，不让百分比/标签贴压其轨道；不借隐藏滚动条掩盖内容裁切。

## 4. 下一批给 Sol 的封闭修正清单

以下是 Astra 候选设计修订，不是 Sol 为通过 CI 压字。保持只读 Mock fixture、无 callback/服务/持久化写入。三页一次成组构建/取证，不分装饰项微推送。本批到此收口，不再新增页面/主题/业务状态。

### A. 必须先修：几何、真实字号与可达性

- 所有数字/单位以两个真实 TextView baseline 对齐，差必须0；不写固定offset，不缩字号。分享保留单位靠右；准备/库存保留数字后8dp间隔。
- 图表独立标题带：`plotTop >= titleInkBottom + 8dp - highestTickInkTopRelativeToTickY`，并取既定最小 plotTop 的较大值。底部阶段文字按实际 Paint 留8dp净空；保留14sp、chart≥160dp及稀疏刻度。文字对墨迹盒不得相交；新增左/底1dp实轴（muted）和阶段1dp竖网格（border）。
- 准备 confirmation 移到固定 dock 最上，以真实自然行高；下8dp、primary≥56、下8dp、details≥48。两状态和豆/粉量在上部 body 局部滚动。1.0 状态和说明首屏完整；1.3 说明与主操作首屏完整，状态逐项完整滚达。
- 库存 body→dock 间隔明确适配：H∈[618,623]dp，`gap=4+0.8*(H−618)`dp；H623为8、H618为4。用实际 px 窗高计算，最终只转换一次整数 px。现 H618.222→4.178dp。兑现原短高约4dp表末余量，不改行高/字体/内部 padding；如仍裁切，停止追加任意减 gap。
- 标准1.0：分享表头+4行、库存2流水均初始全可见。1.3：分享5行/privacy、库存2流水、准备两状态逐项 `requestRectangleOnScreen` 后宽高完整可见，保存 after-scroll PNG/rect，finally 恢复初始滚动。不把 `height>0` 当 PASS。原最近3行严格滚达继续保留。

### B. 字体层次与原稿语义补齐

- 新增仅样张专用 `compactRecipeMetric=16sp/500/24sp`（准备摘要数字），`shareMetric=20sp/400/30sp`（分享卡数值）；不覆盖全局 recipeValue，不改40sp/700大数。原因是原稿摘要低于配方名、分享参数中性，而非腾空间。
- 准备 recipe 名后16dp加14/400副说明“四阶段压力配方”；摘要三组 `[96,304]`、`[328,536]`、`[560,790]`，24dp温度/杯/阶梯图标+8dp，第二组加“目标液重”，labels/units14/400/22；x316/548处1dp、24dp高竖分隔。
- 准备 header 右两张150dp设备卡（x814/972、gap8、y16、min64高），24dp图标+8dp，两行14sp标签/状态。文案“咖啡机/已就绪”“电子秤/实时”；状态accent，卡底用disabled轻底。高度不足按自然高增长，不裁切。左subtitle后16dp同baseline标“演示数据”；实际能力仍是fixture，不接设备。1.3如header自然高超过80，其下body缩小并局部滚动，不压字。
- 准备状态12dp accent点+8dp；确认24dp info、主按钮24dp play，各与文字gap8。最近行min48，名称后16dp插14/400 muted副说明，右侧摘要保留；不足宽时副说明另起行并自然增长，禁止省略。三条 fixture副说明为“四阶段压力配方”“柔和风味 · 三阶段”“低压逐步释放 · 三阶段”（最后一条忠于fixture三阶段，不抄原图四阶段）。行内底1dp分隔；摘要值间竖线替代斜杠。1.0至少2整行、1.3至少1整行+三行滚达仍是硬条件。
- 分享名字24/500，后16dp独立“压力模式”14/400徽标，selected底/accent字、水平padding8；仍在36dp名称容器，自然大字可增长。返回24dp原生箭头放48dp触控容器；右上“设计示意”14sp。四动作24dp相应图标+8dp，disabled状态不变。两卡间只留24dp空隙，删除额外竖线。表头disabled底，1dp border外框/行列线，阶段首列12dp stage色点+8dp。线绘在行内，不额外挤高。
- 库存列表24dp豆图标+12dp，尾16dp chevron；名称/重量间至少16dp。未选行内底1dp线；batch badge跟随实际名称末端16dp，14/400、paddingH8、自然22sp行+上下各4dp。日期列间和流水表头/行底加1dp线，不增加既定行高。primary24dp杯/adjust24dp立方体/details16dpchevron/confirmation24dpinfo，与文字gap8。

### C. 颜色、证据和所有权

- 保留现有候选 semantic palette 和圆角，不全局换主题；就绪点必须 accent，表格/分隔必须对应 token。新增图标用1dp候选原生线条，不声称恢复生成图路径。没有精确原稿色值的项目仍 proposed。
- Sol：`StrictVisualSampleActivity/Chart/Icon/Tokens/Checks`，Tokens只增上述两种专用角色。另一 Sol 独占封装脚本/离线测试。Astra：本 review 与 `strict-native-sample-anchors.json` 的设计修订。不得改原PNG、真实产品Activity/Theme/硬件接口。
- 下一次必须仍收齐12组：初始整窗、滚达后整窗、原始几何、actual Layout/Canvas、同屏baseline、字盒交集、colors/alpha、修复后的host哈希/原稿映射。先保存所有失败证据，再令CI失败；不能把FAIL混成BASELINE_PENDING。
- G2的不可观测内部信息不阻塞这批可验证修复；但不能删 unknown、伪造 glyph来源、缩字、提高容差或单靠CI绿色批准视觉。

## 5. 可以固定什么，不能冻结什么

可固定为下一轮复现输入：三张原PNG身份、真实merge/head关系、三个业务fixture、四种profile、坐标空间/Insets、已验证的字体资产身份、候选role→实际Paint采集机制、平坦区域屏幕颜色实值与15/255 alpha事实。80dp rail、24dp栏间距、48/56dp触控最小值保留为设计约束；不是宣布所有锚点已通过。

**本次不冻结全套视觉 tokens。** 两个摘要角色明确修订；单位、状态色/表头底、图表标签带与1.3自然布局未通过。现palette/圆角保持下轮候选输入，不等于用户已批准；dark没有原稿，需看下一轮成组结果作为独立候选。字体family/weight的复现可核，不得外推“ImageGen原字体精确一致”。其他21页及产品同名页仍需同状态、同视口的独立整页G1–G4证据。

## 6. 原108锚点审计附录

以下保留**本批原候选目标**与实际1.0矩形，避免后续修订把旧失败改写为通过。432次引用全部存在，深浅同字号几何可分别查同路径；1.3是自然排版适配，不拿1.0静态坐标强行判等。坐标为app dp，四舍五入显示3位只用于阅读，判定读取JSON原始值。H差−4.7778dp解释底锚上移，原生逐段整数px分配解释部分累计小偏移；其他差异不能一律归入“抗锯齿”。

### preparation-confirmed-four-stage

| semanticId | 目标 dp | 实际 light1.0 dp |
|---|---|---|
| `sample/nav/rail` | [0, 0, 80, 623] | [0, 0, 80, 618.222] |
| `sample/page/title` | [96, 16, 790, 52] | [96, 16, 789.778, 52] |
| `sample/page/subtitle` | [96, 60, 790, 82] | [96, 60, 789.778, 81.778] |
| `sample/page/headerDivider` | [96, 94, 1122, 95] | [96, 94.222, 1121.778, 95.111] |
| `sample/preparation/mainPane` | [96, 104, 790, 607] | [96, 104, 789.778, 602.222] |
| `sample/preparation/cup` | [814, 104, 1122, 607] | [813.778, 104, 1121.778, 602.222] |
| `sample/preparation/paneDivider` | [802, 110, 803, 607] | [801.333, 109.778, 802.222, 602.222] |
| `sample/preparation/recipe` | [96, 110, 630, 146] | [96, 109.778, 629.778, 145.778] |
| `sample/preparation/changeRecipe` | [646, 104, 790, 152] | [645.778, 104, 789.778, 152] |
| `sample/preparation/summary` | [96, 160, 790, 196] | [96, 160, 789.778, 196] |
| `sample/preparation/summaryDivider` | [96, 204, 790, 205] | [96, 204, 789.778, 204.889] |
| `sample/preparation/stageTitle` | [96, 212, 790, 242] | [96, 212, 789.778, 241.778] |
| `sample/preparation/stage` | [96, 250, 790, 416] | [96, 249.778, 789.778, 411.111] |
| `sample/preparation/stage/plot` | [136, 282, 774, 378] | [136, 281.778, 773.778, 373.111] |
| `sample/preparation/stageNote` | [96, 424, 790, 446] | [96, 419.111, 789.778, 440.889] |
| `sample/preparation/recentTitle` | [96, 463, 630, 493] | [96, 457.778, 629.778, 487.556] |
| `sample/preparation/recentMore` | [646, 454, 790, 502] | [645.778, 448.889, 789.778, 496.889] |
| `sample/preparation/recent` | [96, 510, 790, 606] | [96, 504.889, 789.778, 601.333] |
| `sample/preparation/recent/row1` | [96, 510, 790, 558] | [96, 504.889, 789.778, 552.889] |
| `sample/preparation/recent/row2` | [96, 558, 790, 606] | [96, 552.889, 789.778, 600.889] |
| `sample/preparation/recent/row3` | [96, 606, 790, 654] | [96, 600.889, 789.778, 648.889] |
| `sample/preparation/cupTitle` | [814, 110, 1122, 140] | [813.778, 109.778, 1121.778, 139.556] |
| `sample/preparation/cupTitleDivider` | [814, 148, 1122, 149] | [813.778, 147.556, 1121.778, 148.444] |
| `sample/preparation/beanLabel` | [814, 156, 1122, 178] | [813.778, 155.111, 1121.778, 176.889] |
| `sample/preparation/beanName` | [814, 184, 1050, 220] | [813.778, 182.667, 1049.778, 218.667] |
| `sample/preparation/changeBean` | [1058, 178, 1122, 226] | [1057.778, 176.889, 1121.778, 224.889] |
| `sample/preparation/beanDivider` | [814, 234, 1122, 235] | [813.778, 232.889, 1121.778, 233.778] |
| `sample/preparation/doseLabel` | [814, 242, 1122, 264] | [813.778, 240.444, 1121.778, 262.222] |
| `sample/preparation/doseValue` | [814, 272, 1034, 332] | [813.778, 270.222, 1033.778, 330.222] |
| `sample/preparation/editDose` | [1058, 278, 1122, 326] | [1057.778, 276, 1121.778, 324] |
| `sample/preparation/doseDivider` | [814, 340, 1122, 341] | [813.778, 338.222, 1121.778, 339.111] |
| `sample/preparation/machineReady` | [814, 348, 1122, 372] | [813.778, 345.778, 1121.778, 369.778] |
| `sample/preparation/machineDivider` | [814, 380, 1122, 381] | [813.778, 377.778, 1121.778, 378.667] |
| `sample/preparation/scaleReady` | [814, 388, 1122, 412] | [813.778, 385.333, 1121.778, 409.333] |
| `sample/preparation/scaleDivider` | [814, 420, 1122, 421] | [813.778, 417.333, 1121.778, 418.222] |
| `sample/preparation/confirmation` | [814, 436, 1122, 480] | [813.778, 432.889, 1121.778, 475.111] |
| `sample/preparation/primary` | [814, 495, 1122, 551] | [813.778, 490.222, 1121.778, 546.222] |
| `sample/preparation/details` | [814, 559, 1122, 607] | [813.778, 554.222, 1121.778, 602.222] |
| `sample/preparation/recipeRow` | [96, 104, 790, 152] | [96, 104, 789.778, 152] |
| `sample/preparation/recentHeaderRow` | [96, 454, 790, 502] | [96, 448.889, 789.778, 496.889] |
| `sample/preparation/deviceEntry` | [978, 16, 1122, 64] | [977.778, 16, 1121.778, 64] |

### share-four-stage-not-exported

| semanticId | 目标 dp | 实际 light1.0 dp |
|---|---|---|
| `sample/nav/rail` | [0, 0, 80, 623] | [0, 0, 80, 618.222] |
| `sample/page/headerRow` | [96, 8, 1122, 56] | [96, 8, 1121.778, 56] |
| `sample/page/back` | [96, 8, 144, 56] | [96, 8, 144, 56] |
| `sample/page/title` | [160, 14, 1122, 50] | [160, 13.778, 1121.778, 49.778] |
| `sample/share/recipePane` | [96, 64, 704, 607] | [96, 64, 704, 602.222] |
| `sample/share/actionsPane` | [728, 64, 1122, 607] | [728, 64, 1121.778, 602.222] |
| `sample/share/recipe` | [112, 80, 688, 116] | [112, 80, 688, 116] |
| `sample/share/summary` | [112, 124, 688, 198] | [112, 124, 688, 197.778] |
| `sample/share/temperatureCard` | [112, 124, 392, 198] | [112, 124, 392, 197.778] |
| `sample/share/targetCard` | [408, 124, 688, 198] | [408, 124, 688, 197.778] |
| `sample/share/stageTitle` | [112, 206, 688, 236] | [112, 205.778, 688, 235.556] |
| `sample/share/stage` | [112, 244, 688, 404] | [112, 243.556, 688, 403.556] |
| `sample/share/stage/plot` | [152, 276, 672, 366] | [152, 275.556, 672, 365.556] |
| `sample/share/stageNote` | [112, 412, 688, 434] | [112, 411.556, 688, 433.333] |
| `sample/share/tableCaption` | [112, 442, 688, 464] | [112, 441.333, 688, 463.111] |
| `sample/share/table` | [112, 472, 688, 582] | [112, 471.111, 688, 580] |
| `sample/share/table/header` | [112, 472, 688, 494] | [112, 471.111, 688, 492.889] |
| `sample/share/table/row1` | [112, 494, 688, 516] | [112, 492.889, 688, 514.667] |
| `sample/share/table/row2` | [112, 516, 688, 538] | [112, 514.667, 688, 536.444] |
| `sample/share/table/row3` | [112, 538, 688, 560] | [112, 536.444, 688, 558.222] |
| `sample/share/table/row4` | [112, 560, 688, 582] | [112, 558.222, 688, 580] |
| `sample/share/actionsTitle` | [744, 80, 1106, 110] | [744, 80, 1105.778, 109.778] |
| `sample/share/primary` | [744, 126, 1106, 182] | [744, 125.778, 1105.778, 181.778] |
| `sample/share/secondary` | [744, 190, 1106, 238] | [744, 189.778, 1105.778, 237.778] |
| `sample/share/actionsDivider` | [744, 254, 1106, 255] | [744, 253.778, 1105.778, 254.667] |
| `sample/share/export` | [744, 270, 1106, 318] | [744, 269.333, 1105.778, 317.333] |
| `sample/share/fileShareDisabled` | [744, 326, 1106, 382] | [744, 325.333, 1105.778, 381.333] |
| `sample/share/exportHint` | [744, 390, 1106, 412] | [744, 389.333, 1105.778, 411.111] |
| `sample/share/compatibility` | [744, 428, 1106, 472] | [744, 427.111, 1105.778, 469.333] |
| `sample/share/details` | [744, 480, 1106, 528] | [744, 477.333, 1105.778, 525.333] |
| `sample/share/privacy` | [744, 547, 1106, 591] | [744, 544, 1105.778, 586.222] |

### inventory-three-beans-selected

| semanticId | 目标 dp | 实际 light1.0 dp |
|---|---|---|
| `sample/nav/rail` | [0, 0, 80, 623] | [0, 0, 80, 618.222] |
| `sample/page/title` | [96, 16, 790, 52] | [96, 16, 789.778, 52] |
| `sample/inventory/headerSummary` | [96, 60, 790, 82] | [96, 60, 789.778, 81.778] |
| `sample/inventory/add` | [930, 16, 1122, 64] | [929.778, 16, 1121.778, 64] |
| `sample/page/headerDivider` | [96, 94, 1122, 95] | [96, 94.222, 1121.778, 95.111] |
| `sample/inventory/list` | [96, 110, 496, 607] | [96, 109.778, 496, 602.222] |
| `sample/inventory/listTitle` | [112, 112, 480, 142] | [112, 111.556, 480, 141.333] |
| `sample/inventory/selected` | [96, 156, 496, 220] | [96, 155.111, 496, 219.111] |
| `sample/inventory/listRow2` | [96, 228, 496, 292] | [96, 227.111, 496, 291.111] |
| `sample/inventory/listRow3` | [96, 300, 496, 364] | [96, 299.111, 496, 363.111] |
| `sample/inventory/paneDivider` | [508, 110, 509, 607] | [507.556, 109.778, 508.444, 602.222] |
| `sample/inventory/detail` | [520, 110, 1122, 607] | [520, 109.778, 1121.778, 602.222] |
| `sample/inventory/beanName` | [520, 110, 1026, 146] | [520, 109.778, 1025.778, 145.778] |
| `sample/inventory/batchBadge` | [1042, 110, 1122, 148] | [1041.778, 109.778, 1121.778, 147.556] |
| `sample/inventory/weightLabel` | [520, 154, 1122, 176] | [520, 153.333, 1121.778, 175.111] |
| `sample/inventory/weight` | [520, 184, 1122, 244] | [520, 183.111, 1121.778, 243.111] |
| `sample/inventory/initialWeight` | [520, 244, 1122, 268] | [520, 243.111, 1121.778, 267.111] |
| `sample/inventory/progress` | [520, 276, 1054, 284] | [520, 275.556, 1053.778, 283.556] |
| `sample/inventory/progressPercent` | [1070, 270, 1122, 292] | [1069.778, 268.889, 1121.778, 290.667] |
| `sample/inventory/weightDivider` | [520, 300, 1122, 301] | [520, 298.667, 1121.778, 299.556] |
| `sample/inventory/dates` | [520, 316, 1122, 370] | [520, 314.222, 1121.778, 368] |
| `sample/inventory/dateLabels` | [520, 316, 1122, 338] | [520, 314.222, 1121.778, 336] |
| `sample/inventory/dateValues` | [520, 346, 1122, 370] | [520, 344, 1121.778, 368] |
| `sample/inventory/datesDivider` | [520, 378, 1122, 379] | [520, 376, 1121.778, 376.889] |
| `sample/inventory/ledgerHeaderRow` | [520, 386, 1122, 434] | [520, 383.556, 1121.778, 431.556] |
| `sample/inventory/ledgerTitle` | [520, 395, 978, 425] | [520, 392.444, 977.778, 422.222] |
| `sample/inventory/ledgerMore` | [986, 386, 1122, 434] | [985.778, 383.556, 1121.778, 431.556] |
| `sample/inventory/ledger` | [520, 442, 1122, 512] | [520, 439.556, 1121.778, 509.333] |
| `sample/inventory/ledgerColumns` | [520, 442, 1122, 464] | [520, 439.556, 1121.778, 461.333] |
| `sample/inventory/ledgerRow1` | [520, 464, 1122, 488] | [520, 461.333, 1121.778, 485.333] |
| `sample/inventory/ledgerRow2` | [520, 488, 1122, 512] | [520, 485.333, 1121.778, 509.333] |
| `sample/inventory/actions` | [520, 521, 1122, 577] | [520, 516.444, 1121.778, 572.444] |
| `sample/inventory/primary` | [520, 521, 736, 577] | [520, 516.444, 735.556, 572.444] |
| `sample/inventory/adjust` | [744, 525, 920, 573] | [743.556, 520.444, 919.556, 568.444] |
| `sample/inventory/batchDetails` | [928, 525, 1122, 573] | [927.556, 520.444, 1121.778, 568.444] |
| `sample/inventory/confirmation` | [520, 585, 1122, 607] | [520, 580.444, 1121.778, 602.222] |

## 7. 814e521 第二轮真实样张审查（保留上轮 FAIL）

### 7.1 证据与结论

本节为 Astra 只读审查结果；**第二轮仍不能标三页严格视觉 PASS**。不修改第 1–6 节对上一轮的事实，不以本轮成功覆盖旧失败。

新证据根 `E2/`：

`build/task-first-evidence/ci-814e521-frame/task-frame-tablet-user-landscape/task-first-evidence/strict-native-samples/3cdad10657f64b76391e763f5bc3838cb49a3577/`

PR head `814e521`，CI `39135733668`，实际 merge `3cdad10657f64b76391e763f5bc3838cb49a3577`。已实看三页×浅/深×1.0/1.3全部12张 `window.png`，再看浅/深1.3分享 `after-scroll-5.png`、`after-scroll-6.png` 和库存 `after-scroll-2.png`，共18张。重新核了72项manifest文件hash、432项anchor引用、28组单位baseline；均相符。12份实际app仍2560×1391px、origin(0,74)，`sourceDirty=false`。本轮host原稿映射和差异表存在，不追认上一轮缺失字段。

### 7.2 可以限定关闭的缺陷

| 项目 | 实际结果 | 关闭边界 |
|---|---|---|
| 数字/单位基线 | 28组同屏baseline差均0，PNG不再高悬 | 仅本批7种数值组合×4profile |
| 轴标题与12刻度互叠 | 8图实际文字盒互不相交；1.3标题到最高刻度净距18.879799px，要求18px | 关闭文字对互叠，**不代表网格与文字不相交** |
| 准备确认说明裁切 | 1.3两主题说明、主操作完整；两状态也首屏完整 | 这两个字号和固定fixture，不外推短高/IME |
| 库存标准第二流水差2px | 1.0两主题两流水完整 | 关闭这一标准首屏缺口 |
| 库存大字流水不可见 | after-scroll-2真实PNG已显示两流水；固定操作保持 | 关闭本fixture局部可达，不声称1.3首屏两行完整 |
| 原稿层次/细节遗漏 | 摘要层级、模式徽标、设备卡/演示识别、accent状态点、列表副说明、图标和分隔已出现 | 使用已注明的原生候选图标/布局适配，不冒称原图精确路径 |

580个TextView均有actual Layout；实际weight与请求一致，无fakeBold，includeFontPadding=false、letterSpacing=0。580个tnumActivation仍unknown。24个状态点Canvas已正确记录FILL，不再误记STROKE。样张平坦颜色复采：light page/surface/primary/disabled为(249,250,249)/(255,255,255)/(8,122,120)/(236,239,241)，dark为(17,25,26)/(27,37,38)/(8,122,120)/(38,50,52)。字体、颜色事实可以复现，不自动证明与生成稿原始参数相同。

### 7.3 未关闭：分享滚动截图与几何矛盾

这是证据一致性问题，不能用JSON PASS关闭：

- 两主题1.3 `share-four-stage-not-exported/scroll-reachability.json` 声明row4从 `[252,1425,1548,1486]` 移至 `[252,1368,1548,1429]`，after complete=true；但 `after-scroll-5.png` 的第四行仍没有显示。
- 对initial与after5的RGB内容区 `[252,218,1548,1429]` 做逐像素差，两主题结果均完全相同。区别只在滚动条等区域，左内容没有实际位移。
- privacy声明从 `[1674,1300,2488,1417]` 移到 `[1674,1276,2488,1393]`；但after6相应内容区 `[1674,1276,2470,1393]` 与initial完全相同，第二行仍未完整显示。
- 库存after2确实发生像素位移并显示目标，因此不能把所有滚动证据一概否定，也不能把分享矛盾忽略。

必须核实绘制提交与截图时序，保存同状态的geometry/PNG后再验。禁止任意sleep、减少目标行、放宽完整可视断言或仅凭scrollY/矩形宣布成功。当前只认可分享1.0首屏全表；1.3第四阶段/privacy可达性仍 **EVIDENCE_CONFLICT**。

### 7.4 仅剩本范围内的成组视觉修正

**一、分享表格/库存日期列内距（G1）**

新竖线已画出，但内容仍0横向内距：分享表格列线贴“压力/注水量”和数值；库存日期第二/三列的竖线贴“烘焙/开封”首字。浅/深、两个字号都能看见，不是抗锯齿误差。

- 分享所有header/body cell加水平8dp内边距，首列stage点也从8dp内框起。外框、列比例、行高、字号不变；line与文本之间必须有真实净空。
- 日期第一列保留整体左边界；第二/三列paddingStart16dp，所有列paddingEnd16dp。标题和值使用同一列内容起点，竖线留在列边界。禁止删线或缩字凑空间。
- 不把此规则推广为全产品表格统一24dp行高；真实可点击流水另受触控规则约束。

**二、图表网格穿标签（G1/G3）**

1.3准备/分享的最高网格横线穿过9.0标签。准备plotTop=117px，`9.0 bar`墨迹y=101.75..136.75；分享plotTop=117px，`9.0`墨迹y=103..136。文字对之间零相交门禁抓不到这一情况。

保留全部刻度、轴、阶段网格、数据位置、14sp字号和既定图高。先计算targetLabel真实墨迹盒；只从**grid绘制区域**排除该盒向外2dp的保护框。阶段底色不擦、不加白块，不删除整条网格；axis/data正常绘制。记录grid原段、实际可见段、保护框；断言实际可见grid段与保护框无交集。2dp是本次明确的设计净空，不是像素比较容差。不得把原始完整理论线改名为“不相交”证据。

本轮没有新增页面、状态或产品改造授权。实现只限隔离样张Activity/Chart；取证时序与相应Checks由独立执行者负责，统一提交/构建取证。root已明确授权在anchors仅追加上述两条动态规则；原108个mandatory节点不变。

### 7.5 可有限固定的原生补充基线与产品边界

建议固定为**下一步原生设计输入**：Noto字体资产身份、已看图的14/16/20/24/40sp角色体系及两个专用摘要角色、真实baseline对齐规则、已复核的语义色板和状态点用色、80dp导航/24dp栏间距/48–56dp触控约束。含义是Astra对可复现原生补充的限定选择，不是用户已逐项批准，更不是识别出了ImageGen原始字体/色值。原稿无法提供的glyph来源/tnum激活等仍unknown。

仍不冻结：分享/日期cell内距、grid-label保护规则的实际结果、分享1.3滚动像素证据、整页G1–G4。原文件与anchors继续proposed，不因建议有限固定就改成整体PASS。

**产品迁移只能页级、逐页保留真实行为，不能复制Mock替代业务：**

- Inventory真实流水有详情回调、48dp触控和最近3事件，不能复制样张24dp只读行/2事件。
- Home真实主操作经Extraction/Curve路由，不得直接startShot；粉量确认保留attempted/confirmed/event校验。
- Share保留真实1..4stage、FlowRaw只读、SAF成功后才启用文件分享。
- 最小候选范围可为HomeActivity/HomeRecentCurves/CurveShareActivity/CurveSharePreview/BeanInventoryActivity；共享CurveStageView/CurvePreviewMetrics只能页级opt-in，不全局换色影响其余21页。
- 上述只是迁移边界，不是本轮授权立即迁移或产品通过。完成样张封闭项后，真实产品还必须同状态/同视口整页取证，分别验间距、字体、字号行高和颜色；硬件验证独立保留。

## 8. f62b983 浅色 1.0 有限实图审查

本节仅关闭三个隔离样张在 **light/fontScale1.0** 的限定缺陷，不改前两轮失败事实，不记整页 G1–G4 PASS。CI `39138001156`，head `f62b983`，实际 merge/source `365f701b4b8b0e87cae0e75ba1a8d9b3d669a663`；证据为失败任务保留的 raw-device 原件：

`build/task-first-evidence/ci-f62b983-frame/task-frame-tablet-user-landscape/task-first-evidence/strict-native-samples/raw-device/365f701b4b8b0e87cae0e75ba1a8d9b3d669a663/tablet-user-landscape-light-font-1.0/`

已实看其中 `preparation-confirmed-four-stage`、`share-four-stage-not-exported`、`inventory-three-beans-selected` 的三张 `window.png`，逐张对照 §7 同 profile 三图及三张原稿；核对各自 `visual.json`、`anchors.json`、`tokens.json`、`manifest.json` 和 `window-frame-sync.json`。三份 PNG hash 与 manifest 相符，sourceDirty=false，实际显示2560×1600/density360、app2560×1391px、origin(0,74)、无IME。此处不声称失败任务的完整host封装或12图已完成。

| 限定项目 | 实图与实测结果 | 本节结论 |
|---|---|---|
| 分享表格内距 | 表头及数值cell左右18px=8dp；首阶段容器x252、点x270，首列点也内移18px。四行及表头不再贴竖线，内容完整 | 关闭浅1.0的表格贴线问题 |
| 库存日期净空 | 标题和值第一列start0，第二/三列start36px=16dp，三列end36px；首字与竖线分开，三日期完整且起点一致 | 关闭浅1.0的日期贴线问题 |
| 网格保护绘制 | 两图各6条原grid仍保留；准备绘制7段、分享9段。按保护框加半stroke重新计算，各条可见段与应保留补集精确一致；PNG仅在标签附近断开网格，没有整条删除、白块擦底或轴/数据消失 | 通过这两张浅1.0图的保护绘制结果，不外推1.3 |
| 单位/文字/颜色回归 | 7组数字/单位同屏baseline差均0；三页tokens及candidateRoles与814e521相同，145个TextView的文本、实际px、weight、行高、字距及解析颜色未变 | 原已限定关闭项在这三图未回退，不证明ImageGen原字体身份或全状态G2–G4 |

原始RGB应用区域逐像素对照814e521：准备变化仅屏幕y727..729的顶部网格局部；分享变化为y713..715、804..807网格局部及五行表文字内移；库存变化仅第二/三列日期文字水平位移。其余应用像素相同，未发现背景、阶段底色、单位、固定操作或字体回退。系统栏内容不同，未混入应用比较；没有缩放或模糊图片。

**仍未关闭：** 深色、fontScale1.3、分享大字第四行/privacy滚动像素矛盾，以及三页整体严格视觉验收。该次CI在light1.3准备采集时多次出现稳定279像素差，末尾`-1`是5秒预算耗尽的未完成回调哨兵，不是Android PixelCopy错误码；根因仍待诊断原图。不能用本节三张局部通过代替剩余9张或启动产品视觉通过声明。

## 9. 合成边界诊断与无容差双域取证协议

证据来自 `build/task-first-evidence/ci-a2164ad-frame/task-frame-tablet-user-landscape/task-first-evidence/strict-native-samples/raw-device/1a95859052498942d1b8a36ae1706028ae7d506a/tablet-user-landscape-light-font-1.3/preparation-confirmed-four-stage/`。Astra实看并独立重算`window.png`、`window-surface-before.png`、`window-surface-after.png`，三文件SHA与`window-frame-sync.json`的attempt14记录相符。

- 最终两张Window表面图完全相同；与整屏应用区的279个不同像素，仅位于左下角132px `[0,1446,21,1465]` 和右下角147px `[2539,1442,2560,1465]`。差异沿底边逐行扩宽，display均为`#FFF8F9FC`，surface对应为导航白`#FFFFFFFF`/页底`#FFF9FAF9`。没有文字、图表或按钮位于这些差异像素。
- 14次完整采集中，跨域差异始终279；其中第3次surface前后另有1053像素变化，其余13次为0。不能把全部尝试写成稳定，也不能把末尾回调超时当成279差异的原因。
- 原整屏可见底部圆角合成边界，以上证据支持“窗口表面与系统最终合成不是同一像素对象”，不支持“样张字体/背景漂移”。**具体圆角所属surface/任务栏/其他系统层尚未唯一证实。** 清理后的系统dump也不能反推采集当刻的唯一owner。总bbox `[0,1442,2560,1465]` 横跨全宽，绝不能据此排除整条底边。

SDK依据（本地API34源码，由Sol核public jar接口，Astra复核相关源码位置）：`/Users/boolean93/Library/Android/sdk/sources/android-34/android/app/UiAutomation.java` 的 `takeScreenshot(Window)` 在1220行执行`SurfaceControl.Transaction().apply(true)`，再调用`takeSurfaceControlScreenshot`；同目录`UiAutomationConnection.java`的该方法经`ScreenCapture.captureLayers`（255–265行），普通整屏截图则经`captureDisplay`（240行）。`android/view/RoundedCorner.java`第37行明确radius/center仅为近似，`WindowInsets#getRoundedCorner`不能提供精确系统裁切像素。不得用近似圆、猜半径或本次差异集合制造mask。

**批准仅测试层实施无mask双域协议，尚不是新运行通过：**

1. 实际frame commit后采集`S1(PixelCopy) → W1(public Window层) → D1/D2(原整屏) → W2 → S2`，并记录前后焦点、窗口、scroll及目标geometry。采集两端均须`hasWindowFocus=true`且root/decor的`isLayoutRequested=false`，`windowReadyAtBothEnds`与geometry稳定、两域零差及预算同时成立才可accepted，不能只在开始检查焦点。`S1/W1/W2/S2`在已验证坐标映射的全应用区域严格零像素差；`D1/D2`在同一域严格零差。两张原整屏均保存；采用全屏还是由真实Insets确定的全app比较须在proof明确记录，不能选择性剔除内容像素。全屏时钟变化只能有界重试，不加容差。
2. Window映射不能猜测。SDK `android/view/PixelCopy.java` 的`request(Window,…)`/`adjustSourceRectForInsets`会按surfaceInsets原地偏移传入Rect（291–307行）。本轮仅支持可核验的零surface-inset路径：每次新建Rect，记录调用前后且要求无偏移；W位图尺寸须等于实际decor尺寸，WindowMetrics、decor的window/screen原点及scale/rotation检查一致后，才按`root.getLocationInWindow`映射全app。未知映射、非零偏移、尺寸不符、null或不支持API均FAIL并留原图，不反射、不拉伸、不搜索像素偏移来配准。
3. 新proof须区分`windowLayerMatch`、`displayStable`及协议版本；S与D的跨域差异继续完整记录，但不再要求不等价对象字面相等，不伪称`surfaceDisplayMatch=true`。目标geometry与用于验收的D1必须属于同一accepted组；失败位图/预算诊断保留，异步写入未结束的位图不得提前回收。
4. 这只是纠正帧同步比较对象，**没有降低视觉验收**。原整屏仍逐项验完整内容、实际可见性、间距、字体、字号/单位/行盒、背景/边框/状态色；system boundary另外记录。分享1.3第四行/privacy仍须看新after-scroll原图，任何域同步PASS不自动变成G1–G4 PASS，不追认旧失败，不外推真实产品或硬件。

## 10. 0a7b71a 封闭验收：有限冻结，进入 M1

### 10.1 证据身份与实看范围

**结论：本轮封闭样张修正通过，可启动 `docs/plans/2026-10-18-product-visual-migration.md` 的 M1。** 这是三个固定夹具的原生设计输入验收，不是三张生产页面、更不是24页或硬件验收通过。保留前文旧 FAIL 与取证冲突，不反写旧产物。

证据根 `E4/`：`build/task-first-evidence/ci-0a7b71a-frame/task-frame-tablet-user-landscape/task-first-evidence/strict-native-samples/908edcd9d83a8ea53123ba772ab7556ab6e2b4ed/`。PR head `0a7b71a`，run `39140082042`，实际测试 merge `908edcd9d83a8ea53123ba772ab7556ab6e2b4ed`；12份 `sourceDirty=false`。APK声明 SHA `27f99d0851ad7f7af0a58da2db1fa8a71b5a02d6aedc39f2fa9f1784f561189d`；本次未从APK二进制独立重算，不将声明当重算。应用区仍为 `[0,74,2560,1465]`、2560×1391px、density360、zh-Hans、API34、无IME。

Astra重新实看三张原PNG；按浅1.0→深1.0→浅1.3→深1.3审完12张 `window.png`，另看两主题1.3分享 `after-scroll-5.png` / `after-scroll-6.png` 与库存 `after-scroll-2.png`，本轮共18张真实样张图。独立重算72项manifest文件hash及原PNG hash，检查432项参考锚点映射无缺失；重算56对display整图中实际应用区像素差均0，并核其protocol2等价域四项比较均0差、focus/layout/budget条件成立。Sol并行机械结果见本run根下 `mechanical-audit.json`，不能替代上述实看。 该audit还保留66次尝试中的10次拒绝（10次Window域不匹配，其中4次display也不稳）；56次最终accepted不等于全部尝试成功，跨域279像素差继续只是诊断。准备recent的12条滚达记录为true，但旧记录没有独立恢复位置字段，不补写不存在的restore实测结论。

### 10.2 剩余缺陷关闭与四项独立结论

| 门禁/封闭项 | 本轮实际证据 | 有限结论 |
|---|---|---|
| G1：表格/日期内距 | 四配置共60个分享单元格容器均H18px=8dp；第一列由含色点的stage容器提供padding，不误要求其文字子节点重复padding。库存24个日期标签/值节点start为0/36/36px、end36px；原图对应的列间净空已可见 | 关闭原零内距/日期贴竖线缺陷；这些确定性内距可作为生产输入 |
| G1：分享1.3真实可达 | 两主题after5确实上移左内容57px，完整显示第四阶段三列，目标框 `[252,1368,1548,1429]`；after6只滚右说明24px，完整显示privacy两行 `[1674,1276,2488,1393]`。实际PNG不再停在初图 | 关闭§7.3截图/几何冲突及本fixture第四行/privacy可达缺口；不声称1.3初屏四行及privacy全部完整 |
| G1：固定操作与局部列表 | 准备四初图确认说明、状态、主操作完整；标准最近列表保留两整行，大字采用局部滚动。库存标准两流水完整，大字after2两行完整且底部操作仍在 | 既定适配没有回退；不复制样张只读24dp流水为生产48dp交互行 |
| G1/G3：网格与标签 | 8图分别保留6条原网格，总48条；按实际墨迹外扩2dp并加半stroke guard，独立重算可见补集共68段，与实际Canvas line操作一致。准备1.0为7段，其余每图9段；PNG标签处留空、其余网格存在 | 关闭网格穿字；没有删除整条网格、涂底遮字或缩14sp。坐标轴、目标线/点、阶段底色仍保留 |
| G2：字体/字重 | 12份候选roles与上一轮相同；580个TextView的weight、角色、字距等记录无回退，实际Layout均存在。Noto Sans SC候选资产SHA保持 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da` | 本批中文原生候选字形及层次可选定用于M1；ImageGen字体身份、实际逐glyph字体来源不因此变成已知 |
| G3：字号/行盒/单位 | 580个TextView的textSizePx、lineHeightPx、lineSpacing、baseline、weight、includeFontPadding等与814e521逐项相同，实际Layout及换行实看无新增异常；28组数字/单位同屏baseline差为0 | 本批角色及实际原生行盒基线有限冻结；不以getter行高冒充Layout行盒，不将1.3按线性乘法重算或削减文字 |
| G4：背景/描边/选中/禁用/图表 | 12份tokens与上一轮完全相同，文字当前色记录无回退。PNG复采可见page/surface/primary/selected/disabled/border纯色，浅色为 `#F9FAF9/#FFFFFF/#087A78/#E2F0ED/#ECEFF1/#DDE3E7`，深色为 `#11191A/#1B2526/#087A78/#203D3B/#263234/#3B4B4C`；阶段底色与文字层次实看保留 | 本批实际观察状态的语义色与合成结果可选定用于M1；未观察pressed/focused等状态仍需产品取证，不能从静态drawable推全部屏幕合成态 |

分享目标处另做原始RGB差核验：after5第四行框内相对初图，浅/深分别改变7579/7581像素；after6完整privacy框内分别改变29147/29148像素。这些计数只证明真实画面发生变化，完整内容结论来自实图与对应目标行盒共同核验，不以“非零差”替代可读性。after6左栏恢复到初态是预期，并非旧冲突复发；第四行与privacy由各自滚动操作分别可达，不要求两栏同时停在两个after状态。

### 10.3 冻结对象及不能外推的边界

从现在起不再围绕这三张样张重复研究已关闭项。**有限冻结的是M1的原生设计输入**：80dp导航、16dp页边、24dp栏间距、8/16dp组间距、表格H8dp与日期0/16/16dp内距；已记录的字体角色/实际自然行高规则、同baseline单位、两主题语义色板；图表2dp墨迹保护及网格补集；大字局部滚动、固定操作的组织方式。原tokens/anchors保留 `proposed` 历史身份与 `productAcceptance=false`，本节记录其作为M1实现输入的设计选择，不伪称用户批准了不可恢复的ImageGen参数。

与原稿的有理由适配仍显式保留：实际应用区只有618.2222dp高而非原图比例；准备/分享图压缩为已选定的紧凑目标示意和0/6/12刻度，压力值不变；准备标准显示两条最近记录而非原稿三条，余项局部可达；1.3分享表末行/说明、库存流水允许局部滚动。原生图标为候选路径、纯色面为确定性语义色，不能冒称生成稿图标/渐变逐像素还原。深色是既定原生补充，不存在可用于精确逐像素比较的深色ImageGen原稿。上述选择已在锚点/先前封闭规格定义，本节不是用无裁切倒推认可，也不是新增任意容差。

不可恢复的原字体参数、`tnum`激活、实际TextView glyph墨迹/逐glyph字体等继续按明确对象记unknown；本次不把它们填成PASS，也不把它们升级为永不迁移的障碍。系统合成角部仍按§9双域协议处理，原display整图未mask；系统边界差异不用于豁免任何应用内容、字号、配色或布局差异。

**M1启动门已打开，直接按计划顺序分享→准备→库存，三页成组实施与验收，不再新增样张研究。** 保留真实1–4阶段/FlowRaw/SAF写入成功语义；Home保留Extraction/Curve路由、活动杯/停止、粉量attempted/confirmed及事件匹配；Inventory保留三事件、至少48dp详情触控与全部原回调。不得全局换主题波及其余21页，不把演示状态/虚构日期副说明复制为业务实值。M1生产页面仍须独立G1–G4、状态与业务回归；本节不更改24页范围，也不免除最大字号/横屏短高、后续表单IME或硬件验收。

### 10.4 IME短字段的有限独立复核

独立产品证据为run `39138935786`、head `a2164ad0ffaf661feab7d20f2e3499d7bb11eabf`，根 `build/task-first-evidence/ci-a2164ad-single/`。在三个 `single-screen-tablet-{landscape,large-font,user-landscape}` 对应profile的 `single-screen-window-checks/chinese-form-states/` 中，核中文浅/深的BrewReview dose、CurveEditor name共12组 `short-ime-observed-geometry.json`、`short-after-reveal-geometry.json` 与stability；另实看10张相关前后PNG。

全部在稳定IME后单次reveal变为完整可见，且固定按钮完整、窗口focus=true、无pendingLayout：landscape dose57/65→65/65、name58/65→65/65；large-font为60/65→65/65、62/65→65/65；user为102/117→117/117、106/117→117/117，浅深一致。四个相关生产Activity/布局文件从814e521到a2164ad无diff。故**当前没有依据实施生产布局预算修补**，保留52dp、字号、固定保存与原完整可见断言，维持“稳定→单次reveal→稳定→严格验证”。

只关闭本批“该流程后短字段仍裁切”的复现，不宣称原四次失败唯一根因已证：旧60/45px viewport没有重现，旧失败瞬时祖先/IME现场不足；新reveal前也可以稳定部分可见，不能把三次稳定draw当作自动完整可见。该批 `sameCompositorFrameProven=false` 保留，不冒称同合成帧或整页视觉通过。M2的完整设计/业务/其他短高状态验收继续按原计划。

## 11. BeanEntry独立M2待办：标准初态末字段1px预算不足

新run `39140082109` 的single-user为334/336；证据保留于 `build/task-first-evidence/ci-0a7b71a-single-user/tablet-user-landscape/`。中文浅/深BeanEntry均在 `ChineseFormWindowChecks.kt:407` 的初态 `inputs.forEach(::fullyVisible)` 失败，**尚未进入section、short或IME流程**；result为screenshots=0、sections=0、imeObserved=false。因此不存在本次short失败PNG/geometry/stability，不能把它描述为§10.4已关闭的BrewReview/CurveEditor问题复发。

实读顶层初图和 `single-screen-window-checks/visual-baselines/zh-Hans-light-BeanEntryActivity/BeanEntryActivity-typography.json`：最后“开封日期”输入origin `[1118,1142]`、size972×117，完整底应1259，visible底1258、localVisible高116。祖先 `task-form-scroll` 为1458×995、窗口 `[632,263,2090,1258]`，其内容自然高度996；117px末行越过scroll底1px。日历108px触控先通过；不是系统导航直接盖住末行。初图无IME，入口 `awaitPageReady()` 要求IME隐藏；但没有失败瞬时连续稳定draw与scrollY证据，不能把唯一成因夸称已证明。

**登记为M2独立待办，不中断M1，不改TaskFormSections，也不追加当前批次采集探针。** M2实际迁移BeanEntry时，初态先取稳定geometry/祖先/scrollY/IME/focus/pending现场，再严验五字段同时首屏完整及固定footer；不得先reveal绕过标准态门禁，不容差、不缩52dp/字号。若稳定仍为自然内容996>可用995，应在表单自然内容与固定操作预算/整数舍入处修正，并保留保存/取消与原业务，而非将1px隐去。旧失败产物继续保留；M1样张有限冻结不覆盖本生产页失败。
