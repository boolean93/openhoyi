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

## 2026-10-20 非支持 Window origin 的独立诊断范围

根与Sol原件核得scale dialog decor1408×1048、screen[576,240,1984,1288]，SDK PixelCopy把请求Rect原地由[0,0,1408,1048]偏至[72,72,1480,1120]；legacy短Activity decor2560×630、screen y485而hostmetrics仍2560×1600。前者surfaceInsets/SDK源Rect映射拒绝、后者metrics等式拒绝，均未证明像素一致，不能硬套72或放宽原协议。

允许仅helper与新WindowOriginDiagnostic两个测试文件，在上述两个结构化unsupported reason点标记；原FAIL proof持久化后，只对原同identity/attached root与Window执行一次独立DIAGNOSTIC_NOT_ACCEPTANCE。不触发像素差、超时、失焦、wrong-root负例或泛异常。独立诊断真实5s单独记时，不延长原验收5s、不改accepted、不覆盖原异常/requiredproof。失效仅记录不可用，不切换窗口重试。

只读采集完整Window/decor/root/token/display、四边surfaceInsets、metrics、原请求Rect和SDK后Rect、PixelCopy/window-layer/display原始尺寸与PNG。Layer完整不裁不缩；display与screen app矩形仅对照，不试多偏移找最小差。未知layer原点继续FAIL。本批仅获得真实mapping证据，支持与否须后续独立审查，不自动认证。

## 2026-10-20 a47c870 实际原点诊断与下一最小支持范围

原件分别为 `ci-a47c870-scale/raw`、`ci-a47c870-legacy/raw`，source 12de379c8bc98ed1d309d04c8e73edf09b3cae87。Astra 读两份 diagnostic-window-origin.json，并查看原 layer PNG，独立逐像素核算三张原件；它们仍是 DIAGNOSTIC_NOT_ACCEPTANCE，不追认任何 protocol 2 成功。

- Scale：decor 1408×1048，screen `[576,245,1984,1293]`；SDK 将本次独立 source `[0,0,1408,1048]` 平移为 `[72,72,1480,1120]`。完整 layer 1552×1192。按该实测 source 裁 layer，与原 surface **0 像素差**；完整 display 的同 screen 区域有 728 像素差，均对应 surface alpha<255 的边缘。actualViewRootSurfaceInsets 为 UNKNOWN_REFLECTION_UNAVAILABLE（NoSuchFieldException），不得写成四边已知。
- Legacy 短窗：decor/root 2560×630，screen `[0,485,2560,1115]`，host metrics 2560×1600；SDK source 不偏移。完整 surface/layer PNG 同 SHA，display 同 screen 区域也为 **0 像素差**。

**纠正一次口头判断：** Scale 的 728 跨域差不是正式门禁失败。既定正式零差仍是 S1_W1、S1_W2、S1_S2、D1_D2_app；S1_D1_diagnosticOnly 不要求等价。不得新增跨域零差要求，也不得掩掉透明像素。

批准下一候选仅显式 opt-in 两种映射，默认 Activity 全窗口规则不变：

1. `MEASURED_WINDOW_BUFFER_TRANSLATION`：仅现有 Scale 两个真实 trend dialog 入口启用。保同 Window/decor/root/token/display、焦点、全祖先 identity transform 与前后几何检查。每次 PixelCopy 用新的原 source Rect，要求 SDK 返回 source 宽高完全不变、各边是同一非负 dx/dy 平移；S1 与 S2 的实际平移必须精确相同。不得写入 72 常数或靠搜索像素选择偏移。
2. W1/W2 完整原 layer 均保存、尺寸必须一致。本批只支持已观察的对称扩展：layer.width=decor.width+2*dx，height=decor.height+2*dy；source+offset 全部位于 layer。记录这是测得的 buffer 扩展约束，不称其为已读取四边 surfaceInsets。其它不对称/未知大小继续 FAIL，后续要支持必须另取证。
3. 用实际 SDK 后 source 作为 W1/W2 的 hash/diff rect，保留整个原 root ROI（含透明圆角）；S1/S2不缩放。display 仍用实测 screen rect，D1/D2 原域零差不变，cross-domain差原样输出。所有前置测量和六次采集仍在原真实 5s 内，无额外预采样、时限扩大或重试预算重置。
4. `MEASURED_SHORT_ACTIVITY_WINDOW`：只用于已声明的 M6/M7 短窗调用，保 Activity 自身焦点及与显式Window同等身份链核验。不得在默认 metrics 等式失败后自动 fallback。decor inWindow 必须(0,0)，root screen−window 必须等于decor screen；decor screen矩形包含在host metrics/display内。source无SDK偏移，layer必须等于decor尺寸，完整root边界不得缩小。host metrics 作为host边界保留，不再在这个**显式模式**错误要求与短decor等大。M6短窗未有本次实际样本，仍需运行验证。
5. 原错误 Window/root/失焦负例继续失败；补离线映射契约反例：偏移非平移、负偏移、S1/S2偏移不同、W1/W2尺寸不同、非对称扩展、越界、screen/window原点不等、默认模式短窗不能自动通过。不能用离线反例代替真实CI四零差。新的 accepted 只能由后续真实 protocol 2 产生，原诊断失败记录保留。

实现由根分配原 helper owner 顺序执行，避免与在制阶段计时冲突；本审查不改 helper 或生产。

### 显式映射与阶段计时实现静态收口

已审最终 NativeWindowFrameEvidence.kt SHA256 `efe2a0653e24d8cde2e9cac71f768783a0562fcf874d1a33268391db011318f0`，及 M6/M7 调用、两 runner、window_mapping_contract.py（`c8c01a7f0ad4899dc9192a1a9c497071c6d600b1a24b9e55a684bf103b653527`）。实测平移、对称扩展、两次一致、边界和身份链均依上述限定；默认 Activity 不 fallback。仅原两 trend modal 与已声明短窗 opt-in，负例仍原 strict Window 入口。translated 成功保留完整 W1/W2 原层 PNG，Scale runner（`dc888125180bd261b23ec0ebf1f2c9dc20ddcc5d804ac3fc50c2c65318fbce71`）要求四原件角色及逐 SHA/层尺寸。

纳秒计时仅在匹配过程内存记录既有阶段，终态 finally 写独立 diagnostic sidecar；不参与 accepted，不覆盖失败，不添加等待。原真实5秒、六次采集顺序、四正式零差和跨域仅诊断保持。独立执行9项映射契约测试全过、三个runner bash-n与diff-check通过。静态无剩余阻断；这些离线结果不证明新的真实 Window capture 已通过，需新CI原件验收。

## 2026-10-20 终态proof窄优化静态接受

NativeWindowFrameEvidence.kt SHA256 `2990931eed9ef3143fb6b801e89621c60ee879c285f44233298928ea98f2cca4` 实际diff仅删除重试间writeProof(false)，并在终态失败writeProof异常时addSuppressed后保留原采集异常。全部attempt字段、geometry全比较、原5s匹配、四像素域、retained完整图优先与回收不变。符合本次无损优化计划，静态无剩余阻断；owner报告compileMockAndroidTestKotlin成功。下一实际CI才判断是否减少真实超时，不宣称同步失败已解决。
