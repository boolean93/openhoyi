# 库存首屏空间整理

沿用bean-inventory-v3及整合规范，feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。0d6fb9c用户平板有数据实图仅显示最近流水标题；工具行和独立批次编号行增加固定占用。仅标准宽屏screenWidthDp≥1000且fontScale≤1.1把原工具容器加入现有标题栏，保留大字/较窄原分行；BeanPageRegions补header引用而非依赖索引或重建标题。批次编号与剩余重量标签同行（各半宽），保留14sp，其他数值/字段/流水/操作不变。所有原入口和回调保留，不删流水/缩字体，不改库存或控制。

修改BeanScreenUi.kt和BeanInventoryActivity.kt，验证Mock/AndroidTest编译、diff及既有导航/业务/8语言页面CI；读取后续用户平板实图确认标题不挤压与流水实际空间，不能预判全表已可见。无设备操作/安装/合并/发版/签名变化，合批普通push，回退追加revert。
