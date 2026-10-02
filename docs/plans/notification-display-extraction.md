# 通知显示构造剥离

用户已授权持续完成原生功能与架构升级。本轮只拆纯Android显示，不改BLE/协议/资格/恢复或Service通知时序。

设计选择：保持Service私有factory包装，独立MobileNotificationDisplay持有Service的动态Context，不缓存译文，不使用Application Context替代通知来源。Service保留原通道ID、请求码、停止action、通知ID、show/dedup/失败处理；新类只构造connection/safety Notification和创建同ID通道，不能notify、send Intent或调用控制API。

- [x] Android测试先引用不存在的MobileNotificationDisplay.connection/safety，编译确认缺API。
- [x] 原样搬迁连接/安全通知与通道构造；对Service整文件用明确四处变换核对一致。
- [x] 完整协议/会话/共享/双变体测试及三个APK/Mock testAPK/Lint，独立审查；既有通知factory检查继续覆盖八语言。
- [x] 云端验证真实Android构造与新安全通知标记；记录实际刷新、权限及硬件验收仍未覆盖。

文件：MobileNotificationDisplay.kt新增，MobileService.kt四处最小委托替换；LanguageNotificationChecks.kt调用新组件并检查安全通知的旧ID/标题/正文/BigText/ALARM/ongoing/onlyAlertOnce及受保护目的地Intent；不发布通知，不执行PendingIntent。

2026-10-02：测试先因MobileNotificationDisplay不存在而编译失败（不是运行通过），实现后完整回归成功。177项/变体，176通过、1缺真实用户导出跳过；协议39,823检查/32,856通知回放、会话92及共享回归、三APK/Mock测试APK/双Lint通过。19Python、正式资源生成检查与双APK7,081资源/6转义核验通过。a161de9整Service经明确三组替换（属性插入、两个factory委托合组、安全builder委托）正向重放逐字一致；独立静态审查确认原资源读取/ID/Intent/标志保留。新类仅持有Service Context作每次动态资源读取，创建通道，不notify或send。

新增安全factory Android断言覆盖8语言×两目的地×两displayOnly模式的title/text/BigText、ALARM、ongoing、onlyAlertOnce以及预期受保护PendingIntent身份。当前只有编译与静态证据，实际Android构造待云端；不会把这些构造测试标记为真实通知刷新/权限/机器安全验收。

补充运行证据：b20b90f与8458c34的Android34隔离Mock CI均success，产物已核对8语言factory标记及平台通知posting/update/cancel标记。最新36949018081，见native-language-parity.md。上面的“当前只有编译”是首版历史状态；实际Service刷新资格、权限失败、真实设备/硬件验收仍未覆盖。
