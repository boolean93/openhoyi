# 样张默认主题字体初始化

- 范围：修正 Mock 专用 `StrictVisualSampleActivity` 的默认主题字体，不修改产品主题、子控件候选值、TaskTypography 字体门禁或样张布局。按 [Git 工作流](../git-workflow.md)，复用 `feature/task-first-ui`、目标 `develop`；开始 HEAD `ec690286c1a3cc0489ded612a858f15450453f27`，工作树干净，不推送/合并/取消 CI。
- 真实失败：frame run `39121397062`，测试源码 merge `1b56535dcf91107acb26bebc55e9ddd0ddb83059`。24页真实 frame 已通过；样张准备页已保存整窗和 `touch-geometry.json`（fontScale=1、density360、appSize2560×1391、89节点、issues=[]），随后固定中文字体 gate 失败，未进入完整样张验证。
- 根因：样张直接继承 `Activity`，manifest 继承 `MobileTheme`，其 `fontFamily=sans`。产品 `ThemedActivity` 在 `super.onCreate` 前应用仅含字体的 `MobileChineseTypography`，样张漏了此步骤。显式子控件 `TaskTypography.face` 不代表默认主题正确；失败 gate 比较默认 TextView 与固定字体的实际 `measureText`，没有使用 `Typeface.equals`，不是 equals 假阴性。
- 最小修复/所有权：只改 Mock Activity，在 `super.onCreate` 前应用同一 font-only overlay；不继承会读 appearance/language 偏好和自动 recreate 的 `ThemedActivity`。按主任务额外授权，`StrictVisualSampleChecks` 只在已有 touch-geometry 保存默认字体诊断（原/现 theme fontFamily 解析、默认 probe weight/style、与原 gate 完全相同字符串和40px Paint的两宽度/delta、equals 仅诊断）。新增本计划，不改 `TaskTypographyEvidence`。
- 前置契约扫描：此 CI 已通过 Mock/CI模拟器、source/APK/font SHA 等入口检查并实际创建首张样张；中文locale、1.0字号、density360和touch树由真实文件证明。字体400/粗体700/固定300、400、500、700沿24页已通过的同资源加载路径；后续多行、Canvas字形盒、滚动、深色/1.3、存储不变量仍须运行，不预判通过。未发现另一个必然冲突的初始化接口，不借此放宽内容或门禁。
- 验证：源码稳定后主任务统一编译 Mock/AndroidTest，Astra 复核；新诊断在原固定字体 gate 前落盘，诊断本身不新增 PASS 或放宽断言。编译不代替实际字体门禁重跑和12图。
- 协议/设备/发布：不启动服务或 BLE、不写库存/配方/准备上下文/偏好，不操作设备、不安装、不发版，不改签名、版本、schema。回退仅此 Mock 初始化与诊断提交，不卸载、不清记录。

## 本批验证事实

- Astra 已实读本批 Activity、Checks 和本计划，无代码阻断；默认 probe 从不 measure/layout，不引入 detached LayoutParams 前提问题。
- 主任务统一 Mock/AndroidTest 编译 exit 0，`BUILD SUCCESSFUL in 43s`，`91 actionable tasks: 9 executed, 82 up-to-date`；日志 `/tmp/2026-10-18-sample-font-theme-build.log`。
- 本批 `git diff --check` 与分支策略检查通过；提交前确认暂存区为空，只提交本批三个文件，不推送。
- 原字体 gate 和12张实际图仍未在修复后验证；上述编译/代码审查不是字体 runtime 通过或 G1–G4 视觉通过，不改写此前失败结论。
