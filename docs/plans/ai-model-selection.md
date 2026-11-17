# AI模型选择：历史规划与当前实施状态

2026-11-16：已按最新授权实现可信官方本地 Flash/Pro 目录与独立加密会话槽，详见 [模型与OCR集成](ai-model-ocr-integration.md)。未请求当前账户 GET /models，不能宣称账户权限已验证。下面保留原规划作为历史，不再将其中未实施状态视为当前结论。

用户反馈目前不能选择模型。当前已验证的只是已配置DeepSeek账户的固定deepseek-flash请求成功；没有调用账户GET /models，不能把官网列表当该账户实际可用列表，也不能默认用户选择了其他提供商。

## 目标和设计待确认

- 默认保留当前可用模型，不自动切换提供商、降级high safety、移除只读工具或扩大费用/超时预算。
- 以当前提供商官方GET /models及能力元数据为模型ID/支持能力的依据，使用应用内凭证到该提供商发送边界；Key不经过助手、日志或UI回显。能力元数据不足时结合官方组合约束，不能仅因“支持tools”推断high safety/thinking组合全支持。
- UI只展示一个当前模型入口，点击后选择；不把密钥/提供商/模型/安全/费用开关全部堆进聊天页。入口位置、窄屏与三档字号布局交Astra对照实际页面确认，不机械新增一排按钮。
- 模型ID和能力必须区分。灰显/解释不兼容的模型，不静默关安全或退回另一模型。提供商切换单独设计，新的凭证、数据接收方及计费含义需要用户明确选择。
- 价格来源、输入/输出/推理/安全tokens以及是否需要新的费用确认不能由模型名猜测。刷新模型列表不等于授权推理调用；不得借刷新偷偷发送聊天/本地数据或付费probe。
- 完整协议历史与模型/提供商/Key revision绑定。切换时不静默丢失会话，也不把旧会话/推理/工具结果自动传给新提供商；是否开启新会话以及如何保留旧显示内容，先确定体验与授权。
- Key变化使缓存失效；网络失败时明确列表未刷新，不伪称账户不支持或继续显示其他账户权限。当前固定模型只作现状，不声明模型选择功能已完成。

## 工作流与验证

沿用仓库Git工作流，从合适基线创建独立功能范围，目标develop；当前工具链PR审批阻塞仍未解除，不能因本规划绕过。先确认模型/提供商/安全与费用体验，再实现；验证实际GET模型响应、能力缺失/不兼容、错误/取消/Key变更、会话切换及代表性窄屏字号，不扩大无关测试矩阵。不发布、不安装或调用付费API，除非相应授权覆盖。

官方参考：[Models API](https://api-docs.deepseek.com/api/list-models/)、[工具调用](https://api-docs.deepseek.com/guides/tool_calls/)、[思考模式完整协议](https://api-docs.deepseek.com/guides/thinking_mode/)、[内容安全](https://api-docs.deepseek.com/guides/safety/)。实际模型API结构实施前重新核实，不假设账户响应已验证。

## 官方字段核实（仅文档，未请求账户）

已读GET /models当前参考：id是调用ID；可选name用于选择器，context_window/max_output_tokens给上下文/输出上限，input_modalities/output_modalities区分模态，effort.supported_levels/default_level给思考强度。字段可缺失，缺失不是“不支持”。api_capabilities按协议声明，当前文档示例仅展示anthropic_messages.system_prompt_update，不能据此自动推出Chat Completions的工具+high safety全部组合兼容。以上不是已登录账户响应，未来实现仍需在用户授权的当前提供商/Key作用域取模型列表并处理缓存/失败，不默认新提供商或新增消费。
