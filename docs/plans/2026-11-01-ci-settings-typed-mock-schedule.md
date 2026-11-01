# Typed Mock schedule observation contract

- 需求与范围：134535d真实原件表明MockDeviceRuntime以emptyFrame构造SleepPart；原route宿主错误要求20/15B wire且仅比较raw[]不足以证明typed值不变。仅改instrumentation/host/离线测试，明确实际Mock来源、完整typed4+3日片段和原raw[]；不编码伪wire、不放宽真实协议。
- 来源/工作分支：沿用授权fix/ci-page-service，基线134535d1d62b5115f93d5db67f7e296f5b82835f，PR目标develop；与history mixed metrics及产品dialog预算修复分开commit。
- 改动文件：SettingsVisualMigrationChecks.kt、check_settings_visual_migration.sh、test_settings_weekly_editor_route.py及本计划。
- 实施：runtime校验BuildConfig.MOCK_MODE、mock包名、当前scope拥有的MobileService及实际MockDeviceRuntime实例。manifest绑定Mock flag与两个owner身份；记录firstIndex0/4、first enabledBits/second null、days exact4/3的四个时刻字段、原raw空数组，完整结构before==after。时间戳会自然刷新，不当成写行为。原owner/token/48dp/IME/像素与dialog门全保留。
- 推送与合并：本地正反例/编译 → Astra独审 → 仅本块独立local commit；root统一决定push，不打断当前矩阵。
- 验证：新合同反例覆盖typed变而raw仍[]、来源/owner换绑、index/bit范围/日数/时间/伪wire；134旧raw-only proof明确因缺typed来源拒绝，不注入新字段冒称真实通过。
- 协议与设备风险：只读实际owned Mock typed状态，不写/重发/变更真实协议或机器；不操作设备。生产dialog修复另案。
- 发布与回退：不发版、不改签名/versionCode；后续revert独立提交，不清数据或删证据。
- 未完成：新源码真实CI及完整12格仍待；用户Scale两草稿逐字保留、不混提交。

规则：[Git工作流](../git-workflow.md)，格式：[任务模板](task-template.md)。

## 本地验证与独审

新增typed合同7 tests及全部相关57 host tests通过，日志`settings-evidence/ci-diagnosis/settings-typed-mock-schedule-host-tests.log`。Astra `/root/settings_failure_review` 独立重跑7 tests、bash语法和diff检查后最终独审通过。

history并行源码引用未闭合造成的中间编译失败已单独保留`settings-typed-mock-schedule-blocked-history-compile.log`，没有当作最终结果。该源码完成后，最终`:mobile:testMockUnitTest --tests io.openhoyi.mobile.SettingsDialogBodyBudgetTest :mobile:compileMockAndroidTestKotlin` BUILD SUCCESSFUL（1m9s），日志`settings-evidence/ci-diagnosis/settings-dialog-budget-compile-tests.log`；包含两块当前工作树编译，不冒称新提交已跑真实CI。预算产品修复另行独立提交。

134原run41291835403最终四machine均在schedule native IME footer clipped失败；其后新typed host还会明确拒绝134缺provenance/typed值的旧raw-only证据。旧原件不补字段、不包装为新合同真实通过；新源码完整12格与所有dialog仍待root统一CI。
