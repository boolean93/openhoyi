# 旧版历史只读页面

已批准规格§7/F5：旧版来源列表、详情历史图和完整来源说明。沿用develop66ebda9+ed01de0依赖、feature/task-first-ui→develop草稿PR#4；遵守docs/git-workflow.md。

LegacyHistory列表改紧凑平面行，名称17sp、真实日期/时长14sp，不改原记录顺序、ID点击、来源返回、异步读取和位置恢复。LegacyHistoryDetail采用TaskWorkspace：旧双指标图在主区，完整摘要/来源和缺字段说明在支持区；不再用三行截断隐藏说明。保留完整信息按钮。读取失败使用已有失败文案，与找不到记录区分。图仍只有旧压力bar与机器水流ml/s，不补温度、累计水量、秤重量，也不增加控制入口。

本批不改LegacyShotChartView的数据处理；它的字号/轴/缺口视觉复核仍是后续整体验收项，不因布局变更宣称图已完成。相关文件两Activity、计划/证据；不改数据/协议/签名/版本。不发版/安装/合并。现有单屏有数据图>=96dp、旧列表恢复和升级测试继续；JVM/两APK构建后精确CI和原生截图、独立审查待做。只普通推送原分支；追加revert回退，保留数据。


## 2026-10-24 后续批准修订：History 预览标题组关联间距

root明确批准：[本轮预算计划](../../plans/2026-10-24-m7-history-initial-chart-budget.md)将合并后的title/readonly badge/metadata视为直接说明同一图表的关联组，**仅其下方chartViewport.topMargin由初版16dp改为既定related8dp**。这是语义关系明确后的设计修订，不按新原件裁4px硬减间距；旧16dp实施、预算及已验原件仍保留为历史事实，不回写成旧图已用8dp。

范围仅LegacyHistoryContent；生产构造点只有LegacyHistoryActivity，独立LegacyHistoryDetail不调用此组件。panel四边16dp、footer上16dp、notice/action16dp、栏距24dp、自然字体/行盒、160dp plot、48dp触点、whole chart严格初态可见及既有短窗规则全部不变。不得修改共享tokens或扩大到其它页。

执行/精确SHA/构建/新原图接受按上述本轮计划另记；此修订不签新CI或视觉通过。仍普通PR #4→develop，无设备/发布/签名升级，新revert回退且保留数据/原件。
