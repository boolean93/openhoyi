# 首页安全提示断言诊断

418fec6大字号矩阵的LanguageDetailChecks.warnings在提示校验失败，warning-2截图却显示预期提示；截图不能证明稍后的断言状态。仅为原check增加失败消息：预期/实际文本、可见性、挂载、页面焦点/销毁、同一服务、manualSafetyResource及萃取/连接/高优先级安全状态。没有改变条件、时限、注入和恢复，也不改应用安全优先级。

沿用develop66ebda9+ed01de0依赖、feature/task-first-ui→develop PR #4，遵守docs/git-workflow.md。测试APK构建及diff检查后普通推送，等精确CI证据再定因；不能把日志增强视为修复。不合并、不发版、不实机安装；签名/schema/协议不变，追加revert回退，不清记录。
