# Extraction 特殊态真实 plot 96dp 最小修复设计与实施计划

采用 brainstorming / writing-plans 的设计先行与可验收步骤；本任务只写计划、不开发。优先遵守仓库 [Git工作流](../git-workflow.md) 及既定合批授权。

- 需求与范围：关闭旧643浅1.3的5张Extraction特殊态plot不足；只修M5 product Extraction的plot保底及其既有header滚达证据，不扩业务。
- 来源分支/基线提交：当前既有授权合批 `feature/task-first-ui`，HEAD `5d08fd658ad7300d0478a2b5e117e88eb3c99f11`；此分支既有PR目标develop。此次不另切分支抢共享owner，不以此改写新任务默认从develop建短分支规则。
- 工作分支/PR目标：现 `feature/task-first-ui` → 原PR → `develop`，由root合批；禁止直接推送main/develop。
- 推送与合并：仅root核工作树和相关验证后普通推送；保留他人改动，通过必需CI及本实图复核后再按授权合并，不绕门禁。
- 协议与设备风险：仅measure/取证；不改Service、controller、BLE、时钟、样本、停止/重试、未知记录。Mock不证明硬件ACK。
- 发布：不发版、不签名、不安装、不操作机器；不恢复竖屏、不加功能。
- 回退：独立revert本窄改，不删证据/用户记录、不卸载清数据；原643失败保留。
- 当前阻塞：5张已证实FAIL，5d08新CI待测；本计划无新运行PASS。

## 证据与根因

见 `docs/evidence/2026-10-20-m5-extraction-special-visual-review.md`。旧643实际source `4f990722c0181504b665ab1fe91ac60832619d18`，UNKNOWN_live/manual/no_service_ready真实plot67.5549dp，reference-on68.8882dp，water_reset85.7771dp。各轴glyph完整，但plot低于96dp。

已核 `git diff 643d828..5d08fd6`：`ExtractionActivity.kt`、`ExtractionOverlayView.kt`、`ExtractionHistoryVisualStyle.kt`无变化；其中HistoryDetailContent有104行差异。5d08的History修复未触及Extraction，不可用其构建/CI推定本问题关闭；仍以新CI原图结论为准。

`ProductHistoryChartRenderer.bounds(comparison=false)` 真正消耗的轴带为 `4 * line + 40dp`，其中line来自当前真实Paint ascent/descent。`minimumAxisHeight()` 却是 `ceil(5 * line + 48dp)`，只剩约 `line + 8dp` 的plot，并不承诺96dp。当前浅1.3 line≈61.250396px、轴带≈335.001584px；保证96dp的MetricPlot View应至少 `ceil(216 + 335.001584)=552px`。已有 `minimumEmbeddedHistoryHeight()` 正好提供该相同非comparison几何公式，无须另抄公式或改Chart Paint。

Overlay `onMeasure` 已有永久header ScrollView，只按错误plotMinimum给header分配cap；特殊态前置警告或参考说明增加占高后，完整图仍能画但plot被压薄。Activity的 `max(240dp*fontScale,height*.55)` 是workspace分配估算，不能替代真实bounds保底，且本次wide布局不通过它增加primary列实际高度。仅调大240/百分比不能解决此固定可用窗口。

## 选定方案与范围

**采用现有header局部滚动，修正product Extraction的plotMinimum为真实96dp+轴带。** 不增加主区整页scroll，不移动readiness/preparation/notification/alarm，不改字号、间距或状态全文。标准主态高度充足时保持原自然header；不足时只使用原header ScrollView的cap。停止保持固定。

另外两种方案不采用：提高Activity固定chart预算在本wide窗口无效；给整个primary新增scroll会改变主值/图的位置与交互，范围超过这5态所需。此处不采用History的title+warning合行方案，Extraction信息结构不同。

具体生产改动只允许 `mobile/src/main/kotlin/io/openhoyi/mobile/ExtractionOverlayView.kt`：在 `MetricPlot.minimumAxisHeight()` 的已有productRenderer分支返回既有 `minimumEmbeddedHistoryHeight()`（两种product非comparison调用同轴带；embeddedHistory已有返回值完全不变）。保留visual=null的旧路径、共享renderer函数/绘制、History调用及Activity预算原样。可给现有header加仅 `visual != null && !embeddedHistory` 的稳定semantic tag便于取证；不重建对象/监听，不增加通用框架。

依据旧图可用高的静态估算如下；只是可行性，不是新图PASS：

| 状态 | Overlay高px | 原header自然占高px | 预留552后header cap px |
|---|---:|---:|---:|
| UNKNOWN_live/manual/no_service_ready | 674 | 187 | 122 |
| water_reset | 771 | 243 | 219 |
| reference-on | 868 | 378 | 316 |

现图例行108px，说明普通61px/reset两行122px、reference caption自然高及padding不超过316px，预计可在各自viewport完整滚达。实际实现须按最终真实Layout核完整目标，不能硬编码122/219/316或拿表代替实测。若实际某目标高于cap或96dp无法同时成立，保FAIL并回报具体自然高；不得缩字/降低96dp/藏warning。

现M5 §4A允许极短局部内容滚动，Overlay已有header ScrollView；本方案只使用这个既有局部滚动，外部状态说明仍高优先完整可见。不能把History1.3滚达特例泛化成任意裁切许可：Extraction图体/轴始终完整，只有header内容允许逐目标滚达并精确恢复。

## 实施与验证步骤

所有共享文件由同一Sol owner串行实施；其他人可能在仓库工作，不撤销其文件。Astra只复核。

- [ ] 在现 `ExtractionHistoryVisualMigrationChecks.kt` 的已有Extraction主/特殊状态捕获链补真实Canvas bounds→screen→visible交集的96dp断言，已有空网格也有真实plot时必须核；不能只读chart外框或case机械status。先针对原有5态复现不足，不增加业务夹具。
- [ ] 生产仅改上述product分支及必要header semantic tag；旧visual=null、embeddedHistory、renderer/字体/颜色与Activity完全不改。
- [ ] 在同一测试现有capture/protocol工具上对既有12态及reference on/off状态核header精确目标集合：当状态有图例/说明/参考caption时逐项完整滚达、记录真实Layout/glyph与独立原PNG/protocol2。图例按真实sample/reference能力确定3或4项；不存在caption不造目标。初态先采，不能先滚动再冒充初态。
- [ ] 每个被截断header目标沿原永久ScrollView滚达。至少覆盖图例各按钮、explanation全文、存在时referenceCaption全文；无需点击改变metric/参考选择。每目标采原window/geometry/typography/proof，plot和固定停止保持原屏位置/完整。保存并在finally恢复header精确x/y，再采restored，验证同一header/目标对象、当前杯trace/state/样本、reference选择、journal不变。目标单体仍无法完整可见则FAIL。
- [ ] `scripts/check_extraction_history_visual_migration.sh` 对既有case及其新增header targets/restored要求精确预期证据，不用glob已有文件放过缺图。保持原16主+既有历史特殊+Extraction12×4、5秒协议2与双域零差、cleanup/存储/停止语义不减。新滚达原件归原case子目录，不另建场景框架。
- [ ] 运行现有相关unit/Mock/AndroidTest统一构建，记录源码SHA和日志；不新增只镜像常量的unit。由root启动既有M5 CI后核四配置实际原件。1.0保持初始完整主图/轴要求，1.3不允许plot低于96dp。
- [ ] Astra先核新5态初态+各header reach/restored，确认真实plot≥96dp且所有axis glyph完整，再核其余既有Extraction特殊态/主态回归；History与HistoryDetail已有修复必须原CI回归，不从“分支值不变”直接签视觉。

## 验收边界与独立未完项

完成本计划只关闭5态plot及由本修复必要产生的header全文滚达/恢复。右support-tools完整滚达、journal选择器Dialog本体取证仍按现review单列；不因本plan补header而冒称这些缺口已解决。参考off初态与restored量程不同也不改成像素相同承诺，保持原真实数据/选择/scroll合同。

本计划未改源码、未构建、未触发CI。实施后静态审→统一构建→原CI实图→Astra限定结论，四者分别记账。

## 2026-10-23 Sol 实施记录（待统一构建与实图）

实施前从保留的 `ci-643d828-m5-pr/artifact.zip` 直接读浅1.3原 `geometry.json` 的真实 `plot.boundsPx`，除以2.25复核红证据：UNKNOWN_live、manual、no_service_ready均67.5549dp，water_reset为85.7771dp，reference-on为68.8882dp；未重跑旧CI。随后只改指定三文件：product `MetricPlot.minimumAxisHeight()` 复用既有 `minimumEmbeddedHistoryHeight()`；给原header ScrollView加Extraction专用tag；原12特殊、主态与参考on/off/restored在初态原帧后逐项采header图例/说明/存在时caption的原window、geometry、typography、protocol2，验证目标自然全文、真实Canvas plot可见交集≥96dp、轴glyph、固定停止和滚动精确恢复。脚本按每case固定3/4图例集合及caption存在性逐路径要求原件。`bash -n`、脚本内嵌Python `compile()`、三文件 `git diff --check` 已通过；未做Kotlin/Android构建、CI、设备动作或视觉签收。新图仍须root统一构建和原CI后由Astra复核。

## 首轮实现静态审：需封闭测试修正

Astra已读三文件实际diff并核SHA256：生产 `820076de1ccc4a903db7eb22eac4c5a29c173b00207be05e8d45e4055df7dd12`；测试 `0763693d9e301d80626b4603fdec8a3fa4d609c1da7cd75eeeaf3c04a08226ee`；runner `c0afd0a083e849b70a3094c523710cba6c4fbc6e336c24639a0e587cacdf9ac2`。生产只加product非embedded header tag，并让product MetricPlot复用minimumEmbeddedHistoryHeight；History既有返回值、null visual路径与renderer不变，生产无新增静态阻断。

初态先capture再header链、真实Canvas映射与visible交集≥96、全部axis glyph框、逐目标原图/协议、原12态和reference on/off/restored入口及runner精确metric枚举均已接入。当前不允许以这些已完成项覆盖下列缺口，不签静态通过：

1. **确定会误失败的固定动作选择。** `extractionChartHeaderReachability` 无条件拿 `product/extraction/stop`，到达及恢复均要求whole。manual/no_service_ready原stop是GONE，真正固定动作是禁用start，因此这两态必然被新断言阻断。最小修正：保存start/stop两原对象及原visibility/enabled/text；根据独立模型/既有StopActionPresentation判断本态可见者，要求唯一可见fixed action完整、rect不变；隐藏者保持原状态。STOP_REQUESTED/UNKNOWN的可见stop允许原禁用，禁止把enabled=true当所有态条件。报告改为fixedAction并含semanticId、原状态，runner同步校验。
2. **48dp遗漏。** 当前whole与runner require_text_layout只核可见矩形/行框，不检查legend按钮触控尺寸。对每个legend target在初态和reach都明确核实际width/height≥48dp，记录原px/density，runner复核；explanation/caption是文字不套按钮48dp。不得只靠生产minHeight推定断言存在。
3. **恢复合同尚不完整。** 现helper末尾只核保存owner的trace/shot/points及referenceId/chart.reference，未核Activity仍绑定原Service、referenceCurveId、chart仍原points/trace、manual状态、support-scroll不变；各reach也未核这些业务不变量。增加一个窄unchanged检查，在每次reach及restored调用：原Service引用、owner shot/manual/trace/curve/points、chart points/trace/reference、referenceId/referenceCurveId、journal导出及journalResult失败状态不变；保存原永久support-scroll对象及x/y并核不变，header仅finally精确恢复。所有目标保同一原对象；固定action走第1项。报告/runner补相应原值/复核字段，不用一个无证true代替。

仍只由同Sol修本测试与runner，生产SHA保持；暂不叠加tools/Dialog改动，先冻结新hash供本次窄复核。修正后才统一构建/CI；旧643五态真实不足仍FAIL，新实现尚无运行或视觉验收。

## Sol 三项静态阻断修正（待Astra复核）

测试按既有 `StopActionPresentation.describe` 核本态唯一可见固定动作，记录start/stop原visibility、enabled、text、对象身份和可见按钮rect；manual/no_service_ready核可见禁用start，其余原态核stop且不强制enabled。每个图例按钮在初态和滚达帧测真实width/height≥48dp，记录像素、density和阈值。每个reach与restored保存并逐项比对原Service/chart/header/support-scroll对象、support-scroll x/y、owner shot/manual/trace/curve/点、chart trace/点/reference、referenceId/referenceCurveId、journal导出及failure状态、固定动作状态；runner逐路径读这些原值并核phase同值及对应geometry/protocol。生产SHA未变；只做`bash -n`、内嵌Python语法编译和`git diff --check`，未运行Kotlin/Android构建或CI。

### 三项封闭修正终核

Astra已实际重读修订helper/runner并核hash：生产仍 `820076de1ccc4a903db7eb22eac4c5a29c173b00207be05e8d45e4055df7dd12`；测试 `be1a4abefdce4bb621546f33d9e457fe6c9a3d0326d8af143a2b696e39d8a0cd`；runner `1d764dd93ac7747ea69c92acfcbdf9bec518f1c6aa9b471f8cc090df38820dae`。

三项已关闭：固定动作依StopActionPresentation及原start/stop对象区分，manual/no_service使用真实可见start，禁用状态保持；legend初态/reach实际宽高及density明确核48dp，runner同步；每reach与restored比较完整Service/shot/manual/trace/curve/owner与chart samples/reference/journal状态及support永久对象/scroll，原header精确恢复。runner按case明确action并交叉核各phase固定动作geometry。无剩余已确认静态阻断，可由同Sol串行叠加已计划tools/Dialog取证后统一构建；不是构建或视觉通过，旧643FAIL保持。
