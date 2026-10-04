# 双设备所有者清理

目标：NativeDeviceHub.close在一个设备终态观察者抛Exception时，仍关闭另一个设备并移除全部所属ticker；不扫描或补发写入，原首个异常继续传播，多异常保留suppressed。

根因假设：当前close顺序scanner→coffee→scale，coffee.close的session.disconnect异常会跳过scale.close。先用真实Hub/AndroidDevice/DeviceSession配独立fake GattQueue传输在Android34 Mock复现，再修改生产源码。

测试边界：独立Context保留自身applicationContext且禁止system service、权限和pref访问；仅单个runOnMainSync任务内构造独立Hub，反射替换其未active队列驱动，再fake Connect，不调用真实扫描或连接、不注册Service、不触碰现有Mock owner。正常、coffee失败、scale失败、两项失败四种fixture，finally独立清理防止RED泄漏。检查双方close一次/无新operation/终态、三个ticker删除及异常传播。

- [x] 测试APK/Lint本地101任务通过；独立复审两处异常回传/反射收尾问题修正后无阻塞。
- [x] d81b53b/Mock37182632720实际RED：HubCleanupChecks:67的scale关闭断言失败，Device owner was not closed；独立反射/fake连接/正常fixture先运行，主线程异常准确回传，非超时或测试编译失败。日志/private/tmp/hoyi-hub-cleanup-red-37182632720/mock-lifecycle/language-instrumentation.txt。
- [x] 最小清理修复及本地双APK/双单测/双Lint/testAPK、协议/会话/传输180任务成功（/private/tmp/hoyi-hub-cleanup-green-build.log）。
- [x] ef146a4/Mock37183082271实际GREEN，4组Hub清理marker、通知故障/原8语言/5音频以及compact/wideFont各80页+3标记完整，已读取产物。
- [x] 独立静态复审无阻塞，清理顺序/异常传播/协议门禁及无补发保持。
- [x] ef146a4/native37183077597 success；已读取39823协议检查/32856通知回放/92会话、资源7113×2/6回环与发布fixture/12配置检查。实际Mock37183082271仍运行，不能把构建当四组运行证据。

不证明扫描器所有异常、VM Error、同步重入连接、真实GATT或硬件关闭行为。
