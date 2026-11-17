# AI 助手首版

- 需求与范围：设置页简洁入口，Koog + DeepSeek Flash 多轮文本会话，独立加密 API Key 设置；仅按需读取机器/秤状态、曲线库和萃取历史，不写设备或曲线。首次明确告知发送的数据及 API 费用。
- 来源分支/基线提交：develop / 7a22e6cb；本地依赖待合入工具链 599670aa（Kotlin 2.3.10 / AGP 8.13.2 / Gradle 8.13），AI 与工具链分 PR。工具链合入前仅做本地开发和验证。
- 工作分支：feature/ai-chat；PR目标：develop。
- 改动文件与步骤：先验证 Koog 发布依赖与 Kotlin/AGP 兼容，再实现独立加密存储、只读 DTO、有限会话/传输层、ThemedActivity 页面和离线测试。
- 推送与合并：遵守 [Git工作流](../git-workflow.md)，本地必要验证及独立 Astra 审查后通过 scripts/create-pr 创建草稿；精确头提交 branch-policy/verify 通过后合入。
- 验证证据：fake API/key 验证请求、多轮/工具/取消/边界与泄漏防护，JVM 测试、Mock 构建、lint；相同 Mock 配置前后 APK 原始字节数、哈希与增量。不得调用付费 API。
- 协议与设备风险：不增加 BLE 写入、不启动服务或重连，不安装平板，不操作真实机器。陈旧/未知状态如实表示。
- 发布：不人工发布，不改版本号/签名；合入后既有 Develop 自动通道按仓库规则运行。
- 回退：代码回退；密文独立保存，不触及历史/设备数据。不能以卸载清记录恢复。
- 未完成与阻塞：实现与验证进行中。Android Keystore 真机验证、屏幕截图/窗口几何的动态验证未授权，不能以静态/JVM证据冒充。

## 当前实现与证据（本地草稿）

- Koog 采用同一发布系列 core 1.3.0 / DeepSeek client 1.3.0-beta，实际 AIAgent/ToolRegistry 调度，不自写 agent 循环；仅非 thinking，固定 deepseek-flash，并断言请求顶层 safety_check high。
- 自有 KoogHttpClient 仅允许固定 HTTPS chat/completions POST，不接受 SDK URL/header 覆盖，不跟重定向；仅发送时从独立 Android Keystore 解密 Key，SDK/会话/工具不接收 Key。HTTP 错误不读取正文，异常仅安全枚举。官方 content_filter/refusal 完成响应映射拒答，不显示原始内容。
- 每次发送新 agent/transport，保留有限用户/助手文本，不保留旧 tool results；每次最多4 HTTP请求/6工具调用、工具累计12000字符、60秒；更换/删除 Key、离开、停止均中止旧请求。新会话清空上下文。
- 日志：Application 首次初始化及构造 SDK 前安装无求值 NoOp KotlinLogging factory，排除 slf4j-simple，唯一 SLF4J NOP provider。fake Agent 用户、工具、401正文 sentinel 检查 stdout/stderr；这不等同实际 Android logcat/系统诊断验收。
- 界面：设置页一行入口、顶部简洁操作、中间独立会话滚动、底部固定输入/发送/停止；密钥页写入后不回填/显示尾号/复制，FLAG_SECURE，禁状态保存/自动填充，离开清空输入。会话仅 ViewModel 内存，不磁盘保存。
- 只读：仅注入最少字段 provider，不向工具暴露 service/store 写方法；设备 freshness/age，陈旧值不输出；曲线/历史按需分页，文件2MiB、结果6000字符，未知/失败如实返回。曲线详情最多4启用段，显式单位，未知旧模式不猜。
- 最后受影响重跑：Debug JVM 813例、Mock JVM 820例，均0失败、1项既有跳过；其中33项AI专项在两种变体均通过。包含实际Agent新会话不保留旧tool结果、replace/delete期间阻塞HTTP读取消、自然超时与停止区别、官方拒答、无敏感日志。
- 仓库原 full 检查：全部 Python、202个 Gradle tasks（全模块JVM、Debug/Mock构建、lintDebug）、APK多语言资源校验通过。最后真实临时签名APK初次因logging release AAR离线缓存缺失失败，官方Gradle预取后原脚本重跑通过，12签名/任务图检查及临时version2 APK身份校验通过。未禁用/放宽门禁；创建PR前仍按统一脚本做精确提交检查。
- Android 真实 Keystore/存储失败、日志、截图/配置重建/大中小字号与窄横方屏动态验收未完成，不可发布；没有安装/操作设备或发起真实模型请求。
- 体积：已完成下述 fresh 同配置测量；当前草稿后续若变更生产代码须更新，不是最终发布版大小。此前增量ZIP有空洞的27,341,070 bytes研究样本作废，不再引用其净增。

## fresh APK 体积（2026-11-14，本地草稿）

所有样本均为 mobile Mock、minify=false、debuggable=true、无ABI splits/filters/原生库；包名io.openhoyi.mobile.mock、versionCode=1、versionName=0.1.0、minSdk26、target/compileSdk35、同debug证书。Android APK签名验证和manifest逐项一致性断言通过。

| 样本 | 原始bytes | 相对上一步 |
|---|---:|---:|
| 原develop基线 | 18,955,082 | 基线 |
| 新工具链，无AI | 19,009,894 | +54,812 / +0.289168% |
| 新工具链，仅SDK依赖与日志后端 | 25,017,749 | +6,007,855 / +31.603832% |
| 新工具链，当前全部AI功能 | 25,082,851 | +65,102 / +0.260223% |

当前全部AI相对同工具链无AI：+6,072,957 bytes（+31.946296%）；相对原develop基线：+6,127,769 bytes（+32.327842%）。SDK-only明确包含Koog core/client、其传递依赖及必要kotlin-logging/SLF4J NOP后端，不把后端误算为业务UI代码，也不声称是只有单个Koog jar的大小。

SDK-only通过临时init-script注入与当前功能相同的4个固定坐标并排除slf4j-simple，未更改probe源码；分别clean构建。测量后再次clean恢复无SDK，SHA256与先前19,009,894 bytes控制包逐字节一致，Git仍clean。当前AI也由 :mobile:clean :mobile:assembleMock 构建。以上不是压缩优化后的Release、下载商店估算或最终发布成本。

SHA256：
- 原基线：3da6ccdb541184318dfa1d0cdc867aacf2b535229460b5161901442c2d50346a
- 新工具链：eef8cb2a2fc769cdcee29fcfb9503171799808e47e1ee9c5c38a4e9f2cef390d
- SDK-only：7b2dc2b2193b7f7e29298f5b71bdd3c49ae33985e1642c58cde45b1126073cfa
- 全AI草稿：4fec18ddeeeb84c450934cc336b7cb79697670b43a5f46efe50431850a8e5c37

证据保存在本机 /tmp/openhoyi-ai-size：ai-all-size-results.json、ai-fresh-apk-identities.json、各fresh构建及配置日志、4个归档APK、ai-final-unit-results、ai-final-lint-debug.html、ai-feature-full.log、ai-release-fixture-final.log。没有安装任何APK、操作真实机器或发起付费模型调用。

## Android 动态验收准备（尚未执行）

- 凭据实际使用 MODE_PRIVATE SharedPreferences 密文，并依赖现有 allowBackup=false 和 data_extraction_rules 的 cloud-backup/device-transfer 全排除；不是放在 noBackup 目录。
- 已新增 AiAndroidCredentialChecks / AiAndroidLogChecks / AiAndroidHttpSandbox / AiAndroidUiChecks，通过既有 BrewAudioInstrumentation 的 aiSafetyChecks 分支运行，不扩大 instrumentation runner 或导出面。
- 凭据 seed/verify 强制两个不同 PID，使用严格 UUID 独立 prefs/Keystore alias；真实 Android adapters 的默认名称/alias不变，只增加内部可测参数。日志/聊天在发送前安装进程 URL 拦截，HTTP及非固定DeepSeek URL直接拒绝，固定URL仅内存fake回复，安装失败停测。
- host driver scripts/check_ai_android.py 在任何ADB前拒绝物理/网络serial，只允许显式 disposable 的 emulator-* + openhoyi-ai-* AVD；再次核验qemu/ranchu、目标尚无Mock包、原5个runner map及APK配对签名，避免覆盖任何已有Mock数据。仅抓目标PID logcat，检查fake用户/工具/错误/key sentinels；记录两APK SHA、几何、4个代表布局（非全组合），恢复窗口/IME设置。
- Android fixture首轮assembleMock/assembleMockAndroidTest及Mock JVM通过；host 3项离线契约通过。尚未运行模拟器，不能记为Keystore/logcat/截图/IME/布局动态通过。
- 实际环境阻碍：Mac当前仅实体平板连接，没有运行模拟器、AVD、emulator程序或系统镜像；未向平板发送任何操作命令。Dot cloud无SDK/模拟器且无/dev/kvm。新建/下载本地AVD按项目规则需用户明确同意；替代CI运行则需要已发布且审查过的AI提交。未自行采取任一路径。
- 若获准使用一次性AVD，先准备命名为openhoyi-ai-*的全新模拟器，再在工程根目录用Temurin17构建APK对，执行：

```sh
./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest
python3 scripts/check_ai_android.py --serial emulator-5554 \
  --avd-name openhoyi-ai-APPROVED_FIXTURE --disposable-emulator \
  --sdk "$ANDROID_HOME" --evidence build/ai-android-evidence
```

脚本不会下载、创建或启动AVD，也不会选择/操作平板。实际运行前仍须核对所选环境及授权。失败commit与系统Keystore不可用仅有JVM fake证据，不通过破坏Android权限模拟。

### Android fixture准备的最终离线结果

- 最终 :mobile:clean :mobile:assembleMock :mobile:assembleMockAndroidTest :mobile:testMockUnitTest :mobile:lintMock 成功，2m52s；Mock JVM 820例/0失败/1既有跳过，33项AI专项均通过。
- 原runner映射和APK配对签名核验通过；host 3项、CI策略7项、共享APK合同8项Python回归通过。
- 增加内部adapter参数后重新fresh测得全AI25,081,941 bytes，比前一草稿仅+760 bytes；上表已更新。SDK-only与纯链控制不变。
- 证据补充：ai-android-fixture-final-build.log、ai-android-ready-size-results.json、ai-android-ready-pair.json、ai-android-ready-unit-results、ai-android-ready-lint-mock.html，以及归档APK对。动态测试仍是**未运行**，未生成成功收据或布局截图。

- 新增Android fixture已获独立Astra静态复核，无确定静态安全阻断，动态仍0次。截图null只记录externalCapture=unavailable，不作为黑屏遮蔽实测；实际黑屏才记blackened，并写入host收据。UUID合成布局PNG/JSON保留供拉取审查，cleanup仅清独立凭据及恢复设置，不声称模拟器文件完全清空。

- 当前外部截屏采样对象为 Key Activity；Chat Activity验证FLAG_SECURE属性，布局PNG由应用内合成绘制，仅为视觉审查证据，不冒充外部截图。截图结果元数据补充后AndroidTest重编译通过（49s），主App未改。

## 永久失效恢复与备份专项补强

- 明确永久失效的本机AES alias现在可在用户显式重新输入并保存API Key时重建，仅重试加密一次。分类仅认异常链前8层的KeyPermanentlyInvalidatedException，不匹配错误文本；read/delete/普通InvalidKey/暂态Keystore/存储失败不重建。Android实现只删除本AI alias，不枚举其他钥匙、不触及其他凭据/曲线。
- 这是防御性恢复，不是正常更换锁屏必然触发的问题：当前AI key未要求用户认证；[Android永久失效异常文档](https://developer.android.com/reference/android/security/keystore/KeyPermanentlyInvalidatedException)说明该异常适用于认证绑定key。无法从永久失效材料恢复旧API Key，仍须用户重新输入。
- 原allowBackup=false和API31+ cloud/device-transfer排除保留。按[Android备份配置文档](https://developer.android.com/identity/data/autobackup)补齐API26–30 legacy fullBackupContent规则，常规/设备保护9域全排除。这是显式补强，不声称此前已发生备份泄漏，亦不替代真实云备份/D2D测试。
- 回归：12项KeyStore focused（新增4项恢复/不误恢复边界）通过；随后clean Mock+AndroidTest+全部Mock JVM824例（0失败/1既有跳过，其中AI37项）+lintMock通过，2m58s。备份/FileProvider静态5项、host3项通过；独立Astra局部复审通过。
- 已从最终APK提取编译Manifest及legacy/API31规则，确认规则引用存在、9个legacy排除及10个现代cloud/device-transfer排除。APK对同签名、原5runner图、包名/version/SDK/无native库条件不变。
- 最终fresh主APK25,082,851 bytes，较上一草稿+910 bytes；SDK-only控制未变，上表已更新。证据：ai-key-recovery-final-build.log、ai-key-recovery-unit-results、ai-key-recovery-lint-mock.html、ai-key-recovery-pair.json、ai-key-recovery-backup-compiled-*.txt、ai-key-recovery-size-results.json。
- 永久失效使用JVM fake SecretKey触发真实Cipher异常类型回归，未通过修改真实Android锁屏/认证制造失效；Android Keystore/logcat/窗口/云备份/设备转移动态验收仍0次，仍待获准运行环境。没有真实Key、付费API、实体机器、Computer Use或外部PR重试。
