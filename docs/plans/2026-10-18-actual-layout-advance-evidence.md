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
- 首次提交时尚未实际运行新增 Android 合成断言，后续 CI 失败见下节；不把可编译性或其他代理的 JVM 测试算成本批 runtime 通过。本地没有安装、设备操作、CI取消、推送或视觉改动；真实图与 G1–G4 验收仍另行记录。

## CI 39120883236 的夹具前提修正

- 新证据：源码 `3b0c6e3` 的 task-frame 在 Home 采集时未进入样张；`result.txt` 指向 `TaskTextLayoutEvidenceChecks.verifyOnce` 中测量/布局后执行 `view.text = ""`，随后 `TextView.checkForRelayout` 对 null `mLayoutParams.width` 解引用。此失败不能记为产品视觉失败或样张运行通过。
- 根因：SDK34 `TextView.setText` 在已有 `mLayout` 时调用 `checkForRelayout`，该路径依赖 `mLayoutParams.width/height`；直接给 detached TextView 调 `measure/layout` 只构造其内部 Layout，不会创建父布局参数。
- 最小修复：两个会被独立 measure/layout 的 TextView 在创建时先设置匹配各自 EXACTLY 尺寸的 `ViewGroup.LayoutParams(400,200)`、`(50,40)`；仍不添加 parent、不 attach 窗口。故意未测量的 `empty` fixture 保留；换文本/hint、ellipsis 和全部原断言不删除，`verifyOnce` 不跳过。
- 扫描：本 helper 唯一 measure/layout 后修改文本/提示的路径是上述 `view.text`/`view.hint`。ellipsized 虽无后续修改，也补全同一测量前提；`TaskVisualColorEvidenceChecks` 无 measure/layout 调用，不触发此已有内部 Layout 的重排路径。无需修改 `TaskTypographyEvidence` 或产品代码。
- 本地运行限制不变：不得安装/操作设备；修复后的实际 Android 断言仍待后续 CI，不以静态核对或编译声称 NPE 已在运行中消除。
- 本次修复经 Astra 实读当前 diff 无阻断；主任务统一 Mock/AndroidTest 编译 exit 0，`BUILD SUCCESSFUL in 24s`、`91 actionable tasks: 4 executed, 87 up-to-date`，日志 `/tmp/2026-10-18-detached-layout-build.log`。这只证明可编译，修复后的 selftest 和12张实际样张仍未验证。提交前 HEAD 为 `ebc893989386b6e10aa48f5e441d8a43942ba393`，暂存区为空；只提交 helper 与本计划，不推送。
