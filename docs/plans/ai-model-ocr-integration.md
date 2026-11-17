# AI 模型独立会话与文档文字提取

- 需求：Flash默认，允许明确选择Pro，采用每模型独立可恢复会话，不跨模型复用reasoning/tool协议。PDF/DOCX/PPTX经独立OCR(text_recognition)提取实际文字，先明确同意按页收费解析，成功密文落盘后再单独同意聊天发送；无图表理解承诺。
- 来源/工作/目标：develop来源的现有feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；保留上一轮附件与所有未提交，未来目标develop，本轮不提交/推送/PR/合入。遵守git-workflow及task-template。
- 分工/归属：Sol唯一AI共享文件写者及Gradle执行者；Astra独立只读审；根线程统筹设备，本线程无设备/Key/付费API/CU/咖啡机操作。
- 模型接口：AiModelPolicy可信本地官方目录Flash(text,image)/Pro(text)，均low thinking/tools/high safety，未验证账户可用，不自动fallback。Protocol绑定modelId；schema4保存active与最多1个inactive完整会话槽，整个容器32MiB，禁止递归、未知model、跨epoch。切换先停止/失效curve再原子保存候选，成功才UI切换。256K/200K/8K应用边界不变，usage记录实际requested模型，摘要同模型。附件清理考虑所有槽引用。
- OCR接口：独立AiDocumentOcr有界POST https://api.deepseek.com/v1/tools/ocr，仅purpose=text_recognition+file；复用原附件校验/vault，FilesAPI上传模块不挪作OCR。每文件状态先持久IN_FLIGHT再连网，恢复视UNKNOWN不可重试；成功密文正文先保存再状态READY，原文档仍保留。严格核object/purpose/pages/index/finish_reason，HTTP200error失败；usage为OCR参考不当chat tokens或账单；length结果显著标记、不自动送chat；超限保留结果明确说明，不截断、不重传。
- 费用/安全：选择不联网，OCR与chat两次独立明确同意，解析断开可能仍收费。元数据不提供原始页数预报价，不假造。文字按真实UTF8保守估算；工具确认不从附件、摘要或旧槽继承授权。
- 验证：离线fake状态机/协议/恢复/预算/错误/未知结果/epoch隔离，加模型/附件/curve/compact/searchusage必要回归与compile/lint；不付费probe。改前快照/tmp/openhoyi-model-ocr-before，最终给精确diff与统计。
- 官方依据(2026-11-16只读)：api/list-models、quick_start/pricing、guides/thinking_mode、guides/safety、api/ocr、guides/ocr。目录仅文档能力，非账户授权。FilesAPI retrieve不提供解析tokens/pages，OCR返回actual pages.text从而可先计预算。
- 发版/签名/升级：不发版、不改版本/签名、不安装。schema1/2/3默认Flash兼容、保留132legacyepoch；schema4旧客户端不可读，不以降级清数据回退。
- 回退：保留schema4读取及原始/结果密文，不删除其他槽/未知副本、Key或用户文件。不自动重试/补偿未知计费。
- 未测：Android picker/Keystore、OCR/模型真实账户与设备动态体验、视觉定稿均不在离线证据内。

## 实施结果

- 已完成：更多菜单Flash/Pro选择；切换前说明本地官方能力、账户权限未验证、Pro纯文字/通常更高费用、两槽保存和无跨模型协议；切回恢复完整历史、草稿、usage、附件、OCR状态。schema4兼容1/2/3默认Flash，最多两槽、同epoch、禁止递归/重复模型、整体32MiB。成功slot checkpoint与usage generation/base更新同锁；失败保持原槽。
- PDF/DOCX/PPTX使用独立OCR text_recognition，不理解图表；旧DOC/PPT仍按原校验拒绝。选择仅本地；第一次发送请求单独费用同意后，IN_FLIGHT持久化在网络前，成功文字进独立OCR_TEXT密文副本（单件1MiB、HTTP响应2MiB），原始文档保留；READY后必须再次点发送并同意chat费用。原FilesAPI AiDocumentUpload未改用，OCR不接expires_after或远端file_id，不存在过期自动重传。
- READY文字按实际UTF8保守估算进入256K/200K模型上下文与摘要；OCR usage仅参考计数，不写chat/search账本；UI显示实际识别页数和参考输入/输出tokens并明确非模型计费。length显著标不完整并阻止chat；超预算保留已落盘结果/原稿，超响应接收或提取存储上限无法保留完整结果，文案明确区别，不截断、不自动重试。HTTP200error、页序/计数不符、usage非法、损坏UTF8/未配对代理字符均失败关闭。
- cancelOcr与独立AiOperationFence覆盖newConversation、Key变更、onPause/旋转；epoch/model/context/operation/revision任一变化拒绝旧结果，旧finally不改新busy/job。IN_FLIGHT恢复成UNKNOWN，恢复/切槽均不自动请求。已成功文字先保存密文再状态checkpoint；失败不自动重发。所有槽+OCR原稿/结果引用参与本地清理。
- 本轮实际操作仅离线源码与fake；未读真实Key、未请求账户模型目录、未触发真实OCR/模型/搜索、未操作设备/CU/咖啡机、未安装/改签名版本/发布、未提交/推送/PR。

## 验证与独立复核

- 首编译暴露一个operation局部变量缺失，已修复；首focused暴露旧usage fixture缺少model，已按真实请求补model字段而非把未知请求冒记Flash。
- 最终命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest` 限定16类focused（ModelPolicy、DocumentOcr、AttachmentConversation、ContextPolicy、ProtocolHistory、SessionStore、CredentialEpoch、ChatSession、Usage、WebSearch、CurveWriteCoordinator、CurveDetail、Attachment、AttachmentVault、DocumentUpload、Markdown），随后 `:mobile:lintMock`。
- 最终16类131项，0失败/0错误/0跳过，compileMockKotlin随任务重新编译通过，lintMock通过；BUILD SUCCESSFUL in 2m21s。日志 `/tmp/openhoyi-model-ocr-tests-final.log`，机器统计 `/tmp/openhoyi-model-ocr-evidence.json`，lint报告 `mobile/build/reports/lint-results-mock.html`。没有增加无关全量矩阵。
- 模型8项新增：目录能力与未知ID、两模型完整协议/草稿恢复、坏schema/跨epoch/递归/总上限、跨model联网前拒绝、未知费用实际requested模型、同模型摘要、slot保存与晚usage竞态、失败保存、OCR取消/旧finally generation护栏（其中合并为8个JUnit用例）。OCR7项新增：multipart目的地/无expiry、页/usage/200error严格校验、length、实际内容预算、跨槽attempt与结果恢复、错误/Key变更可能计费/不重试、坏UTF8/结果上限。
- Astra独立只读审查通过；预审发现并修复两处数据竞态，最终小差异再审通过。JVM/fake不代表真实Android取消/Keystore/持久化/模型或OCR服务端契约验收。
- Mock132 AiKeyStore与epoch测试未改，哈希仍为 `1057f580dbe0dcfa6e7b3015fff7ae0e1597250a29448acf042d1f9116a2ae32` / `e1d5d756109d9abee3d2da759ec36d0f46255c61a14a6f8857b474bd231045b5`。
- 本轮精确基线差异 `/tmp/openhoyi-model-ocr-integration.diff`，前后哈希 `/tmp/openhoyi-model-ocr-source-manifest.json`，基线快照 `/tmp/openhoyi-model-ocr-before`。源码allowlist统一冻结由根线程执行；本线程不打包任何.env、local.properties、签名材料、真实数据或崩溃日志。

## 仍待验收与限制

- 官方能力来源为本地受信目录，不等于当前账户可使用Pro；服务拒绝保留会话，不降低high safety或自动fallback。真实OCR页数/费用/Word与PPT原生转换、识别质量/页坐标、真实模型组合尚未调用验证。
- Android picker/Keystore/旋转与实际provider中止、窄屏和三档字号动态体验待根线程统筹；getType没有CancellationSignal，失常provider不能保证强制取消。
- OCR仅文字，表格/图片/格式可能丢失，不能冒称完整rich-layout文档理解；length及超限不会自动选页或删文。未知解析是否收费只能用户核对服务账户，应用不自动重试。
- 全库64MiB/128副本上限仍在，未知旧epoch副本保留占额，不自动清未知数据。schema4旧客户端不可读，回退须保留读取兼容，不卸载清数据。
