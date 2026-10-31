# 复盘页分区层级与保存范围提示

沿用 develop66ebda9+ed01de0 → feature/task-first-ui → develop PR#4，遵守 docs/git-workflow.md。依据已认可 brew-review-v3 和 b6fc53c review-saved-notes.png，不改变设计方向。

补上下文底线、参数/笔记标题底线和两栏中线，均1dp语义边框色；复用原24dp栏间距，不重排输入。底部保存区展示已有本地化 journal_subtitle（只编辑个人记录，不改变机器状态或库存），14sp辅助色，横屏宽窗口与右侧320dp保存区同行；不足624dp时上下排列，保留原保存按钮可点击区。短高/IME通过既有独立滚动区与编辑框高度预算适配；新增标题底线纳入笔记固定高度，不能挤压三段输入而未计量。

无草稿、字段、焦点、选择器、保存或库存逻辑改动。验证XML/编译、diff/branch-policy；沿用现有七字段保存重建、长文本/IME/字号及截图检查，CI图到达后再声明视觉通过。无协议、签名、版本/schema、设备操作、安装、合并或发版；普通push，追加revert回退。
