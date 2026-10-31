# Curve no-pressure fixture locale 与断言诊断

- 决策：Astra 审核 CI `39884263331` 后要求收窄修正。`libraryFixture` 由 `test.targetContext` 取无重量文案，产物为英文 `Not used`；真实 `CurveActivity` 经 `ThemedActivity.attachBaseContext` 使用夹具设置的 `zh-Hans`，显示中文 `不使用`。保留原严格断言与视觉门禁，并在失败前保存三列实际/期望及 locale。原失败目标帧未生成，当前仅能把语言不一致确认为静态缺陷，不能宣称它是唯一失败原因。
- 范围：只改 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt`；预启动 fixture 使用与本次 Activity 相同语言配置的 `AppLanguageContext`，Activity 已存在时直接用该 Activity。首次摘要检查前写诊断 JSON。产品、协议、硬件、门禁脚本均不变。
- 来源与目标：现有协作分支 `feature/task-first-ui` 的 head `5d08fd658ad7300d0478a2b5e117e88eb3c99f11`，目标 PR 为 `develop`；按 [Git 工作流](../git-workflow.md) 走 PR，不直推主干。本次由 Sol 准备本地变更交 Astra 审查，不提交、不推送、不合并。
- 验证：检查精确 diff、文件 hash、Kotlin 括号/引用的静态语法核验；按本轮授权不构建、不重跑设备或 CI。最终通过仍需新 CI 全量 Curve 证据及 Astra 人工视觉判定。
- 风险与边界：无新协议、设备写入或数据迁移；保留未知记录和原断言。无发版授权，不签名、不升级、不安装。若审查不采纳，回退仅本次测试源码与计划改动，证据 ZIP 原样保留。

## Astra静态终核

已读真实单文件diff并核SHA256 `dae8a6b521c0c68b65780716d9f3f6dee5300ba8f3415e611bcb37f8c991ff14`。预启动fixture以当前languagePreferences的AppLanguageContext取资源；实际摘要期望明确传activity。原三列label/number严格相等、unit visibility/text及后续布局门禁未放宽。首次capture回调中的摘要比较之前保存全部三列actual/expected、item/selectedId及Activity/targetContext locale；未catch吞掉原断言或继续下一case，失败仍进入原清理/报告链。产品与runner未改。

静态无新增阻断，可纳入统一构建。只关闭已证跨资源context的测试oracle缺陷；原失败帧缺失，新实际字段/PNG与后续门禁尚待CI，不宣称这是旧栈唯一原因或产品视觉通过。
