# 原生语言对标与实施顺序

## 已核对的旧版范围

旧包 `hoyi-project/app/assets/apps/__UNI__7D80DAB/www/app-service.js` 设置页的 `langRows` 按下表排列。这是旧版真实配置，不将英语单独视为全部对标范围。

| 原索引 | 旧标识 | 原生规范 tag | 语言 | 方向 |
|---|---|---|---|---|
| 0 | CN | zh-Hans | 简体中文 | LTR |
| 1 | EN | en | 英语 | LTR |
| 2 | RU | ru | 俄语 | LTR |
| 3 | TH | th | 泰语 | LTR |
| 4 | AR | ar | 阿拉伯语 | RTL |
| 5 | JA | ja | 日语 | LTR |
| 6 | KO | ko | 韩语 | LTR |
| 7 | ES | es | 西班牙语 | LTR |

原生 `AppLanguage` 是纯标识层；后续AppLanguagePreference已接入独立偏好和统一资源Context，正式七语言资源已在后续阶段生成并打包，选择入口仍未开放。保存时使用规范 tag，显示文字不参与恢复；缺失或不识别的保存值保持当前简中默认。`legacyIndex` 记录旧版顺序，不代表已导入另一个包的私有设置。

## 后续实现与验收

1. 补齐八种语言资源。核对占位参数数量/类型、单位、换行、告警与操作未确认说明。现有完整句子模板可翻译；原曲线名称、用户内容、tips、历史原因必须明确区分原始数据与可翻译显示，不能修改控制元数据。
2. 添加独立的应用语言偏好及设置入口。不得复用机器设置、设备密码、曲线选择或安全恢复存储键。保存失败时不能显示为已应用。原默认用户行为保持简中。
3. 同时覆盖 Activity、Application 的导入导出提示与长期运行 Service 的资源解析。仅改 Activity Context 不足以保证回报/通知语言一致；服务中已保存的事件字符串也需明确刷新策略，保留事件 code 和历史原因。
4. 切换只更新显示。不能重启 BLE、重放命令、清除恢复记录或取消萃取。Activity 重建造成的前后台过渡要检查现有 500ms 退后台延时，萃取中仍保留固定停止入口。
5. 阿拉伯语按 RTL 核对导航、卡片、列表和文字。压力/水流/重量/时间曲线的科学坐标语义应显式保留，不能因文字方向把时间顺序或物理量映射倒置。
6. 完整翻译与显示核验通过后才开放对应选择。使用离线 Mock 检查长文字、空状态、错误/未知结果、深浅色、紧凑/横屏；真实 BLE/机器安全验收仍独立记录，语言测试不替代硬件证据。

## 本轮证据

`AppLanguageTest` 核对八项顺序、规范 tag、Locale 回转、仅阿拉伯语 RTL，以及非法保存值回到简中。新测试先因缺 API 失败，实现后再运行。该阶段没有接入运行时 Context 或控制发送；完整语言功能仍未完成。

2026-10-01：首批七语言安全提示草稿已补齐，见 `localization/README.md`。只完成这一组的机械完整性测试，未启用运行时语言，仍需继续全目录翻译和显示核验。

2026-10-01：通用导航与基本操作七语言各47条草稿已补，共329条，原有安全组加总为57资源键/399条；范围和格式参数两项新检查通过。尚未转换Android资源或启用入口，完整翻译/Service一致更新/布局仍未完成。

2026-10-01：七语言完整资源目录草稿已补齐，各872键/共6,104条，既有57键保留；Python完整性/格式/源一致性校验及Java逐模板格式化验证已加入CI，详见localization/README.md。这解决了草稿目录缺失，不等于正式资源与运行时语言功能完成；接入前具体风险/变体品牌规则见native-language-runtime-audit.md。

2026-10-01：独立偏好和Activity/Application/Service统一资源Context已接入，默认简中、没有选择调用方。4项偏好测试及全回归通过；Android缓存/重建/Toast/通知/RTL仍未验证。见unified-language-context.md。

2026-10-01：正式七语言XML各871键（排除共享app_name）已打包，转换一致性、19Python测试及两个APK各6,969编译文字通过核对；无选择入口，无Android视觉/生命周期或新硬件验证。见android-language-resources.md。

2026-10-02：准备Android运行时前置检查，仅新增Mock instrumentation，不开放语言选择入口。以已有独立语言偏好选择八语言，分别在深浅主题重建Home；从独立Android ConfigurationContext解析预期文字，核对实际导航文字、Activity/Application/现存Service的Locale与字符串、阿语RTL方向。读取原Home绑定，要求Service及原消息身份不变、设备/曲线/槽位/恢复/历史偏好不变，避免额外测试绑定掩盖服务重启。16种组合编译、Mock单测与Lint已通过；云端Android实际运行结果待补，编译不算切换验收。此轮不覆盖母语质量、长文字裁切、科学坐标视觉、通知（Mock刷新函数直接返回）、真实BLE窗口与萃取中切换，不能据此开放所有入口或宣称完成多语言。

运行时测试复审补强：可见文字同时要求isShown与屏幕可见矩形；保存完整app_language与appearance原始map，先还原进程语言权威，再恢复原始偏好（含缺失/非法tag），逐项核对恢复后的磁盘值。不能用只恢复有效默认语言代替测试前原始状态。

修正版测试APK构建、Mock单测与Lint通过；两轮独立静态复核无剩余实质问题。新增检查将通过现有Mock runner独立languageChecks参数执行，日志单独输出language-instrumentation.txt，必须命中LANGUAGE_CONTEXT_CHECKS_PASSED才算本轮运行通过，尚无实际结果。

2026-10-02：活动杯切换测试新增，仅Mock调用现有startShot与真实Extraction停止按钮。每语言独立一杯，等待watchShot已观察RUNNING后捕获杯ID/首点/消息，再切换语言并重建；要求绑定仍为同Service、杯ID与首点保留、消息不重放、译后停止按钮处于屏幕可见且可点击。点击后同主线程立即断言Mock已结束，避免自然32秒结束冒充按钮生效，再等待机遥测/结束观察/杯ID清空才开始下一杯。恢复原始语言/曲线偏好map，通用恢复helper仅在测试代码内复用。最终测试APK/Mock单测/Lint通过，两轮独立静态复审无残余实质问题，Android实际结果待云端。该测试不会证明真实BLE下停止/重连或其它页面的完整布局与通知行为。

Context云端证据（c00e038）：Verify native app36945429783和Verify Mock lifecycle36945429772均success。已读取后者language-instrumentation.txt，LANGUAGE_CONTEXT_CHECKS_PASSED languages=8 themes=2 sameService=true preservedState=true确认16组合实际Home重建、语言资源一致、可见导航文字/RTL方向以及同Service/同事件与非语言偏好保留。该提交没有活动杯检查，不能把随后新增用例算入此证据；科学坐标/真实通知/长文字与母语检查仍未完成，语言入口继续关闭。

2026-10-02：继续仅Mock语言运行时补充验证。LanguageChartChecks对ShotChartView、LegacyShotChartView与CurveStageView实际绘制Bitmap，八语言×深浅共48组合显式LTR/RTL；已知随时间/阶段上升的独立fixture，通过绘图区曲线色像素的左右平均高度验证物理顺序没有镜像。未截图，不修改原始点序列；该检查不证明单位标注、文字裁切、母语质量或所有坐标边界。

LanguageNotificationChecks只读既有Mock服务引用并反射真实通知factory/channels方法：八语言核对正文/警告/断开按钮文字、PendingIntent创建者仍属Mock、通道ID集合稳定且名称随语言更新。只构造对象/创建该Mock自己的通道，不发布通知、不发送PendingIntent；结束还原原语言偏好和既有通道，删除本次新通道。Mock的实际refreshNotificationDisplay仍直接返回，这不是前台/安全通知真实刷新与权限/去重验收。两组检查首版构建/Mock单测/Lint及独立静态复审通过，最终诊断标记调整后重新编译；Android运行时结果待补。

活动杯云端证据（eff6662）：Verify native app36946052287及Verify Mock lifecycle36946052284成功；读取language-instrumentation.txt，原Context16组合标记与LANGUAGE_ACTIVE_CUP_CHECKS_PASSED languages=8 sameCup=true retainedSamples=true translatedStop=true均存在。8语言每杯实际产品Mock启动、Extraction重建保留杯ID/采样/消息和译后停止按钮立即结束已运行通过。此提交不含随后图表/通知factory新检查，不计入其证据；没有真实BLE或真实机器结束证明。

图表/通知factory补充检查最终Mock测试APK、Mock单测与Lint通过；独立静态复审无实质问题。语言分支错误输出已单独标记LANGUAGE_CHECKS_FAILED，便于失败时定向定位；云端运行结果待补。

2026-10-02：实际平台通知发布补充用例，仅隔离Mock获得POST_NOTIFICATIONS权限。新测试以独立OpenHoyiLanguageTest tag及91001/91002 ID使用当前Service动态Context的共享display构造两条通知，由Android NotificationManager实际发布/读取/八语言更新；要求同两key替换、译后正文/动作/警告更新，取消安全通知后连接通知保留，最后取消自身tag。没有点击通知或发送PendingIntent，没有调用真实Service刷新门禁。复审修正清理链：取消、等待、语言权威/原始map和各通道还原独立尝试，原失败保留并附加清理错误，不把清理失败算通过。最终Mock测试APK/单测/Lint成功，两轮静态复审无残余实质问题；平台实际运行待云端。该用例仍不证明Service前台/安全刷新资格、去重/权限失败/真实BLE安全。

三种图表48组合保持相同渲染和像素范围，改为Bitmap.getPixels批量读取后扫描同一row-major数据，减少跨JNI调用；不减少测试样本或降低顺序断言。

2026-10-02：图表/通知加入后的三次Mock CI长期未结束，尚无运行通过证据。核对本机Android 35 SDK源码：Instrumentation.startActivitySync把新ActivityWaiter放入列表并无限wait；仅prePerformCreate/onCreate匹配后移除，onNewIntent不完成该等待。Intent.NEW_TASK在已有任务时复用Activity。活动杯检查结束后首页仍在任务根，通知两用例再次同步NEW_TASK启动首页存在无限等待路径。最小修正仅androidTest：Context检查返回finally恢复后的首页，runner传给两通知用例，检查其未销毁/未finish，保留既有服务绑定/所有断言；活动杯finally先恢复原语言再finish，因此首页onResume无需语言重建。新增五阶段START/PASS日志和instrumentation中间状态，失败收集定向OpenHoyiLanguage日志。未修改生产UI、协议、业务或BLE。等待本地构建和新云端结果，不能仅凭SDK推理声称运行故障已修复。

本轮既有首页复用修正：Mock AndroidTest APK、Mock单元测试和Lint离线构建成功（/private/tmp/hoyi-language-existing-home.log，BUILD SUCCESSFUL，107 tasks）；脚本bash -n及diff --check通过。独立只读审查确认首页引用/恢复顺序和原断言保留，无生产改动。运行验证仍待新云端CI。

Mock CI诊断进一步限定每条既有instrumentation命令600秒：Python subprocess超时退出124，其它失败码原样传播到ERR收集，不继续PASS标记；stdout文件保留阶段中间输出。固定Mock runner只允许audio/language模式。bash -n、四段嵌入Python语法解析及独立静态复审通过；超时不等于产品测试通过，也不操作正式设备。

2026-10-02：b20b90f云端Verify native app36948773115与Verify Mock lifecycle36948773151均success，已读取后者产物language-instrumentation.txt。五个阶段START/PASS及最终五条检查标记全部存在：Context16组合、活动杯8语言、实际Canvas三图表48组合物理时间/阶段顺序、8语言通知工厂/通道/动作、8语言Android平台通知同key更新/取消/清理。audio-instrumentation.txt原五项音频/提示/焦点/退出回归也全部通过。首页复用修正后本次不再挂起；旧a161de9任务在30分钟限时被取消，其语言stdout为空，仅能从旧日志缩小到语言分支，不能以旧日志单独证明某一行。新产物位于/private/tmp/hoyi-language-home-36948773151。平台通知检查不是真实Service刷新门禁/去重/权限失败证明，三图顺序检查不证明单位标注或全部长文字布局；无硬件操作。

600秒CI辅助逻辑的额外离线执行核对：实际嵌入Python代码通过替换subprocess.run模拟成功0、命令失败7、TimeoutExpired→124及非法模式拒绝；每种已启动命令的部分stdout均保留，固定Mock argv和600秒值均断言。未执行adb，没有将模拟超时验收当作Android运行通过。最新8458c34云端运行结果仍待补。

8458c34最终云端证据：Verify native app36949018031与Verify Mock lifecycle36949018081均success。已读取/private/tmp/hoyi-language-bounded-36949018081的language-instrumentation.txt和audio-instrumentation.txt，五阶段START/PASS、五条语言检查与原五条音频/产品提示回归标记全部存在。正常运行没有触发600秒超时；超时失败传播由上面的离线模拟证据单独覆盖，不能混同。功能对标表已更新图表顺序和平台通知的已验证范围，实际Service刷新资格/权限失败、各页面长文字与真实硬件验收继续保留为未完成。

布局/Service编排补充运行证据dfc4c55：构建36952198505、Mock运行36952198311、截图生成36952198275均success；已读取Mock语言七项标记与音频五项标记。紧凑共享组件240组合与固定停止文字完整性通过，实测日语导航裁切已用HoyiUi自适应高度修正；独立未注册无Hub Service的原通知方法资格/去重/强制两postTime更新/Context查询失败也通过。实现与严格边界见../superpowers/plans/2026-10-02-language-layout.md和../superpowers/plans/2026-10-02-service-notification-refresh.md。截图产物已下载但未逐图新视觉核对；不算所有页面布局或真实Service/BLE/硬件证明。语言选择入口和其它功能对标尚未完成。

2026-10-02：3c924d7云端完整构建36953997155与隔离Mock运行36953997198均success。已读取产物：六阶段START/PASS、八条语言通过标记及五条原音频/提示回归标记完整。800个共享导航fixture（八语言×双主题×320/360/600/700/1000dp×1.0/1.3字体×五选中态）、标题/按钮文字检查通过；真实导航横向分支与字号增长已运行确认，原240紧凑子矩阵/裁切负例/八语言活动杯停止检查保留并通过。此轮未发现需修改的生产UI，不改协议或业务，不操作真机。仍待五页完整内容、详情/错误/滚动/图轴文字与母语质量；语言入口继续关闭，真实Service/BLE/硬件证据独立。产物/private/tmp/hoyi-wide-font-36953997198。

2026-10-02：d64f620云端完整构建36972449670及Mock运行36972449671均success（Mock 9m3s，没有触发超时）。已读取两份真实页面日志：compact与wideFont各80个PAGE_START和对应最终通过标记，共160个八语言/双主题/五页面初始配置；实际资源断言360dp/1.0、1280dp/1.3均通过。五个设置分组文字与逐步纵向滚动、导航全可见/48dp、根内容和固定启动行不侵入导航、同服务/非语言偏好保留均通过；全页非输入文字检查包含实际view宽度溢出负例，明确省略的列表摘要保留。原八条语言标记和五条音频/提示回归也全部存在。此轮没有生产UI失败，不修改生产UI/协议；未操作真机。产物/private/tmp/hoyi-language-pages-36972449671。仍缺详情、动态告警/未知结果/确认弹窗/完整列表数据与图轴文字、母语质量、注册Service/BLE/硬件验收；不能把初始页面矩阵等同全功能。


2026-10-03：d69de55完整构建37118581368与隔离Mock37118581341均success，产物已读取。compact/wideFont各80 PAGE_START及PAGE_LAYOUT/DETAIL_DIALOG标记完整，原八语言/五音频标记全部保留。详情、三种合成告警和确认取消已在该矩阵通过；不代表全部弹窗视觉、真实BLE或硬件验收。产物/private/tmp/hoyi-dialog-native-37118581341/mock-lifecycle。

2026-10-03：新增设置页原生应用语言入口，八语言自称、独立偏好提交、当前项免重建、失败保持旧语言/选中项及重试提示。成功后刷新通知显示并重建设置页；异步Service绑定后补刷新，不重启设备会话。Mock入口测试覆盖切换/还原、当前项、保存失败和同Service。最终双APK/双单测/双Lint及测试APK构建成功（170 tasks），19项Python、890键目录、889键生成一致性、两APK各7113模板和6转义回环通过，独立复审无阻塞。实际入口运行仍待新云端CI；本轮不安装/操作真机。计划docs/superpowers/plans/2026-10-03-language-selector.md。


2026-10-03：语言入口0b94f39已完成云端实际运行。Verify native app37125094623、Verify Mock lifecycle37125094578及Capture Mock UI37125094627均success；已读取Mock产物compact/wideFont各80 PAGE_START和三项PAGE_LAYOUT/DETAIL_DIALOG/SELECTOR_UI标记，原八语言及五音频标记完整。实际选择/当前项/保存失败/切换还原/同Service检查通过。完整构建日志确认39,823协议检查、32,856通知回放、92会话场景，以及两APK各7113模板与6转义回环。截图任务成功未逐图新视觉核对；不宣称母语、真实通知生命周期、BLE或硬件完整验收。产物/private/tmp/hoyi-language-selector-37125094578/mock-lifecycle。
