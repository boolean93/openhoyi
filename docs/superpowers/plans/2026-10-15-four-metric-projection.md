# Four Metric Chart Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 交付四指标原生图可复用的数据投影，后续Canvas直接使用真实分段和量程，不再默认用流速代替注水/出液。

**Architecture:** 纯Kotlin展示层读取ShotPoint，既不改变采样也不访问服务/设备/持久化。明确当前与真实历史参考的分段、累计重置边界、物理量范围和最后观察时间。重量仅提供原始秤读数，杯重/归零上下文未被持久化，不能由非空重量推导“出液量”；实时UI将单独核实已有控制层上下文。

**Tech Stack:** Kotlin/JVM、JUnit4；原生Canvas迁移是下一任务，本批不改Activity或旧图。

---

## 治理与责任

延续`feature/task-first-ui`（来源develop66ebda9，既有依赖ed01de0显式本地合并），目标PR#4→develop。遵守`docs/git-workflow.md`与总路线。只提交本批文件，不改其他工作者文件；普通推送、不force，不发版、不安装设备、不改包名/签名/版本/schema，不动未知写入或控制门禁。追加修正或revert PR回退，不删除记录。275项JVM为当前基线，不把原CI或概念稿当新图验收。

Create `mobile/src/main/kotlin/io/openhoyi/mobile/FourMetricChartProjection.kt`：唯一新展示模型。
Create `mobile/src/test/kotlin/io/openhoyi/mobile/FourMetricChartProjectionTest.kt`：采样/单位/断点/参考/量程契约。

## Task 1：测试先行

- [x] 创建测试类。每个fixture用`ShotPoint(ms,pressure,999,water,temperature,weight,999,brewing)`，故意让两个流速字段999与累计量/重量不同。
- [x] 测试以下精确断言：

```kotlin
private fun point(ms: Long, pressure: Int=90, water: Int=123,
    temperature: Int=9350, weight: Int?=456, brewing: Boolean?=true) =
    ShotPoint(ms,pressure,999,water,temperature,weight,999,brewing)

@Test fun exactPhysicalValuesNeverUseFlow() {
    val data=FourMetricChartProjection.build(listOf(point(1000)),emptyList())
    assertEquals(listOf(ChartMetric.PRESSURE,ChartMetric.WATER,ChartMetric.WEIGHT,ChartMetric.TEMPERATURE),data.series.map {it.metric})
    assertEquals(listOf(9f,12.3f,4.56f,93.5f),data.series.map {it.current.single().single().value})
    assertEquals(listOf("bar","ml","g","°C"),data.series.map {it.metric.unit})
}
```

另分别写测试：
1. 无秤的两个采样只能产生压力/水/温度三个series，无WEIGHT；不能生成0g。
2. 重量`100,null,200`为两段；负值`-100`原样保留为-1g且范围下界覆盖-1。
3. 时间`0,1500,3001`压力分段长度`2,1`；1500允许连，1501不连。brewing=false压力为空，水/温度仍保留已观测字段。
4. 水`100,200,50,60`分段长度`2,2`，reset时间`2000`；同时间压力仍单段。重量下降不是自动归零，不修改重量值。
5. 当前只到1000、真实参考到41000：`observedThroughMs=1000`，`timeEndMs=41000`，当前点数不变，参考仍完整。空当前`observedThroughMs=null`。
6. 负时间、相同时间、逆序时间均IllegalArgumentException，当前和参考分别验证；输入list/ShotPoint不变。
7. 初始默认范围压力0..12、水0..60、重量0..50、温度80..100；样本压力18、水100、重量-1、温度110全部扩展不裁峰。随后输入低值且传上次ranges时不能收缩。压力负值也必须保留。
8. 空当前+空参考：无重量、三个机器series皆空段，timeEndMs=1000、observedThroughMs=null；绝不造采样。reference只有重量时也给重量轴但current为空。

- [x] 定向命令先失败，保存实际失败日志，不能把没运行当红灯：

```bash
./gradlew :mobile:testMockUnitTest --tests io.openhoyi.mobile.FourMetricChartProjectionTest --offline -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2 --no-daemon --console=plain
```

## Task 2：实现纯投影

- [x] 新建模型，以下完整接口与算法为本批约束（可按实际Kotlin类型修正编译，不扩范围）：

```kotlin
internal enum class ChartMetric(val unit: String, val floor: Float, val ceiling: Float, val step: Float) {
    PRESSURE("bar",0f,12f,1f), WATER("ml",0f,60f,10f),
    WEIGHT("g",0f,50f,10f), TEMPERATURE("°C",80f,100f,5f)
}
internal data class ChartValue(val elapsedMs: Long,val value: Float)
internal data class ChartRange(val low: Float,val high: Float)
internal data class MetricChartSeries(val metric: ChartMetric,
    val current: List<List<ChartValue>>,val reference: List<List<ChartValue>>,val range: ChartRange)
internal data class FourMetricChartData(val series: List<MetricChartSeries>,
    val observedThroughMs: Long?,val timeEndMs: Long,
    val waterResetsMs: List<Long>,val referenceWaterResetsMs: List<Long>)
internal object FourMetricChartProjection {
    fun build(points: List<ShotPoint>,reference: List<ShotPoint>,
        previousRanges: Map<ChartMetric,ChartRange> = emptyMap()): FourMetricChartData {
        fun validate(samples: List<ShotPoint>) {
            require(samples.all {it.elapsedMs>=0} && samples.zipWithNext().all {(a,b)->a.elapsedMs<b.elapsedMs})
        }
        validate(points); validate(reference)
        fun resets(samples: List<ShotPoint>) = samples.zipWithNext().mapNotNull {(a,b)->
            b.elapsedMs.takeIf {b.waterTenthsMl<a.waterTenthsMl}}
        fun value(point: ShotPoint,metric: ChartMetric): Float? = when(metric) {
            ChartMetric.PRESSURE -> (point.pressureTenthsBar/10f).takeUnless {point.brewing==false}
            ChartMetric.WATER -> point.waterTenthsMl/10f
            ChartMetric.WEIGHT -> point.weightHundredthsGram?.div(100f)
            ChartMetric.TEMPERATURE -> point.temperatureHundredthsC/100f
        }
        fun segments(samples: List<ShotPoint>,metric: ChartMetric): List<List<ChartValue>> {
            val result=mutableListOf<MutableList<ChartValue>>()
            var previous: ShotPoint?=null
            var active: MutableList<ChartValue>?=null
            for (point in samples) {
                val actual=value(point,metric)
                if (actual==null) {active=null; previous=point; continue}
                val prior=previous
                if (prior==null || point.elapsedMs-prior.elapsedMs>1500 ||
                    metric==ChartMetric.WATER && point.waterTenthsMl<prior.waterTenthsMl) active=null
                if (active==null) {active=mutableListOf(); result.add(active)}
                active.add(ChartValue(point.elapsedMs,actual)); previous=point
            }
            return result.map {it.toList()}
        }
        val series=ChartMetric.entries.mapNotNull {metric->
            val current=segments(points,metric); val historic=segments(reference,metric)
            if (metric==ChartMetric.WEIGHT && current.isEmpty() && historic.isEmpty()) return@mapNotNull null
            val values=(current+historic).flatten().map {it.value}
            val old=previousRanges[metric]
            if(old!=null) require(old.low.isFinite() && old.high.isFinite() && old.low<old.high)
            val low=minOf(metric.floor,values.minOrNull()?:metric.floor,old?.low?:metric.floor)
            val high=maxOf(metric.ceiling,values.maxOrNull()?:metric.ceiling,old?.high?:metric.ceiling)
            MetricChartSeries(metric,current,historic,ChartRange(
                minOf(low,kotlin.math.floor(low/metric.step)*metric.step),maxOf(high,kotlin.math.ceil(high/metric.step)*metric.step)))
        }
        return FourMetricChartData(series,points.lastOrNull()?.elapsedMs,
            maxOf(1000L,points.lastOrNull()?.elapsedMs?:0L,reference.lastOrNull()?.elapsedMs?:0L),resets(points),resets(reference))
    }
}
```

不对ShotSamplesStore/MobileService加假杯重字段，不给unknown重量自动归零。所有`ChartValue`都是已有采样点；timeEndMs只是坐标范围，不能冒充当前时间/结束时间。previousRanges仅限调用方同一杯、同一展示会话维护，新杯重置；本模型无全局可变状态。

## Task 3：核对与提交

- [x] 定向测试绿灯；完整`:mobile:testMockUnitTest`，独立读JUnit XML计数/失败/跳过；`git diff --check`。
- [x] 独立规格审查，再独立质量审查，修复具体问题后复查。只提交两个新源码/测试文件，控制者更新本计划与实施证据。
- [ ] 完成后继续Canvas：独立物理轴/窄窗聚焦轴、可操作图例、同图真实历史参考、颜色+线型、当前值新鲜度与图的可访问描述。该任务通过不意味着用户看到的新图已完成。


## 实际验证

实现7fae22e；质量审查发现Float极值可能向内取整，555c356增加两项真实失败回归后修复。最终19项定向测试通过，完整JUnit294项、0失败、0错误、1既有跳过；控制者独立读取XML。独立规格审查及修复后的质量复查通过。Canvas尚未改版。红/绿日志见`/tmp/four-metric-projection-rounding-{red,green,full}.log`；原模型红灯见`/tmp/four-metric-projection-red.log`。
