# 创建与编辑曲线的总流量

- 需求与范围：创建及编辑压力曲线提供独立的总流量（ml）输入；贯通保存重开、复制、预览/详情、导入导出、执行参数与参数指纹。保留阶段分配，不将水量误写成速度。Astra 负责设计/独立复核，Sol 实现与精简验证。
- 来源分支/基线提交：`origin/develop`，`58b731f901992c163e1073c4c2d4df10f83ed19b`。
- 工作分支：`feature/curve-total-volume`；隔离工作树 `openhoyi-curve-total-volume`，不改旧 develop 工作树。
- PR目标：`develop`。
- 改动文件与步骤：既有「总量上限」作为可空整数字段放在阶段表下；显式范围 1..max(ceil阶段合计,800) mL，留空保留旧自动行为；接秤继续 max(基准总量,ceil(4×目标克重))；扩展 `CustomCurveDocument` 与严格版本迁移；更新编辑器/表单、本地化、预览/详情及配方计划；贯通执行适配和参数指纹；补充相关 JVM 回归与必要原生 UI 测试。
- 推送与合并：Astra 独立复核及相关本地验证已通过，本轮已获准仅提交本任务文件、普通推送 `feature/curve-total-volume`，并经 `./scripts/create-pr --draft --base develop` 执行现行完整本地门禁后创建草稿 PR。当前交付尚待执行结果，不合并，等待精确提交的 `branch-policy`、`verify`。日常 PR 不运行 AVD。
- 验证证据：优先运行相关 JVM 测试、资源目录校验、编译及 lint；按完整差异采用仓库现行校验入口。记录实际命令/结果与未运行的设备测试，不将编译当作实际操作证据。
- 协议与设备风险：独立总量影响 `StartParameters.maximumWaterMl`、既有总量门禁及连接秤时的终止行为；根据原 APK 与固件独立证据，将总量门禁下界从阶段和修正为 1 mL，允许阶段 15+40 mL 配置总量 40 mL 提前达到累计量阈值；其余身份、模式、权限、时效、单位、目标范围和上界门禁不变。不改变阶段参数来凑可执行性。不改协议编码、不操作真设备、不安装、不清数据、不发起机器写入。
- 语义核查修正：先前把阶段合计当作固件下界的假设不成立。原 APK 1.2.4/1.2.6 独立保存 totalFlow 到 row.flow，阶段 flow1…4 另存；固件 1.1.4 将启动 b3/b4 保存到活动阈值并在累计量达到后转入结束状态。它不是杯重或流速，也不是所有模式/阶段的无条件硬停；物理精度未实测。详见 [固件证据](../evidence/2026-11-11-curve-total-limit-firmware.md)，[原 APK 证据](../evidence/2026-11-11-curve-total-limit-apk.md)。新增 3 组离线报文使用原 APK 专用入口绑定 APK、bundle 和提取函数 SHA256；不覆盖旧 72 组 fixture。
- 发布：不发版，不改签名、包名、versionCode，不发布 APK。
- 回退：代码回退须保留已有文件和未知设备记录；旧档不批量迁移。显式上限使用 schema2，旧版应用不支持此格式，不能把降级当作无损恢复；保留原始存储并通过后续兼容修复恢复，不卸载、不清记录。
- 未完成与阻塞：Astra 已独立复核实现及原 APK 报文，未发现 P1/P2；相关本地检查通过。交付阶段仍须通过创建 PR 入口的完整门禁与远端必需 CI；无环境阻塞，JDK 17 与 Android SDK 已存在。

具体规则以 [Git工作流](../git-workflow.md) 为准。

## 本地验证与冻结点

- 2026-11-11，JDK 17、既有 Android SDK，隔离树内执行：`./gradlew --no-daemon --console=plain :protocol-core:check :device-session:check :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:compileMockAndroidTestKotlin :mobile:lintDebug`。通过；会话 JVM 720 项，移动端 Debug 519 项、Mock 526 项均无失败；会话自检 103 场景通过。Lint 为 0 errors、435 warnings，未将警告消除列入本轮范围。
- 最后补入原 APK 报文绑定和混合版本存储断言后，精确复跑 `:mobile:testDebugUnitTest` 与 `:mobile:testMockUnitTest` 的 `CustomCurveTest`、`CustomPressureCurveAdapterTest`，两变体各 17 项通过；再次编译 `:mobile:compileMockAndroidTestKotlin`、运行 `:mobile:lintDebug`，通过。
- `python3 -m unittest discover -s localization/tests`（25 项）、`python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`、`python3 scripts/git_policy.py branch`、`git diff --check` 均通过。
- `localization/verify_apk_resources.py` 对实际 Debug/Mock APK 核验通过：两包各 2949 字符串匹配，6 项 literal round-trip 通过。
- `scripts/generate_custom_water_limit_oracle.py` 直接读取原 APK 1.2.4，生成 3 配方 × 6 槽 × 2 秤状态；独立复核重新生成后逐字节一致。原 72 配方 JSON/TSV 与基线逐字节一致。
- 原生 UI 回归已补创建、旋转恢复、保存重开、编辑上限、非法输入不落盘及滚动可达性断言，但本轮仅编译，未实际运行。没有截图/真机停水精度证据，上述冻结验证时未执行任何 AVD、安装、设备写入、发版、提交、推送或 PR 操作。交付阶段仅按授权普通推送并经创建 PR 入口运行完整 `scripts/verify-pr-checks full`；远端 CI 状态以草稿 PR 的精确头提交为准。
- 首轮构建记录：`/tmp/openhoyi-curve-total-volume-validation.log`；最后受影响检查：`/tmp/openhoyi-curve-total-volume-final-checks.log`。上述日志对应独立复核时的未提交冻结状态；交付以随后固定的本任务提交为准，不把这些日志中的基线 HEAD 当作已提交产物源码。
