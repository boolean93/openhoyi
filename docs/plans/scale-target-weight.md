# Felicita / DiFluid 目标液重联动修复

- 需求与范围：当前可写 Felicita、DiFluid EE/DD 设置目标液重后可开始并到重停；灰色开始可点，仅短 toast 解释保护原因，不绕过门禁。旧只读 Felicita、其它 documented 秤不开放。
- 来源分支/基线提交：origin/develop `124ab67f`。
- 工作分支：`fix/scale-target-weight`；PR 目标 develop。
- 改动文件与步骤：protocol-core 新增明确协议支持能力（不提升 VERIFIED）；device-session 统一目标资格与 preflight/dispatch；NativeDeviceHub 读数失效立即保护；mobile 共用灰态原因和点击拦截、双语能力文案；代表性回归测试。
- 推送与合并：用户已授权完成后创建 PR 并合入 develop。完成必要本地检查和独立审查后，由协调线程按 [Git工作流](../git-workflow.md) 普通推送，通过 `scripts/create-pr` 建 PR，必需检查通过后合入；不直推受保护分支。
- 验证证据：相关单测后运行 `:protocol-core:check :device-session:check`、Android unit/build 与 localization 校验。横竖屏共用无省略按钮文字与短 toast；未授权模拟器或实机界面验收。
- 协议与设备风险：依据旧官方客户端明确 tare 帧与通用 7 秒后到重停止流程；能力称为 protocol-supported，不冒称硬件验证。保持归零新样本、freshness、单位、失联、未知结果不重发保护；automaticTare 不开放。不提升旧只读绑定，不改 bean capture VERIFIED 要求。
- 发布：本任务不做正式/RC 发版，合入后的 Develop 既有自动测试预发布通道沿用现有持续授权；不改包名/签名/版本，不安装、不启动模拟器或真实萃取。单测与静态证据不等于硬件验收。
- 回退：回退本任务代码，不涉及数据迁移，不卸载、不清记录，保留未知结果。
- 未完成与阻塞：两品牌实秤与物理停水、横竖屏实机验收待明确授权与设备。

## 实现与证据

- `protocolSupportedWeightControl` 仅新可写 Felicita、DiFluid EE/DD 开启；独立的 `eligibleForTargetWeight` 不改变既有 VERIFIED 控制/称豆资格。各类只读/离线能力与 Mock 入口仍拒绝控制。
- 显式 tare 写成功后必须再收到新鲜零点；5 秒归零超时、1500 ms 新鲜度、7 秒后目标停机、失联/非法单位保护及未知写入不重发均保留。丢失 READY（含单位失效后的 SYNCHRONIZING）立即撤销控制输入。
- 灰色开始可点击，仅 toast 当前原因；双页面共享服务侧只读原因，实际发送仍保留原有二次门禁。温度预热条件独立于开始阻挡，避免未达温时无法准备。原有无秤按水量结束的确认流程不变。
- 官方客户端 1.2.4 / 1.2.6 的 `startRelase`、`encodeTare` 与到重停止片段隔离执行，共 28 断言通过：两品牌 36 g 启动无品牌限制、Felicita `54`、DiFluid `DFDF03020101C5`、归零/7 秒/目标判断。仅 fake 派发，不是完整 APK 执行或实机验收；不照搬其定时假设归零。
- 原始 `app-service.js` SHA256：1.2.4 `70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1`；1.2.6 `4ed90ea99240b7f37373cdb5c9df0bf315103ca7aea6aaab6f37b2c58dd564d7`。

## 本地验证结果

- Java 17、现有 Android SDK 下运行 `./gradlew :protocol-core:check :device-session:check :bluetooth-android:testDebugUnitTest :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:compileMockAndroidTestKotlin :mobile:lintDebug --console=plain`：成功。device-session 734 项 JUnit / 103 项会话检查；mobile Debug 522 项、Mock 529 项，各 0 失败和 1 项既有跳过。
- `ProtocolTargetWeightTest` 以实际解码/会话/去皮和 fake GATT/coffee 验证三 profile 的启动与到重停、写失败/未知/无零点不启、单位/坏帧失效保护、失联/过期、DiFluid 本连接克单位确认。既有 queued dispatch 与零目标回归保留。
- `python3 -m unittest discover -s localization/tests`（25 项）、`python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`、`git diff --check`：通过。
- `localization/verify_apk_resources.py` 使用 build-tools 35.0.0 / android-35 检查两个 APK：每包 2951 项资源匹配，6 项转义往返通过。visual 契约 shell/嵌入 Python 语法检查通过；仅编译 AndroidTest，未运行界面/模拟器。
- 已修复验证期间发现的旧文案断言和测试类型推断错误。准备/温度门禁由独立审查指出后改为 start-only，避免阻挡预热；最终差异仍需 PR 必需检查。Astra 最终独立复核无剩余阻断，29 项定向 JUnit 与 103 项会话检查通过，含最后新增的无自动去皮写入断言；双 UI 点击/门禁/预热路径独立源断言通过。
- 未验证：Felicita / DiFluid 实秤通信及物理停水、真实设备横竖屏显示。已有证据不宣称这些验收完成。
