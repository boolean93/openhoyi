# Scale Reading Viewport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复共享顶栏后粉量捕获页主读数被压到29px的真实CI缺陷，推进已认可秤页的重量优先层级。

**Architecture:** 主重量和当前数据状态固定，次要计时/连接工具/粉量来源说明在独立局部ScrollView中；导航/整个页面不滚。原去皮与粉量捕获回调、来源/稳定性说明、定时器保存/恢复与门禁完整保留。不能通过缩字或删俄语断言规避。

**Tech Stack:** Kotlin原生View，既有AdvancedPageChecks/TaskFrameChecks。

来源develop66ebda9的当前feature/task-first-ui，PR#4→develop；遵守docs/git-workflow.md；只改ScaleActivity.kt，不改别人的文件/协议/schema，不推送/发版/安装用户设备。追加修正或revert PR回退，保留未知记录。

## 根因证据

CI38727735742/artifact advanced-mock-pages-pages/compact.txt：`ru dark=false page=ScaleActivity-Capture vertical text overflow: — г layout=53 available=29`。此前全部中文/部分俄语控件通过，Capture加完整来源说明后固定非滚内容把weight的0height+weight剩余压没。原AutoSize最低36sp无法填29px。不能降低测试字号或删除出处说明。

## 文件与步骤

仅修改`mobile/src/main/kotlin/io/openhoyi/mobile/ScaleActivity.kt`中ScaleActivity.onCreate。ScaleOwnerActivity/服务所有权保持。

- [x] 读失败产物、现有代码和规格§7 Scale/§9短高规则，确认原17条业务路径等断言保留。
- [x] 主reading改为有真实最小高度且不会被次要控件分走高度的固定区；重量48sp（大平板可64sp），系统字号有效，移除AutoSize自动缩字。数字不以裁剪/省略丢信息，特别负值、长值。布局代码模式：

```kotlin
body.addView(reading,LinearLayout.LayoutParams(-1,-2))
reading.minimumHeight=HoyiUi.dp(this,128)
weight.layoutParams=LinearLayout.LayoutParams(-1,-2)
val secondary=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
val secondaryScroll=ScrollView(this).apply {isFillViewport=false;addView(secondary)}
body.addView(secondaryScroll,LinearLayout.LayoutParams(-1,0,1f))
```

最小高度不是硬裁剪高度，重量TextView应按实际fontMetrics自然wrap；额外状态在读取区自然wrap。header/freshness/guard保留。次区承载计时和工具，来源说明完整置于区内，不藏在更多弹窗/不省略。去皮按钮可保留固定reading之后；Capture请求的“使用稳定粉量”是本页主动作，置次区外固定底部，不被滚走；仅重挂原按钮和原callback，原能力禁用条件不变。
- [x] 所有字段/定时器保存恢复/连接门禁/去皮与捕获callback逐项diff核对。主读数不再分配0height权重；不新增整页ScrollView。
- [x] `:mobile:assembleMock :mobile:assembleMockAndroidTest`及完整Mock单测（离线aapt2参数同现有证据），git diff --check。与根协调避免同时Gradle。
- [x] 单独提交职责文件，根运行完整AdvancedPages和3窗口专项，原textFits失败应消失；不在缺少运行证据时宣称已解决所有语言/大字号问题。
- [ ] 独立规格和质量审查后继续F7完整视觉改版，此局部修复不冒称F7交付完毕。


064655b已提交，独立规格/质量审查通过。294项JVM/两assemble通过；云端AdvancedPages尚未重跑，不能当作全语言通过。
