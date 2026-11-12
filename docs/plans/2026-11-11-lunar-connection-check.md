# Lunar 连接链路离线检查

> 历史阶段记录：此处描述当时的最小 length8/12 修复。后续组合记录补齐与当前验证范围见 [最新任务](2026-11-12-acaia-protocol-completion.md)，旧文字不代表本轮最终解析范围。

- 需求与范围：检查最新代码中 Lunar 可扫描但未就绪的可能原因。先新增代表性离线链路测试，确定组合包解析失败后进行最小修复。用户新增官方 SDK 对照要求后曾暂停；完成可读官方示例/许可对照后，协调方已决定继续完成已复现缺陷的最小修复及独立回归复核，不推定现场根因。
- 来源分支/基线提交：`origin/develop` / `e7c7ac8aab0cd1accc661c91a6f664dfa0e8a312`；2026-11-11 fetch 后仍为此提交。
- 工作分支：`fix/lunar-connection-check`；PR 目标如后续获准为 `develop`。
- 分工：Sol 补测试并执行；Astra 独立核查证据及关键路径。当前现场 APK、Lunar 型号、固件和 GATT 日志均未确认。
- 改动文件与步骤：新增 `LunarConnectionPathTest`，由真实广播名分类器、持久协议注册表、会话/GATT 队列、初始化调度及解码器串起 AP 路径。覆盖正常状态+重量、仅重量同步失败，以及分片/坏包撤销单位后恢复。组合事件失败复现后，`LegacyLiveScaleCodec.kt` 仅增加 `length=12 && timer type=7` 的 17 字节布局，沿用原六字节重量和整帧 checksum；既有 codec 自检增加组合包 golden、分片粘包及错误 tag/长度/checksum 回归。没有放开任意尾部、变更端点、初始化或控制权限。
- 推送与合并：本轮不提交、不推送、不创建 PR、不合并；若后续授权，遵守 [Git 工作流](../git-workflow.md) 与 `scripts/create-pr` 本地门禁。
- 验证证据：`JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew --offline --no-daemon --console=plain :bluetooth-android:testDebugUnitTest --tests io.openhoyi.bluetooth.LunarConnectionPathTest` 实际运行 4 项，3 通过、1 失败；详见下节。`git diff --check`、`python3 scripts/git_policy.py branch` 通过。未跑设备或模拟器。
- 协议与设备风险：测试使用既有协议 golden 和精确 AP 端点模拟 GATT 回调，不连接实秤，不改变单位门禁、重试、控制权限或未知记录。旧 `1820/2a80`、Umbra `FE40/41/42` 均不视为用户当前 Lunar 的已证端点。
- 发布与签名升级边界：不发版、不安装、不启动设备或模拟器，不改包名、签名、版本、数据 schema。
- 回退：如后续决定回退，仅还原本任务 codec 两处变更并删除本轮新增测试和计划；不涉及用户数据或设备状态，不卸载、不清记录。本轮不提交、不推送、不创建 PR、不发布。
- 未完成与阻塞：本地最小修复与指定离线回归已完成，独立 Astra 终审通过、无阻断项。Mac 曾短暂断线，现已恢复；离线通过不代表硬件连接验收，也不证明现场故障原因。

## 首轮真实产品链路复现（产品源码未修改）

- 正常 AP 路径通过：广播 `LUNAR 123456` 经真实分类器和注册表选择 AP，精确 GATT 回调后，以 100ms tick 调度实际会话队列；三条初始化写在 2500/5000/5500ms 派发。状态包只确认克、不产生读数；最后初始化写完成后第 4999ms 的有效重量仍可 READY，未发现初始化占用同步窗口的问题。
- 仅重量无 status 通过预期保护：初始化结束后普通重量不产生读数，10500ms（初始化完成后 5 秒）进入 FAILED；初始化与单个心跳外无额外写入。
- 分片/损坏帧恢复通过：分片状态和重量可组帧；坏 checksum 撤销克信任，普通重量无法恢复，新克 status+weight 可恢复。
- **基线组合包失败已确定复现**：`EFDD0C0C05E80000000100070005030108` 是 payload length=12、含 23.2g 六字节重量和 timer type=7 的 17 字节完整帧，交替 payload checksum 为 `01/08`。未修复基线解码器返回 `Invalid(reason=Acaia weight event must have six data bytes)`；实际会话清除克状态，随后普通重量仍 SYNCHRONIZING、零观察值，重新 status+weight 才 READY。新增回归期望组合包产出 2320 百分之一克，首轮 4 项中仅此项失败。
- 独立审查提供的固定协议证据：[AcaiaArduinoBLE 80e03977](https://github.com/tatemazer/AcaiaArduinoBLE/commit/80e03977a23fa8ecab340d0e69859f189e34768d)（2024-05-21）记录 Lunar 2021 FW1.0.016 的重量包 13→17 字节；另有 [acaia-lunar-ble eea7e7c0](https://github.com/beat843796/acaia-lunar-ble/tree/eea7e7c0b68bc4fc8070817f0c09809246e6d74b) 组合重量/计时布局。此格式兼容缺陷不等于用户现场已确认使用该型号、固件或包型。
- 原始 HOYI1.2.6 APK 的独立复核由协调方执行：SHA-256 `26133c2ac8ecf3d5ee7682bafa6555b1c78836749c937477b05214a88d8ea238`，`app-service.js` offset 1108339 的 type12/type5 分支读取重量首六字节，不限制整个事件恰为 13 字节；AP 端点/初始化与当前实现一致。这里保留来源和验证责任，不冒称本测试执行者重新读取了 APK。

## 最小修复离线结果及官方 SDK 对照结论

- 在产品修改暂停前已启动、随后收取的命令：`JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew --offline --no-daemon --console=plain :protocol-core:verify :device-session:verify :bluetooth-android:testDebugUnitTest --tests io.openhoyi.bluetooth.LunarConnectionPathTest`，退出 0，`BUILD SUCCESSFUL in 31s`。协议 39,823 项检查、32,856 条既有通知重放、会话 103 项自检与新增 Lunar 链路 4 项全部通过。`device-session:verify` 不是整个 JUnit suite；未宣称运行 `:device-session:test` 或完整 Android 构建。
- 修复确认组合包产生 2320 百分之一克，后续普通重量无需重发 status 即可保持 READY；既有无 status 用例也加入组合包，仍无读数并在同步期限失败。坏包、未知布局及单位门禁没有放宽。
- 用户新增官方 [acaia/acaia_sdk_android](https://github.com/acaia/acaia_sdk_android/tree/8dc977e8123eb5e13b5370b3f357e6af082c79e0) 对照后曾暂停实现；官方仓库只公开示例 API 和 AAR，未公开底层 wire 解析源码，不能用示例的高层连接/重量事件推断底层解析完全一致。许可明确禁止对二进制反向工程/解码，本轮未下载、反编译或运行 AAR。协调方完成对照后已决定继续完成这个离线已复现缺陷的最小修复与独立复核，不迁移 SDK、不扩大范围。**上述 17 字节布局依据为固定上游实测报告、社区布局及旧 HOYI 字节协议，不是 SDK 官方 wire 证据；现场 APK、型号、固件及失败原因仍未知。**
- 最终状态：解除官方 SDK 对照待决策状态，保留最小修复，指定离线回归通过，独立 Astra 对最终差异终审通过、无阻断项；不 commit/push/PR/发布。终审确认范围限于第三方已记录的 17 字节布局、构造 fixture 与离线真实产品链路，不替代官方 wire 证明或实秤验收；不把该缺陷断言为用户现场根因。官方对照与许可边界另见 [官方 SDK 比较证据](../evidence/2026-11-11-acaia-sdk-comparison.md)。此后只调整产品/测试注释和本计划，不影响代码行为，未重复运行逻辑测试。
- 最终 Lunar JUnit 报告：`bluetooth-android/build/test-results/testDebugUnitTest/TEST-io.openhoyi.bluetooth.LunarConnectionPathTest.xml`（4 tests / 0 failures / 0 errors），HTML 在 `bluetooth-android/build/reports/tests/testDebugUnitTest/index.html`。协议/会话 verify 结果来自本轮 Gradle 实际终端输出；运行汇总保留于 `/tmp/openhoyi-lunar-connection-verification-2026-11-11.txt`，不是原始完整终端日志。
