# AI 错误语义与 Mock 诊断

- 范围：独立纯策略 AiErrorPresentation 和 focused JVM tests；Activity/resources 由唯一 owner 接入，不修改 Engine/共享 UI。
- 来源：develop 系列既有 feature/ai-chat，HEAD 599670aab75d94070bf836cb813239e30e3e46e7。目标 develop；当前不推送、不 PR、不合入。
- 验收：协议校验、本地 SDK、HTTP 服务/权限、连接/读取/总超时不能一概误称网络；闭集 Semantic 对应一句原因与下一步，既有预算等业务语义保持。
- Mock：host 仅在 Mock 调用多行 mockDetails；build/model/stage-kind/http/duration 有界；有 attempt 才展示(UUID校验)。Provider 仅可选 reason/parameter 两闭合 enum；不接受 raw provider/exception/payload，不读 Key/正文，未知不记零。普通版本只显示语义文案。
- 验证：5 focused tests；协调端串行 Gradle，Astra 独审；源码确认 status 位于滚动区且不截行。设备 UI 与真实服务未实测。
- 协议/设备：不改变请求、重试或收费，不设备、不 API，不持久化授权。
- 发布：只纳入已授权 Mock 候选；不安装、不改签名。回退仅删本策略和 host 接入，不清数据。
