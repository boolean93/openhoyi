# 全应用可见性与配置重建连续性

沿用native-language-runtime-audit第4项：显示重建不得打开新的连接窗口。实际代码只有Home/Extraction/MachineSettings三页注册VisibleScreens，Curve/History等页面漏报；最后一页onStop后500ms关闭前台，慢重建会重开NativeDeviceHub.foreground的600s窗口。

方案：Application生命周期回调统一报告所有本应用Activity的started状态。纯AppVisibility保存逻辑页面owner；配置重建停止时保留仅曾started的owner，Bundle中保存owner并给替代实例继承。替代实例started恢复实际集合；真正stop/destroy释放owner。后台页面仅created/保存状态不能创造可见性；不保存Activity/Context到纯模型。Service按唯一owner订阅/取消，启动Hub时读同一可见性，原500ms普通页面切换宽限保留。移除三页手工报告，页面绑定/刷新/停止按钮不变。

本机Android34 SDK源Activity.recreate/isChangingConfigurations与ActivityThread.handleRelaunchActivityInner确认：配置标志先于旧onStop设置，旧save/destroy后启动替代实例。只作为源码证据，不当成真机验证。

任务：
- [x] 旧统计复现慢重建错误前后台序列；新纯模型测试先失败。
- [x] 实现纯AppVisibility及Android callbacks、Application接入。
- [x] Service观察接入并取消订阅，移除三页手工报告；不改Hub/重连策略/协议/队列/恢复/600s控制。
- [x] 覆盖重复/重叠页面、长重建、多次重建、真实后台、从未started、进程重启、服务订阅生命周期。
- [x] 独立静态审查、完整离线回归/构建/资源验证并记录运行时限制。

前提：main线程生命周期顺序。重建保留只在框架标记changingConfigurations时生效，不用不断延长的超时模拟真实可见性；旧实例销毁不解除替代实例的配置owner。真正离开仍由新实例正常停止释放。框架异常/进程终止不能由纯测试证明，Android重建+Home/锁屏/多窗口待设备恢复后验证。

## 已验证的证据与待验项

旧VisibleScreens慢重建事件序列测试先真实AssertionError：期望[true]，实际[true,false,true]；该测试只是旧统计与500ms回调可达路径的复现，不是Android运行时测试。新模型6项测试先缺AppVisibility API失败再通过，覆盖重复重建、页面重叠/重复停止、后台或未started重建、被放弃替代destroy、服务观察者交替、一个页面重建与另一页面离开。移除旧VisibleScreens及其1项测试。

Application回调适配器用IdentityHashMap暂存存活Activity与String owner对应，destroy时移除引用；save实例状态保留逻辑ID。纯状态不保存Activity、Context、计时器或蓝牙对象。onStarted回收rebuilding owner；onStopped配置标志为true且曾started才保留。Application.onCreate先注册回调，Service订阅立即得到当前状态，onStartCommand创建Hub后再次读取实际可见性，onDestroy只取消自己的owner订阅。

整份Service按声明可见性源/观察接入替换重放一致，三页只移除手工报告（设置页多移除无用UUID import），绑定/刷新/停止入口不变。NativeDeviceHub、ReconnectPolicy、协议/队列/恢复源码未改。普通导航500ms宽限仍在，不是无条件免除后台处理。所有本应用Activity都会参与，外部文件选择器或系统设置页面不被误计为本应用页面。

完整离线协议39823检查/32856回放、会话92场景、共享69JUnit通过；Alpha/Mock各145项（144通过、1缺真实旧导出跳过），三APK构建成功。19Python、生成一致性、6项真实AAPT转义fixture及两APK各6969编译文字一致；独立静态审查无实质问题。

尚未执行Android运行时。配置替代若中断，保留owner依赖框架交付替代实例的真实stop/destroy才能释放；纯模型无法保证OEM交付这些回调。快速Home/锁屏、多窗口、后台配置变更及重建中断均待验，见alpha-acceptance.md。未开放语言选择，未操作真机；整体功能与协议硬件安全仍未完成。
