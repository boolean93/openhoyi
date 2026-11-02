# M7 旧数据 / M8 设置六页视觉迁移计划

> **For agentic workers:** 按 `superpowers:executing-plans` 分步执行。Astra规划、设计及验收；主任务分配Sol开发。共享文件单owner，不覆盖别人改动，不取消在途CI。

**Goal:** 完成固定24页中剩余六页的原生视觉迁移，严格检查间距、字体字重、字号行盒/单位基线、背景与状态色，同时保留旧数据只读语义和设置真实门禁。

**Architecture:** M7旧数据展示与M8设置展示分批、独立取证；页面显式opt-in，公共组件默认保持。合法fixture只进入隔离库；需要已有服务的机器设置采用单一Mock服务闭包，不增加协议能力。

**Tech Stack:** Kotlin / Android View、Canvas、LegacyHistory/LegacyCurve合法编解码、原protocol2、真实Layout和Paint证据。

## 0. 当前状态与任务模板

- 只读实施参考为 `eb77b2fbd556833b7a35ed0c44bd6d9e66f8f55e`。develop共同来源基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；延续已授权 `feature/task-first-ui` → develop PR#4，不新开重复分支。遵守 [task-template](task-template.md) / [git-workflow](../git-workflow.md)。
- **本文件是可实施规划，不是开始生产授权或视觉通过。** M6桥接正在独立实施，M5与IME新CI仍在途；新原件优先验收，不由本计划覆盖未完结论。
- 路线复核：M1/2/3/4各3页，M5四页，M6两页，共18页；M7三页后21功能页；M8三设置页后24页。M7/M8分别3×浅深×1.0/1.3＝12主配置，不能减为8，不把特殊状态图计入主配置。
- 中文优先，用户横屏2560×1600/density360；兼容横屏短高、IME、大字和原多语回归。不新增竖屏任务，不锁方向。实际WindowInsets后可用区实测，不硬套1138×623dp。
- 推送流程：owner确认 → 静态审 → 统一相关测试/Mock与AndroidTest构建 → 精确提交owned文件 → 主任务普通push → PR四项必需CI与独立本批CI → Astra实图。禁止直推main/develop、force、路径跳过原门禁。
- 不发版，不改签名/包名/versionCode/schema，不安装或操作用户设备；不增加BLE命令/协议策略、不清未知记录、不自动重发未知操作。回退用追加修正/revert，保留持久数据和既有兼容性，不能卸载清库。

## 1. 原稿与固定基线

依据 [24页路线](../superpowers/plans/2026-10-15-task-first-ui-roadmap.md)、[认可方向](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[既有研究](../research/2026-10-15-task-first-ui-redesign-draft.md)、[严格补充](../superpowers/specs/2026-10-18-strict-visual-baseline.md) 与 [原生限定冻结](../evidence/2026-10-18-native-specimen-review.md)§10。以下六图均已实际查看，1586×992；目录 `docs/research/task-first-ui-concepts/`。

| 原稿 | SHA256 | 对应真实页 |
|---|---|---|
| legacy-curve-v1.png | 5dad2df70071007507e626fec6db35ac87b590a7f79f036c44af3b7ecfbf35bf | LegacyCurveActivity |
| legacy-history-v1.png | ca299170ee2a154015ca5d7894ff73e8e49c39a2d73ea48d56800ddd1899ff5e | LegacyHistoryActivity |
| legacy-history-detail-v1.png | 35f3c2e39575558f3e3b229858d507edb016cc9e4212384557241909d968b7bb | LegacyHistoryDetailActivity |
| app-settings-v1.png | f2022d5eb60b798a722d30f01933028a63f14a342958400c74fcdc25a7c1a899 | AppSettingsActivity |
| machine-settings-v1.png | ccb438958aae1cb9a6e88bec1c2fdd5ffafd4a18d385ff63a0e9024c68f6c60d | MachineSettingsActivity |
| scale-settings-v1.png | f87b9cf3d260a3b01bd9d88e83fa2a5cb40d8e1d456de37b8c579515a8eec36f | ScaleSettingsActivity |

设置稿沿既有整体方向实施，但严格补充曾标三稿为候选，不能补写为用户逐页精确批准。不重画、不重新研究ImageGen字体身份。原稿日期、杯数、值、品牌均不是业务默认值；只显示真实模型，缺失用原未知文案，不填0。

### 1.1 封闭原生角色与颜色

- 继续候选Noto Sans SC 2.004-H2，SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`；字距0、includeFontPadding=false、不伪粗，非中文fallback保留。ImageGen原字体身份、tnum实际启用和不可观测glyph仍unknown，不反向认可现实现。
- title24/500/36，section20/500/30，正文/输入16/400/24，动作16/500/24，辅助/轴/普通单位14/400/22；旧曲线四参数24/700/36，独立14单位。设置读回主文字16，不把普通设置值放大为库存40。真实Android字体缩放与Layout行盒取证，不简单乘1.3或固定像素行高。
- 浅色page/surface/text/muted/border/selected/disabled：`#F9FAF9/#FFFFFF/#101820/#657183/#DDE3E7/#E2F0ED/#ECEFF1`；深色：`#11191A/#1B2526/#EDF3F2/#A9B9B8/#3B4B4C/#203D3B/#263234`。accent主填充 `#087A78`+白；文字动作浅 `#087A78`、深 `#83D6D0`；禁用字 `#8A95A3/#7D908F`。警示沿M5既有warning角色，不以accent冒充写入确认。
- 页面16、主辅间24、组16、相关行8；surface圆角16、控件12，1dp描边/细线；导航80显式opt-in。列表H16/V8，最小64且随文字自然长高。所有真实触控≥48、primary≥56、输入≥52；不在单按钮加不对称margin。
- 数字和单位独立节点同屏baseline精确一致，8dp gap；普通日期/数量不可为fit删字、截断或改精度。数据表优先水平分隔；不把每格重边框带入本批。

## 2. 真实依赖与启动条件

| 范围 | 当前真实接口 | 实施边界 |
|---|---|---|
| M7曲线 | LegacyCurveActivity已有TaskCollectionLayout、连续ListView、selectedIndex+rawJson指纹、原SAF导入与后台list/import线程 | 可独立迁展示；不改codec、替换语义、MAX_BYTES、UTF8、指纹资格/原路由 |
| M7历史 | LegacyHistoryActivity当前仅列表，点击legacyId去独立详情；LegacyHistoryDetailActivity后台读取，LegacyShotChartView仅pressureBar/waterFlow | 必须补原稿选中预览构图，保独立详情legacyId；共享纯只读展示，禁止把oldWFlow/oldWTrend解释成原生秤重或原生累计量 |
| M8应用 | theme真实commit失败回滚、语言持久化、完成提示音本地预览/生命周期、四真实版本信息、TraceStore导出、机器设置入口 | 保监听/失败反馈；仅显式用户操作触音频/SAF，不让主图启动声音或导出 |
| M8机器 | flags0绑定已有Service、500ms刷新、freshIdle使用系统elapsed；原readback/pending/recovery/active/manual门禁 | 主fixture采用默认真实系统时钟+真实Mock IDLE服务。不得用M5固定clock而让本页freshness跨时间域，不新改Service/控制策略 |
| M8秤设置 | 继承ScaleOwnerActivity（定义在M6-owned ScaleActivity.kt）；flags0/250ms，能力显式要求READY且!MOCK | 不绕!MOCK、不复用M6输入伪称真实能力；设置主图真实偏好+完整未连接说明，不是空页。共享父类变更须M6 owner串行 |

M7不依赖M2恢复候选或M6桥接。M8只消费已提交原生样式，不等编号，但需主任务分配设置共享文件、避开M6父类修改。任何需超出上述接口的共享改动先给具体原因，不以视觉迁移改协议。

## 3. M7三页构图与自然预算

### A. LegacyCurveActivity

- 页头back48、title24/read-only14、原导入动作≥48，足宽同排，不足自然换行，16组/8行。只读标识不是禁用整个浏览页；导入仍原合法入口。
- 左约34%连续列表、右约66%详情，24间隔与中线；仅实际双栏自然最小宽成立时并排，窄横屏沿既有collection详情回退。列表title16/500、category/source/index14，selected背景、24图标和chevron；计数取实际bundle，禁止将原图六行当数据上限。
- 右title24、source/category/index真实metadata自然flow；四参数2×2，card16、两轴gap16、label14→8→24数值+14单位。未知值保“—”，stage为真实seg，不拿4填缺失。末尾原只读/不支持控制解释14完整；详情入口原回调不丢。
- 大字/短高先测头、四卡和解释自然高；详情正文局部滚动，原导入/返回不被滚走。selectedIndex+指纹仍决定资格，重建不凭旧index套新bundle。

### B. LegacyHistoryActivity

- 顶部真实三来源入口，保local/legacy来源路由，不把旧资料混入原生journal。左日期分组+连续ListView约34%，右选中预览约66%；真实时间顺序不因排版变化。首选项确定性按现模型/保存id，选择只影响本页预览、不写数据。
- 右永久title24/read-only14、date/duration14，16gap图表，底原来源警示与≥48完整详情入口；打开仍传相同legacyId，返回保位置/offset/选中id。宽不足允许原独立详情导航，不新增页面。
- 图只画pressureBar和waterFlow，图例名称+bar/ml/s、颜色沿既有pressure/water冻结角色；14sp真实Paint。标准1.0真实plot目标≥160dp，短高底线≥96dp另加真实轴/图例带，不能把整个View96当plot96。实测预算不足则中部局部滚，原详情入口固定；标准主图不得以空图或只摘要替代。
- date/duration/name是原LegacyShot事实，不用最后点替duration；summary未知/说明完整。读失败显示失败，不当空列表/零值。新增共享内容仅只读渲染和布局，不复制导入/存储逻辑。

### C. LegacyHistoryDetailActivity

- 页头back、name24、date/duration14和read-only；左约2/3大图，右来源/缺失字段/局限约1/3、24gap。右三组20标题、14完整说明、16/8间距、1dp水平规则、底完整说明≥48。
- 图表与B同源同范围/单位，正常可用空间优先给图，实际轴盒+plot预算记录。右固定动作之外局部滚，短横屏宽不足永久内容fallback，不能UNSPEC撑出固定操作。
- 保温度、原生累计水量、原生秤重/终态未保存的警告；snapshotJson只作为原始资料，不推断支持原生控制/回放。缺id/坏文件仍原错误态，避免显示上一条旧图。

## 4. M8三页构图与自然预算

### A. AppSettingsActivity

- 原稿中心分组布局，保既有约640dp内容maxWidth和整页滚动；page16、组24、组标题20。四组：外观/语言、完成反馈、应用信息、诊断与入口。不得为强求首屏把全部设置缩字；需逐组滚达和恢复证据。
- 主题Switch真实52以上，完整16标签+14说明；语言row至少48、当前nativeName和chevron，足宽两端自然同行、否则文本/动作换行，8间隔；不能固定maxWidth导致长语言名ellipsis。
- feedback真实开关和预览按钮16；预览开始/停止、离页停止、观察者解绑不变。主图不播放；错误由原prefs失败反馈表达，不画静态“已开启”假状态。
- 四info行label14/value16 selectable，完整app名/version/mode/package。按真实自然宽flow，不以fontScale阈值硬裁。诊断export真实SAF、机器设置原入口，图标纯装饰，回调不变。

### B. MachineSettingsActivity

- 左连接与读回约40%、右操作约60%，gap24；连接/Mock提醒14、状态真实。四readback字段16+14单位，日期/standby/杯数完整，旧settingsToggle与App设置入口保留。不能将演示输入写成硬件readback。
- 右先真实命令反馈，再五tabs≥48且可横滚，选中state沿palette，原sectionIndex恢复。温度/功能/待机/计划/维护五组全部保留，不只实现原稿温度tab。
- 温度每行label16/range14、原EditText16/≥52、独立单位14、原动作≥48。永久子树；仅label完整自然宽+输入自然最小宽+原动作自然宽+16间隔可容纳才同行，否则整组上下，相关8。不能resize重挂输入或靠省略范围通过。
- 页面仍可整页滚，原完整反馈与当前section可达；dialog不允许按钮被IME遮住。对话框另采native title/body/buttonPanel自然预算、同screen safeRect、完整focused field和positive/negative；不取hierarchy full代无遮挡，不按上次9px硬补。
- 保readback freshness、IDLE、active/manual/recovery、WRITING/WAITING/UNKNOWN和所有范围限制。Mock不能证明写入成功；可达的演示/拒绝状态实测，真实设备确认/未知写入恢复等不可达态保pending，不创造状态注入。

### C. ScaleSettingsActivity

- 中心约688dp整页滚动，三组：显示精度、当前设备能力、使用说明。14辅助/16正文/20组标题，1dp组内线、16内距/24组距；Switch≥56保真实one_decimal偏好，两个精度示例只说明格式不是实时秤读数。
- 当前能力严格沿READY&&!MOCK分支；Mock主图保真实“未连接/无设备能力”说明、不是假型号/假归零可用。三条timer/控制条件/协议支持说明完整，不把当前秤工具控制复制成设置项。
- 不改ScaleOwner的绑定/刷新/退出策略，不在本页启动新Service；M6私有输入未安装的默认行为需回归。设置返回真实Scale页偏好生效，no-data仍显示“—”，不得因此造读数。

## 5. 文件owner与默认隔离

所有Kotlin路径以下省略 `mobile/src/main/kotlin/io/openhoyi/mobile/`；androidTest同包。

| owner（由主任务明确分配） | 文件范围 | 约束 |
|---|---|---|
| M7生产Sol | LegacyCurveActivity.kt、LegacyHistoryActivity.kt、LegacyHistoryDetailActivity.kt、LegacyShotChartView.kt；必要新增LegacyVisualStyle.kt/LegacyHistoryContent.kt；八values*/legacy_visual.xml | 只展示、永久composition；chart新nullable style null走旧分支；原codec/store不改 |
| M8生产Sol | AppSettingsActivity.kt、MachineSettingsActivity.kt、ScaleSettingsActivity.kt；必要SettingsVisualStyle.kt；八values*/settings_visual.xml | 页面opt-in；控制/存储监听和IO不改 |
| 同M8 owner、经主任务串行协调 | TaskSettingsGroup.kt、BrewFeedbackPreferencesCard.kt、AppLanguagePreferencesCard.kt | nullable/default参数保其他caller原外观及行为；语言callback签名与trailing lambda兼容；不得共享默认改色 |
| M6 owner与M8 owner串行 | ScaleActivity.kt仅scaleBody样式接口如确有必要 | 优先消费M6已有默认null接口；M8不顺手改ScaleOwner生命周期/趋势/clock/桥接 |
| M7测试Sol | 新LegacyVisualMigrationChecks.kt、新check_legacy_visual_migration.sh、新legacy-visual-ui.yml；必要精确SingleScreenListStateChecks/LanguageDetailChecks适配 | 主12+特殊态；原列表完整性/图表门禁保留，新结构按真实叶子和baseline核，不只读contentDescription |
| M8测试Sol | 新SettingsVisualMigrationChecks.kt、新check_settings_visual_migration.sh、新settings-visual-ui.yml；必要精确LanguagePageChecks/LanguageSelectorUiChecks适配 | 主12+五section/偏好失败/IME；不得广泛跳过native Switch/输入/按钮 |
| 单一测试owner串行 | BrewAudioInstrumentation.kt显式两入口 | 旧frame/M1–M6入口、协议2、实时5s、旧业务全保留；两批独立45min job不追加原task-frame |
| Astra | 本文件、主任务授权的实图审查记录 | 规格/代码静态/原PNG G1–G4；不代Sol开发 |

不得改Legacy codecs/adapter含义、Service/MockRuntime控制算法、LiveTelemetry、ScaleReadingPolicy、recovery持久策略。新图标优先现有back/more/import/export/chevron；缺case需具体批准，不能顺手全局换glyph。

## 6. 合法fixture、隔离与主矩阵

### 6.1 M7：三个非空主状态，共12

- LegacyCurve：合法`openhoyi-legacy-curves-v1` bundle，至少40项便于真实连续滚动；主选经典浓缩，温度93、水60、weight360（旧整数十分之一克＝36g）、seg4，含真实factory/user/category与完整rawJson。另有长名/未知参数项，但不能让主项全未知或假截图填值。
- LegacyHistory与Detail同主LegacyShot：固定合法id/date/duration30s、profileName；0..30s非空合法pressure/waterFlow点，oldWFlow/oldWTrend存在也不用于新量。另40条不同id与日期，主选中项、详情legacyId一致。样本来源标synthetic legacy archive，不冒充硬件。
- 原app两个legacy Lazy **不能求value再换**。保存原Lazy身份/isInitialized，在owned cache目录构造两个合法Store，以真实codec导入fixture，整组安装后才开页；原10Lazy闭包按实际日志/页路由依赖采用既有已审内存对象，legacy本批不再仅记录unused。
- 不启Service、不装Mockclock、不读用户剪贴板。`legacy-curve-list/import`、`legacy-history-list/detail`及显式导入的`legacy-history-import`须追踪本scope线程身份；destroy全部页/dialog→join新reader/import线程→main队列barrier→drain本scope Trace/samples→恢复Lazy/typed prefs→原files/shared_prefs/database/noBackup SHA。失败不回滚盘、不继续下一fixture。
- 特殊态：未导入/空、合法缺参数、坏UTF8/超限/错格式、长名称/category、坏文件读失败、缺legacyId、同id重复与冲突。SAF仅显式阶段、owned provider URI，取消/失败原scope库不变；旧曲线替换和历史merge语义各自核，不混成同一种导入。原始文件/未知字段不得丢。

### 6.2 M8：三个完整设置主状态，共12

- App主图真实偏好、中文、真实BuildConfig信息和反馈设置，非空四组；ScaleSettings主图真实精度偏好和完整能力/说明三组，Mock能力提示如实。两页无已有Service时flags0自然不绑定，不为取图造服务。
- Machine主图单一真实Mock Service IDLE、真实settings/readback，温度tab实际非空三输入。**默认系统clock，不安装固定override**；record实际时间/新鲜度，主图协议2仍真实5s。同一renderer稳定若失败保原件，不暂停500/250ms handler。
- 应用10Lazy及owned IO沿已审闭包；服务onCreate后任何页面render前保存原shotRecovery/machineWriteRecovery Lazy身份且必须未初始化，仅替为合法内存空记录；credentials/retry不求值、身份初始化状态不变。onCreate若已读原record即failclosed，不清原盘。真实服务、日志、观察者、hub与callbacks逐项核实，不只套“10Lazy隔离PASS”。
- cleanup：关闭所有设置页/语言dialog及音频预览、确认stop/unobserve/unbind→解除测试bind/stop唯一Service并核destroy/handlers/hub/visibility/feedback观察者→仅本scope reader/export callback及writer排空/main barrier→恢复销毁service的两个Lazy→app原Lazy/语言对象/typed偏好→原业务盘/noBackup哈希。无Mockclock时证明无owner；若后续因确切需求新增clock，需统一所有freshness调用域并另审，不能偷偷复用M5固定clock。
- 偏好只逐键恢复实际触及的`appearance.dark`、`scale_tool.one_decimal`等；语言/反馈优先scope合法内存Preference。保存在性/类型/值，不clear整库；白名单XML物理SHA分列，不冒称字节不变。系统font_scale presence/value严格恢复，沿当前真实失败后候选流程，不把delete返回0或get-config当持久化queueACK。
- 特殊态：主题/语言保存拒绝后原值与错误、8语言实际选择及重建、预览开始/停止/离页停止、诊断SAF取消/失败/owned成功；Machine五section/合法非法输入/计划dialog草稿与IME、无Service/真实Mock能力禁用/可生成pending反馈；Scale精度切换/重建/返回生效。不可合法生成的真实设备写入确认、UNKNOWN/stale能力和硬件声音/归零不填PASS；不缩主12或修改控制策略造状态。

## 7. G1–G4与业务证据门禁

| 门 | 必须保留的实证 |
|---|---|
| G1 | 原PNG同状态同视口对照；page16/栏24/组16/行8/内距/细线/选中态/图例；真实Window及Insets、所有重要rect、自然预算、初态与滚后restore；标准非空图与最小真实plot，完整控件/长文/原列表门禁；dialog实际screen safeRect与IME/按钮，不等同hierarchy可见 |
| G2 | 字体资产SHA、实际TextPaint/typeface角色/weight、letterSpacing/fakeBold；非中文fallback独立。不能宣称ImageGen字体精确识别，unknown保留 |
| G3 | 实际textSize浮点、Layout逐行盒/基线/左右界/ellipsis、Canvas14sp Paint测量与轴墨迹；数字单位screen baseline相等、全量小数/负号/未知符；getter lineHeight不是实际Layout高度 |
| G4 | 原整屏纯色内区与真实状态resolved色分别核对，page/surface/selected/disabled/warning/边/横规则/alpha；Drawable未知不造getter，不用当前代码值反推原稿通过 |

每份主图含head与实际tested merge/source、dirty状态、APK/font/originalPNG哈希、真实profile/窗口/IME、完整fixture输入、protocol2原双域无mask零差及失败attempt、原PNG+字体/Canvas/预算JSON。主图先保存再业务；失败保存当刻几何/PNG并标是否同帧，不能只留异常。独立CI首FAIL停止保留原件，不删失败图凑12。

原中文输入/48目标、语言布局、多页frame、列表返回重建、旧读取与所有必需CI继续；新scope正确不替代旧原业务回归。每页四门各给LIMITED_PASS/FAIL/EVIDENCE_PENDING及精确状态，不能用编译/机械绿色盖视觉。新六页通过也不追认其他18页或硬件。

## 8. 可执行批次与验证

- [x] 对应固定24页、实看六原稿、核源码依赖；本文件规划完成。
- [ ] 主任务指定M7/M8生产与测试owner、共享文件窗口。可独立M7先行；M8与M6 ScaleOwner/Instrumentation串行，不能等编号也不能并发覆盖。
- [ ] M7生产先LegacyCurve/样式，再共享只读History内容与list/detail、独立Paint；保原IO尾部及legacyId/列表状态。Astra一次性静态核构图/自然预算/未知含义，不先推一页。
- [ ] M7测试先scope及合法archive、主12/真实列表与详情恢复，再异常导入/只读无写；旧测试如结构适配只改精确selector/真实分离单位断言。
- [ ] M8生产先三页shell与默认null共享卡，再机器五section永久自然flow/IME；Astra核每个旧控件/回调/guard未删，设置整页滚动不等于固定动作豁免。
- [ ] M8测试先无服务两页，再Machine真实默认clock服务闭包，最后偏好失败/SAF/audio/IME；原系统font恢复未证明成功则CI仍FAIL。
- [ ] 主任务统一运行 `./gradlew :mobile:testMockUnitTest --tests io.openhoyi.mobile.LegacyCurveTest --tests io.openhoyi.mobile.LegacyCurveAdapterTest --tests io.openhoyi.mobile.LegacyCurveWireAuditTest --tests io.openhoyi.mobile.LegacyHistoryTest --tests io.openhoyi.mobile.AppLanguagePreferenceTest --tests io.openhoyi.mobile.AppLanguageTest --tests io.openhoyi.mobile.LanguageCatalogTest --tests io.openhoyi.mobile.LanguageDraftTest --tests io.openhoyi.mobile.MachineSettingsPresentationTest :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain`。实施前核任务/测试存在；本文件未运行。新增测试只针对实质逻辑，不镜像padding。
- [ ] 新runner统一接口 `bash scripts/check_legacy_visual_migration.sh tablet-user-landscape <output-dir>` / `bash scripts/check_settings_visual_migration.sh tablet-user-landscape <output-dir>`，单runner内部三页×四配置；独立workflow各45min、沿现Mock/ranchu-or-goldfish/fresh-process failclosed guard，不减16其他批配置。脚本bash-n/内嵌Python编译、实际12清单/哈希/非空protocol证明均核。
- [ ] 两批各完整12原图按浅1.0三页→深1.0→两主题1.3，结合特殊态与原CI；Astra给四门逐页结论后主任务更新24页矩阵，未达状态继续列明。

## 9. 未完成归属、风险与回退

- M5 Comparison表格“整格描边”与原稿水平分隔的层次差异，仍归M5同owner后续收口；本批不复制该样式、不改Comparison、不借本批完成掩盖。M5布局/字体恢复、M2/M3 IME、M4 dialog、M6主8真实验收以各自最新证据为准。
- M7风险是把旧字段升级成原生事实、IO线程在scope恢复后继续写、selectedid与列表错配；M8风险是whole-scroll/IME遮挡、短高/长语言、偏好回滚与服务恢复记录污染。以上以精确不变量和实际整窗取证解决，不缩字号/隐藏控件/放宽门禁。
- 新Mock隔离文件/服务只用于测试；不安装APK、不写设备、不声明真实硬件控制/归零/反馈成功。出现隔离失败先保存现场并停止，不清文件“恢复原SHA”。
- 代码回退为独立提交的追加修正/revert；nullable默认路径、原模型/存储/协议接口保留。可单独撤回某页opt-in而不撤回用户数据，回退仍需旧CI确认；不得为回退移除未知安全记录。
