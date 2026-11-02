# 曲线库动作裁切失败取证

- 证据：5abe9c2 / lifecycle CI 39115860532，landscape zh-Hans dark=false，CurveActivity detail=longFactory action is clipped。既有日志没有尺寸及截图，不能据此断言是产品或取证时序根因。
- 来源/分支：沿用 develop 派生 feature/task-first-ui，基线36f3cbc，PR #4 → develop。
- 范围：仅 LanguageDetailChecks 和 lifecycle 脚本。保留原断言，失败时在主线程检查返回后捕获真实窗口截图，记录配置、主按钮完整/可见尺寸和父布局尺寸；脚本出错收集截图后再恢复配置。取证异常不得覆盖原失败。
- 验证：Mock/instrumentation 编译、bash -n、diff检查。现场文件需后续CI再次触发失败才可验证，不宣称根因已解决。
- 推送：后续与库存修正批量普通推送；不合并、不直推主干。
- 协议/设备：仅CI隔离Mock取证，不改变门禁、选择、参数和BLE；不操作实机。
- 发布：不发版、不安装，不改变升级/签名。
- 回退：revert这次测试与脚本改动，不清数据。
