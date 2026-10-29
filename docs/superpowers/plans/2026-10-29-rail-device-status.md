# 侧栏双设备状态实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans。用户已批准；Sol实施，Astra规格/质量/实图验收，root统一构建/安装/提交，不重复询问执行方式。

**Goal:** 将侧栏设备状态与操作分开，睡眠入口常驻，秤连接状态与断开/重试归属明确。

**Architecture:** 复用TaskShellStatusView及现有MainActivity回调、服务门禁。两个纵向设备组使用只读标签和独立48dp动作；只在需要区分主动断开时暴露服务已有只读状态，不修改自动恢复策略。

**Tech Stack:** Kotlin、原生Android Views、现有catalog资源生成、Mock APK、ADB原图。

---

## 工作边界

继续当前 `feature/task-first-ui` → `develop` / PR4，原任务来源develop。规则以 `docs/git-workflow.md`、`docs/plans/task-template.md` 为准。不新建工作树，不覆盖他人文件；root普通提交/push，无force、无直接推主干、不合并发版。已批准规格：`../specs/2026-10-29-rail-device-status-design.md`，参考图 `docs/ui/device-status/2026-10-29-rail-device-status-proposal.png`。

不新增唤醒/连接/停止命令；不改变主动断开抑制、未知结果、认证、自动恢复或萃取门禁。睡眠点击仍先原确认弹窗；不能把图稿理解为取消确认或承诺App唤醒。

## 1. 状态与语义（Sol）

Files: `mobile/src/main/kotlin/io/openhoyi/mobile/TaskShellStatusView.kt`；必要时只读调整 `MobileService.kt` / `MainActivity.kt`。

- [ ] 将现有状态证据投影为“设备名、状态、动作、可用性、完整原因”。咖啡机只认新鲜Idle的sleepStateRaw；过期/断连不显示已唤醒，UNKNOWN保结果待确认。萃取、设置、准备和安全拦截保原禁用条件。
- [ ] 秤READY绿点已连接+断开；真实连接阶段显示连接中并禁重复重试；DISCONNECTED/FAILED灰状态+重试，无原设备/权限缺口走原连接页；UNSUPPORTED选择新设备。主动抑制读取已有真实标志，不能凭DISCONNECTED猜主动断开，也不能默认声称正在自动连接。
- [ ] 保现有tag和回调对象。状态标签只读；设备专页已有rail导航，不给标签隐形点击。每个动作完整contentDescription/tooltip包含对象与用途，已睡显示禁用“机身唤醒”说明而非可点太阳。

## 2. 双设备区布局（Sol）

Files: `TaskShellStatusView.kt`、必要的 `HomeVisualIcon.kt` 与 `TaskPageFrame.kt`（仅适配测高，不改变导航语义）。

- [ ] 104dp rail不扩宽；内宽88dp。两个组各设备名/状态14sp、20dp行高，下接至少48dp动作，图标20dp+8dp+两字动作同排居中；上下各4dp、组间8dp，当前实现常规最低约229dp含分隔/外距（2×96+37）。轻分组，无多层描边卡。
- [ ] “睡眠”“断开”“重试”“连接”“选择”保短动作词；普通新增/管理/分享/编辑工具栏仍纯图标。本轮不改变v25合同。
- [ ] 按当前可见内容真实测高，长翻译/放大字自然换行，不硬限96dp组高、不ellipsize状态；仍走现有顶部fallback，两组在顶部各自保状态与动作归属。
- [ ] present仅状态/文本变化时更新，测量不来回重建层级；不引入250ms重复layout或模式振荡。动态按钮改变不改变状态只读区域。

## 3. 资源与有限验证（Sol实施、root执行）

Files: 实际使用的默认资源、`localization/catalog/source.json`及七语言catalog/生成资源；已有shell相关androidTest按真实合同迁移，仅必要状态测试。

- [ ] 先检查已有文案能否复用；新增名称同步所有语言及source校验。保持无读数/未确认/结果待确认语义分开，不生成虚假设备事实。
- [ ] 原生检查保48dp、完整可见、常驻睡眠、禁用门禁；用明确状态验证READY绿点、断开灰点、连接中无重复重试、UNSUPPORTED选择、睡眠UNKNOWN禁用。不删已有导航/业务断言来迁就新层级；编译与实跑分别记。
- [ ] root在冻结后单一构建窗口运行现有mobile unit、Mock/Debug/AndroidTest编译、lint及catalog/APK资源验证。低影响布局不新增重复矩阵，若纯投影抽取新增测试，则覆盖证据优先级而非镜像UI实现。

## 4. 本台原图与交付（root/Astra）

- [ ] root按现有授权安装下一递增Mock版本（当前v25，下版至少v26），先保存前APK和私有数据hash；核同包/同签名、安装APK hash及原数据保持，不卸载清数据，不装辅助测试APK，不发真实机器命令。
- [ ] 实际浅深正常态：两个设备归属清晰、文字无裁切，睡眠与断开动作就近、48dp命中、导航与主内容不被侵入。查看断开/重连及短高fallback的已有Mock可达状态，只有真实执行过的状态写通过。
- [ ] Astra独立审实际源码diff和原始PNG，检查未知状态不伪醒、已睡无App唤醒承诺、主动断开不自动反连；对未实跑的状态/硬件明确留边界。未授权真实设备操作由静审/Mock覆盖，不为截图扩协议行为。
- [ ] 更新本计划勾选和证据路径，root仅提交本轮文件、普通push原PR4；必需CI结果单列，不用软件绿冒充硬件通过。

## 回退

root可revert本轮显示层/只读暴露变更并构建更高versionCode同签名修复包；保留数据和未确认记录，不降版本覆盖、不卸载、不重放设备命令。现阶段不创建发布标签、不发布正式APK。

## 当前状态

设计已获用户批准，实施待Sol；静态审查、构建、安装、原图及原生实跑尚未开始计通过。Astra仅拥有本计划及配套规格，Sol拥有代码，root拥有构建/证据/提交。

## 首轮冻结代码独立审查

Astra已读 `TaskShellDeviceStatus.kt` / Test、`TaskShellStatusView.kt`、新增glyph及文案。规格与质量无状态/设备命令阻塞：未知结果不被新鲜醒/睡值掩盖，stale无睡眠事实；主动抑制只作用于真实DISCONNECTED显示。现睡眠安全gate不变，连接阶段禁重复重试，prefs只读，无新命令。图标按kind缓存、相同文本不重设，模式变化才重挂层级，真实组高参与fallback。新增4个单测针对UNKNOWN优先级、连接阶段、主动抑制和缺失事实，尚不预记构建结果。

精确布局预算纠正：当前组最低96dp，rail外距/分隔/间距共37dp，设备区最低229dp，非草案估算213dp；常规导航约416dp，合约645dp，本台实际是否保持rail仍以图为准。两个待实图点：compound drawable从4dp起点绘制，动作图文是否视觉居中/8dp间隔需检查；连接中glyph是静态回转弧，禁用语义已明确但不冒称动画spinner。构建冻结中未改生产；不以静审代视觉/硬件验收。
