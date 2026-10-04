# 通知文案解析异常隔离

目标：警告文案或错误报告资源缺失不得让通知方法逃逸异常，进而阻断100ms状态检查后续调度。

设计：在普通通知刷新外围捕获RuntimeException并记录固定tag/异常类型；内部显示逻辑不改优先级、缓存时刻、ID或控制条件。notificationFailure的event解析异常单独捕获，解析失败保留原snapshot消息，不用硬编码提示替换控制状态。不会发送新命令、清理未确认记录或启动服务。

测试：沿用NotificationFailureChecks的独立Mock Service、禁止BLE/组件Context与实际pending fixture。将manualSafetyResource临时设为无效ID0，调用实际刷新：不得抛、访问manager或修改snapshot/警告缓存，finally恢复资源ID。再用无效资源ID调用实际错误报告方法，要求保留消息与控制阻止。该故障注入只证明资源缺失两条路径，不证明注册Service生命周期或全部Android错误。

- [x] 新断言在原修复上运行RED（fe8e5cc/37132523724），警告文案解析抛Resources.NotFoundException，位于MobileService:1346；第二项错误报告断言随后待GREEN执行。
- [x] 最小外围和错误报告保护，所有原断言保留；双APK/双单测/双Lint/testAPK170任务成功。
- [ ] 本地构建/单测/Lint、实际Mock GREEN、完整原语言/音频/页面与升级回归。
- [ ] 独立复审及证据更新。
