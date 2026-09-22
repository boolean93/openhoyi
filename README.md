# OpenHOYI 原生通信核心

纯 Kotlin 协议与业务状态机 + Android BLE 库。没有 UniApp、JS、WebView 依赖。这是架构升级第一阶段，不是可安装的完整 App，也尚未验证新代码的实机控制。

## 模块

| 模块 | 边界 |
|---|---|
| `protocol-core` | HOYI / BOOKOO 编解码、整数单位、不可变字节、格式校验、未支持命令清单。无 Android 依赖 |
| `device-session` | 串行 GATT 队列、独立连接代次、初始化就绪、重连策略、去皮及停止策略、真实时序回放。无 Android 依赖 |
| `bluetooth-android` | Android GATT 回调桥接、订阅、扫描、权限检查、主线程调度；`NativeDeviceHub` 连接上述模块 |

`NativeDeviceHub` 应由应用或前台服务持有，不能随页面销毁。宿主负责权限请求、前台服务生命周期、设备地址持久化与 UI；本库只检查权限，不弹出页面。Android 最低版本26（Android8），Java17；使用 `java.time` 因而不声称支持旧版App的API21。

设备会话只对已采集的 HOYI 固件1.1.3开放控制；其他固件只读/不支持。第一版启动控制严格限定三条已采集的完整曲线包。编解码器可以处理其他合法字段，但业务发送入口不会因此自动开放。BOOKOO仅接受已采集的ASCII正负号帧，其他型号和符号编码明确不支持。

## 构建和验证

Java17、Android SDK35、Gradle wrapper8.11.1、Kotlin2.0.21、AGP8.10.0。
设置 `ANDROID_HOME`，或在不提交的 `local.properties` 配置 `sdk.dir`。

```sh
./gradlew :protocol-core:check :device-session:check :bluetooth-android:assembleDebug :bluetooth-android:lintDebug
```

`check` 包含确定性 JVM `verify` 任务；断言失败即构建失败。不是JUnit测试报告，不能把逐帧断言数量称为独立测试案例数。

Google Maven 无法访问时可显式使用 `-PgoogleMirror=aliyun`。本机全局Gradle代理指向未启动的127.0.0.1:7890，本次仅命令行加 `-Dhttp.proxyHost= -Dhttps.proxyHost=` 绕过，没有修改全局配置。Google依赖首次通过可选阿里云镜像获取；默认仍使用官方仓库。

产物：`bluetooth-android/build/outputs/aar/bluetooth-android-debug.aar`。AAR不是自包含APK，使用时需同时包含协议和会话模块；Gradle项目依赖通过 `api` 传递。

## 证据与限制

- 32,856条脱敏通知输入（含listener重复）；每条解码为已知有效帧。
- 三条曲线启动编码与原始20字节完全一致。
- 三次完整窗口回放覆盖手动停止、机器按流量结束、App重量停止。
- 目标重量停止使用去皮后新鲜样本；写入去皮成功不等于重量已归零。
- 新控制策略不等同于旧App：例如当前回放重量停止为17.786秒，旧日志为17.887秒，相差101ms；差异尚未定位到唯一原因，输入调度及基线策略需实机对照，未做硬件等效认证。
- BOOKOO旧代码二进制符号判断与实采ASCII `+/-` 不符；新解码保留符号，已测试负值原始帧，但尚未用已知负重量实物确认。
- 认证密码是六个数字字节（0..9），不是六个ASCII码；日志字符串默认脱敏。
- 没有向咖啡机发送本版本测试控制命令。未修改或安装旧App。

完整范围、缺口和发布门禁见 [覆盖矩阵](docs/coverage.md)；接续开发见 [HANDOFF](HANDOFF.md)。
