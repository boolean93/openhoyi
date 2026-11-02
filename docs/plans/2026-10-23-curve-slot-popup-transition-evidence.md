# Curve 槽位选择 Popup 过渡识别

- 范围：仅原`preset-eligibility-and-slots`点击“分配槽位”后识别原Popup过渡，保真实AlertDialog身份与原slot await 10秒截止；独立frame仍为5秒。不得吞未知窗口、任选候选、重点击或修改产品。
- 依据：`build/task-first-evidence/ci-3a78c4c/curve-first-failure-diagnosis.md`，run39889375949，head3a78c4c。首错是`PopupWindow$PopupDecorView`不存在mWindow；日志证明测试类型假设错误，不证明Dialog是否已出现或后续槽位成功。报告当前仅有日志，不能冒称已审artifact/XML/PNG。
- 基线/工作流：沿用户授权全24页迁移`feature/task-first-ui`→develop，遵守[git-workflow](../git-workflow.md)及[task-template](task-template.md)。同Sol在manifest任务冻结后串行，保留他人改动；不微推、不直接推主干、不升级依赖。
- Owner文件：`mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt`及`scripts/check_curve_visual_migration.sh`必要的精确过渡记录校验；仅现slot流程。其他parameter AlertDialog调用不默认获得Popup豁免，不改NativeWindowFrameEvidence或生产监听。

## 最小实施合同

1. 每轮slot在原`popupEvidence`之后、原`choosePopup`之前，在main从同一个已确认菜单ListView向上找到其实际Inspector root。要求唯一、非Activity、attached、真实PopupDecorView，ListView确为该root后代，token非null且等于list token。保存原root对象引用、token引用、identity/class/display及实际菜单items。不能凭class名称找任意Popup作为待忽略对象。
2. 窄扩`libraryWindowDialog`以可选参数接收这次已记录Popup身份，默认null保持其他调用严格行为。每次await读取Inspector所有当前attached foreign roots，记录class/identity/token/attached/laidOut/focus；只对**同一root对象且同一token/display**的已记Popup允许pending，绝不在它上反射mWindow。旧root身份相同但token变更、陌生Popup、未知foreign root、两个真实Dialog候选均FAIL，不能catch反射异常后普遍return null。
3. 非Popup候选先确认是现SDK的真实Window decor类型，再按已有只读mWindow路径取得Window；反射缺失仍FAIL。始终核Window.decorView===root、callback确为AlertDialog、dialog.window同一Window、isShowing、Context/owner为当前Activity、root/list/token/display一致。未laidOut/焦点尚未就绪的**已识别合法Dialog**可在原await内等待，不重置截止；未知类型不能以“未ready”过滤掉。
4. 若只剩原Popup则pending；若原Popup与唯一合法Dialog同在，只有Dialog聚焦/laidOut/no-layout-request且原Popup已失焦，才可就绪。原Popup仍聚焦时pending而非选一个；Dialog就绪记录旧Popup失焦或已detach/消失的事实。无Dialog则继续原截止，超时仍FAIL。不要为这个过渡新加sleep、加长await或另开第二窗口预算。
5. 在第一次chooser的现protocol2 observe中复核相同Dialog/Window/decor/token与归属、原Popup仅允许上述状态，将身份及foreign集合观测并入target；所有5轮选择前也核对应本轮Dialog身份。原独立WindowBufferTranslation、完整五行/标题、48dp等已有条件及5秒/四零差保持。不得将Popup root或Activity截图冒充chooser证据。
6. 每轮保存短小transition记录，含预点击菜单身份和按顺序观察到的root状态、pending/ready理由；失败也先落记录再由原异常退出，不能为落证重新dump/点击。chooser成功后仍用原list.performItemClick执行原槽位测试、原prefs白名单/落盘/隔离恢复不变。未知写入不重发。

## 验证与交接

- 离线分类反例至少覆盖：只有原Popup→pending；原Popup失焦+唯一合法Dialog聚焦→ready；原Popup仍聚焦→pending；陌生Popup/同root换token/双Dialog/错误owner→FAIL；无窗口→pending至原截止。不能把这些输入当真实Android窗口证明。
- runner要求首chooser身份/过渡记录与accepted target匹配，精确5轮记录、原菜单与Dialog唯一性、原五槽白名单/恢复；不以`transitionCompleted=true`替代字段。其余Window proof检查不改。
- 冻结diff/hash交Astra静态审；root唯一完整构建与下批普通推送owner。继续既有Curve矩阵，不手动新增设备场景/CI。不签后续五槽、其他配置或视觉通过。
- 协议与设备风险：只修测试识别器；不改业务编码/单位/队列、原菜单监听、槽位语义或设备命令。发布：不发版/安装/分发。回退仅revert本窄增量，保留原失败日志和未知记录；不清数据。
- 未完：首轮真实过渡时间、完整chooser原件及后续路径尚待新运行。当前计划未开发/构建；不同时推进M8/Legacy。


## Astra 静态终审（2026-10-23）

结论：两文件窄增量静态通过。独立读取实际 diff 与调用上下文，SHA 与冻结值一致；不以离线分类或构建成功替代真实 Popup → Dialog 运行证据。

- 事实校正：已对照 HEAD 与当前源码，原通用 await 截止均为 10000ms，slot chooser 沿用它；独立 frame 协议仍为 5000ms / 四零差。原计划首段“现5秒截止”混淆两者，现已更正。此次实现未加长任一截止、未新增 sleep 或重点击。
- 每轮先从同一实际菜单 ListView 的祖先确定唯一 Inspector root，保存原 root/token/display/items；仅此对象可作为 Popup 过渡。附着 foreign roots 全枚举，未知类型、token/display 变化、双真实 Dialog、错误归属均直接失败，不捕获反射错误后继续。
- 真实 Dialog 仍核 Window / callback / decor / owner / isShowing；就绪再核 list root/token/display。原 Popup 尚聚焦不能选择；其失焦或已消失且唯一真实 Dialog 就绪才返回。默认不传 Popup 的其他调用没有豁免。
- 首 chooser 的协议 target 内再次核同一 chooser 并记录 foreign 集合；runner 将 accepted target、selected、Window/decor/token 与本轮 transition 绑定。五轮精确枚举和 SHA 引用、原 menu、槽位 prefs 白名单、落盘与恢复链保留。原实际 performItemClick 未改变。
- 过渡状态在原 await 内观察，状态变化才写记录；首次点击前已有原菜单记录，await/点击失败保原错误并尽力附加写盘错误。没有用 Popup/Activity 截图代替 Dialog 证据，也没有对未知窗口任选第一个。
- 测试源码 SHA256 `b407c27fe6c738abcd1b82942dadaa94d55f3b9f18b880b40bfbfdf0a00780dd`；runner `3855f24476e3a8a51e27666d9de6df65209636f33cc033318fc431ffbf6e8123`。独立 bash -n 与 git diff --check 通过。Sol 报告的 8 项离线分类仅为分类验证，本次没有重复运行；root 报告完整构建通过，非本审查执行。
- 未关闭：真实首轮过渡、chooser 原件、后续五槽与各配置仍待新运行；无视觉或整 run 通过结论。本审查只修改本计划，不改源码、不构建、不提交推送。
