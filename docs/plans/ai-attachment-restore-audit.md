# 附件加密副本与恢复核查

- 范围：授权读取时完整复制自有加密副本、失去原URI后恢复、缺失/损坏失败关闭及删除所有权。保留既有多模型、compact、OCR、持久化与132epoch。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)，不提交/推送/PR/合入，保留其他未提交工作。已重读AGENTS，仓库/父目录无.agents目录。
- 分工/文件：Sol唯一写者、串行Gradle，Astra独立只读审。AiAttachment、AiAttachmentVault、对应2类测试、两语ai_chat.xml与本计划。
- 官方：[Photo picker](https://developer.android.com/training/data-storage/shared/photo-picker)授权可能在应用停止/重启后失效且可选云媒资；[SAF documents](https://developer.android.com/training/data-storage/shared/documents-files)支持选定文件URI访问，长期访问可另取持久授权，移动/删除后仍可能失效。当前只瞬时content URI读取并复制，不持久化URI、不开广泛权限，也不靠持久URI恢复。
- 实际链：类型/尺寸先验+有界完整EOF读取→格式校验/图片处理→AES-GCM自有副本write+fsync成功→manifest进入session；恢复仅从密文id/metadata读取，无源URI。source流/临时正文关闭擦除，取消会关流/发CancellationSignal；getType不支持该signal的阻塞仍是已知边界。
- 修复1：已知非null SIZE必须与实际source bytes一致；早EOF或文件变化拒绝返回prepared副本并关闭源。未知SIZE只能有界读到EOF，无法证明原件语义完整；格式校验不冒充通用PDF/Office完整解析器。
- 修复2：remove先load认证ownerEpoch+id AAD和全部metadata，再仅删该id；错epoch、错误metadata、损坏、丢Key均保留。清理临时解密明文由use擦除。Activity原有引用保护保留：全visible/当前draft/OCR原件与结果/所有inactive slots参与allAttachments；移除draft仅在成功checkpoint且无引用后删除。本次新副本过期任务清理受同一认证门禁。
- 保全：恢复未知session写入被SessionStore fence阻止，不自动扫目录删未知记录；new-chat显式恢复会归档原密文。附件storage是唯一新id创建后write+fsync，并非temp/rename原子发布；进程中断可留孤儿残密文，但sync返回前没有session引用，不覆盖已有id。64MiB/128副本quota保留未知孤儿，可能占额；不在本轮自动清理，后续需显式管理。
- 隔离：noBackupFilesDir独占UUID文件名、AES-GCM AAD绑定epoch+id、独立Keystore alias、不在文件/日志放源URI或Key。allowBackup=false、backup_rules及data_extraction_rules排除所有域；这里只读核查，不等于真实OEM备份验收。
- 文案：选择不会上传DeepSeek，但系统provider可能为所选云文件联网，修正笼统‘选取不联网’；不新增上传/权限/重试。
- 验证：合成preparer短流/大小不符关闭；JVM AES恢复后不再开源、损坏/丢副本失败保留metadata；错epoch/metadata/损坏删除拒绝；复用现有quota前检查、存储失败、跨模型引用/恢复、完整wire门禁。focused Attachment/AttachmentVault/AttachmentConversation，无真实Android/文件/Key/API/设备/CU/机器。
- 发布/签名/升级：不发版、不改版本签名、不安装，133快照不改，schema/epoch不变。
- 回退：仅撤回本轮代码/测试/文案，不删附件、历史、草稿、未知密文或他人工作。
- 未测：Android provider撤权/进程被杀/磁盘写满、Keystore、OEM备份/实际恢复；fake不等于设备实测。

## 实际验证结果

- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiAttachmentTest --tests io.openhoyi.mobile.AiAttachmentVaultTest --tests io.openhoyi.mobile.AiAttachmentConversationTest`。
- 首轮29项（Attachment12/Vault5/Conversation12）全过，0失败/错误/跳过，BUILD SUCCESSFUL in 58s；processMockResources和compileMockKotlin实际执行通过。无lint/assemble/无关矩阵。
- 新增known-SIZE短/长不符均关闭源且不返回附件，精确SIZE和unknown-SIZE成功；合成源只打开一次，编码恢复草稿后Vault能加载，损坏/缺副本失败保留metadata、不重开源；错epoch/metadata/损坏删除拒绝。原有存储失败、丢Key不重建、不覆盖已有密文、跨轮wire/缺附件失败关闭继续通过。多模型完整图草稿保全复用上一轮21项证据，无重复全矩阵。
- Astra最终独立只读审查通过，无阻断；核实生产SIZE校验时机、remove认证/擦除/IO路径、两语文案、fake及未测边界。
- 日志`/tmp/openhoyi-attachment-restore-tests.log`，计数`/tmp/openhoyi-attachment-restore-test-counts.json`；before `/tmp/openhoyi-attachment-restore-before`，7文件精确diff `/tmp/openhoyi-attachment-restore.diff`，前后SHA `/tmp/openhoyi-attachment-restore-manifest.json`。
- 无设备、真实Key/用户数据/附件、付费请求、Computer Use、机器操作、版本签名/安装或推送PR。旧133及132epoch保留；Android撤权/被杀/磁盘满/Keystore/OEM备份实测仍未完成。
