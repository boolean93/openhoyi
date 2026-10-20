# M8 冻结生产候选静态审查

审查范围：AppSettingsActivity、ScaleSettingsActivity、MachineSettingsActivity、SettingsVisualStyle、TaskSettingsGroup、BrewFeedbackPreferencesCard、AppLanguagePreferencesCard 及八语言 settings_visual.xml。仅静态审查，不替代 12 主图和特殊态 G1–G4。

## 已核结构

App 640dp 中心整页滚动四组，Scale 688dp 三组，Machine 40:60 自然高度双栏与五 section 均保留。语言/反馈/TaskSettings 共享组件采用 nullable opt-in；默认路径保留。原偏好回滚、Scale render/owner、Machine 监听与确认/可用性门禁、生命周期核心未见被设计迁移删减。原输入对象先固定装入自然 flow，resize 不重新挂载。native dialog 标题与原按钮保持 owner/监听，预算取真实 screen frame 与实际按钮自然高。

## 阻塞：长非法数字输入的横向预算

`SettingsTemperatureRow.onMeasure` 将原 EditText 固定宽设为完整 text 自然宽（最小96dp），但 stack 后 quantity 外宽仅 available；固定子宽仍可能超过父宽并把 °C 单位推到父级外。合法/非法输入均属于已批准特殊态，不能因非法值不执行命令就豁免布局。

最小修正：仅 stack 路径，使用实际 quantity 可用宽减单位自然宽和8dp间隔作为输入宽上界；保留同一原 EditText/选区/监听/校验/字号，采用原生水平文本滚动，不重建输入。记录自然宽、分配宽、unit 宽和预算是否满足；空间不足最小预算不可隐去单位或伪报 PASS。根与生产 owner 协调后实施。

其余初步静态结构不构成视觉通过。后续仍需主12、五 section、8语言、草稿/IME、偏好拒绝、SAF/audio 和业务隔离取证。

## 限宽修正复核

已复核 SettingsVisualStyle.kt:194–226：先量原输入/quantity自然宽，用后者减实际input宽得到真实unit+gap预算；仅stack限制原input宽。记录自然/分配/上限和最小预算满足性，无对象/文本/回调变化。该实现阻塞静态关闭；实际长非法输入、单位完整、选区和水平滚动仍待特殊态原件。

## M8完整测试首版静态审查与封闭收口清单

已读SettingsVisualMigrationChecks、runner、workflow与Instrumentation入口。10 Lazy scope、Machine唯一真实Mock Service/default系统clock、原未初始化recovery隔离/credentials不触及、Activity/Service销毁及callback/writer排空后恢复delegates/prefs/盘哈希的顺序符合计划。App/Scale不为取图造Service。12主配置与五section、8语言、偏好拒绝、原audio preview、SAF取消/失败/owned zip、合法Mock预览/非法输入、原生IME/schedule取消、Scale精度返回入口均落地；未知设备ACK/硬件audio/非Mock能力仍显式PENDING。读取Window只沿真实decor→mWindow→原AlertDialog→当前owner身份链，反射缺失/歧义FAIL；原safeRect与实际IME不放宽。

本批完整收口仅既有两个测试文件，不扩大业务状态、不改生产：

1. runner对每个实际截图特殊态要求明确PNG/geometry/protocol2文件和ACTIVITY/EXPLICIT_WINDOW类型；不能仅glob已有proof。语言拒绝、合法Mock dialog、schedule draft及额外schedule-cancel-no-write附证均必须EXPLICIT_WINDOW。主12继续精确集合。
2. SAF三个BUSINESS_ONLY不造截图，但要求saf-business.json及取消0写、真实失败toast、成功1写且zip metadata、provider清理；硬件PENDING不虚构proof。
3. main reachability、五Machine section、Scale all-instructions记录稳定目标键与每目标proof、before/restored scroll精确值及restore原图；核心目标集按既有页面字段/标签固定校验，额外自然正文也逐项留证，不用complete=true数组或文件glob代替。合法scroll全文仍可滚达，不改首屏要求。8语言current控件capture后reveal需加入滚后实际证据。
4. 协议要求唯一comparison_complete实际elapsedMs≤5000且remainingMs≥0；原四差零、同域、mapping与完整display文件不降。
5. 原生dialog补实际其自身TextView的font/weight/size/baseline/colors取证，不以owner Activity字体报告冒充dialog；原focused input/正负按钮screen∩visibleframe/48与52门禁保持。

已交原owner收口，当前统一编译运行期间不改Kotlin，等root释放。以上是本轮完整必需清单，后续不叠加无计划状态；实际12图和特殊态视觉验收仍待新CI。

### 封闭清单Kotlin复核

已读最新冻结Kotlin：dialog-typography来自accepted EXPLICIT_WINDOW目标自身节点的实际Paint/Layout/字重/基线/颜色，fallback glyph身份仍明确UNKNOWN；不读取owner Activity冒充。reachability每target按accepted geometry内viewIdentity唯一定位并核whole，记录稳定tag/原input field/正文树path，全部ScrollView与HorizontalScrollView原坐标在finally精确恢复，并独立capture restored。八语言current均有滚后protocol图与恢复，非中文保实际fallback不伪报中文字体匹配。这三项静态符合，等待runner fixed核心集合、必需文件及实际matching deadline冻结后一并收口，不新增状态。

### 2026-10-20 当前runner独立终审

实际读取335行runner，SHA256 `a207904293944a15a6918f442f9c2af50926aec82045050f8c03f7b3a951c728`，bash-n、diff-check及5段内嵌Python编译通过。required状态的具体文件/Window种类、exact core target集合、所有reach/restore图、SAF业务原件与provider清理、comparison_complete实际≤5000ms均已落地，不需要owner最终消息才能核定候选。

发现最后一处接口阻断：runner235–239要求safe-controls.controls每项whole，但Kotlin654–662只对shown distinct断言、落盘却包含GONE按钮。官方SDK34 AlertController784–800先构造正负Button，无文案时GONE但getButton仍非null；语言选择仅negative，故会把隐藏positive误当可见控制而失败。不能按globalVisible/complete过滤来绕过真实裁切。

最小schema：四既有dialog按state固定期望role，语言拒绝仅negative，legal Mock仅positive，两schedule均positive+negative。真实按钮记录positive/negative的present/isShown，期望必须显示、非期望不得显示；controls只落已断言shown distinct对象，保原geometry并加roles数组（同对象可同时focused）及isShown=true。runner按固定期望核角色与coverage，继续原whole/safeRect/48/52。由root授权Sol新owner只改captureDialog与runner对应几行；Astra未改实现。

### 2026-10-20 最后按钮角色接口收口：M8静态无剩余阻断

已实际复核最新captureDialog与runner：四既有dialog按固定期望positive/negative角色核真实按钮显示；controls使用同一已断言shown distinct集合，保留focused别名；buttonRoles独立记录present/isShown。runner核固定角色、controls覆盖，继续whole/safeRect，不按globalVisible过滤；原48/52门禁保留。最后明确接口阻断关闭，本批完整五项清单静态无剩余阻断，可由root合批构建并推送实际CI。

核定文件SHA256：

- SettingsVisualMigrationChecks.kt：`7a677a0b60def82c9737063a98fc715035e1a0021b55ac8d2fb18d1c41373b39`
- check_settings_visual_migration.sh：`6cef960c600b40b67127cdabba3744eaf55ee6e273f31d11675e74d561619ad9`

此结论不是运行或视觉PASS。12主图及既定特殊态仍需新CI原件与Astra图审；原font_scale恢复、硬件及其它页失败各自保留。

## 2026-10-20 首轮 scrollbar 渐隐采集修正规格

根与实现owner从 bcc64c1 Settings 实际包报告：S1右缘9×723灰条，后五缓冲完全一致，仅S1差6507px；本节据其原件定位报告定最小采集方案，不冒充本审查者已完成12图验收。

核本机官方SDK34 `View.java:21249–21268`：ScrollabilityCache OFF=0/ON=1/FADING=2；onDrawScrollBars 的 FREEZE_END 将state置OFF，但该调用仍继续本帧绘制，所以只看OFF尚不足以保证最后灰条已提交消失。

仅同一 NativeWindowFrameEvidence 协议 started+5000 总deadline内，在S1前增加只读真实绘制稳定前置条件：遍历目标同root显示的开启scrollbar且fading=true的View，读取mScrollCache.state。ON/FADING继续等待实际帧，OFF后须再完成一次真实hardware commit，复读OFF及scroll/geometry未变才开始原S1…S2。无cache明确记录；非fading静态滚动条不要求OFF。反射不可用/未知state/跨root/超时直接FAIL，不能假定已稳定。记录View身份/tag、fade/state、scroll与实际elapsed；所有等待计入原5s，不重置、不另设隐藏等待预算。后续原四零差域及两display hash仍全部保留，不能把上述条件代替像素验证。不得隐藏滚动条、改动画时长、固定sleep或mask右缘。实际访问能力及新CI仍待验证，不宣称本规格已解决。

TaskFrameChecks本次窄diff已读：定位真实steam row内唯一input及labelFor关联label、原action tag/本地化文本，并复核全局target相同对象，保留原滚达/文本/输入恢复检查。与生产compactTemperatureRows保留原labelFor/input对象吻合；静态无新阻断，编译结果由根留证，实际frame CI仍待。

### scrollbar 前置门规格撤回（同日新证据优先）

SDK34 ScrollabilityCache.state 标注 @UnsupportedAppUsage(maxTargetSdk=R)，项目target35。此前依赖该字段的mandatory OFF门不再批准：不得把高风险不可访问反射设成所有页面的验收前置，不绕hidden API。本批删除settleFadingScrollbars调用及其受限state依赖，恢复原S1前hardware commit、原总5s与四零差协议；两种mapping失败后的独立只读WindowOriginDiagnostic可保留。Settings原渐隐失败仍未关闭。后续仅独立调查公开fade duration/delay、真实帧与各阶段耗时，或优化比较成本争取原预算重试；不把定时等待当稳定证明、不隐藏bar、不增加预算。
