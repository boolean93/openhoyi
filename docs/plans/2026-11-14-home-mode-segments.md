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
