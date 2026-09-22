# Native BLE Implementation Plan

**Goal:** 可构建的纯 Kotlin 协议与设备会话模块、Android BLE 适配器、真实采集回放和故障验证。
**Architecture:** protocol-core 无 Android 依赖；device-session 通过抽象 transport/clock 调度；bluetooth-android 实现扫描、GATT 与订阅。页面不参与控制。未验证命令显式 Unsupported。
**Tech Stack:** Kotlin 2.0.21, Java 17, Gradle 8.11.1, Android SDK 35。

采用测试先行、执行后核验、独立规格及质量审查。当前仓库只有 LICENSE，无既有测试。

- [x] 协议：ProtocolChecks 先红；HOYI 三条20字节启动向量、停止、0x83/40/80、BOOKOO初始化/去皮/负重量/XOR。固定整数单位，字节防御复制，非法长度和参数明确拒绝。真实帧提取到匿名 fixture 并对照，所有未知写入关闭。
- [x] 会话：SessionChecks 先红；独立设备身份/generation、串行操作、期限、断线清队列、旧回调拒绝、不重试有副作用写入、初始化后才Ready、重量陈旧与停止去重。Fake transport+手动时钟重放异常。
- [x] Android：权限前置检查、共享扫描协调、独立GATT、服务属性检查、CCCD写成功后才订阅完成、异步回调绑定generation、close清理。编译及lint，未实机验证项独立列出。
- [x] 集成：离线采集回放三次萃取；编解码和会话互通；重复命令/缺失帧/时钟/越界验证。Android AAR构建，不自动向机器发送测试控制命令。
- [x] 本地规格核对与故障审查、修复后回归；README/HANDOFF/覆盖矩阵/限制清单。
- [ ] 独立审查：子任务额度限制中断，发布前补齐，不将本地复核冒充独立审查。
- [x] 完整Gradle验证后保存本地分支，不自动推送。

验证命令：`./gradlew :protocol-core:check :device-session:check :bluetooth-android:assembleDebug :bluetooth-android:lintDebug`。确定性 JVM checks 失败通过非零进程状态阻止 check；不依赖测试框架下载。

审查状态：独立子任务因服务额度限制中断，不能声称完成独立审查。主任务完成规格核对与两轮故障回归；独立人工/代理审查及真机控制测试仍为发布门禁。
