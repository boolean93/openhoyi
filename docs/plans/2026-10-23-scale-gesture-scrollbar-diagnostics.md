# Scale forty-gesture 滚动条呈现差异窄诊断

- 范围：仅诊断Connection候选列表forty-gesture首帧细灰条差异，不改产品或截图门禁。
- 基线：失败run39906804853/head `4076c2d862f94608fa6ec3d405c91d23a6b6867c`；当前授权feature/task-first-ui→develop批次，遵守[git-workflow](../git-workflow.md)、[task-template](task-template.md)。其他owner的M5/Curve不动。
- 输入：`build/task-first-evidence/ci-4076c2d/scale-first-failure-diagnosis.md`、`scale-forty-gesture-diagnostic-index.json`、原`scale-artifact.zip`（136067333 bytes，SHA256 `53536f669ae33443c2044367b0c2a86198594bb3565e9588e3d7ac49d57b192e`）。沿用1562成员CRC保全，按需读取，不重复下载/全展开。

## 已观察与源码匹配

1.0 attempt1三项差441像素，surface框[2479,623,2488,672]，实际screen框[2479,697,2488,746]；D1/D2为0。attempt2虽四零但5420ms，仍失败。1.3首差315像素，screen[2479,737,2488,772]，第二次4914ms通过，仅限该配置。

独立读原proof的首attempt target，候选ScrollView语义为 `product/scale-connection/candidate-scroll`，并非ScaleSettings/support-scroll：

| 配置 | ScrollView屏幕框 | scrollY | rows高度 | 推导滑块 |
|---|---|---:|---:|---|
| 1.0 | [252,669,2488,1308] | 365 | 8338 | 639px轨道、49px高、顶669+28=697 |
| 1.3 | [252,715,2488,1296] | 367 | 9778 | 581px轨道、35px高、顶715+22=737 |

`ScaleConnectionActivity`创建普通ScrollView，仅设置fillViewport/tag，未覆盖淡出策略；没有自绘灰条逻辑。核本地SDK34 `ScrollBarDrawable`/`ScrollBarUtils`：length=max(2×thickness, round(size×extent/range))，offset=round((size-length)×scrollOffset/(range-extent))。以现几何、右侧9px厚度推导，上表两组恰等原差异框；该公式映射是**强静态吻合**，不是已读到实际drawable range/alpha。9px厚度来自像素，不冒称已有getter原值。View源码存在FADING插值alpha及结束OFF路径，滚动位置不动不代表滚动条已停止呈现变化。

原swipe已有真实MOVE与末尾静止MOVE、UP和waitForIdleSync，随后committedFrame与scrollY检查；不能额外sleep、反复等待灰条消失或误称committedFrame证明全部动画结束。现proof没有实际scrollbar配置/alpha时序，因此尚不能唯一归因淡出，也未证明改变截图实现便可解决。

## 最小实施授权：只读取证

仅允许 `ScaleVisualMigrationChecks.kt` 的forty-gesture调用链及可选诊断hook；`NativeWindowFrameEvidence`、产品、runner、其它case不改。

1. 对原candidateScroll设置一次可选只读记录器，注册/移除均在main，范围从原swipe前至原capture返回/抛错。记录明确阶段：swipe前、原UP后、原committedFrame后，以及capture既有observe每次调用。可在swipe加仅本调用传入的事件时间回调，不改变任何事件坐标/顺序/时长。不得新增idle/frame等待、截图或retry。
2. 用公开API在main读取实际View身份/tag/class、window/decor/token身份、screen/window位置和clip、width/height/padding、scrollX/Y、child高度、layoutDirection、canScroll上下、isLaidOut/isLayoutRequested、verticalScrollBarEnabled、scrollBarStyle/position/size、scrollbarFadingEnabled、scrollBarDefaultDelayBeforeFade及scrollBarFadeDuration。记录SDK/设备标识和uptime/elapsedRealtime；不从theme常量替代getter。确无公开getter的数据写unknown，不反射mScrollCache/Scroller隐藏状态。
3. 可读公开verticalScrollbarThumbDrawable/trackDrawable的class、bounds副本与alpha，并明确它们是最近draw留下的对象状态、不是当前合成像素/OFF状态证明；null也原样保存。不调用draw/mutate/setAlpha/awakenScrollBars/invalidate/requestLayout/scrollTo以便观察。若getter/平台实现不能解释差异，保留unknown，不能直接判OFF。
4. 记录器使用有限小对象列表，在原capture的observe中追加**旁路诊断**，返回的geometry结构和值保持原样；时间戳/序号/alpha绝不放入稳定几何target参与相等比较，否则制造必败。给观察记录递增序号/阶段并可对照原proof before/after及budget，不冒称所有PixelCopy中间阶段均有同步采样。无额外帧循环、无每帧全树/截图/磁盘I/O。记录上限和溢出标记明确；达到上限不能截断原采证或把不完整记录当完整。
5. finally移除记录器并写 `forty-gesture/scrollbar-diagnostics.json`，即使原capture失败也保存已有样本；保留原Throwable，诊断写错suppressed，不把失败改成功。不改原5000ms/四零、重建/导航恢复和业务断言。诊断开销留在原预算内，不为它另延时或扣除耗时。

## 验证和后续决定

实施后静态核hook无副作用、只此case启用、target无新增易变字段、失败能保存。用已存两框核坐标公式/rounding；核null drawable、样本上限、原异常+诊断写错保因。由root统一构建/合批普通推送，禁止微推或放宽CI；新精确源码证据必须同时看差异框、公开状态时序及原预算，不能仅看成功配置。

若实测支持淡出：再独立评估呈现/取证路径性能，不在本计划改产品。必须保留用户看到的滚动位置反馈；禁止关闭滚动条/动画、改全局动画倍率、遮罩这9px、放宽像素阈值、删比较、加sleep或把5秒改大。任何性能候选都需保原采样语义及四零，并由新原件验证；不能用本次静态公式直接批准实现。

目前只规划，未改源码、构建或设备操作。无业务编码/单位/队列/未知重发变更，不发版。回退仅revert诊断增量，原失败ZIP/日志保留。本项不是Scale或ScaleSettings视觉通过。

## Astra 首轮静态复核（2026-10-23，待一项修订）

实读当前diff并核冻结SHA `9b00e1c446a604a48de7ac82b25f998271a82771707485fbdcf1a167f3f2f077`（替代早先175de9版本）。32样本上限/overflow、主线程公开getter、只forty-gesture启用、原geometry返回不含时间/alpha、原手势/等待和deadline、finally写出及原Throwable/suppressed保因均符合范围；未见这些部分的静态阻断。记录器readFailure仅意味着该条诊断不完整，不能当已取得完整状态。

唯一待修：`getGlobalVisibleRect(globalClip)`原始值被命名为 `globalClipScreenPx`，其API坐标是root，不能直接宣称screen。最小改为 `rawGlobalVisibleRectRootPx` 并明确root坐标/未映射；保留独立locationOnScreen/Window与原frame映射，后续用同源证据比对。无需另猜surface偏移或引入共享坐标重构。此项修订前不签最终通过；没有改源码或构建。

## Astra 坐标修订终审（2026-10-23）

静态通过。新冻结SHA独立核为 `864534baf8621034b2f541dbc5c77c4461849db3c3a611fc1c5bb650568c17d1`。实际读取确认global字段改为rawGlobalVisibleRectRootPx并声明root/notScreenMapped；local字段明确rawLocalVisibleRectLocalPx与getLocalVisibleRect，均未冒充screen映射。内存中逆替换这两处字段/语义追加，精确恢复前审 `9b00e1c446a604a48de7ac82b25f998271a82771707485fbdcf1a167f3f2f077`，没有其它逻辑变化。diff检查通过。

前轮唯一坐标命名阻断已关闭，可交root统一构建。新数据仍须以公开getter记录与实际像素/预算交叉判断，不能先认定淡出因果或诊断readFailure/overflow为完整证据。未改实现、构建或推送。
