# M3 Source route Cancel 返回断言补证

- 范围：只补 `librarySourceRoutes` 返回检查前的实际状态与分项结果，不修改产品、不决定根因、不放宽原复合断言。
- 基线/分支：已授权迁移批次 `feature/task-first-ui`，当前head `4076c2d862f94608fa6ec3d405c91d23a6b6867c`，PR目标develop；失败证据来自旧head8b52379、merge `44d34c52a3f3b18537f59cb92607bcc7c6252d09`。遵守 [git-workflow](../git-workflow.md)、[task-template](task-template.md)，同Sol串行，不覆盖其他owner。
- 依据：`build/task-first-evidence/ci-8b52379/curve-first-failure-diagnosis.md`，以及当前 `CurveVisualMigrationChecks.librarySourceRoutes/listState/libraryCapture`、`CurveActivity.onStart/show`。

## 已知与未知

Import Cancel已真实返回并保存restored帧；随后10项复合return/invariant失败。before.detailRequested=false已记录；onStart→show会置true是源码推导，后值未保存，不能据此认定唯一根因，也不能排除list/prefs/业务等其它差项。原returned字段在check之后赋值，失败时缺失。新4076c2d运行未结，不与旧证据混用。

## Sol最小实施范围

仅 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt` 中此函数及必要的私有只读诊断辅助；不动产品、共享capture、runner、deadline或其它业务测试。

1. 各route原出发前读取before的位置，放入一次 `main {}` 读取原listState、detailRequested、两个scrollY，并一并保存Activity/Window/decor/token对象identity、实际class、compact、detailPanel visibility、root/list/detail/fallback的isLaidOut/isLayoutRequested/尺寸/scrollX/Y。原比较基准仍是这些原字段；不可重置选中/滚动或调用show。冻结业务/preset/curvePrefs/clipboard仍用原scope级基准，不改成每route重新取值以掩盖前案变化。
2. 保留原Cancel/Back、await、legacy reader drain、restored libraryCapture及其顺序。capture返回后、原check之前，在**同一个main块**只读一次完整after：listState八字段、detailRequested、两scrollY、相同身份/布局字段、原invariant字符串、两prefs map、clipboardSnapshot、services列表、当前焦点/目标销毁情况。map/集合须快照，不留可变live引用。不要为每个子式重新读取。UI状态读取失败也要记录已获字段和实际异常，并沿失败路径退出，不能填默认值通过。
3. 计算并记录原10子式的独立布尔结果，不让 `&&` 短路使后面的实际值缺席：listState.toString精确相等；detailRequested相等；detailScrollY相等；fallbackScrollY相等；**原returned.actualRestoreState.hasWindowFocus**；invariant精确相等；preset map相等；curvePrefs map相等；clipboard相等；services为空。保存各子式实际before/after值及false项名称；对listState另列八字段各自比较。不要将原字符串相等改成宽松JSON相等，不将map/set/null转成字符串后重新定义相等。
4. 在check前将这些值以及原 `returned`、`restoredCapture` 引用填入现routeReport，新增明确命名的 `returnAssertionDiagnostics`；保持原business.json由现finally写出，并保留现写出失败suppressed路径。这样原断言抛错时，已有finally会保存实际状态；不必增加UI操作、capture或新文件。原 `status=PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE` 仍只在严格检查成功后设置。原错误文字 `M3 $route return/invariant failed` 保留，check判定仍为原10项全部为真；只可使用该次已采实际值，不能额外重读产生“诊断一套、断言另一套”。
5. after属于**capture之后的单次主线程断言快照**，明确记录采样阶段/elapsedRealtime，并与returned frame target分开命名；不是截图同帧认证。焦点原断言仍取returned，额外当前焦点只作诊断，不替换原式。本项不把业务全树塞进帧观察回调，不增加protocol预算或等待。

## 验证与边界

静态逐项对照10个原子式、八list字段、身份与两scroll，确认每个after只采一次、失败前填report、原finally落盘和suppressed保因仍有效。可用只读离线检查/小控制流用例验证：detailRequested单差、list单差、多项同时差、全相等、null/集合值保真；模型不是Android实测。`git diff --check`后冻结源码SHA交Astra，再由root统一构建/合批普通推送，不微推、不绕检查、必需CI通过后合并。

不新增点击/重试/reset，不调用剪贴板/SAF导入动作；只复用原clipboardSnapshot读取，不发设备写入，不清未知记录。5秒frame与原await截止不变。诊断若证实产品差异，另提最小产品修复，不能在此顺手修show或接受detailRequested变化。当前只规划，未实施/构建；不发版。回退仅revert此诊断增量，原ZIP/FAIL与未知记录保留。

## Astra 静态复核（2026-10-23）

结论：冻结增量静态通过，可交root统一编译；不认定原返回失败已修复。

独立重算 `CurveVisualMigrationChecks.kt` SHA256为 `68a931b99d6cfeb385252e12dde0261852af39baa377a9d68d00ecbfa94686bc`，已读实际diff与相关类型/helper。范围仅本函数及三个私有只读辅助。主线程helper保留异常重抛；新增JSONObject/Map<String,*>、Set快照、View/ScrollView与Boolean字段用法未见静态类型阻断，最终仍须真实Kotlin编译。

出发/返回分别一次main块；返回位于原restored capture后，不另等待/采帧/点击。判定使用采下的after局部值，10子式全部执行，无原短路漏项；原listState字符串精确比较、两map原类型相等、clipboard字符串相等、services为空与returned帧焦点条件均保留。诊断布局辅助在同一main块附加读取scroll坐标，仅用于视图信息，不触发操作或另一次断言重采。八list字段结果齐全，scope级业务/prefs/clipboard基准未移到每route重置；Set复制保值，排序仅用于诊断JSON展示，不参与断言。

routeReport在check之前已持有departure、after、原restoredCapture、10结果和falseItems；部分快照失败记录snapshotFailure并重抛，不填默认成功。allTenEqual只由10个实际比较的falseItems得出，PASS仍在严格check之后。原finally写business.json及写错suppressed路径未改，原失败文字保留。明确sameCompositorFrameProven=false，当前焦点只作诊断，不替换原帧焦点。

独立diff whitespace检查通过。本轮未运行Gradle、未改源码、提交或推送。尚无after新运行实值，不能把detailRequested源码推导认定为唯一根因；其它差项、真实视觉与全配置仍待证据。
