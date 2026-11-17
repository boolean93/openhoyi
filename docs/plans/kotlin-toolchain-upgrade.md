# Kotlin / Android 构建链升级

- 需求与范围：按用户 2026-11-14“尝试升级、正面修复，升级完继续开发”的授权，先独立升级 Kotlin JVM/Android 2.3.10、AGP 8.13.2、Gradle 8.13。JVM目标/JDK主版本17、Android min/target/compile SDK26/35/35不变，不含Koog依赖或AI功能。
- 来源分支/基线提交：develop / 7a22e6cbee30322c066ff76a19cde08e7e96cbf4。
- 工作分支：chore/ai-toolchain-probe；PR目标：develop。
- 改动文件与步骤：根插件/wrapper及官方SHA256；三Android模块改用compilerOptions；两处ConsistentCopyVisibility；共享Mock版本合同与测试；签名策略从applied Kotlin脚本机械内联到mobile主脚本，保留原文件与严格等价性测试。
- 内联边界：原69行正文保持一致，仅java.io.File用JavaIoFile import别名解决主Android脚本java扩展名字遮蔽；kotlin.run局部块无receiver，保持原作用域/Project绑定/extra导出/所有版本、路径、证书、脱敏、密码清零与任务依赖。独立Astra签名审查未发现安全阻断。
- 推送与合并：遵守 [Git工作流](../git-workflow.md)，本地完整non-AVD verify、独立复核后通过 scripts/create-pr 创建草稿；精确头提交branch-policy/verify通过后再合入。未达门禁不合入，不关闭lint或绕保护。
- 验证证据：同一Temurin17.0.21+9控制组原apply脚本仍复现findFirCompiledSymbol/UAST崩溃；仅内联后lintDebug通过。签名10正反例、临时signed Release fixture、assemble/bundle任务图及纯工具链完整门禁需全部验证。纯工具链 scripts/verify-pr-checks full 已完整通过：202 Gradle tasks、全单测/Debug+Mock编译打包/lint，双语APK资源校验，以及10项签名正反例、真实临时signed Release APK和assemble/bundle任务图。临时fixture密钥删除，无安装。随后 clean + assembleMock fresh产物 19,009,894 bytes，SHA256 eef8cb2a2fc769cdcee29fcfb9503171799808e47e1ee9c5c38a4e9f2cef390d，与内联前工具链控制包逐字节相同，证明内联未改变APK内容。增量ZIP空洞观察另作历史研究，SDK净增待后续AI阶段fresh重测。
- 本地主机：原Oracle17.0.7出现C1/aarch64 JIT崩溃，另行保留证据；从Adoptium官方取得Temurin17.0.21+9并核验SHA256，仅/tmp及限定JAVA_HOME/PATH用于命令，不改全局JDK。CI仍沿用Temurin17。
- 协议与设备风险：无BLE/硬件/业务协议变更，不安装或操作设备，不清历史，不调用真实/付费API或使用真实Key。编译/CI不等于硬件验收。
- 发布：不人工发版、不改包名/版本/签名。合入后既有Develop自动测试通道仍按原门禁运行。
- 回退：常规revert PR；保留应用数据与设备未知记录，不以卸载/清数据恢复。
- 未完成与阻塞：本地完整门禁通过；PR仍需精确头提交branch-policy/verify与最终独立复核。AI页面、密钥库、日志后端与泄漏测试属于后续独立功能，不包含本PR。
