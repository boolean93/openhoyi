# 持续会话、上下文压缩与不完整回复

- 需求与范围：取消 12 轮/可见累计 24K 字符拒发。可见完整历史独立于模型工作上下文；约 200K token（256K 工作窗口） 时单次有费用模型摘要完整旧轮，保留最近完整协议。截断回答保存安全 content 并永久标记未完成，不执行截断工具、不自动续发。
- 来源分支/基线提交：现有 develop 来源 feature/ai-chat，HEAD 599670aab75d94070bf836cb813239e30e3e46e7；origin/develop fc309bc792218b6a01feeb6f780bb979cc99c42d。按指定继续现有未提交 AI 工作区，不切分支覆盖并发工作。
- 工作分支：feature/ai-chat；PR目标：develop。
- 改动文件与步骤：新增 AiContextPolicy/AiContextCompactor；最小接入 AiChatPolicy/Engine/ProtocolHistory/ResponsePolicy/SafeTransport/SessionStore/Activity，中英资源与对应单测。先保存本轮改前文件于 /tmp/openhoyi-context-before 以生成精确差异。附件、曲线模块不在范围。Astra 只读设计及最终独立复核，Sol 实现测试。
- 推送与合并：本轮不提交、不推送、不创建 PR；后续另由协调者按 Git 工作流和 ./scripts/create-pr 执行。
- 官方依据（2026-11-15）：https://api-docs.deepseek.com/quick_start/pricing/ 模型 deepseek-flash，窗口 1M token，最大输出384K；https://api-docs.deepseek.com/api/create-chat-completion/ max_tokens 范围1..393216、thinking默认64K；https://api-docs.deepseek.com/guides/thinking_mode/ 携带tools时历史reasoning参与上下文。length可能是输出或上下文触顶，现场2000预算是嫌疑，不是完全证实根因。
- 移动端预算：模型能力与本地预算分离。256K 工作预算，8K 输出（thinking及回答共享）、4K 工具/框架预留，约200K token开始摘要；新增文本按UTF-8字节上界保守估算（可能提前触发），最近provider usage对应已提交工作上下文输入+输出，作为历史基线，不伪装精确tokenizer。128KiB响应/4MiB请求/3MiB完整协议边界分别独立报错。取消4000字业务限制，单条仅512K UTF16内存保护；本机可见历史有显式存储容量保护，满时停止发送保留历史与草稿，不能静默删文。
- 摘要：单次无工具、关闭thinking、有限输出，摘要内容作为不可信历史数据，不能成为system指令或恢复写授权。最近完整轮原样保留；仅把完整旧轮压为摘要，失败保留原上下文和草稿并停止本次发送，无自动重试。摘要成功通过持久化回调提交后才发用户问题，恢复只读不发请求。
- 成本：输出预算是上限不是固定消费；实际thinking也计输出。提高至8192可增加实际费用，四次模型调用预算保持，摘要有独立的一次调用上限并计入原usage账本；费用告知更新consent版本，旧同意不默认为同意新摘要收费。
- 成本测算：按上述官方页面当日Flash输出峰值$1.2/百万token，8192输出的理论上限约$0.00983/模型调用（原2000约$0.0024），不含输入、检索；非thinking摘要2048输出上限约$0.00246加摘要输入费。实际按返回usage计数，缓存与时段影响账单，不硬编码价格。
- 验证证据：假数据/Mock单测、完整模块单测、Debug/Mock构建、lint，独立代码审查；不访问真实Key/用户数据，不调用付费API，不操作设备/机器/Computer Use。
- 协议与设备风险：保留最近轮 reasoning/tool_call/tool_result完整组；未知结果不重发，不恢复确认或执行写操作。摘要存在信息损失，明确显示压缩状态且原可见历史保留。不涉及BLE协议。
- 发布：不发版、不安装；不更改包名、签名、versionCode。schema2读取schema1迁移，保留Key epoch隔离和加密存储。
- 回退：按本轮精确差异恢复源码，不覆盖并发文件；不卸载/清数据。旧版本不能读schema2，不降级覆盖安装；恢复能力需继续保留schema2读取。
- 未完成与阻塞：真实API、设备和硬件验收未授权，不以本地测试代替。相关 .agents/skills 在工作区及父目录均不存在；用户级 .agents/skills/autocli 为浏览器任务，不适用本次代码实现。

## 实际验证与交接

- 2026-11-15：使用既有 JDK17/Android SDK，离线运行 `./gradlew --offline --no-daemon --console=plain :bean-core:test :protocol-core:check :device-session:check :trace-core:test :bluetooth-android:testDebugUnitTest :app:testDebugUnitTest :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:lintDebug`，`BUILD SUCCESSFUL in 2m 34s`。Debug 937 tests、Mock 944 tests，均0失败，各1既有跳过；device-session 833、bluetooth 9、app21均0失败。日志 `/tmp/openhoyi-context-build.log`。最初沙箱锁服务失败，随后获准以既有缓存离线执行；未下载模拟器或调用真实API。
- 新增8项上下文测试覆盖200K前后、超过12轮/4000字、无工具单次摘要、保留最近完整工具事务、摘要失败不重试、截断工具不执行与reasoning续聊、schema1/2恢复、摘要checkpoint后回答失败不重复摘要、过大新输入/最近轮不产生无益付费。复用现有Key epoch隔离、取消及持久化失败测试。
- Python静态检查：`test_ai_credential_policy.py` 6项、`test_ai_session_layout.py` 1项、`test_ai_android_acceptance.py` 3项通过；`localization/validate_catalog.py` 和 `localization/android_resources.py --check` 通过。这些不是设备验收。
- Astra独立只读复核：reasoning三态保留、无益摘要预检、checkpoint恢复和触发线/硬上限区分的问题均修复并有测试；最终结论为本范围内无剩余阻塞。未以实现者自查替代。
- 共享接口：`AiChatEngine.reply(..., onCompacted)` 在摘要成功后、用户问题发送前同步调用；回调需返回持久化成功。`AiChatViewState.contextGeneration` 在压缩/清空时递增，后续native曲线确认必须绑定它并取消旧proposal。本轮尚未接入曲线确认UI，不能宣称完整曲线写入验收。附件接入需扩展token估算，不能按file_id字数估计图片/文档真实token。
- 精确差异：`/tmp/openhoyi-context-task.diff` 以任务前快照加已确认并发曲线增量为比较基线，排除独立曲线实现；`/tmp/openhoyi-context-integration.diff` 保留原快照至当前的整合差异用于审计。清单 `/tmp/openhoyi-context-files.txt`，源码哈希 `/tmp/openhoyi-context-source-sha256.txt`。不执行回滚、不提交其他人的文件。
- 限制：UTF-8上界不是tokenizer，缺少usage时可能提早compact；摘要语义正确性、接近200K真实token的服务端行为、长会话设备性能和三档字号布局未做实机验收。保留32MiB加密文件、4M可见字符/20000行容量保护，触顶显式停止，不删历史。旧版本不支持schema2，不能用降级覆盖替代兼容恢复。
