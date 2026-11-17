# 当前 AI 会话加密持久化

- 需求与范围：只保存当前会话的可见消息、完整协议、待发草稿及会话统计；新会话替换当前记录，不提供多会话列表。恢复不发送请求、不执行工具、不重复记账。
- 来源/工作分支：沿用已授权的 `feature/ai-chat` 工作树（功能基线 develop）；保留其他未提交文件。本轮只本地修改，不提交、推送或创建 PR；未来 PR 目标 develop，遵循 [Git工作流](../git-workflow.md)。
- 实现：独立 Android Keystore AES-GCM alias、随机 IV、版本 AAD，`noBackupFilesDir` + AtomicFile。codec 严格有界并校验完整协议与气泡一致；不使用 Bundle、日志或 API Key 身份哈希。凭证 epoch 与 sealed 同次 preferences commit；旧凭证无 epoch 时仅迁移非敏感 epoch。
- 生命周期：草稿短延迟写入、暂停强制写入；请求前先保存待发草稿，每次用量变化及成功回合保存。中断保留待发草稿和已计入的 unknown 用量。恢复只构造统计快照。
- 告知与旧 v2 升级：聊天页常驻简短“会话在本机加密保存”说明；退出重启保留/不云备份/新会话及凭证隔离详情置于 v3 告知，预览壳不显示该声明。发送授权使用 `deepseek_v3` 并更新完整告知，旧 v2 不自动视作 v3。无启动弹窗，不在尚无历史的旧版中虚构迁移聊天；恢复不执行请求。
- 错误：损坏、未知版本或 Keystore 故障保留原文件并阻止自动覆盖，简短提示；普通新会话遇不可读先要求“保留旧记录并新建”确认；原密文原子写入独立 noBackup 隔离件并回读一致后才写新空会话，不自动删除原件。若 session alias 明确缺失，仅此显式保留恢复允许创建缺失 alias；existing()/加密故障不删除或替换现有 alias。隔离失败停止，不写当前文件。自动保存/读取绝不创建替代密钥。凭证 commit 失败保留进程 fence；磁盘上仍是原凭证/epoch，不能关联新凭证。
- 验证：新增纯 JVM state IO 测试覆盖恢复、中断 unknown、epoch 隔离、损坏/未知版本/密钥缺失、原子失败、随机 IV/密文/认证与协议一致性。实跑 `:mobile:testMockUnitTest` 定向 SessionStore/KeyStore/CredentialEpoch/ChatSession 共32项通过（含恢复后下一请求完整协议桥接、显式保留恢复，无网络/工具重放），credential policy 6项通过，localization资源检查无漂移，单处会话保存说明的轻量源码检查1项通过，`git diff --check`通过；未操作平板或真实 Key，不调用付费 API 或硬件。
- 发布与签名：不发版、不构建分发、不安装，不改变签名/升级边界。
- 回退：回退代码，保留加密会话文件；不卸载、不清数据，不重放未完成请求。旧版忽略新增当前会话文件。
- 待验证：Android Keystore/AtomicFile 实机与进程杀死场景尚未验收；JVM 不等于设备证据。
