# 模型与图片历史能力核查

- 范围：官方能力与现有实现复核，补图片历史/草稿跨模型恢复与detail断言；无生产行为变更。不创建远端模型目录接口或解析器。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)，本轮不提交/PR/推送/合入，保留此前全部未提交工作。
- 分工/文件：Sol唯一写者、串行Gradle，Astra后续独立只读审；仅AiModelPolicyTest、AiAttachmentConversationTest和本计划。已重新读取AGENTS；无.agents目录。
- 官方：[Lists Models](https://api-docs.deepseek.com/api/list-models/)例示Flash图片/文字、Pro文字，窗口1048576、最大输出393216；metadata可缺省，不能把未知当支持。当前仅可信本地两模型catalog，不读取账户目录、不声称账户授权已经验证。未知model ID/恢复模型拒绝，不自动fallback。
- 官方：[Vision](https://api-docs.deepseek.com/guides/vision/)支持历史user图，assistant图400；本地仅user附件更严格。detail=original保留上传图像处理档位，low才512缩放。应用实际先缩最长边1600（不是2048）、JPEG<=1MiB、EXIF方向/去元数据，无法宣称原片高保真；现有发送前同意已说明细节损失。1024图片token上界与独立传输门禁沿用。
- 保全：每模型独立slot，Activity先保存候选再替换内存；Flash旧图/图片草稿放inactive，Pro不接收；切回恢复。pending curve先失效，contextGeneration变化，OCR忙时禁止切槽。跨epoch、未知模型及坏恢复不自动发请求。
- 验证：仅ModelPolicy和AttachmentConversation两类focused，复用未知模型、坏schema、保存失败、跨轮图片重放、assistant图拒绝、工具协议恢复、缺副本失败关闭等既有例；新加Flash图片历史+图片草稿→Pro→编码恢复→Flash完整保全，强制给Pro图片在联网前MODEL拒绝，现有wire/replay加detail=original断言。JVM/fake不等于真实模型/Android图像质量验收。
- 协议/设备风险：无协议/schema/epoch/生产门禁变更，无设备/Key/付费/真实附件/Computer Use/机器操作。
- 发布/签名/升级：不发版、不改签名版本、不安装，不改133旧快照/APK。
- 回退：仅移除本轮测试/计划，不清历史、草稿、附件、密文或其他未提交代码。
- 未测：真实账户模型可用性、图片推理质量、Android旋转/解码/Keystore；不为补证发真实请求。

## 结果

- 实际命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiModelPolicyTest --tests io.openhoyi.mobile.AiAttachmentConversationTest`。
- 最终21项（ModelPolicy9/AttachmentConversation12）通过，0失败/错误/跳过，BUILD SUCCESSFUL in 18s；现有主代码/资源编译UP-TO-DATE，无生产变化未扩lint/assemble。新增Pro图零load/零network、tool图manifest拒绝与user重放original断言均纳入本轮执行。
- 首轮新增fixture直接比较AiAttachment对象导致引用不相等（对象按设计无值相等以避免泄密）；改比较user/text与附件持久化id/name/mime/size，未改生产类或放宽门禁。
- 日志`/tmp/openhoyi-model-vision-tests-final.log`，首轮日志`/tmp/openhoyi-model-vision-tests.log`。before `/tmp/openhoyi-model-vision-before`，3文件精确diff `/tmp/openhoyi-model-vision.diff`，前后SHA `/tmp/openhoyi-model-vision-manifest.json`，测试计数`/tmp/openhoyi-model-vision-test-counts.json`。
- 结论：现有生产能力/槽切换符合官方与授权边界，仅补测试/文档；生产协议、epoch、密文、原133均未改。真实Android恢复、图片质量和账户模型能力仍未实测，不能把本轮fake当设备/服务验收。

- Astra最终独立只读复核无阻断；按精简规则完成，无新增生产改动或扩大矩阵。
