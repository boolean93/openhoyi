# 独立搜索统计口径核查

- 范围：当前client function web_search→POST /v1/tools/search（单query、count=3、need_content=false），仅澄清本机尝试/响应分类与账单差异；不扩多query、server工具、fetch或金额估算。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。已读AGENTS及现有usage规划；按[Git工作流](../git-workflow.md)/[模板](task-template.md)保留其他工作，不提交/PR/push/合入。
- 分工/文件：Sol唯一写者/串行Gradle，Astra独立只读审；仅两语ai_usage.xml、AiUsageTest及本计划，不改counter/密文schema、模型cache逻辑、epoch或上传事务。
- 官方：[Search API](https://api-docs.deepseek.com/api/search/)公开response是results，没有独立结算单位或Token账单字段；不能造bill_units、把缺字段写成已测零。[Search guide](https://api-docs.deepseek.com/guides/search/)独立API按HTTP200+非空results的成功请求计费，count只影响结果数；官方服务端失败/空结果/超时不计费，但客户端超时/取消/丢响应不能证明服务端失败。当前客户端状态不能当账户账单。
- 官方：[Pricing](https://api-docs.deepseek.com/quick_start/pricing/)当前独立搜索每1000成功请求USD0.75，模型输入/输出另计且cache hit/miss不同价；本页不估金额，不固定价格进运行时。[Web tools](https://api-docs.deepseek.com/guides/web_tools/)是另一server路径，其请求块数量与tool_search等计费单位不同；本应用未声明，不能将该元数据套入独立API。
- 实际口径：联网前begin记一次attempt，完整无error的results非空/空分别分类；HTTP非成功记failed；HTTP200业务error/坏schema、客户端超时/取消为unknown，不重试。原始非空响应即使链接被本机安全过滤仍保留nonempty计数，不能把无可显示引用说成免费。新文案明确‘请求尝试/非空响应/空响应/HTTP失败/未确认’，搜索费用未确认、以服务账单为准。
- 模型统计保留：本会话与本机累计，跨历次Key，不是账户总量；只有返回有效usage才算reported Token，缺失unknown非测得零。cache按有效hit/(hit+miss)加权与字段覆盖率，不重复加Token；多模型桶不变。日志/存储仅计数和受限模型名，不保存搜索词/结果/Key。
- 验证：新单一fake序列验证非空但引用被过滤、空、HTTP失败、HTTP200业务error、客户端超时5次attempt分别归类且不重试，非法query不读Key/不联网/不计attempt；复用已有14项Usage与4项WebSearch。只JVM fake及资源编译，无真实账单/服务/设备证据。
- 协议/设备风险：仅文案与测试，无生产搜索/统计协议改变；不读真实Key/用户数据、不付费、不设备/CU/机器。
- 发布/签名/升级：不发版、不改版本签名、不安装，133及原子发布/132epoch成果保留。
- 回退：仅撤回本次文案和测试，保留本机计数与未知请求；不重置统计或用户数据。
- 未测：真实账户搜索费用和Android统计UI动态布局，不将fake或官方规则称结算实测。

## 实际结果

- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiUsageTest --tests io.openhoyi.mobile.AiWebSearchTest`。
- 最终19项（Usage15/WebSearch4）0失败/错误/跳过，BUILD SUCCESSFUL in 18s；两语资源首轮processMockResources实际编译通过，主代码UP-TO-DATE。未扩lint/assemble/无关矩阵。
- 首轮新增fixture过度比较完整过滤结果，遗漏合法untrusted_web_data标记；改仅检查status语义，保留已有独立不可信标记断言，无生产counter/parser变化。最终5尝试=非空1/空1/HTTP失败1/未确认2，无自动重试；非法query不读取Key/不联网/不增加次数。
- Astra最终独立只读审查无阻断；确认两语文案、测试和计数schema不变及官方边界。
- 最终日志`/tmp/openhoyi-search-usage-tests-final.log`；首轮日志`/tmp/openhoyi-search-usage-tests.log`；计数`/tmp/openhoyi-search-usage-test-counts.json`。
- before `/tmp/openhoyi-search-usage-before`，4文件精确diff `/tmp/openhoyi-search-usage.diff`，前后SHA `/tmp/openhoyi-search-usage-manifest.json`。
- 未读取真实Key/账单/用户搜索或文件、未访问设备/付费服务/CU/机器；未改签名版本/安装/133快照/epoch/原子发布成果，未推送/PR。真实服务账单与Android UI仍未验收。
