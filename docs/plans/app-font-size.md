# 应用独立字号

- 需求与范围：设置页小/中/大和局部预览，中档默认；不跟随系统字号。应用文本、弹窗、自绘图表统一配置，不改 dp、触控范围、协议和记录数据。系统自有界面不受应用配置控制。
- 来源分支/基线提交：develop / c17d3843。
- 工作分支：feature/app-font-size；PR 目标 develop。
- 改动步骤：独立偏好与配置 Context；应用前局部预览；正确 SP 正反转换；静态覆盖和运行时验证。
- 推送与合并：相关检查完成再依 scripts/create-pr 提草稿 PR；不自动合并。
- 验证：偏好读写失败/缺省测试、所有 Activity/自绘静态扫描、语言和主题回归、草稿/配置重建与萃取行为分离验证；Mock 多布局截图待完成。
- 协议与设备风险：无协议改动；配置重建不得停止服务、发送开始/取消命令。正常导航离开预热仍保持原取消规则。
- 发布与签名：不发版，不独立新包；设备安装遵循已授权 Mock/Debug 同源码同签名保留数据边界。
- 回退：撤销本 PR，未知字号键安全回到中档，历史和配方不改。
- 未完成：实现、检查和设备证据进行中。

## 覆盖与明确边界

- 所有 manifest Activity 继承 ThemedActivity，由其包装配置；Fragment、普通 TextView、原生应用对话框继承 Activity context。
- Application 配置 provider 同步读取字号权威值，MobileService 的资源委托 Application；不重启服务。
- 自绘曲线、末端标注、仪表盘、历史/对比图、电子秤图的字体转换用 SP；相对单位 span 保持相对父文字。仪表盘已有自动适配仍可在有限空间中缩小文字以免截断。
- 新字号预览只创建局部 context；取消与同档应用不落盘/不重建，失败以内联文字保留提示。新档成功后配置重建，不改变蓝牙/预热/萃取协议。
- 用户明确不跟随系统字号，因此应用中档固定 1.0、小档 0.9、大档 1.1；系统字体调大不会改变应用文字。物理 density、屏幕尺寸、触控目标和曲线数据不修改。
- 应用自绘 PreheatToast 跟随字号。主源码 46 处系统 Toast 分为前台即时操作反馈与异步导入/导出反馈；Android 11+ 的系统 Toast 由系统渲染，不能保证遵从应用字号。用户此前暂缓统一 Toast，本次保留原消息、时长、后台可达性，不迁移或吞掉。删除撤销为持久内联行，未降级。
- 权限框、安装器、系统通知等系统界面不受本设置控制。
- 正常导航离开预热页仍按既有要求取消；仅配置重建不得额外发送取消/开始命令。验收区分这两种行为。

## 已执行检查（构建前工作树）

- JVM mobile Mock：132 suites / 688 tests，0 failure / 0 error（包括新增偏好 4 个和覆盖扫描 2 个）。
- 本地化：validate_catalog、android_resources --check、25 个 localization 单测均通过；新增文案用独立双语 feature resource。
- production Mock/Debug Kotlin 编译通过；打包/lint 与 instrumentation 编译继续中。
- instrumentation 首次被离线缺少 AndroidX core-runtime AAR 阻塞，已由 Google 官方 Maven 下载并校验官方 SHA1 后补缓存。非源码错误。
- Mock 仪器入口：`am instrument -w -e fontSizeChecks true io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation`。尚未安装/执行，不作运行时通过声明。

- 独立 Astra 实际代码审查：未发现阻断进入设备验收的生产问题；要求补预热 requestId 保持及动态系统字号真实设备验证。前者已加入仪器测试，待执行。
- lifecycle 字号旧假设已迁移；其余手动截图工具尚需迁移，逐项范围见 [测试工具迁移清单](app-font-size-manual-tools.md)，不把这些未运行工具计入通过范围。

- 已完成 Mock/Debug APK打包、Mock AndroidTest APK编译、lintMock，无错误；测试仍未在设备执行。
- 构建网络：Gradle 使用现有运行环境代理及既有 `/etc/ssl/certs/java/cacerts` 信任库解决官方 Maven TLS；未降低 TLS 校验、未改系统信任设置。

## 真机首次验收后的修复

- 400×640dp freeform + 大档：原生平板 Dialog 默认最小宽度溢出宿主窗口，预览与应用按钮被裁。新增局部 owner-window 宽度预算（两边16dp、最大560dp），resize更新并在dismiss清理，不改变其他弹窗/触控目标。
- 仪器测试曾在同一主线程轮次 show 后立即点击，onShow异步监听尚未安装，导致误判。测试改为分步骤、有界等待监听与两帧布局，保留原断言，新增完整窗口/按钮/预览边界及48dp检查；主线程断言回传测试线程以保留finally恢复。
- 独立Astra复核新增生产helper无阻断；待400dp大档真机复验及弹窗打开时resize测试，未宣称验收通过。

## 后续真机反馈（待最终整体验证）

- 400dp大档弹窗已实测修复，打开中400→568→400dp也不裁切；预览取消保持原配置。
- RadioButton `performClick` 返回值反映监听处理，不保证表示选中失败；测试改断言实际checked、RadioGroup id及互斥状态，保留预览/持久化/布局断言。
- 预热/萃取测试需模拟真实Use入口先持久化选中曲线；前后恢复原曲线偏好。各测试宿主finish后等待destroy，避免上一场景延迟onStop取消下一场景已开始的预热。重建保持与正常导航取消仍分别严格验证。
- 英文中档原本也存在Settings/Disconnect拆词，大档放大后底部被遮；侧栏按实际SP标签/动作字宽计算稳定最小宽（中文主导航保持104dp基线），不缩字、不变点击尺寸和导航位置。
- 用户反馈取消/应用按钮不协调：本弹窗局部改为轻取消+主色应用、统一最小48dp高度/88dp宽度及12dp间距；无全局弹窗风格迁移。
