# Mock 合成预览窗口上下文修复

- 触发/目标：Mock134已按本次授权保数据覆盖成功；隔离合成预览初始UI可见，但点模型菜单后PID1882报BadTokenException，栈Host.choose d.show。当前用户可能接手，立刻停止设备操作。本轮仅本地修复，不能宣称已上设备。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)，不提交/PR/push/合入，保留所有其他未提交工作；已读AGENTS。
- 分工/文件：Sol唯一写者/串行Gradle；Astra只读独审。仅AiChatPreviewActivity.kt与本计划，不改真实Activity、网络、KeyStore、owner guard、版本或已安装134材料。
- 修复：原createConfigurationContext(Activity)只提供资源上下文，不保留Activity窗口服务；改Activity为base的ContextThemeWrapper(this, theme)，首次访问wrapper资源前applyOverrideConfiguration，仍用统一AppLanguageContext.configuration维持预览language/fontScale/dark。保留中文theme应用和隔离进程fence，不以改系统资源或去掉guard绕过。
- API依据：[ContextThemeWrapper](https://developer.android.com/reference/android/view/ContextThemeWrapper)；本地SDK35 api-versions核实Theme构造API23、applyOverrideConfiguration API17，项目min26均可。构造参数theme来自Activity，不是wrapper提前取resources；override只调用一次。
- 验证：compileMockKotlin与现有AiOfflinePreviewStateTest（进程fence、合成模型/附件/OCR/codec等）focused，无JVM假窗口token断言。不运行Android instrumentation/设备，不构建新APK。JVM不能证明真实窗口修复，待下一次明确授权设备复测。
- 安全/协议：无真实Key/用户数据/付费API/CU/咖啡机，真实App不启动；既有合成预览不接picker/Keystore/coordinator，不能升级为这些全链验收。
- 发布/签名/升级：不发版、不改版本/签名、不安装；134仍含旧preview缺陷且已装，此修复只在本地源码。新产生聊天/草稿不得假定可丢，不沿用本次安装的丢弃授权自动再覆盖。
- 回退：仅撤回这两处本地源文件，保留已安装包/数据和134冻结快照；不清数据/降级恢复。
- 证据：before /tmp/openhoyi-preview-window-before；完成保存本轮diff/hash/log到local-materials/ai-preview-window-context-fix。

## 本地结果（未上设备）

- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:compileMockKotlin :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiOfflinePreviewStateTest`。
- compileMockKotlin实际通过，5项既有预览状态测试0失败/错误/跳过，BUILD SUCCESSFUL in 1m；不扩lint/assemble/无关测试。
- Astra最终独立只读审查通过，无阻断；Activity窗口服务继承、override调用顺序、语言/fontScale/dark/中文theme和owner guard保持，min26 API兼容。
- 精确2文件diff/前后SHA/本地日志在`openhoyi-local-materials/ai-preview-window-context-fix`，134冻结APK/源码不变。
- 未再次操作设备，未构建/安装新版本；真实窗口对话框修复仍待以后明确授权复测。当前134安装成功，但不含本地修复，预览模型对话框已知崩溃，后续流程未报通过。
