# M6/M7 显式窗口取证静态审查

审查基线：49a1b46，2026-10-19；只读生产与测试代码。本记录不代表运行或 G1–G4 通过。沿 feature/task-first-ui → develop PR#4，遵守 docs/git-workflow.md；不安装、设备操作、推送、合并或发版。本文件由主任务统一纳入文档提交，回退追加修正即可。

## 已确认结构

M6 runner 要求两页 × 四配置共 8 主 manifest；M7 要求三页 × 四配置共 12。主图与特殊状态分开，实际协议证明逐份核验。M6 trendEvidence、short-height 和 short-height-trend-detail 目前保留 PENDING；不是完整严格取证通过。M7 detailReachability 的完整说明 dialog 同样明确 UNSYNCHRONIZED_DIAGNOSTIC_NOT_MAIN。只读静态观察不能证明实际 service/线程清理、屏幕内容或字体符合规格。

NativeWindowFrameEvidence 当前所有窗口引用绑定 Activity：copyWindow、focus、decor、currentWindowMetrics、takeScreenshot(Window)。实际 modal 持焦时 owner Activity 失焦是合法行为；把 Activity 焦点断言删除或 OR 任意其他窗口焦点均不可接受。

## 可实施的封闭修正

1. 保留现有 screenshot(Activity, root, file, observe) API 和普通 Activity 映射门禁。新增显式 screenshot(Window, root, file, observe) 入口，内部共享同一协议实现；不得另造弱版 dialog 截图器。普通 Activity 可显式传入原 metrics 校验模式；Window 入口必须记录采用的映射模式。
2. PixelCopy.request、UiAutomation.takeScreenshot(Window)、decor 与焦点必须来自同一传入 Window。主线程验证 root.rootView === window.decorView、root/decor 同 windowToken 与同 display、attached、hardware accelerated、laid out、尺寸正值、目标 decor 持焦、无待布局；前后复核同一对象及 token，不依赖 owner Activity 焦点。
3. 每次记录 root/decor 的 inWindow 与 onScreen 坐标、宽高、transform、真实 rootWindowInsets、display ID/尺寸、window attributes 与 currentWindowMetrics。要求 decor inWindow 原点为零；root screen − root window 必须等 decor screen；root sourceRect 完整在 decor 内；root/decor 以及相关祖先均无缩放/旋转等未支持变换。目标必须在当前截图 display 内。
4. Dialog 的 currentWindowMetrics 不预设等于 dialog decor 外框：它可能描述宿主容器。Window 映射使用实际 decor screen 原点、原生 Window 层尺寸及 PixelCopy 实测源矩形，metrics 单列诊断，不能把 Activity 的屏幕原点套到 dialog。只有这些独立约束全部成立才可接受；无法解释的原点/尺寸/transform/surfaceInsets 仍 FAIL。Activity 原严格 metrics/decor 等式保留，不因 dialog 扩展普遍删除。
5. Window-layer 原图尺寸严格等 decor，无 resize/crop 伪造层输入；PixelCopy 使用 fresh Rect，SDK 后矩形必须原样，非零 surfaceInsets 继续 FAIL。不要靠硬编码 dialog inset、阴影宽度或截图裁切补偿通过。
6. 保留 protocolVersion=2 与 commit → S1 → W1 → D1 → D2 → W2 → S2。SystemClock 真实总 5000ms；每阶段使用剩余预算，不重置 deadline。S1/W1/W2/S2 相同颜色空间且全目标像素零差；D1/D2 对实测目标完整 screenRect 全像素零差；跨域只诊断。前后几何/观察对象完全一致，无 mask、容差、暂停生产 handler 或重试放宽。
7. 成功保留两张未经处理的全 display PNG；失败保留六输入及失败 proof，不以 raw diagnostic 替代 accepted。proof 写明 window kind/identity、目标 rect 与 display ID、映射模式；dialog 证明只覆盖该目标 rect，不能把宿主背景也标成同步通过。
8. M6 两处真实趋势 dialog 用实际 dialog.window 与其 decor/所属 content；observe 必须采同一 dialog 的实际 chart/Layout，不能把全局 WindowInspector 中其他 chart 混入目标证据。关闭后检查原 Activity 恢复焦点、原缓冲/计时状态/滚动。标准 1.0 主 fixture 仍强制 inline 趋势；不能用 dialog 替换主图。
9. M6 short-height 是实际 resize 的 Activity window，先走保留原约束的 Activity 入口；若 metrics 不一致，记录具体几何失败，不能按短高名义跳过。确需同已证明的 Window-local 映射时显式选该模式并完整满足上述独立约束；不能默默 fallback。三处取得 accepted 证明后，才删除相应 PENDING。
10. M7 完整说明 dialog 后续可消费相同 Window 入口，但须单独记录其真实 Window 身份、文字全文与关闭恢复。现有诊断明确非同步，不能被 M7 12 主图通过覆盖。runner 应对每个预期严格特殊状态检查 proof 文件存在且 accepted；仅遍历已存在 proof 会漏掉根本未生成的文件。未知硬件状态仍保持 pending，不能为清空 pending 伪造状态。

## 复验要求

实际跑普通 Activity、正常 dialog、短高 Activity/dialog；核普通 Activity 旧输出和门禁未弱化。负例验证传错 owner Window/root、失焦、未知坐标/层尺寸、非零 surfaceInsets 时失败且原件保留。真实总预算与零差字段逐项检查；纯编译、主图数量、CI 绿色不能代替这些证据。

## 冻结候选静态复核（2026-10-19）

已核 `/tmp/hoyi-window-evidence-freeze.json` 所列五文件候选。Activity 入口仍严格比较 metrics/decor 原点和尺寸；显式 Window 入口独立核真实 decor/root/token/display、焦点、祖先 identity，未用 owner Activity 焦点替代。公共核心保留单一真实 5000ms、原硬件 commit、S1/W1/D1/D2/W2/S2 顺序、四项同域全像素零差与色域一致；跨域仅诊断。SDK 后 Rect 不得偏移、层位图必须与 decor 同尺寸，未知映射继续 FAIL。

M6/M7 可见性以 `getGlobalVisibleRect(Rect,Point)` 的 Point 而非被裁切的 Rect 原点换算；另用目标 Point 加自身 scroll 与 `getLocationOnScreen` 逐项验证。已对本机 SDK34 `View.java:19449` 和 `ViewGroup.java:6362` 核：Point 从负自身 scroll 开始，再累加祖先位置减祖先 scroll。该算法静态成立；不是系统 Window/IME 遮挡证明。

runner 现在逐一要求主图、短窗、modal、滚后等预期 proof 存在，不再只遍历已有文件；负例只接受实际 wrong-owner/unfocused-owner 的拒绝原因，并要求独立正例。两份原 display 的 SHA 必核。

结论：**本批静态协议实现无新增阻塞，可进入编译与真实取证，不是视觉通过。** 仍待正常/短窗 Activity 和 modal 正例、327px 实测换算、两真实负例；未知层尺寸和 nonzero surfaceInsets 原生负例仍 PENDING。短 Activity 仍可能因真实 metrics 不一致失败，不得自动 fallback 或清 pending。
