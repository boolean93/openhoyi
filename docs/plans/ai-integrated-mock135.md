# Mock135 预览修复候选

- 范围：仅把已独审/编译通过的Mock窗口上下文修复打入递增候选。模型、OCR、预算、安全、附件事务等继承134，不重审完整矩阵。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。遵守[Git工作流](../git-workflow.md)/[模板](task-template.md)，保留未提交工作，不PR/push/合入。Sol唯一writer/Gradle/条件满足后的设备操作者；Astra只读审freeze与结果。
- 版本：未发现135占用，既有属性hoyiVersionCode=135、hoyiVersionName=0.1.0-local.ai-features-135，不改共享version文件，包名io.openhoyi.mobile.mock、signer须匹配132/134。
- 验证：复用local-only fix编译+5项OfflinePreviewState/Astra；仅assembleMock+lintMock，freeze允许清单源码与前后hash，不重复52/13或硬件矩阵。
- 安装边界：先仅组件/time/进程/install-session元数据核是否仍本次合成流程，无真实会话/草稿新增；不能用当前Launcher或mtime独立证明。当前安装器前台来源需区分本次ADB遗留与其他操作；证据不足则只交候选，禁止猜测或反复install。无Key/聊天正文读取。
- 条件式授权：root转达原用户明确允许弃当次Mock聊天/草稿安装继续测试，只有能排除新增真实活动且无并行安装时才可同签名-r；否则停止安装汇报。不能卸载/pm clear/改系统权限，不碰正式App/机器/付费API/CU。
- 验收若可运行：仅既有隔离合成preview实际按钮；model/PDF两阶段/curve取消/Markdown/usage/codec按入口实际覆盖如实记，非picker/Keystore/生产coordinator/真实模型验收。不进行长aaaa输入，不抢占用户。
- 发布/签名/升级：不公开发布、不标签、不PR/合入；134 APK/source保持不可变，135非Release候选。匹配签名递增且保数据，不以降级清数据恢复。
- 回退：保留134与135材料、会话和未知记录；数据保全不满足时不安装，不删旧用户数据或未知密文。
- 材料：local-materials/ai-integrated-mock135含源码allowlist、APK/签名/版本/测试lint、diff和安装边界证据；不含真实数据/签名密钥/env/local.properties/崩溃正文。
