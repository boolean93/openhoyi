# 两杯对比横向构图

依据brew-comparison-v1及f41b781用户平板实际截图。来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。

- 新TaskComparisonLayout：选杯/指标/警告位于顶端局部滚动区；中间图表全宽；底部两杯信息并排且局部滚动。按实际工具/信息测量分配预算，优先保留图表minimumChartHeight，短高各操作区保留48dp视口；不改绘图投影。
- BrewComparisonActivity迁移现有同一控件到新分区，两个选择器横排（窄宽退为纵排），交换与详情/笔记使用文字操作；两来源各自分组16dp内边距。来源/警告文字13→14sp，与现有辅助字号统一。
- 不改记录身份、占位选择、交换、异步读取、选中指标、时间对齐、数据缺失/重置/间隙/未知说明；不删提示换取空间。
- 原TwoCupComparisonPageChecks/TwoCupChartChecks及single-screen保持；编译Mock/test APK并在CI取用户平板/大字号截图。指标分段控件、结构化摘要表尚未在本步实现，不称整页对齐完成。
- 普通推送现有PR，不合并、安装、发版或硬件操作。无协议/schema/签名变化；回退追加revert保留所有记录。
