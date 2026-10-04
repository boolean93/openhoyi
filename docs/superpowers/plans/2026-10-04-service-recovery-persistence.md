# 应用恢复确认的存储失败

目标：五种已知机器写入恢复操作在有效恢复证据下，存储失败仍保留pending及控制警告；显式重试成功后才清记录，不重复清除或发送。仅androidTest，不改生产逻辑。

每组真实MobileService.acknowledgeManualSafety：合成READY原机身份、fresh idle/new serial、完整周睡眠回读；注入实际MachineWriteRecoveryState，其Storage先返回false再提交UUID偏好。失败要求确切clear_failed事件，避免因证据缺失提前拒绝而误通过；snapshot警告、pending/raw prefs和设置入口阻挡保持。成功重试写第二次，第三次ACK不写。

隔离：detached Service/Hub，不注册/启动组件，不connect/auth，mock=null，running=false，不访问系统服务/权限。两设备的queue driver拒绝所有execute，核对execute=0且queue inactive；READY/身份仅反射赋值，不是连接。五UUID偏好/TraceStore分组，原应用偏好不变，独立finally清双设备、scanner、handlers及日志/偏好。

边界：模拟Storage.write=false，不证明真实OS/磁盘崩溃原子性；合成READY不证明真实认证/新通知。未覆盖active write互斥/真实设备或注册Service生命周期。

- [x] 初始testAPK/Lint101任务编译通过；/private/tmp/hoyi-service-recovery-persistence-build.log。
- [x] shot prefs额外保持检查的最终编译101任务通过；/private/tmp/hoyi-service-recovery-persistence-final-build.log。
- [x] 独立复审无阻塞；shot fixture raw值保护已补。
- [ ] 实际Mock运行五fixture marker+原矩阵，编译不表示执行通过。
