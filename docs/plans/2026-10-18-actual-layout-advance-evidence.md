# 实际 Layout 与数字 advance 最小取证

- 需求与范围：按 Astra 已确认边界，仅补 `TaskTypographyEvidence` 实际 TextView Layout 逐行数据及同 Paint 的 ASCII 数字 advance 探针；保留全部旧字段，不改产品或样张视觉。
- 来源/工作分支：复用已授权 `feature/task-first-ui`，开始 HEAD `a4786a087d0e8959fd747b7f692d8181bd646114`，工作树干净；原 develop 来源及 PR #4 关系沿用主任务。目标 `develop`，遵守 [Git 工作流](../git-workflow.md)；本批不推送、不合并，不取消在途 CI。
- 文件所有权：仅 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/TaskTypographyEvidence.kt`、新增对应 `TaskTextLayoutEvidenceChecks.kt` 和本计划。不得覆盖其他代理文件。
- 步骤：先写合成 Android 取证回归断言，再追加只读取证；源码稳定后由主任务统一编译 Mock/AndroidTest。合成断言随取证首次执行，不冒称本地已实际运行。
- 数据：逐行 start/end/visibleEnd、top/bottom/baseline、left/right、ellipsis；`TextView.getLineBounds` 的 View 逻辑行盒/基线；View origin、scroll、clip。逻辑坐标与可见/屏幕坐标分开，行盒不是墨迹盒；无 Layout、hint 对应布局不可公开读取、变换/跑马灯等不能证实时给 unknown/reason。
- 数字：以复制的当前 Paint 调用 `getTextRunAdvances` 测 `0123456789`，保存原始 float advance，不先像素取整、不加容差；`observedEqualAdvance` 仅说明当前独立探针观察结果，不证明 `tnum` 启用。零/无效 advance 不包装成等宽通过。保留 fontFeatureSettings、size、weight、scale/skew/letterSpacing 等上下文。
- 验证：检查真实 Layout API 对应值、多行/滚动/未布局/hint 语义、原始 advance 与精度差异/无效值、JSON round-trip、不修改 View/Paint；本地只编译和静态检查，不安装、不操作设备。实际运行由后续 CI 取得。
- 协议与设备风险：仅 androidTest 证据，无 BLE、服务、协议、队列、库存、配方、准备上下文或未知记录写入；不重试机器操作。
- 发布/回退：不发版、不打标签、不改签名/版本/升级；仅回退本批取证文件，不卸载、不清记录。
- 仍未完成：G2 cmap/逐 glyph font probe 不在本批；Canvas 产品绘制与最终屏幕合成色覆盖不变；本批不构成 G1–G4 视觉通过。

## 本批验证事实

- Astra 已实读本批三个文件，无代码阻断；保留实际 Layout、逻辑坐标与独立数字探针的边界。
- 主任务统一编译 Mock 与 AndroidTest 成功：`BUILD SUCCESSFUL in 47s`，exit 0，`96 actionable tasks: 12 executed, 84 up-to-date`。日志 `/tmp/2026-10-18-sample-touch-layout-build.log`。
- 本批 `git diff --check` 与分支策略检查通过；提交前暂存区为空，只暂存本批三个 owned 文件。
- 尚未实际运行新增 Android 合成断言；不把可编译性或其他代理的 JVM 测试算成本批 runtime 通过。没有安装、设备操作、CI取消、推送或视觉改动；真实图与 G1–G4 验收仍另行记录。
