# 应用字号规范与轻量回归门禁

- 需求与范围：用户批准把 PR #85 的应用字号规则沉淀到仓库 AGENTS.md、详细规范与适度自动检查；不修改 dot 自定义指令，不改产品行为。
- 来源分支／基线：PR #85 已 squash 合入 `develop`，基线 `d246edb74d4fde7942cb8e3ed4b68f4e36e7b7c3`（与原字号头 fdd29950 产品树一致）。
- 工作分支：`chore/app-font-size-guidelines`，隔离工作树，不改字号开发分支。
- PR目标：develop；正常追加提交，不重写 #85 历史。
- 文件与步骤：AGENTS.md 简明入口，docs/app-typography.md 具体规则／例外／验证边界，扩充 AppFontSizeCoverageTest 与偏好倍率测试，注册源码扫描的 Gradle 输入。复用已有 verify 的 JVM 流程，不新增 AVD。
- 推送与合并：交由 Mac 发布工作者承接本提交，通过 scripts/create-pr 检查后开草稿 PR；branch-policy、verify 按精确头提交通过，不直接推送 develop。
- 验证证据：JDK 17 下 `:mobile:testMockUnitTest --tests '*AppFontSize*Test'` 14 项通过；最终 `:mobile:testMockUnitTest` 134 suites / 705 项，704 通过、1 个既有路径依赖审计跳过（LegacyCurveWireAuditTest.auditsUserExportWhenPathsAreProvided），0 failure/error。`git diff --check`、修改 Markdown 本地链接检查通过。独立复核修复了模板内嵌引号扫描误伤并增加正例；未安装或重跑设备验收。
- 协议与设备风险：仅文档／测试及测试输入配置，无协议、连接、权限、业务队列或生产布局变化，不安装、不操作硬件。
- 发布／签名：不发版、不打标签、不改包名、签名、versionCode 或数据格式。
- 回退：正常 revert 本规范／测试提交，不卸载或清除用户数据。
- 未完成与阻塞：完整 PR 门禁由发布者执行；未验证不记作通过。

流程依据：[Git 工作流](../git-workflow.md)。
