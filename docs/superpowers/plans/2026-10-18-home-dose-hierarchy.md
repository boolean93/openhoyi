# 准备页粉量层级实施计划

> 使用 superpowers:executing-plans 在当前已隔离工作树执行，不新增并行任务。

**Goal:** 按认可的 brew-preparation-v2 将本杯粉量从正文拆成醒目的主数值，保留真实准备状态。

**Architecture:** HomeActivity 继续读取原 beanPreparationResult/current 与库存事件匹配结果；仅拆分显示，不增加状态或写入。

**Tech Stack:** Kotlin / Android 原生 TextView / 既有 TaskVisualTokens。

- 来源 develop66ebda9 + ed01de0；沿用 feature/task-first-ui → develop draft PR #4，遵守 docs/git-workflow.md。
- 文件：mobile/src/main/kotlin/io/openhoyi/mobile/HomeActivity.kt；本计划；视觉证据日志。
- [x] 将 beanSummary 保留为豆名与可点击用豆入口，另设 doseStateSummary 显示原 doseState 的完整本地化状态。
- [x] doseQuantity 在 dose 非空时显示 BeanQuantity.formatGrams(amountMg) + g；40sp/700，g 通过 RelativeSizeSpan(.55f) 显示22sp，数字使用 tnum。空状态隐藏数值，不伪造0或已确认。
- [x] 状态与主数值组成独立组，上下16dp间距、顶部1dp语义分隔线；不改原始状态判断、库存写入、关联逻辑和按钮行为。
- [x] 执行 git diff --check、branch policy、:mobile:assembleMock 和 :mobile:assembleMockAndroidTest；低影响视觉调整不添加镜像实现的单元测试。
- [ ] CI 中检查真实有用豆状态的横屏浅/深色、大字和短高截图；未取得新截图前不得声明视觉验收完成。

取证发现：ci-63a490c-capture/home-selected-dark.png 选择 capture-2，压力目标为空，不能代表认可稿的四阶段压力配方满数据状态；保留无压力提示，不伪造阶段曲线。后续补匹配数据截图。

普通推送当前工作分支，沿用PR；本轮不合并、不发版、不安装、不操作设备。不改协议、签名、versionCode、schema。回退只还原显示代码，保留库存/未知记录。

本地验证：Mock 与 instrumentation 构建 BUILD SUCCESSFUL in 42s，进程退出0；日志 /tmp/task-home-dose-hierarchy-build.log。branch policy / diff whitespace通过，截图验收未完成。
