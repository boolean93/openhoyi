# 电子秤业务观测单一来源

- 需求与范围：全生产链路以 `ScaleObservation` 为唯一电子秤读数，消除非 BOOKOO 实时显示、采样与终重遗漏；Lab 扫描连接保持协议身份。保留各协议编解码和安全控制门禁。
- 来源分支/基线提交：最初从 `develop@31890d58` 开始并本地摘取新鲜度修复 `bcea5f8b`。现已排除该摘取提交，将本轮审计修复移到 `origin/develop@c925e137`（已含 #56 与 #55）；与 #55 合并前的审查基线 `dba8428a` 树哈希同为 `34009be4`，本轮 PR 差异不重复此前修复。
- 工作分支：`fix/scale-data-unification`；PR 目标：`develop`。
- 改动文件与步骤：移除产品 `MobileSnapshot.weight/weightAt` 与业务 `LiveTelemetry(BookooSample)`；Mock 直接生产通用观测；界面、趋势、采样和两类结束历史统一读取有效新鲜 observation。Lab 用扫描所得 protocolId 连接并展示通用观测。移除 Session → AndroidDevice → Hub 的旧 BOOKOO 读数回调，仅保留协议编解码。补实际适配器通知到业务及历史/CSV回归和静态架构约束。
- 后续授权与交付：起初仅授权本地实现和验证；用户随后明确授权“可以带着前面这些所有的修复创建 pr 并合并”。本轮修复已提交并经过两轮独立业务及 transport 审查，无剩余代码阻断；后续按仓库 `scripts/create-pr` 完整门禁、精确 HEAD 必需 CI 与正常 squash 合并，不绕过保护。
- 扩展确认范围：候选秤能力文案依据 adapter 能力区分目标液重；只读秤明确称豆限制；保守控制能力默认值；重复 GATT 原始 endpoint 和 Android GATT service/characteristic 实例拒绝猜选；关闭不确定时 transport sticky fail-closed 并向用户说明阻断；Mock 合成读数不冒充已验证证据，普通 Mock 趋势与可视测试输入接管清窗；M6 host 快照严格 schema 同步升级至 2。没有新增品牌控制权限或 READY 自动断连。
- 验证证据：审查基线 `dba8428a` 与当前 `c925e137` 的树相同。在旧审查基线，`./gradlew --no-daemon --console=plain :device-session:check :mobile:testDebugUnitTest :mobile:testMockUnitTest :app:testDebugUnitTest :mobile:compileMockAndroidTestKotlin` 通过（105 tasks；device-session 740、mobile Debug 533、mobile Mock 540、app Debug 9 个 JVM 用例，两个 mobile suite 各 1 skip）；`scripts/verify-pr-checks full` 通过（191 Gradle tasks，含 JVM、assemble、lint、资源验证及发布配置隔离 fixture，无安装）。末差后 `:mobile:compileMockAndroidTestKotlin` 通过（47 tasks），仅编译、未运行 instrumentation。移到 `dba8428a` 后针对交叉代码重跑 `CustomFlowStartPermitTest` 5、`CustomFlowCurveTest` 7、`ScaleBusinessPipelineTest` 2、`ShotGateScaleObservationTest` 8，均通过，且 bluetooth-android/app 编译与 mock androidTest 编译通过（91 tasks）。`python3 -m unittest scripts.test_scale_architecture -v` 两例通过并已接入 full gate；localization 校验、`python3 scripts/git_policy.py branch` 与 diff whitespace 检查通过。最终 PR 入口仍需在精确 `c925e137` 基线上再跑完整门禁。
- 协议与设备风险：不放宽证据、能力、新鲜度、去皮确认、断连停机及未知写入不得重试。`BeanDoseCapturePolicy` 的 VERIFIED 证据限制保持不变，非 VERIFIED 秤不宣称自动豆量捕获可用。内部 `DeviceRole.BOOKOO` 涉兼容串，暂保留，仅用户可见称谓通用化。
- 发布：不发版、不打标签；仅构建本地验证产物和隔离签名 fixture，不分发、不安装、不手动发布。包名、签名身份、versionCode、保留数据及升级边界不变；CI 和单测不构成硬件验收。
- 回退：通过新 revert PR 回退本轮代码，不清历史、未知写入记录或设备数据；不回填旧历史。
- 未完成与阻塞：未运行 Android instrumentation 或真实硬件、安装/升级验收；这些结果不能由编译、Fake GATT 适配器回归或 Mock 测试替代。#56 的 `customCurveStartPermit` 在 MobileService → NativeDeviceHub → AndroidDevice → DeviceSession 全链保留，并已复核固件与完整参数许可，不冒称实机验证。
