# 两杯对比记录入口归位

> 使用 superpowers:executing-plans 在当前工作树执行。

**Goal:** 按 brew-comparison-v1 将两杯记录入口放在对比表下方，而非放在长篇来源说明之后。

**Architecture:** BrewComparisonActivity只移动原detailButtons的父布局；第一/第二杯身份解析、缺失禁用、notes弹窗与完整来源说明不变。

**Tech Stack:** Kotlin / Android原生LinearLayout、Button / TaskComparisonLayout局部滚动。

来源develop66ebda9+ed01de0；沿用feature/task-first-ui→develop draft PR#4。遵守docs/git-workflow.md。

- [x] 在summaryTable后增加水平recordActions，8dp顶部间距，左右各一杯入口（权重1、间隔16dp），第一杯START、第二杯END；保持原48dp触控范围，长文本允许换行。
- [x] 原detailButtons创建在recordActions内，仍按当前firstId/secondId打开HistoryDetailActivity；sourceCards保留完整状态/对齐/缺重/断点说明及notes入口，放在recordActions之后。
- [ ] Mock与instrumentation构建、diff whitespace、branch policy；新CI核对常规及1.3字号可达性。此次为低影响排版调整，不写镜像布局实现的单元测试。

不改数据/文件/设备写入或采样投影，不安装/合并/发版，不改签名/版本/schema。普通push工作分支，追加revert回退布局，保留记录。24页面范围不缩减，图高/首栏配方信息等仍需继续对齐。

本地构建验证：Mock/instrumentation BUILD SUCCESSFUL in 42s，exit0，/tmp/task-comparison-actions-build.log；branch policy及diff whitespace通过。新布局运行/视觉仍待CI。
