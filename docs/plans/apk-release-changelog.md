# APK 发布页更新说明

- 从 develop（e7f53f99）建立 chore/apk-release-changelog，经 PR 合回 develop。遵守[仓库 Git 工作流](../git-workflow.md)。
- PR 作者写一句面向用户的中文说明。在 Develop APK 公开时按实际发布顺序汇总到正文顶部，保留原有构建及验证信息。历史 PR 缺少说明时提供回溯依据。
- 本地验证 PR 正文门禁、发布范围与正文生成，通过仓库创建 PR 入口执行完整检查。合并前等待必需 CI。
- 仅修改发布说明流程，不涉及协议、签名、版本分配、升级或设备操作。本任务不另行发版。可通过 revert PR 恢复原正文生成逻辑。
