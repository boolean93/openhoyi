# 库存流水表格构图

依据bean-ledger-v3与418fec6用户平板实际截图。来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4。当前BeanLedgerActivity与该截图源码一致。

- 只改BeanLedgerActivity呈现：固定14sp列头，16sp五列内容，8/16dp间距；类型、带符号变更、变更后重量、豆款/批次、真实原因。列表按原事件倒序，保持局部滚动及scrollY保存恢复。
- 仅有效指定batchId时显示该批次名称/当前余额/初始量与查看批次入口。全库存没有单批次余额；不添加模型缺失时间或杯次来源。
- 长列最多两行并提供整行可访问详情，保留完整字段，最小48dp触摸。正常使用不标危险红。
- 新增八语言列标题与顺序说明，不修改库/事件模型/数据schema/写入。
- 验证Mock/test APK、原single-screen流水滚动恢复/长文本/语言主题；新用户平板截图对照，未有截图前不宣告一致。
- 普通push现有功能分支，不合并、不安装、不发版、不操作硬件；回退追加revert保留记录。
