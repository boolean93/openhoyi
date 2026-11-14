# 首页机器模式控件一致化

- 范围：首页模式与编辑器使用相同紧凑文字分段控件，压力蓝、流量绿；断连保留选项文字并禁用。独立排查已连接禁用，不取消真实设备安全保护。
- 来源：origin/develop 893d7f78；共享编辑器依赖 feature/curve-editor-b a5008228，待其合入再对照 develop 差异。
- 工作分支：fix/home-mode-segments；PR 目标 develop。
- 文件：MachineModeControls、CurveEditorSegments、相关导航验收。
- 验证：编译 Mock、控件禁用不回调、读取后更新、窄窗/横窗与深浅色；设备由根线程协调，不操作真实机器。
- 协议风险：不改线协议和写入门禁；保留读回确认后更新选择。
- 推送：scripts/create-pr、本地相关检查、必需检查通过后按授权合入。不发版不自动安装正式包。
- 回退：回退此控件改动，无数据迁移。

## 本轮证据

- `:mobile:compileMockKotlin` 与 `:mobile:compileMockAndroidTestKotlin --offline` 均编译通过；仅编译不代表 instrumentation 已执行。
- 独立 Astra 对三个 Kotlin 差异只读复核通过：禁用不可回调、读回确认、48dp 触控目标、字号/深浅色及自适应逻辑无阻断发现。
- 实屏验收待协调平板窗口，不安装/操作正式包。主线程正处理编辑器 Mock 验收，禁止抢占。
- 已连接禁用：已确认设置证据180秒过期后现有代码无常规刷新恢复，这是代码层缺口；尚不能证明本次用户现场唯一原因。共用受控刷新由独立 settings-recovery 工作实现，本分支不放宽写入门禁。

## 真机 Mock 验收（2026-11-14）

- 集成编辑器完整 R8 `f90e0fa6`，复编 Mock 与 AndroidTest 成功。平板原 Mock114 升级为同签名 Mock115；正式 `0.2.0-dev.120` 未修改，数据未清除。
- 设备空闲超过10分钟、正式曲线库、咖啡机已休眠后执行；只操作 `io.openhoyi.mobile.mock`。
- `am instrument -w -e mainNavigationChecks home ...BrewAudioInstrumentation` 返回 `MAIN_NAVIGATION_CHECKS_COMPLETED`：禁用不提交/启用恢复/程序选择无回调检查通过。720×360dp 专属几何检查在实际1138×711dp设备明确skip，不宣称其通过。
- 实际点击首页变流：蓝色变压/恒压变为绿色变流/快速，第二组选项完整显示慢速/快速，无截断；操作后恢复原变压/恒压。保留实际截图 home-pressure.png/home-flow.png 供主线程交付。
- 本轮实屏仅正常横屏深色，不新增无关尺寸矩阵；其他主题复用现有编辑器调色板且已静态审查。结束恢复正式包前台。

## 补充验收

- 按协调要求新增 `HomeModeVisualChecks`：仅 Mock 包，通过临时状态输入覆盖压力、流量、断连；每种验证两组控件完整可见、48dp触控、文字单行无截断，断连点击不改变选择。结束恢复 snapshot、Mock tick 与主题，不触真实设备。
- 正常横屏2560×1600、模拟方屏1600×1600、模拟竖屏1080×1920，分别深浅主题，共六次 `HOME_MODE_VISUAL_PASSED`。不把模拟UI视为真实BLE连接问题已修复。
- 真实屏幕尺寸、旋转3、主题、正式dev120前台均恢复；Mock115保留完整编辑器R8。
- 拼图 `home-mode-review-board.png` 仅排列原生截图并附局部，标注模拟范围。原始部分截图遇到系统通知，已避开并重拍受影响验收图，不交付通知内容。
