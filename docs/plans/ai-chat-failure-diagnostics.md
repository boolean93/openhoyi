# AI 发送失败脱敏诊断

- 来源/目标：沿用现有 `feature/ai-chat` 草稿，未来目标 `develop`；本轮不提交、不推送、不创建 PR。
- 目标：区分立即失败的 HTTP、DNS/TLS、连接/读取/总超时与 SDK/解析错误；未取得真实设备证据前不声称根因已修复。
- 最小方案：仅在内存失败对象和界面显示固定阶段、固定类别、HTTP 数字状态及单调耗时；无日志、异常原文、请求/响应内容或密钥。保留固定端点、重定向禁用、请求额度和响应大小边界。
- 验证：本地 fake HTTPS 连接和真实 Koog 会话单测，编译 Mock APK。禁止真实/付费 API；不读取真实密钥、不安装、不操作设备或硬件。
- 协议风险：仅 AI HTTP 诊断，无 BLE/机器写入变化；显式连接后仍按原 15s 连接/30s 读取，整轮 60s。
- 发布/签名：仅本地可复测 Mock 构建，沿用原 Debug 签名；不发版、不修改升级数据。不将本地构建视作安装或硬件验收。
- 回退：仅撤回本轮诊断增量，保留已有 AI 草稿和其他未提交改动。Astra 独立复核由协调线程安排。

## 本轮结果

- `./gradlew :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.Ai*' :mobile:assembleMock :mobile:compileMockAndroidTestKotlin --offline` 通过；8 个 AI 测试类合计 43 tests，0 failure/error/skipped。含 HTTP 302/400/401/402/403/429/500/503、DNS/TLS/连接失败、连接和读取超时、JSON/SDK 解码、总超时以及既有取消/Key 替换与日志哨兵测试。
- `python3 -m unittest discover -s scripts -p 'test_ai*.py'`：8 tests 通过。`git diff --check` 通过。
- 最终本地构建：`./gradlew :mobile:assembleMock -PhoyiVersionCode=2 -PhoyiVersionName=0.1.0-local.ai-diagnostics --offline` 通过。
- 本地归档 `/tmp/openhoyi-ai-diagnostics/OpenHOYI-AI-diagnostics-code2.apk`，25,090,315 bytes，SHA256 `42d62d8d703ba653f79ba0a3f28f378d69fedf78871ae395e1a0d94a6c2f56d6`。
- 包名 `io.openhoyi.mobile.mock`，versionCode 2，versionName `0.1.0-local.ai-diagnostics`，minSdk26/targetSdk35，v2 验签通过。证书 SHA256 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54` 与原交付 code1 包一致。原包完整 SHA256 `4fec18ddeeeb84c450934cc336b7cb79697670b43a5f46efe50431850a8e5c37` 已按归档核对。
- Astra 独立只读复核本轮诊断增量通过，无新增阻断项。没有真实联网或界面运行验证，不能报告手机即时失败的根因已找到，也不能报告连接已恢复。未运行完整仓库非 AVD verify，本轮不是 PR/发布门禁完成证据。
- 复测只需覆盖安装后回报错误下方固定诊断码；不要求 Key、聊天内容或原始日志。不开放复制聊天/Key，也不修改 FLAG_SECURE。

## HTTP400 后的请求契约核对

用户 code2 复测取得立即返回 HTTP400；这将故障定位到服务端拒绝请求，而非连接/读取超时。本轮只捕获真实 Koog 对合成问题、合成 fake Key 的序列化请求，对照官方 Chat Completions 契约。保留模型、thinking/safety 参数和全部安全边界；仅修有证据的格式不兼容，并补 fake-only 契约回归。继续不提交、不推送、不安装、不使用真实 API，产递增 code3 同签名本地 Mock 供用户复测。真实服务恢复仍须用户确认。

合成请求核对结果：真实 Koog 两阶段序列化使用 `stream:false` 且不含 `stream_options`；普通函数工具没有 `strict:true`，参数为 object schema；省略 `tool_choice`，按官方契约有工具时默认 auto。模型、thinking、safety_check 和 max_tokens 正确，尚未找到可证明导致 HTTP400 的请求契约错误，因此本轮不改网络参数、不读取错误正文，不将 code3 称为 HTTP400 修复。

按用户随后明确要求，仅取消 `AiChatActivity` 的 FLAG_SECURE 以允许聊天页截图。`AiKeyActivity` 的禁止截图、不回显、不复制及密钥加密存储不变。Android UI 测试仅将聊天页 secure flag 断言改为 false，原有针对 Key 页外部截屏保护测试保持不变。新增静态边界检查与真实 Koog 合成两轮请求契约测试；测试不保存请求内容、不开真实网络。早前诊断版本记录中“不修改 FLAG_SECURE”仅描述 code2，已由本轮明确授权替代。

官方依据：[Chat Completions 请求契约](https://api-docs.deepseek.com/api/create-chat-completion/)、[内容安全要求](https://api-docs.deepseek.com/guides/safety/)。

后续契约测试识别到另一项具体兼容问题：真实 Koog 在工具调用后的 assistant 消息省略 `content`，而官方请求契约声明该字段必需但可 null。新增 `AiRequestContract` 只对 assistant、有非空 tool_calls 且缺 content 的消息补 JSON null；不改变首轮请求，不改其他字段。测试分别保留 SDK 原始合成输入与实际 fake wire，断言原始二轮缺失、修正后完整 JSON 只新增该字段。此问题不在首次请求，不能用来解释首次立即 HTTP400。

按用户后续排障要求，code3 改为包含 HTTP400 有界识别：仅 400 读取最多4097字节（超过4096不解析），2秒独立 watchdog 断开该请求连接并在完成时取消，另有 readTimeout/单调截止检查；原60秒整轮上限保留。严格UTF8、JSON最大深度16，只精确映射官方固定 `Content Exists Risk`、`invalid_request_error` 和固定参数枚举，其余输出 UNKNOWN/MALFORMED/TOO_LARGE/UNAVAILABLE。原始 message、类型值、param、请求ID和正文均不输出、不存储、不交SDK；401等其他HTTP仍不读取正文。未知错误不会被猜测归因，带动态后缀的安全提示也不作任意子串匹配。独立Astra审查要求的阻塞读/watchdog取消/无迟到断连测试已加入。

## code3 交付证据

- `./gradlew :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.Ai*' :mobile:assembleMock :mobile:compileMockAndroidTestKotlin -PhoyiVersionCode=3 -PhoyiVersionName=0.1.0-local.ai-diagnostics3 --offline` 通过，10 个 AI 测试类合计49项，0 failure/error/skipped；新增读取/解析/白名单/watchdog/取消与合成SDK请求契约测试。Python AI静态检查9项通过。Astra独立复核稳定代码及最新49项XML通过。
- 为避免增量 ZIP 打包遗留空洞，仅将生成的增量APK移至本地归档后，以同源码/同参数重新 `assembleMock`（1秒通过），最终 APK 25,096,631 bytes。
- 最终文件 `/tmp/openhoyi-ai-diagnostics/OpenHOYI-AI-diagnostics-code3.apk`；SHA256 `26da07db04e4a4e0251c1ff1d0bdadb5de4fe794fea153ca56457fb5ceac47f0`；包 `io.openhoyi.mobile.mock`，code3/name `0.1.0-local.ai-diagnostics3`；minSdk26/targetSdk35，APK v2验签通过，与code1/code2同证书 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。已保存Library供用户下载。
- 未使用真实API/Key，未安装或操作设备，未提交/推送/PR。未运行完整仓库verify。实际手机截图、断连时延、HTTP400类型及服务恢复仍待用户复测；不能宣称首次HTTP400根因已定位或已修复。

## code4 单变量兼容候选

code3 手机输入“你好”仍立即返回HTTP400/INVALID_REQUEST_ERROR；这不足以识别具体字段。独立核对 Android AAR 与 JVM `serializeProviderChatRequest` 字节码相同，POST/JSON/UTF8及最终字节数定长写入没有确定错误。不修改当前官方有效模型、max_tokens、thinking、safety_check或默认tool_choice。

合成请求三个函数工具均有根部 `required:[]`。JSON Schema draft-04 的5.4.3要求该数组非空，新draft允许空；DeepSeek未声明服务端draft，所以不能据此证明当前400根因。code4仅对 `tools[*].type=function` 的 `function.parameters` 根部空JsonArray required省略该冗余键，维持全optional意图，不改非空/null/错误类型/嵌套schema或任意消息内容；保留code3助手content补null。Astra已独立核对规范并认可作为待复测单变量兼容候选。

验证补充：真实Koog两轮SDK原始合成请求与wire完整JSON差异仅三处空required删除（以及既有助手null兼容），非空/嵌套/无关字段保持；中文“你好”UTF8/定长POST头覆盖。只做本地fake，无真实API/Key/设备/推送。未知错误不采用宽泛正文关键词推断。若仍400，不能把兼容候选包装为修复成功。

规范依据：[JSON Schema draft-04 required](https://json-schema.org/draft-04/draft-fge-json-schema-validation-00#rfc.section.5.4.3)。仅核对该规范约束，不声称运行过完整第三方schema验证器。

code4验证及交付：真实SDK契约测试3项与既有AI合计51项全部通过；Python AI静态检查9项通过；Mock组装与androidTest编译通过，Astra独立代码/最新XML证据复核通过。以最终源码fresh重包后再跑相同51项AI回归通过（2秒），未运行完整仓库verify。最终文件 `/tmp/openhoyi-ai-diagnostics/OpenHOYI-AI-schema-code4.apk`，25,097,443 bytes；SHA256 `f2a5f93b0bd9f9667aead44ce7bb6681f7d644d2f70f3945453f3d81559dcc0a`；包 `io.openhoyi.mobile.mock`，code4/name `0.1.0-local.ai-schema4`，v2验签通过，证书仍为 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。已保存Library。无真实API/Key、设备操作、提交/推送。等待用户用“你好”复测；不能声称400根因已确定或已恢复。

code4用户仍失败后的完整系统核对及code5纯诊断交付，见 [HTTP400审计](ai-chat-http400-audit.md)。code5请求参数未改，仅增加经官方完整模板精确匹配的TOOLS_FEATURE_CONFLICT及构建版本前缀，不再做猜测性兼容试验。52项AI/9项Python及构建、独审通过；真实根因与恢复待用户复测。
