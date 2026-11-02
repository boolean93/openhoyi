# 遗留项目资料保全

遵守 [Git工作流](../git-workflow.md)。来源 origin/develop，工作分支 chore/preserve-project-history，经 PR 合入 develop。

范围：同步已确认的协作与会话规则；归档原 native 工作树独有早期设计素材。旧研究正文已有新版，不覆盖。README 无内容重复提交通过独立备份保留，CI 优化草稿仍待实际运行验证。

验证：文件逐字 SHA256 对照、git diff --check，以及分支必需 CI。无协议、设备、安装、发布、签名或升级操作。回退使用后续 revert PR。
