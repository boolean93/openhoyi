# Brew Preparation Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将真实Home准备页改为阶段预览＋本杯摘要工作台，减少参数/槽位/工具同时抢占主界面；保持原设备/安全/槽位/库存语义。

**Architecture:** 原生View，不新增BLE或业务所有者。新TaskWorkspaceLayout负责真实可用宽高下两栏/上下布局，保留子View实例；Home原render与设备面板控制代码不替换。新增摘要只读取已有曲线、永久使用台账和库存/用豆准备，不触发reconcile/confirm/consume/select。启动入口本小批仍转到原Extraction准备与确认；紧接着的共享启动确认任务才将Home主操作收口为规格里的“开始萃取”，不以当前保守入口冒称Home最终完成。

**Tech Stack:** Kotlin、Android原生LinearLayout/ScrollView/Canvas CurveStageView、现有JVM/Mock instrumentation和GitHub Actions。

## 治理与范围

延续feature/task-first-ui（develop66ebda9＋显式ed01de0依赖），PR#4→develop，遵守docs/git-workflow.md。普通追加提交，不force，不合并旧PR，不发版/签名版本变更/用户设备安装。不改协议、schema、未知记录与既有门禁；失败追加修正或revert PR，保留原数据。

已认可全21页范围不变。本批只改Home视觉/只读摘要和共享布局；不是实现全部冲煮任务，不以旧ImageGen图代替真实渲染。参考docs/superpowers/specs/2026-10-15-task-first-ui-design.md §3/6/7/9与docs/research/task-first-ui-concepts/brew-preparation-v2.png。

## 文件责任

实现者仅拥有：
- Modify `mobile/src/main/kotlin/io/openhoyi/mobile/HomeActivity.kt`：布局构造及只读新字段render；原服务连接、写入确认、门禁、原callback和数据方法不改。
- Create `mobile/src/main/kotlin/io/openhoyi/mobile/TaskWorkspaceLayout.kt`：双区域自适应，primaryBody/secondaryBody稳定实例，后续Extraction复用。
- Create `mobile/src/main/res/values/task_brew.xml`及7个已有语言目录同名资源：新准备页文案的基本翻译，不关闭MissingTranslation。

控制者拥有所有tests/runner/scripts/CI/evidence和HoyiUi.header的公共24sp字号调整。不要还原/提交别人的文件。

## Task 1：共享任务区域

- [ ] TaskWorkspaceLayout(context)对外：

```kotlin
internal class TaskWorkspaceLayout(context: Context): LinearLayout(context) {
    val primaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    val secondaryBody = LinearLayout(context).apply { orientation = VERTICAL }
    // secondaryBody is inside one local ScrollView; primaryBody and the whole page never scroll.
}
```

- [ ] onMeasure按configuration窗口宽（600/840策略）＋容器实测宽高：仅当TaskLayoutPolicy.decide(windowWidthDp,actualHeightDp).twoPaneAllowed且容器宽>=320+280+24dp才横排；不能仅HoyiUi.wide或orientation判两栏。横排主区权重1、辅区宽280–320dp（按实际总宽约30%），24dp间隔。主区至少320dp，不能双扣轨宽。上下布局保留主区权重剩余、辅区局部ScrollView AT_MOST min(160dp,可用高约1/3)，让长说明/大字号可达但不挤没主预览。
- [ ] 模式切换只改orientation/LayoutParams，不remove/add业务内容、不重建主辅View、不performClick，不处理Insets（root仍唯一owner）。高度确实不足96dp预览时由Home减少装饰/详情占高，不悄悄缩字号或遮安全/停止。

## Task 2：Home任务布局

- [ ] header标题改为“准备冲煮”，保留Mock实际模式提示和明确“设备连接”按钮。当前完整安全警告/acknowledgeManual/告警仍在workspace之前，优先级及显隐判断不变；emergencyStop仍是root固定操作，原StopActionPresentation原样保留。
- [ ] coffee/scale状态芯片合为紧凑设备摘要，连接≠数据新鲜；仍用原字段/原render，长文完整可达。四个原温度/压力TextView保留并放设备面板“机器读数”组（同一实例，原raw来源不变），不再把蒸汽信息铺在主任务上。不能删除DevicePanel内任何扫描/候选/权限/固件/告警/拨杆/睡眠/设置/断开/秤读数/去皮/停服务功能或其确认/恢复文案。
- [ ] content余高加TaskWorkspaceLayout(weight1)。主区：当前配方title（24sp，摘要允许两行省略但原click完整内容入口仍48dp）、CurveStageView阶段目标预览（常规高度>=96dp）、明确“阶段目标示意，不是时间预测”说明。目标只来自CurveStageParameters.pressureTargets(item)，没有可验证压力目标时不造压力图，显示明确原因和原详情入口。不给阶段分配虚构秒数。
- [ ] 主操作brewButton固定工作台外/底部，不是大卡内横铺两个正向动作。原点击callback选中可执行曲线→ExtractionActivity，否则CurveActivity，槽位入口仍传EXTRA_SLOT；本批不调用startShot/prepareBrew。选定正常项的label仍诚实“准备萃取”，不得改成“开始”却只是绕一层。活跃/未知杯时主操作改为“查看实时状态”并前往Extraction，不因pref变了而误去Curve；不触发任何机器命令。
- [ ] 辅区本杯摘要：用豆/粉量、真实确认/核对状态、已修正温度与目标（owner.currentCorrectedBrewTemperature()/resolve profile）、所选曲线永久使用次数/上次真实使用时间。读取失败和从未使用分开，不补0，不用仅选择计使用。

只读摘要示意：

```kotlin
val app=application as MobileApplication
val doseResult=app.beanPreparationResult
val dose=doseResult.getOrNull()?.current()
val inventory=app.beanInventoryResult.getOrNull()
val matches=dose?.let { value -> inventory?.events()?.any {
    it.eventId==value.eventId && it.batchId==value.batchId &&
    it.kind==io.openhoyi.bean.InventoryEventKind.CONSUME && it.deltaMg == -value.amountMg
} }
val stats=selected?.let { app.curveUsageResult.getOrNull()?.stats(it.id) }
```

用豆状态：store失败“用豆记录暂不可用”；dose null“未选择本杯用豆”；shotId!=null且!associated“上杯记录待关联，前往处理”，不能称本杯已就绪；shotId!=null且associated“上次用豆”，不能自动当本杯；shotId null＋attempted&&!confirmed“扣减结果待核对”；confirmed但matches!=true“库存记录待核对”；confirmed且matches“已确认用豆”；仅selected则“待确认用豆”。显示原beanName/BeanQuantity.formatGrams(amountMg)，不省略原文编辑值。摘要可两行/局部滚动，点击明确入口打开原BeanPreparationActivity。绝不调用任何库存写入/恢复方法。

使用记录store失败→“使用记录暂不可用”；stats count0且last null→“尚无使用记录”；有记录→真实count＋按AppLocale格式化日期；这只是摘要，完整历史入口可打开HistoryActivity。温度源标“已修正温度”，不能把它当图表原始机器温度或改历史。没有profile/当前温度则—，不填猜测。

- [ ] 新Bean摘要/温度/usage字段只在文本变化时更新；service定时render不能每250ms重建按钮、改变焦点或重复读取大文件（现有result对象是已加载store，仅调用只读方法）。不得每帧重建曲线View或重新绑定按钮监听。
- [ ] 主区不再常驻5个大槽位按钮。新增明确“快捷曲线”入口，显示全部1–5原绑定名称，仍调用原slot对应callback只进入准备页。可用局部dialog/菜单完整列表；不得分页、丢slot身份、把普通本杯和槽位合并。原presetButtons/原render的名称来源保留。原警告导致presetRail移动的代码若需改归属，保留全五槽可达并继续警告优先；不能把警告隐藏到工具里。
- [ ] BeanPreparation/电子秤/曲线库入口为次动作，不抢主色。配方参数/低频设备详情可以局部滚，主页面不整页滚。

## Task 3：真实数据和状态保留

- [ ] active杯（含manual/unknown）展示owner.chartCurveId所指真实本杯；manual找不到profile时明确机器手动，不回退显示pref里的另一曲线为本杯。准备闲置才显示普通selected；新主按钮“查看实时状态”不需改变prefs。所有warning/ack/alert/stop条件保留原代码，不在新摘要中造门禁。
- [ ] showDevicePanel/onStop的dismiss/release完整保留；如新增快捷dialog，onStop同样只dismiss，不派发控制。原睡眠/设置/恢复确认内容不减少，notifications/local feedback仍沿用原资格/一次性行为。
- [ ] 小批先不改启动确认路径；下一共享确认任务必须兑现Home主动作进入原确认和门禁，当前不得声称准备页最终已完成。

## Task 4：验证与提交

- [ ] 读diff确认onCreate外的控制方法/原render安全分支未动；只读摘要不写storage。已有native人口页/业务17路径、生命周期、stop、未知恢复、升级检查全部继续，不能为新结构删断言。
- [ ] 与根协调Gradle，用确切SDK路径（不要临时wrapper）：

```bash
./gradlew :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest --offline -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2 --no-daemon --console=plain
```

295项JVM为当前基线；不为纯可逆布局写镜像单测，真正新来源语义由根补独立native fixture/状态审查，运行原有业务回归和视觉验收。不要把编译当窗口通过。
- [ ] 独立规格审查，再质量审查，修复具体问题后仅提交职责源码/资源；root更新文档和CI，不push、不安装设备。
- [ ] 三窗口和8语言/2主题/大字号实际截图读图，保留操作/数据/状态校验；再执行下一共享启动确认和Extraction完整重排。任一失败修真实布局/数据，不缩小21页目标。


## 独立规格审查后的短窗修复

现有Home/TaskWorkspace代码静态存在P2：180dp workspace先分1/3辅区，120dp主区扣标题/说明后把height0/weight1阶段图压到96dp以下，minimumHeight不覆盖EXACT剩余高。

修复要求：先按实际文字测量为主区保留96dp图＋标题/说明所需高度，辅区只使用真正余量（不要假定占1/3就足够）。若极短窗口连基本主内容仍放不下，阶段图不能被挤成伪图：同一目标数据切换成明确“阶段目标示意、非时间预测”的完整文字，必要时只在该阶段详情区域局部滚动；不隐藏安全/停止，不自动缩字，不丢目标。正常窗口恢复真正>=96dp图。允许这一明确极端退化，不把它当正常工作台完成，也不能仅将chart设GONE却没有完整替代信息。

Home实现者负责HomeActivity/TaskWorkspace/task_brew资源内修复，不改root tests或其他文件。根新增正常/180dp短高/font1.3/恢复的native检查，验证可读图或明确文本退化，所有目标值/原信息保留。首次现存完整build已实际通过（47s/295JVM/两assemble），不等于此P2已解决或实屏通过。


## 质量审查追加与验证状态

- c7a4b83修复辅区被fallback压到0的问题：先保48dp辅区，再用主区预算计算96dp图或完整阶段文字，静态规格/质量复查关闭原P2。
- 295JVM/两APK51s实际通过，但正常/180dp/font1.3/恢复native尚未执行；不可勾选实际窗口验收。
- 根HomeWorkbenchChecks外层finally包含selected修改和Activity启动失败恢复，expectedFontScale实际断言及完整阶段顺序断言已补。
