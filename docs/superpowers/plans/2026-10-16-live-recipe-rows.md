# 实时萃取本杯参数行

按extraction-four-series-v3及cbe8076用户平板运行图，将右栏段落转为字段/值对齐行。遵守docs/git-workflow.md；develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4。

TaskRecipeSummary保持配方设定温度、目标重量、豆款、记录粉量四字段，14sp标签/16sp值、8dp上下留白、细分隔。值允许换行，不省略无重量目标说明；缺失—。数据仍来自本杯trace绑定的ShotRecipeSnapshot和本杯notes，不能使用下一杯配置冒充。概览补入同一参数全文。

LanguageActiveCupChecks改为查询真实可见温度行，继续验证语言重建、本杯身份/快照不变、停止可用和实际停止。构建Mock/test APK和diff/branch检查；实际窗口/短高/大字号待CI。不改设备控制/签名/schema，不安装/合并/发版；普通push原PR，追加revert回退。
