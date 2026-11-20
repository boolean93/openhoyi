# 同步自动萃取评价的公开帮助

- 需求：同步 README、GitHub Pages 的 AI 区块及原生功能状态。区分聊天主动发送与独立的默认关闭开关，说明触发、数据、费用、非阻塞、查看及导出边界。
- 基线：`develop`，`07735e7b198d4d61ffc6b5f14918b7fe21525447`。工作分支：`docs/automatic-shot-review-help`；PR 目标：`develop`。
- 验证：对照已合入的 #161 源码、简体中文界面标签及隔离 Mock 证据；运行 `./scripts/create-pr --check-only --base develop` 检查文档与 Pages。PR 只合入通过的精确提交。
- 风险与回退：纯文档，不改应用、协议、硬件、签名、版本、安装或发版设置。需要回退时通过普通 revert PR 恢复文案，不影响用户数据。

具体规则以[Git 工作流](../git-workflow.md)为准。
