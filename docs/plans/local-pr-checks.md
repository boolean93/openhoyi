# 创建 PR 前运行本地检查

从 develop 的 0163a503 建立 chore/local-pr-checks，PR 目标为 develop。
本地入口和 verify 共享范围判断及检查命令，验证通过后才创建 PR。保留原有推送 hook 和合并保护。

验收用临时 Git 仓库验证失败时不会创建 PR，成功时传递正确提交、目标和证据，并对本分支运行完整检查。
普通推送工作分支，等待 branch-policy 和 verify。此改动不涉及设备、协议、签名、升级或发布。回退本次 PR 即可。
