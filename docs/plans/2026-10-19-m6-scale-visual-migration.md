# M6 电子秤与连接页视觉迁移计划

> **For agentic workers:** 按 `superpowers:executing-plans` 分步执行。Astra负责设计、静态审查与实图验收；主任务分配Sol开发。共享文件单owner，不覆盖别人修改，不取消在途CI。

**Goal:** 迁移ScaleActivity、ScaleConnectionActivity两个真实页面，按认可原稿检验间距、字体字重、字号行盒及背景状态色，保留真实读数来源、趋势、App计时和全部操作门禁。

**Architecture:** 两页显式opt-in冻结原生样式，ScaleOwner默认路径与单服务所有权不变。图表只迁展示；有样本整页取证需要受限Mock观察输入与同源时钟，先核契约再由主任务分配，不改BLE协议/高级秤抽象。

**Tech Stack:** Kotlin、Android View/Canvas、既有ScaleObservation/ScaleTrendWindow/LocalScaleTimer、Mock隔离、原NativeWindowFrameEvidence protocol2。

## 0. 当前状态、模板与启动条件

> **当前执行索引（2026-10-19，eb77b2f 工作树候选）：** 主任务已分配同一生产 Sol，§5 桥接及两页展示首版已落盘；测试 Sol 正准备独立完整 8 配置，尚未冻结终审。以下早期“待分配/未实施”表述保留规划时点，不再阻止已授权实施。Service 实际集成、8 张主图和全部 G1–G4 仍未验收，不由 helper 单元测试或静态审查替代。

- 只读基线 `91ab835aaf28d9e133a78efd620d7780ba3e7192`；已核工作树clean。来源develop共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`，延续已授权 `feature/task-first-ui` → develop PR#4，遵守 [task-template](task-template.md) / [git-workflow](../git-workflow.md)。本文件只完成规划，未实施或运行M6。
- 主任务同意两页展示可独立推进，不以M2–M5编号等待；**主任务已审准§5最小只读输入桥接：MobileService私有Mock输入（必要独立MockScaleVisualInput）+ScaleActivity四处同源elapsed；待主任务明确单owner执行，不改MockRuntime算法/控制策略**。M2/M3 IME、M4 dialog、M5全图在途结论均不被本计划覆盖，新失败原件优先审。
- 24页范围内本批仅第17/18两页；ScaleSettings留后续，不借共享父类迁样式。中文、2560×1600/density360横屏，浅深色/1.0/1.3及横屏短高；不新增竖屏工作或锁方向，已有非本任务回归保留。
- 明确owner → 静态审 → 统一构建/相关测试 → 只提交owned文件 → 主任务普通push → PR原必需CI及新增独立M6完整8配置 → 实图G1–G4；不单项微推、不force或直推main/develop。
- 不发版、不改签名/包名/versionCode/schema，不安装/操作用户设备。无新增协议、命令、归零策略、稳定判定或自动停水。Mock结果不证明硬件归零或控制能力。回退追加修正/revert，不清未知记录/原偏好、不自动重发未知操作。

## 1. 已实看原稿与原生补充边界

依据 [全24页路线](../superpowers/plans/2026-10-15-task-first-ui-roadmap.md)、[认可规格](../superpowers/specs/2026-10-15-task-first-ui-design.md)、[研究](../research/2026-10-15-task-first-ui-redesign-draft.md)、[严格基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)、M1限定冻结与既有 [秤单位层级](../superpowers/plans/2026-10-16-scale-unit-hierarchy.md)/[真实趋势](../superpowers/plans/2026-10-16-scale-live-trend.md)。历史竖屏建议不覆盖最新横屏范围。

| 已实看PNG（docs/research/task-first-ui-concepts/） | 尺寸 | SHA256 | 必须保持的构图 |
|---|---|---|---|
| scale-v2.png | 1586×992 | d33ae2f0348cd0464b992196a5e1b1c855b3bdce7232f211755e19d4b693bfaf | 左侧大重量/状态/归零与最近10秒；右侧本地计时、设备能力和连接入口，栏间细线，顶部真实状态 |
| scale-connection-v1.png | 1586×992 | 21cba381b9c7025aa138458d37d0c0f0f3e0a9fb277bff943ea3cbcec1edc931 | 上部状态/权限入口/扫描；下部候选列表，名称地址、RSSI、只读限定、右连接动作；底部能力说明 |

原图18.0g、82%、设备品牌、稳定绿点与3个地址是概念值，不能直接进入产品。当前MobileSnapshot没有已连接秤型号字段，也没有可供常规称重显示的独立“读数稳定”事实；不能由候选列表猜已连接品牌，不能借beanDoseCaptureAt调用副作用制造稳定状态。改用真实新鲜度/证据/能力说明；型号未知则不造型号行。Mock保留“演示模式，不是真实设备读数”，不是图上的“概念示意”。

原图字体身份/精确sp/色值不可可靠恢复，继续候选字体SHA与原生角色，不重新研究或重画。共享字体Noto Sans SC 2.004-H2，SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`；字距0、非伪粗、includeFontPadding=false，tnum/glyph未知如实记录。

### 1.1 本页角色与颜色（实现目标，待M6实图）

- title24/500/36，section20/500/30，正文16/400/24，动作16/500/24，辅助/轴14/400/22。数字单位按实际原生baseline对齐，不能取整截断float字号。
- 秤主值沿既有专页批准64sp/700，不用共享库存40sp取代；页内明确行高96sp。窄横屏保留既有48sp角色，不因短高度降字。单位采用22sp/400/34独立节点、8dp gap；这是原生层级补充，不声称原图精确字号，也不再用64×0.45派生不明确单位。计时40/700/60，完整mm:ss.t同一实际App时钟，原精度/负号/小数位不能删。
- page/surface/text/muted/border/selected/disabled沿M1冻结：浅 `#F9FAF9/#FFFFFF/#101820/#657183/#DDE3E7/#E2F0ED/#ECEFF1`；深 `#11191A/#1B2526/#EDF3F2/#A9B9B8/#3B4B4C/#203D3B/#263234`。主按钮 `#087A78/白`，文字动作浅 `#087A78` /深 `#83D6D0`，禁用文字浅 `#8A95A3` /深 `#7D908F`。
- 趋势使用accent深青，2dp原生线/末点，淡填色仅同真实连续segment下方12% alpha；不跨gap，不把填充误作新增读数。轴/网格muted/border1dp，14sp400 Paint，实际墨迹与2dp保护净空。警示沿M5本地warning palette，不新改全局tokens。
- 页面16、栏间24、普通组16、相关行8、列表H16/V8；容器16/控件12圆角，边1dp；导航80。固定触控≥48、主操作≥56自然高，禁止单侧非对称margin。

## 2. 实际接口与依赖结论

| 接口 | 已核事实 | 本批边界 |
|---|---|---|
| ScaleOwnerActivity（在ScaleActivity.kt） | flags0绑定已有MobileService；250ms render；onStop remove refresh、unbind、owner=null；scaleBody被秤设置等继承 | scaleBody增加默认null的页级visual参数，只有本两页显式传；不改绑定/刷新频率/默认导航或其它页 |
| ScaleActivity | LiveTelemetry.scale(ScaleObservation,state,now)决定可见重量；原one_decimal偏好；timer纯App；tare/capture均额外拒Mock | 只改展示和获批后的同源elapsed入口；不启用Mock控制，不制造归零成功/稳定粉量 |
| ScaleTrendWindow | 每个真实onScaleObservation进缓存，10秒/2048上限；断连/1500ms缺口/证据变更分段；重复乱序拒绝 | 不改模型、时间窗/阈值/采样；不得按250ms render重复造点 |
| ScaleTrendPanel/View | 现轴12sp；normal内联，不足打开同源详情；onStop dismiss | 新nullable style、14sp轴/真实Paint证据；null保留旧组件测试路径。标准1.0必须有样本inline图；1.3按自然预算必要fallback时另采有样本详情，不以空图替代 |
| ScaleConnectionActivity | 实际BOOKOO角色候选，protocolId保留，Mock scan/connect禁用；candidateScroll保存/重建/返回 | 不改radioUsable/权限、scanPending/原Home入口、唯一owner、connectScale或候选资格 |
| MockDeviceRuntime + refreshMock | 当前只有BookooSample weight，无ScaleObservation/capabilities；refreshMock覆盖snapshot而不通知trend | 单靠M5服务/clock不能完成有样本Scale主图；需§5专用输入桥接，默认行为不改 |
| 时间 | Service已经MobileMonotonicClock；ScaleActivity四处仍SystemClock（timer/capture/render） | 只有本页这四处改同源入口，LocalScaleTimer算法不动；真实构建仍系统时间；协议2始终真实SystemClock5秒 |

展示没有M2/M3布局对象依赖，可独立；父类scaleBody、MobileService/Mock时钟、Instrumentation入口是共享文件，必须单owner串行。M5已提交接口可用不等于M5已视觉通过。

## 3. 两页封闭布局

### A. ScaleActivity

1. 页头title/subtitle、Mock标识与真实状态分组。主辅约70/30，右自然最小280dp/最大320dp、栏间24；只有实际宽够才双栏，不能因短高把整个右区UNSPEC撑出窗口。永久子树，不在resize重挂EditText/按钮。
2. 主区居中：当前重量label14 →8→64数字+22单位 →8→真实有效/过期/只读状态；接原归零56固定操作（最大240dp宽）、8gap原等待/未知/失败说明。真实tare监听顺序、pending禁用、UNKNOWN不重发保持。没有有效读数显示“—”，不保留旧值冒实时、不改零。
3. 下方16gap最近10秒section20；完整趋势描述14可与标题自然同行，宽不足换行，不另叠重复状态。正常1.0 plot沿原≥180dp自然预算（包括实际轴文字带，不以120小图过门禁）；1.3沿既有180×fontScale最低图预算，整体实际不足时使用原明确趋势入口打开同源有数据图，标题/说明和按钮完整。另采详情图/滚达与关闭恢复。不得把标准主fixture也退到空图/仅摘要。
4. 右区：本地计时20 →16→40计时 →8→“App计时器”及原说明14；16gap开始/暂停/继续与重置原回调同行16gap。真实timer起点/累积保存，重建继续/暂停不丢时长；暂停24.6主fixture通过真实start→advance→pause，不反射伪时间。
5. 设备组距24：section20、各行H0/V8、label14/value16、细线；仅实际battery capability且值0..100才显示百分比，否则原不可用。连接入口与设置入口48保留，原连接禁用由changesAllowed；设置仍路由ScaleSettingsActivity不迁其视觉。原图缺设置入口，但现功能不可删除。
6. Capture模式仅额外原底部确认56，保持稳定来源说明与RESULT_EXTRA整值，Mock始终不可采；原归零降为次操作，capture固定。短高先测header/真实读数/操作/说明自然高；次区局部滚，趋势详情可达，捕获/归零不能一起被整页滚走。若实测最小预算无解，报告具体矩形，不降字号/56/48。

### B. ScaleConnectionActivity

1. 顶部状态面16padding，左真实status+完整原说明，右设备/权限管理48（原Home路由）及扫描56。自然宽不足时动作组整体下一行，16组/8行，不删除权限说明或把“蓝牙已开启”硬编码。
2. 下部section20“附近的电子秤”与实际候选计数14（允许新增纯展示scope资源），候选区域独立ScrollView，保持真实顺序/地址/协议/滚动恢复。卡内16，标题分隔1dp；行H16/V8/min64，实际长说明自然增高，不定死原图行高。
3. 每行永久结构：24dp秤图标+16gap，名称16/500/24、地址14/400/22；RSSI14完整dBm（信号glyph只能由实际RSSI规则，不虚称质量）；右连接48。Felicita真实protocolId才显示原完整实验只读限制，warning色且文字不省略，不以品牌名决定权限。窄宽按自然宽将RSSI/限制放第二行，按钮原回调不变。
4. 行容器与连接按钮不得制造双重嵌套点击。若旧Button改容器，仅显式连接Button执行原回调；完整行contentDescription保留，测试改精确语义而非泛化豁免。Mock按钮仍disabled；UNKNOWN/活动杯原不允许变更规则不改。
5. 底部原能力说明14与16留白保留。原稿虚拟设备说明不进入正式产品；测试fixture全部标synthetic。无候选为空态，不编造3个设备；scan/connecting/failed/unsupported按原真实状态，未有模拟能力则证据pending，不造成功。

## 4. 文件所有权

| owner | 文件（mobile/src/main/kotlin/io/openhoyi/mobile/，除另标） | 允许改动 |
|---|---|---|
| 生产Sol | ScaleActivity.kt、ScaleConnectionActivity.kt | 页面布局与原生展示；scaleBody默认null隔离；获批后ScaleActivity四处elapsed同源。业务监听/保存/绑定保持 |
| 同一生产Sol | ScaleTrendPanel.kt、ScaleTrendView.kt；新ScaleVisualStyle.kt；八语种values*/scale_visual.xml | 显式nullable样式、独立Paint/单位/轴、panel自然预算及真实draw证据；默认旧分支保留 |
| 桥接单owner（主任务已授权契约，待分配执行） | MobileService.kt；必要新MockScaleVisualInput.kt | 仅§5受限输入。优先Service内私有immutable状态，不修改MockDeviceRuntime算法；若实现确需动runtime须先说明比Service层更小的理由 |
| 测试Sol | 新androidTest/…/ScaleVisualMigrationChecks.kt、新scripts/check_scale_visual_migration.sh、新.github/workflows/scale-visual-ui.yml、BrewAudioInstrumentation.kt显式入口 | 独立完整8配置，不追加原frame；沿原协议/guard/隔离。必要精确适配SingleScreenListStateChecks、ScaleTrendPanelChecks/ChartChecks，但旧null用例/实际断言保持 |
| 测试Sol | 新mobile unit MockScaleVisualInputTest.kt（若桥接新类）、既有ScaleTrendWindowTest/LocalScaleTimerTest等运行 | 测试实质新owner/拒绝/通知语义，不写padding镜像测试 |
| Astra | 本计划与主任务明确授权记录 | 设计、静态、8图G1–G4，不改生产/测试 |

禁止改ScaleSettings、TaskWorkspace默认、LiveTelemetry、ScaleReadingPolicy、ScaleTrendWindow/LocalScaleTimer算法、协议decoder/adapter/registry、StandaloneTare、BeanDoseCapturePolicy、任何BLE transport。图标先用已有scale/search/chevron/info；缺glyph仅具体case另核，不扩大图标库。

## 5. 有样本真实页取证桥接：待主任务分配后实施

### 5.1 最小输入契约

只在fresh Mock模拟器进程、已安装同owner MobileMonotonicClock且唯一service.hub=null时可用。候选接口名可按现有风格落实，但语义必须等价：

```kotlin
internal fun installScaleVisualInput(owner: Any)
internal fun offerScaleVisualInput(owner: Any, state: DeviceState, observation: ScaleObservation?)
internal fun clearScaleVisualInput(owner: Any)
```

- 三mutation必须主线程、BuildConfig.MOCK_MODE、clock同owner、hub=null；安装单owner，拒重复/错误owner/未安装/real build。安装不启动shot、不做tare/scan/connect，不读取或清设备恢复记录。
- 输入是防外部别名的immutable snapshot：ScaleObservation.copy并复制raw ByteFrame字节；只读合法能力、receivedAtMs≥0且≤同域now、合法重量范围。主夹具 `ScaleCapabilities(weight=true, tare=false, deviceFlow=false, battery=false, deviceTimer=false, validatedWeightControl=false)`、LIVE_READ_ONLY。拒任何可写tare/validatedWeightControl或VERIFIED_TRANSPORT，不能凭测试输入获得控制资格。可另测battery=true的明确synthetic能力，不标成Felicita真实能力。
- READY+null表示确无读数；非READY输入必须null；state变化走原 `onDeviceState(DeviceRole.BOOKOO,state)`，新观察走原 `onScaleObservation`。每次显式offer才算一条通知；重复timestamp由原窗口规则拒绝，250ms mockTick不能重喂同一观察、刷新其receivedAtMs或补点。
- refreshMock只有安装期间保留该scope的scaleState/observation/capabilities覆盖；默认未安装完全原runtime.sample行为。注入期间legacy weight/weightAt不冒充已验证控制数据，保持null；M6 scope禁止startShot/prepare/tare，正常Mock机器状态/handler继续运行。此桥接是合成只读输入，不是更换getter或停handler。
- 不新增session协议、不改原ScaleTrendWindow/LiveTelemetry接受条件。断连/重连/10秒过期仍由真实函数执行；clear只清本scope状态与只读趋势，销毁后才撤clock，不向原设备发请求。
- candidates继续现有独立synthetic snapshot字段fixture路径，refreshMock本已保留candidates；只改该字段、明确noScan/noConnect、恢复原值，不把全snapshot反射快照冻结为长期状态。新输入bridge不承包扫描模拟状态。

### 5.2 服务/时钟/存储闭包

复用M5已审契约但独立scope对象：app的feedbackPreferences/logs/curves/samples/history/customCurvesResult/curveUsageResult/beanInventoryResult/beanPreparationResult/journalStoreResult十Lazy，保存原身份/initialized不value；legacy两项只核。合法内存库与history callbacks只指scope，trace/samples只owned cache。新scope绝不借Home无审核扩依赖。

clock先装，测试BIND_AUTO_CREATE创建唯一Mock服务；onCreate后核mock/hub/logs/history/observer身份。Scale页面仍flags0绑定，不创建第二owner。本scope无机器控制调用；若使用M5通用清理需触及service shotRecovery/machineWriteRecovery，则按已审条件原四Lazy必须未初始化、仅前两换合法内存空态，credentials/retry不求值；不以“可能需要”读取原record。实际调用链/持有引用写manifest。

清理顺序：趋势dialog/页面全部destroy → ScaleOwner每实例owner=null、bound=false、visible=false、refresh无排队 → 测试bind解除+stop唯一service，核onDestroy、全部service handler与observer撤销/hub=null → main barrier与实际owned读写线程排空 → 释放本service输入owner/恢复其被替Lazy身份 → 撤clock → app十Lazy/语言/实际typed偏好逐键恢复 → files/noBackup/shared_prefs/dbhash及legacy身份核。失败也同序清理，不提前restore、不回滚原盘、不清整库；host finally force-stop。

仅白名单appearance.dark、scale_tool.one_decimal及实际触及curves.selected，存在性与类型逐键恢复，不clear；白名单XML物理SHA独列而逻辑map精确核，其它业务盘必须原样。protocol2及等待deadline始终真实SystemClock，不能被测试clock冻结。M5特殊状态未生成不由M6伪补。

## 6. 完整8主配置与真实状态

`2页 × light/dark × font1.0/1.3 = 8`，每页每profile独立instrument/freshscope。两页均非空主fixture：

- **Scale有样本主图**：clock在100000经真实按钮start计时，先推进至114600，再每1000ms一条只读观察，重量hundredths依次 `[-125, 200, 800, 1200, 1600, 1750, 1800, 1801, 1799, 1800, 1800]`，末时124600；完整10秒，当前18.00g，真实one_decimal=true副状态18.0g。负值不裁，主线只连真实相邻segment。末时通过真实按钮pause，累计24600ms；时钟始终单调，不为补历史点回退clock或倒灌通知，manifest记录实际绝对时刻，不调用timer反射改状态。主页Mock仍禁止tare/capture且明确只读，电量不可用不造82%。
- **Connection主图**：3个合成候选，真实DiscoveredDevice角色BOOKOO，唯一地址 `02:00:00:00:60:01/02/03`，名称“测试秤 A / 测试只读秤 / 测试秤 B”，RSSI -52/-65/-70；protocolId分别bookoo/felicita-ascii-read-only/bookoo。完整限制文字，Mock连接/扫描均禁用。后加40候选用真实gesture非零scroll，原恢复/返回精确锚点不变。

| 必测副状态 | 真实建立方式 / 不可伪造 |
|---|---|
| 无服务/无读数/失联 | 真正无service独立case；输入READY+null与DISCONNECTED分开，当前“—”；旧趋势仅10秒内历史而非当前读数 |
| stale/过期 | 同clock advance1501、10001而不offer，LiveTelemetry拒当前、窗口真过期；不得tick更新旧receivedAtMs |
| gap/负数/重复/证据变化 | 合法offer负数、断连再READY、>1500gap、相同timestamp、LIVE_READ_ONLY↔OFFLINE_CANDIDATE；检查实际线段/空隙及原模型测试，不补0/跨线 |
| 能力限制/电量 | weight=false另合法无读数case如桥接拒则独立原模型验证并显式pending整页；battery=false不可用、synthetic battery=true值82合法显示，负值/>100原门禁；不宣称任何协议实机支持 |
| timer | 真实start/pause/resume/reset；有值暂停必须继续而非抹零；重建状态/时间精确，默认真实clock回归保留 |
| capture/归零 | capture Intent原入口，Mock capture与tare仍disabled/null，不产生RESULT_OK或库存事件。真实WRITING/WAITING_ZERO/CONFIRMED/FAILED/UNKNOWN由原StandaloneTare tests保留；没有合法Mock UI驻留路径时明确整页pending，不反射制造成功或解除Mock保护 |
| 连接限制 | empty/3/40候选、长名/完整MAC/只读说明；活动门禁、权限/蓝牙失败沿原业务门禁/合法Mock状态，有真实入口才采；Mock不能证明系统扫描或连接成功 |
| 短高/1.3 | 实际自然预算、固定操作与完整文字；必要打开同源趋势详情，采图→状态改变同步→dismiss→恢复；不是一张空趋势截图 |

主8图不得缩减。任何桥接/合法状态缺口须列具体待补，不按通过数改分母或以组件测试冒整页。未知保留；有样本主图为开始视觉验收前置。

## 7. 可执行批次与验证

### Task 1 — 入口/桥接先封闭

- [ ] 主任务明确Service桥接单owner，核91ab或更新HEAD/status与在途M5，不改未分配文件。
- [ ] 先写实质拒绝/通知测试：real模式拒、重复/错owner拒、immutable输入不被调用方改变、同timestamp不新增trend、tick不改变接收时刻、断连断段、清理owner释放；验证测试会在缺桥接/错误实现时FAIL。
- [ ] 实现§5最小bridge与本页四处同源elapsed，保默认真实路径；检查所有mutation仅Mock/main/同clockowner。Astra审实际diff后才接service主图。

### Task 2 — 两页展示/趋势同批

- [ ] 先明确页内ScaleVisualStyle与scope资源，不改共享全局。实现§3永久结构、单位baseline、真实状态/能力行；原监听及保存尾部对比。
- [ ] ScaleTrendView新nullable style与draw证据，独立14sp Paint/axis/grid/segment/fill；无样本不绘旧线。Panel原fallback语义与dismiss保持，实际自然预算记录reading/actions/trend/right/header各px及局部scroll范围。
- [ ] Connection精确节点/tag/完整description与原候选资格，改旧selector仅针对该结构；不把48/64门禁改成“任意可见”。默认ScaleSettings/Prep像素与行为不受opt-in影响。

### Task 3 — 完整8入口、证据与回归

- [ ] 新ScaleVisualMigrationChecks复用原protocol2、Typography/Color、实际Canvas与scope清理；主图先存再业务，异常清理前留原PNG/geometry。8主fixture不空、身份/fontScale/IME/insets/API/真实app区域、source/head/merge/APK/原PNG/hash齐全。
- [ ] 独立`scale-visual-ui.yml`单job45min候选、`check_scale_visual_migration.sh`，8组合明确清单与首FAIL保原件；不堆入task-frame，不增无限timeout。沿已审fresh pidof fail-closed、font_scale存在性恢复、EXIT不覆盖finalizer。原所有required/single/advanced/lifecycle继续。
- [ ] 统一运行：

```bash
./gradlew :mobile:testMockUnitTest \
  --tests io.openhoyi.mobile.ScaleTrendWindowTest \
  --tests io.openhoyi.mobile.LocalScaleTimerTest \
  --tests io.openhoyi.mobile.LiveTelemetryTest \
  --tests io.openhoyi.mobile.StandaloneTareTest \
  --tests io.openhoyi.mobile.BeanDoseCapturePolicyTest \
  :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain
```

新增bridge tests另加真实存在类过滤；不得以零匹配当通过。继续现`ServiceShotDispatchChecks`实际通知/trend、`ScaleTrendChartChecks`左右线/中央缺口/过期无线、`ScaleTrendPanelChecks`normal/short/detail/recovered/empty、8语言图表、TaskFrame24、AdvancedPage含Capture、SingleScreenListState连接40项/原owner/重建返回、SingleScaleConnectionSlot/ReadOnlyScaleSession/StandaloneTare原门禁。现有portrait规则保留兼容但不新增M6竖屏交付。

### Task 4 — Astra八图收口

- [ ] 浅1.0两页对原稿 → 深1.0 → 两主题1.3，再有样本详情/副状态。核同状态、同可用窗口而非整画布拉伸；配置背景色与真实屏幕纯色区分别核。
- [ ] 明确每页G1间距/构图、G2Typeface/weight/字距、G3实际sp→px/Layout/单位baseline/Canvas墨迹、G4背景/选中禁用/图alpha独立结论。64/22与14轴实际非线性缩放记录，不从getter行高推实际行盒，抗锯齿不当布局容差。
- [ ] 只对已实测状态限定通过，统一更新24页对应两行；未测硬件归零/连接/最大字号/未知恢复状态各自保留，不冒称24页完成。统一提交/push取证，不重画图追认偏差。

## 8. 本计划的完成边界

已实读两张原稿、两Activity/父类、trend模型/Panel/View、Service接线、MockRuntime、时钟、能力类型和相关原测试。展示可独立实施，但Service输入桥接仍待主任务按§5明确分配；没有执行生产/测试修改、构建、设备或推送。8主图、所有新桥接运行和M6 G1–G4均未通过。原M1限定冻结继续有效，M2–M5在途结果独立，不按批次编号或Mock模型存在性推断完成。


## 9. M6 首版静态审查记录（非运行/视觉通过）

审查基于 `eb77b2f` 上未提交候选：本页 7 Kotlin、8 语种 scope 资源与桥接 unit；不包含尚未冻结的 `ScaleVisualMigrationChecks`。没有操作设备、构建或修改生产文件。

- **默认隔离/真实语义：** ScaleOwner 的 visual 默认 null，ScaleSettings 不 opt-in；Panel/View 原 null 路径保留。Mock bridge 双层约束 Mock/main/同 clock owner/hub-null；显式 offer 才进入原通知入口，read/tick 不造点或刷新 timestamp。新增通知 serial 仅 Mock 且已安装 scope 时计数，不进入 snapshot/控制；必须由真实 Service scope 验证 invalid/tick/read 的 delta=0、每个非 null offer 的 delta=1，helper tests 不能代替。
- **已收口的静态基线问题：** 主值/单位 quantity 不再用禁用 LinearLayout baseline 对齐的 CENTER_VERTICAL，现为 CENTER_HORIZONTAL|TOP 且 baselineAligned=true。64/700/96 与 22/400/34、8dp 间距不变；最终屏幕 baseline 差仍须实测为 0。
- **上述三项已静态收口：** Connection 空候选节点现使用冻结 auxiliary14/400/22 与 muted palette，原文/状态不变；趋势 grid/data/fill 现记录真实 Paint colorArgb，endpointCircle 记录实际中心/半径/颜色/style，绘制路径未改；窄横屏 stacked 最低 support 预算现取实际宽度下 VISIBLE 祖先树可点击节点最大 measuredHeight 加 scroll padding（至少48dp），保留实际测量前全子树 forceLayout，并记录 minimumSources、两区独立及 combinedMinimumSatisfiable。宽双栏分配、字体、图最小预算和默认 helper 未改变；总预算无解仍如实记录，不减门槛。仅本轮三处复核无新增静态阻断，可进入统一构建；不是局部可达性或屏幕视觉已通过。
- **待真实验收：** 标准主 fixture 非空 inline 趋势、1.3必要详情与关闭恢复、64/22真实非线性字号/Layout/baseline、完整文字/禁用色/浅深纯色内区/12%实际alpha、局部操作完整滚达、同源时钟下 stale/gap/证据变化与 owner/drain 清理。配置色不是屏幕合成色；原稿字体精确身份与不可达硬件状态仍保留明确 unknown/pending。

本次同时只读复核了其它 owner 的 Batch 重建 readiness 与 Comparison 高按钮预算：前者只在明确原调整窗口重开时核该唯一 dialog 持焦与原字段身份，不放宽默认 Activity ready；后者以真实可点击自然高替代固定48dp最低 viewport，WrongCall 改为在 override 直接调用一次 super.onMeasure。均为静态无新增阻断，实际重建及俄语大字148px操作仍待各自 CI，不计入 M6 八图验收。
