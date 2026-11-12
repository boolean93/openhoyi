# Release 列表最新版本入口

- 需求与范围：在 GitHub 原生 Release 列表首项提供醒目的最新测试版直达链接，随发布自动刷新。保留现有 release/tag/附件 URL、prerelease 和旧客户端协议；不声称修复 GitHub 的排序算法。
- 来源/基线：develop，5a765743a502ad03036a56c517fd211d2878edc5。
- 工作分支/目标：fix/release-list-order → develop。
- 实现：独立脚本遍历全部 Release，按 dev-N 数字选择最新公开测试版；在 API 列表首个公开测试版的「本次更新」开头插入受控提示。此前带提示的 Release 同步刷新，避免首项变化后留下过期链接。只 PATCH body，幂等并回读验证，保留正文。异常直接失败，不自动重试未知写入。
- 部署链路：Develop APK workflow 现有串行 concurrency 下，plan/publish/cleanup 后运行独立提示 job；即使没有新 APK 或发布部分失败，也可同步已公开版本。仅 plan 成功且通道开启时运行，使用现有 GITHUB_TOKEN contents:write，不增加密钥或设备操作。手动重新运行工作流可修复失败提示；不另外造号或重新发布 APK。
- 推送/PR：遵守 [Git 工作流](../git-workflow.md)，本地 scripts/create-pr 检查通过后创建 PR；精确头提交 branch-policy、verify 通过再合并。独立审查由 Astra 执行。
- 验证：离线覆盖 99/104 错序、仅改 body、正文保留、幂等、105 成首项后的旧提示刷新、stable 首项拒绝、损坏标记拒绝；合并后回读线上首项和新提示。
- 协议/设备风险：无 APK、App 协议或设备改动，不安装、不操作机器。现有发布门禁保持不变。
- 发布/签名/升级：保留 dev-N、原包名/签名/versionCode 分配。合并仍遵守已有 Develop 自动预发布授权；本任务不人工触发新 APK 或测试仓库。
- 回退：revert PR 移除自动提示任务；必要时仅删除受控标记段，保留正文及所有发布身份。

## 已核实的限制

2026-11-12，公开网页及 authenticated Release API 都返回第一页 99、98、97、96、95、94、93、92、86、85，第二页以 104、103、102、101、100 开始。`?sort=date` 不改变此顺序。104 已公开，不是漏发。created_at 不能当作发布时间或排序算法证明。

旧客户端 ReleaseUpdatePolicy.kt:46–59 强制 dev-N 及全部对应 URL，直接改数字标签会破坏更新；因此不改标签、不迁移索引。此提示修复的是“从列表看见并找到最新版本”，不是把 104 移到第一位。新增 105 也不能未经实际验证就保证其排序。

[原生列表](https://github.com/boolean93/openhoyi/releases) / [第二页](https://github.com/boolean93/openhoyi/releases?page=2) / [GitHub 排序讨论](https://github.com/orgs/community/discussions/8226)。
