# 真实三页视觉迁移实施计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本计划逐项执行；Astra 负责设计/验收，Sol 负责实现，主任务统一分配所有权、构建和取证。不得自行派生代理或改其他执行者文件。

**Goal:** 在本轮原生样张封闭缺陷通过实图审查后，将准备、分享、库存三个真实页面对齐已认可原稿及可复现原生补充，保留全部业务行为，再按固定顺序完成其余 21 页。

**Architecture:** 只迁移页面构图、文字角色、语义色及局部滚动规则；真实数据、状态判定、回调和存储流程不变。生产代码不得依赖 `src/mock` 的样张类；共享组件仅提供显式页级 opt-in，默认行为不变。

**Tech Stack:** Kotlin、Android 原生 View/Canvas、现有 JVM 与 instrumentation 检查、隔离 Mock CI；不新增框架。

## 0. 仓库、状态与范围

按 [task-template](task-template.md) 和 [git-workflow](../git-workflow.md)：

- 来源：既有 `feature/task-first-ui`，与当前 `origin/develop` 的共同基线为 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；不另建长期并行分支、不重写历史。只读规划时 HEAD 为 `19d91c8c600d27f6933a17fa9698a87b00aa6c73`，三页生产实现未随样张修改；`f62b983` 是本轮样张候选。
- PR：既有 #4 → `develop`。主任务检查工作树/远端后普通推送，必需 branch-policy、verify、lifecycle、upgrade 通过且问题解决后才考虑合并；不推 `main/develop`，不 force、不取消在途 CI 来重跑。
- 本次仅写本计划，不修改/提交生产或测试代码。读取时 Sol 的 `ChineseFormWindowChecks.kt` 正在独占补证，随后由主任务提交为 `19d91c8`；不覆盖其改动。
- 当前**样张未通过最终图审**：`814e521` 仍有表格/日期内距、网格穿标签及分享 1.3 截图/几何冲突。`b083712/f62b983` 只是代码预审无阻断。主任务最新报告：新 CI 在 light1.3 准备初图多次完整采集均存在稳定的279像素差；末尾 `PixelCopy before display failed:-1` 是5秒总预算耗尽后未完成回调的哨兵，不是Android PixelCopy错误码，不能认作首次回调失败的根因。仅 light1.0 三页完整，产物在 `build/task-first-evidence/ci-f62b983-frame`；这是部分证据，不是12图完成。取证冲突仍待定位，执行者不得放松像素门槛。不得把本计划写成视觉通过记录。
- 协议/设备风险：不改编码、单位精度、控制队列、启动/停止门禁、未知记录、库存幂等、连接/重试、数据库；不新增设备命令。视觉测试仅隔离 Mock，不替代硬件验收。
- 发布：不发版、不签发 APK、不安装用户设备、不改包名/签名/versionCode，不操作机器或秤。
- 回退：各页布局及页级 opt-in 可追加修正或 revert；默认共享样式保持，业务模型/持久化格式不变。不得卸载、清库存/未知记录或重发未确认动作来恢复。

依据：[已认可设计](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[严格基线草案](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、[样张锚点](../research/task-first-ui-concepts/strict-native-sample-anchors.json)、[样张审查 §7](../evidence/2026-10-18-native-specimen-review.md#7-814e521-第二轮真实样张审查保留上轮-fail)、[24 页矩阵](../evidence/task-first-page-matrix.md)。旧矩阵的局部通过不得覆盖新运行失败。

## 1. 启动门：一次封闭样张，不无限追加研究

- [ ] Astra 核新源码/实际 PR merge SHA、APK/原图/PNG/参数文件 hash、窗口及 fixture 身份。
- [ ] 实看 12 张初始整窗；重点补看两主题分享 1.3 `after-scroll-5/6.png`，确认第四行与 privacy 真的出现，不只看 frame-sync accepted。
- [ ] 核 8 张图表的 6 条原网格及可见补集，标签保护框外扩 2dp；不删整条网格、不擦底色、不缩 14sp。
- [ ] 核分享每列 H8dp、库存日期 start=0/16/16dp 与 end=16dp 的实际行盒/净空；复核此前通过的单位基线、准备固定操作区及库存流水未回退。
- [ ] Astra 对上述封闭项给明确实图结论。仅修发现的本范围失败，不增加第四样张或新探针项目；通过后直接执行本计划。

### 最小设计决策（本计划给出，不再逐参数询问用户）

1. 原稿负责构图与层次；通过实图审查的原生样张负责确定性参数回归。原稿 PNG 不做非等比拉伸。用户窗口固定优先 `2560×1600/density360/zh-Hans`，实际可用区与 Insets 另记，不能强造 1138×623dp。
2. 字体采用既有 `Noto Sans SC 2.004-H2` 原生候选，SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。样张审查后可选作生产设计输入，始终不宣称它是 ImageGen 原字体。图像无法恢复原字体不是无限阻塞项；身份未知和原生输入是否按选定字体实现分别记账。
3. 角色沿用样张实测：正文16/400/24、辅助/单位14/400/22、分区20/500/30、标题24/500/36、大值40/700/60（字号/行高均 sp）；操作16/500/24。准备摘要用专用16/500/24，分享摘要用专用20/400/30。两个专用角色是设计层次，不是为过裁切门禁缩字。
4. 普通文字不伪粗、字距0、`includeFontPadding=false`；Canvas 同角色。数字和单位同 baseline，单位独立角色，不以父字号的 `.55` 比例代替。`tnum` 激活、实际 TextView glyph 字体来源等未被公共 API 证明的项继续 unknown；不得把 probe 命名为 actual rendering，也不把无法证明原图 tnum 作为无限停止条件。若某段真实文字发生 fallback 或字形异常，则该段另验，不因全局字体 hash 一律放行。
5. 通过样张的语义色板作为页级原生设计输入，light/dark 分别应用；保留背景/面/边框/选中/禁用/状态/图表等分角色，不全局替换资源。ImageGen 各稿色值波动已由严格基线明确选定，不继续猜测“唯一原色”。屏幕合成色仍须真实 PNG 核对。
6. 标准横屏沿用80dp导航、16dp页面边距、24dp主辅栏间距、8/16dp组间距。大字按实际 `sp` 换算与 Layout 自然高度流式增长。保留至少48dp可点击区域、56dp基础主操作；标准与大字分别验，禁止缩字/砍字段/全页压缩。
7. 状态来自真实业务。样张“演示”“已就绪”、最近配方副说明和日期仅为 fixture；生产无数据就显示既有缺失/未知说明，不填假数。Mock 的模拟身份标记保留，正式页不得硬编码已就绪或演示样张文案。

## 2. 所有权及最小改动边界

生产主批只含下列五文件，建议按下述顺序由一名 Sol 顺序实施；若主任务分配多名，先划清文件，不允许互改。

| 所有者责任 | 文件（均在 `mobile/src/main/kotlin/io/openhoyi/mobile/`） | 可改与禁改 |
|---|---|---|
| Sol 分享 | `CurveShareActivity.kt`、`CurveSharePreview.kt` | 调整分栏、摘要、操作顺序及全表局部滚动；文件内 `profileDocument/profileCard/profilePreviewDialog` 是共享功能，不能顺带改 |
| Sol 准备 | `HomeActivity.kt`、`HomeRecentCurves.kt` | 主辅区域、摘要/近期列表/设备展示/操作位置；不改 service 生命周期、状态分支、写入/停止与路由 |
| Sol 库存 | `BeanInventoryActivity.kt` | 仅 Activity 内列表/详情/操作样式；同文件末尾 `beanPage/beanInput/beanError` 是共享 helper，禁止默认全局换样式 |
| Sol 集成（单一所有者） | 必需时新增 `TaskProductVisualStyle.kt`；`CurveStageView.kt`、`CurvePreviewMetrics.kt` 仅显式 style 参数/属性 opt-in | 不让 main 引用 StrictVisualSample 类；旧调用默认保持原样。共享框架若无法页内配置，由主任务先列出确切桥接文件与默认不变 diff 后分配，不“顺手”全局改 `HoyiUi/TaskPageFrame/TaskNavigationView/TaskWorkspaceLayout/TaskCollectionLayout` |
| Sol 取证（单一所有者） | `HomeWorkbenchChecks.kt`、`AdvancedPageChecks.kt`、`AdvancedBusinessUiChecks.kt`，路径在 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/` | 同状态真实 Activity 整窗与业务不变量；不得把 StrictVisualSampleActivity 冒充真实页面。测试扩展也纳入同批审查 |
| Astra | 设计偏差与 G1–G4 结论 | 不负责改生产源码；主任务指定证据文档后才更新 matrix，保留旧失败 |

若必须扩大五个主文件之外：只允许完成页级样式桥接/真实图表复用所需的少量显式依赖，提前列出文件与默认不变证明；不以“五文件”硬限制迫使复制整个图表，也不借共享组件扩展其余21页。资源文案优先沿用已有本地化项，确需新语义时补全既有语言，不硬编码中文业务文案。

## 3. M1：三页成组实施，一次构建取证

### M1-A 分享先行：布局最独立，先建立真实表图取证

- [ ] `CurveShareActivity` 对齐左预览/右操作、24dp空隙与内容内16dp；移除当前两块大卡的不一致外壳，或按原稿保留必要的内容面，不改变可达入口。
- [ ] 右栏顺序固定为：分享方式标题 → 分享文本主按钮 → 复制文本 → 分隔 → 导出文件 → 分享文件及未导出说明 → 兼容性/完整详情 → privacy。说明与详情不再挤到主操作前；长说明局部滚动，主按钮仍可达。
- [ ] `CurveSharePreview` 摘要改专用20/400/30、单位14/400/22；图表压力使用阶段四色，不与准备页统一蓝混同。1–4阶段以真实 doc 顺序生成，表格 H8dp，表头/每行真实自然高度。
- [ ] 移除“预算不足就 `table.visibility=GONE`”策略。最小预算为其他自然内容 + 全表自然高度 + 图表至少160dp；不足时左预览局部滚动，右栏不随左侧滚动。不把无压力/FlowRaw 画成压力图。
- [ ] 保留 `doc.encode()`、复制、text/plain 分享、SAF CREATE_DOCUMENT、URI clipData/read permission、恢复 document/exportedUri；只有输出流成功写完才能启用文件分享。取消/失败不能变已导出，不新增网络/外发动作。
- [ ] 业务测试先覆盖取消/失败/成功三个导出状态（只隔离测试 URI/接收器），再迁移布局；图表/可达性红证据来自现有旧页，不为每个 padding 写镜像测试。

### M1-B 准备：按真实状态重组，不复制样张假状态

- [ ] 左栏按配方标题与更换入口 → 横向紧凑摘要 → 压力阶段图/真实不可用说明 → 最近使用组织；右栏按本杯豆子/粉量 → 机器与秤状态 → 确认/异常说明 → 主操作与详情组织。标准主辅边界沿样张锚点；额外警示出现时使用实际自然高度预算。
- [ ] `HomeRecentCurves` 保留最多3条真实最近记录及排序，取消“整块放不下就只剩右栏大按钮”的标准态行为，改左栏局部列表；每行至少48dp，名称16sp，真实次数/配方摘要14sp且不捏造。标准两整行、1.3至少一整行并全部可滚达；极短回退保留现有入口，不丢选择。
- [ ] 压力预览统一压力蓝、实际目标值及非时间轴语义不变。标准/1.3优先保留图和可读标签；极短可以沿用明确文字回退，但必须列全目标并另验，不拿回退图冒充标准原稿一致。
- [ ] 主按钮移入右栏固定操作区，但保留其当前条件：活动杯或可执行选择 → `ExtractionActivity`；否则 → `CurveActivity`。禁止直接调用 `startShot`；预设仍经 EXTRA_SLOT 路由。
- [ ] 保留最近选择 `canStart`、活动杯禁用、selected preference；使用次数只能由原业务记录，浏览/选择不新增使用事件。
- [ ] 保留粉量 `attempted/confirmed/shotId/associated` 全部分支及库存 CONSUME eventId/batchId/delta 匹配。确认展示不可由非空豆名替代。主数值改真实同 baseline 的独立单位角色，不能改 `BeanQuantity` 精度。
- [ ] 设备状态点/卡复用真实状态；扫描、权限、设备面板、预设、秤工具、设置、断连入口仍可达。保留显著警告、恢复确认、活动杯/手动杯与 emergencyStop；停止不得移入滚动区或藏进详情。

### M1-C 库存：接受真实交互引起的有理由排布差异

- [ ] 列表/详情按库存原稿主辅结构：豆款与总量标题、选中态、详情名旁批次徽标、40sp余额＋14sp单位、初始量/比例、三日期、最近流水、固定操作区。
- [ ] 三日期起点内距0/16/16dp、末端16dp，标题和值对齐；缺日期继续“未设置”，不显示 fixture 日期。大值与单位同 baseline，负数/超100%不隐藏、不篡改余额。
- [ ] **保留最近3条事件**，每行是至少48dp真实触控，包含类型/数量/原因及原详情回调。不得复制样张24dp只读两行。标准场景优先首屏显示标题、摘要、三日期和至少一条完整流水；其余局部滚达。两个事件的原专项仍要求保留两条完整首屏，若自然高度不满足应先设计重排，不能改旧门禁；三个事件不强造“三条都首屏”的新目标。
- [ ] 固定操作保留用豆 → `BeanPreparationActivity`、批次详情、批次流水；顶栏保留新增豆款/总流水。样张“调整”不能凭视觉新增直接改库存行为，现有路径继续负责调整。
- [ ] 选择批次、列表 scrollY/offset、单栏返回列表、重建恢复及数据刷新逻辑不变。查看/滚动不增删事件、不重复扣减；确认/取消业务仍归原页面。

### M1-D 汇合与证据（不逐处微推送）

- [ ] 三页实现完成后，Astra 一次检查关键构图/角色/颜色及业务差异，Sol 集中修正；不按每条边距单独推送。
- [ ] 在旧页或固定旧产物上运行新增有意义的表格可达/业务状态断言，保留红证据；不能删除旧失败或只写“预期会红”。
- [ ] 主任务统一运行一次匹配的 JVM 检查与 Mock/AndroidTest 编译；新增纯预算策略如有独立测试，验证边界/自然高度/字号而非复制实现常量。
- [ ] 无阻断后普通推送同一批；CI 保存三页标准12图、滚动后图及状态夹具。Astra 收齐同源码证据后成组审，G1–G4分开判定。

## 4. 真实 fixture 与验收清单

所有 fixture 写入只在隔离 Mock 测试仓库中；页面本身用真实 Activity/真实 render 分支，不由测试直接改 TextView 文字伪造业务。以固定输入生成正常中文主样张，长名/错误态单列。

| 页面 | 标准主状态 | 必验业务/自适应状态 |
|---|---|---|
| Home | 经典浓缩93°C/36g，4目标2/9/8/6bar；选中豆款18g确认且有匹配耗用事件；3条真实 usage；Mock 就绪状态明确标识 | 无选择、只读/无压力、无豆/待确认/确认冲突、未连接/过期秤、活动杯/停止与未知警示、长名、最近3行滚达；真实路由/无额外使用记录 |
| Share | 经典浓缩93°C/36g，4目标3/9/7/5bar及15/25/15/5ml，未导出 | 1/2/3阶段、FlowRaw只读、长名、导出取消/失败/成功/恢复；4行表和privacy初始/滚动后；右栏不跟左滚动 |
| Inventory | 三豆款余额90/160/250g、选中90/100g批次、三日期、入库+100g/调整-10g（两事件原专项） | 另建合法三事件状态：+100/-18/+8→90g，保留3事件详情；空态、长原因/长名、缺日期、比例>100%、选择/返回/重建；固定操作与所有事件可达 |

Home 18g耗用与库存90g示例属于独立 fixture，不伪称同一批次状态。合法仓库不支持某样张输入时保留实际值并标明差异，只比较同业务语义与布局，不能造不可执行曲线/假确认事件。

### Profile 与四项门禁

首轮主图共12张：三页 × light/dark × font1.0/1.3，中文、2560×1600、density360、固定CI镜像。随后完成该镜像支持的最大系统字号（记录实际配置）、横屏短高及现有横屏回归；本任务不兼容竖屏，不新增竖屏工作，也不以方向锁替代适配。既有非本任务 portrait/phone CI 保留，但不纳入本轮新增视觉验收；Home/Share/Inventory本页无编辑器时 IME 标 N/A 并写理由，不能用此免除后续表单页 IME。

| 门禁 | 独立证据与通过条件 |
|---|---|
| G1 | 原稿/样张语义锚点 → 真实页面语义节点映射，逐项边距、栏宽、内距、区域/描边、触控与滚动预算；同窗口PNG＋实际rect。库存48dp/3事件、真实警示等差异有本计划的设计理由；不是“布局没裁切”就通过 |
| G2 | 字体资产SHA、实际Typeface weight/style、fakeBold/字距、逐span角色、fallback异常；以选定原生候选为验收对象，ImageGen身份未知独立保留。Paint独立probe不得冒充TextView实际绘制字体 |
| G3 | 请求sp、Android实际px、实际Layout逐行baseline/行盒/ellipsis、单位span/独立节点、Canvas Paint与边界；字号1.3不能简单乘1.3推算。单位同baseline，不靠缩字、砍行或限高通过 |
| G4 | 页底、内容面、导航/选中、主辅文字、按钮enabled/disabled/focused/pressed、状态点、边框、图表色/alpha；实际ARGB/Drawable/Canvas＋PNG纯色内区。隔离Drawable采样不等于屏幕合成色，未观察的状态保留EVIDENCE_PENDING |

每页每必验状态均记录 PASS/FAIL/BASELINE_PENDING/EVIDENCE_PENDING；只限通过的实际对象和状态。缺原图字体参数不妨碍选择明确原生补充，但未审的真实布局/色值不得借“候选”绕过门禁。未知 probe 元数据只按其明确不可观测对象保留，不把与验收目标无关的原图不可恢复信息升级成永不完成的总门禁。

像素对照：原始整窗保留；只按记录的Insets截出应用区，禁止缩放、模糊或加任意容差。边界内部颜色、基线、换行、字重/笔画变化是实际差异。只有参数、布局、轮廓一致且差异确实局限栅格覆盖边缘，才能单列抗锯齿差异；不能用AA解释全部不一致。

每份证据保留 source/merge/head、dirty、APK SHA、CI run/job、fixture全文/状态来源、locale/theme/font/density/API、窗口/Insets/IME、PNG hash、实际Layout/Canvas、采集前后geometry与帧同步结果。几何和PNG不符直接EVIDENCE_CONFLICT；不拿JSON替图像结论。

## 5. 验证命令、范围与退出条件

仅主任务组织一次统一本地验证；本计划没有运行下列命令，也不声称结果：

```sh
git diff --check
ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2
```

预期 exit0；重点现有 `CurveStageParametersTest`、`CurveOrderingTest`、`BeanDoseCapturePolicyTest`、`BeanPreparationTest`、`TaskLayoutPolicyTest` 及新增预算边界测试。构建只证明可编译，不是运行/视觉/硬件通过。

通过现有 `.github/workflows/task-frame-ui.yml`、`advanced-mock-pages.yml`、`single-screen-ui.yml` 取证，CI disposable emulator 上沿用脚本入口，不对用户设备执行这些会安装/修改显示配置的脚本：

```sh
bash scripts/check_task_frame_ui.sh tablet-user-landscape
bash scripts/check_advanced_mock_pages.sh pages
bash scripts/check_advanced_mock_pages.sh business
bash scripts/check_single_screen_ui.sh tablet-user-landscape
bash scripts/check_single_screen_ui.sh tablet-landscape
```

已有流程不自动意味着新增fixture已覆盖；取证执行者须把本计划三页状态接入对应检查，缺项逐项报告。保留其他语言及现有横屏回归；已有非本任务 CI 不删除，但不新增竖屏兼容工作。不能只跑中文标准图就关闭功能回退。新增页级opt-in还需证明未opt-in页面的默认参数/行为不变。

退出 M1：三页所有必验状态四项门禁及业务回归完成；有单项失败则只修对应已明确缺陷并成组重验。通过项记录有限冻结，后续未改的参数不重复研究。硬件验收仍单独未完成，不阻止纯视觉迁移，不伪称硬件通过。

## 6. 剩余21页排序：固定范围，不开新样张项目

M1完成后直接按下表原稿逐组迁移与真实取证。当前短高IME诊断可先独立执行；稳定失败未补证前，不盲改生产 padding。它不属于样张研究，也不能在24页最终交付中漏掉。

| 批次 | 页面（去重后总数24） | 优先闭环 |
|---|---|---|
| M1 / 3页 | Home、CurveShare、BeanInventory | 本计划，真实业务迁移 |
| M2 / 3页 | BrewReview、CurveEditor、BeanEntry | 原短高IME失败先稳定取证；52dp短输入/固定保存/草稿选区恢复；不能以一张普通态代替 |
| M3 / 3页 | Curve、CurveImport、CurveImportPreview | 已修footer与全表可达不退化；长名、只读、冲突、导入IME、用户profile |
| M4 / 3页 | BeanPreparation、BeanBatch、BeanLedger | 选择/确认/幂等、48dp流水详情、真实原因/日期/余额；不给不存在的时间补假值 |
| M5 / 4页 | Extraction、History、HistoryDetail、BrewComparison | 真实四指标/无秤、状态/停止/未知、采样/长复盘/同尺度对比；硬件控制另验 |
| M6 / 2页 | Scale、ScaleConnection | 有效/过期/负值/趋势与候选连接列表，实验只读能力；不把空态当全部完成 |
| M7 / 3页 | LegacyCurve、LegacyHistory、LegacyHistoryDetail | 非空/长名/来源与缺字段、压力bar/流量ml/s，不冒充新记录 |
| M8 / 3页 | AppSettings、MachineSettings、ScaleSettings | 三设置稿仍为候选，Astra在原方向内完成原生补充；完整分组/大字/失败回滚/能力说明，不做真实机器写入 |

每组先列真实必验状态与共享规则复用/例外，再集中实现、一次构建/推送、同源码整组验收。21功能+3设置范围不减；不能因为共享字体/色板已选定就外推其余页G1–G4已通过。

## 7. 当前未完成

- 新 `f62b983` 样张取证只完成 light1.0 三页，1.3 多次完整采集稳定存在279像素差，末尾 `-1` 为5秒预算耗尽哨兵而非Android错误码；取证冲突待定位，12图未齐、分享1.3旧冲突未关闭；M1生产实施门未打开。本计划可执行但不是通过证明。
- 三产品页尚未按本计划迁移；五文件之外确需的页级桥接由主任务划定单一所有权。
- 原表单 short-height/IME 真实失败正在补稳定现场，不能被三页视觉成功覆盖。
- 其余21页、最大字号/所有状态、硬件/签名/发布均没有因本文获得通过。
