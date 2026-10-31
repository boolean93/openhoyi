# 旧版详情说明分组

> 使用 superpowers:executing-plans 在当前工作树执行。

**Goal:** 按legacy-history-detail-v1将右栏长说明分为来源、缺失数据、解释限制。

**Architecture:** LegacyHistoryDetailActivity仅改展示，三组20sp标题/14sp正文/16dp组间距/1dp分隔线；旧完整disclaimer仍在全文弹窗保留。8语言资源一起更新。

**Tech Stack:** Kotlin原生View、既有TaskWorkspaceLayout局部滚动及Android strings。

来源develop66ebda9+ed01de0，feature/task-first-ui→develop draft PR#4；遵守docs/git-workflow.md。

- [x] 增加来源说明、缺失标题/四行说明、限制标题/说明，八语言不退回中文；现source_heading简化为数据来源。
- [x] 替换右栏单段disclaimer为三组，正文明确原始wFlow/wTrend保留但不等同原生秤重/流速，旧记录不证明实际停止；缺失值不造零，不改图表投影。
- [ ] 资源XML解析、Mock/instrumentation构建、diff whitespace、branch policy；后续实际横屏/大字号/浅深色检查。排版可逆低风险，不写镜像实现单测。

不发版/安装/合并，不改签名/版本/schema/设备或数据写入；普通push，revert展示可回退。旧图不能证明新分组视觉通过。

本地验证：八份资源XML解析通过；Mock/instrumentation BUILD SUCCESSFUL in 46s、exit0，/tmp/task-legacy-detail-sections-build.log；diff whitespace与branch policy通过。实际布局/分组截图待CI。
