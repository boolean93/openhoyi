# Koog 接入兼容性调查（2026-11-14）

本轮按用户“先研究清楚，到底要更新哪些东西”暂停主工程升级。以下是发布制品与实际构建证据，不把上游源码构建工具版本当作消费端要求。

## 结论与候选

| 项目 | 当前主工程 | Koog 0.5.4 保守候选 | Koog 1.3.0 最新候选 |
|---|---|---|---|
| Kotlin JVM / Android 插件 | 2.0.21 | 2.2.10，最小 Android 编译/打包通过 | 2.3.10 |
| Android Gradle Plugin | 8.10.0 | 保持 8.10.0 | 8.13.2，满足 Kotlin 2.3 的 D8/R8 |
| Gradle wrapper | 8.11.1 | 保持 8.11.1 | 8.13，AGP 8.13.2 最低要求 |
| JDK / JVM target | 17 | 保持 17 | 保持 17 |
| minSdk / compileSdk / targetSdk | 26 / 35 / 35 | 保持 | 保持 |
| Compose / KSP / kapt 插件 | 无，原生 View | 无需新增 | 无需新增 |

这不是把旧版本自动定为最终方案。应在确认功能、安全与 APK 体积成本后选择版本；按同一发布系列固定兼容模块版本（如 core 1.3.0 / DeepSeek client 1.3.0-beta），不跳过 metadata 校验。

## 发布制品实测

- 基线 `:mobile:assembleMock` 成功。Mock APK 为 **18,955,082 bytes**，SHA-256 `3da6ccdb541184318dfa1d0cdc867aacf2b535229460b5161901442c2d50346a`。已单独保存，不被后续构建覆盖。尚无最终 APK，不报告虚假的增量。
- Koog `prompt-executor-deepseek-client:1.3.0-beta` AAR metadata 为 minCompileSdk 1 / minAGP 1.0.0，不要求消费工程沿用上游 AGP。但其 Kotlin 2.3 metadata 不能被当前 Kotlin 2.0.21 编译器读取，实际编译失败。
- Koog 0.7.1 的 Maven POM 虽写部分 Kotlin 2.1.21 依赖，实际发布 `agents-core-jvm`、DeepSeek client 等 JAR 的 metadata 为 2.3.0。当前工程实际编译失败，不能按 POM 猜兼容。
- 最后一个旧版候选 0.5.4 的 `agents-core-jvm` `kotlin.Metadata.mv=[2,1,0]`，但传递依赖 stdlib / reflect 2.2.10 的 metadata 为 2.2.0，当前 Kotlin 2.0.21 仍实际编译失败。保守验证选择同版本 Kotlin 2.2.10，而非强行降级标准库或跳过检查。
- 0.5.4 DeepSeek 发布 source JAR 有 additionalProperties 字段和展平 serializer，具备 function tools / toolChoice；这不等于实际请求顶层参数生效，下文实测否定。首版需显式 `thinking.type=disabled`、`safety_check.level=high`，模型 ID 使用当前官方 `deepseek-flash`。不能宣称旧客户端支持 thinking 工具链。

## 影响范围

- 根 `build.gradle.kts` 的 Kotlin JVM/Android 插件联动，影响 protocol-core、device-session、trace-core、bean-core、bluetooth-android、lab app 与 mobile，不只是 AI 文件。
- 当前基线已有 `CustomCurveStore.Deletion` 与 `SafetyNotificationPresentation` 的 data class 非公有构造/copy 可见性迁移警告。升级 Kotlin 后需修正并回归相关业务，不能只改版本号。
- `scripts/shared_mock_artifact.py` 明确校验并记录 Kotlin 2.0.21 / AGP 8.10.0 / Gradle 8.11.1；`scripts/test_shared_mock_artifact.py` 与 `single-screen-ui.yml` 共用此合同。选择升级后需按真实工具版本更新合同和测试，不能删除校验。
- 现有 CI JDK 17 与 Android SDK 35 可保留。新 wrapper 会由 CI 下载；仍运行仓库完整非 AVD verify。已有设备测试/Develop 分发继续按仓库门禁，不把编译通过称为硬件验收。
- `apksig:8.10.0` 是 APK 签名验证库，并非 AGP 插件；没有证据要求它随 AGP 升级，不能顺手改动。
- 单独建 Android AI module 可隔离代码边界，但最终 APK 仍由同一 D8/R8 处理；标准库/反射等传递依赖还会参与全局解析，不能靠“隔离 module”保证保留不兼容的工具链。

## 验证与后续

已在 `/tmp` 独立最小 Android 工程验证候选，不修改主工程的 Kotlin/AGP/wrapper，不安装设备，不访问真实 DeepSeek，不使用真实密钥。测试使用 Ktor MockEngine，驱动真实 Koog AIAgent → 只读工具 → 模型回合，并断言模型与 safety / non-thinking 参数。

隔离旧版样例 `:app:compileDebugKotlin`、`mergeExtDexDebug`、`packageDebug`、`assembleDebug` 真实成功，样例 APK 5,666,566 bytes（不是 OpenHOYI APK，不能作为体积增量）。使用真实 Koog AIAgent 的 fake 模型工具调用→工具结果→最终回答两次请求往返通过。

但 0.5.4 的**必需参数顶层断言失败**：实际请求是 `"additional_properties":{"thinking":{"type":"disabled"},"safety_check":{"level":"high"}}`，而非 DeepSeek 要求的顶层字段。后续通过的往返测试仅明确记录了这一坏行为，不是安全参数测试成功。不能直接采用旧版；若选它还须受测传输修正。

新版 1.3.0 隔离 JVM 样例使用 Kotlin 2.3.10 / JDK 17 / 当前 Gradle 8.11.1，`compileKotlin`、`compileTestKotlin`、`test` 全部通过（1 test、0 failures）。真实 Koog AIAgent 两次 fake 请求 / 一次工具读的往返成功，且两次请求均断言顶层 `thinking.type=disabled`、`safety_check.level=high`、`model=deepseek-flash` 及先前助手回复保留。使用模块为 `agents-core:1.3.0`、`prompt-executor-deepseek-client:1.3.0-beta`、`http-client-ktor:1.3.0`，测试仅 Ktor MockEngine 3.3.3，无真实网络/API费用。

此结果证明新版解决了本次旧版参数问题；**不是新版 Android APK / R8 优化 / OpenHOYI 完整回归已通过**。新版 Android 路径仍须按官方 D8/R8 要求升级 AGP/wrapper并实际打包。选择版本后，才能继续集成、独立安全/体验复核、完整 verify 和同配置前后 APK 字节数/百分比比较。

## 官方依据

- [Kotlin Gradle 插件兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Android Kotlin / D8 / R8 要求](https://developer.android.com/build/kotlin-support)
- [AGP 8.13.2 发布说明及最低 Gradle 8.13 / JDK 17](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Koog 0.5.4 官方 Maven 发布制品](https://repo.maven.apache.org/maven2/ai/koog/agents-core-jvm/0.5.4/)
- [Koog 0.5.4 DeepSeek 官方 Maven 发布制品](https://repo.maven.apache.org/maven2/ai/koog/prompt-executor-deepseek-client-jvm/0.5.4/)
- [DeepSeek 当前 Chat Completion API](https://api-docs.deepseek.com/api/create-chat-completion/)
- [DeepSeek 安全检查要求](https://api-docs.deepseek.com/guides/safety/)
