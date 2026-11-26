# 完杯弹窗保留结束秤读数

- 范围：本杯有已确认净重时显示「液重」；只有结束秤读数时显示「结束时秤读数」，不把原始秤重当成净重。没有有效结束读数仍显示「—」，不使用后续移杯读数或注水量。
- 基线：develop @ 7e27859f → fix/completion-shot-weight；集成目标 develop。按[工作流](../git-workflow.md)和[模板](task-template.md)执行，如需集成，仅普通推送、通过 PR 和必需 CI 合入。
- 实现与验证：沿用首个有效待机端点，向反馈结果与弹窗传递来源。定向 JVM 回归覆盖 34.9 g、移杯、无数据和净重；核对界面标签。不改 BLE、去皮、停机、历史或 AI 请求。
- 不发版、不安装、不改签名或版本。回退本次显示修改即可，不涉及持久数据。
- 验证结果：旧逻辑复现 3490（百分之一克）变成 null。修复后 ExtractionScaleReadoutTest、BrewFeedbackTrackerTest、BrewFeedbackDeliveryTest 共 36 项通过；compileMockAndroidTestKotlin 通过。未安装到平板，未做实机萃取验收。
