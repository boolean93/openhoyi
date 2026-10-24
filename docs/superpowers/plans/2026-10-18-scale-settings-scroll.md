# 秤设置完整滚动取证

沿用develop66ebda9+ed01de0来源feature/task-first-ui→develop PR#4及docs/git-workflow.md。bcda211首屏协议说明靠近系统栏，补实际滚动状态而非默认所有内容已验收。

复用现有TaskFrameChecks设置滚动工具，支持ScaleSettingsActivity。定位完整scale_offline_evidence，滚动后等待两帧，验证globalVisibleRect与textFits，保存独立PNG与结果；校验scale_tool偏好未变，恢复原滚动。应用设置两状态/版本断言及24页数量不变，不点击开关、导出或设备控制。

验证测试包编译及diff，运行结果待CI。与已有机器设置调整整批推送，不新建手动流程。无设备/安装/合并/发版，版本签名schema不变；回退新增取证即可。
