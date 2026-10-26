# 用户反馈改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. 用户已指定 Astra 规划验收、Sol 开发，直接按该分工执行，不再询问执行方式。

**Goal:** 将机器首页、真实Tab、同图萃取、设备恢复和统一设置落成可运行原生界面，修复BOOKOO连接即计时。

**Architecture:** 单 `MainActivity` + 六根Fragment及壳内子路由，Controller持有页面View；服务持续持有硬件及萃取事实。连接恢复与UI导航分离，原业务门禁不降级。

**Tech Stack:** Kotlin、Android View、AndroidX Fragment、现有MobileService/device-session、JUnit、原生instrumentation。

## 范围与Git边界

- 来源：现有 `feature/task-first-ui`，基线 `f88f0de47078f50d3a8bd301553c69f19b1a9f6b`；它是既有PR #4的develop功能工作，不覆盖未合并PR历史或重建分支。
- 目标：将来仍PR到develop，禁止main/develop直推。用户已授权开发，主协调者在现有feature分支普通提交并推送既有PR，只收本任务文件；不合并。
- 规则：[git-workflow](../../git-workflow.md)、[任务模板](../../plans/task-template.md)。不得覆盖同工作树其他开发者改动。
- 不发版，不改包名/签名/versionCode，不安装APK、不操作真实机器。升级数据格式不变，未知写入/历史记录保留。
- 回退：按独立改动组追加revert或修复；不清数据库、不卸载、不降级APK、不重发未知写入。
- 设计：[用户反馈设计](../specs/2026-10-26-user-feedback-redesign.md)。Astra以本文件记录验收，避免另建重复跟踪文档。

## 进度

当前状态截至已推送的 `c12b2d5`，含Capture Mock UI运行40372606827证据。下方历史审查按当时事实保留；本表是当前状态，旧容器启动、旧首页布局问题已修复，不再列为未关闭阻塞。“开发完成”表示实现与静态审查完成，不等于全部软件或硬件验收通过。

| 项目 | 当前状态 | 已有证据 / 仍待验 |
|---|---|---|
| 协议计时修复 | 软件通过；实机待验 | 仅删07，精确三帧/时序、同地址两代连接回归通过；Mini/Ultra实际计时与重量待验 |
| 真Tab迁移 | 开发完成；核心原生通过 | 25169d2真实MainNavigation：六Tab20轮、720×360几何、冷启动/重建、同服务同series通过；历史来源后续修复已静态审结，710f33c复验待结果。非零历史滚动、豆列表滚动/选批/详情的Tab往返新增测试已静态通过，运行待验；二级编辑页及全部子路由重建仍按既有验收确认 |
| 机器首页常驻控制 | 开发完成；已看代表图通过 | 机器状态、紧凑双锅炉、快捷五槽及常驻控制已实现；720×360几何通过，中文浅/深首页实图已审；实际锅炉回读与设备操作待验 |
| 大图设计 | 平板代表场景面积及视觉通过 | c12b2d5有/无秤实际网格263.27023dp、图宽占页面96.97%、停止完整56dp且间隔8dp；浅/深运行及有/无秤图已审。仅此正常字体平板范围，不代表全部字号/尺寸或硬件 |
| 自动导航与重连 | 开发完成；策略及核心导航通过 | 101 session checks与手动ID策略通过，251原生manualShell通过；原机恢复、主动断开抑制、认证only静态审结，真实机器/秤恢复待验 |
| 设置弹层统一 | 开发完成；部分视觉通过 | 根页、二级及自有DatePicker接入已审；启动确认浅/深图、机器设置浅色可见部分通过；全部弹层长文本/IME/英文与放大字体尚未整体批准 |
| 整体验收 | 进行中，未整体通过 | 710f33c已推；root确认最新统一构建2分15秒、两APK资源各10897值通过。251核心原生与audio五组通过，代表图已审；最新完整CI、下列未验状态恢复和全部硬件仍待 |

未验边界：截图只批准实际看过的页面与主题；曲线库浅/深首屏通过不代表滚动恢复通过。非零历史滚动、豆列表滚动/选批/详情、二级豆编辑页及全部子路由/语言重建、全套自有弹层、真实拨杆两杯、睡眠唤醒与原设备恢复仍需对应证据。没有安装或操作真实设备，不以Mock替代硬件。

批次复选框表示该条写明的工作已完成；混合“实现+验证”的旧条目已拆开，未取得运行证据的验收项保持未勾。

## 文件所有权与交接

同一文件只设一名开发所有者；其他人给补丁建议，不并发覆盖。下面 mobile路径基准为 `mobile/src/main/kotlin/io/openhoyi/mobile/`。

| 所有者 | 文件范围 | 责任 |
|---|---|---|
| Astra | 本计划、配套spec | 设计决策、diff审查、验收结论 |
| Sol导航 | `mobile/build.gradle.kts`、`mobile/src/main/AndroidManifest.xml`、`ThemedActivity.kt`、`MainActivity.kt`、`TaskNavigationView.kt`、`TaskNavigationModel.kt`、`TaskPageFrame.kt`、新增根Fragment/Controller及壳路由；迁移六根Activity与ExtractionActivity | 真Tab、页面View迁移、壳绑定服务、saved state；拥有这些Activity迁移期间所有编辑 |
| Sol协议 | `protocol-core/.../Protocol.kt`及对应协议/会话测试 | BOOKOO初始化最小修复；不改UI |
| Sol连接（导航接口稳定后） | `MobileService.kt`、session连接恢复文件及新增纯策略测试 | 新杯ID、原设备恢复、主动断开抑制；不得重写导航文件 |
| Sol视觉/设置 | `SettingsVisualStyle.kt`、新增公共Dialog组件、设置/图表View和样式文件 | 公共组件；根Activity/Controller接入交给导航所有者或串行交接 |

首页/萃取视觉改动在导航迁移交接后由其Controller所有者接入，避免两个Sol同时改巨型Activity。任何临时兼容Activity不得成为最终导航实现。

## 批次1：真Tab与内容迁移

- [x] 增加AndroidX Fragment依赖并将主题宿主改为FragmentActivity；保持语言/主题包裹。Manifest主入口指向MainActivity。
- [x] 新增壳导航契约：`selectTab(destination)`、`openRoute(route)`、`back()`；根页稳定tag匹配destination。Tab transaction只show/hide、setPrimaryNavigationFragment、setMaxLifecycle；重复点当前Tab不重建、不写返回栈。
- [x] MainActivity只安装一个TaskPageFrame和导航/状态操作区。TaskNavigationView只接受当前目的地和回调，不凭activity.javaClass推断选中项。
- [x] 先迁AppSettings/Scale，再Curve/History/Bean，最后Home/Extraction。Fragment真正返回页面内容View；Controller可使用宿主Context/主题/系统授权，不可创建Activity、调用host.setContentView或读取宿主类名决定页面。
- [x] Controller输入为路由参数和saved state；实现createView、可见/隐藏、saveState、dispose。隐藏停UI轮询；onDestroyView清View引用、监听和弹窗。服务连接由MainActivity统一持有，页面不得因Tab切换释放BLE连接。
- [x] 返回/saved state及壳内入口迁移完成；旧顶层Activity仅重定向Main并结束，二级页不带顶层rail。
- [ ] 验证所有Tab子路由、非零历史滚动及豆列表滚动/选批/详情恢复，以及全部主题/语言重建入口；不得仅据核心导航marker勾选。
- [ ] 二级豆编辑Activity按既有编辑/重建测试确认草稿恢复。根Bean Tab为列表，没有编辑草稿，不要求不存在的根草稿Tab往返场景。
- [ ] 核对二级页：曲线编辑/导入/预览/分享，历史详情/对比/复盘，豆录入/批次/准备/台账，机器设置/秤连接/秤设置，以及仍可达的legacy页面本轮只确保入口和状态无回归，可保留原Activity但移除顶层rail；不展开所有二级Fragment迁移。
- [x] 新增并运行核心 `MainNavigationChecks.kt`：25169d2实际六Tab20轮、冷启动/重建、720×360 geometry、manualShell及同服务同series通过。
- [ ] 非零History滚动及豆列表滚动/选批/详情的Tab往返补足实际运行证据；新增测试已复用非零fixture并静态通过，仍不能据测试存在标运行通过。全部子页恢复单独验收。

批次1当前交付：真实六页和萃取在单壳，原业务门禁保留；核心原生已通过，剩余状态恢复验收按上列未勾项记录。

## 批次2：机器首页与常驻操作

- [x] HomeController改成机器状态首屏；固定双锅炉温度/开关、机器连接状态、配方与快捷启动；把准备细节移到次级展开区。
- [x] 抽取原confirmStart可复用入口供首页和萃取共用，同样执行设备/曲线/温度/秤/未知写入门禁和用户确认；不复制一套较弱校验。
- [x] 提取睡眠确认与秤连接状态到壳操作区；断开×和重连icon为独立48dp目标，禁用时保留说明。锅炉写入等待回读，不乐观标成功。
- [ ] 测试IdleTelemetry双锅炉可显示、ExtractionTelemetry蒸汽缺失显示未知；检查五槽合法边界及断连/未知写入时不可快捷启动。
- [x] 720×360首页完整控件geometry实际通过；1280×800中文浅/深实图已查看，连接、双锅炉、快捷与常驻操作可见。

## 批次3：萃取大图

- [x] 改 `TaskExtractionWorkspace.kt` 移除强制宽侧栏，主图占主要空间；原 `ExtractionOverlayView.kt`/`ProductHistoryChartRenderer.kt` 复用 `FourMetricChartProjection.kt`。
- [x] 四线同图、指标/单位/范围一排；当前实线与参考虚线区分；图例只切轴和强调，不能退回四小图或一次只见一项。
- [x] 把重复准备/摘要/说明卡压缩为一行和次级工具；停止固定可见。遵守spec尺寸、色值与无动画参数。
- [x] 保留projection/inspection单元测试及原生四指标、缺测、几何断言；本地Mock unit构建通过，浅/深运行主图与停止边界已图审。
- [ ] 四指标原生全部场景（缺测、注水复位、负重量、字体/语言）按实际CI逐项确认，不以主图截图代替。

## 批次4：新手动杯导航与连接恢复

- [x] MobileService从PassiveShotDetector开始事件公开稳定ID；MainActivity只消费新ID。先保存lastPresented再导航。重复遥测、旋转、同杯切Tab不重跳；下一杯重跳一次。
- [x] 新增纯策略测试：前台新杯→一次，切走同杯→零次，重建同杯→零次，下一杯→一次，后台已结束杯→零次。
- [x] 查明现有ReconnectPolicy和DeviceHub角色连接所有权后复用；仅补缺失的原设备地址、用户意图抑制和退避触发。禁止全设备扫描后随便连另一台。
- [x] 增加意外断连→同地址恢复；主动断开→不恢复；明确重连→解除抑制；旧generation回调→不改变新连接；蓝牙关闭/权限缺失→停重试并提示。
- [x] outbound回归：重连不能发送启动/停止/设置/睡眠/去皮/计时；原未知结果继续等待核实。机器唤醒无证据的控制帧不新增。

## 批次5：BOOKOO初始化修复

- [x] 生产只删除初始化 `030A0700000E`，保留其余三帧及500/1000/2000ms。
- [x] 协议测试精确断言三帧和时间；adapter拒绝旧07帧。
- [x] DeviceSession模拟同地址首连到READY→意外断线→同地址重连到READY，两个generation分别只写预期三帧，通知继续产出重量。不额外补stop/reset/tare。
- [x] 更新旧“每帧500ms、共四帧”测试假设，运行两模块check。
- [ ] 真实Mini/Ultra连接计时不动且重量不清零的实机验收。

## 批次6：设置与自有弹层统一

- [x] 用rg枚举 `AlertDialog.Builder`、`Dialog(`、`PopupMenu` 入口并映射到公共构建组件；统一间距/字体/颜色/宽度/动作顺序。系统权限和文件面板不接管。
- [x] 设置机器/秤/App三组保留所有现存参数和低频入口。迁移只改显示与导航，不改设置编码/值范围/回读与未知结果门禁。
- [ ] 检查代表弹窗：启动确认、睡眠、断开、锅炉编辑、删除曲线、语言/主题选择、图表检查、恢复未知结果；浅深色、长中文及英文、放大字体时正文可滚动且动作可点。
- [x] 搜索所有剩余直接构建点，逐项迁移或注明系统/外部排除理由；不能只换一个公共弹窗后宣称“全部统一”。

## 有界验证与验收

每批先跑改动模块的针对性测试；合并工作树稳定后跑一次完整本地验证，除新改动/失败外不重复扩大历史矩阵：

```bash
./gradlew --offline --console=plain :protocol-core:check :device-session:check :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest
python3 -m unittest discover -s scripts -p test_git_policy.py
python3 scripts/git_policy.py branch
```

依赖首次解析若离线缓存缺失，用联网Gradle解析正式依赖后再跑；失败不得伪装通过。Mock安装/运行只在主协调者确认既有模拟器授权范围后执行，不安装用户真机。原生检查接现有instrumentation runner，不依赖网页样张代替Android。

| 验收 | 最小场景 | 通过条件 |
|---|---|---|
| 原生视觉 | 720×360、1280×800；中文浅/深；另中文1.3字体与英文各一屏 | 无关键遮挡；首页关键控件首屏；四指标同图；停止始终可点 |
| 生命周期 | 六Tab20次、萃取中切换、一次旋转/主题重建 | 单Activity，Tab状态保留，服务不重启/断开、不重发命令 |
| 新杯导航 | 手动杯1、同杯切走、手动杯2、后台结束杯 | 首杯/第二杯各一次，同杯与已结束杯不抢回 |
| 写入安全 | 断连/未知写入、快捷启动、锅炉/睡眠待回读 | 门禁保留，待确认不当成功，重连无业务命令 |
| 连接策略 | 意外断线同地址、主动断开、手动重连 | 恢复原设备；主动断开不反连；连接代际不串线 |
| BOOKOO软件 | 首连/同地址重连精确帧与通知 | 不含07，三帧时序不变，重量通知正常 |
| 实机待验 | 真机双锅炉、拨杆两杯、机器睡眠唤醒、秤断线恢复、Mini/Ultra计时 | 必须真实设备记录；Mock和绿色CI均不替代 |

完成报告只列本次代码结果、测试实际输出、剩余实机项；未完成内容继续留在进度表，不把阶段性编译通过当成整个改版完成。

### BOOKOO最终独立审查（2026-10-26）

Astra已阅读生产与同地址断线重连测试diff，批准9个协议/会话文件作为独立修复提交。生产保留500/1000/2000ms三条安全初始化，未引入其他命令；adapter拒绝07。新测试确实经onDisconnected进入FAILED后同地址重连，generation变化，两代outbound精确为三帧，通知恢复READY。其余测试只修正旧四帧/500ms步进假设。测试结果由主协调者核对：675 JUnit零失败零错误、protocol39823 checks、device-session93 checks。软件通过，物理设备效果仍待Mini/Ultra验收。

协议修复已由主协调者独立提交为 `c48dee2`；本条只记录已完成的软件项，不代表已推送或实机通过。

## 后端恢复补充（Astra批准）

最近READY机器原地址与已有加密凭据配合；不记住从未认证成功的候选地址。机器与秤独立退避，自动尝试只走现有连接认证/订阅/只读同步入口；物理断机待现有tick进入OUTCOME_UNKNOWN后，仍受原ShotGate/matchesDevice/凭据失败门禁约束。主动断开抑制不能被foreground重置，只有明确connect/reconnect解除。普通后台关闭窗口；已有前台服务且活动/未知萃取时保持有界恢复，不在后台启动新服务。

`MobileService.manualShotPresentationId: String?` 在已确认活跃手动杯期间稳定，结束/断线为null；壳保存lastPresented再切页。手动杯断线继续abandon、finishSeries(false)，不得拼回旧图。断线后的活动片段不冒充新实体杯：展示ID抑制至观察真实IdleTelemetry，随后新的Started才重新允许新杯导航。未知结果警告与旧记录保留。

## 分批迁移审查记录

初批只读审查已向导航Sol指出：API26–29需insets兼容分支；隐藏Fragment必须释放Activity级返回回调；壳返回优先当前子详情；事务需primaryNavigationFragment与stateSaved保护；系统insets只能由壳应用一次；Home启动服务后必须确保壳能重新绑定。旧instrumentation的Activity假设需改成Main+真实Fragment，不能保留两套顶层导航仅为让旧测试通过。以上为开发中发现，尚未验收关闭。

## 第二轮独立审查（开发中，未放行）

- 后端：600s预算不能从进入App开始后永久失效；稳定READY超过10分钟后物理断链仍必须开启有界恢复episode，连续失败不重置预算。机器与秤都核查。
- Home前台入口需自然恢复明确记住的机器与秤；当前只自动启动记住秤的服务、机器bindExisting(0)不足。最近READY地址必须真正被恢复入口消费；不新增后台拉起App或跨进程静默认证。主动断开抑制仍优先。
- 环境暂停与用户主动断开不能混为同一个不可恢复suppressed：蓝牙/权限恢复应允许重新检查原设备，仍受预算、凭据及业务门禁约束。
- 真实产品大图走ProductHistoryChartRenderer；修改fallback绘图不算完成。图例按自然文本宽度采用4列或2列，自适应英文/放大字体，不能把指标挤入header滚动。无秤保留第四项缺测读数而不画零线；孤立真实采样点仍须可见。
- AppDialog旧Builder适配必须给实体底板、选项行与自定义正文统一样式；App自建DatePickerDialog不属于系统权限/文件面板排除项。长正文/长按钮/放大字体需原生量测。

当前软件证据由root核对676 JUnit零失败，但这些审查项未关闭，不将编译/JUnit通过当成整体验收通过。

### 视觉三文件复审

已从源码确认liveMode真实产品分支、四色实线与参考虚线、缺秤第四图例且无假曲线、孤立点保留、旧Builder实体底板和choice样式均已接入。仍不放行：live轴单位与最高tick使用相交的垂直预算，时间tick与独立时间单位也相交；且MetricPlot最低高度仍调用history最低高度，使live较低预算失效。下一步修正两处几何预算并接通live最低高度，随后一次真实原生布局量测及截图核验（四指标、720×360、英文/放大字体），不继续只凭静态循环判断。

导航已静态确认此前API/insets/返回/事务主要问题修正；需补show/hide情况下的图表检查弹窗离页关闭，不能仅依赖onDetachedFromWindow。旧根Activity重定向已存在，instrumentation迁移仍在进行。

### 图表最终静态复审与后端最后一项

图表5文件已核：live上缘为2行字体+12dp，时间单位合并末刻度，实时/历史最低高度分流，检查弹窗提供显式dismiss。FourMetricChartChecks读取实际renderer绘制evidence的glyphBounds与path，未发现伪断言；但其入口仍反射重定向后的ExtractionActivity，需随导航测试迁移为MainActivity/ExtractionFragment才能执行。现状态仅构建/静态通过，原生设备运行待验。

后端episode从物理失败开始、环境暂停独立、已记住机器前台入口、凭据上限、认证only写帧与旧世代隔离已静态核实。剩余阻塞：首次无记住秤时foreground(false)，首次READY未更新ReconnectPolicy.hasRememberedScale，故当前前台会话断线无法自动恢复；须只在验证READY后启用资格并补首次配对回归。

新Home测量前静态确认360dp必超高：旧header/锅炉/快捷/五槽/机器行加shell超过可用高度。导航Sol已接受紧凑两行锅炉、合并快捷与五槽、移出冗余header；交互目标仍至少48dp。quick需直达同一启动确认而非多点一次开始；shell仍需短状态/状态点/重连icon和遥测新鲜度；萃取工具须恢复被零高度辅助区隐藏的详情入口。以上均是原需求闭环，不扩大范围。

root统一构建结果：48秒成功；mobile 337项、0失败、1跳过；device-session 676项、0失败；Mock APK及Mock AndroidTest APK均构建成功。后端首次秤READY资格阻塞仍未关闭，视觉仅静态/构建通过、原生待验。设置与全部App弹窗迁移仍未完成，继续保持开发中，不能以本次构建当作完成证据。

### 二级弹层与后端最终独立审查

Astra逐diff核对后批准本批二级/辅助弹层的静态实现：公共show/styleShown不覆盖原onShow/onDismiss与自定义按钮校验；输入控件不重包，机器参数/日程校验和写入确认保留。DatePicker自有弹层接公共外观而保留内部日期控件与原回填/销毁关闭。语言选择失败提示、对话框引用禁重、Bean helper注销生命周期回调、反馈音频清理都未被覆盖。CurveShare和LegacyHistoryDetail经beanShowDialog间接覆盖。MachineSettingsEditGate与抽取前所有条件逐项一致。尚未据此宣称全部App弹层迁移完成，根Fragments由导航所有者继续接入。

首次配对秤恢复P1已闭合：仅observeScaleState(READY)将hasRememberedScale置true，未验证CONNECTING仍false；新增foreground(false)→显式连接→CONNECTING→READY→长期连接后FAILED回归通过。Sol报告device-session 101 checks红绿通过；Astra已确认对应代码与用例，不代称实机成功。

真正待验：本批阶段统一构建；Main+Fragment原生入口测试运行；720×360首页与实时图布局、英文/放大字体图例、弹窗长正文/按钮/IME、Tab状态与两杯一次导航；真实锅炉回读/睡眠唤醒/同地址重连/主动断开不反连/BOOKOO Mini与Ultra连接不计时。Mock不能替代最后一组硬件效果。

### 导航生产主体冻结复审

Astra已核实：Home紧凑双锅炉与快捷五槽上移，低频内容在下方；共享confirmStart路径已接快捷入口；壳通过稳定manualShotPresentationId在前台记住ID后跳转，saved state保存去重ID；主导航返回只询问当前Fragment，不再保留隐藏页Activity级返回拦截。主要根Fragment在onPause停止轮询、移除服务观察、关闭自有弹层，萃取检查图显式dismiss。睡眠按钮使用LiveTelemetry新鲜度，不把未知当清醒。

仍需修的有界项：ExtractionFragment延迟绑定时的一次性启动确认请求离页必须取消；FAILED断线态显示重连icon而非仅×；HistoryDetailContent/beanShowDialog在Main宿主下创建的辅助弹窗要登记壳离页关闭，不能只等Activity销毁。已交导航Sol，不修改生产代码。

### CI与验收状态边界

远端 `c48dee2` 的必需branch-policy、verify、lifecycle、upgrade均pass。该提交尚未包含本次未推送的新导航主体；旧可选视觉检查仍有失败，不能声称“全部CI绿色”。证据位于 `build/task-first-evidence/feedback-redesign-2026-10-26/ci-*-failed.log`：M3焦点/选中/滚动，M4的424/1154/1082 IME，scale Window/display同步，以及form 12项失败。它们作为现有基线状态保留，不据此扩大旧审美矩阵。

本轮有限布局验收仍锁定当前Main+Fragment：720×360与1280×800横屏首页/萃取、英文或放大字体代表场景、关键弹层/IME、Tab状态与手动两杯导航。原生布局尚未执行，静态紧凑重排不等于一屏目标已通过。

### 2026-10-27最终静态收口

已实际确认三项中的核心修复：ExtractionFragment.onPause及普通萃取入口清除未消费启动确认；FAILED现在显示重连icon；HistoryDetailContent三条弹层与Main宿主beanShowDialog均登记壳清理，普通Activity仍保留原销毁清理。最后UNSUPPORTED分支已补：MainActivity.reconnectScale先openScale并return，非READY进入ScaleConnectionActivity，不再尝试旧设备。root已读取对应分支确认；本轮3+1项静态阻塞全部闭合。

抽查MainPageTestHost、MainNavigationChecks、FourMetricChartChecks：新测试进入真实MainActivity，校验primaryNavigationFragment/RESUMED/View attached、零Tab返回栈；萃取测试从实际ExtractionFragment取chart。图表仍核glyph边界/缺秤无假线；导航仍核搜索状态、同服务与同series、手动ID去重。History非零滚动仅在已有非零fixture时检查，不能据静态检查声称全部滚动恢复已验证。测试未运行，不以测试代码存在当通过。

root最新统一构建1分21秒成功：protocol39823 checks、device101 checks、mobile337 JUnit零失败1跳过、session676 JUnit零失败、Mock与Debug APK成功。最后BeanScreenUi登记一行在本次构建之后增加，仍待最终AndroidTest重编译。远端必需CI通过仍对应c48dee2，新导航尚未推送；当前生产静态收口不代表新源码CI或原生布局通过。有限原生与实机待验范围保持不变。

最终冻结记录：生产静态复核已收口，不再扩大审查范围。三组测试冻结后由root统一编译并推送CI；最新BeanScreenUi清理登记和UNSUPPORTED分支纳入该次构建。当前仍只确认上述已发生的本地构建与静态结果，最终源码构建、新提交CI、原生布局/生命周期运行和真实硬件验收均按各自证据更新，不提前标通过。

### 最终统一构建记录（2026-10-27）

root已核对session38861退出码0，`final-integration.log`末尾BUILD SUCCESSFUL，耗时2分34秒，167 tasks（45 executed）。覆盖protocol/session checks、mobile Mock unit、Mock/Debug/AndroidTest APK及lintDebug：协议39823 checks、session101 checks；mobile337 JUnit零失败1跳过、session676 JUnit零失败。该结果覆盖最后冻结的生产与测试改动，取代前文“最终重编译待验”状态。

生产静态阻塞全部闭合，本地最终统一构建通过。新提交CI、原生布局/生命周期实际运行及真实硬件仍待验；远端c48dee2的既有CI不能替代新提交验证，旧可选视觉基线失败记录仍保留。本计划本轮冻结，后续仅据实际验收证据更新。

### c729ab9原生CI启动阻塞（2026-10-27）

新提交c729ab9的lifecycle运行40356964635与scale运行40356964624暴露产品启动崩溃，不属于基础设施失败。Astra已读取`build/task-first-evidence/feedback-redesign-2026-10-26/run-40356964635/mock-lifecycle/errors.txt`：`No view found for id 0x1`，HomeFragment在FragmentActivity.onStart创建View时找不到容器；scale运行同类问题由root确认。

静态根因：MainActivity虽然在onCreate调用setContentView，但TaskPageFrame.pageContent直到首次onMeasure才挂入frame；FragmentActivity.onStart可早于首次测量，因此容器尚非Activity视图树后代。导航Sol负责修复，Astra复审：onCreate提交Fragment前容器必须已附着且可按ID找到；重建恢复稳定容器ID；布局调整保留同一容器。不得以推迟Fragment生命周期或放宽测试绕过。此前本地构建结果仍有效，但“生产静态全部闭合”已被实际运行证据推翻，原生验收目前明确未通过。硬件仍待验。

### 启动容器修复独立复审

Astra实际读取MainActivity/TaskPageFrame两文件diff，静态批准本次根因修复：TaskPageFrame.init立即将pageContent与navigation挂入初始BOTTOM结构，configuredMode与之对应；MainActivity.setContentView后、showCurrent前检查findViewById(pageId)与实际container身份相同。onMeasure切换布局仍搬移同一pageContent/container对象，STATE_PAGE_ID保存恢复路径未变。未推迟Fragment生命周期，未放松测试。此结论仅限静态修复正确；最小冷启动与重建容器断言正在补齐，统一构建和真实CI重跑仍待，不据此将原生启动标通过。

另据root核对，c729ab9的verify失败为`compiled string keys differ from source`资源键校验，并非Gradle或基础设施失败；已交所有者定位Fragment依赖资源键，保留现有门禁。该问题与启动崩溃分开跟踪，待修复证据关闭。

### APK资源键修复静态审结

root查实新增键仅为AndroidX的`androidx_startup`与`status_bar_notification_info_overflow`，App缺键为0。Astra实际复核`localization/verify_apk_resources.py`及`localization/tests/test_apk_resources.py`：仅对source之外这两个精确键接受依赖默认值`androidx.startup`、`999+`；startup不得包含语言配置，overflow可保留依赖自带非空翻译。App缺键和其他额外键仍失败，App全部source键及七语言逐值字典严格相等检查未改；同名若进入App source也适用原严格校验。新增用例拒绝未知键及错误依赖默认值，原缺语言/错值检查保留。静态审查通过，root独立22 tests通过。

本批文档冻结。修复后Gradle构建仍在运行，资源校验真实APK/新提交CI、冷启动与重建原生运行、硬件仍待对应证据，不以本次脚本静态批准代替。

### 启动容器与依赖资源修复构建核对

主协调者核对修复构建 exit 0，2分13秒，Mock/Debug/AndroidTest APK及lintDebug通过（container-fix-build.log）。独立运行localization全部22项测试通过；实际两APK资源校验通过，literalRoundTrips=6，Alpha/Mock各matchedStrings=10873。生产两文件及资源脚本小diff均获Astra静态批准。新增Home/Scale冷启动与重建容器ID/附着/活动Fragment回归仍待新提交原生CI；本地构建不证明启动崩溃已在设备闭合。

### 701eda0首次真实首页图审：未通过

Astra已用view_image查看`build/task-first-evidence/feedback-redesign-2026-10-26/run-40359050909/mock-ui-screenshots/home-light.png`。页面仍以“准备冲煮”为标题，已连接时占一整行“扫描并连接设备”；锅炉开关横跨大卡，五槽仅数字，配方未选提示重复，下半旧准备区包含模式未知/空间不足/阶段示意等冗余，rail秤文字实际截为“电…”。与机器首页和简洁常驻控制目标不符，不批准视觉验收。

已交导航Sol的有界修正：所有横屏统一紧凑机器页，不只对短屏特判；机器标题与单行连接状态，READY时把设备选择收为小入口；双锅炉两行卡，温度与至少48dp局部开关同排，第二行明确目标温度；单一配方启动区，五槽显示真实配方短名或未配置；旧准备/重复配方块移入仍可达的次级入口，保留业务。窄rail使用秤图标和独立状态点，完整状态保留可访问描述及点击入口，横向壳才显示短文字；睡眠/断开/重连触控目标不缩小。

另已读取`run-40359050840/mock-lifecycle/errors.txt`：720×360快捷按钮view高112px而可见仅110px，属于真实裁切；必须减少实际内容和间距，保留完整可见与48dp断言，不加容差。修后重跑既有geometry与同场景截图；不扩旧视觉矩阵。启动容器崩溃已通过当前实际运行，当前失败是新的布局问题。隐藏二级rail为0×0的旧测试语义由测试所有者修正，不与真实首屏裁切混淆。硬件待验状态不变。

### 首页图审后的生产修订静态复核

Astra已读HomeFragment、TaskShellStatusView及新增文案diff。旧标题/重复设备卡撤下，机器状态行、紧凑双锅炉和目标标签、单行快捷五槽已接入；五槽名称取现有PresetSlots解析后的本地曲线名，缺失显示未配置，不扩大协议。旧workspace移入仍可打开的devicePanel，保留原业务回调与onPause弹层清理。锅炉继续confirmBoilerChange及MachineSettingsEditGate，快捷/五槽继续共享confirmStart。公共48dp触控下限、主快捷56dp及固定停止区未削弱。窄rail仅秤符号+状态点，状态改变会重新着色，完整状态仍在可访问描述。

720×360常态静态预算约212dp：content上下16、机器行48、两间距16、锅炉约76、快捷56；相比上次实际236dp页面viewport有余量。此估算不替代测量，长名称/温度、告警和放大字体仍按既有场景核验。本批实现静态通过；统一构建及新截图/geometry实际复跑待完成，首页视觉仍未批准。

### 视觉fixture迁移有限审查

新增磁盘例外仅为files根下精确`profileInstalled`，四套均另记其前后SHA；原业务文件哈希相等与既有偏好恢复检查未放宽。Main单一Mock服务检查保留IDLE、hub为空、未扫描及设置/日程/去皮/杯数/预热未写；Scale先启动受控Main再释放该宿主绑定并停止服务，不在停止后重启Main。二级rail改断言隐藏并检查真实可见48dp控件，顶层完整可见断言保留。

仍有两处明确待修，已交root：ProductVisualMigrationChecks.homeRoutes在关闭详情后调用已脱离窗口的brewButton.performClick，须改成真实可见入口并核完整可见，不以隐藏控件调用证明路由；Curve/Legacy/Settings只读服务helper漏检Mock.sleeping，sleepNow只改该字段而不会触发现有写入标志，须补初始睡眠状态不变检查。仅修此两处，不扩大旧视觉测试矩阵；本批暂不批准全部隔离门禁闭合。

### 首页修订构建与fixture补丁进展

root确认Home-layout-build退出码0，2分33秒；mobile Mock unit337项零失败1跳过，Mock/Debug/AndroidTest APK及lint通过，实际两APK资源各10897值校验通过。此构建之后的最小测试补丁仍需AndroidTest重编译。Astra已确认homeRoutes保留打开的details，并在点击前断言同dialog视图树、isShown及完整可见，隐藏按钮调用问题静态闭合。三套sleeping检查待补丁落盘复核；新截图与原生CI仍待，不提前标首页视觉通过。

### fixture两项静态收口与冻结

Astra重新读取当前落盘源码，已确认CurveVisualMigrationChecks:208、LegacyVisualMigrationChecks:379、SettingsVisualMigrationChecks:214的只读服务条件均包含`!mock.isSleeping`。连同前述真实dialog内按钮可见性检查，本轮两项审查问题均静态闭合。root正在执行仅assembleMockAndroidTest重编译（session11735），结果待其确认。计划本轮冻结；实际原生运行、新截图及硬件仍待验，不将静态收口当视觉通过。

### 首页实图整改本地验证

主协调者已核对：home-layout-build.log exit0，2分33秒，Mobile Mock337 tests/0fail/1skip，Mock/Debug/AndroidTest APK与lint通过。两APK资源逐值校验各10897项。Astra要求的可见按钮与sleeping守卫补齐后，home-test-final-build.log再次assembleMockAndroidTest exit0，33秒。四份相关脚本bash -n及diff检查通过。此为新提交前软件构建证据；720×360首屏、新截图、原生服务隔离与全部硬件效果仍待验证，不以估算212dp代替实测。

### 25169d2新版两张真实主图验收

Astra已用view_image实际查看`run-40362904894/mock-ui-screenshots/home-light.png`及`extraction-running-light.png`：首页机器状态、紧凑双锅炉与目标、局部开关、快捷五槽及rail状态符号可见，旧准备重复块消失；萃取四线同图面积足够，四色图例/独立量程/末时间刻度单位清楚，停止完整可见。批准这两张中文浅色常态截图的本轮视觉结果；不泛化到其他语言、字体、全部弹层或真实设备。

root核对25169d2真实MainNavigation marker通过：六Tab二十轮、720×360 geometry、冷启动/重建、manualShell、同服务同series；audio五组通过。全CI尚未通过，HubCleanup旧coffee反射修订中；AppSettings稳定tag、TaskFrame活动滚动及M5History只读owner fixture小补丁待后续统一验证。

LanguageActiveCupChecks:172的温度裁切断言已定位旧视图：TaskRecipeSummary属于隐藏secondaryBody；真实“工具→概览”把recipeSummary.text展示为AlertDialog正文，不会搬移原number/unit视图。已要求通过真实入口检查实际展示温度/单位完整可见并保留同杯/快照/语言条件；不能放宽可见断言或继续查隐藏旧view。若坚持独立数字单位baseline，需对实际展示的TaskRecipeSummary组件验，而不能伪造视图可见。本项修复及CI结果仍待，硬件验收仍待。

同一artifact补看`extraction-confirm-light.png`与`settings-light.png`：确认弹窗实体圆角底板、标题、四行参数/模拟说明、取消和开始动作均完整可读，无观察到的遮挡；批准该中文浅色确认弹窗代表图。机器设置浅色图的回读状态/设置反馈/分类标签层次清晰，当前温度输入、单位、范围和提交按钮可读，保留“等待机器回读确认”语义；批准该截图可见部分。下方设置内容为滚动区域，不据截图批准未展示部分、IME或全部设置弹窗。

Language检查采用当前真实工具→概览文本契约，导航Sol已确认撤回临时搬移TaskRecipeSummary的草稿，生产无此改动；测试应检查真实弹层全文、同杯/快照/停止/语言条件。其余标签、滚动、Hub及语言旧UI断言修复仍待CI。

### 旧结构断言适配最终有限复核

Astra已实际读本批diff：AppSettings仅改稳定语义tag，原文本和样式不变；TaskFrame从当前Settings Fragment的明确scroll取证，增加isShown/attached，48dp与其他几何断言保留。test-only NativeHubTestOwners获取真实coffee$delegate.value和scale对象，13组同类读取为机械替换，原停止、写帧、未知结果与清理条件未删。M5 History明确单一IDLE Mock、hub为空/不扫描/不睡眠/无业务写，绑定隔离logs/history身份；停服务、清空回调和observer后才恢复原delegate。M5及Product仅额外排除files根精确profileInstalled并另存前后SHA，其余业务哈希不变。

LanguageActiveCupChecks已改为真实点击工具→概览，读取实际AlertDialog message，核本杯快照温度与单位、将目标文本行滚动进入真实可见区域并执行全文textFits；保留同杯ID/快照/语言/偏好不变及点击停止立即ENDED_OBSERVED断言。不再检查隐藏的旧独立number/unit节点。以上适配静态通过，不代表原生运行已通过；待root统一构建与CI结果。本轮计划冻结，不扩大视觉矩阵。

### 原生契约适配构建核对

主协调者核对native-contract-build.log exit0，2分25秒：Mobile Mock单测、Mock/Debug/AndroidTest APK、lintDebug均通过；两实际APK资源各10897值核对通过。Astra已静态复核本批owner/活动Fragment/真实弹窗入口与只读fixture修复。此前25169d2真实MainNavigation、前后台edges及5组音频通过，核心浅色4图已批准；整条lifecycle及其它原生流程仍待本次修复重跑，不能把分项成功冒充全绿。

### 已有中文深色及曲线库代表图复核

Astra已实际view_image查看同一`run-40362904894/mock-ui-screenshots`中的home-dark、extraction-running-dark、extraction-confirm-dark、curves-light、curves-dark五张现有截图。深色首页的温度/目标/机器状态/快捷入口可读，层次与浅色一致；实时图四色线和图例清楚，量程、单位及固定停止完整可见；深色确认弹窗底板与遮罩有区分，标题、参数、正文和两个动作完整。批准这三张中文深色代表图的局部视觉结果。

曲线库浅/深两图的搜索、排序、筛选、数量、可见列表条目与右侧详情占位布局清晰，字重、行间距、缩略图及分隔线可读，未见可见完整行的文字遮挡。筛选条右缘和列表下缘显示的是滚动区域边界；截图不能证明滚动行为、末项可达或滚动位置恢复，相关原生运行证据仍单独验收。批准这两张当前首屏的局部外观，不批准未展示的选中详情/滚动状态，不新增风格调整。

root确认统一构建2分25秒通过，39e4eac已推送完整CI复验；本次视觉判断沿用25169d2现有artifact，后续唯一相关生产改动为不改变外观的AppSettings info tags。新提交完整CI及硬件结果仍待，未提交或推送本文档。

### 历史来源路由与诊断滚动目标修复

Astra已独立读取MainActivity/HistoryFragment/HistoryActivity/LegacyHistoryActivity小diff：requestedSource优先于onCreateView的saved/default并消费一次，消除onCreate阶段请求被默认false覆盖；已有View切来源先记列表并退出旧详情。Legacy入口明确传journal/local双向来源，普通无source Tab不改当前来源；Main重建不重复消费旧intent，新onNewIntent仍消费明确来源。既有LegacyVisual强断言未改，静态批准此根因修复，真实路由仍待CI复跑。

TaskFrame诊断目标改为当前真实底部export按钮，并同时核诊断组标题；目标需可点击/可用，原完整可见与滚动恢复条件保留。没有通过重复或已移顶的机器设置文字替代底部验证，未改停止或业务逻辑。本批仅静态结论，构建与原生结果待root记录。

### 最后两项测试隔离与坐标修复审结

Astra已读ServiceWriteDisconnectionChecks小diff及上下文：strict Context仅新增随机fixture的devices偏好映射；实际允许同机replacement时精确要求仅coffeeAutoSuppressed=false，其余被拒场景仍保持原空集。真实devices纳入finally逐值不变检查，系统服务/权限/BLE/组件零调用断言与原写入/停止条件未动。

LanguageActiveCupChecks将getGlobalVisibleRect的root坐标经root屏幕位置和Point offset换算到screen；目标采用实际layout首末行范围、padding和scroll构造屏幕框。保留并加强offset与getLocationOnScreen一致、整行contains、全文textFits，双postOnAnimation等待后检查且写数字诊断，没有增加容差或删可见性。静态批准坐标系修正，真实CI仍须验证；先前root/window矩形与屏幕origin混比可解释旧失败，但不据此跳过复跑。

root正在统一history-route-build（session14116）。本轮计划冻结；仅确认上述静态门禁未放宽，不声明本次构建或新CI已经通过。

### 本地复验（2026-10-27，历史路由与测试坐标批次）

Root 复核 `history-route-build.log`：Mock 单测、Mock/Debug/AndroidTest APK、lintDebug 统一构建成功，耗时 2 分 15 秒。实际 Alpha/Mock APK 资源各 10897 个值、6 个 literal round trips 校验通过；diff 检查无错误。Astra 已静态批准本批，原生运行与截图仍以新提交 CI 为准。

### 非零列表真实Tab往返测试静态审结

Astra已读SingleScreenListStateChecks/MainNavigationChecks小diff：复用既有40项fixture与真实swipe，History要求非零firstVisiblePosition，Bean要求scrollY>0；新增tabReturn通过可见可用可点击的实际task-nav切到首页再返回，waitForIdleSync与当前真实Fragment检查同实例。History保留showJournal与完整position/offset Anchor，Bean保留滚动值、selectedBatchId、detailRequested和详情metadata。等待沿用有界状态轮询，不新增盲sleep，原业务与偏好清理不变。静态通过，原生运行待CI。

纠正概念：根Bean是库存列表，Tab状态为滚动、选批与详情；编辑草稿属于独立二级Activity，按既有重建验收单列，不再要求根Bean草稿切Tab。本轮仅改计划，冻结待运行证据。

### font_scale恢复与截图同步问题记录

Astra已读check_settings_visual_migration.sh小diff：仅原先缺席的font_scale最多五次重复原delete；原存在时仍单次put原值。每次记录存在性和值，最终严格after==before；恢复失败不会把原非零退出码改成功。静态批准有界恢复重试，结论限最后一次实际读取，不宣称Android之后永不重建默认值。

Settings/Scale反复出现PixelCopy S1与Window层不同步，窗口几何及显示D1/D2一致并不能证明像素属于同一提交帧；现有5秒守卫失败应保留。Main每250ms shellStatus.present可能重复设置未变文本只是未证实假设，不足以放宽截图接受条件、增加任意等待或直接改生产。建议限原失败场景记录捕获/提交帧与shell实际变更计数时间，先验证因果；若证实无意义重复绘制，再单点去重并复跑原场景。当前仅记录问题，不新增矩阵或标截图同步通过。

### LayoutHost与严格磁盘差异诊断复核

Astra已读LanguageUiLayoutChecks/StrictVisualSampleChecks两diff，静态通过。Language使用Main同款TaskPageFrame.host，明确六Tab顺序、BREW选中和不出现根壳冗余tools菜单；原宽度/字体矩阵、精确尺寸、48dp、textFits及内部边界检查保留。被移除的是二级install宿主下不适用的旧工具菜单条件，不削根导航断言。既有窄宽布局回归不升级为本轮产品竖屏支持目标。

Strict只在原storage哈希不等时向外部证据目录写added/removed/changed路径及前后SHA，storage收集范围不变，未添加profileInstalled白名单；原严格相等失败和service owner检查仍在，诊断写入失败也不会吞原数据不变失败。此补丁提供定位证据，不把尚未解释的磁盘差异当成功。计划冻结，实际运行待CI。

### 实际绘图区面积复核：尚未达标

Astra实际view_image查看`run-40368846679/extraction-running-light.png`。原测试chartBottom>stopTop与当前固定停止在图下方的设计冲突，应改为两者不重叠且停止完整可见；本次约8dp间隔合理。但chart外框或MetricPlot高度不等于renderer实际plotBounds；Sol测得扣轴后约173–184dp，低于spec常见平板260dp目标。此前代表图批准仅适用于可读性/外观，不代表该数值面积已验收，本项现明确待修。

最小空间调整交导航Sol：运行态标题/模拟说明/安全提示压为约48dp紧凑行，时间与秤读数用约56–64dp同排；压力/注水/温度已由四指标图例展示，避免重复大数值支持区，节省约70–90dp归实际网格。四指标、单位/量程、轴文字安全间距及至少48dp停止不能删减。正常字体常见平板按真实plotBounds高至少260dp验收。720×360原180dp目标与安全优先保留，若预算不足先报告真实几何，不静默改规格或缩触控。

测试应先输出viewport、density、chart外框、MetricPlot、renderer.plotBounds和stop几何，再断言plotBounds/density及chart-stop不重叠。不能只改旧断言使通过，不能按外框高度宣称图面积达标。

### 图面积几何测试首轮复核

LanguageActiveCupChecks已先输出geometry，记录pageContent宽、真实plotBounds高度/density，要求图宽至少页面70%，仅常见平板>=1000×600dp、fontScale=1要求网格>=260dp，未将260套用短屏。但新非重叠断言仍将chart.getLocationOnScreen与stop.getGlobalVisibleRect混比，暂不批准；已交测试Sol统一stop屏幕坐标，global矩形仅用于完整宽高校验，同时建议核真实网格在plot及祖先可见范围内。生产图面积仍待调整，不能只修测试取得通过。

### 主图压缩生产与最终几何检查复核

生产ExtractionFragment/TaskLiveReadouts首版压缩仍有阻塞：HoyiUi.header内标签默认在纵向容器生成MATCH_PARENT宽，单改横排会使标题占满、副标题挤出；须运行态显式title WRAP_CONTENT、subtitle余量weight并在非运行态恢复。理论回收约80dp，原较低173dp网格仅到约253dp，尚不能保证260；需再去除约8dp无效间距或按实际可用预算分配，不动字号、四图例、轴文字安全距离及停止。已交导航Sol，当前构建不作为面积通过。

测试最终版已重新读取并静态批准：chart与stop均以getLocationOnScreen坐标比较不重叠；plot的global可见矩形映射到screen，并验证映射与真实plotLocation一致；axesInsidePlot及可见RectF包含完整dataPlotScreen避免祖先裁切漏检。geometry先落盘，再执行真实plotBounds>=260dp（平板正常字体）、宽度>=页面70%和停止完整可见条件。未削阈值，实际面积仍须修后CI测量与截图确认。

### 主图压缩最终生产静态收口

已读最终ExtractionFragment/TaskLiveReadouts：运行态title WRAP_CONTENT、subtitle宽0/weight1，退出运行态恢复MATCH_PARENT/weight0；chartHost运行态上边距0、非运行态恢复8dp。GONE支持数值行不再参与测量或布局，四指标图例、原值/单位、字号、renderer轴间距和固定停止保持。两项静态阻塞闭合。额外回收8dp后最低预算约261dp，只是理论估算，仍须真实plotBounds>=260dp及可见边界/截图验证。

root确认上一版统一构建2分30秒通过，但最后标题宽度与间距补丁在其后，待最终构建与单批推送CI；本轮计划冻结，绘图区面积未提前标通过。

### 绘图区最终补丁本地复验（2026-10-27）

Root确认 `live-plot-final-build.log` 完整Mock单测、Mock/Debug/AndroidTest APK与lintDebug构建成功，2分8秒；实际两APK各10897资源值及6 literal round trips通过。Astra最终生产和测试静态复审通过。260dp为真实数据plot门槛，尚待新提交CI；未因编译成功批准图面积。710f33c的verify/upgrade/branch-policy通过，lifecycle根frame身份问题已针对性修正；Capture越过概览温度后发现旧交叠断言及真实图面积不足，本批同时修测试和生产。Settings/Scale像素帧同步仍未闭合，Strict样张磁盘差异新增逐文件诊断而非白名单。

### Settings固定像素差证据的限定结论

据Sol对两run retained PNG的精确比较：差异8038坐标一致，跨run对应图像字节相同；alpha一致，RGB最大差3（6510像素差1、1401差2、127差3），范围x35..1956/y17..1399；导航1866、正文6172，其中shell281。一次S1=W1精确相同，W2随后呈相同8038差异。Astra读取run-40368854378与run-40368846485的window-frame-sync，确认对应surface-before/surface-after文件SHA跨run重复及各run D1/D2一致，失败状态仍为捕获截止时间耗尽。

这些事实支持优先定位取证/合成路径差异，而不支持把250ms shell刷新认作主因；Sol已撤回该猜测。尚不足以唯一排除产品、渲染驱动或合成时序影响，也不能把retained失败attempt当成功样本。原5秒及精确像素门禁不改，不基于此扩大生产重绘整改。生产c12b2d5已冻结推送CI中；本项只记录定位证据与限制，不宣称视觉取证通过。

### Strict唯一profileInstalled差异的证据与最小方案

Astra独立读取run-40372606813的`storage-diff-preparation-confirmed-four-stage.json`：added/removed均空，唯一changed为files根直接`profileInstalled`；SHA从d20889a3238789b8f254f507c5c54b7c5c508bdd6f447d05d9c7eaa24f3b7174变为3c708052c56c6044f35dd56fc86c3bde2b1727cc0533867dc1937bf0224a784f，未报告其他files/prefs/db变化。

批准与既有套件一致的最小方案：仅该精确路径从business hash map单列，另记marker前后SHA（缺席为null）；其余所有文件严格相等、原服务与失败门禁不变，不删除marker、不按basename递归扩大排除。此为有实际差异证据后的方案批准，实施diff及TaskFrame真实重跑仍待，不宣称套件通过。

### Strict精确marker实现静态闭合

Astra已读实施diff：storage排除条件同时要求name=files、parentFile=root、name=profileInstalled；子目录同名文件及prefs/db仍受检。每fixture启动前、finish并idle后分别读marker SHA/null，原断言通过后附入对应manifest。原业务哈希严格相等、service owner及storage-diff失败诊断保持；失败不转成功，不删除marker。实施静态通过，文档冻结，实际套件重跑待验。

### c12b2d5真实主图面积验收闭合

Capture Mock UI运行40372606827成功。Astra实际view_image查看extraction-running-light/dark及active-cup-visual/with-scale、no-scale/window.png，并读取两份geometry：1138×623dp、fontScale1、density2.25；真实dataPlot高592.35803px=263.27023dp，chartWidthFractionOfPage=0.9697479。axesInsidePlot、dataPlotFullyVisible、plotScreenMappingMatches、stopFullyVisible均true；chart底1303px、stop顶1321px，间距18px=8dp，stop高126px=56dp。

批准此平板正常字体有秤/无秤的绘图区面积与真实可见边界。浅深运行图紧凑标题/数值/四图例可读，网格明显增高；无秤明确“未记录该时刻的秤读数”，不画绿色假线，主数值使用累计注水。批准所看四图范围，不泛化到其他字体/尺寸或硬件，也不概括全部CI通过。旧173–184dp面积阻塞在本场景已被实际证据关闭。

同时静态复核LanguageChartChecks最小diff：仅native ShotChartView像素期望独立明写浅#176BD1/深#80B4FF，与已审live/fallback色板一致，不从被测renderer读取期望；Legacy/Stage旧色及实际Canvas精确像素、meanY上升>20dp、语言/浅深矩阵保持。该测试修订运行待验，文档冻结。

root另核对ACTIVE_CUP_VISUAL_PASSED marker与上述几何一致，最终小测试补丁构建35秒通过；待其提交推送复跑，生产c12b2d5未改变。

### 通知阶段宿主适配首轮审查

BrewAudioInstrumentation改为activeCup清理后新建真实Main并由同一宿主运行三通知suite，方向正确；顶层Mock包门禁保留，清理仅作用同Mock服务组件，无生产变更。但首版finally清理异常可能覆盖原suite失败，暂不批准：已要求保存原Throwable，清理错误附为suppressed，无原失败时仍抛清理错误，finish与idle各自尽力执行。通知断言不改，待此小项闭合。

### 通知阶段宿主适配最终静态闭合

Astra复核最终diff：三通知suite共享新建的真实Main宿主；stage异常原样抛出并保存，shutdown/stopService、finish、waitForIdleSync分别尽力执行，主线程清理异常捕获回传。已有stage失败时清理错误附为suppressed；无stage失败时仍抛清理首错并附其余错误，不掩盖原失败或吞清理失败。顶层Mock模式及精确Mock包校验保留，清理仅同Mock服务组件，原context/activeCup顺序、通知权限和无BLE断言未改，生产未变。此前唯一静态阻塞闭合。

已读取notification-host-final-build.log：assembleMockAndroidTest BUILD SUCCESSFUL in 34s（74 tasks，4 executed）。本项静态审查及测试APK构建通过，通知阶段实际CI运行仍待；不概括全部CI或硬件通过。计划冻结。

### Product首页详情弹层就绪等待静态审结

独立读取run-40374718381的details-initial-frame-sync.json：失败发生在Explicit Window就绪校验，elapsedMs=109、单次attempt的budget为空、尚无captureArtifacts；5秒取证协议未进入实际帧捕获。此次ProductVisualMigrationChecks仅加强homeDetails原10秒有界条件轮询：真实dialog显示后，decor需attached、有效token、hardware、focus、laidOut、无layout request、正尺寸及默认display。取证调用使用同dialog的decor，主线程业务入口既有路径未改。没有新增固定等待，也没有改5秒截图期限、身份/祖先/变换校验、像素相等或数据/服务门禁。

该最小diff静态通过；准备条件仍可能在下一帧变化，最终由原严格截图校验决定，不能据此宣称Product取证已通过。root另报告StrictVisual四组storageUnchanged=true已通过，此状态与Product弹层待复验分开记录。计划冻结。

### 缺秤图例旧契约修订静态审结

ChartLegendChecks末尾旧断言要求WEIGHT节点消失，与已批准的四指标缺测提示设计冲突。Astra读取新diff：四图例顺序保留；重量按钮禁用且未选中，文本/无障碍描述含g及当前语言缺秤文案并检查textFits；投影不含WEIGHT，实际plot Canvas非空path必须从原四条降为三条。原初始四线、单位/范围、点击焦点、全部语言/宽度/浅深矩阵保持，未以隐藏节点或伪线通过。该修订静态批准，原生运行待复验。

有限检查后续三通知suite未发现依赖旧图表或root可见结构的明确断言，仍使用真实Main的服务、系统通知文本/权限/意图及Mock隔离门禁；这不是通知运行通过结论。root报告b44真实runner已通过核心生命周期、五组audio及language context/activeCup/charts阶段，随后在旧缺秤节点断言失败；只记录已走过范围，不概括整个lifecycle或全部CI通过。与Product弹层准备等待小修一起待提交复验，文档冻结。

Root本地复验：`missing-scale-dialog-test-build.log` AndroidTest构建42秒成功，覆盖Product弹窗就绪与ChartLegend缺秤契约小修；Astra静态通过，实际新CI仍待。生产未改变。

### ChartLegend单轴契约方案审查

dfb0f3a运行到historical zh-Hans/540/false后，旧同帧四轴断言失败。Astra通读现有函数、非embedded ShotChartView实际轴路径及规格：各指标独立映射，左轴仅显示选中量纲，四图例持续提供各自范围，点击不隐藏其他曲线。批准最小替换方案：同时移除宽屏同帧四单位要求及compact图例分支暗含的同帧全部单位/端点要求；复用原点击循环，每次实际点击/layout后重绘TextBoundsCanvas，按独立fixture字面值核选中单位及两端刻度、唯一选中态，并再次实际Canvas计数四条非空path。四图例完整范围/无障碍/48dp/textFits/不重叠与缺秤三path保留。当前非embedded图例所有宽度均fullTitle，可将旧name-only容许收紧为全部宽度完整范围。

未发现本函数其他确定的旧显示契约；不改生产、不新增工具或测试矩阵。此次仅方案批准，实施diff与真实运行仍待，不能标chart-legends或整个lifecycle通过。文档冻结。

### ChartLegend选轴实施静态闭合

Astra复核冻结diff：四个量纲端点以独立字面常量定义，所有宽度均严格检查图例完整当前值/范围；两处旧同帧多轴断言已替换。原四次真实performClick/layout分别校验唯一selected，新建TextBoundsCanvas捕获实际plot绘制，轴单位列表必须恰为当前单位且包含对应两端点；每次实际drawPath仍严格四条。初始Canvas文字边界、四线、图例textFits/触控尺寸/非重叠以及缺秤三线检查均保留。未扩语言/尺寸/主题矩阵，未改生产或引入新测试工具。实施静态批准，最终编译和CI原生运行待root执行；文档冻结。

Root确认`selected-axis-test-build.log` AndroidTest构建45秒成功；仅图例测试契约小修，生产保持c12b2d5，原生最新完整CI仍待。

### 240×220英文裸图组件高度失败：限定诊断

4e693d1的ChartLegendChecks在en/240/false报usable plot missing，原条件合并VISIBLE及plot高度至少96dp，artifact未分别记录实际值。Astra核对当前路径为ShotChartView(context)的visual=null非embedded图：minimumAxisHeight=ceil(fontMetrics行高×4.5+32dp)，header最多占总高减此动态最低高；高度不足最低高加48dp时才进入compact并隐藏图。96dp并非此路径的显式预算，本轮规格180/260dp指整页真实dataPlot，与此240×220裸组件不能直接等同。英文标题增高可能让图收至动态最低高，但现有证据不足以断定实际失败分支。

下一步仅在原断言补density/fontScale、父子measure/layout高、header/legend/explanation及scroll、plot可见性/高度、动态minimum/fontMetrics/compact诊断；保留原96dp、220dpfixture及既有矩阵，先得到真实数值。未批准降低阈值、增大fixture或修改生产，不将非目标英文窄组件扩展为新产品范围。文档冻结。

### 裸图高度失败诊断补丁静态闭合

Astra读取ChartLegendChecks最终小diff：仅扩展原check的惰性失败message，VISIBLE且>=96dp条件、240×220fixture及后续绘制门禁未改。记录density/scaledDensity/fontScale、chart/overlay/plot/header/headerContent/legend/explanation实测与布局尺寸/可见性/scroll，以及动态最低轴高、字体行高、compact/notice；反射各自runCatching，外层诊断亦捕获并保留原usable plot missing前缀，不改变失败结论。静态批准，待仅编译测试并重跑原用例取得数值，不增加矩阵，不提前判定根因或批准阈值调整。文档冻结。

Root确认`legend-height-diagnostic-build.log` AndroidTest构建35秒成功；仅原断言失败诊断，96dp门禁/fixture/生产未改。原生具体尺寸待CI取证。

### 96dp组件最低预算根因与最小修复批准

root/Sol报告1beb199运行40382248647实际诊断：en/240×220dp、density1.25，plot可见300×113px=90.4dp且恰等minimumAxisHeight；原96dp需120px，差7px。header162px、content199px、legend145px、explanation54px，compact=false。批准非embedded的MetricPlot View最低预算max(原字体动态值,ceil(96dp))，保留embedded历史原值及所有轴几何。96dp是含轴View下限，不是数据网格高度；平板真实网格260dp验收仍独立。

建议在minimumAxisHeight集中处理，使onMeasure与compact共用；当前案例header上限回收7px到155px，199px内容仍由原ScrollView可达。非compact分支保证高度至少minimum+48dp，因此header上限非负；compact分支用已钳为非负的总高，沿原提示降级。字体/范围/测试96dp及220dpfixture不改，不扩语言适配。此为方案批准，实施及原用例复验仍待；文档冻结。

### 96dp最低预算实施静态闭合

Astra复核ExtractionOverlayView唯一函数diff：dynamic沿用原renderer或fallback字体公式；embeddedHistory直接返回dynamic，其他路径返回maxOf(dynamic,ceil(dp(96f)).toInt())。向上取整不低于96dp，既有onMeasure/compact共享此函数，未改header滚动、轴绘制、字体、测试或fixture。实施静态批准，root统一构建及原用例CI待验，尚不标问题实际通过。文档冻结。

Root确认`minimum-axis-floor-build.log`完整Mock单测、Mock/Debug/AndroidTest APK、lintDebug构建2分19秒成功；实际两APK资源各10897值和6 literal round trips通过。仅非embedded MetricPlot最低View高96dp修正，Astra静态通过，原fixture及新提交CI仍待。

### 选轴Canvas尺寸载体静态闭合

root报告a4cbca4已越过en/240的96dp检查，后在en/540/WATER文字边界检查遇到旧675×120 bitmap尺寸。Astra复核测试：点击后重新layout可能改变plot高度，原focused绘制确实复用了首次分配的bitmap。冻结小diff现于每次真实点击/layout后按当前plot.width/height建立focusedBitmap，两次真实Canvas绘制共用该当前尺寸，finally回收。唯一selected、单位及端点、TextBoundsCanvas严格文字边界和四条path断言原样；初始检查保留初始bitmap，缺秤原本已按重测尺寸单独分配，无需改动。

该测试生命周期适配静态批准，生产未变；实际后续CI仍待，不把旧载体异常解释为已证生产裁切，也不概括整个图例矩阵通过。计划冻结。

### 系统inset变化后主图余量不足与最小布局批准

Astra实际查看run40384714929的extraction-running-light.png并读取with-scale geometry：content顶74px/高1391px，dataPlot581.35803px=258.38135dp，未过260dp；说明行64px，stop126px完整可见、间距18px，图宽96.97%。相比既有263.27023dp，内容少11px对应网格少11px，说明旧余量不足，不能以调整截图配置或门槛规避。此前代表场景通过保持为历史事实，当前面积出现明确回归待修。

批准运行态将常态采样/独立量程说明移至既有工具→概览，保留完整文字和plot无障碍说明；回收当前64px后理论网格286.83dp，实际待测。四图例/范围/缺秤提示/停止/字体/renderer边距及系统insets不动。由于说明还承载注水复位和遥测间断，禁止无条件隐藏所有说明：异常时保留精简警示，常态采样前缀可省；闲置与embedded历史不变。此为有界方案批准，不增功能/矩阵，实施与真实面积复验待验。文档冻结。

### 运行态说明压缩实施静态闭合

Astra复核ExtractionOverlayView/ExtractionFragment两文件：active状态同步compactRunningExplanation，仅visual非embedded运行态启用；正常采样说明不占主图高度，water reset/gap仍显示notes.drop(1)的精简警示。fullExplanation保留完整采样/异常文本，工具→概览调用overviewExplanation并附当前选轴的独立量程/单位；plot.contentDescription原完整notes未削减。idle恢复全文，embedded历史不启用压缩，四图例、量程、字体、renderer及停止逻辑未改。实施静态批准，测试focusedBitmap修订保持此前审结；统一构建与新几何/截图复验待root执行，未提前标260dp通过。计划冻结。

Root确认`live-inset-budget-build.log`完整Mock单测、Mock/Debug/AndroidTest APK、lintDebug构建2分钟成功；两APK资源各10897值及6 literal round trips通过。Astra批准常态说明移概览及focusedBitmap尺寸生命周期修正；原260dp/选轴/字边界门槛不变，实际新CI待验。
