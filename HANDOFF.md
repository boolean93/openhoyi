# Native BLE / Lab handoff

分支 `feature/native-ble`，worktree `../openhoyi-native`，基于独立openhoyi仓库。旧hoyi-project未改动。前一阶段核心提交363cea5；本轮新增原生Lab诊断APK。

## 最新实机进展（2026-09-23）

详见 `docs/validation-2026-09-23.md`。独立APK已安装，咖啡机认证、设置和实时遥测已确认，固件1.1.3；BOOKOO四条初始化写入、Ready及并行遥测已确认。08:54系统GATT Map发现旧版持有咖啡机连接。用户确认无操作并授权后，暂停旧版；再次扫描发现两台设备，原生Lab重新双连。08:56～09:03独占对照中，咖啡机/秤持续约6分钟，累计385/3792条通知；切后台及锁屏各约1分钟仍收数，无自然断线。记忆秤随咖啡机Ready自动连接已实测；手动重复点击秤会造成一次冗余重连，待改。旧版目前保持停止，Lab保持连接。

前一晚两次BLE `status=8`断线仍未定因，独占对照只表明短时稳定。08:44仪器测试曾重启Lab并中断连接；后续必须用`scripts/run_lab_smoke.sh`检查服务不存在，不能在活跃连接上直接运行测试。实机仪器测试已验证Activity重建、前后台服务、服务停止后进程内ZIP导出。负重量、异常断线后的重试、SAF选择器、长期BLE和萃取仍未验收。认证未确认时只接收遥测也不Ready的回归已加入，会话场景增至33。

## 当前交付

六模块：protocol-core → device-session → bluetooth-android，另有trace-core供app（Lab）/mobile（Alpha）共用。纯Kotlin模块无Android依赖；两款App均为原生View、Activity、本地Binder和connectedDevice前台Service，没有UniApp/JS/WebView。独立包 `io.openhoyi.lab` 和 `io.openhoyi.mobile`，均不覆盖旧App。

`mobile` 第一段原生日常版已有首页连接、实时状态、传输日志导出、三条采集曲线与100条工厂曲线、五个快捷槽位、常用机器设置写入与回读状态、萃取实时图、历史列表及曲线详情；见 `docs/mobile-alpha.md`。工厂曲线在有秤/无秤两种模式共200条临时启动帧及1000条快捷槽位帧与旧版编码函数逐字节对照，并在发送前再次核对曲线、帧和秤模式；报文不符即拒绝。萃取开始需显式确认，服务层要求咖啡机新鲜待机数据，重量模式要求新鲜秤数据；运行中拦截主动断链和停服务。用户要求暂不安装，因此尚无Alpha实机启动或控制证据。不能把代码验收当成硬件等效或完整替代App。

工厂曲线的逐字节许可集合必须从 `MobileApplication.curves` 注入 `NativeDeviceHub`，最终到 `DeviceSession.startExtraction`。若仅在页面和服务层放行，会话层仍拒绝工厂帧。默认无注入时会话仍只接受三条采集帧；不要移除此写入门禁。

本轮离线验证：旧版编码器重新生成200帧，与随包资源字节一致；Alpha单测19项0失败、debug构建成功、Lint 0错误/2条警告。未安装、未发起任何机器控制。

后续增加了首页 BOOKOO 独立去皮：`StandaloneTare` 将 GATT 写入与新鲜归零读数分成两个阶段，写入后5秒未见归零即显示结果未知；萃取期间禁止手动去皮。会话检查34项、Alpha单测22项、debug构建及Lint通过（0错误、2条警告）。该功能尚未实机验收，实际测试需检查写入日志、归零时延和重复点击门禁。

首页五个快捷槽位使用旧版 `startChart(slot,1)` 路径；`scripts/generate_factory_wire_oracle.py` 从旧版编码器另生成 `factory_slot_wire_v1.tsv`（100条×5槽×2秤模式，1000帧）。`FactoryWireProof` 在运行时核对后将帧许可传到底层会话；`CoffeeSessionControl` 保留启动槽位用于停止。快捷位默认旧版前五条曲线，曲线库可本地重指派；点击快捷位仍需显式确认。历史记录新增槽位字段，旧8字段记录继续可读。尚无实机槽位启停证据。

常用机器设置新增萃取/蒸汽设定温度、两路加热、照明、自动待机时间及每周睡眠计划总开关；首页新增手动/自动压力/自动流量三种拨杆模式。独立脚本 `scripts/generate_machine_setting_oracle.py` 仅执行旧版对应编码函数，生成88条允许参数报文样本。自动待机写入档位0–4，机器回读分钟0/15/30/60/120；更改时间时原样保留最近0x83回报的待机温度，若温度在确认期间改变则拒绝写入。`MachineSettingChange` 限制参数，`SettingsWriteTracker` 只在写入后收到新的匹配0x83设置帧时认定已应用；无回读显示未知。拨杆模式使用flags 0x80/0x40，不包含另一个涉及温度等待的运行模式位0x04。每日启用位按旧版0x83 0x40帧的bit7–bit1显示；开启总开关要求两段计划和所有时间有效，关闭不依赖计划完整。服务层与Hub均禁止萃取期间改设置。实机设置写入和回读时序尚未验收。

设置报文资源复生成后字节一致；协议检查35,146项（含32,856条通知回放）、会话检查34项、Alpha单测28项通过，debug构建成功，Lint 0错误/2条既有警告。未安装、未发送实机设置命令。

本轮离线复核：临时200帧与快捷位1000帧重新生成后均与随包资源完全一致；会话检查34项、Alpha单测25项通过，debug构建成功，Lint 0错误/2条警告。未安装、未发出实机命令。

Lab仅提供授权/扫描/手动连接/断开/实时数据/日志导出/停止服务。没有萃取、设置、校准、OTA按钮。咖啡机连接必须输入6位密码（数字字节0..9），认证并同步时间；BOOKOO按500ms间隔执行4条初始化写入，收到之后的新样本才Ready。连接并非完全只读，以上初始化是明确例外。

- `app/.../LabActivity.kt`：界面、权限、扫描设备选择、内存密码、未知/过期/断线显示。UI按250ms刷新，不持有Gatt。
- `LabService.kt`：前台通知、双设备Hub、成功秤地址持久化、会话快照；页面离开不关闭连接；旋转不重置重连窗口。
- `LabApplication.kt` / `trace-core/TraceStore.kt`：进程唯一日志队列，避免Service快速重启多writer；导出独立于服务，SAF期间停止服务也可完成。Alpha由`MobileApplication`持有自己的实例。
- `device-session/.../WireTrace.kt`：认证01/改密0B整帧脱敏（含XOR），只在coffeeWrite识别；最大512bytes。Driver真实边界调用，观测异常隔离。
- `protocol-core/.../Protocol.kt`：已观察帧长度、BOOKOO ASCII符号与定点单位。
- `device-session/.../GattQueue.kt`：连接代次、串行、超时关闭、取消排队启动。
- `ExtractionController.kt`：去皮确认/样本时效/一次停止/结果未知；本轮修复断线重连后允许显式手动停止、未发出的启动被取消后可凭新idle结算；近期活动帧优先，不能误解锁。

## 控制边界

设备会话仅对HOYI固件1.1.3开放控制；Alpha启动限定三条已采集曲线包或100条经两种秤模式报文校验的工厂曲线；Lab不暴露这些控制入口。其他固件仅看已知帧/Unsupported；其他秤未实现。不要为了UI演示移除门禁。

策略阈值不是厂家认证规格：样本1.5s过期；启动后1.5s请求去皮；去皮后新样本绝对值≤0.5g确认；启动4s未确认则尝试停止；至少7s才允许目标重量停止；通常idle需距最后阀开帧>2.8s才结算。取消确定未发出的start且stop传输成功后，可由新idle结算，前提无活动帧证据。均须实机标定。

回放重量停止17.786s，旧记录17.887s，相差101ms，未证明物理等效。断线或进程被杀无法保证停液，不恢复/重放启动命令。

## 验证与审查

完整命令在README；不能只运行Gradle默认test而漏掉纯Kotlin verify。

- 协议34,991次断言，其中32,856条匿名通知（不是同等数量的独立用例）。
- 会话/策略/回放/trace共32个命名场景通过。
- App JUnit 9项通过（日志顺序/转义/轮转/重开session/IO失败/队列满/关闭/时效与单位）。
- app APK、AndroidTest APK构建通过。App lint 0错误5警告（中文诊断文案未资源化、版本提示）。
- 独立审查确认核心停止边界；独立App范围及两轮生命周期复核已完成。第二位代码质量审查启动后因服务额度中断，不能计为通过；主任务补做最终检查。
- 上述为首轮构建时状态；本轮已通过Wi-Fi ADB安装并运行，实际验证与限制以本文顶部及实机报告为准。

## 下一步

1. 设备接入后按 `docs/native-lab.md` 验证权限、初始化、双连接、旋转/锁屏、断线、日志导出；先只用Lab观察，不开放萃取UI。
   Alpha安装获系统允许后，先做冷启动、曲线选择和只读连接验收，使用`OpenHoyiMobile`标签与已过滤的诊断事件，不为常规状态反复截图。
2. 导出新版连接记录，与旧记录对照；实物核对BOOKOO负重量、单位、时效。
3. 在人工监督下验证停止与去皮，再验证三条曲线；记录最后杯重与时序差异。
4. 证据足够后再接入曲线库、原生萃取页面、设置读回事务与更多秤型号。

## 构建与资料

Java17、SDK35、min26、Gradle8.11.1、Kotlin2.0.21、AGP8.10.0。本机local.properties不提交。Google不可达时 `-PgoogleMirror=aliyun`；全局旧代理通过命令行 `-Dhttp.proxyHost= -Dhttps.proxyHost=` 绕开，不改全局配置。首次补依赖后可offline。

APK：`app/build/outputs/apk/debug/app-debug.apk`。独立包可以直接adb install -r，不用卸载旧App。debug签名为本机调试密钥；未来签名迁移需单独规划，不能承诺任意机器产物可覆盖。

fixture：protocol-core/src/test/resources/notifications.tsv、provenance.json；device-session/src/test/resources/shots.tsv。原始私人日志仍在父目录captures，不提交。scripts/extract_fixtures.py / extract_shots.py可重新提取。

日志最多8×4MiB；队列512项；丢失/错误/淘汰显式统计。sessionId标识日志实例、ownerId标识服务实例、role/generation/token标识传输。突发进程终止可能丢队尾，统计仅当前实例，不代表所有历史的完整性证明。未推送GitHub。
