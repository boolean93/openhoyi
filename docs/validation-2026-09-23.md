# 原生诊断版实机验证 · 2026-09-22～23

设备：小米平板 M2105K81AC，Android 13 / API33；Wi-Fi ADB。安装独立包io.openhoyi.lab，未覆盖或修改旧App。本轮没有发送萃取、停止萃取、加热、温度设置或校准命令。

## 已确认

| 项目 | 实际证据 |
|---|---|
| 安装、冷启动、未知数据显示 | 正式诊断APK安装成功；Android instrumentation断言通过 |
| 咖啡机扫描、订阅、认证 | 用户更正密码后收到普通83设置帧，识别固件1.1.3并Ready |
| 实时数据 | 成功解析普通设置、两段睡眠配置、19字节空闲遥测；界面显示温度、压力 |
| 认证失败门禁 | 首次未确认认证时仍收到空闲遥测；10秒未收到设置帧，主动关闭且未Ready；新增纯Kotlin回归 |
| 脱敏 | 本地捕获的3次认证写请求及相关回调均为整帧REDACTED；未保存用户密码 |
| 断线显示 | status=8后显示连接失败/历史数据，未继续标实时 |
| 页面重建 | instrumentation调用Activity.recreate，确认Service为同一实例且running |
| 前后台生命周期 | instrumentation切HOME/返回，确认同一Service保持；显式shutdown后停止 |
| 停止服务后日志导出 | 在真实Android运行Application日志队列，导出ZIP至内存流，检查metadata和测试标记存在 |
| 服务持久性 | 隔夜约11小时后仍可读取同一前台Service；这不代表BLE持续连接 |

自动测试输出：

```text
PASS native UI startup / unknown readings
PASS Activity recreation preserves Service instance
PASS background/return preserves Service; explicit shutdown stops it
PASS Android ZIP export after Service stop (in-process stream, not SAF picker)
```

测试在BLE权限已授予、蓝牙开启的平板上运行，调用扫描但不选择/连接硬件。没有权限或蓝牙未开启会显式SKIP生命周期测试，不应当作通过。测试会终止Lab原进程，所以不应在正在进行真实设备操作时运行。

## 未解决 / 未验证

- 两次已认证连接分别观察到约80秒和51秒通知后收到GATT status=8，根因未定位。第二次断开是21:25:22，页面进入后台是21:25:46，故不能归因于切后台。不能声称已实现长期稳定连接，也不能根据该状态码认定唯一原因。
- 电子秤未扫描到，BOOKOO初始化/双连接/重量/自动重连仍待实测。
- 本轮自动化验证的是Android流导出，**不是SAF系统文件选择器全流程**；SAF取消、存储提供方异常、选择期间停服务的端到端验证待做。
- 未验证真正锁屏后的BLE连接；Activity.recreate不是物理旋转的全部系统行为。
- 未测试真实萃取、去皮或停止控制，更未完成新旧App物理等效验收。

## 本轮修订

LabActivity只在文本变化时setText，避免250ms轮询反复产生布局/无障碍事件。LabService新增ui.visibility时间点以关联生命周期与GATT事件。未改协议包、初始化顺序或硬件控制门禁。

自动测试启动改用Instrumentation的shell前台启动并设置Activity等待超时，适应平板对测试进程后台启动的限制；测试包安装需`adb install -r -t`。旧版测试曾需要手动adb带前台才能结束，本轮新测试自主完成。

## 本地证据

原始日志仅放在git忽略的 `captures/native-validation-2026-09-23/`，不提交个人完整采集数据。提交的安全回归仅使用无身份信息的遥测帧。连接日志只包含脱敏认证，不可据此验证实际输入的密码字节。

复跑：

```sh
./gradlew :protocol-core:check :device-session:check :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <device> shell am instrument -w io.openhoyi.lab.test/io.openhoyi.lab.LabSmokeInstrumentation
```
