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
| 自动连秤 | ReconnectPolicy + NativeDeviceHub | 10分钟窗口、退避、手动取消测试 | 地址变化时重新扫描/绑定；宿主生命周期实测 |
| 去皮与重量停止 | ExtractionController / Policy | 去皮写成功后等待近零通知、重量时效、去重、掉秤保护停止、停止未知结果 | 真实秤延迟和业务阈值标定 |
| 三次萃取链路 | ReplayChecks + shots.tsv | 手动44.098s；流量结束无额外stop；重量17.786s | 不等于物理咖啡机回放；最终杯重未认证 |
| Android连接/服务/CCCD/写入 | AndroidGattDriver | SDK35编译、AAR构建、lint | 真机permission/revoke/disconnect/GATT回调 |
| 扫描 | ScanCoordinator | Android编译/lint；统一扫描两角色 | 真机扫描频率、位置权限与开关验证 |
| Android后台 | LabService + NativeDeviceHub | connectedDevice前台服务、Binder与页面解耦；静态独立审查 | 真机锁屏/旋转/权限撤销；不能承诺进程被杀后的停液 |
| 日志 | WireTrace + LabApplication + TraceStore | 真实传输边界、密码整帧脱敏、进程单写队列、顺序/轮转/导出/失败测试 | 真机导出；崩溃前未落盘记录可丢失，codec.hex本身不脱敏 |
| 设置写入结果 | OperationResult、SettingsWriteTracker | 区分Failed/Unknown/Cancelled/transport Success；常用设置需新鲜匹配0x83帧才标确认 | 其余设置的回读事务未实现；实机时序未验收 |
| 大包/MTU/通用Read | 首版明确不支持 | Android写入限制20字节 | 后续需真实协议分片证据后实现 |
| 管理命令/OTA | UnsupportedCommandGroup | 无执行入口 | 密码修改、校准、出厂、OTA独立验证 |
| 原生诊断UI/持久化 | LabActivity / LabService | 独立APK、未知/过期/断开显示、成功秤地址、离线UI测试APK | 冒烟APK已构建未执行；实机布局、权限、连接和导出 |
| 原生日常版基础流程 | mobile/HomeActivity、MobileService、CurveActivity、ExtractionActivity、CurveCatalog、ShotGate | 独立APK、曲线和启动门禁单测、此前构建和Lint通过；复用已验证会话控制 | UI/连接/真实萃取未实机验收；未知进程终止无法保证停液 |
| 旧版工厂曲线萃取入口 | FactoryCurveCatalog、FactoryCurveAdapter、FactoryWireProof、CurveLibrary、factory_wire_v1.tsv | 100条元数据与旧版归一化一致；有秤/无秤200帧和旧版编码函数逐字节对照；发送前复核曲线、帧和秤模式 | Alpha尚未实机萃取；用户自定义曲线未导入，曲线编辑后置 |
| 首页五个快捷槽位 | PresetSlots、factory_slot_wire_v1.tsv、DeviceSession、ExtractionActivity | 100条×5槽位×2秤模式共1000帧与旧版 `startChart` 对照；本地映射、确认页、停止槽位和历史记录接入 | 尚未实机验证槽位启停与机器显示；用户自定义曲线未导入 |
| 独立电子秤去皮 | StandaloneTare、MobileService、NativeDeviceHub、BOOKOO去皮帧 | 写入结果与归零读数分离；要求写入后新鲜近零读数；超时/断线保留未知，阻止同时重复发送 | 尚未用实机确认去皮响应和归零时延 |
| 常用机器设置与拨杆模式 | MachineSettingChange、SettingsWriteTracker、MachineSettingsActivity、HomeActivity | 萃取/蒸汽温度、两路加热、照明、自动待机时间、睡眠计划总开关、手动/自动压力/自动流量共88条允许参数报文与旧版编码函数对照；写入后以新鲜匹配0x83回读确认 | 尚未实机验证设置回读时序；运行模式位0x04及每日睡眠时间、待机温度、温差补偿等写入未开放 |
| 首页立即睡眠 | CoffeeCommands.sleepNow、DeviceSession、SleepNowTracker、HomeActivity | 旧版固定5字节命令；新鲜待机遥测、显式确认、萃取/设置事务门禁；写入后须新鲜0x40睡眠状态1确认 | 尚未实机验证入睡/唤醒；App没有唤醒命令，需机器拨杆 |
| 多页面前台状态 | VisibleScreens、MobileService | 独立页面token、切页500毫秒缓冲；多页面交叠单测通过 | 需实机验证切页对自动连秤窗口的影响 |
| 原生萃取历史基础页 | ShotHistory、HistoryActivity、MobileService | 持久化请求及终态，未知结果不标完成；状态/重启/坏行/保留上限单测通过 | 旧App数据未迁移，异步SharedPreferences落盘前突然断电可能丢最后写入；UI待实机验收 |
| 实时和历史曲线 | ShotSeries、ShotChartView、ShotSamplesStore、HistoryDetailActivity | 只处理0x80已解码字段，新鲜秤重合并；2000点上限、时间顺序、文件往返与清理单测通过；构建/Lint通过 | 采样只在明确结束后异步落盘，进程中断可能没有曲线文件；图表待实机验收 |

## 最新实机补验（2026-09-23）

见[实机报告](validation-2026-09-23.md)。咖啡机认证/设置/遥测有实机证据；新增仪器测试的4项检查通过。纯Kotlin会话回归增至33场景。BLE两次status=8断线未定位，未通过长期稳定性验收；08:42双设备并行约2分钟无断线，后因测试重启进程中断。秤负重量/自动重连及SAF系统选择器流程未完成。下方首轮结果为历史记录，不能代替最新报告。

Alpha 日常版第一段功能见[说明](mobile-alpha.md)。立即睡眠接入后，协议检查35,186项、会话检查34项、Alpha单测35项通过，debug构建成功，Lint 0错误、2条警告。当前用户要求暂不安装，尚无Alpha实机萃取、槽位启停、独立去皮、睡眠或设置回读证据。Lab独占连接约6分钟、后台/锁屏各约1分钟持续收数，见上方报告。

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
