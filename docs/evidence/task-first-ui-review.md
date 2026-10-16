# Task-first UI 实屏读图记录

## c3a5675：共享框架首轮

证据来自PR#4工作流`38727735636`的`task-frame-tablet-user-landscape`artifact。配置2560×1600、density360、字号1.0、简体中文、浅色；隔离Mock，未操作真实平板。每张均为实际Activity窗口，不是ImageGen图。

本地artifact：`build/task-first-evidence/ci-c3a5675-landscape/tablet-user-landscape/`（ignored）。`result.txt`有24页启动与`TASK_FRAME_CHECKS_PASSED pages=24 noBle=true`；当前主导航/工具路由、目标尺寸、焦点模拟检查已在该窗口实际运行，不证明其他窗口、键盘实机或全部业务。

| 实际截图 | 本次观察 | 处置/结论 |
|---|---|---|
| `screenshots/HomeActivity.png` | 左轨四目的地与底部秤/设置可见；尚未选配方时任务入口明确。主体仍是旧版横铺槽位和按钮，下部大空白；没有新准备工作台阶段预览 | 框架可继续；F3必须重排主体，不接受为全页改版完成 |
| `screenshots/ExtractionActivity.png` | 图占主要区域、左轨可见。实际图仍为压力＋g/s；主读数仍重量/流速，尚未落实四指标；右侧工具仍较拥挤。当前未选曲线，开始禁用 | F2/F3必须替换；此空态不代表RUNNING/STOP_REQUESTED/UNKNOWN已视觉验收 |
| `screenshots/CurveActivity.png` | 左轨选中曲线；连续列表、搜索、分类与旧版入口仍在。右侧尚未选择曲线为空白详情，按钮与分类占高，曲线预览尚弱 | F4继续任务化改版与真实曲线缩略图；本批不删功能 |

这些观察只支持框架阶段进展。其余21页内容、满配/无秤/断采样、深色/大字号、真实IME和控制状态仍需后续实屏验证。


## 56d0684：四指标原生图首轮

PR工作流38733280170三个窗口均实际成功：每窗24页Frame/导航检查＋机器三项/四项/断点参考/100dp字号1.3紧凑及恢复检查。下载路径`build/task-first-evidence/ci-56d0684-{landscape,portrait,phone}/`。原生组件PNG由Detached View直接draw，背景原先透明，在黑底查看器显黑；实际App截图是浅色白卡，不将透明PNG当深色UI证据。

实际查看：横屏`chart-components/four-metrics.png`，手机`gaps-and-reference.png`、`short-height-font-1.3.png`及`screenshots/ExtractionActivity.png`。四量纲坐标、真实分段和焦点参考已出现；**尚不接受为最终视觉通过**：

- 原生默认Button灰底偏重；手机4项图例呈3＋1不平衡，点选后应有更明确轻量选中态。
- 常驻3行提示挤压图，手机仅约100dp图线区，说明局部滚动出现半行，虽然可达且测试通过，观感与聚焦不佳。
- 萃取主读数仍是旧时间/压力/重量/流速，未落实时间＋有效出液或注水的层级，副工具仍占两行。F3必须重排，不能以组件green交付全页。

决定：widget明确语义背景、轻量描边图例；窄窗/竖屏最多两列完整自适应行；把hint放读屏和控件语义，常态可见说明压成“各项独立量程 · 本杯采样至…”，必要reference/gap/reset信息保留。随后主页面F3释放图高。短高说明是异常退化，不代替正常窗口完整图。

## 699fee0 工作台实际截图（2026-10-16）

Actions38835933986专项三窗口实际成功，取回`build/task-first-evidence/ci-699fee0-task-frame`。每窗result pages24、chart fixtures4、Home每个fontScale1.0/1.3各normal/180dp/recovery3fixture都有实际PASSED，且记录实际字号，不从脚本设定推断。

本轮人工实际读图：横屏Home/Extraction、手机Extraction、手机Home font1.3 short180；不是全矩阵视觉签收。Home已由参数/槽位堆叠改为主预览与右侧摘要；无选曲状态诚实空提示，主按钮选择曲线。短高保留完整“空间不足/阶段非时间预测/阶段1 9.0bar”文本，辅区仍有48dp可滚视口。Extraction横屏单图明显扩大、两主读数/pressure+rawtemp次级，右侧工具；手机单图及工具局部滚动都保留，主开始固定。

仍有缺口：此处框架截图为空采样准备态，不能证明活跃杯停止/未知状态或长语言；手机图例3指标仍2+1行，属于真实窄窗折行，尚非全图视觉定稿；整体仍需活跃/四指标/来源/缺口/2主题/完整页面流程验收。Home共享直接启动确认还未迁入，当前“准备萃取”导航诚实保留。原有single-screen/lifecycle全项仍在运行，不能以专项成功声称总体通过。

顺带读了56d0684横屏CurveActivity/BeanInventoryActivity/CurveEditorActivity/HistoryActivity：曲线库顶部多排工具占高且711dp竖屏仍硬双栏；曲线编辑仅两字段+巨大留白/强制分组，未实现参数表邻接预览。原库存/历史空态不是已完成集合改版证据。后续须继续原21页，不以共享壳算这些页验收完成。

## 2026-10-16 inline forms and exact CI failure follow-up

- Local worktree based on `95a90ce`: BeanEntry/CurveEditor now keep short fields in bounded inline cards, fixed actions, native inline IME, <=680dp readable form width. Curve editor has validated current stage preview on eligible large windows. Invalid raw drafts clear the preview. No save/control semantics changed.
- Root finished interrupted implementer work, fixed FrameLayout.LayoutParams compilation, added actual inline focus/caret/IME checks and per-group scrolling visibility. Existing BrewReview modal/long-text checks remain. Independent inline-form spec/quality review is still pending; this is not visual acceptance.
- `/tmp/task-inline-build.log`: testMockUnitTest + assembleMock + assembleMockAndroidTest BUILD SUCCESSFUL, 295 tests / 0 failures / 0 errors / 1 existing skip. Later per-group/check-source changes require incremental test APK compilation and native CI.
- Exact remote `699fee0` single-screen run `38835933987`: phone succeeded; tablet jobs failed. Downloaded tablet-user-portrait evidence shows all page geometry/language captures and six list restoration paths passed before ScaleConnection test selected all ScrollViews with single(). Navigation rail introduces a second valid ScrollView. Test now resolves actual candidateScroll and asserts it owns rows; swipe/recreation/navigation/source safety checks stay unchanged. This does not prove the other tablet failures share the same cause.
- Exact `699fee0` lifecycle failure was a real compact AppSettings tools-button clipping issue after scrolling. TaskPageFrame now hosts tools outside scrolling settings headers, with an actual scroll-to-bottom visibility assertion. New fix still needs native CI.
- Final incremental validation after per-group inline visibility and exact scale viewport fixes: `/tmp/task-inline-final-test-build.log`, assembleMockAndroidTest BUILD SUCCESSFUL in 24s. Native execution and independent reviews remain pending; no new CI run or device acceptance is claimed.

## 2026-10-16 curve import workspace

CurveImport now has explicit Paste/File source row above native inline raw editor and one fixed Preview primary action. Original clipboard-on-click, SAF, UTF-8/size/decode, preview and save separation unchanged. Explicit raw text, focus, selection and local text scroll restore added under the original saved-text size guard. Advanced business checks now exercise inline focus and raw/caret recreation without curve-store writes; all original 17 paths and invalid/conflict/idempotency assertions retained.

Local `/tmp/task-import-build.log`: testMockUnitTest + assembleMock + assembleMockAndroidTest BUILD SUCCESSFUL in44s, 295 tests / 0 failures / 0 errors / 1existing skip. This import batch is not included in running d86a599 CI; independent review/native execution/visual evidence pending. No hardware acceptance claim.
