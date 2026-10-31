# M3 最新槽位反馈 Implementation Plan

> **For agentic workers:** 使用 `superpowers:executing-plans` 顺序实施。Astra 设计/验收，Sol 实施；root 独占构建、提交、推送。不新建代理，不覆盖其他人的改动。

**Goal:** 连续分配快捷槽位时只显示当前曲线最近一次本地分配结果，反馈不覆盖固定操作。

**Architecture:** 移除该业务点的逐次 Toast，在已有 `availability` 信息文字中保留资格说明并追加一个最新结果。只保存当前 Activity 的一份小状态，不引入消息队列、计时器或通用反馈框架；沿用现有详情测量与短窗 fallback。

**Tech Stack:** Kotlin Android Views、现有字符串资源与 `CurveVisualMigrationChecks`。

## 1. 范围与依据

- 延续 `feature/task-first-ui` / PR #4 → `develop`；develop 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`，检查 HEAD `aac5c917c4bada1b30602b263a49db5221f52c74`。遵守 [git-workflow](../git-workflow.md)。不是发版。
- 用户授权目标是可用且对稿的 UI；“最新反馈替换旧反馈、非遮挡固定动作”是 Astra 基于原图问题作出的本轮设计决策，由 root 分配为有限实施范围，不冒称用户逐字确认。实施结果仍需真实图审，不追认旧六图。
- [既有 Google/Apple 研究](../research/2026-10-15-task-first-ui-redesign-draft.md) 的 Google canonical list-detail 保持上下文，Apple Layout 关联分组、Buttons 固定主操作、Charts 数据优先与 Design Principles 清楚反馈，作为已有设计依据；此处不声称官方要求本项目采用某个具体控件。
- 具体字体/间距/颜色沿用 [M3 冻结规格 §2](2026-10-19-m3-curve-import-visual-migration.md)，不重新生成稿件或变更全局样式。
- [原图验收](../evidence/2026-10-24-m3-library-light1-limited-review.md)：五次槽位及 restored 共六图存在旧 Toast 覆盖“曲线详情”，但业务原件各槽位写入正确。无需先证明系统队列实现，已有证据足够做本地反馈设计修正。

## 2. 设计冻结

选择 **已有资格信息区内的最新结果**。未选“复用一个 Toast 并 cancel”：仍覆盖底部固定动作；未选 Snackbar/新常驻横条：会占额外区域或引入锚定/动画机制。

| 项目 | 本次决定 |
|---|---|
| 位置 | 详情标题下现有 `detailIdentity` 的 `availability`，保留 `detailCategory`。普通态示例 `已验证 · 已设为快捷槽位 5`；使用现有资格原文，不硬编码“已验证”替代业务判断。当前标题已指明曲线，不重复长名称。 |
| 字体 | 沿用 product font SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`，aux 14sp / 400 / 22sp，字距0，无伪粗、无额外 font padding。字号跟系统，不缩字。 |
| 颜色/背景 | 沿用资格已有 `productStyle.accent`（light `#087A78` / dark `#83D6D0`），不增加成功绿或底色；透明文字背景，承接现有内容面 light `#FFFFFF` / dark `#1B2526`。资格不可用时仍原 muted，不由反馈改变能力。 |
| 间距 | 不新增容器、padding 或边框。沿用标题至 identity 8dp；category 与 availability 同行间16dp，不够时既有纵向回退间8dp。自然换行，不 ellipsize 反馈。 |
| 图表与固定操作 | 常规宽度仍一行，不另占横条；若实际文本自然换行，沿用既有测量进入短窗 fallback，不压缩字体/160dp 图最小值，不覆盖图或 selectionDock。详情/用于本杯入口及能力不变。 |
| 1.3 / 短窗 | 保留原 `updatePreviewSpace` 自然测量。短窗里现有 `previewFallback` 的资格段使用合成后的 availability 文本，局部滚动可达；不新增整页滚动、不改 fallback 阈值或清空滚动位置。不能把短窗反馈放在已 GONE 的 metadata 中而不进入 fallback。 |
| 生命周期 | 不自动消失，无延时/动画/排队。最新赋值立即覆盖上一份（1→2→3→4→5 只保留5）。不同曲线选中、离开 Activity 到别的页面/后台时清除；同 Activity 同曲线重绘/打开并关闭 chooser 保留。重建不恢复该瞬时结果。资格原文始终保留。 |
| 可访问性 | 原生文字包含明确“快捷槽位 N”，不只靠色；对当前可见承载文字使用 polite live region，短窗只由可见 fallback 承载，不双重重复播报。无焦点抢夺。 |
| 存储语义 | 原 `PresetSlots.key(slot)` / item.id / `.apply()`、资格条件均不动。仅在原写入调用返回后设置反馈；文案是本地分配，不说“已同步设备”或“磁盘写入已确认”。不吞异常、不为反馈重复写入。 |

## 3. 有限实施任务

文件 owner：Sol 仅 `mobile/src/main/kotlin/io/openhoyi/mobile/CurveActivity.kt`、必要的 `mobile/src/main/res/values*/strings.xml` 新槽位短文案、`mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt` 的已有槽位链证据。其他 IME/Bean/Settings 修改不属于本计划。

- [ ] 在 Activity 保存 `lastAssignedCurveId: String?`、`lastAssignedSlot: Int?`，保留独立资格基础文字，渲染时组合 `base + " · " + localizedSlotResult`。勿从已组合文字再拼接，避免重复累计。不同 id 的 `renderPreview` 先清这两项；同 id 重绘不清。
- [ ] 原 `.apply()` 返回后记录当前 item.id 与 slot，重绘状态文字并请求现有 layout 更新；移除且仅移除此处 `Toast.makeText(...).show()`。不得重置 ListView 位置、detailScroll 或 fallbackScroll。
- [ ] 生命周期离开清理放在 `onStop`（调用 super），不在 chooser 的局部焦点变化/onPause 清除。清理同时还原基础资格文字，防止返回沿用陈旧结果；不存 Bundle。无延迟回调需要取消。
- [ ] 新短文案采用资源格式参数，例 `已设为快捷槽位 %1$s`。沿用本项目既有本地化策略，不能把中文直接写进 Kotlin。原长文案资源若其他地方使用不删。
- [ ] 同步现有短窗 fallback 资格段和 live-region 可见 owner；不加新的横条，已有布局算法保持原值。
- [ ] 扩充**原有**连续槽位 1..5 测试：保留每槽实际存储值/原key验证；每步记录实际可见反馈、期望槽位/curveId、资格基文、footer 可见范围。断言当前文案只有当前槽位，不累计前一结果；最后同曲线恢复仍为5（若未离开 Activity），不要误要求 Toast 消失再截图。
- [ ] 对现有 source-route 离开/返回和现有 short-pressure 证据，只补状态范围核对，不添加业务场景矩阵。不改变原 restored 列表/草稿/设置严格相等断言。若需要新增独立取证场景，先报 root，不自动扩大。

## 4. 验证、交付与回退

- Sol 先报精确 diff/文件 SHA，`git diff --check` 应为0；原 `python3 scripts/test_ime_viewport_probe_contract.py` 仅在共享 fixture 变更影响其契约时复跑，不能当反馈视觉通过。root 统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest`，保留日志、APK SHA。
- 沿用现有 CI 槽位链/恢复/已产配置，不新开设备操作。所有截图保持原5秒、四项差异全零、原失败尝试和真实窗口证据；不能 hide/cancel 反馈给截图开后门，不加 sleep。
- Astra 验收：五槽每次都对应最新本地结果；原资格仍可见；图表、两固定动作无遮挡；1.3/短窗自然回退保持全文可达。旧六图继续为未通过历史证据，只有新精确 SHA 原件才能关闭。
- 不改变 BLE/算法/设备槽位/门禁/未知记录/重试，Mock不证明硬件。无安装、发版或数据迁移。回退只追加修正/revert 相关源码，不清 presets，不撤销用户真实已存分配。
- root 合批普通推送工作分支、更新 PR，经必需 CI 与评审后合并；Sol/Astra 不独立提交推送。当前状态：设计已交付，实施和新原图验收未完成。

## 5. Astra 冻结源码静态验收（2026-10-24）

**结论：本次限定静态验收通过，可由 root 统一构建；不等于运行或新六图视觉通过。** 实际阅读完整增量及 `TaskCollectionLayout.onMeasure` / Activity原 onStart 链，未发现本范围新增阻断。

- 写入仍为原 prefs/key/item.id/`.apply()`，无设备路径；只有返回后才改最近状态，无吞错/额外写入。八语言资源各只有一个 `%1$s`，XML均可解析；旧长文案仍保留。
- `updateAvailabilityFeedback` 从独立 `baseAvailabilityText` 重建，不累积旧槽位。同id render保留，不同id或筛选排除选中时清理；生命周期 onStop清理再super，chooser不触发该清理。重建无保存瞬时槽位。
- 反馈更新后显式 `collection.requestLayout()`；`TaskCollectionLayout.onMeasure` **每次**调用 `onDetailSpaceAvailable`，不受 split 是否改变限制，再由 `updatePreviewSpace` 先取新 availability 的自然高、再构造 fallback文本，避免只有宽高变化才刷新。原160dp/8dp阈值、字号及局部滚动结构未动。新反馈路径本身不调用 scrollTo/show/reset。
- onStop还原availability并请求布局，随后原onStart使用 `resetDetailScroll=false` 重绘当前项；资格不会从带反馈的旧字符串反复拼接。清理不写presets，也不把离页当撤销分配。
- 最终正常布局只有availability为POLITE；短窗metadata隐藏，fallback为POLITE、availability为NONE；无requestFocus/announce重复调用。无压力普通布局保持availability播报，fallback只显示原无压力说明且NONE。静态只核最终可见承载关系，不冒称已实测TalkBack播报时序。
- 原槽位链保留磁盘实际值、白名单差分、服务为空与恢复断言，逐步检查最近id/slot/完整资格合成文案，记录可见承载者及footer几何；同曲线重绘不丢最后槽位、换曲线清理均纳入原链。source-route增加空状态检查，short-pressure只补原fallback资格段核对。未增加新的业务场景、sleep或掩码。
- 主动披露：这些普通route/short场景并未新造“短窗内分配槽位再离页”场景；本轮不把静态链路推成此组合已经运行。沿原授权范围验证，不临时扩矩阵。

精确SHA256（读取工作树独立核对）：

| 文件 | SHA256 |
|---|---|
| `CurveActivity.kt` | `adf62c249e6b88ec11aad92797253f2056a341029c9cd8e3c41c6b398b116e39` |
| `CurveVisualMigrationChecks.kt` | `7a7569164d7e01bc48e50134d8e3d17fe10252ed91d4928892ed9b7f51eade56` |
| `values/strings.xml` | `4d1e492666f030faef54b59b9446f5beec8851a917c06245d9f748f900c6b36e` |
| `values-ar/strings.xml` | `f3f780c31fd6e075c6a1cf2887e9cd3b7b6a30edcf411673e8cc2949fd258e4c` |
| `values-en/strings.xml` | `4b14e5aa5528a6bfcf524426aba21258e9f8aed359d97d48f001b4c156c50e4b` |
| `values-es/strings.xml` | `23607201a7e91ecaac2595627c6f6d24f731e2e53fe554fc7953a9534deca6da` |
| `values-ja/strings.xml` | `cfd395873009b32223a84ed7c5759f171378b9a3da7d417d3a4022faa75339f8` |
| `values-ko/strings.xml` | `2b1bd4651eb9a39d7cfcee3bbda227d89e190c5f256df8ebf8318f605b1cd48f` |
| `values-ru/strings.xml` | `5012c67350aa219bd4f1a80eadab6befc2905d3378b9ddf3b8eca195d00f83cb` |
| `values-th/strings.xml` | `e359d6393730f0dae0b6ecf1912a2ed7dc9e15e14a9aedb566925c1ee6b93fd3` |

Astra执行 `git diff --check` 返回0；未跑Gradle/设备。root构建结果另记，原Toast六图结论不变。

## Root 构建取证

2026-10-24：`:mobile:assembleMock :mobile:assembleMockAndroidTest` 退出0，1m9s，91 tasks（15 executed）。日志 `build/task-first-evidence/ui-2026-10-24-slot-feedback/build.log`。Kotlin及strings.xml构建前后SHA完全一致，见同目录source-before/source-after.json；APK哈希见apks.json。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA `cbd52da8208ede81f75336bb5b37688c334a10e52ccc96261d5aafa33930ee4f`。
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA `7529acb3f15a3f77041592bbc67efffe94f3aa0bc72c89151034f0b45fc71df8`。

此处只证明编译成功，新增仪器断言尚待CI实际执行，旧六张Toast问题不在本地构建阶段关闭。未安装、设备操作、合并或发版。
