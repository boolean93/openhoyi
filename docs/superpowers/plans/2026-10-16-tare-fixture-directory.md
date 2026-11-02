# 去皮派发测试夹具目录初始化

- 范围：9d457ef 的 lifecycle run38841222177 在 NOT_READY 模式改变设备状态前的 WRITING 断言失败。测试日志先请求去皮、随后 UNKNOWN。TraceStore.prepare() 与 SharedPreferenceTareStorage.Marker.arm() 都可能创建同一个未初始化的夹具目录；两者的 isDirectory/mkdirs 检查存在竞争。日志未直接记录 mkdirs 失败，故这是源码支持的根因候选，须原生重跑确认。
- 来源/目标：沿用 develop66ebda9 + 显式 ed01de0 依赖，feature/task-first-ui → develop，draft PR#4。遵守 docs/git-workflow.md。
- 步骤：在创建 TraceStore 前同步建立本测试拥有的唯一目录；原 WRITING 断言补充状态/调用数/marker 诊断。保留10种派发和4种持久化模式的所有断言。
- 验证：Mock JVM、应用及 instrumentation 编译；精确提交的生命周期 CI 重跑。编译不等于原生通过。
- 协议与设备风险：仅测试初始化，不改生产持久化失败→UNKNOWN、安全门禁、队列、超时或设备写入。无实机操作。
- 推送：本地检查后普通推送原 feature，更新草稿PR；不直接合并、不发版、不改签名/版本。
- 回退：追加 revert，仅移除测试初始化和诊断；不清除任何真实记录。
- 未完成：原生重跑、独立审查。
