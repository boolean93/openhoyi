# Native Lab Implementation Plan

**Goal:** 独立原生诊断APK，连接/数据/日志可检查；不暴露萃取或设置控制。
**Architecture:** Android Activity通过本地Binder观察Service持有的NativeDeviceHub；Service处理连接、前台服务和持久日志；UI不解析协议。应用io.openhoyi.lab与旧App共存。
**Tech Stack:** Kotlin、Android原生View、Service、SAF导出；复用三个库，SDK35/min26。

- [x] 独立审查原通信核心的生命周期/回调风险；确认问题后先补回归测试再修复。
- [x] app模块：foreground connectedDevice权限、Service、Activity绑定与恢复、权限/蓝牙状态、扫描设备选择、密码输入不保存不记日志。
- [x] 实时状态模型区分未知/有效/陈旧，界面原始温度/压力/重量单位明确；断线不显示成实时。
- [x] 结构化传输日志：真正的GATT提交/回调/通知，角色、代次、token、时间；认证字节脱敏；有界异步写入和显式丢失统计。通过ACTION_CREATE_DOCUMENT导出，不自动分享上传。
- [x] 测试：日志脱敏/顺序/导出/写错、状态投影；JVM全量回归、app assembleDebug/lint、仪器测试APK构建；设备在线才安装验证，无设备明确标注。
- [x] 更新README/HANDOFF/覆盖矩阵，保存本地提交。

默认不自动连接咖啡机；点击连接需要六位密码（只在内存中使用）。秤初始化写入遵循现有协议；没有萃取、设置、校准、OTA按钮。不要从旧App读取密码或强停旧App自动连接硬件。

执行说明：App JUnit 9项、会话32场景通过；APK和离线UI测试APK已构建，ADB无设备所以未执行UI/硬件测试。日志最终归属Application进程单实例，Service重启复用；SAF结果直接由Application导出以消除绑定依赖。独立范围/生命周期复核完成，额外代码质量审查因额度中断，主任务补查；不能将中断审查记为通过。
