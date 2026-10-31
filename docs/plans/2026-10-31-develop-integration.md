# Task-first 与设置文档整合到 develop

- 需求与范围：用户明确授权将完整 feature/task-first-ui 经 PR #4 合入 develop；包含既有首页、曲线、萃取、库存/复盘、连接恢复及最新 A 设置长文档。设置生产基线 e84b595；本次先修生成资源漂移及萃取覆盖页返回来源，再执行严格 CI。
- 来源/目标：develop 8e12fe159db6bbd5aa168ea938645b091f4d1300 → 既有 feature/task-first-ui；PR 目标 develop。设置隔离分支 feature/settings-compact 的提交先在本地安全快进整合；原 AGENTS.md 未提交内容保留。
- 改动步骤：按 catalog 重新生成语言 XML 并验证无漂移；保留萃取覆盖页前一个目的地及其重建状态，关闭覆盖页后恢复来源，显式切 tab 清理来源，避免返回栈无限增长。现有 MainNavigationChecks 的秤→萃取→秤→设置断言覆盖该路径，不放松门禁。
- 推送/PR：仅普通推送 feature/task-first-ui；更新现有 PR #4 并转为 ready；等待该精确头提交的 branch-policy、verify、lifecycle、upgrade 四项成功且讨论解决后，正常 PR merge，不强推、不绕过保护、不直接推 main/develop、不删除分支。
- 验证：现有 A39 生产源码 e84b595 已通过双包/AndroidTest 编译和363单测（362通过、1跳过）。七语言、A39首图独立视觉及真实 Mock 锚点/返回/精度/窄窗/草稿证据保留；本次修正再次执行生成校验、相关编译单测及远端必需CI。原生编译不冒充原生运行；以 CI 实际结果和本地日志为准。
- 协议与设备风险：本次无新增 BLE 编码/命令/支持型号；沿用原门禁、确认、回读与未知状态。此前分支含预热/连接恢复等协议相关工作，既有独立审查/计划及验证边界继续有效。Mock 无物理机器验收，机器写入/真实回读尚未验证。
- 发布/安装：不发正式版本，不打标签、不建 Release。PR 合入后核对精确 develop merge commit；安全切换原目录到该 develop，AGENTS.md 逐字保留。从合并提交构建同源码递增版本（至少40）的 Mock/Debug，核查平板空闲、同包名、原签名及数据，-r 覆盖保留数据并双包核验；不启动真实咖啡机动作。
- 回退：新增修复分支/PR 或 revert merge PR；保留应用数据和未知操作记录。不得卸载、清数据、降级或重发未知结果；安装异常立即停止并记录。
- 未完成：本计划提交时远端CI/合并/合并后双包安装尚未完成，最终以操作证据为准。当前 main 保持不动。

唯一规则源：[git-workflow](../git-workflow.md)。设置规范：[A 锚点设计](2026-10-31-settings-anchor-design.md)；完整项：[设置清单](2026-10-31-settings-inventory.md)。既有萃取：[仪表及阶段计划](../superpowers/plans/2026-10-31-live-extraction-instruments.md)。
