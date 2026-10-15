# Four Metric Native Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把实时萃取中旧压力+流速图替换为同图压力/累计注水/有效秤读数/温度，窄窗能点选明确刻度，不造未来或出液语义。

**Architecture:** 保留ExtractionOverlayView类名与points/reference属性，改为原生垂直LinearLayout，native图例按钮在上、独立Canvas绘图区在下。数据只用FourMetricChartProjection，范围由当前traceIdentity会话维护；服务只新增只读采样身份出口，不修改连接/控制/采样/数据格式。主读数的完整工作台重排为F3，本批图表不能冒称已完成F3。

**Tech Stack:** Android View/Canvas、Kotlin、现有Mock instrumentation/GitHub Actions。

来源/PR/规则同总路线：feature/task-first-ui从develop66ebda9，PR#4→develop，含显式ed01de0依赖；普通追加commit/push，不force，不merge/release/install用户设备，不改协议/签名/版本/schema；失败追加修复或revert PR，保留未知记录。

## 文件责任

图表实现者只拥有`mobile/src/main/kotlin/io/openhoyi/mobile/ExtractionOverlayView.kt`及新`mobile/src/main/res/values*/task_chart.xml`。根拥有`ShotSeries.kt`/`ShotSeriesTest.kt`的只读traceIdentity及其测试、`MobileService.kt`只读代理、`ExtractionActivity.kt`接线、androidTest/runner/scripts/CI/evidence。不改别人文件，不新增BLE/业务写入。

## Task 1：真实原生失败检查

- [ ] `FourMetricChartChecks`已先写，三个fixture：机器三项、满配四项、缺口/水计数重置/真实参考。故意设置两个flow=999，累计量/秤重不相同。旧View缺少`chart-metric:WATER`等原生可点击图例，应在CI实际失败，保存精确日志；代码编译不算红灯。
- [ ] 原同页`TrendChartTextChecks`需改为新指标/物理轴合同，不再断言g/s是默认轴；保留字号/文字不出界/不重叠/参考说明/实际绘图区检查。旧CupFlowProjection测试和高级信息保留。

## Task 2：原生图例与数据会话

图表对外完整接口：

```kotlin
internal class ExtractionOverlayView(context: Context): LinearLayout(context) {
    var points: List<ShotPoint> = emptyList() // setter rebuildProjection(), never mutate source
    var reference: List<ShotPoint> = emptyList()
    var traceIdentity: String? = null // on actual identity change: previousRanges=emptyMap(); rebuildProjection()
    var referenceLabel: String? = null // source/date/alignment from existing record; visible if reference nonempty
}
```

- [ ] 接收上述四属性。投影传入上一帧rangeMap，同杯只扩；traceIdentity改变清范围。参考只显示已有完整数据，不能根据配方补预测。
- [ ] `orientation=VERTICAL`。图例用真实原生Button/TextView点击控件，按PRESSURE/WATER/(WEIGHT)/TEMPERATURE顺序，tag=`chart-metric:<ENUM>`，字号14sp且尊重fontScale，目标≥48dp。测量实际文字宽度/高度后逐行排布，不缩字或裁字；窄窗可两行，不能固定四等分造成长标题裁切。不要每帧重建按钮、抢焦点；仅series集合/布局列数改变时更新容器布局，现有按钮实例可保留。
- [ ] 每个标签为中文“压力 · bar”“累计注水 · ml”“秤读数 · g”“温度 · °C”。无杯重/归零上下文不称出液量；默认当前/参考都是未知杯重，不能从重量非空猜。无有效重量移除g轴及其按钮，保留机三项；后来缺测保留已有重量段及按钮。
- [ ] 点选指标使该native控件isSelected=true、其余false，突出该线并选择窄屏物理刻度；初始全部同等强调，窄窗初始压力轴。一次只能一个选中，点击当前可保持选中。标记颜色和短线样式，不能只凭颜色区分；蓝实线压力、青长虚线注水、深青实线+点重量、琥珀点线温度。压力沿用chart_pressure。新增专用语义色task_chart_water=#007F81、task_chart_weight=#005B53、task_chart_temperature=#946000；夜间分别#64C7C4/#7DDCCE/#FFC36B。颜色置values/task_chart.xml与values-night/task_chart.xml，不改旧图全局资源。
- [ ] native辅助文字：“点选指标查看对应刻度”“本杯采样至 %1$s s”或空采样等待，不能写成当前/结束时刻。参考存在时直接显示referenceLabel及“灰色细线：历史参考，不是本杯预测”，不是仅藏在详情。水reset存在时显示“注水计数发生重置，已分段显示”；只有gap不能声称具体断连原因。
- [ ] 所有新文案在7种已有语言给基本翻译以符合MissingTranslation必需lint；优先中文，其他语言精修后续，不加lintignore或translatable=false规避。

## Task 3：一张Canvas与明确物理刻度

- [ ] 子Canvas View占容器剩余高度：`addView(plot,LayoutParams(-1,0,1f))`。控件字号至少11sp，`scaledDensity`而非density；辅助提示可按14sp。测量fontMetrics，文字上下界和图例不遮线。字体过大/短高时保留坐标主体，不能悄悄不绘图；若需局部图例滚动须只包图例，图/主操作不整页滚。
- [ ] 图仅一个plot，时间从0到data.timeEndMs，共用x，不同metric各用自己的range。数据/边界算法：

```kotlin
fun x(ms:Long)=left+(right-left)*(ms.toDouble()/data.timeEndMs).toFloat()
fun y(value:Float,range:ChartRange)=bottom-(bottom-top)*(value-range.low)/(range.high-range.low)
// For every current/reference segment: new Path.moveTo(first), lineTo(rest).
// One-point segments get a real dot, not an invisible zero-length path.
```

不coerce原值到0、不积分flow、不跨segments连线、不延长最后点。每个系列可clip到plot安全区，但range必须先覆盖全部真实值和参考值。current末帧可细竖线；不能在timeEndMs（可能来自更长reference）伪画本杯末帧。参考灰色细线/点与本杯线型+色区分。

- [ ] 量测每metric的unit和5行tick字符串宽度＋8dp得到axisColumn；四项左右各两列、三项左两列右一列。使用`TaskLayoutPolicy.chartAxes(availableWidthDp,measuredAxisGuttersDp)`，保证有效plot≥320dp才全刻度；否则仅一列focusMetric刻度，native标签明确当前focus，不把其他值拿该轴单位解释。单位与tick数值对齐，左右列不重叠。高度允许时5行，否则按实际fontMetrics+至少8dp间隔减少行数，短高也保留上下界两行；不可保留5行导致重叠。窄plot必要时明确只保留一侧轴，不留三列空白。
- [ ] 底部时间0/中点/终点秒及时间单位，按实际文本宽避重叠；不强制旧图19标签断言。空采样显示真正空态；reference-only可显示完整灰参考但本杯observed仍未知。
- [ ] Canvas contentDescription包含各metric名称/单位、最近采样值（不是live值）、gap/重置和参考语义，native图例可读屏选择。无权通过图例触发去皮/控制或修改采样。

## Task 4：接线、真实运行和提交

根的接线代码（不要求图表实现者修改这些文件）：

```kotlin
// ShotSeries: distinct display identity retained after finish; updated only in begin.
var traceIdentity: String? = null
    private set
// begin: traceIdentity=shotId
// MobileService:
val chartTraceIdentity: String? get() = series.traceIdentity
// ExtractionActivity.render, before points setter:
chart.traceIdentity=owner?.chartTraceIdentity
// loadReference after referenceStatus.show:
chart.referenceLabel=if(available) referenceStatus.text.toString() else null
```

- [ ] 根新增ShotSeries身份保持/同配方下一杯不同ID测试，先红后绿；仅readonly身份字段，不改active id/finish/checkpoint。
- [ ] `:mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest :mobile:lintDebug`，本地offline+aapt2覆盖同既有证据。Gradle不并行争用；独立规格审查，再质量审查。
- [ ] 推送PR#4，三窗口新fixture从真实失败转通过；取回实际组件和Activity截图逐张检查满配/无秤/断点/参考与窄窗轴。组件bitmap只证明native绘制，不当成真实运行杯或全部21页完成。保留原业务17路径、未知门禁、生命周期、升级及所有必需CI。
