# 直接休眠与显式开始萃取

- 需求与范围：用户要求休眠、选曲后点击萃取不弹普通确认；仅确有低温时保留预热选择。选曲/槽位卡/再次使用仅选择及导航，不能选曲即出水。保持安全门禁、当次身份、连点保护及停止方式重核。
- 来源分支/基线提交：`origin/develop e7c7ac8a` 上本任务前序提交 `43d0818b`。
- 工作分支：`fix/home-switch-readback`；PR目标 `develop`。
- 改动文件与步骤：MainActivity/HomeFragment及兼容HomeActivity休眠去普通确认；移除跨导航/绑定的自动开始布尔请求；ExtractionFragment及兼容Activity仅显式开始按钮直接调用原Service，低温使用typed PreheatGate原因且确实低于目标；可见非打断停止方式提示；相关纯函数与既有导航测试更新。
- 推送与合并：只本地实现/提交，不push/PR；后续授权时依仓库统一门禁和受保护PR流程。
- 验证证据：定向mobile单测/编译及必要导航androidTest编译，统一由协调方安排，避免与独立初始认证修复编译冲突；不运行模拟器、不操作实体机器。
- 协议与设备风险：不改协议指令、物理控制许可、未知记录、秤/水量模式判断或停止门禁。只有用户显式“开始萃取”触发；选择/返回/旋转/重新绑定不会自动开始。低温例外不包括温度未知或过高，不增加普通模式新阈值。
- 发布：不发版、不改版本/签名；禁止安装直到协调方确认安全时机，不代点休眠/开关/萃取。
- 回退：revert本提交，不清数据/未知记录、不卸载。
- 未完成与阻塞：实际触控/设备验收待用户；并行初始认证竞态由独立工作者负责，不纳入本提交。

遵守 [Git工作流](../git-workflow.md)。

## 已实现与验证

- 删除MainActivity/ExtractionFragment延迟自动开始机制及confirmStart参数；所有选曲/槽位/再次使用/库Use仅导航，明确开始按钮才同步调用原Service，无跨绑定/恢复自动控制。休眠主导航/首页及兼容入口去普通确认，不改服务门禁。
- typed温度不足且已知校正温度低于目标−1°C才提供原预热对话框；缺温度、过高、模式/准备不一致仍原阻断。低温预热确认时再核visible/service owner/原完整profile。正常模式未新增温度阈值。
- 原停止方式文本移到开始按钮旁，仅预览可见；运行、手动或OUTCOME_UNKNOWN不展示新选曲线停止方式。实际scaleMode仍在Service启动及出队前重核，不承诺无READY秤按液重停止。
- 配套初始认证早83缓存，Service native settingsAt改用Hub真实settingsObservedAtMs，缺失为null，不用当前时间伪造新鲜；Mock保留原now。Session/Hub改动由并行独立任务负责，不纳入本commit文件归属。
- `./gradlew --offline --no-daemon --console=plain -PhoyiVersionCode=80 -PhoyiVersionName=0.2.0-preview.80 :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:compileMockAndroidTestKotlin :mobile:assembleDebug`：成功（1m39s），包括两个新增纯函数测试及原完整mobile单测/导航测试源码编译；未执行instrumentation/设备控制。
- 最后Astra指出OUTCOME_UNKNOWN不在原active变量，已将state纳入纯展示谓词，两入口可见及文本均同一判定，增加UNKNOWN断言。仅重跑 `:mobile:testDebugUnitTest --tests io.openhoyi.mobile.DirectExtractionStartTest :mobile:assembleDebug`：成功（37s），2项测试零失败。
- `git diff --check`：通过。Astra已审主路径；按审查意见收窄的UNKNOWN展示谓词及断言已复核。未触碰实体休眠/锅炉/萃取。

## 安装与后续授权

用户已明确要求完成后直接创建 PR 并合并。2026-11-11 UTC 14:39 已通过 `adb install -r` 将本次真实 BLE Debug 覆盖平板：同包名 `io.openhoyi.mobile`、同签名、同 versionCode 80／`0.2.0-preview.80`。安装前确认不在曲线页、连续18秒无设备事务；未卸载或清数据，Mock 未变更。69个稳定业务文件路径与摘要完全一致，设备偏好文件逐字一致，首次安装时间保留。安装后拉回 APK 与候选 SHA-256 一致：`fbde54eaebb93ae727a7e18b8209b54218fe7b2396cbfc4fb0b2db7c5ce478f9`。

安装时源码是 `43d0818b` 加初始认证和本交互已审改动，实际输入文件摘要另有本地安装证据；APK 不嵌源码证明，`sourceCommitVerified=false`。用户当时正在其他应用，未抢占前台启动或执行任何物理控制；新交互与初始竞态的实际触控验收仍由用户进行。

随后依仓库 `scripts/create-pr` 运行完整本地门禁，推送本任务分支并创建 PR；仅精确头提交的必需 CI 通过后合并到 develop，不创建人工发布标签，不手动发布 APK。此授权替代本规划早期的暂不推送／PR限制。
