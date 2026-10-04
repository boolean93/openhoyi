# 恢复确认的时间证据

目标：五种机器写入恢复门禁（杯数、设置、周睡眠、立即睡眠、预热等待）只允许非负单调时间域中的新鲜idle证据。负时间、未来时间、减法溢出不能允许人工确认清除未确认记录。当前产品elapsedRealtime正常非负，本修复针对共享API无效输入，不宣称发现真机发生该错误。

设计：类内统一freshIdle，先检查at非空、at>=0、now>=at，再计算now-at<=1500，非负有序Long域不会溢出。不改设备身份、写入状态、新序号、参数条件、人工确认、持久化clear或协议发送。

- [x] 五kind×负时间/溢出/合法边界共15测试，原实现10实际RED、5合法边界通过；/private/tmp/hoyi-recovery-clock-red.log。
- [x] 最小修复15GREEN（0失败/错误/跳过），完整180任务构建、原会话检查、双APK/test APK/单测/双Lint通过；/private/tmp/hoyi-recovery-clock-green.log。
- [x] 独立复审无阻塞，不代替实际执行测试。
- [x] 源码58fd8fc推送，native37186079149及自动Mock升级37186079174已确认in_progress。
- [x] Mock升级37186079174成功且seed/verify/result实际产物已读取，/private/tmp/hoyi-recovery-clock-upgrade-37186079174；同源码/schema Mock1→2，不代表Alpha/历史迁移。
- [x] native37186079149成功且实际日志读取，/private/tmp/hoyi-recovery-clock-native-37186079149.log；39823协议/32856通知/92会话/两APK各7113资源+6回环/发布12及临时签名APK通过。

测试只读canClear资格并检查pending/disk不变，不能证明产品完整恢复行为或真实硬件安全；现有Mock与后续设备验收仍有独立作用。
