# Scale support证据改用完整值快照

范围仅修测试证据序列化。旧5d08 push run39884256073的三次完整attempt像素一致，geometry仅serviceSnapshot中的SleepPart对象地址变化；随后5秒耗尽。保留FAIL，不判产品像素有错或视觉通过。证据见 `build/task-first-evidence/ci-5d08fd6-scale-push/diagnosis.md`。

- 基线：固定5d08fd658ad7300d0478a2b5e117e88eb3c99f11，现授权feature/task-first-ui合批→develop PR；按[Git工作流](../git-workflow.md)，不直推主干、不另抢owner。
- 文件：同Sol只改 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ScaleVisualMigrationChecks.kt`，runner若需校验schema仅 `scripts/check_scale_visual_migration.sh`；不改MobileSnapshot/协议类toString、不改shared Window协议或生产。
- 验证/推送：静态冻结→相关测试与双包统一构建→原Scale四配置CI→Astra原图；root合批普通推送，不微推。当前未构建/触发CI。
- 设备/发布：仅已有Mock owner读取，不点tare/capture/机器按钮，不写设备、不改服务状态/时钟，不发版/签名/安装。
- 回退：独立revert本测试补丁，保留ZIP和失败，不清记录。

## 具体序列化合同

5d08的supportState用 `service.snapshot.toString()`，既不稳定也不完整：SleepPart未重写toString，message.toString只给translatable标志。改为主线程单次读取 `val snapshot=service.snapshot` 后生成detached JSONObject；不能每字段重新取snapshot造成跨时刻拼接。不保留不稳定地址字符串在受比较target内；这不是删业务字段，需覆盖MobileSnapshot全部19个公开字段及各子值。

精确顶层keys：coffeeState、scaleState、coffee、coffeeAt、alarmBits、alarmAt、settings、settingsAt、sleepFirst、sleepSecond、sleepFirstAt、sleepSecondAt、weight、weightAt、scaleObservation、scaleCapabilities、candidates、scanning、message。null显式JSONObject.NULL，enum保name，列表保原顺序，不sort/drop，不round时间/数值。

子结构均值编码，不用任意Object.toString兜底：

- HoyiMessage写kind与对应构造字段：Settings的firmwareMajor/minor/patch、flags、brewTemperatureC、brewCompensationTenthsC、steamTemperatureC、standbyMinutes、standbyTemperatureC、cupCount、filterFlags；IdleTelemetry的brew/steamTemperatureHundredthsC、brew/steamPressureTenthsBar、sleepStateRaw、alarmBits、cupCount、extraSensorRaw；ExtractionTelemetry的slotOrPhase、elapsedSeconds、pressureTenthsBar、totalWaterTenthsMl、brewTemperatureHundredthsC、flowTenthsMlPerSecond、statusBits、manualStageRaw；SleepPart的firstDaySundayIndex、enabledBits、days每项sleepHour/sleepMinute/wakeHour/wakeMinute。
- 每个HoyiMessage与BookooSample、ScaleObservation保raw完整字节值（测试仅Mock已知遥测/设置/睡眠/秤帧；不得导出认证命令/密钥）；用固定大写hex或逐byte0..255数组，空与null不混。ByteFrame长度一并记录，类型unknown直接fail，不能转换成地址。
- BookooSample保weightHundredthsGram、deviceFlowHundredths、weightSignRaw、flowSignRaw、raw。
- ScaleCapabilities保weight/tare/deviceFlow/battery/deviceTimer/validatedWeightControl六bool；ScaleObservation保hundredthsGram、receivedAtMs、capabilities、evidence、deviceFlowHundredths、batteryPercent、raw，以及派生eligibleForControl作交叉检查，不能用其替代原字段。
- candidates逐项address、advertisedName、rssi、candidateRole、scaleProtocolId，顺序保持；service对象identity/observationSerial与现有UI/趋势/timer不变量继续独立比较。
- SnapshotMessage保initialText、当前确定资源context的render结果、是否resource；为了保留原资源身份/参数，测试用现有field只读原private resource树：ResourceMessage记录resourceId与values每项明确类型和值，ResourceSequence记录按序parts并递归。资源标量保持null/String/Char/Boolean/整数/浮点/BigInteger/BigDecimal区别，不截断；不得调用任意对象toString当fallback。此树访问只在测试，未识别ResourceText类型立即fail。生产Message API不扩展。

原supportState中的serviceObservationSerial、各View/Window/scroll身份、weight/freshness/tare/device状态、enabled、fixedPrimary/Tare/Capture及trend/timer字段全部保留。同帧observe前后仍完整比较该结构化serviceSnapshot；reach invariant也比较完整值，不只对SleepPart特殊忽略。无延时、无屏蔽字段、无放宽5秒或四零差。

## 实施与验收

1. 在Scale测试内写窄值快照helper，所有call site使用同一schema；字段明确列出。完成后对照当前冻结MobileSnapshot/协议声明逐项检查遗漏。
2. 增加有意义的序列化验证：同值但新建SleepPart/ByteFrame/Message实例输出精确相等；改变任一sleepDay/enable/raw byte/timestamp、能力、候选顺序、message资源ID或参数则输出不等；null与空/0区分。可用现Mock AndroidTest前置校验，不引入单独通用序列化库，不mock采帧来当真实CI。
3. 保存schema版本/完整字段值；现protocol target仍零差比较，support前/逐目标/恢复完整不变量保持。runner若原按字符串类型读取，改为强制精确schema和完整值相等；不能兼容接收原地址字符串作为成功。
4. 原Scale CI重新走support-before、六目标、restored、short-window链，收同源PNG/geometry/proof；真实字段若仍变化，保FAIL并列差异路径，禁止再删字段来稳定。

旧失败与新源码/新CI分开记；不因snapshot稳定即签支持区可达/视觉通过。

## 本地实施记录（待 Astra 审）

- `ScaleVisualMigrationChecks` 的 `supportState` 先单次读取 `service.snapshot`，再写完整 19 字段结构；`serviceSnapshotSchemaVersion=1` 独立保留。原有 owner/UI/趋势/scroll 字段与同帧比较不变。
- 测试在真实 Scale Activity 上先运行纯值反例：新实例同值相等；sleep day、enable、raw、timestamp、capability、候选顺序、message ID/参数、null、空列表、秤 raw、咖啡字段变化均不等。另核资源标量类型、递归 sequence 和精确 19 keys。报告写 `snapshot-contract.json`。
- runner 拒绝旧字符串 schema，检查完整快照的键、子类型、raw 长度/大写 hex 与反例报告；全部 protocol2、5 秒预算和零差要求保持。当前仅完成静态验证，未构建、未运行设备/CI、未提交或推送。

## Astra静态终核

已实际读取测试/runner完整diff并与MobileSnapshot、协议4个HoyiMessage子类型、BookooSample、ScaleObservation/Capabilities、DiscoveredDevice及SnapshotMessage资源树声明逐项对照；冻结SHA测试 `29339b949340f5c24c9cd8d571a9943ebfa0794b3b5ba33d4518c5730c3051a7`，runner `b5738a025c4bac156b4ff76eb1daf82bbe0a9ced8d9dbbafa92448d18ef746b1`。

19顶层字段无遗漏，SleepPart的firstDay/enable/days/raw与时间戳均保留；消息资源ID、递归parts、参数类型/值、initialText及rendered都进入值证据。raw明确长度和完整hex；候选/资源序列保序、null与空/0分开，不用对象地址兜底。supportState仅取一次snapshot，原observationSerial/UI/趋势/timer/scroll身份及完整业务比较保持。

新实例同值对照与12类变值反例均有真实check，额外覆盖null/空、资源标量及sequence；这些是待运行的前置合同验证，不声称已执行。runner要求schemaVersion=1、精确19键及子结构/类型分支/raw格式，拒绝旧字符串；原前后/逐目标/恢复仍比较完整serviceSnapshot，protocol2/5秒/四零差未改。

未发现剩余已确认静态阻断，可纳入统一构建与原Scale CI。旧5d08的support-before失败不追认通过；新完整支持区滚达/恢复及视觉仍待原件验收。

## Astra 编译单行增量复核

独立读取当前 ScaleVisualMigrationChecks.kt：SHA-256 `e27185a45e33703146511b2d1ab41460a6c0fff5225191c5ebb8cb7b3c81dc25`。当前仅第15行保留 `import io.openhoyi.session.DeviceState`；在第41行 `DeviceRole` import 后于内存逆补同一 DeviceState import，所得SHA精确等于旧已审 `29339b949340f5c24c9cd8d571a9943ebfa0794b3b5ba33d4518c5730c3051a7`。

因此确认本次只有删除重复import一行，未变19字段快照/子类型/反例/runner合同；原静态审核结论保持。此复核不宣称重构建已成功，由root继续原统一构建。本次未改源码、未运行构建。
