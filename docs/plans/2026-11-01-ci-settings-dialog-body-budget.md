# Native settings dialog body budget

- 需求与范围：仅 `SettingsVisualStyle.kt` 的 page-local observer/body预算与对应纯JVM测试。134原图是native footer消失；432的新原图证明先前差分预算引入body0回归。本次以独立自然chrome替代差分，不改AppDialog、native节点/listener、焦点/IME请求、52dp输入/48dp动作或whole门。
- 来源/工作分支：沿用已授权 `fix/ci-page-service`，PR7目标develop；settings基线4322700c8bb34207b47c9182711ec4358280d377。同branch历史agent独立提交history，不混其文件或用户Scale两草稿。
- 原件与更正：`settings-evidence/ci-diagnosis/settings-4322700/` 保留四scope失败，8个app/scale格原fullverify通过、4个machine被零高body裁切、aggregate失败。真实body0、safe619、panel144、差分chrome475；先前216/259固定点来自差分假设，不是独立自然测量事实。旧6项数值测试未覆盖Android空闲空间/祖先minimum约束，不作为产品通过证明。
- 实施：核真实parentPanel直属top/content/custom/button集合与顺序；固定panel只支持WRAP_CONTENT、无weight；custom到body单子FrameLayout、parent到decor真实单内容FrameLayout/竖直LinearLayout，仅排除明确system-bar overlay。未知结构预算null并记原因，不猜常数。
- 自然预算：在真实可用宽和layoutDirection下分别自然测top/content及已有native bar。真实wrapper padding/child margins/divider是加法，minimum（含background及Frame foreground minimum）按内到外 `max(inner+decoration, minimum)`。foreground额外非零padding语义未识别时uncertified。safe仍实际decor∩visibleFrame；预算不读body现高/scroll/decor空闲。已知native约束不可满足、或不足52dp，分别明确false，不抬高cap制造空间。
- 收敛与诊断：只在固定测量签名变化时自然measure，随后force原层级并requestLayout、阻止该帧一次以恢复正常布局；签名排body/cap/scroll/实际高度。schema2记录真实结构identity/LP/width/rect、自然高、内外layer分项、window/decor/token、cap与可满足状态；没有改parent/owner/focus。实际位置和whole仍由原采证门验证。
- 官方依据：[Android14 native layout](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/res/res/layout/alert_dialog_material.xml)、[AlertDialogLayout](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/com/android/internal/widget/AlertDialogLayout.java)、[AlertController](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/com/android/internal/app/AlertController.java)。消息与custom同时存在时不能假定特殊三panel测量；custom由原controller挂入，保持原对象。
- 推送与合并：本地验证→Astra独审→仅本块3文件独立local commit，root统一push；不自行追加CI。按用户最新要求收敛验证，代表性light1.0原失败键盘输入/按钮场景与1.3边界优先，现有CI顺带运行不取消；不额外自设完整12格merge硬门，实际branch protection必须通过，未测如实保留。
- 验证：8个纯预算边界覆盖零body反馈回归、nested minimum、真实wrapper约束、IME可用高度和按钮变化、不可满足/未知/溢出。原57个host回归保持。自然数值中的216/144/619仅测试输入，不冒充新Android实测；代表性新实景原图/输入和按钮同时可用仍待root统一push后的CI。
- 协议与设备：仅UI布局；不改BLE/秤/真实wire或Mock typed合同，不操作设备/删除数据，不把Mock像素零差异等同硬件验收。
- 发布与回退：不发版、不签名/安装、不改versionCode；后续revert本独立提交，保原件/数据。Scale草稿原字节保留。

规则：[Git工作流](../git-workflow.md)，格式：[任务模板](task-template.md)。

## 当前本地验证

首轮8项JVM和instrumentation编译 `BUILD SUCCESSFUL in 54s`，原57项host `OK`。日志分别为 `settings-evidence/ci-diagnosis/settings-natural-chrome-compile-tests.log` 与 `settings-natural-chrome-host-tests.log`。Astra要求的真实parent浅签名及Frame foreground约束已补。最终同命令 `:mobile:testMockUnitTest --tests io.openhoyi.mobile.SettingsDialogBodyBudgetTest :mobile:compileMockAndroidTestKotlin --no-daemon --console=plain` 再次成功（55s），日志 `settings-natural-chrome-final.log`；8/0fail/0error/0skip原XML已外置 `settings-natural-chrome-junit.xml`。bash语法/diff检查通过；Scale两草稿原哈希未变。Astra `/root/settings_failure_review` 最终只读独审通过，亲核生产补丁、最终55s日志、外置8项XML和57host；无新增blocking finding，准仅本块3文件独立本地commit。未push、未实景设备操作。代表性新IME输入与按钮同时可用的运行原件仍待新CI，不把静态/单测称实景已修好。
