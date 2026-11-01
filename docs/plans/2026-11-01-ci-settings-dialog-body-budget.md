# Native settings dialog body budget

- 需求与范围：134535d四scope真实schedule IME原图与几何表明原生footer整排消失。仅修SettingsDialogLayoutObserver（SettingsVisualStyle.kt）：测实际decor∩visibleFrame，保已独立测得native button自然reserve，即使panel塌缩0也保留；只缩滚动body，不改native listener/48dp/52dp/IME/whole门或AppDialog全局。
- 来源/工作分支：沿用fix/ci-page-service，PR目标develop；基线134535d1d62b5115f93d5db67f7e296f5b82835f。与typed Mock合同、history mixed metrics分开提交。
- 原件：settings-evidence/ci-diagnosis/settings-134535d/schedule-ime-raw/light-1.0/；实际safe/decor高619、body403、panel0、native自然reserve144、非body非button chrome216，旧cap475使footer仍0，原PNG可直接见无buttons。
- 改动步骤：同文件纯budget函数与observer使用它；增加JVM边界/收敛测试。cap=max(0,safe619−chrome216−reserve144)=259；下一轮body259+panel144仍cap259。记录safe交集、actual panel、reserve、chrome、cap和不足52dp的不可满足状态。未知native结构不猜0 reserve、不宣称预算认证、不新增全局dialog崩溃。
- 推送与合并：本地测试/编译 → Astra独审 → 独立local commit；root统一push，不自行追加CI或打断当前矩阵。
- 验证：原真实失败PNG/geometry保留；新纯JVM测固定点、实际decor增长、按钮自然高度变化、空交集/不足52dp、未知结构及溢出边界；真实新CI仍需四scope三dialog、原图、安全controls与完整12格证明。
- 协议与设备风险：仅UI测量，不写BLE/秤/设置、未知记录，不操作设备，不把Mock同域零差异等同硬件/视觉验收。
- 发布与回退：不发版、不签名/安装、不改versionCode；后续revert本独立提交，保原件/数据。
- 未完成：新源码真实native footer/IME证据与完整12格；Scale两草稿原样不混提交。

规则：[Git工作流](../git-workflow.md)，格式：[任务模板](task-template.md)。

## 本地验证与独审

`SettingsDialogBodyBudgetTest` 6项JVM测试通过，涵盖真实数值固定点、native reserve变化、实际safe decor增长、空/不足预算、未知结构和溢出边界。原JUnit XML归档`settings-evidence/ci-diagnosis/settings-dialog-body-budget-junit.xml`；共享Gradle测试报告会被其他agent的测试覆盖，因此另重跑单class并立即外置归档，没有把报告覆盖误报为测试失败。

最终测试+instrumentation编译 BUILD SUCCESSFUL（1m9s），日志`settings-evidence/ci-diagnosis/settings-dialog-budget-compile-tests.log`；另独立JVM最终日志`settings-dialog-budget-jvm-final.log`。全部57相关host tests、bash语法和diff检查通过。Astra `/root/settings_failure_review` 生产独审通过，无remaining blocking，允许验证完成后仅本块3文件独立本地提交。未改AppDialog/原native listeners/48与52dp/IME/whole门。

本地数值测试证明预算逻辑及收敛，不证明Android实际footer已经可见。134旧原图仍为失败原件；新源码四scope的schedule IME/无IME dialog、实际按钮原图、安全controls及完整12格必须另由真实CI验证。未push、未设备操作，用户Scale两草稿哈希原样。
