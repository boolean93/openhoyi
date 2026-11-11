# 统一电子秤启动读数时间来源

- 需求与范围：修复 dev-79 真实 DiFluid/Felicita 已收到新鲜读数，萃取/预热却因 BOOKOO 专用 `weightAt` 为空而被拒的问题。统一共享 `ShotGate` 的真实秤时间来源，不修改协议、设备写入或 1500 ms 阈值。
- 来源分支/基线提交：`develop` / `31890d58`。
- 工作分支：`fix/scale-freshness-source`；PR 目标：`develop`。
- 改动文件与步骤：先用真实 Felicita、DiFluid EE/DD 解码器产生通用 observation，复现旧门禁失败；在 `ShotGate` 使用同一有效 observation 的 `receivedAtMs`，保留 Mock 旧时间路径；补充过期、未来、无效重量、能力、断连与零目标回归。
- 推送与合并：2026-11-11 先获授权本地提交、构建真实 BLE 私测 APK，随后按「最近说的两个修复可以创建pr了」授权，通过 `./scripts/create-pr` 完整本地门禁并创建草稿 PR #55。用户最新授权「好的。可以带着前面这些所有的修复创建pr并合并。」后，先等 PR #56 合入 `develop`，再非重写合入该基线、核对相关回归，普通推送精确 HEAD，转为 ready；仅在新 HEAD 与新基线的 `branch-policy`、`verify` 全绿且无未解决审查后正常合并 #55。不 force、不绕过检查、不使用 admin merge、不启用 auto-merge，不直接推送 `develop`，继续遵守 [Git 工作流](../git-workflow.md)。
- 合并前整合基线：PR #56 的 `d046eb8b1de8f176028005c1ba7078e6dc2def73`，以 merge commit `0d834cb0347803e09b694b04536f8c8ee3732ac6` 无冲突合入，保留其 `customCurveStartPermit`。相对新基线仍仅本任务 3 个文件，不加入全秤审计改动。
- 验证证据（2026-11-11）：
  - 修复前：`:mobile:testDebugUnitTest --tests io.openhoyi.mobile.ShotGateScaleObservationTest`，真实 Felicita、DiFluid EE、DiFluid Ti DD 解码产生新鲜 observation 且 `weightAt=null` 的 3 项测试全部失败，返回 `start_block_weight_not_fresh`。
  - 修复后：`./gradlew --no-daemon --console=plain :mobile:testDebugUnitTest :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.ShotGate*Test'`，Debug/Mock 各 16 项，合计 32 项通过、0 失败/跳过；两变体主代码及 JVM 测试编译成功。使用 JDK 17，未跑模拟器。
  - 覆盖：真实 Felicita/DiFluid EE/DD/BOOKOO 解码到门禁；DD 的启动结构化结果与预热字符串入口；1500 ms 边界、过期/未来/负时间/越界重量不得被旧时间掩盖；缺失 observation、证据/能力、断连、零目标、Mock 兼容。
  - `git diff --check` 与 `python3 scripts/git_policy.py branch` 通过。Astra 独立只读复核实现、真实回调链与调用入口，无阻塞；独立执行 `:mobile:testDebugUnitTest --tests io.openhoyi.mobile.ShotGateScaleObservationTest --tests io.openhoyi.mobile.ShotGateTest --rerun`，测试 task 实际执行、16 项全绿，并核对 Mock 16 项全绿。以上是初验范围；PR 创建阶段另按完整入口运行含 lint 的本地门禁，确切命令、HEAD/base 和通过证据由入口写入 PR 正文。服务 instrumentation 与实机仍未验证。
  - #56 基线整合后执行 `./gradlew --no-daemon --console=plain :device-session:test --tests io.openhoyi.session.CustomFlowStartPermitTest :mobile:testDebugUnitTest :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.ShotGate*Test' --tests io.openhoyi.mobile.CustomFlowCurveTest --tests io.openhoyi.mobile.CustomCurveTest`，两变体主代码与相关 JVM 回归通过，session verify 103 项通过；未新增实机/视觉验证。原完整入口证据保留，新 HEAD 必需远端 CI 重新等待并核实。
- 协议与设备风险：不更改单位解析、能力证据、写入队列、去皮/停止策略或历史数据格式；真实模式不得回退到旧 BOOKOO 时间掩盖过期/未来/无效 observation。新鲜度上限仍为 1500 ms。
- 构建与发布：只生成真实 BLE Debug 私测 APK（`MOCK_MODE=false`、`debuggable=true`），不作 GitHub/正式发布、不安装、不操作设备。实际核对 dev-79 APK 为 `io.openhoyi.mobile`、versionCode `79`，证书 SHA256 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`；Gradle `:mobile:signingReport` 确认本机既有 Debug 证书与之相同。遵循日常本地覆盖规则，沿用 versionCode `79`，版本名设为 `0.2.0-dev.79-scale-fix`，以提交与 APK 哈希区分，不占用发布版本号。Release 外部签名环境当前未配置，不伪填旧版本绕过 Release 门禁。构建后需对实际 APK 再验包名、版本、证书与非 Mock 模式；身份兼容不等于已经安装或验证保留数据。
- 回退：撤销本次 `ShotGate` 局部修改及对应回归；不清数据、不删除未知记录。
- 未完成与阻塞：实机萃取、预热与蓝牙连通性留待明确授权的硬件验收。历史终重仍有 BOOKOO 专用读数依赖，单独报告，不扩散本轮修复范围。
