# 手动记录的待用配方提示

- 需求与范围：只在实时页标题为机器手动萃取时，在同一标题区域显示轻量“待用：曲线名”；来源与开始按钮一致，无可用配方不显示，常规界面不变。
- 来源分支/基线提交：origin/develop e86c4827，实施时快进同步 #106 至 234fce74。
- 工作分支：fix/manual-pending-curve；PR目标 develop。
- 改动：共享纯展示选择与标题样式；Fragment/兼容 Activity；中英资源及针对性测试。
- 流程：遵循 docs/git-workflow.md，经 scripts/create-pr、本地检查及精确提交 CI后合入。
- 验证：选择、删除、槽位、普通记录不显示，字体/长名/主题/窗口 Mock 验收及独立审查，结果补充。
- 协议风险：只读已解析配方，不改变门禁、设备命令、选择或历史。禁止真实机器操作。
- 发布：不单独发版；Mock安装仅用于授权验收，保留用户数据。
- 回退：revert PR，无数据迁移。

## 验证进度
- `:mobile:testMockUnitTest --tests *ManualPendingRecipeTest :mobile:assembleMock` 通过。
- 中英资源校验及 git diff --check 通过。
- 独立审查指出主标题折行吞副行风险，已改为两个独立单行文本；补充 Mock-only 绘制探针（中英、深浅、三字号、三宽度）。
- 平板正在使用正式App，本轮不覆盖 Debug/Mock，不操作真实机器。绘制探针及完整页实屏尚未执行，不能把构建称为实机验收。
