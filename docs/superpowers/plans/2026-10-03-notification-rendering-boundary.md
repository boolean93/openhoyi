# 通知文案解析异常隔离

目标：警告文案或错误报告资源缺失不得让通知方法逃逸异常，进而阻断100ms状态检查后续调度。

设计：在普通通知刷新外围捕获RuntimeException并记录固定tag/异常类型；内部显示逻辑不改优先级、缓存时刻、ID或控制条件。notificationFailure的event解析异常单独捕获，解析失败保留原snapshot消息，不用硬编码提示替换控制状态。不会发送新命令、清理未确认记录或启动服务。

测试：沿用NotificationFailureChecks的独立Mock Service、禁止BLE/组件Context与实际pending fixture。将manualSafetyResource临时设为无效ID0，调用实际刷新：不得抛、访问manager或修改snapshot/警告缓存，finally恢复资源ID。再用无效资源ID调用实际错误报告方法，要求保留消息与控制阻止。该故障注入只证明资源缺失两条路径，不证明注册Service生命周期或全部Android错误。

- [x] 新断言在原修复上运行RED（fe8e5cc/37132523724），警告文案解析抛Resources.NotFoundException，位于MobileService:1346；第二项错误报告断言随后待GREEN执行。
- [x] 最小外围和错误报告保护，所有原断言保留；双APK/双单测/双Lint/testAPK170任务成功。
- [x] 本地构建/单测/Lint通过；a9ef9a7实际Mock37181425760 GREEN，新增rendering/errorReporting两项及原八语言/五音频标记完整，compact/wideFont各80页及3标记。升级37181425752真实Mock同源码/schema1→2通过，不代表Alpha或历史schema迁移。
- [x] 独立复审无阻塞；native37181425768通过并读取39823协议检查/32856通知回放/92会话场景、资源7113×2/6回环、发布fixture和12场景标记。日志在/private/tmp/hoyi-notification-render-{green,upgrade,native}-对应runID。
