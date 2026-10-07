# 安全通知成功后去重

问题：MobileService.updateSafetyNotification在获取NotificationManager及notify/cancel之前更新safetyMessage。临时异常后相同警告被去重，不能在后续watcher中重试。该缓存只涉及显示，不是机器安全记录。

范围：保持通知key、目标页面、语言刷新、警告计算、运行资格及错误反馈不变。仅成功完成连接通知与安全通知/取消后，才更新运行中对象的去重缓存；任何一步失败保留之前成功的缓存。停止对象的现有缓存行为保留。失败不循环立即重试，等待现有下一次刷新。不得重发蓝牙、清除持久记录或改控制资格。

验证顺序：
1. 先扩展现有NotificationFailureChecks，用同一个脱离注册生命周期的Service连续刷新相同警告。失败后缓存应为空，第二次应重新获取通知服务；保持snapshot控制字段、记录、running和hub不变。当前代码应在首次缓存断言失败。
2. 仅在Android34云端Mock实际复现失败后修改缓存提交时机。
3. 复用既有LanguageServiceNotificationChecks，验证成功后同警告仍去重、强制语言刷新仍更新；补充notify/cancel阶段的局部失败场景需真实平台或隔离代理，不能把lookup失败当作覆盖全部平台调用失败。
4. 回归构建、独立复审、云端Mock运行，并记录精确源码和产物。

测试对象没有onCreate/onStart或BLE，不代表真实注册Service生命周期、系统通知权限行为或机器验收。
