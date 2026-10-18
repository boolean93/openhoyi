# M2 三个真实表单页视觉迁移计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本计划执行。Astra 负责设计和验收，Sol 负责实现；主任务分配文件、统一构建与推送。不得自行派生代理或覆盖其他执行者文件。

**Goal:** 在 M1 真实三页完成限定验收后，迁移 BrewReview、CurveEditor、BeanEntry 的真实表单，复核 BeanEntry 标准初态末字段历史裁切并按真实证据处理，保留草稿、IME、固定操作和全部业务。

**Architecture:** 复用已有限冻结的原生字体、语义色与间距，页面显式 opt-in；三个现有表单布局分别适配真实自然高度，不另建样张或全局换主题。输入、校验、保存与恢复仍由原 Activity/model 负责。

**Tech Stack:** Kotlin、Android View/EditText/Canvas、现有 JVM/instrumentation、隔离 Mock CI。

## 0. 仓库、启动门与边界

遵循 [task-template](task-template.md) 和 [git-workflow](../git-workflow.md)。

- 来源：`develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；延续已授权 `feature/task-first-ui` / PR #4 → `develop`，不另建长期分支。初稿只读规划 HEAD `0f3b759bd99c596d95a53fee7bee8554f86de1aa`；本次接口复核 HEAD `026f9937f055342ba7256d2f446065b3bbdb0746`，开始时工作树为空。
- 当前状态（2026-10-19）：M1 `026f993` / 实测 merge `75e5333f20bb21ec911ec99befb9d979ad483dba` 已完成真实 12 配置限定 G1–G4 验收，详见 [M1 计划 §11](2026-10-18-product-visual-migration.md)；记录提交 `8698bfb`。主任务已正式启动 M2，生产/测试 Sol 按 §4 所有权并行实施。样张不重开；M1 限定通过不外推 M2、其他页面、未测状态或硬件。
- 当前只写此文件，不改生产/测试/其他文档，不提交或推送。实施时重新检查状态与远端，由主任务普通推送；branch-policy、verify、lifecycle、upgrade 必需检查通过且问题解决后走 PR，不直接推 main/develop，不 force。
- 不改 BLE、编码、压力/水量/重量精度、队列、开始/停止、重试、库存幂等、未知记录或持久化格式。保存复盘不改变库存/机器；保存曲线不开始萃取；入库只经原合法 addBatch。
- 不发版、不改签名/包名/versionCode、不安装用户设备、不操作机器或秤。Mock 只证明对应 UI/业务夹具，不能代替硬件。
- 回退：按 M2 文件追加修正或 revert PR；M1 样式默认与其他页面保持，原业务记录/草稿格式保留。禁止卸载、清记录、退款式回滚事件或重发未知写入。
- 范围仍为 24 页中的既定 M2 三页；只验用户横屏与现有横屏短高回归。**不新增竖屏兼容工作，也不通过锁方向规避窗口变化。**

依据：[认可设计](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[严格基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、[review §10/§11](../evidence/2026-10-18-native-specimen-review.md)、[24 页矩阵](../evidence/task-first-page-matrix.md)、[M1 迁移计划](2026-10-18-product-visual-migration.md)。历史 BeanEntry 失败仍保留；M1 最新限定结论以 M1 计划 §11 与矩阵当前状态为准，不由历史段落反向覆盖。

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

后续 `cb50af4` single 三横屏 profile 已全部成功；这不抹去旧失败，也不足以证明其唯一根因。本批不预设必须修改生产高度预算：未复现就保留旧证据并记录本轮实测，不制造红项。

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

### M2-A BeanEntry：先复核历史红项与标准初态

- 居中卡维持现有680dp最大宽作为生产适配输入，不把原图按比例拉伸。模式→身份→重量→日期→固定操作顺序不变；名称/重量标签在输入上方，日期标签与输入同行。组间8/16dp；三日期行之间8dp；标题/分隔不重复叠加默认 label padding。
- 原 `TaskFormSections` 只被本页调用，可页内传入 style；不修改 `beanInput/beanPage/beanError` 的默认行为。清点每个 panel 的 margin 和 label padding，只保留一次设计间距。卡底说明与取消/保存以自然宽度判断能否同行，不沿用任意宽度阈值造成有空间却多占一行。
- 标准用户窗口 font1.0、无IME、新豆款5个 EditText（name、quantity、purchased、roasted、opened）和两个模式、三个日历、保存均首屏完整；已有豆款不重复显示只读名称框。若冻结角色落地后仍不能满足，先给精确自然预算/实际差额及构图重排建议，不能擅自牺牲原五字段门禁。此时是明确设计阻断，不按任意容差关闭。
- font1.3/IME 保留整个五字段集合，局部滚动到当前字段；固定 footer 位于 IME 之上，取消与保存完整。首屏五字段规则仍保留原配置条件，不能强扩到所有大字/IME，也不能缩窄原条件。
- 保留 `newNameDraft/existingMode/rememberedBeanId/selectedBeanId`、日历初始值/取消、ISO日期解析、数量正数检查、三个ID（event/batch/newBean）和 saved 幂等保护。取消/非法输入零事件，成功 addBatch 恰一次；重建/模式往返不丢两种身份草稿，不重复建豆款。

#### BeanEntry 条件横排与日期间距（2026-10-19 已批准的同页适配）

- 日期标题“日期（选填）”与重量下方分隔都保留。重量→日期标题仅一次8dp边界间距，由 parent margin 或 spacer 单一承担；1dp分隔绘在该间距内，不再增加高度。标题20/500/30，不继承旧 label 的额外上下 padding；标题→首日期8dp、后两日期各8dp，不叠加 date panel 的首行间距。名称/重量标签14/400/22，各自到输入8dp，输入自然高度至少52dp。
- 模式始终独立占完整一行。默认沿原稿纵排身份→重量→日期；先以实测卡宽计算完整纵排的自然高度（包含卡内距、固定操作自然高度及唯一操作间距），再与真实可用卡高比较。**仅当纵排超高，且身份/重量两列各自的自然宽度均可容纳标签、输入及真实单位/尾部控件时，启用身份/重量同行。** 不按屏宽常量、历史差1px或预测差16dp强行切换。
- 同行左右顺序为身份、重量；扣除16dp列间隙后的内容宽按 **3:2** 分配。足高继续原稿纵排；窄窗无法满足两列自然宽时保留纵排与局部滚动、固定操作，不缩字、不截断标签/placeholder、不降低52dp。标准配置仍必须满足原五字段首屏门禁，不能因 fallback 改成只测滚达。
- 构造时固定模式 host、身份 host 与重量 host；后两者置于一个可切方向的布局容器。测量只改变该容器方向/子布局参数，输入 View 对象、父层级、TextWatcher、selection/composing 和恢复键不变，不在分支间 reparent 或重建 EditText。已有豆款模式使用原身份内容切换，不另加一份只读名称。
- 记录 `verticalNaturalPx`、`availablePx`、`inlineFits`、两列实际宽度及所选分支；这些是预算证据，不是通过标记。原五字段/模式与日历48dp/固定保存、模式往返与重建草稿、IME后单次 reveal 均按 §5 实测。静态542dp与526dp的差额只解释适配动机，不作为运行结果。

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
- 极短窗口采用**单一字段内容滚动区 + 区外固定 footer**，不是整页滚动。该内容 host 从构造起永久包含 metadata 与阶段/预览区；正常高度内容 child 按 viewport EXACT，维持原局部滚动；只有实测 metadata 短输入及祖先内距 + 下区最低需求 + 唯一16dp分组间距无法同时容纳时，内容 child 改自然高度，下区最低需求取阶段输入/标题/切换/内距与160dp预览/标题/内距两者较大值。取消/保存始终在 host 外。输入不 reparent；保留原 metadata/stage scroll 恢复键，新增内容 host 的独立 scroll 键。标准11字段首屏门禁不因这个分支豁免，静态预算接近边界不算运行失败或通过。

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

### 4.1 当前接口核对与交付顺序（026f993）

六个 Activity/布局文件与上表一致；`TaskFormSections`、`TaskReviewSections`、`TaskCurveEditorForm` 各仅对应一个页面，`CurveStageCountTabs` 目前仅由 CurveEditor 使用。现有四个 JVM 过滤类均存在；这些不是 Android 实际布局证明。

| 顺序 | owner / 交付 | 当前接口与必须保留的测试 |
|---|---|---|
| 0：主任务明确切换后 | 测试 Sol 先固化三页真实 fixture/初态证据；生产 Sol 同步只读预算 | `ChineseFormWindowChecks` 已在 `awaitPageReady()` 后保留 BeanEntry 5 输入首屏断言，但尚无该断言失败当刻的稳定初态全套记录；只补这个缺口。Editor 原 11 输入、三元数据顶边、四个阶段按钮、预览/详情/说明的首屏断言全部保留。已有短字段稳定→单次 reveal 流程不重写 |
| 1：BeanEntry + 最小页内 form style | 生产 Sol：`TaskProductFormStyle`、`BeanEntryActivity`、`TaskFormSections`；测试 Sol：新 `FormVisualMigrationChecks` 对应入口及既有业务检查 | 沿用 `panels/actions/jumpTo/save/restore`；日期 `bean-date-picker-*`、模式 `bean-mode-*`、所有字段/草稿键不变。先首屏和固定操作预算，再 IME/日期取消/新已有切换/保存幂等。旧失败不复现时不强加预算补丁 |
| 2：BrewReview | 同一生产 Sol：Activity + `TaskReviewSections`；同一测试 Sol：复盘 fixture/业务/恢复 | `panels/fitMultiline/jumpTo/save/restore` 保留；intent `SHOT_ID` 指向合法 observation。`app.journalResult` 是由 `journalStoreResult` 与 `journalObservationFailure` 组成的 getter，不是可替换 Lazy。隔离 scope 使用现有 store Lazy 与 failure 字段，不反射不存在的 `journalResult$delegate`。Activity 文件尾部共享 `journal*` helpers 不随本页视觉迁移改变 |
| 3：CurveEditor | 同一生产 Sol：Activity + `TaskCurveEditorForm`，必要时同 owner 负责 `CurveStageCountTabs` opt-in；同一测试 Sol：曲线 fixture/解析/恢复 | 保留 `metadata/stageRows/stageHeader/supportingBody/setStageCount/save/restore`、`CURVE_ID`、`selectedCount` 和 `curve-stage-count-*` Button 标签。保持11个真实 EditText 与 TextWatcher；预览设置 `CurveStageView.productVisualStyle`，targets 仍由 `parsed()` 产生。既有 advanced 的保存/重开/取消及 FlowRaw 只读路径不能被新截图入口替代 |
| 4：合批收口 | Astra 静态审；主任务统一构建/推送；测试 Sol 运行取证 | 三页同一版本 12 主图 + 原 single/advanced/lifecycle 与规定 IME/草稿状态；不按每个边距分别推送。各步骤不能以编译或业务绿替代 G1–G4 |

**M1 方法复用边界：**

- `TaskProductVisualStyle.page(...)` 初次装配即传入 style，借用已有 80dp Frame/Nav；不能先调用旧 `beanPage()` 装入默认 Frame 后再更换 style。该 page 返回的 `BeanPageRegions` 保留 fixed actions 接口；BeanEntry 的卡内 actions 与 Review/Editor 页级 dock 各按实际布局使用，不盲目重复添加两层 footer。
- `TaskProductVisualStyle.apply(TextView, Type, secondary)` 会设置 minHeight/gravity；不能无条件套到 EditText 后让其覆盖输入自然52dp、多行 TOP 或编辑语义。页内 form helper 只处理候选字体/色/间距，输入状态仍由原 Activity 负责。`quantity()` 只用于静态数值，不把可编辑数值替换为 TextView。
- 已有 `HomeVisualIcon.buttonIcon()` 保留真实 Button 文本/回调、24dp装饰图标+8dp间距、RTL和 disabled tint；有适合的真实语义时可直接复用，不把 Home 状态/配方 adapter 带入表单，不新增假状态。M1 已有状态的图标渲染已限定验收；本批表单的新文案、换行、禁用与 RTL 组合仍须自己的实图证据。
- `CurveStageView.productVisualStyle` 默认 null 保持旧 renderer；M2 仅 Editor 显式 opt-in。不给 `TaskWorkspaceLayout` 增加表单分支，也不改 M1 `Home/Share/Inventory` 或通用 `beanInput/beanPage/beanError` 的默认样式。
- 测试复用隔离契约，不直接调用 M1 私有 `Scope` 或改 M1 夹具；新增 scope 的真实依赖闭包、清理顺序与 hash 记录仍须静态审。现有 `AdvancedBusinessUiChecks` 的日历/身份去重、复盘编辑、曲线保存/重开断言继续运行；仅新增缺失状态，不绕过原门禁。

## 5. 真实 fixture 与验收状态

真实主图固定中文、2560×1600/density360、light/dark×font1.0/1.3，共12配置；Insets/可用窗口分别实测。主图先无焦点/无IME，输入操作后另存，不拿主图代替IME。日期按真实模型ISO解析，时区单独记录。

| 页面 | 可复现主输入（用合法内存存储/API，不改TextView伪造） | 同批必要状态 |
|---|---|---|
| BeanEntry | 新豆款“哥伦比亚 水洗”、250g；购入2026-10-06、烘焙2026-10-04、开封2026-10-10。另有合法已存豆款供选择；草稿经真实字段操作输入，不先写入目标批次 | 空新建初态五字段；新/已有往返及重建；三日历选择/取消；空名、0/非法数量、非法ISO日期；name/quantity/末日期IME；取消零事件、保存一次、重复/重建不新增 |
| BrewReview | 合法 journal observation：经典浓缩、elapsedMs=27400、weightHundredthsGram=3620、status=ENDED；豆款哥伦比亚水洗、doseMg=18000、研磨“2.3圈 / 中细磨”；三notes依次为“后段流速略快，希望更平稳。”、“柑橘酸质，焦糖甜感，尾段略干。”、“研磨调细一点，其他条件不变。”，通过合法 `journal.edit` 写入隔离记录 | 未选豆/未知状态/无时长或秤值；长研磨/三段长笔记；dose与三笔记IME、选区/滚动/重建；取消、成功、内存写失败；observation/inventory/usage不变 |
| CurveEditor | 合法本地草稿“经典浓缩”、93°C、36g，targets `[30,90,70,50]`、waterTenthsMl `[150,250,150,50]`，stageCount4 | 1–4切换及隐藏值恢复；长名、空名、非法数字/超界、无效预览；name/temperature/cup/末阶段IME；草稿重建/取消/保存；已有FlowRaw只读和复制来源警示仍可达 |

隔离只在 guarded disposable Mock CI：沿 M1 已审的原 Lazy 不求值、合法内存库、owned cache writer、真实 Activity、销毁/解绑/停止服务→drain writer→恢复原引用与白名单逐键prefs→业务盘hash不变。复盘/入库成功只写隔离内存库；失败不回滚真实文件。只读页截图协议不可自动推成带 caret/IME 的同帧证据：有动画/光标导致零差无法成立时保留失败/实际上下文，不 mask、不调大容差；IME稳定几何与合成同帧结论分别记。

### M2 测试隔离闭包（2026-10-19 方案静态批准，运行待证）

每页每 profile 使用独立 guarded Mock 进程。新 `FormVisualMigrationChecks` 独立实现已验契约，不调用/修改 M1 私有 Scope，不改 `NativeWindowFrameEvidence`。真实替换 Lazy 恰为 `feedbackPreferences/logs/curves/samples/history/customCurvesResult/curveUsageResult/beanInventoryResult/beanPreparationResult/journalStoreResult`；保存原 Lazy 身份与 initialized 状态，不求原 value。`legacyHistory/legacyCurves` 仅核身份和 initialized 不变；`journalResult` 不是 delegate。

合法内存库及 history callbacks 全指向本次 scope；trace/samples 仅使用唯一 `form-visual-UUID` owned cache。主线程整组安装、getter 身份核对后才启动 Activity；partial install 也进入清理，installed 标记须先于首个引用替换。测试不启动/绑定服务。结束关闭表单/对话框并确认 Activity destroyed→停止并确认无服务→只 drain 本次 samples writer 与 trace→恢复原 Lazy、language/failure、`appearance.dark`/`curves.selected` 原存在性与类型。不得 clear 全库或删除文件追 hash；原业务盘/其他 prefs/db hash 与 legacy 状态不变，两个白名单 prefs 的物理 SHA 单列。失败停止后续，不回滚原盘；host finally force-stop。

BeanEntry 目标250g保持待填草稿，不预先创建目标批次；已有豆款由合法 addBatch 创建。Review 用合法 ENDED observation 后经 journal.edit 写上述 notes；Editor 用真实 CURVE_ID 加载合法四阶段文档。初态无焦点/无 IME 图沿用原双域协议；IME 图明确记录同步能力边界，不把稳定几何冒称同合成帧。

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

完成要求：Astra给三页主状态四门独立结论、原BeanEntry初态红项有新真实证据闭环、规定IME/草稿/业务状态通过或逐项明确未覆盖；root更新既有matrix而非抹去历史。M1已限定完成、M2已获授权实施；当前所有M2最终验收均待新构建/运行/实图证据，本计划及静态方案批准不授予M2任何页面PASS。

## 7. 2026-10-19 首批静态终审与构建（运行/视觉未验）

Astra 已只读审本批8个生产 Kotlin、8语种 `form_visual.xml`、6个测试/入口文件，当前无剩余静态阻断，可由主任务统一构建。M1 helpers 与共享默认未改；三个 Activity 从 `save.setOnClickListener` 到文件尾均独立比对与 HEAD 逐字相同。`CurveStageCountTabs` 默认null保留旧分支。

已收口：BeanEntry 固定模式行与输入父层级、实测条件3:2同行及唯一日期间距；Review 初始与重测共用同Paint的两段 StaticLayout 自然盒预算，不再 `getter lineHeight*2`；Editor 单一字段内容滚动/区外固定 footer、完整最低预算、原稿“阶段参数”标题与现有间距内1dp表格分隔。StaticLayout是实际Paint的独立预算，不冒充 TextView 本次实际 Layout；最终行盒仍由运行证据核对。

测试已保留原5/11字段、52dp输入/48dp触控门槛；Bean失败几何在断言同main catch保存，再采原PNG；实际fontScale/uiMode/无IME与非空协议2原件严格核验。恢复测试先建立实际IME、非默认焦点/selection/正scroll，三真实frame仅等稳定，重建后先snapshot严格比对再清焦点截图。10Lazy合法闭包、destroy→drain→restore、逐键typed prefs及原盘hash边界已审，unknown不填PASS。`git diff --check`、脚本 `bash -n`、8语种两资源key解析核验通过；这些不是Android编译或运行证明。

**待CI与实图：** BeanEntry原条件五字段/Editor原条件11字段的实际整数预算；两字号真实两行Layout及单位baseline；固定footer与IME/非零滚动重建；错误/不可用/保存取消幂等；同源码12原整屏G1–G4。静态542/472等预算估算不得写为通过或已复现失败，不把内容滚达代替标准首屏。主任务统一构建/提交/push后取证，M3继续仅规划，矩阵M2状态不改PASS。

统一构建随后完成：生产 `5c495e8`、测试 `b77c704`；日志 `/tmp/2026-10-19-m2-form-migration-build.log` 显示 `BUILD SUCCESSFUL in 1m 12s`，Mock/AndroidTest 均构建成功。Astra 独立读取四份对应 JVM XML：LanguageDraft 4、BeanQuantity 3、CustomCurve 8、BrewJournalHistory 2，共4 suites / 17 tests，0 failure / 0 error / 0 skip。仅证明本地编译与这些JVM用例；真实12配置、IME/恢复和G1–G4尚未运行验收。

## 8. 95be8fb 首轮真实图缺陷与局部补丁预审（未闭环）

Astra 已独立实看 advanced business 的 `bean-entry-new-mode.png`、`curve-editor-four-stages.png`、`review-saved-notes.png`，均为2560×1600整屏；目录 `build/task-first-evidence/ci-95be8fb-advanced/advanced-mock-pages-business/advanced-page-screenshots/business/`。旧18条business路径通过不包含新 `verifyM2FormContract`，后者只在frame入口执行；此时尚无完整三页12配置/实际Typography/颜色与几何验收，不写G1–G4通过。

| 实图明确差异 | 源码定位与当前静态判断 | 必须复验 |
|---|---|---|
| BeanEntry同行名称/重量两输入描边相接，批准16dp净距未出现（G1） | `TaskFormSections.arrangement` 在测量中原地改相对margin；候选修正每轮明确start/end，再按identity.layoutDirection解析physical margins。已只读核补丁范围：保留3:2与16gap预算、纵排8gap、自然高度及原输入层级，无静态阻断 | 横排真实两panel/输入rect间16dp、实际start/end/left/right与方向；回纵排清零、RTL镜像；原五字段首屏、重建/IME不回退。未把代码修正当运行关闭 |
| BrewReview选豆长名第二行只露字顶（G1/G3） | `choiceAdapter` 使用系统 `simple_spinner_dropdown_item`，SDK34 XML 的固定 `dropdownListPreferredItemHeight` 被 AbsSpinner/Spinner按LP测量；仅设置maxLines/minimumHeight不能解除固定高。补丁selected/dropdown改LP WRAP_CONTENT，并维持minHeight与minimumHeight52；原16sp/400/24、H12/V8、selected两行/下拉完整、ID及监听不变，静态无阻断 | Spinner及selected child实际高/LP/compound padding、逐行Layout与visible范围、长名下拉与选择恢复；Bean已有豆款也使用此adapter，须同批回归 |
| CurveEditor第四阶段两输入底边被左局部视口截断（G1） | 当前compact切换用单行最低预算，`fullNaturalPx`仅记账，不保证标准11字段首屏。尚无同次实际完整矩形，不凭图猜缺口px、不先削padding或缩字 | actual window/insets/font/density、available/metadataNatural/stageNatural/fullNatural、header/footer、stagePane/scroll/row4/两输入full与visible/scrollY；标准11首屏不能用切compact后滚达替代。待真实账本给集中适配 |

两补丁目前只构成可进入统一构建的静态修正；待主任务合批构建/推送后同状态实图闭环。Editor源码未因上述初读修改。用户再次强调的间距、字体、字号、背景色继续按§6四门逐项验：已有候选来源不等于本轮实测一致；肉眼未见另外的字色回退也不填PASS，未知保持unknown。

另据主任务报告，advanced pages 常规240夹具通过，wideFont `ru/light/BrewReviewActivity` 在曲线名/时间辅助文本触发 `LanguageUiLayoutChecks:170` ellipsized。该非中文既有回归不得忽略或放松断言；此条为主任务运行报告，尚非Astra独立PNG/几何验收。允许的最小设计方向是**仅Review页内**按实际文案/字体自然宽在同行与自然多行header之间适配：返回/标题/详情保留，曲线名/时间完整，时长/秤值/状态保持完整语义组，16dp组距/8dp行距；不缩字、不隐藏日期或按语言硬编码。可用宽不足时在永久父层级内重排，不动态reparent输入；新增header自然高进入正文预算，footer不滚走。具体布局待生产owner给宽度账本后静态复核，不能据此预称修复。

**frame终态补记：** run `39149210695` / head `95be8fb` / tested merge `36620b349186cb1e87e70c8eee8e589b772d916a` 失败，M2正式主图 **0/12**。原件根为 `build/task-first-evidence/ci-95be8fb-frame/task-frame-tablet-user-landscape/task-first-evidence/form-visual-migration/raw-device/36620b349186cb1e87e70c8eee8e589b772d916a/tablet-user-landscape-light-font-1.0/bean-entry/`。Astra独立读取 `empty-initial/chinese-form-states/empty-BeanEntryActivity-bean-initial-five-fields-{geometry,stability}.json`：连续3次OnDraw稳定，五输入full==visible且各117px；末日期 `[1127,961,2099,1078]` 完整，仅可记录该空初态几何结果，不外推有内容/大字/IME或四门。name右边1515、quantity左边1515，独立实测确认同行净距0px，仍是G1失败。此PNG记录同合成帧未证明，不包装成正式协议2主图。

随后新测试 `selectSection` 误按contentDescription定位（五输入均null，但labelFor→id完整）抛NoSuchElement，属于测试适配错误。只允许按原标签labelFor/id恢复精确定位，全部原可见性/数量/业务断言保留；Editor未启动，**本次没有其自然高度或第四行矩形**，不得用该失败宣判其预算。两runner另发现环境无rg而if内pidof guard未正确执行，须可靠读取退出状态/非空输出并fail-closed；补丁待独立静态审查。旧24页frame/样张/M1入口通过不抵M2缺证。两种测试基础错误都不能记为页面视觉PASS或替代已见business图缺陷。

**最小测试修正静态复核：** `ChineseFormWindowChecks` 两处Bean selector改精确标签文本/labelFor→原EditText id，保留shown/reveal/fullyVisible；两runner移除对rg的依赖，仅adb成功、远端pidof确实完成且返回1/输出空才允许instrument，未知/错误/存在进程均停止，先留 `fresh-process.json`。form页顺序改Editor→Bean→Review，只为优先取得实际Editor预算，仍完整12配置、首错停止及原协议/断言。Astra独立 `bash -n`、嵌入Python解析、抽取实际predicate每runner7个成功/失败样例通过；这些未调用设备，不证明运行成功。

single另报Review/Editor在短IME态出现27/34px可点击TextView小于48dp。源码身份已核为 `TaskNavigationView.createToolsHost` 的 `task-tools-menu`，有真实PopupMenu回调，不能当装饰豁免；原48dp被opt-in `TaskProductVisualStyle.styleHeader` 的aux22sp minHeight覆盖。批准仅该tag在字体应用后保留minHeight/minimumHeight/minWidth至少48dp，真实菜单/字级不改；legacy默认不动，M1 opt-in必须回归。补丁与Review自然flowheader落稳后统一静态审查/构建，实际短IME与非中文页头仍待运行。

**本批生产终核（构建中，未验运行）：** Review永久 `ReviewHeaderLayout/ObservationFlow` 已落盘，返回明确48dp；同父层级内按真实自然宽同行/堆叠、RTL逻辑方向和GONE过滤，指标组16dp/行8dp，曲线名/时间不再两行ellipsis。`TaskPageFrame`按递归 `task-tools-host` tag及滚动祖先找原host，不依赖header直接子索引；构造期一次重组不破坏compactTools引用，resize不重挂。完整header自然高由原shell扣入正文，footer独立；保存监听至EOF已与HEAD逐字比对不变。精确tools target在style.apply之后恢复48dp，未改legacy默认。Astra核当前四生产文件与三测试/runner补丁无剩余静态阻断；主任务唯一统一构建日志 `/tmp/2026-10-19-m2-visual-fixes-build.log`，此处不预写结果。暴露的 `productHeaderLayoutEvidence.headerHeightPx` 是resolve前自然预算，尚未写入Form geometry，不冒称已采实际高度。

**Editor补充原门禁红证据：** Astra已直接读取 `build/task-first-evidence/ci-95be8fb-single/single-screen-tablet-user-landscape/tablet-user-landscape/single-screen-window-checks/chinese-form-states/zh-Hans-light-CurveEditorActivity-result.txt`：标准用户横屏中文浅色在原11字段初态断言（`ChineseFormWindowChecks:489`）失败，EditText446×117px仅visible `[450,1147,896,1249]`（102px），少15px；sections/IME均未进入。该结果进一步确认标准首屏失败，但仍没有完整metadata/stage/fullNatural账本；不由15px倒推削减间距，本批保持Editor源码不动，下一次Editor-first沿原断言取完整预算。

### 本批修正构建证据

测试修正 `2ce9d09`、生产修正 `e77e3fa` 经 Astra 静态终核无阻断。唯一统一构建日志 `/tmp/2026-10-19-m2-visual-fixes-build.log`：`BUILD SUCCESSFUL in 1m 5s`，96 tasks / 12 executed；Mock 与 AndroidTest assemble 完成。四份本轮 JVM XML 合计17 tests（4/3/8/2），0 failure/error/skip。只证明编译与这些测试，不关闭本节已见视觉 FAIL。下一正常批次正式夹具先运行 CurveEditor，完整12配置及首失败终止保持，获取实际四阶段预算后再修其裁切。
