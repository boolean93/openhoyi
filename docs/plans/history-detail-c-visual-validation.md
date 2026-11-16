# 历史详情 C：待执行视觉验收

本计划延续 [实现记录](history-detail-c.md)。当前仅准备方案，不操作用户平板、不安装、不修改显示、不推送/PR。AI 平板任务仍在运行。此前独立包安装拒绝仍有效，不能换包或换工具绕过。

## 本机能力调查

- 已安装 Android SDK 与 Android Studio；Studio 内有 layoutlib.jar、layoutlib-loader.jar 及 design-tools/resources/layoutlib。
- 仓库未配置 Paparazzi、Roborazzi、Robolectric 或 screenshotTest 渲染入口。mobile JVM 测试依赖只有 JUnit 与 JSON，现有 Activity 不是可直接运行的离线预览。
- `$HOME/.android/avd` 与 SDK 的 emulator 目录不存在；system-images 下未发现 source.properties。没有启动、下载、创建或恢复 AVD。
- Studio 缓存目录或 layoutlib 文件存在不等于具备能加载本次 Activity、应用字体和真实窗口的截图运行器。本轮没有启动 Studio、连接 adb 或运行 Gradle。
- 现有 `check_extraction_history_visual_migration.sh` 要求 disposable emulator，会安装 Mock/测试包、更改 wm size/density、主题与系统字号；不能在用户设备运行。`check_single_screen_ui.sh` 同样有设备写入，不能因为有截图命令就直接使用。
- 旧 M5 runner 固定 tablet-user-landscape、density 360、系统 fontScale 1.0/1.3，不能冒充 App 三档字号验证。AppFontSizePreference 的实际档位为 small=.9、medium=1、large=1.1，独立于系统字号。

## 可执行的矩阵清单

只导出待验清单，不访问设备、不生成截图、不判定通过：

```sh
python3 scripts/history_detail_c_visual_plan.py > /tmp/history-detail-c-visual-plan.json
```

共 11 个代表场景，避免完整笛卡尔积。2560×1504 内容区覆盖深浅色×三档字号，共6项；另5项分别为1600×900常见横屏/大字深色、1504×2560平板竖屏/中字浅色、1080×2160手机竖屏/大字深色、900×900小窗/大字浅色、1280×640短窗/中字深色。具体 density 与档位见脚本输出。

尺寸指**扣除系统 Insets 的 Activity 内容区**，包括应用页头；不是屏幕分辨率，也不是纯 plot。运行时必须以真实窗口几何核验，不能仅执行 wm size 后声称达成。各尺寸是验收目标，不是本轮实测值。

## 截图运行入口待补齐

复用 ExtractionHistoryVisualMigrationChecks 的真实 Activity、隔离 Scope、NativeWindowFrameEvidence / PixelCopy 与 host 验证，不另画示意图代替 App。新增 history-detail 专用 case 参数，传入尺寸/主题/App字号，保留真实窗口、控件和业务状态证据。需要扩展旧入口及 host 对 profile/density/fontScale 的硬编码；目前不能用旧脚本直接执行这11项。

App字号只在一次性测试 Scope 的内存存储中设置，使用 AppFontSizePreference.select，再按应用既有重建路径生效；记录实际 current.key 和 scale。不能通过系统 font_scale 代替或写用户偏好。使用合成历史样本和六字段个人记录；不读取用户已有记录、Key或连接真实机器。先正常样本全11项，再仅在最窄/大字号场景补 UNKNOWN、空样本和长名称/长个人记录代表例，不扩成全矩阵。

窗口变化另检查同 Activity 高度变化后的重新测量与滚动恢复；不能仅重启不同大小的 Activity 就宣称已验证 resize。

## 必须保留的断言与人工复核

1. 每张图绑定实际源码状态（HEAD+dirty diff摘要）、APK SHA、case、App字号、主题、density、Insets和内容区几何。截图和几何必须来自同一接受帧；不复用旧截图冒充本轮。
2. 统计实际 plot 矩形高度/内容区高度及面积占比，排除轴、图例与外框。与真实旧图同口径比较，交 Astra 判断曲线是否确实主导；160dp只防坍缩，不能作为“曲线主导通过”标准。小窗允许滚动，不强制单屏。
3. 曲线在图例/汇总前；配色、实虚线、物理刻度、单位与实时 renderer 保持一致。左右轴极值、时间轴起终点、无数据提示不裁切或覆盖。数值不能因选中指标而丢失。
4. 原name/number/unit/range逐行完整可达、无ellipsis；指标切换可操作，4项图例的实际完整可见点击区域至少48dp×48dp。只检查minHeight不足以通过。
5. 计时标签与数值、结束秤读数、末次采样注水、采样状态/UNKNOWN警告均完整可达且语义不变。对比（顶部或底部）、复盘、详情3个入口各完整48dp可达。
6. 详情弹窗六项原始记录、长文本、流速采样明细可滚动读取；截图覆盖每一原文行。关闭后焦点/内外滚动恢复，points/reference/selected/journal不变。
7. 深浅色与三字号由真实像素人工复核：对比度、轴标签间距、图例辨识、曲线占比及操作拥挤程度。编译、静态预算、矩阵导出或host合成契约通过均不等于这一项通过。

## 早期安全执行路径（历史记录，已被下方当前路线覆盖）

目前没有本机零安装的现成真实截图入口，保持全部 NOT_RUN。优先在后续获得推送/CI执行授权后，适配仓库现有手动 GitHub Actions 一次性模拟器，仅运行这11个代表case及少量异常case；当前禁止推送/PR，因此不触发工作流，也不把远端旧提交截图算本次结果。

若未来选用户平板，必须等AI任务释放、另有明确安装/设备授权且确认数据保护边界；这并不自动授权覆盖安装、独立包安装或显示修改。当前不得以“继续验收”为由执行这些操作。

## 早期轮次实际执行（仅当时状态）

只读检查配置/本地路径，导出11项待验矩阵并核对字段与唯一ID，检查文档链接及git diff空白。**真实Android渲染0次、截图0张、设备操作0次**。多尺寸/主题/字号视觉缺口全部保留。

## 当前执行路线（2026-11-16，覆盖上述早期只读/NOT_RUN方案）

用户已授权历史-only分支在临时 GitHub CI 模拟器执行真实 Activity 验收并最终走 PR；未授权本地 AVD 或本任务操作用户平板。旧 `history_detail_c_visual_plan.py` 仅是历史待办导出器，不列入本次提交或成功证据。

使用已注册的 `extraction-history-visual-ui.yml`，手动输入 `mode=history-c`，固定分支头提交；默认 legacy 路径保留。专用 `check_history_detail_c_visual.sh` 仅在 GitHub 的 qemu 设备运行，7 个代表配置固定系统字号1，实际调用 AppFontSizePreference small/medium/large 并记录 Activity 读回的字号、density、主题与 window bounds。复用既有隔离 Scope 与严格 NativeWindowFrameEvidence；每项PNG、原生geometry及proof哈希需关联通过，不能凭状态字符串判通过。

配置：2560×1600@360 medium/dark、同尺寸 small/light、1600×1000@200 large/light、1600×2560@360 large/dark、1080×2400@480 medium/light、1920×1080@480 large/dark、900×900@320 large/light。全部读取真实 Canvas、完整轴标题与刻度、原生图例正文与48dp触区、图例相对绘图区位置、滚动可达操作；首配置验证真实范围/详情弹窗，手机配置验证 UNKNOWN 与 empty。

最后一个配置在临时CI平台启用并启动后读回的 freeform 能力上，通过公开 launch bounds 启动真实 Activity，再对同一 task 执行 `am task resize`；读取当前 Activity（包括重建后实例）的 bounds 与 multiwindow。resize 与 restore 后重复布局检查。若平台不支持明确 BLOCKED/失败，不把 wm size 或仅截图当作真实 task resize。CI系统设置、night与wm原始值在finally恢复并读回；失败保留截图/manifest，不降级为PASS。

目前状态：实现与本地编译验证进行中；尚未推送/dispatch，7配置和真实task resize **均未执行**。结果将以精确提交的CI run与原始证据更新；机械通过后仍须 Astra 看图审核。旧整合平板实拍只说明单台设备效果，不能替代本路线。

## 当前授权与一次真实点击补丁

用户已授权历史-only分支推送并在临时 GitHub CI 做真实多尺寸验收，实际图证据审核前暂不创建 PR，不操作本机平板。当前 runner 是上述 **7 个**代表配置，不是早期11配置 PLAN_ONLY 导出器。首配置追加一次压力图例的真实屏幕坐标 DOWN/UP 注入：坐标来自点击前严格 native frame 的完整至少48dp触区中心，验证指定压力弹窗的范围与bar单位，关闭后验证曲线原始数据/引用/trace/journal和选择状态保留，最后恢复原选择/焦点/滚动。其他配置沿用轻量可达检查；不能用 performClick 当作这一次实际触摸证据。

本次仅修改仪器/host和本计划，未改生产代码、未运行Gradle或设备。实际注入、7配置与task resize仍待精确分支头的CI执行；通过编译/静态检查不代表已执行。最终补编译由协调方串行进行。
