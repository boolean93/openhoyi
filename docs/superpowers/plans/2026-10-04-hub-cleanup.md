# 双设备所有者清理

目标：NativeDeviceHub.close在一个设备终态观察者抛Exception时，仍关闭另一个设备并移除全部所属ticker；不扫描或补发写入，原首个异常继续传播，多异常保留suppressed。

根因假设：当前close顺序scanner→coffee→scale，coffee.close的session.disconnect异常会跳过scale.close。先用真实Hub/AndroidDevice/DeviceSession配独立fake GattQueue传输在Android34 Mock复现，再修改生产源码。

测试边界：独立Context保留自身applicationContext且禁止system service、权限和pref访问；仅单个runOnMainSync任务内构造独立Hub，反射替换其未active队列驱动，再fake Connect，不调用真实扫描或连接、不注册Service、不触碰现有Mock owner。正常、coffee失败、scale失败、两项失败四种fixture，finally独立清理防止RED泄漏。检查双方close一次/无新operation/终态、三个ticker删除及异常传播。

- [x] 测试APK/Lint本地101任务通过；独立复审两处异常回传/反射收尾问题修正后无阻塞。
- [ ] 实际Mock RED；编译不作为运行证据。
- [ ] 最小清理修复、本地构建及实际Mock GREEN。
- [ ] 独立复审和新源码云端证据。

不证明扫描器所有异常、VM Error、同步重入连接、真实GATT或硬件关闭行为。
