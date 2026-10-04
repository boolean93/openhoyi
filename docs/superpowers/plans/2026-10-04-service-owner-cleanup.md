# 服务释放设备所有者的异常边界

目标：系统销毁MobileService时，Hub关闭回调异常不能逃逸并保留旧hub引用；服务callback与双方ticker移除、fake连接关闭，不清除shot/machine write未确认记录，不补发控制命令。

根因假设：onDestroy尾部hub.close后才置hub=null/hubForeground=false；Hub虽已能清理两端，仍按约定重抛观察者异常，宿主未隔离。

测试：独立未注册Service+独立Hub，禁止系统服务（通知故障仅SecurityException）、权限/组件，偏好仅UUID fixture；原Session inactive队列替换fake传输，只fake Connect；真实onDestroy，在coffee终态注入观察者异常。主任务全部异常回传instrumentation线程，finally独立释放handler/设备/journal/fixture，保持注册Mock服务与真实偏好不变。

- [x] 本地测试APK/Lint101任务通过，独立静态复审无阻塞。
- [x] fdb56c6/Mock37183314102实际RED，ServiceOwnerCleanupChecks:102命中Owner close exception escaped service destruction，非反射/编译/超时；日志/private/tmp/hoyi-service-owner-red-37183314102/mock-lifecycle/language-instrumentation.txt。
- [x] 最小生产修复及最终本地双APK/双单测/双Lint/testAPK、协议/会话/传输180任务通过（/private/tmp/hoyi-service-owner-green-final-build.log）；补充主线程拒绝检查后独立复审无阻塞。
- [ ] 实际Mock GREEN和完整云端回归。

范围仅Hub关闭Exception；不声称反馈/采样等此前步骤的全部故障、真正注册Service/Binder关闭、VM Error或硬件验证。正常shutdown的未确认门禁必须保持原样。

最小修复：shutdown原全部退出门禁通过后与onDestroy尾部共用closeDeviceOwner。保持关闭过程中hub可见及原回调顺序；捕获Hub关闭Exception只记录类型/抑制异常数量，finally清hub引用及hubForeground。正常shutdown随后原running=false/通知/handler/snapshot/stopForeground/stopSelf继续；onDestroy随后super继续。没有清除持久未确认记录、修改报文或调用重试。

复查线程边界：helper在catch之前检查主Looper，非所有者线程必须拒绝且不清Hub引用。新增独立worker反向探测，要求反射cause为IllegalStateException、hub/hubForeground及两个fake连接未改，再回主线程销毁。此新断言尚待GREEN，不声称先前RED覆盖它。
