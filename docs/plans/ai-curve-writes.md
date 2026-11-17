# AI 自定义曲线提案与原生确认

- 需求与范围：模型仅提议新增／替换自定义曲线。内置100条不可编辑；不删除，不连接或下发机器，不修改快捷槽。
- 来源与目标：已有 `feature/ai-chat`（基线 `599670aab75d94070bf836cb813239e30e3e46e7`），目标 `develop`。保留现有未提交 AI 工作，不切分支、不重置。
- 分工：Sol 实现与精简测试；Astra 独立审查写入授权及体验。
- 实现：严格 8KiB 参数文档，应用生成新增ID；替换只接受可信单例库现存可编辑草稿。原生显示操作、名称、ID和完整前后参数／单位。模型只得到 awaiting_confirmation，无执行／批准工具。
- 授权：一个内存待确认快照，随机一次性token绑定会话、凭据epoch、库revision和目标文档。原生点击才提交；取消、返回、恢复不自动写入。目标变更、删除、ABA或写盘失败均消费授权，需重新提案。待确认期间不允许静默替换提案。
- 集成约束：使用 MobileApplication 原单例，禁止第二个可写store；engine/provider工具只可propose；UI独占confirm，凭据/会话变化失效；不持久化可执行提案或授权。既有编辑器另存语义不变。
- 验证：纯JVM fake存储／mock网络，覆盖未批准不写、token/epoch、插入/替换、ABA、删除、无效schema和失败落盘。Android确认界面未经实机验证不得宣称通过。
- 协议与设备风险：仅本地配方文件原子提交，无BLE／控制器／机器行为变化。
- 推送与合并：本轮无新推送／PR授权，不执行；若后续授权，遵守 [Git工作流](../git-workflow.md) 与 create-pr 门禁。
- 发布与回退：不发版、不安装、不操作硬件；回退本功能代码，保留兼容原格式的曲线和未知记录，不清数据。
- 实现状态：Engine 暴露 `propose_custom_curve` 与只读 `custom_curve_document`；原生确认绑定会话随机标识、credential revision 与 context generation。compact、stop、离开页面、失败、Key更换均取消。成功／取消结果追加至最终可见回答，并同步最终协议content，不改历史tool、不额外请求provider。详情读取与写入共享原库单例；替换明确提示已有快捷入口未来使用新参数。
- 验证结果：`JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME="$ANDROID_HOME" ./gradlew :mobile:testMockUnitTest --tests 'io.openhoyi.mobile.AiCurve*Test' --tests 'io.openhoyi.mobile.AiChatSessionTest' --tests 'io.openhoyi.mobile.AiProtocolHistoryTest' --tests 'io.openhoyi.mobile.AiContextPolicyTest' --tests 'io.openhoyi.mobile.AiLiveProbePolicyTest'` 通过43项。首次运行发现旧诊断只支持5工具的fixture失败，修复为仍只放行原3只读工具后复跑通过。
- 独立复核：Astra静态复核通过授权链；其指出的未知落盘结果误报与原生结果反馈静默失败已修正。最终同范围43项测试与Mock编译复跑通过（日志 `/tmp/ai-curve-tests-final.log`）。
- 未完成：未进行Android实际确认界面/三档字号/窄窗验收，JVM和编译不代表UI已验收。
