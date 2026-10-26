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

状态仅使用“未开始 / 开发中 / 软件通过 / 实机待验”。“软件通过”不代表设备效果确认。

| 项目 | 状态 | 当前证据/下一步 |
|---|---|---|
| 协议计时修复 | 软件通过 | Astra最终审结通过：生产仅删07且保留时序；同地址READY→FAILED→重连两代精确三帧通过。主协调者核对675个JUnit零失败/错误、协议39823 checks、会话93 checks；Mini/Ultra实机待验 |
| 真Tab迁移 | 开发中 | 六根及萃取已迁真实Fragment，本地统一构建通过；c729ab9原生CI启动崩溃：Fragment容器首次测量前未附着；两文件修复静态通过，冷启动/重建原生重跑仍阻塞验收 |
| 机器首页常驻控制 | 开发中 | 紧凑双锅炉/快捷五槽/48dp壳控制已实现；实际701eda0首页截图不通过：仍是准备页、重复块/大按钮/rail截字；720×360快捷控件真实裁切，已交Sol修复 |
| 大图设计 | 开发中 | 实际live renderer四指标、独立刻度及几何静态通过；新Main/Fragment入口下原生绘制与图高待验 |
| 自动导航与重连 | 开发中 | 后端静态审查与101 checks通过，首次秤READY资格已闭合；壳新杯导航仍待原生验收；机器/秤恢复实机待验 |
| 设置弹层统一 | 开发中 | 二级/辅助弹层及根Fragment接入静态审查通过；最终统一构建通过，长文本/IME原生运行仍待验 |
| 整体验收 | 未开始 | 下述有界原生场景，硬件项单独标记 |

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

## 批次1：真Tab与内容迁移（立即执行）

- [ ] 增加AndroidX Fragment依赖并将主题宿主改为FragmentActivity；保持语言/主题包裹。Manifest主入口指向MainActivity。
- [ ] 新增壳导航契约：`selectTab(destination)`、`openRoute(route)`、`back()`；根页稳定tag匹配destination。Tab transaction只show/hide、setPrimaryNavigationFragment、setMaxLifecycle；重复点当前Tab不重建、不写返回栈。
- [ ] MainActivity只安装一个TaskPageFrame和导航/状态操作区。TaskNavigationView只接受当前目的地和回调，不凭activity.javaClass推断选中项。
- [ ] 先迁AppSettings/Scale，再Curve/History/Bean，最后Home/Extraction。Fragment真正返回页面内容View；Controller可使用宿主Context/主题/系统授权，不可创建Activity、调用host.setContentView或读取宿主类名决定页面。
- [ ] Controller输入为路由参数和saved state；实现createView、可见/隐藏、saveState、dispose。隐藏停UI轮询；onDestroyView清View引用、监听和弹窗。服务连接由MainActivity统一持有，页面不得因Tab切换释放BLE连接。
- [ ] 每Tab子路由与草稿独立恢复；处理返回、旋转、主题/语言重建；外部通知入口转为壳内路由。旧顶层兼容Activity只能转到MainActivity并结束，不能再创建业务View；二级Activity可保留但无顶层rail。
- [ ] 核对二级页：曲线编辑/导入/预览/分享，历史详情/对比/复盘，豆录入/批次/准备/台账，机器设置/秤连接/秤设置，以及仍可达的legacy页面本轮只确保入口和状态无回归，可保留原Activity但移除顶层rail；不展开所有二级Fragment迁移。
- [ ] 新增 `MainNavigationChecks.kt` 原生场景：六Tab循环20次Activity始终1个、每根Fragment至多1个；Curve筛选/History滚动/豆草稿各保留；Extraction切走回来series ID/时间递增；配置重建恢复当前Tab和子页。单看截图或类声明不算通过。

批次1交付点：真实六页和萃取在单壳可用，原有业务门禁不变。之后按所有权交接视觉接入，不能让占位Fragment变成永久实现。

## 批次2：机器首页与常驻操作

- [ ] HomeController改成机器状态首屏；固定双锅炉温度/开关、机器连接状态、配方与快捷启动；把准备细节移到次级展开区。
- [ ] 抽取原confirmStart可复用入口供首页和萃取共用，同样执行设备/曲线/温度/秤/未知写入门禁和用户确认；不复制一套较弱校验。
- [ ] 提取睡眠确认与秤连接状态到壳操作区；断开×和重连icon为独立48dp目标，禁用时保留说明。锅炉写入等待回读，不乐观标成功。
- [ ] 测试IdleTelemetry双锅炉可显示、ExtractionTelemetry蒸汽缺失显示未知；检查五槽合法边界及断连/未知写入时不可快捷启动。
- [ ] 原生截图：720×360dp和1280×800dp首页首屏，不滚动能看到连接、双锅炉、快捷启动和常驻操作。

## 批次3：萃取大图

- [ ] 改 `TaskExtractionWorkspace.kt` 移除强制宽侧栏，主图占主要空间；原 `ExtractionOverlayView.kt`/`ProductHistoryChartRenderer.kt` 复用 `FourMetricChartProjection.kt`。
- [ ] 四线同图、指标/单位/范围一排；当前实线与参考虚线区分；图例只切轴和强调，不能退回四小图或一次只见一项。
- [ ] 把重复准备/摘要/说明卡压缩为一行和次级工具；停止固定可见。遵守spec尺寸、色值与无动画参数。
- [ ] 保留并运行 `FourMetricChartProjectionTest` 和 `ChartInspectionTest`；增加同图四指标呈现、缺测断线、注水复位、负重量场景。原生量测图高与停止按钮边界，不只检查tag存在。

## 批次4：新手动杯导航与连接恢复

- [ ] MobileService从PassiveShotDetector开始事件公开稳定ID；MainActivity只消费新ID。先保存lastPresented再导航。重复遥测、旋转、同杯切Tab不重跳；下一杯重跳一次。
- [ ] 新增纯策略测试：前台新杯→一次，切走同杯→零次，重建同杯→零次，下一杯→一次，后台已结束杯→零次。
- [ ] 查明现有ReconnectPolicy和DeviceHub角色连接所有权后复用；仅补缺失的原设备地址、用户意图抑制和退避触发。禁止全设备扫描后随便连另一台。
- [ ] 增加意外断连→同地址恢复；主动断开→不恢复；明确重连→解除抑制；旧generation回调→不改变新连接；蓝牙关闭/权限缺失→停重试并提示。
- [ ] outbound回归：重连不能发送启动/停止/设置/睡眠/去皮/计时；原未知结果继续等待核实。机器唤醒无证据的控制帧不新增。

## 批次5：BOOKOO初始化修复

- [x] 生产只删除初始化 `030A0700000E`，保留其余三帧及500/1000/2000ms。
- [x] 协议测试精确断言三帧和时间；adapter拒绝旧07帧。
- [x] DeviceSession模拟同地址首连到READY→意外断线→同地址重连到READY，两个generation分别只写预期三帧，通知继续产出重量。不额外补stop/reset/tare。
- [x] 更新旧“每帧500ms、共四帧”测试假设，运行两模块check。
- [ ] 真实Mini/Ultra连接计时不动且重量不清零的实机验收。

## 批次6：设置与自有弹层统一

- [ ] 用rg枚举 `AlertDialog.Builder`、`Dialog(`、`PopupMenu` 入口并映射到公共构建组件；统一间距/字体/颜色/宽度/动作顺序。系统权限和文件面板不接管。
- [ ] 设置机器/秤/App三组保留所有现存参数和低频入口。迁移只改显示与导航，不改设置编码/值范围/回读与未知结果门禁。
- [ ] 检查代表弹窗：启动确认、睡眠、断开、锅炉编辑、删除曲线、语言/主题选择、图表检查、恢复未知结果；浅深色、长中文及英文、放大字体时正文可滚动且动作可点。
- [ ] 搜索所有剩余直接构建点，逐项迁移或注明系统/外部排除理由；不能只换一个公共弹窗后宣称“全部统一”。

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
