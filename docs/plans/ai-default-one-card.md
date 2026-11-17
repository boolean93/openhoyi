# AI 推荐默认单卡

- 需求与范围：未明确要求更多时只推荐最合适的一张卡；明确数量（包括超过三张）继续尊重现有资源预算。
- 来源分支/基线提交：develop，47d558ff；工作分支：fix/ai-default-one-card；PR目标：develop。遵守 [Git工作流](../git-workflow.md)。
- 改动文件与步骤：同步 AiChatPolicy 系统提示词与 AiChatEngine 选卡工具说明；补策略和真实请求描述回归。不增加数量硬截断，不改变提案/编辑器保存确认。
- 推送与合并：实现者仅提交；协调者通过 scripts/create-pr 执行完整本地门禁、普通推送及PR，等待精确头提交 branch-policy/verify 通过后合入。不运行日常AVD。
- 验证证据：针对 AiChatPolicyTest、AiChatSessionTest、AiCurveCardSelectionTest 的本地 JVM 测试；无付费API调用。实际命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:testMockUnitTest --tests '*AiChatPolicyTest' --tests '*AiChatSessionTest' --tests '*AiCurveCardSelectionTest' --console=plain`，通过。
- 协议与设备风险：仅模型提示规则，无设备协议、门禁、队列、未知记录、重试/停止改动；不安装、不操作设备。不以提示词测试宣称真实模型效果已验收。
- 发布：不发版、不修改签名或版本、无升级安装。
- 回退：revert 本次代码提交；不改数据格式、不清理记录。
- 未完成与阻塞：真实模型输出未调用验证；完整非AVD门禁由PR入口执行。
