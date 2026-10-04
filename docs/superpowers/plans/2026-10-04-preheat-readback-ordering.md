# 预热温度回读序号

目标：WAITING_TEMP收到较新的低温回读后，旧/相同主机序号的高温回读不能标为READY；更新匹配回读正常确认。仅共享host serial契约，产品计数递增，不声称真实BLE乱序或识别重新编号旧帧。

设计：observe先检查WAITING_TEMP与新序号，再推进afterSample，之后保留原isAtTarget判断。written建立每操作baseline；新操作低基线仍支持。温差规则、目标/曲线匹配、READY锁存、取消/超时token与实际启动前实时检查不改。此类不是持续热就绪检测器，不拿它替代设备控制出队检查。

- [x] 原3tests实际2RED（旧hot、重复serial变hot），新操作fixture原实现通过；/private/tmp/hoyi-preheat-order-red.log。
- [x] 3项GREEN（0失败/错误/跳过），180任务完整本地协议/会话/蓝牙与应用单测、双APK/test APK/双Lint通过，/private/tmp/hoyi-preheat-order-green.log。
- [x] 独立复审无阻塞，未代替执行测试。
- [x] 源码069681d已推送，native37187536336确认in_progress。
- [ ] CI最终日志核验。

真实预热执行/取消/断链恢复仍待硬件证据；软件边界不证明设备安全。
