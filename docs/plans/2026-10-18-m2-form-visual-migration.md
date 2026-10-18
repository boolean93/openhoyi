# M2 三个真实表单页视觉迁移计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本计划执行。Astra 负责设计和验收，Sol 负责实现；主任务分配文件、统一构建与推送。不得自行派生代理或覆盖其他执行者文件。

**Goal:** 在 M1 真实三页完成限定验收后，迁移 BrewReview、CurveEditor、BeanEntry 的真实表单，关闭 BeanEntry 标准初态末字段裁切，保留草稿、IME、固定操作和全部业务。

**Architecture:** 复用已有限冻结的原生字体、语义色与间距，页面显式 opt-in；三个现有表单布局分别适配真实自然高度，不另建样张或全局换主题。输入、校验、保存与恢复仍由原 Activity/model 负责。

**Tech Stack:** Kotlin、Android View/EditText/Canvas、现有 JVM/instrumentation、隔离 Mock CI。

## 0. 仓库、启动门与边界

遵循 [task-template](task-template.md) 和 [git-workflow](../git-workflow.md)。

- 来源：`develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；延续已授权 `feature/task-first-ui` / PR #4 → `develop`，不另建长期分支。只读规划 HEAD `0f3b759bd99c596d95a53fee7bee8554f86de1aa`，工作树原为空。
- 当前 M1 为生产 `7e3f662`、测试 `9c71043`；CI `39142720200` 在途。**M1 未完成图审，本计划不是 M2 开工指令或 M1 通过记录。** 待 Astra 对 M1 真图给出限定结论、主任务明确切换 M2 后实施。
- 当前只写此文件，不改生产/测试/其他文档，不提交或推送。实施时重新检查状态与远端，由主任务普通推送；branch-policy、verify、lifecycle、upgrade 必需检查通过且问题解决后走 PR，不直接推 main/develop，不 force。
- 不改 BLE、编码、压力/水量/重量精度、队列、开始/停止、重试、库存幂等、未知记录或持久化格式。保存复盘不改变库存/机器；保存曲线不开始萃取；入库只经原合法 addBatch。
- 不发版、不改签名/包名/versionCode、不安装用户设备、不操作机器或秤。Mock 只证明对应 UI/业务夹具，不能代替硬件。
- 回退：按 M2 文件追加修正或 revert PR；M1 样式默认与其他页面保持，原业务记录/草稿格式保留。禁止卸载、清记录、退款式回滚事件或重发未知写入。
- 范围仍为 24 页中的既定 M2 三页；只验用户横屏与现有横屏短高回归。**不新增竖屏兼容工作，也不通过锁方向规避窗口变化。**

依据：[认可设计](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[严格基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、[review §10/§11](../evidence/2026-10-18-native-specimen-review.md)、[24 页矩阵](../evidence/task-first-page-matrix.md)、[M1 迁移计划](2026-10-18-product-visual-migration.md)。矩阵历史局部通过不能覆盖 review §11 的新失败。

## 1. 已实读原稿、真实页和失败边界

本次实看四原 PNG，以及 `ci-0a7b71a-single-user/tablet-user-landscape/single-screen-window-checks/zh-Hans-light-{BrewReview,CurveEditor,BeanEntry}Activity.png`。这些真实页分别为空笔记、两阶段、空新豆款，不冒充下面的有内容/四阶段夹具。

| 原图（均在 `docs/research/task-first-ui-concepts/`） | 实际尺寸 | SHA-256 | 本批保留的构图 |
|---|---|---|---|
| `brew-review-v3.png` | 1586×992 | `0edbc1516c06b02741164b5b1452d39fc30dfedc6962b7c03257a0e1dd3c79c4` | 顶部杯次/时长/秤值/结束状态；左参数、右三段笔记；全宽固定保存区 |
| `curve-editor-v1.png` | 1586×992 | `a0cc88b81db31d3e3c84adfce47bca52d60b3f67b8099812d942df52c3630c12` | 顶部三个元数据框；下方左四阶段表、右真实预览；固定取消/保存 |
| `bean-entry-v2.png` | 1585×992 | `1f33720cd81681cbd1f2c3818e69731a2c0975a12c12fcaebcf58a5a63e3f580` | 居中单表单卡；双模式、名称/重量、三日期；卡底说明/取消/保存 |
| `bean-entry-keyboard-v2.png` | 1586×992 | `31c636d706c9c8f571dde0d42eee39ed075cbec35f7c4aecbaa4bc72c39e2e55` | IME 上方固定操作，字段局部滚动，原输入仍可见 |

原图的系统时钟/键盘皮肤、示意徽标、固定日期/状态不复制为生产业务。字体不能从 ImageGen 精确识别；使用 §2 明确的原生候选，不无限研究原字体，也不声称像素原字体已证。

### 1.1 BeanEntry：已知独立缺陷，必须保留首屏门禁

`39140082109` single-user 334/336；中文浅/深 BeanEntry 在 `ChineseFormWindowChecks` 初态 `inputs.forEach(::fullyVisible)` 失败，尚未进入 section/short/IME。原文件保留在 `build/task-first-evidence/ci-0a7b71a-single-user/tablet-user-landscape/`。

末字段“开封日期” origin `[1118,1142]`、size972×117、应到1259但 visible 到1258，仅116px。`task-form-scroll` 为1458×995，内容自然高996。日历48dp目标先通过，初图无 IME；没有失败瞬时稳定 draw/scrollY，所以“自然预算差1px”是强证据，不是唯一原因已经证明。

实施先补该**原断言之前**的稳定初态现场，断言失败前保存完整 PNG、每字段/祖先 rect、内容/viewport、scrollY、Insets/IME、focus/pendingLayout。不能先 reveal、改阈值或把5字段改成逐个可达来过原门禁。

### 1.2 BrewReview / CurveEditor：不预判需要生产短高补丁

旧 dose/name IME 失败后来在 `39138935786` 三横屏 profile、浅/深均经“稳定→单次 reveal→稳定”完整可见；相关生产布局没有变化。保留原短字段严格宽高断言与该流程，不把旧失败认作仍需盲改预算，也不声称唯一历史原因已证。

源码待本批重测的具体风险：`TaskCurveEditorForm` 元数据 viewport 上限 `height/3`；`TaskReviewSections` split 时未用短字段 minimum，`fitEditors()` 低预算分支能落到48dp。它们是需验证/修正的实际路径，不据源码猜测新运行必然失败。

## 2. 共用设计输入与适配优先级

### 2.1 固定角色，禁止为过门禁缩字

- 使用现有 `TaskProductVisualStyle` 冻结 palette/roles，字体 `Noto Sans SC 2.004-H2`，SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。正文/输入16/400/24，辅助/标签/单位14/400/22，分区20/500/30，标题24/500/36，操作16/500/24，复盘顶栏紧凑摘要20/400/30；均为 sp。复盘不是库存大余额，不迁入40sp大值。
- 字距0、无伪粗、`includeFontPadding=false`；真实 Layout 行盒/基线实测，不能用 getter lineHeight 代替。输入的 `inputType`、selection、composing、TextWatcher、IME action 不被样式函数重置。短输入 `WRAP_CONTENT` 且高度至少52dp，并满足真实文字+上下内距自然高度；不将52dp当固定上限。
- 页面80dp rail、16dp页边距、24dp栏间隔、8dp相关间距、16dp分组/普通面内距；面圆角12dp、输入10dp、描边1dp。输入沿用水平12dp/垂直8dp内距，尾部单位/日历占独立区域，文本不伸入其触控区。原生像素按当前确定性 dp/sp 换算记录，不额外引入“1px容差”。
- 页底/内容面/主辅字/边框/选中/禁用/主操作直接引用冻结 light/dark 语义色，不复制原截图抗锯齿边缘采样值。普通输入为 surface+border，焦点描边为 accent，提示字为 muted；错误沿现有 danger 语义并记录实际色值，不能伪称这个未在三样张覆盖的错误态已冻结。焦点、错误、禁用分开保存真实状态。
- 单位独立14sp节点，静态数字与单位同 baseline；EditText 的单位不得加入可编辑字符串或改变解析。使用实际 EditText baseline 对齐，禁止只按容器 center 推断。表头保留 bar/ml 语义，不要求把每格重复单位都写入数据。

### 2.2 测量与缩高顺序

1. 用**实际可用窗口**减 Insets/IME、页内固定 header/footer 的实测自然高度。先保留固定取消/保存与短输入自然高度，再分配内容 viewport；不减小字体/52dp、不隐藏保存。
2. 标准 font1.0 优先原稿构图和既有首屏约束；font1.3 及短高让字段/笔记/预览在各自局部区滚动。不要把整页连固定按钮放入 ScrollView。
3. 元数据/参数区 viewport 至少能完整容纳当前短输入的实测自然高度；确需祖先内距也计入。若两个堆叠区不能同时满足，紧凑态采用**单一字段内容滚动区、固定 footer**，保持原 View/字段对象和恢复键，不把输入 reparent 来回丢焦点；实现者先列出该布局分支，再由 Astra 静态核对，不能偷偷改成弹窗编辑。
4. 多行输入至少保留实际两行文字+内距所需高度，不沿用48dp压盒；超长文字可在输入内滚动，笔记区域另有外层滚动。必须分别验证 caret/文本行和整个短字段，不把“长全文都在首屏”设为不可能目标。
5. 所有预算先用实测整数 px 计算，最后才记录 dp；sum(children+margin+padding) 与 viewport 同源。禁止在末字段上写 `-1px`、全局减 padding、改变父 clipChildren 或放松 visible 比较。

## 3. 页面设计与业务不变量

### M2-A BeanEntry：先关闭已知红项

- 居中卡维持现有680dp最大宽作为生产适配输入，不把原图按比例拉伸。模式→身份→重量→日期→固定操作顺序不变；名称/重量标签在输入上方，日期标签与输入同行。组间8/16dp；三日期行之间8dp；标题/分隔不重复叠加默认 label padding。
- 原 `TaskFormSections` 只被本页调用，可页内传入 style；不修改 `beanInput/beanPage/beanError` 的默认行为。清点每个 panel 的 margin 和 label padding，只保留一次设计间距。卡底说明与取消/保存以自然宽度判断能否同行，不沿用任意宽度阈值造成有空间却多占一行。
- 标准用户窗口 font1.0、无IME、新豆款5个 EditText（name、quantity、purchased、roasted、opened）和两个模式、三个日历、保存均首屏完整；已有豆款不重复显示只读名称框。若冻结角色落地后仍不能满足，先给精确自然预算/实际差额及构图重排建议，不能擅自牺牲原五字段门禁。此时是明确设计阻断，不按任意容差关闭。
- font1.3/IME 保留整个五字段集合，局部滚动到当前字段；固定 footer 位于 IME 之上，取消与保存完整。首屏五字段规则仍保留原配置条件，不能强扩到所有大字/IME，也不能缩窄原条件。
- 保留 `newNameDraft/existingMode/rememberedBeanId/selectedBeanId`、日历初始值/取消、ISO日期解析、数量正数检查、三个ID（event/batch/newBean）和 saved 幂等保护。取消/非法输入零事件，成功 addBatch 恰一次；重建/模式往返不丢两种身份草稿，不重复建豆款。

### M2-B BrewReview：左右真实表单，顶栏不再挤掉正文

- 页头合并同一杯次摘要：标题/曲线与时间在左，时长/秤值/真实状态在右；标签14、数值20、单位14同 baseline。保留 UNKNOWN 警示、缺失值“—”，不画假“已结束”或原稿演示徽标。
- 左参数区建议沿现有280dp内容宽；右笔记占剩余宽，24dp分栏及1dp分隔。统一两侧标题起点和基线，不叠加大标题上下16dp与额外组间16dp。咖啡豆、粉量、研磨在左；三段笔记与标签在右，默认有内容夹具三段首屏可辨，长内容局部可达。查看记录/采样原路由保留。
- 底部完整“只编辑个人记录，不改变机器状态或库存”说明、取消、保存；宽横屏同行，短高说明可随内容区滚动但按钮不滚走，说明仍可达。
- 保留 bean选择、剂量解析/范围、grind/expectation/taste/next 五字段、journal.edit 只改 notes、不可用/写失败提示及保存重试状态。重建恢复选豆、所有文字、选区、两侧 scrollY；取消不写 notes，保存不改变 observation、inventory、usage 或样本。

### M2-C CurveEditor：四阶段输入与真实预览

- 元数据名称/温度/目标液重保持同行；复用现有1.3:1:1宽度比例、16dp组间距，输入自然高度，三输入顶边对齐。下方左阶段表/右预览沿现有1.1:1比例，24dp间隙。减少旧重复厚重留白，不压输入。
- 左标题与1–4阶段切换同一行；列标题14，阶段正文16，色点+阶段名使用真实序号；输入统一16/400/24（现有18sp是旧实现，不作为本批基线）。bar/ml为真实单位标签，不改变 parse scale。标准四阶段11输入全部可达并保留原用户配置首屏断言。
- 右图显式 opt-in 冻结 renderer，使用编辑器 `parsed()` 的真实 targets，阶段四色；沿用已验14sp、0/6/12刻度、2dp标签保护/真实网格补集、图至少160dp。不得把非法输入继续画成上次有效值；保留空目标+错误说明、预览和完整详情入口。
- 底部补原稿已有的取消入口（仅 finish，等同返回，不写库），与保存并列；保留“仅保存本地，不启动萃取”范围说明。保存仍走当前 parsed.validate→custom store.save，copy-warning/FlowRaw只读/缺失源保持原业务，不扩大 FlowRaw 编辑能力。
- 保留 draftId、stageCount、全部11输入草稿（含暂隐藏阶段）、焦点/选区、metadata/stage scroll；1→4→1→4不丢值。非法数/空名/不可用/写失败不保存、不启动机器。

## 4. 文件所有权与实施顺序

路径前缀 `mobile/src/main/kotlin/io/openhoyi/mobile/`，测试前缀 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/`。

| 所有者 | 文件 | 边界 |
|---|---|---|
| 生产 Sol（单一） | `BeanEntryActivity.kt`、`TaskFormSections.kt`；`BrewReviewActivity.kt`、`TaskReviewSections.kt`；`CurveEditorActivity.kt`、`TaskCurveEditorForm.kt` | 按 A→B→C顺序；六文件均属于本次三页。先 natural budget/布局，再样式；不改模型/存储 |
| 同一生产 Sol，必要桥接 | 新增 `TaskProductFormStyle.kt`（仅输入/标签/焦点态、单位布局）；`CurveStageCountTabs.kt` 可加默认null页级 style | 复用 M1 已有 `TaskProductVisualStyle`、Frame/Nav/Stage opt-in，不改其默认和 M1 源码。若确需其他桥接先列 exact diff 与默认不变证明，由主任务划所有权 |
| 测试 Sol（单一） | `ChineseFormWindowChecks.kt`、`AdvancedBusinessUiChecks.kt`；新增 `FormVisualMigrationChecks.kt`；必要的 `BrewAudioInstrumentation.kt`、独立 `scripts/check_form_visual_migration.sh`、现有 task-frame workflow 新调用 | 复用已验 `NativeWindowFrameEvidence`，不重写/放松双域协议。M1 fixture只借鉴隔离方式，不修改其夹具/门禁。新增真实表单取证，不建新 Mock 样张 |
| Astra | 本计划；后续主任务指定的既有 review/matrix | 设计及 G1–G4 成组验收，不改产品代码 |

- [ ] **启动检查**：M1 有限定实图结论、主任务切换M2；核 HEAD/status/共享文件所有权，保存本批基线。
- [ ] **红证据**：在旧 BeanEntry 原状态执行稳定初态五字段门禁，保存 fail 当刻证据；若新环境不复现，保留旧产物并写“未复现”，不能制造假红。BrewReview/Editor保留现有IME断言，不为无证据猜测先改生产预算。
- [ ] **A/B/C 实施**：每页按 §3 修改；输入仍用真实 Activity/model。先保存业务状态夹具及不变量，再改布局；对实际发现的预算函数可新增小型 JVM 边界测试，不为每个padding写镜像测试。
- [ ] **统一静态复核**：Astra核原稿构图、字段/回调、默认隔离、输入对象/草稿生命周期、自然高度和固定footer；一次列完本批差异，Sol集中修正。
- [ ] **一次本地构建**：主任务运行 `./gradlew :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.LanguageDraftTest' --tests 'io.openhoyi.mobile.BeanQuantityTest' --tests 'io.openhoyi.mobile.CustomCurveTest' --tests 'io.openhoyi.mobile.BrewJournalHistoryTest' :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain`。新增预算测试另加精确类；期望exit0，编译不算运行通过。
- [ ] **一批推送取证**：主任务普通推送，同源码真实12主图+规定状态；现有 single-screen 三横屏 profile 与 lifecycle/advanced业务保持。不得取消在途CI或按每个边距微推送。
- [ ] **Astra实图验收**：先浅1.0三页，再深1.0，最后两主题1.3和IME/错误/恢复；仅有证据的状态闭环，M3不被新样张研究阻塞。

## 5. 真实 fixture 与验收状态

真实主图固定中文、2560×1600/density360、light/dark×font1.0/1.3，共12配置；Insets/可用窗口分别实测。主图先无焦点/无IME，输入操作后另存，不拿主图代替IME。日期按真实模型ISO解析，时区单独记录。

| 页面 | 可复现主输入（用合法内存存储/API，不改TextView伪造） | 同批必要状态 |
|---|---|---|
| BeanEntry | 新豆款“哥伦比亚 水洗”、250g；购入2026-10-06、烘焙2026-10-04、开封2026-10-10。另有合法已存豆款供选择；草稿经真实字段操作输入，不先写入目标批次 | 空新建初态五字段；新/已有往返及重建；三日历选择/取消；空名、0/非法数量、非法ISO日期；name/quantity/末日期IME；取消零事件、保存一次、重复/重建不新增 |
| BrewReview | 合法 journal observation：经典浓缩、elapsedMs=27400、weightHundredthsGram=3620、status=ENDED；豆款哥伦比亚水洗、doseMg=18000、研磨“2.3圈 / 中细磨”；三notes为原稿三句中文 | 未选豆/未知状态/无时长或秤值；长研磨/三段长笔记；dose与三笔记IME、选区/滚动/重建；取消、成功、内存写失败；observation/inventory/usage不变 |
| CurveEditor | 合法本地草稿“经典浓缩”、93°C、36g，targets `[30,90,70,50]`、waterTenthsMl `[150,250,150,50]`，stageCount4 | 1–4切换及隐藏值恢复；长名、空名、非法数字/超界、无效预览；name/temperature/cup/末阶段IME；草稿重建/取消/保存；已有FlowRaw只读和复制来源警示仍可达 |

隔离只在 guarded disposable Mock CI：沿 M1 已审的原 Lazy 不求值、合法内存库、owned cache writer、真实 Activity、销毁/解绑/停止服务→drain writer→恢复原引用与白名单逐键prefs→业务盘hash不变。复盘/入库成功只写隔离内存库；失败不回滚真实文件。只读页截图协议不可自动推成带 caret/IME 的同帧证据：有动画/光标导致零差无法成立时保留失败/实际上下文，不 mask、不调大容差；IME稳定几何与合成同帧结论分别记。

### 不可降低的运行门禁

1. BeanEntry 原条件（宽≥1000dp、高≥600dp、font≤1.05）保留5输入全部首屏完整，模式/日历≥48dp且完整，保存完整；既有模式另核身份去重。初态校验前禁止reveal。失败当刻保存现场再throw。
2. 短字段至少52dp且真实行盒不裁切；实际自然高更大时按更大值。IME前、稳定后、单次reveal后、关闭IME后分别记录完整矩形和固定footer；不能以“已经可见”为等待终点掩盖过渡，失败保留原断言。
3. 复盘三笔记与编辑四阶段全部可达；当前短字段可完整reveal，多行caret/选区可见；另测长全文滚达。首屏/滚达明确分开，existing原首屏断言不删除。
4. 固定取消/保存全过程完整，不跟内容滚走；滚动后恢复状态实测。语言/主题/重建后输入值、selection、stageCount、selectedBeanId与各scroll位置恢复；活动记录/未知结果不被重写。
5. 保存/取消/错误业务报告记录真实模型前后，不仅看toast或按钮文案。库存事件数/ID/数量、journal observation/notes、custom草稿ID各自断言；成功不代表硬件写入。

## 6. G1–G4 与完成条件

每配置目录保存 `window.png`、`manifest.json`（head/实际merge/APK/hash/fixture/profile）、`fixture.json`、真实 `geometry/typography/colors`、`baseline-alignment.json`、`frame-sync`、滚动/IME前后PNG及业务/恢复报告；所有 unknown 写原因，不用预期常量填实际值。沿用现有取证字段，不为本批重新设计字体探针。

| 门禁 | 逐页核对对象 | 通过依据 |
|---|---|---|
| G1 | 原稿区域顺序、页/面/输入内距、栏宽、分隔、表格列、真实整数预算、footer位置 | 原稿+本计划适配输入+同状态完整PNG及节点rect；标准首屏和IME滚动分别判定。仅存在节点不算通过 |
| G2 | 选定字体SHA、各TextView/Canvas字重、fakeBold/letterSpacing、中文/单位角色 | 真实运行参数与字体文件；原稿字体身份继续unknown，不误称已识别，不阻止执行选定候选 |
| G3 | 16/14/20/24sp角色、真实换算、Layout行盒/基线、52dp自然下限、单位、光标/选区 | 实际Layout而非仅getter；数字/单位屏幕baseline、文本不裁切；两字号分别验，不缩字过关 |
| G4 | light/dark页底/面/边框/输入focus/error/选择/禁用/主操作、alpha | 实际drawable state ARGB+原整屏纯色内区及人审；不把独立drawable色冒充屏幕合成色。新增表单状态限定冻结 |

抗锯齿字缘差异与真实字体/布局/配色差异分开；不采用整体截图任意“相似度”阈值，也不非等比拉伸原稿。原图缺失的深色、错误、IME系统样式明确为原生适配输入，不倒推成原图已批准参数。

完成要求：Astra给三页主状态四门独立结论、原BeanEntry初态红项有新真实证据闭环、规定IME/草稿/业务状态通过或逐项明确未覆盖；root更新既有matrix而非抹去历史。M1未完成、本批尚未实施/构建/运行，当前所有M2最终验收均待证；本计划不授予任何页面PASS。
