# M5 Extraction 643 浅色1.3既有特殊态独立视觉复核

结论：16张原PNG已逐张查看；本格不通过。5张真实Canvas plot不足96dp：UNKNOWN_live、manual、no_service_ready、参考开启、water_reset。图表外框/轴完整和机械检查通过不能关闭这个G1/G3阻断。其余可见状态、文字层级、系列区分及指定颜色取样限定接受；右工具全目标滚达、journal不可用选择器本体仍有取证缺口。

## 原件与边界

依据 `docs/plans/2026-10-19-m5-extraction-history-visual-migration.md` §4A/§6、`docs/evidence/2026-10-19-m5-eb77b2f-visual-review.md` 的既有接受边界及全范围矩阵。只审旧643包的 `tablet-user-landscape-light-font-1.3/extraction/special-states/`；实际source `4f990722c0181504b665ab1fe91ac60832619d18`。不外推深色/1.0、其它页、后续5d08源码或新CI。

根 `build/task-first-evidence/ci-643d828-m5-pr/`；原件 `artifact.zip`，preservation记录SHA256 `38b9776a621dffdea48fa919aab6c416ac2b070edc4dd9520355275a12f485bb`。2037项CRC及5个manifest/2016引用沿用root已完成校验，本审未重复全包。16PNG按 `extraction-special-visual-index.json` 逐项重算SHA一致；所有同目录geometry、typography和window-frame-sync从ZIP按需读取，没有全量解压。

实际2560×1600，density2.25，app `[0,74,2560,1465]`，fontScale1.3。16份protocol2均accepted；这仅证明对应静态取帧，不证明视觉、硬件ACK或实时运行。所有状态是synthetic Mock输入，case明确hardwareMeasurement=false。

## 逐图结果

下列路径相对上述special-states；每行均为亲看原 `window.png`，不是只读机械报告。plot高从 `geometry.charts[0].canvasEvidence` 的 `operation=plot.boundsPx` 上下差除2.25所得，不用chart View高度替代。

| 图 | 真实plot高dp | 状态、图表及G1/G3判断 |
|---|---:|---|
| STARTING | 153.7771 | 启动命令处理中全文、时间破折号、独立秤读数12.50g；三图例/空采样说明合理，停止完整。可见区限定接受。 |
| STOP_REQUESTED | 153.7771 | “停止后确认机器停水”与禁用“停止请求处理中”明确；四图例及0秒单点不伪作结束，限定接受。 |
| UNKNOWN_live | **67.5549** | 两行unknown警示及两条readiness说明完整，实时机器值为破折号；旧样本仍明确采至0秒。plot被压薄，G1/G3不通过。 |
| historical_reference_toggle（初态） | 153.7771 | 当前杯四图例清楚，无参考说明，限定接受。 |
| historical_reference_toggle/states/extraction-reference-off | 153.7771 | 右区已滚至参考/概览入口，两按钮完整；参考关闭后无参考caption，限定接受。 |
| historical_reference_toggle/states/extraction-reference-on | **68.8882** | 日期、对齐依据、灰线仅压力历史参考/非目标预测全文可见，灰线与本杯区分；caption占高后plot不足，G1/G3不通过。 |
| historical_reference_toggle/states/extraction-reference-restored | 153.7771 | 初始右区位置恢复、caption消失；水轴量程70与首次初态60不同，不称像素相同恢复。case记录selection/scroll恢复及当前杯/journal不变。 |
| journal_unavailable（初态） | 153.7771 | 当前杯与停止按钮完整，可见区限定接受。 |
| journal_unavailable/states/extraction-journal-unavailable-choice | 153.7771 | 实图是选择后返回萃取页，并非Dialog截图。两工具按钮完整；不能据文件名签选择器视觉通过。 |
| manual | **67.5549** | 顶部明确机器手动萃取、用机器拨杆停止；本杯标题为机器手动萃取，参数破折号，禁用app开始。plot不足，G1/G3不通过。 |
| missing_samples | 153.7771 | 主秤值仍有独立实时来源；图明确尚无萃取采样，三图例，不伪造weight采样。限定接受。 |
| no_scale | 153.7771 | 第二主值切累计注水0.6ml、无weight图例，右区秤未连接及按水量结束说明可见，限定接受。 |
| no_service_ready | **67.5549** | 红色机器未就绪、模式/告警未回读，主值为空且开始禁用；空网格仍有真实plot，不豁免96dp。G1/G3不通过。 |
| sample_gap | 110.6660 | 采至7秒与缺口分段说明完整，曲线缺口真实留白、无跨缺口连线，限定接受。 |
| stale_scale | 110.6660 | 主秤值破折号，不把旧值充实时；三图例与告警过期说明完整。设备READY字样不等于fresh，限定接受所见事实。 |
| water_reset | **85.7771** | reset与缺口两行说明完整，水曲线分段、主水值0.5ml不积分伪造；说明占高后plot不足，G1/G3不通过。 |

## 四门证据

G1：两栏chart右边1778、右区左1832，相隔54px=24dp；右区宽692px=307.56dp，符合既有280–320dp范围。固定操作rect `[1868,1303,2488,1429]`，高126px=56dp，完整且不随右scroll移动。主值/辅助值层级清楚，警告未藏入更多。右区初态自然内容超过viewport，设备文字或下一工具行在scroll底缘部分露出，这是局部scroll边界，不能直接叫文字丢失，也不能直接签全工具可达。只有参考off/on及journal choice图看到参考/概览两入口；其余工具、各态滚达/恢复未由这16图全面证明。

G2：16份实际TextView快照weight仅400/500/700，letterSpacing均0、includeFontPadding均false、复制Paint未见fakeBold。Canvas轴文字均42.3px/400，关联候选字体SHA256 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`，类型来源TaskTypography.face。字体证据沿既有Noto Sans SC候选资产合同限定接受；ImageGen字体身份、实际逐glyph fallback与tnum启用仍unknown，不以数字probe/equality冒充已证明。

G3：实测主读数108px/700、主单位56.25px/400；其余实际角色42.3/45.45/53.100002/59.399998px，保留非线性缩放浮点。实际Layout及PNG未见本次所看主值、图例、警示、完整可见参数行新裁字。Canvas每个文字glyph bounds按chart-plot真实screen origin映射，16张均在真实visibleRect内；完整轴不等于96dp plot。上述5张plot不足是独立且可复现的构图阻断，不能用View高、时间轴完整或机械status改判。

G4：16PNG实际纯色取样page `(220,230)` 为 `#F9FAF9`，chart `(240,chartTop+20)` 为 `#FFFFFF`，操作内区 `(1900,1340)` 为启用 `#B5302D` 或禁用 `#ECEFF1`。Canvas实际压力 `#176BD1` 实线、水 `#007E89` 虚线、秤 `#00685F` 点/实线、温度 `#A46500` 点线；参考压力 `#7D657183`/alpha125细线。实图色与线型可区分；颜色结论限定这些取样/已画系列，不扩成所有ripple、边框和未出现状态的逐像素校验。

## 未完成项

1. 修复并新图复验上述5张真实plot不足；不缩字体、不隐警告、不降低96dp。
2. 右工具全目标可达/恢复按既有合同补齐或另审相应证据；这16张不足以关闭。
3. journal case的 `actualJournalResultFailed=true`、`chooserOnlyOffNoFakeRecord=true` 仅机械事实；本包所列choice PNG没有Dialog本体，不能签其字体/布局/错误说明可见。
4. 参考恢复case与图只能证明对应选择/scroll/本杯不变，初态与恢复轴量程不同保留原件事实，不宣称像素恢复。
5. 其它三配置、其它页面、真实硬件、当前5d08新CI均不在本次接受范围。未运行CI、未开发、未提交；本review不宣布M5或24页完成。
