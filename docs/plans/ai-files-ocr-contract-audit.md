# Files / OCR 安全契约复核

- 范围：仅核对官方契约、修正中英文OCR收费/隐私提示，补fake断言；保留全部既有附件、模型、compact、会话及132 epoch实现。
- 来源/工作/目标：既有develop来源的feature/ai-chat，基线599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。本次不切换共享工作树、不提交/推送/PR/合入。遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)。
- 分工/文件：Sol唯一写者与串行Gradle；AiDocumentUploadTest、AiDocumentOcrTest、两份ai_chat.xml及本计划。Astra独立只读复核。仓库及父目录未发现.agents目录。
- 验收：精确multipart字段集合、purpose顺序、TTL有效范围；OCR未知/超时/取消不重复请求；复用既有不完整结果、恢复UNKNOWN、expired不重传测试。仅JVM fake及资源编译，无真实API/Key/用户数据/设备/CU/机器操作。
- 协议风险：未改生产网络协议；Files备用模块未接Activity，OCR实际入口保留独立两阶段同意及READY-only发送。
- 发布/签名/升级：不发版、不改版本或签名、不安装；无schema/epoch改动。
- 回退：仅撤回本轮文案和测试差异，不删除其他未提交代码、密文或未知状态。

## 官方依据（2026-11-16只读核对）

- [Files](https://api-docs.deepseek.com/guides/files_api/)：multipart显式expires_after[anchor]=created_at、expires_after[seconds]=3600..2592000，省略两者永久保存。已有备用上传显式86400秒，响应校验expires_at；过期拒绝而不重传。不承诺物理永久删除。
- [File Parsing](https://api-docs.deepseek.com/guides/document_parsing/)：PDF/Word/PPT以purpose先于file解析；text_recognition不保留图表，rich_layout_recognition也可能漏图。只能引用file_id，不能下载原件或解析正文。解析成功按页收费，后续聊天再计输入；断开仍可能完成收费。当前UI不用这条路径。
- [OCR](https://api-docs.deepseek.com/guides/ocr/)：仅purpose和file，其他字段400，不存Files；实际代码text_recognition。官方支持PDF/DOC/DOCX/PPT/PPTX，应用有界校验只开放PDF/DOCX/PPTX。page_count/pages及usage参考值由响应提供，usage不计账单。成功页每1000页USD0.225；服务端失败/超时不收费，客户端取消不能推断服务失败。HTTP200仍可能error，length结果不完整且禁止聊天。
- [Vision](https://api-docs.deepseek.com/guides/vision/)：明确Flash图片能力，不能推断Pro支持。JPEG/PNG/GIF/WebP，inline总请求48MiB、单件32MiB、每图最多1024tokens；应用采用更小图片/内存上限并按1024估算，未声称精确实测usage。

## 本轮修改

- 上传/OCR生产代码已符合上述契约，不增造mode或OCR expiry；保留现有有效24h Files TTL。
- 中英文同意说明区分服务端失败不收费、成功但本地不可用仍收费、取消未知不等于失败，并说明OCR不进入Files不构成永久删除保证。
- fake新增精确字段白名单与TTL范围断言，OCR超时/取消后同实例二次调用不联网。既有partial/expired/恢复测试复用。
- 真实服务响应/计费、Android provider/Keystore/设备体验仍未测，本轮不得当作实测通过。

## 验证结果

- 实际命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiDocumentUploadTest --tests io.openhoyi.mobile.AiDocumentOcrTest`。
- 首次沙箱运行因Gradle缓存锁权限未启动；获离线执行权限后，首次编译发现新增fake InputStream返回类型过窄，显式返回基类类型后修复。最终20项（Upload12/OCR8）全部通过，0失败/错误/跳过，BUILD SUCCESSFUL in 18s。最终日志`/tmp/openhoyi-files-contract-tests-final.log`；此前失败日志`/tmp/openhoyi-files-contract-tests.log`保留。
- `processMockResources`和`compileMockKotlin`首轮实际执行通过，最终轮UP-TO-DATE；两份XML纳入Android资源编译。未重复lint/assemble或扩大矩阵。
- Astra独立只读复核计划、最终文案与测试通过，无阻断。无真实计费/远端保留/Android设备验收。
- 精确5文件diff和前后SHA：`/tmp/openhoyi-files-contract.diff`、`/tmp/openhoyi-files-contract-manifest.json`。旧133源码快照/APK未改，本次文案修正需下次构建纳入。
