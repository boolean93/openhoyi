# 库存流水首屏验收

feature/task-first-ui→develop PR#4及docs/git-workflow.md。现有业务仅验证日期/真实流水文本存在，再滚动截图，不能证明概念稿最近流水首屏可见；0d6fb9c实图已显示仅标题。修改AdvancedBusinessUiChecks：在既有inventory-selected-after-adjustment截图之后，找到-10g/+100g两条真实事件行，记录窗口配置/完整可见边界；标准≥1000×600dp、font≤1.1要求两行完整可见，其余配置仍按原滚动流程。原库存不变量和后续路径不删，失败先留截图与txt。无产品数据写入新增、不操作设备。

验证AndroidTest编译/diff，与已编译库存空间候选合批push。运行通过前不声称首屏流水完成；不合并发版/安装/签名改变，回退追加revert。
