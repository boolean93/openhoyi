# 表单IME关闭边界诊断

229f798 single-screen38881692281用户平板失败335/336，zh-Hans-light-BrewReviewActivity短字段编辑后cleanupEditor→awaitPageReady超时；14项列表恢复通过。logcat产物为空，现有超时信息未区分焦点、窗口身份或IME窗口，不能判断是产品故障还是观察竞态。

- 来源develop66ebda9+ed01de0；feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。
- 只改ChineseFormWindowChecks：记录每轮实际页面窗口ID、焦点类、窗口ID/type/focus/active/bounds、IME bounds和rootWindowInsets可见性，以及hideSoftInputFromWindow返回值；超时保存独立现场截图，截图失败仅附加诊断，不掩盖原异常。
- 不改变5秒等待、双次稳定判据、输入/焦点操作、失败结果或生产代码；不自动重试失败测试，不取消其他CI。
- 编译test APK、diff/branch检查，普通推送现有分支。后续失败证据定位前不宣告修复。
- 不涉及协议/库存/schema/签名，不安装、不合并、不发版、不操作设备。回退追加revert且保留所有记录。
