# M5 eb77b2f 原图复核（进行中）

本次已实际查看四页 × 浅深色 × 1.0/1.3 共 16 张主 PNG，并与批准的四页原稿对照。60 张特殊图、所有实际字体/Canvas/颜色与滚达审查尚未完成，不能记全组通过。源码 head eb77b2f，实际 PR merge/source 6ee51623e7f32f6df425e0d84313d1bc5e7f6bed，CI 39196410451。原包 SHA256 2ef8e6c65224b19869e1f107d763d24f3c8b49afe62e5da2625b0dfdbfd2474a；根已核 895 项 CRC、16 主 manifest 的 840 文件哈希引用。

原件根：`build/task-first-evidence/ci-eb77b2f-m5/raw/6ee51623e7f32f6df425e0d84313d1bc5e7f6bed/`。本记录不外推后续 49a1b46，尤其 Comparison 自然按钮高度另有新修正。

## 当前确定结论

History 标准浅/深 1.0 的时间轴仍被首屏 viewport 截断，G1/G3 FAIL。两主题 chart rect 均 `[1193,645,2524,1306]`，visible `[1193,645,2524,1267]`，缺 39px，原图亦可见时间轴标题被截。列表数字/单位现在保持同组，旧单独 g 换行在该主夹具已消除。

History 1.3 的 chart rect `[1193,736,2524,1475]`，visible 到 1267，缺 208px。**此事实本身不构成 FAIL**：M5 §10.4 已允许 1.3 首屏出现真实 plot，图/notes 全文局部滚达并恢复。首条口头观察将 1.3 与标准 1.0 合并判失败的范围已在此纠正；仍需核 ≥96dp 真实 plot 和完整滚达，当前 EVIDENCE_PENDING。

Comparison 四主图的主绘图区已恢复主次关系，1.3 摘要区初始只露部分内容须结合后续滚达判断；不能自动当作截字失败。表格整格描边仍与既有批准的水平分隔层次不同，是明确未关闭项。详情及萃取的主构图、实际图和固定动作在已看主图没有新增明显视觉阻断；G2/G3/G4 与特殊态尚未完成，不授 LIMITED_PASS。

## History 最小封闭修正规格

沿 M5 §10.4，禁止继续按差几 px 削 padding/字号。仅 embedded 分支重排完整辅助说明：

1. 将图中“各项独立量程 · 本杯采样至 … s”辅助说明移到同一永久 preview content 的完整图之后、notes 之前。当前实际自然单行高 46px，1.3 为 61px；标准 1.0 图仅差 39px，移出该完整行预计可释放足够空间，但结论必须由新自然测量与 PNG 确认，不硬减 39px。
2. 全文与同一 chart 的真实采样/状态同步，保留原语义及检查交互；不是隐藏/删掉提示，也不在 resize 重挂子树。可以显式 opt-in 把该说明的现有渲染内容同步到永久外部 TextView，默认路径保持原位置。具体接口由 owner 最小实现，不复制业务投影逻辑或造截止时间。
3. 原图例指标/单位、48dp 交互、14sp 轴、真实 plot≥96dp、原 16/8 间距、40/40/20 数值与14单位、56/48 footer 全保。标准1.0 首屏包含完整时间轴及轴墨迹；1.3 仍允许局部滚动，真实图、此说明和 notes 全文可达并精确恢复。
4. 独立 HistoryDetail、Extraction 与组件默认路径不继承说明外移，不修改同源时钟、采样、图表范围、曲线/记录身份或控制。
5. 既有 EmbeddedHistoryPreview.productLayoutEvidence 必须真正复制进当帧取证 JSON；本包尚未找到 chartMinimumPx 等账本。新增字段只取已存在实际预算，不用估算替代图审。记录上方 chrome、legend、说明、真实轴带、plot、viewport、footer 与总预算。
6. 精确测试适配：标准 1.0 初态对 chart 整框和实际轴文字墨迹的屏幕可见区作完整检查；1.3 对实际 plot 下限及整图/说明/notes 的各自局部滚达与恢复检查。不能把标准初态断言挪到滚后；不能删除原16主/60特殊或协议2。

最小生产范围：`HistoryDetailContent.kt` 的 embedded composition/预算，以及负责现有 chart 辅助说明的 `ShotChartView.kt`（必要时其当前 `ExtractionOverlayView.kt`，仅显式 embedded opt-in，先核实际所有权）。不得顺手改独立详情、萃取、Comparison。最小测试范围：现有 `ExtractionHistoryVisualMigrationChecks.kt` 的 History 初态/滚达及只读预算采集（实际文件名以仓库为准）；如采集统一函数位于别处，先向根报告确切函数，不改公共协议 helper。修改后由根协调编译、原回归和新 CI，再由 Astra 复核原 PNG。

分支沿 feature/task-first-ui → develop PR#4，遵守 docs/git-workflow.md。不安装、设备操作、推送、合并、签名或发版；本记录仅授权范围内审查，根统一文档提交。回退追加修正，不改原证据。

### 实看进度补记

追加实看 dark/font1.0 的 HistoryDetail 五张特殊态原 PNG：missing-samples/no-notes/unknown-observation/sample-gap/water-reset。空采样空图及说明、未知徽标和未确认提示、采样缺口断线、累计注水重置断线均实际可见；尚无该组新增视觉阻塞。合计已实看 16 主图及20/60特殊态；另外40张和 G2/G3/G4 逐项证据审计仍未完成，不宣称整批通过。

继续实看：深1.0 History/Comparison十张及浅1.3 HistoryDetail五张完成，当前16主+35/60特殊。深色History仍有时间轴底裁切，Comparison整格边框待办仍在。浅1.3独立详情未知状态额外警示占高后真实plot较浅，须结合Canvas预算核最小高度，不凭截图粗估直接关门。

## 原 PNG 全量实看完成（不等于四门完成）

四profile × 三页 × 五特殊态60张，连同16主图现已逐张实看。未知警示/破折号、空采样、无notes、gap与reset分段符合所见fixture，不把CI绿色代验。History标准1.0轴裁切与Comparison整格边框仍开放；1.3局部滚达不能只从首屏裁切判失败。

新增真实预算实证：独立HistoryDetail UNKNOWN/font1.3浅深均 Canvas `operation=plot` bounds `[275,127.875595,1310,279.87402]`，真实plot高151.998px，density2.25即67.55dp。其 `chart-plot` View rect `[216,863,1778,1350]` 高487px；旧single只核View高96dp会漏掉真实plot被轴带占用。此独立特殊态须核批准最小预算并定精确修正，不能被只改embedded History的候选关闭。Comparison UNKNOWN1.3 toolbar tab下半首屏折叠，须核获准局部滚达及恢复，未仅凭此判FAIL。

字体/Canvas全量角色、实际line boxes、颜色及滚达审计仍未完成，暂不新增LIMITED_PASS。

## Comparison 表格层次最小修正

仅 `ComparisonSummaryTable.kt` 的 `visual != null` 路径去常态cell四边框，保持统一surface。在表头后及相邻数据行之间绘一条1dp既有palette divider水平线，不加外框、竖线或双线。列权重.7:1:1、H8/V4、字体、float14sp单位及同baseline、taste48dp触控不变。taste全文dialog/tooltip/disabled语义和原listener不变；focused可保accent焦点框，pressed保selected ripple，常态不恢复边框。`visual == null`默认路径保留。该项是已批准水平分隔层次的收口，不是新业务。两主题/四主图及taste focus/pressed需原图复验。

## 独立详情 UNKNOWN/font1.3 真plot不足的修正边界

67.55dp真实plot归为G1图预算FAIL，与embedded时间轴39px缺口分开。最小路径先测独立详情三metric的真实纵向预算和同排自然宽，复用永久metric cell的条件同行：只有纵排自然预算不足以容纳96dp真实plot及其同源实际轴带，且三cell inline自然宽+原24dp组距足宽时切换；足高保持纵排。保40/40/20数值、14单位、label与quantity的8dp间距、16dp图间距。独立原说明、warning、legend、轴、状态全文和notes不删、不外移，不把embedded opt-in套给独立/Extraction。

当前只授权先核该布局回收是否足够；若自然宽不满足或同行后仍不足，不硬缩、不假报96、不预滚，先回报实际剩余预算再定永久局部viewport方案。最小生产预计仅 `HistoryDetailContent.kt`（必要的只读真实轴预算API先报告）；不修改samples、reader、永久记录、量程或公共renderer默认预算。后续取证必须使用实际Canvas plot内框和屏幕可见plot，不使用chart-plot外View高代替。主态与五特殊、两主题字号分别复验；新独立修正不自动关闭embedded History。

## G2–G4 逐项证据审计结果（eb77，已执行而非待审）

共读取76份主/特殊态typography：Extraction 4份188个TextView记录、History 24份1402、独立详情24份996、Comparison 24份916。所有已采TextView无fakeBold/斜体模拟；16主图textScaleX均1、letterSpacing均0、includeFontPadding均false。主图实际角色在1.0为14/16/20/24/40/48sp对应31.5/36/45/54/90/108px；1.3实际系统非线性换算为42.3/45.45/53.100002/59.399998/90/108px，不能将大值108未乘1.3误报锁字号。萃取单位22sp为49.5→56.25px。重量/时间字重700，正文400、操作/标题500，辅助与单位400；History20sp水量为400。

四主profile实际Canvas text记录：Extraction112、History176、独立详情192、Comparison36；均14sp实际31.5/42.3px、weight400、fakeBold=false、matchesAssignedTaskTypeface=true，关联Noto资产SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。此为实际Paint与已分配字体关联，不冒称平台每个glyph的内部font provenance。数字probe等advance仅证明该probe等宽，不宣称tnum已启用。Comparison单位span共44个，22个31.5px、22个42.3px，全部400且无伪粗；旧整数截断未复现。

主图baseline实际记录Extraction20组、History12组、独立详情12组，44组number/unit屏幕baseline精确相等。Comparison独立baseline数组为空，但number/unit为同一TextView的actual metric run，不能伪报独立baseline组数。16主图已完整可见TextView中未发现layoutHeight>contentHeight或ellipsis；这不包含屏幕外滚动内容，也不能抵消History Canvas时间轴裁切。

16主PNG app区域实际纯色频数与全图实看相符：页底浅#F9FAF9/深#11191A，surface浅#FFFFFF/深#1B2526，分隔浅#DDE3E7/深#3B4B4C，选中浅#E2F0ED/深#203D3B；萃取停止底#B5302D，详情主要动作#087A78。实际TextView主辅色浅#101820/#657183、深#EDF3F2/#A9B9B8。History与独立UNKNOWN警示实际文字浅#795119/深#EACB87。Canvas四系列实际色与冻结压力蓝/水青/秤深青/温度琥珀一致，两主题分别解析；未以颜色频数反推文字字体或用背景众数证明所有小色块。

| 页 | G2字体（本次已采范围） | G3字号/行盒 | G4颜色（本次已采范围） | 尚不能外推项 |
|---|---|---|---|---|
| Extraction | LIMITED_PASS：4主态实际Paint/字重/单位/数字probe相符 | LIMITED_PASS：4主态角色、20组baseline与可见行盒相符 | LIMITED_PASS：4主态页底/主辅字/系列/停止色相符 | 未采运行状态的focus/pressed/disabled和所有业务状态不在本限定结论 |
| History | LIMITED_PASS：4主+20特殊已采文字角色无新字体差异 | FAIL：标准1.0真实时间轴截断；1.3轴/notes滚达仍缺精确原件 | LIMITED_PASS：4主+20特殊可见选中、warning、曲线色相符 | 旧列表非零恢复不是右侧preview图/notes滚达，不能混用 |
| HistoryDetail | LIMITED_PASS：4主+20特殊角色及12组主态baseline相符 | EVIDENCE_PENDING：已可见行盒无新裁切；完整notes各自滚达/恢复未在本包证明；UNKNOWN图浅属独立G1 FAIL | LIMITED_PASS：4主+20特殊可见正常/UNKNOWN/缺样本色相符 | 真plot67.55dp不能由外框487px或G2/G4通过抵消 |
| Comparison | LIMITED_PASS：4主+20特殊，44个float单位span相符 | EVIDENCE_PENDING：可见行盒无新裁切；1.3 summary/UNKNOWN toolbar滚达与taste全文目标仍需状态证据 | LIMITED_PASS：已采主辅/选中/图系列色相符 | 整格边框是G1层次FAIL，非颜色数值FAIL；focus/pressed未采不外推 |

这里LIMITED_PASS只在列明已采视觉状态成立，不将缺失的glyph内部身份或tnum激活声明补成已知，也不是整页全业务/全部交互状态最终PASS。

### 明确的证据缺口而非继续泛称待审

已读History `history-business.json`：仅local/journal位置12、top−18、selectedId、recreateRestored，states四目录也仅local/journal nonzero-selected/recreated。它们不证明preview右侧graph/说明/notes滚到底及恢复。Comparison business仅四metric、swap/recreate、placeholder和journal不变，不含summary/toolbar滚达；单看绿色completed不能关闭该G3范围。后续候选需补对应永久viewport的实际scroll before/after/restore、每目标完整screen rect、同协议截图。

## 最新生产候选静态复核与取证增量

已实际读ComparisonSummaryTable/HistoryDetailContent最新diff。三条border色水平线/无常态竖框、taste init与update保focus/ripple、null兼容均符合。StandaloneHistoryPlot永久容器先vertical自然测量，再按同源4line+40dp轴带+96真实plot预算及自然inline宽判断；未按UNKNOWN特判或硬减79，未污染Extraction。原件三cell inline合计1169≤1562px、预计回收79px，102.66dp仅是候选预期，新实际Canvas/PNG才关闭67.55dp失败。静态无新阻断。

最小测试增量限定现 `ExtractionHistoryVisualMigrationChecks.kt` 与对应runner：

1. geometry深拷standalone预算，主态及五特殊从实际Canvas plot bounds转screen并与实际plot View可见矩形相交，核真实plot96dp；missing-samples保持空采样真实分支，不造plot。
2. Detail永久support-scroll对各notes全文目标分别滚达、实际Layout/screen完整、protocol2截图；finally原scroll精确恢复、shotId/journal不变、固定edit完整。
3. Comparison永久toolbar/details各目标（原控件、summary行、taste）分别滚达，1.3/UNKNOWN不得用首屏partial当全文；两scroll与IDs/metric/journal恢复。主态先原scroll0 capture，不预滚；原taste实际全文dialog仅既有业务scope内执行。
4. runner逐项要求预期proof存在及协议2、真实5s、双域零差，原16主+60特殊不减，不只glob已有文件。
5. 已读新的History主入口：chart/说明/双notes四reach和restored五proof，含axisInk、96真实plot、固定actions、journal与scroll恢复，静态覆盖主夹具旧缺口。特殊态仍需对应调用与证据，不能用主态代替。

### M5 test冻结审查追加

已读 `/tmp/hoyi-m5-reachability-test-freeze.json` 对应两文件，主16/特殊60入口均接入真实plot和永久scroll检查；空sample不造path，History5份、Detail7份及Comparison逐目标+restore均由runner要求协议2/真实5s/四项零差。

剩余封闭项仅Comparison目标inventory：动态controls>=7/details非空和实际目标数不足以防止漏目标。要求7个既有tag各唯一：comparison-first/second/swap，comparison-metric-PRESSURE/WATER/WEIGHT/TEMPERATURE；source-legend与source-1/2各唯一；UNKNOWN实际scope须有warning。表头+3原label行、A/B taste、A/B detail与notes4按钮分别有稳定semantic key，按钮按现有本地化文本与所属details树核身份，不新增生产tag。runner按固定集合+条件warning精确比对，不只count。已交root协调原owner收口。

M5 exact inventory复核收口：最新候选20固定key，原journal selected statuses决定UNKNOWN第21目标；7tag各唯一、4原动作本地化文本/parent归属、所有View distinct，runner按固定集合与长度双重精确检查。最后静态阻断关闭，可编译/实际CI；原76图失败不据此关闭。

## 2026-10-20 bcc64c1 实际 PR 样本增量验收

再次从本包 History manifest 核对：run39414724042，head `bcc64c1a10c1bff6cd08d4e786b0807f27fe8a4d`，实际 tested merge `8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249`。原件位于 `build/task-first-evidence/ci-bcc64c1-m5/raw/raw-device/8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249/tablet-user-landscape-light-font-1.3/history/`。根已核 ZIP SHA e5e0c546411dbf986616226b68d4a7de333cc2e404806a580d85fe63e515a893 与 85 条 CRC；完整性不等于视觉通过。

### 失败根因及最小修正规格

`requireEmbeddedHistoryInitial:673` 的 `legendActions.length()==4` 是测试错误，不是该样本布局/坐标失败。实际 missing-samples 图仅压力、水、温度三个图例，均高108px且完整可见；原始采样数0，实际空网格 plot 及初始可见 plot 均96.44374dp。`FourMetricChartProjection.build` 对 current/reference 都无 weight segment 的情况明确不生成 WEIGHT；不能用 observation 的结束秤读数虚构采样图例。

仅改 `ExtractionHistoryVisualMigrationChecks.kt` 与 M5 runner：从独立声明的当前/参考夹具样本推导精确有序 metric tag 集合，恒有 PRESSURE/WATER/TEMPERATURE；任一样本 weightHundredthsGram 非 null 才在 WATER 后包含 WEIGHT。实测 actions semanticId 必须与集合逐项相同、无重复、无缺失、多余，仍逐项核48dp、完整screen与Layout。不得改成数量>=3或笼统 emptyExpected→3，也不得以实际 projection/按钮自身作为期望oracle。主态四项仍必须精确匹配。runner 当前218及主态276应同步使用对应独立夹具的精确契约。

空样本可以仍绘制真实空网格，所以 actualPlotHeightDp 非 null 合法；emptyExpected 的 actualSampleCount==0/无数据 path 必须独立于 plot-null 分支核验。保留有实际plot时96dp与1.0整轴要求；不造plot、不增加WEIGHT、不修改生产。已批准16主+60特殊、协议2/真实5s/零差/滚达恢复不变。

### 实际 PNG 与 G1–G4 限定结论

本次亲看主态、missing-samples，以及 chart/explanation/note-0/note-1/restored 五张原始 PNG。浅色1.3主态真实29样本、plot96.44374dp，固定动作完整。chart滚至129时整个图及时间轴完整；说明滚至226完整；豆款与口感分别328/412完整可读，restored回0且永久说明identity、journal与精确scroll恢复均有JSON证据。初始轴在局部scroll下方不违反已批准1.3规则；不以此外推1.0整图。

此一浅色1.3 History主态：G1/G3 在已采初态及上述四目标/恢复范围 LIMITED_PASS；G2/G4 原有角色、字体大小层次、数字单位、系列与主辅颜色在本次PNG未见回归，仅限所见样本。missing-samples初态三图例/空网格/“尚无萃取采样”语义正确，G1初态未见失败；因673提前终止，其后说明/notes滚达未执行，G3全链仍 PENDING。未采其他profile、Comparison与独立详情新修复，不能关闭原标准1.0截轴、整格边框或UNKNOWN 67.55dp旧失败。

本run整体 FAIL。font_scale 原presence/value未恢复是独立清理失败，不能归入图例断言或由本次修正关闭。全24页仍未通过。

bcc图例窄修最新两文件diff已复核：独立声明current/reference，实际chart.points/overlay.reference全等，有序semanticId精确匹配，runner同契约；原missing-samples后续raw空/segments空/vertices空检查继续保留。静态无剩余阻断；实际CI仍待，不据构建成功关闭视觉/清理缺口。

## 2026-10-20 a47c870 Comparison 来源全文实际 FAIL

PR39417690186，artifact12263680615，ZIP SHA256 `d2151fbc2d72a5083f9f91f6030ca74faf7640288d29481612ba619c36f895b7`，648 CRC（根校验）。manifest 实读 source `12de379c8bc98ed1d309d04c8e73edf09b3cae87`，head `a47c87090240d5b682501d84759d48f0bcf36736`。原件 `ci-a47c870-m5/raw/raw-device/<source>/tablet-user-landscape-light-font-1.3/comparison/`。

Astra 查看 `states/comparison-target-8-reachable/window.png` 与 geometry：details viewport `[216,1298,2524,1429]` 高131px；实际第一杯来源 TextView `[252,1298,1316,1583]` 高285px，5行、零省略，visible只能到1429。原屏同步看见来源后半被局部区下缘截。第二杯自然来源也高285px。主态预算为 toolbar455 + chart598 + details131 = workspace1184px。来源文字完整、Layout宽高自然，并非错误坐标或requestRectangle无效；**目标自身比viewport高154px，无法靠滚动让整目标同时可见，G1/G3 FAIL成立**。本轮已越过旧图例集合失败，但不覆盖这个独立缺陷。

根因在 TaskComparisonLayout.measureProduct 的 controlMinimum：只计 clickable 后代自然高，details来源全文不是clickable，因此只保了按钮级viewport，没有保已批准必须完整滚达的来源块。

最小生产修正限 TaskComparisonLayout（如必须新增只读预算字段就在该文件）：detailsMinimum 取既有48dp、原可点击控件、两来源TextView、摘要四个独立行的**实际自然测量高**最大值，再加真实scroll内距。不把整个details或整张summary表当不可分目标，不新增控件、不压缩/省略来源、不改字号字重及字符串。使用已存在真实View/tag/行结构定位且保对象不替换；隐藏状态不参与最低预算。

分配仍先保障完整目标最低viewport：若 height >= preferredChart + toolbarMinimum + detailsMinimum，则保持标准160dp plot；否则用既准96dp fallback。toolbar预算在预留detailsMinimum后取自然高与可用高较小值，details再取自然高与剩余；两viewport必须满足各自实测最低高。当前1184/598/285算术仅说明toolbar最多301px，不作为硬编码，也不先宣称toolbar自然最低可满足。无法同时满足时保真实不足状态并FAIL，不挤穿plot96或控制48下限。记录minimum来源、两source自然高度、各summary row自然高度及可满足性，便于下一CI核。

测试保原20+条件warning精确集合、whole/真实Layout、两个永久scroll恢复和业务identity/journal门禁；不修改失败断言来迁就viewport。下一实际CI须完整走到source1/2、摘要行、taste及四按钮，并恢复初态。仅本light1.3原件的失败已定位，其它配置和后续特殊态不能由此外推通过。

### Comparison 最低详情预算修正静态收口

已审 TaskComparisonLayout.kt SHA256 `7ca0c698a18bd9065c3a2c71aae63c1cdca507c89dd12f142160e1fac72818ef`。只在 product 分支增加两精确来源 TextView 与原 summary 直接 LinearLayout 独立行的自然高度；隐藏不计，最低值取 max 并加原 scroll padding，不取整表高度。预留 details 最低预算再分 toolbar，160优先/96 fallback 与真实不足字段保持。旧 null visual 路径、字体/内容/业务和测试门禁未变。静态无新增阻断；编译日志由 owner 提供，不代表实际5行来源已完整滚达，仍待新CI。

## a47c870 History / HistoryDetail 增量原图验收

该包仅采到 **light/font1.3** 的 History、HistoryDetail（及已失败Comparison），没有 font1.0 或 dark 原件。因此旧 History **标准1.0截轴仍无本轮修复证据**，不能用1.3滚达代替关闭。

### 两个指定旧问题的实际结果

- 独立 HistoryDetail `special-states/unknown-observation`：Astra 实看初态、note-5-reachable、restored 原PNG。真实 Canvas plot local `[275,127.875595,1310,358.87402]`，screen `[491,911.8756,1526,1142.874]`，visible与全plot完全相等，density2.25，**102.66596dp ≥96dp**。不是用chart外框代替plot。条件同行的 metricsInline=true，自然宽1169≤1562，chrome293→214回收79px；UNKNOWN说明仍完整，结束时长/秤读数保持破折号，没有假造结果。旧该配置67.55dp不足已由新实际图与Canvas证明修复。六notes报告与固定编辑按钮、shotId/journal/scroll恢复成立；所看末目标及恢复图未见新增裁切。
- History `special-states/missing-samples`：Astra 实看 chart/explanation/note-0/note-1/restored 五张原PNG。真实0sample、无path；图例为压力/累计注水/温度三项，不强行造WEIGHT。actualPlotHeightDp及初态visiblePlotHeightDp均96.44374。滚达四目标对应scrollY129/226/328/412，complete均true，说明“各项独立量程 · 等待本杯采样”与两note原文完整；最后恢复0、permanent explanation identity与journal不变。1.3恢复初屏底轴仍可在局部scroll下缘，符合已准“滚达后完整”，不作为标准1.0通过证据。

### G1–G4 限定结论

G1：上述UNKNOWN真实plot下限和missing-samples全目标滚达/恢复通过本配置验收，不扩至未采配置。History共40份、HistoryDetail共48份protocol2均accepted/5000ms且四正式diff count=0（独立逐份读核），这只证明采集契约，不代替未逐图细读状态的视觉结论。

G2：沿用固定字体资产与角色来源；本次未取得新的glyph fallback/原稿字体身份认证，unknown保持。G3：两个指定状态的product TextView实际letterSpacing0/textScaleX1/fontPadding=false；missing的elapsed/weight数值单位baseline同为671px，UNKNOWN sampled-water数值单位同为488px。所看原图轴字、单位、说明和滚达notes未见被切/错位。G4：原图保白色图面、ink/muted层级、UNKNOWN褐色说明、原四指标辨色；实际配置ink #101820/muted #657183/accent #087A78/warning #795119。此为浅色指定状态，不外推深色或所有动画合成像素。

独立详情其余已采主态及4特殊态的plot几何均137.77707dp，作为机械证据记录，不凭数字代签其所有文字视觉。UNKNOWN预算账本仍有 `primaryBudgetSatisfiable=false`（chartNativeHeaderNaturalPx434、chartMinimum986），与实际完整102.66596dp不是同一个测量量；本次以原Canvas/屏幕交集验收真实plot，保留该账本不一致待单独静态核实，不能删字段或由false反推图形不足。全M5、Comparison裁切、标准1.0截轴及未采配置结论不变。

## 2026-10-20 Extraction 剩余 G1 实图审查与库存纠正

本次逐张查看 eb77 四张 Extraction 原 `window.png`（浅/深×1.0/1.3），对照批准 `extraction-four-series-v3.png` 及 M5 §4A；实际 merge `6ee51623e7f32f6df425e0d84313d1bc5e7f6bed`。原件在 `build/task-first-evidence/ci-eb77b2f-m5/raw/<merge>/<profile>/extraction/`。复读各 manifest、geometry、Canvas 及 extraction-business，未重新采图。

**纠正库存：16主+60特殊不等于 Extraction 有特殊图。60特殊属于其余三页；Extraction 四目录各只有主 window.png 和 display 伴图。** 新一些的49a包四 manifest 也各列同样12项 pending。此前全范围缺口表把Extraction特殊态写成“已有图待审”不准确，应以下表替代；不能靠重读现包完成未采状态。

| 配置 | 本次 G1 结论 | 实际图与测量 |
|---|---|---|
| light/1.0 | RUNNING主态限定接受 | 左上时间/秤读数主值，压力/注水/温度次级；主图在下、右侧本杯与工具、红色停止固定。四轴/图例/时间标题完整，无图例抢主读数。右侧配方、状态、两工具及模式说明均完整可见 |
| dark/1.0 | 同范围限定接受 | 与浅色同构，图轴/文字没有因暗面消失；右侧层次与停止固定完整，不由单色值替代此次实看 |
| light/1.3 | 核心主读数、图、固定停止限定接受；工具滚达仍缺证 | 四图例与四物理量轴、时间轴全在图内；右栏配方自然换行，设备状态完整。两辅助工具只露顶部，位于合法support-scroll内，但无滚后/恢复原件，不能签全页可达 |
| dark/1.3 | 同浅1.3限定结论 | 同样两工具初屏部分可见，不新判生产裁切FAIL，也不能假定能滚达；停止完整，不被右栏内容挤走 |

四图 density=2.25，主区 x216..1778、右区 x1832..2524，列距54px=24dp；右宽692px≈307.56dp，符合280–320dp及约30%预算。读数组间36px=16dp，label→quantity及number→unit为18px=8dp；读数底到chart顶36px=16dp。停止rect均 `[1868,1303,2488,1429]`，高126px=56dp，完整在app内。图rect标准 `[216,529,1778,1429]`，大字 `[216,561,1778,1429]`；既有同源Canvas真实plot高分别202.468dp/153.777dp，非拿外框冒充图。原图时间刻度及“时间·s”均完整。

原稿演示的24.6、28.4、目标36g不作为生产数据要求：本scope真实RUNNING、activeTraceIdentity=m5-live-shot、末采24600ms，原模型时间显示24s，未tare故明确“秤读数16.80g”，目标按水量模式明确不使用重量结束。实际四线截至采样端，没有照抄原稿未来线/假杯重。批准的实时状态/本杯快照优先规则保留。这里没有看到采样缺口，不能以连续线正确证明gap分支。

G2–G4沿用本文件已完成的四主态角色/基线/颜色限定接受；此次原图未见新字形/行盒/色彩偏差，不再重复全量审计。**新增关闭的只是四主态上述核心G1；Extraction全状态仍未完成。**

### 未采项归属及最小补齐计划

这不是新增产品范围。M5 §4A/§5/§6已要求真实控制状态、无秤/stale、参考及缺失采样语义；manifest逐项列出未实现或未采分支。不能把pending永久当免责，也不能拿History的同名状态图代替实时页。

| 原manifest pending | 需补的原要求证据 |
|---|---|
| STARTING | 真实模型启动中状态/允许操作/状态说明，不能直接造RUNNING代图 |
| STOP_REQUESTED | 原停止请求中状态、停止操作呈现及未收到结束确认的语义；对应批准停止中稿，不伪造ENDED |
| UNKNOWN_live | 原未知观察、最后有效读数/时间与警示，不冒充实时或已结束；对应未知稿 |
| no_scale | 原允许的非重量启动模式，注水成为另一主值、三指标图、无秤限制；不绕重量门禁，对照无秤v2 |
| stale_scale | 原freshness真实失效后的说明/旧读数呈现与动作限制，不冻结旧有效值当实时 |
| no_service_ready | 无服务/未准备就绪的原状态和操作，不临时制造Service充就绪 |
| manual | 原合法手动模式的来源、读数、固定操作，不改变控制语义 |
| historical_reference_toggle | 真实已存参考身份、开/关对应图及恢复，不由配方生成参考线 |
| missing_samples | 原空样本说明与不造值、不造线 |
| water_reset | 原注水重置断线，不桥接；状态/本杯/停止仍完整 |
| sample_gap | 原缺口断线，不补零、不跨缺口连线 |
| journal_unavailable | 原记录不可用提示/入口禁用或降级语义，不以假记录填补 |

执行最小范围先限现有M5测试 `ExtractionHistoryVisualMigrationChecks.kt` 与对应runner，生产保持不动。沿已批准唯一真实Mock Service/同源单调钟/原模型输入和隔离清理路径，列出每状态的**真实模型进入条件及合法fixture**后逐项实现，不能仅反射UI文本/强置View状态。某状态现有Mock不能合法达到时，报告具体模型前置条件给Astra裁定，不伪造硬件ACK，也不删状态。沿既定四主配置组织既有特殊态，manifest/runner按固定预期集合核全，不只核计数；所有新增原件沿同一原5s/四零差协议及真实geometry/Canvas/typography，标Mock能力边界。静态方案批准后由owner实现，不在本审查改代码。

另为RUNNING右栏增加最小滚达：按原对象精确定位“选择历史参考”“萃取概览”和其后模式说明；分别滚到完整screen rect、Layout全行盒零省略，保存滚后原PNG；finally恢复同support-scroll原坐标及本杯traceIdentity/参考选择/原模型状态，保存恢复图，核固定停止rect未变。只滚动不点击副作用操作；不能靠调整初始scroll把不完整主态藏掉。1.0完整初态也记录同一目标集合，1.3必须有实际滚后和恢复证据。完成后Astra按图签可达，不以report accepted代签。

## 2026-10-20 dc5875c Comparison 来源修复与UNKNOWN前置断言

原件 `build/task-first-evidence/ci-dc5875c-m5/raw/raw-device/84aaa3efd951e4f6229fc9d235e257e61f0f257c/tablet-user-landscape-light-font-1.3/comparison/`。本轮只有light/1.3，不是新1.0。Astra实看主window、target-8/9-reachable、unknown-observation/window原PNG，并读精确目标/恢复及UNKNOWN geometry。

来源修复：两来源滚后真实rect分别 `[252,1144,1316,1429]`、`[1424,1144,2488,1429]`，自然高285与details viewport285相符，完整五行至“时刻”均在原图内；旧131px窗口裁切此配置已关闭。20目标集合、两个scroll均[0,0]精确恢复、ids/metric/journal不变报告成立。主图摘要已呈水平分隔而无旧整格竖框，所看字体/主辅色未见新回归。只签该修复样本，不将整页manifest FAIL改PASS，不扩其他配置/状态。

新失败归属Comparison UNKNOWN（不是History）：实际warning正确全文“结果未确认，请以咖啡机实际状态为准。”，Layout一行61px、零省略，rect `[216,592,2524,653]`；toolbar viewport `[216,245,2524,546]` 高301，body延至761。警示处于初始可滚区域下方，当前初图确实看不到。dc测试1170在调用既有完整reachability之前强求whole，无法进入第21个warning滚达证明。这里不是错坐标推定，也不能把初态不见改写成已见；已批准Comparison大字toolbar局部滚达允许这种初态，需实际滚后证明方可关闭。

最小适配限M5测试+runner：仅Comparison UNKNOWN初态保正确文字、isShown、同根/实际warning对象、真实UNKNOWN/非ENDED数值语义，先保原初图；把whole放到既有21目标精确inventory里的warning滚达执行，要求完整rect/全部行盒/零省略、正式原PNG、finally两scroll精确恢复与IDs/metric/journal不变。runner显式要求该条件warning目标及其accepted证明，不按数量替代。History/独立Detail的原初态whole不变，不删全局whole/48/plot门禁，不改产品。warning本轮没有滚后图，继续EVIDENCE_PENDING；适配静态完成也不能先签通过。

## 2026-10-20 Extraction 原12态补齐：B静态终审

静态无剩余阻断；不是12态运行或视觉通过。核完整五文件差异及原ExtractionController、onCoffeeFrame/passiveShot、Service清理路径。scenario只允许Mock/main/同clock owner/hub缺席安装；未安装时原Mock分支不变，真实hub分支未改。原start准备/资格校验保留，复用原controller归约，不写UI/state；synthetic completion不作硬件ACK。manual由两帧进入原passive observer，app controller仍IDLE；tick不产生样本。12态各有独立Scope，先关闭Activity、销毁Service并排空回调再clear scenario/scale输入、恢复delegate与clock；失败不续下一scope。

补齐operationInputs后，fixture/case有序记录start请求ID、同ID synthetic_Success、STOP未完成的独立请求ID、UNKNOWN的DISCONNECTED输入；manual/no_service无app start。runner精确核12集合×4配置、原16主+60历史特殊态保留，额外48张Extraction状态图仍须真实协议/原件。参考选择实际Dialog/ListView、短暂STARTING、无样本Canvas与所有状态布局仍待CI，不以5个unit或编译代验。

冻结SHA256：MockExtractionScenario `ce632475ae1079337fed9ac316368839a951390c73df7fe2004a03be2b11e6c0`；MobileService `651cdeeb62cd3be808ad35e93db37443c433e081322085b60af41bc0cbcef73d`；M5测试 `ca4c1d0a9e95542bb7233a9b636209ac9ba1bc0fbf6f420773bca3dbad2641d0`；runner `38f7a3e7ff4b73ab8f4ee11f6ae4c14ed5c1a52d2087b94e4dcbc42db5d6b6aa`；unit `b530ab76322cc2d58ad1f0aad10fa37f581c28c5dd72b8f7dc33f726d87bfb60`。最终operationInputs增量后构建由root统一核。

RUNNING滚达规格纠正：当前实际模式说明在header，非support-scroll内；精确两个滚动目标为reference-choice/overview。核header实际模式全文完整，不虚构第三个滚动目标。已核两目标原对象、每目标protocol2/完整行盒、恢复图、停止rect与本杯/参考身份保持的静态闭环。Comparison UNKNOWN窄适配也已核：仅其初态whole延后至原21目标，其他页whole未改；实际滚后图仍待。
