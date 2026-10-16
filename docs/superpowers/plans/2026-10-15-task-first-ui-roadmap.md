# HOYI Task-first UI Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **2026-10-16范围更新**：用户取消竖屏兼容要求；后续仅横屏专项实施/验收。21页、原业务安全、大字号/IME/短高仍保留，按[横屏范围计划](2026-10-16-landscape-only-scope.md)执行。旧竖屏证据只作历史，不新增竖屏任务。

**Goal:** 按已确认规格实现全部21页任务工作台改版，优先中文及用户平板，不删除原功能或削弱门禁。

**Architecture:** 保留现有原生Android View、Activity和业务/服务所有权。新共享布局与展示投影只接收窗口、已有采样及状态，不调用BLE或库存写入；页面通过原确认和业务回调执行操作。共享层、图表、业务页面与验收分开提交。

**Tech Stack:** Kotlin/JVM、Android View/Canvas、JUnit4、现有Mock instrumentation、Gradle、GitHub Actions。

---

## 1. 授权、基线和Git路径

用户已回复“按此进入实施规划”，不再请求同一规格或视觉方向确认。默认在本会话逐批推进；不把选执行方式变成额外审批。此路线保留完整21页目标；首批落地不代表整项完成。

- 规则：[Git工作流](../../git-workflow.md)，字段参照`docs/plans/task-template.md`。
- 需求：[已确认设计规格](../specs/2026-10-15-task-first-ui-design.md)、[21页版本索引](../../research/task-first-ui-concepts/index.md)、[源码契约审计](../../research/2026-10-15-ui-source-contract-audit.md)。
- 本次fetch后`origin/develop`：`66ebda95dc7986eec66f9fa0b7e6cbd565c74506`。
- 高级功能`origin/feature/advanced-experience`：`7fe992fd472e7a1db5e82c8c821595a78a8ec770`。
- 既有完整页面/门禁来源`origin/feature/single-screen-ui`：`ed01de0f1b6f6449257e3480053340a9bb75b160`。
- 新工作分支`feature/task-first-ui`从最新`origin/develop`创建，独立工作树`openhoyi-task-first`。不在旧PR分支追加改版实现。
- 依赖引入采用**显式本地依赖合并**：将固定的`ed01de0`合入新feature，单独merge commit说明其包含高级功能和旧布局；再合入本设计文档分支。不合并或关闭任何远端旧PR。若远端基线变化或发生冲突，先核对差异，不能重置其他人的内容。
- `develop...ed01de0`现有239文件差异属于依赖，不冒称本轮新UI。新UI实质差异单独用`git diff ed01de0..HEAD`审查；依赖源码与新改动都经过新分支验证，不沿用旧CI充当新证据。
- 目标PR为`develop`，普通推送、不force、不直推main/develop。PR正文分别列依赖、真正UI改动、验证和已知缺口；四项必需CI通过且讨论解决后才考虑合入。不通过引入依赖绕过任何CI或协议证据要求。
- 不发版、不打标签、不发布/安装APK、不操作机器。不改包名、签名、versionCode、数据schema；模拟器安装只限隔离Mock测试，不是用户平板安装。
- 回退：追加修正/revert PR，不重写共享历史，不卸载清数据、不删除未知控制或库存事件。未合入feature可保留现场停止，不把降级当恢复。

## 2. 文件责任和实施批次

下列是全量交付分解，不是声称所有批次已具备逐行实施补丁。首批有独立的可执行步骤与完整代码，后续批次须在对应依赖/实测接口明确后形成同级执行清单；不能把本路线表当成已经执行的任务。

| 批次 | 负责文件（目录`mobile/src/main/kotlin/io/openhoyi/mobile/`） | 交付与验证边界 |
|---|---|---|
| F1 共享展示基础 | 新`TaskLayoutPolicy.kt`；随后`TaskPageFrame.kt`、`TaskNavigationView.kt`、`TaskControls.kt`；接入`HoyiUi.kt`；语义资源`mobile/src/main/res/values/colors.xml`、`values-night/colors.xml`、`values/strings.xml` | 先以纯窗口规则测试锁定用户平板横屏、短高/IME/字体测量后的最小宽度；真实Frame再用instrumentation检查Insets/导航/单主操作。禁止在此引入设备/库存所有权 |
| F2 四指标图表 | 新`ShotChartModel.kt`、`ShotChartProjection.kt`、`ShotMetricChartView.kt`；迁移`ExtractionOverlayProjection.kt`、`ExtractionOverlayView.kt`、`ShotChartView.kt`；保留`ShotSeries.kt`/存储原语义 | 确定采样验证bar/ml/g/°C、时间、四独立量程、聚焦模式、缺口/负值/超范围/参考/旧来源。用可计算端点而非PNG非空证明正确，不修改记录和控制 |
| F3 冲煮任务（2页） | `HomeActivity.kt`、`ExtractionActivity.kt`、`BrewFeedbackDialog.kt`；原`StopActionPresentation.kt`与`ShotGate.kt`作为不变契约 | 准备→原确认→运行→观察结果→复盘；设备面板、五槽、预热/取消、告警/通知、历史参考、一次趣味反馈保留。实测停止中/未知/手动，不复制状态机 |
| F4 曲线任务（6页） | `CurveActivity.kt`、`CurveEditorActivity.kt`、`CurveShareActivity.kt`、`CurveImportActivity.kt`、`CurveImportPreviewActivity.kt`、`LegacyCurveActivity.kt`；`CurveStageView.kt` | 连续列表及排序/分类/恢复；完整四阶段、阶段非时间；绑定槽位、分享/显式导入/冲突、旧版只读。不得扩大协议控制能力 |
| F5 记录与复盘（6页） | `HistoryActivity.kt`、`HistoryDetailActivity.kt`、`BrewComparisonActivity.kt`、`BrewReviewActivity.kt`、`LegacyHistoryActivity.kt`、`LegacyHistoryDetailActivity.kt`、`LegacyShotChartView.kt` | 三来源工具各自保留；单指标同尺度两杯对比；完整笔记与旧豆身份；旧图不补温度/累计量/秤重；读取失败≠零值 |
| F6 库存任务（5页） | `BeanInventoryActivity.kt`、`BeanEntryActivity.kt`、`BeanBatchActivity.kt`、`BeanLedgerActivity.kt`、`BeanPreparationActivity.kt`；共享原生字段/局部编辑容器 | 名称/重量/三日期、目标余量＋原因→预览→确认、原余额复核、选择与耗用分开、事件幂等；流水无伪造时间。`bean-core`不因UI而改schema |
| F7 秤工具（2页） | `ScaleActivity.kt`、`ScaleConnectionActivity.kt`；设置视觉随`ScaleSettingsActivity.kt`、`MachineSettingsActivity.kt`、`AppSettingsActivity.kt`共用控件 | 重量/实时性/能力、App计时/恢复、去皮原条件、稳定粉量来源、单连接所有权、只读协议限制；三个设置页字段不减 |
| F8 整体验收 | 新`mobile/src/androidTest/kotlin/io/openhoyi/mobile/TaskFirstUiChecks.kt`；适配原业务/列表/窗口检查及`.github/workflows/single-screen-ui.yml`、`scripts/check_single_screen_ui.sh`；新增人工读图记录 | 21页＋状态/字号/主题/键盘/列表恢复；原业务17路径、文档选择器、库存/未知写入/生命周期/升级保留。几何检查不替代视觉/任务审查 |

## 3. 按需求追踪

- 规格§1–4 导航、层级、操作：F1/F3–F7。
- §5 四指标/参考/量程/来源：F2/F3/F5。
- §6 状态/停止/告警/反馈：F3/F7，原契约回归F8。
- §7 21页：F3的2页＋F4的6页＋F5的6页＋F6的5页＋F7的2页＝21，不遗漏旧版页。
- §8 输入/库存/恢复/分享：F4/F5/F6/F7，F8验证原始值和幂等事件而非只查控件存在。
- §9 用户平板与窄窗：F1/F2，各页面迁移时实际渲染，F8统一复核。
- §10 所有权边界：F1/F2不接触控制，F3–F7继续原事件回调；F8检查新旧差异中控制文件未被无意修改。
- §11–13 完成证据/Git：F8和本路线；没有证据的条目保持未完成，不用旧截图或源文件清单冒充。

## 4. 命令与证据组织

所有Gradle命令在新feature工作树执行。先记录baseline日志，再记录修改后的精确源码提交。云端使用现有JDK17/Android35配置。

```bash
./gradlew :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain
bash scripts/check_advanced_mock_pages.sh business
bash scripts/check_single_screen_ui.sh tablet-user-landscape
bash scripts/check_single_screen_ui.sh tablet-landscape
bash scripts/check_single_screen_ui.sh tablet-large-font
```

后三条只在一次性CI模拟器运行，脚本会覆盖模拟器显示/字号，禁止在用户平板上执行。现有脚本的旧页数/截图计数和源码查找方式若与新壳不匹配，先更新成能验证相同或更强契约的测试，不删业务断言或只修改成功标记。新增短高IME、1.3/系统最大字号、深色等场景时单独记录实际窗口与截图；不能把增加计数当验收。

业务基线应保留现有检查的实际断言：17路径、库存耗用与重入、四阶段完整字段/导入冲突、真实系统文档选择器、服务所有者、断连/结果未知写入、停止、声音一次性与恢复、列表/光标、升级。新导航可改测试选取路径，但事件/数据/门禁断言不降低。

人工读图证据写`docs/evidence/task-first-ui-review.md`，逐场景记录源码、窗口、主题/字号、实际截图、当前状态/主读数/下一步/停止能否快速识别、发现问题与修正；不事先填写通过。硬件证据与Mock证据分开。

## 5. 首批任务和继续方式

执行[首批窗口策略计划](2026-10-15-task-first-layout-policy.md)。它只交付确定的自适应规则与测试，不声称已换页面。以其输出约束真实Frame和图表布局，随后逐批执行上表；不得因首批通过就缩小21页目标。每批检查diff和相关验证后提交，最终保留未验收项清单，不自动发版。
