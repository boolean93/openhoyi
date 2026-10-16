# 复盘分区与研磨输入比例

依据已认可brew-review-v3和9744053正常态截图：补本杯参数/我的记录20sp分区标题；研磨输入从160dp收敛到52dp，保留多行文本与内部滚动；跳转选择器限制为右侧240dp，不再拉满整行。标题在各自滚动区内，短高不会固定占用编辑空间；笔记高度计算扣除标题实测高度。仅调整现有控件的样式和排版，不更改草稿、焦点、保存、库存或观察语义。后续同条件截图继续判断，不能把这批小修当整页完成。

沿用develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4，遵守docs/git-workflow.md。不安装/合并/发版、不改协议/签名/schema。Mock双APK编译和现有中文长文本/IME/滚动检查验证，截图核对字体间距。普通push，追加revert回退。

验证：首次编译因无障碍属性名失败，改用API28起的isAccessibilityHeading并保护版本后，双Mock APK构建39秒成功；git diff --check通过。候选尚缺原生截图/IME结果。
