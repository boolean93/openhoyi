# M7 原导入 Toast 生命周期取证计划

> Sol按executing-plans实施；Astra验收；root唯一完整构建/合批推送owner。保留其他人的改动。

**Goal:** 证明本fixture发出的每个导入Toast真实结束，再进入后续采证/下一进程；不隐藏用户反馈。

**Architecture:** 在原两个Toast创建点暴露默认null的窄只读观测口。仅仪表安装观测器，给实际原Toast注册公开Callback，记录shown/hidden，在现有导入reader/main收尾10秒截止内纳入条件；不改frame协议。

**Tech Stack:** Android Toast.Callback（API30+，当前采证SDK34）、Kotlin instrumentation、现有host verifier。

## 基线/已证边界

遵守[git-workflow](../git-workflow.md)/[模板](task-template.md)；当前`feature/task-first-ui`，基线588855c96fdceec79bf87f0fd5950f33fa6aff1c，PR目标develop。读`ci-8b52379-legacy-pr/main-toast-occlusion-diagnosis.md`及`2026-10-23-m7-main-pages-visual-review.md`：旧PR tested merge44d34c52a3f3b18537f59cb92607bcc7c6252d09的History四图、Detail两图有Toast遮挡。文案吻合前页导入，但旧证据无token/起止时间，不能倒推出唯一来源。固定curve→history→detail与force-stop不证明系统队列清空；Activity四零也不证明全局overlay缺席。

## 批准的最小文件范围

1. 新 `mobile/src/main/kotlin/io/openhoyi/mobile/LegacyImportToastObservation.kt`：仅internal、默认null的观测回调，接收原Toast引用与明确source标识；不持久化、不接设备、不自造Toast/全局窗口推断。正常生产null时不注册Callback、不等待、不保存上下文。
2. `LegacyCurveActivity.kt`原导入结果Toast和`MobileApplication.kt#importLegacyHistory`原Toast：在原makeText得到对象后、原show前调用该观测口。原context、文案、LENGTH_LONG、show次数/时机、refresh/选择与线程顺序不变；不cancel，不改duration，不用自绘Toast。
3. `LegacyVisualMigrationChecks.kt`：仪表专用实际Callback及生命周期记录，安装/恢复观测口必须同main，Scope还原原引用；不覆盖已有非本scope observer。新增记录纳入manifest SHA。
4. `scripts/check_legacy_visual_migration.sh`：精确核本页原导入case对应记录、全部结束、scope关闭、source/profile/page/PID身份；在现有result成功路径、下一轮force-stop之前读取并验证本页小型终态JSON，失败立即停，保原件。不能仅等全部12页后才发现前页Toast未结束。
5. 可新增 `scripts/test_legacy_toast_lifecycle_contract.py`，只用仓库内小型构造对象/导出纯验证函数，不依赖ignored build原件。不得增加新业务fixture。

## 执行合同

- [ ] 先写离线状态机/host反例。仪表在每次原非取消导入之前同main分配唯一operation序号，捕获scope/source/page；仅实际上述创建点可将一个Toast绑定此operation。取消操作必须0个Toast。非取消各原case必须恰一个；缺创建、多创建、非预期source、跨operation、回调重复/逆序全部失败，不能以“0个pending”掩盖没收到事件。
- [ ] 在`.show()`之前给**该对象**注册Toast.Callback；记录对象identity、source、operation、PID、elapsedRealtimeNanos、主线程身份、声明duration、show-request/shown/hidden。用observer序号标识对象，不将identityHashCode当跨进程系统token。不得反射Toast私有token、查任意窗口或找一个新Toast替代。
- [ ] 只扩原`callback()`之后`drainReaders(... settled)`条件：原reader/界面就绪 AND 本operation实际shown后hidden。共用现有**总10秒deadline**，不为Toast额外重置/延长，不再套第二个10秒await；可通过现有主线程barrier观察公开回调，不加固定sleep/自动重试。正常LONG应自然结束后才触发下一原import，避免本fixture自行堆积Toast队列；用户仍看到完整原duration。若系统丢弃、限流、未shown/未hidden/SDK不支持，一律留下实值失败，不推定已消失。
- [ ] 本页原imports最后一次真实hidden后再执行原结尾capture/close；scope退出先drain所有本scope异步reader和main回调，再确认注册过的操作逐一闭合、没有未归属事件。失败时照常留生命周期诊断，cleanup错误suppressed到原Throwable；不cancel Toast来“恢复”。observer恢复不能抹掉未闭合记录或将其改PASS。
- [ ] 下一页main捕获前记录本进程无本scope在途Toast，原main先采/初态不改；host必须已验证前页闭合记录才允许原下一步force-stop。所有main/state截图仍原5秒四零、原窗口身份、原12配置/页顺序。未知/外部系统Toast不被本记录认证缺席，proof明确命名`owned_import_toasts_ended`，不能命名`global_toast_free`；实际新PNG仍由Astra核遮挡。
- [ ] host验证精确case集合（从现archiveImports逐case冻结，不只count>0）、取消0/非取消1、source与运行身份、shown<hidden<=operation原deadline、main线程、pending=0、完整文件SHA与refs。不同进程记录不可串用。正例与缺hidden/无shown/双实例/错source/跨case/超期/旧PID/缺记录/取消有Toast等反例必须拒绝。原timeout与门槛不改。

## 验证/交接/风险

API30以下正常产品null observer路径不调用Callback；仪表采证前精确声明SDK能力，不做“兼容成功”fallback。离线测试仅证明合同，不能证明Android真实Toast回调。Sol提交实际diff、源SHA、离线结果；Astra检查两个原show点无行为改动及同一对象注册；root统一编译/合批普通推送，必需CI通过后PR合并develop，不微推/force/no-verify。新运行逐case回调和12原主图才闭环旧遮挡，旧图不覆盖、不重拍循环。

不改硬件写入、未知重发、原数据、Toast文案/时长、采图预算；不发版。回退只revert本观测/测试增量，保留日志/ZIP，禁止删记录恢复。当前计划可实施；不能声称全局系统窗口无遮挡已解决。

## Astra 静态复核：冻结候选待修（2026-10-23）

实际读取三生产文件、仪表、runner及离线合同；SHA：observer `755cef105af906ee5b92513b8f2049b1a3241a84295f62b0bf96f98da7312e0b`、Curve `34be35f12bf588cd9907d291adece849a118302eea85ccf42b73896b31e29e0c`、Application `24c6c3c9562c91d6df1c99104f45ef74dfcfb7342fbf286298c50c6224520ac0`、Legacy test `74374d3eac11cf9c758ffbac4fa19af7d38442a3fa400671f5d0e5f2ca3e938d`、runner `f91a831a7fe848fe379f8ff026e76dd932b73ad4256fb2864f99ba0ff8e38f22`、toast oracle `57b1dcd759cdcde9d05f4d620e22c357a277b06fdb903ec302e3b1cdd665f0b5`。

原 Toast 对象/文案/context/LONG/show次序保留；observer 默认null，公开Callback仅本scope绑定；取消0/其余1、source/page/profile/PID、逐事件截止与下一页force-stop前SHA验证均有落实。reader使用传入deadline，未新增Toast等待预算，frame原5秒不变。仅证明owned imports，未宣称全局无Toast。

**阻断：** `Scope.cleanup` 的新observer还原/check及文件写入位于会抛错的finally，可能覆盖原cleanup Throwable；`callback` 的begin/invoke在try外，finish finally也没有主错保护。修订限此新增生命周期：将begin/invoke/drain纳入统一主错捕获，只有实际begin成功才finish；finish/observer所有权校验与还原/落盘各自保留错误，首错原对象、后错suppressed。不能吞所有权失败或覆盖别人的observer，不能把未闭合操作改PASS。对observer校验失败也尽力留原诊断，不让写盘覆盖前错。另将毫秒deadline从同一startedNs派生，避免第二次读时钟得到稍晚截止。

独立运行两个合同模块共6 tests通过，但不覆盖上述真实Kotlin异常控制流。本候选不予整组静态通过；修后只需增量复核，不改生产Toast行为或门禁。

### Astra 第一次增量复核（2026-10-23）

已核 test `88d3802462032e456083c567176779723eb87a44bcdf894f460b7062df4aa216`、axis oracle `a2cbeaaacfd268b32a641c3a1311aed7b31d0cff5eca0e75aa0eeb97fd451fca`，独立6 tests通过。Toast begin/invoke/drain统一try，began守卫finish，callback/cleanup原Throwable及suppressed后错、observer所有权失败不覆盖他人、同startedNs截止已闭合。axis原引用/scroll/尺寸与initial对照已移至同main；host从完整Canvas派生全部index/role/text/glyphBounds，三态固定Canvas与目标集合一致，原漏项漏洞已闭合。

仅剩axis同main块之后重复check仍有 `&& chart.points==scope.mainShot.points` 非主线程读取；该断言已在上方main保留，请仅删除块外重复部分。余项不需重做；新hash窄核后再签整组通过。

### Astra 最终窄复核：静态通过（2026-10-23）

新test SHA `0b474122e54f10d332968a92f3e554c23049a87a42a5335238e0ab93340a87fc`；独立逆补唯一块外重复points条件，精确还原前版 `88d3802462032e456083c567176779723eb87a44bcdf894f460b7062df4aa216`。main内points严格检查仍在，未降门禁。此前三阻断现均闭合，结合前次8文件实审与6tests，本M7/单屏/badge增量静态通过。root可继续统一构建与合批流程；不等于新Toast/轴字/badge/单屏真实运行或视觉验收，仍待新原件。无源码修改。
