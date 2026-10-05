# 恢复确认的设备归属

目标：实际MobileService的五种恢复ACK在另一台咖啡机身份或产品断连状态下不得调用Storage或清除原设备记录；校准到合成原机READY后保留现有busy/存储失败/重试测试。

仅扩展ServiceRecoveryPersistenceChecks：每kind先将合成coffee.session.activeAddress改为另一个合法地址，ACK一次，核对确切waiting事件、attempt0/pending/raw prefs/warning保持。还原地址后将snapshot.coffeeState置DISCONNECTED，重复同断言，再还原READY。每kind两次，共10次ACK拒绝；任何fake transport.execute仍禁止。

不是实际重连/认证流程，不修改生产连接或控制。原设备地址来自UUID fixture，原应用prefs保持；独立finally沿用。此测试补产品identity/READY门禁接线，不代替真实GATT身份绑定。

- [x] 5 wrong-identity及5 disconnected ACK断言已实现。
- [x] testAPK/Lint101任务成功，/private/tmp/hoyi-recovery-identity-build.log；shell语法/diff检查、独立复审无阻塞。
- [x] 源码aaa95ee推送；native37373087183已确认in_progress，Mock生命周期37373087119与Mock升级37373087127已确认queued。
- [ ] 新源码云端实际Mock marker与全部既有矩阵，未运行前不声称通过。
