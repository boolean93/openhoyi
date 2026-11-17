# 推荐卡独立宽度

- 需求与范围：推荐卡不再沿用消息气泡宽度；RECOMMENDATION 左对齐，宽度为 min(实际host可用宽, 400dp)，不随正文长度改变，不重复扣除父布局边距。提案确认卡保持 min(520dp,78%)。
- 来源分支/基线提交：develop，47d558ff442dff346f6b0cb4a3f99ec118f38f09；工作分支：fix/ai-card-independent-width；PR目标：develop。遵守 [Git工作流](../git-workflow.md)。
- 改动文件与步骤：添加独立推荐卡宽度策略并在实际host按Kind选择；保留原有标题/按钮换行、48dp触控目标和分段图最小宽度/横滑提示，不缩字或截参数。
- 推送与合并：实现者仅提交；协调者普通推送，通过 scripts/create-pr 执行完整本地检查及独立PR，等待精确头提交 branch-policy/verify 通过再合入。不运行日常AVD。
- 验证证据：策略覆盖280/368/728→280/368/400与零边界、保留提案旧规则；复用固定Mock卡片fixture，增加窄窗大英文长名称四段/常规手机中文/平板Detached测量及短长正文卡宽一致断言。已运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:testMockUnitTest --tests '*AiCurveCardLayoutPolicyTest' :mobile:compileMockAndroidTestKotlin --console=plain`，6项策略测试与androidTest Kotlin编译通过。Instrumentation/Detached测量断言尚未执行；编译不代表实际布局验收，未取得设备截图。
- 协议与设备风险：仅UI测量，不改变设备协议、门禁、曲线数据或保存确认；不安装、不控制设备，不以Mock代替硬件验收。
- 发布：不发版、不改版本、签名或升级。
- 回退：revert 独立代码提交，数据保持兼容、不清记录。
- 未完成与阻塞：不运行付费API/设备/本地AVD；实际设备布局/截图验收未执行。
