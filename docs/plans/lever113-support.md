# 1.1.3 拨杆配方兼容

- 需求与范围：允许 1.1.3 使用现有拨杆配方保存流程；不引入试验包、指定设备/曲线、一次性许可或设置过期刷新修改。
- 来源分支/基线：origin/develop，ed14584fd5911a5c03d557eb2837ed31d9c198f1；工作分支 fix/lever113-support；PR 目标 develop。
- 改动：同步 Mobile 与 DeviceSession 的精确固件集合；在 1.1.3 下运行既有安全/未知结果回归，并验证深烘 Bloom 帧及受限流量模式。协议依据见[兼容说明](../evidence/2026-11-13-lever113-compatibility.md)。
- 推送与合并：协调线程检查差异、提交并普通推送，通过 scripts/create-pr 执行本地完整 verify；branch-policy、verify 通过并独立审查后 PR 合入。实现线程不提交或推送。
- 验证：针对性 StoredLeverRecipeWriteTest 与 mobile:compileDebugKotlin；完整本地 verify 和精确提交 CI 由协调线程记录。单测模拟传输，不是设备验收。
- 协议与设备风险：仅扩展版本许可；原待机、时效、告警、队列最终校验、未知事务 journal、无自动重发与曲线形态限制保持不变。1.1.3 保存与断电保持尚未实机验证，不将 GATT 成功视作保存确认。
- 发布与升级：本任务不手动发版、不更改版本/包名/签名、不安装、不操作设备；现有 Develop 自动通道按仓库既定规则运行。
- 回退：通过 revert PR 撤销许可，不删用户数据或未知事务，不卸载降级。
- 未完成：实机写入、保存及断电保持留待用户后续现场验证；设置过期提示的独立修复仍暂停。

遵循 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)。
