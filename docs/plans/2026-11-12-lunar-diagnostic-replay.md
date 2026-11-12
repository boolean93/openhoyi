# Lunar 用户诊断回放修复

> 历史阶段记录：此处描述当时的最小 length8/12 修复。后续组合记录补齐与当前验证范围见 [最新任务](2026-11-12-acaia-protocol-completion.md)，旧文字不代表本轮最终解析范围。

- 需求与范围：分析用户提供的 Lunar 诊断并验证既有最小解码修复；不改变握手、心跳、单位确认、设备控制权限。
- 来源/目标：develop@7fbfcbea69da126b2361c48fcf88e1d6acf80b07，工作分支 fix/lunar-diagnostic-replay，后续 PR 目标 develop。本轮不 commit/push/PR/合并。
- 迁移：从旧 fix/lunar-connection-check 只读复制已有 codec 最小修复、codec 和连接链路测试及历史证据。旧工作树保持原样。新增真实 status、分包及变化重量回归。
- 分工：实现者补现场回归；Astra 独立只读复核确认该限制正是日志拒包原因，不需扩大协议范围。
- 协议风险：仍只允许 length8 或 length12且timer tag7；保留 checksum、克单位确认、坏包失效。不开放 Lunar/AP 去皮、自动去皮或目标液重控制。
- 发布/升级：本轮不发包、不安装、不操作硬件；无签名、包名、版本、数据格式变化。
- 回退：仅回退本次 codec 和测试；不清除用户数据或未确认记录。

## 现场证据

诊断应用 0.1.0-lunar-diag.1，baseSourceSha=e7c7ac8aab0cd1accc661c91a6f664dfa0e8a312，Android API36，真实 BLE，非 Mock。ZIP SHA256=e932386f56dc86521b046cf31aa18c25aa8a8c0c22885c983fef6337c784259d。原始用户日志不入库，测试仅留无设备标识的协议字节。

202条事件覆盖两轮连接，32.707秒。每轮 connect/discover/subscribe/初始化write均status0；共53次通知，其中44次decode_invalid，9次decode_unknown（含状态及待拼分包，不能据unknown推断状态未处理）。两轮均进入SYNCHRONIZING后约5秒超时，由应用关闭连接；未出现READY。

- status `efdd080929020301000101010e2d` 在 identify 后、subscribe 初始化完成前收到，unit=2；第二轮 status 同为unit2。
- 重量+timer `efdd0c0c05000000000100070000021407` 校验正确、length12、timer tag7。基线只允许length8，因此拒绝并清除克信任，持续重量也无法就绪。
- 实际存在 `efdd0c0c` 与 `05000000000100070000021407` 两次通知拼帧；不是丢包证明。
- 真实变化读数使用既有解析比例得到3.2、351.0、565.1、259.5、0.6g；这里只验证协议解析结果，不证明现场砝码精度。

## 验证

见本轮测试结果补充。代码回放通过仍不等于用户实秤验证；修复包需用户验证连接和连续读数。本次不声称官方SDK内部wire实现，不引入/反编译SDK AAR。

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :protocol-core:test :device-session:test --console=plain`：通过，33秒；协议39,827项检查及32,856通知回放，会话103自检，device-session JUnit 773 tests / 0 failures / 0 errors。
- `./gradlew --offline :bluetooth-android:testDebugUnitTest --tests io.openhoyi.bluetooth.LunarConnectionPathTest --console=plain`（同JAVA_HOME/ANDROID_HOME）：通过，8秒，4 tests / 0 failures / 0 errors。
- `git diff --check`、`python3 scripts/git_policy.py branch`：通过。
- Astra 只读审核：真实status确认克、组合帧checksum与timer tag正确；修复覆盖本次拒包原因。未实秤验收，不扩大去皮、计时控制、自动萃取能力。
- 原始运行日志本机 `/tmp/lunar-replay-tests.log`、`/tmp/lunar-path-tests.log`；JUnit报告位于各模块 `build/test-results`。未运行模拟器或实际蓝牙写入。
