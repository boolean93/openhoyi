# OpenHOYI Alpha 第一段功能

包名 `io.openhoyi.mobile`；独立于 `io.openhoyi.lab` 诊断包和旧 HOYI。没有 UniApp、JS 或 WebView。`HomeActivity` 只负责权限、扫描选择和展示，`MobileService` 持有 `NativeDeviceHub` 及双 BLE 连接，曲线页只读 `CurveCatalog`。设备会话和编解码复用 `device-session` / `protocol-core`；两款应用通过独立的私有目录使用共享 `trace-core` 实现记录，不混用日志文件。

## 已实现

- 原生首页：启动/停止前台设备服务、扫描 HOYI/BOOKOO、输入六位咖啡机密码并连接、分别断开设备。
- 实时状态：咖啡机温度/压力、固件号及秤重量；超过1.5秒的数据不标为实时。
- 曲线库：三条来自采集记录的完整参数，进入详情后可设为当前曲线。名称是临时编号，不声称对应旧版曲线名称；选择只保存 ID。
- 记忆秤：成功 Ready 后在本包保存秤地址；前台10分钟窗口由 `NativeDeviceHub` 负责自动连接。
- 操作与传输记录：共享 `TraceStore` 有界异步写入，认证帧在传输边界脱敏；首页可通过系统文件选择器导出ZIP。Alpha与Lab各有自己的日志session。
- 萃取页：选定已采集曲线后展示连接、重量、温度、压力和萃取状态。明确确认后才调用共享 `ExtractionController`；手动停止为单击操作。服务层重复校验曲线、固件就绪、咖啡机新鲜待机帧及秤数据时效，并记录尝试/结果/传输。萃取结果未确认时阻止主动断链或停止服务；若断线导致结果未知，允许重连咖啡机后显式重试停止，不自动重放启动。

## 仍未开放

产品页没有独立去皮、设备设置、校准或 OTA 按钮。底层控制仍受已知固件和三条完整启动帧限制。萃取页已经接通启动/停止代码，但尚未在Alpha上完成真实启动、去皮确认、按重量停止和异常恢复验证；不能视为与旧App硬件等效。App进程被系统杀死时不能保证停液。

## 验证

`./gradlew :protocol-core:check :device-session:check :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:lintDebug -PgoogleMirror=aliyun -Dhttp.proxyHost= -Dhttps.proxyHost=` 已通过；新增萃取页后再次通过 `mobile:testDebugUnitTest :mobile:assembleDebug :mobile:lintDebug`。曲线单测核对三条20字节启动帧、目标重量及唯一ID；安全门禁单测覆盖未知/不支持固件、非待机或过期咖啡机帧、秤缺失/过期、未结束上一杯。Lab日志测试在抽取共享模块后仍通过。2026-09-23两次实机安装均被平板返回 `INSTALL_FAILED_USER_RESTRICTED`，因此不能声称 Alpha 的 UI 或设备连接已实机通过。Lab 的实机证据不自动等同于 Alpha 的实机验收。
