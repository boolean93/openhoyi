# 用豆准备工作台实施规划

沿用已认可规格§7 BeanPreparation、§8库存边界与横屏修订。遵守docs/git-workflow.md；develop66ebda9/显式ed01de0依赖，feature/task-first-ui → develop draft PR#4。仅普通推送，不合并、不发版、不安装设备；追加提交回退，保留原数据及未知记录。

- [x] BeanPreparationActivity复用TaskWorkspaceLayout。主区原生局部滚动放完整豆名/批次/粉量或选豆表单，支持区放状态、称豆工具和库存前后值，确认/返回留在固定操作区。IME保留同一输入框，不用字段弹窗。无竖屏专项优化。
- [x] 新选择与保存但未尝试确认的状态显示只读预计余额；已扣减仅从匹配eventId/batch/kind/delta的真实流水显示当次前后余额；未知/冲突不显示推算成功。库存可能后来变化，流水余额明确标“本次扣减”。不修改select/confirm/discard/associatePending、幂等UUID或任何协议门禁。
- [x] AdvancedBusinessUiChecks现有17路径补断言：90→72预览不写库存；保存后仍预览；确认并重建后真实事件90→72；旧重复确认与不回库断言继续。增加八语言资源，不改原文意义。
- [ ] 本地JVM/两MockAPK构建；随后精确源码横屏CI、实际截图、IME和大字检查。没有本地模拟器，原生断言未运行不能写成已通过。

文件：BeanPreparationActivity.kt；values*/advanced_dose.xml；AdvancedBusinessUiChecks.kt。测试与视觉验收尚未完成；不因此缩小全21页范围。

本地最终验证：`/tmp/task-dose-workspace-final-build.log`，完整JVM/两MockAPK BUILD SUCCESSFUL in42s，295tests/0fail/0error/1skip。8语言新资源格式校验通过。新增原生余额断言已编译，尚未执行；精确源码CI/读图/独立审查仍未完成。
