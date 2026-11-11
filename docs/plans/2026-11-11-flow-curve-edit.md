# 流量先决曲线完整编辑

- 需求与范围：支持新建压力/流量先决曲线，从工厂或已支持范围内的完整 profile 无损另存编辑，贯通保存重开、预览、分享导入及执行参数。Astra 核定原 APK/固件语义与独立复核；Sol 实现和精简测试。旧缺少模式位的 FLOW_RAW 文件只读，不猜模式。
- 来源分支/基线提交：`origin/develop`，`31890d58031610990c7357f1deffa7f1c640d201`。
- 工作分支：`feature/flow-curve-edit`；隔离工作树 `openhoyi-flow-curve-edit`。
- PR目标：`develop`，本轮不推送、不创建 PR、不合并。
- 改动文件与步骤：新增严格 schema3 配置块，保留全局 flow、首段模式、独立 pressureLogic、闷蒸、首段限时、补偿和四槽 inactive 参数；保留 schema1/2 及指纹兼容。新增完整参数 adapter/policy 与动态主机许可，不放宽旧 pressure policy。编辑器可选择控制方式，流量模式可选择首段及 Hs/Ls；单位、混合预览、导入导出摘要一致。字段、控制状态和源文档随旋转保存。
- 兼容细节：新建空白压力仍默认 schema1/2，保留 1–4 段及 0–12 bar 能力；选择流量或输入新增完整参数后才显式升级。已有曲线降段，或新草稿的隐藏槽确实被编辑过时，用 schema3 保留非零尾槽；未改过的默认隐藏预填槽不强迫新建压力升级。完整曲线按新窄范围校验，压力与流量各缓存自己的逻辑位，不把流量 Hs 带回压力模式。
- 验证证据：代表性真实工厂及原 APK 独立 oracle，复制不改→存储重开→分享导入→执行帧逐字节相同；100 条工厂全保真；旧 schema/未知 RAW、模式/计时/补偿/尾槽、当前存储精确授权及队列撤销。运行相关 JVM、资源校验、Debug/Mock 构建和 lint；原生 UI 测试仅编译，不把编译当实际截图证据。
- 协议与设备风险：wire 只有全局 variableFlowLogic 与首段 firstSegmentFlowMode，不支持任意逐段模式。pressureLogic 为独立位，flow 下 Hs/Ls，pressure=true 的旧“跟随”仅保留，不新增执行许可。新增 schema3 执行仅临时槽7、2–4段、已证实目标/水量/计时范围；流首段必须有时间或水量出口，time1=255 拒绝，inactive 槽仅校验 wire 范围并保真。总量不是各阶段无条件硬停。保留既有固件/遥测/秤/队列/权限与精确当前配方门禁，不改编码器、不发真实机器命令。
- 固件范围：新完整流量参数在 1.1.3 仅允许 Hs + 首段压力；Ls 或首段流量须精确固件 1.1.4。界面、capture 和实际队列派发共用判定，固件或当前存储配方改变会撤销旧许可。原 factory/captured/旧压力路径不变。
- 发布：不发版、不安装、不改签名/包名/versionCode，不启动模拟器或真实萃取。
- 回退：代码回退保留本地原始数据与未知设备记录；旧版不支持 schema3，不能通过降级、卸载或清数据实现恢复。保留原文件，后续兼容修复。
- 未完成与阻塞：实现及相关本地检查已完成，Astra 独立代码复核无剩余 P1/P2。原生 UI 回归仅编译，未实际运行或取得横竖屏截图，不宣称布局已完成设备验收；未进行硬件验收、真实萃取或物理停水验证。无已知本地构建阻塞。提交由协调线程随后仅在本工作树本地冻结，不包含远端操作。

具体规则以 [Git工作流](../git-workflow.md) 为准。协议依据见 [原 APK 与固件证据](../evidence/2026-11-11-flow-curve-edit.md)。

## 本地冻结验证（2026-11-11）

- 完整相关验证使用既有 JDK 17 与 Android SDK：`./gradlew --no-daemon --console=plain :protocol-core:check :device-session:check :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:compileMockAndroidTestKotlin :mobile:lintDebug`，通过，日志 `/tmp/openhoyi-flow-verified.log`。protocol 自检 39823 checks / 32856 recorded notifications；session 自检 103 场景、JVM 739 项通过；移动端 Debug 529 项 / Mock 536 项均 0 失败，各有 1 项既有跳过。
- 最后保留单段压力创建与隐藏槽修复后，精确复跑 `CustomCurveTest`、`CustomFlowCurveTest`、`CustomCurveExecutionLibraryTest`，Debug/Mock 各 25 项全部通过；同时重建 Debug/Mock、重新编译 Mock instrumentation、运行 lint，通过。日志 `/tmp/openhoyi-flow-final-freeze.log`，`BUILD SUCCESSFUL in 2m 38s`。Lint 为 0 errors、435 warnings。
- 最后命令为 `./gradlew --no-daemon --console=plain :mobile:testDebugUnitTest --tests io.openhoyi.mobile.CustomCurveTest --tests io.openhoyi.mobile.CustomFlowCurveTest --tests io.openhoyi.mobile.CustomCurveExecutionLibraryTest :mobile:testMockUnitTest --tests io.openhoyi.mobile.CustomCurveTest --tests io.openhoyi.mobile.CustomFlowCurveTest --tests io.openhoyi.mobile.CustomCurveExecutionLibraryTest :mobile:assembleDebug :mobile:assembleMock :mobile:compileMockAndroidTestKotlin :mobile:lintDebug`；设置 `JAVA_HOME=$(/usr/libexec/java_home -v 17)`、`ANDROID_HOME=/Users/boolean93/Library/Android/sdk`。
- `python3 -m unittest discover -s localization/tests`（25 项）、`python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`、`python3 scripts/git_policy.py branch`、`git diff --check` 通过。`localization/verify_apk_resources.py` 对实际 Debug/Mock 两包各核对 3049 字符串，6 项 literal round-trip 通过。
- 新 JVM 回归包含 100 条真实工厂曲线 × 两秤状态全链路保真，6 条完整参数配方 × 两秤状态与原 APK 独立 oracle 逐字节一致；旧压力精度和 schema1/2 断言仍保留。新 opt-in instrumentation 覆盖模式往返、单段压力创建、工厂无改保存、首流/Hs-Ls/计时/补偿编辑、旋转/重建、分享导入、隐藏槽恢复、无出口拒绝和 RAW 只读，但本轮仅编译，未执行。
- APK 仅为本地验证产物，不上传、不安装、不发布。Debug：`mobile/build/outputs/apk/debug/mobile-debug.apk`，SHA256 `b355c263a262398bbd4e2c2092c7b07606158adcc9086e8b3a8e6b535e8a6adc`；Mock：`mobile/build/outputs/apk/mock/mobile-mock.apk`，SHA256 `964272bdf180af6813205d703376a6781bb79f0196f91c2b88087994f65c8f41`。APK 来自本工作树的本次实现，源码冻结以最终本地提交记录为准；不是安装、硬件验收或发布证据。
