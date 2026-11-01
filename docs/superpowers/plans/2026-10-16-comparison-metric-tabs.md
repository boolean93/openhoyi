# 两杯对比指标分段选择

依据brew-comparison-v1，替换右侧原生指标下拉框，四个指标直接可见。develop66ebda9+ed01de0来源，feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

- 新ComparisonMetricTabs：压力/注水/秤重/温度四个真实按钮，16sp和48dp最小目标，选中使用accent_soft和isSelected状态，键盘焦点/涟漪保留。单位沿用现有本地化资源；不删长标签、不缩字体，横向等分，可换行。
- BrewComparisonActivity放置独立一行（在选杯/交换信息之后），选择只改变既有selectedMetric及chart.metric，保存恢复不变，不改样本和投影。
- TwoCupComparisonPageChecks从操纵Spinner改为真实按钮performClick，保留四指标样本不变、绘图区>=96dp、身份清空/交换/恢复、摘要绑定；标准平板检查四个按钮完整可见且48dp。
- 编译Mock/test APK并通过CI确认全语言/大字号/短高，未有新截图不称整页完成。
- 普通推送现有PR，无安装、合并、发版和设备操作。无协议/schema/签名变化，回退追加revert并保留记录。
