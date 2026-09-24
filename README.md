# OpenHOYI Native

纯 Kotlin 协议与业务状态机 + Android BLE 库。没有 UniApp、JS、WebView 依赖。仓库提供两套独立 Android 包：`OpenHOYI Lab`（`io.openhoyi.lab`）用于诊断与采集；`OpenHOYI Alpha`（`io.openhoyi.mobile`）是日常使用版的第一段原生功能。Lab 已实测咖啡机和 BOOKOO 双设备连接、后台及短时锁屏收数。Alpha 已具备首页连接/实时状态、曲线与五槽位启动报文校验、常用机器设置、运行模式和拨杆模式写入与回读状态、曲线温度预热、一次性立即睡眠、实时/历史曲线，以及需要显式确认的萃取与手动停止页面；Alpha 的实机启动和控制尚未验收。

## 模块

| 模块 | 边界 |
|---|---|
| `app` | 原生 Activity + Binder + connectedDevice 前台服务，实时数据显示、权限请求、日志导出 |
| `mobile` | 独立 Alpha 包；原生首页、设备连接、实时读数、曲线库、萃取页与本地曲线选择；复用同一套协议/会话/BLE 库 |
| `protocol-core` | HOYI / BOOKOO 编解码、整数单位、不可变字节、格式校验、未支持命令清单。无 Android 依赖 |
| `device-session` | 串行 GATT 队列、独立连接代次、初始化就绪、重连策略、去皮及停止策略、真实时序回放。无 Android 依赖 |
| `bluetooth-android` | Android GATT 回调桥接、订阅、扫描、权限检查、主线程调度；`NativeDeviceHub` 连接上述模块 |
| `trace-core` | 两款App共用的有界异步JSONL日志与ZIP导出；无Android依赖 |

`NativeDeviceHub` 应由应用或前台服务持有，不能随页面销毁。`app` 模块实现权限请求、前台服务生命周期、成功连接的秤地址持久化与 UI；库只检查权限，不弹出页面。Android 最低版本26（Android8），Java17；使用 `java.time` 因而不声称支持旧版App的API21。

设备会话只对已采集的 HOYI 固件1.1.3开放控制；其他固件只读/不支持。启动控制限定三条已采集曲线或100条经旧版有秤/无秤报文双模式逐字节校验的工厂曲线。编解码器可以处理其他合法字段，但业务发送入口不会因此自动开放。BOOKOO仅接受已采集的ASCII正负号帧，其他型号和符号编码明确不支持。

## 构建和验证

Java17、Android SDK35、Gradle wrapper8.11.1、Kotlin2.0.21、AGP8.10.0。
设置 `ANDROID_HOME`，或在不提交的 `local.properties` 配置 `sdk.dir`。

```sh
./gradlew :protocol-core:check :device-session:check :bluetooth-android:assembleDebug :bluetooth-android:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:lintDebug
```

两个纯 Kotlin 模块的 `check` 包含确定性 JVM `verify` 任务；断言失败即构建失败。逐帧断言不是独立案例；App 的日志/数据展示使用 JUnit 测试。

Google Maven 无法访问时可显式使用 `-PgoogleMirror=aliyun`。本机全局Gradle代理指向未启动的127.0.0.1:7890，本次仅命令行加 `-Dhttp.proxyHost= -Dhttps.proxyHost=` 绕过，没有修改全局配置。Google依赖首次通过可选阿里云镜像获取；默认仍使用官方仓库。

APK：Lab `app/build/outputs/apk/debug/app-debug.apk`；Alpha `mobile/build/outputs/apk/debug/mobile-debug.apk`；Lab UI 冒烟测试包 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。

## 使用原生 Alpha

```sh
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb shell am start -n io.openhoyi.mobile/.HomeActivity
```

Alpha 与 Lab、旧版 HOYI 分包安装。首页可扫描、手动连接咖啡机和秤、查看实时温度/压力/重量及机器告警、独立去皮，并通过系统选择器导出本包的传输与操作日志；成功连接过的秤地址只保存在 Alpha 自身，首页前台的10分钟内可独立于咖啡机自动重连。曲线库可按分类及名称查找三条已采集曲线与100条旧版工厂曲线；后者的200种临时槽位帧和1000种五槽位帧均与旧版编码函数逐字节对照。首页五个快捷槽位可指定工厂曲线，启动前仍须确认。首页可查看及切换手动、自动压力、自动流量三种拨杆模式；机器设置页显示设定值与睡眠计划，并可修改两路温度、冲泡温差补偿、加热、照明、自动待机时间、待机温度、供水来源、运行模式、每周睡眠计划总开关及每日睡眠时间。累计杯数也可经两次确认后重置，并须等待机器回报归零。上述设置写入后等待机器对应回报才确认。萃取页用原生Canvas显示实时压力、机器水流、萃取温度、秤流速和重量；历史详情可离线查看本包采集的曲线，历史页可导出包含采样点的ZIP。首页支持持久化的深浅色切换。未知状态明确保留为未知。选择曲线只保存其 ID；曲线通过报文校验并在萃取页明确确认后才可能发送控制。产品层要求已验证固件、咖啡机新鲜待机遥测；重量模式还要求秤的新鲜数据。连接同一设备前应关闭其他 App 对该设备的连接。Alpha 的真实萃取和设置写入尚未验收，不应把编译通过视为硬件等效。

库产物：`bluetooth-android/build/outputs/aar/bluetooth-android-debug.aar`。AAR不是自包含APK，使用时需同时包含协议和会话模块；Gradle项目依赖通过 `api` 传递。

## 使用诊断 App

安装新包，不覆盖旧App：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.openhoyi.lab/.LabActivity
```

点击扫描并授权，选择 HOYI 或 BOOKOO。咖啡机需要六位设备密码，仅内存使用；连接会认证和同步时间。秤连接会执行四条初始化写入。只显示已接收的数据；未收到显示未知，超过1.5秒标陈旧，断线保留历史标记。独立包不读取旧App数据，也不保存机器密码。

连接属于前台服务，离开/旋转页面不主动断链；通知或页面可停止服务。进程被杀后不会自动重启或恢复机器连接。成功连接过的秤地址仅保存在本App，进入前台10分钟内、咖啡机就绪后尝试重连；手动断秤取消该窗口。

通过系统文件选择器导出 ZIP。日志按 GATT 请求/接受/完成/通知区分，密码指令整帧脱敏；最多保留8个4MiB文件。后台有界队列满时丢记录并统计，不阻塞蓝牙；metadata包含当前日志sessionId、丢失/错误/淘汰统计。每条服务记录ownerId区分Service重建，generation/token关联操作。进程突然终止可能丢失尚未落盘记录，不能视为完整黑匣子。

诊断 App 的范围与手工验证步骤见 [Lab说明](docs/native-lab.md)。

## 证据与限制

- 32,856条脱敏通知输入（含listener重复）；每条解码为已知有效帧。
- 三条曲线启动编码与原始20字节完全一致。
- 三次完整窗口回放覆盖手动停止、机器按流量结束、App重量停止。
- 目标重量停止使用去皮后新鲜样本；写入去皮成功不等于重量已归零。
- 新控制策略不等同于旧App：例如当前回放重量停止为17.786秒，旧日志为17.887秒，相差101ms；差异尚未定位到唯一原因，输入调度及基线策略需实机对照，未做硬件等效认证。
- BOOKOO旧代码二进制符号判断与实采ASCII `+/-` 不符；新解码保留符号，已测试负值原始帧，但尚未用已知负重量实物确认。
- 认证密码是六个数字字节（0..9），不是六个ASCII码；日志字符串默认脱敏。
- 没有向咖啡机发送本版本测试控制命令。未修改或安装旧App。

最新实机结果见 [验证报告](docs/validation-2026-09-23.md)。完整范围、缺口和发布门禁见 [覆盖矩阵](docs/coverage.md) 与 [功能状态](docs/native-feature-status.md)；接续开发见 [HANDOFF](HANDOFF.md)。
