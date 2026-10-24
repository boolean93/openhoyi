# M7 History首屏完整图表预算计划

> 实施使用 superpowers:executing-plans；本文件先交root审阅，再由Sol执行。共享工作树内不得覆盖他人修改。

**Goal:** 标准横屏浅深×1.0/1.3的History初态，不滚动即看全原压力/水流图、左右轴和时间刻度，同时保持完整文本、原字号与触点。

**Architecture:** 不降低图表高度，不压缩字体或padding；把两个宽度富余的纵向信息区改为自然宽度同行：页级“记录数量/只读说明＋来源切换”、预览级“名称＋只读徽标＋元信息”。宽度不足仍自然换行，图表Viewport机制与短窗路径不改。

**Tech Stack:** Kotlin原生View测量、现有HoyiUi.tabs/LegacyNaturalFlow、既有M7 instrumentation及Python host合同。

## 1. 仓库与边界

遵守 `AGENTS.md`、`docs/git-workflow.md`、`docs/plans/task-template.md`。develop派生现有 `feature/task-first-ui`，当前source `e2f48c4de3d0437dc51a356b5012bd305bb7e43d`，PR #4→develop。root统一Mock双包构建、普通合批提交/推送，四必需CI及未解决问题不绕过。无发版、签名/version/schema变更、安装或设备行为；不把Mock当实机。回退以新提交/revert PR撤销展示增量，保留原件、数据与未知记录。

## 2. 已证实的预算与设计取舍

依据 `docs/evidence/2026-10-23-m7-main-pages-visual-review.md` 的aac5c91增量实审；十二主图均已看，四个History初态真实裁切，不是Toast或host Float32误报。原图/geometry/实际TextView Layout在 `ci-aac5c91/m7-main-selected/`。依据冻结 `2026-10-16-legacy-history-layout.md`、`2026-10-16-legacy-chart-axes.md` 及现TaskProductVisualStyle角色：信息不删、原压力bar/机器水流ml/s语义不变。

现源码 `LegacyHistoryContent` 纵向标题/metadata/图/notice-action；LegacyChartViewport取 `max(minimumVisualHeight(width,160), available)`，子图保160dp但父viewport不足。四主态1.0裁39px、1.3裁160px；调整scroll不能满足初始可见。

| 原实测（density2.25） | 1.0 | 1.3 |
|---|---:|---:|
| 页级count自然文字宽/高 | 651/49px | 880/61px |
| 三tab文字自然宽 | 124/124/186px | 168/168/252px |
| 现tabs高度、count→tabs间距 | 108px、27px(12dp) | 同左 |
| 预览名称/徽标/metadata自然宽 | 216/178/571px | 236/222/770px |
| 预览metadata高＋旧8dp上距 | 49+18=67px | 61+18=79px |
| 原图所需补足高度 | 39px | 160px |

推荐两个区域同行：页级节约count高度+原tabs上距=76/88px；预览级节约67/79px，合计143/167px。1.3所需160px可覆盖，余量约7px，**这是基于原实际测量的可行预算，不代替新布局实际测量**。不靠削padding凑2px。

其他方案不选：仅预览头同行最多79px，不足160；降低plot到96/缩字/删警示直接违反标准态；改全页单栏/删列表或把图滚达当完整均改变原任务体验。当前方案只重排两个信息组，保原34:66列表详情、24dp栏距与原固定操作。

## 3. 精确展示方案与文件

### A. 页级信息栏

修改 `mobile/src/main/kotlin/io/openhoyi/mobile/LegacyHistoryActivity.kt`：把原count与HoyiUi.tabs放入本页私有自适应容器。仅创建时挂载一次，不在onMeasure中换父节点；HoyiUi.tabs现有三个TextView、listener/selected/accessibility/来源跳转原样保留，不改共享HoyiUi。

- 宽度足够：count置逻辑start、tabs占剩余宽，组间24dp；三tab仍等宽、8dp间距、最小48dp高，文字完整居中。count与tabs垂直居中。原count的8dp上距保留在容器外；同行时取消原属于“第二行”的12dp上距，不新增负margin。
- 判定使用实际count自然宽（含padding）与三个tab文字/compoundPadding的自然需求。等宽tab最低宽为三者最大自然宽与48dp的较大值；三格+两8dp间距，再加count与24dp间距必须≤可用宽。不能按字符数或fontScale硬编码分支。
- 原1.3可用2308px，count880＋54px组距后剩1374px；扣两18px间距，单tab446px≥最大252px文字/48dp触点，自然宽有余量。实际控件padding仍应参与运行测量。
- 宽度不足：count完整自然高占一行，tabs下一行保原12dp间距及三等宽行为；不得ellipsize、改textSize或隐藏count。RTL按逻辑start/end布局。两种状态以当前自然宽决定，避免受上一次收窄宽度缓存影响；数据加载更新count后正常requestLayout。
- 私有小容器可留在本文件，职责仅该页两子项测量/布局，不新建通用响应框架。

### B. 预览信息头

修改 `mobile/src/main/kotlin/io/openhoyi/mobile/LegacyHistoryContent.kt`：复用当前 `LegacyNaturalFlow(context,16dp,8dp)`，把同一个metadata TextView作为title、readonly之后第三组加入flow，去掉独立 `style.add(this,metadata,8)`。不改任何文案或角色。

- 原1.3自然宽236+222+770+两36px间距=1300px≤可用1398px，三组一行高max(86,79,61)=86px。1.0为1037px≤1398px。
- 名称、徽标和metadata各组完整测量；长名称/窄屏按原flow的8dp行距换行，不删日期/时长/只读、不强行单行或截断。此处只需复用自然flow，不替换图表对象或正文。
- title24角色/500、metadata与徽标14角色/400、颜色、panel16dp四边、chart上16dp（初版；2026-10-24后续批准修订为关联8dp，见末节）、footer上16dp、notice与action16dp、action48dp、24dp栏距全部保留。沿用现Flow顶部对齐语义，不为本批改共享Flow的对齐规则。
- `LegacyChartViewport`、`LegacyShotChartView`数据/字号/图例/轴布局/160dp最小图、Detail/Curve页和短窗96dp策略不改。选中ID、列表位置、加载generation、scroll恢复和详情返回不动。

## 4. 已有测试的最小补强

仅涉及：
- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/LegacyVisualMigrationChecks.kt` 的**普通History主态** `history-main-axis-reachability`；
- `scripts/test_legacy_history_axis_contract.py`；如需host核完整chart view，`scripts/check_legacy_visual_migration.sh` 原history分支最多增加对应断言。

步骤：

- [ ] 先对aac原件以新“initial必须完整”的要求确认四配置确实失败，保留失败理由，不要求旧原件变绿。以现有initial几何与timeTicks为依据，不另造场景。
- [ ] 普通主态主图采集后、任何requestRectangleOnScreen之前：要求原chart整个view rect==visibleRect、全部time tick wholeGlyph且screen在clip内、actualPlotHeightDp≥160、visiblePlotHeightDp≥160；仍核固定详情动作≥48dp和完整文字。完整chart view保证不是仅把时间刻度挪上来而裁掉上轴/图例。保原记录/Canvas操作，不允许预滚动代替首屏布局。
- [ ] 在现有axis函数加 `verify(initial,true)`（在原try内、request之前，保finally恢复）；原reached/restored capture与严格对象/数据/scroll恢复继续，初态已全部可见时requestRectangleReturned=false是合法实值，不为制造滚动强迫移动。既有函数不要求moved=true，不需删除此证据。
- [ ] host validate对initial/reached/restored都执行原wholeGlyph与clip条件；增加visible plot原字段的160下限（若复用已有字段即可，不新造预算估值）。保Float32来源精确重建，不加epsilon。现整数/序列化Float样本保留，补一个“仅initial裁切、reached完整”的拒绝反例，避免回归只验reached。
- [ ] 用现已有标准四配置CI主图再次核字体/间距/颜色、所有图例/左右轴/时间轴首屏完整、count三tabs及完整警示/action。原协议5秒四零、字体原合同、列表/模型恢复保持。新图才可关闭aac主态裁切；不需要重做此前80轴字/短窗图审或增加语言/设备矩阵。原CI若自然执行其它既有场景仍须通过，不能跳过。

## 5. 交付与停止边界

Sol冻结上述限定文件SHA，Astra静态核自然宽、测量稳定性、RTL、控件身份/监听、首屏断言；root跑 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 和限定Python合同，普通PR增量CI。旧数据/原件不改、不重新生成“通过图”。无设备写入或控制协议风险，无安装/升级/发版授权。

若实际1.3两区域同行后的chart仍差高度，记录真实各组测量与差额并回报，不擅自缩字、减16dp padding、改160/48下限或把滚动包装成初态完整；当前自然预算已给出具体可行候选，不能仅凭7px估计声称运行必过。

本文件仅规划，root阅后才交Sol实施；不夹带翻译扩展、Import产品修复或第三项UI改造。

### 交付状态

root已阅读完整计划并认可上述两处自然宽同行设计；已交Sol按限定范围实施。此批准来自当前任务内root的执行协调，不冒称用户逐字批准布局细节。尚未实施验收/构建/新四配置视觉通过。

## Root构建证据

统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 成功，1m6s、91 tasks（9 executed / 82 up-to-date）。日志 `build/task-first-evidence/ui-2026-10-24-history-initial/build.log`；构建前后303个Kotlin源SHA一致，见同目录source-before/source-after.json。Root独立6项host合同及diff-check通过。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA256：`0ab0b8f91f4e9e774b0c03e1c947ce72629b0cd6d63d571c099034c14993ef53`
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA256：`30c2addce50aca4f439bbec7204ec8971af93c43f231e0cb098f2eb5799bceea`

仅编译，未安装/发布；四配置新首屏是否完整仍待CI真实截图，不能由静态预算或构建成功签过。

### Astra冻结静态验收（2026-10-24）

四文件SHA256：

| 文件 | SHA256 |
|---|---|
| LegacyHistoryActivity.kt | `8bd80e2e637f838cb2ee41273ab407aed606a54209017677613b193fb66796a6` |
| LegacyHistoryContent.kt | `3a2a066bf8ec73d8a0701143dac818cd05e15012cdf2834bd33ac13c46eeec51` |
| LegacyVisualMigrationChecks.kt | `65a21e26369beb0f3cc4a80edb0e6675f15ee17b5879f7face6d498c8509ebdc` |
| test_legacy_history_axis_contract.py | `a125ce5472b7b46c75e117bdd48d65899bd6b330b730079f44e7a229b9d5da1d` |

完整diff已审：每轮count/tab先UNSPEC自然测量，三等宽tab按max自然宽/48dp及8dp间距计入，24dp组距；两排/同行LP每次恢复，forceLayout确保后续测量，RTL使用marginStart并resolve方向，原tab对象/监听一次挂载，未改共享HoyiUi。metadata仅移入原flow第三组，未改角色、padding或图。初态chartRect==visibleRect、verify(initial,true)确在request之前且仍原try/finally；原恢复/固定动作/Float32精确screen合同保留。静态无阻断。

独立 `python3 scripts/test_legacy_history_axis_contract.py` 6 tests OK，diff-check0。旧aac四配置反证记录 `ci-aac5c91/m7-history-old-initial-gate.json` SHA `8480d48659864e36ccc1c1ec6b451e62dc7a195bdffebbed3027ace106d1c4b5` 已核：原39/160px和五tick不完整均应被拒绝。此为静态/离线接受，不等于新Android布局测量或四配置新图通过；构建与实际图由root后续证据追加。

## 2026-10-24 新原件4px缺口的设计复核（root已批准后续间距修订，实施另验）

新run40058182253实际source `2cb3cb816829f8d0382623718f018a8d2804cd55`（PR merge，不等同head1365e1f）。Sol已定向测量 `ci-1365e1f-legacy/history-initial-budget-measurement.json` SHA `fca7e366115706cff610cd2f391825d8a77132d038c5ca40ddb892962e172ae8`：light1.3两组确实同行，chart643px、viewport639px，底裁4；plot160.1106dp及5ticks完整，首attempt5秒四零通过，但完整chart初态断言正确失败。不得以ticks完整降级整个chart要求。

**11px不是自然高度损失。** Astra只增量核新旧原typography：标题同59.399998px、View高86，旧origin[351,110]→新[351,121]；HOYI旧y99→110，前部nav同+11；旧app顶部63→新74，底端1465未变。count仍61px、tabs仍108px。故页级真正重排收益仍88px，预览79px，合计167；绝对collection top只前移77，是窗口顶部少11px抵消了收益。旧160px缺口加11=171，167后剩4。先前计划预算未覆盖这次系统栏差异，不能归责tab测量或再修不存在的自然高回归。

root明确批准的后续设计修订：当前预览名称/只读徽标/metadata已合并为同一图表标题组，将**该标题组到chart的关联间距由16dp改为8dp**，作为此次合并后的明确产品间距设计更新，不按4px减padding、不动panel内距或底部独立警示/动作16dp。净增18px空间，原4px缺口有14px余量；不承诺任意短窗首屏全部可见，既有短窗viewport合同保持。root已明确认可这一个间距token更新；不是说旧冻结16dp已允许随意削减。

仅改 `LegacyHistoryContent.kt` 中chartViewport的topMargin一处16→8（Astra已核生产调用点仅LegacyHistoryActivity第57行，独立Detail不调用此组件），不改shared tokens、chart.minimumVisualHeight(width,160)、footer、字体、48dp触点、任何rect严格相等或plot门槛。相比增加测量循环的动态4px补偿，固定关联8dp保持页面一致且更窄；不新增响应式框架、不改96/160规则。

Sol按此批准范围交单点SHA，Astra静态核，root统一Mock双包构建及原四主态/既有特殊态CI；新真实首屏完整chart与G1–G4仍须验，失败继续保原件。既有PR #4→develop、无设备发布/签名升级及新revert回退规则不变。当前已完成设计批准与规格同步，源码由Sol单点实施并另行静态/构建验收；本节不预签实施或新图。

### related8 单点冻结静态验收

Astra核唯一diff为 `LegacyHistoryContent.kt` chartViewport.topMargin `style.dp(16)`→`style.dp(8)`；最终SHA256 `90f27b43a05bd8d47589785c61c4baa38c228fc4df38fc497b5700fc45277d7d`，独立重算吻合。唯一生产调用仍History；panel/footer/字体/160plot/48touch/whole检查未动。静态通过，root构建另记；新真实窗口完整性和图审仍未签，未由Astra改源码或运行设备。

### 关联间距修订Root构建

统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 成功，30s、91 tasks（5 executed / 86 up-to-date）。日志 `build/task-first-evidence/ui-2026-10-24-history-related-gap/build.log`；303个Kotlin源构建前后SHA一致，见同目录source-before/source-after.json。diff-check通过。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA256：`cdfe0ee3aa8085952298f7a26d35f750af02108f61cea74a26bd685b8d5a5cc5`
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA256：`94deec8c8d681fc4f146da2202591b10e6182989233229dcd13e825d15297b17`

没有安装/发布/合并；旧1365首屏仍裁4px事实保留，新8dp规格对应真实四主态与严格完整图验收待CI。
