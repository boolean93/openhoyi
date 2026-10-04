# 正常退出与未确认门禁实测

目标：实际MobileService.shutdown在独立Hub关闭观察者Exception后，仍依序停止前台/自身；无待确认记录时running=false、hub与重连任务释放，有shot/write未确认时保持运行、连接与记录，不发送命令。

方法：独立未注册Service/Hub、fake Connect、UUID偏好、主线程构造和收尾。仅该Service的mActivityManager注入本地java Proxy；只允许setServiceForeground与stopServiceToken原参数，所有其它调用拒绝，不接入真实ActivityManager/Binder注册。代码依据Android14 Service.java的631/912/978/1042行（android-14.0.0_r1），stopForeground/stopSelf通过该字段而非Context dispatch；未启动前台时traceTitle为null。检查独立token/component和原调用顺序。四组无记录/shot/write/两者待确认，fixture与App真实prefs不变。主线程异常回传，finally移除所有独立Handler和设备/journal/偏好。

- [x] 本地测试APK/Lint101任务通过；独立静态复审无阻塞。ART上的隐藏字段/Proxy与四项断言仍待实际运行。
- [ ] 新源码实际Mock运行和原回归标记。

仅验证本地代理调用与三种持久未确认门禁，不代表真实已注册Service或平台Binder成功，也不覆盖其它退出门禁/硬件。没有修改生产源码。先前onDestroy测试不替代本项。

源码依据：[AOSP Android14 Service.java](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/java/android/app/Service.java)。本地取证/private/tmp/hoyi-service-android34-source.java，仅用于确定fixture代理入口。
