# 批次详情信息层级

依据bean-batch-v1与abe9614真实batch-after-adjustment.png。沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4、docs/git-workflow.md。

BeanBatchActivity剩余重量升为32sp/700，单位0.55比例；初始重量24sp不变。摘要比例改1:1:2，剩余比例条和百分比同行；添加摘要上下分隔、20sp批次信息标题，日期布局保持三列。流水行添加细分隔，数据/点击详情/库存调整及确认不改。8语言标题资源补齐。

InventoryEvent无时间字段，不能伪造认可稿时间列；保持真实类型/变化/原因，记录尚未对齐项，不改存储schema。Mock/test APK构建、资源XML和diff检查；CI再验证完整字段、大字号/短高实图。普通push；不安装、设备操作、合并/发版，不改签名；追加revert回退。
