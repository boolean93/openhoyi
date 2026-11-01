# 两杯对比不同轨迹取证

依据051f16e实图：两杯31点完全相同，虽然控制/图例/实虚线实现存在，视觉只见重合线，不能证明不同轨迹可辨识。沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

仅修改Mock instrumentation fixture：新增v2命名两杯，第一杯30s/36g，第二杯32s/35.2g；压力峰值/衰减、注水、温度、重量均不同。按各自结束时长/末重量写Observation；不覆盖原命名样本或用户记录，已有同名内容不同仍失败。四指标切换时断言两个投影均非空且不同，再截图；sources.txt记录明确fixture版本/两杯时长/点数。原历史、交换、缺失选择和不变性检查不删。

构建test APK、diff/branch-policy，CI新图验证分离的实虚线。仅测试数据，不更改产品采样/显示算法/存储schema，无安装/设备写入/合并/发布；普通push、追加revert回退。
