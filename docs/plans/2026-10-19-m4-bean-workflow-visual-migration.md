# M4 用豆、批次与流水三页视觉迁移计划

> **For agentic workers:** 按 `superpowers:executing-plans` 执行。Astra 设计/验收，Sol 分别负责生产与测试，主任务协调文件、统一构建及推送。不得覆盖其他 owner 文件或取消在途 CI。

**Goal:** 在 M2、M3 按既定顺序完成限定验收并由主任务启动后，迁移 BeanPreparation、BeanBatch、BeanLedger 三个真实页面；保留选择、确认扣减、调整预览和只读流水的原业务边界。

**Architecture:** 仅这三页 opt-in 已冻结 M1 原生样式，复用已提交 M2 表单接口；真实 Activity/model/render 与既有隔离 Mock 取证。无新样张、功能、设备入口或全局主题替换。

**Tech Stack:** Kotlin、Android View/EditText/ScrollView/Dialog、现有 JVM/instrumentation、API34 原生窗口双域取证。

## 0. 任务模板与启动边界

遵守 [task-template](task-template.md) 与 [git-workflow](../git-workflow.md)。

- 来源 `develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；继续已授权 `feature/task-first-ui`，PR #4 → `develop`。只读规划 HEAD `95be8fb`，M2 生产 `5c495e8`、测试 `b77c704` 已提交；M2 frame `39149210695` 在途，不把本地构建当视觉通过。本计划不授权提前启动 M3/M4 代码。
- 启动前重新核 status/HEAD、已验 M2/M3 接口、owner 与原门禁。M2 新证据优先，不因后续规划耽误当前缺陷闭环。固定24页、中文横屏优先；保留横屏短高/大字/IME，不新增竖屏工作、不锁方向。
- 推送：只提交所属文件 → 相关验证 → 主任务普通推送 → PR 必需 branch-policy/verify/lifecycle/upgrade 及评审。禁止直推 main/develop、force、取消 CI。当前只写计划，不构建、提交、推送或设备操作。
- 不改 mg 精度、库存/准备 schema、事件 ID、秤稳定策略、BLE/命令/未知结果。既有手工输入与真实捕获边界不变。Mock 不代替秤或机器验收。
- 不发版，不改包名/签名/versionCode，不安装。回退用追加修正或 revert PR；保留所有原库存、准备意图、日志及未知结果，不清库、不退款、不自动重发。

## 1. 原稿与已选输入

依据 [认可规格](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[M1 §11](2026-10-18-product-visual-migration.md)、[M2 接口](2026-10-18-m2-form-visual-migration.md)、[24页矩阵](../evidence/task-first-page-matrix.md)。三个原 PNG 已查看并读取尺寸/hash，没有新图或新研究。

| 原稿（`docs/research/task-first-ui-concepts/`） | 尺寸 | SHA-256 | 保留构图 |
|---|---|---|---|
| `bean-preparation-v1.png` | 1586×992 | `ff154e547f7a8ff73be115e10560646aa4754d18b5807bd6b84d554735701cd7` | 左批次选择、右真实本杯用量/库存前后、固定显式操作 |
| `bean-batch-v2.png` | 1585×992 | `81ccf1510d6f780bd0e1e83b06130b9521820f2d51a89e045cfe4841158614ca` | 名称/批次、余额与初始量/比例、三日期、最近流水、固定调整 |
| `bean-ledger-v3.png` | 1586×992 | `114940f8efb35918334c5281d705e1a981394bb989110f617f9a0c7b4cf2f6ba` | 批次余额摘要、最新在前、类型/变化/余额/批次/原因五列、返回批次 |

冻结候选字体源/语义色直接复用 `TaskProductVisualStyle`；原图字体身份未知仍明确，不无限阻塞。title24/500/36，section20/500/30，body16/400/24，action16/500/24，aux14/400/22，大余额40/700/60；0字距、无伪粗、无 font padding。字号依真实 Android 换算，采实际 Layout，不按1.3简单乘算。数字与14sp单位用真实 baseline，不能用缩放 span 冒充同基线。

rail80、页边16、栏间24、组间16、关联8、面内16、圆角12、输入圆角10、描边1dp；沿冻结 light/dark page/surface/selected/disabled/ink/muted/accent。原有批次选择72dp不下调；流水/详情触控至少48dp，短输入自然高至少52dp，主操作至少56dp且不封顶。字段或按钮自然变高时局部滚动，不缩字/负边距或隐藏固定操作。

**真实适配，不伪造原稿数据：**

- 用豆原稿的减/加步进不是现有功能，不新增；保留真实数值 EditText。原稿“稳定18g”只能来自真实捕获状态，Mock 保持捕获禁用及真实限制说明。概念“批次日期”不能从模型猜测，已有采购/烘焙/开封日期逐项明确标签，缺值沿原“—”。
- 准备的“保存选择”和“确认扣减”是两个真实阶段，不合成一个按钮。确认、重试、放弃、关联恢复按真实 attempted/confirmed/shotId/associated 显示。
- InventoryEvent 没有时间戳或直接本杯归属字段；流水按原 append 顺序倒序，不造日期/时间/杯号。正常耗用使用正文色，不当异常警告；新增/调整/错误只用真实类型/状态。

## 2. 文件所有权与默认隔离

| Owner | 允许文件 | 不得顺带修改 |
|---|---|---|
| 生产 Sol | `mobile/src/main/kotlin/io/openhoyi/mobile/{BeanPreparationActivity,BeanBatchActivity,BeanLedgerActivity,DoseBalanceView}.kt`；确需新文案时独立八语种 `values*/bean_workflow_visual.xml` | M1/M2/M3 已验 Activity、全局资源/主题、共享 bean helpers 默认行为 |
| 测试 Sol | 新 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt`、新 `scripts/check_bean_workflow_visual_migration.sh`；必要增量 `AdvancedBusinessUiChecks`、`SingleScreenListStateChecks`、`BrewAudioInstrumentation` 与现 task-frame workflow | 原断言/fixture/profile/协议降级；其他 owner 的生产文件 |
| Astra | 本计划及明确授权的矩阵/统一验收记录 | 源码、设备、共享 index 未协调提交 |

`DoseBalanceView` 当前仅用豆页调用；若追加 style 参数，默认 null 完整保留旧分支，仅本页传入。共享 `TaskPageFrame`/`TaskWorkspaceLayout` 仅使用既有 opt-in，不能全局改默认；共用 `beanRemainingRatio` 不因本批变色或改高度，必要时在本页画展示层。复用 M2 `TaskProductFormStyle.input/label/unit/footer`，保留原输入对象、inputType/filter/listener/selection；unit 只构造期包裹，不在测量或 IME 切换时 reparent。

不修改 `BeanPreparation.kt`、`bean-core/Inventory.kt`、`BeanDoseCapturePolicy.kt`、`ScaleOwnerActivity`（位于 ScaleActivity.kt）、ScaleActivity 或 MobileService。若实际接口缺口超过以上范围，先报告具体依赖，由主任务分配，不能暗中增加桥接。

## 3. 实施顺序与不可变业务

### A. BeanPreparation：真实状态驱动两栏

1. 左连续批次列表保持当前选择、72dp触控及 `dose-batch-*` / `dose-batch-summary-*` 身份。真实名称/批次/余额分层，选中背景与 M1 库存一致；长名自然换行、完整详情沿现入口，不新增假日期。
2. 右侧名称、用量、真实捕获说明、库存预览/回执按当前状态分组。只有编辑态显示数值输入；确认后使用真实锁定摘要，不能遗留可编辑外观。预览与回执继续分开 `dose-balance-preview` / `dose-balance-receipt`；回执必须取匹配 eventId/batch/kind/delta 的实际流水，不将预测当事实。
3. 右栏局部内容滚动、主操作/返回固定；标准双栏外容器无多余滚动，短高采用既有合法 fallback，但固定操作必须完整且自然高。禁用捕获、错误/未知态说明不被塞进主按钮前的重复大卡；保留真实限制信息可达。
4. 保持 nextEventId/draftBatchId/draftAmount、恢复与所有监听：选择仅保存意图不扣减；confirm 先持久化 attempted，再按稳定ID consume；重复确认不二扣。attempted 未决不变回普通选择；discard 仅显式原流程且不退款；关联重试只核权威流水并补原 journal，不产生新消费。不启动萃取/秤连接，不自动重试。

### B. BeanBatch：摘要、日期、最近事件与原调整对话框

1. 顶部真实名称/批次、40sp余额+14sp单位，初始量/比例独立层次；保留已知三日期，16dp列内净空，行分隔画在已有留白中。不得因长名增加重复身份框或压缩数字。
2. 最近事件仍取本批逆序最多3条；三列类型/变化/原因，至少48dp真实详情行，保留完整详情回调、长原因披露。标准原100→90、两事件夹具首屏完整；第三条/长内容在局部列表可达，不删记录来拟合原图。说明与调整操作固定，完整流水入口保持原 batchId 路由。
3. 调整仍为**目标余额+必填原因 → 前后/差额预览 → 显式确认**两个原生对话框。只迁字体/背景/单位/间距；保留无变化拒绝、负数/非法/超长原因拒绝、取消不写、预览不写、确认重读余额与并发冲突提示、单次事件ID与 committed 防重复。原目标/原因草稿与重建重开恢复不丢失。

### C. BeanLedger：真实只读五列表

1. 有 batchId 时显示该批名称/余额/初始量与返回批次；无 batchId 保持全库语义，不杜撰统一余额。固定表头五列、表格 H8 内距，必要列保留16dp净空；顺序按事件原逆序，不按不存在的日期排序。
2. 保留 ADD/CONSUME/ADJUST、符号、实际 balanceMg、真实批次号/名称、reason 或“—”。48dp详情目标、已有最多两行省略与完整明细披露保留；局部列表滚动，返回/重建的原 savedScrollY 不变。没有编辑、撤销、退款、删除、导出或新增筛选。

三页逐项静态审后一次合批构建；不把每个装饰微改各自推送。发现实际自然预算不足先记录窗口、行盒、祖先/内容自然高，再一次性调整语义分组，禁止靠容差/初始滚动通过首屏要求。

## 4. 最小真实夹具与隔离

三页×浅深×font1.0/1.3共12主图，中文用户2560×1600/density360，实际 app窗口/insets/fontScale另测。每页/profile独立 Mock 进程与合法内存模型，所有图必须真实 Activity，不修改 TextView 假造数据。

| 页 | 固定主夹具（独立库，不强求三页同一状态） | 必补真实状态 |
|---|---|---|
| Preparation | 三真实批次；选中批次250g经两次合法18g consume余额214g；当前手工18g未保存，预测196g；无设备/Mock捕获禁用 | 保存选择不扣减、确认后实际214→196回执、重复确认/重建不二扣、取消/显式放弃不退款；非法/不足余额/存储拒写/未知意图和关联恢复沿既有模型测试与可构造UI状态核对 |
| Batch | 单批初始100g、合法adjust−10g原因“盘点修正”至90g；采购2026-10-02/烘焙2026-09-28/开封2026-10-03 | 两事件首屏；三事件/长原因完整详情；目标/原因输入与IME/草稿重建、预览取消、成功与并发余额变化/存储失败不误报 |
| Ledger | 单批250g、两次18g consume至214g；逆序三条214/232/250，reason空显示“—” | 全库/单批过滤、ADJUST长原因、missing批次原处理、非零滚动返回/重建、详情关闭不改顺序/数据 |

测试隔离沿已审 M1/M2 契约，明确10个 Lazy：feedbackPreferences/logs/curves/samples/history/customCurvesResult/curveUsageResult/beanInventoryResult/beanPreparationResult/journalStoreResult；原 Lazy 只存身份/initialized、不求 value；legacy两Lazy不触发。合法内存库、history callbacks全指向scope，trace/samples仅 owned cache。语言内存，appearance.dark/curves.selected逐键类型与存在性恢复，不 clear；业务盘精确hash，白名单prefs物理SHA单列。

**用豆页服务边界须精确复用：** `ScaleOwnerActivity.onStart` 只 `bindService(...,0)` 绑定已存在的单服务，并每250ms刷新；不会 AUTO_CREATE。主图无服务时显示真实等待/不可捕获，不为截图启动服务。若既有 business 路由打开 Home 导致合法 Mock service启动，只允许原单owner及测试隔离依赖；实施前列实际持有的scoped引用，不套M2“完全无服务”。所有 Activity/dialog destroyed/解绑与 onStop回调移除 → stop并确认scope服务destroyed → 新writer/trace退出 → 恢复原对象/偏好 → 身份/initialized/原盘核对。失败停止、不回滚文件，host最终force-stop。不得伪造 VERIFIED_TRANSPORT、关闭Mock捕获guard或进入真实BLE。

## 5. 验证、证据与四项门禁

- 本地：生产/测试静态成组审；`./gradlew :bean-core:verify :mobile:testMockDebugUnitTest --tests io.openhoyi.mobile.BeanPreparationTest --tests io.openhoyi.mobile.BeanQuantityTest --tests io.openhoyi.mobile.BeanDoseCapturePolicyTest :mobile:assembleMockDebug :mobile:assembleMockDebugAndroidTest`。仅实际新算法需要针对性测试，不写镜像断言。日志/实际suite数与精确源码SHA另记，不预称成功。
- 原 advanced 18业务路径全部保留，特别 adjust-cancel/adjust-confirm-preview、dose-invalid-no-write/select-no-deduction/confirm-repeat-recreate/discard-no-refund；原日期/库存两事件、单屏控制完整、列表14夹具的BeanLedger返回/重建、各语言/字号/生命周期门禁不得替换。只在确实改为容器时按精确tag+真实clickable修 selector，不弱化业务断言。
- 新入口复用 `NativeWindowFrameEvidence` 已验protocol2，保留原样张/M1/M2/M3入口与失败产物。首图先稳定而非先reveal；初图和局部滚后分别记录实际几何/可达/恢复。IME/对话框按真实边界记录，未证同帧不冒称同步；不得加sleep、mask或容差。
- G1：同状态同窗口对照原图及上述适配；测页边/组距/列内距、实际自然高度、完整主操作、原72/48/52下限、两事件首屏、流水与状态说明可达、非零滚动恢复。数据/错误/禁用状态不得只靠主图外推。
- G2：固定font资产SHA/实际Typeface角色与Paint来源；原图字体身份、不可观察glyph fallback仍unknown，不以对象equals或“看起来像”作证。
- G3：逐TextView实际字号/字重/字距、Layout行盒/baseline、单位同屏baseline；图像上的换行/裁切与字段值共同检查。getter lineHeight不是实际行盒，数字equal-advance不证明tnum启用。
- G4：浅深页面/面板/选中/禁用/错误/边框与文字的配置ARGB、alpha和原屏纯色区共同核对；ripple/描边未知保留，不把配置值冒充屏幕合成色。
- 每fixture保存source/head/merge/APK/原稿hash、profile/insets、完整PNG、实际Typography/anchors/业务结果/滚动后PNG/隔离结果。缺证记 EVIDENCE_PENDING，实际差异记FAIL；Astra逐张实看后才限定通过，不写24页/硬件全部通过。

## 6. 当前完成与未完成

已完成：原稿/真实三Activity、DoseBalanceView、ScaleOwner绑定行为、BeanPreparation与Inventory模型、原业务/列表及JVM入口只读核对；所有权与设计适配如上。

未完成：M2在途证据验收、M3实施与验收、M4启动授权/代码/构建/真实12图/特殊状态运行与G1–G4。原稿不存在的时间戳、步进/稳定值不会通过造数据补齐；硬件稳定捕获保持独立设备验收范围。本计划不是实施完成报告。
