# 用户反馈改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. 用户已指定 Astra 规划验收、Sol 开发，直接按该分工执行，不再询问执行方式。

**Goal:** 将机器首页、真实Tab、同图萃取、设备恢复和统一设置落成可运行原生界面，修复BOOKOO连接即计时。

**Architecture:** 单 `MainActivity` + 六根Fragment及壳内子路由，Controller持有页面View；服务持续持有硬件及萃取事实。连接恢复与UI导航分离，原业务门禁不降级。

**Tech Stack:** Kotlin、Android View、AndroidX Fragment、现有MobileService/device-session、JUnit、原生instrumentation。

## 范围与Git边界

- 来源：现有 `feature/task-first-ui`，基线 `f88f0de47078f50d3a8bd301553c69f19b1a9f6b`；它是既有PR #4的develop功能工作，不覆盖未合并PR历史或重建分支。
- 目标：将来仍PR到develop，禁止main/develop直推。用户已授权开发，主协调者在现有feature分支普通提交并推送既有PR，只收本任务文件；不合并。
- 规则：[git-workflow](../../git-workflow.md)、[任务模板](../../plans/task-template.md)。不得覆盖同工作树其他开发者改动。
- 不发版，不改包名/签名/versionCode，不安装APK、不操作真实机器。升级数据格式不变，未知写入/历史记录保留。
- 回退：按独立改动组追加revert或修复；不清数据库、不卸载、不降级APK、不重发未知写入。
- 设计：[用户反馈设计](../specs/2026-10-26-user-feedback-redesign.md)。Astra以本文件记录验收，避免另建重复跟踪文档。

## 进度

状态仅使用“未开始 / 开发中 / 软件通过 / 实机待验”。“软件通过”不代表设备效果确认。

| 项目 | 状态 | 当前证据/下一步 |
|---|---|---|
| 协议计时修复 | 软件通过 | Astra最终审结通过：生产仅删07且保留时序；同地址READY→FAILED→重连两代精确三帧通过。主协调者核对675个JUnit零失败/错误、协议39823 checks、会话93 checks；Mini/Ultra实机待验 |
| 真Tab迁移 | 开发中 | Sol实现单壳、真Fragment与Controller；六占位页不算完成 |
| 机器首页常驻控制 | 未开始 | 首页双锅炉/快捷萃取、睡眠秤状态固定操作区 |
| 大图设计 | 未开始 | 四指标同图参数已定，复用真实投影 |
| 自动导航与重连 | 未开始 | 新杯ID去重、原设备恢复、主动断开抑制 |
| 设置弹层统一 | 开发中 | 统一tokens与公共弹窗入口，功能清单逐项迁移 |
| 整体验收 | 未开始 | 下述有界原生场景，硬件项单独标记 |

## 文件所有权与交接

同一文件只设一名开发所有者；其他人给补丁建议，不并发覆盖。下面 mobile路径基准为 `mobile/src/main/kotlin/io/openhoyi/mobile/`。

| 所有者 | 文件范围 | 责任 |
|---|---|---|
| Astra | 本计划、配套spec | 设计决策、diff审查、验收结论 |
| Sol导航 | `mobile/build.gradle.kts`、`mobile/src/main/AndroidManifest.xml`、`ThemedActivity.kt`、`MainActivity.kt`、`TaskNavigationView.kt`、`TaskNavigationModel.kt`、`TaskPageFrame.kt`、新增根Fragment/Controller及壳路由；迁移六根Activity与ExtractionActivity | 真Tab、页面View迁移、壳绑定服务、saved state；拥有这些Activity迁移期间所有编辑 |
| Sol协议 | `protocol-core/.../Protocol.kt`及对应协议/会话测试 | BOOKOO初始化最小修复；不改UI |
| Sol连接（导航接口稳定后） | `MobileService.kt`、session连接恢复文件及新增纯策略测试 | 新杯ID、原设备恢复、主动断开抑制；不得重写导航文件 |
| Sol视觉/设置 | `SettingsVisualStyle.kt`、新增公共Dialog组件、设置/图表View和样式文件 | 公共组件；根Activity/Controller接入交给导航所有者或串行交接 |

首页/萃取视觉改动在导航迁移交接后由其Controller所有者接入，避免两个Sol同时改巨型Activity。任何临时兼容Activity不得成为最终导航实现。

## 批次1：真Tab与内容迁移（立即执行）

- [ ] 增加AndroidX Fragment依赖并将主题宿主改为FragmentActivity；保持语言/主题包裹。Manifest主入口指向MainActivity。
- [ ] 新增壳导航契约：`selectTab(destination)`、`openRoute(route)`、`back()`；根页稳定tag匹配destination。Tab transaction只show/hide、setPrimaryNavigationFragment、setMaxLifecycle；重复点当前Tab不重建、不写返回栈。
- [ ] MainActivity只安装一个TaskPageFrame和导航/状态操作区。TaskNavigationView只接受当前目的地和回调，不凭activity.javaClass推断选中项。
- [ ] 先迁AppSettings/Scale，再Curve/History/Bean，最后Home/Extraction。Fragment真正返回页面内容View；Controller可使用宿主Context/主题/系统授权，不可创建Activity、调用host.setContentView或读取宿主类名决定页面。
- [ ] Controller输入为路由参数和saved state；实现createView、可见/隐藏、saveState、dispose。隐藏停UI轮询；onDestroyView清View引用、监听和弹窗。服务连接由MainActivity统一持有，页面不得因Tab切换释放BLE连接。
- [ ] 每Tab子路由与草稿独立恢复；处理返回、旋转、主题/语言重建；外部通知入口转为壳内路由。旧顶层兼容Activity只能转到MainActivity并结束，不能再创建业务View；二级Activity可保留但无顶层rail。
- [ ] 核对二级页：曲线编辑/导入/预览/分享，历史详情/对比/复盘，豆录入/批次/准备/台账，机器设置/秤连接/秤设置，以及仍可达的legacy页面本轮只确保入口和状态无回归，可保留原Activity但移除顶层rail；不展开所有二级Fragment迁移。
- [ ] 新增 `MainNavigationChecks.kt` 原生场景：六Tab循环20次Activity始终1个、每根Fragment至多1个；Curve筛选/History滚动/豆草稿各保留；Extraction切走回来series ID/时间递增；配置重建恢复当前Tab和子页。单看截图或类声明不算通过。

批次1交付点：真实六页和萃取在单壳可用，原有业务门禁不变。之后按所有权交接视觉接入，不能让占位Fragment变成永久实现。

## 批次2：机器首页与常驻操作

- [ ] HomeController改成机器状态首屏；固定双锅炉温度/开关、机器连接状态、配方与快捷启动；把准备细节移到次级展开区。
- [ ] 抽取原confirmStart可复用入口供首页和萃取共用，同样执行设备/曲线/温度/秤/未知写入门禁和用户确认；不复制一套较弱校验。
- [ ] 提取睡眠确认与秤连接状态到壳操作区；断开×和重连icon为独立48dp目标，禁用时保留说明。锅炉写入等待回读，不乐观标成功。
- [ ] 测试IdleTelemetry双锅炉可显示、ExtractionTelemetry蒸汽缺失显示未知；检查五槽合法边界及断连/未知写入时不可快捷启动。
- [ ] 原生截图：720×360dp和1280×800dp首页首屏，不滚动能看到连接、双锅炉、快捷启动和常驻操作。

## 批次3：萃取大图

- [ ] 改 `TaskExtractionWorkspace.kt` 移除强制宽侧栏，主图占主要空间；原 `ExtractionOverlayView.kt`/`ProductHistoryChartRenderer.kt` 复用 `FourMetricChartProjection.kt`。
- [ ] 四线同图、指标/单位/范围一排；当前实线与参考虚线区分；图例只切轴和强调，不能退回四小图或一次只见一项。
- [ ] 把重复准备/摘要/说明卡压缩为一行和次级工具；停止固定可见。遵守spec尺寸、色值与无动画参数。
- [ ] 保留并运行 `FourMetricChartProjectionTest` 和 `ChartInspectionTest`；增加同图四指标呈现、缺测断线、注水复位、负重量场景。原生量测图高与停止按钮边界，不只检查tag存在。

## 批次4：新手动杯导航与连接恢复

- [ ] MobileService从PassiveShotDetector开始事件公开稳定ID；MainActivity只消费新ID。先保存lastPresented再导航。重复遥测、旋转、同杯切Tab不重跳；下一杯重跳一次。
- [ ] 新增纯策略测试：前台新杯→一次，切走同杯→零次，重建同杯→零次，下一杯→一次，后台已结束杯→零次。
- [ ] 查明现有ReconnectPolicy和DeviceHub角色连接所有权后复用；仅补缺失的原设备地址、用户意图抑制和退避触发。禁止全设备扫描后随便连另一台。
- [ ] 增加意外断连→同地址恢复；主动断开→不恢复；明确重连→解除抑制；旧generation回调→不改变新连接；蓝牙关闭/权限缺失→停重试并提示。
- [ ] outbound回归：重连不能发送启动/停止/设置/睡眠/去皮/计时；原未知结果继续等待核实。机器唤醒无证据的控制帧不新增。

## 批次5：BOOKOO初始化修复

- [ ] 生产只删除初始化 `030A0700000E`，保留其余三帧及500/1000/2000ms。
- [ ] 协议测试精确断言三帧和时间；adapter拒绝旧07帧。
- [ ] DeviceSession模拟同地址首连到READY→意外断线→同地址重连到READY，两个generation分别只写预期三帧，通知继续产出重量。不额外补stop/reset/tare。
- [ ] 更新旧“每帧500ms、共四帧”测试假设，运行两模块check。真实Mini/Ultra连接计时不动且重量不清零留作实机验收。

## 批次6：设置与自有弹层统一

- [ ] 用rg枚举 `AlertDialog.Builder`、`Dialog(`、`PopupMenu` 入口并映射到公共构建组件；统一间距/字体/颜色/宽度/动作顺序。系统权限和文件面板不接管。
- [ ] 设置机器/秤/App三组保留所有现存参数和低频入口。迁移只改显示与导航，不改设置编码/值范围/回读与未知结果门禁。
- [ ] 检查代表弹窗：启动确认、睡眠、断开、锅炉编辑、删除曲线、语言/主题选择、图表检查、恢复未知结果；浅深色、长中文及英文、放大字体时正文可滚动且动作可点。
- [ ] 搜索所有剩余直接构建点，逐项迁移或注明系统/外部排除理由；不能只换一个公共弹窗后宣称“全部统一”。

## 有界验证与验收

每批先跑改动模块的针对性测试；合并工作树稳定后跑一次完整本地验证，除新改动/失败外不重复扩大历史矩阵：

```bash
./gradlew --offline --console=plain :protocol-core:check :device-session:check :mobile:testMockUnitTest :mobile:assembleMock :mobile:assembleMockAndroidTest
python3 -m unittest discover -s scripts -p test_git_policy.py
python3 scripts/git_policy.py branch
```

依赖首次解析若离线缓存缺失，用联网Gradle解析正式依赖后再跑；失败不得伪装通过。Mock安装/运行只在主协调者确认既有模拟器授权范围后执行，不安装用户真机。原生检查接现有instrumentation runner，不依赖网页样张代替Android。

| 验收 | 最小场景 | 通过条件 |
|---|---|---|
| 原生视觉 | 720×360、1280×800；中文浅/深；另中文1.3字体与英文各一屏 | 无关键遮挡；首页关键控件首屏；四指标同图；停止始终可点 |
| 生命周期 | 六Tab20次、萃取中切换、一次旋转/主题重建 | 单Activity，Tab状态保留，服务不重启/断开、不重发命令 |
| 新杯导航 | 手动杯1、同杯切走、手动杯2、后台结束杯 | 首杯/第二杯各一次，同杯与已结束杯不抢回 |
| 写入安全 | 断连/未知写入、快捷启动、锅炉/睡眠待回读 | 门禁保留，待确认不当成功，重连无业务命令 |
| 连接策略 | 意外断线同地址、主动断开、手动重连 | 恢复原设备；主动断开不反连；连接代际不串线 |
| BOOKOO软件 | 首连/同地址重连精确帧与通知 | 不含07，三帧时序不变，重量通知正常 |
| 实机待验 | 真机双锅炉、拨杆两杯、机器睡眠唤醒、秤断线恢复、Mini/Ultra计时 | 必须真实设备记录；Mock和绿色CI均不替代 |

完成报告只列本次代码结果、测试实际输出、剩余实机项；未完成内容继续留在进度表，不把阶段性编译通过当成整个改版完成。

### BOOKOO最终独立审查（2026-10-26）

Astra已阅读生产与同地址断线重连测试diff，批准9个协议/会话文件作为独立修复提交。生产保留500/1000/2000ms三条安全初始化，未引入其他命令；adapter拒绝07。新测试确实经onDisconnected进入FAILED后同地址重连，generation变化，两代outbound精确为三帧，通知恢复READY。其余测试只修正旧四帧/500ms步进假设。测试结果由主协调者核对：675 JUnit零失败零错误、protocol39823 checks、device-session93 checks。软件通过，物理设备效果仍待Mini/Ultra验收。
