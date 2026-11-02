# MachineSettings tab 48dp 最小修复计划

- 范围：仅 `MachineSettingsActivity.kt` 的本页 tab 高度下限，不改共享字体 style、文字/色彩/监听/设备写入，不放宽测试。
- 来源：develop 来源的现有 `feature/task-first-ui` → develop，基线 b08adeca02326a6765973bf404385871af63c505；遵守 [git-workflow](../git-workflow.md)、[task-template](task-template.md)。Sol 实施，Astra 验收，root 合批构建/推送。

## 原件与源码原因

`ci-4076c2d/settings-first-failure-diagnosis.md`，run39906804817/source4076c2d；三档温度 tab 实高102/88/102px，小于 density2.25 的108px。首败是 click 的48dp前置门禁；ENOSPC为后发，不替换首因。

已读实际源码：section 创建 tab 时先 style.apply 再设 minHeight=dp(48)，但 showSettingsSection 对全部 tabs 再执行 visual.palette.apply；共享 apply 尾部无条件把 TextView.minHeight 改成 sp(type.lineSp)。因此创建期的48dp被后续选中/未选中重绘覆盖。不能只在创建处再加一次 minHeight。

## 最小批准范围

- [ ] 在 showSettingsSection 的每个 tab 内，紧跟 visual.palette.apply 调用恢复真实可点击 View 的 `minimumHeight = dp(48)` 和 `minHeight = dp(48)`。两个下限均设置；仍 wrap_content，自然文字+上下padding可以超过48dp，不固定height、不缩字。创建处也保留原48dp；可补相同 minimumHeight 以保持初始化一致，不重构组件。
- [ ] 保留原选中下划线/颜色/500与400字重/文案/8dp上下padding/横向滚达/点击回调与 selectedSettingsSection。只修本页5个同构 tab，不泛改TaskProductVisualStyle，不引入TouchDelegate伪装实际View高度。
- [ ] 静态验证5个 tab 初始、每次 showSettingsSection、恢复 selectedSettingsSection 时均先字体再触控下限；与原源码反例确认旧调用会覆盖 minHeight、新调用恢复下限。冻结实际单文件SHA交Astra。此低影响小改不新增只断常量的单元测试。
- [ ] root 统一Mock双包构建、现Settings12格取证：四配置原5个section逐项真实View visible/enabled/clickable/width与height≥48dp，原click门禁不改。观察选中/未选中、recreated、1.3全文/下划线/组间隙与横向可达，后续特殊态仍执行，5秒/四零不变。新图/geometry确认≥108px才关闭，不能以编译或源码minHeight宣布通过。

协议/设备：无编码、队列、能力/未知记录/重试/停止变化，不发设备命令；Mock不作真机验收。不发版。普通批次提交推送当前分支，必需CI通过后再合并，不微推。回退仅本页高度下限增量，保留数据与原失败证据。当前仅计划，实施/新实图未完成；设备容量缺口另案处理。


## Astra 静态验收（2026-10-23）

通过。独立重算 `MachineSettingsActivity.kt` SHA256 `2ed9e0dce209aad1910546eaf239c907e681fdaacde669c772f774e6d63f8a9e` 与冻结值相同。实际diff仅 showSettingsSection 的 palette.apply 后增加一行两个赋值：minimumHeight/minHeight=dp(48)，全部5个tab、选中及未选中均执行。后续颜色、isSelected、typeface与背景不重新调用字体apply，因此不再覆盖该下限。仍原wrap_content，可自然增高；创建处原minHeight48保留。无字号/padding/下划线/监听/设备逻辑或测试门禁变化。独立diff --check通过。

只静态通过，尚未以新Android原图/geometry证明实际108px及完整文字；须保持原click门禁执行并核四配置与recreate，不据源码赋值直接关闭真实首败。不构建、不提交推送。
