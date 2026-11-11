# 一键萃取后的 Mock lifecycle fixture 修复

- 需求与范围：修复 PR #58 删除普通开始确认后，语言页面 instrumentation 仍反射 `confirmStart` 的失败。仅改测试，不恢复生产弹窗，不把反射调用改成出水命令。
- 来源分支/基线提交：`origin/develop`，`ef186137`。
- 工作分支：`fix/direct-start-lifecycle`；PR 目标 `develop`。
- 改动文件与步骤：语言检查改为真实 Start 按钮触发低温 studio 预热确认，仅点击取消；保存恢复 Mock 设置、温度、snapshot、选曲，验证 shot/trace/preheat/message/安全记录不变。同步 lifecycle 输出和严格 grep。复用现有导航 Mock 杯的独立按钮检查，验证无普通确认且重复点击不产生第二杯；保留曲线选择仅导航检查。
- 推送与合并：由协调方串行提交、普通推送，运行 `scripts/create-pr`，必需 CI 通过后合并；GitHub lifecycle 执行验证另记。此实现线程不提交/推送。
- 验证证据：定向相关 JVM 单测、Mock instrumentation Kotlin 编译、Mock 与 androidTest APK 构建。本地不运行模拟器、不安装实体设备；编译不能冒充 instrumentation 执行通过。
- 协议与设备风险：无生产协议/服务/门禁变更；fixture 强制 Mock 包、hub 为 null。低温确认只负按钮，finally 只取消/还原，不启动预热或萃取。直接开始用例仅复用已有隔离 Mock 杯及其停止清理，不触碰真实机器。
- 发布：不发版、不改版本/签名、不安装或操作实体机器。
- 回退：revert 测试修复，不删设备数据或未知记录。
- 未完成与阻塞：GitHub 实际 lifecycle 和 PR 门禁由协调方执行；本地结果待补。

规则：[Git 工作流](../git-workflow.md)。

## 本地验证结果

- `./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.DirectExtractionStartTest :mobile:compileMockAndroidTestKotlin :mobile:assembleMock :mobile:assembleMockAndroidTest`：最终成功，41s。定向 JVM 2 项，0 失败/错误/跳过；Mock 与测试 APK 已构建。日志 `/tmp/openhoyi-direct-start-lifecycle-local-final.log`。首轮仅错误引用 `session.ShotGate` 导致 instrumentation 编译失败，已改为实际 mobile 同包类并重跑通过。
- `bash -n scripts/check_mock_lifecycle.sh`、`git diff --check`：通过；旧 `confirmStart`/`cancelStart` 反射与 `cancelledStart` 成功声明已无残留。
- 未运行 instrumentation、模拟器或实体设备，不宣称低温取消/直接按钮已实际运行通过。CI lifecycle 对中英、主题和窗口组合的实际执行由协调方继续。
