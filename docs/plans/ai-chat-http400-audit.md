# AI HTTP400 系统核对（code4 后）

## 结论与证据边界

用户最新截图可直接读到 `HTTP/RESPONSE_HEADERS/159ms/HTTP400/INVALID_REQUEST_ERROR`，输入“你好”。这确认收到 HTTP400，不是连接/读取超时；没有明确指出错误字段。截图本身不显示安装版本，不能据截图证明其APK身份。

本轮不再修改模型或安全参数，不再次把兼容猜测当修复。code4 空required兼容试验未解决用户问题，不能继续把它当已定位根因。

“真实 SDK 合成请求”是实际 Android AAR 类在 host JVM 单测中运行并向 fake HTTPS 连接写入的 JSON，不是手机真实 HTTPS 抓包。已检查 `testMockUnitTest.classpath`：DeepSeek 为 `prompt-executor-deepseek-client-release-runtime.jar`，其 DeepSeekLLMClient class 与 Android AAR classes.jar 内容相同，SHA256 `0f4e797c753b7494e2684d63c0cbf7296dfb3221b0b1704e739e95f1361d38b4`。此前只比较 Android/JVM 一个方法字节码不足以代表整个Android运行，不能混称为设备实测。

## 逐项核对表

| 项目 | 当前首轮合成/源码事实 | 官方对照与本地结论 | 精确未验证边界 |
|---|---|---|---|
| endpoint | 固定 `https://api.deepseek.com/chat/completions` | 符合[首次调用](https://api-docs.deepseek.com/)；不跟随redirect | 用户网络代理/中间层行为未抓取 |
| 方法与媒体类型 | POST，Content-Type/Accept为application/json | 与[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)一致；fake在connect前断言 | 真实Android socket报文未抓取 |
| 中文与长度 | 最终请求字符串UTF8编码，fixedLength使用最终bytes.size，写同一bytes并关输出流 | “你好”专用fake通过，排除源码以字符数充字节数 | 非真实网络端到端抓包证据 |
| Authorization | 发送边界现取Key，加Bearer前缀 | fake断言通过；没有把Key交SDK/JSON | 不读真实Key，不能验证来源、权限、账户或实际认证；400也不证明认证成功 |
| 模型 | `deepseek-flash` | [模型页](https://api-docs.deepseek.com/quick_start/pricing/)当前有效，支持工具及非思考 | 该账户/服务实际可用性未调用验证 |
| tokens | JSON max_tokens为整数2000；本地contextLength/maxOutputTokens不作为额外JSON字段发送 | 在当前[参数范围](https://api-docs.deepseek.com/api/create-chat-completion/)内；不是新模型的陈旧8192上限推断 | 实际服务对该账户的处理未知 |
| messages | 首轮只有system和user、content均非空字符串；“你好”保持原文 | 无附件、prefix、latest_reminder、assistant续写或server_tool_calls | 内容安全可能审核system及输入，不能凭“你好”判定一定不拒绝 |
| tools外层 | 三个不同名称，type=function；无web search/read_url工具 | 符合[函数工具基本结构](https://api-docs.deepseek.com/api/create-chat-completion/) | 与safety扩展组合的实际服务兼容待确认，见下节 |
| tools参数 | 根type=object，properties包含可选integer/string；code4省略根部空required，保留其他内容 | 本地完整JSON差异测试通过；[draft4约束](https://json-schema.org/draft-04/draft-fge-json-schema-validation-00#rfc.section.5.4.3)只支持跨draft兼容动机，不证明服务端draft | code4仍失败，空required不能继续作为当前已证根因 |
| strict | 没有strict:true，不调用/beta | 不会无意启用strict beta | 无已发现的strict违例 |
| tool_choice | 首轮不发送 | [契约](https://api-docs.deepseek.com/api/create-chat-completion/)说明有tools时默认auto，符合[安全指南](https://api-docs.deepseek.com/guides/safety/)允许的选择 | 未证实后端是否存在默认处理差异；不据猜测加auto |
| stream | false；没有stream_options | 不违反[非stream限制](https://api-docs.deepseek.com/api/create-chat-completion/) | 无已发现的stream选项违例 |
| thinking | 顶层对象type=disabled | 当前[非思考开关](https://api-docs.deepseek.com/api/create-chat-completion/)有效；没有因reasoning_effort缺省误开思考 | 服务实际响应未验证 |
| safety_check | 顶层对象level=high | [当前安全指南](https://api-docs.deepseek.com/guides/safety/)有效，不能盲删或降级 | 与function工具组合及实际内容拒绝仍未排除 |
| extra字段 | 首轮恰为messages/model/stream/tools/max_tokens/thinking/safety_check七个键 | 没有additional_properties包装、temperature、response_format、reasoning_effort、parallel_tool_calls或web_search_options | 手机进程实际输入/会话与该合成首轮是否相同未采集 |
| 后续工具回合 | 既有助手工具消息缺content时补null；其余字段保留 | 解决nullable-required兼容问题；两轮fake通过 | 不在首轮，不能解释当前首次400 |
| 制品依赖 | Android runtime解析为Koog core/model/base 1.3.0、DeepSeek 1.3.0-beta、serialization1.10.0、logging8.0.01 | Android SDK类确实参与51项本地AI测试，不是仅独立JVM SDK样例 | Android OS下URL连接与平台行为仍非host单测证明 |
| Mock/测试分离 | Mock只模拟设备；main AI路径真实网络，fake HTTPS在test/androidTest；AI引擎不按MOCK_MODE切fake | 不会因安装Mock自动获得AI假响应 | 用户实际运行APK版本须由版本界面/安装记录另核实 |

## 制品与安装配置

本地归档code4包 `io.openhoyi.mobile.mock`，versionCode4，versionName `0.1.0-local.ai-schema4`，minSdk26/targetSdk35，debuggable；v2签名证书与先前code1/2/3一致。归档完整SHA256 `f2a5f93b0bd9f9667aead44ce7bb6681f7d644d2f70f3945453f3d81559dcc0a`。未安装/查询用户设备、未读真实Key、未调用真实API、未推送。以上只能证明已交付制品，不替代用户安装状态核验。

## 此前遗漏但现在发现的官方交叉限制

[Web Tools 的 Errors 表](https://api-docs.deepseek.com/guides/web_tools/#errors)列出了涉及tools与safety_check/title等扩展组合的完整固定错误模板。原因栏写“其他服务端工具”，而我们的工具是function，因此尚有适用范围歧义。不能从这一段直接得出必须关safety，也不能说已证实function不支持。

这使“安全扩展与自定义工具组合冲突”成为有官方固定错误模板可核实的候选，不再是空required式兼容猜测。当前诊断只留下通用invalid_request_error，确实丢失了这条可区分信息；之前未逐项读到交叉功能错误表，排查不完整。

## 最小可准确定位方案

1. 不改code4请求的任何模型、工具、token或安全字段；无自动重试、无额外请求。
2. 在既有HTTP400有界解析器中，仅对上述完整官方错误文本做全字符串相等匹配，输出封闭 `TOOLS_FEATURE_CONFLICT`。不使用contains/模糊正则，不输出原文、Key、用户内容、请求ID或未知字段。
3. 保留4096字节解析上限、UTF8/深度限制、2秒watchdog、取消/Key变更和未知fallback。输入附加任何前后缀或仅出现关键词，均不得命中该分类。
4. 先fake回归、Astra独审。只有诊断版获协调确认后，用户再复测一次。如果精确命中，就按服务实际明示的组合冲突评估设计替代；不因命中就自动关安全。若不命中，明确仍未知，不能继续随机删参数。
5. 若还是通用错误，下一步应获取服务端可确认的原因：在用户授权下，由用户从DeepSeek支持渠道提供这次服务请求关联信息并查因，或单独设计经过授权的受控A/B请求。不能拿本地fake宣称已验证服务接受，也不能私自使用真实Key或产生费用。

## code5 诊断版授权与验证范围

协调线程已明确要求完成code5。code5不修改code4请求参数，保留模型、思考、安全、工具和预算边界；只增加官方精确组合冲突模板分类及错误码前的 `B5/` 安装版本标识。版本标识来自BuildConfig.VERSION_CODE，不使用Key、用户输入或响应内容。Key页禁止截图、不回显复制、加密存储不变。

此包旨在确认服务是否实际返回已知组合冲突，不能称为已修复；未命中时仍回到通用分类，不能据此排除其他未知错误。

code5已完成：52项AI单测（10类）及9项Python检查通过，Mock组装/androidTest编译通过，Astra独立复核稳定代码与最新XML/构建证据通过。fresh组装最终包 `/tmp/openhoyi-ai-diagnostics/OpenHOYI-AI-diagnostic-code5.apk`，25,097,763 bytes，SHA256 `7b7021625d8a0f4f2a9661307413441dd019bc9272b9a4d4cc30e56bfeb9b132`；包 `io.openhoyi.mobile.mock`，code5/name `0.1.0-local.ai-diagnostic5`，v2验签通过，同原证书 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。已保存Library并交协调线程。版本前缀仅证实versionCode，不代替源码/完整APK哈希身份。未做真实API/设备安装或服务恢复验证，不称修复。

## 最新解决结果（保留前文历史排查记录）

一次已授权真实合成请求的词线索指向tools/safety_check/thinking组合。保留high safety与三只读工具、启用low thinking后，补齐官方要求的完整跨轮reasoning/tool协议，仅内存保存并受64KiB/128条上限约束，失败取消不提交半轮，新会话/关闭/Key变化清理，不向UI/日志/磁盘展示或存储reasoning。

实际已验证：同一平板账号主Mock126真实SDK单轮HTTP200且非空回复；随后固定合成只读工具1次、2用户轮、3个模型请求全部HTTP200。并非只改参数后未经验证就称修复。未执行真实机器/秤操作，工具真实本地数据读取仍非本场景验收范围。全部真实API验证已停止，总费用按当前人民币峰价保守估算≤0.02258元（含最初无usage400保守预留），非账单实扣证明。

源代码仍未提交/推送，受限工具链PR等待明确批准，不绕过。模型选择另见[后续规划](ai-model-selection.md)，未实施或宣称账户模型列表已验证。
