# 应用字号与无障碍布局规范

本规范约束 OpenHOYI `mobile` 产品界面，承接 [PR #85](https://github.com/boolean93/openhoyi/pull/85) 的实现。AGENTS.md 是日常入口；Git、发布与安装仍遵守 [Git 工作流](git-workflow.md)。本规范不修改 dot 的自定义指令。

## 1. 配置与单位

- `AppFontSizePreference` 保存规范键 `small`／`medium`／`large`，当前绝对倍率分别为 0.9／1.0／1.1；缺失或未知键回到中档。中档不是“跟随系统”。改变档位定义属于产品决策，不另设页面倍率。
- Activity 继承 `ThemedActivity`，由 `AppLanguageContext` 一次组合语言、主题及应用字号；保留密度、方向、屏幕与窗口配置。Application 使用同一偏好及隔离资源缓存，Service 资源委托 Application。不要修改全局 `Resources`、系统字体设置或 physical density。
- TextView／Button／EditText 使用 SP 和现有样式；XML 字号、行高直接值使用 `sp`。自绘 Paint 使用 `AppTextUnits.pixels(resources, sp)` 或已有基于 `TypedValue.applyDimension(SP, …)` 的样式入口，resources 必须来自所在 View／应用配置 Context。
- Android 14 起存在非线性字体换算，不用 `sp * scaledDensity`、`density * fontScale` 或倒除 `scaledDensity` 代替。需要反向换算时使用 `AppTextUnits.sp`。绘图线宽、间距、触控范围仍用 dp；不把整个 Canvas 缩放当字体适配。
- `setTextSize(PX, value)` 可以复制已转换的字号或恢复已保存的 View 像素值；不能把未转换常量当像素字号。相对字号 span 随父文字缩放，不重复乘应用倍率。

## 2. 布局与可操作性

- 不因切换字号截断正文、拆坏关键标签或遮住操作。先压缩装饰留白，再自然换行、按实际字体测宽调整布局，必要时提供滚动；不得挤掉内容或通过自动缩字抵消用户选择。已有仪表盘自动适配见例外表。
- 所有可操作控件至少提供 48×48dp 的有效触控区域，视觉图标可以更小。除了 minimumHeight，还要检查真实 bounds、祖先裁切、系统缩放和窗口坐标；完全被裁的 48dp 控件不合格。
- 弹窗宽度依据宿主窗口，不依据整块物理屏幕；窄窗／freeform 下标题、内容和按钮都需完整可达。原生 OEM 按钮栏可能有固定高度，不能假设增大外层预算能容纳子按钮外边距。
- 按钮、单选项保留可访问名称、选中／禁用状态与合理焦点顺序；自绘的重要信息须有等价可访问文本。新增字号选项应让 TalkBack 读出名称及选择状态。

## 3. 预览、持久化与生命周期

- 预览创建局部 Context，取消不保存；同档应用不重建。持久化失败展示明确错误并保持原运行时档位，不能先改全局状态再回滚。
- 重建保存原始输入字符串（例如 `12.`），不要只保存校验后的数字；还需保存编辑对象身份、模式、焦点、选区与滚动状态。新布局更短时滚动值可在有效范围内钳制，不可因此丢草稿。
- 初始布局重挂载与真实离页要区分：编辑器恢复开始前可以等待重新 attach 并绑定当前观察器；开始恢复后的 detach、停止／销毁仍取消，清理全部监听，不抢回用户已转移的焦点。
- 字号只影响显示，不重启进程／Service／BLE、不创建新萃取、不重发命令。验证 Service、杯记录、采样与预热请求身份连续。正常导航离开预热页仍按既有安全规则取消一次，异常连接不补发，不能以字号适配为由放宽。

## 4. 已知例外与适用范围

| 位置／类别 | 原因与约束 | 验证方式 |
|---|---|---|
| `AppTextUnits.sp` 的 API <34 分支 | 旧系统无 `deriveDimension`，仅该兼容分支允许除 `scaledDensity`，不能整文件豁免新增转换 | 源码局部豁免检查、API 分支审查与换算仪器测试 |
| `BoilerTemperatureDial` 既有 auto-size 和已转换 PX 缓存 | 有界圆盘内的既有数字适配，保留原机制，不将此例外推广到正文／按钮 | 大档真实拨盘文字、测量与可读性检查 |
| 权限框、文件选择器、安装器、系统通知 | 系统拥有排版，不承诺应用字号控制其文字 | 明确区分应用页和系统页，不伪报全局覆盖 |
| 标准 `Toast.makeText`，尤其 Android 11+ 系统渲染 | 原生 Toast 改造此前暂缓；保留消息、时长和异步导入／导出的反馈可达性，不吞掉或强制迁移 | 关键反馈流程检查；数量不是永久 allowlist |
| 应用自绘 `PreheatToast` | **不是豁免**，仍需使用应用 Context 与 SP | 字号／预热显示验证，不触发真实设备操作 |
| `app` 实验模块、Mock 视觉采样和 updateFixture 专用界面 | 本规范的产品自动扫描聚焦 `mobile/src/main`，不是全仓库／最终 merged manifest 证明 | 增加产品入口时纳入主扫描；变体测试入口单独审查 |

新增例外需记录具体位置、理由、影响与验证方式。优先修复实现，不能为绿灯加整目录、整文件豁免。

## 5. 自动检查能证明什么

`AppFontSizeCoverageTest` 已随 `scripts/verify-pr-checks full` 的 mobile JVM 单测运行，无新增 AVD／CI job。该检查是有限源码约束，不是完整 Kotlin 编译器或视觉分析器：

- 解析主 manifest 的 Activity／alias 目标，并检查当前 Kotlin 简单继承链通向 `ThemedActivity`；未知或无法解析的声明报错，手动核查后改进解析，不静默跳过。
- 扫描主 Kotlin 源中的常见 `Resources.getSystem`、直接 `scaledDensity`、dp／线性 density 字号写法；忽略注释与字符串，兼容分支只豁免明确表达式。不同别名、间接变量、字符串模板内表达式或 Java／动态生成源码仍需人工审查。
- 检查主资源 XML 中字号／行高的直接数值单位；资源引用允许但不递归追踪，引用值和运行时赋值仍须审查。
- 偏好单测验证三档绝对倍率、失败／未知值与同档不写；扫描器本身有正反样例，防止“仅当前仓库恰好通过”。

这些检查**不能证明**实际 48dp 可点击、没有截断、TalkBack 体验、所有页面覆盖或机器状态连续。相关源文件及资源声明为 Gradle test 输入，避免只改源码注释／资源时误复用旧检查结果。

## 6. 相称验收与记录

1. 逻辑改动先跑偏好／覆盖等相关 JVM 测试；按仓库门禁执行编译和 lint。文档改动只验文档与引用。
2. 涉及字号入口／配置／重建时，使用已有 Mock `fontSizeChecks` 代表验证独立系统字号、预览取消／应用、预热请求、活动杯、原始草稿／焦点／选区。运行须有相应安装／设备授权，Mock 不证明真实硬件效果。
3. UI 变更按风险覆盖小中大、中英文与窄手机窗、方窗、常规横窗；采用代表组合而非无条件全排列。至少看改动处的大档窄预算，并确认中小档未退化；涉及布局切换时检查阈值两侧或打开中 resize。
4. 系统字号变化独立性和 Android 14+ 换算单独验证，不能把系统 `font_scale` 调节冒充应用三档。历史手动工具需先核对 [迁移清单](plans/app-font-size-manual-tools.md)。
5. 记录精确 commit、语言、应用字号、系统字号、窗口尺寸／密度、实际执行命令与截图／状态证据。区分已跑、未跑、Mock、真机及系统 UI 例外；必要检查通过后停止，不追加无关全矩阵。

局部测试示例（仓库根目录）：

```sh
./gradlew :mobile:testMockUnitTest --tests '*AppFontSize*Test'
```

设备端已有入口（只在隔离 Mock 且已有授权时执行）：

```sh
adb shell am instrument -w -e fontSizeChecks true io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation
```
