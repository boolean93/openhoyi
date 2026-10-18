# 两杯图轴留白收敛

> 使用 superpowers:executing-plans 在当前隔离工作树执行。

**Goal:** 保持12sp轴文字及完整单位，在同一高度给曲线更多绘图区。

**Architecture:** TwoCupChartView按真实字体行高计算上/下边距；将单独一行秒单位合并进末端时间刻度（如32.0 s），不改采样、范围、线型。顶部单位占一行，最高刻度占半行，另留8dp；底部最低刻度半行与时间刻度一行，另留8dp。

**Tech Stack:** Kotlin / Android Canvas / 既有TextBoundsCanvas。

来源develop66ebda9+ed01de0；沿用feature/task-first-ui→develop draft PR#4，遵守docs/git-workflow.md。

- [x] TwoCupChartChecks补220dp高组件：实际plot至少128dp，维持12sp字体边界/无重叠；时间刻度校验末端为带s的真实时长。夹具总数9→10同步instrumentation报告及脚本检查。
- [x] TwoCupChartView axisHeight从4.5line+20dp改为3line+16dp；bounds上下各1.5line+8dp；末端tick为seconds(timeEndMs)+" s"，移除独立单位行，其他坐标保持。
- [ ] Mock/instrumentation构建、脚本bash -n、branch policy、diff whitespace；CI两字号新10夹具与整页截图待验。不以编译替代绘制检查。

预计绘图区增加1.5倍行高+4dp；该值是代码几何差异，不是实测。96dp最低绘图区/短高回退/无重量提示保留，不放松安全或可读性门槛。不合并/发版/安装，不改协议/数据/schema/签名/版本；普通push，追加revert可回退。

本地验证：Mock/instrumentation BUILD SUCCESSFUL in 43s、exit0，/tmp/task-comparison-axis-spacing-build.log；bash -n、branch policy、git diff --check通过。10夹具实际运行待CI。
