# 设计稿与原生实现一致性审计

2026-10-16。用户明确要求开发与生成图保持一致，包含字体和间距。当前结论：**未通过视觉一致性验收**。21页概念覆盖、原生窗口几何通过，不等于本项完成。

最新全范围状态见[24页验收矩阵](task-first-page-matrix.md)。以下保留按时间追加的历史证据，旧版本描述不代表当前源码。

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

### 用户平板表单实图裁切修正（2026-10-16）

- 7e4cee1 task-frame38885369874已成功，下载ci-7e4cee1-task-frame并目视用户平板两页：入库单卡/紧凑日期方向已生效，但开封日期输入底部仍被表单视口裁切；编辑器三元数据卡/左右面板已生效，固定280dp预览图使图注和次要动作位于局部视口下方。框架24页通过不是这两项的完整可见性证明。
- 入库仅减少名称/重量标签上下留白，从8+6dp改4+4dp，保留15sp标签、18sp输入、52dp控件和五字段断言。编辑支持区测量实际标题/图注/状态/按钮高度，图表使用剩余160–280dp；短高继续局部滚动。不缩字号、不遮挡控制、不修改保存行为。
- 扩展标准用户平板四阶段检查：原11输入/选择器完整可见之外，图注、预览和详情按钮也必须全显。新测试尚待CI，不能凭源码声明修复。
- 最终Mock/test APK编译41s成功（/tmp/task-form-viewport-final-build.log），diff和branch policy通过；新截图与断言结果未到，未宣告修复验收。

### 两杯对比横向构图（2026-10-16，待运行图）

- 目视brew-comparison-v1与f41b781用户平板BrewComparisonActivity基线：旧版图表仅左侧，右侧两杯选择/全部来源/四个大按钮堆叠，第二杯信息在局部视口下方；与已批准上方选杯、全宽图表、下方对照的结构不同。
- 新TaskComparisonLayout把选杯/指标/交换置顶，图表全宽，两杯来源置底并排。顶/底局部滚动，预算优先保留图表minimumChartHeight；原异步样本加载、身份和交换/指标逻辑、来源时间对齐及异常文字不改。图例显示A/B实虚线，完整时间/状态在各来源和无障碍说明保留。
- 详情/笔记/交换改文字操作，辅助字13→14sp；没有缩图中文字。原测试继续验证四指标不改变原样本、清空真实身份、交换和恢复；新增用户平板标准字号四个工具全显与图表占窗口80%以上断言。
- 本步不是完整对齐：指标仍原生下拉框，底部还未整理为时长/结束秤重/口感的结构化摘要表，数据来源说明也需要后续实图核对。不得将本次构图变更当成整页完成。
- 最终Mock/test APK编译成功41s（/tmp/task-comparison-final-build.log）；diff/branch policy通过。新布局的图高、工具全显和各语言运行待CI验证。

### 编辑器新增面板后的CI失败定位（2026-10-16）

- baf1155 single-screen38884756522用户平板334/336：中文浅/深色四阶段字段117px仅107px可见。large-font334/336：中文浅/深色IME中“阶段”表头layout34px、available15px。下载两套产物ci-baf1155-tablet-user-landscape/ci-baf1155-tablet-large-font并读取instrumentation.txt确认，不能以其他页或框架CI成功覆盖。
- 根因分别为新增面板/元数据留白占用阶段高度，以及阶段选择器/列头固定在滚动区外，短高时被父布局约束高度。将元数据标签上下留白8+6dp收紧为4+4dp（字体14sp、输入18sp/52dp不变），选择器/列标题和字段放进同一阶段局部滚动区。字段键、数据、保存和原断言不改。
- f3233d6此前修的是右侧预览图预算，本次补齐左侧阶段表的独立问题。两项都需要新版本实际CI验证，不能称旧失败已自动消失。
- Mock/test APK编译42s成功（/tmp/task-editor-stage-scroll-build.log），diff/branch policy通过。运行断言待CI，保留失败来源和后续回归追踪。

### 两杯结果摘要表（2026-10-16，待实图）

- 按brew-comparison-v1在底部加入三列摘要表：指标/A/B，展示记录的时长、结束秤读数和口感。14sp标题、16sp值、8dp水平/4dp垂直内边距及border网格；口感为48dp可点击单行摘要，焦点/涟漪反馈和完整文本入口具备。数据来自BrewJournal.Entry，不从对齐后的图形推算结果，缺失为—。
- 原来源时间、状态、采样、对齐和缺失/重置/间隙说明保留表格后方，局部滚动可达；表格不是对旧安全文字的替换。原详情和完整笔记操作仍保留。
- 扩展TwoCupComparisonPageChecks：真实选择清空后摘要三值为—；交换身份后摘要随两杯原值交换。保留全部指标/原样本/占位选择/恢复断言。指标控件仍未改分段形式，新表格实图未取得，不能称整页完成。
- 最终Mock/test APK编译43s成功（/tmp/task-comparison-summary-final-build.log），diff/branch policy通过；运行和视觉结果待CI。

### 指标分段选择与修正后表单截图（2026-10-16）

- 两杯对比指标下拉框替换为同一行四个真实按钮；保留本地化名称/单位、16sp字体、48dp触摸目标、键盘焦点和选中状态。TwoCupComparisonPageChecks以performClick逐一切换，保留原样本不变/交换/清空/恢复和图高断言。不是通过隐藏长标签腾空间。
- Mock/test APK最终构建成功6s（/tmp/task-comparison-tabs-final-build.log，91项up-to-date；此前实际编译45s成功）。diff检查及分支规则通过；新指标控件尚无运行截图，不宣告视觉完成。
- 下载f525db3 task-frame run38888397896至ci-f525db3-task-frame，24页框架检查通过。目视用户平板2560×1600中文浅色：BeanEntry五个输入框及保存按钮均完整；CurveEditor双阶段状态的图注、温度/杯重摘要、预览与详情动作均完整可见，旧图表挤出底部动作的问题在此状态不再出现。
- 该编辑图只有两个阶段，不覆盖四阶段或IME；BrewComparison基线为“至少需要两杯长期记录”的空状态，不可证明新全宽图/结果表。对应有数据和四阶段检查仍待后续CI，当前证据不外推。
- 7e4cee1 single-screen38885378343已读取三profile失败：用户平板入库日期117px仅104px可见、编辑第四阶段117px仅107px可见；一般横屏/大字号IME阶段表头受裁切。这是f3233d6/f525db3之前版本，不能据此重复修改或认为新版本失败。修正后专项断言仍需核实。

### 浅色运行态用户平板证据补齐（2026-10-16，待CI）

- 现有Capture脚本仅1600×1000/density200且只有深色模拟运行态，无法与认可浅色萃取稿在用户平板几何上比较。改为2560×1600/density360/fontScale1.0，保存display-profile.txt，PNG检查精确尺寸。
- 原深色开始/结束/历史流程保留；之后通过实际主题开关回浅色并核实持久化值，真实点击再次开始模拟，留存第二次开始日志及ready/confirm/running/ended浅色截图。未使用等待页替代运行页，未伪造概念稿数值。
- bash -n、git diff --check及分支规则通过。未执行本机adb；新增点击流程和截图仍需CI运行。无秤运行态、与设计稿的逐项间距对照仍未完成，本次只是补采集能力。

### 电子秤主读数单位层级复核（2026-10-16）

- 目视f525db3用户平板ScaleActivity与scale-v2：主读数/去皮/最近10秒趋势和右侧本地计时已对应两栏结构；该实图为Mock无可用重量—，不是稿件18g稳定读数的同状态验证。
- 明确差异：实图g与主数值同64sp/700，设计单位较小；设备区还是连续说明文字，未呈现稿件标签/值分隔行。型号、电量和稳定状态缺乏本图真实来源，不能凭概念稿补假字段。
- 本轮仅将读数单位设为RelativeSizeSpan(0.45)，主值64/48sp和字重不动，完整本地化文本保留在同一TextView中；单位从各语言格式资源取得，包含泰语/俄语/阿语，不硬编码g。无数据仍—，不改去皮与采样。
- 后续需新实图核对尺寸/排版，设备信息结构和有数据状态仍未完成验收。
- Mock/test APK构建41s成功（/tmp/task-scale-unit-build.log），diff和分支规则通过；指标切换single-screen38889727697三个profile仍queued，未把排队当失败或取消重跑。

### 电子秤设备信息行（2026-10-16，待实图）

- 按scale-v2将原散落电量/状态说明改为标签和值两列，14sp/16sp、35%/65%、8dp上下内边距、48dp最小行高及1dp分隔线；值允许换行，保持在原支持区局部滚动中。设备标题、连接/设置入口与门禁未变。
- 电量仍只取有效live读数、battery能力且0..100的数据；显示纯百分比以避免重复标签。无能力/缺失解释、freshness全部状态保留。未添加无来源的BOOKOO型号、稳定状态或82%电量。
- 八语言标签/百分比资源XML解析通过；Mock/test APK构建44s成功（/tmp/task-scale-device-rows-build.log），diff及分支规则通过。新行高/换行需后续实际窗口验证，不能以编译替代视觉验收。
- 查询7a141fa/cbe8076/fb19415的截图和single-screen任务均queued，未取消或重复启动。尚无这几批的运行截图。

### 分享四阶段真实截图与旧表单诊断复核（2026-10-16）

- 709a337 advanced38886601670 completed/success，下载至ci-709a337-advanced。landscape/wideFont分别240夹具（8语言×2主题×15页）通过；业务17路径通过，含12曲线字段/4阶段保存与参数隔离复制。
- 逐图查看curve-share-v1、business/curve-share-four-stages.png及wideFont中文浅色CurveShareActivity：38%支持栏、温度/目标两张摘要卡、四阶段色带/线、五行表格及分层分享动作均实际生效。业务图四阶段2.4/5.7/8.3/6.9bar与表格一致，94°C/41.7g，文件分享未导出禁用。大字号图只有两阶段且完整，不能外推四阶段大字号或用户平板。
- 仍可见的差异：缺少概念稿“压力阶段预览”和阶段参数分区标题；详情文字动作位于右侧分享区而非参数区；分享范围说明未靠右栏底部；摘要字体/图表标签与概念仍有差异。新图证明若干修正生效，不是整页视觉通过。后续增加分区标题时必须保留160dp图高和四阶段完整表格，不能挤掉数据。
- 27ae12b single-screen38885839398 completed/failure，完整下载ci-27ae12b-single：一般横屏通过；用户平板332/336，仅入库末日期与编辑第四阶段浅/深色裁切；大字号334/336，仅编辑IME阶段表头裁切。均为f3233d6/f525db3之前来源，现有修正专项CI仍queued，不重复修旧版本。
- 27ae12b用户平板BrewReview浅色表单结果completed=true、17截图、8长状态、inline/IME=true，本次没有重复229f798的清理IME超时；这仅表示该轮未复现，不宣告未知时序问题已修复。新增超时诊断仍保留。

### 分享页分区与详情归属修正（2026-10-16，待新图）

- 根据709a337已读实图，加入20sp预览标题；阶段数量16sp标题与48dp详情文字动作同行，入口移回参数区。移除表格原16dp上留白，所有新固定高度仍纳入onPrimarySpaceAvailable，160dp图表下限及短高表格回退未变；详情不随表格隐藏。
- 非压力文档继续显示原始语义解释及完整参数入口。右栏保留分享/复制/导出及文件禁用状态，fillViewport弹性空白让范围说明靠底；短高空白归零并保留局部滚动。未改导出或JSON内容。
- 八语言新阶段数量标题XML解析通过；Mock/test APK构建43s成功（/tmp/task-share-sections-build.log），diff/分支规则通过。原四阶段逐行全显和文件禁用断言未改，新增标题后的实际高度需新CI验证，不以709a337旧图覆盖本改动。

### 旧版三页有数据证据与图表修正（2026-10-16）

- 逐图读取27ae12b用户平板visual-baselines中文浅色LegacyCurve/LegacyHistory/LegacyHistoryDetail：分别40条曲线列表（未选中）、41条历史列表及有真实夹具采样的详情，不再只有框架空态。列表导入工具全宽、重复说明、详情右侧说明墙/全宽概览仍与各自认可稿有差异。
- 详情图与legacy-history-detail-v1直接比较：压力/机器水流独立量程，但两条实线完全重合时仅水流可见；缺左右物理刻度与中间时间刻度。候选将水流改8/6dp虚线，图例同线型，左右bar/ml/s和真实刻度、浅网格与中间秒数齐备，测量文字宽度保留轴空间，12sp随系统字体缩放。旧样本/量纲/投影未改。
- LanguageChartChecks增加同一对角线的两条物理序列，实际Canvas中心区必须同时检出两种系列颜色；保留全部语言/主题与RTL时序断言。新增原生断言尚未运行，不宣称红绿通过；旧实图为问题证据。
- 最终Mock/test APK构建39s成功（/tmp/task-legacy-chart-final-build.log），diff及分支规则通过。新轴/线型的实际截图、短高和大字号需CI复核，尚未完成旧版详情视觉验收。

### 旧版详情构图与表单大字号复验（2026-10-16）

- 按legacy-history-detail-v1将旧记录摘要移到图表上方（16sp、最多两行，全文在详情保留）；右侧来源/限制独立16dp面板、20sp标题和14sp原完整说明；详情改右栏底部文字操作，取消跨页底部按钮。左图16dp内边距并用“历史采样”标题，不改异步读取、数据含义或缺失记录状态。
- 八语言资源XML、diff和分支规则通过；Mock/test APK构建43s成功（/tmp/task-legacy-detail-build.log）。本构图仍待实际有数据截图和短高回归，未标视觉完成。
- f3233d6 single-screen38887181838的大字号job完成success，已下载ci-f3233d6-large-font：result.txt checked=336/pages=21、56表单截图、14列表恢复；中文浅/深色编辑表单completed=true、7截图、6区、inline/IME=true。该版本本轮大字号检查通过，不证明f525db3后的阶段滚动或用户平板四阶段断言；其他job仍运行。
- cbe8076 capture38889966602已进入Capture tablet UI and simulated shot，截图产物尚未上传；未将运行中视作完成。

### 用户平板浅色运行态与入库裁切结果（2026-10-16）

- cbe8076 Capture38889966602 completed/success，产物ci-cbe8076-capture/mock-ui-screenshots。display-profile.txt核实override2560×1600/density360/fontScale1.0；shot-start-light-log.txt记录实际Mock开始，未发送蓝牙命令。实读extraction-running-light.png并对照extraction-four-series-v3，不再用等待页代替运行态。
- 图中6秒、4.20g、6bar、3.6ml、93°C；四指标同图、四独立刻度与单位、右下停止和本杯曲线/设定温度92°C均可见。数值属于Mock实采，不能硬替换成概念稿24.6秒/28.4g；无豆款/粉量明确—，无重量目标的含义仍完整。
- 明确剩余差异：主绘图区比设计稿矮，页头/运行指导与图例/范围说明占高；数值单位与数字同粗同大；右栏配方仍段落而非对齐信息行；历史参考/概览仍重边框按钮。停止位置已符合右下方向，但全页字体、间距及图表面积尚未通过。无秤状态仍缺对应运行图。
- f3233d6 single38887181838已终态failure：普通横屏/大字号success，用户平板334/336，唯一失败为浅/深色编辑第四阶段117px仅107px可见。入库浅/深色不再失败；中文入库表单completed=true、4截图、3区、inline/IME=true，结合之前实图确认该版本入库末日期裁切修正通过这一窗口检查。
- 编辑第四阶段为f525db3左侧留白/滚动修正之前；其single38888405636三profile当前均in_progress，不为旧版本失败再次修改或放宽断言。

### 实时萃取主图优先候选（2026-10-16）

- 根据cbe8076实际用户平板浅色运行图，宽窗指导移入标题行右侧，与标题/Mock标识并列，保留完整文本和自然换行；compactChrome窄窗仍独立显示指导，避免运行时隐藏页头连带隐藏安全指导。Activity未声明configChanges，窗口配置变化沿用重建。
- 时间及主数量保留40sp/700，单位RelativeSizeSpan0.55；完整字符串与无障碍读法不改，未知—不造单位。历史参考/概览改文字操作，48dp目标及原回调保留。
- 报警、未知结果、停止固定区和图例/量程说明未改，右栏配方段落仍是后续差异。本次页头释放空间需实际量测，不宣称主图已达到设计稿比例。
- Mock/test APK构建43s成功（/tmp/task-live-chart-priority-build.log），diff/branch通过；窗口检查/运行态截图待CI。f525db3 single38888405636三profile仍in_progress，未取消/重跑。

### 实时本杯参数信息行（2026-10-16，待实图）

- cbe8076用户平板运行图的右栏配方段落改为TaskRecipeSummary四行，标签14sp/值16sp、8dp上下内边距与细分隔；单位和无目标说明不省略，长值自然换行，继续在原支持区局部滚动。
- 来源逻辑保持：运行/已有采样/未知结果使用trace匹配的activeShotRecipe和本杯notes；未开始才显示待选profile。缺失—，未用下一杯设置补当前杯，未将设定92°C冒充实时93°C。
- 概览加入同一参数全文。LanguageActiveCupChecks从反射旧段落改为查找实际温度行，保留isShown/本杯温度、快照身份、语言重建不换杯和停止操作断言；不是查询脱离界面的隐藏摘要。
- 新分行增加的右栏自然高度需大字号/短高CI和运行图验证，不能凭布局源码宣称全部内容同时可见。
- Mock/test APK构建44s成功（/tmp/task-live-recipe-build.log），diff和分支规则通过；新增可见温度行断言与新布局实图待CI。

### 两杯对比摘要实图与四指标取证补充（2026-10-16）

- edd89da advanced38888881932 completed/success，下载ci-edd89da-pages，普通/大字号各240夹具通过。目视中文浅色对比页与brew-comparison-v1：全宽图表和三行双杯摘要实际生效，缺失第二杯时长/重量显示—，未知结果提示保留；原指标仍下拉框，因为fb19415在此版本之后。
- 该高级夹具两杯记录没有保存采样，图为0..1s空图，不能证明双轨迹绘制。大字号截图停在支持区下滚位置，摘要只见口感行，不能将其认作全表同时可见，也不能在没有滚动状态证据时直接认作裁切。
- 复用SingleScreenWindowRun既有非空两杯采样，四指标真实点击且断言通过后，等待绘制并分别保存comparison-metrics/<language-theme-page>/<metric>/window.png、字体JSON及sources.txt；保存前再次检查两杯至少两点及指标正确。原清空/交换/finally恢复全部保留，不新增或伪造采样。
- test APK构建24s成功（/tmp/task-comparison-metric-evidence-build.log），diff和分支规则通过。新四指标图尚待CI；f525db3普通横屏已success，用户/大字号仍in_progress。

### 四阶段裁切闭环与指标分段实图复核（2026-10-16）

- f525db3 single/run38888405636已核实completed/success，三个横屏profile各checked=336/pages=21/fixtures=336，列表恢复各14；普通/用户平板表单各58张，大字号56张。中文浅色编辑表单分别8/8/7张，completed/inlineObserved/imeObserved均true，noSave=true。
- 实读ci-f525db3-single用户平板chinese-form-states/zh-Hans-light-CurveEditorActivity-four-stage-table.png：3项元数据及8项阶段输入完整，第四阶段不再裁切；右侧图注、参数摘要、预览/详情及固定保存均可见。该证据关闭f3233d6遗留的第四阶段窗口缺陷，不代表阶段数量下拉框已符合认可稿的分段选择，也不代表所有字体/间距完成。
- 实读ci-fb19415-pages/landscape-screenshots/zh-Hans-light-BrewComparisonActivity.png：四指标分段按钮、单位及压力选中态实际显示，全宽图与三行双杯摘要存在；第一/第二杯、实虚线图例及未知结果提示保留。该高级夹具没有采样，0..1s空图不能作为双轨迹渲染证据。
- fb19415 single/run38889727697当前三个job均在实际窗口截图步骤in_progress；ce8c964专项run38893548072仍queued。未取消/重跑。最新萃取/分享/秤/旧版详情改动不能借本次较旧版本通过外推；仍等待对应新图。
- 本轮只更新证据和24页矩阵，不修改产品代码、不安装或操作设备、不合并/发布。

### 编辑阶段数量直接选择（2026-10-16，待实图）

- 对照curve-editor-v1，将1–4段从Spinner改为同一行192dp四等宽按钮；16sp、48dp点击区，8dp内边距。当前项采用primary_button实色/白字，避免深色accent浅色背景与白字低对比；保留键盘焦点、涟漪和本地化阶段辅助描述。
- 仍调用原stageCount/visibility/previewChanged，字段实例不重建、不清隐藏内容，初始状态不触发回调，保存与Bundle恢复逻辑不变。没有改曲线数据或设备行为。
- 业务检查改为实际点击，并增加填完四阶段后4→2→4内容保留检查；原持久化/重建/重新打开断言保留。窗口检查保留11项输入及预览完整性，增加四按钮完整、48dp、选中项断言。
- 首轮instrumentation构建因缺Button导入失败，已修正。最终Mock/test APK构建43s成功（/tmp/task-editor-stage-tabs-final-build.log），diff/branch检查通过。新增原生断言尚待CI运行；没有用旧f525db3的窗口通过替代新控件实图，整页字体/间距仍未完成验收。

### 入库日期日历入口（2026-10-16，待实图）

- 实读f525db3用户平板中文浅色BeanEntryActivity与bean-entry-v2：居中表单/三个日期行完整，但日期只有纯文字输入，没有认可稿右侧日历入口；新豆款模式仍为下拉，底部说明/间距仍有差异。本次没有缩减这些剩余项。
- 三日期输入右侧加入24dp线性向量图标、48dp独立点击区域；FrameLayout保留原输入宽度权重与行高，end padding为按钮让位，RTL采用END。焦点边框、涟漪及本地化字段描述保留。
- 日历初值取有效现值，否则今天只用于选择焦点；取消不写字段，确认写ISO日期。保留手动编辑/清空、可选值及原保存验证。销毁Activity时关闭日历，无库存写入逻辑变化。
- 业务UI检查增加打开/初值/取消/确认后原重建与持久化检查，窗口检查增加三个入口全显及48dp断言。Mock/test APK构建45s成功（/tmp/task-bean-date-picker-build.log），diff/分支检查通过；新增运行断言和实际截图待CI，不能以编译充当视觉通过。

### 电子秤单位与设备行实际截图（2026-10-16）

- 55f3f3f advanced/run38890558531 completed/success，产物ci-55f3f3f-pages。landscape/wideFont日志均明确8语言×2主题×15页=240夹具通过，preservedState/noBle=true。
- 已逐图查看中文浅色ScaleActivity普通/1.3字号截图并对照scale-v2：主读数单位g明显小于占位主值，电量/数据状态两列实际生效，长状态大字号自然换行，计时、去皮、趋势图、连接/设置入口可见。这里只证明无读数状态的层级和信息行，不证明有效数字、稳定读数、电量82%或真实去皮。
- 仍有差异：认可稿的连接设备摘要、设备型号行与分区分隔未齐；主值只有—，趋势为空，不能据此核对有数据曲线/稳定状态。图像1600×1000，不代替优先用户平板2560×1600专项；字号/间距的完整测量仍未闭环。
- 源码核实MobileSnapshot含scaleState/scaleObservation/scaleCapabilities/candidates，但无当前连接设备的名称/型号字段。候选advertisedName存在于扫描列表，不能未经身份绑定就拿某个候选名当当前型号；后续设备身份展示需从同一连接所有者提供可信关联，不写死BOOKOO。
- 最新萃取aaf1c44 capture/run38893146356及e15e00e/run38892839825仍queued；旧fb19415 single/run38889727697仍in_progress。没有取消/重启，也没有借秤页面通过外推萃取页。

### 无秤原始Mock采样支持（2026-10-16，截图未完成）

- 源码检查发现MockDeviceRuntime.sample始终返回scaleState=READY及非空BookooSample，现有capture_mock_ui.sh不能产生真正无重量来源的运行态。单纯隐藏图线不满足extraction-no-scale-v2的数据语义。
- 新增Mock内部scaleConnected状态，默认true；false时输出DISCONNECTED、weight=null、weightAt=null，机器压力/注水/温度和运行状态不变。没有增加产品设置、广播入口或真实BLE连接操作。
- 指定MockDeviceRuntimeTest 14项通过（0失败/错误/跳过），新检查涵盖断秤空闲、运行机器三项值、结束仍无重量及恢复采样。Mock/test APK构建48s成功（/tmp/task-no-scale-fixture-build.log），diff/分支规则通过。
- 这里只完成数据夹具基础，尚未将新状态接入真实开始/停止截图流程；无秤实图、主数量语义及三线实际绘制仍是明确未完成项。不能将本次单测作为视觉或硬件验收。

### 有秤/无秤真实Mock运行截图接入（2026-10-16，待CI）

- LanguageActiveCupChecks新visualEvidence模式复用真实startShot、语言重建、本杯身份/配方一致性、固定停止和安全偏好断言；只增加中文有秤/无秤两杯取证，原八语言默认模式不变。
- 等服务观察到连接状态变化后开始，采样至少5秒/两点；无秤要求全部点weightHundredthsGram=null及snapshot重量/时间戳均空，有秤要求存在重量点。机器压力/注水/温度大于零，当前主数值标签分别为秤重/注水。不是隐藏曲线或改图代替真实采样。
- 保存active-cup-visual/with-scale及no-scale的window.png、字体测量JSON、source.txt，点击既有停止并确认ENDED_OBSERVED；finally恢复模拟连接和语言/曲线偏好。此为Mock实际流程，不代表物理停水。
- Capture脚本在原浅色用户平板流程后重启Home启动Mock服务，再运行仅允许disposableCiEmulator的instrumentation入口，必须结果标记通过并拉取产物。脚本未在本地执行；bash -n/diff/branch检查通过，Mock/test APK构建23s成功（/tmp/task-no-scale-capture-build.log）。运行和图片尚待CI，不能声称无秤视觉验收完成。

### 分享分区实图与对比操作裁切定位（2026-10-16）

- abe9614 advanced/run38891161712 completed/success，下载ci-abe9614-advanced。业务17路径通过，12曲线字段/4阶段与3日期/7笔记字段持久化、重建检查均通过。
- 实读business/curve-share-four-stages.png并对照curve-share-v1：预览标题、4个阶段参数标题、同行详情及右栏底部范围说明实际归位；四阶段表格全部完整，94°C/41.7g及2.4/5.7/8.3/6.9bar对应保存数据。导出后文件分享仍禁用。该图1600×1000，不外推用户平板/四阶段大字号；图高、字体、单位排版和图标仍有差异，不是整页完成。
- fb19415 single/run38889727697终态failure，下载用户平板ci-fb19415-user。result=334/336，仅浅/深色BrewComparisonActivity指标按钮裁切：实际高108px但visibleRect高54px。实图同时显示压力双杯实/虚线（样本曲线重合）与两杯摘要，不再只凭高级空图讨论，但尚无四指标逐图。
- 定位TaskComparisonLayout先为details分配height/3再压缩toolbar，新增分段行后正常用户平板也被裁。候选先保留图表下限和48dp详情，再按自然高度分配toolbar，剩余空间给局部滚动详情；原完整可见/点击尺寸/图表门禁不放宽。新CI待验。
- 修正源码Mock/test APK构建41s成功（/tmp/task-comparison-toolbar-budget-build.log），diff及分支规则通过；这只证明构建，窗口裁切是否消除仍须新CI实图。没有安装、设备操作、合并或发版。

### 萃取标题及读数层级修正版实图（2026-10-16）

- e15e00e capture/run38892839825 completed/success，产物ci-e15e00e-capture/mock-ui-screenshots。display-profile核实2560×1600/density360/fontScale1.0，浅色启动日志实际记录capture-2 Mock开始且未发送蓝牙命令。
- 实读extraction-running-light.png并对照extraction-four-series-v3：运行指导与标题同行，不再另占一整行；时间6s/重量4.20g的单位缩小，历史参考/概览变为文字操作，停止保持右下固定。原四指标独立轴、单位、图例及当前无告警文字仍在。数据来自该次Mock，不能把6秒曲线替换成概念稿24.6秒。
- 仍未对齐：实际绘图区纵向比例偏小，图例到轴之间的解释区及全页下方固定状态/操作区占高；概念稿的设备摘要和参数分区尚不同，实际次要读数偏密。该图早于aaf1c44参数分行，不能评价后续TaskRecipeSummary。没有删除安全文字或放松图表最小尺寸来追稿。
- aaf1c44 capture/run38893146356再次核实in_progress，正在Capture tablet UI and simulated shot；不重复启动。后续拿该图核对右栏，再结合a7867fd有/无秤字体JSON分析布局预算。当前截图只有有秤，不关闭无秤缺口。

### 主图高度实际边界取证（2026-10-16，待CI）

- 检查ExtractionOverlayView的原生图例换行、48dp目标、说明行和MetricPlot轴边距，以及固定状态/操作区：不能仅凭截图把这些全部当空白删除。先记录实际测量再定位可收敛的高度。
- 有/无秤运行态截图同时保存geometry.json：content/chart/legend/explanation/plotView/dataPlot/stop屏幕像素边界、density/fontScale及绘图区占图表高度比例。dataPlot取已绘制RectF而非layoutParams估算，要求实际可见非空。产品字体/门禁/布局不变。
- instrumentation APK构建23s成功（/tmp/task-live-chart-geometry-build.log），diff/分支检查通过。尚无新geometry产物，不宣称已测得比例或已放大主图。aaf1c44 capture/run38893146356刚完成success，正在下载用于核对参数分行。

### 萃取右栏参数实图与固定操作区域修正（2026-10-16）

- aaf1c44 capture/run38893146356 completed/success，下载ci-aaf1c44-capture，已实读用户平板extraction-running-light.png：四行参数、分隔与右对齐值真实生效；“不使用（由咖啡机按水量结束）”完整换行，缺豆/粉量仍—，设定92°C与实时93°C区分。文字操作和停止完整可见；图高仍不足，不宣称全页通过。
- 实图和源码对应发现root级TaskActionDock虽只绘右侧按钮，却让左图也损失整个操作行高度。新增专用TaskExtractionWorkspace：宽横屏主区全高，右侧ScrollView+固定footer；窄横屏仍主区/局部工具区/固定底部动作。原30%/280–320dp右栏和24dp间距、原按钮实例/回调、点击尺寸保留，不改共享TaskWorkspaceLayout或其他页面。
- 准备/通知/告警三行移到workspace之前，全宽固定并保留原未知/过期/可见逻辑，不进入工具滚动区。新增实际运行取证断言：停止完整可见、宽窗图表底部进入停止所在纵向区域；不以隐藏状态说明放大图表。
- 新布局尚待CI截图、geometry.json及大字号/短高回归；旧aaf1c44图不能覆盖本改动。无安装、设备操作、合并或发版。
- 最终源码Mock/test APK重新构建40s成功（/tmp/task-extraction-side-dock-final-build.log），diff/分支规则通过。没有把编译作为新增窗口断言通过。

### 批次与用豆非空截图审计（2026-10-16）

- 实读abe9614业务batch-after-adjustment.png：调整后90g、初始100g、90%、三日期及调整-10g/入库+100g真实显示；原业务17路径已验证持久化，不再只以缺失批次框架图判断。对照bean-batch-v1，剩余重量层级弱、比例条与数字分行、缺批次信息标题和分隔。
- 候选将剩余重量32sp/700、单位0.55，摘要比例1:1:2，进度条/百分比同行；新增20sp批次信息标题及摘要/日期/流水细分隔。原值、日期、调整确认、幂等和详情入口不改。八语言标题XML解析通过。
- InventoryEvent源码没有时间字段，认可稿流水时间列尚无数据源，保持类型/变化/原因，不用购入日期或当前时间伪造；该缺口仍待后续设计与数据迁移规划，不冒称已完全匹配。
- 实读abe9614高级BeanPreparationActivity：有250g库存批次但粉量为空，称豆被当前活动/恢复状态门禁阻止，页面为下拉+输入而非认可稿左列表/右选择；不能将此图当成已确认扣减状态。须补选择/确认实图并调整构图，称豆门禁不能为了匹配稿件去掉。

### 用户平板新实图与取证启动修正（2026-10-18）

- 构建会话65749已返回exit 0；/tmp/task-resume-batch-capture-build.log记录Mock与test APK BUILD SUCCESSFUL in 23s。shell语法、diff与branch-policy重新通过。批次新层级仍待CI截图，不以编译替代视觉检查。
- 实读ci-35db865-user的result.txt：336 fixtures/21 pages，58中文表单截图，14列表恢复fixture。实读pressure/window.png与sources.txt：两杯各31点，四指标按钮完整显示，不再是旧图半高裁切；底部摘要三行可读，图中两条压力线重叠，不能声称有明显数值差异。其他三指标和深色尚待逐图审查。
- 实读中文four-stage-table.png：1–4阶段分段控件已生效，选中4、四阶段8输入和顶部3字段完整，右侧预览/保存完整。实读BeanEntry-section-2.png：三日期日历入口均完整可见；仍是新豆款下拉而非认可稿双模式切换，不宣称入库页完全一致。
- capture旧失败位于LanguageActiveCupChecks初次等待machineSettingsFresh。app-logcat记录am instrument杀死预先启动的Home/Mock服务；Extraction只绑定已有服务。现改由visualEvidence测试进程先启动Home并等待服务，再进入Extraction；原8语言路径、超时、数据与停止断言不放松。脚本失败时拉取已有取证输出。运行态修复尚待新CI。

- 补读同批comparison-metrics：浅色water/temperature及深色weight/window.png，单位分别ml/°C/g、选中指标与曲线切换一致，按钮完整。与brew-comparison-v1对照仍有实质差距：顶部选杯卡和说明占高较多，图例不在图表右上，配色非概念稿蓝色，表格边框偏重；不能因裁切修复就关闭整页。全部四指标fixture轨迹重叠，只证明真实双序列渲染，不证明不同曲线的可辨识性。
- 09946fa与784eaaf已普通推送feature/task-first-ui。截图39081548229、单屏39081551257、业务39081551245等已由GitHub返回in_progress；保留运行，不取消/重启。未安装或操作设备、未合并/发版。

### 入库双模式与独立草稿（2026-10-18）

- 依据bean-entry-v2替换混合下拉：两个等宽16sp模式按钮，已有模式才显示豆款选择，无库存禁用已有选择。选中浅色背景、键盘焦点边框、48dp以上点击区；日期前细分隔。
- newNameDraft与rememberedBeanId分离，模式/名字/真实ID随Bundle恢复；异步Spinner回调在新建模式不写身份。原日期/重量/事件ID/库存保存与幂等不改。
- 业务检查新增新→已有→重建→新→重建→已有，核对只读名称与真实Bean.id，取消后事件和余额不变；两种状态分别截图。表单检查要求两个模式按钮完整且至少48dp，不放松原五字段可见性。
- Mock/test APK最终构建exit0，22s（/tmp/task-bean-mode-final-build.log）；8语言XML解析、diff、branch-policy通过。新增instrumentation尚未运行，本地未安装；需新CI确认完整字段、大字号/短高与两种模式实图，不宣称与生成稿完全一致。

### 批次实图确认与对比顶部候选（2026-10-18）

- 784eaaf业务run39081551245 success，下载ci-784eaaf-advanced-business。business-flows记录17路径通过、三日期/7备注/12曲线字段/4阶段与三个持久化仓库检查。实读1600×1000 batch-after-adjustment.png：90g主值+小单位、100g初始、90%和同行比例条、批次信息标题/日期/流水分隔均生效，底部操作完整。未以该标准横屏截图替代用户平板大字号验收；时间列数据缺口仍在。
- 对比页按brew-comparison-v1将交换动作放两杯之间，说明改14sp普通字重/4dp上下间距，图例移到指标之后右对齐。两条曲线统一chart_pressure蓝色，保持实/虚线和孤立点形状；轴标11→12sp。无采样/投影/量程/状态门禁改动。
- 最终Mock/test APK构建exit0/38s（/tmp/task-comparison-header-final-build.log）；diff和分支规则通过。原四指标全控件可见性、真实杯身份交换、绘图区最小高等断言保留，新增布局未取得CI实图前不关闭整页差异。原capture39081548229、single39081551257仍in_progress，未取消或重启。

### 有秤/无秤真实运行截图与正常状态行（2026-10-18）

- capture39081548229（784eaaf）success。已下载ci-784eaaf-capture并实读active-cup-visual/with-scale及no-scale/window.png、source.txt、geometry.json；ACTIVE_CUP_VISUAL_PASSED states=2 actualMockStartStop=true noBle=true。启动修复已获得运行证据，不再只是编译通过。
- 两种状态各21个实际Mock采样：有秤21个重量样本、主读数3.50g；无秤0个重量样本、snapshot重量/时间为空、主读数累计注水3.0ml，图例/纵轴无重量项。四/三指标同图，机器93°C与配方92°C区分，停止完整可用，结束由原测试实际调用，不属于硬件验收。
- 用户平板2560×1600 density2.25 fontScale1：内容高1402px、chart高874px、真实dataPlot高514.148px，占chart58.827%；图表底部1447px超过停止顶部1321px，固定操作区域不再占左图整行。此比例是测量值，不是视觉一致通过阈值。
- 实图“当前无告警”仍占主读数上方整行，与认可稿不符。候选扩展原紧凑运行页隐藏条件至全部运行布局：只收起fresh<=1500ms且bits精确0的正常状态；任一非零位、未知、缺失/未来/过期时间或非active仍展示。控制权限/告警描述/未知结果/停止逻辑不变。增加纯判定边界测试及实际运行无告警行收起断言，几何增量待新CI实图。
- 最终构建会话2716 exit0/46s，MachineAlarmsTest报告7 tests/0 failures/0 errors；Mock与test APK成功，diff/分支检查通过。未本地安装或硬件操作。

### 入库模式运行证据与测试计数修复（2026-10-18）

- ff651be/run39081920735：pages job success，business job failure。下载ci-ff651be-business；business-flows含18条已执行路径，包括entry-mode-draft-recreate-cancel及所有原17路径，异常精确指向691行末尾paths.size==17。不能把整次business宣称成功，末尾恢复不变量仍须重跑确认。
- 实读bean-entry-new-mode.png与existing-mode.png（1600×1000）：两个模式直接可见、浅色选中态/日期分隔生效；新名称草稿保持Unsaved new bean、重量250；已有模式显示所选实际Bean，原ID/只读名称与新→已有→重建→新草稿恢复断言均在该路径记录之前执行。既有名称在选择器与只读字段重复仍属后续简化空间；不以此标准横屏截图代替用户平板大字号/IME检查。
- 将Kotlin末尾计数和完整字段标记、shell成功标记统一为18（增加要求，不删断言）。test APK构建exit0/24s，shell语法、diff、branch-policy通过。测试包尚待新CI完成收尾，不绕过失败。

### 用豆批次列表与三状态取证（2026-10-18）

- BeanPreparationActivity新选择态按bean-preparation-v1改近等宽两栏：左侧可滚动真实库存批次按钮，右侧所选豆款/粉量/库存预览/称豆门禁。20sp标题、16sp行、72dp最小行高、8dp行距；选中浅色与键盘焦点样式。选择按真实batchId，点击不保存/扣减；原保存→确认/未知恢复/取消/丢弃/重复确认路径不改。
- 业务检查从Spinner实现依赖改为真实批次按钮选择；补草稿、待确认、已确认流水截图，不增加路径计数或删余额/事件断言。最终Mock/test APK构建exit0/24s（/tmp/task-dose-list-final-build.log），diff与branch-policy通过；待新CI实图，不宣称完整复刻（粉量步进/大号库存前后值尚未对齐）。
- 784eaaf单屏run39081551257失败已下载原始结果：335/336，仅ar-light-HistoryDetailActivity报missing populated historical chart。该提交早于本次用豆改动；需独立检查异步加载/实际状态，不当作新用豆故障，也不放松曲线存在断言。其他335检查不能替代全矩阵通过。

### 历史异步取证与入库日期裁切修正（2026-10-18）

- 实读784eaaf失败ar-light-HistoryDetailActivity.png，图表无数据且仍显示读取本地样本；HistoryDetailContent通过后台reader+chart.post赋值，旧测试只固定等300ms。SingleScreenWindowRun现按样本>=2且布局稳定等待（上限10s），超时带点数/页面状态，原图表存在/96dp绘图区断言不变。产品代码无加载策略修改。
- ff651be/run39081920801用户平板结果334/336，仅中文浅深BeanEntryActivity失败；原始日历108×108只露43px。实图确认第三日期被保存区上方裁掉，属于实际布局问题，不是等待问题。已回收模式底距8dp、分隔边距24dp和名称/数量标签顶部共8dp，合计40dp；字体/字段/48dp日历/保存按钮不变。保留单屏五输入/三日期入口完整检查。
- 最终构建93646 exit0/45s（/tmp/task-history-wait-entry-spacing-build.log），Mock/test APK成功，diff/branch-policy通过；两个修正都待新CI运行，未宣称裁切或异步取证已实图通过。

### 用豆库存前后值视觉层级（2026-10-18）

- 新DoseBalanceView按bean-preparation-v1将前后值分为两列14sp标签、40sp/700数字、0.55单位，后值accent、中央方向箭头；RTL方向对应左右阅读顺序。下方保留原完整“预计…尚未扣减”或“本次扣减流水”说明及取证tag，区分预览与已持久化流水。
- 草稿非法/空/超库存时整块隐藏，不以0冒充；待确认仍读余额计算，已确认/关联恢复仍只从匹配eventId/batchId/CONSUME/amount的实际事件还原前后值。组件不读写库存，操作门禁/幂等/原业务断言未改。八语言标签XML已解析。
- 已有三状态运行截图将覆盖此展示；未取得本候选截图前，不宣称字号、间距和长数值可见性已验收。
- 最终构建61147 exit0/22s（/tmp/task-dose-balance-final-build.log），Mock/test APK成功；diff/branch-policy通过。无安装、设备写入、合并或发版。

### 萃取空间实测回收与对比分离样本（2026-10-18）

- 1b0deb4/capture39082450018 success，ci-1b0deb4-capture两种运行图与geometry/source已读；正常状态行确实消失，停止126px高仍完整，三/四指标正确。绘图区514.148→624.148px，净增110px；chart内占比58.827%→63.42968%。主读数/工具行向上移动，未知/过期告警由原边界测试覆盖；不把Mock当硬件验收，也不宣称整页完全匹配。
- 051f16e单屏39082233253用户结果334/336，失败仍仅旧中文BeanEntry第三日期裁切，已由166594d修改待复验；本次实读pressure/window.png确认两杯之间交换动作、右上图例、蓝色和12sp轴标生效。头部占比、摘要粗边和字体细节仍待最终对照。
- 原两杯fixture完全相同，实虚线重合，不能以该图证明差异可辨。只在instrumentation新增v2命名31/33点两杯，30s/36g与32s/35.2g，四指标均不同；Observation与样本末值/时长一致，旧命名及用户记录不覆盖。增加两投影不同且非空的断言、sources版本/时长记录，不改产品曲线算法。分离样本截图尚待CI。
- 新测试包89311 exit0/25s（/tmp/task-distinct-comparison-build.log），diff/branch-policy通过；构建不等于分离轨迹已实图可辨。

### 业务收尾通过与对比轴检查基线（2026-10-18）

- b6fc53c/run39082670718 success。下载 ci-b6fc53c-business 并读取 business-flows.txt：18 路径、三日期/7备注/12曲线字段/4阶段、三个持久仓库及最终 preservedMachinePrefs/sameService/noBle 均有成功标记；旧计数错误已关闭。该提交早于用豆新布局，不替代新布局实图验收。
- 166594d/run39083184345 的 task-frame 失败精确为 TwoCupChartChecks 对 bar 文字仍要求 11sp，产品已在051f16e改12sp。仅将独立预期同步12sp，保留字体缩放精确比较及全部边界/重叠/单位/数据检查。产品未改，CI复验仍待完成。
- 本地测试包构建会话61986 exit0，BUILD SUCCESSFUL in 21s（/tmp/task-comparison-axis-check-build.log）；diff/branch-policy通过。未本地安装，字号1.0/1.3运行断言待CI。
- 读取1b0deb4实际有秤运行态 ExtractionActivity-typography.json（density/scaledDensity=2.25，fontScale=1）：标题24sp/500，时间与秤读数40sp/700，辅助标签14sp/400，压力及机器温度18sp/500；停止16sp/500、padding16/8/16/8dp。固定中文字体源记录为Noto Sans SC 2.004-H2。符合对应字号层级不等于生成稿字体被精确识别，整页比例与其余页面仍未最终验收。

### 有数据复盘与导入预览逐图审查（2026-10-18）

- 实读 ci-b6fc53c-business 的 review-saved-notes.png，与 brew-review-v3 并排核对。左右参数/记录成立，30s/36g/已观察结束、三段实际保存文字和底部保存均可见。原图使用长UUID测试内容，不应把测试文本当产品默认文案；研磨字段长内容需内部滚动。右上另有分类选择、分区线/底部“仅保存复盘”可见说明不足，不能宣布一致。此图1600×1000，非用户平板最终对照。
- 同批 import-new.png/import-conflict.png 与 curve-import-preview-v1 对照：四阶段实际2.4/5.7/8.3/6.9bar、94°C/41.7g、四行水量12/23/34/45ml完整；新导入无冲突动作，冲突态查看/保留/另存完整。图表量纲正确，但右侧缺暖色警示区域，左侧缺稿件分区标题/边界，左右宽度和图高仍不同。
- 本候选仅修冲突文案视觉分组：20sp、16dp内边距及组间距，独立浅深暖色背景/边框。保留原非颜色文字说明和全部保存/身份/无自动启动行为；不因部分差异修正就关闭整页。
- 候选构建会话14951 exit0，Mock/test APK BUILD SUCCESSFUL in 40s（/tmp/task-import-conflict-emphasis-build.log）；浅深色XML解析、diff/branch-policy通过。未安装，实际警示区/大字号空间仍待CI截图。

### 复盘分区与保存范围可见（2026-10-18）

- 对应brew-review-v3，补上下文底线、两个标题底线与参数/笔记栏间线（1dp语义边框色）；保持原24dp栏间距，已有独立滚动区和输入不重新挂载。笔记高度预算显式加上标题底线高度。
- 保存区显示现有八语言journal_subtitle，14sp辅助色；>=624dp与右侧320dp保存区同行，其余上下排列。提示不再只在信息弹窗里，用户可直接知道保存不改变机器/库存。保存、草稿、焦点、重建逻辑不变。
- 最终构建56213 exit0、Mock/test APK BUILD SUCCESSFUL in36s（/tmp/task-review-section-final-build.log），diff/branch-policy通过。既有业务重建/保存/磁盘重开断言保留；新实图和IME/长文本/大字号仍待CI，不以构建替代运行。

### 旧版详情/曲线与流水的用户平板实图（2026-10-18）

- 对照legacy-history-detail-v1，实读ci-051f16e-single-user中中文浅深LegacyHistoryDetailActivity.png。左bar 0–12、右ml/s 0–6及0/0.5/1/1.5/2s刻度完整；重合轨迹仍能看见蓝实线与绿虚线，旧版“只有水流可见”和缺轴刻度缺口在该夹具中已关闭。样本仅2s、摘要30s来自不同原字段，未改变采样来伪装概念稿走势。不是硬件采样验收。
- 同批实际字体JSON：density2.25/fontScale1，页面标题24sp/500，历史采样与来源标题20sp/500，说明14sp/400，详情16sp/500；详情触控高108px=48dp。该三个legacy源码文件从051f16e至f49ecb1无差异，仍不以此推出其他页面/字体比例完成。
- 与认可稿仍有明确差异：右侧未拆分来源、缺失数据和限制，而是一段长说明；图例两行而非一行；未有同状态完整比例/字距验收。下一步应分组已有真实语义，不添加虚构温度/秤值，也不改只读边界。
- 实读同批LegacyCurveActivity.png：40条连续列表成立，未选中时右侧重复只读说明，导入动作仍占满整行。字体JSON测导入按钮16sp/500、2398px宽/108px高；需收为紧凑次级工具并明确未选中状态，后续需选中详情图，不能以列表完整关闭该页。
- 实读BeanLedgerActivity.png：五列类型/变更/变更后/批次/原因对齐，最新入库事件+250g与余额250g可读，列表延伸至底部。不足：该图只有入库，尚不能覆盖扣减、调整或长原因；事件模型无时间字段，不得伪造概念稿时间。
- 本轮仅更新证据与矩阵，未改产品布局、未启动额外CI。现有39083184371/39083440522仍in_progress，39082950054/39083695169/39083443733仍queued；未取消或重启。

### 旧版曲线紧凑导入与明确未选中态（2026-10-18）

- 补读051f16e的legacy-curve-selected-restored.png：选中第4项恢复后，真实93°C/70ml/36g/2段及分类、原位置、来源完整；详情仍是17sp同权重段落而非稿件四参数分组，不宣布整页一致。原恢复测试比较整个详情文本，不删改该断言。
- 按legacy-curve-v1把数量与导入放入同行工具栏（>=624dp），导入用次级文本样式、最多320dp宽，原最小点击区保留；窄窗上下排列。只改展示，不动文件选择器/import/指纹/恢复。
- 未选中详情改八语言“选择一条曲线查看参数”；标题和数量行仍提供只读限制，选中后原完整参数及限制不变。消除一处重复说明，不隐藏能力边界。
- 构建71660 exit0，Mock/test APK BUILD SUCCESSFUL in50s（/tmp/task-legacy-curve-toolbar-build.log）；八语言XML、diff/branch-policy通过。新工具栏运行和浅深/大字号实图待CI，未安装或操作设备。

### 新运行态证据与历史数字/单位层级（2026-10-18）

- 63a490c/capture39083440522 success，下载ci-63a490c-capture。读取ACTIVE_CUP_VISUAL_PASSED states=2 actualMockStartStop=true noBle=true；实读有秤图（20样本均有重量）、geometry，四指标同图、5s/3.50g、停止126px完整，dataPlot高624.148px与前次一致。本次没有重新证明硬件停止。
- 实读同批history-detail-dark.png并对照history-detail-v2：真实Mock结束32.2秒/22.40g、123采样点、四条轨迹、复盘入口均存在；当前空复盘应显示未填写，不能填造示例豆款。数字单位与主值同大、图表明显偏矮、右栏没有分区线，均未最终对齐。
- 本候选只对独立历史页两个主读数缩小单位至0.55倍，数字保持40sp/700；数字前缀匹配兼容日/韩无空格单位，未知破折号不改变，全文语义不变。图高问题不能由此声称修复，后续核对workspace与独占底部操作区。
- 最终构建34161 exit0，Mock/test APK BUILD SUCCESSFUL in25s（/tmp/task-history-metric-units-build.log）；diff/branch-policy通过。新增单位实际呈现及八语言/大字号仍待CI，未安装。

### 历史详情释放左图底部空间（2026-10-18）

- 根因在HistoryDetailContent：独立详情使用TaskWorkspaceLayout，primaryDock另挂在body末尾，因此整行高度同时从左图扣除。复用已有TaskExtractionWorkspace并setFooter，将固定复盘按钮留在右栏，笔记仍独立滚动；集合预览不改。未修改共享布局或图表投影。
- 右侧三个参数行与后三项笔记补1dp边线，文字/字号/原间距/详情入口保留。未知结束提示和所有未填写字段不隐藏。
- 原SingleScreenPageChecks仍检查固定按钮全可见且>=48dp、图表全可见且绘图区>=96dp；多语言/大字号/短高继续验。需新实际图确认释放高度，未在未截图前宣称图高已验收。
- 最终构建52934 exit0，Mock/test APK BUILD SUCCESSFUL in24s（/tmp/task-history-workspace-height-build.log），diff/branch-policy通过。无安装或硬件操作，视觉/运行验收等待新CI。

### 用豆三状态实图与过期错误反馈（2026-10-18）

- 8c16949/run39082950054 success；下载ci-8c16949-business并读取18路径/收尾不变量成功标记。实读dose-selected-draft、pending-confirmation、confirmed-receipt（1600×1000）：选择态左列表/右粉量、真实18g/90→72g成立；保存态明确尚未扣减，确认态明确本次流水及已扣18g，取消/放弃/返回完整。该提交早于DoseBalanceView，不能验收后来40sp前后值或用户平板大字号。
- 截图发现选择态18g合法且预览正确时，底部仍显示前次18.0001的红色错误。TextWatcher只刷新预览导致过期反馈。候选仅在可解析正数且errorResource精确为dose_bad_amount时清除；保存、确认、称豆及恢复错误保留。不新增任何写入。
- 回归在原流程增加18.0001报错→0仍报错→18无该错误，保留预览、未保存/未扣减和后续重复确认幂等检查。三状态构图仍未完全贴合：保存/确认改为摘要卡，不是概念稿连续列表上下文；仍不能关闭整页。
- 最终构建5143 exit0，Mock/test APK BUILD SUCCESSFUL in42s（/tmp/task-dose-validation-feedback-build.log）；diff/branch-policy通过。新增反馈断言未在本地设备执行，CI实图回归仍待完成。

### 入库日期裁切与历史加载复验（2026-10-18）

- 166594d/run39083184371三个横屏job均success。下载ci-166594d-tablet-user-landscape与tablet-large-font；各自result.txt均checked=336/pages=21/fixtures=336，14列表恢复fixture；中文表单截图分别58/56，入库浅深结果completed=true/sections=3/imeObserved=true/noSave=true。
- 实读用户平板中文浅深section-2及大字号浅色section-2：五输入、三日期行和日历入口完整，原第三日期裁切已关闭；用户平板density2.25/fontScale1，大字号JSON确认density1.25/scaledDensity1.625/fontScale1.3。大字号证据不能冒充用户设备相同密度。BeanEntryActivity从166594d至c4130ee无源码差异。
- 历史加载等待修正这一版已通过原336夹具，不再有此前ar-light-HistoryDetailActivity固定300ms造成的空图失败。后续af9a6d4历史布局不包含在该证据中，仍须单独回归。
- 新疑点：用户平板深色section-2图中保存按钮文字未显，而同轮zh-Hans-dark-BeanEntryActivity.png和section-0正常。实际字体JSON含保存批次16sp/500、bounds[1550,1285,2090,1411]、padding16/8dp。代码白字状态、几何与其他帧不足以确定单帧缺字原因；不能把该图判为整页视觉通过，也不据此盲目改产品配色。保留为取证/渲染待查项。
- 本轮仅收证更新，未新增产品变更或CI；63a490c高级页面仍排队，大号库存前后值仍待实际图。
