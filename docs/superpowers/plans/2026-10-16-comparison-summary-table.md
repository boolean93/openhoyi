# 两杯对比摘要表

依据brew-comparison-v1；来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。

- 新ComparisonSummaryTable：三列（指标、A、B），时长/结束秤读数/口感三行，14sp表头、16sp值、8dp单元格留白及border分隔。缺失字段用—，不以0替代；数据来自已选BrewJournal.Entry，不从曲线插值推算结束值。
- 放在TaskComparisonLayout底部详情滚动区最前；原两杯来源/采样/对齐/缺失/重置/间隙说明和详情笔记入口保留其后，不删警告。口感摘要单行省略，48dp可点击区域打开完整文本。
- render按实际first/second选择更新摘要，空选择清除旧值，交换随身份更新。扩展现有页面测试检查清空时摘要为—及交换后显示两杯原摘要，不写库存或日志，不改数据持久化。
- 编译Mock/test APK；原四指标/样本/交换回归及single-screen保持，后续需实际平板截图证明表格比例/可读性。当前不改指标下拉控件。
- 普通推送现有PR，不合并/安装/发布/设备操作；无协议/schema/签名变动。回退追加revert保留记录。
