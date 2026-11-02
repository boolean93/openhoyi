# 图例实时值测试契约同步

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。10bf727/run39090130756的language-instrumentation.txt实证失败在ChartLegendChecks.kt:57，实际压力10.0bar/量程0–12，旧预期仍为“压力 · bar”。生命周期导航及音频五组已通过，不能把图例失败归咎模拟器图形警告。

产品637f83f新增实时图例值是已要求功能，保留产品实现。ChartLegendChecks按固定夹具末采样独立预期10bar/30ml/30g/92°C；可见名、值、单位与无障碍量程全部精确校验。两处轴单位检查从稳定指标标签对应明确单位，不从已变更人类文案分隔符猜单位。保留64夹具、触控/重叠/textFits、96dp绘图区、四物理路径、交互/缺秤断言。

验证：已有CI失败是修改前证据；编译测试包、差异检查；更新后完整CI运行才证明修复，不能用编译代替。与本地布局批次一起普通push，不重跑旧版本、不减少门禁。无设备/安装/合并/发布，版本签名schema不变；回退测试契约更新即可。
