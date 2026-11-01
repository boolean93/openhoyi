# 用豆库存前后值层级

依据已认可bean-preparation-v1。沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

DoseBalanceView只展示调用方提供的before/after毫克值，不读取或写库存。两列14sp“扣减前库存/扣减后库存”，40sp/700数值、0.55比例g；后值使用accent，中央箭头。下方保留原14sp“预计…尚未扣减”或“本次扣减流水”完整语义及原TextView tag，避免把预览冒充已扣减。16dp上下间距；未知/非法粉量隐藏整块，不显示假零。

BeanPreparationActivity草稿和待确认仍从真实余额减合法粉量；已确认/关联待恢复仍只从匹配eventId/batchId/kind/amount的持久化事件计算前后值。余额计算、操作门禁、幂等不改。八语言补前后标签；原业务真实余额断言与三状态实图检查保留。Mock/test APK、XML、diff/分支检查；CI实图再验收，不安装/设备写入/合并/发版，不改schema签名，追加revert回退。
