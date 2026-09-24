# 覆盖矩阵与多轮核验

这不是百分比覆盖率。包数量、断言数量、功能数量分别计算，不把回调success当作硬件执行成功。

| 要求 | 实现入口 | 证据 | 未完成项 |
|---|---|---|---|
| 协议无Android依赖 | protocol-core | JVM独立编译 | 无 |
| 咖啡机编码 | CoffeeCommands | 三条实际20字节启动、停止、温度、加热、睡眠及认证人工向量 | 未采集组合不开放发送 |
| 0x83/40/80解码 | HoyiCodec | 真实通知全量回放、字段人工断言、截断与随机输入 | 仅已观察长度；未知固件/新扩展拒绝或标未知 |
| BOOKOO | BookooCodec | 18,840条通知、XOR、正负号、去皮初始化 | 已知负质量对照、其他固件/其他秤 |
| 字节防篡改 | ByteFrame / GattOperation.Write | 输入、输出数组修改回归 | 无 |
| 双设备隔离 | 两个GattQueue/AndroidGattDriver | 独立队列测试；08:42双设备Ready并行通知约2分钟 | 长时间并行/掉线后恢复 |
| 队列与超时 | GattQueue | 串行、旧代次、超时关闭、异常回调、取消旧启动 | Android回调行为实测 |
| 协议就绪 | DeviceSession | 咖啡机认证+设置、秤四条初始化+首个样本真机均Ready | 其他型号与长时间运行 |
| 自动连秤 | ReconnectPolicy + NativeDeviceHub | 10分钟窗口、退避、DISCONNECTED/FAILED终态恢复、成功后退避重置、不支持设备停止重试及手动取消测试 | 地址变化时重新扫描/绑定；宿主生命周期实测 |
| 去皮与重量停止 | ExtractionController / Policy | 去皮写成功后等待近零通知、重量时效、去重、优先按新鲜机器萃取计时执行7秒门槛、机器计时过期时单调时钟兜底、掉秤保护停止、停止未知结果 | 真实秤延迟和业务阈值标定 |
| 三次萃取链路 | ReplayChecks + shots.tsv | 手动44.098s；流量结束无额外stop；重量17.786s | 不等于物理咖啡机回放；最终杯重未认证 |
| Android连接/服务/CCCD/写入 | AndroidGattDriver | SDK35编译、AAR构建、lint | 真机permission/revoke/disconnect/GATT回调 |
| 扫描 | ScanCoordinator | Android编译/lint；统一扫描两角色 | 真机扫描频率、位置权限与开关验证 |
| Android后台 | LabService + NativeDeviceHub | connectedDevice前台服务、Binder与页面解耦；静态独立审查 | 真机锁屏/旋转/权限撤销；不能承诺进程被杀后的停液 |
| 日志 | WireTrace + LabApplication + TraceStore | 真实传输边界、密码整帧脱敏、进程单写队列、顺序/轮转/导出/失败测试 | 真机导出；崩溃前未落盘记录可丢失，codec.hex本身不脱敏 |
| 记忆秤轮询可观测性 | NativeDeviceHub / MobileService | 前台窗口打开、自动连接尝试序号、窗口关闭以低频事件输出到 `OpenHoyiMobile`；2026-09-24 Alpha 冷启动第4次尝试进入 READY，详细传输保存在 trace | 10分钟窗口关闭、断链退避及手动断开待按[验收顺序](alpha-acceptance.md)核对 |
| 设置写入结果 | OperationResult、SettingsWriteTracker | 区分Failed/Unknown/Cancelled/transport Success；常用设置需新鲜匹配0x83帧才标确认 | 其余设置的回读事务未实现；实机时序未验收 |
| 大包/MTU/通用Read | 首版明确不支持 | Android写入限制20字节 | 后续需真实协议分片证据后实现 |
| 管理命令/OTA | UnsupportedCommandGroup | 无执行入口 | 密码修改、校准、出厂、OTA独立验证 |
| 原生诊断UI/持久化 | LabActivity / LabService | 独立APK、未知/过期/断开显示、成功秤地址、离线UI测试APK | 冒烟APK已构建未执行；实机布局、权限、连接和导出 |
| 萃取断链人工提醒 | ShotSafetyAlert、MobileService、HomeActivity | 启动/萃取/停止期间断链或结果未知时保留警示；前台服务通知更新、高优先级提醒、首页提示，以及首页和萃取页固定底部停止入口；StopActionPresentation 测试涵盖运行中、停止处理中、断线结果未知和重连恢复；萃取页申请一次通知权限并提示拒绝后的限制；明确终态后清除 | Android通知权限或系统策略可能阻止独立高优先级通知；尚未实机验证后台提醒 |
| 原生日常版基础流程 | mobile/HomeActivity、MobileService、CurveActivity、ExtractionActivity、CurveCatalog、ShotGate | 独立APK、曲线和启动门禁单测、此前构建和Lint通过；2026-09-24 Alpha 实机完成 HOYI 与 BOOKOO 连接和持续通知 | 已验证三次受监护实杯的启动与手动停止；目标重量自动停止、异常断链和其它 UI 仍待实机验收；未知进程终止无法保证停液 |
| 实时读数时效 | LiveTelemetry、HomeActivity、ExtractionActivity | 连接就绪且时间戳在过去1.5秒内才显示当前机器/秤数字；过期、未来或断开后显示“—”；边界单测 | 页面状态变化和阈值在Alpha实机待验收 |
| 旧版工厂曲线萃取入口 | FactoryCurveCatalog、FactoryCurveAdapter、FactoryWireProof、CurveLibrary、factory_wire_v1.tsv | 100条元数据与旧版归一化一致；分类与名称搜索，未通过报文校验的曲线只可浏览；有秤/无秤200帧和旧版编码函数逐字节对照；发送前复核曲线、帧和秤模式 | 工厂曲线槽位5已实机启动并手动停止，其他曲线及目标重量自动停止仍待验；用户自定义曲线仅可只读导入，控制与编辑后置 |
| 首页五个快捷槽位 | PresetSlots、factory_slot_wire_v1.tsv、DeviceSession、ExtractionActivity | 100条×5槽位×2秤模式共1000帧与旧版 `startChart` 对照；本地映射、确认页、停止槽位和历史记录接入 | 槽位5已实机启停，但其它槽位及机器屏幕显示待验；用户自定义曲线不能放入槽位 |
| 已记住电子秤自动连接 | ReconnectPolicy、NativeDeviceHub、DeviceSession、HomeActivity、MobileService | 前台10分钟窗口，咖啡机未连接时也尝试；失败退避、连接以 DISCONNECTED 结束后的继续重试、成功后退避重置、不支持设备停止重试、手动断开取消；冷启动自动拉起服务；2026-09-24 Alpha 权限已授予，第4次建链成功并持续收数 | 前三次建链超时原因、完整10分钟窗口、后台及人为断链恢复尚未实测 |
| 咖啡机密码记忆 | CoffeeCredentialStore、CoffeeCredentialRetryGate、MobileService、HomeActivity | 成功进入 READY 后按地址用 Android Keystore AES-GCM 保存；密文地址绑定、两次失败回退及失败计数跨 Service 重启单测通过；2026-09-24 首次真机连接 READY 后确认本机存在 1 条密文且无明文密码 | 第二次选择同一 HOYI 时免输密码及失败回退仍待实机核对 |
| 独立电子秤去皮 | StandaloneTare、MobileService、NativeDeviceHub、BOOKOO去皮帧 | 写入结果与归零读数分离；要求写入后新鲜近零读数；超时/断线保留未知，阻止同时重复发送；2026-09-24 Alpha 两次去皮分别在写入后约62毫秒与53毫秒收到归零通知 | 负重量与移走杯子的实际动作相符；仍需核对秤屏、掉线恢复及异常路径 |
| 常用机器设置与拨杆模式 | MachineSettingChange、SettingsWriteTracker、MachineSettingsActivity、HomeActivity | 萃取/蒸汽温度、冲泡温差补偿、两路加热、照明、自动待机时间、待机温度、睡眠计划总开关、供水来源、咖啡馆/工作室模式、手动/自动压力/自动流量共593条允许参数报文与旧版编码函数对照；要求新鲜唤醒待机遥测，写入后以新鲜匹配0x83回读确认 | 尚未实机验证设置回读时序；其它低频设置写入未开放 |
| 累计杯数重置 | CoffeeCommands、CupResetTracker、MachineSettingsActivity | 固定5字节命令与旧版编码函数对照；输入当前杯数并二次确认；新鲜且一致的设置/待机杯数，写入后只有新报文归零才确认 | 无真实重置采集样本，未在Alpha实机执行 |
| 每周睡眠时间编辑 | WeeklySleepSchedule、DeviceSession、SleepScheduleWriteTracker、MachineSettingsActivity | 旧版整周两包写入样本逐字节对照；500ms顺序写入、禁止并发、两段新回报全周匹配才确认 | 写入期间机器端行为及两段回报时序尚未实机验收；部分成功无自动回滚 |
| 首页立即睡眠 | CoffeeCommands.sleepNow、DeviceSession、SleepNowTracker、HomeActivity | 旧版固定5字节命令；新鲜待机遥测、显式确认、萃取/设置事务门禁；写入后须新鲜0x40睡眠状态1确认 | 尚未实机验证入睡/唤醒；App没有唤醒命令，需机器拨杆 |
| 工作室曲线预热 | CoffeeCommands.brewWait、BrewPreparation、StudioStartGate、MobileService、ExtractionActivity | 0/75–105°C共32帧与旧版编码对照；模式位回读、补偿后±1°C判定、新鲜温度确认、显式启动、取消及10分钟自动取消 | 取消命令无独立回读；实机预热/取消/启动时序未验收 |
| 机器告警显示 | HoyiCodec、MachineAlarms、HomeActivity、ExtractionActivity | `0x40` bit0–14与旧版C1–C14/C16映射对照；合成帧及新鲜/过期状态单测；新鲜C1–C14和未知bit15阻止启动，C16保留警示 | 采集记录没有非零告警帧，尚无实机告警验证；没有忽略告警入口 |
| 多页面前台状态 | VisibleScreens、MobileService | 独立页面token、切页500毫秒缓冲；多页面交叠单测通过 | 需实机验证切页对自动连秤窗口的影响 |
| 原生深浅色主题 | ThemedActivity、昼夜色板、HomeActivity | 首页 Switch 保存偏好；六个 Activity 统一使用当前模式的主题与色板；设备 Service 不因页面重建而重置 | 深浅色页面对比度、图表和系统栏需实机验收 |
| 原生萃取历史基础页 | ShotHistory、HistoryActivity、MobileService | 持久化请求及终态，未知结果不标完成；状态/重启/坏行/保留上限单测通过 | 旧App数据未迁移，异步SharedPreferences落盘前突然断电可能丢最后写入；UI待实机验收 |
| 历史与曲线导出 | ShotHistoryArchive、HistoryActivity、MobileApplication | SAF创建ZIP，CSV保留原始状态和单位，每杯采样独立TSV；路径用固定编号，单杯读取失败不丢其它记录，ZIP结构单测 | 系统文件选择器及外部提供方写入尚未实机验收；导出时进行中的曲线只是快照 |
| 实时和历史曲线 | ShotSeries、ShotChartView、ShotSamplesStore、HistoryDetailActivity | 只处理0x80已解码字段，新鲜秤重及秤报告流速合并；原生图展示压力、机器水流、秤流速、重量及萃取温度，温度使用独立动态刻度；2000点上限、时间顺序、首次与约每5秒异步暂存、七列文件及旧六列兼容、最终文件往返与清理单测通过；损坏文件明确报错，不静默跳行或截断；构建/Lint通过 | 最后一次暂存后的点及尚未完成的异步写入可能因进程中断丢失；秤流速物理单位及图表待实机验收 |

| 旧版曲线库只读迁移 | 旧版 `exportLegacyCurves`、LegacyCurveCodec/Store、LegacyCurveActivity | 完整保存 `hy_chartLib` 数组与分类配置；格式、重复导入、损坏替换测试；旧版分享链路16项测试通过；未进入任何BLE控制许可 | 缺真实导出文件与分享/SAF实机验证；开放启动前须逐条旧版报文对照 |
| 旧版用户曲线报文采样 | `generate_legacy_curve_oracle.py` | 固定旧版编码函数哈希和导出文件哈希；100条工厂曲线×6槽位×2秤模式共1200帧与现有 oracle 一致；空槽位和损坏输入检查 | 缺真实用户曲线导出和针对这些曲线的逐字节对照，不开放控制 |
| 导入曲线候选参数转换 | LegacyCurveAdapter | 100条工厂形状曲线×6槽位×2秤模式与现有旧版帧对照；`seg1FlowMode` 独立旧版向量；模糊/缺失字段拒绝 | 缺真实用户曲线导出及逐条证明；此适配器不进入启动许可或服务层 |
| 导入曲线逐帧离线核验 | LegacyCurveWireAudit | 导出原始字节哈希、旧版编码器哈希、非空索引与六槽位顺序绑定；逐条对照有秤/无秤报文；缺行、错序、帧差异及转换失败均拒绝 | 实际用户导出尚未提供；无真实文件时实物测试跳过，匹配也不自动开放控制 |
| 自定义曲线边界报文 | 合成导出与旧版编码器离线证明 | 128条非工厂形状曲线×6槽位×2秤模式，共1536帧通过原生逐帧核验；覆盖重量取整、分段及首段流量模式 | 合成极端值不代表机器安全范围，也不代替用户真实导出 |
| Mock 设置与预热交互 | MockDeviceRuntime、BrewPreparation | 常用设置、单日睡眠计划、杯数重置、去皮、立即睡眠修改纯内存回读；工作室预热约5秒达到目标，支持取消并复用预热状态机；萃取中、预热中和入睡后禁止不合时序的设置操作；模拟萃取保留结束重量；无BLE权限和Hub | 未安装设备检查按钮、文案与小屏布局；模拟时序不是机器行为证据 |
| 旧版历史显式导入 | LegacyHistoryCodec/Store、LegacyHistoryActivity | 按旧版 `brew_history_v1` 保存结构构造 JSON；严格校验、独立存储、冲突拒绝、重复导入与图表单位说明；Alpha/Mock 单测各72项通过 | 已修改旧版有显式导出入口，但缺少真实导出文件；未实机验证分享、SAF 和旧数据兼容 |

## 最新实机补验（2026-09-24）

见[实机报告](validation-2026-09-23.md)。咖啡机认证/设置/遥测有实机证据；新增仪器测试的4项检查通过。纯Kotlin会话回归增至33场景。BLE两次status=8断线未定位，未通过长期稳定性验收；08:42双设备并行约2分钟无断线，后因测试重启进程中断。秤负重量/自动重连及SAF系统选择器流程未完成。下方首轮结果为历史记录，不能代替最新报告。

Alpha 日常版第一段功能见[说明](mobile-alpha.md)。历史与曲线导出接入后，协议检查36,796项、会话检查39项、Alpha与Mock各96项单测（95通过、1项真实导出条件测试跳过），双变体构建成功，Lint 0错误、2条警告。2026-09-24 Alpha 已安装，完成 HOYI/BOOKOO 同时 READY、三次受监护实杯的启动与手动停止、两次独立秤去皮，并修正了重量目标曲线启动前的去皮时序，见[验收记录](alpha-acceptance.md)。目标重量自动停止、睡眠、睡眠计划写入、设置回读与后台断链提醒仍缺实机证据；最新 Lint 因 Gradle 代理不可用而未重跑。Lab独占连接约6分钟、后台/锁屏各约1分钟持续收数，见上方报告。

## 本轮 Lab 结果（2026-09-22）

原生APK及AndroidTest APK构建通过；会话/策略/集成/回放/trace共32个命名场景；App JUnit 9个测试通过；App lint 0错误5警告（版本提示与中文诊断文案未资源化）。协议断言仍为34,991次。完成独立范围审查及两轮生命周期复核，修复断线后手动停止失效、取消未发送启动后卡住停止、导出期间停止服务挂起、重启并发日志写入。

本机ADB无设备，Android UI冒烟测试仅构建未执行；无原生连接/控制实测。

## 前一轮核心结果（2026-09-22）

标准Gradle全量命令成功。协议34,991次断言（其中32,856条通知，不是34,991个独立用例）；会话/策略/集成/回放共25个命名场景；Android debug AAR成功；lint 0 errors / 2 warnings。首次使用网络补齐依赖后，最终验证在offline模式完成。

## 检验轮次

1. 测试先行：协议API缺失、会话API缺失、策略缺失等红灯后实现；认证数字字节单独失败后修复。早期编译使用缓存Kotlin编译器，最终均走标准Gradle。
2. 真实数据：32,856通知解码、三条启动逐字节、三段2428条观察时序输入。来源文件哈希和匿名会话/seq保留；不包含MAC、认证密码或厂家bundle代码。
3. 状态故障：旧连接回调、超时、重复通知、陈旧或未来重量、错误会话、去皮未确认、掉线结果未知、非法启动参数、回调抛异常、排队启动被停止取消、队列满时紧急停止仍被接纳。
4. 规格核对：Android与纯Kotlin依赖边界、公开API传递依赖、控制能力门禁、未知结果、后台宿主边界、未验证功能清单。
5. 构建门禁：Gradle check、Android assembleDebug、lintDebug。lint 0错误，2警告为跨API属性与Gradle更新提示。

前一轮核心阶段独立审查曾因服务额度中断。本轮已补独立核心停止语义审查与App范围/生命周期静态审查；静态通过不代表硬件等效验证。

## 发布前必须完成

- 继续在实机验证已审查的控制和断线语义。
- 通过可安装测试宿主，先只连设备/接收，再人工控制下验证停止、去皮及三条曲线。
- 校准新旧时序差异、BOOKOO负值与重量单位；验证回到前台、锁屏及断连。
- 在已有诊断Service和持久日志基础上，验证系统后台限制后再开放正式萃取UI。
