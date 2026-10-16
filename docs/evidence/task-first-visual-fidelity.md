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
