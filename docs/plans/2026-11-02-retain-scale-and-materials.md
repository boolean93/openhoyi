# 保留 Scale 草稿与历史资料

- 范围：保留用户明确选择的 Scale 同步调用内观察复用和独有用户反馈；历史资料审查后归档。
- 基线：develop `3d25d48fa1cfde768059ed870842d115d1431d7d`。
- 工作分支：复用用户指定的 `feature/settings-compact`；PR 目标 develop。
- 工作流：[Git工作流](../git-workflow.md)。普通推送、PR、branch-policy/verify/lifecycle/upgrade 全通过后合并。
- 验证：原稿已编译及离线序列化等价；本轮补充最小编译，Android运行效果与提速仍未证明，不扩大矩阵或延长5000ms门槛。
- 协议与设备：仅 Android 测试观察代码，无产品、协议、采样和设备操作变化。
- 发布：不发版、不安装，不涉及签名或升级。
- 回退：通过 revert PR 回退；保留原 CI 草稿、原始日志和已有备份。
- 未完成：必需CI及远端合并核验；原始日志已识别 session/owner 标识，原件仅留本地不入库；机器配置留本地。
