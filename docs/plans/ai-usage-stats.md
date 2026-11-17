# AI 本机用量统计
- 范围：聊天菜单入口，本次会话/本机累计、模型、输入输出 Token、搜索次数；无金额估算，不作为账户账单。
- 来源/工作分支：沿用已授权 AI 集成工作树 feature/ai-chat（当前构建链基线），目标 develop；保留其他未提交改动。
- 数据：每次实际 HTTP 尝试在本机记一次；完整响应的 usage 独立于最终回复成功记录；缺失/取消记未返回。搜索按原始 results 非空、空、失败和未确认分类。
- 隐私：仅计数与受限模型名，原子保存 noBackupFilesDir；不保存 Key、聊天、搜索词、结果或账户标识；换 Key 后仍明确本机累计。
- 验证：纯 JVM 解析/幂等/失败/取消/恢复/汇总测试，编译与 lint；不发付费请求、不操作设备。验证结果交接时补充。
- 推送/发布：本轮不推送、不发版、不安装；后续依仓库 scripts/create-pr 和既有具体授权，不绕过阻碍。
- 风险/回退：不影响咖啡机协议；统计保存失败标不完整，不中断聊天。回退代码保留本机计数文件；未来 schema 不覆盖未知文件。

## 2026-11-15 本地验证
- 初版完整接线后：Debug Kotlin、Mock AndroidTest Kotlin 编译，`AiUsageTest/AiSafeTransportTest/AiChatPendingDraftTest/AiWebSearchTest/AiProtocolHistoryTest` 共23项 JVM，`lintDebug` 全通过（3m43s）。
- 收紧模型白名单、持久化后的取消/Key复查、存储初始化异常降级及拒绝响应计量用例后：`compileDebugKotlin`、`testMockUnitTest --tests '*AiUsageTest' --tests '*AiSafeTransportTest'` 共13项（统计8+传输5）及 `lintDebug` 全通过（3m7s）。
- 日志 `/tmp/openhoyi-ai-usage-final.log` 与 `/tmp/openhoyi-ai-usage-tightened.log`。没有真实API请求、平板操作或界面动态截图；未推送、未建PR、未发布。UI以现有主题/字号的 AlertDialog 与滚动消息区承载，动态布局仍待授权验收。
- 独审修正：完全未返回 usage 的模型显示「Token 未返回」而不是 0；部分返回数值明确标为「已返回用量」。补充旧会话晚回调隔离、实际传输取消/超时、完成时存储失败不影响回复、缺失展示决策测试。最终 `compileDebugKotlin`、统计12+传输5共17项 JVM、`lintDebug` 全通过（2m27s，`/tmp/openhoyi-ai-usage-review-fix.log`）。

## 缓存命中率增量
- 仅从实际 `prompt_cache_hit_tokens` 与 `prompt_cache_miss_tokens` 提取，要求非负整数且和等于该次输入 Token；按跨请求累积命中/(命中+未命中)，不平均百分比、不重复加入输入 Token。
- 缺字段/不一致/零分母显示未知；部分数据明确显示缓存字段已返回 x/y 次请求。schema1旧计数不丢失，其未记录缓存数据保持未知，不从历史输入反推。
- Mock 离线界面预览增加纯合成用量弹窗（全缺和部分缓存样本），不读取真实 Store/Key，标题明确离线样例。
- 最终 `compileDebugKotlin`、`compileMockAndroidTestKotlin`、统计14+传输5共19项 JVM、`lintDebug` 全通过（3m25s；日志 `/tmp/openhoyi-ai-usage-cache.log`）。未进行动态设备/付费请求。
