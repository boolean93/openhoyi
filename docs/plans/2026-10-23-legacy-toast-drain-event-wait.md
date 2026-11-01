# M7 Toast drain 事件等待最小计划

> 执行者按 superpowers:executing-plans 逐项执行；本计划只授权下列测试与离线测试，不授权生产行为修改。

**Goal:** 将等待原 Toast 真实 hidden 的空转改为同一操作内的有界事件等待，避免无变化 round 无限增长；保留完整实际证据和原期限。

**Architecture:** 每个现有 ToastLedger.Operation 持有独立一次性完成信号；main callback 只记真实事件并发信号，instrumentation 线程只等剩余预算。醒后重新走原 reader/main/Toast 严格检查，信号本身不是成功。

**Tech Stack:** Kotlin instrumentation、原 Toast.Callback、java.util.concurrent.CountDownLatch、现有 Python 离线合同测试。

## 来源、范围与边界

- 需求：58ae push39915609620 首格 finally `readerDrainProofs.toString(2)` OOM，非 layout 首败。原 log 已实读第216/235行：申请37748752B，heap上限201326592，LegacyVisualMigrationChecks:1833。完整 ZIP SHA `a2bb3be698fe4110fd94f05f22bc4a93031000db70b703caf255e48b5381f0b0`。
- 来源：既有 develop 派生 `feature/task-first-ui`，冻结基线 `58ae69ed99c1bbde98311fed24c8bf9c056b9134`；PR目标 develop。沿 `docs/git-workflow.md` / `task-template.md`，不是新发版。
- 已读实际125–166行循环：无活reader而 `settled=false` 时，循环没有阻塞，持续 stack scan、main barrier、round JSONObject；finally一次性完整转字符串。旧原件没有 reader-drains.json，因此不伪造具体round数量/大小。
- 已读真实 toast-lifecycle：5个 LONG Toast 的 shown→hidden 各约3.79–3.81s，所有 hidden 在各自10s期限内；scopeClosed=true/pending0。循环空转导致证据扩张是源码与失败路径支持的机制，不宣称已测到每次分配因果。
- 不提高heap，不截断/删round字段，不去重丢实际观察，不变Toast文本/时长/show/cancel，不改产品；不改截图5s/四零或10s总截止。不能加固定sleep、重拍、重试业务。

## 文件所有权

只改 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/LegacyVisualMigrationChecks.kt` 和 `scripts/test_legacy_toast_lifecycle_contract.py`。不改 WindowProofStreamingWriter、生产observer、runner或其它JSON写点。Settings等他人修改必须保留。root独占统一构建/提交/推送。

## 实施步骤与合同

- [ ] 先写红例：模拟一个原operation先绑定、shown后迟到hidden；无live reader但Toast未结束时，旧循环能反复追加round，新候选必须阻塞等待真实事件；不是每20ms轮询。
- [ ] Operation创建时加独立 `CountDownLatch(1)`。它不是业务状态；cancel操作不需要等待。保持现有startedNs/deadlineNs，不另起10s。
- [ ] 现有 `onToastHidden()` 必须先原样追加Event，再 `countDown()`；禁止在shown时放行。异常归属/重复绑定事件也须唤醒等待并在main上按原严格合同失败，不能把invalid当hidden成功。信号每operation独立，不能由旧Toast隐藏解锁新operation；不复用全局latch。
- [ ] 为drain增加一个可选的“取得当前operation待等信号”接口，仅 callback(activity-result...)传入；scope-cleanup维持原行为。接口只在既有main快照内调用，冻结确切operation及信号引用，不在后台读取ledger列表或View。
- [ ] 保留 reader join、main barrier、重新发现successor reader及原两次确认链。当已无live reader但真实当前Toast生命周期仍待hidden时，在instrumentation线程对已冻结信号执行 `await(remaining, TimeUnit.MILLISECONDS)`，remaining严格用原deadline减当前elapsedRealtime。不得阻塞main。无绑定但预期Toast的operation也可等同一个信号，main回调仍能执行；缺callback到原期限必须失败。
- [ ] 等待后无论返回true/false都重新main检查原ledger/readerReady；true不是closed，invalid/错序/晚hidden不得通过。timeout抛原类型失败并保失败原件；新事件状态仍按原 `show-request→shown→hidden` /身份/source/pid/main/期限校验。已在等待前hidden由latch记忆避免丢唤醒；cancel无Toast直接沿原确认链。
- [ ] 完成信号后若readerReady仍不成立，仍走原reader发现/确认而非误报成功。禁止为了消除空转吞掉异常、跳过最终no-live-reader guard。现有round全部保留，可给实际执行等待的round追加wait开始/结束/remaining/返回值；不按轮次或大小截断。
- [ ] 保持 callback finally 的finish/首错+suppressed；等待/ledger快照失败也进入同链，Scope cleanup照常。`reader-drains.json`写盘时机、文件格式及原失败优先关系保持；本项先消除已证空转，不顺手改shared writer。若实现实际压力测试仍显示单次JSON峰值不可接受，停止并交证据申请独立JSONArray strict writer方案，不擅自包装对象改格式。

## 验证与验收

- [ ] 离线增量覆盖：hidden提前到达不丢信号；真实延迟hidden仅有限实际round；shown不放行；无hidden超原剩余预算失败；旧operation hidden不解锁新operation；错误/重复归属唤醒后严格失败；cancel无Toast不等；reader successor仍被join并二次确认；hidden完成但readerReady=false不接受；中断/检查失败保原Throwable，cleanup错误suppressed。
- [ ] 同步原六项生命周期/轴合同回归。离线线程模型只证明等待握手和边界，不替代Android callback运行。
- [ ] 在同192MiB限制的模型中模拟五次延迟hidden并检查完整JSON可写、所有实际round保留；报告实测round数量/字节/峰值，禁止用空JSON代真实形状。仅模型结果不声称正式设备OOM关闭。
- [ ] root统一Mock双包构建，Astra审真实diff与hash后纳入下一批；不微推。精确source新CI需保完整 reader-drains、toast-lifecycle、manifest，确认每次总deadline未延长、actual hidden已结束、原严格layout/protocol保持。直到该证据出现，M7 OOM仍待运行关闭。

## 风险、回退、未完成

没有设备协议/未知记录/重发/停水变化；测试只读实际Toast事件，不隐藏用户反馈。回退只撤这两文件的本项增量，不卸载/清记录/删除旧失败原件。普通推送工作分支→PR必需检查→授权合并，禁止直推main/develop，不发版。当前只完成计划，未实施/构建；font恢复后错独立保留，不能据本项关闭。

## Astra 冻结实现静态验收（2026-10-23）

**静态通过，可交root统一构建和精确source CI；不宣称Android OOM已关闭。** 实读两文件完整diff并独立核SHA：

- LegacyVisualMigrationChecks.kt：`8d04bf1530f6d6a67a119426f2794fd060914943988f7ba954dfb45004d4ee16`。
- test_legacy_toast_lifecycle_contract.py：`43be021086304934d223faa10eeffe62cb0f9733cd7e5e23c8bdb19bc0599275`。

每operation独立latch且callback闭包捕获原operation；hidden先记录再唤醒，正常shown不唤醒。绑定错误及错误shown可唤醒但pendingSignal在main严格拒绝，不能转成成功。信号身份用同对象比较；cancel不等、已hidden不会丢唤醒。没有替换原closed条件。

等待只在无live reader且ui未settled、有该operation信号时发生；剩余时间来自同一原deadline，未新开10s。await在instrumentation线程，醒后main重新核原readerReady/ledger并重新观察reader，再continue回原join/两次确认及最终no-live-reader/deadline guard。即便await返回true，超时或错序仍不能通过；无hidden仍按原期限失败。hidden已结束但readerReady为false时不凭信号接受，仍沿原reader检查。原finally/写盘点/异常聚合不改；所有实际round保留，仅新增实际等待字段。

独立执行 `python3 -m unittest discover -s scripts -p 'test_legacy*contract.py'`：12 tests通过（含原轴合同）。模型本次5操作/10 rounds/7189B，192MiB JVM上限201326592B、报告峰值30486528B。**准确限制：该Java模型只对Python预生成JSON字节读写和SHA核对，未在JVM构造Android JSONObject树或执行原toString(2)，不是Android序列化峰值/真实Toast时长验证。** 它支持完整小模型记录保留，不支持声称已复现并消除真实OOM。测试中缩短延迟也不替代真实3.8s callback运行。

本次静态判断的依据是实际Kotlin阻塞消除了已证“等待hidden期间无阻塞追加round”路径，而非模型内存数字。正式运行仍须取得完整reader-drains/Toast生命周期及原10s/5s四零证据；其它readerReady长期不成立、scope-cleanup异常等原失败不得通过删证据掩盖。没有扩大writer/产品/runner/时间门禁。
