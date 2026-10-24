# M2 Editor / M3 Import 重建后 IME 变化只读取证计划

> Sol按executing-plans执行；Astra审查；root独占完整构建/合批推送。不改产品恢复策略，不覆盖其他人文件。

**Goal:** 夹住产品恢复完成至严格断言之间IME/viewport/scroll的第一次实际变化，保留无法归因的边界。

**Architecture:** 在现有仪表生命周期与真实View上添加可移除的只读监听，短周期内仅内存采样，在原稳定帧与断言同main取同一组字段。两业务使用同一诊断schema但保留独立fixture、原恢复实现和判定。

**Tech Stack:** Kotlin instrumentation、ViewTreeObserver公开观察API、现ImeRecreationTrace/native Winscope旁证。

## 来源/范围

遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)，分支feature/task-first-ui，当前基线588855c96fdceec79bf87f0fd5950f33fa6aff1c，PR目标develop。

已读`ci-b08adec/frame-ime-decoded-causality.md`：Editor同field27/同Decor客户端在产品574568005447ns恢复结束后，574924211496ns出现InsetsController#hide，随后notifyImeHidden。但IMMS cur_token是IME token，不是App window/request tag；既存bytes无调用栈/同请求tag，不能把近时HIDE_SOFT_INPUT_BY_INSETS_API归为产品或测试调用。

已读`ci-588855c/import-ime-source-diagnosis.md`：Import产品171889760376ns前自报IMEtrue/raw4885/middle107并卸监听，173108832707ns后实际IMEfalse/raw4281/middle0。保存文字/选区/焦点相同不允许放宽原scroll/IME合同。产品ResultReceiver code2曾与实际IMEfalse同时出现，不能把响应码当可见证据。Editor/Import不是共享产品恢复helper，不能预判同一根因。

## 允许实现文件

- 新 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ImeViewportTransitionProbe.kt`：限定一次recreate区间的只读采样器。
- `AdvancedBusinessUiChecks.kt`：仅现有M2 Editor重建诊断接点。
- `CurveVisualMigrationChecks.kt`：仅原Import重建诊断/稳定帧接点，不改已过Library十项、不碰popup或业务。
- 必要时`ImeRecreationTrace.kt`仅添加独立probe文件引用与状态，既有native命令/截止/原始bytes保存不变。
- 可新增离线 `scripts/test_ime_viewport_probe_contract.py` 验证事件归属、上限、顺序/unknown和schema；自含小fixture，不假称Android行为验证。

不改CurveEditorActivity/TaskCurveEditorForm/CurveImportActivity恢复代码，不加show/hide、requestFocus、clearFocus、setSelection、scrollTo、requestLayout、IME等待成功条件或重试。

## 最小观察与接点

- [ ] 在原recreate前同main安装旧Activity观察；复用现Application.ActivityLifecycleCallbacks，replacement的`onActivityCreated`只保留原native trace，待`onActivityPostCreated`产品视图建好后、首layout前安装新观察，在attach时补注册真实ViewTreeObserver。SDK34的`super.onCreate`可先派发`onActivityCreated`，此时Editor/Import字段尚未初始化，不能将decor/null field/empty scroll固化为replacement。基于原field resolver唯一解析Editor key或Import raw字段；缺失/多对象标记unknown，不找“另一个聚焦EditText”代替。保存Activity/Window/decor/field/root/原所有相关ScrollView对象引用，身份比较用同进程`===`，hash仅显示；新旧对象分开编号。
- [ ] 只add/remove监听：attach、ViewTreeObserver globalFocus/windowFocus/globalLayout/scrollChanged/preDraw。preDraw永远返回true，不调用requestApplyInsets。**禁止setOnApplyWindowInsetsListener、setWindowInsetsAnimationCallback、setOnFocusChangeListener等覆盖产品已有监听**，不能让取证自身撤掉恢复监听。现有产品onApply/animation事件若已存可作旁证；新增事件仅明确为“在这个公开观察回调读取到的insets”，不伪称onApply原回调或hide发起点。
- [ ] 每次采样在同一main turn读取：elapsedRealtimeNanos/seq/pid/tid、eventSource、Activity/Window/decor/field与各自token引用身份、attached/windowFocus/hasFocus/focusedView、rootWindowInsets IME visible及insets四边（null保unknown）、root/host/field宽高、layoutRequested、EditText scrollX/Y与真实Layout高/行数/当前scroll可行范围、所有既定中层scroll x/y/viewport/range、选区。字段身份变更记录而不重新绑定掩盖。仅保文字长度/已有fixture文字摘要及原状态引用，不在每个回调复制92行正文。
- [ ] 监听从old_before_recreate保持到after comparison（成功或失败）后finally才移除，**不跟产品RESTORED结束一起卸载**。以状态变化去重，首次/强制接点保留；最多256事件，另记累计观察数、droppedCount、截断起始时刻，满额不覆盖旧事件，也不把截断称完整。无额外定时poll、sleep或等待，没有截图重拍。回调只读少量字段/内存记录，不写文件/执行shell/做PNG/遍历无关树。
- [ ] 原stableSnapshot/stableWindow各采样main块中追加`sample("stable-frame", frameIndex)`，使用当前原对象；不向原稳定signature插入不断变化的时钟，不改变原三帧/期限。原断言处在采before/after同一main块强制probe样本，再原严格断言；before/after业务JSON仍保原样，不能把probe纳入或删除比较字段。
- [ ] 将产品现有restore evidence与probe时间线并列保存，在稳定帧/断言接点记录其实际status/最新事件序号（只读已有值，不发请求）。原native trace保留，callback/native时钟均用设备elapsedRealtimeNanos；严格区分客户端窗口身份、App window token、IME token和request tag，缺tag继续unknown，不用时间近邻拼造同请求。
- [ ] finally逐项移除本probe注册的监听（保留原ViewTreeObserver引用、alive检查及attach重注册账目），不删除产品监听；封存最后样本后才落单独`ime-viewport-transitions.json`，纳入原证据目录/manifest。原业务异常保主Throwable，诊断/清理失败suppressed；若没有主错，诊断失败必须明确PARTIAL/ERROR，不能伪称完整。原失败现场先保留，任何诊断都不得触发原断言后才有的unfocus动作提前执行。

## 离线与实跑验收

离线反例至少覆盖：旧/新field串用、token未附着null误认为相同、跨PID时钟排序、重复seq、字段缺失、事件上限截断、监听重复安装/未移除、changed事件只记录scroll而漏IME、未知值默认为false、不同request按近时合并。静态检查无输入/布局变更API和无set监听覆盖；原M2/M3严格断言及所有原预算差异为零。

Sol交实际diff/SHA和离线日志；Astra审同main与监听生命周期；root完整构建/普通合批推送现PR，不微推/force/no-verify，必需CI后合并develop。新CI只回答首次可观测IME/viewport/scroll变化顺序、发生在哪个观察事件区间、产品finish前后及对象是否一致。**不能仅凭这条时间线宣布hide调用方或唯一根因**；若仍缺调用栈/request归属，保留unknown，再定下一只读证据，不直接强制键盘。

不放宽frame5秒四零、原恢复timeout、不伪造成功、不增加设备写入或未知重发；不发版。回退仅诊断增量并保留原件，不清设备/记录。当前批准只读取证，产品修复尚未批准。

## Astra 静态复核：replacement 绑定时点阻断（2026-10-23）

实际读取四文件diff、完整probe与离线脚本，SHA：probe `6cad109900bf561dfc5e3e6da22b50e22eeb857bcfe66e2634083214153da918`、Advanced `b9e4a28001fbc5ad2b92765eb4edeb134d423cb87d460e20f45301ef8c1d826c`、Curve `07909bd4d5b08203bd676ae23b92e03a864a5b0bed80625f213e57214b50b392`、offline `0127c5da878bdd630bcc12e87c8a63da0e3b955199aa943798b31acd8e1971d9`。独立直接执行脚本14反例通过；这是静态/合成schema检查，不是Android生命周期执行。

**确定阻断及计划事实校正：** 本计划原写“onActivityCreated（视图存在后）”条件未成立。当前SDK34源码 `android/app/Activity.java:1720` 在Activity.onCreate内部 dispatchActivityCreated；performCreate 在子类onCreate完成后才 dispatchActivityPostCreated（8606）。两产品均先super.onCreate再初始化fields/form/raw，因此当前replacement-created resolver必然过早。probe将失败解析记录为unknown后仍加入observed并固定decor/null field/empty scroll，之后duplicate_activity_install保护禁止同owner再安装；无法夹住replacement真实编辑器IME变化。

最小修订：old-before-recreate接点不动；replacement的真实Targets解析/install移到已经注册的onActivityPostCreated，仅首次安装，视图已创建且首layout前。onActivityCreated继续原native trace，不绑定替代decor作为成功真实对象。确实无法唯一解析时仍记录unknown/PARTIAL，不等待、查任意聚焦字段或更换对象。补离线生命周期顺序反例明确pre-create解析失败不得成为永久replacement完整采样。此修正不涉及产品行为/等待/严格比较。

其余静态边界：监听使用add/remove、preDraw返回true，无输入/IME/布局变更API；原5秒deadline及signature不加入时间戳；256上限、dropped/截断/PARTIAL和App/IME token未知边界保留。旧新owner直接引用比较、同main stable样本和断言、失败时封存均存在。原业务Throwable在已存在时仍被保留并附加probe finalization错误。补充收尾要求：若probe.closeAndWrite先失败而原业务成功，后续native cleanup/report写盘也失败，应将后错suppressed到该首个probe异常，不得由裸File.writeText覆盖；可在本新增finally范围聚合主错，不扩大产品或其它业务。

当前不通过。修后Astra只审上述接点/收尾增量；root完整构建不等同取证合同通过。未修改实现、未触发设备或构建。

### Astra 最终增量复核：静态通过（2026-10-23）

已实际核 replacement 两接点均仅在onActivityPostCreated解析/install，onActivityCreated仅保原native trace；旧实例接点与字段唯一解析未放宽。SDK34子类视图初始化结束后、首layout前建立监听，原过早空Targets阻断闭合。M2/M3 finally新增首错聚合：原业务异常优先；probe先失败则后续native cleanup/finish附加suppressed，诊断文件写失败不覆盖既存首错。原native单独失败仅留诊断的既有政策未扩改。

冻结SHA：probe仍 `6cad109900bf561dfc5e3e6da22b50e22eeb857bcfe66e2634083214153da918`；Advanced `20a3ebb8713c275da1b26f062cc507ea93c0a29ab3bb2560ee394c8ca0575e4d`；Curve `f9aa5026a2563b1e39204f675a6d50c8b4a4e9dcc17c8e21522ab1e9d57fe00b`；offline `3aec279d2b6df47a4d45565baa0f9fb706d6062107785dd8634faed2335e85fb`。独立直接运行14 schema反例与1生命周期顺序反例通过。原strict equality、frame/稳定预算、用户IME行为及产品源码未更改。

本项允许root进行统一构建及按仓库流程合批提交/推送；构建结果由root负责。本结论仅只读取证增量静态验收，不能称IME恢复修复或hide调用方已查明；仍需新原件核真实Targets、监听账目与PARTIAL/截断。未构建或修改源码。
