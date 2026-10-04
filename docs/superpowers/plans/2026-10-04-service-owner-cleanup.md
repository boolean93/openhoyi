# 服务释放设备所有者的异常边界

目标：系统销毁MobileService时，Hub关闭回调异常不能逃逸并保留旧hub引用；服务callback与双方ticker移除、fake连接关闭，不清除shot/machine write未确认记录，不补发控制命令。

根因假设：onDestroy尾部hub.close后才置hub=null/hubForeground=false；Hub虽已能清理两端，仍按约定重抛观察者异常，宿主未隔离。

测试：独立未注册Service+独立Hub，禁止系统服务（通知故障仅SecurityException）、权限/组件，偏好仅UUID fixture；原Session inactive队列替换fake传输，只fake Connect；真实onDestroy，在coffee终态注入观察者异常。主任务全部异常回传instrumentation线程，finally独立释放handler/设备/journal/fixture，保持注册Mock服务与真实偏好不变。

- [x] 本地测试APK/Lint101任务通过，独立静态复审无阻塞。
- [ ] 实际Mock RED；编译不代表已运行。
- [ ] 最小生产修复及本地回归。
- [ ] 实际Mock GREEN和完整云端回归。

范围仅Hub关闭Exception；不声称反馈/采样等此前步骤的全部故障、真正注册Service/Binder关闭、VM Error或硬件验证。正常shutdown的未确认门禁必须保持原样。
