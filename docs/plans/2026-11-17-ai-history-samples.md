# AI 按需读取历史采样

- 需求与范围：历史列表提供 shotId，新增只读 extraction_samples 按精确记录分页返回保存的原始时序观测；不默认发送全部历史、不新增设备能力、不补造缺失值。
- 来源/工作分支：origin/develop 6c01bc71；feature/ai-history-samples，PR 目标 develop。
- 步骤：复用只读 journal 校验与 ShotSamplesStore；限制 ID、分页与完整 JSON 大小，标明单位、时基及抽稀边界；更新工具、提示词及中英文同意说明。
- 验证：代表性样本/缺失/损坏/孤立 ID、跨 500 分页、fake 模型 history→samples→回答及 schema；独立复核后使用 scripts/create-pr 完整本地门禁，再等待精确头提交 branch-policy、verify。
- 协议与设备风险：不改 BLE、采样落盘、数据库及控制门禁，不操作设备、不调用真实模型 API。历史 UNKNOWN 不证明结束，配方设定不作为实测。
- 发布与回退：不发版不安装；通过 revert 回退代码，不改历史格式、不删除用户记录。
- 未验证：真实模型自行选择读取页数及真机端到端体验，测试不代替硬件验收。

遵循 [Git 工作流](../git-workflow.md)。

## 实际验证（2026-11-17）

- `JAVA_HOME=$(/usr/libexec/java_home) ./gradlew --no-daemon --console=plain :mobile:testDebugUnitTest --tests '*AiExtractionSamplesTest' --tests '*AiReadOnlyDataTest' --tests '*AiChatSessionTest' --tests '*AiLiveProbePolicyTest' --tests '*AiChatPolicyTest' --tests '*AiRequestContractTest'`：37 项通过。
- `python3 -m unittest discover -s scripts -p test_ai_android_acceptance.py`：3 项通过。
- `python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`、`git diff --check`：通过。
- Astra 最终独立复核通过；未调用真实 API、未操作设备、未启动 AVD。完整 PR 门禁与必需 CI 在后续创建 PR 时执行，结果记录于 PR。
