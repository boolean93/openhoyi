# AI 附件会话集成

> 当前状态（2026-11-16）：图片与UTF-8文本已接会话/发送；PDF、DOCX、PPTX已由后续[模型/OCR集成](ai-model-ocr-integration.md)接通独立OCR两阶段流程。先单独同意文档文字识别费用，成功密文落盘并检查实际内容预算，再次发送/确认聊天费用。实际UI不使用备用 `AiDocumentUpload`/Files API，OCR不带Files过期字段。下方98项验证等为早期集成阶段记录，不是文档仍不能发送的当前结论。

- 需求与范围：沿用现有样式接入左侧＋、图片与文档本地选取、加密草稿/历史manifest与发送确认，无视频。图片、本地UTF-8文本完成wire闭环；早期Files API路径因解析正文不可下载而阻止文档发送；后续已采用独立OCR返回实际文字的两阶段流程，PDF/DOCX/PPTX发送不再受此早期阻碍。OCR仅文字，不承诺图表/布局理解；旧DOC/PPT仍拒绝。
- 来源/工作分支：develop来源的既有feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；保留全部未提交工作、不另建重复工程。未来目标develop。
- 文件/接口：复用Attachment Source/Images/Documents/Vault；新增AiAttachmentConversation manifest与临时wire展开。AiChatLine携带manifest；Session schema3兼容1/2并存附件草稿；Protocol保留本地manifest，出站前展开为content array，base64不进入持久协议。Activity为唯一协调者，Presentation只展示/回调。保留曲线确认链、legacy epoch与200K compact。
- 分工：Sol实现，Astra只读独审；共享文件唯一写者，Gradle串行。.agents/skills在工作区和父目录均不存在。
- 推送/合并：本轮仅本地，不提交、不推送、不PR、不合入；未来遵守Git工作流和scripts/create-pr。
- 验证：离线fake/JVM重点覆盖manifest恢复、损坏拒绝、实际图像/文本预算、wire不泄漏manifest、历史协议一致与compact。编译/lint相称检查；不以fake冒充设备/服务证据。
- 协议风险：每图按官方vision上界1024 tokens预算，文本按UTF8字节保守估算；历史图片总量受移动端临时请求内存边界，必要时提前compact并透明提示。不重传过期远端文件、不接入解析上传、不自动重发。选择不向DeepSeek上传；系统云文件提供方可能为读取所选文件联网。
- 官方依据：https://api-docs.deepseek.com/api/create-chat-completion/ 、https://api-docs.deepseek.com/guides/vision/ 、https://api-docs.deepseek.com/guides/document_parsing/ （2026-11-15只读核对）。文档完整内容常驻服务端上下文且不可下载，file_id不能作token预算。
- 发布/签名/升级：不发版、不改版本/签名，不安装。保留132只读epoch；schema1/2读取兼容，旧版不能读取schema3，禁止数据清除式降级。
- 回退：代码回退须保留schema3解码和密文，不删除用户原文件、未知副本、凭据或会话；不卸载/清数据。
- 限制：Android picker/Keystore/真实服务/设备体验待验，PDF/DOCX/PPTX两阶段OCR已启用，真实服务与设备验收尚未完成。

## 实际完成与证据

- `:mobile:compileMockKotlin` 首编译通过（39s，`/tmp/openhoyi-attachment-compile.log`）。最终 `testMockUnitTest` focused 12类共98项，0失败/0错误/0跳过，随后 `:mobile:lintMock` 通过；串行命令总耗时2m6s，日志 `/tmp/openhoyi-attachment-tests-final.log`，机器可读统计 `/tmp/openhoyi-attachment-evidence.json`。未运行无关全量946项。
- 新增11项 `AiAttachmentConversationTest`：manifest严格校验/重复id、wire临时content array和擦除缓冲、真实图像/文本预算、schema3附件草稿与历史恢复、schema1/2兼容、缺失副本及收费文档发送阻断、伪造元数据/assistant附件拒绝、多轮协议重放、摘要看到实际旧图片且用户正文仅一次、真实schema预算、超预算摘要联网前失败。复用既有附件15、上传12、上下文8、协议9、会话11、epoch4、曲线13、会话工具9、Markdown6项回归。均为JVM/fake，不代表Android/真实服务。
- 曲线独立模块与KeyStore未修改；附件选择/移除递增contextGeneration并cancelCurveProposal，compact原有同一失效路径保留。协议完整工具事务原样保留，摘要无tools/无批准权。Mock132 legacy兼容两文件SHA256仍与132证据一致（KeyStore `1057f580dbe0dcfa6e7b3015fff7ae0e1597250a29448acf042d1f9116a2ae32`；epoch测试 `e1d5d756109d9abee3d2da759ec36d0f46255c61a14a6f8857b474bd231045b5`）。
- 独立Astra只读终审通过，预审指出的取消副本清理、移除checkpoint、provider取消信号、真实schema与摘要预算均已修复。取消/迟到本次新副本以NonCancellable IO清理；显式移除及新会话仅在持久化成功后清理已知不再被历史/草稿引用的副本，不触碰用户原文件或未知文件。
- 精确本轮差异：`/tmp/openhoyi-attachment-integration.diff`；前后源码与hash清单：`/tmp/openhoyi-attachment-source-manifest.json`；原共享文件快照：`/tmp/openhoyi-attachments-before`。这是相对本轮开始共享源码的差异，不以HEAD差异混入其他未提交开发。
- 剩余边界：getType无CancellationSignal，恶意或失常DocumentProvider可能不响应query/open/close取消，不能宣称强制中止全部provider；Android picker、实际解码、Keystore、字体/窄屏布局与服务wire仍待真实验收。旧epoch/未知副本保留并占用全库64MiB/128副本上限，未自动清理；容量不足明确拒绝，后续需要用户可见存储管理。schema3旧客户端不可读，回退须保留schema3读取，不得卸载清数据。
- 早期本阶段曾仅支持PDF/Office本地草稿；该限制已被后续独立OCR集成取代，当前PDF/DOCX/PPTX可在两次明确同意后提取文字并发送。未接入备用DocumentUpload/Files API，不存在Files默认过期或自动重传路径。图片/文本选择不向DeepSeek上传；系统云文件提供方可能联网。发送前说明目的地、模型/摘要重复输入费用、16MiB临时请求与6MiB历史图片提前compact边界。
- 本轮无APK发布、安装、设备/真实Key/咖啡机/Computer Use/真实附件上传/付费调用，无提交、推送或PR；Gradle已完成并释放。

## 当前验证与未完成边界

- 后续模型/OCR集成131项、预算修复33项、恢复29项与发布事务13项等为各阶段独立JVM/fake证据，不相加冒充唯一用例数。Mock136仅真实Android合成界面与内存codec；系统picker、真实provider撤权、AndroidKeyStore/磁盘进程恢复、真实OCR/模型API仍待验，Mock存储probe实现不等于已执行。
- 64MiB/128副本配额会计入未知旧epoch/中断孤儿；当前安全保留、超限拒绝。用户可见存储占用/安全管理界面未实现，不能自动删除未知对象以绕过配额。本轮不启动该新数据清理流程。
