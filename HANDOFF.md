# Native BLE handoff

分支 `feature/native-ble`，worktree `../openhoyi-native`，基于独立openhoyi仓库初始LICENSE。旧hoyi-project未改动。

本轮交付可构建的三个库模块，无APK、无新代码实机控制验证。使用README命令运行全部门禁，不能只运行Gradle默认test而漏掉verify。

重点文件：
- protocol-core/.../Protocol.kt：真实帧长度、BOOKOO ASCII符号。CoffeeCommands.kt：数值密码认证和20字节编码。
- device-session/.../GattQueue.kt：连接代次、串行、超时关闭、取消排队启动。
- DeviceSession.kt：固定GATT端点，1.1.3能力门禁，BOOKOO初始化后才Ready。
- ExtractionPolicy.kt / ExtractionController.kt：定点重量单位0.01g、去皮确认、样本时效、一次停止、结果未知。
- bluetooth-android/.../NativeDeviceHub.kt：service-owned双设备集成、前台窗口自动连秤。

当前策略常数不是硬件认证规格：样本1.5s过期；启动后1.5s请求去皮；去皮后新样本绝对值≤0.5g确认；启动4s未确认则尝试停止；至少7s才允许目标重量停止；明确idle帧且距最后阀开帧>2.8s才结算。均须实机标定。

控制入口仅开放三个实采曲线包。不要为了UI演示移除门禁。其他固件为Unsupported但可解析已知长度帧；其他秤没有适配实现。设置编码存在不等于设备会话开放设置控制。

回放资料：protocol-core/src/test/resources/notifications.tsv（匿名role/seq/相对时刻/原始通知）；provenance.json（来源哈希、曲线UI参数）；device-session/src/test/resources/shots.tsv（三次窗口）。原始日志仍在父目录captures，不提交全量私有日志。

可重新提取：`python3 scripts/extract_fixtures.py ../captures/2026-09-20/protocol-recordings`；`python3 scripts/extract_shots.py ...`。生成后检查provenance UI快照非空。

已发现并处理：密码数字字节与ASCII区分、符号编码差异、队列回调异常卡住、非法参数残留启动状态、停止后旧排队启动再次发送、启动后早到idle误结算。上述故障均有回归测试；另补满队列停止优先级测试。最终25个命名会话场景通过，协议34,991次断言通过，AAR和lint通过（0错误2警告）。

新模块的独立审查和新代码实机测试仍待完成，详见docs/coverage.md。当前代码未推送GitHub；没有触碰原App安装。
