# 机器与秤后开机的本地连接恢复

- 需求与范围：App 长期开启后，机器和已记住的秤稍后开机时减少无效等待；保留 Settings 就绪门禁、用户意图及萃取安全。Astra 负责安全设计/独立复核，Sol 实现和本地测试。
- 来源分支/基线提交：origin/develop，df108928。
- 工作分支：fix/late-device-recovery。
- PR目标：develop；用户已授权创建 PR，真机后续验收；本轮不合并。
- 改动文件与步骤：设计确认后修改 DeviceSession 的有界同步补取、ReconnectPolicy 的发现后快速恢复与 NativeDeviceHub 接线，补代表性真实日志及 owner/过期/失败/取消测试。
- 推送与合并：遵守 [Git 工作流](../git-workflow.md)，本地完整门禁后普通推送并创建 PR；不合并、不操作设备。
- 验证证据：复用本机 JDK17/Android SDK，运行相关 JVM 测试与必要编译，记录实际结果；真实日志只读本地副本，不提交原始用户日志。
- 协议与设备风险：认证帧有时间同步副作用，不视作纯读取；不以实时帧跳过 Settings，不无限延期、不发控制、不重发未知控制结果；补取需 owner、队列、过期、萃取守护。秤防止广播持续重置快速窗口，保留前台/手动断开/长期离线退避。
- 发布：不发版、不改版本/签名、不安装，不执行 ADB 或任何平板/机器操作，不创建或启动 AVD。设备体验未验。
- 回退：撤回本任务本地代码，不卸载、不清数据、不删除未知记录。
- 未完成与阻塞：局部修复和独立复核已完成，真机连接体验待用户后续验收。

## 本地完成结果

- 已实现：机器一次安全限定的认证/时间同步补取；Settings 仍为唯一就绪证据；原 10 秒仅在补取派发后延至同步起点 +15 秒硬限，实际写回调另受 5 秒期限限制。在途 Settings 保留接收时间并等待补写成功，不允许失败/迟到结果打开就绪门禁。
- 已实现：秤扫描命中与 GATT 结果生命周期拆开；扫描期间旧 FAILED/DISCONNECTED 不抢先结束尝试；首次命中固定至少 30 秒快窗，重复广播不续期；每次尝试起点至少间隔 10 秒，长期等待仍 30 秒。前台/交互不重置快速预算。
- 实际验证：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :device-session:test :bluetooth-android:compileDebugKotlin --console=plain` 最终通过。device-session JUnit 805 项通过（本次 LateDeviceRecoveryTest 9 项），既有 SessionChecks 103 项通过；蓝牙 Android Kotlin 编译通过。`git diff --check` 通过。
- 回放包含真实 21:42:20–23 的四个 0x40 及 0x83 Settings；验证只触发一次补取、不能提前 READY，覆盖过期/迟到回调、旧 owner、取消、失败、萃取/睡眠/告警守护及扫描旧终态轮询。
- Astra 已独立只读复核，并修复其发现的迟到写成功及扫描阶段旧终态两处问题。未发现剩余必修问题。
- 仍未验证：真实硬件、Android 完整交互/后台恢复、平板实际连接体验。慢速扫描仍存在发现等待，不能承诺开机几秒必连。未运行 ADB、安装、设备命令、AVD、完整 lint/全仓库门禁；未提交、推送、创建 PR 或发布。
