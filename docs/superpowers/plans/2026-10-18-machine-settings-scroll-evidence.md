# 机器设置底部可达性证据

沿用feature/task-first-ui（develop66ebda9+ed01de0来源）→develop PR#4，遵守docs/git-workflow.md。现有实图只覆盖温度页前两项，不能证明第三项可达。扩展TaskFrameChecks现有真实窗口滚动检查，滚到蒸汽设置按钮，检查标签、输入框、按钮完整可见并输出截图；保留输入值、恢复原滚动位置，不点击按钮、不修改设置。仅Mock测试，无设备/安装/协议/签名/发版变化。验证AndroidTest构建、diff，实际结果待CI；与曲线库批次合并推送，不重启旧CI。回退仅该测试扩展。
