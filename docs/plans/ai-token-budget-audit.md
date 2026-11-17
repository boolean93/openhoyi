# 256K / 200K 完整请求预算核查

- 范围：修复预compact漏算工具schema及usage替代本地完整估算，统一语义文本预算；不改可见历史、协议成对校验、待确认曲线写失效及摘要checkpoint/失败不自动重试。
- 来源/分支/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。本轮不提交、推送、PR或合入；保留全部他人未提交内容，遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)。
- 分工：Sol唯一写者和Gradle；Astra独立只读复核。当前仓库/父目录无.agents目录；沿用已读AGENTS及现有context/附件/model/OCR规划。
- 文件：AiContextPolicy、Compactor、ChatEngine、ProtocolHistory、AttachmentConversation，3类focused测试，两份ai_chat.xml及本计划。
- 验收：完整system、历史roles/reasoning/tool calls及参数/结果、待发内容、同源工具schema、图片1024上界、OCR实际语义文字字节，预留8192输出与4096工具空间。200000估算阈值触发摘要，262144应用硬预算不扩大。
- 实现：完整本地估算与provider usage取较大值，usage不会降低本地预算。Session缓存自身ToolRegistry，Engine从同源descriptor与现有Koog OpenAI schema生成器取得schema预算（附512包装余量），传入所有compact判定及实际wire上限检查；真实schema超出预算拒绝发送。无工具执行/本地读取。UI独立预判默认16384 schema余量，focused验证大于当前真实schema；未来schema超出默认须同步调整，运行wire门禁拒绝低预算。
- 文本：语义JSON树逐字符串UTF-8字节和结构余量计数，不把传输中的反斜杠转义当成额外模型正文。附件元数据size是已验证的实际文本字节，另加512提示/结构；不必为预检解密内容，不采用六倍文本上界。图片按1024上界，临时Base64不计为模型文本，独立传输/图片内存门禁保留。
- 协议/设备风险：估算可能提前触发收费摘要，仍须既有明确同意；完整历史保留，仅工作上下文摘要。工具追加请求每次检查完整wire，未来工具余量不是保证容纳任意工具结果；超限停止，不自动裁文或请求。无设备/Key/付费调用/Computer Use/咖啡机。
- 发布/签名/升级：不发版、不改版本/签名、不安装。会话schema与Mock132只读epoch不变。
- 回退：仅撤回本轮差异；不删除原始历史、草稿、附件、未知记录或既有工作。

## 官方计数依据及限制

[Token & Token Usage](https://api-docs.deepseek.com/quick_start/token_usage/)提供一般字符比例、离线tokenizer示例和usage依据；明确不同模型分词可不同。未采用字符比例作为硬门，未下载或运行tokenizer，未安装依赖。该说明未给出当前Flash部署版本与压缩包/chat模板的精确兼容证明，不能宣称当前估算是官方精确token数。JVM中199999/200000仅为应用保守估算边界，不是实际tokenizer实测；usage仅记录服务端已经处理的请求，不能证明当前新schema或新增内容的完整预算。[Vision](https://api-docs.deepseek.com/guides/vision/)的1024图片上界沿用。

## 验证（待执行结果）

仅离线focused ContextPolicy/ProtocolHistory/AttachmentConversation及资源/主代码编译。复用现有摘要失败不重试、成对工具保留、恢复checkpoint、length未完成标识测试；新增usage不降低预算、schema跨200K阈值、真实SDK schema受预算覆盖、低usage不能绕过完整wire、转义OCR容量/预检覆盖。真实服务tokenizer/计费、设备均未测。

## 实际结果

- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiContextPolicyTest --tests io.openhoyi.mobile.AiProtocolHistoryTest --tests io.openhoyi.mobile.AiAttachmentConversationTest`。
- 首轮资源/主代码编译通过；唯一失败是旧附件fixture的总预算<20000断言未计新增默认schema16384，改成<schema+20000仍验证1MiB图片编码不当作token；未放宽生产门禁。最终33项（Context11/Protocol10/Attachment12）0失败/错误/跳过，BUILD SUCCESSFUL in 18s。
- 新增5例：usage不降低local（含大reasoning）、schema跨估算200K恰触发一次compact并保留可见/最近协议、同源六工具预算覆盖SDK真实输出schema、低usage+超预算reasoning在联网前拒绝、转义OCR按语义文本不六倍放大且预检覆盖wire。既有摘要失败/完整工具事务/恢复checkpoint/length标识均通过。
- 日志`/tmp/openhoyi-token-budget-tests-final.log`；首轮失败日志`/tmp/openhoyi-token-budget-tests.log`；精确计数`/tmp/openhoyi-token-budget-test-counts.json`。
- Astra独立只读审查通过，无阻断；生产五文件最终hash与审查版本一致。未重复全量lint/assemble/无关矩阵。
- 本轮before基线`/tmp/openhoyi-token-budget-before`；11文件精确diff及前后SHA为`/tmp/openhoyi-token-budget.diff`、`/tmp/openhoyi-token-budget-manifest.json`。旧133包/快照、会话schema及132epoch未改。本轮没有设备/真实Key/用户附件/模型API/付费请求。
- 局限：未运行官方tokenizer，未证实当前模型隐藏chat template/特殊token实际数量。结构和文本字节为保守工程估算，不能称真实199999/200000边界。UI预判用已被实际schema测试覆盖的默认16384上界，真实Engine使用同源descriptor算出的预算；未来SDK/schema增加须保持该断言与wire门禁。4096未来工具预留不保证任意新增结果均可容纳，后续每次工具请求仍受完整wire硬门限制，失败不自动重发或破坏成对协议。
