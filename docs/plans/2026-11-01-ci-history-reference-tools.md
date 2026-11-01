# M5 真实历史参考入口迁移

- 需求与范围：迁移确定失效的隐藏support-scroll参考按钮操作；只改测试，使用真实chart-tools→当前toolsDialog→reference-choice→真实chooser。保原参考on/off、eligible/index、实际saved samples对齐、trace/points、stop位置、journal及scroll恢复；不恢复旧UI、不直接点隐藏source。
- 来源分支/基线提交：develop `35798d22e1dcbe2dd404bdbc744ffd486c0c8e50`（PR6已合并、postmerge四required及capture全SUCCESS）；现有本地CLI小提交335af83保留。现有branch正常merge基线后为804f723，未新建分支/force。
- 工作分支：按授权继续`fix/ci-page-service`，保Scale两草稿原字节、不混v46/安装。
- PR目标：新的后续PR→develop；PR6已关闭，不直接push develop。
- 改动文件与步骤：ExtractionHistoryVisualMigrationChecks增加真实菜单helper；菜单源语义仅对照text/enabled，不直接performClick；实际菜单Button要求whole/48dp/正确owner/window/token及原protocol2截图。chooser按实际focus/callback/owner识别，不能假设进程只有一个foreign window。两个确定同因场景historical_reference_toggle与journal_unavailable共用该helper；未删后者，避免下一轮才暴露同一旧入口。
- 验证保持：选择行真实adapter position/eligible+off count/visible whole/48dp/enabled/token后performItemClick；保原on/off/restored原截图、header、原始points、traceId、journal、stoprect及滚动值。异常保真并只dismiss本测试持有dialog，最终Main实际恢复焦点。host verifier增加新route证据校验，原字段/矩阵数量/原质量门不删。
- 推送与合并：本地编译/相关回归→Astra独审→本块独立commit→root普通push/新PR→精确head真实M5及原required验证；无admin/保护改变。
- 验证证据：JDK17 `./gradlew --offline --no-daemon :mobile:compileMockAndroidTestKotlin`本地成功（28s，47 tasks，日志`settings-evidence/ci-diagnosis/history-reference-tools-reviewed-compile.log`在workspace）。独立host回归共31项通过：reference route 8、existing extraction tools 9、legacy history axis 6、git policy 8；分支策略、`bash -n`与`git diff --check`通过（日志`settings-evidence/ci-diagnosis/history-reference-tools-targeted-tests.log`）。route负例覆盖owner/window/token、hidden/disabled/48dp、item index/count、frame拒绝/种类/映射/前后geometry/真实density。只记录本机离线验证，不代表真实窗口验收。Astra（`/root/settings_failure_review`）独审四文件及实际`ExtractionFragment.showTools/chooseReference`、`MainPageTestHost`与cleanup/host合同，独立复跑route 8项通过，未发现阻断提交问题；运行端真实window协议采证已保留，后续可进一步交叉绑定host menu identity与frame顶层identity（非阻断建议）。Native真实工具和chooser窗口、参考on/off业务完整验收必须新CI，不以静态或旧2ef结果替代。
- 扩展回归限制：额外`test_*contract.py` discovery运行43项，其中9失败及1 setup error均因旧artifact缺失（`ci-a47c870-{scale,legacy}` raw、`ci-3a78c4c/settings-artifact.zip`），日志`settings-evidence/ci-diagnosis/history-reference-tools-host-contracts.log`。未删、改、跳过这些测试；本次相关31项已单独完整通过。
- 协议与设备风险：不改生产/Tracker/Mock频率/命令/单位/参数；不扫描连接BLE、不用户设备、只CI fixture。原dialog采集的surfaceInsets等不支持条件继续fail，不为此降低协议或改产品阴影。
- 发布：不发版、不打安装包、不设备安装。
- 回退：仅回退该测试迁移，保业务及未知记录；不删除branch/worktree/历史。
- 未完成与阻塞：真实新head M5待CI；M1四用户面迁移和system-image cache仅分别规划，不混本提交。
