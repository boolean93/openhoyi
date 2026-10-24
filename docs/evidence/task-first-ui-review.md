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

## 2026-10-16 d86a599 native task-frame review

Run 38838413476 completed success on phone/user tablet portrait/user tablet landscape. Downloaded landscape artifact to build/task-first-evidence/ci-d86a599-task-frame. Root visually inspected actual CurveEditorActivity/BeanEntryActivity screenshots and font1.3 curve-library normal fixture. Inline controls, fixed save and adjacent stage preview are visible; bean entry is centered and width-constrained. Repeated group/field names create unnecessary visual repetition. Changed group names to Basic information/Extraction targets and Bean information/Stock entry/Optional dates in eight locales, retaining precise labels/units. This follow-up needs new screenshots; initial screenshots are not blanket visual acceptance.

Historical ShotChartView migrated to the shared four-metric native renderer, keeping the existing API. Native legend tests now target actual buttons, independent units/ranges, 4 traces, scale absence and physical axis text across original languages/themes/widths; scientific ordering probe measures the actual plot hierarchy. HistoryDetail checks actual usable inner plot, not only wrapper height. `/tmp/task-history-chart-build.log` full JVM + two Mock APK build passed. Native execution of this later history change is pending; the successful d86a599 run does not cover it.
- Also inspected d86a599 two-cup-font-1.3/water.png: shared ml axis 0–60, shared elapsed seconds, A solid/B dashed, reset/gap segments remain disconnected; 45ml isolated sample stays a point. This is deterministic component evidence, not a populated two-cup page/hardware claim. Actual font1.3 log confirms 9 two-cup fixtures and 3 curve-library fixtures passed.
- Historical flow measurements remain available as actual elapsed-time/ machine ml/s / optional scale-derived g/s in full-details advanced table; raw scale absence remains —. No old flow integration or control changes.
- Final historical source validation: `/tmp/task-history-final-build.log` JVM + Mock/APK tests build succeeded in43s; final exact-range AndroidTest update `/tmp/task-history-ranges-build.log` succeeded in20s. All 16 changed form/history locale XML files parse and git diff --check passes. Historical native runtime and independent review remain pending.

## 2026-10-16 d86a599 full-run outcomes and date fix

- Exact d86a599: single-screen run38838413546 success across six profiles; lifecycle38838413486 success; upgrade38838413491 success; verify38838413488 success; task-frame38838413476 success. These close the prior settings tools clipping and scale viewport-test failure at this source.
- Advanced run38838413516 failed only pages job; business job succeeded. Downloaded advanced-mock-pages-pages/wideFont.txt reports BrewComparison selected date2023-11-1422:13:20 ellipsized. compact.txt proves240 fixtures/8languages/2themes passed. No blanket advanced success claim.
- Fix uses full-width stacked cup selectors below600dp actual selector width; stable widgets, IDs, callbacks remain. Selected date opts into unrestricted line wrapping through beanChoiceAdapter selectedMaxLines, default stays2 for other callers. Full dropdown names unchanged. Original no-ellipsis/geometry/native selection checks remain.
- Local final date-fix validation `/tmp/task-comparison-date-build.log`: testMockUnitTest + assembleMock + assembleMockAndroidTest BUILD SUCCESSFUL in49s; native rerun still pending.

## 2026-10-16 compact inventory display

BeanLedger replaces repeated padded cards with flat neutral rows: kind/change/after-balance lead, bean/batch metadata follows, full actual adjustment reason wraps when present. Reverse source order, batch filter, list scroll persistence and inventory reads unchanged; no timestamps inferred. BeanBatch render now uses primary balance/name and supporting initial quantity/batch/three actual dates, retaining fixed Full details/Ledger/Adjust actions and unabridged details. adjustment() and all preview/recheck/UUID/commit/recovery code unchanged.

Local `/tmp/task-inventory-layout-build.log` assembleMock + assembleMockAndroidTest succeeded. Read-only layout edit does not add mirror unit tests; existing business invariants and actual populated native page/list restoration checks remain. This change is later than running33ab403 CI and is not claimed covered by it. Native screenshot and independent review remain pending.

## 2026-10-16 scale workspace layout

ScaleActivity now uses stable TaskWorkspace regions: readout/tare primary, App timer/connection/settings in local supporting scroll; freshness/battery/activity guard remain fixed above and dose capture below. Capture intent makes capture sole primary-colored action, normal scale retains primary tare. Status/help increased to14sp. ScaleOwnerActivity, renderScale, stableDose, service/tare callbacks and timer/state logic unchanged.

Local `/tmp/task-scale-workspace-build.log`: JVM + both Mock APKs BUILD SUCCESSFUL in43s, 295 tests/0failures/0errors/1existing skip. Scale UI change and inventory d8a9e75 are not in running33ab403 CI. Actual scale/capture screenshots, full native gates and independent review still required; no physical scale command or device install performed.

## 2026-10-16 history detail workspace

HistoryDetail chart now owns TaskWorkspace primary; actual stop reason, full journal fields and full-details/advanced-flow entry use supporting local scroll. Curve/status/date/time/raw-end-scale and UNKNOWN warning remain fixed above, review/compare below. Compact primary budget reservation limits auxiliary allocation without hiding its48dp access. Dates/status text raised to14sp. Original sample loads, journal reads and intents unchanged; no record writes. Exact populated inner-plot native requirement remains96dp.

Local `/tmp/task-history-workspace-build.log`: testMockUnitTest + assembleMock + assembleMockAndroidTest BUILD SUCCESSFUL in43s, 295tests/0failures/0errors/1existing skip. This later layout is not covered by running33ab403. Native screenshots and independent review pending.

## 2026-10-16 curve delivery preview workspace

Shared profileCard (Share/ImportPreview/explicit preview dialog) now uses bounded primary stage/name/capability area plus full parameter support. It shows actual temperature/target/all stages alongside or beneath the pressure schematic; raw-flow sources retain read-only warning without a fake graph. Full name/JSON details remain. Existing share/SAF/import/conflict callbacks unchanged. Caption uses14sp.

Local `/tmp/task-curve-delivery-build.log`: JVM + two Mock APKs BUILD SUCCESSFUL in45s. Actual short-window minimum plot, localized full parameters, dialog and page screenshots, plus independent review remain pending. This batch is later than running33ab403 and has not been pushed yet.

## 2026-10-16 scale polling/layout observation race

Exact33ab403 advanced run38839838096: business job succeeded; wideFont passed Chinese comparison and progressed through Thai, then Arabic ScaleActivity failed missing battery TextView.layout. Artifacts at build/task-first-evidence/ci-33ab403-advanced-pages. Previous date ellipsis is no longer the failure. Test awaited settled text in one main callback, then checked in a second;250ms Scale render repeatedly reassigned unchanged text between callbacks. Root changed Scale string assignments to setIfChanged and made initial/each scroll/restored geometry checks atomically observe readiness and assert in one bounded main-thread poll. No clipping assertion removed, no exception catch/skip. Actual changing telemetry and original gates unchanged.

Local `/tmp/task-scale-stable-frame-build.log`: JVM + two Mock APK builds successful. Native rerun still required.33ab403 lifecycle/single-screen were confirmed running after this fix, so no cancellation/restart and no latest-source native success claimed.

## 2026-10-16 inline review workspace

BrewReview now uses TaskReviewSections: stable bean/dose/free-text grind region and three multiline note regions. Actual>=840/min-width windows split parameters left/notes right; compact stacks bounded parameter/notes scroll areas. Fields stay attached across resize; note viewports adapt to space while preserving text size and16000 filter. Original previous-bean union/identity, raw drafts, save trim/dose validation/journal edit and inventory nonmutation remain. Added region scroll/focus/caret restoration and explicit error jump.

Chinese native form checks resolve each actual labeled field, scroll it into view, test long top/end and 16000 native filter via accessibility, edit/caret0/end and clear/refocus preservation; fixed actions/IME assertions retained. Generic short edit uses numeric input for dose. Old modal expectations only apply to non-inline fields, not the new layout. No screenshot/IME success claimed before native execution.

Local `/tmp/task-review-inline-build.log` JVM+bothMockAPKs BUILD SUCCESSFUL in47s; final accessibility-refocus instrumentation compilation `/tmp/task-review-inline-test-build.log` succeeded. Current9d457ef CI does not include this review batch. Independent review/native screenshots remain pending.

## 2026-10-16 tare fixture initialization

Exact9d457ef PR lifecycle38841222177 failed at ServiceStandaloneTareDispatchChecks before NOT_READY device mutation: expected WRITING/calls1 but logs show immediate UNKNOWN. TraceStore.prepare and durable marker arm can race to create the same unique fixture folder; this candidate is supported by code, not directly proven by the existing log. Synchronously create the owned directory before starting TraceStore, retain all10 dispatch/4 persistence modes and add pre-mutation state/call/marker diagnostics. Production gates/persistence/queue untouched. Same-source push lifecycle38841217380 separately succeeded, consistent with an intermittent fixture issue but not proof of root cause.

Local `/tmp/task-tare-fixture-build.log` JVM plus two APKs BUILD SUCCESSFUL in23s. Native rerun and independent review remain pending; no threshold relaxed.

## 2026-10-16 9d457ef advanced/native visual evidence

Advanced38841222253 succeeded: compact and wideFont each240 fixtures/8languages/2themes/15pages; business job also succeeded. Exact screenshot sources under build/task-first-evidence/ci-9d457ef-advanced and ci-9d457ef-task-frame. Root viewed wideFont/zh-Hans-light-CurveShareActivity.png: stage graph left, actual complete parameters right, main share action distinct. Viewed wideFont/zh-Hans-light-ScaleConnectionActivity.png: empty state honest, but old scanning/manager buttons take two full rows. Viewed task-frame-tablet-user-portrait/ScaleActivity.png: large excess blank reading card, timer/tools locally scroll; **visual polish remains**, despite geometry passing. Same-profile HistoryDetailActivity.png is missing-record state only and cannot prove populated graph acceptance.

## 2026-10-16 scale candidate list

Scan/manager use paired tool row. Candidate remains native Button, now flat/start-aligned with full17sp bold name and14sp scaled address/RSSI/candidate-specific read-only notice. Native ripple/min64dp; no name truncation. Original service, radio checks, guarded callbacks, list order and saved scroll unchanged; status/scan text only update when changed. Existing40 synthetic candidates must remain disabled with same owner/no scan/no connection across recreation/navigation.

Local `/tmp/task-scale-list-build.log` JVM+twoAPK BUILD SUCCESSFUL in45s. This and inline-review9e13664 are newer than9d457ef CI; native screenshots/independent review pending. No hardware install or control.

## 2026-10-16 scale portrait workspace correction

Based on9d457ef actual tablet-user-portrait screenshot: TaskWorkspace now has an opt-in compact primary height budget; Scale requests320dp including readout/tare. Its support scroll uses the remaining height and natural content instead of160dp cap, placing routine tools directly beneath the readout rather than below a large blank card. Other charts/pages keep default expanding primary behavior; two-pane behavior unchanged. Setter requests layout and restoring null restores default weighted allocation. No field recreation/control changes.

SingleScreenPageChecks adds a real attached-window check for Chinese600–839dp-wide tall portrait<=1.3font: reset timer, connect and settings buttons must be fully visible with text fitting without local scrolling. Existing all-target/text checks remain. Local `/tmp/task-scale-portrait-final-build.log` JVM+twoAPK BUILD SUCCESSFUL in44s. Exact new native/visual evidence pending; runningff1ef65 does not include this correction.

## 2026-10-16 legacy curve read-only collection

LegacyCurve now uses stable TaskCollection list/detail regions. Wide displays both; compact opens selected metadata with native return and Android33 back callback. Flat selected rows retain full names/category/source, details preserve existing metadata/unknown values and actual source index. No graph/control entry added. Saved selection uses index+SHA256 of source rawJson, validated against newly loaded data; changed import clears selection. Original SAF/import/parser/async generation guards and list-state owner remain. Removed obsolete520dp empty card width that would overflow a narrow split pane.

Native list-state test now opens inline metadata, recreates with same content, captures legacy-curve-selected-restored.png, returns through actual compact button (wide retains list), then checks original list position. Original preference/read-only/noBle contracts retained. Local `/tmp/task-legacy-curve-build.log` JVM+bothAPKs succeeded45s; final screenshot instrumentation compilation `/tmp/task-legacy-curve-evidence-build.log` succeeded21s. Actual native execution and independent review pending.

Current ff1ef65 single-screen run38842891488 tablet-user-landscape job116786769461 failed before tests: Android Emulator package installation reported `Error on ZipFile unknown archive`, emulator/ADB unavailable, no screenshot artifacts. Raw job log `/tmp/task-ff1ef65-landscape-job.log`; other windows still running, no cancellation/restart. Must rerun the terminal failed job when GitHub permits, not classify as UI assertion failure. ff1ef65 verify/lint, upgrade and task-frame passed; lifecycle/advanced/remaining window results still pending at this entry.

## 2026-10-16 legacy history presentation

LegacyHistory list uses flat64dp-min rows with17sp full names and14sp actual date/duration. Source order, ID navigation, source return, async-generation guards and scroll persistence unchanged. LegacyHistoryDetail now gives the existing two-series chart TaskWorkspace primary and complete summary/source/missing-field disclaimer a bounded support scroll; removed three-line ellipsis from those texts. Full-details action remains. Read failure uses existing failure message instead of missing-record message. No new series, old wFlow/wTrend reinterpretation, storage write or execution entry.

Local `/tmp/task-legacy-history-build.log` JVM + two Mock APKs BUILD SUCCESSFUL in44s. New source is not covered by runningff1ef65 CI; actual populated screenshots, short-height/localized geometry and independent review pending. Legacy chart axis/font/gap behavior still needs full design audit; this layout batch is not an assertion that the whole legacy chart is complete.

## 2026-10-16 review long-editor viewport coordinates

ff1ef65 single-screen phone/tablet-landscape/tablet-large-font failed only Chinese light/dark BrewReview at keyboard-time longEditor visibility, after long top/end succeeded (334/336 page checks). These are separate from tablet-user-landscape SDK archive failure. Artifact paths: build/task-first-evidence/ci-ff1ef65-single-screen/*/single-screen-window-checks/chinese-form-states. Root viewed phone section-1-long-end.png: long grind draft at end and fixed Save visible before IME.

Android34 local SDK View.java:8099 requestRectangleOnScreen requires content coordinates and subtracts child scroll offsets during ascent; getDrawingRect:17862 includes scroll offsets. Test previously passed0,0,width,height after EditText itself scrolled to the end, thereby asking the enclosing region for the text origin rather than editor viewport. All four requests now use getDrawingRect, retaining full geometry/IME avoidance/local scrolling/draft/filter/accessibility assertions. Added inline-ime-viewport screenshot before validation. No production editor behavior or threshold changed. Local `/tmp/task-review-coordinate-build.log` JVM+twoAPKs successful24s; new native run remains required.

ff1ef65 lifecycle38842891557 and advanced38842891517 now terminal SUCCESS, including original tare persistence fixtures and inline-review business paths. This is evidence for ff1ef65 only, not the subsequent local batches or full UI completion.

## 2026-10-16 native history date hierarchy

HistoryActivity adds the real local-calendar date at the first record of each contiguous day, preserving source order, adapter record count/positions/IDs and source-specific saved list states. Flat rows replace card gaps; status/meta now14sp, original name/time/duration/raw-scale/slot/taste content and three-source tools unchanged. No synthetic date rows or data writes. This fulfills the date-list portion only; adaptive inline detail pane remains outstanding.

Local `/tmp/task-history-date-build.log` JVM+bothMockAPKs BUILD SUCCESSFUL in44s. Native screenshots/list restoration and independent review pending; precedingff1ef65 CI does not cover this batch.

## 2026-10-16 inventory collection and explicit preparation entry

BeanInventory now uses compact continuous native batch rows plus adaptive detail pane: name/balance/ordinal/initial quantity/three dates, original batch adjustment page and scoped ledger, explicit preparation entry. Global add/all-ledger retained; scoped ledger/back labels localized in8 languages. Stable batch ID/detail-request/list scroll survive recreation; deferred one-shot scroll restore waits until hidden list actually has visible layout. Inventory still owns no BLE/session. BeanPreparation intent seeds only draftBatchId; original active preparation branches and all save/consume/recovery code unchanged.

Advanced business flow now actually clicks inventory batch then preparation, confirms correct batch and unchanged preparation/balance/events before original invalid/save/consume/restore paths. List-state check recreates selected details, compares ID+7 metadata strings, captures inventory-selected-restored.png, returns through native back and verifies original list position. No original safety/inventory assertion removed.

Local final `/tmp/task-inventory-entry-build.log`: testMockUnitTest+assembleMock+assembleMockAndroidTest BUILD SUCCESSFUL45s,295tests/0failures/0errors/1existing skip. Earlier compile passes45/46/41s are development evidence only. Exact new native geometry/business/recreation/screenshots and independent review remain pending; running2773b29 does not include inventory batch.

## 2026-10-16 scale portrait actual correction image

Root viewed exact2773b29 task-frame38844288879 tablet-user-portrait/screenshots/ScaleActivity.png under build/task-first-evidence/ci-2773b29-task-frame-portrait. Compared9d457ef screenshot: reading card no longer expands to push tools below the local fold; timer start/reset, connect and settings all fully visible beneath readout/tare. Mock/no-readout and unavailable-battery labels remain honest. This closes the observed normal portrait fold issue visually, not full language/max-font/capture/hardware acceptance. Six-window native suite38844288915 still running at this entry.

## 2026-10-16 explicit landscape-only scope change

User explicitly cancelled portrait compatibility for ROI. Spec§9 and roadmap now supersede their prior portrait delivery criteria: target user tablet landscape, keep full21 pages/business/safety and landscape fonts/IME/short-height. Existing portrait fallback code retained but not a new optimization/gate; no orientation lock imposed.

Default single-screen matrix now user-landscape/general-landscape/large-font-landscape; task-frame defaults user-landscape while retaining its internal1.0/1.3font and short component checks. Advanced/lifecycle page matrices now landscape1.0 + wideFont1.3; advanced business uses landscape, no assertion/path dropped. Background resize remains a landscape-to-landscape configuration transition. Capture script removes only its portrait/phone tail after existing landscape light/dark/extraction flows. Old compact instrumentation/manual profiles remain available, not default release criteria.

bash -n passed for5 affected/relevant scripts;7 embedded Python blocks parsed with ast; `/tmp/task-landscape-scope-build.log` bothMockAPKs BUILD SUCCESSFUL22s. No production protocol/schema/manifest/signature/version changes. Exact updated CI remains pending. Prior2773b29 run still active; observed landscape failures remain in-scope and must be diagnosed separately, not waived by this user change.


## 2026-10-16 922752c 用户平板横屏实图抽查

实际读取CI38846239450的2560×1600图片：inventory-selected-restored.png选中列表行与完整批次信息相符，三日期、批次/全库存两种流水及选豆入口可见；列表首行位于已滚动位置，不是丢失标题。zh-Hans-light-ExtractionActivity.png为未选择曲线/未开始状态，主图、实时压力温度/原始秤读数和禁用启动可见，不算运行中或硬件验收。zh-Hans-light-HistoryDetailActivity.png为31真实fixture点/30s，压力/累计水量/原始秤读数/温度同图；右侧个人记录、固定复盘与对比可见，四量程不混单位。图片均在build/task-first-evidence/ci-922752c-user-landscape下。此抽查不覆盖尚未原生运行的50784e5用豆新布局和7dbb2f9历史双栏，也不补全所有21页状态视觉验收。

三个横屏的instrumentation与result均336场景/7列表14恢复通过；原工作流后置截图计数失败，明确两张新增恢复截图单独分类后，在三份实际产物上精确校验成功。922752c四项必需CI与advanced/task-frame/Capture全成功。849e1c1已普通推送新原生CI，仍待结果。
