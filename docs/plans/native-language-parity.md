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
