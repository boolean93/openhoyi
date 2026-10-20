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
