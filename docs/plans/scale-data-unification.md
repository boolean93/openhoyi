# 电子秤业务观测单一来源

- 需求与范围：全生产链路以 `ScaleObservation` 为唯一电子秤读数，消除非 BOOKOO 实时显示、采样与终重遗漏；Lab 扫描连接保持协议身份。保留各协议编解码和安全控制门禁。
- 来源分支/基线提交：`develop` / `31890d58`；继承已本地摘取的 `bcea5f8b`（新鲜度修复）。
- 工作分支：`fix/scale-data-unification`；未来 PR 目标：`develop`。
- 改动文件与步骤：移除产品 `MobileSnapshot.weight/weightAt` 与业务 `LiveTelemetry(BookooSample)`；Mock 直接生产通用观测；界面、趋势、采样和两类结束历史统一读取有效新鲜 observation。Lab 用扫描所得 protocolId 连接并展示通用观测。移除 Session → AndroidDevice → Hub 的旧 BOOKOO 读数回调，仅保留协议编解码。补实际适配器通知到业务及历史/CSV回归和静态架构约束。
- 后续授权与交付：起初仅授权本地实现和验证；用户随后明确授权“可以带着前面这些所有的修复创建 pr 并合并”。本实现阶段仍先冻结给协调者独立审查，按协调者统一的提交、更新基线和远程操作顺序执行；目前未提交、推送、建 PR 或合并。
- 扩展确认范围：候选秤能力文案依据 adapter 能力区分目标液重；只读秤明确称豆限制；保守控制能力默认值；重复 GATT 原始 endpoint 和 Android GATT service/characteristic 实例拒绝猜选；关闭不确定时 transport sticky fail-closed 并向用户说明阻断；Mock 合成读数不冒充已验证证据，普通 Mock 趋势与可视测试输入接管清窗；M6 host 快照严格 schema 同步升级至 2。没有新增品牌控制权限或 READY 自动断连。
- 验证证据：`./gradlew --no-daemon --console=plain :device-session:check :mobile:testDebugUnitTest :mobile:testMockUnitTest :app:testDebugUnitTest :mobile:compileMockAndroidTestKotlin` 通过（105 tasks；device-session 740、mobile Debug 533、mobile Mock 540、app Debug 9 个 JVM 用例，两个 mobile suite 各 1 skip）。`scripts/verify-pr-checks full` 通过（191 Gradle tasks，含 JVM、assemble、lint、资源验证及发布配置隔离 fixture，无安装）。末差后重跑 `./gradlew --no-daemon --console=plain :mobile:compileMockAndroidTestKotlin` 通过（47 tasks），仅编译、未运行 instrumentation。`python3 -m unittest scripts.test_scale_architecture -v` 两例通过，已接入 full gate；`python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`、`python3 scripts/git_policy.py branch`、`git diff --check` 通过。
- 协议与设备风险：不放宽证据、能力、新鲜度、去皮确认、断连停机及未知写入不得重试。`BeanDoseCapturePolicy` 的 VERIFIED 证据限制保持不变，非 VERIFIED 秤不宣称自动豆量捕获可用。内部 `DeviceRole.BOOKOO` 涉兼容串，暂保留，仅用户可见称谓通用化。
- 发布：不发版、不打标签、不构建分发 APK。包名、签名身份、versionCode、保留数据及升级边界不变；CI 和单测不构成硬件验收。
- 回退：回退本地代码/未来 PR，不清历史、未知写入记录或设备数据；不回填旧历史。
- 未完成与阻塞：未运行 Android instrumentation 或真实硬件、安装/升级验收；这些结果不能由编译、Fake GATT 适配器回归或 Mock 测试替代。后续还需按协调者指示在 #55/#56 合入后的最新 develop 整合并验证 customCurveStartPermit 链路及两项 CustomFlow 测试。
