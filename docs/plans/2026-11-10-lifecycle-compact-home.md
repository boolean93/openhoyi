# 修复短横屏首页首屏布局

- 需求与范围：CI run 42814857538 在720×360首页检测到锅炉卡片超出viewport；仅为横屏高度不超过420dp的窗口恢复紧凑读数与48dp操作，保留竖屏和大屏圆表、六入口底栏和横屏侧栏。遵守[Git工作流](../git-workflow.md)。
- 来源分支/基线：develop，58bbd96ff12f6f72d47130ac6f7bade4e788eef4。
- 工作分支：fix/lifecycle-portrait-navigation；PR目标develop。
- 改动文件：HomeFragment.kt、现有TaskLayoutPolicy及阈值单测；androidTest新增只读home入口复用原断言，并记录fontScale；不改几何断言或门禁。
- 推送与合并：本轮只交付本地差异，由协调者安排检查、推送与PR；不直接推送主干。
- 验证：读取失败CI日志、截图与帧证据；运行Mock/Debug构建、androidTest编译和9项布局策略单测。用户经协调者授权真机720×360默认/1.3只读idle检查；双76原签名覆盖、业务哈希前后比对，并精确恢复原WM/density/font/rotation。原running/last-curve CI断言完整保留；不运行本地模拟器。
- 协议与设备风险：只改视图组合，不改协议、写入门禁、数据、停止或重试；开关继续复用原确认回调。
- 发布：不发布APK、不分配发布版；为真机验证按用户规则原签名同76双包保数据覆盖，非发布签名认证；不改变包名或升级边界。
- 回退：回退本次布局改动，保留全部用户数据与未知记录。
- 未完成：实际短横屏idle证据见交付记录；运行中停止按钮及完整lifecycle待精确源码CI，大屏与竖屏路径未更改。

## 实际结果

- `:mobile:assembleMock :mobile:assembleDebug :mobile:assembleMockAndroidTest :mobile:testMockUnitTest --tests io.openhoyi.mobile.TaskLayoutPolicyTest -PhoyiVersionCode=76 -PhoyiVersionName=0.2.0-preview.76 --offline` 成功；布局策略9项、零失败。`git diff --check`与分支规则检查通过。
- 真机 `M2105K81AC` 仅跑 `mainNavigationChecks=home`，复用原720×360完整可见与48dp触控断言；默认字体与1.3字体均输出 `MAIN_HOME_720_GEOMETRY_PASS`，记录真实fontScale。MIUI对instrumentation自身启动Activity有限制，使用shell显式启动同一Mock Home辅助有界monitor；未改权限或系统启动策略。
- 两轮发现并修正1.3下上杯辅助标题撑高、真实预设名省略。最终五槽完整显示 `Espresso深烘`、`SOE浅烘`、`HOYI Turbo Shot`、`HOYI Limit Extraction`、`Hyper Ex 超萃手冲`，短横屏真实名称不限行、自然高度，超长自定义名称可以正常滚动，不承诺所有任意内容均挤入首屏。
- 本地证据保存在本任务目录 `evidence/lifecycle-compact/`：`final-default.txt`、`final-font130.txt`、对应 `final-*/idle.txt`、两张 `final-*-foreground.png`、`final-apk.json`、业务hash与恢复配置回读。默认图有泛灰现象，不把原因归于未经证实的因素；1.3前台图清晰。
- 原签名指纹 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`；同76双包覆盖后，Debug的64个及Mock的57个业务偏好/样本文件SHA-256逐项一致。未清数据、未造杯、未运行完整lifecycle脚本、未操作真实机器。
- 结束回读：physical size1600×2560、physical density360，无override；font1.0、accelerometer_rotation0、user_rotation3；恢复Mock Home。未运行本地模拟器。
- 仍待协调者执行最终完整非AVD门禁/远端精确源码CI；本次只读idle实机证据不替代running、last-curve、完整lifecycle或硬件验收。
