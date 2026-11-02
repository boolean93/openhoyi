# AppSettings：相关区域内联最新操作反馈

> 执行者：Sol；Astra独立静态/实图验收，root统一构建及提交。当前仅计划，排在M2工作之后。

## 决策与仓库边界

需求是消除真实反馈遮挡，不是让截图避开Toast。依据 [120目标实图报告](../evidence/2026-10-24-app-settings-special-limited-review.md)：四配置反馈保存失败、主题重建残留导出Toast，以及dark1.3英文初态残留语言失败Toast，共9目标覆盖试听动作/说明。root已批准相关组内联方向。备选继续Toast或底部覆盖式Snackbar仍可能遮挡；仅等待/隐藏截图不能改善用户页面，故不采用。

- source：`929de4e4ba7ea9e1363a6ab8bfe37cb2c6c8c61d`，现有 `feature/task-first-ui`，PR #4 → `develop`。执行前记录实际HEAD和工作树，保留他人改动；遵循 [Git工作流](../git-workflow.md)。
- 普通合批，不直推main/develop、不force。root运行验证、普通推送、更新PR，必需CI通过后才可合并。
- 不发版、不签名/版本变更、不安装、不做设备操作；音频真实输出不由Mock证明。无协议/机器控制/未知记录修改。
- 回退仅新的revert/修复提交，保原数据和未知记录；不清数据、不降版本。

## 冻结视觉与行为

使用现有 `TaskProductVisualStyle.auxiliary`：14sp、冻结字体/自然行盒，错误 `mobile_danger`，成功普通正文色；与关联内容8dp间距。透明背景，继承原卡片/dialog背景，不新增浮层/固定底条。不截行、不省略、不缩字号；空状态GONE不占位，显示时自然增高并沿原滚动布局可达。不改变16dp面板内距、24dp组间距、原按钮触点或试听说明。

1. **语言失败**：在现有语言dialog内容内、8选项列表后/取消动作前显示完整 `language_save_failed`。保旧radio选择与dialog继续打开。列表使用原选择逻辑/52dp自然行、剩余空间可滚；不能压缩8项或把错误塞入按钮。只更新一个状态TextView，live region polite，不requestFocus/announce双播。重复失败替换原值，成功/取消/close清理；非AppSettings旧调用者保持原行为。
2. **反馈保存失败**：原反馈组，开关与其说明之后、试听分隔/按钮之前加入单一状态行。保存失败原refresh/旧值恢复不变；成功设置清除该错误。试听start/stop、离页停止不改变。`stop()`清理页面状态；不从截图fixture隐藏。
3. **诊断导出**：原诊断组export按钮后、machine分隔之前单一结果行，完整显示原success/failed/not_started文案（包括异常类型）。新一次导出操作清旧结果，完成替换，不并排积累。SAF取消不制造成功。主题/语言重建与离页清理当前页面状态，不把旧结果投递给新页面。
4. **主题保存失败**：现有内联themeError已合理，保持原旧值恢复与完整错误，不改其行为。

## 改动文件与实施顺序

所有路径相对仓库根，仅限：

- `mobile/src/main/kotlin/io/openhoyi/mobile/AppSettingsActivity.kt`
- `mobile/src/main/kotlin/io/openhoyi/mobile/AppLanguagePreferencesCard.kt`
- `mobile/src/main/kotlin/io/openhoyi/mobile/BrewFeedbackPreferencesCard.kt`
- `mobile/src/main/kotlin/io/openhoyi/mobile/MobileApplication.kt`
- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt`
- `scripts/check_settings_visual_migration.sh`（root后续明确批准的第6文件，仅SAF两结果字段断言）

不新增文案/翻译，不建通用通知框架，不全局替换Toast。

### 1. 先迁移原断言，保留真实失败覆盖

原fixture `language-write-rejected`、`feedback-write-rejected` 与SAF cancel/fail/success顺序保持。语言失败检查旧selected/current未改且dialog内完整错误TextView可见；反馈失败检查原prefs/开关旧值且组内错误全文可见。把原SAF成功/失败等待 `TYPE_NOTIFICATION_STATE_CHANGED` Toast条件迁移为对应真实结果TextView更新条件；取消仍不导出，真实owned URI结果/文件业务断言全部保留。不能删除错误可见断言，也不能造成功字符串代替实际导出回调。 host原 `actualFailedToast` / `actualSuccessToast` 两断言同步迁为 `actualFailedInline` / `actualSuccessInline`，不保留旧字段true伪称Toast，也不删除验证；取消零写入、真实URI/ZIP结果断言不变。

为原dialog frame observation纳入新错误TextView的geometry/typography，不去掉既有8选项与取消资格；新错误为空不伪造可见节点。保原5000ms/四零及原状态数量，不加sleep/重试/遮罩/延长时限。

### 2. 两张卡片使用页面限定内联反馈

`visual != null` 的现AppSettings卡片路径采用上述状态行；非visual调用保原Toast以避免跨页行为漂移。语言dialog有单一真实错误owner，close清理引用；反馈卡片status owner随卡片销毁。不修改preferences写入、回滚、通知刷新或audio owner。

实现可复用现有 `style.text("", TaskProductVisualStyle.auxiliary)` 并设置 `visibility=GONE`、`accessibilityLiveRegion=POLITE`；写入非空时先设置完整文本再VISIBLE。不得给status强制单行/maxLines或焦点。只对当前操作更新单一状态。

### 3. 导出回调保持旧调用者兼容

在 `MobileApplication.export` 增加可选页面结果回调，默认null维持原Toast。示意契约：

```kotlin
fun export(uri: Uri, onResult: ((String, Boolean) -> Unit)? = null)
// String = 使用当前资源解析的完整原UI文案；Boolean = 是否失败。
// onResult != null：主线程仅回调，不再发Toast；null：原Toast路径。
```

原 `logs.record("ui.export")`、写入URI方式、logs.export、export.finished文案与异常处理顺序保持。异步成功/失败和启动RuntimeException三分支均通过同一结果分发，不遗漏not_started；实际日志错误不改为成功。

AppSettings传页面限定回调，只更新其诊断状态行。使用当前页面生命周期/操作代次拒绝旧回调（建议WeakReference页面+每次export序号，`onStop`使旧序号失效，destroy不持有View）；新结果不得回退为默认Toast，不能在旧Activity完成时给新Activity写旧消息。SAF返回后该次export正常启动并可在恢复交互页显示，不能简单以onActivityResult瞬间未RESUMED而永久丢弃结果；可保存本页面有效pending结果并在onStart呈现，后续onStop/重建清除。不cancel实际导出、不改变文件副作用，仅隔离页面呈现生命周期。

### 4. 有限验证与交付

- `git diff --check`；静态核所有export旧调用者默认路径、AppSettings三分支、卡片非visual路径及onStop/重建旧回调不发Toast。
- root统一 `./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest`，冻结源码SHA/日志/APK；不以编译宣称视觉闭合。
- 复用原Settings正式四配置及原特殊态采集：8语言/dialog、拒绝、试听start-stop/leave、SAF三业务分支、theme-success-recreated。原严格业务/错误全文/窗口合同不变。新增状态行只在对应原case中核，不另扩矩阵。
- Astra实际原尺寸看反馈失败、语言失败dialog、theme重建及导出结果已有目标：错误/结果完整，试听按钮说明不被覆盖；语言8选项/取消仍可读可达。SAF没有PNG时仍不能签其独立视觉，业务记录保持独立。
- 提交前由Astra复核精确SHA，root确认仅授权文件；本计划和报告随合批保存。当前没有实施/构建/新CI证据，不声明9目标已修。

## Astra 冻结静态验收（2026-10-24）

六文件限定diff审阅通过，无当前静态阻断；不是新PNG视觉通过，也不是构建/设备通过。`bash -n scripts/check_settings_visual_migration.sh`、`git diff --check` 均exit0。

- 语言保原single-choice adapter/8项、52dp自然行与旧radio恢复；`setView`仅添加辅助14错误内容，未改列表文字尺寸/ellipsis/触点。原取消按钮不改，错误无requestFocus，polite live region。SDK实际dialog列表/custom panel空间仍以原四配置采集和严格whole为准，静态不假称8项新布局实测通过。
- 反馈错误在开关说明后、原试听分隔前；成功写入和stop清理，真实偏好回滚/audio行为保留；非visual旧调用者Toast不变。
- 导出callback仅捕获WeakReference和代次，onActivityResult新代次、未onStart暂存、onStart显示、onStop/onDestroy递增清理；旧结果不落到新Activity、不回退Toast。业务导出继续完成。生产另一个调用者HomeActivity使用默认null，原Toast及ui.export/export.finished日志/URI写入方式保留；启动异常仍有完整not_started结果。
- fixture错误全文/旧值检查保留。最初截图后off-main新增View读取已修正为accepted `frame.target.nodes` 唯一semanticId节点核全文/whole；点击后状态检查仍main。SAF等待真实inline结果，host仅两字段名等价迁移；cancel零写、fail零写、success一次写与ZIP metadata、owned provider清理均未删除。原5000ms四零不变，原业务等待10秒未扩张。

| 文件 | SHA256 |
|---|---|
| AppSettingsActivity.kt | `52cd1984ffdee7039e1b7ff8484cd026d4da4d4c1648700003bb26de01833cf7` |
| AppLanguagePreferencesCard.kt | `27e641d4630c3bbc90291c6112e53e8cd491a30aaefc2990438345f6f52eac87` |
| BrewFeedbackPreferencesCard.kt | `4229ddc41c45777317d8d16c2ebe7edcf5a8640ac09d3fd4b95db613046a2a76` |
| MobileApplication.kt | `09641454da9a08b39f689687cf7bfa5be5fc632287727dc182600195772ad080` |
| SettingsVisualMigrationChecks.kt | `519eeef72c5234462fbffa06753cd69913e14e9d4da1d6ffec0386f712ecbc66` |
| check_settings_visual_migration.sh | `02b23a617de107cc48e41f6d2ea162f369584dab15c8e3ce48a985416fabca6d` |

可由root继续统一两包构建/普通合批；原9个Toast遮挡目标须新source实图关闭，旧报告事实不回写成通过。

## Root 构建证据

在 `c2f4a0e` 加本计划六文件冻结修改上执行 `:mobile:assembleMock :mobile:assembleMockAndroidTest`，BUILD SUCCESSFUL in 1m 4s，91 tasks（9 executed）。构建前后451个仓库Kotlin源SHA一致；日志、源清单和APK摘要在 `build/task-first-evidence/ui-2026-10-24-app-inline-feedback/`。

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA256 `c054b19a0ae034d95701db228b9e4b4987863e7df447895d48f09b07f58c0602`
- `mobile/build/outputs/apk/mock/mobile-mock.apk` SHA256 `5189c82c87008fb6ced904899dc0bf6125b29f1fb4bd804ad2d6444b58870c7e`

`bash -n scripts/check_settings_visual_migration.sh` 与 `git diff --check` 通过。无安装、设备操作、合并或发版。此次只证明静态/编译通过，原9目标遮挡是否关闭仍待新CI原图，不提前签视觉通过。
