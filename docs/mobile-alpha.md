# OpenHOYI Alpha 第一段功能

包名 `io.openhoyi.mobile`；独立于 `io.openhoyi.lab` 诊断包和旧 HOYI。没有 UniApp、JS 或 WebView。`HomeActivity` 只负责权限、扫描选择和展示，`MobileService` 持有 `NativeDeviceHub` 及双 BLE 连接，曲线页只读 `CurveCatalog`。设备会话和编解码复用 `device-session` / `protocol-core`。

## 已实现

- 原生首页：启动/停止前台设备服务、扫描 HOYI/BOOKOO、输入六位咖啡机密码并连接、分别断开设备。
- 实时状态：咖啡机温度/压力、固件号及秤重量；超过1.5秒的数据不标为实时。
- 曲线库：三条来自采集记录的完整参数，进入详情后可设为当前曲线。名称是临时编号，不声称对应旧版曲线名称；选择只保存 ID。
- 记忆秤：成功 Ready 后在本包保存秤地址；前台10分钟窗口由 `NativeDeviceHub` 负责自动连接。

## 仍未开放

产品页没有萃取/停止/去皮、设备设置、校准或 OTA 按钮。底层控制仍受已知固件和三条完整启动帧限制。当前应用尚不是可用于完整制作咖啡的替代品；接入控制前须先把操作、回调与设备状态纳入可导出的诊断记录，并验证真实停止/去皮时序。

## 验证

`./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug :mobile:lintDebug -PgoogleMirror=aliyun -Dhttp.proxyHost= -Dhttps.proxyHost=` 已通过。曲线单测核对三条20字节启动帧、目标重量及唯一ID。2026-09-23两次实机安装均被平板返回 `INSTALL_FAILED_USER_RESTRICTED`，因此不能声称 Alpha 的 UI 或设备连接已实机通过。Lab 的实机证据不自动等同于 Alpha 的实机验收。
