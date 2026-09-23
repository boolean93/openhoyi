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
| 双设备实连（后续核对） | 08:42:51咖啡机Ready、08:42:59 BOOKOO Ready。随后约2分钟内分别收到115/1072条通知，无断线回调。BOOKOO四条初始化写入在08:42:57.953、58.552、59.166、59.775开始，顺序及约500ms间隔符合设计 |

自动测试输出：

```text
PASS native UI startup / unknown readings
PASS Activity recreation preserves Service instance
PASS background/return preserves Service; explicit shutdown stops it
PASS Android ZIP export after Service stop (in-process stream, not SAF picker)
```

测试在BLE权限已授予、蓝牙开启的平板上运行，调用扫描但不选择/连接硬件。没有权限或蓝牙未开启会显式SKIP生命周期测试，不应当作通过。测试会终止Lab原进程。08:44对正在双设备连接的实例运行测试，确实中断了用户的连接；这是本轮操作失误，不属于设备自然断线。后续必须先运行保护脚本检查Service未存在，不能直接运行`am instrument`。

## 补充核对：旧版连接竞争（08:54）

平板系统蓝牙GATT Client Map显示旧版`com.hoyi.personal`持有一条连接，地址与咖啡机一致；连接建立记录是08:44:47，紧随仪器测试重启原生进程。原生Lab随后扫描未发现HOYI，Android扫描统计本身仍收到大量周边广播。这解释了当时为何无法立即恢复咖啡机连接，不能反推21:15的两次`status=8`一定由旧版造成。

旧版通过Android`connectGatt(context, false, callback, TRANSPORT_LE)`建立连接；原生版使用同样的`autoConnect=false`和LE传输。旧版采样日志中曾有约9002秒连续咖啡机通知，说明硬件与平板曾长期保持连接，但不同日期环境并非受控对照。

## 独占连接对照（08:55～09:03）

用户确认咖啡机没有执行操作并授权暂时停止旧版后，停止`com.hoyi.personal`。原生Lab再次扫描发现HOYI和BOOKOO两台设备；停止前同一Lab扫描得到0台。此结果强烈支持旧版占用是当时扫描不到设备的原因，但不能据此证明前一晚`status=8`的根因。

08:56:54咖啡机认证并Ready。Lab凭已记住的秤地址，于08:56:54自动开始连接BOOKOO，08:56:57 Ready，证明“咖啡机就绪后连接记忆秤”路径在实机可用。08:57:10又手动点击扫描列表中的秤，导致已连接的秤关闭并重新连接，08:57:13再次Ready；这是冗余交互，后续UI应防止对已连接设备重复发起连接。

到09:03:17，咖啡机从Ready起持续约6分23秒，秤最后一次Ready起约6分04秒；本会话分别记录385条和3792条通知，没有新的失败状态或非预期关闭。08:59:24退到桌面，此后约1分钟分别仍收到61条和606条通知。约09:00:30锁屏，随后约1分钟分别仍收到60条和596条通知。唯一`wire.close`位于08:57:10，与手动重连同秒。屏幕唤醒后的ADB截图仍返回黑帧，因此本次锁屏后UI恢复没有视觉验收；持续通知和状态日志只能证明服务及BLE链路继续运行。旧版目前保持停止，Lab仍运行并保持连接。

## 未解决 / 未验证

- 两次已认证连接分别观察到约80秒和51秒通知后收到GATT status=8，根因未定位。第二次断开是21:25:22，页面进入后台是21:25:46，故不能归因于切后台。不能声称已实现长期稳定连接，也不能根据该状态码认定唯一原因。
- BOOKOO扫描、四条初始化写入、Ready和双设备并行遥测已实测；记忆秤在咖啡机Ready后自动连接已实测。已知负重量的实物校准及异常断线后的重试仍待实测。
- 本轮自动化验证的是Android流导出，**不是SAF系统文件选择器全流程**；SAF取消、存储提供方异常、选择期间停服务的端到端验证待做。
- 真正锁屏后约1分钟BLE通知仍在继续；更长时间的锁屏稳定性、唤醒后的UI视觉恢复及物理旋转仍待验证。
- 未测试真实萃取、去皮或停止控制，更未完成新旧App物理等效验收。

08:50尝试恢复连接时5秒扫描未发现候选设备；08:55暂停旧版后再次扫描发现两台。两次扫描的环境不同，不能将08:50结果解释为扫描实现普遍失效。

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
scripts/run_lab_smoke.sh <device>
```
