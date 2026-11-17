# AI 图片与文档输入：本地附件基础

> 当前状态（2026-11-16）：本地基础模块已被[附件会话集成](ai-attachment-integration.md)与[模型/OCR集成](ai-model-ocr-integration.md)接入UI、加密会话、协议与发送。图片/UTF-8文本可发送；PDF/DOCX/PPTX采用独立OCR先收费提取实际文字、后确认聊天发送的两阶段流程。下方独立文件与15项测试是最初基础模块的历史范围，不代表当前仍未接入。

- 需求与范围：输入框左侧 `+` 选择图片或文档；选取不调用模型、不上传。Sol 本轮独立实现本地校验、系统 picker adapter、加密副本和 fake 单测；协调线程接入会话、界面和发送，Astra 独立复核。用户最新要求不提供视频入口或视频实现。
- 来源分支/基线提交：现有 `feature/ai-chat` 基线 `599670aab75d94070bf836cb813239e30e3e46e7`，本工作树含大量其他未提交 AI 工作，全部保留，不另切分支或重置。
- 工作分支 / PR目标：`feature/ai-chat` / `develop`。本轮不提交、不推送、不创建 PR；后续遵守 [Git工作流](../git-workflow.md) 及统一 `scripts/create-pr` 入口。
- 独立文件：`AiAttachment.kt`、`AiAttachmentImages.kt`、`AiAttachmentDocuments.kt`、`AiAttachmentVault.kt`、`AiAttachmentAndroidSource.kt`、`AiAttachmentTest.kt`、`AiAttachmentVaultTest.kt`。本轮不编辑共享 Activity、Engine、ProtocolHistory 或 SessionStore。
- 约束：最多3件、单轮20MiB；JPEG/PNG/静态WebP源4MiB/16MP/边长16384，解码有界、尊重EXIF方向，重新编码长边1600、JPEG不超过1MiB并移除位置等元数据。拒绝APNG/animated WebP，不静默取首帧。UTF-8 TXT/Markdown/CSV/JSON合计16KiB，拒绝非法UTF-8、二进制控制字符和bidi控制。
- 文档：PDF/DOCX/PPTX单件10MiB，按 MIME 和容器内容校验。OOXML限制条目数、展开总量、压缩比、路径并核验对应Word/PPT核心条目，不保存展开正文。旧DOC/PPT当前拒绝，需转DOCX/PPTX，不以OLE magic冒充准确识别。格式校验不保证供应商最终解析成功。当前UI使用独立OCR text_recognition，仅purpose+file，不存Files API、不带expires_after；第一次同意解析费用、成功密文保存实际文字并检查预算后，再次同意聊天发送。备用Files上传模块AiDocumentUpload显式24小时TTL，未接当前UI，不能与OCR存储/过期语义混淆。选择不向DeepSeek上传，系统云文件提供方可能联网。
- 存储：副本只进应用 `noBackupFilesDir` 专属附件目录；独立Android Keystore AES-GCM alias，AAD绑定凭据epoch与随机id，密文内校验完整metadata。源URI不持久化、不日志。最多64MiB/128副本，超额拒绝，不自动清未知/引用中数据。删除仅作用于显式附件id对应的新副本，不触碰用户原文件。历史与草稿引用的回收由会话协调层在核实引用后执行。
- 接口：`AndroidAiAttachmentSource(context).prepare(uri, existing)` 仅本地IO；`AiPreparedAttachment.attachment/bytes` 通过 `use` 擦除临时bytes；`AiAttachmentVault(context, ownerEpoch).save/load/remove`。源流和图像均有界；picker/DocumentProvider与Android解码行为仍需动态验证。
- 验证：fake 本地字节、fake image processor、JVM AES/ciphertext storage，不读真实Key或附件，不调用付费API。本轮使用缓存 Kotlin 2.3.10 compiler + Android 35 android.jar 对7个独立Kotlin文件编译通过，凭据接口使用 `/tmp` 同签名stub；随后真实 JUnit4 执行 `AiAttachmentTest` / `AiAttachmentVaultTest` 共15项通过（0失败）。此隔离检查不启动Gradle、不代表完整app编译，也不验证真实Android adapters。后续真实Gradle集成与131项模型/OCR验证记录见上方链接；不同阶段证据不相加或冒充设备验收。未完成Android picker/实际图片解码/Keystore/noBackup/配置重建动态验收，不能把静态或JVM证据当成动态通过。
- 协议与设备风险：不修改BLE、机器控制、曲线写入、history/response预算或请求协议；不安装、不操作实体设备。
- 发布 / 签名 / 升级：不发版、不改版本/签名，不安装。后续合入不代表设备验收。
- 回退：撤回本轮新增模块；已有密文保持，不删除原附件、未知记录或设备数据，不以卸载清数据恢复。
- 当前未验/限制：UI/session引用、wire预算、持久化兼容与收费同意已接入并有JVM/fake证据；真实Android picker/Keystore/进程恢复及供应商OCR/模型API尚未验收。旧DOC/PPT明确不支持，用户需转换格式。64MiB/128副本满额后的用户可见存储管理未实现；未知文件保留占额，当前拒绝新增，不自动清理。
