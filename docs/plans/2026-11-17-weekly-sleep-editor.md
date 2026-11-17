# 每周睡眠统一设置与逐日编辑

- 需求与范围：每天统一睡眠设置并直接逐日微调。
- 来源分支/基线提交：develop b7c870d066d9a3d9bf327103f0f6adfe80f8322e。
- 工作分支：feature/schedule-batch-cup-count。
- PR目标：develop；此功能单独PR，由协调线程完成本地门禁/独立审查/合入，本线程不合并。
- 改动文件与步骤：MachineSettingsControls 直接编辑；SleepScheduleEdit 显式统一周计划策略；MobileService/MockDeviceRuntime 接受显式统一计划；本地化每天；纯逻辑测试及直接编辑视觉契约。
- 推送与合并：本地检查完成后交协调线程，必须独立PR；未经审查不合并。
- 验证证据：SleepScheduleEditTest 4项、MockDeviceRuntimeTest 19项通过；Mock编译及lint通过；视觉契约Python 8项及最终Android测试编译收尾。
- 协议与设备风险：协议完整周报文及顺序不变；单日仍限制仅一日变化，批量必须明确 allDays 且目标七天相同。保留基线回读比对、设备所有权、空闲新鲜度、持久未知记录及写后回读确认，无自动重发，不操作真机。
- 发布：不发版，不安装设备；Mock原有只预览流程保留。
- 回退：回退本分支提交；不更改持久数据/设备未知记录。
- 未完成与阻塞：真机排程写入验收留待明确设备测试窗口，不以编译代替验收。
