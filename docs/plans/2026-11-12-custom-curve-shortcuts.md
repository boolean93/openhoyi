# 自定义曲线本地快捷入口

- 范围：已保存且可执行的原生自定义曲线可绑定首页五槽；引用稳定 ID，编辑更新，缺失/不可执行明确提示不回退。
- 来源：develop 00330680；分支 feature/custom-curve-shortcuts；目标 develop。
- 实现：共享 PresetSlots 解析，工厂曲线沿用协议槽 1..5，自定义点击先选中曲线再进入既有槽 7。分配与导航均不发送设备指令。
- 安全：不扩展任何协议/固件门禁；保留保存参数身份重检、预热确认、秤与目标液重保护、停止/历史实际槽位；不是复制到咖啡机机内曲线。
- 验证：本地代表性 JVM 回归、Mock 构建及独立 Astra 审查；不运行真实设备或本地 AVD。结果完成后补记。
- 推送与合并：尚未授权 PR/推送，不执行。未来按 docs/git-workflow.md。
- 发布：不发布、不安装；签名/数据升级未实测。
- 回退：回退代码，保留原快捷引用及曲线数据，不清未知记录。

## 本轮验证结果

- `:mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest` 通过；Mock 单测 561 项，1 个既有跳过、0 失败。
- 最终 `:mobile:testMockUnitTest :device-session:test` 通过；会话自检 103 项通过，既有启停/历史等 JUnit 回归通过。
- localization/validate_catalog.py 与 git diff --check 通过。
- Astra 独立只读复核未发现阻断问题。
- 已在空闲平板安装同源码、同签名、同 versionCode 的 Debug/Mock 双包，保留数据。Mock 实窗检查通过：真实菜单绑定槽位 5、首页名称与预览、进入 slot 7、Activity 重建、编辑后更新、删除后不可用提示且不跳转。
- 检查仅在 Mock 包运行，校验无 BLE hub、无萃取和历史新增；临时曲线只注入内存，结束恢复原引用和选中项，曲线文件未修改。设备实际启停仍未验，不发送真实咖啡机命令。
- 设备记录：`CUSTOM_SHORTCUT_WINDOW_PASSED assignment=true navigation=true recreate=true edited=true missing=true noBle=true noShot=true`。安装前后备份核对：用户曲线/历史/设置保留；有运行日志轮转、安装标志及空 presets.xml 等正常运行差异。
