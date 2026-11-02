# Scale 既有滚动证据两项补齐

来源 `feature/task-first-ui` 当前候选，PR目标 develop，合批构建/推送；不发布、不设备操作、不改真实协议。仅 owner 修改 `ScaleVisualMigrationChecks.kt`、`scripts/check_scale_visual_migration.sh`，不改生产/helper/15态清单。回退仅本两文件窄增量。

## A. 原六目标结束后的恢复原图

保留原 `supportReachability` 六目标/顺序/原 PNG。进入前同主线程记录 support ScrollView identity/x/y、Activity/Window/token、原读数/趋势buffer/本地timer/控制状态与业务不变量（复用既有 trendReturnState，不另建模型）；用同次 accepted 初态或一个明确 before capture 绑定原状态，不能采后拼接。

finally 恢复原 x/y，hardware commit 后独立 `scroll-restored/window.png`、geometry/proof/typography；同次 accepted target 包含恢复状态。精确比较原scroll/身份/buffer/timer，固定primary/footer rect不漂移，原六项业务门禁不变。只有恢复图与原件引用成功才 completed=true；原错保首位，恢复/写盘错suppressed。现仅restoredY=401不能冒充恢复原图。

summary仍v2 page_root相对路径+SHA，新增 before/restored 引用，不嵌整树或完整proof。runner读取原件核同次target、原5秒四零差、身份及状态全等；不要只信restored=true。

## B. 三个已存在特殊态的右栏全文

只在现有 `negative-offline`、`mock-capture-disabled`、`short-height` 初图之后、任何下一状态offer/clock推进/导航之前，补本态真实支持栏 disclosure；不新增业务状态，不改变原15态、主态数量或真实Mock输入。其他已审特殊态与原六目标不重复整套。

固定目标：timer说明、device-status完整文字、connect、settings、scale-trend-fallback；capture场景另含现有粉量采集说明。用现有实际tag/对象固定定位，若tag缺失可由同support祖先+原field身份定位并落明确semanticId；先从源码列出精确集合给审查，不用任意“所有TextView”动态数量代替。battery与timer两按钮已被主六目标覆盖，除非本态初图有新增文案/实际遮挡，不另造点击。

每个目标初图同次geometry若已经whole且Layout零省略，可引用该初图作为覆盖；否则调用真实 requestRectangleOnScreen 并commit，在该Activity和同一Window模式采图，再从同次accepted target核whole。如果文字View高于真实viewport，复用既有Legacy/M4的实际Layout逐行screen rect与clip算法，逐段滚动，覆盖全部原行/offset且零省略；不要求高于viewport的整个长View一次完整。按钮必须完整卡+完整文字+原48dp门槛，不能按文字逐行替代按钮完整。

short-height所有采图明确 `MEASURED_SHORT_ACTIVITY_WINDOW`；其余默认strict。这个补证不点击trend、不新开Dialog、不改文案/颜色/字号。短窗已有trend Dialog原流程继续在补证恢复后执行。

每个特殊态进入补证前保存其实际scroll/原业务状态；finally恢复同一原坐标并独立restored accepted图，核永久View/Window身份、读数/buffer/timer/固定primary/footer、原状态语义与业务不变量不变。负号/离线限制全文不可截尾。后续原special state断言照旧。

报告仅目标coverage、行范围、相对路径+SHA、恢复小状态；原PNG/geometry/proof完整单独保存。runner以三个case与精确目标白名单核全覆盖、同次target绑定、模式、四零差/5秒与restored完整，不扩到新状态。运行失败保部分证据与原错误，不继续假称补证完成。

## 验证

静态复核精确目标/行映射/恢复及runner → 唯一Gradle统一编译 → 原CI原状态取证。新增图由Astra验收，不以机械完整关闭全文视觉缺口。原643截图的限定接受与整run OOM FAIL分别保留。

## 首轮静态审：两处窄阻断

已审 Scale Kotlin `4b0a3f1d3f070f7f32a4f2323f70a3f77e774c9114fb6a1ded983042f432ab30`、runner `f511ce98da1588c0d02a0a2f11319aed644c2bfed4589bdff28921c7074e46d3` 实diff。

1. fallback必须处理真实互斥布局：ScaleTrendPanel.fit在inline时将fallback设GONE。643浅1.3三态都false不能外推1.0。固定semanticKey仍保留；同次initial target证明trendInline=true、原fallback确GONE时记录明确layout-not-applicable，不采隐藏目标。inline=false仍必须全文/完整按钮滚达，不得缺文件跳过。host核同次真实状态与分支，原5/6目标枚举保持。该修正防止把合法inline布局当缺失功能，不豁免其它隐藏目标。
2. 多段文字需固定原Layout分行offset：当前只核text和lineCount，随后按line index合并，尚不能排除同数量行换行边界变化。首个accepted stage保存有序(start,end)小基线，后续每stage严格相等；host核行数等count、首start0、末end完整UTF16长度、相邻end=nextstart及各stage全等。保留换行符原语义，不trim、不按字符大致范围冒称全文。小offset列表不重嵌geometry/proof。

其余本轮未见静态阻断：main六目标before/restored原图、support状态同次观察、业务/固定区域/身份恢复、三态不推进clock输入、标准/short协议选择、原始文件相对引用与finally首错保因符合范围。待上述两点收口hash复核，不提前签通过。

## 两处增量复核

Astra 已核 fallback 的同次 trendInline/visibility/identity 互斥分支，以及真实长文首0、末UTF16长度、相邻连续、逐stage固定lineOffsets和host逐项重验。两处阻断已收口，无新增静态阻断。原N/A只适用inline时GONE的那个固定fallback目标，不豁免其它目标；原whole/48dp/四零差/5秒与恢复检查保留。

核读版本：Scale Kotlin `0aa6b667cd7e46fa60fa04d05876fb777a5d101eddfbf170ebf9d41abe1b606b`；runner `728e8b23ee2eca8c105977fb7087c896d3f6b6afe1c09f8de4158f1c97db21e8`。该hash可作为静态通过候选；任何后续漂移须另报。独立bash-n通过；真实逐段/恢复图片与Android运行尚待统一构建及原CI，不提前关闭视觉缺证。
