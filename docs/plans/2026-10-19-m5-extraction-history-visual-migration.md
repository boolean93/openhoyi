# M5 实时萃取、历史与两杯比较视觉迁移计划

> **For agentic workers:** 按 `superpowers:executing-plans` 执行。Astra 设计/验收；主任务明确分配生产、测试 Sol 的文件，统一构建推送。不是新样张项目，不覆盖其他 owner 修改，不取消在途 CI。

**Goal:** 迁移 ExtractionActivity、HistoryActivity、HistoryDetailActivity、BrewComparisonActivity 四个真实页面，严格对照既有原稿的构图、间距、字体/字号和背景，保留实际设备能力、观测来源、未知记录与原业务。

**Architecture:** 四页显式 opt-in M1 原生样式；已有 telemetry/projection/history/journal 与原服务 owner 不变。显示层独立适配，不修改模型来迎合图片。历史内嵌详情与独立详情共用展示接口，旧版历史不迁入本批。

**Tech Stack:** Kotlin、Android View/ListView/Canvas、现有模型/JVM/instrumentation、隔离 Mock、已验 `NativeWindowFrameEvidence` protocol2。

## 0. 模板、依赖和当前状态

**当前执行索引（2026-10-19，5ef3b8f）：** 主任务已核实际依赖，同意M5四页展示生产可独立启动，并已明确授权§8.3最小同源时间桥接；原按批次等待及该局部禁改Service表述由此替代，其它服务/协议行为仍禁止修改。当前仅完成本计划与依赖审，四页/16配置范围不减。生产由同一Sol顺序负责§3全部展示文件/图表及三个clock桥接文件，测试Sol负责独立scope/入口；共享文件仍单owner串行。不能拿无服务/非RUNNING图替代Extraction；历史三页测试可先准备。M2/M3/M4新原件优先图审，其未验状态不被本授权覆盖。下列0647518/33b6e85记录保留为最初规划基线。

- 遵守 [task-template](task-template.md) 与 [git-workflow](../git-workflow.md)。来源 `develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；延续授权 `feature/task-first-ui`，PR #4 → `develop`。本次生产只读基线 `0647518`；记录时 HEAD `33b6e85779f16d215828ba2ac57be057329d146e` 仅另含两旧 UI 入口脚本修正。
- 当前仅 M5 规划，未授权生产实施。M2 实图/缺陷闭环优先；M3 已获独立部分先行授权，不能按编号误写 M2 已完成。M5 开工由主任务核实际依赖和 owner 后明确分配；不要求为无依赖页面等待编号，但不能并改共享文件。
- 固定24页，中文、2560×1600/density360 横屏优先；保留横屏短高、1.3大字及已有其它语言回归，不新增竖屏工作、不锁方向。已有非本任务 CI 保留，不纳新竖屏验收。
- 只提交所属文件 → 本地验证 → 主任务普通 push → PR 必需 branch-policy/verify/lifecycle/upgrade 与评审；不直推 main/develop、不 force、不单独微改推送。
- 不改 BLE、命令/队列、控制状态机、启动/停止/秤策略、采样解码/保存精度、schema、导入导出格式、未知记录或重试语义。Mock 不证明物理停水/杯重/温度校准。
- 不发版、不改包名/签名/versionCode、不安装或操作用户机器。回退为追加修正/revert PR；不卸载清库、不删 UNKNOWN、不自动重发未知操作。

## 1. 已有设计依据与准确边界

依据 [认可规格](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[图表能力](../research/2026-10-15-extraction-chart-capabilities.md)、[严格基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、[M1 §11](2026-10-18-product-visual-migration.md)。本次已实看以下原 PNG，无新图/新研究；旧能力文档提及竖屏的历史建议不覆盖当前横屏范围。

| 原 PNG（`docs/research/task-first-ui-concepts/`） | 尺寸 | SHA-256 | 对照重点 |
|---|---|---|---|
| extraction-four-series-v3.png | 1585×992 | dab980016ff32a5134e1d6ac767c0afad7b2edc1aed741dbac8e5c7e5f9741f2 | 左时间/有效重量主值与同图四指标，右真实本杯/固定停止 |
| extraction-no-scale-v2.png | 1585×992 | 82af60e9351a23140b2e105bac638d7d2849cb9325ceba37759bfe76bafb7867 | 时间/注水主值、三指标与真实无秤说明 |
| extraction-stopping-v2.png | 1584×993 | 6fce98ba64faa2e7cadaeffe5dbf81bfae3eebd824f6d3bd947ec783b83b9201 | 请求中状态与固定原停止控件，不冒称已停 |
| extraction-unknown-v2.png | 1584×993 | 2eb37ec11fe102a6f1b8946010ea3d52035aef9b425689e7073d2f776037d52c | 高优先警告、最后有效时间、缺失当前读数与过去轨迹 |
| history-list-v3.png | 1586×992 | f3b9d375ed73a445f9a00752c7a213ca79b1f8c69b7ac327af03062881384283 | 左真实来源/连续列表，右选中摘要、图、笔记与固定入口 |
| history-detail-v2.png | 1586×992 | 7eaaee545d1cf6b13402d210e49b71453d26f6aef47c6a4a876e9875574f5a6d | 左指标/同图，右实际笔记与固定编辑复盘 |
| brew-comparison-v1.png | 1586×992 | 7c7f43ebf4ce7508ab302c2aef75753e930dbde196c33b799e2d95c1d6832269 | 两杯选择/交换、单指标同尺度、真实摘要表/详情路由 |

ImageGen 字体身份、精确 sp/色值不从图反推；概念数值/端点也不是实际观测。继续固定候选字体 SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`，不因未知身份无限阻塞。原图未包含的深色/大字/缺数据属于明确原生适配。

## 2. 视觉输入与同图数据契约

- 冻结角色：title24/500/36、section20/500/30、body16/400/24、action16/500/24、aux14/400/22、large40/700/60。萃取时间/有效重量或注水采用严格基线已有的 **候选** focus48/700/72、单位22/400/34；这两角色和危险/警告色须在本批实图限定验收，不冒称 M1 已覆盖。历史主值用40/700/60，普通单位14同 baseline。通过独立节点或可证真实 span 实现，不能用中心对齐假充基线。
- 0字距、无伪粗/额外 font padding；真实 Android sp 换算与实际 Layout 行盒，不能按1.3直接乘算，也不降字号过预算。页边16、栏间24、组间16、关联8、面内16、surface圆角12/输入10/描边1dp；rail80。按钮至少48、主操作至少56且自然增高。冻结 light/dark page/surface/ink/muted/selected/border/accent 直接复用 `TaskProductVisualStyle`，不全局替换资源。
- 图轴/图例14sp/400，实际 Paint/字形盒留8dp标签带，网格与标签保护框相交时只排除网格可见段（2dp保护+半stroke），不删轴/标签/全部网格。按实际有效系列测量轴列，不把4列硬挤；保留现有极短无可读图提示，但常规主图不得以提示替代图。
- 四系列候选来自严格基线：压力 light/dark `#176BD1/#8CB8FF` 实线；水 `#007E89/#66CAD3` 长虚线；重量 `#00685F/#83D6B9` 实线/点；温度 `#A46500/#F2C16D` 点线。网格使用 border。停止 `#B5302D`/白字；警告背景/描边/文字 light `#FFF5E8/#D7B782/#795119`、dark `#382F22/#A78B52/#EACB87`，仅对应真实状态。候选视觉待本批实图核验，不改变原 StopActionPresentation 的文案/可用条件。

| 显示 | 唯一来源与约束 |
|---|---|
| 压力 bar | `pressureTenthsBar / 10`；已知非冲煮段不硬接压力线，未知 phase 不伪称已知 |
| 累计注水 ml | `totalWaterTenthsMl` / `ShotPoint.waterTenthsMl`；不从流速积分，重置/回绕断段，不自行累加修复 |
| 有效出液/秤读数 g | 新鲜 `weightHundredthsGram / 100`；无有效杯重/归零上下文只称秤读数。没有秤不画0g，中途失效保留过去片段，当前“—” |
| 温度 °C | 原 `brewTemperatureHundredthsC` / `temperatureHundredthsC`；不得混用用于预热控制的校正温度 |
| 时间与参考 | 实际 elapsed/采样时间；UNKNOWN 的最后有效时间不冒称当前或结束时间。只允许用户明确选择的真实已存参考，标身份/起点，不用配方生成未来线 |

保留 `FourMetricChartProjection/ChartInspection/ExtractionReference`：共用时间轴、独立物理量程，同杯范围稳定扩展；不裁峰/负值，不补零、桥接缺失或延长最后采样。原 gap/reset/单调时间验证不改。ml/s 与 g/s 只保留原高级信息，不默认第5/6条线。两杯默认一次一个指标，A/B共用相同尺度、实虚线区分，不默认八线。

## 3. 文件所有权与隔离默认

生产路径 `mobile/src/main/kotlin/io/openhoyi/mobile/`；测试路径 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/`。以下列完整允许候选范围，实际开工仍按主任务分配；未必要的文件不为凑清单修改。

| owner | 文件 | 边界 |
|---|---|---|
| 生产 Sol A | `ExtractionActivity.kt`、`TaskLiveReadouts.kt`、`TaskRecipeSummary.kt`、`TaskExtractionWorkspace.kt` | 后三者负责展示/自然预算，不能写 telemetry/model；Workspace 同时供历史详情，改接口必须与 B 协调、默认旧路径保留 |
| 生产 Sol B（或同一 Sol 顺序） | `HistoryActivity.kt`、`HistoryDetailActivity.kt`、`HistoryDetailContent.kt`、`BrewComparisonActivity.kt`、`TaskComparisonLayout.kt`、`ComparisonMetricTabs.kt`、`ComparisonSummaryTable.kt` | 内嵌/独立详情共用；选择/列表/异步/读写回调保持；默认未 opt-in 调用不变 |
| 单一图表 owner | `ExtractionOverlayView.kt`、`ShotChartView.kt`、`TwoCupChartView.kt`，必要独立 `ExtractionHistoryVisualStyle.kt` 与八语种 `values*/extraction_history_visual.xml` | 新 style 默认 null，旧 renderer 路径保留，四页明确传入；独立 Paint，不能污染其它图表或修改投影数据。局部新角色/状态色不扩全局 TaskProductVisualStyle |
| 测试 Sol | 新 `ExtractionHistoryVisualMigrationChecks.kt`、新 `scripts/check_extraction_history_visual_migration.sh`、独立 `extraction-history-visual-ui.yml`；必要 `FourMetricChartChecks/TwoCupComparisonPageChecks/SingleScreenListStateChecks`、`BrewAudioInstrumentation` 显式入口 | 共享测试/runner/workflow 单 owner 串行；不向旧task-frame追加16配置，保留其它批次/原门禁/原protocol2。组件图不充整页图 |
| Astra | 本计划及明确授权的统一记录 | 设计、源码静态审、实图 G1–G4；不改产品/设备 |

复用 Frame/Nav/TaskCollectionLayout 的现有接口，不改默认。禁止修改 `MobileService/ShotSeries/ShotSamplesStore/FourMetricChartProjection/ChartInspection/ExtractionReference/LiveTelemetry/StopActionPresentation/ShotGate/BrewJournal/ShotHistory` 业务；`LegacyHistory*` 仅保留原路由，不迁样式/数据。若确实缺公共展示接口，报告具体调用链后由主任务定 owner，不扩大共享默认。

## 4. 四页封闭实施规格

### A. Extraction：现场状态优先

1. 主区读数→同图、右区本杯→设备/明确参考→固定动作。两栏保留现实际右宽30%及280–320dp边界、24dp间距；足宽读数同一行，实际自然宽不足才辅助组换行，不靠缩字。按实际标题/读数/图例/警告/footer高度分配图，不漏系统Insets；极短保留原可读提示/局部内容滚动，停止完整固定。
2. 原 readiness、preparation/notification/alarm 警告仍高优先可见，不移入更多或为凑图隐藏。原设备、选择配方、豆子、预热/取消、参考、详细信息、反馈入口完整；运行中 enabled/visibility 与当前控制层一致。
3. 本杯 recipe 使用 `activeShotRecipe/traceIdentity` 与该杯真实 notes，不用当前另选配方替换运行快照。源缺失“—”；不抄原稿假18g/目标36g/设备就绪。无秤仅在原允许启动模式显示水为另一主值，不绕过重量模式门禁；READY但stale不能以旧重量充实时，也不借UI切换启动条件。
4. STARTING/RUNNING/STOP_REQUESTED/UNKNOWN 依真实模型显示。原 stop、confirmStart、预热、cancel 监听与校验顺序保持；请求停止不直接写 ENDED，不新增自动重试。离页仍原绑定/可见性/反馈行为，不停止、启动或新建第二 owner。保留通知/前后台/服务重建门禁。

### B. History：连续来源列表与选中预览

1. 左来源/计数/连续 ListView，右选中完整名/日期/状态、实际摘要/图/笔记、固定复盘与完整记录入口。实际 ListView 保留，不分页、不改排序；原 local/journal 分别的 selectedId、Parcelable viewport、detailRequested、短高返回列表与重建语义不变。
2. ENDED/UNKNOWN 状态可辨；缺样本、缺notes、journal不可用、空库分别说明，不把 unavailable 当空库或恢复写入。右图使用实际选中记录，不用上一项异步结果；请求返回后核身份/attached。
3. 原本地包导出、旧版导入、长期记录导出、显式迁移及旧版入口均保留原路由/确认/能力与IO。可在原更多分组中披露，不能因原稿省略而删除，不能新增自动迁移/解析保存。复盘只对可编辑的长期记录开放。

### C. HistoryDetail：观测与笔记分工

1. 左时间/重量主值、实际水量辅助与同图；右bean/dose/grind/曲线期待/taste/next真实notes，原稿没有“曲线期待”仍保留。编辑复盘固定，完整记录与比较路由保持 shotId。局部滚动可达所有笔记，不把字体降为说明级拟合。
2. elapsed/weight/status来自 Observation，不用图末点覆盖记录事实。注水辅助若来自samples，明确“末次采样注水”；无样本不造终量，UNKNOWN或reset仍保留限定，不新增存储字段。
3. `HistoryDetailContent` 两调用均传样式；内嵌/独立操作区别不消失。sample异步加载/失败/卸载与attached检查保留。完整详情原起止/原因、scale来源与高级原机流量/秤流量可达，不把ml和g混为一个量。

### D. BrewComparison：真实两杯、一次一指标

1. 顶部A/B原选择器与交换、4指标可选、全宽同尺度plot、三列摘要（项目/A/B）和真实详情/编辑入口。选择器至少52自然高，完整长名自然两行或已有详情披露，不能按原稿假固定名称。表格 H8、组间16、单位同baseline；长taste保留原48dp完整详情目标。
2. 仅两条不同有效ID才绘；placeholder/删除后失效/同一杯保持原清空规则。A/B调换不重写数据，selectedMetric与IDs重建恢复，异步cache按真实observation与当前选择核对；destroy关闭reader，不用旧callback覆盖新选择。
3. “同豆/同配方”等仅真实字段一致才出现；不推断同粉量、好坏分数或最佳杯。UNKNOWN、缺重量/温度、部分样本/reset/gap逐杯明确，比较只按已存数据。原4tabs、统一range、plot自然最小高度与标准图宽门禁不下调。

## 5. 最小真实夹具与安全隔离

主矩阵 **4页×浅深×font1.0/1.3=16**，中文2560×1600/density360，实际app窗口/insets独立记录。真实 Activity/model/render，不改TextView造数值，不用组件或概念图背景代替整页。

- 历史 A 合法 ENDED Observation：开始 `1700000000000`、elapsed27400、end=start+27400、weight3620；B 下一天、elapsed31000、weight3950。两者同真实可解析curveId、不同id；A notes固定 bean“晨间拼配”、dose18000mg、grind“2.3圈 / 中细磨”、期待“后段流速略快，希望更平稳。”、taste“柑橘酸质，焦糖甜感，尾段略干。”、next“研磨调细一点，其他条件不变。”。其它列表项用合法独立观测组成足以真实非零滚动的连续列表，含UNKNOWN/缺样本/无notes，不靠假摘要。
- 样本是明确合成的合法 Mock输入，不宣称原图/硬件测量：A 每1000ms至27000再27400，B至31000；water整数按各自elapsed线性到604/650（十分之一ml），weight到3620/3950（百分之一g），温度9300/压力90、brewing=true；machineFlow100为独立原字段，不用于积分。manifest存全部实际输入。缺秤用null，gap/reset/非brewing/超范围另独立case，不污染主图正常夹具。
- Extraction 主夹具 RUNNING、实际一杯身份与真实有效机器/秤观察、样本末时24600ms；没有有效tare上下文就明确“秤读数”。补无秤合法非重量模式、stale/断秤、STOP_REQUESTED、UNKNOWN、无服务/ready、手动、真实历史参考开关。必须先列 **MobileService 当前实际持有对象与安全 Mock 注入路径** 交审，不能套静态页10Lazy就称服务隔离完成。
- 固定主图需要可重复且前后一致的真实测试状态。使用已有guarded Mock能力提供一致的owner/telemetry/time输入；不冻结生产handler、不改current getters、不延长freshness、不遮像素。若现有路径不能稳定满足protocol2，先报告具体fixture接口缺口，不减原零差/5s预算。动态行为用原状态机/lifecycle门禁另测，静态帧不证明实时正确。
- 历史/比较scope可沿已审10Lazy闭包逐项核依赖：合法内存模型、原Lazy仅身份/initialized不求值、history callbacks全部指向scope，trace/samples只owned cache；language内存、实际触及prefs逐键typed/existence恢复。不能借新增导出/旧版导入触发原legacy初始化；相关IO必须独立owned临时文件/合法原入口测试，未隔离依赖先交审。
- 全Activity/dialog destroyed与解绑、各handler/reader停止 → 确认scoped service destroyed → owned samples/trace writer drain → 恢复原对象/明确白名单prefs → 原业务盘hash/legacy身份与initialized不变。异步读线程同样不能跨恢复继续持有scope；不清库/回滚盘追hash。失败停止后续，host最终force-stop；Mock/qemu/fresh进程guard沿最新fail-closed实现。

## 6. 验证、顺序与四门

- [ ] 主任务明确启动/owner，核当时HEAD/status、M2/M3/M4共享接口和原门禁；先列live fixture依赖闭包，不抢共享文件。
- [ ] 先统一图表显式style/真实Paint接口与HistoryDetailContent，再四页成组实施；原模型/控制不变。每页自然预算测完整标题、真实行盒、图例/轴、固定footer；不得凭少几px削padding。
- [ ] 静态审全部结构/角色/颜色/单位/状态与默认隔离，统一构建：`./gradlew :mobile:testMockUnitTest --tests io.openhoyi.mobile.FourMetricChartProjectionTest --tests io.openhoyi.mobile.ChartInspectionTest --tests io.openhoyi.mobile.ExtractionReferenceTest --tests io.openhoyi.mobile.LiveTelemetryTest --tests io.openhoyi.mobile.StopActionPresentationTest --tests io.openhoyi.mobile.BrewJournalHistoryTest --tests io.openhoyi.mobile.ShotHistoryTest --tests io.openhoyi.mobile.ShotHistoryArchiveTest :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain`。仅新增实质算法补对应边界测试，不给静态padding写镜像测试。
- [ ] 原 `FourMetricChartChecks/ChartLegendChecks/LanguageChartChecks/TrendChartTextChecks/TwoCupChartChecks/TwoCupComparisonPageChecks`、TaskFrame/单屏/列表14、advanced业务、lifecycle/transport unknown门禁全保留。特别原4metric/48触控、短图明确提示、标准comparison全toolbar/图宽≥80%与实际plot下限、placeholder清身份/交换/重建、不改samples；History两来源各自非零scroll/selected返回恢复。不能以新16主图替换这些断言。
- [ ] 真实main16图用原protocol2，保存原整屏/双方域proof、完整实际Layout/Paint、geometry/单位baseline、滚动前后与restore、source/head/merge/APK/fixture/原稿hash。动态/IME/系统dialog未证明同帧如实unknown，不mask/放宽；每次失败清理前保存原件。
- [ ] Astra按浅1.0四页→深1.0→两主题1.3→特殊真实状态/业务图逐组看；先闭环明确差异再有限冻结，统一记录而不微推送。

| 门禁 | 必须独立判定 |
|---|---|
| G1 间距/构图 | 同状态同实际可用窗口；16/24/8/16、表格/日期内距、主辅读数层次、固定停止/复盘、真实自然高度、轴列/网格标签保护、列表与图检查可达；标准首屏与局部滚达不能混为通过 |
| G2 字体 | font资产SHA、实际Typeface/weight/letterSpacing/fakeBold来源；ImageGen身份与未观测glyph/tnum仍unknown，不用对象equals/数字equal advance冒充特性启用 |
| G3 字号/行盒/单位 | 全TextView实际sp→px/逐行Layout、Canvas真实Paint与墨迹框、40/48主值及22/14单位baseline；非线性缩放与字体fallback如实记录，getter lineHeight不等于行盒，不因图拥挤退11sp轴字 |
| G4 背景/颜色 | 两主题page/surface/selected/disabled/border、实际状态warn/stop、4series颜色/线型/alpha；配置ARGB与原屏纯色内区分开核，不把drawable配置当合成像素；抗锯齿边缘不掩盖真实色/布局差异 |

## 7. 当前完成与未完成

已完成七张原稿、四Activity/详情共享源、readouts/recipe/workspace/comparison/plot与真实数据projection、现有仪表/列表门禁只读核对；本计划是可执行设计输入。M5代码/构建/16主图/live隔离运行/特殊状态/G1–G4均未完成，不授予PASS。原图未知字体不重开研究；缺少服务夹具证据不伪造实时状态。M1限定结论、M2未决缺陷、其它页及硬件边界各自保持，不以本批规划覆盖。

## 8. 5ef3b8f 实际依赖收口与最小运行契约

### 8.1 可独立启动范围

四页展示层、图表opt-in与历史详情共用展示没有引用M2/M3/M4页面的布局对象；生产同一owner可按§3顺序实施，不以编号等待。消费现有Frame/Nav/Workspace与已提交style，默认旧路径保持。测试先实现三历史页的合法10Lazy/owned samples/真实reader闭包和完整16入口骨架；Extraction四配置明确pending，不能把已采12/16报告成完整M5。独立workflow保留原所有CI，不扩旧task-frame链；45min候选上限需按实际完整运行核对，不靠减配置或无限加timeout。

### 8.2 Extraction 实际服务持有与清理顺序

源码已核：`MobileService.onCreate`取得并持有app.logs/history，向同scope feedbackPreferences及原app.visibility注册ownerId观察者；另持有series、MockDeviceRuntime、feedback tracker/delivery。Extraction.onStart以flags0绑定已有service且每250ms render，onStop移除refresh并unbind。服务mockTick/watchShot也每250ms运行，停止/销毁可能追加history/samples，因此原模型绝不能提前恢复。

1. fresh独立Mock进程、无原窗口/服务；安装合法10Lazy整组后才创建唯一Mock service。原history callbacks、logs、samples、usage/journal/preparation/inventory全部指向scope；创建后核service.logs/history身份、mock存在且hub=null，记录ownerId。语言/反馈为scope，不触真实BLE。测试不通过Home绕路启动额外owner。
2. `shotRecovery`、`machineWriteRecovery`、`coffeeCredentials`不是app那10Lazy；只读其初始化状态并盘点实际调用路径，不能借已有原偏好伪造清洁状态。Mock正常启动/停止分支无需真实写入恢复意图；如果特殊状态测试会求值/写这些独立store，先给合法内存替换闭包再授权，不能清空原恢复记录。业务盘核对扩含真实 `noBackupFilesDir` 中pending恢复文件，不仅files/shared_prefs/databases。
3. 所有Extraction/详情/对话框destroyed，页面service=null/bound=false/visible=false且refresh退出 → 主线程停止该唯一scope服务并证实onDestroy → service的mockTick/watchShot/leaveForeground/stopAutomaticScale无排队回调，feedbackPreferences与app.visibility对应ownerId已unobserve，hub关闭 → reader退出并main barrier → owned samples writer和trace drain → 撤销测试时钟（若获批）/恢复10Lazy与typed prefs → 原身份/initialized/业务盘核对。任何一环未知或失败停止，不先restore、不回滚原文件。原app.visibility诊断observer保留，不clear整个observer表。

### 8.3 已授权同源单调时钟桥接（尚未实施）

现有`MockDeviceRuntime.sample(now)`虽有参数，但`MobileService.mockTick`每250ms强传`SystemClock.elapsedRealtime()`并按真实atMs追加ShotSeries；Extraction的freshness/告警/启动说明也直接读系统时间。没有现成可注入clock。仅反射snapshot、改startedAt、停handler、延freshness或屏蔽动态图都不能解决为真实主图提供一致输入的问题。

主任务已明确授权仅三生产文件：新`MobileMonotonicClock.kt`、`MobileService.kt`、`ExtractionActivity.kt`。默认每次返回原系统elapsedRealtime；只有BuildConfig.MOCK_MODE且明确独立测试scope才允许一次安装可单调前进的clock，真实构建即使存在override也必须系统值。安装/撤销为单owner、身份可核，拒绝重复owner和时间倒退；失败也沿相同排空顺序清理，不能以恢复默认为由提前撤钟。Service单调时间源与Extraction四处时间读取统一；`MockDeviceRuntime`/`ShotSeries`/`LiveTelemetry`/控制状态机算法不改，不混墙钟与单调时钟。该授权只替代原§3禁止Service修改中的时间来源一项；采样值、协议/命令、状态机、handler频率及其它服务行为仍不许修改。

scope在service创建前安装clock；通过真实Mock启动/合法观测路径，按manifest声明时间序列前进至主fixture。取证时仅逻辑时刻不前进，原250ms handler照常执行，重复时刻由原ShotSeries非递增规则拒绝新增点，freshness与服务/页面使用同一个时刻。显式推进后核stale/新采样/结束等真实结果；不直接写TextView、状态getter或曲线投影，不新增不存在的遥测能力。测试等待/协议2五秒预算必须继续用真实系统时间，不能被fixture clock冻结。该模式不证明真实watchdog延时、物理时序或硬件；原默认真实clock的lifecycle/stop/预热/transport门禁完整保留。其它会用系统时间读该service的页面不纳入固定clock scope；若需要路由必须先审同源缺口。

### 8.4 历史与比较读取排空

- `HistoryDetailContent` 每次渲染创建裸`history-detail-reader`，先加载samples再chart.post；内嵌History切换也会产生旧reader。新scope起始核无该名存活线程，在关闭全部相关Activity/窗口之后join仅该fresh scope的新reader，循环核至无存活reader并作main barrier；不只查当前可见chart。旧post的isFinishing/isDestroyed/attached防护保持，不能把页面销毁等同IO已终止。若测试无法可靠取得owned线程，才提最小只读跟踪接口，不先改读逻辑。
- `BrewComparisonActivity.readers`为两线程池；onDestroy只有shutdownNow，并不等待任务退出。测试保留每个实际实例的reader引用，destroy后awaitTermination成功再main barrier，之后才drain样本writer与还原app对象。任务内会读取`app.samples`，因此不能先恢复原Lazy再等线程。保留SourceKey与当前选择校验，不将迟到A样本绘成B。
- 主图/列表/详情/比较不触legacy导入导出。若本批补显式IO业务，则另列`journal-export`及MobileApplication导入/导出线程、owned URI/provider和legacy依赖，排空后再恢复；未隔离时只保留旧原门禁，不冒称新scope已覆盖。样本先经合法owned Repository保存并等待对应writer屏障，文件格式/时间单调/缺失与坏文件照原规则，不替换load返回值。

本节仅完成源码依赖与方案审计，未改生产、未运行设备。四页生产及已授权clock桥接可前进；实现需独立静态审查后才接入真实16配置。G1–G4仍逐页实图判断，不能因隔离/时钟方案获批继承视觉PASS。


## 9. 首版静态审查与封闭修正（2026-10-19，尚无16图验收）

当前生产17 Kotlin+8scope XML及独立测试入口已实施；§7及§8.3“未实施”是规划时历史状态，不再作为等待条件。基于45fdc9e的首版构建证据为 `build/task-first-evidence/m5-first-full-build/verification.json`：8 suites/47 tests/0fail/0error/0skip及APK构建成功；这不覆盖随后候选修订，更不证明视觉通过。

首版全组静态审已完成。四页显式opt-in；默认null renderer/helper保持；真实控制、来源、列表身份与reader防迟到语义未见新增阻断。Service37处、Extraction4处单调时间入口同源，real始终SystemClock，Mock单owner/拒倒退，协议2的真实5s计时不变。服务recovery闭包、异步排空及完整4×4=16测试入口静态可执行，运行仍待。G2原稿字体身份/tnum未知仍如实保留。

本批必须先收口三项：

1. **History内嵌预览自然预算。** 旧固定块按名义角色加总约406dp（先前聊天506dp算术错误已纠正），约518dp正常区域只余112dp给图，低于现真实Paint轴区+原图头自然预算。数字仅静态估算，不冒称运行测量。仅embedded分支按原稿语义重排：title24与more48同一永久行；date/status/chartStatus自然flow（16横/8纵），保完整实际加载/失败说明；原metrics label14、8gap、40主值/20水量不变；图后16gap，底部豆款摘要行，再口感摘要左与原review56/full-page48动作组右，宽不足自然换行。保原全文披露/回调，禁止resize reparent。量完整header/metrics/footer及实际图最低高度；标准主fixture不能以compactNotice代替真实图，极短仅内容局部可达、操作固定，不削字号或图预算。
2. **Comparison单位精度。** 移除 `AbsoluteSizeSpan(sp(14).toInt())` 的整数px截断，现MetricAffectingSpan在measure/draw共同写实际float sp(14)及400typeface；同TextView全文、baseline及selector保持。用户density360下14sp=31.5px不能静默变31px。
3. **Comparison状态背景。** tabs外框opt-in使用冻结palette、1dp边/12dp圆角；null旧路径不变。SummaryTable不得用最终纯背景循环覆盖taste原Ripple/focus，正常surface+border、focus accent边、ripple selected；按钮选中/按压/焦点与非交互状态保留，不用颜色配置冒充屏幕合成色验收。

上述三项交生产Sol实施，Astra只做复核。其余首版无新增静态阻断；修订后须冻结构建、新16真实PNG/实际Layout与Paint/同域协议及业务回归，再逐页判G1–G4。当前不是M5视觉通过，也不覆盖未生成特殊状态、其它页或硬件。


### §9 三项候选收口的静态复核

已复核冻结的 `HistoryDetailContent/ComparisonSummaryTable/ComparisonMetricTabs`：embedded永久title/meta/metrics与两行fixed footer已分离；ScrollView子内容以resolved EXACT测量，图预算含同生产14sp/400 Paint轴区及真实native图头，不靠设置LayoutParams假定ScrollView约束。40/20/14角色、16/8间距及真实异步/完整披露/原操作保持。比较单位span在measure/draw两路均使用float sp(14)+400；tabs与taste选中/焦点/禁用/按压palette路径已收口，null旧路径保留。

三项静态阻断已关闭，可进入冻结构建/CI；这不是G1–G4通过。必须实测标准fixture fullNatural与available、初态完整图及固定footer，大字/短高局部滚达/恢复。预算JSON不能代替真实Layout/屏幕证据，16配置尚未验收。

## 10. 91ab835 首轮完整16主图验收：未通过，保留原件

### 10.1 身份、范围与运行边界

CI `39193804077`，head `91ab835aaf28d9e133a78efd620d7780ba3e7192`，真实测试merge/source `a0816c2425823f1ad1da916a191f1d88f9211d99`。原ZIP与保存校验在 `build/task-first-evidence/ci-91ab835-m5/{original.zip,preservation.json}`；以下证据共同根为其 `raw/a0816c2425823f1ad1da916a191f1d88f9211d99/`。每页路径为 `tablet-user-landscape-{light,dark}-font-{1.0,1.3}/{extraction,history,history-detail,comparison}/`。

Astra已逐张实看16张 `window.png`，与§1四张对应认可原稿对照，并读取实际geometry、Typography和Canvas。独立重算16份manifest命名的408个文件hash，未发现不符；PNG均2560×1600，主图app区域2560×1391。另实看浅深1.3的 `history-detail/states/notes-local-scroll-reachable/window.png`，末项“下次调整”实际出现，固定编辑/比较/详情仍可见。未将16张主图外所有状态截图都冒称已视觉验收。

本轮最终CI失败是清理 `font_scale`：原key absent，delete返回0后实际present `1.0`。主16取证成功不等于scope最终恢复成功，不等于设计通过。历史5特殊样本的新测试代码不在该次运行内；STARTING/STOP_REQUESTED/UNKNOWN/stale等未生成实时状态仍pending。其它20页、24页整体、真实设备与协议行为不外推。

### 10.2 四门分开判定

| 页面 | G1 间距/构图 | G2 字体/字重 | G3 字号/行盒/基线 | G4 背景/状态色 |
|---|---|---|---|---|
| Extraction | 四主图有效读数、同图与固定停止可见；1.3右栏辅助入口在局部viewport边界，尚不能用主图证明所有入口滚达。无新增明确裁切修复项；不授全页PASS | 已采角色/字重符合候选，限定本轮文本节点；不声称ImageGen字体身份 | 48/22主值单位、20辅助/14说明角色已实测；局部可达与所有状态未完成，不由getter推全页通过 | 纯色区/停止色实屏匹配；警告/按压/禁用等未采状态不授通过 |
| History | **FAIL**：图被挤成细条，1.0图底裁切，1.3首屏只有图例/轴标题而无plot；列表有单位独立换行；顶部与底部两个“查看详情”不同动作 | 已采40/20/14等字重无配置偏差，不抵消布局失败 | **FAIL（显示结果）**：时间轴被裁；不是靠缩轴字号修。原角色/独立单位继续保留 | 主背景/surface与选中配色可有限核对；不能据此把构图FAIL改PASS |
| HistoryDetail | 四主图和浅深1.3 notes滚动图未见新增必修裁切；原稿未含的真实曲线期待保留。当前为限定观察，不外推所有记录/状态 | 已采角色/字重符合候选 | 主值/单位、20标题/16正文/14说明实际字号可证；notes末项已实看可达，未把首屏局部滚动边界误判为文本丢失 | 已采背景/曲线候选色未见新增偏差；缺样本/UNKNOWN及交互颜色待特殊状态实图 |
| Comparison | **FAIL**：图被summary优先预算长期压到约96dp，原稿的大图层级未迁入；ru1.3选杯全文被截断 | 已采角色/400单位span符合候选 | **FAIL（全文）**：原advanced已证selected文字ellipsis；不以contentDescription替代实际显示 | 背景及已采tab状态可核；原稿较轻的表格分隔与当前整格描边仍是构图/层次差异，未宣称精确一致 |

G2/G3实测：16份共714个TextView都有 `actual_layout_api`；0字距、`includeFontPadding=false`，复制当前Paint的fakeBold均false；实际weight仅对应400/500/700角色。714数字probe观察等advance，但714项tnumActivation仍unknown，实际TextView逐glyph墨迹来源也不从probe推定。比较单位span实测14sp/400，density360时31.5px，未再截为31px。

| 角色sp | 1.0 实际px / getter行高 / 单行Layout高 | 1.3 实际px / getter行高 / 单行Layout高 |
|---|---|---|
| 14 | 31.5 / 49 / 46 | 42.3 / 56 / 61 |
| 16 | 36 / 54 / 52 | 45.45 / 59 / 66 |
| 20 | 45 / 67 / 65 | 53.100002 / 67 / 77 |
| 22（live单位） | 49.5 / 76 / 71 | 56.25 / 76 / 81 |
| 24 | 54 / 81 / 79 | 59.399998 / 81 / 86 |
| 40 | 90 / 135 / 130 | 90 / 135 / 130 |
| 48（live主值） | 108 / 162 / 156 | 108 / 162 / 156 |

这是Android实际非线性sp换算，不按1.3乘算；getter行高与真实Layout差异已分别保存。字体资产候选SHA沿§1，不声称从生成图精确识别字体。

G4实屏取样不以Drawable配置替代：16主图page纯色点screen `(220,230)` 浅 `#F9FAF9` / 深 `#11191A`；真实chart节点左上内移10px的无文字纯色点浅 `#FFFFFF` / 深 `#1B2526`；4张Extraction停止按钮真实rect内移 `(30,30)` 均 `#B5302D`。这些样本只证明相应纯色区域。抗锯齿边缘不拿来设任意容差，也不由纯色点推断所有文字、边框、ripple和未出现状态均通过。

### 10.3 明确图表几何，不以“存在图控件”代替可读图

以下同主题两份一致，数值为原artifact实际节点/Canvas，不是按截图肉眼估计。`plot`指真实Canvas内框，不是含legend和轴的外框。

| 页面 | 1.0 | 1.3 |
|---|---|---|
| Extraction | chart `[216,529,1778,1429]`，plot高455.552px≈202.468dp | chart `[216,561,1778,1429]`，plot高345.998px≈153.777dp |
| History | chart `[1193,712,2524,1221]`，visible底1195，底部少26px；plot高64.552px≈28.69dp | chart `[1193,736,2524,1464]`，visible底1099；plot高79.998px≈35.555dp，初屏真正plot未出现 |
| HistoryDetail | chart `[216,573,1778,1362]`，plot高344.552px≈153.134dp | chart `[216,597,1778,1350]`，plot高230.998px≈102.666dp |
| Comparison | chart `[216,591,2524,998]`，plot高216.164px≈96.073dp | chart `[216,647,2524,1101]`，plot高216.249px≈96.111dp |

History1.3实际header187px、metrics214px、固定footer330px，占去主要内容空间。原 `minimumAxisHeight=5*line+48dp` 包含轴带但没有保证96dp真实plot；“按真实Paint测过”本身不足以证明图层级正确。Comparison `summaryRequired` 优先保完整摘要而长期挤到算法最小plot，96dp算法底线不能替代原稿常规大图。

当前geometry未采embedded/comparison的只读 `productLayoutEvidence`，因此不虚构fullNatural账本。下一次可将现成预算字段只读复制进证据，但实际rect/Canvas和原整屏仍为验收依据，不新造探针替代图审。

### 10.4 交Sol的一次性修正规格

**History，只改embedded与本页列表/菜单，standalone/default保持：**

1. 图预算改为真实plot内框至少96dp加 `bounds()` 同源实际上下轴带；额外常规空间优先给图，不能用5行+48的外框公式当plot下限。16/8组距和14sp轴不降。
2. 内嵌专用图例按原稿显示真实指标名称、单位、线型/颜色；不重复末值挤成多行。保每个原48dp交互、指标选择与检查，末值仍在真实摘要/已有检查说明中可读；无数据不造值。只加embedded展示选项，Extraction/独立详情及默认旧路不继承修改。
3. 固定footer只保原复盘56/完整记录48动作。豆款和口感摘要保14label+16value及全文入口，归入永久局部content的图后区；不再把notes与自然换行一并固定占据1.3的330px。原回调/identity不动，不resize reparent。
4. title/meta24/14不变；metrics40/40/20、单位14、语义组间24不变。只有完整垂直自然预算溢出且三cell实际自然宽可容纳时，label/value改同行8gap；足高保原纵向层级。标准1.0首屏完整图含时间轴，1.3首屏至少出现真正plot而非只有legend；图及notes完整局部滚达并恢复。若仍不容纳，报告真实各块预算，禁止再按差几px削padding/字号或预滚截图。
5. 顶部IO菜单用既有“更多”语义，原导入导出动作保持；底部完整记录入口不变。列表日期、时长、重量按真实语义chunk自然flow，数字与单位作为同组不拆出单独末行`g`；不改变连续ListView、排序/来源/selected/恢复。

**Comparison，限M5 opt-in：**

1. selected Spinner取消2行END截断；保16/400/24、H12/V8、min52、真实全文与原selectedID/adapter回调。dropdown不降级。权威回归证据为 `ci-91ab835-advanced/raw/wideFont.txt:72`：ru/light/1.3 `2023-11-14 22:13:20 · 采集曲线 1` ellipsized，不能仅改读contentDescription。
2. 取消仅600dp判横排及shared说明抢余宽的逻辑。以两杯实际选中文字、Paint、padding/arrow及交换自然宽预算；不足时shared完整说明先落下一行，再不足才A/交换/B永久子节点自然纵向flow。16组距/8行距，不缩字、不删日期/来源、resize不重挂节点。
3. 原稿常规图层级的原生补充目标：标准plot内框160dp，轴区按实际Paint另加；96dp仅保极短fallback，不再以summaryRequired先吞到96。先保此图预算，再将剩余可用空间分给details至其自然高，details至少48dp且表、来源、原详情/notes动作局部可达；toolbar与details都已自然容纳后的余量给图。48dp是短高可达底线，不是标准固定只露表头的高度。若toolbar自然高+160plot+完整轴+48details无法同屏，保原toolbar局部滚动并明确预算，不缩标签、删控件或改变数据范围。
4. 标准四主图、ru1.3全文、details操作可达都需新证据。原table单位float14/400、H8、48点击、taste全文披露、统一量程/真实reader/默认null路径保持。当前整格描边与原稿水平分隔的层次差异是明确未完成项；本批先闭环真实截断与大图预算，不额外扩业务。即便本批两项布局修正通过，仍不能遗漏或自动关闭该表格层次待办。

本节规格已分阶段发主任务，由同一生产Sol顺序实施，避免共享renderer/style冲突。修正与取证期间91ab原件不变、FAIL不追认。涉及新160dp常规plot目标属于明确原生设计补充，不冒称ImageGen已有精确dp。

### 10.5 同轮测试适配与清理，独立于视觉结论

- `LanguageActiveCupChecks` 的旧单TextView全文温度断言不适用真实number/unit结构。已静审候选：精确真实温度数字与`°C`、同parent、两者完整可见/textFits及screenY+native baseline严格相等；原RUNNING/cupId/recipe身份/停止语义不变。不能仅读contentDescription，也不据该适配宣称其它生命周期已通过。
- History顶部IO Button与底部完整记录 Button同文案是两个真实不同动作。列表恢复测试仅用精确 `product/history/full-page` 唯一tag+Button+原文案/可用/完整可见，不用first。当前测试选中journal来源，expected ID必须核当前真实adapter及journal observation，不得拿local history替代；进入详情核同shotId，原返回锚点保持。产品菜单文案歧义按10.4另修。
- font_scale原absent恢复仍严格要求absent，不能等同present1.0。已知系统可在从1.3切回默认1.0时异步持久化；delete返回0只证明该次删除成功。暂无已证公开DisplayThread持久化完成ACK，global值/重复读/AMS或app barrier都不假充它。可先将完整16配置font顺序改1.3→1.0，让最终完整配置在真实1.0下运行，再执行原严格delete/读回；这是减少确定回写触发的候选，不声称排空证明，不加盲sleep/容差、不减16。若仍present必须FAIL保现场，不能反复删除到看似通过。

目前M5整体不通过。可有限保留的证据是已采节点的候选字体/字号配置、指定纯色内区及HistoryDetail末项实际滚达；不能把它们扩成全页G1–G4、完整特殊状态或24页完成。
