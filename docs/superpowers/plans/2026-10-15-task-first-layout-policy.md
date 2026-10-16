# Task-first Layout Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 先实现不依赖设备或业务状态的自适应决策，确保用户平板横竖、窄窗和IME不会被旧700dp阈值错误分类。

**Architecture:** 一个纯Kotlin策略文件只输入窗口及已测量的内容最小宽度，输出导航、双栏许可、工具位置和图轴模式。它不接收Activity、服务或采样，不可能因重算布局派发设备命令。真正View接线属于后续F1/F2任务，不在此伪装完成。

**Tech Stack:** Kotlin/JVM、JUnit4、现有`:mobile:testMockUnitTest`。

---

来源/分支/依赖、PR、验证、风险、不发版和回退均按[总路线](2026-10-15-task-first-ui-roadmap.md)§1执行。该策略不改变存储、设备、权限或业务门禁。

## Task 1: 建立明确的依赖基线

**Files:** 无手工改动已有业务文件；只新增分支/merge记录。

- [x] Step 1: 从design工作树核对基线和祖先关系。

```bash
git status --short
git merge-base --is-ancestor origin/develop ed01de0f1b6f6449257e3480053340a9bb75b160
git merge-base --is-ancestor 7fe992fd472e7a1db5e82c8c821595a78a8ec770 ed01de0f1b6f6449257e3480053340a9bb75b160
```

预期两个祖先命令退出0。若不是0，先重新核对依赖，不能用reset覆盖。

- [x] Step 2: 新建工作树并显式引入依赖及已提交设计文档。

```bash
git worktree add /Users/boolean93/workspace/hoyi_app/openhoyi-task-first -b feature/task-first-ui origin/develop
git -C /Users/boolean93/workspace/hoyi_app/openhoyi-task-first merge --no-ff ed01de0f1b6f6449257e3480053340a9bb75b160 -m "chore: record existing advanced UI dependencies"
git -C /Users/boolean93/workspace/hoyi_app/openhoyi-task-first merge --no-ff docs/task-first-ui-redesign -m "docs: include approved task-first redesign plan"
```

预期无冲突；出现冲突保留两侧并检查具体文件，不自动采用整侧。只本地合并，不改变远端PR状态。

- [x] Step 3: 核对新树业务源码仍等于依赖，然后建立单测baseline。

```bash
git diff --exit-code ed01de0f1b6f6449257e3480053340a9bb75b160 -- mobile bean-core device-session protocol-core bluetooth-android
python3 scripts/git_policy.py branch
./gradlew :mobile:testMockUnitTest --no-daemon --console=plain
```

预期业务diff为空、策略检查通过、Gradle退出0。记录真正输出；构建环境问题先修复环境，不把未运行当通过，不安装用户平板。

## Task 2: 用需求边界测试驱动纯布局策略

**Files:**
- Create: `mobile/src/main/kotlin/io/openhoyi/mobile/TaskLayoutPolicy.kt`
- Test: `mobile/src/test/kotlin/io/openhoyi/mobile/TaskLayoutPolicyTest.kt`

- [x] Step 1: 创建下面完整测试文件。

```kotlin
package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class TaskLayoutPolicyTest {
    @Test fun userTabletLandscapeUsesRailAndTwoPanes() {
        val result = TaskLayoutPolicy.decide(1138, 711)
        assertEquals(TaskNavigationMode.RAIL, result.navigation)
        assertTrue(result.twoPaneAllowed)
        assertFalse(result.toolsInTopBar)
    }

    @Test fun userTabletPortraitKeepsRailButNotTwoPanes() {
        val result = TaskLayoutPolicy.decide(711, 1138)
        assertEquals(TaskNavigationMode.RAIL, result.navigation)
        assertFalse(result.twoPaneAllowed)
    }

    @Test fun navigationChangesAt600NotAtOrientation() {
        assertEquals(TaskNavigationMode.BOTTOM, TaskLayoutPolicy.decide(599, 1000).navigation)
        assertTrue(TaskLayoutPolicy.decide(599, 1000).toolsInTopBar)
        assertEquals(TaskNavigationMode.RAIL, TaskLayoutPolicy.decide(600, 1000).navigation)
    }

    @Test fun expandedWidthStillNeedsMeasuredContentSpace() {
        assertFalse(TaskLayoutPolicy.decide(839, 700).twoPaneAllowed)
        assertTrue(TaskLayoutPolicy.decide(840, 700).twoPaneAllowed)
        val largerText = TaskLayoutPolicy.decide(900, 700,
            primaryMinimumWidthDp = 500, secondaryMinimumWidthDp = 400)
        assertFalse(largerText.twoPaneAllowed)
    }

    @Test fun keyboardHeightMovesToolsInsteadOfClippingThem() {
        val short = TaskLayoutPolicy.decide(1138, 320)
        assertEquals(TaskNavigationMode.RAIL, short.navigation)
        assertTrue(short.toolsInTopBar)
        assertFalse(short.twoPaneAllowed)
        assertFalse(TaskLayoutPolicy.decide(1138, 360).toolsInTopBar)
    }

    @Test fun measuredAxesLeave320DpOrUseFocusedMode() {
        assertEquals(TaskChartAxisMode.ALL_SCALES, TaskLayoutPolicy.chartAxes(528, 208))
        assertEquals(TaskChartAxisMode.FOCUSED_SCALE, TaskLayoutPolicy.chartAxes(527, 208))
        assertEquals(TaskChartAxisMode.FOCUSED_SCALE, TaskLayoutPolicy.chartAxes(240, 208))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDimensionsAreNotValidLayoutEvidence() {
        TaskLayoutPolicy.decide(-1, 600)
    }
}
```

- [x] Step 2: 在新feature树执行以下定向单测，确认因缺少策略类型/对象而失败，不把其他错误当作预期红灯。

```bash
./gradlew :mobile:testMockUnitTest --tests io.openhoyi.mobile.TaskLayoutPolicyTest --no-daemon --console=plain
```

- [x] Step 3: 创建下面完整实现文件。窗口尺寸均指扣系统栏/IME后可用尺寸；最小内容宽度由View测量传入，不用固定fontScale猜文本宽度。360dp是六个48dp目标＋40dp品牌区＋32dp间隔的导航容量，同时是该基础策略的默认双栏最小可用高度；页面可传入更严格高度。

```kotlin
package io.openhoyi.mobile

internal enum class TaskNavigationMode { RAIL, BOTTOM }
internal enum class TaskChartAxisMode { ALL_SCALES, FOCUSED_SCALE }

internal data class TaskLayoutDecision(
    val navigation: TaskNavigationMode,
    val twoPaneAllowed: Boolean,
    val toolsInTopBar: Boolean,
)

/** Display-only decisions. Measured content constraints never change business state. */
internal object TaskLayoutPolicy {
    const val RAIL_WIDTH_DP = 72
    private const val CONTENT_PADDING_DP = 32
    private const val PANE_GAP_DP = 24
    private const val SIX_TARGETS_HEIGHT_DP = 360

    fun decide(
        windowWidthDp: Int,
        availableHeightDp: Int,
        primaryMinimumWidthDp: Int = 320,
        secondaryMinimumWidthDp: Int = 280,
        twoPaneMinimumHeightDp: Int = 360,
    ): TaskLayoutDecision {
        require(windowWidthDp > 0 && availableHeightDp >= 0)
        require(primaryMinimumWidthDp >= 0 && secondaryMinimumWidthDp >= 0)
        require(twoPaneMinimumHeightDp >= 0)
        val navigation = if (windowWidthDp >= 600) TaskNavigationMode.RAIL
            else TaskNavigationMode.BOTTOM
        val contentWidth = windowWidthDp - if (navigation == TaskNavigationMode.RAIL)
            RAIL_WIDTH_DP else 0
        val requiredWidth = primaryMinimumWidthDp.toLong() + secondaryMinimumWidthDp +
            PANE_GAP_DP + CONTENT_PADDING_DP
        return TaskLayoutDecision(
            navigation = navigation,
            twoPaneAllowed = windowWidthDp >= 840 && contentWidth >= requiredWidth &&
                availableHeightDp >= twoPaneMinimumHeightDp,
            toolsInTopBar = navigation == TaskNavigationMode.BOTTOM ||
                availableHeightDp < SIX_TARGETS_HEIGHT_DP,
        )
    }

    fun chartAxes(availableWidthDp: Int, measuredAxisGuttersDp: Int): TaskChartAxisMode {
        require(availableWidthDp >= 0 && measuredAxisGuttersDp >= 0)
        return if (availableWidthDp.toLong() - measuredAxisGuttersDp >= 320)
            TaskChartAxisMode.ALL_SCALES else TaskChartAxisMode.FOCUSED_SCALE
    }
}
```

- [x] Step 4: 重跑定向单测，预期7项通过；再运行全部Mock单测，确认没有新回归。

```bash
./gradlew :mobile:testMockUnitTest --tests io.openhoyi.mobile.TaskLayoutPolicyTest --no-daemon --console=plain
./gradlew :mobile:testMockUnitTest --no-daemon --console=plain
git diff --check
```

- [x] Step 5: 只提交两个新文件，不加入其他工作树的文件。

```bash
git add mobile/src/main/kotlin/io/openhoyi/mobile/TaskLayoutPolicy.kt mobile/src/test/kotlin/io/openhoyi/mobile/TaskLayoutPolicyTest.kt
git commit -m "feat: define task-first adaptive layout policy"
```

## 本批完成边界

2026-10-15实际记录：任务1依赖树建立、基线单测完成；任务2提交`45494cb`，7项定向/273项完整Mock单测0失败，完整套件有1项既有跳过，规格及质量审查通过。普通Gradle命令受本机无效代理阻挡，实际成功命令额外使用`--offline -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2`，未持久更改配置。详见`docs/evidence/task-first-implementation.md`。

纯窗口策略通过不等于真实导航、图表、IME或21页完成。必须在F1真实Frame和F2图表接线后用实际控件/测量/读图验证；本批不宣布改版完成、不推送主干、不发版。
