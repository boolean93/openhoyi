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

不新增文案/翻译，不建通用通知框架，不全局替换Toast。

### 1. 先迁移原断言，保留真实失败覆盖

原fixture `language-write-rejected`、`feedback-write-rejected` 与SAF cancel/fail/success顺序保持。语言失败检查旧selected/current未改且dialog内完整错误TextView可见；反馈失败检查原prefs/开关旧值且组内错误全文可见。把原SAF成功/失败等待 `TYPE_NOTIFICATION_STATE_CHANGED` Toast条件迁移为对应真实结果TextView更新条件；取消仍不导出，真实owned URI结果/文件业务断言全部保留。不能删除错误可见断言，也不能造成功字符串代替实际导出回调。

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
