# AI 会话界面打磨

- 需求与范围：借鉴 iMessage 的左右气泡、克制配色及固定底部输入栏；会话操作做成应用内锚定浮层，不申请系统悬浮窗权限。保留内存会话、发送/停止、Key 与授权流程。
- 来源分支/基线提交：develop；当前工作基线 599670aab75d94070bf836cb813239e30e3e46e7，AI 草稿与其他改动均保留。
- 工作分支：feature/ai-chat；PR目标：develop。
- 改动文件与步骤：AI 会话左右气泡、输入栏和浮层；抽取生产与 Mock 共用的纯 View `AiChatPresentation`。Mock-only 非导出预览 Activity 使用显式外观配置和合成问答；设置页在 `BuildConfig.MOCK_MODE` 下显示「聊天界面预览 · 离线样例」，普通前台点击固定组件启动该页，Debug/Release 不显示。复用原 Mock runner 的独立互斥 `aiPreviewOnly` 分支，保持原 AI 凭据/HTTP fixture 门禁与5-runner合同。经原作者确认，仅移除额外 DeepSeek 兼容 instrument 的 manifest 注册，保留其源码。
- 推送与合并：本轮不推送/创建 PR；后续按 docs/git-workflow.md 及授权推进。
- 验证证据：UI 初版 `./gradlew :mobile:compileMockKotlin :mobile:compileMockAndroidTestKotlin :mobile:testMockUnitTest --offline --console=plain` 成功（1m25s，61 tasks）。共享 presentation/Mock 预览后 `./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest :mobile:testMockUnitTest -PhoyiVersionCode=128 -PhoyiVersionName=0.1.0-local.ai-ui-128 --offline --console=plain` 成功（1m33s）；`python3 -m unittest discover -s scripts -p test_mock_ui_tap.py` 17项、`python3 -m unittest discover -s scripts -p test_shared_mock_artifact.py` 8项通过；test APK 通过 `shared_mock_artifact.inspect_apk(..., 'test.apk')` 原5-runner校验。Mock 主包独立 aapt/apksigner 检查为 `io.openhoyi.mobile.mock` v128，同已安装 Mock 证书 SHA256 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`；不能用固定CI版本1的 `inspect_apk(app.apk)` 校验本地v128。Android UI fixture 仅编译，未运行；合成截图和几何检查为以后获授权运行时准备，不宣称已验收。
- Mock 设置入口本地最终验证：`./gradlew :mobile:compileDebugKotlin :mobile:assembleMock :mobile:assembleMockAndroidTest :mobile:testMockUnitTest -PhoyiVersionCode=128 -PhoyiVersionName=0.1.0-local.ai-ui-128 --offline --console=plain` 成功（1m46s）；Astra 独立静态复核该入口通过。此次本地新包未安装。
- 最新构建产物（设置入口仅本地，未安装）：`mobile/build/outputs/apk/mock/mobile-mock.apk` SHA256 `0078c42a3e28a7fcdff38a1ad9ad99cbc23eaa085a4618d3240299fa6cfed7f8`；`mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk` SHA256 `51fb49e9dcc26527c0f4bf12bc49c13215801255c22d04e87b09145163ade246`，同一证书。前一不含设置入口的 Mock128 已安装；不可混称已安装新入口。
- 后续独立静态检查：本地化25项通过，`validate_catalog.py` 中英1082键完整，`android_resources.py --check` 无漂移。`AppFontSizeCoverageTest` 7项通过。`lintMock` 首轮0错误、564警告，发现本次图标每帧分配 `ChatActionIcon`；已改为缓存图标和 Path。增量重跑先因本机 Oracle JDK 17.0.7 AArch64 C1 编译器内部崩溃中断，非源码断言；随后仅一次 `--no-daemon --max-workers=1` 受控重试成功（1m26s），字号7项全过，`lintMock` 0错误、563警告，`DrawAllocation` 已消失。剩余本次相关警告是同步保存 consent 的 `ApplySharedPref`（保持原隐私语义）和 Canvas KTX 风格建议，不是构建阻断。
- **产物边界：** 上述两个 APK 构建于图标缓存优化之前；增量验证已重新编译最终源码，但没有重新打包 APK。若要安装/分发最新源码，必须重构建并重核哈希与签名；不能把旧 APK 哈希当最终源码产物。

## 第二轮：键盘发送与安全 Markdown

- 需求与范围：软键盘 Send、硬键盘 Enter 提交，Shift+Enter 保留换行；合成中/空白不提交，按键 DOWN/UP 不重复。只对助手气泡解析常用 Markdown 标题、粗体、列表、代码及链接，原始会话/协议字符串不改。
- 分支与流程：仍在 `feature/ai-chat` 草稿工作树，来源/目标 `develop`；本轮不推送、不创建 PR、不安装、不操作设备。后续按 `docs/git-workflow.md`。
- 文件与步骤：`AiChatPresentation` 键盘和显示委托、纯解析/策略及 JVM 测试、`AiChatActivity` 的链接本地确认和双语资源、Mock 合成预览断言。其他线程的 API/Key/协议改动一律保留。
- 安全/体验：纯文本解析不用 HTML/WebView/图片下载，不接受 `file:`/`javascript:`/账号信息/本地地址链接。仅点击公开域名 HTTP(S) 链接时先在应用内显示完整 URL，用户确认后才交系统打开；不附加会话/设备私有数据。代码内容只显示，不执行。
- 验证：先与同工作树搜索任务串行 Gradle，再运行纯解析/键盘 JVM、Mock/Debug 编译与相关 lint/本地化；预览 instrumentation 只编译，不设备执行。按同构建配置测 APK 字节差异并说明无法单独归因的同期改动。
- 协议、设备、发布与回退：请求/只读协议/Key/设备控制不变；不发布、不动签名/安装/升级。若需回退，仅回退本轮 UI/解析和文案，不清用户数据或会话记录。
- 协议与设备风险：不改请求、协议、服务生命周期或存储；不安装/操作设备。同包 Mock 预览只隔离本 Activity 的业务路径，不是进程/数据沙箱：`MobileApplication` 启动会读取既有语言/字号偏好，并初始化更新组件，其下载缓存维护可能写 Mock 私有文件。自动更新对 `.mock` 身份不合格，不应发自动元数据请求；如要求零数据接触，未来动态验收应在一次性模拟器或隔离包而非用户现有 Mock 上进行。
- 发布：不发版，不改版本、签名或升级路径。
- 回退：回退本次 UI 文件即可；不卸载或清除任何记录。
- 未完成与阻塞：首次在平板上运行预览 instrumentation，MIUI 拒绝后台启动非导出预览页，前台主界面后单次重试仍未显示；未取得视觉/IME截图证据，没有更改权限、导出状态或系统设置。新增可见的 Mock 设置入口供普通前台点击，不再依赖隐式/后台启动绕过；该入口本轮只本地构建，未再次安装。预览分支若日后可运行，会把合成界面截图和真实控件几何写到唯一 UUID 目录并关页，不改全局显示或 IME 设置。未执行前不能称视觉或 IME 已通过。

## 2026-11-15 平板受控验证

- 同签名覆盖安装 Mock `128 / 0.1.0-local.ai-ui-128` 及测试 APK 成功；仅 `adb install -r`，未卸载、清数据或访问 Key。签名 SHA256 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54` 与原 Mock127 一致。
- 安装前后 UID `10201`、首次安装时间 `2026-09-25 10:27:30` 不变；这些是覆盖安装证据，不冒称读取验证了用户凭据内容。
- `aiPreviewOnly` 使用纯合成数据，首次启动被 MIUI `Permission Denied Activity ... AiChatPreviewActivity` 拒绝。正常启动 Mock 主界面到前台后，仅重试一次，仍同样拒绝。已停止测试，未更改安全权限、导出属性、显示或输入法设置。
- 显示前后仍物理 `1600x2560 / 360 dpi`。两个新建 UUID 预览证据目录为空，没有截图。未执行原有需要空 Key 的 AI safety fixture，没有模型 API 调用。
- 视觉、浮层、字号与 IME 动态验收尚未完成；编译/静审通过不能替代运行时效果。后续需允许正常前台打开预览的路径或可用隔离 Android 环境。

- 随后按明确授权，同签名 `adb install -r` 覆盖带设置入口的本地 Mock128 成功；新 APK SHA256 `0078c42a3e28a7fcdff38a1ad9ad99cbc23eaa085a4618d3240299fa6cfed7f8`。UID和首次安装时间仍保持。未启动真实聊天或读写Key，未改权限。
- 当前用户可从 Mock「设置 → 聊天界面预览 · 离线样例」正常点入；未再用 instrumentation 重试受限启动。此正常前台入口是否实际打开、视觉/IME及截图仍待用户点击后确认。

## 后续正常导航证据

- 正常 Mock 主界面 → 设置 → 离线预览入口已由受控重试任务成功打开，`topResumedActivity` 明确为 `AiChatPreviewActivity`。此路径没有修改系统权限；与上文失败的 instrumentation 后台启动不同。
- 已有真实浅色横屏截图 `/tmp/openhoyi-preview-retry/landscape-preview.png`，SHA256 `3cd4e7450a8e6ff7450eeb7e351bd3f5d580ccfa4062940d86cf42b602149b16`；仅离线合成对话。静图复核可见气泡无截字、输入栏完整、圆形发送按钮居中及消息/输入限宽一致。
- 后续接手时前台已变为正式 App；为避免干扰用户，未切换应用或继续输入/截屏。键盘、菜单、深色及其它比例动态验收仍待空闲设备，未宣称通过。

### 非设备补充审查

- 源码配色的 sRGB 对比度计算：用户白字/深青气泡约5.17:1；浅色助手正文约17.89:1；深色助手正文约13.97:1；浅色输入提示约4.95:1。仅证明指定色值，不替代屏幕可读性动态检查。
- 独立 IME 静态复核未发现必然双算：Insets 类型并集取边最大值，padding覆盖；API33并不会因target35自动强制Android15 edge-to-edge。极矮横窗与浮动键盘仍是未验证边界，不凭推测改布局。

## 2026-11-15 发送、键盘与 Markdown 增量

- 多行输入框显示 IME Send；硬键盘 Enter 发送、Shift+Enter 换行，拦截重复 DOWN、UP、组合输入中、空白及生成中发送；停止按钮仍可单独取消。
- 发送开始即清空输入并显示右侧待发送气泡。仅成功响应提交协议历史与问答；失败或取消恢复原草稿以便重试。新会话、Key 变更清除待发送状态与可见输入。仅助手消息渲染受限 Markdown（标题、粗体、列表、代码、安全 HTTP(S) 链接）；链接点按后仍需本地确认才交给浏览器，无自动图片加载或 HTML 执行。
- 最终受影响编译/JVM：`./gradlew --no-daemon --max-workers=1 :mobile:compileDebugKotlin :mobile:compileMockAndroidTestKotlin :mobile:testMockUnitTest --tests '*AiChatPendingDraftTest' --tests '*AiMarkdownTest' --tests '*AiChatKeyboardPolicyTest' --tests '*AiWebSearchTest' --tests '*AiProtocolHistoryTest' --tests '*AiLiveProbePolicyTest' --tests '*AiChatSessionTest' --tests '*AiSafeTransportTest' -PhoyiVersionCode=128 -PhoyiVersionName=0.1.0-local.ai-ui-128 --offline --console=plain` 成功，33 测试、0 失败；仪器测试只编译，未在设备运行。
- `lintMock` 与 `AppFontSizeCoverageTest` 在本次键盘/Markdown更改后通过；最后 Key 清屏与测试夹具调整后已重编译，未重新跑 lint。最终 `assembleMock` 成功；`mobile/build/outputs/apk/mock/mobile-mock.apk` 26,869,176 bytes，SHA256 `0bbf7f4dfa0724aec67443f5a4cda538e2f90e74de6d3b14896f573066fa73e9`。该包仅本地构建，未安装或验证新功能的设备视觉/IME。

## 2026-11-15 Markdown 表格窄屏修正计划

- 来源/目标仍为 `feature/ai-chat` → `develop`，继续保留工作树中其他人的未提交 AI、持久化与协议改动；本轮不推送、不创建 PR、不发布/安装。回退为撤销本轮表格解析与合成预览内容，不触会话、Key 或服务端协议。
- 验收：严格识别表头＋分隔行＋至少一行数据的普通 Markdown 表格；在同一气泡内纵向排列「列名：值」，标签加粗，换行跟随应用字号和实际气泡宽度，避免窄屏横向溢出。保留安全链接的二次确认、不执行 HTML/不加载图片；不完整或不支持的表格保持原文，不能吞掉回答。验证纯解析单测、相关 Mock 编译与现有安全测试，设备动态效果另行如实报告。
- 实现：纯解析器只接受2–8列完整表格，按行纵排并给列名加粗；空表头、异常分隔符、超24k展开预算均回退原文，不截断。Mock-only 预览合成消息加入中英文表格，仪器用例检查转写后的文字及现有主题/字号气泡可见性；原真实AI会话、协议和外链确认行为未修改。
- 本轮定向验证：`./gradlew --no-daemon --max-workers=1 :mobile:compileMockAndroidTestKotlin :mobile:testMockUnitTest --tests '*AiMarkdownTest' --tests '*AiRequestContractTest' --tests '*AiProtocolHistoryTest' --offline --console=plain` 成功，17项、0失败；预览菜单新增用量入口后的按钮数断言修正后，单独 `:mobile:compileMockAndroidTestKotlin` 再次成功。仅编译与JVM，不称实际平板表格视觉已验；未安装/调用付费API。
