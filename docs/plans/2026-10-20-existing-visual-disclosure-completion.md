# 既有详情披露补证与Library覆盖核对

2026-10-20，基于已推`7082e04`。Astra只读规划，不改产品/测试，不重审已接受图，不等待或取消活跃CI。仅补原M3/M4/M7要求，无新增功能、状态机或取证框架；M2/M3 IME unknown结论不动。

## 1. LegacyCurve：原长名/短窗详情滚达

已执行dc样本：long-name-category只有初图；shortWindow只缩至280dp、核永久list/chart identity、采初图后恢复Window属性。它没有进入短窗详情，也没有长名下方第二排metrics/notice/详情入口逐项可达和恢复图。新CI原样重跑不会自动补齐。

**最小owner范围：** `LegacyVisualMigrationChecks.kt` + `scripts/check_legacy_visual_migration.sh`，不改LegacyCurveActivity/Collection/renderer/默认protocol。单owner顺序实施。

- 复用原long合法archive、原Activity，不增加另一假名称。标准窗口先保现初图，保存实际selectedIndex/fingerprint、list first/offset、collection.detailRequested、detailScroll x/y、Window属性、archive字节SHA和nativeInvariant。
- 以原对象精确定位detail title、`legacy/curve/metadata`、四metric各自label/number/unit、`legacy/curve/notice`、唯一`ui_full_details`动作。ScrollView按detailContent祖先定位，不对全树任意scroll操作；四metric与标签按实际父子关系核，不只字符串计数。
- 在原detailScroll逐目标requestRectangle/真实commit后，用现有协议2保存window/geometry/Typography；记录同root完整screen rect、实际Layout全文/零ellipsis/行盒，不把isShown当无遮挡。每个可在viewport内完整容纳的目标必须whole；长文本若单View超过viewport，按原Layout行范围滚动，证明所有行盒/墨迹区间均至少一次完整进入真实viewport，不声称该超高View整体whole，不删除原适用whole检查。
- short-window复用同长名Activity与原Window.setLayout。初图保留；若collection已回退列表，通过真实选中行点击进入原详情（不是反射show/set detailRequested），核原selected/fingerprint与archive不变。固定页面back/import及详情back在其实际可见层必须完整≥48，不把Activity工具菜单误当导入按钮。然后按同目标集合完成实际短窗详情滚达，截图调用已批准MEASURED_SHORT_ACTIVITY_WINDOW，不能用全屏Activity默认冒充。
- finally先恢复detailScroll及原详情/列表展示状态（真实返回动作）、list精确first/offset；再恢复Window属性、commit及完整恢复图，核selected/fingerprint/archive/nativeInvariant与原固定入口身份/位置（各自对应窗口尺寸）不变。不能要求短窗坐标等于全屏坐标。失败保原异常；恢复失败附加且保持FAIL。
- runner精确要求两个既有case各目标集合、逐目标proof、restore图及恢复报告；short子目录的mapping模式显式按该已声明scope判定，不用“任意后缀含short”放开。其余main/special集合与门禁不变。超过可见高度仍不可读则据实FAIL，先交Astra定位，不缩字/缩padding。

不增加Legacy详情弹窗新业务，本批只证明既有页面详情内容与入口可达；若发现现有真实回退无法进入详情，保失败，不能用反射绕过。

## 2. M4：Batch与Ledger原生详情全文原图

**最小owner范围：** `BeanWorkflowVisualMigrationChecks.kt` + `scripts/check_bean_workflow_visual_migration.sh`。复用原batchBusiness的`m4-long-reason-adjust`（30次说明）和ledgerBusiness已实际点击的可见event row，不增加库存事件/点击次数/提交行为。

现状：Batch等待唯一focused decor后仅查包含longReason，随即dismiss；Ledger点击后直接dismiss，只核返回非零scroll。故“原字符串未截断”不等于“屏上全文可读”。

- 在既有dismiss前插入取证，沿已批准Settings显式Window身份方法，只读定位唯一attached/laidOut/focused原AlertDialog Window，核decor/callback/owner Activity；不可得则FAIL，不修改生产存Dialog字段。复用现NativeWindowFrameEvidence显式Window与已批准实际source映射，不新建截图协议，四零差/真实5s不变。
- 保存dialog初图、真实title/message/positive按钮对象与全文、Layout行盒、scroll原坐标、原eventId/业务模型/页面viewport。核message来自当前合法event的原详情formatter，不仅contains任意同词TextView。
- 原native message scroll内按真实Layout逐段滚动，完整覆盖每一行；每个viewport内可容纳控件whole，positive始终完整≥48且处实际safe rect，title若可滚同样记录真实行覆盖。长message不能以一个巨大TextView的whole不成立就删除全部可读检查，也不能以字符串相等代替逐行覆盖。每段原PNG+协议/geometry/行范围，终态恢复dialog原scroll并采恢复图。
- 沿原一次positive dismiss，核原页面精确scroll/event顺序/原选中及scope.inventory等业务不变，保原业务case命名，追加详情取证报告及runner精确requiredproof。仅这两个已存在弹窗，不顺便重构调整输入/预览IME弹窗。

## 3. Library原计划覆盖：实现≠本轮执行≠视觉接受

核`CurveVisualMigrationChecks.libraryBusiness`、`LanguageDetailChecks.curves`、`LanguagePageChecks`、`CurveLibraryViewportChecks`及对应runner。下表是具体入口结论，不以整个workflow绿勾替代。

| 原要求 | 现有实现/入口 | 已保存执行与接受边界 | 缺口/下一最小处理 |
|---|---|---|---|
| 压力自定义主态、三metrics、四阶段真实target、固定footer | M3 run library主fixture与Canvas/geometry，四profile在原循环内 | dc仅中文用户平板浅1.0主图已验；其余原格会被后续Import失败阻断 | **已实现待执行**的其余profile，不重复建状态 |
| 连续列表、query/category/MOST_USED、非首选择、详情返回/重建、用于本杯只改selected | libraryBusiness真实手势/选择/原parameterShortcut/真实use；RECENT作为初态 | dc浅1.0 non-first/restored原图及业务报告已验 | 其余profile待执行；当前参数dialog只打开关闭，无全文原图，不宣称dialog视觉已验 |
| longFactory/noPressure | LanguageDetailChecks五夹具循环的两项，lifecycle landscape/wideFont各8语×2主题；仅这两项调用captureSuccessfulFooterWindow | 旧`ci-a4786a0-lifecycle/.../language-detail-success`确有这些图；旧1600×1000/旧源码，不能替当前用户2560×1600四格 | **已实现旧回归截图**；独立M3原件没有这两特殊态protocol2图。用真实库item加入M3原scope状态取证，不重做已过旧图 |
| captured/unavailable/fourStage | 同五夹具循环均有canStart/原因/targets或fallback/footer文本矩形断言；unavailable是既有合成CurveLibraryItem，经反射show，不是存储真实条目 | 只有失败捕获；成功无专属整窗（四阶段主态也不能代表所有来源） | **断言已实现，成功视觉取证未实现**；复用合法captured/factory/custom模型与真实选择，unavailable需现有可存只读模型走真实选择，不能把反射UI夹具升格产品证据 |
| 无压力/只读disabled完整原因及完整参数披露 | 旧五夹具核TextView与fallback；M3只采可执行pressure和另一factory | 当前主图不能证明这些状态；不把Preview FlowRaw图当Library只读图 | M3增原状态protocol2原图、原因/参数可达及恢复；不造压力图、不把96旧布局下限冒充主160门禁 |
| 快捷1–5仅合资格factory | M3明确`presets_not_touched`；本次检索现instrumentation未找到Library真实1–5资格/选择动作闭环，旧upgrade仅slot1存储迁移不等价 | **M3未实现这项业务验证**，不是“等当前CI会有” | 后续限定真实合资格/不合资格菜单与原slot回调，白名单存在性/类型恢复，不发机器命令；与主测试owner排顺序 |
| 编辑/分享/导入/旧版路由不丢 | M3 libraryBusiness未调用这些按钮；旧文档/编辑器测试覆盖目标页，不证明Library来源回调 | **该独立入口闭环缺失**，不能以页面已存在代替 | 后续最小路由核真实Intent/目标Activity/chooser后取消与返回身份；不外发分享，不保存/启动；仅原范围 |
| 短高fallback及footer | 原LanguageDetail/Viewport有layout fallback条件断言；M3 library没有显式short-window状态采集 | **独立短高原件未实现**；正常1.3不是短窗证明 | 后续复用现shortWindow能力声明scope，真实进入/返回及原fallback/固定footer取证；不新增产品布局 |

首批实施优先§1+§2四个既有披露路径，工作量小且直接关闭已有G1/G3缺口。Library表中未实现部分登记为原范围遗漏，不永久pending；由root在当前M5/M8 batch后分配M3测试owner，最小范围`CurveVisualMigrationChecks.kt`+其runner（原LanguageDetail不削减），按真实模型选择与既有Scope恢复合同补齐。若需新增prefs白名单仅实际slot1–5键，不clear整库，不触硬件。正式补齐前须列精确状态/requiredproof集合，不能重复此次Extraction“宣称覆盖、实际仅主图”的遗漏。

以上规划未宣称新测试、当前活跃708 CI或新视觉通过；不重开设计稿，不扩大24页。

## 4. Library执行合同（固定集合，交M3测试owner）

### 4.1 范围、合法模型与顺序

仅`CurveVisualMigrationChecks.kt`、`scripts/check_curve_visual_migration.sh`；不改CurveActivity、库资格规则、LanguageDetailChecks及既有回归。不把旧UI-only `CurveFooterFixtures.fourStage`/人工unavailable item反射show当真实库产品证据。保原12主配置与主libraryBusiness、原103基础条目不动；以下在Library每个原profile追加，主图和原连续列表业务先保全。主流程最后use已finish Activity，特殊态另启动真实CurveActivity，仍用该合法隔离scope；禁止拿已destroyed对象继续操作。任何scope隔离失败即停止，不以取更多图为由续跑。

固定视觉case顺序：`long-factory`、`captured`、`no-pressure`、`readonly-flow`、`four-stage-pressure`、`short-pressure`。每profile恰6项，不增加mainConfiguration计数。fixture先声明item ID、来源、实际完整模型、expected targets、canStart/可复制/slot资格，由产品库查回同对象数据后通过真实ListView点击选择。可使用原search/filter来定位，再真实滚到行点击；不得反射show、伪写selected/availability/chart。

| case | 实际输入与选择规则 | 必须核的状态 |
|---|---|---|
| long-factory | 当前完整factory集合中按原CurveDetailsText全文长度最大者（并以ID打破并列），与旧longFactory规则同源，记录ID/全文hash；不另造长名 | 真来源/完整参数、实际压力或无压力分支、固定footer；列表原最多两行省略可保留，详情披露必须完整 |
| captured | 当前CurveCatalog中首个经validated的真实capture条目，稳定ID并记录完整profile，非改过ID的display-only拷贝 | captured来源、真实metrics/targets、canStart与动作资格 |
| no-pressure | 当前factory中真实pressureTargets为空者；按ID稳定选择，若不存在则明确fixture能力FAIL，不清空chart造状态 | chart不绘假path；原无压力fallback全文、完整参数可达、canStart仍由库决定，不把“无压力”等同“不可执行” |
| readonly-flow | scope CustomCurveStore合法save一个唯一draft UUID、四合法stage的FLOW_RAW文档（可复用现preview合法FlowRaw构造）；库find确实返回，canStart=false | 原只读原因、用于本杯disabled、edit disabled、share按fromLibraryItem能力、无假bar图；不强置禁用 |
| four-stage-pressure | 原main scope.document（20/90/80/60）真实库条目 | 4个targets/标签均真实，主fixture图已保留，但此case另外证明参数披露和返回；不冒称旧非catalog read-only四stage夹具已变可执行 |
| short-pressure | 同原四阶段条目，真实Window.setLayout(-1,280dp)，保原值并finally恢复；显式短Activity模式 | 原自然预算决定图或文字fallback；如fallback必须四stage完整可读，固定footer/实际back完整；不得把未显示图标为图已验 |

readonly文档属于owned内存库的声明fixture输入，添加前后集合和写次数记录；原主图仍104条，后续特殊集合如105须明确，不能悄改主fixture断言。完成后移除仅该新增owned文档，核原custom文档逐字一致；失败由scope销毁还原，不能删用户库。main业务不变量与fixture合法变更时段分开，不把预期fixture添加伪装成“全程无写”。

### 4.2 精确requiredproof与全文披露

每case目录`special-states/library/<case>/`：`case.json`、`fixture.json`、初态`window.png/geometry.json/window-frame-sync.json`、实际typography/baseline/Canvas（无图明确无path而非捏造非空）、`reachability.json`、`restored/window.png/geometry.json/window-frame-sync.json`。统一common目标为实际title、source/category、availability、3metrics各label/value/真实unit、parameterShortcut、use；不存在的unit必须根据模型精确声明null，不按空列表跳过。图/fallback条件集合由声明targets与实际预算分支共同核，不能仅从当前isShown反推应有项。

- 每个初态已完整目标可引用同次初态proof；需滚达目标另写`reachable/<semanticKey>/`的原PNG/geometry/proof，并保存准确目标对象/行盒。short所有这些原图均用同声明短窗mapping；其他默认Activity严格模式。
- 五个常规case通过原parameterShortcut打开真实参数AlertDialog，`details/initial`、必要`details/segment-000…`、`details/restored`各原图/geometry/proof；全文必须等于产品实际`item.name + details + availability`，同时与声明模型formatter相符。按真实Layout行区间覆盖所有行，标题/确认按钮分别核安全完整；五case即使全文相同也保各身份，不能以一张截图覆盖不同来源。
- short-pressure同样打开原完整参数入口，采同Window真实dialog证明；Activity短窗mapping与Dialog来源mapping分开，不根据父Activity模式套Dialog。若当前受支持协议不能认证该Window，保持FAIL交Astra，不新增映射猜测。
- finally关原dialog、恢复同Activity实际各scroll、query/category/sort、selected及list first/offset、detailRequested；short先按原导航返回再恢复Window及最终图。核产品库/usage/history/journal/inventory/beanPreparation/选中偏好无额外变化、无Service。恢复阶段必须实际比对，不只填true。
- runner精确6case和每case条件target集合；要求每份所声明requiredproof实际存在并满足原协议2/5000ms/四零差/身份/文件SHA，`visualAcceptance=ASTRA_PENDING`。整格不能因其中失败写PASS，也不以新增状态数抵扣原12主配置。

### 4.3 快捷slot固定白名单与资格

新增独立业务case `preset-eligibility-and-slots`，不归入上述6视觉case，也不计主配置。复用真实合资格factory项（可用已声明long-factory但须canStart=true，否则当前已验证factory中按ID选）；从真实更多菜单进入原assignPreset弹窗。先采选择器整窗，确认原5个本地化选项恰1–5且无第6项；按真实ListView当前可见child/adapter位置点击，不能假定adapter索引等于child索引。

依次1→5各一次，核每步仅`presets/PresetSlots.key(n)`从原值改为该factory ID，其他键逐类型/值/存在性不变；每步记录前后map、目标ID、原菜单/请求、实际落盘读回，原Activity/模型无start/stop/usage变化。每次重新打开同原选择器，不调用隐藏assignPreset.performClick绕过菜单。五个完成后恢复原slot键并等待原prefs提交/主队列闭环，不触未知键。

精确白名单仅`presets.slot_1`…`slot_5`；现`appearance.dark`、`curves.selected/sort`保持原恢复。首次任何写前保存整个presets map供非白名单相等核；slot原值若非String也按原类型恢复，不强转或清文件。businessDisk可仅将presets.xml/.bak列为已声明物理变化文件并另留前后SHA，须同时严格核所有非slot键，禁止扩大为整个shared_prefs免检。原不存在键恢复remove而不是空串；禁止clear/remove文件/字节回滚。

负资格复用captured与readonly-flow：真菜单不展示assign slot（当前产品对非factory GONE），记录实际菜单集合、原assignPreset isEnabled=false及slot全map不变；不点击不存在或禁用项。另核已验证factory资格为true；不人工清proof使整个库坏掉来造第三种factory状态。真实硬件执行资格仍不是Mock证明。

required：`preset-eligibility-and-slots/business.json`、`chooser/window-frame-sync.json`与原图、五次`slot-1`…`slot-5`各Activity结果图/proof、`restored`图/proof、两负资格菜单实际节点/完整本地化文本及“不存在assign”报告。PopupMenu仅作为原入口语义证据，不把其无独立Window认证的层次记录冒称协议2弹窗图；正式选择器是原AlertDialog。

### 4.4 原来源路由与恢复（不是再测目标页全功能）

固定业务case `library-source-routes`，目标顺序：`edit-draft`、`edit-copy-factory`、`share-page`、`import-page`、`legacy-page`。各通过当前可见direct按钮或原更多/管理菜单；不对未attach旧Button直接performClick来声称可达。

| route | 期望实际目标/参数 | 操作上限 |
|---|---|---|
| edit-draft | CurveEditorActivity，CURVE_ID为原draft；表单source同doc | 不改字段、不保存，原返回 |
| edit-copy-factory | CurveEditorActivity，CURVE_ID为选中factory，原fromLibraryItem产生新draft ID且显示原拷贝说明 | 不保存，原返回；原factory与custom库不变 |
| share-page | CurveShareActivity，CURVE_ID为选中可复制项，显示实际编码文档 | 只到本地分享页并返回，不点外部分享/复制/导出，不调用系统chooser |
| import-page | CurveImportActivity，原管理入口，无文档extra | 空原文主态，原取消返回；不读clipboard/不打开SAF |
| legacy-page | LegacyCurveActivity，无伪造模型参数 | 仅owned旧库的原列表读完成后返回；不导入、不写原文件 |

每route要求目标Activity monitor一次且目标实际focused、Intent字段和当前产品内容匹配，保存`routes/<route>/target/`原PNG/geometry/proof、返回后的`restored/`原PNG/geometry/proof及business.json。路由取证不是目标页完整G1–G4重验；无外部消息/硬件调用。记录进入前和返回后同一Library ID、query/category/sort、selected/detailRequested、list position/offset与详情scroll，库/usage/history/库存/准备/clipboard/slots不变。若原返回无法保持某字段据实FAIL，不替生产改导航。

Legacy入口存在具体Scope前置：当前M3只保存legacyHistory/legacyCurves原Lazy，不替换，直接点会触原初始化，**不得这样执行**。在M3测试Scope中仅为该路由安装owned cache LegacyCurveStore合法archive及原lazy身份保存/恢复；复用M7已有合法store输入/reader终止方式，不改产品，不读原旧库。先确保原reader不存在，跟踪新legacy-curve-list/import线程，目标Activity销毁/reader排空/主队列barrier后恢复原Lazy；原businessDisk字节不变。若此闭包未实现或不能证明排空，该route FAIL/未执行，不能通过拦截Intent不启动目标来标真实路由通过。

### 4.5 实施与验收顺序

先六视觉case/原参数dialog，后slot白名单，最后五路由；全部同一M3测试owner顺序改两文件，完整freeze再统一构建/真实CI，不逐状态微推。保原成功marker但扩明确计数字段：原主12不变，Library每格6视觉case+2业务case，原Import/Preview完全不减项。每项Astra按真实图签有限状态；旧five-kind语言回归继续原入口，不用新M3状态取代旧UI-only回归断言。该计划是补已批准范围，不是已实现/已运行声明。

## 2026-10-20 本批静态复审收口项

- Legacy：局部同日GONE日期处理符合原卡/title/metadata完整要求；禁止全局过滤隐藏文字。逐段geometry现取CapturedFrame.target，但行盒/viewport/whole必须也在同次observe中保存，再由stage引用，不能截图后另读。host核引用文件与accepted target及逐行事实相等。finally写报告失败附加原错误，不覆盖。
- M4：新增Ledger文案变量与原Activity变量重名须改为batchLabel，原路由保持。两原生details scope显式复用已批准`MEASURED_WINDOW_BUFFER_TRANSLATION`（含零平移合法情形），不默认失败后fallback、不改helper。host复用window_mapping_contract及原四artifact SHA/尺寸、全geometry/颜色/四零差检查。requestRectangle与实际baseline/Layout原点同源。以上为本批窄收口，未增加弹窗/点击/库存事件；真实图仍待CI。

### 最终静态结论

上述收口已逐项复核，M4与Legacy两组均无剩余静态阻断，可交唯一构建owner统一编译。不是设备执行/视觉通过：真实dialog映射、全文滚达及短窗仍须新CI原图。

| 文件 | SHA256 |
|---|---|
| BeanWorkflowVisualMigrationChecks.kt | defe23a3c176f32ef4b423478a2fb278bc18cc90a2661ad59952bf845626d783 |
| check_bean_workflow_visual_migration.sh | 08f672c6317e047c0a745d9cf98e1e27ae0227530e1f197de7fc78bf273a4a11 |
| LegacyVisualMigrationChecks.kt | 71881e44d11c5b450037dd95afe3ed2f4a85fc66f6d74e8340b27ab853ee8fab |
| check_legacy_visual_migration.sh | 331dcf24d096dd64fd50012d23f6b21d40126992699fa622804e2546a59cab3c |

M4原message formatter与event身份、原一次点击/dismiss保持；accepted target包含真实baseline行盒，四原PNG及映射全契约保留。Legacy逐行事实现从accepted target读取、host逐字段核对；GONE仅原同日日期对象、可见标题/metadata及卡rect仍完整；报告写盘异常不覆盖原错误。

### Library §4 owned store 生命周期定案

`CustomCurveStore`没有单draft删除接口。主Scope的原103/104流程完整执行并严格cleanup之后，才创建独立Library特殊态owned Scope；在其prepare/install之前预装合法FlowRaw/four-stage模型，另报真实count，不套用主Scope的104不变断言。六case、依赖该模型的slot/route在这个独立scope顺序完成；Activity/readers/writers排空后恢复原Lazy/prefs，沿原owned目录清理规则结束整个scope。禁止反射documents、为撤单draft删文件、初始化/操作原用户store。任一scope清理失败即停止后续scope。

### Library short-pressure 恢复顺序修正

用户宽屏1138dp中CurveActivity.compact=false，setLayout仅缩高不会改变onCreate决定的compact；原Back会finish。故在同Activity还活着时先恢复原Window属性、commit、列表/详情scroll及selected/model，采restored图和同identity账本；再走原Back并确认原Activity destroyed、Window消失。下一case需要Library时明确启动新Activity并记录新identity，owned store仍同scope，不冒称同实例返回。实际compact=true才按其真实Back返回browser分支执行。禁止改产品compact/反射showBrowser或要求已finish实例再截图。此为按原导航纠正取证顺序，不新增功能或减掉恢复核验。

### Library §4 首轮冻结静态审（2026-10-20，待窄补）

审查候选：Kotlin `285a2cc359aec02b6b6cb87acbb5491cf32e38ecc81a55382af4f2a0a0419d4c`；runner `fede905472142eaf6207474e287c4fb5e7c57094cf9a67d7145737b94b`。本轮未编译、未设备执行，不签通过。

104 主 Scope 先完整 cleanup，再开 105 特殊 Scope 的顺序、六项真实库选择、五 slot 的原菜单/chooser及逐类型白名单恢复、五路由的 Activity monitor/原返回、owned legacy Lazy 隔离，均在原合同范围内。需要收口的限定项已直接交两文件 owner：

1. 新 short/Dialog 的 `observation` 与逐行 viewport 不能直接比较 raw `getGlobalVisibleRect` 和 screen origin。复用已批准的实测同根 screen 映射，保 token/display/祖先 identity、全矩形与全部行检查，不用固定偏移。
2. `no-pressure` 按稳定 ID 实际选 `factory-v3-003`，其重量为 0、单位应 GONE。特殊态基线必须按声明模型区分真实单位同基线与 `NOT_APPLICABLE_MODEL_NULL_UNIT`；短窗实际隐藏项另标 fallback，不伪 PASS。原 main 两项严格基线不改。
3. 新 host 的 initial/reachable 分支要重验同次 accepted target 的原 whole 合同（完整 rect、Layout、行数、无省略、行盒宽高），不能 initial 只认文件名或 reachable 只认 complete。
4. slot chooser 的 accepted target 须记录真实标题和五个 adapter-position 对应的 View/文字/whole，而非只记录构造字符串与 adapter 内容；不增加选择动作或截图次数。
5. long-factory 按合同仅以原 formatter 全文长度排序、ID 打破并列，落原全文 SHA；不能悄加 name 长度改变选择规则。

以上是修正取证语义和精确覆盖，不增加产品功能、状态或门禁。owner 新 hash 冻结后再核，统一构建与真实 CI 图仍是后续步骤。

### Library §4 窄补终审通过（2026-10-20）

最终实核 SHA-256：

- `CurveVisualMigrationChecks.kt`：`e3b6342681061e2371fd76dbd32342881cd5196cd367c2d3c43e6d645eac916a`
- `check_curve_visual_migration.sh`：`adda5af43032f2f69abd6e4e2b467e4c3575acc397a50e44c0688b2de7c50df4`

上述五项已逐项复核：同根映射覆盖 observation 与两处逐行 clip；特殊基线精确区分可见/模型 null/short fallback且原 main 不动；host initial/reachable 重验原 whole 全字段；chooser 同次 accepted target 包含真实标题、五个 adapter 位置绑定的 child/label及完整行盒；long-factory恢复原 formatter 长度排序并保存全文 SHA。本组静态无剩余阻断，允许进入 root 协调的统一构建。

本 reviewer 独立执行 `bash -n`、6 段 Python heredoc 编译、`git diff --check` 全部 exit 0。尚无本候选 Android 编译或真实 CI 执行结论；六状态、原生 dialog、slot与路由的真实采集/视觉验收仍待原件，不由静态通过或旧图代签。
