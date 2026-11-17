# 维护页累计杯数

- 需求与范围：维护页显示累计杯数；只显示新鲜设备回读，未知或过期显示 —，真实零值显示 0。
- 来源分支/基线提交：develop b7c870d066d9a3d9bf327103f0f6adfe80f8322e。
- 工作分支：feature/schedule-batch-cup-count；独立杯数提交供协调线程拆到独立PR。
- PR目标：develop。
- 改动文件与步骤：MachineSettingsControls 新增读数；MachineSettingsPresentation 格式化；展示回归测试。
- 推送与合并：由协调线程创建独立PR、执行门禁、审查后合入。本线程不合并。
- 验证证据：7项 MachineSettingsPresentationTest 通过，含新鲜/过期/未知/零值；与排程变更合测 Mock 编译及 lint 通过，独立PR仍需验证。
- 协议与设备风险：只读显示；不改变杯数清零命令、确认及未知结果门禁。未操作设备。
- 发布：不发版、未安装；UI实机未验收。
- 回退：回退代码，不更改持久数据及未知写入记录。
- 未完成与阻塞：无开发阻塞；实机显示待验收。
