# 同认证连接设置缓存：启动与正预热修复计划

> **For agentic workers:** 按 `superpowers:executing-plans` 实施；用户已明确要求修复并安装真实 Debug。Sol 开发，Astra 独立审查，root 构建与安装，不重复请求许可。

**Goal:** 消除同一已认证连接的 Settings 超过180秒后，启动萃取/正预热被误阻断的问题，同时保留设置写入的严格时效。

**Architecture:** 区分“当前认证连接已观察到的设置”与“可安全覆盖配对字段的新鲜设置”。新增仅供启动/正预热/温补读取使用的有限证据策略；原 SettingsFreshness 180秒及睡眠计划共用逻辑不动。

**Tech Stack:** Kotlin，device-session 与 mobile 现有门禁，JUnit/原生回归。

## 分支、范围与风险

- 来源为 develop 派生的既有任务；继续 `feature/task-first-ui`，基线 `759ff8d`，PR4 → develop。遵守 `docs/git-workflow.md`、`docs/plans/task-template.md`。普通提交推送，不直接推main/develop、不强推、不合并、不发布。
- 用户已授权本轮真实 Debug v30 覆盖安装，root 执行同包/同签名/单调版本/hash和数据保留验证。安装不等于授权自动启动泵或宣称硬件萃取成功。
- 同连接缓存**无法感知机器未回报的本机设置修改**。继续利用新收到 Settings 更新、启动上下文 settings 字段快照和入队/dispatch复核；不把缓存称“最新设置”，不伪造 settingsAt。
- DeviceSession connect/终止已清 lastSettings，且仅接收 authenticated Settings；Service断线/失败清snapshot.settings。每个新增调用必须同时满足READY、当前连接settings存在、时间戳非负且不在未来。时间戳合法本身不是认证证明。

## 实施与文件边界

- [ ] device-session 新增小型 `BrewSettingsEvidence` 策略与测试。只检查已由调用者保证连接归属的设置证据，不改 `SettingsFreshness`、`SleepScheduleFreshness`。
- [ ] `DeviceSession.kt` 仅 `canStartExtraction`、`canStartPreheat`/正温度 `setBrewWait` 使用新连接证据。保新鲜Idle、awake、告警、READY及角色检查。保startContext generation/address/参数/flags/温度/温补字段一致及5秒有效期，队列真正dispatch再次验证。
- [ ] `MobileService.kt` 只温补读取、studio启动检查、正预热入口及对应准备UI调用使用新证据。`ExtractionFragment.kt`/兼容萃取入口按实际需要同步准备按钮，不批量替换所有machineSettingsFresh。
- [ ] 普通/paired设置写入、锅炉设置、杯数重置、睡眠计划及其分段写入保持原180秒。停止/取消预热原路径不变。UNKNOWN恢复记录、确认、曲线/秤/温度门禁、持久化写入注册与所有dispatch许可不放宽。

## 必要回归与独立审查

- [ ] 同一认证连接Settings超过180秒、Idle持续新鲜且安全：启动/正预热可进入原确认与dispatch；普通配对字段写入/杯数仍拒绝过期基线。
- [ ] 断线重连或换机后没有新Settings：启动和正预热仍拒绝；收到当前连接Settings后才恢复。未来/缺失时间戳拒绝。
- [ ] 排队后Idle过期、睡眠/告警、连接世代/地址变化或已观察Settings字段改变：原启动dispatch拒绝。UNKNOWN、预热在途/结果未明与停止门禁保持。
- [ ] Astra 审生产/测试diff，确认没有改全局180秒、改settingsAt、复制旧连接设置或自动重试启动。
- [ ] root统一相关protocol/session/mobile测试、Mock/Debug/AndroidTest编译、lint及APK资源验证；原生编译与实际执行分别记录。
- [ ] root安装真实Debug30，验证签名/包名/递增versionCode/APK hash及既有数据保留，记录原APK回退证据。真实设备上的长连接启动效果只有用户/授权实际操作证据后才标硬件通过。

## 回退与当前状态

- [x] 用户授权与有限策略静审通过，发现严格写入与启动确共用SettingsFreshness，已给Sol具体边界。
- [ ] 最终源码/测试独立审查、构建、安装完成。
- [ ] 真实机器长连接效果验收，未完成前不得以Mock或安装成功替代。

回退使用普通修复/revert提交和同签名更高版本恢复；保留数据及所有未确认记录，不卸载清数据，不盲目降级APK。Astra只拥有本文档，不修改生产、不运行构建或提交。

## 冻结代码独立审查

2026-10-31 已实际审查生产五文件、新 `BrewSettingsEvidence.kt` / `BrewSettingsEvidenceTest.kt` 和预热队列回归diff，静审通过。限定调用点与本计划一致；原 SettingsFreshness 及 SleepScheduleFreshness 无改动，普通/paired写入、杯数和停止/取消仍走原门禁。新测试实际通过 DeviceSession 覆盖181秒持续Idle启动/正预热、过期普通配对写入/杯数拒绝、断线换机无新Settings拒绝及出队Idle过期/host guard拒绝。启动世代/地址/设置字段快照/5秒及dispatch复核原逻辑保持。

开发侧简要记录见 [同连接萃取设置证据修复](../../plans/2026-10-31-brew-connection-settings.md)，作为实施补充；本文件为统一计划入口，两者不改变全局180秒策略。当前root构建中，尚不宣称测试/安装或真实机器效果通过。
