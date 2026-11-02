# ScaleSettings 恢复认可稿左右24dp

> 实施者 Sol，按 executing-plans 执行；root负责统一构建、提交推送。不是独自工作，仅修改本计划所有权范围。

**Goal:** 将本页body左右padding由16dp恢复到认可稿24dp，不改设计迁就实现。

**Architecture:** 只在ScaleSettingsActivity取得page body后覆盖其左右padding，保留上下padding及共享style。688dp仍指包含左右padding的body总宽，不扩大为736dp。

**Tech Stack:** Android Kotlin View，既有Settings protocol2与实际视觉取证。

## 依据、基线与范围

依据 [四配置实际视觉复核](../evidence/2026-10-23-m8-scale-settings-visual-review.md) 及 [冻结分组设计](../superpowers/plans/2026-10-18-scale-settings-groups.md)。当前HEAD `8b52379`；独立比较旧审图head `5d08fd6` 到当前的 `ScaleSettingsActivity.kt`、`SettingsVisualStyle.kt`，无diff。已确认旧实图的16dp问题仍存在：shared page创建body为16dp四边，Scale页调用page(...688,"scale")未覆盖。

shared容器onMeasure把body.layoutParams.width设为min(实际可用宽,dp(688))，故688包含padding。宽屏总宽1548px（density2.25）不变；左右各54px，内容宽1440px=640dp（旧1476px=656dp）。卡片内部现16dp不变，其文字可用宽从624dp降至608dp；自然换行/整页滚动承担新增高度。

沿现授权 `feature/task-first-ui` → develop批次，遵守 [git-workflow](../git-workflow.md) 和 [task-template](task-template.md)。允许修改仅 `mobile/src/main/kotlin/io/openhoyi/mobile/ScaleSettingsActivity.kt` 的body创建处。不改SettingsVisualStyle/shared palette、其它页面、字号/字体/颜色、卡片内部padding、组间距、资源文案、688总宽、业务监听/能力判定/偏好。若现有证据不足需另报，不自行扩测试框架。

## 最小实施

- [ ] 在现val body=visual.page(...)后原位设置：

```kotlin
body.setPadding(style.dp(24),body.paddingTop,style.dp(24),body.paddingBottom)
```

保留其tag、实例、scroll容器、header/back及后续组装顺序。不要把shared默认16改成24，不加页面通用参数、不全局扩散、不修改上下16dp。左右相同无需改变RTL语义。

- [ ] 核小diff仅上述本页布局覆盖；原precision Switch最小56dp、返回触控及原监听保持，不缩小任何动作目标。页内没有固定底部动作条，不新造sticky header/动作；原导航栏仍由TaskPageFrame承担。
- [ ] `git diff --check`，冻结单文件SHA交Astra静态审核。低影响单行布局不写只重复常量的单元测试；root统一Mock主包/测试包构建，沿原PR正常合批推送与必需CI，不微推/force/no-verify/直推主干。

## 严格新原件验收点

- [ ] 四配置light/dark×font1.0/1.3按原Settings取证链，记录新的source/head/APK/font与PNG/geometry/proof配对；不得用旧5d08图签新布局。宽屏body实际总宽688dp、左右24dp、上下不变；卡片与组标题内缘对齐且总内容640dp，不把扩宽body伪装成24dp修正。窄窗仍用实际可用宽，不引入水平溢出。
- [ ] G2/G3：固定字体400/500、实际字号/行框不变；重点看1.3副标题、precision标签/解释、能力说明、操作限制、协议支持全文。缩窄16dp可能改变换行；接受自然增高和滚达，不许ellipsis/裁字/减字号/隐藏状态。控件文字与开关不相撞，Switch实际触控高≥56dp，既有其它动作≥48dp且身份未变。
- [ ] G1/G4：新卡片内缘、留白、分隔线及深浅主题实际看图；颜色不得为此次间距改动变化。页内不含图表，不增画图检查。
- [ ] 继续原14主态+14说明目标、所有恢复与既有特殊态证据，不为本修正减少目标或放宽5秒四零差。同帧真实target全字可见、精确scroll恢复/偏好不变仍严格。四主态及底部图是必审增量，但不能替代尚未逐图完成的其余目标。
- [ ] 新图审查才能关闭G1边距差异；其余完整滚达/恢复/特殊态视觉待办与真机能力仍保留。编译/机械PASS不签M8整页或整run通过。

## 风险、发布与回退

只修本页横向预算，主要风险是1.3换行使自然高度增加，须以上述实图/原滚达链验证。不改设备命令/编码/单位/队列/未知记录，不自动重发，不将Mock当硬件验收。不发版、安装或分发。回退仅revert本页padding覆盖，保留原证据及未知记录，不清数据。本次只规划，未修改源码或构建。

## Astra 单行静态验收（2026-10-23）

静态通过。独立读取实际diff，冻结SHA256为 `2cd452100331d48dbf0a367957355986fc8aa20bc9c71c1f0597330eb90218bd`。仅在本页body创建后增加约定的setPadding一行；内存删除该行后逐字节还原HEAD，原SHA256 `da2d2af54d5585d97d27434754d7a78b72e319d125002450c63e6bf76392db90`。

左右各24dp；上下直接保留原paddingTop/paddingBottom，688dp包含padding的总宽参数不变。共享SettingsVisualStyle、卡片内部间距、字体颜色、56dp Switch与偏好监听、renderScale/设备能力/生命周期均未改。独立git diff --check通过。

本结论仅静态范围通过，可交root统一构建和原链取证。未执行构建/设备/提交推送；仍须四配置新原图核24dp、1.3自然换行及动作预算。旧图不能关闭新G1，其余14+14、恢复和特殊态视觉待办不因此签过。
