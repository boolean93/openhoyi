# Settings geometry：文字采样快照合同窄修

## 决策

不扩 comparator 的 Spannable/Editable 白名单。不是继续声称“任意可变对象与旧序列化逐字等价”的性能优化；改正 Settings 取证 producer 将活的 `Editable` 引用塞入历史 geometry 的语义。每次原主线程 geometry 采样期间，把该 TextView 的当前文字复制为不可变 String，与同次实际 Layout/样式字段一起保存。before 与 after 各自独立采样，不共享活文本对象。

来源 `feature/task-first-ui` 当前候选，合批 PR → develop，不发布/设备/签名升级。仅 `SettingsVisualMigrationChecks.kt`、Settings runner及小范围既有SDK34 oracle测试扩展；不改产品、不改 `WindowGeometryJsonEquality`、不改 NativeWindow 的采样顺序/预算。owner与Scale补证同人顺序实现。回退为本窄改独立revert，不能将旧原件重标新合同。

## 已证问题与非结论

证据 `ci-643d828-settings-pr/comparator-fallback-type-trace.md`：App selectable package TextView 与 Machine EditText 经 `observation` 的 `put("text", text.text)` 原样入 JSON。SDK34 put(Object)不复制；旧JSON序列化在比较时才调用toString。所有8初态有该链，必回退。但不能据此断言它独占耗时或是所有像素差/超时的原因。

mutable Editable 若在before采样后更新，原before JSON中的文字也可能随之改变；等到比较再序列化可能看到与after相同的新字，而不是原采样文字。即使其他Layout字段偶然抓住这次变化，也不修复该字段的历史快照语义。仅加入已知类白名单、逐叶交错toString既不能保持旧读取顺序，也不能使before变成真实历史快照，因此拒绝。

## 最小实现合同

1. 仅在原 `observation` 主线程采样里，将写入 `textLineBoxes[*].text` 的 `text.text` 改为当次 `.toString()` String。不在比较阶段偷偷重读View，不把after的文字回填before。保原字段名、完整UTF-16内容、空串、换行、Unicode、无trim/归一化/省略；文字输入仍沿用真实原对象和监听，不改UI。
2. 明确这是该字段的**取证语义修正**，不是旧活引用在并发变化时的行为等价。给 Settings `geometry` target 加固定版本标识 `textSnapshotContract = "SETTINGS_TEXT_AT_MAIN_THREAD_OBSERVATION_V1"`，runner要求新候选各capture该标识。原protocol2/5秒/四零差/全字段有序比较合同不变；不以该标识代替任何稳定判断。旧643 proof没有此标识，保留原结果。
3. callback既有main线程约束必须可核（producer入口可check主线程）；同一次geometry不yield。只复制文字，原actualTypography/Layout/颜色/span证据继续独立完整采样，不删spans，不把纯text等价当渲染等价。不同spans同字面时，原样式证据与最终像素门槛继续负责区别。
4. Settings源码范围检查其他 `put` 是否还传入活CharSequence；仅这类明确文字字段同样转String，给出精确diff列表。不得递归调用任意对象toString“清洗全树”，不得改变Number/NULL/tag/对象顺序。其它页面和未知custom对象仍由原比较器整对旧路径fallback；不要承诺此次所有geometry必然fast。
5. 每次采样耗时仍在原5000ms预算内，不能将复制前移到预算外。所有原按钮、业务、12格隔离、恢复/失败保因都不动。CPU/内存收益需下一原CI实测，不报host结果为Android性能。

## 必须验证的反例与不变量

复用官方SDK34 org.json离线oracle，不新建取证框架。新增明确分类的producer语义测试：

- 活MutableCharSequence A入before，源改B后after入同源：旧序列化可见B/B；新采样String分别A/B且比较拒绝。此案预期**新旧结果不同**，报告不得列“全等价”。保留更改源后before值不变断言。
- 内容未变时：String/可变CharSequence转String所得完整文字与SDK旧JSON字符串编码一致；转义、换行、空串、中文/补充字符/未配对surrogate保持原UTF-16，不用trim。
- before采样完成后到after采样之间多次变更，before始终固定；after取当次值。只证明采样值，不声称捕获两次采样间所有瞬态；最终四零差门槛不降。
- comparator未知custom/抛错对象及非有限number原fallback/null行为不改，既有57case继续有效（不用改变白名单以通过）。

离线可用受控MutableCharSequence证明JSONObject活引用反例，但明确不是Android Spannable实现测试。下一既有 Settings CI 中 actualTypography 的真实EditText/selectable TextView、同采样字段、marker与源diff共同验证真实producer路径；无需新增业务scenario。若要补Android类型小测试可在既有测试前置局部构造真实SpannableStringBuilder，不能启动额外Activity或改产品文本来凑通过。

runner逐份从accepted原geometry重核marker/完整文字字段仍在，保持原geometry==accepted target与whole/Layout/颜色/像素/身份验证。静态复核 → 统一AndroidTest构建 → 原12格CI；未过仍FAIL，不增加时间或等待、不隐藏滚动条。

## 限界

这是App和Machine共同producer的一次修正，不做“只放SpannedString、Machine继续回退”的微推。若还存在其它非原生值导致回退，保留，不额外扩大本批。异步后台直接非法修改Editable不由本测试修复；主线程采样后不可变快照仍避免历史值被之后合法UI修改污染。

## 实施静态终审

Astra 已核三文件实diff及冻结hash：Settings测试 `d3882869abdbac892f8b54c201d5a280f43f76cc9d47f32f1231b2109f6dd7c1`，runner `1cf61e2729af82aace1ca4a8fd8f17c301a14f022a12b92bc849fed115a68b19`，既有oracle `4f4fdfc2a4bca21f3f505c968b2ad84efc9a29d5a1878c0e8a3b2d3796a80bb6`。

静态无剩余阻断：原raw text引用在observation内复制String，并给该次actualTypography复用；Activity/Dialog geometry主线程检查与V1标识俱全，现有diagnostic调用也在main包裹；runner对accepted before/after核标识及文字同型同值，原文件与同次target相等。比较器/采样时序/5秒/全字段/像素门禁未改。

oracle保留原57比较契约并新增7个producer语义例；A→B→C活引用反例明确标成故意非等价，未冒充Android Editable本机实测。未知抛错custom仍走原fallback。静态审读取64例报告与源码，独立bash-n通过；不把host耗时当Android收益。允许进入统一构建/原12格CI，旧643超时与像素差未关闭。
