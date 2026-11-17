# AI 集成 Mock134 本地候选

- 范围：将昨晚模型/OCR、完整预算、vision恢复、安全响应、附件恢复/原子发布、搜索统计全部纳入同源Mock体验包。用户等待，仅本地构建与证据；绝不安装或导航设备。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。本轮不提交/PR/push/合入，保留其他未提交工作，遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)，已读AGENTS。
- 分工/唯一写者：Sol串行Gradle与候选材料；Astra只读核对冻结输入/签名/构建证据，root独立核查设备状态。
- 版本方式：既有Gradle属性`hoyiVersionCode=134`、`hoyiVersionName=0.1.0-local.ai-features-134`，不修改共享build版本文件。133已占用，材料目录尚无134，包名仍io.openhoyi.mobile.mock，签名须与132/133的c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54完全一致。
- 验证：单次offline assembleMock、7类52项代表集成（ModelPolicy、DocumentOcr、AttachmentConversation、ResponseSafety、Publication、CurveWriteCoordinator、Session132Golden）与lintMock；复用昨晚其他focused日志，不跑无关完整矩阵。APK实际验包名/版本/SHA/签名，源码allowlist archive+逐文件SHA，构建前后比对防漂移。
- 操作/协议风险：仅构建，不读真实Key/用户正文/附件，不付费API/CU/机器。CI/JVM不等于设备体验或硬件验收。保留high safety、256K/200K估算、原生曲线确认、内置曲线只读、会话schema/epoch、未知结果不重试。
- 安装边界：root今早只读公包确认Mock132仍在，SHA e926160ab6d53c59ff9d980bd44c6e410daec227d7628aabba7541cc671e7695，signer如上。132缺当前内存/草稿保存成功的外部证明，130弃聊许可不涵盖新增数据。134和133均不得安装，直到安全保全与明确新授权满足。
- 发布/签名/升级：非公开候选、不打标签/Release、不发布APK、不安装，不触正式App。默认Mock同原debug签名，验证失败即停止交付为兼容候选，不卸载清数据。
- 回退：保留132/133旧APK/源码不变，134仅供后续审核；不以降级/卸载/清数据回退，旧客户端读schema4等边界仍须独立验收。
- 交付：openhoyi-local-materials/ai-integrated-mock134内source.tar.gz、source-manifest.json、candidate.json、APK、签名报告、测试/lint和差异证据；材料不含签名材料、local.properties、环境配置、真实数据或崩溃日志。

## 构建中发现并修复的API26兼容问题

- 首轮52项集成通过，但lint发现O_CLOEXEC公开字段需要API27。首个未安装134产物/源码/日志完整保留在候选材料failed-attempt1，不能作通过候选。
- 最小修复：仅API>=27增加O_CLOEXEC；API26使用O_RDONLY|O_NOFOLLOW，fstat确认目录并在finally关闭短生命周期目录fd。不猜flag、不用更高API的fcntl、不放宽lint。
- 重新冻结源码及产物，重跑受影响Publication/Vault13项、assembleMock和lintMock，复用首轮其余集成证据；最终SHA以新candidate.json为准。真实Android API26目录同步尚未执行验证。
