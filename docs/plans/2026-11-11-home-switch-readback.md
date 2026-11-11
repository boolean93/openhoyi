# 首页锅炉开关回读修复

- 需求与范围：首页发送萃取/蒸汽锅炉开关后，自动取得新的机器设置报告，不以本地预设值冒充成功。仅覆盖 `BrewHeating` / `SteamHeating`。
- 来源分支/基线提交：`origin/develop` / `e7c7ac8a`。
- 工作分支：`fix/home-switch-readback`。
- PR目标：`develop`。
- 改动文件与步骤：DeviceSession 保留会话凭据并提供成功锅炉写后一次性的校时/设置刷新；Service 先进入 WAITING 并启动原 watchdog，再请求刷新；复用持久 owner/token/设备身份和原安全门禁。增加代表性 fake-GATT 与 permit 测试。独立协议证据见 [回读证据](../evidence/2026-11-11-home-boiler-readback.md)。
- 推送与合并：本轮只本地实现/验证，不推送、不创建 PR、不合并。后续如获授权，使用普通分支推送及 `scripts/create-pr`，等待必需 branch-policy/verify。
- 验证证据：基线 SettingsWriteTrackerTest 与 103 session checks 通过；修复后运行 device-session tests 与受影响 mobile/bluetooth 编译及最小回归，实际命令和结果补记于本文件。
- 协议与设备风险：没有已证实的纯读命令。使用原 `0x01` 认证/校时帧（当前时间、同会话密码），会更新机器时钟并返回 `0x83`，不是只读请求。严格限制成功锅炉写后一次，不重发锅炉控制、不重连、不轮询。刷新传输完成不代表设置生效，必须新 `0x83` 匹配。保留 UNKNOWN、watchdog、持久记录与原发送白名单；连接/身份变化取消旧刷新。凭据不输出，断开清除。不进行设备安装/操作，不宣称硬件验收。
- 发布：不发版、不改版本或签名、不安装；无升级边界变化。
- 回退：revert 本次代码，不改变数据结构，不卸载、不清除未知写入记录。
- 未完成与阻塞：实机开关/时间同步/回读时序待授权硬件验收。现有 180 秒设置新鲜度到期问题不在本轮扩为轮询。

规则：[Git工作流](../git-workflow.md)。Astra 负责协议分析与独立审查，Sol 负责实现/测试。

## 本地验证结果

- `./gradlew --offline --no-daemon --console=plain :device-session:test --tests io.openhoyi.session.BoilerSettingsRefreshTest --tests io.openhoyi.session.SettingsWriteTrackerTest --tests io.openhoyi.session.MachineWriteDispatchPermitTest`：通过，5 项新增 fake-GATT/permit 测试、10 项 tracker 测试、48 项原 dispatch permit 测试；附带 103 项 session checks 全通过。覆盖原通知先于锅炉 GATT、新回读先于刷新 GATT、两种锅炉单次校时、失败/无回读、重入/身份变更/旧回调与安全门禁。
- `./gradlew --offline --no-daemon --console=plain :device-session:compileKotlin :bluetooth-android:compileDebugKotlin :mobile:compileDebugKotlin`：通过。
- 最终 `./gradlew --offline --no-daemon --console=plain :device-session:test :bluetooth-android:testDebugUnitTest :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock`：通过（52 秒）。JUnit 共 1,842 项，0 失败、0 错误、2 项既有跳过；device-session 750、Bluetooth 5、Mobile Debug 540、Mock 547；103 项 session checks 全通过。Debug/Mock APK 构建成功但未安装。
- `git diff --check`：通过。Astra 已只读独立复核最终代码及 5 项新增测试，无阻断；既有 opcode01 WireTrace 脱敏规则继续生效，未修改出站协议白名单。
- 未运行实体设备、界面 instrumentation/AVD、lint 或完整 PR verify；无推送、PR、发布、安装或机器操作。上述结果不等于实机开关、时钟或睡眠日程验收；后续创建 PR 前仍须使用仓库统一本地门禁。
