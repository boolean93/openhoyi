# M2/M3 recreate 后 IME 隐藏：源码与原件因果核对

## 结论与范围

当前 `643d8281ee46a4765133ec6e9796a971d8120dc8` 源码冻结，不改恢复逻辑、测试条件、等待时限或场景。此次找到可证伪的具体机制：**保留 Window 的 recreate 拆除旧输入框，可能先发出旧连接的 hide；同 Window 接收的异步 hide 消息随后在新输入框已 show 时才执行，并经动画结束通知形成后续 hide。** 这是有源码和时序支持的首要假设，不是已经捕获调用栈的根因结论。

没有发现可以凭现有证据直接批准的产品修复。M2/M3 继续 FAIL/未知，不重试 show，不改为等待断言成功，不拿结果码或三帧稳定替代最终 IME/scroll 实况。

## 能直接证明的内容

| 事实 | 源码/原件 | 能说明什么，不说明什么 |
|---|---|---|
| 测试走真实 `Activity.recreate()`，不是手工 finish/start | `AdvancedBusinessUiChecks.recreate`；`CurveVisualMigrationChecks.importBusiness` | 与系统本地 relaunch 同路；无需怀疑测试伪造了新 Activity |
| 活跃 Activity relaunch 默认保留 Window，销毁时 `clearContentView()` | SDK34 `Activity.java:6949`，`ActivityThread.java:5845`、`:5654` | dc 的旧/新 Activity、field 不同而 decor/token 同一，是支持的系统行为，不是 identity 检查失效 |
| 旧 served View detach 可清掉 `mNextServedView` 并排入 checkFocus；next 为 null 时 `closeCurrentInput()` 发 hide | SDK34 `InputMethodManager.java:862`、`:2975`、`:3038` | **存在**不经过应用 `hideSoftInputFromWindow` 的 client-origin HIDE_SOFT_INPUT 路径；尚未证明本次执行了该分支 |
| `W.hideInsets` 在 Binder 接收端只排入 `MSG_HIDE_INSETS`，主线程稍后调用 controller.hide | SDK34 `ViewRootImpl.java:10368`、`:8973`、`:5940` | 接收时的旧/新 field 与真正执行时可能不同；看到新 field 的 controller 快照不等于 hide 在新 field 时发起 |
| `InsetsController#hide` 的该 native dump 仅在 `fromIme=true` 分支产生，且在执行方法体之前 | SDK34 `InsetsController.java:1168` | 不能把该快照直接说成应用调用 public `WindowInsetsController.hide()`；不证明本次 hide 必有非空动画，也不标识请求来源栈 |
| 动画结束且未请求显示时会 `notifyHidden(null)`；`notifyImeHidden(null)` 自建 HIDE_SOFT_INPUT_BY_INSETS_API token并再次通知服务 | SDK34 `ImeInsetsSourceConsumer.java:63`；`InputMethodManager.java:3135` | dumpsys 中该 reason **不唯一指向应用主动 Insets API hide**，可能是框架反馈链 |
| 产品 finish 移除 animation callback 不直接调用 hide | 两产品 `finish()`；SDK34 `View.java:11959` 仅赋 callback 字段 | 排除“setWindowInsetsAnimationCallback(null) 本身直接 hide”这个具体解释；不排除其它并发框架状态变化 |

上述 SDK 是本机官方 Android34 源码，不是 CI 系统二进制逐字认证。既有 service proto wrapper 差异、collector PARTIAL 边界继续保留。

## 同一 dc 原 trace 中已存在、无需再采的顺序

来源：`build/task-first-evidence/ci-dc5875c-curve/m3-offline/timeline.json`（66 entries）及该目录的既有诊断、原三 trace。使用设备 elapsedRealtime，不把 wall time 当请求 ID。

| elapsed 秒 | 原事件 |
|---:|---|
| 55.116034 | fixture 调 `recreate()` |
| 55.143873 | manager-service hide，editor fieldId=1（旧） |
| 55.155093 | `ViewRootImpl.W#hideInsets` 接收 |
| 55.488561 / 55.491110 | 产品唯一 show 请求 / manager-service show，fieldId=3（新） |
| 55.527510 | `InsetsController#hide` 执行入口，新 field、原 decor |
| 55.679552 / 55.687678 | animation finished / `notifyImeHidden` |
| 55.687856 / 55.807201 | manager hide / IME service hide |
| 55.891642 | 第二个 `W#hideInsets` 接收 |
| 56.063167 / 56.072485 | 产品实际 IME=true 时应用 scroll=4885 / 宣告本次恢复完成 |
| 56.085248 | 第二个 controller hide 入口 |
| 56.333059 / 56.333287 | notifyImeHidden / manager hide |
| 56.880562 之后 | 原比较观察 IME=false、scroll=4281，失败 |

这条顺序与“旧 hide 入队跨过新 show，再由动画结束反馈”的假设一致。**两组 W→controller 并没有同一 statsToken/消息身份的原始链接，不能仅按先后强配对。** 尤其第二个 W.hide 在产品 apply 之前已接收，因此不能仅因第二个 controller.hide 在 apply 之后，就认定是产品 scroll/finish 新触发的 hide。

## M2、M3 不混为同一已证根因

- dc M3：产品曾实际 apply=4885 且当时 IME=true；终态=4281、IME=false。产品“完成”只描述当时满足的 native 条件，不抵抗随后异步 hide。活动动画集合为空，也不能证明 Binder/Handler 中没有尚未交付的 hide。
- 708 M3 原件 `ci-7082e04-curve/raw/raw-device/847a2befd04f0384edc133958a95d5cc4fab91ee/.../import/raw-restoration.json`：scroll 4885→4270；产品仍 `WAITING_FOR_NATIVE_WINDOW`，没有 apply 事件。请求时 IME=true、hostHeight340，结果回调 IME=false、hostHeight1051；`showSoftInputReturned=true`、resultCode=2。**本原件不是 show 方法返回 false**，应区分方法返回值与结果回调时的可见性。其终态失败无需先假设“恢复后又变了”。
- a47 M2 已核原件：11 字段、选区、焦点、draft 身份不变；IME true→false使 content 680→1391，有限 scroll range662→0，产品停在等待、无 apply。scroll=0符合变大视口的合法范围，不能凭它批准 clamp/store 修复。
- 原测试在截图/unfocus 之前比较；dc trace 无 failure 前 fixture_unfocus marker。三个稳定布局帧只能证明观察窗口内稳定，不能排空未关联的 IME Binder事务；严格比较仍应保留，不修改成“等相等再通过”。

## 唯一下一缺证与停止条件

最值得验证的单一假设 H：**recreate 的旧 served View detach→checkFocus(next=null)→closeCurrentInput 发出首个 hide；同 Window 的入队 hide 在新 show 后执行，后续 hide由通知反馈产生。**

现有证据缺的是首个 hide 的**实际调用链/请求归属**，不是更多 field/token 快照。继续重复同三 proto 或再加 lifecycle/geometry 日志不会补齐它。先等当前 643 CI 原件，不启动第二场景。

若同一失败仍需下一轮定位，可只选择已有 M3 light1.0 的原 recreate 区间做一次有界、非采样 ART method trace，利用公开 `Debug.startMethodTracing(path, fixedBufferBytes, 0)`，结束仍在原比较/finally内；不改产品，不重复 recreate。离线只回答上述方法链是否出现、主线程 enter/exit 顺序及 `W.hideInsets→MSG_HIDE_INSETS→controller.hide` 是否有完整可辨调用轨迹。该方向需先单独落实格式/溢出可判定与 owned 文件合同，**本记录不授权直接追加实现**；若框架方法未覆盖、缓冲溢出、跨线程请求仍不能唯一对应，结论保持 UNKNOWN，停止追加同类 trace。

SDK34 Debug 文档明确 method tracing 会使 VM 变慢。因此该候选仅能验证调用路径；它的延迟、偶然通过或原失败未复现不能作为正常时序/视觉/恢复验收，不能用于放宽当前门禁。最小替代是能够取得同一首个 hide 调用栈的已支持调试设施；不要绕 hidden API、注入 hide/show、取消原 pending 消息或改变 preserveWindow 来制造结论。

若确认 H，才另审“产品何时有公开可证明的新窗口请求生效边界”的修复；现阶段没有公开 pending-hide 队列已空 ACK，不能建议固定 sleep、多帧数、重复 show 或强保 IME。若 H 被否证，则沿捕获的实际首个 hide caller 定点处理，不并行撒日志。
