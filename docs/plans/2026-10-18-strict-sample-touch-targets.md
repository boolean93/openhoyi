# 原生样张触控容器判定与失败取证

- 范围：仅修 `StrictVisualSampleChecks.kt` 的触控目标识别与该失败的预验证审计；纯Mock helper和JVM测试。不得改产品、样张绘制、字体取证或另一Sol的文本布局检查。
- 来源/工作分支：沿用 `feature/task-first-ui`，基线head `a4786a0`；PR目标 `develop`、PR #4。依 [Git工作流](../git-workflow.md) 保留他人未提交文件；本批不新建/重写共享历史。
- 失败证据：frame/run39119880479 的 `light-1.0-result.txt` 在首张PNG后失败：`Touch specimen below 48dp: sample/nav/item/1/icon`。本地路径 `build/task-first-evidence/ci-a4786a0-frame/task-frame-tablet-user-landscape/task-first-evidence/strict-native-samples/`。
- 源码身份：PR head是 `a4786a0`，该次CI checkout/source目录 `33a71f7ae8c9022bdfb6f147d30e53165d493e8f` 是PR合成merge，不误报为source错误。
- 根因：旧 `tag.startsWith("sample/nav/item")` 把24dp图标和文字子节点当作48dp触控容器。真实导航容器由 `navItem` 创建，最小64dp；其他展示动作由 `activity.actionViews` 按对象身份登记，故不能只按tag前缀或isClickable判定（样张无业务callback）。
- 步骤：保留原失败红证据，先写7项纯策略测试；用登记对象身份识别action，用精确导航语义且必须ViewGroup识别导航容器；真实目标仍严格检查宽高≥48dp，禁用展示动作不豁免。先写geometry/预验证visual，再执行触控断言；失败仍中止，不转为通过。
- owned文件：`StrictVisualSampleChecks.kt`；新增 `src/mock/.../StrictVisualSampleTouchPolicy.kt`、`src/testMock/.../StrictVisualSampleTouchPolicyTest.kt`；本规划。helper和测试均限Mock source set，Debug测试不引用Mock helper。不修改 `TaskTypographyEvidence`、`TaskTextLayoutEvidenceChecks`、runner/workflow或绘制文件。
- 验证：本批与另一Sol稳定后由主任务统一Gradle；实现前只做diff和原失败/分类红证据检查，未独立构建/安装/运行设备。Mock定向7项JVM、Mock应用/AndroidTest APK和Debug测试编译已通过；原生样张运行、完整12张及Astra G1–G4仍待验证，不把编译/纯策略测试当视觉通过。
- 推送/合并：仅本地修改/验证及获主任务安排后的本任务文件提交；合批普通推送由主任务处理，不推送/合并main/develop、不取消在途CI。
- 协议/数据：仅Mock展示检查，无设备命令、协议、服务、偏好、存储或业务回调变动；不清数据或未知记录。
- 发布：不发版、不改包名/签名/版本/升级边界。
- 回退：追加revert本批检查/helper/测试，不删除真实失败证据、不卸载清数据。
- 未完成：精确源码原生样张CI、完整12张及Astra读图验收；本地编译/7项JVM和源码审查已完成。

## 审查与source-set修正

Astra已无阻断通过触控身份/精确容器语义、严格48dp宽高、六项导航完整性及预保存逻辑。首轮测试原在通用 `src/test`，Astra指出Debug变体不可访问Mock helper；待主任务47秒构建结束后，测试仅移动至 `src/testMock`，未把helper移入产品main。

- 原失败/分类红检查：已直接读取CI失败txt；对原 `startsWith` 判定要求装饰icon不是目标时，实际 `AssertionError: RED: existing prefix classifier marks 24dp decorative icon as a touch container`。新JVM文件先写后实现，但未伪称执行过缺helper时的JVM编译红阶段。
- 首轮共享构建（含触控与G3文本取证）：退出0、`BUILD SUCCESSFUL in 47s`、96 tasks（12 executed/84 up-to-date）；Mock应用及AndroidTest APK完成。日志 `/tmp/2026-10-18-sample-touch-layout-build.log` 已读取；当时测试尚在通用目录，不用该轮证明Debug可编译。
- 移动后的变体验证：退出0、`BUILD SUCCESSFUL in 32s`、65 tasks（19 executed/46 up-to-date）；Mock定向7项与 `:mobile:compileDebugUnitTestKotlin` 均通过，日志 `/tmp/2026-10-18-sample-touch-variants-build.log` 已读取。XML `mobile/build/test-results/testMockUnitTest/TEST-io.openhoyi.mobile.StrictVisualSampleTouchPolicyTest.xml` 为7项、0失败/错误/跳过，包含图标/文字排除、精确导航身份、非容器拒绝、真实目标宽高不足拒绝及禁用展示动作不豁免。
- `git diff --check`与分支策略检查通过。本批没有新增业务回调、不安装/运行设备、不推送；新CI必须证实样张可完成取证，仍不能据这两轮构建关闭G1–G4或宣称12张已存在。
