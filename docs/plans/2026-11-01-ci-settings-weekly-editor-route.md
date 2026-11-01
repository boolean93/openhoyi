# Settings weekly editor 测试路径

- 需求与范围：精确head49e9ab6的settings CI原件证明测试在编辑区GONE时直接点击Sunday（0×0/layout不存在）。仅修instrumentation：用原完整可达/48dp click展开真实唯一localized toggle，等待稳定可见和collapse文案，再按原路径打开Sunday及两个schedule dialog。禁止直接setVisibility、隐藏performClick或放宽reveal/whole/像素门。
- 来源/工作分支：沿用已授权fix/ci-page-service，基线49e9ab6908f47848e805f53c92268be9ac6de294，PR目标develop。当前CI收尾与本地下一提交分开记录，不push打断现run。
- 改动文件与步骤：SettingsVisualMigrationChecks.kt、check_settings_visual_migration.sh、新离线host contract测试及本计划；settings workflow仅在现有build测试命令加入新测试模块，共5文件。增加collapsed-toggle/expanded-Sunday两份原accepted Activity frame，绑定原owner/window/token、locale资源文案、7day顺序/身份/共同parent；七day只做runtime结构断言，不另加7份frame。原special case和dialog/draftcancel/no-write全部保留。
- 推送与合并：本地实现 → 负例与instrumentation编译 → Astra独审 → 本块独立local commit；root统一决定下一次push，禁止本任务自行push。
- 验证证据：49e9真实CI取证在settings-evidence/ci-diagnosis/settings-49e9/，此修复的新源码真实CI另待。截图5000ms、单page600s、job45min不增。
- 协议与设备风险：只改测试及host门，不改产品、BLE/秤、生产tag或未知记录，不操作设备。原49e9 legal-input measured和app语言契约的成功原件独立保留，不能冒称本修复已真实跑过。
- 发布：不发版、不签名/安装、不改versionCode或数据。
- 回退：后续revert本独立提交；不清数据/删证据。
- 未完成与阻塞：新源码真实CI和完整12格验收待；用户Scale Kotlin及草稿逐字保留且不混提交。

## 实施与验证

真实唯一localized toggle经原reveal和48dp click展开，等待editor可见、layout稳定和collapse资源文案；七day身份/顺序/共同editor/真实节点文字均按资源绑定。两份accepted帧在同主线程观测中记录冻结Activity/window/decor/root/token/Service身份、原schedule第一/二片真实raw bytes与IDLE/pending=null；展开操作不得改变这些状态。只有toggle与Sunday宣称本帧完整，其余6day不宣称可达。原snapshot不把缺readback伪装为真实内容。

click可选`beforeClick`回调默认不影响原调用；toggle、Sunday初开及重开在原shown/enabled/clickable/48dp检查之后、同一main callback的真正performClick前立即重验冻结身份与schedule状态，避免采证和点击间换owner/token。没有直接setVisibility、隐藏performClick或放松whole/reveal。

新增5项self-contained测试（31类负例、before/after同步后对指定合同拒绝、缺帧、非null20/15B原readback同值正例与同长度字节变化拒绝）通过；连同原50项共55 tests通过。日志：`settings-evidence/ci-diagnosis/settings-weekly-editor-route-host-tests.log`。

最终`:mobile:compileMockAndroidTestKotlin`成功；完整日志 `settings-evidence/ci-diagnosis/settings-weekly-editor-route-compile.log`。bash语法、diff检查、Ruby YAML解析通过。Astra `/root/settings_failure_review` 最终生产独审及新增测试独立复跑通过，要求旧50回归和最终编译完成后准独立本地提交，均已完成。未push；不与history独立提交d2934f8混改。

## 旧49e9原件边界

旧run41289101583已completed FAILURE：四scope app+scale共8格原fullverify PASS，四machine均在真实折叠Sunday路径失败，首错1且隔离安全继续；四font key absent→absent严格恢复PASS。原aggregate正确FAIL并保留四份FAIL receipt。四scope原legal-input measured与语言错误JSON合同重放通过，尚不能证明未触达的schedule两dialog或新展开路径。

四个一次性16G AVD最终可用KiB分别light1.3 11558896、light1.0 10288372、dark1.3 10060380、dark1.0 11702336，本次失败路径未ENOSPC，但完整12格容量尚未验收。原小证据与有界range取证/局部验证限制详见`settings-evidence/ci-diagnosis/settings-49e9/derived/forensic-summary.json`；没有重新下载全部PNG或计算整ZIP digest。

规则：[Git工作流](../git-workflow.md)，格式：[任务模板](task-template.md)。
