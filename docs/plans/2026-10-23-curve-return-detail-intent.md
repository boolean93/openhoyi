# Curve 返回时保留详情意图 Implementation Plan

> 执行者：Sol，使用 executing-plans；仅实施本计划，保留他人改动。Astra 静态复核，root 独占完整构建与合批推送。

**Goal:** 生命周期刷新不能把双栏的可见详情误当作用户打开详情的意图。

**Architecture:** 保留单参数 `show(item)` 显式展示入口，将其渲染主体提取为本类私有方法；`onStart` 使用进入刷新前的意图且不主动重置预览滚动。不改测试接受条件。

**Tech Stack:** Kotlin / Android Activity、TaskCollectionLayout、既有 instrumentation。

## 范围与基线

遵循 [git-workflow](../git-workflow.md) 和 [任务模板](task-template.md)。develop 来源的现有迁移工作分支 `feature/task-first-ui`，当前基线 `b08adeca02326a6765973bf404385871af63c505`，PR 目标 develop。本轮不另分支、不微推。

仅允许修改 `mobile/src/main/kotlin/io/openhoyi/mobile/CurveActivity.kt`；回归断言可增补 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveLibraryViewportChecks.kt`。不得改 `CurveVisualMigrationChecks.kt` 的十项等值断言、返回诊断或 runner；不得改共享布局、编辑/导入存储、设备行为。

## 已证原因

`ci-b08adec/curve-import-return-business.json` SHA256 `89b2fc453d6d43f5e0d9310a80e7897613e404fd07eae7416c5796a94600ac04`（独立重算相同），所属 run 39907715471、ZIP SHA256 `bcc42697ddc0f9792ebcc765bab2b6b27709b905999733daac0e8ed7aba303a6`。只有 detailRequested：false→true；列表八字段、两条 scrollY、返回窗口焦点和五项业务条件均相同。这是恢复采图之后的 main 快照，不声明同合成帧。

源码 `onStart` 的 `wasVisible = !compact || detailPanel.visibility == VISIBLE` 在双栏为 true，继而 `show` 无条件写 detailRequested=true、detailScroll.scrollTo(0,0)。TaskCollectionLayout 的双栏可见性本来独立于 detailRequested。因此保留 wasVisible 作为是否需要刷新可见内容的判断，但不能以它改写意图。

## 最小实施

- [ ] 保留 `private fun show(item: CurveLibraryItem)` 的 JVM 单参数签名，避免破坏现有反射测试；其调用新私有方法 `renderPreview(item, requested = true, resetDetailScroll = true)`。
- [ ] 将原 show 主体逐行移入 `renderPreview(item: CurveLibraryItem, requested: Boolean, resetDetailScroll: Boolean)`。仅把 `collection.detailRequested = true` 改为 `collection.detailRequested = requested`，把 `detailScroll.scrollTo(0,0)` 改为 `if (resetDetailScroll) detailScroll.scrollTo(0,0)`。其余字段、次序、列表 Parcelable 保存恢复及能力判断不变。
- [ ] `onStart` 在 refreshList 前增加 `val requested = collection.detailRequested`；原有效 previousId + visibleItems 门槛及 wasVisible 分支保留，原 `show(it)` 改 `renderPreview(it, requested, resetDetailScroll = false)`。隐藏分支仍只更新 selected 和 adapter；不存在/已过滤条目不恢复过期意图。
- [ ] 不增加 post/sleep/retry，不先调用 show 再补写 false，不写 preferences、不主动选择新导入项。fallback ScrollView 原来没有主动 reset，本次仍不增加 reset。真实内容变化或视口缩小时 Android 合法 clamp 不用旧越界位置强行覆盖。

## 入口语义与回归

| 入口 | 本次必须保留的语义 |
|---|---|
| 首次展示、有/无持久化 selected | onCreate 原 initialId/show/showBrowser 逻辑不变；首次 onStart 只保留其结果，不另开详情 |
| 列表明确选中 | show 单参数仍请求详情并重置原 detailScroll；列表独立视口不动 |
| 导入页 Cancel、普通离开返回 | 同一条目最新内容仍刷新，原 false/true 意图均保持；内容与窗口未变时两条预览滚动和列表精确不变 |
| 编辑保存同 ID | 编辑器 store.save 后 finish，library 动态 draftsProvider 仍重新解析并渲染最新对象，不因旧 data class 不等于新对象而跳过刷新；保留刷新前意图，不强制滚回顶部 |
| 导入成功、新条目 | Preview 保存后 finish，最终返回库仍 refreshList；旧选择仍存在则保持，不自动选导入条目 |
| 当前条目删除/过滤移除 | refreshList 原清空 selected/详情/动作路径保持；不得恢复失效 ID 或 true 意图。CurveActivity 当前没有新增删除入口，本项只检验存储移除后的刷新边界 |
| recreate | saved selected/listState/category/query/detailVisible 原保存恢复合同不变；本轮不新增跨进程状态 schema、不把 detailVisible 改解释成 detailRequested；随后 onStart 不再二次覆写恢复结果 |

- [ ] 既有 `CurveLibraryViewportChecks` 内增加真实 Activity/main 回归：双栏选定条目后设 detailRequested=false，调用生命周期刷新前后断言仍 false、相同 selected ID、相同列表状态；true 分支亦保持。用实际可滚内容设置非零 detailScroll/fallback（分别适用的呈现，不伪造不可滚高度），刷新后重新布局再核坐标。若当前 fixture 无可滚范围，明确记录不覆盖该分支，不能以 0=0 冒充非零回归。
- [ ] 复核原单参数 show 反射入口、显式选中仍 true/reset；编辑同 ID 新内容/移除当前项/新增不抢选择的回归可在现有 Mock fixture 中完成并严格恢复 store/preferences。不得向生产添加测试钩子或新功能。保存/重建分支至少静态逐入口核对，未实际执行不得报 runtime PASS。
- [ ] root 统一构建，原 Curve visual return 十项断言原样跑；精确源码的新 CI 恢复图/快照和两条滚动证明通过后才能关闭本首败。该修复不证明整个 Curve 或全 24 页通过。

## 验证、合批与回退

Sol 交实际 diff、生产/测试 SHA、`git diff --check`，列明真实运行和仅静态检查。Astra 重点审 wrapper 签名、两个差异点、refreshList 清除边界与无额外状态改动；root 执行统一 Mock 双包构建与相关现有 CI，不重复微推。普通推送当前分支、更新 PR，必需检查和讨论通过后才能合并。

协议/设备风险：仅本地呈现意图，不改编码、单位、队列、BLE、未知记录、重试或停止，不做硬件写入。不发版。回退仅本计划源码/测试差异，以新提交或 revert PR，保留数据和未知记录，不卸载清记录。

未完成：实施、静态验收和精确新源码 CI 尚未执行；原失败原件保留。其他 Settings/Legacy/M5 工作不在本计划内。


## Astra 首次静态复核（2026-10-23）

生产 `CurveActivity.kt` SHA `741ac8c9d7ab3592d47a3c2d702fedfa89fc93d2f323f9ec0bd09100a0cd7839`：独立核实际diff通过；恰为约定 wrapper/私有 renderPreview、onStart 提前保存 requested 和两个受控赋值，其他渲染次序/刷新/失效选择分支不变。原十项断言不在本次修改范围。

测试 `CurveLibraryViewportChecks.kt` SHA `2a08195ccef69d917c0c0a834cdada91825b317be98a72a98fc4e84b6ca9b3e7`：暂不通过，须两处最小修订。

1. 新 helper 开头的真实 selected/collection.detailRequested/detail.scrollY/fallback.scrollY 及第一次 `before = viewport()` 读取发生在 instrumentation 线程。全部放入 main，冻结原对象/原值及首次 viewport 后只传不可变结果，不以主线程之外的 View getter 当回归依据。后续现有 main 断言保持。
2. helper finally 的恢复或 waitForIdleSync 失败会覆盖原断言 Throwable。保留 try 主异常，cleanup 失败 suppressed；没有主异常时 cleanup 仍抛出。不得吞恢复失败或写成功报告冒充失败恢复成功。

非零范围不足时明确 detailNonzeroCovered/fallbackNonzeroCovered=false，符合不冒签约定；真实 Activity 回归尚未执行，静态红绿模型不替代它。生产已认可，测试修订后须独立新 hash 窄核才能给两文件整体静态通过。


## Astra 两项修订窄验（2026-10-23）

两文件整体静态通过。独立重算生产 SHA 仍为 `741ac8c9d7ab3592d47a3c2d702fedfa89fc93d2f323f9ec0bd09100a0cd7839`；测试新 SHA `f17730b36ae05743c76684faadeb5f7968cbe61fe6b940cb0fa10c37edfd4d35`。

实际读新 helper：原对象、selected、detailRequested、两 scrollY 已同 main 冻结；首次 viewport 也 main 读取，后续所有 View 断言/滚动仍在 main。catch 保原 Throwable；finally 的 show、意图、两滚动与 waitForIdleSync 分别尝试，cleanup 第一个错误保为主 cleanup，余错 suppressed，再挂到原主异常；无主异常则 cleanup 失败抛出。两阻断关闭。生产与原十项视觉返回断言未改变。

非零实际滚动回归的覆盖位仍按真实范围记录，没有把无范围算已覆盖。此结论不证明回归已在 Android 执行，也不替代新的 import Cancel 真实返回证据；原失败只有 detailRequested 的结论与原件保持。
