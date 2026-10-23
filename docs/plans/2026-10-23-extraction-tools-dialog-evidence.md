# Extraction 右工具与journal选择器剩余取证计划

本计划仅补既有12态的右support-tools到达/恢复与journal选择前Dialog原件；不改生产、不增业务夹具。按设计先行/实施计划工作流编写，依据固定 `5d08fd658ad7300d0478a2b5e117e88eb3c99f11` 的 `git show` 源码，不把Sol编辑中的工作树当冻结版本。

- 来源/工作分支：现有授权合批 `feature/task-first-ui`，基线5d08fd6；PR目标develop。新任务默认develop短分支规则保持，本次同Sol在现分支串行补证。
- 规则：[Git工作流](../git-workflow.md)。本规划不推送、不合并；实现后root检查工作树、统一验证、普通推送原PR，必需CI与视觉审分开，不绕门禁。
- 文件owner：仅同一Sol在完成plot/header改动后继续 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt` 与 `scripts/check_extraction_history_visual_migration.sh`。不改Overlay、Activity、NativeWindowFrameEvidence或现plot计划，不开第二实现owner。
- 协议/设备：只观察和原journal选择器既有点击，不点停止/开始/预热/取消预热/连接/路由按钮，不重发未知写入，不改真实设备、Service、样本、单调钟或恢复流程。
- 发布：不发版、不签名、不安装、不硬件操作。
- 回退：revert本测试/runner窄补；保留旧失败与原ZIP，不删未知记录/用户文件。
- 验证：原48dp、protocol2真实5秒与四零差、精确恢复保持。构建≠新CI≠Astra视觉通过。

## 先审旧证据：关闭一个已有格，不重复补

已清点本地各M5原ZIP目录项，643 PR与708 push均有主RUNNING浅1.3两工具reach和restored；本次选择较新643实际原件复核。根 `build/task-first-evidence/ci-643d828-m5-pr/artifact.zip`，SHA沿preservation为 `38b9776a621dffdea48fa919aab6c416ac2b070edc4dd9520355275a12f485bb`，source `4f990722c0181504b665ab1fe91ac60832619d18`。路径前缀 `raw-device/<source>/tablet-user-landscape-light-font-1.3/extraction/`。

Astra已逐张亲看 `states/extraction-reference-choice-reachable/window.png`、`states/extraction-overview-reachable/window.png`、`states/extraction-tools-restored/window.png`，并读各protocol及 `extraction-reachability.json`。两工具从初态部分露出到support-scrollY120，参考rect `[1868,1124,2169,1285]`、概览 `[2178,1124,2479,1232]`，实际高161/108px≥48dp；参考两行完整、概览一行完整。最后scroll精确回0，固定停止 `[1868,1303,2488,1429]` 不移，主值/图未被滚走。三个protocol2均accepted，JSON记录noToolClicks、modelAndSelectionUnchanged与exactScrollRestored。

**因此旧643主RUNNING浅1.3的这两个工具可达/恢复限定接受。** 前一16张特殊态review没有审这些主态图，其“16图不能关闭全工具”结论仍成立；此处增量关闭主态该格，不外推特殊态/其余配置。原主态取证保持，无需为这一个已存在缺口再另建新链。

12特殊态没有独立逐工具reach/restored。`journal_unavailable/.../extraction-journal-unavailable-choice/window.png` 已审，是关闭选择器后的Activity；基线函数确实先performItemClick再capture，所以无法补作Dialog视觉证据。

## 精确support目标合同

复用并窄扩现 `extractionReachability`，不要另建通用框架。主RUNNING原两目标合同不删；12态初态capture后、reference/journal交互前执行各自support链。声明以下有序目标，按固定fixture/模型条件校验实际visibility，不得“搜到几个就验几个”；额外或缺失Button均FAIL。

| 既有case | 必须取证的右工具按钮 |
|---|---|
| STARTING、STOP_REQUESTED、no_scale、stale_scale、historical_reference_toggle、missing_samples、water_reset、sample_gap、journal_unavailable | reference-choice、overview |
| UNKNOWN_live | choose-beans、reference-choice、overview、prepare、cancel-prepare |
| manual | reference-choice、overview、prepare、cancel-prepare |
| no_service_ready | connect-devices、choose-beans、reference-choice、overview、prepare、cancel-prepare |

此集合由5d08 render逻辑及643实际typography互核；choose-curve在这12个既有fixture均隐藏，须验证GONE，不能为“覆盖更多”新增不合法曲线fixture。未来fixture输入若变化导致模型期望变化，必须先说明差异并重审，不通过读实际按钮反推期望集合。

引用原Activity字段 `chooseBeans/referenceChoice/prepare/cancelPrepare/connectDevices/chooseCurve` 定位同一对象；overview是原referenceTools内唯一概览Button，可按原资源文案与父对象唯一定位，不改生产加tag。每个对象应为support-scroll后代且attached。禁用按钮仍应完整可见、48dp触控框、真实文字自然高；**不能沿用旧helper全局stop.isEnabled=true或所有工具enabled的断言**：manual的reference禁用、STOP_REQUESTED/UNKNOWN的stop禁用、manual/no_service的固定start禁用均是正常模型结果。保存并核实际模型推导enabled/visibility，滚动不得改变。

对每目标先记初态Layout，requestRectangleOnScreen整view，等待原committedFrame，用现capture采独立 `states/extraction-support-<key>-reachable/`。核完整screen矩形、实际Layout无ellipsis/行框裁切、按钮宽高≥48dp。deviceSummary及可见preparationSummary作为对应状态文字一并记录可见/自然高；如果被viewport裁切，追加其原对象全文reach，不能仅靠按钮可达断言文字可达，也不新造状态文本。

恢复合同：进入链时保存永久support/header scroll各自x/y与对象identity；finally恢复精确原值，再采 `states/extraction-support-restored/`。支持非零初始值，不硬写0；本次普通初态通常0。只移动support，不动plot/header；核原固定动作（实际可见start或stop）的rect、文字、enabled/visibility不变且完整。核Service身份、shotState/manual状态、trace/curve/points、referenceId/referenceCurveId/chart.reference、journal导出/失败状态和当前杯不变；未知写请求不变、不触发任何工具click。原对象集合、目标预期列表、初态/到达/恢复与各proof写入该case独立JSON，由runner要求完整列表。

工具链放在既有reference toggle之前，因此不必为on/off各再造一套工具目标：原toggle仍保有其选择/scroll恢复合同。plot计划新增header链必须先恢复，再执行support链；两条永久ScrollView分别记账，不能共享一个“scrollRestored=true”掩盖另一个未恢复。

## journal选择前Dialog原件

仅改已有 `extractionJournalUnavailable` 的时序，不新增journal故障fixture、不重复点击开启第二次。先保存support/header位置与当前杯/reference/journal失败状态；使原referenceChoice整框可达，调用原监听performClick。待唯一归属当前Activity且focused/attached/laidOut、无layoutRequested的真实AlertDialog出现。

复用M4已存在的只读SDK34 WindowInspector→decor.mWindow→Window.callback AlertDialog ownership检查方式，限定当前Activity Context链、decor/window/token一致；发现多窗口、错误owner或无法确认时FAIL，不能随意抓“非Activity那个窗口”。不修改生产持有Dialog字段，不新建测试替代Dialog。

**在任何list.performItemClick前**保存 `states/extraction-journal-unavailable-dialog/`：原整屏PNG、该Dialog真实Window原件/协议、geometry、完整原生TextView typography。使用已存在 `NativeWindowFrameEvidence.screenshot(window,decor,...)` 或已有经独立原点测量的 `screenshotMeasuredWindowBufferTranslation` 路径；取证geometry需实际Dialog window/decor/app mapping，不能用Activity截图或Activity geometry假称Dialog。沿原独立测量、5秒、双域四零差，不mask dim/阴影、不加容差；失败保原件停止，不为系统Dialog放宽门禁。

精确可见目标：标题 `R.string.reference_choose`、ListView唯一条目 `R.string.reference_off`、原负按钮 `android.R.id.button2` 对应取消。要求ListView count=1、唯一记录就是关闭参考，条目/取消触控≥48dp且完整，文字实际行框/颜色/font信息可查。不得伪造历史记录或添加“记录不可用”错误文案：当前产品Dialog只提供关闭参考+取消，本计划只证明现行为；若产品希望另外显示失败说明，另行设计，不能在取证修复夹带。

Dialog存在时核Activity仍原杯/原状态，journalResult仍failure、reference仍空；没有自动取新数据、无写设备。原唯一off条目按现逻辑performItemClick（只改reference选择，不是设备写入），等待Dialog消失与Activity重新focus，再保留原 `extraction-journal-unavailable-choice` 采图/断言。

finally安全关闭仅本次确认ownership的Dialog（如仍存在），恢复保存的support/header位置，采 `extraction-journal-unavailable-restored`；核无残留Dialog、精确scroll、当前杯/样本/journal失败/原空reference保持。异常仍由原Scope清理，保留原异常和cleanup附加异常，不吞错继续下一case。

## 同Sol串行步骤与门禁

1. Sol完成plot/header实现后冻结其diff；本计划只在该版本上追加测试/runner，不覆盖临时改动、不改已有plotplan。
2. 先完成上述状态目标列表、enabled/visibility oracle与support finally恢复，再接现12态；没有新增业务输入、没有动作按钮点击。
3. 插入journal选择前真实Dialog capture，保留原选择后图并补恢复；复用原Window/typography工具，只在M5测试增加窄helper，不动共享取帧层。
4. runner对每case精确target keys及reach/restored、journal dialog/choice/restored要求原PNG、实际Layout、独立协议2 accepted/5秒/四零差与恢复JSON。缺字段/缺原件直接FAIL，不用glob计数代替。
5. 同批双APK构建与原相关回归；root在既有M5四配置运行，不另触发业务场景或缩减原矩阵。Astra审新增原图：工具全文/48dp/固定动作、Dialog真实字体行框/颜色/唯一off条目/取消，再判限定通过。构建通过不授视觉PASS。

本计划不关闭旧5态plot不足；那由独立plot/header计划及其新图复核。也不外推当前5d08后新CI、硬件或全部24页。本次仅创建此文档，未改实现文件、未运行构建/CI。

## Sol 首轮实施（待静态复核、统一构建与原图）

仅扩原M5测试和runner，生产未改。12个既有特殊case在原初态及已恢复的header链后，按上表独立模型目标采support滚达、真实48dp、全文Layout和原protocol2；可见的device/preparation说明初态记录，若不完整则补原对象滚达；finally恢复原support x/y，并逐帧核Service、当前杯、chart、reference、journal、未知待写请求、固定动作及header位置不变。`chooseCurve`须GONE，禁用工具只取证不点击。原主RUNNING工具链未重写。

journal不可用case只点击原referenceChoice开启一次真实Dialog；在唯一off条目点击前，经SDK34 WindowInspector的Window callback/Context owner验证，用原 `screenshotMeasuredWindowBufferTranslation` 采Dialog独立整屏、geometry、全部原生TextView typography和5秒protocol2；核标题、唯一off条目、取消的全文和触控框。随后沿原off条目返回Activity，保留原choice图并加finally恢复图。异常沿原case失败，不改变业务输入。`bash -n`、内嵌Python语法编译、两文件`git diff --check`通过；未运行Kotlin/Android构建、CI或设备动作。静态与实图仍待核。

## Astra首轮静态审：一个确定阻断与两项合同缺口

实际已读最新实现/runner增量，SHA测试 `b9b0239856f2ccbb2cedfa61e8e1e56e948599c698c6f1c41ae1c8f0a26ea28a`、runner `3e13b531d59716898739b205c386cd575b57556c5c5bccdc3d14a1068af44af8`；生产仍820076de冻结。原plot/header逻辑未发现被撤销。尚不签通过，最小修正限现两文件：

1. **确定误失败：子visibility不能代表祖先有效显示。** helper初始/逐state及runner用Button.visibility==VISIBLE推导实际目标集合。正常STARTING/RUNNING把preparationTools父容器设GONE，但prepare/cancel子Button自身仍VISIBLE，TextView.show只换文本不改这个事实，因此期望2项会错误得到4项。用isShown（含祖先visibility、不要求屏幕clip内）作effectiveShown，保留子visibility及父容器visibility证据；原独立case目标集合不变。runner按effectiveShown核列表，同时核preparationTools状态；不要拿globalVisible/whole筛掉尚待滚达的目标。
2. **enabled oracle缺口。** 当前只保存初态enabled再比较不变，未核既定能力条件。补固定fixture预期：manual reference-choice必须禁用，其余已声明reference-choice可用；可见prepare/cancel在这批既有fixture均禁用（按5d08真实ready/temperature/manual/normalPreparation条件核，不增加新输入）；overview/choose-beans/connect-devices对应原允许入口保持可用。固定start/stop沿原模型已有检查保留。runner同步明确这些预期，不能仅“初态如何就接受如何”。
3. **journal对话框不变量漏了实际chart samples。** `unchanged`只核owner.points与chart.trace/reference，dialog sampleCount取保存points.size，若实际chart.points被改不会触发断言。补保存chart.points、owner.curve/manual、pendingStart/Stop及原固定可见action的identity/rect/text/enabled/visibility，在开Dialog前、每observe、off之后、restored核；保留现Service/referenceCurve/journal失败与导出不变。Dialog存在时不要求被遮Activity的whole，否则错误借owner可见性；只核原对象/layout字段，关闭恢复后再核完整可见。报告/runner带实际值及对应前后比较。

其余本轮已满足：Dialog唯一foreign Window、Context链/Window.callback归属、decor/token与真实Window采证；原off点击前已保存title/唯一off/cancel的真实Layout/typography/48dp与协议2；原off监听只调参考，Dialog消失后choice图与finally精确scroll恢复，清理异常附加原异常；support逐目标全文/48dp/固定action、模型/样本/reference/journal与header不动证据已有。上述认可不替代待修三项，也不代表Dialog真实运行已成功。

## Astra 三项修订窄复核：静态通过，待合批构建与原图

已直接读取冻结测试 `75f7f6bb09bd1a43c7b9e9274204c76514e42e1453e633be59c7bb4e94378821`、runner `f95c4d861ebf7b8cdafecf6fec941f5941a6b10f078eb5ea9570c6b89ecf8f73`，并核当前生产 render 条件，未只据实施报告签字。

三项已封闭：目标集合使用含祖先的 `isShown/effectiveShown`，保留 raw visibility/parent visibility/preparationTools 状态，runner 独立 case 列表未改成屏内筛选；enabled 按 manual reference、prepare/cancel 禁用及其他入口可用的固定 fixture oracle 核验，不仅保存初态；journal 新 `activityState` 纳入实际 chart.points、owner.curve/manual、pending IDs、fixed action identity/rect/text/enabled/visibility，进入前、Dialog 双端 observation、off 后与恢复均完整比较。模态时不要求背景 Activity whole，关闭后恢复 whole 要求。原 plot/header 和 48dp/真实 Window/5秒门禁未撤销。

本结论只解除上述三项静态阻断，可交 root 合批构建/后续原矩阵取证；未运行构建/CI，不签工具或 Dialog 视觉通过。后续仍需真实运行证明有效目标、全文、精确恢复与同次 Window 原图。

## Astra D8 两循环编译增量核验

独立读取当前 ExtractionHistoryVisualMigrationChecks.kt，SHA `ab806af1de7ff63bd411b6d22c77dee13ae16321d9d21ccfe1ed371331271a26`。只在内存将 `for ((key,view) in tools)` 逆换为原 `tools.forEach`，将 `for (index in textViews.indices)` 与紧随其后的解构行逆换为原 `textViews.forEachIndexed`，结果SHA精确等于已审 `5658b8e5d474ac009482f1844391623c8f3c5e7199408b0640d388aaddac5fa1`。

因此确认只有两循环头及第二循环局部解构变化，循环体、main捕获、committedFrame/capture/check/put、finally恢复与门禁逐字未变。固定List未在循环内修改，无需保留lambda特有的return语义；原静态通过结论保持。此为语义保持的编译候选，不证明D8具体指令根因或重构建已成功。未改源码、未构建/提交/推送，保留其他owner改动。

## Astra D8 state 函数值候选及精确增量核验

attempt-3表明两循环改写未解决D8。已读 `build/task-first-evidence/ui-5d08-followup-build/attempt-3-d8-diagnosis.md`：同精确D8单class可复现；仅去目标方法LVT可过、仅去行号仍失败，独立20/21/22参数lambda均过。因此21捕获数只是线索，不能再称其本身为根因；当前确证限该方法LVT与D8版本处理路径的交互，不采用删LVT构建预处理或升级依赖。

已审核并批准一次源码候选：局部 `fun state():JSONObject` 改为 `val state: () -> JSONObject`，唯一return改末表达式。原体无提前return/递归/函数身份依赖；捕获仍为原对象引用，创建函数值不执行观察或缓存JSON，所有state()调用时点、main与恢复不变。

独立读取新SHA `f6eec2063be6b5d3cbf3f3c46f4ef3fb20f955089b76034e57b45b3cab0d0a24`，在内存精确逆补上述两处，得到 `ab806af1de7ff63bd411b6d22c77dee13ae16321d9d21ccfe1ed371331271a26`。确认其余循环体、合同/门禁/finally逐字未变，静态语义通过；D8是否解决仍由root第四次完整构建判定，不以隔离诊断或hash核验代替成功。本审核未构建/提交/推送。
