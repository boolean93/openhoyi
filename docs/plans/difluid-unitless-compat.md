# DiFluid 无单位帧受限兼容

- 范围：在现有 `fix/difluid-endpoint-compat`（develop 基线 69e31001，本地端点修复 85f343e9）扩展严格 12 字节传感器载荷，未来 PR 目标 develop；遵守 [Git工作流](../git-workflow.md)。本轮仅本地提交，不推送或创建 PR。
- 体验：当前连接收到有效 func01/cmd04/length1/unit0 协议单位响应后，12 字节载荷才可输出克读数。该响应可能与设置命令回显相同，不证明硬件效果。写成功本身不确认单位。
- 实现：新 raw/unitless 类型，固定 signed32 字段和边界、校验和、精确长度及类型。13 字节帧自带单位独立解码，不授权后续无单位帧。非克/未知单位及无效帧撤销确认；未确认无单位帧清掉同批先前候选并撤销 READY；重连创建新解码器。
- 证据限制：官方 1.2.4/1.2.6 使用固定字段但宽松识别会把 ACK/status/坏校验帧误当重量。本实现仅兼容可安全识别子集。12 字节样本是合成数据，无旧固件实抓，不能保证所有官方数据包、型号或固件。
- 初始化维持 500/1000/1200ms，不自动去皮，不赋予机器控制资格。无实秤验收、不安装、不操作设备、不发版，不更改签名或升级边界。
- 验证：protocol-core 与 device-session JVM 检查及相关 Android 编译；结果完成后补记。
- 回退：撤销本次修改，保留已存数据与未知记录，不卸载清数据。

实际验证：JDK17/Android SDK 下 `./gradlew --offline :protocol-core:test :device-session:test :app:compileDebugKotlin --console=plain` 通过。protocol-core 自定义 verify 检查通过（非 JUnit XML 计数）；device-session XML 共 728 项，失败/错误/跳过均 0，另含 103 项会话自定义检查。`git diff --check` 通过。首次验证发现原有“所有 12 字节无效”断言，已按新 raw 类型契约更新后复测通过。未做硬件验证。

最终补充：DeviceSession 状态机单测覆盖 READY 后坏帧/非克撤销到 SYNCHRONIZING、单位响应加无单位帧恢复，以及断开重连后未确认无单位帧不能 READY。fresh decoder 同批 full13+未确认12 不输出有效观察并发出 ReadingInvalidated。修正类型注释后再次运行 `:protocol-core:test :device-session:test` 通过（33秒），device-session 729 项全通过，失败/错误/跳过均 0；app 编译沿用此前通过证据（此后仅测试和注释变更）。

独立审查：Astra 最终源码及新增状态机用例审查通过，无阻塞项；本地提交不代表硬件验收或发布批准。
