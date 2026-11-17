# 独立 Android 验证包评估（仅评估，未创建或安装）

- 来源/工作/目标：develop来源既有 `feature/ai-chat`，基线 `599670aab75d94070bf836cb813239e30e3e46e7`；未来目标develop。本轮仅写此评估，不改源码/版本/manifest、不创建包、不构建、不推送/PR/合入，保留全部未提交。遵守 [Git工作流](../git-workflow.md)。
- 目标：判断现有本地环境能否在无需新安装下补真实Android依赖证据；不以JVM/fake或预览冒充Keystore、ContentProvider、生命周期动态验收。

## 授权与拒绝作用域

可信父线程回读记录：2026-11-13T01:51:48Z，aeon报告独立布局测试包安装失败 `INSTALL_FAILED_USER_RESTRICTED`；2026-11-13T01:53:13Z，用户 `Sentinel_d13640e1ef28819198c0dd73504aae32` 明确说「别装独立包了，你就直接用mock包呗」，助手随后答应不再安装独立包。进一步核对原布局线程 `6af665b7-9d64-8191-8253-2e70e41a803a` 的原始工具记录：被拒目标是 `io.openhoyi.mobile.layoutcheck`，首次adb安装返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，随后启动返回 `Error type 3`，确认该包未安装。之后改用既有Mock91→92同签名保数据覆盖成功，不构成当前独立包或133覆盖授权。

这是当时的设备/用户安装拒绝及后续明确限制，不是当前自动审批拒绝或此次新失败。精确作用域证据由根线程保存于 `openhoyi-local-materials/ai-offline-preview-mock133/isolated-install-scope-review.json`；一般平板测试授权未明确撤销此限制。

最新「支持长程任务开发，用平板测试」没有明确撤销独立包限制。本轮不重试、不换包名/签名/安装器绕过，不改系统安全设置，不创建辅助包，不把一般测试授权视为独立包安装许可。若未来明确解除这项限制，仍须单独核对当时设备、包身份、数据边界及最小验证范围。本轮未执行install、adb、设备导航/输入/截图，也未启动或创建AVD。

## 本地只读核实

- 配置SDK目录 `$ANDROID_HOME` 存在Android 28/33/34/35编译平台及build-tools，可复用离线编译能力。
- `system-images/android-33/google_apis` 与 `android-34/google_apis` 只是空目录，没有ABI子目录、镜像文件或source.properties；不能称已有可运行镜像。配置SDK的 `emulator` 不存在，`$HOME/.android/avd` 不存在。
- 当前Gradle依赖只有既有JUnit/org.json等测试支持，未声明Robolectric；已配置缓存中也未发现org.robolectric或android-all模块。Android SDK的android.jar是编译接口，不能提供真实Android运行时或Keystore。
- 因此没有核实到无需新安装、下载或启动环境即可新增真实Android证据的本地运行入口。不搜索废纸篓/恢复镜像、不下载组件、不启动旧或新AVD，不为进度重复已覆盖测试。没有检查或操作当前平板。

## 独立包技术上成立的条件（尚未实施）

1. 使用独立applicationId且不声明sharedUserId，获得独立UID、私有data/noBackup目录和Android Keystore命名空间。同签名不等于共享私有数据；同时必须移除现有签名fixture权限与任何跨包调用/URI授权通道，不能访问Mock/正式包的provider。
2. 必须是最小专属Application和显式组件白名单，不能只给现有app换applicationId。当前MobileApplication普通主进程会初始化设备/更新owners；现有主manifest还继承INTERNET、安装、前台服务、通知、更新FileProvider及其他组件。现有Mock独立预览进程guard不等于新UID容器隔离。
3. 合并后manifest不得包含INTERNET、蓝牙/定位/相机/存储/安装等非必要权限；不声明可访问用户数据的组件。测试provider使用独立authority、默认不导出，仅返回内置合成数据；入口不接受URI、路径、凭据或任意网络地址。测试签名/权限不能让其获得现有Mock fixture访问权。
4. 仅复用生产纯策略、Presentation、codec及明确注入的Android依赖接口。所有文件、密钥alias、provider和恢复状态均属于该测试UID；网络依赖只允许fake，不能构造真实模型/OCR/搜索调用。不读取、替换或删除用户Key、聊天、曲线、附件或更新文件。

## 可复用证据与真正缺口

- 模型/OCR/附件/曲线/compact/搜索/用量已有16类131项离线测试全部通过，lint通过；证据 `/tmp/openhoyi-model-ocr-evidence.json`。
- Mock133合成隔离预览已有7类40项、lint与Android测试编译通过；进程guard修复后2类6项及133构建通过，证据 `/tmp/openhoyi-offline-preview-evidence.json`。133只保存，未安装或设备运行。它的专属进程仍与Mock共享UID，依靠不调用用户store的代码隔离；不能冒充独立UID、真实picker/Keystore或生产曲线写入验收。
- 已有Android credential fixture会写假Key，禁止在用户Mock数据容器直接运行。当前132也没有非敏感保存成功ack，黄金schema2迁移fake不能证明设备新增内存草稿/历史已落盘，不能据此覆盖安装。

未来若独立包限制被明确解除，最小新增验收只需三组真实Android行为：①在测试UID生成合成AES-GCM alias，保存/重建/恢复假会话，并验证缺alias/坏密文失败关闭；②自有合成provider验证content URI、MIME/大小边界与取消/关流，不选择用户文件；③样例Activity配置重建/暂停/进程恢复，核对草稿、双模型槽及IN_FLIGHT恢复UNKNOWN，确认恢复不触发请求。复用已有131项证据，不扩全矩阵，不联系真实服务，不触及原Mock或正式包。独立包自身也应先核对合并manifest、UID、authority和数据目录，再运行这些用例。

## 结论、发布与回退

本地编译缓存可用，但没有可直接补真实Android依赖证据的运行环境；独立包安装仍被既有用户限制约束。当前最小阻碍是授权边界与缺少可用本地Android运行时，而非再跑一次JVM测试。

本轮仅评估文档，不发布、不安装、不读真实Key/数据、不使用Computer Use/咖啡机、不修改环境。未新增可执行验证，因此没有重复运行Gradle或测试。无包、签名、数据schema或用户状态变更；撤回此文档即可回退。后续仍按原工作流与用户授权推进，不以卸载、清数据或绕过系统限制作为验证手段。
