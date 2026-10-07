# 停止请求与显示故障隔离

延续已授权的控制安全升级：停止入口的提示/日志必须在停止请求之后，普通显示异常不能阻止停止，也不能使未确认记录被清除。保留原手动机器/萃取状态/连接资格，不变更报文、超时、重试与硬件完成判定。

根因：MobileService.stopShot真实路径先event再manualStop；event中的资源解析及日志记录为同步依赖。TraceStore正常磁盘/满队列失败已隔离，此任务不把磁盘故障当作当前同步错误。验证注入独立Service未初始化日志字段，明确为合成故障，不是正常生命周期重现。

方案：小型StopRequestDelivery按dispatch→report执行，只捕获报告的Exception；dispatch失败不吞，报告的fatal Error不吞。Service只在原资格通过后使用。Mock既有路径不变。

- [x] 先以原report→dispatch顺序做可编译RED，四JUnit覆盖顺序、报告Exception、派发Exception、fatal报告Error。
- [x] 修正共享执行顺序并接真实Service。
- [x] Android fixture使用真实Service/Hub/Session/Controller与Guarded fake GATT：先正常提交一个合法无重量目标start，真实回调Success后注入日志未初始化，stopShot仍发原槽位停止字节；保留pending记录，重复停止不重发。纯合成READY、不注册Service、不扫描/BLE/系统/组件。
- [x] 完整离线回归、test APK/Lint、独立审查、推送。未读取Android结果前不宣称实际fixture通过。

停止报文写成功也不证明物理停水；无新idle证据时仍STOP_REQUESTED，重建仍恢复pending。

本地RED4fail与最终219tasks/49s通过，双mobile各182/0fail/0error/条件导出skip1；独立复核无阻塞。Android运行结果仍待推送读取，以上最后一项仅完成本地验证与提交准备。证据见evidence/stop-feedback-boundary-2026-10-07.json。
