# 通知异常不阻断服务处理

目标：修复普通刷新/销毁中的通知管理器获取及取消异常，避免显示失败中断回调或资源清理。用户已授权继续开发与离线验证；不用实机。

设计：保留通知ID、优先级、文案、去重、控制/恢复条件和BLE流程；仅在普通刷新获取管理器、取消安全通知处捕获RuntimeException。普通刷新保留已有错误事件，清理阶段只记录固定tag和异常类型。取消失败不跳过后面的Handler/Hub清理。显示专用路径仍不改变snapshot。

测试：androidTest中创建独立Mock Service对象，附加禁止BLE/组件调用的Context；正常刷新时注入NotificationManager lookup SecurityException，要求被捕获且控制字段/恢复偏好不变。销毁测试只设置独立对象的Application字段、主线程排入自己的回调，通知lookup失败后必须执行完并清除回调，不触碰真实Mock owner。它证明特定异常与detached清理，不证明注册Service生命周期或真实系统notify/cancel异常。

- [x] 先加测试，在云端确认原方法因SecurityException失败（RED）：b4618aa/37131421784，refreshSafetyNotification:1350；Android34 Application字段反射先执行成功。
- [x] 最小修改普通刷新与公共取消helper，复用到shutdown/onDestroy；协议与恢复条件不变。
- [ ] Mock实际运行GREEN，原语言/音频/页面检查保留；构建、单测、Lint与升级回归。
- [ ] 独立复审并更新证据与handoff。

当前验证：bc593e1修复已推送，本地双APK/双单测/双Lint/testAPK170任务成功；fixture加强pending记录后testAPK/Lint101任务成功。独立静态复审未发现必须修复项。云端Mock37131995532和升级37131995440运行中，GREEN未证明；本测试不模拟NotificationManager.notify/cancel自身异常，也不证明注册Service生命周期及文案资源解析失败。
