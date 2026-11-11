# 首页锅炉开关回读修复

> 以下首轮规划及“本地验证结果”为历史记录，随首次提交46306f07冻结。用户后续授权的第二轮有界补同步/重连、直接点击、安装与只读检查取代首轮“不重连/不安装”的边界，当前有效方案见文末第二轮章节。仍不发布、不推送、不代用户控制锅炉或萃取。

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

## 平板反馈后的第二轮（2026-11-11）

用户反馈重复开关后出现结果未知、体验不流畅，并明确要求“点击按钮不用弹窗确认”。本轮继续同分支，不自动安装或操作机器；Astra 确认协议方向前不改业务门禁。

实际 `trace-00000000000000000081.jsonl`：前三次（萃取关、蒸汽关、萃取开）均取得新主设置回报并确认。第四次蒸汽开（UTC 13:49:20.770，seq103）控制 GATT 成功，seq107/108/109 的认证/校时刷新同样 request/accepted/completed status=0，但此后只有持续 `0x40`，没有主设置或睡眠分片；六秒后 seq116 进入 UNKNOWN。刷新时最新 idle 约 121ms，非 1500ms 新鲜度拒绝，也无连接代次/owner变化。底层为何未处理或丢失回复尚不能由 GATT 日志定论，不把猜测当实证。

- UI 已按明确要求调整：锅炉直接单击，仍经原编辑/Service门禁；执行中仅对应按钮显示“开启中/关闭中”，未确认保留“结果未知”，不改实际回读值；移除独立可伸缩的等待提示行。
- `HomeBoilerSwitchPresentationTest` 两项通过；Debug Kotlin编译通过；中英文catalog/Android资源同步检查及 `git diff --check` 通过。
- 协议后续设计待 Astra 确认。不得以设备传输成功等同生效，不自动重复锅炉指令，不清除未知持久记录。


### 第二轮方向与交付进展

用户进一步要求有界补同步/必要时重连，并授权“修好后安装”“可以先安装，再检查”。Astra确认：250/1250ms最多两次校时同步，原6秒写回读截止；约3秒仅原机器重连一次，原成功写起18秒封顶并抑制普通自动重连循环；typed锅炉在已派发同步后的新83不符目标时可对账闭合原owner，不假成功；未知时保留显式单次同步恢复。其他控制、未知记录与永久自动连接偏好不放宽。实际设计/故障证据及安装边界详见证据文档。

关键51项JUnit和103项session checks、v80 Debug APK编译、localization校验通过；Astra快审无阻断后已同版本同签名覆盖真实BLE包，未安装Mock。APK SHA-256为 `27b67601adeae0419ede2e4e65a1e7f721d017ef052f4b81050c3661afc4b7d0`。70个稳定业务文件不变，trace追加及devices活动标记变化如实保留。只读启动确认新83/40与实时读数，未代点锅炉/清提示；旧泛化SETTING记录仍需用户显式核对，不能直接试点锅炉。完整相关模块回归安装后进行，结果另补。

### 第二轮最终收尾

- 相关四模块全部回归：1855项JUnit，0失败、0错误、2既有跳过；103 session checks全过（59秒）。三个旧资源数量快照改为990/989，逐键/格式验证保留。
- 原owner typed锅炉WRITING/WAITING也只显示局部进度；第3项presentation范围测试通过，Astra复核通过。最终v80 Debug构建7秒成功。
- 最后实装APK SHA-256：`aca64b8b37e5c2c85bf7e41e973f6e38eee91c260ba422977dce87a28afaabd1`，同原签名、同v80覆盖，安装后拉回一致。69稳定业务文件不变，devices XML仅活动时间更新。该覆盖发生在“暂停安装”新指示到达前，随后立即停止进一步启动/截图，无再次安装。
- trace84已获用户六次新事务全部首同步确认的真实报告；旧提示由用户自己核对清除。另发现初始认证83早于GATT callback 1ms的独立竞态，已报告，不混入本轮未经设计修改。
- 不推送、不建PR、不发版。后续休眠/萃取去普通确认另commit处理，继续保留控制安全、温度不足分支及停止方式提示。
