# 历史截图等待与入库留白修正

沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

## 已观察失败

- 784eaaf单屏335/336：ar-light-HistoryDetailActivity缺已填充曲线；失败截图仍显示读取本地样本，源码由history-detail-reader异步post，测试只等300ms。按既有对比页模式等待至少2样本且布局稳定，10秒上限，超时报告实际点数/加载说明；原存在性/96dp绘图区检查保留，不修改产品数据加载。
- ff651be单屏334/336：中文浅/深入库页第三日期ImageButton 108×108仅43px可见，来自新增模式区16dp底距和日期分隔24dp额外边距。模式区底距16→8，分隔上下边距8+16→0，两个普通字段标签顶部4→0，共收回40dp；字段字号/高度、日期行8dp间距、48dp日历及保存按钮不缩。保留三日期全可见断言，不能用可滚动替代标准平板单屏目标。

构建Mock/test APK，diff/branch-policy；新CI验证实际等待和字段完整。既有CI不取消/重启。无安装、设备写入、合并或发布，不改schema/签名；普通push，追加revert回退。
