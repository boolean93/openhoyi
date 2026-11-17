# Mock AI 隔离预览与132恢复兼容验证

- 范围：新增adb可启动的Mock-only离线场景；复用原AiChatPreviewActivity/生产Presentation及纯ModelSessions/SessionCodec/Context/OCR parser。内置合成附件元数据、模型独立会话、固定草稿恢复、OCR两阶段模拟确认/取消/unknown不重试、Markdown/usage/compact显示；curve仅纯展示预览，不冒称真实store写入验收。
- 来源/工作/目标：develop来源既有feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7，保留所有未提交与此前24文件；未来目标develop。本轮不提交/推送/PR/合入。
- 文件：Mock AiChatPreviewActivity基类、AiOfflinePreviewActivity/Host、AiOfflinePreviewState、mock manifest；MobileApplication只加BuildConfig.MOCK_MODE+确切专属进程guard/onTerminate late-init保护；Mock JVM预览测试与132 schema2黄金fake。正式manifest不动，旧预览入口保持兼容。
- 隔离：新入口运行:ai_offline_preview，不初始化DeviceAutoConnectOwner/ReleaseUpdateOwner/ReleaseDownloadOwner，不读写Key/SessionStore/Vault/用户曲线/原文件。仅语言/字号既有prefs只读。任意intent不提供URI、路径、正文或网络控制能力，extras仅原有语言/字号/明暗白名单。样例状态仅本进程内存与合成Bundle，不保存输入框用户文字，不调用真实Engine/SDK/HTTP。旧默认preview行为不变。
- 分工：Sol实现/唯一写者与串行Gradle，Astra独审，根统筹设备/签名/源码冻结。不操作设备、安装、Key、付费API、CU、咖啡机；当前132无保存ack，覆盖安装保全仍阻塞。
- 验证：focused Mock JVM纯状态机/132真实schema2形状迁移、编译/lint，复用生产测试证据。不把preview当picker/Keystore/网络/曲线写入真实通过。必要通过后候选133仅构建保存，属性hoyiVersionCode=133、hoyiVersionName=0.1.0-local.ai-features-133，根验签。
- 发布/升级：不是发布，不安装、不改源码版本/签名，不读真实数据；保留legacyepoch/schema4两槽。候选APK不证明设备覆盖安全。
- 回退：撤回preview及Mock进程guard，不改其他功能/用户数据；不卸载清数据。
- 证据：改前快照/tmp/openhoyi-offline-preview-before，最终本轮diff/hash与日志另列。

## 实际结果与边界

- 新入口 `io.openhoyi.mobile.AiOfflinePreviewActivity` 仅Mock导出，独立进程 `:ai_offline_preview`。原 `AiChatPreviewActivity` 保持非导出/原进程以兼容既有instrumentation；不可把旧入口启动当独立进程隔离证据。
- MobileApplication在Mock中只有精确普通self主进程才初始化owners，保留updateFixture正常主进程；专属preview或未知/错误/子进程全部不初始化DeviceAutoConnectOwner、ReleaseUpdateOwner、ReleaseDownloadOwner。preview还要求专属进程ready明确确认，否则显示拒绝说明。正式BuildConfig=false仍执行原正常逻辑；API26使用公开ActivityManager自身PID查名，无隐藏API，失败关闭。onTerminate仅关闭已初始化owner。
- 只读沿用 `app_language` / `app_typography` 样式偏好已获协调者允许；不是‘零真实文件读取’。不读凭据/聊天/曲线，不写用户存储，不启动设备/更新owner或网络。Intent仅原有语言/字号/明暗白名单，忽略URI/路径/正文等，不接受外部附件。输入框自由键盘文字不进入controller/Bundle/样例历史；固定合成草稿可通过生产SessionCodec恢复，专属合成epoch也被校验。
- 所有预览动作清楚标‘离线模拟’，附件仅内置元数据，不代表picker/图像解码/密文Vault验收。OCR两次模拟确认、取消/unknown/length阻止重试，复用真实OCR parser/SessionCodec/ModelSessions/ContextPolicy。compact仅构造显示状态不调用模型。curve仅文案和纯ID边界，不连接CustomCurveStore/生产确认协调器，不能冒充真实新增编辑或全部100条内置曲线实测。
- Mock专属状态测试放 `src/testMock/kotlin`，不让正式Debug测试引用Mock-only类。新增5个预览focused用例覆盖进程guard（含正式/普通Mock/updateFixture/null/foreign/子进程）、>4000固定草稿/双槽恢复、OCR第二次确认和未知不重试、取消/length、完整可见历史与compact状态。新增1个132黄金迁移fake：读取候选132源码codec后按真实schema2形状手工构造纯合成JSON，包含legacyepoch、非空中文草稿、covered summary、reasoning/null、成对tool_call/tool_result、usage/cache/unknown。schema4往返完整保留；不是实际用户会话或真实Keystore迁移验收。

## 实际验证

- 首次离线 `testMockUnitTest` 限定7类（OfflinePreviewState、Session132Golden、ModelPolicy、DocumentOcr、SessionStore、ChatKeyboardPolicy、Markdown）40项，0失败/0错误/0跳过；`lintMock` 与 `compileMockAndroidTestKotlin` 通过，BUILD SUCCESSFUL 2m51s。日志 `/tmp/openhoyi-offline-preview-tests.log`，首测试统计 `/tmp/openhoyi-offline-preview-first-tests.json`。既有Android预览检查仅编译，未运行设备instrumentation。
- 根据独审补上API26未知进程failclosed及updateFixture self主进程兼容后，仅重跑受影响的新预览/黄金迁移2类6项，0失败/0错误/0跳过；同时 `assembleMock` 通过（51s）。最终参数 `-PhoyiVersionCode=133 -PhoyiVersionName=0.1.0-local.ai-features-133`，日志 `/tmp/openhoyi-offline-preview-133.log`。未无改重复40/131/946矩阵。
- Astra独立只读终审通过；进程未知fail-open与updateFixture回归均经预审修复并补断言。`git diff --check`通过。Mock132 legacyepoch相关源码未改。
- 新候选APK：`mobile/build/outputs/apk/mock/mobile-mock.apk`，25,326,375 bytes，SHA256 `aabd40c6f10478e2029726cee5bbe9af49fa6ab2541715d26c8b28068769ee70`。包名io.openhoyi.mobile.mock，versionCode133/versionName0.1.0-local.ai-features-133，构建metadata核实；根线程已独立验签与installed132同证书，并保存到 `openhoyi-local-materials/ai-offline-preview-mock133/openhoyi-mock133.apk`。本线程没有安装/重启或操作设备。
- 根线程验签记录：证书SHA256 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54` 与实际installed132一致。这不解决当前132内存草稿/历史保全ack缺口，因此仍未覆盖安装。源码兼容fake通过不能替代该设备保存证明。
- 本轮精确diff `/tmp/openhoyi-offline-preview-integration.diff`，前后源hash `/tmp/openhoyi-offline-preview-source-manifest.json`，结构化证据 `/tmp/openhoyi-offline-preview-evidence.json`。仅修改MobileApplication、3个Mock Kotlin文件、Mock manifest、2份新测试、此计划；其余未提交内容保留。根线程统一冻结allowlist源码，不打包任何真实配置/密钥/用户数据/崩溃日志。

## 后续安全入口（仅说明，未执行）

取得设备保存保全证据与安装授权并由根统筹后，才可在已安装133上手动启动：

`adb shell am start -n io.openhoyi.mobile.mock/io.openhoyi.mobile.AiOfflinePreviewActivity --es previewLanguage zh-Hans --es previewFont large --ez previewDark false`

本轮未执行这条命令，未HOME、截图、输入、安装或使用AVD。预览动态布局/Android生命周期/正式产品流程和真实服务仍待验，不得把候选构建当设备验收。
