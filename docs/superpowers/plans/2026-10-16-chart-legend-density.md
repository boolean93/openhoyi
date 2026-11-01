# 图例重复信息与绘图区密度

依据f41b781历史详情用户平板实际图，以及Google/Apple已确认的信息层级原则。来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4。

- ExtractionOverlayView共享同一轴模式计算。仅正常绘图且全部物理轴可见时图例显示名称+单位，不重复范围；聚焦轴/过矮提示模式继续显示完整范围。无障碍描述始终保留完整范围。原14sp/48dp目标、线型、切换、投影、缺口与独立轴不变。
- HistoryDetail独立页去掉图表卡8dp四周及额外8dp上边距的重复间隔，保留前一组16dp间距；嵌入预览不动。数据指标40sp不缩小。
- ChartLegendChecks/TrendChartTextChecks：完整范围始终存在可访问描述，若图例未列范围，则实际Canvas必须绘出每个指标单位与上下限；聚焦模式继续检查范围文字。旧绘图标签边界、字号、线条、无秤、RTL、48dp触摸不删除。
- Mock/test APK构建，CI真实绘图/窗口与同状态截图。未取得实图前不宣称绘图区变大或验收通过。
- 普通push，不合并/发布/安装/外部发送/硬件操作，不变签名版本schema；回退追加revert，数据保留。
