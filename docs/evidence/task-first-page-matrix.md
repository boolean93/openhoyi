# 全范围视觉验收状态矩阵

## 当前状态（优先于下方历史观察时点）

最新已推送 head `dc5875cc924b2fbf7359b876b29e4e38f30ee1ec`，PR4仍为draft、目标develop；工作树提交后干净。新PR检查已启动：frame39422164343、curve39422164404、bean39422164468、scale39422164339、legacy39422164336、settings39422164407、M539422164438、snapshot39422164320；完整句柄在 `build/task-first-evidence/ci-dc5875c/run-handles.json`。本批全部终态：PR 7 success/7 failure，push 4 success/8 failure；PR branch-policy、snapshot、upgrade、lifecycle、single、native、advanced通过，curve、scale、settings、bean、legacy、M5、frame失败，本head仅取得下述M4说明与Comparison浅色1.3限定验收，不借用其他配置结果。

本提交包含：Comparison按来源全文/摘要独立行自然高保详情viewport；M4两已有特殊态全文滚达/恢复补证；M8两真实Switch单次点击测试修正；Scale/Legacy显式窗口映射及诊断计时；M2/M3独立IME因果证据、生命周期超时条件快照；snapshot实际列表解析。全部经Astra静态审查，最终AndroidTest编译30秒成功；8分支策略、11snapshot、9window契约测试通过，5runner/8段Python语法通过。编译/契约通过不代表UI或硬件通过。

本轮已查看的上一版本a47原图仅取得有限增量：浅色1.3 UNKNOWN详情真实plot102.66596dp，旧67.55dp不足已修；missing-samples三图例、空采样、四目标滚达及恢复已验。Comparison裁切有修正但待新图；标准1.0截轴本轮未采，仍待验。完整24页G1–G4未完成，未安装、合并、发版。以下摘要保留历史来源，不视为当前运行状态。

### 已审历史批次摘要

上一轮远端 head `a47c87090240d5b682501d84759d48f0bcf36736`；下述已审 bcc64c1 批次 PR 实测 merge `8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249`（M5/M8 原始产物确认）。2026-10-20 本轮 native 与 branch-policy 已通过，其余检查仍在运行或失败，不能沿用旧批次成功。原 `49a1b46` / merge `46e2dbc79ca0732290db92b5c78ad097c82c86cb` 的 M5 CI `39204483061` 成功和16主manifest/840引用文件仅为历史证据。

本轮已提交 M8 三页、M5/M6 布局修正和测试取证收口；真实 CI 仍有阻断。M8 `39414724094` 首个 AppSettings 1.3 截图同步失败，已定位 S1 初始滚动条渐隐、其余 W1/D1/D2/W2/S2 同像素，原5秒零差门禁继续保留；拟用私有 scrollbar state 的方案因 maxTargetSdk=R 被撤回，不进入本批，渐隐问题未关闭。M5 `39414724042` 第673行失败经 Astra 对照实图确认是缺采样态合法三图例却被固定要求四项；真实浅色1.3 History 主态、图表/说明/两项notes可达与恢复仅此样本限定通过，未外推未执行态。字体设置恢复是独立失败。原始 ZIP 远端 SHA 与 CRC 已校验，分别保存在 `build/task-first-evidence/ci-bcc64c1-settings/` 与 `ci-bcc64c1-m5/`。

其余新失败：scale `39414724076` 非零 surfaceInsets 不受采集器支持；legacy `39414724165` Window-layer/decor/metrics 映射未知；curve `39414723962` 重建前后 scrollY 4885→4281、IME未恢复，文本/selection/focus未变。本轮已真正取得原生 IME trace，尚待因果分析，不先猜改生产。三组原始产物已分别保存并校验 SHA/CRC 于 `ci-bcc64c1-scale/curve/legacy`。升级回归 `39414724019` 已通过，不代替视觉或硬件验收。

独立 snapshot 探针 `39414724127` 在解析真实空列表响应时失败，尚未保存/加载/修改字体，不能据此判断恢复能力。后续未提交窄修仅适配精确响应并收紧空 stderr，10项离线测试及 Astra 静态复核通过；恢复能力仍待新 CI。TaskFrame 本轮“Missing steam temperature group”已定位为旧文案选择器不适配新 label；候选仅改测试定位，保留滚动、完整可见及输入不变门禁；本地 AndroidTest 构建已通过29秒，Astra 静态审查无阻断，仍待新 CI 复验。

single `39414724030` 三配置（含用户2560×1600）及 advanced `39414723959` 已通过。Astra 已核两个1600×1000配置的八张旧版历史/详情中文浅深原图及同run checkout SHA，旧chart结构适配、首屏96dp交集断言与列表返回恢复限定闭合；single包缺独立Canvas数值与M7 protocol2逐目标proof，不能替代完整M7或G1–G4。用户2560×1600四张中文浅深旧版历史/详情原图也已由 Astra 核验，无新确认偏差；同样仅限旧结构及已有列表恢复边界。

本轮 PR 已全部结束：6项成功、8项失败。M4 bean `39414724217` 的12主配置均完成机械布局/隔离检查，原始777条ZIP CRC及736个manifest引用SHA均核验；最终失败是 `font_scale` 原key缺失但恢复后被系统补写1.0，`originalExitStatus=0`。保留cleanup失败，同时允许对已完整产出的真实图进行独立G1–G4验收；Astra已完成12主态及已列特殊态的限定接受，Preparation确认后说明滚达仍待补证；不标全M4无条件通过。

a47c870 新批实测 merge 为 `12de379c8bc98ed1d309d04c8e73edf09b3cae87`（scale/legacy/settings 原manifest一致）。两类 Window 失败已产出独立原图诊断，尚不代表正式协议通过；Settings 本次进入业务点击阶段后由 CompoundButton 返回值误判中断，原48dp尺寸与真实监听已有证据，候选仅修测试。snapshot 已到保存ACK，但实际列表 `--` ID 被拒；候选精确适配后11项离线测试/Astra静态通过，尚未加载/恢复。当前新批仍有活跃CI，不取消/重启或用旧批结果代替。 lifecycle `39417690375` 本轮在独立 idle 诊断 Home/service ready 的10秒门禁超时，尚无具体失败条件快照；原日志及4文件artifact已保存于 `ci-a47c870-lifecycle`（SHA07620d64f886e0f4e0f248f9018c9c4471a70d7ea399001d0bf8ffd2b8494846）。不能用上一轮lifecycle成功覆盖。

完整24页未最终验收。M1三页保持既有限定通过；M5已采G2/G4限定通过、明确G1/G3失败或证据缺口见下表及新review。其余页面不由构建/CI绿色推定视觉通过。当前全部工作仅本地/CI，不安装、不合并、不发版。

## 历史观察记录（按下方源码与时间点理解）

**最新合批冻结验证：** M5三项静态阻断已由同Astra复核关闭；M2/M3 IME候选、Batch限高及独立safeRect门禁一并静态允许复验。最新构建成功（57s，98 tasks），bean-core verify通过；18 suites/93 tests，0失败/错误/跳过，Mock与AndroidTest APK生成。源码hash/XML/日志保存在 `build/task-first-evidence/m5-ime-final-build/`。旧45fd运行FAIL仍未关闭；完整24页及M5真实16配置、特殊状态/G1–G4待新CI，不以静态/编译替代。

**M5首版构建（未验收）：** 四页生产17 Kotlin/8语种资源与完整16配置测试已落地，基线45fdc9e的工作树首次构建成功（1m10s，96 tasks），8 suites/47相关unit tests，0失败/错误/跳过；Mock/AndroidTest APK已生成。实际源码hash、XML、日志保存在 `build/task-first-evidence/m5-first-full-build/`。Astra已完成完整live scope/runner静态审，无新增阻断；生产全组审核曾因模型capacity中断，现同Astra已完成首版规格与质量审；除下列三项封闭修正外无新增静态阻断。三项仍待落地复核，不授予运行/视觉通过。16实际配置与特殊状态/G1–G4均未通过，不以构建替代。M2/M3/M4按最新45fd实证继续修。Astra续审发现M5三个静态阻断：内嵌HistoryDetailContent竖向自然内容挤占图表、ComparisonSummaryTable单位sp浮点被AbsoluteSizeSpan整数截断、ComparisonMetricTabs仍有旧背景/描边。首版构建成功不关闭这些问题；后续修正需重新验证。


**45fdc9e最新实证补记：** head `45fdc9e1606ba17ead131e891f31dc30b2deef46` / PR tested merge `34fe3eafe83253e3986fc6ffd03a454e9c7c44f9`。frame39190681951、curve39190681860、bean39190681932失败；native39190681888、upgrade39190681813、branch39190681925成功，advanced39190682024、lifecycle39190681940及single39190681909（三横屏配置）最终均成功。三原ZIP均保存并核GitHub SHA256/CRC，见 `build/task-first-evidence/ci-45fdc9e-{frame,curve,bean}/preservation.json`。

M2/M3新诊断均为savedImeVisible/requestedIme/observedImeVisible=true、requestCount=1、status=RESTORED_AFTER_OBSERVED_IME_LAYOUT、imeAnimationEvents为空，且曾应用原scroll（Editor662、Import4885）；断言时真实IME=false/窗口高1391，scroll分别0/4281。不是未保存或未发请求，最终IME保持仍FAIL，后续关闭原因未定，不把页内self-report当OS最终成功。M4本轮Preparation浅1.0业务已通过真实IME输入及固定动作场景，不外推四配置/G1–G4。流程进入Batch后失败：root实看adjustment-input-IME/window.png，两个输入完整，底部取消/预览操作被键盘遮挡；原“focused input clipped”断言与画面不符。dialog的screen rect `[576,74,1984,863]` 和root-local visible rect `[0,0,1408,789]` 疑似混用，全部文字fullViewVisible=false；测试坐标问题与真实footer遮挡需分别修复，不因其中一项修复宣称全部通过。


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
| ExtractionActivity | EVIDENCE_PENDING | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | eb77四主态已采字体/字号baseline/颜色限定通过；全业务状态与G1完整验收仍未关闭，见M5逐门review |
| CurveActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M3已实施：冻结字体/间距/语义色、真实指标与连续列表；保留原选择/排序/槽位。31 JVM tests及两个APK构建通过，新独立12配置CI39153382312运行中，尚无结果。历史footer/list限定证据保留，不追认新视觉。 |
| HistoryActivity | FAIL | LIMITED_PASS | FAIL | LIMITED_PASS | eb77标准1.0真实时间轴裁39px；1.3右侧图/说明/notes滚达证据缺口。候选已修并补检查，待新CI；G2/G4仅已采范围 |
| HistoryDetailActivity | FAIL | LIMITED_PASS | EVIDENCE_PENDING | LIMITED_PASS | eb77 UNKNOWN/1.3实际plot67.55dp；notes完整滚达未证。条件同行候选构建成功不关闭失败；G2/G4仅已采范围 |
| BrewComparisonActivity | LIMITED_PASS | LIMITED_PASS | EVIDENCE_PENDING | LIMITED_PASS | dc5875c浅色1.3两来源各285px全文滚达、20目标/双scroll恢复、水平分隔已实图接受，关闭该配置旧131px视口裁切。UNKNOWN warning在合法toolbar滚动区下方，初态whole前置断言冲突；需在既有21目标中严格滚达/原图/恢复，当前缺证。其余配置不外推。 |
| BeanInventoryActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | 026f993真实4配置及本页已采滚动/业务状态；按M1计划§11限定，不外推其他状态/页面/硬件 |
| BeanEntryActivity | FAIL | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | 95be8fb失败现场三次draw稳定，五字段均117px完整；名称右1515=重量左1515，规定16dp间距实测0px。随后section定位错误阻断，主图0/12。064业务图观察间隔改善，但未完成正式精确16dp和全配置复验；9b51936候选待新frame，历史末字段失败保留 |
| BeanBatchActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | bcc64c1 / tested merge8e8f 的四主态及已列特殊态由Astra实图限定接受：90g/100g/90%与日期/流水完整，主态5目标全滚达/恢复；原生长理由焦点流程完成但无独立全文PNG。字体资产/角色证据有，原稿字体及逐glyph fallback未知；不扩全状态或硬件，见M4 review。 |
| BeanLedgerActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | bcc64c1四主态与已列特殊态限定接受：214/250摘要、五列三事件和类型色正确，主态3目标全可达/恢复，baseline零差；两行省略+原生全文详情语义保留。逐glyph字体不可观察项及CI字号清理FAIL保留，见M4 review。 |
| BeanPreparationActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | bcc主态限定接受沿用；dc5875c两原说明状态浅深×1.0/1.3共16张visible/restored原图已由Astra逐张核，8份报告/16正式proof成立，全文/精确恢复/库存与journal不变，关闭旧两说明缺口。只限已列主/特殊状态；原生dialog全文字形和未采IME协议不外推，见M4 review。 |
| BrewReviewActivity | FAIL | EVIDENCE_PENDING | FAIL | EVIDENCE_PENDING | 95be8fb真实业务图选豆第二行裁切；advanced wideFont ru/light页头曲线/时间ellipsis失败；single短高真实tools菜单触控高度不足48dp。064业务图已观察长选豆第二行完整、advanced两job通过；正式12配置和四门仍缺，9b51936新frame待验，旧18业务通过不抵消视觉差异 |
| CurveEditorActivity | FAIL | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | 95be8fb业务图第四阶段输入底部裁切；single短高真实tools菜单触控高度不足48dp。064正式M2已取得本页浅色1.0主图：fullNatural1055/available1040，第四阶段117px输入仅102px可见（15px）；064 single另一夹具117px仅113px可见（4px），两夹具不可混同。9b51936同正式浅1.0主图经Astra实看确认991/1040且两输入117px完整，限定关闭该15px初态缺口；其余配置与后续恢复失败未关闭，不能以滚达替代标准11字段首屏 |
| CurveShareActivity | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | LIMITED_PASS | 026f993真实4配置及本页已采滚动/业务状态；按M1计划§11限定，不外推其他状态/页面/硬件 |
| CurveImportActivity | FAIL | EVIDENCE_PENDING | FAIL | EVIDENCE_PENDING | M3已实施：原生原文输入/显式粘贴与文件读取、固定操作；原decode/UTF8/MAX_BYTES/草稿路径保留。静态与构建通过，但9b51936 push run39153376290的IME输入高0/标题裁切已证G1/G3 FAIL；主图2/12后停止，完整合法/非法/IME/SAF和四门仍待修复复验。 |
| CurveImportPreviewActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M3已实施：条件metrics同行、共享阶段标题、完整图表及右固定动作；原LayoutPolicy与冲突/副本/幂等语义保留。图160和标准5行完整门禁不减；静态/构建不替代实图。 |
| ScaleActivity | FAIL | EVIDENCE_PENDING | FAIL | EVIDENCE_PENDING | 49a1短高freshness真实裁51px；坐标比较错误与真实裁切分别修正，尚待新图关闭 |
| ScaleConnectionActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M6已实施；原49a1测试在Scale中途失败，不推定本页完整状态已验收 |
| LegacyCurveActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M7已实施；49a1 reader清理等待失败，测试修正静态/编译完成，待新运行 |
| LegacyHistoryActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M7已实施；完整12主配置与状态图待新CI，不复用旧非空列表为新验收 |
| LegacyHistoryDetailActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M7已实施；bcc64c1 single三profile真实fixture/viewport检查及中文浅深原图限定接受，旧结构失败闭合；不替代M7 protocol2逐目标完整验收。a47新Window诊断用于核短窗口映射，未认证通过。 |
| AppSettingsActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M8生产已实现并构建；12主配置测试已接入，requiredproof收口中，未实际取图 |
| MachineSettingsActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M8永久40:60/五section与原控制边界保留；实际Mock Service测试已接入但未运行，不证明设备写入 |
| ScaleSettingsActivity | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | EVIDENCE_PENDING | M8分组/全文滚动已实现；偏好与实际页面返回测试已接入，真实能力状态仍独立待验 |


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


## 2026-10-19：91ab835 CI 补充取证（未通过最终验收）

本节记录已结束运行，不覆盖此前限定结论。源码 `91ab835aaf28d9e133a78efd620d7780ba3e7192`；PR 实际测试合并提交 `a0816c2425823f1ad1da916a191f1d88f9211d99`。

| 检查 | 原始证据与当前结论 |
| --- | --- |
| M5 萃取／历史 39193804077 | 四页 × 四配置共 16 组已保存；运行收尾失败：原 `font_scale` 不存在，删除返回 0 后实读为 `1.0`，严格原值恢复不通过。不能记 CI 通过，也不能用截图齐全代替 Astra G1–G4 验收。 |
| advanced 39193804136 | `wideFont ru dark=false` 的 `BrewComparisonActivity` 日期／曲线名称文字发生 ellipsis，真实布局回归，待 Astra 修正规格。 |
| single-screen 39193804056 | `HistoryActivity` 列表恢复检查遇到多个匹配节点，13/14 恢复夹具完成；须精确定位节点歧义，不能改成任取第一个来消除失败。 |
| frame 39193804143 | `editor-stage-hidden-values` 重建状态不相等，仍失败。 |
| bean 39193804092 | IME 安全区下沿 754px，确认按钮到 763px，真实裁切 9px。自然按钮栏高度候选修复已冻结，尚待构建、运行与 Astra 验收。 |
| curve 39193804062 | 首个恢复提交帧 IME 可见，后续三帧隐藏。已排除当前已查明的显式 hide 调用；服务端焦点策略仅为候选原因，尚未证实。保留严格恢复断言。 |
| lifecycle 39193804078 | 旧温度单节点断言与新数字／单位分离结构不符。严格双节点、完整可见、真实文字 Layout、屏幕基线相等的最小测试适配获 Astra 静态认可，待重新运行。 |

新增原始 ZIP 已保留在 `build/task-first-evidence/ci-91ab835-{m5,advanced,single,frame}/original.zip`；各目录 `preservation.json` 记录 SHA-256、字节数、CRC 校验和文件数。原始证据不裁剪、不改色、不缩放；全部 24 页范围不变。后续开发不改变协议、设备控制、发布或硬件验收边界。


### 91ab835：Astra 第一阶段实图复核

Astra 已将浅色 1.0 四页与批准概念稿并列检查。当前明确失败：History 内嵌图主绘图区被压成细条，底部时间轴文字被 viewport 截断；列表摘要单位 `g` 单独换行；Comparison 图表占比不符合原稿主次关系，另有俄语大字选择器 ellipsis。尚未完成的深色／1.3／实际 Paint 与背景色审查不记为通过。

Batch 自然按钮栏预留及 LanguageActiveCupChecks 双节点温度断言已获 Astra 静态无阻断；统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 构建成功（52 秒，91 tasks，9 executed）。证据保存在 `build/task-first-evidence/ui-batch-followup-build/`；仅证明候选可构建，不证明运行裁切已消除。


### 后续候选：History 唯一入口与 Comparison 修正

- History 列表恢复测试改为唯一 `product/history/full-page` 入口，保留原文本、完整可见、可点击与返回锚点检查；新增当前实际列表和长期记录库的唯一 ID 核验，再验证详情 intent 的同一 ID。审查已纠正误查最近记录库的中间版本，不使用该版本作完成证据。最终候选 AndroidTest 构建成功（27 秒），尚未 CI 运行。
- M2/M3 runner 新增独立 IME 日志及 input_method dump，原错误日志、恢复断言和时间门限不变；shell 语法检查通过。
- Astra 已给出 Comparison 修正规格：选中杯子全文、按真实文本自然宽度布局，常规 plot 优先 160dp 并另计真实轴标签空间，详情最低 48dp 且内容局部滚达。Sol 已开始实施；当前 91ab835 视觉 FAIL 不变。


### 本批冻结与新运行门禁

History／Comparison 七生产文件修正及字体顺序候选已冻结，Astra 最终静态审无剩余明确阻断。Mock 与 AndroidTest 统一构建成功（57 秒，91 tasks，9 executed）；分支策略检查及 8 项策略单测通过。构建日志与源码哈希见 `build/task-first-evidence/m5-layout-repair-build/`。

这些结果尚不关闭 91ab835 实图失败。新 CI 必须复验 History 完整时间轴、1.3 真正 plot、备注滚达及恢复；Comparison 俄语全文、160dp 常规 plot 与详情可达；Batch 原生按钮完整避让 IME；字体原 absent 状态精确恢复。M5 新增 60 特殊状态截图仍需运行及独立 Astra 图审。Comparison 表格整格描边与原稿层次差异仍列待办，24 页完整范围不变。


## eb77b2f 返回结果（未查询的 CI 不推定状态）

- native `39196410481`：`TaskComparisonLayout` helper 内调用 `super.onMeasure` 触发 WrongCall。最小候选已把调用移回 override，Astra 静态核各分支仅一次、无递归、无 suppress；本地 lint 被代理拒绝及直连 TLS 握手失败阻断，不能记通过，待新 CI。
- bean `39196410461`：已越过原按钮 IME 检查，在调整窗重建就绪等待失败。Astra 实读新图确认原窗及草稿恢复；测试错误要求 modal 持焦时 owner Activity 同时持焦。待精确调整窗身份、真实 decor 焦点和布局就绪修正，不放宽普通页面或 IME 安全区要求。
- curve `39196410563`：恢复仍失败。新 IME 日志显示 recreate 附近客户端隐藏请求及后续 Insets 隐藏；不能沿用仅服务端无 editor 的单一假设，也不能据 reason 直接断定产品显式 hide。继续按调用链查证，不增加 show 重试或延长等待。

两个失败原包保存于 `build/task-first-evidence/ci-eb77b2f-{bean,curve}/`，均有原 ZIP、CRC/SHA 校验与未改原始文件。未获得全批成功或视觉验收。


### eb77b2f 对比页后续归因

advanced `39196410528` 俄语大字详情按钮裁切：测试已要求整个控件滚入后仍仅见108px，而按钮自然148px。Astra确认滚动viewport最低预算小于真实控件，不能用“可滚动”豁免。修正须按实际可点击后代最大自然高度及scroll padding预留，仍保真实plot目标与原完整控件断言。失败俄语截图未产出（检查先抛错），不冒称已目视该失败截图；原日志与同批中文大字结构图分别保留。


## M6 电子秤两页：生产候选与验证边界

ScaleActivity／ScaleConnectionActivity及显式nullable趋势样式已形成生产首版；旧ScaleOwner默认路径、Mock归零/采集禁用及真实控制边界仍需后续集成复核。八语言新增资源均解析成功，三键集合一致。

只读输入桥接已完成真实红绿：`MockScaleVisualInputTest`先8/8因未实现失败，再8/8通过（0 failure/error/skip）。证据分别位于 `build/task-first-evidence/m6-input-red/` 与 `m6-input-green/`。这些纯helper测试不证明Service实际通知/清理，也不证明页面外观；真实Service scope和两页8主配置检查已形成候选，入口、runner与独立workflow已加入；尚未编译或运行，特殊趋势窗口严格同帧证据仍待补齐。

Astra发现的主重量数字/单位外框居中问题已改为CENTER_HORIZONTAL|TOP，保持64/22角色及8dp间距；静态阻断已关闭，实际屏幕baseline仍待新图精确测量。生产首版构建两次自动审批超时，命令均未执行，日志不存在；等待用户明确重试授权，不得写作构建失败或成功。范围仍为24页。M7三页生产候选已冻结，测试取证正在实施；M8三页已有规划，尚未开发。


### 最新取证与 M7 候选状态

- M5 `39196410451` 已只读查询确认 `completed/success`，萃取、历史、详情、对比页检查及证据上传步骤均成功；该结果仅为 CI 机械检查，不关闭 Astra G1–G4。artifact `12162740736`（83,753,980 bytes）的远端摘要为 `2ef8e6c65224b19869e1f107d763d24f3c8b49afe62e5da2625b0dfdbfd2474a`。原包下载因自动审批服务容量不足未执行，本地 SHA/CRC/截图内容尚未核验；状态见 `build/task-first-evidence/ci-eb77b2f-m5/retrieval-status.json`。
- M7 LegacyCurve／LegacyHistory／LegacyHistoryDetail 生产候选及最小 History 初始来源导航已冻结。根任务逐项复核 15 文件 SHA 与候选清单一致；记录保存在 `build/task-first-evidence/m7-production-freeze/source-freeze.json`。这不是编译、业务或视觉验收；三页×四配置共 12 主图及异常、导入、恢复状态仍待执行。
- M6 runner 的 Bash 与五段内嵌 Python、M6/M7 合计 16 份 XML 仅通过语法检查，见 `build/task-first-evidence/m6-m7-offline-syntax/result.json`。不据此声称 Kotlin 可编译。
- 用户明确保留 Astra 最终验收并等待额度恢复。Sol 继续执行，所有待验收项保持原范围，不以 CI 成功或静态检查替代设计稿对照。


### 本地构建授权恢复后的实际结果

用户明确允许重试后，本批首次构建实际执行并发现 Kotlin 编译错误（列表适配器 receiver、Paint.style 同名遮蔽及两处括号）；对应 owner 已精确修正。第二次 `:mobile:testMockUnitTest --tests io.openhoyi.mobile.MockScaleVisualInputTest :mobile:assembleMock` 成功，38 秒，8 tests / 0 failures / 0 errors / 0 skipped。随后 `:mobile:assembleMockAndroidTest` 成功，34 秒，含 M3 tracing、M6/M7 新 scope 与原测试精确适配。日志、XML 和候选源码哈希位于 `build/task-first-evidence/m6-m7-approved-build/`。分支策略及 8 项策略单测通过。

以上只证明候选构建及指定 helper 单测通过，未运行新 instrumentation，不关闭任何 G1–G4 或硬件验收。M6 8 主配置、M7 12 主配置及原有回归须由新 CI 实际取证；短窗／趋势 dialog 严格窗口映射仍待 Astra 审查，M8 后续独立开发。


### eb77b2f 原件补齐与新一批运行

- M5 artifact 12162740736 已补下载，原 ZIP SHA 与远端一致，895 条目 CRC 通过；16 主 manifest 中 840 个文件哈希逐项匹配。实际 tested source 为 `6ee51623e7f32f6df425e0d84313d1bc5e7f6bed`，head 为 eb77b2f。Astra 已恢复实图审查；这些完整性结果不是视觉通过。
- Astra 首项新图结论：History 四配置图表 `complete=false`。浅色1.0 chart底部1306而visible至1267，裁39px；1.3底部1475而visible至1267，裁208px。标准1.0时间轴仍遮挡，G1/G3继续FAIL；1.3按M5§10.4允许首屏仅真实plot、图/notes完整局部滚达，因此缺208px本身不判FAIL，须核后续滚达证据。此处纠正首轮观察过度外推。列表单位换行有改善，不复用旧91ab失败作为此次证据。精确修正规格待Astra完成。
- 旧 lifecycle 39196410585 与 single-screen 39196410497 已核实success。旧 frame 39196410409仍FAIL，artifact12161953409原ZIP SHA/930条目CRC验证通过。原restore JSON显示字段、selection、draftId保持，而IME true→false、content高680→1391、content-scroll662→0；页面自报frame7恢复不等于最终系统状态，不认定原因、不放宽原断言。
- 新候选49a1b46已推送；12项PR CI运行句柄保存在 `build/task-first-evidence/ci-49a1b46/run-handles.json`。当前除branch policy成功，其他仍运行或排队；不取消、不重跑，不将旧结果外推新候选。M8三个设置页由Sol继续开发。


### 49a1b46 已返回的门禁

原生必需CI `39204483104` 已成功；原始日志确认 `:mobile:lintDebug` 实际执行并成功，关闭先前 TaskComparisonLayout WrongCall 的编译/lint阻断，不代表视觉通过。日志见 `build/task-first-evidence/ci-49a1b46/native-success.log`。

M6 `39204483033` 与 M3 `39204483014` 已失败并保存原ZIP及SHA/CRC。M6趋势入口误报为根坐标与screen坐标混比较，另有短窗freshness真实裁51px，分别修复；M3原业务恢复仍失败，追踪因UiAutomation命令传输未正确执行而无proto，不据缺记录推断产品根因。精确规格见 `2026-10-19-m6-short-window-and-ime-trace-review.md`，均待修复后实际复验。


### 49a1b46 M5 终态与下一批本地验证

M5 `39204483061` 已 completed/success；artifact `12166285439` 原 ZIP 83,523,168 bytes、SHA `083c7a1d46cfd9f3c70ce347307d89f1e5bb5899dddd608ea3ed2173908bc992` 与远端匹配，895 条目 CRC 通过，未改原图已解压至 `build/task-first-evidence/ci-49a1b46-m5/raw/`；16 主 manifest 的 840 个文件哈希也已逐项匹配，source 均为实际 merge `46e2dbc79ca0732290db92b5c78ad097c82c86cb`。CI 通过不关闭设计验收。Astra 已逐张查看 eb77b2f 的 16 主图和 60 特殊图，G1–G4 审计仍进行；新增独立 HistoryDetail UNKNOWN/1.3 实际 plot 约 67.55dp 的预算问题，不能以 chart View 外框高代替绘图区高，也不能由仅嵌入路径的候选修正推定关闭。

下一批冻结修复的 `:mobile:assembleMockAndroidTest` 实际成功，31 秒（74 tasks / 4 executed）；包含 Single 精确 fixture/chart viewport 适配、M4 原生详情焦点等待、History 实际 plot/滚达检查以及窗口证据修正。日志和已跟踪文件源码哈希保存于 `build/task-first-evidence/frozen-repairs-androidtest/`。尚未运行 instrumentation；M3 start/stop 确认仍需按 Astra 新审查收紧，M8 12 配置测试尚未完成，不计视觉通过。


### M5 逐门审计和最新布局候选

Astra 已完成 eb77b2f 的 76 份 typography、3502 条 TextView、516 条主态 Canvas text、44 组实际数值/单位 baseline 及44个单位 span 的审计；G2/G4 仅在列明已采状态为 LIMITED_PASS。完整页状态不据此外推。具体四页 G1–G4 和缺失的右侧滚达证据见 `2026-10-19-m5-eb77b2f-visual-review.md`。

Comparison 常态整格边框已在 visual 路径改为水平分隔，保留首次及后续 taste focus/ripple；独立 HistoryDetail 新增按真实自然预算条件同行的永久布局，不降低字号或96dp真实plot门槛。两文件已通过 Astra 静态审查与 `:mobile:assembleMock`（30秒，62 tasks / 4 executed），日志及两文件哈希位于 `build/task-first-evidence/comparison-detail-build/`。尚无新真实截图，旧布局 FAIL 保留。Sol 正补两文件范围内 M5 主/特殊态的真实plot、notes、toolbar/summary逐目标滚达和精确恢复证据，保留16主+60特殊。

字体原 absent 恢复问题继续独立 FAIL：官方 Android14 源码观察链可解释delete在global仍1.3时触发1.0持久写回，但未追踪CI实际writer；`am get-config`文本不含fontScale，不能作恢复ACK。源码和局限见 `build/task-first-evidence/font-scale-source/`。当前仅调查受guard的可丢弃CI emulator snapshot隔离能力，未改runner，不声称已修复。


### M5/M8 测试包统一编译与剩余取证收口

完整 M8 首版接入12主配置后，首次 AndroidTest 编译实际失败18秒，仅两处Context传入要求Activity的接口。原owner已用真实dialog owner精确修正；第二次 `:mobile:assembleMockAndroidTest` 实际成功32秒（74 tasks / 4 executed），包含M5固定20目标+条件UNKNOWN警示、M3收紧ACK及M8首版。两次原日志与源码哈希位于 `build/task-first-evidence/m8-m5-androidtest/`，失败未覆盖。

Astra已复核M5最后精确集合，静态阻断关闭。M8隔离与业务范围符合已批准计划，但requiredproof、每目标滚达恢复、dialog自身字体报告仍按审查完整清单收口；不能将上述编译成功记作测试运行或最终取证版本通过。

独立snapshot能力探针新增3文件，9项离线契约测试实际通过（含未知save/list/pull零load、恢复失败保留snapshot、真实源码配置格式解析）；Astra修正版静态通过。探针尚无Android实测，保持font_scale严格恢复未通过。仅独立disposable CI，随下一合批PR检查，不接现有视觉runner。


### 2026-10-20 合批前最终检查

M8最后safe-controls隐藏按钮接口问题已按四dialog真实按钮roles收口；原whole/safeRect/48/52门禁保持。Astra核当前Kotlin SHA `7a677a0b60def82c9737063a98fc715035e1a0021b55ac8d2fb18d1c41373b39` 与runner SHA `6cef960c600b40b67127cdabba3744eaf55ee6e273f31d11675e74d561619ad9`，完整本批清单静态无剩余阻断。最终 `:mobile:assembleMockAndroidTest` 实际成功30秒；日志/哈希见 `build/task-first-evidence/m8-final-contract-build/`。四runner Bash与20段内嵌Python语法通过；8项分支策略单测通过。

仅具备进入新CI条件，不授M8实际12图、M5/M6旧FAIL关闭或全24页视觉通过。独立snapshot probe随PR触发，仍不接现视觉runner；原font_scale恢复失败保持严格判定。


### 2026-10-20 a47 新结果与下一批静态复核

PR `39417689948`（advanced）和 `39417689968`（single-screen）已 success；不据此升级 G1–G4。M5 `39417690186` 失败于 `M5 comparison target clipped: comparison-source-1`，并有独立 font_scale 恢复失败。artifact `12263680615` SHA `d2151fbc2d72a5083f9f91f6030ca74faf7640288d29481612ba619c36f895b7`，648 条目 CRC 通过，原件保存在 `build/task-first-evidence/ci-a47c870-m5/raw`，由 Astra 继续核实际裁切原因，不降低 whole 门禁。

M4 a47 artifact `12263336165` SHA `4d020c2edaeb3ab9baf165fb61b4b663254bf57d3953ddb63bc633adec02c464`、777 条目 CRC 通过；12 主 manifest 已产出，restore 原件 `originalExitStatus=0`，仍因原 absent 字体键变为 present/1.0 整项 FAIL。此事实不替代特殊状态新增补证，也不重复外推视觉通过。

下一批 M4 两特殊态全文滚达/恢复、M8 两真实 CompoundButton 单次点击、显式 Window 平移/短窗模式与诊断计时已由 Astra 静态复核。最终 helper/runner 对应 AndroidTest 编译成功31秒，M3 因果诊断最终编译成功29秒；root 独立重跑 snapshot 11 项和 window runner 契约9项均通过。这些只证明本地编译/离线契约，未证明新版本 CI、实际像素四零差或硬件。Lifecycle 仅按 Astra 规划补超时前内存条件快照，保原10秒与失败，未猜测生产修复。


M5 新缺陷已由 Astra 原图/geometry 确认：light/1.3 的来源全文自然高285px、details viewport仅131px，无法滚达完整目标，属于生产最低预算遗漏而非测试坐标。最小方案见 `2026-10-19-m5-eb77b2f-visual-review.md` 新节：加入来源和独立摘要行自然高，不改字体/全文断言或图表96dp下限。Sol 正按此实施。Task-frame PR `39417690223` 已 FAIL 于 `editor-stage-hidden-values` 重建状态比较；只读取证中，未推定具体失真字段。

Lifecycle 两文件窄诊断已获 Astra 静态通过，编译32秒成功。主线程完成读取时间不等同超时瞬间原子现场；附加诊断布尔也不是新 ready 门禁。豆子 a47 12 manifest/736 文件哈希已逐项匹配，均机械通过且最终恢复 FAIL，保存 `ci-a47c870-bean/manifest-verification.json`。


M5 单文件预算修正 SHA `7ca0c698a18bd9065c3a2c71aae63c1cdca507c89dd12f142160e1fac72818ef` 已获 Astra 静态通过；App+AndroidTest 合并编译实际成功52秒（两 Kotlin 编译任务执行），日志 `sol-m5-comparison-budget-build.log`，真实新图仍待下一CI。8项branch-policy测试通过，PR4仍OPEN/draft→develop，未合并/发版。

M2 新原件 ZIP SHA `30a9099fa511cd3647720deb0e0272806948afbd65da479712d73ed774157b68`、930条目CRC通过；11字段值/选区/目标焦点/草稿身份一致，仅IME true→false、内容高680→1391、scroll y/range662/662→0/0。Astra确认不能推定与M3同源；仅批准同一失败scope内因果诊断，不修改生产恢复逻辑/原断言/等待与show次数。只读报告 `ci-a47c870-frame/m2-offline-diagnosis.md`。


### a47 终态与实际视觉增量

已重新查询GitHub确认a47全部25个runs终态：PR 5 success/9 failure，push 3 success/8 failure；保存 `ci-a47c870/final-all-status.json`，未取消/重跑。四runner Bash与7段嵌入Python静态语法通过。

Astra 对本包实际 light/1.3 原图增量复核：独立 UNKNOWN HistoryDetail 的真实Canvas plot为102.66596dp且screen完整，本配置旧67.55dp问题已获实际修复证据。missing-samples 三图例、空sample语义及图/说明/两note/restore原图已查看。只采light/1.3，旧标准1.0截轴没有本轮证据，不能关闭；预算诊断 `primaryBudgetSatisfiable=false` 与实际plot足的账本差异保留待核，不覆盖实际原图/geometry。


### 最终合批静态收口

M2 五文件最终 Astra 静态无阻断；最终 AndroidTest 编译成功30秒，记录 `m2-causal/build.log` 与 `source-sha256.txt`。只给编辑页 light/1.0 首次重建增加独立原生trace与关联证据，M3默认保持；原恢复逻辑/等值断言/等待/show次数/PARTIAL判定不变。两窄收口为capture取消焦点调用边界标记、诊断写盘异常不覆盖原业务异常，未改变产品恢复结果。M2原FAIL与M3原PARTIAL保留，实际新运行待CI。


### dc5875c 首批原件

Scale39422164339 在浅色1.3初态即5秒同步FAIL，尚未运行到新dialog映射，不把该结果记作映射失败。artifact12265131111 SHA `aaab06e5b8a10324459f08d0edc1c1aa1a9300175b19ca87db88528fb6ab5519`，24条CRC通过，实际merge `84aaa3efd951e4f6229fc9d235e257e61f0f257c`；新计时原件由Sol只读分析。

独立snapshot39422164320已PASS：artifact12265061384 SHA `bcf78c70bcfcfc2a6c3993a6d6eaf3e04a955ed598da631741ad1b2780c2b9be`，187条CRC和result引用186原件SHA已逐一核对。before.settings确无font_scale，mutated为1.3，load后restored.settings再次无该键且framework配置1.0；save/load/delete各一次ACK、host先留模拟业务失败证据。只证明指定disposable emulator快照恢复能力，现视觉runner尚未接入，不关闭旧清理FAIL，也不追认UI通过。

完整24页缺口及实际字体验收终点已由Astra整理在 `2026-10-20-full-scope-acceptance-gaps.md`；下一步优先完成已有Extraction原图的设计对照，不只增加诊断材料。


Scale新timings只读定位：首轮S1独有右缘9×823瞬态差异，形态与scrollbar渐隐一致；第二轮四同域零差，但comparison完成5659ms超原5秒659ms。首轮JSON比较1134ms/哈希714ms/中间proof序列化846ms，不能以持久化后总时长冒称采集预算扩大。报告 `ci-dc5875c-scale/analysis-window-frame.md`；无代码改动。

M3新关联证据保全及分析见 `ci-dc5875c-curve/m3-causal-diagnosis.md`。原断言仅scroll4885→4281，文本/选区/焦点一致；native确认新field上的hide但无可靠caller栈/request链接，不能证明发起方或-604因果。显式fixture unfocus尚未执行，collector仍PARTIAL；不根据相关性猜改生产。

Extraction覆盖纠正：Astra实际重看eb77四张主态发现本页只有四主图与display伴图，60特殊图属于其余三页，不能用于宣称Extraction特殊态已采。原manifest的STARTING/STOP_REQUESTED/UNKNOWN/no_scale/stale/sample_gap等12项仍pending，另1.3右栏两工具完整滚达缺证。正对原M5计划核遗漏，完整24范围不缩水。


### dc5875c 后续已保存证据

M4 bean原业务exit0，12主manifest/960引用文件SHA逐一匹配；两既有特殊说明状态各4profile均有visible/restored原图，共16图，待Astra限定验收。仍因字体key absent未恢复整run FAIL，不覆盖清理失败。M5 6manifest（3主+3special）/1180引用SHA匹配，仅light1.3；History/Detail机械通过，Comparison已越过旧来源裁切处，但UNKNOWN warning的全文/可见断言失败，新图待审，不宣称原布局已关闭。Legacy 3manifest/86引用SHA匹配；Curve/light1.3短窗正式proof已产，History/light1.3真实plot84.11058dp低于当前160门禁，待实际设计原因核查。全部tested merge为84aaa3efd951e4f6229fc9d235e257e61f0f257c。

Settings39422164407初态即同步超时，未到Switch；artifact12264527887 SHA f04df8c3e9567c1943771cf99900cb8df46405454270355550d0fdba76704cc7/23CRC通过。首轮geometry JSON比较1415.8ms、中间proof序列化976.7ms，第2轮display请求前5017ms已越原deadline而拒发。与Scale共同提示可无损减少中间序列化，但尚未实施，不放宽5秒门禁。


本地磁盘ENOSPC导致dc frame解压中断，原ZIP已核远端SHA/全CRC、未损坏，部分raw不作为证据。保留全部原ZIP，逐文件SHA核完全一致后仅移除18历史重复raw，回收1,773,912,479 bytes；还原清单在 `build/task-first-evidence/raw-eviction-summary.json` 和各包 `raw-eviction-record.json`。当前dc/a47/bcc/eb77/M1审查原件未清理。引用历史raw时先按原ZIP恢复，不误判原证据丢失。


dc5875c整批26运行已从GitHub确认终态，最终句柄/结论保存 `ci-dc5875c/final-all-status.json`。未取消/重启。M2 frame artifact12264853174 ZIP SHA697df968a12604b5f71db5fcad0cfd102c0f9217ae0e32e47d092253cf136119、1082CRC，ENOSPC后已从保留ZIP安全恢复完整raw；新因果sidecar确认同decor/token、新field13→27，显示结果与应用滚动阶段IME状态曾改变，最终原断言仍FAIL，未据此推定hide调用方。


### dc后续候选（尚未新CI）

Window A只删除重试中间proof序列化、保留终态一次完整写盘与原异常优先级，Astra静态通过，compileMockAndroidTestKotlin成功；5秒/所有像素及geometry比较未改。Legacy opt-in自然测量两文件静态通过（52156035/cb1172fd），默认null路径不变，同Paint自然160且EXACT/AT_MOST真实约束保留；旧84.11dp失败仍需新原图关闭，未据静态升格。

Scale专用snapshot host事务四文件静态收口，root独立运行13事务契约+11探针测试均通过；其中disk-full报错为测试主动注入分支，不是新磁盘耗尽。实际Scale集成尚未运行，不追认旧清理FAIL。原矩阵失败隔离方案首批仅M8，见 `2026-10-20-existing-matrix-failure-isolation.md`；只在全部既定隔离证据通过后继续原下一格，整体失败码保持，未知清理即停，尚在实施。


### 本地合批终审补记（2026-10-20，尚待新 CI）

Astra 已完成 Extraction B 五文件静态终审：12 态通过原 reducer/typed events 进入，operation request/completion 因果证据已补，默认 Mock/真实 hub 路径不变。最终 M5 测试 SHA `ca4c1d0a9e95542bb7233a9b636209ac9ba1bc0fbf6f420773bca3dbad2641d0`、runner `38f7a3e7ff4b73ab8f4ee11f6ae4c14ed5c1a52d2087b94e4dcbc42db5d6b6aa`。统一 `assembleMockAndroidTest` 与场景单测成功（34s，5 tests/0 failures/errors/skips），原日志 `build/task-first-evidence/extraction-final-build.log`。这些结论不替代 12 态实际截图、参考 Dialog 或 Window 协议验收。

Curve Library 浅色1.0主态/非首选择/恢复，以及 Legacy Curve 浅色1.3主态/恢复/缺参数/空态/读取失败，已由 Astra 实看原图限定接受。Legacy 长名和短窗下方全文滚达仍缺图；导入 Toast 覆盖处不签无遮挡。详见 `2026-10-20-library-legacy-curve-visual-review.md`，不外推四配置或 Import/History。

M2 dc 原 ZIP 与恢复轨迹复查见 `ci-dc5875c-frame/diagnosis.md`：仅 IME true→false、content 680→1391、scroll 662→0；字段、selection、focus、draftId 相同。trace collector 仍 PARTIAL，hide 来源 unknown，不根据与 M3 的相似症状猜改生产逻辑。

M8 固定矩阵失败隔离三文件已获 Astra 最终静态通过，11 项离线测试通过；EXIT 优先保留账本合法首失败码（7→后续9仍为7），账本不可读则保留当前失败。原12格、全部成功格完整门禁、未知隔离即停均保留。实际安全续格仍待下一轮 CI，不覆盖 dc 的失败。
