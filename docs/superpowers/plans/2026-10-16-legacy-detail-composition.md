# 旧版详情上下文与来源栏

按legacy-history-detail-v1及27ae12b有数据实图：摘要应在图表上方，来源说明归入右栏，详情不占整页底部。遵守docs/git-workflow.md；develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4。

摘要16sp最多两行，完整文本仍在详情对话框；记录名/时间/时长/只读来源不改。左右面板16dp内边距/12dp圆角，左20sp“历史采样”，右20sp“数据来源与限制”。右栏完整原声明14sp可滚动，底部48dp文字详情动作；不是隐藏缺失指标或将旧曲线转原生数据。旧版图表/异步读取/记录不存在状态不变。

构建Mock/test APK、8语言资源XML、diff/分支检查；原有非空旧图表、窗口及列表返回断言保持。新图/大字号/短高待CI，不把源码视为视觉验收。无安装/合并/发版，不改协议/签名/schema；普通push原PR，追加revert回退。
