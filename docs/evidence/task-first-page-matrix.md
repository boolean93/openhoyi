# 全范围视觉验收状态矩阵

## 当前状态（优先于下方历史观察时点）

2026-10-19：最近已推送 head `5ef3b8f2efba1ce2d2469bc51a6815817ecc938b`，PR tested merge `f15b6d66579445f1bba7e328ebbc39679b3a1356`。native39187428091、single39187428132、advanced39187428111、lifecycle39187428154、upgrade39187428126、branch39187428120均成功；frame39187428163、curve39187428152、bean39187428107均失败。普通回归通过不替代四项视觉验收。M1仅原三页限定通过；M2/M3/M4分别只取得1/12、2/12、1/12主图，其余配置未完成。

M2最新恢复记录的11字段值、selection、focusedFieldKey、draftId、stageCount一致；IME true→false、窗口680→1391、合法scroll range/y 662→0。原包69份M2 JSON没有产品恢复诊断，尚不能确定请求未发出、取消或时序根因。M2生产不猜修，只补断言前独立诊断，原比较对象、精确断言、稳定协议和IME操作不变。

本批修复冻结为Import页内真实IME/滚动恢复、Preparation宽优先受限双栏及测量缓存祖先链修正，加三份测试诊断。Astra已静态复核无阻断；最终验收仍由Astra完成，不替换模型。隔离基线 `a42f931183e6776819e37c7d4d2dd836ceebca46`，仅应用上述五文件，排除未完成M5。隔离构建成功（1m28s，98 tasks），bean-core verify及11 suites/48 tests通过，0失败/错误/跳过；Mock和AndroidTest APK均已构建。patch SHA256 `8ccaeb8ca91aa99a11a304bdc1422c113324a41a1957ce191dda1e7ce7040989`，日志/XML/patch留存 `build/task-first-evidence/isolated-ime-repair-a42f931/`。新CI仍待运行，不预先标记运行修复成功。


2026-10-19，最新已验产品证据head `026f993` / frame `39146205031` / tested merge `75e5333f20bb21ec911ec99befb9d979ad483dba`。M1三页已在中文用户横屏、浅深×1.0/1.3主夹具范围获得G1–G4限定验收；**不是24页/全状态最终验收**。其余21页、硬件与未采状态仍待验。详细审查集中于[M1计划§11](../plans/2026-10-18-product-visual-migration.md#11-2026-10-19-026f993-真实-m1-限定验收进入-m2)。 M2最新历史正式证据为 `0647518` 的1/12主图与Editor首屏FAIL（详见下一段）；`9b51936` 已推送候选修复及M3实现，PR frame `39153382452`、curve-visual `39153382312` 正在运行，尚无新视觉验收结论。95be8fb的0/12保留为历史，不再作为最新进度。

**0647518 本轮追加证据（不关闭未测项）：** frame `39150740279` / tested merge `640315453618dcb29ba8ec2cbe2fb5af53d0efb9` 正式M2主图1/12，仅Editor浅色1.0；原11字段首屏断言失败。实际 available1040 / metadata253 / stage766 / previewMinimum741 / fullNatural1055，第四行两个117px输入各仅102px可见，固定footer完整。主图协议2及隔离通过不抵消G1 FAIL；IME/业务恢复未执行，Bean/Review正式夹具未进。新业务PNG仅观察到入库间隔与复盘长选豆第二行改善，精确间距/全状态仍待验。native `39150740210`、advanced `39150740270` 两job通过；single `39150740256` 标准/大字号通过、用户平板失败。M1本轮12配置机械回归无失败，fresh-process13份实测有效；不外推为M2视觉通过。 编辑页候选修正 `ee7f270` 已单独提交：仅在满字段纵排超高且三卡实际宽度可容纳时，元信息卡内部条件同行；原字体、8/16间距、52dp输入与11字段首屏门禁不变。静态审及生产/AndroidTest构建已通过，9 suites/31 tests通过；尚未取得新运行原图，不关闭上述FAIL。

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。本次只同步事实，不操作设备、不合并/发版；检查24页覆盖、状态值、引用及文档差异。后续由主任务合批普通push，可追加revert回退。

## 统一取证要求

- 已批准稿件保持不变；中文、用户横屏2560×1600/density360/fontScale1.0优先；系统栏另计。大字号、短高、IME为额外检查。
- 同业务状态的实际图、真实字体/布局测量、逐项差异核对才可验收；框架空态通过不能代替有数据页。
- 字体固定源及Google/Apple应用依据见既有研究与详细审计；固定字体身份不证明ImageGen使用相同字体。
- 下表初始框架证据来自f525db3/run38888397896；当时24份JSON已逐一解析（density/fontScale核实），但不声称24张PNG均已人工比较。较新限定结论以各行精确源码及[Astra review §10/§11](2026-10-18-native-specimen-review.md)为准；严格基线§6与旧记录保留追溯，不能覆盖新失败。下表“字体测量条目”仍是历史框架快照计数，不是M1新取证总数。

**9b51936 M3首轮push失败证据：** 独立run `39153376290` 已结束FAIL，实际source=head `9b51936fa8ab86a8bc15fc07c0f8c265346b93d2`，不是PR merge。完整小产物位于 `build/task-first-evidence/ci-9b51936-curve-push/curve-visual-tablet-user-landscape/`。主图仅2/12（Library与Import浅色1.0），Preview未进入。Library业务/隔离机械通过不等于G1–G4完整通过。Import的IME稳定现场app2560×631，raw输入框 `[216,514,2524,514]` 高0、可见区空、92行实际Layout高4966/contentHeight−36；JSON标题Layout65px但contentHeight28，截图证实标题裁切/输入消失，G1/G3 FAIL。line647的focus与scroll联合断言为停止位置，原断言前未输出这两个字段，具体子条件仍UNKNOWN，不把真实布局问题改称测试假红。字号恢复另发现缺省font_scale被写成字面null，测试owner修复原存在性恢复；不抵消主要失败。PR `39153382312` 保持独立观察，不取消或以push结果冒充其终态。

**9b51936 PR终态与等待验收：** 本轮PR全部已结束：native39153382347、single39153382406、advanced39153382336、lifecycle39153382344、upgrade39153382315、branch39153382305成功；frame39153382452与curve39153382312失败。frame原日志明确 `M2 form recreation changed draft/focus/selection/identity/scroll: editor-stage-hidden-values`（AdvancedBusinessUiChecks:603/699，FormVisualMigrationChecks:451）；已读取原restore.json：所有字段值/selection、focusedFieldKey及draftId/stageCount一致；差异是IME true→false、窗口2560×680→2560×1391、content-scroll y2147483647→0。测试在重建前对ScrollView调用scrollTo(Int.MAX_VALUE)，异常最大整数值及IME恢复的根因尚未判定，不能宣称字段丢失或把它视为合格滚动夹具；待Sol/Astra审查，不据single绿关闭M2。用户已选择保留Astra最终验收并等待额度恢复，不换模型代审。生产Import修复未落地；测试owner的断言前状态取证与font_scale存在性恢复两文件已有未提交修改，尚未完成统一验证，不当已修。原CI在途表述只代表此前观察时点，以本段终态为准。

**9b51936 Editor初态限定复验（Astra实看）：** PR frame原包实际merge `d0c281e351817e9e3ebc43300569f3e971e2f9b2`，关联head9b51936。浅色font1.0主图第4阶段完整：metadata189px（旧253），inlineSelected=true，fullNatural991/available1040；target3 `[450,1083,896,1200]` 与water3 `[914,1083,1360,1200]` 均117px且full=visible。限定关闭064相同正式主夹具的15px首屏缺口；不外推其他主题/字号，不关闭后续恢复失败，也不记M2完整G1–G4通过。

**5550a98 本轮M2恢复实证：** PR frame `39185179305` 已FAIL，实际merge `082a3f518e1acbfd3348d4df13618020278f18b4`，M2仍仅Editor浅1.0主图1/12。修正有限范围夹具后，原restore记录只差IME true→false、窗口高度680→1391、外滚动y662→0；实际range同步662→0，因此0是不同viewport下合法clamp，不能独立称“滚动丢失”。11字段值/selection、focusedFieldKey、draftId、stageCount均一致。已证IME重建未保持，唯一根因仍待审，不放宽精确恢复门禁。原包在 `build/task-first-evidence/ci-5550a98-frame/task-frame-tablet-user-landscape.raw.zip`，928项CRC通过、SHA256与GitHub digest一致；仅提取33份小文件，完整原包保留。M3的5550 inline构造故障与单literal修复见M3计划§8，未进入IME，旧零高度FAIL未关闭。

**5550a98 常规回归终态补记：** PR native39185179371、single39185179274、lifecycle39185179343、upgrade39185179420及branch39185179406均成功。advanced39185179289整体失败，下载business小原件后确认17条路径输出，最后在AdvancedBusinessUiChecks:1338报 `Import raw text must edit inline`；与M3独立检查发现的构造期inline祖先丢失一致，不把前17路径当18业务全部成功。原件保留 `build/task-first-evidence/ci-5550a98-advanced-business/business-flows.txt`。该生产单literal候选已单独提交56a48de，尚未新运行复验；M2真实IME恢复候选另由Sol按Astra约束实施，不改测试主动打开IME。

**M4实现/构建状态：** 三页及独立完整12配置测试入口已实现，Astra静态终审无剩余阻断；与M2 IME恢复候选统一构建成功，库存核心检查及11 suites/48 tests通过。实际运行与G1–G4仍待新CI，不把实现和编译记为视觉通过。

**5ef3b8f 首轮新增实证：** curve39187428152与bean39187428107均FAIL，实际merge/source `f15b6d66579445f1bba7e328ebbc39679b3a1356`。M3仅2/12，Import文本/selection/focus均保留，raw scroll4885→4281分别等当时合法最大值（4966−81与4966−685）；IME true→false导致viewport改变，不能独立称丢scroll，旧focus构造失败已越过、完整恢复仍失败。M4仅Preparation浅1.0主图1/12；IME时root `[0,63,2560,754]`，support-pane `[216,345,2524,1751]`被裁，actions `[252,1463,2488,1715]`越窗底，两操作不可见；金额输入117px完整，不能代替固定操作门禁，G1 FAIL。两scope隔离PASS不抵消业务/视觉失败。原ZIP/CRC/SHA与派生报告保存在 `build/task-first-evidence/ci-5ef3b8f-{curve,bean}/`，frame39187428163当次观察仍在运行。

## 最新分层结论（样张、生产实现、运行证据分开）

- **样张封闭，有限冻结已成立**：[review §10](2026-10-18-native-specimen-review.md#10-0a7b71a-封闭验收有限冻结进入-m1) 的 head `0a7b71a` / run `39140082042` / tested merge `908edcd9d83a8ea53123ba772ab7556ab6e2b4ed`，Astra已实看三页12初图及6滚动后图。冻结的是该批实际覆盖的字体角色、间距、浅深语义色、单位baseline、图表标签保护/网格和局部滚动设计输入；原文件保留proposed/productAcceptance=false历史身份。原图字体身份仍未知；未观察的焦点/按压等状态不因此通过。
- **M1真实三页限定通过**：`026f993` / frame `39146205031` 三页12主配置已逐张实看，另查两主题1.3共8张滚动后图及长名business图。标准两最近/两流水/完整四阶段表成立；大字局部滚动与固定dock符合已定适配。G1–G4各自边界见下表及[M1计划§11](../plans/2026-10-18-product-visual-migration.md)。不是Mock样张外推。
- **旧M1红项在相同限定场景闭环**：库存header横向溢出/两事件预算、Share短名13px与长名54px缺表已由026真实证据关闭；原0f3b759/cb失败详见下方历史快照，保留追溯，不继续标作当前相同场景FAIL。
- **M2修复待复验，M3实施待首验**：最新候选 `9b51936` 已推送；9 suites/31 tests及Mock/AndroidTest构建通过。M2历史正式证据为064的1/12，Editor满字段自然高1055px超过1040px，G1仍FAIL；Bean/Review仅业务图观察到改善，未完成正式四门。当前frame `39153382452` 与独立M3 `39153382312` 分别覆盖各自计划12配置，运行中不记通过。原五/11字段、52dp、IME、草稿/焦点/selection和实际滚动恢复门禁不降。

## 24页状态

| 页面 | 认可稿/范围 | 框架证据状态与缺口 | 字体测量条目 |
|---|---|---|---|
| HomeActivity | [准备v2](../research/task-first-ui-concepts/brew-preparation-v2.png) | **M1限定PASS（G1–G4）**：026f993真实浅深×1.0/1.3共4主图及局部滚动核对完成。真实状态卡/摘要/分隔/最近/固定操作已对齐已定生产适配；标准2条最近、大字1条，全部3条可达；3组单位baseline/配置为0。两主题1.3秤状态滚后完整，固定确认/按钮/详情不滚走。字体与色值边界见M1计划§11；未测状态/硬件不外推。 | 25 |
| ExtractionActivity | [满配v3](../research/task-first-ui-concepts/extraction-four-series-v3.png)、[无秤v2](../research/task-first-ui-concepts/extraction-no-scale-v2.png) | 784eaaf用户平板有秤/无秤实际启动截图通过；21/0重量样本、四/三指标、参数行/停止完整；绘图区514.148px。1b0deb4无告警行已收起且绘图区增至624.148px，637f83f实图确认图例含实时值、次要读数数值突出；有秤绘图区599.148px、无秤624.148px，停止完整。bcda211两态实图确认48sp数字/22sp单位，绘图区565.148px、停止完整；异常/参考/大字与最终视觉仍未验收 | 33 |
| CurveActivity | [曲线库v2](../research/task-first-ui-concepts/curve-library-v2.png) | **10-18最新：Astra已读a4786a0关联lifecycle/run39119880608的4张真实成功整窗及前后geometry：中文浅色、density200、1280×703dp、font1.0/1.3，longFactory/noPressure均显示无压力“深烘拿铁”。主按钮469×70px全幅可见，父行947×70px；详情468×60px全幅可见、局部y5..65与主按钮y0..70中心相同。关闭这两个fixture/字号的原3px底裁切。追加用户配置single/run39119880590：Astra已读2560×1600/density360/font1.0中文浅色实际整窗、populated及typography；选中“采集曲线1”（91°C/27g/两阶段），主按钮738×126px、详情738×108px均全幅可见，父行1494×126px，两按钮垂直中心均1375px，关闭该用户配置状态的底裁切。** 不外推未取证状态或严格视觉。single结果336/336，列表专项14夹具；Curve返回/重建保持factory-v3-007、position=2、offset=-142，重建保留query=factory-v3/filter=DARK/sort=MOST_USED，属于限定列表恢复通过，不是硬件动作验收。用户证据位于`build/task-first-evidence/ci-a4786a0-single-user/tablet-user-landscape/single-screen-window-checks/`的`zh-Hans-light-CurveActivity.png`、`states/...-populated.png`、`visual-baselines/zh-Hans-light-CurveActivity/{window.png,CurveActivity-typography.json}`及`list-state-result.txt`。 证据在`build/task-first-evidence/ci-a4786a0-lifecycle/mock-lifecycle/language-detail-success/footer-{landscape,wideFont}_zh-Hans_dark=false_page=CurveActivity-{longFactory,noPressure}-window.{png,txt}`。历史3b5f29f/0f0beda的469×67px失败保留追溯，已由上述限定新证据替代；根因及a7fcc86修正见[footer证据](2026-10-18-curve-footer-clipping.md)。103条列表/恢复、四阶段只读fixture等各有原范围，不冒充可执行选择、稿件或硬件验收；G1–G4仍未通过 | 31 |
| HistoryActivity | [记录v3](../research/task-first-ui-concepts/history-list-v3.png) | 空记录列表 | 17 |
| HistoryDetailActivity | [详情v2](../research/task-first-ui-concepts/history-detail-v2.png) | 712913e用户平板深色Mock结束实图确认主数值/小单位、左栏图表延伸页底、右栏分隔线已生效；121采样四指标可读。复盘字段空态，长内容/未知/缺重/大字与完整入口仍未最终验收 | 8 |
| BrewComparisonActivity | [对比v1](../research/task-first-ui-concepts/brew-comparison-v1.png) | 4240daa大字号336夹具通过；不同31/33点四指标整页已读取，实虚线/单位和30/32秒、36/35.2g吻合；记录入口已前移待新图，用户平板标准字号实图及24/16/14sp已核对；39ac261轴留白两字号10组件夹具已运行通过；整页图高/顶栏比例仍待新图验收 | 9 |
| BeanInventoryActivity | [库存v3](../research/task-first-ui-concepts/bean-inventory-v3.png) | **M1限定PASS（G1–G4）**：026f993真实4主图、1.3滚动后及长名business已验。页头/列表层次/日期分隔/流水/固定三操作说明齐；真实48dp事件与已批准摘要适配保留。标准两事件首屏，1.3第二事件完整滚达；长名business两事件各108px完整。余额单位baseline/配置0，三事件/详情/恢复4组通过，关闭旧header/两事件回归；其余状态与硬件不外推。 | 11 |
| BeanEntryActivity | [入库v2](../research/task-first-ui-concepts/bean-entry-v2.png) | **当前：9b51936待正式复验；064业务图间隔改善，但精确16dp与完整四门未验。以下95及更早为历史。** **M2已实施/已见FAIL与其余缺证见四门表：95be8fb包含条件3:2同行、日期间距归一、自然52dp/固定操作与原五字段稳定初态取证；本地17测试及assemble通过，本轮CI已失败，当前差异与缺证以本页四门表及M2计划§8为准。历史G1失败保留，不能当作本轮已修或当前已验FAIL：0a7b71a / single-user39140082109为334/336，中文浅深新建初态第五EditText仅116/117px；task-form-scroll viewport995px、自然内容996px。失败在五字段首屏断言，尚未进入section/short/IME。** 详见[review §11](2026-10-18-native-specimen-review.md#11-beanentry独立m2待办标准初态末字段1px预算不足)及 `build/task-first-evidence/ci-0a7b71a-single-user/tablet-user-landscape/`。M2已补五字段断言前的稳定初态/失败现场采集，保留五字段首屏/52dp自然高度/固定footer，不先reveal或容忍1px；旧唯一根因仍未证，待本轮真实结果。已有豆款历史通过不覆盖新建失败。历史限定证据：**旧版本10-18：Astra已读3b5f29f用户横屏中文浅色font1.0已有豆款模式：仅选择器显示身份，无重复只读名称框，三日期及取消/保存完整；18路径含模式草稿/重建/取消不变量。关闭该标准状态重复身份与第三日期首屏缺口，不外推新豆款1.3、IME或严格视觉。** 5abe9c2新豆款1.3开封日期需局部滚动的记录保留，不能与已有豆款标准态混为一谈。历史35db865日历入口、ff651be双模式/草稿、b6fc53c业务18路径和166594d当时三日期专项仍仅作用于对应源码/状态；同一深色section-2原图保存文字完整的纠正保留。14/16sp及取消说明有实现，不等于G1–G4验收 | 20 |
| BeanBatchActivity | [批次v2](../research/task-first-ui-concepts/bean-batch-v2.png) | **当前：5ef3b8f M4已实施，正式12配置待CI，以下为旧版本历史，不代表当前新布局已验。** 63a490c业务实图确认90/100g、90%同行、三日期分区及两条流水；与当前BeanBatchActivity无差异。v2已纠正虚构时间；后续6a61d4a进度条/底部说明待新图。用户平板/大字/长原因及字体间距仍待最终验收 | 8 |
| BeanLedgerActivity | [流水v3](../research/task-first-ui-concepts/bean-ledger-v3.png) | **当前：5ef3b8f M4已实施，正式12配置待CI，以下为旧版本历史，不代表当前新布局已验。** 051f16e用户平板有事件图已读：入库+250g/余额250g、批次与原因五列对齐，最新记录在前；当前图未覆盖扣减/调整/长原因，时间列无数据源，仍未最终验收 | 14 |
| BeanPreparationActivity | [用豆v1](../research/task-first-ui-concepts/bean-preparation-v1.png) | **当前：5ef3b8f M4已实施，正式12配置待CI，以下为旧版本历史，不代表当前新布局已验。** 8c16949业务18路径通过，三状态实图已读；左右列表/表单成立，63a490c大号余额90g→72g两态实图已读，预览/流水区分清楚；合法粉量仍留旧错误，候选已修待回归；确认态左侧批次列表/右侧摘要已调整待新图；粉量控件与底部操作区仍未完整对齐 | 10 |
| BrewReviewActivity | [复盘v3](../research/task-first-ui-concepts/brew-review-v3.png) | **当前：9b51936待正式复验；064业务图长选豆第二行完整，advanced通过，正式12配置四门仍缺。以下95及更早为历史。** **M2已实施/已见FAIL与其余缺证见四门表：95be8fb包含冻结字体/语义色、真实两行StaticLayout预算、固定操作及真实草稿/IME取证；本地17测试与assemble通过，本轮CI已失败，当前差异与缺证以本页四门表及M2计划§8为准。历史较新限定证据：a2164ad/run39138935786三横屏profile、浅深dose经稳定IME→单次reveal均完整，固定按钮保留；生产布局未改，无依据盲改预算，旧失败唯一原因未证。见review §10.4。本轮严格四门仍待验。** 历史限定证据：**旧版本10-18：0649834用户平板有内容实图已读：时长/克重小单位生效，三段笔记与取消/保存可见；长参数局部滚动，整页字体/比例未最终验收。** 4bd173c业务18路径通过，有内容实图已确认三段记录与取消/保存，仍为旧1600×1000配置；秒/克同大粗体差异本轮修正候选，用户平板和最终视觉待验。 b6fc53c保存后实图：左右参数/记录、三段填写、时长/秤值/结束状态和保存完整；右上分类选择未与认可稿对应，分区线与底部保存范围说明已补，构建通过待新图；候选已移除重复分组选择、统一16sp输入并补取消，五组长文检查保留，待运行；整页比例仍未验收 | 9 |
| CurveEditorActivity | [编辑v1](../research/task-first-ui-concepts/curve-editor-v1.png) | **当前：064正式1/12证实1055/1040自然预算与15px裁切；9b51936条件元信息同行修复待新frame。以下95及更早为历史。** **M2已实施/已见FAIL与其余缺证见四门表：95be8fb包含阶段参数/分隔、自然52dp预算、永久内容滚动host及独立固定footer，原标准11字段门禁不降；本地17测试与assemble通过，本轮CI已失败，当前差异与缺证以本页四门表及M2计划§8为准。历史较新限定证据：a2164ad/run39138935786三横屏profile、浅深name经稳定IME→单次reveal均完整；不等于全页四门通过或旧失败唯一原因证明。见review §10.4。本轮真实四阶段/IME/草稿与四门待验。** 历史限定证据：35db865用户平板四阶段实图：1–4分段选择生效，11输入/预览/保存完整；字体、间距和预览比例仍需最终对照 | 31 |
| CurveShareActivity | [分享v1](../research/task-first-ui-concepts/curve-share-v1.png) | **M1限定PASS（G1–G4）**：026f993真实4主图、两主题1.3 after5/6实图及长名business已验。标准短名与长名五行表首屏完整，关闭旧13px/54px缺表；1.3第4行/隐私经局部滚动完整，不声称全部首屏。两组单位baseline/配置0；图标签/grid、表格H8、mode/动作图标/禁用与两主题颜色符合冻结输入。SAF4组通过；其他状态不外推。 | 9 |
| CurveImportActivity | [导入v2](../research/task-first-ui-concepts/curve-import-v2.png) | 空JSON输入；合法/非法/键盘另验 | 13 |
| CurveImportPreviewActivity | [冲突预览v1](../research/task-first-ui-concepts/curve-import-preview-v1.png) | **10-18最新：Astra已读a4786a0关联advanced/run39119880424中文浅色四阶段滚动前后4张真实整窗及geometry，用户窗口1138×618dp。font1.0表1470×356px、图1470×450px，默认首屏图表完整，scrollY=0；font1.3表1470×431px仍显示，图1470×360px（160dp下限），首屏表尾41px在左滚动区外，scrollY=41后全表完整；5行均可达，右操作区滚动前后Rect(1713,263–2533,736)不变。关闭原1.3整表隐藏/不可达缺陷，不声称大字全表默认首屏完整。** 证据在`build/task-first-evidence/ci-a4786a0-advanced-pages/advanced-page-screenshots/{landscape,wideFont}/import-preview-reachability/zh-Hans-light-CurveImportPreviewActivity-{before,after}-scroll.{png,txt}`及同profile顶层geometry。压力3/9/7/5bar、水量15/25/15/5ml各四行可读；1.3滚到底时配方名上缘随左栏滚动移出可见区，不是隐藏数据。0f0beda/run39117264495的tableShown=false失败已被上述限定新证据替代，仍保留追溯；冲突/其他字号、原稿字体/间距/配色及G1–G4未验收 | 9 |
| ScaleActivity | [电子秤v2](../research/task-first-ui-concepts/scale-v2.png) | 55f3f3f普通/大字号实图已证单位缩小与设备两列；仍无有效重量/趋势，用户平板及设备身份待验 | 25 |
| ScaleConnectionActivity | [连接v1](../research/task-first-ui-concepts/scale-connection-v1.png) | 无候选；候选/连接/实验协议限制需另验 | 13 |
| LegacyCurveActivity | [旧曲线v1](../research/task-first-ui-concepts/legacy-curve-v1.png) | 051f16e用户平板40条列表实图确认连续列表，但导入工具栏及未选中提示已改待图；选中重建图已读，参数真实；名称/来源与四格参数已分组待新图，完整说明保留，未最终验收 | 13 |
| LegacyHistoryActivity | [旧记录v1](../research/task-first-ui-concepts/legacy-history-v1.png) | 框架零条；另已读27ae12b的41条记录列表，非空列表成立，构图待对齐 | 13 |
| LegacyHistoryDetailActivity | [旧详情v1](../research/task-first-ui-concepts/legacy-history-detail-v1.png) | 051f16e用户平板浅深实图确认：bar/ml/s双轴、中间秒刻度、实虚线重叠可辨及右栏详情已生效；24/20/14sp已实测；右栏来源/缺失/限制三组已调整、八语言同步待新图，未最终验收 | 9 |
| AppSettingsActivity | [设置v1候选](../research/task-first-ui-concepts/app-settings-v1.png)：保留配置/安全说明、允许整页滚动；候选紧凑分组已实现、构建通过、未运行验收 | 39ac261用户平板外观/语言/偏好首屏已读，3c0019c用户横屏首屏和底部实图已读，版本/包名及导出/机器入口滚动完整、偏好未变；10bf727标准浅色四行信息实图已对齐；动作图标及大字深色仍未对齐 | 26 |
| MachineSettingsActivity | [机器设置v1候选](../research/task-first-ui-concepts/machine-settings-v1.png)：保留配置/安全说明、允许整页滚动；4d308a7实图已确认四个回读标签/值行；831aa1c实图确认温度标签同行/下划线标签，scrollY=45后三控件在系统可见区内；大字及其他标签仍待验收 | 模拟回读设置；真实写入/回读不在视觉证据范围 | 37 |
| ScaleSettingsActivity | [秤设置v1候选](../research/task-first-ui-concepts/scale-settings-v1.png)：允许整页滚动；bcda211未连接首屏实图确认三组及20/16/14sp、16/8dp；4d308a7底部协议说明实图与滚动断言通过；深色/大字/能力状态仍待验收 | 4d308a7未连接态精度/能力/说明分组与底部实图已读，偏好未改变。真实有能力状态另验 | 14 |

## 每页G1–G4当前状态

依据[严格基线](../superpowers/specs/2026-10-18-strict-visual-baseline.md)与[review §10/§11](2026-10-18-native-specimen-review.md)，G1为间距/构图，G2为字体，G3为字号/行盒/单位，G4为背景/状态色。三样张有限冻结已发生，M1三真实页在本表明确范围获`LIMITED_PASS`；它只覆盖已采状态，不是生产页全状态最终通过。`EVIDENCE_PENDING`表示迁移已实施、该源码真实运行/实图尚未完成验收；`VISUAL_PENDING`表示已有选定原生输入、真实生产页实测/实图尚待核对；`BASELINE_PENDING`表示该页特定基线/状态尚未完成，不能再解释为全局字体色板都未选择。`FAIL`保留已见差异/回归，候选代码不能替代运行关闭。

| 页面 | G1 | G2 | G3 | G4 | 当前判定边界 |
|---|---|---|---|---|---|
| HomeActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | 026f993真实4配置及本页已采滚动/业务状态；按M1计划§11限定，不外推其他状态/页面/硬件 |
| ExtractionActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 运行态证据不等于精确四项冻结 |
| CurveActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M3已实施：冻结字体/间距/语义色、真实指标与连续列表；保留原选择/排序/槽位。31 JVM tests及两个APK构建通过，新独立12配置CI39153382312运行中，尚无结果。历史footer/list限定证据保留，不追认新视觉。 |
| HistoryActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 非空同状态及精确基线待补 |
| HistoryDetailActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 限定四指标实图不覆盖全部状态 |
| BrewComparisonActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 实图/轴专项不等于精确构图冻结 |
| BeanInventoryActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | 026f993真实4配置及本页已采滚动/业务状态；按M1计划§11限定，不外推其他状态/页面/硬件 |
| BeanEntryActivity | FAIL | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | 95be8fb失败现场三次draw稳定，五字段均117px完整；名称右1515=重量左1515，规定16dp间距实测0px。随后section定位错误阻断，主图0/12。064业务图观察间隔改善，但未完成正式精确16dp和全配置复验；9b51936候选待新frame，历史末字段失败保留 |
| BeanBatchActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M4已实现24sp初始量、真实两事件预算/短高永久中部滚动及固定footer；调整模型不变。 5ef3b8f已推送；48项测试及APK构建通过，独立12配置CI39187428107运行中，未作视觉通过。 |
| BeanLedgerActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M4已实现真实五列/摘要自然换行与类型色；原排序、详情和恢复保持。 5ef3b8f已推送；48项测试及APK构建通过，独立12配置CI39187428107运行中，未作视觉通过。 |
| BeanPreparationActivity | FAIL | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M4已实现批次列表、输入/捕获/预测顺序、状态色及固定操作；原扣减与恢复边界保持。 5ef3b8f已推送；48项测试及APK构建通过，独立12配置CI39187428107运行中，未作视觉通过。 |
| BrewReviewActivity | FAIL | EVIDENCE_PENDING | FAIL | EVIDENCE_PENDING | 95be8fb真实业务图选豆第二行裁切；advanced wideFont ru/light页头曲线/时间ellipsis失败；single短高真实tools菜单触控高度不足48dp。064业务图已观察长选豆第二行完整、advanced两job通过；正式12配置和四门仍缺，9b51936新frame待验，旧18业务通过不抵消视觉差异 |
| CurveEditorActivity | FAIL | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | 95be8fb业务图第四阶段输入底部裁切；single短高真实tools菜单触控高度不足48dp。064正式M2已取得本页浅色1.0主图：fullNatural1055/available1040，第四阶段117px输入仅102px可见（15px）；064 single另一夹具117px仅113px可见（4px），两夹具不可混同。9b51936同正式浅1.0主图经Astra实看确认991/1040且两输入117px完整，限定关闭该15px初态缺口；其余配置与后续恢复失败未关闭，不能以滚达替代标准11字段首屏 |
| CurveShareActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | 026f993真实4配置及本页已采滚动/业务状态；按M1计划§11限定，不外推其他状态/页面/硬件 |
| CurveImportActivity | FAIL | EVIDENCE_PENDING | FAIL | EVIDENCE_PENDING | M3已实施：原生原文输入/显式粘贴与文件读取、固定操作；原decode/UTF8/MAX_BYTES/草稿路径保留。静态与构建通过，但9b51936 push run39153376290的IME输入高0/标题裁切已证G1/G3 FAIL；主图2/12后停止，完整合法/非法/IME/SAF和四门仍待修复复验。 |
| CurveImportPreviewActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M3已实施：条件metrics同行、共享阶段标题、完整图表及右固定动作；原LayoutPolicy与冲突/副本/幂等语义保留。图160和标准5行完整门禁不减；静态/构建不替代实图。 |
| ScaleActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 无读数不能替代有效重量/趋势状态 |
| ScaleConnectionActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 候选/连接/限制/错误状态待补 |
| LegacyCurveActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 非空只读图仍需同稿精确对照 |
| LegacyHistoryActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 非空列表证据不等于精确构图冻结 |
| LegacyHistoryDetailActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 双轴/来源实图不覆盖全部必验状态 |
| AppSettingsActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 设置稿仍为候选，完整滚动/状态待精确验收 |
| MachineSettingsActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 候选稿与模拟回读不证明真实写入或四项通过 |
| ScaleSettingsActivity | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | BASELINE_PENDING | 候选稿、未连接/能力状态须分别核对 |

## M1历史快照（不作为026f993当前判定）

以下为旧表原文，仅保留失败和早期限定证据。其中“当前/仍FAIL/未通过”均指旧记录当时；相同已复核场景以本表上方026f993结论为准。

| 页面 | 认可稿/范围 | 历史原文 | 历史字体测量条目 |
|---|---|---|---|
| HomeActivity | [准备v2](../research/task-first-ui-concepts/brew-preparation-v2.png) | **当前M1：7e3f662已迁移真实摘要/最近列表/固定操作，9c71043接入真实取证。0f3b759/frame39142720200仅light1.0 Home报告6目标可达/恢复、3组baseline差0、4路由与隔离通过；不是四配置完整图审，G1–G4仍未验收。旧构图差异只有代码候选，不能视为运行关闭。** 历史限定证据：**旧版本10-18：Astra已读0f0beda用户横屏font1.0/1.3真实整窗：capture-1、未选本杯豆子，91°C/27.00g/2阶段摘要及697×126px主操作完整；不是已确认18g或开始萃取状态。紧凑1.3组件改用0/12端点，关闭该两阶段场景刻度重叠，不外推四阶段/任意高度。** G1仍有摘要上下排、最近使用在右侧大按钮、右栏工具密集等已确认构图差异；阶段颜色/背景/顶栏也未对齐，G1–G4未通过。历史d873340/run39116385295组件发现的刻度失败已被上述限定新证据替代，不再写未修复 | 25 |
| BeanInventoryActivity | [库存v3](../research/task-first-ui-concepts/bean-inventory-v3.png) | **当前M1 FAIL：head0f3b759 / advanced39142720173两job失败。pages首例1138×618dp/font1.0/中文浅色header摘要“1 · 250 g”horizontal text overflow，未到该页截图。business长UI beans UUID名两行、页头异常高，-10g行1331×108px仅84px可见，+100g行未完整出现。** Astra实看 `build/task-first-evidence/ci-0f3b759-advanced/advanced-mock-pages-business/advanced-page-screenshots/business/inventory-selected-after-adjustment.png` 及同目录 `inventory-ledger-first-screen.txt`；pages日志 `advanced-mock-pages-pages/landscape.txt`。最小页头修正为title横排0/weight1、summary自然宽+16dp，不足整组纵排；静态无阻断不关闭运行FAIL，详情字号/长名/48dp/两事件门禁保持。旧“两流水关闭”不覆盖此次回归。历史限定证据：**旧版本10-18：Astra已读3b5f29f用户横屏中文浅色font1.0实图及geometry，单行固定操作区已运行，-10g/+100g两条流水均108/108px完整；关闭该标准状态第二行裁切，不外推1.3/最大字。** G1仍缺原稿整体标题摘要及部分图标/分隔组织，G1–G4未通过。历史30bee76第二行14/108px失败与36f3cbc仅构建状态已被该限定新证据替代；4240daa选中250g批次与固定选豆入口只证明其当时状态，不代替全状态验收 | 11 |
| CurveShareActivity | [分享v1](../research/task-first-ui-concepts/curve-share-v1.png) | **当前M1 G1 FAIL：7e3f662已迁移，但0f3b759/frame39142720200 light1.0第四表行首屏仅36/49px，缺13px。** Astra已实看原稿与新window.png：preview viewport1094px、自然内容1107px、chart360px已达160dp下限；row4完整框[252,1357,1557,1406]、visible到1393，三文字Layout46px无ellipsis。after-scroll-5运行记录滚13px全幅、restore=true/protocol2 accepted；是首屏失败，不是时序冲突。证据根 `build/task-first-evidence/ci-0f3b759-frame/task-frame-tablet-user-landscape/task-first-evidence/product-visual-migration/raw-device/e357bdcad6bcd643e59d4bc23b271f36b3116fac/tablet-user-landscape-light-font-1.0/share/`，含window/after-scroll-5/scroll-reachability/anchors。SAF取消/失败/成功/恢复报告通过不抵布局FAIL；其他主题/字号未采。仅批准本页header12dp+workspace8dp叠加归一冻结8dp、回收27px，不缩图/字/表或初始滚动，待新运行。历史限定证据：**旧版本10-18：Astra已读3b5f29f用户横屏中文浅色font1.0四阶段整图及geometry：tableShown=true，表1328×320、图1328×398，四行2.4/5.7/8.3/6.9bar及12/23/34/45ml完整，右侧操作完整。关闭该标准状态四阶段缺表，四阶段1.3/最大字仍待验。** root新读0f0beda advanced/run39117264495 wideFont分享geometry为stages=2、tableShown/tableComplete=true，仅补两阶段结论。G1仍有兼容说明和详情入口占据主操作前方的已确认构图差异，严格字色/构图未通过。历史5abe9c2两阶段普通/1.3完整仍限两阶段；4bd173c的24sp/700数字、14sp/400单位有实测，其1600×1000/density200四阶段图不冒充用户profile；abe9614分区调整保留历史效力，不代替G1–G4 | 9 |

## 已获得的其他证据（不能跨版本外推）

- 709a337 advanced/run38886601670：15业务页8语言×2主题、常规/1.3字号各240夹具通过。四阶段分享图已人工核对；分区调整abe9614在其后，须新图。
- 27ae12b single/run38885839398：普通横屏通过，用户平板332/336、大字号334/336；失败仅旧入库/编辑裁切，后续f525db3/run38888405636三个横屏各336夹具通过，第四阶段裁切专项已关闭。复盘IME本轮未复现不等于根因已修。
- cbe8076补用户平板浅色真实Mock启动流程；不是硬件验收，也未覆盖无秤运行态。
- 55f3f3f电子秤普通/大字号实图已核对单位和设备行，仅无读数状态；fb19415指标分段、edd89da摘要表已有实际高级夹具图，但空图不能覆盖有采样双轨迹。

## 下一步优先级

1. **M1限定封闭，不重开样张**：026f993三真实页已按§11验收；后续改动保留现门禁与真实业务，未采错误/警示/语言等状态仍明确待验，不因CI绿扩大结论。
2. **优先M2候选复验**：9b51936已推送，frame39153382452运行中；064历史1/12与Editor15px缺口保持FAIL，不能用31测试/编译替代实图。按原五/11字段、自然52dp、IME、固定操作、草稿与实际滚动恢复及G1–G4验收。
3. **M3已实施，独立首轮验收运行中**：[M3曲线/导入](../plans/2026-10-19-m3-curve-import-visual-migration.md)的curve-visual39153382312须完整覆盖三页12配置，不能以静态审或M1继承替代。M2与M3均未通过视觉验收。
4. **M4及后续保持全范围**：[M4用豆/批次/流水](../plans/2026-10-19-m4-bean-workflow-visual-migration.md)已完成真实接口/隔离依赖审计，主任务已授权独立三页生产与12配置测试，实施中但未验收；其余固定24页沿迁移计划继续运行/记录、工具、旧数据、三个设置页。保留中文、大字号、横屏短高、业务安全与硬件边界；不新增竖屏、不锁方向、不取消在途CI、不微推送替代整批验收。

P0取证基础与P1三样张已分别有后续运行/有限冻结记录，不再把d87ef95构建或“三样张待开始”列作当前下一步；旧证据、未知项和原失败仍在review中保留。
