# Settings 单次主线程观测内文本快照复用计划

> Sol按executing-plans实施；Astra静态验收；root统一构建/合批推送。不是独自工作，不改已提交批与他人文件。

**Goal:** 消除一次Settings geometry内重复TextView采集，完整保留JSON证据与原采图门槛；不宣称已定位右缘变化根因。

**Architecture:** 仅在每次geometry调用栈创建以实际TextView引用为key的IdentityHashMap；同一主线程观测内首次实际采集完整textLineBox，后续祖先的同节点展开复用该不可修改快照。before、after、下一attempt及所有独立observation均重新读取，完整重复JSON树仍照原格式输出。

**Tech Stack:** Kotlin instrumentation、原Android Layout/Paint/颜色证据、原NativeWindowFrameEvidence及strict JSON链。

## 来源与判断

遵守[git-workflow](../git-workflow.md)及[模板](task-template.md)。本轮基线 `58ae69ed99c1bbde98311fed24c8bf9c056b9134`，原授权feature/task-first-ui→develop，下一合批，不并入已提交25文件批。

已读 `ci-588855c/settings-machine-capture-timing-diagnosis.md`，SHA `22719f3879db0a24c997542a819859f997102b8a732a69664c143a202bffc257`。四档第一轮geometry before+after+equality共2.558–2.612s；采集各约0.62–0.69s，equality约1.26s。首轮右缘9×1167/1071变化使严格失败，第二轮预算不足；不是已证scrollbar根因，失败保全6.1s不等于延长原5s匹配预算。

源码geometry先nodes(content)，每个tag/TextView再observation(view)遍历全部后代TextView；同TextView的Layout、Paint、颜色/背景克隆光栅等被重复采集。独立检查保全light1 proof首轮before：117个node、501个textLineBox，按tag+实际origin+text关联的88组重复快照均逐字段相等。这88组是离线关联，不冒称88个唯一View身份。该证据与源码重复结构支持窄优化；不证明运行耗时必降到阈值。

TaskTypographyEvidence读取已有Layout，不重新整形；TaskVisualColorEvidence只对副本Paint和已知静态Drawable独立clone操作，自定义span不重放、未知保原unknown。主线程同一同步geometry块无事件队列让步、无业务/布局变更。故只复用同一次观测内已读取的同对象值成立，不能外推到不同主线程块/帧/不同对象。原完整成功JSON仍保留全部重复位置，equality耗时本轮不改。

## 批准范围

仅 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt`；必要新增自含离线合同 `scripts/test_settings_observation_reuse_contract.py`。Android实际getter对照检查优先作为该类内小型私有synthetic检查，无需新通用缓存框架或新业务fixture。不得改产品、shared TaskTypographyEvidence/TaskVisualColorEvidence、NativeWindowFrameEvidence、WindowGeometryJsonEquality、strict writer、runner、workflow或截图次数/阶段/门槛。

## 实施步骤与等价合同

- [ ] 提取当前observation.map中**完整单TextView textLineBox构造**为私有函数，字段/put次序/数值类型/null/UNKNOWN一字不减；保实际text.text.toString、真实Layout与全文/ellipsis/水平边界、textVisibility全部字段、actualTypography完整结构。首次采集仍在原遍历首次到达位置，不预先采集另一时间点。
- [ ] geometry开头main检查之后创建局部 `IdentityHashMap<TextView,JSONObject>`，仅传给本次nodes.map的observation。observation默认不带缓存的其它调用行为保持；几何中的每个外层view.visibility仍按原路径新建，nodes/后代顺序与重复次数不改。key必须引用identity，不能用tag/text/identityHashCode（可能相同/碰撞）。不memo整个visibility或全页面节点。
- [ ] 每个首次TextView调用原构造一次，后续同对象只复用该完整JSON值。每个外层observation仍创建独立JSONObject/JSONArray；缓存lineBox及其所有嵌套对象发布后不得put/remove/append。核所有当前消费者只读lineBox，top-level roles/action装饰仍只改外层对象。禁止toString→parse深拷贝来“优化”，禁止以引用ID代替重复JSON子树。共享引用非循环，成功序列化须仍字节相同。
- [ ] 缓存不得成为class字段、ThreadLocal、全局/weakmap或捕获到下一frame；geometry返回即丢弃map。before/after每次新map，Dialog原专用observer、独立reveal/check/capture外流程不复用。原captured.target落盘与actual-text-typography.nodes同源关系不变，不把独立事后字体文件冒充同帧。
- [ ] 不改任何原稳定signature、节点筛选/隐藏节点、Paint/clip/glyph/背景unknown、48dp/字号/fontScale、所有业务字段、protocol2四零、5s/三帧/attempt算法、原截图时序与失败保全。优化只能缩短原geometry内重复运算，不插sleep/extra frame/dump/scroll/先截图预热。

## 必需验证（先红后绿，诚实区分层次）

- [ ] 自含小树测试：一个TextView被多个标记祖先重复引用；两个不同TextView拥有相同tag/text仍分别采集；同一观测每个引用恰采集一次；新观测修改text/尺寸/scroll/颜色后必须重新读取。失效反例包含跨观测map、按tag缓存、漏字段/少展开一次、改变数组/键序、把null换缺字段、外层装饰污染共享lineBox。默认测试不依赖ignored build ZIP。
- [ ] Android SDK实际对象对照：在原业务fixture安装之前、同main的小型未挂窗synthetic View树上，使用原逐次构造与新局部复用生成完整同序JSON，断言toString(2)字节相等；包含已measure/layout的多行TextView、空文/无Layout、标准span、不同对象同tag、嵌套祖先与静态背景。不使用实际业务树双采来改变首capture时间；不运行新的Activity/设备命令。测试同时验证原View/Paint/Drawable状态未改变、两次观测间修改synthetic字段可被新map捕获。该检查结果不能假称真实机帧通过。
- [ ] 对现保全真实light1 geometry做离线完整重展开/字节对照，保持所有501位置，记录原/新字节及SHA。该回放只验证树/序列化等价，不能代替SDK getter检查；可显式命令引用本地原件，不能使正常CI测试依赖build原件。
- [ ] 独立对照原完整构造表达式diff、测试计数与hash；Sol提交冻结SHA，Astra静态复核，root统一两包构建及正常合批。现timing已足够作性能前基线；新正式frame timing仍逐项记录，不增加重复截屏或新成功门槛。必须保留before/after/equality与右缘失败数据，即便总耗时下降也不能宣布scrollbar原因或Machine验收完成。

## 停止条件与后续边界

若在SDK同main对照出现任何字段/字节差异，或发现当前getter在同main重读有可见副作用、JSON消费者修改共享lineBox，则停止这项复用，不用弱化oracle“修通过”。报告精确不同路径/值/对象；下一步仅另定只读getter次数/阶段耗时与实际scrollbar状态诊断，不改产品、不加延时。

本轮不优化equality、更换比较算法/序列化格式，不删重复证据，不扩大到M5/Curve。即使新完整frame通过，仍需实际Machine图核48dp与G1–G4，不能用旧失败target的108px记录签已完成。视觉验收沿批准执行终点：已核字体/字形不因逐glyph私有fallback不可读无限pending，不新增未计划状态门禁。

不发版/安装/硬件写入，不清原件；回退只本测试观测复用，保留诊断。只写本计划，未修改实现。

## Astra 静态验收：通过，运行效果待证（2026-10-23）

实际核两文件完整diff。Settings test SHA `651ab7c5d9fd4a5b3dd7988144183bd98b21f71379dde7d0fabcbadd958fed1a`；offline `c668e1d315086491c89d0b3916df44d040bc0d9eba4779940a0491a3f5657ac2`。完整textLineBox原put字段、次序、真实getter/null/类型均保留，只从map体提取。geometry在main检查后创建局部IdentityHashMap，首次到达原对象才读；每次geometry重新创建，无跨before/after/attempt缓存。外层visibility/JSONObject/JSONArray各自新建，后续roles只装饰外层，不修改共享lineBox。其余observation缺省null仍重读，Dialog没有获缓存；原nodes顺序/重复展开数及captured.target证据链不变，shared writer/equality未改。

synthetic在原Scope prepare/install/业务Activity启动前同main执行，仅构造未挂窗LinearLayout/TextView，没有操作真实业务树/帧/IME/scrollbar。涵盖重复祖先、不同对象同tag、实际多行Layout/无Layout、标准span及静态背景；比较原/复用JSONObject.toString(2)，核原状态不变，并在synthetic修改后新map重读。源码可编译性仍由root统一构建确认，Android synthetic目前未执行，不能把Python模型当SDK getter证明。

独立3 host tests通过；显式真实ZIP回放117nodes/501lineBoxes，Python有序JSON展开前后4069636B、SHA均 `8edaaa3d1f329f0e9f9ad5813b9739dbfd009c6f0e555389feaddccc97100e63`，不冒称Android原toString字节oracle。git diff --check通过。没有改变5秒、四零、attempt/采样阶段、成功证据字段、触控/字体/clip/业务断言。

静态批准root统一构建及按仓库流程合批；Android synthetic若失败必须保原错停，不能删除该检查或改几何字段通过。性能收益、Machine正式accepted frame及视觉均待新运行；首轮右缘变化仍不能唯一归因scrollbar，旧Machine失败不关闭。未改源码/构建/推送。

## Root 构建取证（2026-10-23）

本地 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 构建退出0，35秒，91 tasks（4 executed）。日志 `build/task-first-evidence/ui-58ae69e-followup-build/settings-build.log`；构建前后303个Kotlin源文件SHA完全一致，保存在同目录 `settings-source-before.json` / `settings-source-after.json`。两包SHA记录于 `settings-apks.json`：Mock `11216d3d173e24392f36fe231a10b97ae1952af15b26424a483e923c1e876ccd`，测试包 `53223346eaeb0546b2b2e3875ec7545ba236846be00d96a85722338957046883`。

这里只确认两包编译成功。Android synthetic 尚未运行，真实Machine截图、性能和视觉验收未关闭。未安装、未操作设备、未发布。
