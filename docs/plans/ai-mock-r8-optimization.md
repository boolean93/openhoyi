# Opt-in Mock R8 candidate

- 需求与范围：在相同源码、版本、签名、资源下对比普通 Mock 和显式优化 Mock；不删字体、字形或功能。先修卡片构造崩溃，优化候选不自动安装。
- 来源/工作分支：既有 develop 派生 `feature/ai-chat`，基线 `599670aab75d94070bf836cb813239e30e3e46e7`；保留当前所有未提交实现。目标为 develop，但本轮不推送、不 PR、不合入、不发版。
- 文件/接口：`mobile/build.gradle.kts` 的 `-PhoyiOptimizeMock=true`（严格 true/false，默认 false）；`mobile/proguard-mock-optimize.pro`。不改变 Debug/Release 默认，不允许与 update fixture 混用。
- 优化候选单独设 `isDebuggable=false`，避免继承 debug 的 R8 调试模式限制；仍是相同 Mock 包/签名及 MOCK_MODE。不能使用 run-as 读取用户数据。Mock 诊断导出按 MOCK_MODE 而非 BuildConfig.DEBUG，代码盘点未发现 AI 入口依赖 DEBUG；实际预览可达性须由 APK Manifest 与候选运行核实，不为测试扩大 exported/权限。
- 严格同条件基线：`-PhoyiMockOptimizationControl=true` 默认 false、仅接受 true/false，与 optimizeMock/updateChecks 互斥；仅 Mock 设 non-debuggable、minify=false、shrinkResources=false。真正体积比较用此 control 对照 optimized（同版本/签名/源码/资源），唯一处理差异为 R8。默认 debuggable Mock 只作历史参考，不作为严格体积基线。两个开关均省略时默认 Mock 完全不变。
- 官方依据：AGP 8.13.2 使用 [R8 legacy DSL](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization/) 的 minify/shrinkResources 与 `proguard-android-optimize.txt`；[serialization 官方说明](https://kotlinlang.org/docs/serialization-get-started.html#configure-r8-for-kotlin-serialization-in-android-projects) 和本地 1.10.0 JAR 均确认自带 consumer rules。
- 盘点：生产源码无 Class.forName/ServiceLoader/JNI；getIdentifier 仅查 android 系统资源。Koog tools 用显式 ToolDescriptor 及 typeToken<JsonObject/String>，无注解工具发现。应用无 @Serializable 数据类，持久化采用显式 JSON。SLF4J nop 2.0.17 JAR 的 META-INF/services 指向 NOPServiceProvider，因此保留该闭集 provider。保留 Signature/注解/嵌套类元数据；Manifest/资源入口依赖 AGP 生成规则，不粗暴 keep 整个 app。
- 验证：由唯一 Gradle owner 串行构建 control/优化 `assembleMock`，同一版本和签名参数，分别仅传对应开关。先保存 control APK 再构建优化包，禁止误用被覆盖的产物。检查合并 configuration.txt 未含 dontoptimize/dontobfuscate，mapping.txt 确有非入口应用类改名；检查 Manifest、字体资产、签名/包名/版本一致。比较 APK 字节和 zip 分类体积；必要 focused AI 协议、工具 schema、显式 JSON 恢复测试及 lint 复用当前修复验证。JVM 测试不运行 R8 后代码，不代表优化 Android 已通过；卡片构造、工具/序列化/ServiceLoader 和恢复仍需优化 APK 的隔离 Android 验收。
- 诊断归档：构建 owner 把 mapping.txt、configuration.txt、usage.txt、seeds.txt（存在者）和源码 manifest/精确差异、APK SHA256/version/签名指纹保存到仓库外 local-materials 对应候选目录。mapping 必须绑定 source hash + APK SHA + version，不覆盖旧 mapping，不随 APK 公开；不复制签名私钥/密码。保留 SourceFile/LineNumberTable 供 retrace。
- 协议/设备/升级：本改动不改协议、数据 schema 或签名配置；优化会影响反射/运行时，构建通过不足以证明兼容。无设备/真实 Key/API/机器操作。未完成优化 APK 验收前不声称实机通过。
- 回退：去掉 opt-in 即恢复正常 Mock 构建，不卸载/清数据、不降级版本。保留所有源快照及候选。
