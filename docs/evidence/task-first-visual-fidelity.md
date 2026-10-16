# 设计稿与原生实现一致性审计

2026-10-16。用户明确要求开发与生成图保持一致，包含字体和间距。当前结论：**未通过视觉一致性验收**。21页概念覆盖、原生窗口几何通过，不等于本项完成。

## 对照基线

- 设计：docs/research/task-first-ui-concepts/index.md中各页当前版本；萃取为extraction-four-series-v3.png。
- 设备：用户平板等效2560×1600/density360，中文、浅色、fontScale1.0优先；对齐应用内容区域，系统栏另计。
- 实屏：922752c/CI38846239450的用户横屏截图。已读取库存选中恢复、未开始萃取、有数据历史详情。运行中萃取与生成稿尚没有同状态成对证据，不能直接作像素验收。
- 本地后续历史/用豆布局尚未有新运行截图；849e1c1原生CI进行中。

## 首轮可证实差异

| 项目 | 设计/规格 | 当前代码或实际证据 | 结论/下一步 |
|---|---|---|---|
| 实时主值字号 | 规格40–48sp，生成稿显著突出时间与液重 | ExtractionActivity主读数26sp | 明确偏离；用统一主值token修复，再验证主图/停止不被挤占 |
| 字体基线 | 生成图未标明font family/file与字重 | HoyiUi按钮sans-serif-medium，标签使用默认字体；无bundled字体 | 不能证明一致；补可复现字体基线，包含中文fallback与数字 |
| 通用卡片圆角 | 规格12–16dp | HoyiUi.card为18dp，其他shape默认16dp、部分12dp | 明确参数漂移；统一容器语义token，非所有控件一刀切 |
| 通用卡片/按钮间距 | 规格8dp体系 | card内20/18dp、上间距14dp；button上间距10dp | 未统一；分清内边距/组间距/相关控件间距并收敛 |
| 图例形态 | 萃取稿紧凑线型+标签 | 实屏是带边框按钮，另加独立量程范围 | 交互/单位说明应保留，需按设计稿收敛视觉外观，不能直接删信息 |
| 主操作位置 | 萃取稿右栏底部停止 | 当前统一底部跨内容区主动作 | 存在结构差异；核对运行中状态与安全要求后形成明确修复方案 |
| 库存列表/详情 | 已认可双栏，选中批次与用于本杯动作 | 922752c实际恢复截图能对应选中项及批次 | 结构已接近；字体/间距仍需量化，不能标整页完成 |

## 完成证据要求

每页保留：当前认可图、精确源码、同状态/数据/主题/窗口截图、控件字体与布局值、差异记录。不能用调整测试阈值或生成一张更像当前代码的图消除未经说明的偏差。保持原21页/3设置功能及横屏范围，先修核心页、再传播共用tokens。当前全表均不构成完整验收。

## 首批修正候选

新增TaskVisualTokens作为共用卡片/按钮与实时主值参数源：卡片padding/radius/groupGap16dp、相关gap8dp、button16sp/48或56dp目标/12dp圆角；实时值40sp/粗体/tnum并移除浅绿底板。标签显式sans-serif/medium；未解决生成图缺精确字体文件基线的问题，不能标字体一致。代码已本地构建通过46s，297/0/0/1；未有新截图，表中差异仅进入“已改代码、待视觉验证”，不关闭验收。

## be3a538 局部实屏复核

实读CI38848732852用户平板task-frame/screenshots/ExtractionActivity.png：40sp主秤读数与去除浅绿底板确已生效，主图/压力/温度/禁用主动作均可见；这是未选曲线空态，不能验证运行态停止布局或同稿同状态一致。图例边框与辅助工具仍比原稿视觉更重，保留未关闭；固定字体b7aadf0尚未推送/没有这张图中的证据。新增TaskTypographyEvidence容器测量已编译22s，未运行。

## 历史集合的第二轮结构修正候选

be3a538标准横屏和大字号single-screen通过，用户平板336页面通过但列表恢复13/14：选中历史图434/1402未满足原40%门槛。实际history-list-v3为右侧全宽曲线，嵌套TaskWorkspace既偏离设计又在可用宽度不足时堆叠支持区。新候选去除此嵌套，将豆子/感受摘要置于图下，详情/复盘/对比/全屏入口合并为底部操作栏；长摘要明确省略，完整字段仍保留在原详情对话框。其他视觉差异（主读数层级、图例样式、每页间距）未因此关闭。固定字体、原生帧提交等待和新历史结构需新CI截图，未验收。

## 运行态实屏对照

下载并实读be3a538/CI38848727282的extraction-running-dark.png（1600×1000，旧系统字体，非用户平板同状态配对）：主时间/秤读数40sp可见，四指标同图；右栏只余参考/概览/禁用预热而隐藏曲线名和设备状态，停止跨整页。与认可v3右栏本杯上下文及底部停止位置有明确差异。新本地候选恢复只读曲线/连接上下文；edd3db5减轻图例边框；二者都未获得新原生截图。停止位置、配方字段和精确字号/间距仍未通过，不能把这张深色运行截图与浅色概念稿直接算像素一致。

## 固定字体原生首轮证据

693f215/CI38850951237 task-frame成功。下载用户平板横屏24份*-typography.json与截图，实读ExtractionActivity：density2.25/fontScale1.0，主值textSize90px=40sp、weight700、lineHeight130px、includeFontPadding=false。原生主题与300/400/500/700字体检查通过，证明固定字体接入生效；不是ImageGen原字体身份的证明。该页为未选曲线空态，图例仍旧版（edd3db5本地未推），右栏按钮过重及操作位置仍偏离，其他23页不能仅凭JSON存在当视觉验收。单屏三横屏CI38850951515仍运行。

## 用户平板693f215单屏证据

已下载CI38850951515用户横屏产物并实读collection-details/history-selected-restored.png：历史曲线31点、四系列、完整右栏图和图下摘要/操作均可见，恢复14/14通过，之前读取中旧帧问题本场景不再出现。并不证明21页视觉完成；该截图的粗大工具按钮、紧凑主数值与原稿仍有差距。

同一报告332/336：中文浅深色Extraction图高540/1391低于原40%门槛，BrewReview长输入框360px仅150px可见。固定字体的运行指标通过不等于字体增大后的空间分配正确。保留原门槛，待修真实布局，不撤回字体或删除测试。新主操作右对齐候选仅本地构建通过，不当这些失败已修复。

## 0cd05cc 萃取空态实屏

CI38852903580 task-frame成功，已下载并实读用户平板ExtractionActivity.png：主读数与压力/温度同一行，图例不再有常驻边框，开始按钮对齐右栏底部，原40sp主值保持。此图仍为空态，不证明运行态停止/有采样一致性。右栏按钮阴影和禁用绿底仍存在（cf80e57仅本地）；主页额外font padding和主操作对齐候选亦未入该截图。三横屏单屏仍运行，不将task-frame成功等同全量完成。

## 0cd05cc 运行态实屏

CI38852898167 Capture成功，实读extraction-running-dark.png（1600×1000）：6秒/4.20g主值、压力温度同一行、四指标共图、当前曲线和设备连接上下文已显示，停止固定右下。属于真实隔离Mock运行过程，不是硬件效果证明。已兑现上述结构修正；右栏仍缺原稿完整配方/粉量字段，禁用预热工具占位突出（共用禁用中性色修正还在本地），同状态浅色/用户平板配对仍缺，不能标整页完成。

## e6eedb8 复盘原生对照与下一批候选

实读用户平板single-screen的zh-Hans-light-BrewReviewActivity及研磨IME截图，与brew-review-v3对照：底部查看记录/保存各占半屏，而原稿查看记录位于左参数区、保存为右下有限宽操作；代码候选已调整两入口位置，业务不变，双APK37秒通过，未有候选截图。原稿顶部记录摘要、分区标题和跳转选择器仍有差异，不能标整页一致。

同源原生表单报告定位短输入框IME裁切：用户平板153px可见150px，大字体98px可见77px，两组均334/336。按钮与输入额外字体留白候选已本地提交但未推送；不能把编译通过当作裁切修复。高级页面报告landscape与wideFont各240fixtures通过，证明相应布局/状态断言通过，不证明与生成稿一致。

## 曲线库集合层级候选

实读e6eedb8用户平板CurveActivity空选择截图，对照curve-library-v2：原生每个普通行都有描边卡片、缩略图统一绿底，选中层级不明确。候选改为轻量分隔列表、缩略图中性底、仅选中行强调；不改连续滚动和阶段数据。双APK40秒通过，但缺候选原生图，不关闭曲线库验收，右侧空态不能拿来冒充原稿已选状态。

## e34f8cd 用户平板按钮实测闭环

已下载并实读CI38856140908的task-frame用户平板截图及HomeActivity-typography.json。同一density2.25/fontScale1.0、同空选曲线状态，浏览曲线库按钮从e6eedb8的150px降至108px（48dp）；字形仍36px=16sp、weight500、lineHeight52px，includeFontPadding由true改false，padding由[0,23,0,23]变[36,18,36,18]。证明按钮留白候选在原生生效，并非靠缩字。首页其他间距/任务层级和就绪态仍未整页验收。

同组BrewReviewActivity.png实际为缺失记录页：TaskFrameChecks只传Activity、不传SHOT_ID，这是导航框架/缺失态检查，不能用于复盘表单对齐或IME证明。e34f8cd单屏与高级矩阵仍在跑，应使用它们已有命名记录fixture的截图，不将24张空态截图计作24页正常态验收。

旧e6eedb8 single-screen最终失败：用户/大字体各334/336，复盘短输入框裁切；标准横屏instrumentation达到1199.999秒超时（真实进程已终态）。它不是全部页面断言通过，也不是仅观察工具超时。保留失败证据；新e34f8cd已有独立同范围任务，不另行重复启动旧源码。

## e34f8cd 配方摘要与原生回归

CI38856140871 Capture成功，实读extraction-running-dark.png：运行态右栏已展示请求温度92°C、无重量目标的真实含义、缺失豆款/记录粉量—，单图四指标与右下停止仍可见。与机器当前93°C区分，不冒称请求温度已生效。该图1600×1000深色，尚非用户平板浅色认可稿同条件配对。

CI38856146902生命周期通过；language-instrumentation明确LANGUAGE_ACTIVE_CUP_CHECKS_PASSED（8语言、同杯、保留样本）及SERVICE_SHOT_DISPATCH_CHECKS_PASSED（39fixtures、隔离GATT）。本批加入的配方trace绑定/显示断言已随这些路径执行；不当硬件证据。

高级CI38856146892：landscape240fixtures通过，wideFont在BeanInventoryActivity信息图标ⓘ横向溢出，business通过。与single-screen大字体128页相同回归，0a5131d已对48dp图标控件单独修padding；尚未原生复验。下一批还含复盘本杯摘要、曲线轻量列表和有限宽主操作，不能把旧截图归给新代码。最新JVM14秒298/0/0/1及双APK39秒通过。

## 7d9c1a6 曲线列表原生复核

CI38857204220 task-frame成功，实读用户平板CurveActivity.png：普通行透明底/细分隔与缩略图中性底已生效。仍为空选择状态，不能证明右栏主操作及预览对齐；筛选区域占高、精确间距仍有差距。该源未包含d8c184a压力蓝与刻度，截图仍显示旧绿线，不能混淆版本。

下一批490c3c4含阶段物理刻度、固定/滚动操作分离可达验证，以及中文正常态PNG+测量配对。最新完整Gradle命令6秒成功（全部up-to-date，沿用已有298/0/0/1及双APK结果）；不是新原生结果。整体21页+3设置验收仍未关闭。

## 7d9c1a6 已选曲线详情复核

CI38857197854 Capture成功，实读curve-detail-dark.png：选择采集曲线2后，右下使用此曲线已有限宽，左侧选中行有强调底与描边；不再是普通行卡片和整栏主按钮。该源缺压力阶段，原生正确显示缺参数说明，不能将其当有阶段图的认可稿配对。整体标题/工具区与筛选密度仍有差距。

准备页下一主要缺口已静态确认：HomeActivity仅显示usageSummary（当前曲线统计），没有认可brew-preparation-v2图下的可选最近配方列表。CurveUsageLedger已有lastUsedAtMs，CurveOrdering已有RECENT排序，不需新数据源。后续须实现真实最近项，保留无记录/存储失败区别；点击仅选择配方，不绕过原启动确认或更改正在萃取的本杯；短高保留可达入口，不能让新列表挤没阶段图和安全区。

## 9744053 正常态大字体原生复核

实读ci-9744053-large/tablet-large-font/single-screen-window-checks/visual-baselines中zh-Hans-light-BrewReviewActivity与CurveActivity的window.png。曲线库已选采集曲线1，显示蓝色9.0/6.5bar阶段目标、0/3/6/9/12刻度、非时间阶段横轴、右下有限宽使用按钮；不把阶段目标当实时轨迹。筛选控件仍有额外留白，df0aaed候选尚未在该截图生效。

复盘正常态有真实fixture上下文，查看记录在左参数区、保存固定右下已生效；但对照brew-review-v3，仍缺本杯参数/我的记录分区标题，跳转Spinner抢占顶部整行，研磨框过高，摘要缺少原稿的数字层级。本图fontScale1.3，只作结构核对，不冒称标准字体像素一致。上述缺口仍待实施和同条件验证，21页+3设置验收未完成。

9744053 single-screen工作流38857880081最终success，用户平板横屏、标准横屏、大字体三job均success。该结果不覆盖后续ba1abdb或本地复盘分区候选；本地候选新增20sp分区标题、240dp右对齐跳转控件、52dp研磨编辑视口（仍多行可滚动），双Mock APK39秒成功，尚待原生截图与长文本IME复验。

## ba1abdb 首页最近配方原生证据

CI38859504685 task-frame成功，已下载并实读用户平板screenshots/HomeActivity.png及home-font-1.0/recent-wide.png。完整窗口为空记录状态，显示最近使用空态；recent-wide为960×720dp组件绘图，有三条真实ledger fixture，顺序采集3/2/1，不冒充实际窗口截图。home-1.0.txt和home-1.3.txt均HOME_WORKBENCH_CHECKS_PASSED fixtures=4 shortHeight=true recovery=true recentSelection=true readOnly=true noBle=true；证明两字号选择/短高可达/恢复断言通过，不是硬件证据或整页视觉一致。

对照brew-preparation-v2，右栏四个白底描边工具按钮仍太重、豆款/粉量层级不完整。后续先收敛工具入口视觉，不删功能。复盘390a7ae与曲线标题3b4bf73尚在独立CI运行/排队，不使用ba1abdb旧图给它们验收。

## ba1abdb 生命周期分项结果

38859504790整体失败；下载audio-instrumentation.txt明确五组音频检查通过，包括LOCAL_FEEDBACK_EXIT_CHECKS_PASSED previewDisabled=true previewPageExit=true extractionPageExit=true noBackfill=true，确认onPause候选通过原导航静音断言。后续errors.txt定位LanguageChartChecks.kt:26/56：阶段图仍查mobile_accent像素，生产CurveStageView已用chart_pressure，导致No rendered series pixels。只更新该原生检查预期为压力蓝，保留真实Canvas采样、8语言/两主题、左右上升20dp断言；修正后的整体原生结果待新CI，不把分项成功当整体成功。

## 3b4bf73 曲线标题同排原生复核

CI38860026487 task-frame成功，实读curve-library-font-1.0/normal.png（360×640dp组件绘制，不是用户横屏实窗）：返回/标题/操作同排、蓝色压力阶段及完整单位说明保留；curve-library-1.0.txt与1.3.txt均CURVE_LIBRARY_VIEWPORT_CHECKS_PASSED fixtures=3 shortHeight=true recovery=true readOnly=true noBle=true。证明该组件字号/短高/恢复通过，不证明所有语言长名和认可稿整页一致。390a7ae Capture38859825828成功，但此工作流没有正常复盘表单图，仍等待single-screen的visual-baselines，不能用缺失记录页替代。

库存页9744053大字体正常态与bean-inventory-v2对照：现有余量和正文层级弱、普通行白底明显、顶部新增/日期/流水结构仍有差距。本地候选只调整24sp名称、14sp余量标签、40sp/700余量主值、8/16dp留白、普通行透明；双Mock APK42秒成功，未有原生截图。原稿没有真实数据来源的流水时间不得照抄。

## 390a7ae 复盘正常态与表单结果

工作流38859831914标准横屏job成功，已下载single-screen-tablet-landscape：result.txt checked=336 pages=21 fixtures=336，中文浅色复盘completed=true screenshots=17 sections=5 longStates=8 inlineObserved=true imeObserved=true noSave=true。实读正常态window.png确认本杯参数/我的记录标题、52dp研磨视口、左侧查看记录和右下保存；没有用缺失记录页替代。跳转选择器仍占整行留白，后续候选移至笔记标题右侧，尚未原生验证。用户平板/大字体任务仍在跑，标准横屏成功不覆盖其他窗口。

## d20ee63 首页次级入口实屏

CI38860435640 task-frame成功，已下载并实读用户平板screenshots/HomeActivity.png：四个次级入口已为左对齐强调色文字，白底卡片去除；主选择按钮仍固定右下。实屏是未选曲线/无用豆状态，不能证明就绪态本杯信息与原稿一致，也不能关闭首页验收。

390a7ae标准横屏HistoryActivity正常态截图及typography.json：标题17sp/lineHeight31px占62px，状态14sp/lineHeight25px占51px，均includeFontPadding=true。后续候选保持字号和字段，显式500/400字重并去font padding，普通行透明；双Mock APK40秒成功。候选原生行高/滚动恢复尚待验证，不能把几何通过当视觉完成。

## 电子秤工具页差异核对

实读390a7ae标准横屏ScaleActivity正常态与scale-v2：当前无读数—，不能与稿中18g/稳定/电量82%视为同状态；结构上主重量300细体、大白卡、横向计时标题和值偏离稿件。候选改700重量、透明主读数区、20sp计时标题+40sp/700数字上下排、16/8dp间距；双Mock APK40秒成功，尚无候选原生图。最近10秒真实趋势、完整设备区与有限宽去皮仍是缺口，不能因本批字体修正关闭电子秤验收。

## 旧回归闭环与新趋势候选边界

2026-10-16查询确认d20ee63 lifecycle38860435519 completed/success，390a7ae single-screen38859831914 completed/success；阶段颜色预期/音频离页问题与旧复盘三横屏任务已通过各自检查。它们不覆盖后续cc81e35标题同行、库存/历史字体或新增秤趋势。

新秤趋势具备模型、真实通知接线、10秒/g轴Canvas、断点/空态/年龄摘要与短高详情入口，304项JVM无失败、双APK最终39秒成功；尚无候选原生结果/有数据实屏和短高入口证据，不能关闭电子秤页视觉验收。

## ce0a39d 用户平板秤页与审查修复候选

读取GitHub确认38862941104 task-frame、38862941116 lifecycle、38862941059 upgrade及38862935882 Capture已成功；38862940986 single-screen仍in_progress。下载task-frame用户平板产物并实读ScaleActivity.png（2560×1600）：透明主区、粗体重量、上下排计时、最近10秒空态图已出现。它是Mock无读数态，不能与scale-v2的18g实时稳定态当作相同状态。仍有当前重量标签、有限宽去皮按钮、右侧设备信息层级及按钮视觉差异，不能关闭秤页验收。

独立审查确认一项P2：趋势详情缺少连接/过期/来源文案。候选已把页面当前状态传入弹窗并实时更新，保留历史读数年龄。新增组件测试打开详情、只读来源切换为断连、旧来源不残留及关闭；新增服务fake GATT通知进入缓存检查。首次沙箱构建因共享Gradle锁权限未启动，授权缓存后完整JVM/双APK47秒通过，304 tests/0 failures/0 errors/1既有skip。新增原生检查尚未运行；不能声称真实ScaleActivity短高可达或服务切秤/断连生命周期已验证。

## 电子秤分区与去皮宽度候选

对照ce0a39d用户平板实屏与scale-v2，新增当前重量16sp标题与16dp下间距，去皮改居中且最大240dp（仍按实际宽度测量换行高度）；右侧计时后24dp间隔建立20sp设备标题，电量/状态/连接入口归入该组。没有虚构BOOKOO型号或82%电量。8语言新增标签，真实操作门禁和单BLE owner未改。双Mock APK43秒构建成功，diff检查通过；当前候选还没有原生截图，不声称字体/间距最终一致或短高已验收。上一批4ecc593的single-screen38864418212、task-frame38864418258、lifecycle38864418227查询均in_progress。

## 7285e2f 原生平板复核

task-frame38864826234成功，下载用户平板产物并实读ScaleActivity.png，当前重量、居中有限宽去皮及设备组已在2560×1600实屏出现。home-1.0.txt/1.3.txt均包含SCALE_TREND_PANEL_CHECKS_PASSED fixtures=4 shortEntry=true recovery=true empty=true dialogStatus=true dialogDismiss=true componentOnly=true。实读font1.3趋势正常fixture：负值保留、两段间未连线、g/秒轴与最新读数年龄清晰。这是独立组件，不替代完整ScaleActivity短高窗口证据。4ecc593 single-screen38864418212与lifecycle38864418227均已成功。

7285e2f lifecycle38864826313失败日志是SDK下载Android Emulator时报Error on ZipFile unknown archive，模拟器未启动且检查脚本未执行；未修改业务或放松门禁，仅重跑failed job，查询attempt2已in_progress。7285e2f single-screen38864826144仍in_progress。

实屏仍见去皮结果在左侧远离按钮、次级入口重边框。后续候选将结果置于按钮下方居中并计入自然高度，清零/连接/设置使用文字按钮及原48dp点击区、禁用与焦点反馈。字体/间距整体一致性仍未验收。

## 曲线库三项参数预览候选

对照curve-library-v2，先前温度/目标液重/阶段数只有完整参数弹窗可见，主预览缺少三列摘要。候选增加14sp标签、24sp/700数值、16dp上下间隔，直接读取已有三类配方模型；零目标显示既有“不使用”，缺失显示破折号，不推测未知参数。短高模式把三项完整摘要纳入现有滚动fallback，仍保留全参数入口与原启动门禁。8语言标签补齐。双Mock APK45秒成功；现有原生组件检查新增正常态三列文本完整性与短高摘要保留断言，尚待执行和实际截图，不能宣称已对齐整页。

## aac1155 电子秤实屏与库存正常态复核

已下载并实读task-frame38866128495用户平板ScaleActivity.png：去皮结果居中位于按钮下方；清零、连接、设置无卡片边框，主计时与去皮可区分。当前是无设备Mock态，不能用—g证明18g正常态或硬件读数。整体状态重复、设备真实型号缺口及图表有数据整页仍需核对；不关闭本页验收。

为避免用库存空态替代正常态，从已成功single-screen38864418212下载single-screen-tablet-user-landscape，并实读visual-baselines/zh-Hans-light-BeanInventoryActivity/window.png：24sp豆名、40sp/700余量、普通列表透明已落地；底部仍有全局新增/流水两个宽按钮，详情的批次/流水/选豆按钮占两整排。与bean-inventory-v2的顶部维护工具和有限宽主操作不一致。下一批应整理全局新增/流水到工具区，限定选豆主操作宽度，同时保留所有入口、批次选择和滚动恢复。此截图仅普通字号中文浅色正常态，不覆盖全部语言/主题/大字号。

bf2ed46曲线三项摘要的task-frame38866479646和single-screen38866479455当前queued，native38866474145与lifecycle38866474104 in_progress。候选尚无实际截图，不能用aac1155旧图替代。

## 曲线摘要原生证据与库存操作候选

bf2ed46 task-frame38866479646和single-screen38866479455均completed/success。下载task-frame用户平板产物，实读curve-library-font-1.3/normal.png（360×640dp组件，非整页横屏）：91°C/27.00g/2阶段完整，阶段图与说明仍可见；curve-library-1.3.txt三fixture短高/恢复通过。组件标题窄宽换行不能当作完整横屏标题布局验收。7285e2f lifecycle38864826313 attempt2 completed/success，之前SDK压缩包失败闭环。

库存候选把全局新增/流水移到上方文字工具区、批次详情/流水改文字入口，选豆主操作独立限宽dock，空库存新增也限宽。只改BeanInventoryActivity，没有触及库存写入和共享beanPage。双Mock APK37秒成功、diff检查通过；新布局实际窗口/大字号/列表恢复待CI，不用旧截图判定新布局通过。

## 实时萃取核心信息核对

实读4ecc593用户平板单屏基线是待机/未选曲线，不能与extraction-four-series-v3运行态比较。另实读390a7ae Capture的extraction-running-dark.png，确认旧运行态具有同图四指标、时间/秤读数主值及固定红色停止，但顶部辅助读数仅压力和温度，缺少累计注水。该旧运行图不是当前候选证据；还有禁用预热工具等视觉差距，不能关闭核心页验收。

候选补机器累计注水辅助读数（只有主值显示秤重时出现），与无秤主注水共用经过LiveTelemetry过滤的同一数值及预浸等待规则；压力/注水/温度辅助字号提升至18sp/500，主数值40sp/700不变。没有把未知秤重改称已验证出液，缺失/过期仍为—，不动停止/报警/采样。新运行态实屏及大字号尚待验证。

## 字体内存失败定位

1c256c8 push task-frame38869781029实际失败：HomeActivity采集TaskTypographyEvidence时，Font.Builder.createBuffer分配17,772,319字节失败；堆上限201,326,592字节，剩余约2.4MiB。此时未进入库存检查，不能归为库存几何失败，也不能当基础设施下载问题重跑掩盖。代码核对发现主题四字重family之外，TaskTypography又按字重加载独立XML，每份指向同一17MB字体。

候选API28+复用主题family，以精确weight创建Typeface并保留缓存；API26–27原精确字体资源路径保留。没有换字体、删字重检查、扩大堆或降低截图分辨率。既有TaskTypographyEvidence继续检查主题字形宽度、400默认及300/400/500/700实际字重、粗体700；实际内存和字形回归仍须新CI证明。

## 693e696 用户平板实屏证据

已确认task-frame38870140486 completed/success，下载用户平板产物并实读ExtractionActivity与BeanInventoryActivity：18sp压力/累计注水/温度位于主读数旁，待机无累计数据正确为—；空库存新增已为右下有限宽按钮。截图分别是未选曲线待机和空库存，不能覆盖运行态图表空间或库存正常态工具栏/列表恢复。未把同版本通过泛化为b349502字体修复通过：后者task-frame38870529947和single-screen38870529964查询仍queued，Capture38870524533和lifecycle38870524546 in_progress。

## 运行态准备工具收敛候选

旧运行态截图中，禁用的预热/取消预热仍占据右侧两枚按钮。本批仅在active+normalPreparation+两按钮禁用+无BREW_WAIT恢复记录时收起整组；非active自动恢复。normalPreparation已有新鲜设置、无控制安全消息、准备IDLE和到温/咖啡馆模式约束，其他等待/失败/未知状态不被本条件隐藏。没有改变按钮原启用判定、点击处理、固定状态及停止。尚待运行态实屏和异常状态回归，不能将代码条件视为原生验收。

本轮再次核实b349502 task-frame38870529947与Capture38870524533、库存正常态single-screen38869788055及萃取Capture38870134243均in_progress，未因等待重新启动任务。

## 复盘结果摘要对照

实读4ecc593用户平板中文浅色正常态BrewReviewActivity/window.png与brew-review-v3：参数/笔记两区和标题同行选择器已生效，但结果顶栏仍一行14sp文本，不符合稿中独立时长/秤读数数值层级。候选保留原elapsedMs/weightHundredthsGram格式与观察状态，改24sp/700双数值、14sp标签及独立状态列，UNKNOWN警告未动。豆款、研磨、三块笔记、保存与恢复未改。没有将此正常空笔记截图视为与稿件相同数据态；候选仍需多语言/大字号/IME和新实屏证明。

## 字体修复24页与正常/运行态证据

b349502 task-frame38870529947 completed/success，产物result.txt为TASK_FRAME_CHECKS_PASSED pages=24 noBle=true：此前OOM路径已走完。和693e696同窗口24份typography.json按view path比对，weight/textSizePx/lineHeightPx/includeFontPadding零差异。407个同文本节点的绝对坐标/内边距存在315项差异；Home/Curve主要统一上移11px，不能把它们直接认定为字体宽度变化或声称像素完全一致。需要按实际内容区归一核对；不扩大为低内存全部场景或API26–27验收。

1c256c8 single-screen38869788055 completed/success，实读用户平板正常库存截图：上方全局新增/流水文字入口、批次文字入口、右下限宽选豆主操作已出现；result.txt checked=336 pages=21，list-state-result.txt库存recreate及navigation-detail-return均scrollY=626，列表恢复通过。仍缺原稿日期/流水摘要层级，不关闭整页验收。

693e696 Capture38870134243 completed/success，实读extraction-running-dark.png：6s/4.20g主值、6.0bar/3.6ml/93.00°C辅助读数均出现，四系列同图和红色停止保留。此为1600×1000深色Mock运行态，不能证明用户2560×1600或1.3字体同场景；尚不包含7b03ebe收起预热工具的改动。

进一步定位11px偏移：HomeActivity的root/0实际系统顶部padding在693e696为74px，在b349502为63px，底部内容边界均1465px；内容首层top同步74→63。这能解释统一上移，不应通过改应用padding抵消系统差异。字体缓存修复不改变应用间距token；其他非统一边界差异仍需逐页按内容区核对。

## 复盘字段标签候选

按同一brew-review-v3，复盘字段标签从15sp灰色改16sp/500正文色，豆款标签也统一并通过labelFor关联原选择框。beanInput强调选项默认false，只在复盘启用；其他页字段样式、输入/保存/草稿和视口策略未改。首次编译因局部记录id遮蔽Spinner.id失败，明确this.id后重建；未放松任何检查。新字段样式和2fc59a5结果摘要仍待实际正常/长内容/大字号/IME截图，不因旧CI绿色标记完成。

## 历史详情与导入预览：整页构图审计

2026-10-16，工作树HEAD 500d5d6。实读1c256c8 single-screen38869788055产物中用户平板2560×1600、中文浅色的HistoryDetailActivity/window.png和CurveImportPreviewActivity/window.png，并分别对照history-detail-v2.png、curve-import-preview-v1.png。此轮是视觉审计，不是新实现验收；源码确认相关布局仍为下述结构。

| 页面 | 已观察差距 | 实施与验收要求 |
| --- | --- | --- |
| 历史详情 | 时长/结束秤读数仍为15sp单行；图表横贯页面；豆款和感受在图下；三个等宽操作占满底部。认可稿是结果双数值、左图右记录，编辑复盘为主操作，对比为顶部次操作。 | 按整页构图调整，不能只增大两项字号就宣称对齐。右栏展示已有豆款、粉量、研磨、感受、下次调整；缺失值明确留空态。保留完整信息入口和未知状态告警。 |
| 导入预览 | 当前是无冲突态：全宽卡片、左图右长参数、底部全宽保存。认可稿是冲突态：左侧参数/阶段预览、右侧冲突说明与决策。 | 先补同标识不同参数的冲突fixture和截图，再验决策区。无冲突态仍需整理参数摘要、完整参数入口、限宽主操作；不得为了贴稿强行显示冲突。 |

历史源码边界：renderHistoryDetail由HistoryActivity集合详情与HistoryDetailActivity共用。全屏详情的右记录栏不能直接套入已有左右集合布局，否则形成三栏并挤压图表。实施须区分全屏与嵌入上下文，保持同一记录和采样读取、异步结果归属检查、对比和复盘参数不变。长笔记不能挤压图表或遮住操作；横屏短高/1.3字体必须用真实窗口验证，不新增竖屏要求。

导入源码边界：CurveImportPreviewActivity已经实现existing预览、保留本地、saveCopy及写入时再次发现冲突后render。当前无冲突截图不能证明这些入口缺失，也不能证明冲突态布局通过。finishedSave、保存失败恢复按钮和importDocument冲突处理必须保留；此次审计没有改存储行为。

设计依据仍采用研究稿的Google canonical layouts与Apple Layout/Buttons/Charts：相关记录就近分组、次操作降级、保留图表中心地位。生成图中的示例数据和曲线形状不覆盖真实采样；仅比较相同业务状态下的信息结构与字体/间距。系统栏需从应用内容区分离后比较。上述两页均保持未验收，完整21页+3设置范围不变。

历史详情构图候选已实施：仅全屏路径启用TaskWorkspaceLayout，双结果40sp/700、14sp标签；六个笔记字段在右侧局部滚动区分组展示，对比为右区顶部文字操作，完整信息为下方文字入口，编辑复盘固定限宽dock。列表内详情保留原路径，未知告警和采样加载不变。最终双Mock APK23秒成功，diff检查通过；尚无本候选运行截图，不能认定字重/间距和可用图高已与认可稿一致。

## 状态截图覆盖补缺

500d5d6 task-frame38872022602 completed/success，已下载并实读用户平板BrewReviewActivity.png：实际为“找不到这杯记录”的缺失记录态。24页框架检查通过不能证明复盘双数值或字段标签；继续等待正常fixture的single-screen/advanced证据。2fc59a5 native38871320546、lifecycle38871320525、upgrade38871320508和task-frame38871320557均成功；这些也不替代500d5d6正常复盘的实屏。418fec6新历史构图single-screen38873060543和task-frame38873060561查询时queued。

源码确认AdvancedBusinessUiChecks已有冲突保留/副本及重复导入幂等断言，但不输出冲突截图。本候选在已有流程中新增review-saved-notes、import-new、import-conflict三张实际窗口截图，采集前验证窗口和对应按钮，脚本校验PNG完整性及1600×1000尺寸。未改变17路径、库存事件、文档或设备断言。测试APK24秒构建成功，bash -n和diff检查通过；尚未运行本候选原生流程，不将截图代码存在当作截图已产生。范围是中文浅色标准字号CI横屏，不等同用户平板或字体1.3验收。

## 7b03ebe运行态实屏复核

Capture38870992324 completed/success，已下载并实读ci-7b03ebe-capture/mock-ui-screenshots/extraction-running-dark.png（1600×1000）。时间6s、秤读数4.20g、压力6.0bar、累计注水3.6ml、机器温度93.00°C均出现；四指标同图、独立量程和右下固定红色停止仍在。正常运行态禁用的预热/取消预热组确实已消失，这一项从代码候选推进到实屏证据。

仍未对齐项：右侧“选择历史参考/萃取概览”仍为两块边框按钮，图例的名称+单位+量程较密；需要与认可稿同主题/同内容区继续校准。没有把此Mock正常态推及未知、断连、预热恢复、用户2560×1600或字号1.3。复盘正常态single-screen38871320553此时仍in_progress，历史构图single-screen38873060543仍queued；没有重启或取消这些任务。

## 导入预览构图候选

仅CurveImportPreviewActivity从共享profileCard改为任务双区：左侧24sp标题，14sp/24sp三项参数摘要和原CurveStageView；右侧冲突文本、本地预览及保留本地；保存/副本独立TaskActionDock限宽。无冲突态不显示警告，兼容性说明明确导入不自动选曲线/启动。未修改分享页、profilePreviewDialog、finishedSave、保存失败按钮恢复、importDocument冲突重绘和saveCopy逻辑。

双Mock APK40秒成功，diff检查通过；实际布局、冲突行为、字号1.3仍待精确提交CI。认可稿内嵌阶段参数表尚有缺口，目前完整参数在原详情入口，不能把此候选视为整页一致。224cec0新增的业务截图检查将提供两种导入状态证据，不得用无冲突截图证明冲突态。

## 复盘大字号正常态与键盘实屏

38871320553整个工作流尚in_progress，但tablet-large-font和tablet-landscape子任务均completed/success；用户平板子任务仍in_progress。已下载large-font产物，result.txt为checked=336 pages=21，中文浅色复盘表单状态completed=true screenshots=17 sections=5 longStates=8 inlineObserved=true imeObserved=true noSave=true。实读正常态window.png及section-1-long-inline-ime.png：30s/36g独立结果层级已显示；1.3字体下普通页面各字段/保存完整；键盘态研磨末尾编辑位置和保存按钮可见。

同brew-review-v3对照仍有差距：页标题与结果摘要分两行占高，查看记录仍为边框按钮，底部“仅保存复盘，不改变库存”说明未常显；这一提交尚不含500d5d6的16sp/500字段标签，不能拿旧图验新标签。键盘态顶部摘要未收紧，使正文可视区偏小，属于下一项体验改进，不因几何检查通过关闭。普通态fixture为空笔记，不等同认可稿的填充数据。此证据只覆盖2fc59a5标准CI横屏1600×1000大字号，不推及用户平板或全部21页一致。

历史构图418fec6 task-frame38873060561 completed/success；single-screen38873060543已in_progress，仍需带记录截图确认全屏左右构图和图高。e98d7c7 single-screen38874021152及task-frame38874021083查询时queued。本轮仅审读证据，没有新增样式提交触发更多任务。

## 新完成失败任务分诊

500d5d6 Capture38872022621明确在sdkmanager安装system-images;android-34;default;x86_64时“ZipFile unknown archive”失败，模拟器尚未启动；后续5554连接拒绝是清理失败。已对终止任务执行failed-only重跑，尚未证明成功，未修改应用或检查。

693e696 single-screen38870140692仅tablet-large-font失败。已实读产物instrumentation.txt：336个页面已完成，列表恢复13/14；ScaleConnectionActivity recreate scrollY=352通过，进入HomeActivity设备管理后，在detailReturn等待detail.isDestroyed && activity.hasWindowFocus处超时。无OutOfMemory/应用断言越界证据，日志中的ColorBuffer警告不能单独证明根因。较新2fc59a5大字号同矩阵通过，说明不是每次出现，仍不能以此关闭旧失败。

候选仅给detailReturn原等待失败添加主线程状态诊断（详情/父页destroyed、finishing、focus、task及父页attached），保留等待时间、断言与恢复逻辑，不盲目重跑693e696或延长超时。等待后续证据定位；此项尚未修复。

## 导入阶段参数表候选

在e98d7c7左右构图基础上补压力配方内嵌三列表：阶段、压力bar、本段水量ml，直接使用文档阶段值，14sp文本与8dp横向/4dp纵向单元间距。通过已有workspace测量回调实测标题、指标、说明、详情入口及表格高度，剩余图表预算至少160dp才显示；短高收起表格但完整参数入口不移除。原始流量文档不绘制压力表，保存/冲突逻辑未改。

八语言表头XML解析和键检查通过；首次资源脚本误列仓库不存在的values-de路径，已按实际zh/en/ja/ko/ru/th/ar/es集合补全，没有新建不支持语言。双Mock APK43秒成功，diff检查通过。当前仅为候选，实际表格可见性、字号1.3、短高和冲突状态截图未验收，不能声明与概念稿已一致。

## 418fec6历史构图用户平板实屏与大字号失败

single-screen38873060543已终止：用户平板、普通横屏成功，大字号失败。下载并实读用户平板中文浅色HistoryDetailActivity/window.png，result.txt为336窗口/21页、列表恢复14/14。40sp结果双值、左图右记录、右下固定编辑和文字对比已生效。仍明显未对齐：右栏空字段逐项分行占高，次级标题/摘要占用较多纵向空间，图表绘图区偏矮；应压缩元数据与简短字段，而不是缩小图表文字或删完整记录。此fixture为空笔记，不证明长笔记同稿一致。

大字号产物显示335/336页面通过、列表恢复14/14（包括ScaleConnectionActivity设备管理返回，本次scrollY=346）。失败实际位于zh-Hans-light-HomeActivity的LanguageDetailChecks.kt:109：注入manualSafetyResource后，warning.isShown且文本等于预期的断言失败，不是历史详情图高断言。当前失败消息没有区分隐藏还是文字不符，根因未定。HomeActivity实际提示优先级为ShotSafetyAlert→manualSafetyMessage→machineWriteSafetyMessage；必须检查实际显示值/绑定服务/状态，不可删掉安全提示或放松断言。已查看的Home截图是普通空态，不能当失败瞬间的提示证据。未重跑该任务掩盖失败。

e98d7c7 advanced38874021113、single-screen38874021152查询仍in_progress；本轮未新增应用样式，也未将成功子任务扩大为整个工作流成功。

进一步实读失败产物chinese-safety-states/zh-Hans-light-HomeActivity-warning-2.png：红色“无法清除萃取安全记录；请检查机器并重试。”完整显示，符合第三项注入资源。故截图时提示正确，不能证明随后的断言时状态仍一致；不能据截图直接归因于布局。新增断言失败上下文以区分可见性、文字、服务绑定和状态覆盖，原条件/注入/恢复不变。根因和修复仍未完成。

## 历史详情信息密度候选

根据418fec6用户平板截图，全屏元数据从曲线独行+状态/日期行改为14sp曲线/日期摘要与状态同行；完整文本仍由原详情入口提供。豆款、粉量、研磨改14sp标签/16sp值双列，允许自然换行，不省略长值；后三项笔记维持完整文本与局部滚动，标题16sp强调。图表字体/采样/量程未改，列表内嵌路径和UNKNOWN固定告警未改。

双Mock APK23秒成功（/tmp/task-history-density-build.log），diff检查通过；实际图高增益、长字段、大字号和间距仍需新截图，不把节省空间的意图当测量结果。

e98d7c7 advanced38874021113的business子任务已completed/success，pages仍in_progress。下载产物并实读business/import-conflict.png：94°C/41.7g/4阶段摘要、左图右冲突选择、右下限宽另存副本均出现；business-flows.txt确认17路径、3库存事件、3新草稿和完整字段恢复。此截图为1600×1000中文浅色，尚不含fb209f8内嵌表；左下系统剪贴板浮层遮住部分导航，不作为无浮层像素验收或用户平板验收。冲突标题、次操作和阶段颜色仍与认可稿有差距，不关闭导入页视觉范围。

## 已保存复盘状态与长研磨字段

实读同一e98d7c7业务产物review-saved-notes.png：新版16sp/500字段标签已显示，长豆名换行，三块已保存笔记内容与固定保存可见。此前500d5d6只有空态的标签验证缺口，在1600×1000中文浅色标准字号这一范围得到补充；不是用户平板或全部字体状态验收。

发现长研磨字段出现半行：BrewReviewActivity.field给非数字字段设minLines=2，但TaskReviewSections.fitEditors对panels[1]强制min(52dp,budget)且至少48dp，未按实际lineHeight+上下padding计算。下一修改应在参数区预算足够时按两行实测高度显示，短高保持可编辑/滚动与固定保存，不能简单扩大所有笔记框。当前保存/重建完整字段测试通过，故问题是可见行高，不据此宣称文本丢失。

fb209f8完整SHA查询：task-frame38875187723、upgrade38875187771成功；advanced38875194305、task-frame38875194422仍in_progress，single-screen38875194439queued。框架默认无文档态不能证明内嵌表出现，继续等待正常/冲突业务截图。

研磨行高候选将正常预算目标改为max(52dp,2×lineHeight+compoundPaddingTop+compoundPaddingBottom)，预算不足仍沿用原48–52dp回退；仅panels[1]的研磨编辑框受影响，其他笔记及保存恢复不改。双Mock APK40秒成功（/tmp/task-review-grind-height-build.log），diff检查通过。等待同一长研磨fixture截图与IME/字体1.3原生检查，未标记视觉缺陷已闭环。

## 参数表正常态/冲突态实屏证据

fb209f8 advanced38875194305全工作流completed/success，business和pages均通过。下载并实读business/import-conflict.png（四段，标准字号）与wideFont-screenshots/zh-Hans-light-CurveImportPreviewActivity.png（两段，1.3字号）：内嵌三列表实际出现，各段压力/本段水量与图中压力一致，右下保存可见。business-flows确认17路径、完整字段、重复导入/冲突保留/副本；landscape及wideFont各240fixture、8语言×2主题×15页、60截图，preservedState=true。

范围限制：均为1600×1000 CI窗口，不能扩大到用户平板；冲突图仍有系统剪贴板浮层，不能当无遮挡视觉基线。图表绘图区仍明显扁于curve-import-preview-v1，阶段配色与警告视觉分组未对齐；表格出现并不等于整页完成。下一步需同时调摘要/图表/表格的空间，不只继续缩文字或增加最低高度导致表格消失。1.3图只含两段，不证明四段大字号全部可见。

500d5d6 Capture38872022621 attempt2已completed/success，之前SDK镜像解压失败的执行通路恢复；此状态不替代对应截图审读。9d5156d single-screen38876652976、advanced38876652959和65c040a single-screen38876121135查询仍queued。当前工作树基线9d5156d，本轮未新增样式提交。

## 导入图表辅助内容迁移候选

根据fb209f8正常/冲突实屏，把左侧兼容性说明及全宽详情按钮迁到右侧辅助区；详情降为accent文字操作，仍有48dp目标、ripple及键盘焦点边框。左侧标题/指标/图表单位说明/三列表与字体尺寸不变，原短高测量回调自然排除已迁出的控件，不提高隐藏表格阈值。保存/副本/失败恢复和未支持原始流量说明未变。

双Mock APK41秒成功（/tmp/task-import-chart-space-build.log），diff检查通过。预期腾出的空间尚未由精确提交截图测量；仍需普通四段、1.3四段、用户平板和短高证明，不声明已达到认可稿图表比例。

## 三个设置页范围审计

实读500d5d6 task-frame用户平板AppSettingsActivity、MachineSettingsActivity、ScaleSettingsActivity三张PNG。核对500d5d6..HEAD三个Activity无改动；这只是这些页面旧版本正常态的有限依据，不代表共享组件最新状态或全部交互通过。规格第1节明确三个设置页保留全部功能、统一导航/字号/层级，但不要求单屏，因此不能将首屏以下内容或滚动条判为单屏缺陷。它们没有独立的已认可ImageGen页面，按共同tokens和文字规格审计，不虚构像素对照稿。

| 页面 | 当前观察 | 后续范围与边界 |
| --- | --- | --- |
| 应用设置 | 限宽居中内容列，外观/语言/偏好分组；左轨设置选中，标题和控件可读 | 窄内容列有阅读价值，不为铺满平板强改双栏。需补下方信息/导出与主题语言切换后的实屏，不能从首屏推断功能缺失。 |
| 机器设置 | 连接/回读在左，反馈/分类在右；初始状态卡片占高多，首屏部分输入在折叠区域下 | 优先收紧摘要层级，但保留回读确认、失败/未知结果及所有分类。未检查滚动后内容，不认定控件丢失。Mock图不构成设备写入验收。 |
| 秤设置 | 显示精度开关、能力卡和三条说明；电子秤导航选中符合工具上下文 | 协议说明行过长且与操作限制混排，适合限制说明宽度并分组渐进披露。未连接能力态不能代表已连接协议能力；不能删实验性/只读边界或虚增支持。 |

完整21页+3设置范围保留。9d5156d single-screen38876652976与65c040a single-screen38876121135本轮查询仍queued；最近失败列表没有新增至这两个提交。本轮不推送额外样式，避免继续累积待验收候选。

## 2026-10-16 分享页构图修正（待新截图）

对照 curve-share-v1.png 与 418fec6 用户平板 CurveShareActivity 实际窗口：旧页把四个操作放到页底、右栏只有密集参数，与目标左右分工相反。现改为左侧24sp名称、14sp标签/24sp700温度和目标重量、阶段图、压力阶段参数表；右侧20sp分享方式、主分享文本、复制、分隔后的导出和文件分享，14sp导出条件及分享范围说明直接可见。8语言补齐标题与提示。压力表不足160dp图高时收起，完整参数仍可查；原始流量模式不伪造压力图。通用弹窗 profileCard 未改。

本地 Mock + instrumentation APK：`BUILD SUCCESSFUL in 45s`，日志 `/tmp/task-share-composition-build.log`。这只证明编译，不证明实际字号/间距/比例已对齐。现有 AdvancedBusinessUiChecks 只验证复制；另有 CurveDocumentContractChecks 的拦截式分享、取消/成功导出、UTF-8参数、只读URI授权，以及 CurveSystemDocumentChecks 的系统选择器路径，不能混称实际向第三方发送验证。本轮未启动外部分享、安装或硬件操作。

未完成：新源码用户平板与1.3字号截图、右栏各操作可达性、四阶段表与图高、阶段色及卡片边界细节。总目标仍21功能页+3设置页，不以此页构建通过代替视觉验收。此前 d3f81bc task-frame 38877244920 已成功，其 single-screen 38877244702 和 advanced 38877245190 本轮查询仍 queued；不取消旧任务。

## 2026-10-16 编辑页结构审计与新 CI 证据边界

前一轮 f41b781 是有效实现进展。本轮重新查询：f41b781 single-screen 38878319787、advanced 38878322390 尚 queued，task-frame push 38878313279 正在运行；未据此重启或取消任何工作流。

已下载并查看 d3f81bc task-frame 38877244920 用户平板产物（build/task-first-evidence/ci-d3f81bc-task-frame）。其中 CurveImportPreviewActivity 是无输入文档的非法状态，HistoryDetailActivity 是记录缺失状态；这两张只能证明对应空/错误界面，不能证明 d3f81bc 导入图高或65c040a详情密度已正确。9d5156d advanced 38876652959 的 business/pages 两个任务均仍 in_progress，尚无产物，研磨度第二行修正仍待实际窗口。

编辑页的有效对照采用418fec6 single-screen用户平板正常窗口与 curve-editor-v1.png；CurveEditorActivity 从418fec6到当前无改动，适用于当前结构审计。数据2阶段与稿中4阶段不同，不算缺陷；如下结构差异与数据无关：

| 项目 | 当前实际 | 已认可目标 | 判定 |
|---|---|---|---|
| 基本参数 | 左栏名称/温度纵排、液重在下一个卡片 | 顶部名称、温度、液重并排 | 未达成 |
| 阶段编辑 | 六分组通用表单，阶段卡片位于首屏下方 | 左侧紧凑阶段参数表，1–4段可见可编辑 | 未达成 |
| 预览 | 右侧280dp固定栏、180dp图、大段重复摘要 | 与阶段表并排、足够大的图和轻量说明 | 未达成 |
| 动作 | 底部预览与保存各半屏宽 | 有限宽度主保存，预览已有内联反馈不应同等突出 | 未达成 |
| 字体间距 | 表单采用beanInput默认字重/通用卡片12dp与10dp间隔 | 与稿和规格一致的16sp正文、14sp说明、8/16dp节奏 | 待按新结构逐项验证 |

根因代码定位：CurveEditorActivity 直接复用 TaskFormSections（基础/目标/4阶段六组），TaskFormSections将所有panel纵向排列并限制预览栏280dp；不是遗漏一次样式设置。下一步应新增编辑页专用布局，避免修改共享表单而影响豆子页面。保持同一批EditText实例与name/temperature/cup/target-N/water-N键、stageCount、draftId、form-focus/selection及本地保存逻辑；仅重排控件。横屏标准高度顶部3栏、下方阶段表/预览；大字号或IME时阶段区局部滚动，保存固定可达。不得取消校验、减少4阶段覆盖或把实际图改成静态图片来追求截图一致。

### 编辑页专用布局候选实现

新增 TaskCurveEditorForm：元数据三列，下方阶段参数/预览两栏；阶段压力与注水量并排。旧六分组跳转下拉移除，阶段数量Spinner仍保留1–4；stageCount、draftId、全部字段key及解析保存不变。元数据和阶段各自局部滚动，焦点/选区恢复沿用原key，增加metadata-scroll；未修改共享TaskFormSections。右侧预览图从180dp扩大到280dp，预览弹窗入口移至右栏，保存改为有限宽TaskActionDock。

ChineseFormWindowChecks 对编辑页按真实字段定位六组，而不是旧跳转Spinner，继续逐个检查可见范围及IME。AdvancedBusinessUiChecks仍完整保存/重开/重建12字段4阶段，额外保存 curve-editor-four-stages.png；脚本强制检查第四张业务截图存在且为1600×1000。未降低任何业务路径计数或可见性要求。

首轮双APK构建41秒通过，新增业务截图后双APK23秒通过。仍需精确最终源码CI与实际截图，尤其四阶段是否能在标准字号同时呈现、冗长字段标签占高、元数据三列对齐、右栏摘要密度、短高度IME。这里是结构修正候选，不是宣告编辑页已与概念稿一致。无实机安装/发送/合并/发布。

## 2026-10-16 研磨字段修正的实际窗口验证

9d5156d advanced run 38876652959 已全部成功（business、pages均completed/success）。本轮下载business产物至 build/task-first-evidence/ci-9d5156d-business，实际查看 review-saved-notes.png，并与e98d7c7同路径截图对照：同为1600×1000、浅色、已填复盘。旧研磨输入框第二行被底边截断；新图第二行字形完整，查看记录按钮相应下移，保存按钮仍在底部可见。随机token不同，不把文本长度差异当像素比较依据；更长的第三行仍需输入框内滚动。TaskReviewSections.kt从9d5156d到70dab05无diff，该项呈现修正仍适用于当前源码。

日志核验：ADVANCED_BUSINESS_UI_FULL_FIELDS_PASSED（豆子3日期、复盘7字段、曲线12字段4阶段，未保存及重开后重建恢复）；17业务路径、3库存事件、3新草稿。另有文档契约11路径/7选择器契约/2分享契约与系统选择器7路径通过，两者externalSend=false、noBle=true。不把测试拦截或模拟设备当外部发送/硬件验收。

本轮只核验business窗口；pages任务成功不等于其全部截图已审阅。用户平板2560×1600与1.3字号、横屏IME仍需各自真实图。4973a6e诊断版single-screen 38875772682：普通横屏/大字号已成功，用户平板任务仍in_progress；没有本次断言失败的诊断，不能宣称旧间歇性warning问题根因已解决。70dab05 advanced38879399864、task-frame push38879389705及f41b781 single-screen38878319787复查仍queued。未重启、取消或放松门禁。

## 2026-10-16 阶段色与参数表联动候选

原CurveStageView所有阶段使用chart_pressure单蓝色，与已认可编辑/导入/分享图的青、蓝、橙、紫阶段身份不符。本次新增独立CurveStagePalette，浅深色各四个语义色；用于目标线、点、数值和低强调背景分区，并同步导入/分享表格首列及编辑页阶段标题。阶段数字和bar读数仍保留，横轴依然是阶段而非时间；不动实时四指标配色及任何采样、数值投影、协议数据。

浅/深主题所有阶段文字对mobile_background/mobile_surface对比度最低4.84；计入12/255透明分区叠色后最低4.55。为文字清晰度，浅色橙色比示意稿更深，不从图片直接采色导致低对比。XML解析与计算通过。LanguageChartChecks增加每个阶段实际列的颜色像素检查，并继续检查LTR/RTL科学上升顺序；原实时/历史单色检查保留。测试已编译，尚未运行，不把静态配色计算当Canvas像素检查通过。

Mock + instrumentation构建 `BUILD SUCCESSFUL in 46s`，日志 `/tmp/task-stage-palette-build.log`。仍需精确源码CI、浅深色/大字体实际截图，字体/间距/区域比例及21+3页总目标仍未完成。没有重新生成概念稿来追认实现差异。

## 2026-10-16 导入图高证据与系统浮层污染修正

d3f81bc advanced 38877245190 的business产物已取得（整个run当时仍in_progress）。已查看build/task-first-evidence/ci-d3f81bc-business下import-new与import-conflict两张1600×1000实际窗口：移走左侧兼容提示/详情动作后，绘图区域约y350–570，四阶段表与右侧决策/底部保存可同时显示；新导入与冲突都保留94°C、41.7g、4阶段及对应参数。17路径和12字段4阶段保存恢复日志通过。

两张图左下仍有Android系统剪贴板预览，遮住表格后两行的阶段文字，不能称完整无遮挡视觉验收。页面本身是否被系统浮层遮住，与应用几何溢出分开判断。当前444d9d4另新增颜色，故d3图片不能当新配色证据。

本次只修改AdvancedBusinessUiChecks截图准备：临时读取交互窗口，在确认应用窗口存在后，等待其它窗口可见可点击节点不再与应用内容矩形相交；最多15秒，超时记录窗口观测及节点范围并失败，finally恢复accessibility flags。不给剪贴板清空/关闭操作，不注入触摸，不裁剪或重绘图像。仍使用真实全屏takeScreenshot和原四张尺寸门禁。构建test APK41秒通过，`/tmp/task-business-overlay-build.log`；等待逻辑实际运行与无浮层新图仍待CI，未将编译包装为成功消除浮层。

最新查询：70dab05 task-frame38879399856仍in_progress；f41b781 advanced38878322390已有business产物，但正常分享布局截图仍待single-screen。全21+3页目标未完成。

## 2026-10-16 库存流水构图候选与编辑页实图复核

库存流水对照bean-ledger-v3与418fec6用户平板正常长列表。原BeanLedgerActivity至本轮前无源码变化：每行只有两段拼接文字，缺少列头与横向比较关系。已改为类型/带符号变更/变更后余额/豆款批次/真实原因五列，14sp列头16sp内容；按已有事件倒序及scrollY恢复不变。仅有效批次筛选时展示批次当前/初始余额及详情入口，全库不伪造汇总。长值两行省略、整行48dp以上可访问详情并保留完整值。无时间戳、杯次归属或正常耗用危险红。八语言新增列头/记录顺序说明，库存模型及写入未改。首轮双APK42秒通过；最终文案构建结果另核验，真实新窗口仍待CI。

70dab05 task-frame38879399856已success，下载至build/task-first-evidence/ci-70dab05-task-frame。已查看用户平板2560×1600 CurveEditorActivity正常2阶段截图：新元数据三列/阶段与预览并排/有限宽保存按钮已生效；旧六分组选择器不再占首屏。仍不达标：目标液重标签两行造成输入框纵向错位；每段重复冗长范围说明，行高偏大；右栏摘要与预览入口需要局部滚动。该图不含4阶段，也不含444d9d4新配色，不能冒称完整验收。下一步把范围说明从重复可见标签中分离，同时保留访问/错误校验；压缩阶段为真正表格行，标准字号检查四阶段同屏，大字号/IME允许局部滚动。

流水最终双APK构建38秒通过（`/tmp/task-ledger-final-build.log`），`git diff --check`通过。此结果不替代新增行详情/列宽/字体的运行验证。

## 2026-10-16 编辑页紧凑阶段表与对齐检查

依据70dab05用户平板实图，顶部可见标签改为简短名称/设定温度°C/目标液重g，14sp；完整范围和精度保留为输入可访问名称、长按提示及右栏完整说明入口。阶段区增加固定阶段/压力bar/注水量ml列头，行内64dp编号+两个52dp原生EditText，8dp行距；没有保留隐藏的旧分组或重复标签来满足测试。所有原fields键、数值解析/校验、监听器、stageCount、draftId、滚动/焦点/选区恢复不变。右栏成功摘要仅列温度/液重/阶段数，完整参数仍可预览。

测试按可访问名称找到没有重复文本标签的字段，保留原12字段4阶段数据断言。ChineseFormWindowChecks针对用户平板等级（宽≥1000dp、高≥600dp、字体≤1.05）增加11个文本输入全可见、阶段数量控件全可见、顶部三个输入Y差≤1px，并保存four-stage-table实际窗口；大字号/短高仍走原逐字段/IME可达检查，不强行要求全字段同时显示。新断言尚待实际CI运行，不能当作已通过证据。

首轮Mock与instrumentation构建43秒通过（/tmp/task-editor-table-density-build.log）；最终断言编译另记录。下一步须核对新四阶段同屏图、顶部对齐、浅深/大字体、键盘与范围说明入口，不以当前候选替代已认可效果图。

最终test APK编译24秒通过（/tmp/task-editor-table-checks-build.log），diff空白检查通过。仍无本提交运行截图。

## 2026-10-16 分享页/历史详情用户平板窗口复核

f41b781 single-screen38878319787全部成功（用户平板、普通横屏、大字号）。本轮下载用户平板产物至build/task-first-evidence/ci-f41b781-user-single，结果checked=336、pages=21、listRestoreFixtures=14；7页列表14恢复路径通过。仅将实际已看的窗口列为视觉证据，不把336检查当336视觉达标。

已查看用户平板浅/深主题CurveShareActivity：左侧名称、91°C/27g、2阶段图和压力/注水量表，右侧分享方式与四个操作、导出条件、分享范围说明全部位于窗口内。与旧418fec6底部2×2操作相比，左右任务分工改善已证实。剩余差异：导出/详情仍为大块描边按钮，参考图是更低强调文本操作；参考图左右有明确轻边界面板；阶段身份色属于后续444d9d4，不能由本图确认；四阶段/大字号截图本轮未下载审阅，不能按本图推断。

已查看同批HistoryDetailActivity正常样例：曲线/日期合并为单行、状态靠右，30秒/36g、左图右复盘、有限宽编辑动作可见。右侧前三字段已变成紧凑标签值行；下次调整/详情仍需要局部滚动，属于支持区而非整页。图表实际绘图区仍比图例/摘要占比小，空复盘场景“未填写”较密集；不得将这张未填样例当长记录验收。HistoryDetailContent从f41b781至当前无改动，证据仍覆盖其布局。

70dab05 advanced38879399864也已全部success，证明当时专用编辑布局的原业务检查通过；本轮尚未下载四阶段产物，不把状态代替截图，也不外推为229f798紧凑表及新对齐断言已通过。444d9d4 single-screen38880099503查询仍queued、advanced38880099292仍in_progress。总目标保持21+3页，尚未完成。

## 2026-10-16 分享面板候选与阶段配色实图

依据f41b781分享页用户平板浅深实图，分享页两区增加mobile_surface/12dp圆角/边界/16dp内边距；右侧开启填满支持区的可选属性，TaskWorkspace默认行为不变，内容多仍可局部滚动。导出和详情改为文本操作，保留原Button最小触摸范围、16sp/500、焦点描边、涟漪及禁用前景色；分享文本、复制、文件分享层级和导出/URI逻辑不变。分享预览测量扣除内边距后再决定图高/表格，不沿用全宽预算。双APK构建43秒通过，日志/tmp/task-share-panel-build.log；本候选仍需实际截图与交互CI。

444d9d4 advanced38880099292现已全部success。下载business至build/task-first-evidence/ci-444d9d4-business，实际查看import-conflict.png：青/蓝/橙/紫目标线、数值、轻分区及参数表阶段文字一致，2.4/5.7/8.3/6.9bar与12/23/34/45ml未改变；该浅色1600×1000图支持配色改动生效。系统剪贴板仍遮下方阶段名，属于7fcb665截图等待之前，不能称无遮挡验收。17路径和原12字段4阶段恢复日志通过。仅此business图不能证明全部深色/大字号像素检查或新紧凑编辑布局通过；不能把advanced成功混为全量视觉一致。

## 2026-10-16 无浮层导入窗口验收证据

7fcb665 advanced38880593942已全部success。下载business至build/task-first-evidence/ci-7fcb665-business并实际查看import-conflict.png：1600×1000浅色、四阶段、冲突状态，系统剪贴板预览确已消退，四行阶段名/压力/水量和保存副本按钮完整可见。对照444d9d4同状态图，之前遮挡第三/四行的系统浮层已不在；阶段色与数值保持。17路径及12字段4阶段保存/重开/重建断言通过。等待逻辑未清剪贴板、未点击关闭，也未裁图。此项采集缺陷有实际图证明修正，不能扩大为用户平板/大字号或21+3页全量验收。

本轮未发现新失败，最新failure仍为已有418fec6/693e696等历史运行。229f798 single-screen38881692281与advanced38881692269仍queued；c31bfd1 single-screen38881146289/advanced38881146368仍queued，task-frame push38881138966已success但本轮未看该截图，不冒称流水视觉通过。

历史/实时共用图的面积问题定位到ExtractionOverlayView：图例每项同时列名称单位和完整量程，header还含独立量程说明，MetricPlot再绘四轴/刻度/时间轴。此处存在信息重复，但窄窗聚焦模式仍必须始终可见其他指标单位与min/max；下一步不得直接删量程或缩字号求图大，应先分清全轴与聚焦模式再设计呈现，同时保留48dp图例触摸及原科学绘图检查。本轮未修改该组件。

## 2026-10-16 宽图例去重与历史绘图区间距候选

历史详情实图中，完整四物理轴旁图例重复上下限；现ExtractionOverlayView仅在图可用且ALL_SCALES时显示名称+单位。窄窗聚焦轴或过矮提示仍显示完整量程；可访问描述始终保留范围。图例判断和Canvas绘制共享axisColumns/showsAllAxes计算，范围变化跨越布局阈值会请求重新测量，避免轴切换后漏掉范围。14sp图例、11sp轴、48dp触摸、独立投影、采样缺口、线型及选择状态不变。

历史独立详情去掉chartCard8dp四周与额外8dp上边距，保留指标组16dp底间距，嵌入式详情不变；40sp结果读数不缩小。理论上释放外围空间，但真实绘图区增长及视觉比例必须由新截图证明。

ChartLegendChecks仍检查准确可访问名称/范围、物理单位、48dp、文本边界、四线与RTL；240/300dp明确要求可见量程。对未显示范围的图例，新增实际Canvas单位/两个上下限必须存在的断言。TrendChartTextChecks同样不以contentDescription代替可见物理轴，检查真实绘图文本。Mock/test APK42秒构建通过，日志/tmp/task-chart-legend-density-build.log。真实Canvas检查和新窗口尚待CI；229f798 advanced38881692269本轮查询仍queued，无新失败据此宣布修复。

## 2026-10-16 紧凑编辑与流水表实际截图核对

229f798 task-frame push38881685298已success。下载build/task-first-evidence/ci-229f798-task-frame，查看用户平板2560×1600 CurveEditorActivity：名称/温度/液重三个输入上沿已齐平，短标签不再把液重输入挤低；阶段编号、压力、水量以紧凑行排列，预览青蓝阶段身份一致。该截图仅默认2阶段，不能代替新增4阶段完整可见断言。右侧预览/完整说明操作仍在支持区下方，截图中出现按钮顶部，需局部滚动；完整可达要看后续表单测试，不能根据露出边缘就宣称失败或成功。CurveEditorActivity/TaskCurveEditorForm自229f798至当前无差异。

c31bfd1 advanced38881146368已有pages产物（本轮查询整个run仍in_progress）。下载build/task-first-evidence/ci-c31bfd1-pages，实际查看中文浅色BeanLedgerActivity普通横屏及1.3字号：五列标题和单条入库值对齐、无裁切，批次上下文与当前/初始250g正确，+250g明确增量。日志两种profile各240 fixtures、8语言×2主题×15页passed；并非所有截图都已人工审阅。图为1600×1000单事件批次，不证明长列表滚动恢复、4096字原因详情、混合消耗/调整或用户2560×1600完整验收。BeanLedgerActivity自c31bfd1至当前无差异。

本轮无新失败（最近failure仍418fec6/693e696/1c256c8/bf2ed46历史运行）。229f798 advanced38881692269仍queued；c31bfd1 single-screen38881146289仍queued。原21+3页目标及剩余图表/长文本/异常状态验收不缩减。

### 批次详情横屏构图（2026-10-16，待新截图）

- 对照bean-batch-v1：旧页面主区仅豆名/余额、右栏日期、底部三按钮，信息分散且留白过大。改为横向库存摘要及三日期，最近三条流水的局部列表，次要文字入口和单个限宽强调按钮。14/16/20/24sp及8/16dp间距使用既有字体体系。
- 概念稿的流水时间、“本杯使用”没有模型依据，不照搬；呈现真实事件类型、带符号重量和原因，点击可看完整原因及变更后余额。比例无初始量时为—，超过100%保留真实百分比；进度轨道仅限0–100%。调整预览/确认逻辑未改。
- 新增业务测试确认库存变更后batch-after-adjustment截图；脚本要求五张业务状态PNG。最终Mock/test APK编译成功44s（/tmp/task-batch-final-build.log），shell语法、git diff --check、branch policy通过；尚未执行新版本运行测试，不能宣告与图一致。
- 229f798 advanced run38881692269已成功；single-screen38881692281仍in_progress。该状态不证明本轮批次页，也不替代编辑页四阶段截图目视对照。

### 曲线编辑器四阶段实图对照（2026-10-16）

- 229f798 advanced38881692269成功；下载ci-229f798-business，目视curve-editor-four-stages.png（1600×1000，中文浅色，四阶段2.4/5.7/8.3/6.9bar，温度94°C/41.7g）。三元数据输入对齐，四行阶段输入完整，阶段色与预览对应；business-flows.txt证明17条业务路径和四阶段12字段保存/重建检查通过。
- 与curve-editor-v1比较仍缺少三块元数据卡片和左右面板，右侧两行全宽按钮强调过重。继续增加既有surface/border、12dp圆角、16dp内边距，将两入口改为同排文字操作；不改字段解析/保存和错误提示，也不减字号或触摸目标。
- 这是1600×1000业务截图证据，不冒称用户平板四阶段单屏验收；该版本single-screen38881692281仍运行，新增面板也需要新源码CI。阶段数量仍使用原生选择器而非概念分段控件，图表高度及新增边距后的标准字号四阶段可见性尚待实图验收。
- 本轮Mock/test APK编译成功42s（/tmp/task-editor-panels-build.log）；diff检查与branch policy通过。运行及视觉验收仍待新CI，不据此宣告完成。

### 入库表单对照（2026-10-16，实施后待截图）

- 目视f41b781用户平板中文浅色BeanEntryActivity/window.png与bean-entry-v2：旧版重复分区跳转/标题、多张纵向卡片占高，日期起始已接近底部，保存按钮跨满页面，不符合居中紧凑表单的已批准稿。
- TaskFormSections仅BeanEntryActivity引用；改为最大680dp单卡，16dp内边距/8dp相关项间距，取消跳转下拉框及重复标题。五字段/三个逻辑分区、局部滚动、错误跳转、草稿焦点选区保持；保存放卡内240dp按钮。日期14sp标签、18sp输入同排，真实YYYY-MM-DD格式提示，无新增日历行为。
- ChineseFormWindowChecks现在通过真实字段定位入库三分区，不保留测试专用隐藏控件；原可见性、IME、编辑/恢复检查不删除。新增标准用户平板五字段及保存按钮全显断言，待CI运行。库存逻辑不变。
- 尚未对齐新/已有豆款分段选择、日历选择交互；当前仍原生豆款选择器和文字日期。不可将本轮称为完整视觉验收，也未生成新图覆盖偏差。
- 最终Mock/test APK编译成功43s（/tmp/task-bean-entry-final-build.log）；diff/branch policy通过。运行证据仍待新CI。
- 另已下载229f798的large-font产物：result.txt为336窗口/21页、56表单截图；list-state-result.txt为7页14恢复夹具；编辑器浅色表单completed=true、inlineObserved=true、imeObserved=true。这是修改前版本的大字号运行证据，不覆盖本轮入库表单或baf1155新增面板。

### 229f798用户平板失败与四阶段实图（2026-10-16）

- single-screen38881692281结束failure：用户平板335/336；唯一失败为中文浅色BrewReviewActivity短字段编辑后cleanupEditor等待page-focus/IME-hidden超时；14项列表恢复全部成功。不是四阶段字段裁切断言失败。artifact logcat.txt为空；现有失败未记录超时瞬间窗口条件，根因未证实。
- 新增ChineseFormWindowChecks边界诊断：实际窗口ID/焦点类、所有窗口类型与bounds、IME窗口和insets可见性、hide请求返回值；超时独立截图。等待5秒/两次稳定/原失败断言保持，未重试或取消测试，生产代码未改。test APK编译24s成功（/tmp/task-form-ime-diagnostics-build.log）；diff/branch policy通过。这只是证据补强，不称已修复。
- 目视ci-229f798-user-failure/.../chinese-form-states/zh-Hans-light-CurveEditorActivity-four-stage-table.png：用户平板2560×1600四行八字段完整显示，上方三字段对齐，阶段/色带对应；结果completed=true、screenshots=8、inlineObserved=true、imeObserved=true。右侧旧双按钮仍需局部滚动；baf1155已改文字操作但尚待其实际截图，不能将旧证据覆盖新面板。

### 批次摘要与分享面板实图（2026-10-16）

- e837af4 run38884386706的pages产物已下载ci-e837af4-pages；landscape/wideFont各240夹具通过。目视中文浅色批次页1600×1000普通/1.3字号：三列摘要、三日期、真实入库行、文字入口和限宽调整按钮完整，修正旧双栏留白和按钮矩阵成立。只有单条ADD/250g数据，不能覆盖三条近期事件/长原因、用户平板或比例超过100%；概念的分隔线/进度条粗细仍有差异。
- 0d5a393 pages产物ci-0d5a393-pages，中文浅色分享页可见左右面板，14/24sp摘要层次、双阶段颜色和完整表格、主分享/次复制/文字导出操作分级，禁用文件按钮及条件说明完整。对应441e862面板改动已获得实际运行图，不再仅是源码推断。
- 分享页截图仍两阶段，与curve-share-v1四阶段状态不同。新增业务流程中已持久化四阶段draft的curve-share-four-stages截图与五行完整可见、未导出文件禁用断言；不发送外部内容，原JSON复制隔离校验保留。待新CI验证。
- 分享面板宽度比例、摘要子卡、表格网格/标题与生成图仍未完全一致，不能标整页完成。实时图表并不在该15页产物内，0d5a393图例修改尚未因此获得验收。
- 新增四阶段分享证据代码编译test APK成功24s（/tmp/task-share-four-stage-evidence-build.log）；shell语法/diff/branch检查通过。未将编译当作运行验收。

### 分享比例改进与大字号图表证据（2026-10-16）

- 分享页按curve-share-v1将支持栏改为38%/最大440dp（默认工作区30%/320dp不变），增加温度/液重子卡与16dp间隔、表格网格及标题底色。图注复用“阶段目标示意，不是时间预测”，未缩减字体、图表160dp最低预算或动作触摸目标。新四阶段断言仍保留。
- Mock/test APK编译43s成功（/tmp/task-share-proportions-build.log），diff/branch policy通过。新比例尚待实图，不宣告一致。
- 已下载ci-0d5a393-large-font，result.txt为336/21窗口、56表单截图、14列表恢复。目视中文浅色HistoryDetailActivity：四条数据在同一图，四物理轴单位/端点完整，图例为单行名称/单位，不再重复量程；30秒与36g主结果清晰。右侧个人记录末项仍需局部滚动，不能称全部内容同时可见。
- 同版本ExtractionActivity基线仅“未选曲线/等待采样”，图例与轴完整，不代表正在萃取。0d5a393图例和历史图间距已得到该大字号静态状态证据；正在萃取、不同秤连接状态与用户平板标准字号仍需对应实图。
