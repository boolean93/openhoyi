# M5 light1.3 当前原件有限视觉复核

## 阶段一：实际查看3/13图

已实际打开 History/main、HistoryDetail/main、Comparison/missing-samples 三张原PNG，配对实际字体、geometry和同帧target。**已见区域的主次、字体、数字单位基线与颜色可有限接受；History时间轴滚达、详情右侧全文、Comparison缺样本说明及其余10特殊图尚未看，不能签整页或整M5通过。**

来源 run39906804820，push source=head `4076c2d862f94608fa6ec3d405c91d23a6b6867c`，无merge。profile仅 `tablet-user-landscape-light-font-1.3`/zh-Hans。ZIP SHA256 `c64ff096375ef23c4af4a0d3a5d8d4317b277c808d66a3cd583f79074e3130fc`；精确索引 `build/task-first-evidence/ci-4076c2d/m5-visual-acceptance-index.json`。原run在Comparison missing-samples case保存后summary strict写盘拒绝而FAIL；不以case或主manifest状态改写整体结果。

本次独立重算这三组12个选件SHA均与索引相同；各geometry==accepted attempt.before.target==after.target，geometry.typographyEvidence==独立typography JSON。保留公开API未知glyph身份，不能靠资源常量代替实际字体。原图2560×1600，工具显示缩放版；尺寸取原geometry，不按显示图片量像素。未重新全ZIP解包或重跑protocol像素比较。

对照 [M5冻结与§10.4](../plans/2026-10-19-m5-extraction-history-visual-migration.md)、[摘要表合同](../superpowers/plans/2026-10-16-comparison-summary-table.md)、[旧审查](2026-10-19-m5-eb77b2f-visual-review.md) 与固定中文字体合同；旧source结论不移植为本批验收。

## G1–G4

| 区域 | 实际观察/配对数据 | 限定判断 |
|---|---|---|
| History G1/G3 | 左列表、右预览和底部两动作分区明确；三指标数字/单位不再孤立换行。真实plot与初始可见plot均96.44374dp；四图例完整，蓝压力、虚线注水、带点秤读数及点状温度可区分。chart整框[1193,725,2524,1385]，初态visible底1267，时间刻度下部/轴标题仍在滚动区外。 | 初屏真实plot下限已见，符合1.3允许局部滚动的合同；**不签完整时间轴**，须另看本source滚达/精确恢复，不把首屏裁切自动当1.0式失败。 |
| HistoryDetail G1/G3 | 左图右个人记录，时长27.4/结束秤36.20大值优先，末次采样60.4ml次级；四指标图例与独立量程说明清楚，真实plot及可见高142.66595dp。时间0/13.7/27.4和“时间·s”完整。右侧豆款/投粉/研磨/曲线期待可读；更下方口感区域在滚动边界外。 | 本主态图与指标有限接受，右侧固定动作可见但未操作；不把隐藏的口感全文签过。 |
| Comparison G1/G3 | 两杯选框与交换动作清楚，第一杯21:35:20/第二杯22:13:20身份可读；中央大图不再被完整摘要挤成很浅的最小框，实际plot高(479.1244−118.875595)/2.25≈160.11dp。仅第二杯压力虚线可见，未凭空补第一杯采样。摘要当前两行是水平分隔，无旧整格竖边线。 | 关闭**本帧已见摘要区域**的旧整格描边差异；下部口感、缺样本解释、来源/详情动作和完整表仍待滚达，不能把该缺样本态仅凭图上一条线签完整语义通过。 |
| G2 实际字体 | 三组typography为固定Noto Sans SC候选；资产SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。History/Detail主值实际90px/700，次值53.100002px/400，单位42.3px/400。轴实际42.3px/400且Canvas记录candidate_asset_association，不是平台逐glyph身份。实际看字形无明显缺字/假粗或数字变形。 | 已见角色有限接受；不宣称候选字体精确等于生成稿字体。Comparison span格式保留在原渲染证据，本次不把strict写盘错误当视觉字体错误或已修复。 |
| G3 基线 | History主两组数字/单位screenBaseline均660，注水均616；Detail主两组均442，注水均398；来自同帧真实Layout，不是字体常量。 | 各对基线一致，三组不同层级允许不同基线，不强行同高。Comparison不是这组独立number/unit View合同，baselinePairs空不推定失败。 |
| G4 | 浅背景/白图区/深主字/灰辅助层次清楚，青色状态与选中列表一致；Canvas压力#FF176BD1、温度#FFA46500等实值与当前蓝/青/绿/橙指标分工一致，线型也承担区分。 | 只签已见浅色区域可读性，不外推深色、所有交互色、量化全页对比度或硬件真值。 |

## 实看原件清单

每组另读同目录geometry、typography和frame proof；以下是实际打开的PNG及SHA。

| 页面/态 | 原PNG路径 | SHA256 |
|---|---|---|
| history/main | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history/main/window.png` | `bcfb18dd873e181c9e4fb70f4753ab4a97f8d78f56b4eade4477f1aee2886c1f` |
| history-detail/main | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history-detail/main/window.png` | `a7008ef41b22d6f965b3cce2590eaec1634b0651e2c1f7eff2aad459f2546fb5` |
| comparison/missing-samples | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/comparison/missing-samples/window.png` | `fd696234d7cce2dcb7b53cc35bc0f8020833539493985155e50855a502a59ba7` |

## 未完成

13选件中的History/HistoryDetail各5特殊态共10张未看；本source所有局部滚达与恢复图、其它配置、Extraction未在本阶段验收。原summary strict失败继续保留，不用机械PASS、旧图或本三图关闭。当前只新增此审查文档，未改源码、构建、设备操作或提交推送。


## 阶段二：其余10特殊态已实际查看（累计13/13选件）

本节更新阶段一“10张未看”的进度，不改变整run FAIL或滚达/恢复未审边界。实际逐张打开 History 与 HistoryDetail 的 missing-samples/no-notes/sample-gap/unknown-observation/water-reset；独立重算40选件SHA，逐组核 accepted before.target==geometry==after.target、typographyEvidence==独立字体JSON。

- **missing-samples**：两页图内“尚无萃取采样”及暂无保存采样提示可见，末次采样注水为破折号；结束摘要30.0s/36.00g仍保留，未伪造采样曲线。详情图例值为破折号，秤样本图例缺席，与无秤采样不同于结束秤摘要的层级可辨。History的时间轴仍在初屏外；详情时间轴完整。
- **no-notes**：History初屏主要是图与列表，不把未展示notes当已审；详情当前可见个人记录字段为“未填写”，没有伪造内容。下部口感仍待滚达。
- **sample-gap**：两页曲线在缺口处分段，不连接缺失区；详情说明明确“存在采样缺口，曲线分段显示”，实际全文一行可读。History已见断线，但移到图后的完整说明尚未滚达审阅。
- **unknown-observation**：两页均有“结果未知”徽标及“结果未确认，请以咖啡机实际状态为准。”全文警示；时长、结束秤摘要为破折号，末次采样注水62.0ml及部分样本保留。不把最后样本当已确认结束值。History警示与标题同排但没有碰右工具；详情警示独占一行，真实及可见plot高107.55485dp，已超过96且时间轴完整，关闭本帧旧未知态plot仅67.55dp的差异，不外推其他主题/配置。
- **water-reset**：两页注水虚线在重置处分段回到低值，没有斜连成连续累计量；详情明确显示“注水计数发生重置，已分段显示”，全文可读，末次注水32.0ml与图例一致。History解释仍须另看图后滚达。

G1/G3：五个History真实/初屏可见plot均96.44374dp；详情除UNKNOWN为107.55485dp，余四态均142.66595dp。各页既有分区和固定动作未见新重叠，History 1.3初态时间轴裁切仍按原合同保留滚达待办，不能签完整初态轴。G2：已见状态文字/数字与阶段一固定字体层级一致，实际同帧字体字段已配对，没有以资源常量补证。G4：未知态棕色警示与普通青色结束徽标明显区分；蓝压力/青虚线注水/绿带点秤/橙点状温度在本浅色可辨，缺口和重置另有线型/文字，不仅靠颜色表达。

### 新增实际查看PNG清单

| 页面/态 | 原PNG路径 | SHA256 |
|---|---|---|
| history/missing-samples | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history/missing-samples/window.png` | `8696e1bb23e3024d06855e3f7135e8b2102f08916c0b4f8334b5d51326389c27` |
| history/no-notes | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history/no-notes/window.png` | `1180b55ffd42e0455281476865e0af0fd803ffcdfd7ec75cabef2ca94bd60216` |
| history/sample-gap | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history/sample-gap/window.png` | `dc6ba4323f096cfd0decd5949f754825eb34e84d98dc7609445ab348622ca212` |
| history/unknown-observation | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history/unknown-observation/window.png` | `b6c3346a4aa45ce918dce06758de112d3abbde27cfacfc43879e13cb551d9949` |
| history/water-reset | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history/water-reset/window.png` | `d65164336c5e4c669e1fa036b20d95b1a6c16005bc34b513e10a8c49c743f70a` |
| history-detail/missing-samples | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history-detail/missing-samples/window.png` | `5b2b21ef91eddd657d2ed2713cb02f84cf181992b62827708e92021fe923f4bb` |
| history-detail/no-notes | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history-detail/no-notes/window.png` | `d5c816f5f2a0d31bcef65b1b27248dc9341e1ed9de13313b868ae18a94938722` |
| history-detail/sample-gap | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history-detail/sample-gap/window.png` | `f749434799269dc63dba7451498ba765bdd8d118d21f385f906986f3da662867` |
| history-detail/unknown-observation | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history-detail/unknown-observation/window.png` | `f8a400e70a0121fbd0d5b794abc6434eb980f9ff331f42e9a8d2bc95e4274809` |
| history-detail/water-reset | `build/task-first-evidence/ci-4076c2d/m5-visual-selected/history-detail/water-reset/window.png` | `34e1afaf597661478edcc20904248fde435c7038bd162b471fe6f5d0a95d108a` |

最终范围：本索引13张初态PNG全部实看，但并非完整M5视觉通过。全部局部滚达/恢复、Comparison其余特殊态/说明全文、其它字体/主题、Extraction和真机仍不在此范围。可有限接受本节明确可见的特殊态表达和详情UNKNOWN真实图预算；不把初态图数量当全合同覆盖。本阶段仍仅写审查文档。
