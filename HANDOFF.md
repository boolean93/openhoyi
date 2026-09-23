# Native BLE / Lab handoff

分支 `feature/native-ble`，worktree `../openhoyi-native`，基于独立openhoyi仓库。旧hoyi-project未改动。前一阶段核心提交363cea5；本轮新增原生Lab诊断APK。

## 最新实机进展（2026-09-23）

详见 `docs/validation-2026-09-23.md`。独立APK已安装，咖啡机认证、设置和实时遥测已确认，固件1.1.3；BOOKOO四条初始化写入、Ready及并行遥测已确认。08:54系统GATT Map发现旧版持有咖啡机连接。用户确认无操作并授权后，暂停旧版；再次扫描发现两台设备，原生Lab重新双连。08:56～09:03独占对照中，咖啡机/秤持续约6分钟，累计385/3792条通知；切后台及锁屏各约1分钟仍收数，无自然断线。记忆秤随咖啡机Ready自动连接已实测；手动重复点击秤会造成一次冗余重连，待改。旧版目前保持停止，Lab保持连接。

前一晚两次BLE `status=8`断线仍未定因，独占对照只表明短时稳定。08:44仪器测试曾重启Lab并中断连接；后续必须用`scripts/run_lab_smoke.sh`检查服务不存在，不能在活跃连接上直接运行测试。实机仪器测试已验证Activity重建、前后台服务、服务停止后进程内ZIP导出。负重量、异常断线后的重试、SAF选择器、长期BLE和萃取仍未验收。认证未确认时只接收遥测也不Ready的回归已加入，会话场景增至33。

## 当前交付

五模块：protocol-core → device-session → bluetooth-android → app（Lab）/mobile（Alpha）。纯Kotlin模块无Android依赖；两款App均为原生View、Activity、本地Binder和connectedDevice前台Service，没有UniApp/JS/WebView。独立包 `io.openhoyi.lab` 和 `io.openhoyi.mobile`，均不覆盖旧App。

`mobile` 第一段原生日常版已完成首页连接、实时状态和三条采集曲线浏览/选择；见 `docs/mobile-alpha.md`。`mobile:testDebugUnitTest`、`mobile:assembleDebug`、`mobile:lintDebug` 均通过。平板两次拒绝安装新包（`INSTALL_FAILED_USER_RESTRICTED`），因此尚无Alpha的实机启动证据；APK位于`mobile/build/outputs/apk/debug/mobile-debug.apk`。此包未开放萃取控制，不能当成完整替代App。

Lab仅提供授权/扫描/手动连接/断开/实时数据/日志导出/停止服务。没有萃取、设置、校准、OTA按钮。咖啡机连接必须输入6位密码（数字字节0..9），认证并同步时间；BOOKOO按500ms间隔执行4条初始化写入，收到之后的新样本才Ready。连接并非完全只读，以上初始化是明确例外。

- `app/.../LabActivity.kt`：界面、权限、扫描设备选择、内存密码、未知/过期/断线显示。UI按250ms刷新，不持有Gatt。
- `LabService.kt`：前台通知、双设备Hub、成功秤地址持久化、会话快照；页面离开不关闭连接；旋转不重置重连窗口。
- `LabApplication.kt` / `TraceStore.kt`：进程唯一日志队列，避免Service快速重启多writer；导出独立于服务，SAF期间停止服务也可完成。
- `device-session/.../WireTrace.kt`：认证01/改密0B整帧脱敏（含XOR），只在coffeeWrite识别；最大512bytes。Driver真实边界调用，观测异常隔离。
- `protocol-core/.../Protocol.kt`：已观察帧长度、BOOKOO ASCII符号与定点单位。
- `device-session/.../GattQueue.kt`：连接代次、串行、超时关闭、取消排队启动。
- `ExtractionController.kt`：去皮确认/样本时效/一次停止/结果未知；本轮修复断线重连后允许显式手动停止、未发出的启动被取消后可凭新idle结算；近期活动帧优先，不能误解锁。

## 控制边界

设备会话仅对HOYI固件1.1.3开放控制，且启动限定三条已采集曲线包；Lab不暴露这些控制入口。其他固件仅看已知帧/Unsupported；其他秤未实现。不要为了UI演示移除门禁。

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
