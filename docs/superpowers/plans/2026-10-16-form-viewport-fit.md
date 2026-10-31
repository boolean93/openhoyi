# 表单可用高度修正

7e4cee1 task-frame38885369874实际用户平板截图发现：入库最后日期输入底部被局部视口裁切；编辑预览固定280dp导致说明/次要动作落到视口外。该任务框架CI成功不代表全部字段可见；新增single-screen断言尚未运行。

- develop66ebda9+ed01de0来源，feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。
- BeanEntryActivity只将名称/重量说明标签的上下留白设为4dp（原8+6dp）；保留15sp标签字号并收紧字段间距，输入18sp/52dp不改，日期14sp/52dp不改，不削弱五字段全显断言。
- TaskCurveEditorForm预览ScrollView根据实际横向宽度测量标题、说明、状态及按钮所需高度，将图表高度限制在剩余空间、160–280dp；实际不足160dp继续允许局部滚动，不能以缩小图表文字解决。值/字段解析/存储不变。
- Mock/test APK编译；用户平板五字段/四阶段、font1.3和IME原门禁保持。新运行截图前只称已改，不称修复验收。
- 普通推送现有分支，无安装/合并/发版/机器操作。无协议/schema/签名变化，回退追加revert并保留数据。
