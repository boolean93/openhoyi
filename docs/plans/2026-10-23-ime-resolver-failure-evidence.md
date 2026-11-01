# M2 / Import resolver 失败点只读取证计划

> 执行者按 superpowers:executing-plans 逐项执行。仅补诊断，不修产品恢复或改变探针成功标准。

**Goal:** 一次原resolver执行内留下能定位实际失败检查的证据，区分引用缺失、树内计数和host归属；不由异常类猜根因。

**Architecture:** probe为每次原install提供本次专属解析记录器；两个resolver在原顺序的读取/检查前后填入已读实值。helper原catch保存该记录和原异常后仍fallback并标PARTIAL。所有View读取与原check在同main turn。

**Tech Stack:** Kotlin instrumentation、JSONObject基础值、既有Python离线合同。

## 来源与事实

基线 `58ae69ed99c1bbde98311fed24c8bf9c056b9134`，既有develop派生 `feature/task-first-ui`，PR→develop；遵守 `docs/git-workflow.md` / `task-template.md`。root统一构建提交，不另微推，不发版。

已读 `ci-58ae69e/import-probe-resolver-diagnosis.md`（SHA `005c5626171e1d2293c0e7f9fe2b7f098d01d63f1618a235d039a465e2f92fb0`）及两个现resolver和helper install catch。Import旧Targets成功；replacement post-created真实已到，原件只有IllegalStateException，不能区分两个显式check。rawY4885→4281严格FAIL保留。后采raw存在不能回填post-created当刻。

root已核M2原包 `frame-artifact.zip` SHA `48a7eac9fcb5d1d39ca51ba8a39393502e77fb001af83ad89ffe3352844b6fc1`、1086项；同样replacement解析IllegalStateException、PARTIAL、sameField=null。本计划根据源码为M2各失败点同样取证，不声称已独立解码该包或两页有相同产品根因。

## 文件 / 实施合同

只改：

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ImeViewportTransitionProbe.kt`
- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt`
- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/AdvancedBusinessUiChecks.kt`
- `scripts/test_ime_viewport_probe_contract.py`

不动产品、native trace、collector、窗口策略或其它fixture。不是独自工作，保留他人修改。

- [ ] 先加红例：first-check、second-check、reflection/type failure各留下不同stage与原Throwable信息；null未读取与实测0计数不得混同。
- [ ] helper每次install只创建一条解析记录，字段包括fixture/phase、device elapsedNs开始/结束、mainThread、owner identity/class、stage、结果。把该记录器传给原resolver（只更新这两个调用点），不在catch重新调用resolver、不二次扫描树补“当时”值。
- [ ] Import阶段依原顺序：input读取、pageRoot读取、formScroll读取/转换、raw-count、middle-count、first-check、parent/host读取、host-count、second-check、Targets完成。记录实际引用identity/class/parent identity；计数结果保存并直接供原check使用，保持原 `&&` 短路语义：前项false时未求值项标not_evaluated，不为凑完整数字改变原路径。每个既有count调用仍在原位置，不以延后全树或候选替换。
- [ ] Editor阶段依原顺序：pageRoot、fields(owner)[key]、target非null/唯一检查、form反射、host反射/转换、host唯一检查、Targets。记录key、返回的target是否null、identity及实际count、form/host身份。原字段容器/semantic寻址逻辑完全不改。
- [ ] 通用record只容纳JSON基础值（字符串/布尔/数字/NULL），不序列化View/Throwable对象。保存原异常class、message、完整stackTraceToString及失败时最后stage；不得只存异常类。可记录已读parent/root/decor身份，不能为诊断额外找可用控件或读取raw正文。原check的真假与原异常类型不改变。
- [ ] 原helper catch保留既有issues字符串、`unknown_named_reference_resolution` fallback及PARTIAL判定；新增 `resolutionAttempts` 数组按真实install顺序写入原probe JSON。成功解析也留同格式成功记录，使旧/新可比较；不抹掉已有events/oldNewIdentity/256上限、监听计数或恢复信息。
- [ ] 诊断写字段若自身抛错，不覆盖最先原resolver异常；保留diagnostic error与partial，原finally仍先清理监听、原业务Throwable优先、后续错误suppressed。不为了诊断失败再install/retry，旧新每owner仍一次。
- [ ] 不改变postCreated接点、观察事件时序、稳定采样/5s预算、焦点/IME/scroll、原text/selection/draft等严格断言。fallback不升级成功，原null不能推成字段未存在的产品结论。

## 验证与交付

- [ ] 离线反例覆盖两页各第一/第二check失败、读取早失败、短路未求值、相同identity但count0/2、诊断自身失败不遮原错、成功记录、两owner不串记录、fallback仍PARTIAL；同时执行原14schema+1lifecycle合同，直接运行脚本，不能把discover 0 tests当通过。
- [ ] 静态核每个count保存值确是原check使用值，所有读取仍main，只有一次原resolver调用；无全局遍历/重试/任意EditText选择。
- [ ] 提交前Astra审核冻结diff/hash，root统一双包构建并按既有PR流程合批；不宣称Android探针已通过。新原件必须给出具体stage与同刻实值，之后才能判断下一窄修；若实际显示选择器边界而非产品问题，也必须另审方案，不在本计划顺手修。

## 风险 / 回退 / 未完成

无设备写入/未知记录/重发/停止，不改用户键盘。回退仅撤本项四文件增量，保留旧失败原包和原断言。等待实施与新CI，M2/Import真实IME/scroll失败仍未关闭；该证据即使完整，也不直接证明hide调用方。无发布、无强推、无main/develop直推。


## 2026-10-24 Astra 独立静态收口

当前四文件无剩余静态阻断，可交 root 统一双包构建。首次审核发现的四项原合同缺口已闭环：Import raw/middle/host 与 Editor target/host 分别保存 NULL/not_evaluated 且早于首个 View 读取；引用记录同 turn parent identity/class；finish 对每个字段独立隔离诊断失败；离线模型按两页实际 check 分开，覆盖 Import raw失败/middle失败/host null及0/2、Editor target null及0/2、不同反射/类型早失败和逐字段诊断失败。

逐段核原 resolver 读取/反射/count 顺序、原 && 短路及原异常类型未变，各 count 的保存实值直接用于原 check。新增 parent 读取只取已读对象的父引用；没有重新解析、树重扫补历史或候选字段替换。helper 每 install 一条记录、一次 resolver，原 fallback/issue/PARTIAL、post-created 接点、监听清理、256上限、5s与原text/selection/draft/scroll严格恢复均保持。诊断 put 失败不抛回覆盖原resolver异常，其余异常字段继续尝试写；这不宣称内存耗尽时仍能可靠写出任意JSON。

直接执行 `python3 scripts/test_ime_viewport_probe_contract.py` 退出0，输出原14 schema与1 lifecycle反例，新分页面resolver模型也在main内实际执行；`git diff --check` 退出0。仅离线模型和静态源码验证，未操作设备，也不是Android执行通过。

冻结 SHA256：

- ImeViewportTransitionProbe.kt：`521470066c130aaa0dfa084a1e83b7697763703be9075bf785376bea02e49054`
- CurveVisualMigrationChecks.kt：`e940c3401e39bc08bec0269c4902788bc8e7cf5443fc523c18372de8bfed9081`
- AdvancedBusinessUiChecks.kt：`3bd385439f1630a3c5ccb309e06a7348bc820d4a8724cd073add17a45fc45ecc`
- test_ime_viewport_probe_contract.py：`e75eef85600fe901f4dd7557a6ce9f1adff040c5a000cc87c5f53cf9790d14e9`

M2/Import原失败原件和严格恢复FAIL仍有效；需新同刻stage/实值证据后另判断下一窄修。本结论不修产品、不关闭IME/scroll失败、不发布。


### 构建首错后的单点增量

root 首次统一编译发现 `phase` 仅构造参数，成员 `safely` 不可引用；前述静态审核遗漏此 Kotlin 可见性问题，首轮构建未过。Sol 仅将它声明为 `private val phase: String`。独立逆替换校验精确恢复上列 probe SHA，故本次无额外行为变化。新冻结 probe SHA256 `ed03feaeb85449c4927a7a3f992c88d0dadf7f0f16c539f1f0373a47d905f1fc`；其余三文件冻结不变。该增量静态接受，实际编译结果以 root 另存的新构建日志为准，保留旧失败日志。
