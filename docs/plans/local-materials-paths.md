# 本地资料路径说明

- 需求与范围：更新两处原始日志引用，说明主资料副本、兼容副本与不随克隆分发的限制；不提交日志或清单原件。
- 来源分支/基线提交：develop，`18e9bc3`。
- 工作分支：复用已授权的 `feature/settings-compact`，已 fast-forward 到 develop 基线。
- PR目标：develop。
- 改动文件与步骤：更新 `docs/validation-2026-09-23.md`、`docs/history/2026-11-02-retained-materials/source-index.md`；保留 gitignore 与本地 SDK 配置。
- 推送与合并：自查 → 普通推送工作分支 → PR → branch-policy、verify、lifecycle、upgrade 四项通过 → 合并。
- 验证证据：`git diff --check`、文档路径引用核查；本地三份日志复制 SHA256 一致的既有证据已核对，原件及清单不入库。
- 协议与设备风险：仅文档，无协议、数据、设备操作影响；完整日志载荷未完成公开发布审查。
- 发布：不发版，不涉及签名、升级、安装或设备验收。
- 回退：通过 revert PR 回退文档；保留所有本地资料、工作树与分支。
- 未完成与阻塞：无实现阻塞；本地资料不随 clone，缺少时向持有者取得或独立复制。

遵守 [Git工作流](../git-workflow.md) 与 [任务模板](task-template.md)。
