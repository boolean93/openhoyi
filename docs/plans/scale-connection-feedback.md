# 设备连接反馈去歧义

- 需求与范围：设置页分别显示当前状态与已记地址，避免连接新设备时将旧地址误认当前目标；正常非重量/同步通知只记诊断，不显示通信异常。
- 来源分支/基线提交：develop / 31e1ea9。
- 工作分支：fix/scale-connection-ui；PR 目标 develop。
- 诊断补充：现有 wire trace 增加写入特征属性位、withResponse、Android writeType、回调单调耗时和断链 newState；不记录额外地址/身份，不改变握手。
- 文件与步骤：SettingsDeviceControls、DeviceStatusText、MobileService 的显示映射；独立诊断分类及最小回归测试。
- 推送与合并：本地完整验证后普通推送，通过 scripts/create-pr 创建草稿 PR，精确 head 的 branch-policy/verify 通过后正常合入。
- 验证：正常通知与 Invalid/真实错误分类；中英文状态与已记地址分隔；完整本地门禁。无设备时如实保留实机布局限制。
- 协议与设备风险：仅显示/日志，不改 BLE 握手、控制门禁、重连、解析及记忆设备保存时机。Unknown 原始通知仍由 wire trace 保留。
- 发布：不发版、不安装、不操作设备；签名/升级无变化。
- 回退：revert PR，不清除用户设置或日志。
- 阻碍：云端无 gh 登录，Mac 离线时不能按 scripts/create-pr 创建 PR；不得冒称发布。
