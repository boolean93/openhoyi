# 本杯用豆：批次列表与右侧表单

依据已认可bean-preparation-v1。沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md；当前工作树干净。

1. BeanPreparationActivity使用TaskWorkspaceLayout(.5,680)形成近等宽左右栏；左侧已有ScrollView显示批次按钮列表，右侧表单显示所选豆款、粉量、原库存预览和称豆入口。左侧20sp标题，16sp批次行、至少72dp触控高度，选中浅色底/焦点边框，行间8dp。无分页、不伪造日期或库存。
2. 初始draftBatchId不存在时选择真实第一批；点击只修改内存ID和右侧文字，不触发preparation.select或扣库存。粉量保留原EditText/校验/草稿恢复；提交按draftBatchId找batch。保留保存选择→确认扣减两个步骤、未知结果恢复、重复确认、取消/丢弃、称豆权限门禁。
3. AdvancedBusinessUiChecks替换Spinner实现依赖为真实批次按钮/ID选择断言，并截图草稿、待确认、已确认三状态；原18路径、事件数及余额断言不变。相关短高/IME检查沿用实际字段与滚动可达性，不缩小点击区。
4. Mock/test APK构建、diff、分支检查，普通push后CI核对列表选中/完整字段/扣减前后实图；不安装/设备操作/发版/合并，不改schema签名；追加revert回退。布局与认可稿其余差距继续记录，不能用编译证明视觉通过。
