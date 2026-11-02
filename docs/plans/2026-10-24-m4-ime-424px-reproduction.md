# M4 原 424px IME 窗口单例复现计划

> 执行者：Sol，按 executing-plans 分步执行；本文件仅规划，先交 root 读结论，不代表已授权设备执行。

**目标：** 在原 light1.0 真实编辑/IME 路径，用受控 Activity 窗口复现 workspace424px、actions inner1082px，验原紧凑同行 guard 与完整输入。只一例，不扩四配置矩阵。

**架构：** 复用 Bean 现有 amount-IME、stableWindow、diagnostic 与业务不变断言；参照 Legacy `shortWindow` 保存/恢复 WindowManager.LayoutParams。控制真实窗口，不改 View 高度、产品预算或 IME 值。技术：Android instrumentation / Kotlin，现有 Mock CI。

## 来源与边界

- develop 基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`，当前工作分支 `feature/task-first-ui`，来源提交 `1365e1f2f748c47671caa20792cf39d590cdf42d`，PR #4 → develop。沿已授权任务合批，普通推送，必需 CI 后 PR 合并；不直推 main/develop、不强推。
- 原失败：aac5c91 workspace424、secondary宽1154、左右padding各36、inner1082；原纵排support82 < editor117px。新2f784ec四IME图workspace484、support142，仍纵排，不能关闭旧边界。
- 仅修改 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt`。不改产品、共享截图工具、runner、workflow、全局 font_scale/density/wm、键盘设置或数据策略。
- 保留原16dp pane、24dp栏、8dp dock/按钮间距，52dp输入、56/48dp动作及完整自然字体。不得强开compact、传伪IME、反射改预算、减少控件自然高或放松whole。

## 单例步骤

- [ ] 在既有 light1.0 Preparation 草稿 amount-IME 阶段、正常IME验证后且原unfocus前插入一次私有受控窗口检查；其它profile不进入。保持原input实例、文本18、selection0..1、批次及nextEventId。
- [ ] 主线程先复制原完整WindowManager.LayoutParams；保存窗口实际decor高、workspace rect、secondary内宽、滚动、input/两button身份、focus/selection、业务events/balance/intent。真实Insets.ime必须为true；原宽度及density必须与原case一致，否则写明“不具原窗口复现条件”，失败而非跳过签过。
- [ ] 仅一次计算候选窗口高度：`candidateHeight = decor.height - (workspace.height - 424)`，保持原width、gravity、x/y、softInputMode及其它属性，调用 `window.setLayout(original.width, candidateHeight)`。若workspace本来424，无需resize。只允许正高度且原workspace≥424；不增加窗口/循环搜尺寸。沿现有stableWindow等真实布局稳定，不加sleep。
- [ ] 随即保存实际window/decor/app/workspace/Insets几何及productLayoutEvidence。**窗口请求高度不等于workspace高度**：必须实际workspace.height==424、secondaryWidth==1154、innerWidth==1082、真实IME=true、split=true，才称复现。ADJUST_RESIZE/系统栏若使结果不同，保候选和实际值并失败收口，不再调第二尺寸、不改全局配置或强开guard；该失败只说明受控窗口候选未复现，不判产品同行失败。
- [ ] 已满足原窗口条件后，沿原输入requestRectangleOnScreen及稳定路径，检查原纵向required>secondaryHeight、原编辑按钮eligible、实际自然宽候选成立、actionsCompactEnabled=true且实际HORIZONTAL；support预算≥自然editor高。继续原whole(amount)、两button whole及56/48dp高度门槛；附实际自然宽/高、最终rect、8dp间隔、16/24dp不变证据。不得只因orientation横排就通过。
- [ ] 保存该单例PNG、geometry、typography、三帧稳定记录、原始预算JSON与业务前后值。IME沿原 `diagnostic` 的明确 `unsynchronized_IME_diagnostic_not_main_protocol2` 身份，不伪造同合成帧或把它降格为普通主图通过；已有非IME 5s四零协议不变。Astra实际看输入/单位/同行主次操作的完整性后方可视觉关闭。
- [ ] 同一try/finally中恢复完整窗口attributes，无论资格/断言/写盘失败都执行；用原稳定/帧路径核恢复实际窗口几何及原attributes。保持焦点/selection/文本/批次/eventId，业务events/balance/intent精确不变。后续原unfocus仍核IME关闭、纵排参数恢复；若原baseline已424，横排可在IME仍显示时保留，不能错误要求恢复窗口就必纵排。
- [ ] 原异常为主；恢复或证据写盘异常附加，不掩盖首错。窗口恢复失败明确留证/失败，不能进入后续消费步骤冒充隔离成功。不中断或重建Activity来回避恢复。

## 验证与交付

Sol交唯一fixture diff、精确SHA与 `git diff --check`；静态检查单profile入口、一次resize、原guard/输入/业务断言、finally恢复。无需新增镜像模型或故障注入。root统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest`，沿原Bean任务实际产一例；不另开设备执行。真实运行后分别报告“窗口复现资格”“产品同行/whole”“视觉”“窗口及font_scale恢复”，不能混成总PASS。

当前此候选尚未实施/运行，setLayout在真实IME下是否得到424px必须由实际几何确认。不能保证一次请求必复现；若不成立，保原件交Astra做下一单点判断，不自动扩矩阵。新四图484px限定接受与旧424未闭合事实均保留。

协议/设备：无BLE、秤、咖啡机命令或库存策略变更；仅独占CI Mock窗口测试。无发布、签名、升级、安装及正式设备授权。回退为新增revert提交移除本测试增量，保留全部证据及真实数据；不卸载/清库/重发未知操作。

## Root执行裁定

已阅读并批准上述单例fixture实施，交Sol开发、Astra静态与图审、root统一构建/普通PR提交。仅允许现有CI Mock流程，未授权实体设备操作或PR合并；不新增矩阵，也不以窗口请求值替代实际424px资格。

## 2026-10-24 冻结实现静态验收

Astra 复核唯一源码 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/BeanWorkflowVisualMigrationChecks.kt` SHA256 `26fc3eee48262087da7b00c67ae810b2bf9c53ce3f5878414c6e469ec11cc0fe`。限定静态通过，交root最终统一构建；尚无实际424窗口/同行原图，不关闭运行边界。

核对 light1.0 唯一入口位于原amount-IME验证后/unfocus前；单次候选resize，真实424/1154/1082及IME/split资格不符即失败。产品字段均存在；原自然宽/纵向不足/完整输入及56/48dp动作、16/24/8间距未放松。diagnostic原函数明确调用TaskTypographyEvidence.capture，实际保存独立typography及非protocol2诊断身份，不缺计划要求的字体证据。

finally恢复完整attributes，并核copyFrom变化mask为0、decor/workspace/secondary原几何、scroll、input/按钮身份、focus/selection、批次/eventId/业务；原unfocus后的纵排检查保留。初版0638c542的末尾status/stackTraceToString不在保护try内可盖原错，已提出阻断；本冻结将status、堆栈生成、writeText纳入同一诊断try，失败时保原failure、附suppressed，最后抛原对象。未改产品或其它profile。独立重算SHA吻合，`git diff --check`通过。

## Root最终构建

最终源码冻结后 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 成功，35s、91 tasks（4 executed / 87 up-to-date），日志 `build/task-first-evidence/ui-2026-10-24-bean-424/build-final.log`。303个Kotlin源构建前后SHA一致，见同目录source-final-before/source-final-after.json。首次build.log保留，但不代表最终源码。diff-check通过。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA256：`c5bfc921337225ae30d742565fa97357fab03d59d11b273dafc6e72a08aaa4cb`
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA256：`30c2addce50aca4f439bbec7204ec8971af93c43f231e0cb098f2eb5799bceea`

未安装、未发布；424px实际复现、同行可读和恢复仍待本次精确源码CI，构建不证明这些已通过。

## 2026-10-24 首次真实候选结果：窗口未复现，不是同行产品失败

run40058928582实际tested merge `a7f6afbb29b760589f5d3c7598d950b3172a0ba6`，原ZIP `ci-484b137-bean/artifact.zip` 5183778B，SHA `9bbe3e9f276c875e946b772bca1969700d06dbcf2ee78b442ab0d90435e040bc`，root核72CRC。Astra直接只读原 `preparation-amount-IME-424.json` 和稳定记录，未解全包/改源码。

候选1540确已设置，decor1600→1540；但workspace原/实际均 `[216,234,2524,718]` 高484，app均2560×691，secondary1154/inner1082不变。真实IME=true，stableWindow通过三连续无pending布局帧。系统栏局部insets由 `[0,63,0,135]` 变 `[0,33,0,105]`，各减少30px；decor减60并未减少实际workspace。不能把窗口attributes接受等同目标可用高复现。

原预算实际verticalRequired459≤secondary484，support142≥editor117，所以compact=false/not_needed是正确guard结果，候选在资格检查即按计划FAIL；尚未执行同行/whole单例验收，不判产品布局失败，也不关闭旧424缺口。

finally `restorationQualified=true`、attribute change mask0，原workspace/secondary/scroll/输入身份/选区焦点/事件与业务严格还原；独立对比businessBefore==businessAfter。原IME保持true，系统insets回63/135。该局部窗口恢复不替代runner全局font_scale结果。索引摘要 `ci-484b137-bean/424-candidate-review.json`。

本轮按原计划停止，不追加第二尺寸、不硬开guard、不缩字号/间距。后续若root再授权规划，应以此次实际窗口/insets关系选择可用区控制策略；当前不猜下一高度、不自动重跑。
