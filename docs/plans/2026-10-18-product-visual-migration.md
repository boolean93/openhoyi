# 真实三页视觉迁移实施计划

> **For agentic workers:** 使用 `superpowers:executing-plans` 按本计划逐项执行；Astra 负责设计/验收，Sol 负责实现，主任务统一分配所有权、构建和取证。不得自行派生代理或改其他执行者文件。

**Goal:** 在本轮原生样张封闭缺陷通过实图审查后，将准备、分享、库存三个真实页面对齐已认可原稿及可复现原生补充，保留全部业务行为，再按固定顺序完成其余 21 页。

**Architecture:** 只迁移页面构图、文字角色、语义色及局部滚动规则；真实数据、状态判定、回调和存储流程不变。生产代码不得依赖 `src/mock` 的样张类；共享组件仅提供显式页级 opt-in，默认行为不变。

**Tech Stack:** Kotlin、Android 原生 View/Canvas、现有 JVM 与 instrumentation 检查、隔离 Mock CI；不新增框架。

> **当前状态索引（2026-10-19，HEAD `026f993`）：** 原生样张已限定冻结，见 [review §10](../evidence/2026-10-18-native-specimen-review.md)；真实 M1 在 `026f993` / CI `39146205031` 的三页12配置已获 **§11限定验收**，可进入既定M2；不是24页/全状态/硬件通过。下方早期 §0/§1 是历史前置记录，不再据此重开样张或重复执行旧启动门；当前修订与验收按本计划 **§8–§11**，保留历史证据。

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

## 8. 2026-10-19 Home 首批实图后的封闭修订

### 状态、证据与所有权

前文 §0/§7 是写计划当时的历史状态，不再代表当前进度。样张已由 [审查 §10](../evidence/2026-10-18-native-specimen-review.md) 有限冻结并启动 M1；真实三页未获整体通过。生产首批 `7e3f662`、测试 `9c71043` 后的真实 Home 证据来自 head `0f3b759` / tested merge `e357bdcad6bcd643e59d4bc23b271f36b3116fac`：

`build/task-first-evidence/ci-0f3b759-frame/task-frame-tablet-user-landscape/task-first-evidence/product-visual-migration/raw-device/e357bdcad6bcd643e59d4bc23b271f36b3116fac/tablet-user-landscape-light-font-1.0/home/`。

Astra 已实看 `window.png`，对照 `brew-preparation-v2.png` 与 §10 接受的准备样张；同目录 `HomeActivity-typography.json`、`anchors.json`、`baseline-alignment.json` 用于实测，不以其布局 PASS 替代视觉。该图确认真实单位三组 baseline 差0、压力图可读及部分底色已对齐，但存在下述结构/层次遗漏，Home 仍不可判整页通过。`ce0eebe/cb50af4` 未改 Home，新分享/库存图独立验收。

- Sol 生产所有权：`HomeActivity.kt`、`HomeRecentCurves.kt`；`TaskProductVisualStyle.kt` 仅 Home 的 `TaskProductRecipeMetrics`，必要时新增生产页内图标辅助；不得依赖 Mock 样张类。资源新增语义补全现有语言。
- 主任务另授权 `TaskNavigationView.kt` **仅现有 product style opt-in** 图形映射：杯/记录/豆/齿轮，替换当前 house/clock/leaf/sliders；24dp 图标、原80dp rail/64dp行/14sp标签/路由不变。null 旧分支及其余21页不改。图形是原生候选路径，不冒称恢复 ImageGen 精确路径。
- Astra 负责这组设计和代码/实图复核；测试与帧同步仍由原测试所有者维护，本组不放松任何旧门禁。修订集中验证/推送，不逐边距微推送。

### A. 页头：真实状态与连接操作分离

- 保留当前真实页面标题及 Mock 身份说明；subtitle 使用14/400/22与 muted，不改成无条件“配方与粉量已确认”。正式模式不能显示“演示数据”。
- 右侧改两张150dp、最小64dp高的状态卡，间距8dp；24dp机器/秤原生图标，内距8dp、圆角12dp；标签14/400/22，状态14/500/22。低强调面 light `#ECEFF1` / dark `#263234`。自然文本更高时增高，不压行盒。
- 卡片触控至少48dp，**只调用原 `showDevicePanel()`**，无扫描/连接/去皮副作用；无障碍说明明确“设备状态，打开连接控制”。原右上单个设备连接按钮由两卡共同披露替代，入口不删除。
- `render()` 从同一 `s.coffeeState/s.scaleState` 及现有 `coffeeFresh/liveScale` 读取；连接文案沿用 `label(state)`，实时必须由现有新鲜度成立。连接成功不等于可冲煮；没有快照保持未连接/未知，失败/不支持保留原危险语义，不以假“已就绪”复制样张。
- 页头最小80dp，之后间距8dp与1dp border 横分隔；真实警示产生的自然高度单独预算。状态卡不足宽时自然重排，不能缩字或隐藏连接入口。

### B. 主栏：补齐配方层次，不改实际曲线

- 配方行至少48dp，名称24/500/36；旁隔16dp补真实“阶段数与压力/水量控制模式”辅助说明14/400/22，来自当前展示的 `CurveLibraryItem` draft/factory/captured 参数。缺参明确未知，不写死四段。原更换/浏览动作、选择详情回调保留。
- 温度/目标/阶段摘要补24dp温度计/杯/阶段图标，图标与字间8dp；保留真实labels、16/500/24数字与14/400/22单位同 baseline、组间竖线，组后补1dp border。无目标和缺失沿原语义，不将0变成假36g。
- 主副栏仍24dp空隙，中间放1dp border；不挤改已确定栏宽。图仍真实 targets、14sp、0/6/12、至少160dp、2dp墨迹保护及原说明，不恢复五刻度拥挤布局。
- `onPrimarySpaceAvailable` 将新增行、分隔及 margins 按实际测量计入自然预算；不能只加 View 而沿用旧高度和裁切结果。

### C. 最近使用：补齐真实信息与行分隔

- 标题与“更多配方 ›”同一至少48dp行，更多仍到原 `CurveActivity`；标题边界与各行用1dp border。
- 保留3条真实排序/选择、原 `canStart` 与活动杯禁用、原 usage count 文案和语义tag。标准仅分配前两条实际自然行高，1.3前一条；所有条目仍局部滚达，不再用剩余空间吃满三行而挤图。
- 名称按冻结样张实际使用的 **16/500/24**（action角色）；辅助说明、实际count、温度/液重/阶段摘要14/400/22。此处更正初次聊天写的名称400，不能以当前实现倒推认可。
- 左组为名称、真实控制模式/阶段说明和独立count；右组为紧凑参数，组间16dp。宽不足时次说明自然换行、行至少48dp且不强限高度。保留count独立节点；缺参数用—，0目标仍无目标。不得捏造“柔和风味”等原稿示例，也不因选择/浏览新增使用事件。

### D. 本杯：明确编辑入口及自然预算

- 标题20/500/30，titleRow至少42dp；标题分隔 top2 + 1dp线 + bottom7。增加咖啡豆标签14/400/22；豆名24/500/36与独立至少48dp“更换”同组，沿原 `BeanPreparationActivity` 路由。
- 粉量标签保留原 `attempted/confirmed/shotId/associated` 和 CONSUME event 匹配判定；标签后间距8dp，大值40/700/60与单位14/400/22同 baseline，右侧至少48dp“编辑”仍到原用豆页面。不得仅由豆名判定确认；真实18不强造18.0。
- 机器/秤行16/400/24、状态点及完整连接/实时/未知/失败文本保留；正常新鲜状态用冻结 accent，其他状态按真实分类，不重定义就绪条件。
- bean、dose、machine、scale后分隔均 **Home 页内 top8 + 1dp线 + bottom7**，不沿用通用16/16，也不改通用默认值。dose不再叠加旧上下16dp额外padding。body至dock间距8dp。
- 以上是自然布局最小值。标准正常fixture核心豆/粉量/两个状态应完整；1.3或真实警示允许body局部滚动并完整可达，不新增“大字所有状态硬塞首屏”要求。

### E. 低频披露与固定操作

- `temperatureSummary`、`usageSummary`及其History回调、quickPresets、额外用豆/秤入口移到现有 `devicePanel` 的“准备详情”分组，保留原对象、更新和回调。面板标题可准确标为准备与设备详情；不重写已有扫描、断连、设置、萃取、去皮等控制逻辑。
- 右栏innerScroll只放本杯核心数据与两设备状态。固定dock依次为24dp info图标+原确认说明14/400/22、间距8、原主操作至少56dp、间距8、“查看准备详情 ›”至少48dp，详情仍 `showDevicePanel()`。
- 主操作保留真实“查看萃取准备”等状态与全部路由，不冒充样张直接开始萃取，也不画暗示直接startShot的播放三角；可用查看方向图标。显著安全警告、恢复确认、service status、活动/手动杯和emergencyStop不移到低频披露区。
- 极短横屏保留已有明确fallback与入口，不以缩字号/减少自然触控高度/隐藏固定操作达标。

### 本批严格验收与退出边界

本批角色沿 §1；正文16/400/24、辅助14/400/22、分区20/500/30、标题24/500/36、大值40/700/60、操作16/500/24，字距0、无伪粗、字体资产身份不变。新增按钮须在创建完成后应用页级style，避免现“设备连接”晚创建导致旧52px行高与 `#1C2631` 字色泄漏。

页/字/线采用已冻结 light/dark：page `#F9FAF9/#11191A`、ink `#101820/#EDF3F2`、muted `#657183/#A9B9B8`、border `#DDE3E7/#3B4B4C`、primary `#087A78`、accent `#087A78/#83D6D0`；状态、选中/禁用与 pressed/focused 仍按真实状态独立采证，未看到的不写通过。

- **G1：**同真实fixture两主题×1.0/1.3四整窗；页头/主副栏/分组分隔、标准最近两条或大字一条、固定确认/主操作/详情完整。主/辅局部滚动后目标完整及恢复；长名/警示/短高按已有门禁，不隐藏安全信息。
- **G2：**候选字体SHA、weight/style/字距/fakeBold；ImageGen原字体未知身份不改，不能借未知跳过本轮实际字体输入验证。
- **G3：**实际 Layout 行盒、换行/ellipsis、每个角色sp/px和新增文本；摘要与剂量单位 baseline 差0；图表文字14sp及自然墨迹保护。不靠缩字或限高通过。
- **G4：**实际ARGB/Drawable/Canvas与整窗纯色内区分别核背景、字色、卡面/描边、选中/状态；控件独立ARGB不是合成像素。保留glyph/tnum无法证明项，不填假PASS。
- **业务：**原四条主操作路由、最近选择/无新usage事件、用豆编辑、详情内旧操作、服务/警告/停止及剂量关联门禁不降。图标不是独立触控，真实卡/按钮仍至少48dp。

本节只授权这批实现与取证，不代表Home或M1已通过；其他21页与硬件仍未验。等待Sol集中diff审查及同源码实图，不重开样张研究。

## 9. 2026-10-19 Share 长名称自然预算补充

证据为 `build/task-first-evidence/ci-cb50af4-advanced/advanced-mock-pages-business/` 下 `business-flows.txt` 与 `advanced-page-screenshots/business/curve-share-four-stages.png`、`curve-share-four-stages-space.txt`。真实名称 `UI recipe` 加 UUID 自然显示两行；fontScale1.0，表1305×245px、图1305×360px（160dp）。原表格检查逐行包含header：失败 `row 3` 是**第3条阶段数据**，其49px只见44px，并不是第4末行只差5px。其full底1398、viewport可视底1393，第4条还需49px，故完整四行至少短缺 **54px**。PNG与该解释一致，阶段4未出现。

本轮PNG实为2560×1600；x0/20纯色区域的应用边界在y63与1465，应用高1402px，density2.25下约623.11dp。`screenDp=1138x623`是资源配置，不代替实际尺寸；也不能套用旧产品fixture的1391px/618.22dp。下载的windows dump已清理回launcher，不冒充Share失败时刻geometry。

主任务批准的唯一生产范围为 `CurveSharePreview.kt`，由原生产Sol实施：

- 保留 `cb50af4` 的页头单一8dp间距、pane四边16dp、完整两行名称、20sp摘要、14sp表文与图文字、图至少160dp及全部五条表行。
- 先按实际内宽测量原上下构图的所有自然高度、margins和全表；仅当图取160dp仍超出真实可用高度、且以下两段自然宽度加16dp不大于内宽时，将它们变为**同一个自适应元信息行**。
- 左边保留原阶段数文案（14/500/22）；右边保留原“阶段目标示意，不是时间预测”说明（14/400/22，muted），两段间距至少16dp，两文本各自保持语义节点。行前、到表之间各8dp，不隐藏、缩字号或省略任何一段。
- 该语义合并消除一条约49px自然行盒及18px组距，当前fixture预计释放67px，覆盖54px缺口；此数值只解释预算，不硬编码为高度或通过阈值。实测后仍按真实全表首屏断言判断。
- 短名称且原预算足够时维持原稿上下构图；同排自然宽度不足则保持上下排与左局部滚动，不挤压右栏，不预置scrollY、不把滚动后图冒充初态。大字/其他语言继续既有可达性要求，本标准长名原有全表首屏门禁不降。
- 原图/字段/导出与SAF状态、字重、颜色、单位baseline、图表targets和grid保护不改。同步记录是否启用同行、原/新自然预算与实际viewport；重验标准短名、该真实长名、两主题1.3及FlowRaw，确认没有新增裁切/ellipsis和语义遗漏。

该修订是有条件的分组重排，不是逐像素削padding。等待集中diff、构建及同源码真实图审；不能由预计67px收益直接判通过。

## 10. `cb50af4` 浅色1.0三真实页有限图审与剩余收口

### 已检查的证据及限定结论

来源：CI `39144065820`，head `cb50af4c6e7357f53c2bca4f7e0c830b70e9779d`，tested merge `c1a0930ef5cecbf7932a95bf24f0dc326f1ce8e7`。共同目录：

`build/task-first-evidence/ci-cb50af4-frame/task-frame-tablet-user-landscape/task-first-evidence/product-visual-migration/raw-device/c1a0930ef5cecbf7932a95bf24f0dc326f1ce8e7/tablet-user-landscape-light-font-1.0/`。

Astra 实看 `home/share/inventory/window.png`，并逐一对照原稿和 §10 已接受的三个原生样张；读取三页 manifest、anchors、实际 typography、baseline。三份manifest共74份命名文件SHA独立重算一致；真实合成整屏2560×1600，应用origin(0,74)、size2560×1391、density360、zh-Hans、font1.0、无IME。仅 **3/12**：后续三事件业务检查在首次layout前查找新节点遇NPE，后9配置没有运行，不能据此完成整矩阵。

| 页 | G1结构/间距 | G2字体候选输入 | G3字号/行盒/单位 | G4区域/文字颜色 |
|---|---|---|---|---|
| Home | FAIL：§8遗漏仍在，无新的独立计划 | 同一字体SHA；recent名称400须按已冻结500修正，未知glyph身份不伪证 | 摘要/粉量三对baseline差0；晚创建旧设备按钮行高52px仍偏离action54px | page/nav/selected实像素匹配；Mock副文案/旧设备按钮颜色及新增状态卡按§8修 |
| Share | 正常短名4行表初态完整，限定关闭旧13px裁切；原长名54px仍FAIL，另有下列图标/badge/返回差异 | 实际44个TextView的角色weight/字距与已选输入相符；不证明ImageGen字体或实际fallback glyph | 20sp摘要=45px、action16sp=36px、aux14sp=31.5px；两组baseline均491px；privacy两行Layout95px，不以getter49px冒充行盒 | 主要面/按钮/禁用色实图匹配；表头应ink却为muted，须修；新图标状态待验 |
| Inventory | 当前主fixture header/两事件完整；仍缺列表层次/分隔/固定说明。已批准摘要重排及48dp事件保留 | 42个TextView角色weight/字距匹配所用候选输入；列表拆分后重验真实各节点 | 40sp余额=90px、135px请求行高/130px实际Layout、单位31.5px，两者baseline450px；两事件各108px=48dp完整 | page/选中豆款/比例/主按钮/表头背景实像素匹配；缺失分隔/图标/说明不是“颜色已过” |

实际getter/Layout差异保留：标题81/79px、section67/65px、body54/52px、aux49/46px，不能将二者混写。字体资产仍为已选 `a3041811…99af0da`，不是恢复原图字体证明。独立probe/字形墨迹/tnum未证内容继续按原边界记unknown，不新开研究。

独立PNG纯色样本（屏幕坐标，均alpha255）：三页(200,230) `#F9FAF9`、(10,100) `#FFFFFF`；Home(30,250)、Share(30,400)、Inventory(30,750)选中导航均 `#E2F0ED`；Share面(1700,280) `#FFFFFF`、禁用(1800,850)/表头(300,1160) `#ECEFF1`、主按钮(1800,430) `#087A78`；Inventory选中豆款(240,340) `#E2F0ED`、比例(1400,505)/主按钮(1300,1380) `#087A78`、表头(1300,805) `#ECEFF1`。这些只证明已采静态内区，不外推dark/pressed/focused或硬件。

库存主图实际两事件rect为(1193,841)-(2524,949)、(1193,949)-(2524,1057)，各108px；header title/summary均1行、无width0。metadata被fillViewport撑到1040px，不将它冒称UNSPEC自然高度。独立新advanced长名fixture `inventory-ledger-first-screen.txt` 也记录两事件完整：y909–1017、1017–1125。仅关闭这些同状态旧裁切；三事件详情/恢复仍等待测试修正后完整执行。

主任务后续已读 `cb50af4` 终态：single三profile成功，advanced pages成功、business仅上述Share长名54px失败。它们不替代本节逐图结论，也不证明所有状态的视觉一致。BeanEntry M2保留此前初态1px失败定位及初态稳定/五字段/固定footer取证计划；本轮绿不证明旧失败唯一根因已修，也不预设M2必须修改生产布局。先按已规划状态验真实图和稳定几何，有实际缺陷才改。

### Share：同批确定修订

- 文件仍 `CurveShareActivity.kt/CurveSharePreview.kt`，执行 §9。正常短名mode badge紧邻名称、间距16dp；只有自然名+badge+16超内宽时才给名称剩余宽自然两行。压力/FlowRaw值来自doc，不硬编码兼容。
- 四个真实动作补24dp share/copy/export/share图标、图文间8dp、整体居中；原Button身份、SAF状态和回调可保留，禁用图标同onDisabled，不改变按钮enabled。原生候选路径，不声称ImageGen精确路径。
- 表头三格改ink，保留14/500/22及H8dp；正文、阶段dot/数据/图表/摘要框不改。
- 原描边返回按钮外壳改透明底、accent 24dp箭头、至少48dp触控；原back/无障碍行为不变。只作用于product style opt-in，其他页面/null路径不改。保留真实info帮助入口，不能补假“设计示意”。

### Inventory：同批确定修订，不推翻真实交互适配

- 页头短title与真实总量summary自然相邻、gap16，剩余弹性留在整组之后，避免title weight把summary推至右端；宽不足仍原自然上下排，不重现MATCH_PARENT横排bug。补页头1dp横线、主副栏1dp分隔；侧栏标题使用“在库咖啡豆/在库豆子”语义，不重复页面名。
- 列表：24dp豆图标+12dp间距、真实名16/400/24弹性区、右余额16/400/24、8dp后16dp chevron。每行min64、H16/V8内距、行间8、首行距标题14；非选行底1dp border，选中面保持selected。真实批次N以名称下14/400/22辅助行保留，不能只藏入无障碍；长名自然换行，余额/箭头不相撞。仍原选择、tag、滚动恢复与返回。
- 可使用真实可点击容器替代整段旧Button，不自绘假文字。测试所有者只对依赖旧Button类名的batch selector改用View，原isShown/isEnabled/完整触控/performClick及业务断言必须保留；生产者不改测试。
- 详情短名旁badge gap16，长名分配真实剩余宽自然增长。**已批准40sp余额+右侧初始量/标签并排、比例行、48dp真实事件、三真实操作路由全部保留**，不恢复样张24dp只读行。
- 日期已有0/16/16起点、end16、标签值gap8；补两条1dp竖分隔，只描画不占新增列宽。事件与表头补1dp底线，不增行高。库存不是Share网格表，不强套Share H8：保留当前1:1:2原因列及8dp尾净空，header/正文同列对齐、最多3条及详情回调不变。
- 固定原三操作补图标：用豆cup24、批次详情details/chevron、批次流水record/chevron，图文gap8，真实文字/顺序/路由保持。不得用cube暗示直接改库存，也不得把批次详情换成样张“调整库存”。其后增加top8、info24+14/400/22 muted的“确认本杯用量后才扣减库存。”固定说明，准确对应既有确认消费语义，不产生新扣减。
- 不另增重复48dp“查看全部”标题行，现固定“此批次流水”已保留入口；这属于真实事件自然预算适配。新说明应按自然高度计入dock，原两事件首屏、48dp、长名/大字及三事件全可达门禁不降。当前长名两事件末底1125至pane底1393尚有空间，但必须重跑验证，不以静态预算声称通过。

本节与§8/§9合为一次三页收口清单；源数据/实际回调不变，颜色和字体输入不重新选择。等待集中diff审查、构建及四profile真实图，不能以本次浅色1.0部分可用或机械PASS宣布M1/24页完成。

### 本批落盘后的静态复核（构建/新实图之前）

Astra 只读核对了生产及取证diff，结论为**无剩余静态阻断，可统一构建后取证**，不是运行或视觉PASS：

- Home 真状态卡只披露原面板；状态来自原snapshot与新鲜度。原剂量关联分支、四路由、安全警示/停止不改；低频View与回调搬到详情。Recent已从错误的固定三层改为真实TextView自然宽flow，count独立原文/tag保留，正常两行/大字一行预算按自然高度。subtitle明确muted、padding0、title后topMargin8，补回冻结间距。
- Share自适应元信息在mode变化时仅失效自身测量，使用`resolvedInline`统一measure/layout；宽度检查不再污染前次高度测量。RTL按logical start/end镜像，原baseline不变。图高下限、字角色、五行表和SAF回调没有降低/删除。
- 图标helper保留真实Button文字/回调/状态，依据实际Layout行边界移动原生start drawable，禁用读取currentTextColor；不是仅以gravity声明图文居中。最终图标位置、墨迹、RTL与多行效果仍须实际PNG，不把该公式当渲染通过。
- 库存列表/tag/选择/滚动恢复、48dp事件及最多3条、三真实路由、40sp余额/日期角色均保留；规则线仅foreground不占预算。固定说明按自然高度进入dock。当前旧标准及长名两个事件的已采空间支持该预算方向，但新dock、长名及1.3仍须运行原首屏/可达门禁；不能凭预计高度关闭。共享`beanPage`等文件尾部helper已独立字节比对不变。
- 导航与返回改动仅既有product opt-in；null旧路径保留。新增home/inventory共16份语言XML可解析，同组键一致，不宣称翻译文字的所有窄屏效果已经验过。
- 三事件测试将render与点击分开，有限等待实际frame commit和真实tag/attached/laidOut/无pending/focus/正尺寸，接受前及退出循环前均严格检查5秒预算；超时仍FAIL且清理前保留明确未同步诊断图。没有重试业务断言。库存selector仅从Button类名改为实际batch-tag可点击View，原业务/恢复/完整性断言保留。

本次只运行了只读检查与`git diff --check`；统一构建由主任务安排。待同源码三页12图及长名/事件业务证据，再独立执行G1–G4；未采主题/字号/状态、硬件与其余21页均不能外推。

后续构建记录：生产分批提交为 `ff5f069`（Home/Share/页级辅助）与 `1d82b2f`（库存），测试为 `c7223e5`。首次构建的Recent Paint接收者遮蔽编译错误已仅以限定外层style引用修正；最终日志 `/tmp/2026-10-19-m1-three-page-closure-build-final.log` 实读为 `BUILD SUCCESSFUL in 1m 2s`，Mock/AndroidTest编译完成。JVM XML独立汇总72 suites、321 tests、0 failures、0 errors、1 skipped（既有skip）。这只证明本轮本地检查/构建结果，新CI整窗、三事件实际执行和视觉验收仍未完成。


## 11. 2026-10-19 `026f993` 真实 M1 限定验收，进入 M2

**结论：Home / Share / Inventory 在本节真实夹具、中文用户横屏、浅深×1.0/1.3范围内，G1–G4限定通过；本轮无新增必修差异，允许按已提交M2计划启动三个真实表单迁移。** 这是对原稿、已冻结原生输入与§8–§10明确生产适配的联合验收，不是ImageGen逐像素复制、其他21页、全部业务状态或硬件验收。前节旧FAIL是历史证据，不再作为当前相同场景的未关闭项。

### 身份和实际检查范围

- frame run `39146205031`，head `026f9937f055342ba7256d2f446065b3bbdb0746`，tested merge/source `75e5333f20bb21ec911ec99befb9d979ad483dba`，12 manifest均sourceDirty=false、realProductActivity=true、specimenOnly=false；原artifact的ASTRA_PENDING不回写成通过。本节是独立人审记录。
- 共同目录：`build/task-first-evidence/ci-026f993-frame/task-frame-tablet-user-landscape/task-first-evidence/product-visual-migration/75e5333f20bb21ec911ec99befb9d979ad483dba/`；`tablet-user-landscape-{light,dark}-font-{1.0,1.3}/{home,share,inventory}/window.png`共12张逐张实看。API34、zh-Hans、density360，原PNG2560×1600；实测app origin(0,74)、size2560×1391，无IME。APK SHA `e5975f4854726ce81188995faeef85504666205be8dfb3de366340d2b7844ab2`是manifest声明，不冒充本次独立重算APK。
- 另实看两主题1.3：Home `after-scroll-2.png`，Share `after-scroll-5.png/after-scroll-6.png`，Inventory `after-scroll-2.png`共8张，联合各页`scroll-reachability.json`、`anchors.json`、实际Typography、`canvas.json`、`baseline-alignment.json`。重新对照原`brew-preparation-v2.png/curve-share-v1.png/bean-inventory-v3.png`；不以代码或机械PASS替代图审。
- 结合独立business长名证据：`build/task-first-evidence/ci-026f993-advanced/advanced-mock-pages-business/advanced-page-screenshots/business/`中的`curve-share-four-stages.png/-space.txt`与`inventory-selected-after-adjustment.png/inventory-ledger-first-screen.txt`。两PNG均2560×1600，SHA分别`12c446f35473e342d3b360dec9e4582880c11391be6d8d92a14fb6e8285dff52`、`977ec25efdc9577adffc23b04a81c12f254b46db0c9106e83b9fb651e652c886`。

### 逐页四门结论

| 页面 | G1 构图/间距与适配 | G2 字体输入 | G3 字号/行盒/单位 | G4 背景/状态色 |
|---|---|---|---|---|
| Home | **限定PASS**：真实两设备卡、Mock识别、横向摘要及分隔、主图/最近、右栏核心与固定操作均落地；1.0两条最近、1.3一条，三条局部可达。1.3卡内状态自然两行，未缩字/省略；右侧秤状态须局部滚动，固定确认/主操作/详情未随内容滚走 | **限定PASS**：选定字体、recent name500、辅助400、设备state500与既定角色一致；原图字体身份仍unknown | **限定PASS**：摘要两组与粉量共3 baseline/配置均差0；图0/6/12、真实2/9/8/6bar与标签带可辨。实际行盒与换行可读，无截断固定文字 | **限定PASS**：浅深页/导航、卡面、主辅字、状态点、主按钮/图蓝符合冻结语义；没有以原图假“就绪”替换真实状态 |
| Share | **限定PASS**：双面、相邻mode、摘要、图表、操作分组、底说明与图标整体居中；标准短名全表首屏，长名两行+元信息同行恢复全表。1.3表尾/隐私采用原允许局部滚动，不声明默认全表首屏 | **限定PASS**：标题500、20sp数值400、表头500/正文400、操作500；未改变原字体身份未知边界 | **限定PASS**：每配置两组单位baseline0，表格H8与阶段点净空、图14sp标签/grid保护无可见重叠；大字after5/6实像素分别完整显示第4行/隐私 | **限定PASS**：表头ink纠正、面/描边/阶段色、主/描边/文本/禁用动作及其图标一致；没有把disabled当可导出状态 |
| Inventory | **限定PASS**：header总量相邻、列表图标/批次/余额/箭头、主副分隔、日期分隔和流水底线、固定三真实操作/说明齐；接受已批准大余额与初始量并排的生产适配，真实事件仍48dp而非样张24dp | **限定PASS**：name/balance16/400、batch14/400、主值40/700、action16/500及辅助角色一致 | **限定PASS**：每配置余额单位baseline0；标准两事件首屏、1.3第二事件完整滚达；日期三列净空、长名和固定说明无实际丢字 | **限定PASS**：选中豆款、页底、比例、表头、描边、主辅文字和三动作图标色符合两主题输入；未外推未采焦点/按压 |

### 不与首屏混写的1.3滚动事实

两主题几何一致，后图均实际看见目标，不仅依赖JSON：Home秤状态由初态不完整变为`[1877,949,2367,1015]`完整，确认/按钮/详情固定；Share第4行原`[252,1346,1557,1407]`可视到1393，after5为`[252,1332,1557,1393]`完整；隐私after6为`[1683,1276,2488,1393]`完整。Inventory第二事件原`[1193,1120,2524,1228]`可视到1206，after2为`[1193,1098,2524,1206]`完整。各对应记录scrollRestored=true。`getGlobalVisibleRect=false`时返回内容不是屏幕有效visibleRect，Home初态秤节点失败时的残留局部矩形不当屏幕坐标引用。

标准长名Share business文本只给table1305×245/chart1305×362、tableShown=true；PNG实看表头+4行完整，**限定关闭旧54px缺表**，不虚构逐row rect。Inventory同business两事件`y920..1028/1028..1136`各108px，完整；新增固定说明后的该长名预算闭环。原始两PNG/日志保留，不覆盖旧失败。

### 字体/行盒、颜色与运行证据边界

本批584个TextView（每配置Home53/Share44/Inventory49）均有actual Layout；字距非0、includeFontPadding=true、复制实际Paint的fakeBold=true均为0。两主题一致的实测序列为`sizePx / getter lineHeight / 单行Layout高`：

| 冻结角色 | font1.0 | font1.3 |
|---|---|---|
| aux14 | 31.5 / 49 / 46 | 42.3 / 56 / 61 |
| body/action/compact16 | 36 / 54 / 52 | 45.45 / 59 / 66 |
| section/shareMetric20 | 45 / 67 / 65 | 53.100002 / 67 / 77 |
| title24 | 54 / 81 / 79 | 59.399998 / 81 / 86 |
| large40 | 90 / 135 / 130 | 90 / 135 / 130 |

平台实际非线性sp换算照实记录，不用fontScale乘算，也不把getter行高当Layout。两行aux实际Layout为1.0的95px、1.3的117px；24组实际数字/单位baseline差0。字体SHA仍`a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`；实际TextView glyph墨迹/原ImageGen字体身份、tnum启用未知保留，ASCII等advance不证明tnum。没有因此重开字体研究。

独立PNG内区采样与实际resolved ARGB分别核：page `F9FAF9/11191A`、nav/surface `FFFFFF/1B2526`、卡面/disabled `ECEFF1/263234`、selected `E2F0ED/203D3B`、primary两主题`087A78`，内区alpha255；ink/muted/accent与既定两主题输入相符。以实际节点rect确认库存selected/action内区，排除字缘抗锯齿像素，不用任意色差容差。图表8图的轴/标签/网格补集与真targets可辨；未改变已冻三刻度/160dp下限。

运行机械报告与人审分开：12主配置、24 baseline0、56目标可达/恢复、12隔离报告、4组SAF与4组三事件业务均通过；protocol2保留同域零差与跨域诊断边界。主任务终态确认single三横屏各336/336、list14/14，库存返回/重建offset用户617/标准399/大字369各自一致；advanced pages两profile各240 fixture、business18路径通过。这些只支撑各自断言，不能外推原图/硬件。可执行M2；错误、警示、其他语言/最大字/极短窗口等未在人审矩阵覆盖的状态及其他21页仍逐项待验，不撤销旧历史证据。
