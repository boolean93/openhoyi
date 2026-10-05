# 活动事务的恢复确认互斥

目标：五种已知写入的WRITING及transportSuccess后等待回读两个阶段，实际ACK不能清安全记录、调用Storage或改变tracker。相关控制入口不得借此绕过待确认事务。

扩展ServiceRecoveryPersistenceChecks原五fixture：真实tracker.begin/written Success（仅值操作，不走BLE），两阶段检查machineWriteAcknowledgementAvailable=false、ACK存储attempt0/pending/raw prefs/state保持、fake驱动执行0。随后tracker.disconnected转UNKNOWN，补serial2/fresh snapshot后执行原存储false→retrytrue逻辑。

计数：5kind×2阶段=10次被挡ACK。四普通写入×6entry×2=48；BREW_WAIT×5无关entry×2=10，共58次控制阻挡。预热进行中MachineRecoveryWarningPolicy正常隐藏advisory，用确切preheat-block文案校验。startShot属于预热消费流程，不能未经证据假设全面禁用；此预热fixture不以缺曲线拒绝当事务互斥证明。预热消费/温度/start-context由既有独立规则与会话测试覆盖，完整产品整合仍需后续。

隔离沿用detached Service/Hub、合成READY、UUID prefs、强制mock=null与fake驱动所有execute拒绝，禁止真实BLE。此测试不覆盖队列已发送操作撤回或真实设备。

- [x] 初始testAPK/Lint101任务通过，/private/tmp/hoyi-service-recovery-busy-build.log。
- [x] BREW_WAIT语义校准后的最终101任务testAPK/Lint成功，/private/tmp/hoyi-service-recovery-busy-final-build.log；独立复审无阻塞。
- [x] 源码88200b0推送，native37187311009、Mock生命周期37187311005、Mock升级37187311012确认in_progress。
- [x] 同源码/schema Mock升级37187311012成功，实际seed/verify/result已读；/private/tmp/hoyi-service-recovery-busy-upgrade-37187311012。
- [x] 88200b0/Mock生命周期37187311005成功且完整产物读取；5fixture×busyStages2/blockedACK10/blockedEntries58/failedWrites5/retries5、原入口13/78、清理/通知/八语言/五音频、两profile各80页+3marker均通过，证据evidence/service-recovery-busy-2026-10-06.json。native37187311009成功已读取39823/32856/92/7113/发布12日志。
