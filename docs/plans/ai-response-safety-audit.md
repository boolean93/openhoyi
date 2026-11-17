# 非流式响应安全核查

- 范围：实际POST完整响应路径；防止HTTP200混合error/未知finish/畸形refusal进入工具或成功历史。保持safety high及既有length安全未完成文本路径，不新增stream/server web tools。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。本次不提交/推送/PR/合入，保留所有其他工作，遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)。已重新读取AGENTS；仓库/父目录无.agents目录。
- 分工/文件：Sol唯一写者及串行Gradle，Astra独立只读审。AiResponsePolicy、AiWebSearch、AiUsagePolicy、AiResponseSafetyTest及本计划；不动Activity、曲线授权、epoch、持久化schema。
- 官方：[Safety](https://api-docs.deepseek.com/guides/safety/)要求high审完整输入输出；最终content_filter或message.refusal须拒绝。即使拒绝也可能计费，usage不能因拒绝清零。[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)非stream finish允许stop/length/tool_calls、content_filter、insufficient_system_resource、aborted；后三种按拒绝/网络失败，其余未知/缺失/非字符串关闭。
- 官方：[Web Tools](https://api-docs.deepseek.com/guides/web_tools/)区分server工具和直接Search/Fetch；当前仅client function web_search→独立/tools/search，未声明deepseek_web_search/deepseek_read_url，不需新增server_tool_calls/signature持久化。本轮不扩能力。
- 实际链：agent.run→KoogHttpClient.post→SafeTransport有界完整body→先记usage→ResponsePolicy→ProtocolTurn→SDK工具/最终答复→Activity成功才追加历史。sse/lines拒绝；无增量UI，故partial后refusal撤回和无DONE流式测试不适用，不新增stream。失败finally取消pending curve，不产生原生写批准；visible/history/draft保全沿用。
- 最小修复：非null root.error拒绝（不读出错误正文），finish_reason只允许已知完成状态；refusal非空字符串或畸形非null类型均拒绝，null/空串兼容；以上在length保留之前。Search error+results变unavailable/UNKNOWN，防止错误误记有效引用。合法拒绝usage仍保存，缺usage仍unknown非测得零，不自动重试。
- 验证：新整合fake验证错误/拒绝/未知finish+proposal零派发、length+refusal不保留、不成功commit、单次网络、high/非stream、usage保留/缺失unknown、search混合error不引用；复用既有ChatSession/WebSearch/Usage及length相关最小回归。日志不存Key/正文。
- 协议/设备风险：更严格拒绝不完整/非法成功体，不会降安全或重发。无设备/Key/用户文件/付费API/CU/咖啡机。
- 发布/签名/升级：不发版、不改签名版本、不安装，133快照/APK不变。
- 回退：仅撤回本轮差异；不删原始会话/草稿/未知请求或其他未提交工作。
- 未测：真实服务拒绝/收费和Android UI路径；fake不等于服务/设备验收。

## 实际验证结果

- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiResponseSafetyTest --tests io.openhoyi.mobile.AiChatSessionTest --tests io.openhoyi.mobile.AiUsageTest --tests io.openhoyi.mobile.AiWebSearchTest --tests io.openhoyi.mobile.AiContextPolicyTest`。
- 最终42项（Safety4/Session9/Usage14/Search4/Context11）全部通过，0失败/错误/跳过，BUILD SUCCESSFUL in 54s；compileMockKotlin实际执行通过，资源UP-TO-DATE。首轮即通过，无lint/assemble/完整矩阵。
- 10个有界错误/拒绝fixture均完整HTTP200、1次网络、proposal零执行、不生成partial/success、原历史不变，合法refusal null/空字符串及error null仍兼容。所有失败中的有效usage仍记录，缺usage为unknown而非测得零。直接搜索混合error+results不引用、不计NONEMPTY/EMPTY、不自动重试。
- 日志`/tmp/openhoyi-safety-response-tests.log`，计数`/tmp/openhoyi-safety-response-test-counts.json`；before `/tmp/openhoyi-safety-response-before`，精确5文件diff `/tmp/openhoyi-safety-response.diff`，前后SHA `/tmp/openhoyi-safety-response-manifest.json`。
- 无设备/真实Key/用户正文或附件/付费服务/Computer Use/机器操作；未改133、签名/版本、会话schema或132epoch；未推送/PR/合入。真实服务计费/拒绝与Android界面仍未实测。

- Astra最终独立只读审查通过，无阻断；3份生产源码SHA与审查版本逐一一致，已回读diff/测试及42项结果。
