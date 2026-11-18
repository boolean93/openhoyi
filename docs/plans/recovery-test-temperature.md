# 恢复测试温度配置隔离修复

- 需求与范围：修复 lifecycle 的严格 Context 测试夹具缺少 temperature_confirmation 命名空间。统一补齐同类夹具，使既有快照、不污染断言和清理覆盖该配置。不修改生产行为、不放松断言。
- 来源分支/基线提交：develop / a7f82b78378ec8986fc6613cc5945de458c0ff45。
- 工作分支：fix/recovery-test-temperature；PR 目标 develop。
- 改动：仅 androidTest 的配置名称列表及本规划。
- 推送与合并：遵守 ../git-workflow.md；使用 scripts/create-pr，精确提交必需检查通过后合入。
- 验证证据：本地 create-pr 完整门禁；合入后已有云端 lifecycle 和 upgrade。编译不等于 instrumentation 通过。
- 协议与设备风险：没有生产蓝牙、预热或协议变更；不安装、不操作机器，不启动本地 AVD。
- 发布：不手工发布；既有 Develop 自动预发布仍需同 SHA 的全部门禁成功。
- 回退：revert 本 PR，不清用户数据。
- 原始证据：GitHub lifecycle run 44328083447 的 language-instrumentation.txt，ServiceRecoveryPersistenceChecks.kt 的 fixtures[name] 空值异常。

- 同类测试语义核查：六个通用 settingsWrite 夹具仍以 BrewTemperature 验证旧通用持久门禁；改用有效普通设置 Light(false)，同步对应回读 fixture，保持原断言不变。温度生产路径不变。
