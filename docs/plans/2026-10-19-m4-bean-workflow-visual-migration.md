# M4 用豆、批次与流水三页视觉迁移计划

> **For agentic workers:** 按 `superpowers:executing-plans` 执行。Astra 设计/验收，Sol 分别负责生产与测试，主任务协调文件、统一构建及推送。不得覆盖其他 owner 文件或取消在途 CI。

**Goal:** 按2026-10-19主任务已授予的M4独立全三页生产/测试授权，迁移 BeanPreparation、BeanBatch、BeanLedger 三个真实页面；不以批次编号强制等待M2/M3视觉验收，保留选择、确认扣减、调整预览和只读流水的原业务边界。

**Architecture:** 仅这三页 opt-in 已冻结 M1 原生样式，复用已提交 M2 表单接口；真实 Activity/model/render 与既有隔离 Mock 取证。无新样张、功能、设备入口或全局主题替换。

**Tech Stack:** Kotlin、Android View/EditText/ScrollView/Dialog、现有 JVM/instrumentation、API34 原生窗口双域取证。

## 0. 任务模板与启动边界

遵守 [task-template](task-template.md) 与 [git-workflow](../git-workflow.md)。

- **当前状态索引（2026-10-19）：** HEAD `9b51936` 已包含M2候选修复与M3完整生产/独立取证入口，冻结构建与31 tests通过，真实CI/视觉验收仍在途；M2未通过。下述原95规划时点保留历史，不作为当前启动依赖。依赖审计后，主任务已正式授权两Sol分别启动M4完整三页生产与独立12配置测试；独立CI不堆原frame。本授权取代原按M2→M3→M4编号等待的启动条件，§7列实际接口与隔离边界。
- **历史规划时点（非当前限制）：** 来源 `develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；继续已授权 `feature/task-first-ui`，PR #4 → `develop`。只读规划 HEAD `95be8fb`，M2 生产 `5c495e8`、测试 `b77c704` 已提交；M2 frame `39149210695` 在途，不把本地构建当视觉通过。当时本计划尚未授权提前启动M3/M4；此限制已被上述2026-10-19正式授权取代，保留本句仅供追溯。
- 启动前重新核 status/HEAD、冻结但未全验的M2接口、owner 与原门禁；不能把共享helper编译通过写成M4候选状态已验。M2 新证据优先，不因后续规划耽误当前缺陷闭环。固定24页、中文横屏优先；保留横屏短高/大字/IME，不新增竖屏工作、不锁方向。
- 推送：只提交所属文件 → 相关验证 → 主任务普通推送 → PR 必需 branch-policy/verify/lifecycle/upgrade 及评审。禁止直推 main/develop、force、取消 CI。Astra仅维护计划并做设计/验收；Sol实施、统一构建及提交推送由主任务协调，本计划不单独授权设备操作。
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
| 生产 Sol | `mobile/src/main/kotlin/io/openhoyi/mobile/{BeanPreparationActivity,BeanBatchActivity,BeanLedgerActivity,DoseBalanceView}.kt`；确需新文案时独立八语种 `values*/bean_workflow_visual.xml` | M1限定已验及M2/M3待验Activity、全局资源/主题、共享 bean helpers 默认行为 |
| 测试 Sol | 新 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt`、新 `scripts/check_bean_workflow_visual_migration.sh`；必要增量 `AdvancedBusinessUiChecks`、`SingleScreenListStateChecks`、`BrewAudioInstrumentation` 及新独立 `.github/workflows/bean-workflow-visual-ui.yml`；不向现 task-frame workflow追加本批12配置 | 原断言/fixture/profile/协议降级；其他 owner 的生产文件 |
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

- 本地：生产/测试静态成组审；`./gradlew :bean-core:verify :mobile:testMockUnitTest --tests io.openhoyi.mobile.BeanPreparationTest --tests io.openhoyi.mobile.BeanQuantityTest --tests io.openhoyi.mobile.BeanDoseCapturePolicyTest :mobile:assembleMock :mobile:assembleMockAndroidTest`。仅实际新算法需要针对性测试，不写镜像断言。日志/实际suite数与精确源码SHA另记，不预称成功。
- 原 advanced 18业务路径全部保留，特别 adjust-cancel/adjust-confirm-preview、dose-invalid-no-write/select-no-deduction/confirm-repeat-recreate/discard-no-refund；原日期/库存两事件、单屏控制完整、列表14夹具的BeanLedger返回/重建、各语言/字号/生命周期门禁不得替换。只在确实改为容器时按精确tag+真实clickable修 selector，不弱化业务断言。
- 新入口复用 `NativeWindowFrameEvidence` 已验protocol2，保留原样张/M1/M2/M3入口与失败产物。首图先稳定而非先reveal；初图和局部滚后分别记录实际几何/可达/恢复。IME/对话框按真实边界记录，未证同帧不冒称同步；不得加sleep、mask或容差。
- G1：同状态同窗口对照原图及上述适配；测页边/组距/列内距、实际自然高度、完整主操作、原72/48/52下限、两事件首屏、流水与状态说明可达、非零滚动恢复。数据/错误/禁用状态不得只靠主图外推。
- G2：固定font资产SHA/实际Typeface角色与Paint来源；原图字体身份、不可观察glyph fallback仍unknown，不以对象equals或“看起来像”作证。
- G3：逐TextView实际字号/字重/字距、Layout行盒/baseline、单位同屏baseline；图像上的换行/裁切与字段值共同检查。getter lineHeight不是实际行盒，数字equal-advance不证明tnum启用。
- G4：浅深页面/面板/选中/禁用/错误/边框与文字的配置ARGB、alpha和原屏纯色区共同核对；ripple/描边未知保留，不把配置值冒充屏幕合成色。
- 每fixture保存source/head/merge/APK/原稿hash、profile/insets、完整PNG、实际Typography/anchors/业务结果/滚动后PNG/隔离结果。缺证记 EVIDENCE_PENDING，实际差异记FAIL；Astra逐张实看后才限定通过，不写24页/硬件全部通过。

## 6. 当前完成与未完成

已完成：原稿/真实三Activity、DoseBalanceView、ScaleOwner绑定行为、BeanPreparation与Inventory模型、原业务/列表及JVM入口只读核对；所有权与设计适配如上。

已启动：主任务已授权M4三页生产与独立完整12配置测试，两Sol分别执行。

未完成：M2/M3真实证据验收、M4代码/构建/真实12图/特殊状态运行与G1–G4。M3生产与取证入口已实施冻结，不再列作未开工；本页不据此追认M2或M3通过。原稿不存在的时间戳、步进/稳定值不会通过造数据补齐；硬件稳定捕获保持独立设备验收范围。本计划不是实施完成报告。


## 7. 9b51936 真实依赖复核与M4正式启动

**结论：不存在必须等M2/M3视觉通过的代码依赖；主任务据此已正式授权M4完整三页生产与独立12配置测试，分别交生产/测试Sol启动。** 现有三Activity与 `DoseBalanceView` 不引用M2三页的布局对象，也不引用M3 Activity/Metrics/导入策略。消费的是已经提交的M1 `TaskProductVisualStyle`、Frame/Workspace显式opt-in，以及M2 `TaskProductFormStyle.input/label/unit/footer`；这些接口已编译，但M2完整运行仍未验，M4必须独立验证实际输入/单位/焦点/固定操作。若共享helper随后需要修复，由主任务单owner串行处理并回归受影响批次，不在M4偷偷复制或修改默认实现。

- **无生产文件交叉：** 本批仅§2列三Activity、DoseBalanceView和独立八语种XML。`TaskCurveEditorForm`、M2三Activity、Curve/Metrics/Import/Preview不在修改范围。DoseBalanceView实际仅用豆页三处使用，style参数必须默认null原路保留。`BeanPreparationActivity`空库“添加豆子”只保留原BeanEntry路由，不迁移或以该入口替代M2验收。
- **共享测试必须串行：** 新BeanWorkflowVisualMigrationChecks/runner是独立文件；`BrewAudioInstrumentation`与必要的AdvancedBusiness/SingleScreenListStateChecks是交叉接口，主任务指定单owner、前一批冻结后再接入，不改M2/M3入口与断言。现task-frame已连续原24/样张/M1/M2，本批已批准使用独立CI，不向原frame追加12配置；新workflow由测试owner接线并由主任务统一静态审/构建。保留完整12和旧门禁，沿M3的独立运行方式记录实际SHA/APK/fresh-process与失败原件，不靠增大timeout或少测页数。
- **服务隔离是实际前置，不是批次编号依赖：** BeanPreparation继承ScaleOwnerActivity，onStart以flags0绑定既存服务、每250ms仅刷新capture/guard，onStop移除refresh并解绑。主fixture必须独立Mock进程、无原Activity/无服务、10Lazy整组合法内存安装完后启动；无服务状态真实显示等待与捕获禁用，不启动或暂停服务/handler制造截图。父类owner/bound/visible/refresh与全部Activity/dialog销毁后需核解绑/回调移除，再关闭本scope writers、恢复原对象/白名单偏好与核原盘。Batch/Ledger直接只读库存，调整/确认写入也只作用于合法内存scope。
- **恢复/关联闭包：** 用豆关联重试调用 `MobileApplication.reconcileJournal()`，所用journal/history/preparation/inventory必须全部是同一scope，不能只替换库存。三主图无需经过Home；若补原业务确需Home路线导致Mock单服务启动，先列该服务实际持有的scoped对象与onDestroy排空证据，再允许该单独测试状态，不套“无服务”结果。未知/未决/拒写场景不自动重发或回滚原文件。
- **执行优先级：** M2新frame与M3独立12原件到达即优先设计验收；在途源码SHA原件保留，M4推进不能关闭其FAIL。完成M4独立静态部分后仍需合批构建/12真实图及原业务门禁，不能因沿用候选helper而继承PASS。

Gradle入口已据当前 `verify.yml`、module任务与真实测试文件校正为 `testMockUnitTest` / `assembleMock` / `assembleMockAndroidTest`，无MockDebug变体；本次只改计划，没有执行构建、设备或生产修改。

## 8. 首版成组静态审查与封闭收口（2026-10-19，未构建/未图审）

Astra已读四个生产Kotlin完整差异，并重新对照三张既有原稿；八语种 `bean_workflow_visual.xml` 独立解析通过，均为selection/preview/receipt三key，未添加不存在的稳定读数/时间戳。首版保留Preparation原确认/关联/放弃路径与ScaleOwner绑定，Batch调整预览/确认与Ledger原只读详情；DoseBalance默认null旧分支隔离。以上仅静态核验，当前不得写M4通过；新12配置与原全部门禁未运行。主任务已批准以下一次性收口，由生产Sol实施，Astra不改源码。

### 8.1 已批准的自然预算重排

- Preparation完整粉量label与原输入/独立单位构造期保持固定子树。先量完整label及输入自然最低宽，足宽同行、8dp相隔，不足纵排；52dp输入下限、原值/监听/selection不变。这对应原稿粉量同行，不是缩字号换空间。
- Batch页header保留24/500标题、真实name·batch16/400、原完整详情48dp入口；实宽足时标题/身份同行16gap，不足身份副行。日期区heading20/500与三日期足宽同行、16gap；不够宽恢复标题上方，日期标签和值原角色与16列净空保留。完整流水48入口合入recent标题行logical-end，16gap。固定footer完整说明与主56动作自然足宽同行，不足换行；唯一top8留白内画分隔，不再独立叠大高度。
- Batch summary优先原label在上/值在下；仅完整纵排自然高无法保两条真实48dp事件且三cell均自然宽足时，label/value改同行8gap。每项实际自然高/宽、选择分支和viewport都记录，不能拿静态526dp等预算数字当首屏通过。

### 8.2 首版必须修正后才可终审

1. **Batch初始量角色：** 首版借用shareMetric20/400未经设计批准。恢复页内24sp/400/36sp角色、独立g14；余额40/700/60不变。原稿初始量明显高于辅助指标，且本批约定保原角色，不能为了fit降至20。这个明确原生角色不声称识别了ImageGen字体数值。
2. **Batch短高可达：** 首版仅recent列表滚动，summary/date/标题/表头自然高可能把weight事件viewport挤为0。采用构造期一次安装的永久中部ScrollView：足高child EXACT实际viewport保原局部事件scroll；实际resolved chrome加至少一条真实48dp事件自然高仍超过viewport时，仅中部自然高、最多3条事件按自然高，固定footer在外。原标准两事件首屏门禁不豁免，第三条仍保留可达；记录完整两事件预算、最小可达预算与实际分支，不预滚截图通过。
3. **Ledger摘要横向完整：** 首版四个WRAP节点永久横排比旧可换行TextView缺少不足宽路径。改为两个永久“label+number+unit”组，真实自然总宽足时同行16gap，不足纵排8gap；数字/单位不拆、不缩字，原值和返回批次路由保持。
4. **Preparation顺序：** 原稿为选中身份→粉量→秤捕获/状态→库存预测。首版将捕获放在预测之后，须移回粉量之后；真实hint/guard全部保留，preview前16组距，固定保存/确认动作仍在外。缺秤只显示真实等待/不可捕获，不照概念稿填“稳定18g”。
5. **G4状态色：** DoseBalance after数字与g共同accent；批次选择bean图标selected用accent、未选用ink，实际selected背景继续原状态。Ledger仅ADD的类型/变化使用accent，正常CONSUME正文ink、ADJUST不按正负猜危险色。Batch/Ledger表头aux14/500 muted，正文16不变。配置ARGB仍须下一轮原屏纯色区独立核验。
6. **分隔和动作语义：** Preparation行底border1dp与分组线画在现有8/16dp留白中，不叠新高度。首版通用action对所有primary画coffee、所有secondary画back不符合确认库存语义：只返回用back、添加豆子用bean，保存/确认映射原稿check；其它动作不硬套咖啡/返回图标。主任务已明确授权HomeVisualIcon最小新增check case：24dp坐标路径(5,12)→(10,17)→(20,6)，1dp实线；默认及其它kind/页面调用不变。保存选择/确认用check，关联或扣减重试、放弃/取消选择可保文字，不硬套错误图标；沿既有8gap/currentTextColor/居中helper，不改回调。不能以未知kind画空图或扩全局。

本节是有限设计/实现审查意见，不是运行FAIL证据，也不改变两事件首屏、72/48/52/56下限、三条事件保留、原业务写入/不重扣和完整12配置范围。M2/M3原件到达优先验收；M4半成文件不混入其它批次定点修复的构建或提交。

### 8.3 收口后生产与测试静态终核（未构建、未运行）

Astra已终核当前四个生产文件、`HomeVisualIcon`唯一check case、八语种三key，以及完整五个测试/入口文件。§8.2六项均已落实：Batch初始量24/400/36；永久中部fallback与外置footer；Ledger两语义组自然flow；Preparation真实顺序、状态色与动作映射；分隔不额外占高。DoseBalance默认null旧路、业务确认/未决/关联/调整/只读路径保持。没有剩余明确静态阻断，可由主任务统一冻结构建；Batch标准两事件、字号1.3、短高与长内容仍须真实运行，不因fallback已存在而豁免首屏。

新测试独立三页×两主题×两字号12配置，原稿SHA已与本地三PNG逐一重算一致。初图先保存再reveal，实际Layout横纵边界/ellipsis、单位baseline、固定操作、标准Batch两事件/Ledger三事件首屏与滚后精确恢复保留。合法内存10Lazy闭包先Scope后prepare，原Lazy不求值；无Home/无服务主路径，全部Activity/dialog销毁且每个Preparation实例owner=null、bound/visible=false、250ms refresh已移除后，才drain新writers、恢复原Lazy/逐键偏好并核业务盘hash。关联恢复使用同scope原history/journal/preparation/inventory；未知/拒写不回滚原盘。

完整业务覆盖仍由真实控件触发：选择不扣、稳定eventId确认/拒写/显式重试/不二扣、真实回执/关联、调整双对话框与并发重读、流水真实gesture和返回/重建。旧Advanced两处selector只按当前容器身份迁移并保完整语义/72dp/回调；旧入口不替换。独立45min workflow/fail-closed新进程guard/原protocol2与font_scale存在性恢复已静态核对，只有完整12成功才报告机械完成，视觉仍 `ASTRA_PENDING`。条件轮询用于生命周期/原生状态就绪，不替代可见性断言；IME和对话框诊断明确未证明同合成帧。

本次独立执行仅 `git diff --check`、新runner `bash -n`、三原稿SHA与八XML解析/key集合核对，均通过；未执行Gradle或设备。主任务后续构建与实际证据另记。M2 IME、M3 inline/IME尚待复验，不因M4静态通过而关闭其FAIL；24页与硬件验收均未完成。

### 8.4 5ef3b8f 首轮用豆 IME 实际失败与局部修正规格

run `39187428107` / head `5ef3b8f2efba1ce2d2469bc51a6815817ecc938b` / tested merge `f15b6d66579445f1bba7e328ebbc39679b3a1356`：主图 **1/12**，在Preparation实际IME固定操作门禁失败，Batch/Ledger未进入。原件在 `build/task-first-evidence/ci-5ef3b8f-bean/bean-workflow-visual-tablet-user-landscape/raw-device/f15b6d66579445f1bba7e328ebbc39679b3a1356/tablet-user-landscape-light-font-1.0/preparation/`。Astra实看states/preparation-amount-IME/window.png并独立读mechanical-failure-audit：root `[0,63,2560,754]`、IMEtrue；输入 `[721,601,2488,718]` 高117完整，但support-pane至1751、actions `[252,1463,2488,1715]` 完全位于app底之外。getGlobalVisibleRect=false后的残留rect不当可见像素。三帧稳定/前后几何相同仍只诊断，sameCompositorFrameProven=false；scope隔离PASS不抵G1 FAIL。

源码原因明确：workspace余高484px约215dp触通用360dp阈值变stacked，取消secondary bound；secondaryBody被UNSPEC测至1406px，内部weight scroll失去受限高度，所谓固定actions跟整个右pane滚出窗口。不是52输入或字号太大，不靠删说明/削padding修复。

本批限BeanPreparationActivity私有width-first workspace，不改共享TaskWorkspace默认。两个pane与原输入固定父层级，宽度足够原左右最低宽及24gap时保双栏，不因IME余高低于360自动退整块support滚动；右pane始终EXACT实际可用高，内部supportScroll weight1和自然actions固定，无外层secondary ScrollView。常态50%/max680、16面距/24gap保留。实测本例右最低预算为72px面内距+252px actions+18px关联间距+117px输入=459px，小于484；无需缩字/降52/56/48。窄宽才stack，给support至少实际actions+8gap+padding+编辑自然下限，余量给左局部列表，所有pane继续有界。若实际连最低预算也无法容纳，记录不可满足账本交审，不能隐藏动作或写PASS；不扩大新页面/功能。修后仍须原输入+两个固定操作IME断言及全部12配置，无测试放宽。

### 2026-10-19 本批统一构建

Astra生产与测试静态终审后冻结源码，首次构建发现Batch/Ledger的Paint.style接收者遮蔽，Sol仅限定Activity接收者，颜色与布局不变。重跑日志 `/tmp/2026-10-19-m2-ime-m4-build-retry1.log`：BUILD SUCCESSFUL in 1m 10s，98 tasks（13 executed/85 up-to-date）；`:bean-core:verify`通过，实际XML为11 suites/48 tests，failure/error/skipped均0；Mock及AndroidTest APK均构建成功。仍未运行新M2恢复、M3 IME和M4完整12配置，不关闭视觉FAIL或缺证项。


## 9. 45fdc9e Batch IME 两类独立问题与候选（2026-10-19）

原件 `build/task-first-evidence/ci-45fdc9e-bean/raw/raw-device/34fe3eafe83253e3986fc6ffd03a454e9c7c44f9/`。Preparation的IME输入与固定操作业务已越过旧失败点；Batch调整dialog原PNG显示目标80及原因完整，而取消/预览被底部IME遮挡。旧取证把dialog screen rect与getGlobalVisibleRect的root-local rect直接比较，使全部文字fullViewVisible误报false；修正坐标后hierarchy完整仍不能证明未被系统IME遮挡。两类问题独立，均不能用另一项修复消除。

- 测试候选：保留raw root-local值与root screen/window origin，转为screen后继续原exact whole/Layout门禁；另同main用owner WindowMetrics.bounds及IME|systemBars insets构造safeScreenRect。真实focused target及两个native按钮须同时whole且完整位于safe内，并要求实际IME可见；原PNG/前后geometry先落盘再assert。45fd回放safe=[0,74,2560,754]，输入在内而两个按钮底845/854仍FAIL。仅本CI停靠IME范围，sameCompositorFrameProven仍false，不泛化浮窗遮挡。
- 生产候选仅BeanBatch输入dialog：ADJUST_RESIZE，永久中部AdjustmentFormViewport按实际visibleDisplayFrame高度减native decor与中部高度差约束AT_MOST；原native标题/取消/预览在外，body可局部滚动。原target/reason对象、preview/confirm及dismiss草稿逻辑保持，dismiss/detach注销监听，不缩52输入或字体。

两候选静态无新增阻断，可合批构建并复验；尚无新运行/视觉通过，完整12配置与G1–G4继续待验。
