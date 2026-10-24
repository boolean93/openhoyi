# 曲线详情底部操作行裁切证据

规划：[曲线footer修正](../plans/2026-10-18-curve-footer-clipping.md)。基线 `116d19d`，工作分支 `feature/task-first-ui`。不发版、不安装、不操作设备。

## 失败与根因

- 原始失败：CI `39116731390` / `3b5f29f`，`landscape zh-Hans dark=false page=CurveActivity detail=longFactory`。下载证据位于 `build/task-first-evidence/ci-3b5f29f-lifecycle/language-detail-failures/failure.{txt,png}`。
- 最新修复前对照：CI `39117264517` / `0f0beda` 同样失败；已核对 `build/task-first-evidence/ci-0f0beda-lifecycle/mock-lifecycle/language-detail-failures/failure.txt`，仍是同一 `longFactory`、同一 `469×70px` 按钮仅可见 `469×67px`、父行 `947×77px`。不是新的根因。
- 主任务已逐文件核对的补充对照：`0f0beda` single/run39117264580三个profile均320/336，16项失败均为CurveActivity同按钮裁切。用户density360下按钮738×126px仅可见738×120px，父行139px、top19/bottom145，裁6px；density200仍裁3px，与同一居中margin算法一致。`list-state-result -13`也因该按钮裁切，saved/actual列表锚点相同，不另造列表恢复根因。产物位于 `build/task-first-evidence/ci-0f0beda-single/`。
- 已读取截图：选中“深烘拿铁”，无压力示意，详情与本杯选择同排；主操作下边框被平切。日志为实际按钮 `469×70px`、可见 `Rect(1116,843–1585,910)` 即 `469×67px`、父行 `947×77px`；窗口 `1280×703dp`、density=200、fontScale=1.0。应用底部仍在925px，不是系统导航栏挤掉3px。
- `HoyiUi.button` 已将父行 `isBaselineAligned=false`。因此本次不能归因于默认基线对齐；footer显式关闭它是表达外框对齐约束，不是根因修复。
- 对照本机 SDK `sources/android-34/android/widget/LinearLayout.java`：`measureHorizontal` 按 `child.measuredHeight + topMargin + bottomMargin` 计入行高；`layoutHorizontal` 的 `CENTER_VERTICAL` 使用 `paddingTop + (childSpace-childHeight)/2 + topMargin-bottomMargin`，此分支明确不执行基线对齐。
- 原 `select` 独有 `topMargin=dp(6)=7px`。在已观察到的77px行高内，`top=(77-70)/2+7=10px`、`bottom=80px`，超出父行3px，与失败完全吻合。问题在单侧margin被居中公式完整追加，而非字号或图表大小。
- 本机引用源码SHA-256：`2b298872073243509d8beb5f5a9366040858773fefe8efcc7be7432cfb4d60e4`。

## 修正与不变量

`CurveActivity` 两控件不再带单侧垂直margin；整行承担8dp上间距，保留 `CENTER_VERTICAL`。按钮文字、字号、padding、最小触控高度、宽度权重、点击回调及 `canStart` 完全不变。`TaskActionDock` 不改。没有缩字、压图或放宽可见性门禁。

算法上，取消单侧margin后子外框居中且处于父行内；这不是新的实测70/70px结果。实测完整高度仍待下述CI运行。

## 定向检查与待验收矩阵

| 检查 | 覆盖与严格条件 | 当前证据级别 |
|---|---|---|
| `LanguageDetailChecks` | 原 `longFactory/captured/unavailable` 加明确 `noPressure/fourStage`，共5类；8语言、浅深主题；原1280dp landscape/font1.0与wideFont/font1.3 | 检查源码已扩充，待CI运行 |
| 真实window footer | 保留主按钮完整可见检查；再检查两操作全高全宽可见、父行内、外框中心差≤0.5px、文本完整；写出成功几何 | 待CI；不得冒称70/70已观察到 |
| `CurveLibraryViewportChecks` | 保留360dp正常/180dp短窗/恢复3项，新增两类型在实际collection可用尺寸中的测量与PNG/几何，共5项 | 待task-frame CI，组件截图不冒充原生完整window截图 |
| 用户平板 | 既定task-frame用户横屏2560×1600 / density360；font1.0/1.3均检查新增两类型，不增加竖屏交付门禁 | 待CI运行与Astra读图 |
| 门禁计数 | runner与两个脚本同步 `curveKinds=5`、viewport `fixtures=5` | 本地源码/脚本一致性已检查 |

无压力fixture只读取已有库。资产探针确认存在35条无压力工厂曲线，没有四阶段压力工厂曲线（仅有2条四阶段variableFlow曲线，不能误作压力）。四阶段使用 `CurveFooterFixtures` 内存只读profile，ID刻意不同于已验证catalog，并断言 `canStart=false`；其按钮采用原有不可启动文案，可能比可执行文案更长。本项只验证显示外框，不代表可执行四阶段选择行为；保留真实可执行 `longFactory` 并行覆盖。全部检查不写用户曲线存储。组件检查仍核对选择偏好与使用统计不变；语言矩阵保留原状态保护。

## 本地验证

- `python3 scripts/git_policy.py branch`：`GIT_POLICY_CHECKS_PASSED`。
- `git diff --check`、`bash -n scripts/check_mock_lifecycle.sh scripts/check_task_frame_ui.sh`：退出0。
- 构建命令：`ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2`。
- 首次被沙箱拒绝访问 `~/.gradle` 锁；同命令获权限后 `:mobile:assembleMock` 成功，AndroidTest在另一项并行颜色取证文件尚缺类型时失败。该文件未被本任务改动，不把这一轮声称为测试APK通过。
- `a7fcc86`与当时颜色文件稳定后的共享构建：退出0，`BUILD SUCCESSFUL in 28s`，`91 actionable tasks: 4 executed, 87 up-to-date`；包含只读fixture和两个调用，不含下方之后新增的成功整窗取证。完整本地日志 `/tmp/2026-10-18-task-visual-color-build.log`（另复制至 `build/curve-footer/compile.log`）。存在既有deprecated/type-mismatch等编译警告，不能称零警告。
- 没有运行安装、模拟器、仪器测试或硬件操作。新增UI的运行结论与截图必须由后续CI提供。

## 源码复核后的成功整窗取证补充

Astra已无阻断通过 `a7fcc86` 源码复核。后续补充只完善既有横屏 `landscape/wideFont` 取证，不增加竖屏交付范围。

- `LanguageDetailChecks` 对成功通过严格布局检查的 `longFactory/noPressure` 保存真实整窗PNG，直接使用 `UiAutomation.takeScreenshot()`；不使用组件 `View.draw` 或合成截图替代。
- 路径：`language-detail-success/footer-<profile>_<language>_dark=<theme>_page=CurveActivity-<kind>-window.{png,txt}`。先等待两帧并留出compositor提交时间；PNG前后均检查焦点、两操作完整宽高和父行内边界，记录完整屏幕坐标、可见Rect、尺寸、enabled、窗口、字号和density。
- lifecycle成功路径拉取该目录，并严格要求每个既有横屏profile有32套PNG/geometry（8语言×2主题×2类型）；错误路径保留已有成功样本。font1.0与1.3继续沿用原profile，不改门禁。
- 两处原中心差断言及新截图断言旁均明确：比较的是双倍整数中心，`<=1` 只容纳奇偶高度造成的≤0.5px中心量化；不是裁切容差，完整宽高仍必须相等。
- 本补充已参加稳定后的唯一样张批次统一构建：退出0、`BUILD SUCCESSFUL in 56s`、`96 actionable tasks: 16 executed, 80 up-to-date`，Mock及AndroidTest APK完成；日志 `/tmp/2026-10-18-strict-native-batch-build.log` 已读取。Astra源码复核无阻断；本补充仍没有原生运行或新PNG，严格全宽/全高及两字号、两density的裁切关闭必须待精确源码CI与读图验收，不能用编译替代。
