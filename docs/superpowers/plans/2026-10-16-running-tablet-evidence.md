# 用户平板浅色运行态截图证据

按已批准的横屏规格、extraction-four-series-v3和视觉一致性要求补证据，不重新设计或生成稿件。遵守docs/git-workflow.md；来源develop66ebda9+ed01de0，现有feature/task-first-ui→develop PR#4。

- CI Capture Mock UI使用2560×1600/density360，fontScale1.0，与用户平板几何一致；仅CI模拟器，不执行本机adb或设备安装。
- 保留已有深色模拟萃取/结束/历史流程；其后切回浅色，实际点击开始、确认，检查Mock开始日志后采集第二杯运行态和结束态。
- 留存窗口尺寸、密度、字号、主题和运行日志。检查PNG精确尺寸，不只检查横屏。
- 图中数据来自Mock实时采样，不伪造概念图数值。仍不能当实机或无秤连接证据；字号/间距须拿新图逐项核对。
- 验证bash语法、diff和分支规则；实际点击/截图由新CI验证。无生产协议、签名、版本或schema修改，无安装、发版或合并。普通push，不取消已有CI；回退追加revert。
