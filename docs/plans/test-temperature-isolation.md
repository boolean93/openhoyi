# 补齐所有恢复测试 Context 的温度命名空间

- 需求与范围：PR #149 后云端 lifecycle 在 ServiceStopFeedbackChecks.kt:34 的紧凑白名单失败。扫描全部 androidTest getSharedPreferences 覆盖，补齐四处恢复测试名称列表，包括 check(name in names) 和 fixtureNames 两种遗漏形式。
- 来源分支：develop / c4c9c6a475153364d6be6ed25b9b1ae8d776e8b0。
- 工作分支：fix/test-temperature-isolation；PR 目标 develop。
- 改动：四个测试名称列表；现有 test_temperature_ui_isolation.py 增加自动枚举不同空白/映射形式的回归断言。保留隔离、原始配置不污染和 finally 清理，不修改生产逻辑。
- 推送/合并：遵守 ../git-workflow.md，运行 scripts/create-pr 本地门禁、必需 CI 后合入。
- 验证：静态扫描测试及负向变异验证，Mock androidTest 编译；实际 instrumentation 由已有云端 lifecycle 执行，不将编译等同验收。
- 协议/设备风险：无生产预热、蓝牙或协议改动，无安装或设备控制；不启动本地 AVD。
- 发布：仅既有自动 Develop 通道，全部精确 SHA 门禁成功前不能发布。
- 回退：revert 修复，不清除用户配置和未知操作。
