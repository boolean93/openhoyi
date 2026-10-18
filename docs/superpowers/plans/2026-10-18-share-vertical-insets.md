# 分享预览垂直留白校正

feature/task-first-ui→develop PR#4及docs/git-workflow.md。0649834用户1138×618dp四阶段实图仍隐藏表，space记录chart664/table320，放入后344px距360下限差16px。兼容/详情重排已生效且右侧操作完整，不重改回调或删内容。现有header已提供底部12dp，再给workspace16dp；卡片上下各16dp继续消耗空间。按共享8dp间距，将workspace topMargin和两个pane上下padding设RELATED_GAP_DP=8，左右保留16dp。释放24dp垂直高度，数值/标题/行高/160dp下限不改。

验证Mock/AndroidTest构建、diff，原四阶段逐行完整可见门禁不变，后续实际图确认；不把计算余量当运行通过。无设备/安装/合并/发布/签名变化，普通合批push，回退追加revert。
