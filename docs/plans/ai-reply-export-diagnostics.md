# AI 请求导出诊断

- 范围：reply 开始、成功、失败、取消的闭集脱敏事件进入现有 TraceStore 和导出 ZIP；不新增请求或自动重试。不是 HTTP 次数或费用账单。
- 基线：`599670aab75d94070bf836cb813239e30e3e46e7`，既有 `feature/ai-chat`；目标 develop，但本次不推送、不 PR、不合入。
- 文件：AiDiagnosticRecorder、AiChatActivity 局部 hook、focused test、本规划。保留共享未提交内容；不修改 Engine/SDK 日志。
- 白名单：事件 `ai.reply.started/succeeded/failed/cancelled`；scope=reply、随机 attemptId、previousUnsuccessfulAttemptId（上次未成功尝试，并非断言正文相同）、model=flash/pro/unknown、durationMs(0..86400000)、problem/stage/failureKind 枚举、httpStatus(100..599)。构建身份由 TraceStore 自带 app 字段记录。不采集 usage，不把未知记为零。
- 隐私：禁止 Key、授权头、请求/回复正文、URL、文件名、Throwable.message/stack、provider 原文；SDK 保持 silent。未知 modelId 只写 unknown。沿用 TraceStore 8MiB/7日有界保留，清 UI 不清已记录脱敏事件。
- 验证：focused tests 覆盖字段白名单、未知输入脱敏、失败后手动发送关联、成功重置、取消只终结一次、迟到回调隔离、sink 异常不改变请求、duration/http 边界；由协调端串行 Gradle 执行，并独立安全复核。
- 协议/设备：只观察 reply，不读真实 Key/用户内容，不操作设备，不调用 API；一次 reply 可能包含多个内部请求。
- 发布/签名：仅纳入用户要求的 Mock 候选，不安装，不改变签名/数据 schema。
- 回退：仅撤销本次 hook/recorder；已有日志按原保留规则存续，不清用户数据。
- 限制：Activity 重建后的手动发送不保持 previousUnsuccessfulAttemptId 关联；每条事件仍有 UUID 与 TraceStore 时间/构建信息。日志保留/队列满时可能丢事件，不保证覆盖每次故障。
