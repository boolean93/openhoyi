# Legacy 短窗 chart 内容坐标请求修正

> 实施者 Sol，按 executing-plans 串行执行；root 唯一完整构建、提交及推送 owner。保留其他人的冻结改动。

**Goal:** 仅修测试请求坐标，使祖先收到真实 viewport 盒子；不修改产品或验收阈值。

**Architecture:** 在现 shortDetailChartReachability 的两处请求中，用内层实际 scrollX/Y 表示当前可见内容区。继续用接受帧 Canvas 与实际可见区交集验收，不能以请求成功代替采证。

**Tech Stack:** Kotlin instrumentation、离线坐标数学验证。

## 基线与证据

沿已授权 `feature/task-first-ui` → develop 批次，遵守 [git-workflow](../git-workflow.md) 与 [task-template](task-template.md)。已读 `build/task-first-evidence/ci-3a78c4c/legacy-first-failure-diagnosis.md` 全文及末节，并核 `LegacyVisualMigrationChecks.shortDetailChartReachability`、`LegacyChartViewport` 与同帧 shortDetailState。

旧请求在内层 scrollY=176 后仍传 Rect(0,0,w,216)。Android34 祖先转换减去 child.scrollY，实际请求外层 [453,669]，故滚246；真实 viewport 为 [629,845]，需滚422，外层容量584。数字只用于复算反例，不能写入运行逻辑。实际接受帧仅17.5552dp可见，原失败有效；本诊断不是产品不可达或新请求已成功的证明。

## Sol 允许范围

仅 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/LegacyVisualMigrationChecks.kt` 内 shortDetailChartReachability 的请求坐标与 plot 初始滚动整数选择。不改生产、shared viewport、runner、frame协议、报告schema、初态/恢复/Canvas观察。离线数学脚本可保存在本地证据目录，无需新通用框架。

- [ ] 先保存并执行旧公式反例：外层 top629、height216、可见高423、容量584、innerY176，旧区间 [453,669] →246；新当前viewport内容区间经转换后应为 [629,845] →422。
- [ ] 两处 request（plot与逐轴字/legend）都在同一现有 main block 内、scrollTo之后，读取 **实际已clamp的** scrollX/Y，使用：

```kotlin
val x = viewport.scrollX
val y = viewport.scrollY
viewport.requestRectangleOnScreen(Rect(x,y,x+viewport.width,y+viewport.height),true)
```

可以提取该方法内的小局部函数复用两处，不能改其它页面 requestRectangleOnScreen。不要用期望 scroll 值替代实际值，不直接 scroll 外层到422，不硬编码176，不新增重试/等待或选择某个成功帧。

- [ ] plot 滚动保持动态 Paint 边界。当前 `plot.top.toInt()` 向下取整会令176.5008仍露出0.5008px非plot区域，在恰96dp的viewport中只剩215.4992px。将该起点改为 `ceil(chart.top + plot.top).toInt()`（读取实际chart在viewport内布局top，现结构为0），仍交 scrollTo 自身clamp，随后请求上述实际viewport内容区间。这是同一测试滚达坐标修正，不是加高度或改96门槛。不得仅改外层请求后把亚像素失败容差化；若实际容量不足/plot不足，原严格检查继续失败。
- [ ] 轴字/legend仍用原完整Canvas operations枚举和中心定位，不能删operation、缩字号、跳过裁剪项；只替换其viewport请求矩形。所有接受帧仍先落盘再检查 actual mapping、plot visible>=96dp、width>0、wholeGlyph及文本/index/role/bounds完整一致。
- [ ] 原finally恢复全部ScrollView精确x/y，恢复接受帧/身份/窗口/初始列表保持。不得改5秒四零差、短窗永久布局、真实窗口身份、滚动恢复或业务不变量。

## 离线数学验证与交接

- [ ] 用简单 Python 数学断言验证：`local=[sx,sy,sx+w,sy+h]` 经当前view传播减`(sx,sy)`后恒为`[0,0,w,h]`；加祖先布局偏移后恒为实际viewport盒子。覆盖x/y为0、正值、请求超过容量但实际已clamp、已有非零外层scroll、布局偏移变化。旧0起点必须在非零内滚反例失败；新式不能依赖具体176/422。
- [ ] 严格plot边界反例：top176.5008、bottom536.74963、viewport216，floor起点相交215.4992<216；ceil起点177相交216。再测整数top、不同density的96dp、非零chart.top、plot不足或clamp无法滚足时应保留失败，不加epsilon。
- [ ] `git diff --check`；保存公式、输入与结果到 `build/task-first-evidence/ci-3a78c4c/legacy-request-rectangle-math.md`，冻结源码SHA交Astra做逆增量审查。数学验证不模拟或冒称Android接受帧。
- [ ] root合批完整构建、普通推送原PR；不微推/force/no-verify/直推主干，必需CI通过才可合并。新运行继续原short-window链，保留初态、plot、所有轴字/legend与恢复原件；若失败保存同帧值另审，不改产品掩盖。

## 风险与边界

无业务编码、单位、队列、设备命令变化；不重发未知，不用Mock代替硬件验收。不发版/安装/分发。回退仅revert测试坐标增量，保留原ZIP与未知记录。当前仅规划，未修改源码、构建或验收新实图；M7后续文字/按钮链仍未通过，不能签整页或整run。

## Astra 窄增量终审（2026-10-23）

静态通过。独立核实际单文件 diff 与冻结 SHA256 `6f88df1453defe44d137eb9c095d0395bf1d414b1df3796de3bbaaf678d1db82`：仅增加当前 viewport 内容矩形局部函数、两处调用，以及 plot 起点 ceil(chart.top+Paint.top)。在内存逆替换这些增量后，逐字节精确还原 HEAD 版本；无其它逻辑修改。

两处 helper 都在原 main block、scrollTo 之后读取实际 scrollX/Y，因此使用 clamp 后值；祖先减去内滚后得到真实 viewport 布局盒子。未硬编码旧176/422。ceil处理实际亚像素边界，不添加容差。已读离线数学记录并核公式：旧请求246、新请求422的差由内滚176精确解释；plot不足/clamp不足仍失败，离线结果不冒称设备通过。

原同帧capture target落盘、mapping/visiblePlotHeightDp>=96/width、全部Canvas operation与wholeGlyph、finally所有scroll精确恢复、Window协议及5秒四零差均未改变。独立 git diff --check 通过。未修改源码、执行构建、提交或推送；可交root继续统一构建与后续真实CI，不能签M7/视觉/整run通过。Settings host审核仍未完成，不并入本次结论。
