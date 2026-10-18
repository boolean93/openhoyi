# M3 曲线库与导入三页视觉迁移计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本计划执行。Astra 负责设计/验收，Sol 负责生产/测试；主任务明确分配文件并统一构建推送。不得自行派生代理、修改其他 owner 文件或取消在途 CI。

**Goal:** 在 M2 完成限定验收、主任务明确切换后，迁移 CurveActivity、CurveImportActivity、CurveImportPreviewActivity，保留连续列表、本杯选择和显式导入/冲突流程。

**Architecture:** 沿用 M1 已限定冻结的原生字体、语义色、80dp rail、局部滚动和真实截图协议；仅三页 opt-in，不新建样张、不改变数据格式或全局默认。真实 Activity/model 决定所有数据、能力、冲突和路由。

**Tech Stack:** Kotlin、Android View/ListView/EditText/Canvas、已有 JVM/instrumentation、隔离 Mock CI。

## 0. 仓库与启动边界

遵守 [task-template](task-template.md) 与 [git-workflow](../git-workflow.md)。

- 来源 `develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；延续已授权 `feature/task-first-ui`，PR #4 → `develop`。本次源码只读基线 `026f9937f055342ba7256d2f446065b3bbdb0746`，文档 HEAD `8698bfbc928a1fee8bf21ad7881c36734a523cca`；M2 生产/测试正在同树修改，本计划不覆盖或提交其文件。
- M1 真实三页限定结论见 [M1 §11](2026-10-18-product-visual-migration.md)。M2 仍在实施，**本计划不授权提前实施 M3**；收到 M2 交审优先处理。原24页范围不变，不新增竖屏适配，也不锁方向规避窗口变化。
- 开始时重新核 HEAD/status、文件 owner 和已完成 M2 接口。普通推送由主任务合批执行；不直推 main/develop、不 force。branch-policy、verify、lifecycle、upgrade 必需检查及评审完成后走 PR。
- 不改 BLE/命令/算法/精度、导入 schema/MAX_BYTES/UTF-8、执行资格、库存、usage 排序模型、快捷槽位身份、未知记录或持久化格式。用于本杯仅保存原 selected 选择，不启动机器；预览不保存，冲突不覆盖。
- 不发版、不改包名/签名/versionCode、不安装或操作用户机器/秤。Mock 不证明硬件。回退用追加修正或 revert PR，保留原数据/草稿与未知结果，不清库、不重发。

## 1. 已读取的设计与现有证据

依据 [认可规格](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[严格基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、[当前24页矩阵](../evidence/task-first-page-matrix.md)。本次直接查看三个原 PNG，未重新调研或生成新图。

| 原图（`docs/research/task-first-ui-concepts/`） | 尺寸 | SHA-256 | 保留构图 |
|---|---|---|---|
| `curve-library-v2.png` | 1586×992 | `f5f298239f63481caa2a96587cec244aed2cde487412799c58bf81630fd0fc01` | 左搜索/排序/筛选/连续列表，右真实摘要与压力图，固定完整参数/用于本杯 |
| `curve-import-v2.png` | 1586×992 | `a77854b7731204fc54ec209659a68693dbdb36fb9bf03d83508f2cc0587f2199` | 两个等宽显式输入来源、原生大文本框、固定取消/预览；空框仅 placeholder |
| `curve-import-preview-v1.png` | 1586×992 | `6338a8fb47cbae4c7c91431f0ac857ab7a8e062fa09e3c190915da440d117199` | 左完整配方/图/参数表，右真实冲突卡、查看本机/副本/保留与范围说明 |

原图字体身份未知继续保留；使用已验候选字体，不无限研究。概念徽标、使用次数、配方描述、兼容能力及系统装饰不能伪造成真实业务。原图未涵盖的无冲突、只读、错误、深色/大字属于明确原生适配，不称原图精确参数。

历史限定证据须保留：

- Curve footer 的 a7fcc86 同行外框修正已由 a4786a0 用户 density360 与 density200 的 longFactory/noPressure 真实整窗关闭相应裁切。行间距在父行，禁止给主按钮单侧 topMargin 重现 CENTER_VERTICAL 越界。当前 G1–G4 尚未验。
- ImportPreview a4786a0 中文浅色四阶段：1.0 图表首屏完整；1.3 保留表并局部滚41px可达，右栏固定。保留原 `CurveImportPreviewLayoutPolicy` 全表优先、图至少160dp逻辑；不退回“表存在但完全隐藏”。
- 当前 single 连续列表返回/重建、advanced 240 fixtures/18 business paths 已运行，不代表本批新样式通过。M3 必须保留这些入口，新增截图不能取代它们。

## 2. 固定视觉输入与真实适配

沿用 `TaskProductVisualStyle` 的 font SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`，字距0、无伪粗、无额外 font padding；实际字号由 Android 换算并采 Layout，不按 fontScale 简单相乘。

| 角色 | sp / weight / lineHeight | 使用 |
|---|---|---|
| title | 24 / 500 / 36 | 页头、选中配方名称 |
| section | 20 / 500 / 30 | 图/参数表/冲突标题 |
| recipe metric | 20 / 400 / 30 | 温度/目标量；不套库存40sp |
| body | 16 / 400 / 24 | 输入、普通正文 |
| action / row title | 16 / 500 / 24 | 按钮、曲线列表名称 |
| aux | 14 / 400 / 22 | 标签、单位、来源、使用次数、说明、表头/表值 |

页边16dp、栏间24dp、关联8dp、组间16dp、面内16dp；面圆角12dp、输入10dp、边框1dp。颜色直接引用冻结 light/dark palette；选中态、警告/错误/禁用分别按真实 state，后两者实际色仍需对应状态证据。主操作至少56dp自然高，点击目标至少48dp；原有更严格目标不下调。短输入采用 M2 的自然52dp下限，不固定封顶；多行编辑保留真实两行及内距下限。

单位独立14sp，与数字实际 baseline 对齐；未知值仍“—”、无目标量仍原说明，不补0或假单位。标题/表格长内容先按自然宽换行、局部滚动，再使用已有完整详情披露；不把省略后的文本写回模型。

## 3. 每页一次性实施规格

### A. CurveActivity：原连续列表与真实能力

- 初次装配即使用已有 style Frame/Nav，不先安装默认 Frame 再切 style；body 采用16dp页边，保留 `TaskCollectionLayout` 浏览/详情分工和既有紧凑横屏返回语义。左约34%生产比例保留，24dp栏间距和分隔由本页承担，不改变共享 Collection 默认。
- 页头新建入口、搜索、排序、筛选、数量各有稳定区域。排序仍原 RECENT/MOST_USED 与存储键；筛选仍全部既有 category，允许横向滚动/原菜单，不伪造新分类。新建/导入/旧版入口全部保留。搜索原原生单行对象/TextWatcher 不换成弹窗；自然52dp，输入/图标内距按角色。
- 列表仍一个连续 ListView，不分页。行保留真实名称、类别/自定义来源、可执行或只读说明、真实 usage 次数与缩略图，16dp水平/8dp垂直内距、组内8dp；沿现有76dp最小行，不固定封顶。名称最多两行的旧披露边界可保留，选中详情可见完整路径；长辅助文本自然换行。选中surface/左accent、行分隔、chevron为装饰，不变选择对象。缩略图继续 existing compact 真实targets，不向78×48dp图硬塞14sp坐标；大图才 opt-in 冻结 renderer。
- 右页头名称、真实来源与可用性；编辑/分享常用入口宽度够则直接可见，更多保留完整参数/编辑副本/分享/快捷1–5槽位原回调与 enabled 条件。没有业务描述的数据不抄“适合日常意式”。
- 三列温度/目标量/阶段数标签14、值20、单位14同 baseline，8dp标签间隔及竖分隔；`CurvePreviewMetrics.present()` 的 draft/factory/captured 来源优先级与零目标语义保持。主图为真实压力 targets、蓝色语义，14sp真实 Paint、0/6/12、2dp保护及网格可见补集，不画时间轴。
- 固定底栏保留“查看完整参数”和“用于本杯”原触控对象/回调；可执行状态来自 `library.canStart(item)`，不与连接状态混淆。同行外框居中，间距放整行；无压力与只读保持真实原因，不画假压力。
- 短高仍允许现有真实参数文字 fallback + 完整详情，不将未显示图冒称图验收；常规1.0/1.3有压力主状态优先图与固定操作。测量一次完整自然元信息/标题/dock再分配图，不能反复隐藏元信息导致预算振荡。不得缩字或将固定操作滚走。
- `show/refreshList/onStart/onSaveInstanceState` 的独立 viewport、query/category/sort/selected/detailVisible 与原返回手势全部保留。点击预览不动 usage；用于本杯只改 selected；绑定槽位只沿原 factory+canStart 资格写对应槽位，不开始/停止/创建记录。

### B. CurveImportActivity：两个显式来源，同一原文编辑器

- 页头返回与标题；单句导语；等宽次要“从剪贴板粘贴/打开文件”原按钮24dp图标+8dp间隙。下一行左数据标题、右格式说明，空间不足自然两行；正文16，辅助14。不新增右说明墙或解析结果徽标。
- 保留唯一原生多行 EditText、原始空格/换行、输入类型、selection/focus/scroll与最大字节的恢复约束。无焦点空态只有原 placeholder，不放不合法示例JSON/行号。输入占余下内容 viewport，自身局部滚动；IME上方固定取消/查看预览，不整页滚动。
- 固定说明完整“未经确认，不会保存或启动萃取”；说明与按钮按实际自然宽判断同行，不用固定624dp/320dp猜测。空文本可按原稿禁用预览；非空文本不以新实时解析规则提前拒绝，仍点击后用原 decode 给原位错误。此空态可用性变化单独验证，不能删除非法输入路径。
- 显式粘贴才读 clipboard；文件仍 ACTION_OPEN_DOCUMENT、原mime/UTF-8严格解码/MAX_BYTES，取消或失败不覆盖现有草稿。保留文件读取成功后原直接进入预览行为，不因为原稿重新设计两次确认。预览前不入库，预览返回原文/选区不丢；取消不写任何曲线。
- 错误使用真实 error 节点，不能被 editor 权重挤出而不可达；错误和大字/IME需独立矩形/图，不拿主图推断。

### C. CurveImportPreviewActivity：完整参数与真实冲突决策

- 左/右约65:35、右最大400dp沿现有生产比例；左右surface、边框/24dp间隔按原稿与冻结palette。右警告只在 `existing != null` 的真实冲突存在时显示，不把同ID且完全相同当冲突。
- 左名称与真实 controlMode badge；温度/目标量两块16dp内距、标签14/值20/单位14同baseline；阶段数保留为标题辅助信息，不能删现有字段。长名自然两行及完整详情路径，元信息用实际自然预算，不能重演长名称叠加重复间距。
- 压力图使用真实doc targets及阶段四色，冻结renderer，至少160dp；表header+实际1–4行全部保留。表头/正文14，cell水平8dp，纵向按真实行盒+既有4dp内距，第一列阶段色点+名称，单位在列标题。FlowRaw 不硬画压力图、不换算猜测bar，保留原只读说明与完整原文详情。
- `CurveImportPreviewLayoutPolicy.allocate()` 继续先保留所有自然高文本/表，最后扩展图；唯一16/8dp组间距不重复。标准原条件下图+四阶段全表默认首屏完整；1.3/短高左内容可局部滚动逐行完整，右动作固定。不预滚动替代首屏、不减图/字/表格内距过关。
- 冲突卡保留真实警告标题及解释；查看本机版本、保存副本、保留本机三个既有动作完整，副本为主操作，保留为次要。非冲突使用原保存动作。完整详情/兼容与只读原因/“只保存到曲线库，不会启动萃取”均可达；错误不挤掉主操作。
- 沿 M1 Workspace 的现有显式固定副区 opt-in 能力实现右局部说明+固定操作；不更改其默认。极短横屏可用原局部滚动fallback，但每个操作自然触控高度完整可达，不整页连导航一起滚。保留 `finishedSave`、禁用→失败重新启用、运行时冲突重 render、saveCopy新ID、importDocument重复幂等；不得静默覆盖或自动选中。

## 4. 文件 owner 与实施顺序

主路径 `mobile/src/main/kotlin/io/openhoyi/mobile/`，测试路径 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/`。

| owner | 文件 | 边界 |
|---|---|---|
| 单一生产 Sol | `CurveActivity.kt`、`CurvePreviewMetrics.kt` | Metrics当前仅Curve调用；可显式style参数，默认null保持旧路径。模型/排序/筛选/槽位/选择不改 |
| 同一生产 Sol | `CurveImportActivity.kt`、`CurveImportPreviewActivity.kt` | 只页面构图/角色/动作展示；保留所有decode/IO/store/幂等。已有 `CurveImportPreviewLayoutPolicy.kt` 默认不改，若实际预算接口必要则同owner连测试一起列diff |
| 同一生产 Sol，复用不修改 | `TaskProductVisualStyle`、M2已验 `TaskProductFormStyle`、`HomeVisualIcon`、`CurveStageView`、`TaskPageFrame/Nav`、`TaskWorkspaceLayout`、`TaskCollectionLayout` | 沿现有显式opt-in；不改共享默认/不回写M1/M2。新图标若现有语义缺失只能新增明确kind，经owner协调；不能临时改全部按钮或renderer |
| 单一测试 Sol | 新 `CurveVisualMigrationChecks.kt`、新 `scripts/check_curve_visual_migration.sh`；`AdvancedBusinessUiChecks.kt`、`AdvancedPageChecks.kt`、`SingleScreenListStateChecks.kt`、`LanguageDetailChecks.kt` 必要增补；runner/workflow新增入口 | 不修改既有门槛/协议/M1 M2夹具；新scope独立审闭包；现有字段/标签优先保留，确需selector迁移只能精确语义不扩大匹配 |
| Astra | 本计划及主任务指定的matrix/review | 设计、静态审、真实 G1–G4，非生产实现 |

- [ ] 主任务确认M2限定结束并划M3 owner；核实际HEAD/status/M2helper接口，保存既有门禁结果。
- [ ] 测试先固定真实主fixture与原不变量；生产按 Curve→Import→Preview 成组落实§3，不按每个padding推送。
- [ ] 静态审一次核：共享default隔离、原本杯/槽位资格、输入原文恢复、冲突幂等、完整表预算/固定操作。源码冻结后统一构建，不与他人构建并行。
- [ ] `./gradlew :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.CustomCurveTest' --tests 'io.openhoyi.mobile.CurveOrderingTest' --tests 'io.openhoyi.mobile.CurveSearchTest' --tests 'io.openhoyi.mobile.CurveCategoryFilterTest' --tests 'io.openhoyi.mobile.CurveStageParametersTest' --tests 'io.openhoyi.mobile.CurveImportPreviewLayoutPolicyTest' :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain`；期望exit0，额外真实预算函数才补边界JVM，不给每个padding写镜像测试。
- [ ] 主任务合批普通push，保持single三个横屏profile、advanced/lifecycle全部原入口，并新增本批12真实主图/规定状态；不取消在途CI。
- [ ] Astra先浅1.0三页，再深1.0，再两主题1.3/滚达/IME/错误；仅真实差异返修，限定结论更新matrix，不重开样张或外推24页。

## 5. fixture、业务与原门禁

12主配置：中文、2560×1600/density360、light/dark×font1.0/1.3×三页；head/实际merge/APK/原图SHA与实际Insets/可用窗口各自记录。均真实Activity/model，不写TextView假数据。

| 页面主fixture | 必须覆盖的真实状态/不变量 |
|---|---|
| Curve：合法压力自定义“经典浓缩”，93°C/36g，targets20/90/80/60，注水150/250/150/50 tenths ml；真实库保留工厂/采集/自定义来源，选择该真实条目；usage来自合法scope观察，不写“24次”假展示 | 原连续103条相关fixture、RECENT/MOST_USED/筛选/query、选择前后viewport不动；详情返回/重建position+offset+selected一致。longFactory/noPressure/captured/unavailable/fourStage原LanguageDetail夹具继续。用于本杯仅selected变化；不可执行disabled且原因可读；快捷1–5仅合资格factory。编辑/分享/导入/旧版路由不丢 |
| Import：空原生文本框主图；合法文档通过真实粘贴/输入建立另图 | 未显式粘贴不读clipboard；原3非法格式/schema/范围失败；原文空格/换行、focus/selection/scroll重建；IME下固定取消/预览；文件取消、合法UTF-8、不可读/超限/非法UTF-8分别不写库，失败不清原文 |
| Preview：与已有合法doc同ID但内容不同的真实冲突；名称“经典浓缩（新版）”、93°C/36g、pressure targets30/90/70/50、水量150/250/150/50 | 原本机doc先合法存入scope，输入doc不先存。四阶段标准全表首屏/大字逐行可达、图>=160、右固定。无冲突保存、完全相同重复、冲突查看/保留/副本、运行时冲突、写失败再启用、FlowRaw只读/非法document。检查store内容/原ID未变/副本新ID，不仅看toast |

新scope按已验10Lazy闭包逐项核定，尤其 `CurveActivity.onCreate` 会初始化history做usage迁移；必须是scoped history及其全部callback，不触原initializer。完整内存模型+owned cache、关闭Activity/对话框/服务→新writer drain→恢复原Lazy/language/failure与明确白名单prefs；原业务盘hash不变。Curve另写 `curves.sort/selected`，快捷测试写 `presets` 对应槽位：只备份/恢复实际触及键的存在性与类型，物理SHA另记，禁止clear整库/删除文件追hash。clipboard测试在明确scope操作内备份恢复；不修改产品自动读取时机。新scope不借用M1/M2私有对象或改其入口。

**保留原门禁，不下调：**

1. `LanguageDetailChecks.curves()` 的真实footer外框完整/同行中心检查及其成功整窗；不以组件截图或只测enabled替代。
2. `SingleScreenListStateChecks` Curve真实手势列表、query/category/MOST_USED/selected与返回/重建锚点；不分页、不把原selected自动清空来通过。
3. `AdvancedPageChecks.verifyImportPreviewTable()`：header+4行存在，图至少160dp；宽≥1000dp、高≥600dp、font≤1.1原条件下图/全表首屏完整；其余配置逐行完整且无ellipsis、右区位置不随左滚变化。恢复scroll实测。旧采图方法不伪称协议2，新主图走已验原协议。
4. `AdvancedBusinessUiChecks` 原18路径继续，其中paste-preview-save/repeat-idempotent/conflict-keep/conflict-save-copy/invalid-three及rawdraft选区重建不删除。新SAF状态只增缺口，禁止借视觉改decode或MAX_BYTES。
5. 所有用于本杯/保存/副本业务仅隔离Mock；记录session/usage/inventory未被视觉动作额外修改、无start/stop。主图仍真实整屏；IME几何稳定不等于合成同帧，保留未知，不mask。

## 6. G1–G4、证据与收口

每配置保存原整屏PNG/manifest、fixture/节点rect、真实Layout/字号字重单位baseline、drawable实际state颜色及alpha、Canvas真实targets/网格补集、协议2proof/原PNG哈希、滚动前后与恢复/业务报告。沿现有探针，不增加无法观测字体身份研究。

| 门禁 | 本批独立核验 |
|---|---|
| G1 | 原稿左右/字段顺序、16/24/8/16间距、列宽/表格内距、实际自然预算、连续列表、固定footer；标准首屏与大字滚达分开 |
| G2 | 候选字体SHA、实际weight/letterSpacing/fakeBold/中文及单位角色；ImageGen字体身份与tnum仍unknown，不虚称精确识别 |
| G3 | 14/16/20/24角色实际sp→px、实际Layout行盒、数字单位baseline、52dp输入自然下限、图文字不重叠；不以getter lineHeight冒充实际行盒 |
| G4 | 两主题page/surface/selected/border/primary/secondary/disabled，冲突warning和error状态；resolved配置与原PNG纯色内区独立核，不把drawable色当合成色 |

抗锯齿边缘差异不作为改色理由；真实间距/字号/行盒/背景差异不得归咎抗锯齿。原图未知、深色适配与真实无数据状态按上述边界明确记录。完成后Astra仅给有证据三页/状态限定结论；硬件、其他页面和未测状态继续待验。当前仅规划，M3未实施/构建/运行/验收，不授予任何PASS。
