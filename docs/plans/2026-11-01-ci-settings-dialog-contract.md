# Settings CI native dialog contracts

- 需求与范围：修复 app language-write-rejected 的合法错误 TextView host 契约；后续独立提交只为 machine legal-input-mock-preview 启用既有 measured Window 协议。保持所有可见性、身份、前后几何、四原图和零像素差门禁。
- 来源分支/基线提交：现有 fix/ci-page-service，7b7562ddb4e0621679d1b442d2acc688f4caa096；沿用已获授权的 CI 恢复分支，不切换或重写历史。PR 目标 develop。
- 改动文件与步骤：SettingsVisualMigrationChecks.kt、check_settings_visual_migration.sh、针对 host 合约的离线测试；先 language 独立实现、验证和 Astra 独审，再 machine 独立实现、验证和 Astra 独审。ScaleVisualMigrationChecks.kt 和 2026-11-01-ci-scale-fresh-observation-read.md 是已有用户草稿，不修改、不纳入提交。
- 推送与合并：本地逐项验证 → Astra 独审 → 仅提交明确本任务文件；不自行 push，待 parent 协调 PR7 当前运行完成后决定新 PR 或后续追加。不会中断现有精确源码 CI。
- 验证证据：运行 self-contained 正反例、既有 host 协议测试和 Kotlin instrumentation 编译。旧 artifact 缺新资源期望字段，仅用于问题重现；显式迁移 fixture 的回放不等于新源码真实 CI 或视觉验收。
- 协议与设备风险：只更改 instrumentation/host 证据契约，不更改产品、BLE/秤协议、队列或未知记录；不操作实体设备，不把 Mock 结果当硬件验收。保留5000ms预算、无容差比较及全部原始证据。
- 发布：不发版、不签名、不安装实体设备、不调整 versionCode 或数据。
- 回退：通过后续 revert 恢复本任务提交；保留已有证据、用户数据和未知记录。
- 未完成与阻塞：真实 CI 待 parent 协调；容量方案另行设计和审查，不与缓存对照或用户 Scale 草稿混合。

规则：[Git工作流](../git-workflow.md)，格式：[任务模板](task-template.md)。

## App 实施与独审

主线程 accepted target 记录 `language_save_failed` 资源值及 locale；host 只接受唯一、完整、安全区内的错误 TextView，与既有三类构成精确类集合。保留原身份、controls、前后及保存几何一致性。

`test_settings_language_error_contract.py` 4 tests 通过（22 种错误变体及 accepted-before/after/saved 绑定），`bash -n`、`git diff --check` 通过。`:mobile:compileMockAndroidTestKotlin` BUILD SUCCESSFUL（30s）。自包含 settings 测试共24通过；旧归档套件因固定 ZIP 未提供而 setup 失败，没有跳过或宣称完整归档回放成功。旧2ef几何仅提供故障定位，缺新的资源期望字段，不能等同新源码 CI。

Astra `/root/settings_failure_review` 只读独审通过，复核了安全区外、零面积、重复身份负例及原门禁保留。真实 CI、视觉验收仍待 parent 调度；未 push。
