# 应用入口的持久恢复门禁

目标：实际MobileService六个机器控制入口在持久未确认记录存在时先拒绝，而不是依赖hub缺失或设备不READY拒绝。无新有效设备证据时ACK不得清记录。保留生产流程，新增仅androidTest。

13fixtures：六machine-write kind各搭配shotPending false/true，另加noPending对照。调用设置、杯数重置、周睡眠、立即睡眠、预热、萃取六入口共78次，前五必须返回实际machineControlSafetyMessage；startShot允许并存machineWriteSafetyMessage优先。noPending对照必须返回service_unavailable，避免把缺owner当安全门禁证据。每组调用ACK并核对warning/raw prefs/原应用prefs不变。

隔离：detached Service，只attach UUID prefs context与局部TraceStore，不调用onCreate，不注册Service，不建立hub，强制mock=null走生产分支，拒绝权限/systemservice/组件派发。主线程传出Throwable，finally移除handler、关闭/drain TraceStore与删除fixture prefs。固定Mock包断言。无真实BLE。

范围：持久恢复gate入口和无有效证据ACK；不覆盖READY设备/已在队列中的操作互斥、可清除的恢复证据或commit失败。不能将无owner测试说成真实设备控制验收。

- [x] Mock testAPK和Lint编译通过，最终101tasks；/private/tmp/hoyi-service-recovery-gates-final-build.log。
- [x] shell语法及diff检查通过。
- [x] 独立复审未见调用/隔离阻塞；采纳marker语义修正，ACK attempts=13不称全部被拒绝（noPending/UNKNOWN可能no-op）。
- [x] 源码8ab251c推送，native37186585789、Mock生命周期37186585805、Mock升级37186585831均确认in_progress。
- [ ] 实际云端instrumentation 13fixtures/78entries及原矩阵marker核验；编译不表示测试执行通过。
