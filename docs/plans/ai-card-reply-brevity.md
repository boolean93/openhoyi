# 推荐卡正文去重

- 需求与范围：推荐曲线卡片出现后，正文仅保留简短选择理由和必要提醒，不重复温度、目标重量、各段压力/流速/水量等卡片参数。避免再次列参数、泛泛收尾和无必要追问；用户主动问参数、解释或详细比较时可展开。保留重要不确定性、安全限制和必要确认，不机械删除输出。
- 来源分支/基线提交：develop 来源的 fix/ai-default-one-card，b505f6a14b24582d0a4a0875463a523695c62fea；工作分支：fix/ai-card-reply-brevity；PR目标：develop，默认单卡提交合入后保持独立差异。遵守 [Git工作流](../git-workflow.md)。
- 改动文件与步骤：同步 AiChatPolicy 系统提示词与 AiChatEngine 选卡工具说明；策略与请求工具描述回归。沿用既有默认单卡和显式数量规则，不改数量逻辑或卡片内容。
- 推送与合并：实现者提交独立改动；协调者普通推送，通过 scripts/create-pr 执行完整本地门禁并创建独立PR，等待精确头提交 branch-policy/verify 通过后合入；不运行日常AVD。
- 验证证据：已运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:testMockUnitTest --tests '*AiChatPolicyTest' --tests '*AiChatSessionTest' --console=plain`，20项通过（Policy 4、Session 16），验证正文规则、主动详情例外与安全边界抵达模型；不用付费API。
- 协议与设备风险：仅提示规则，不修改协议、门禁、数据、队列、未知记录或重试/停止；不安装、不操作设备。fake 请求测试不代表真实模型的输出效果验收。
- 发布：不发版、不修改版本/签名，不安装或升级。
- 回退：revert 本次独立提交，不清理任何数据或未知记录。
- 未完成与阻塞：真实模型不调用；完整非AVD门禁由PR入口后续执行。

## 完整门禁后的预算回归修正

- 全量门禁发现原新增提示词令既有三POST场景超过8192字节。只压缩本次新增系统/工具文案，不改变既有预算、上限、协议或断言；保留参数去重、简短理由/必要提醒、详情/对比例外和风险确认。
- 已运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:testMockUnitTest --tests '*AiChatPolicyTest' --tests '*AiChatSessionTest' --tests '*AiProtocolHistoryTest' --tests '*AiLiveProbePolicyTest' --console=plain`，34项通过（4+16+10+4）。
- 临时字节诊断后已撤销诊断输出。最终三POST为7649、7920、8094字节，最大值距8192上限尚余98字节。再次单跑三POST场景通过。后续完整门禁仍由PR入口执行。
