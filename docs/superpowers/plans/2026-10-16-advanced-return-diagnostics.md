# 高级页面返回焦点诊断

遵守docs/git-workflow.md，沿用develop66ebda9+ed01de0依赖，feature/task-first-ui → develop草稿PR#4。693f215/CI38850951301在泰文浅色BrewComparison退出后awaitMain193超时；此前页面检查已经完成，无法据此归因为字体/布局。失败日志未区分销毁、首页焦点与服务身份；仅AndroidRuntime/OpenHoyiLanguage的logcat为空。

新增超时状态细节与ActivityTaskManager/ActivityManager/WindowManager日志及window dump。保留原10秒超时、完整240场景/60截图和返回首页同一服务断言，不自动重启/重试掩盖故障。仅隔离Mock CI诊断，不改生产、设备、数据、签名或版本，不发版/安装/合并。验证shell语法及测试APK编译，原生证据待下一批CI；追加提交/revert回退。

本地测试APK编译成功23秒；bash -n与git diff --check通过。尚未有新原生诊断，未宣称修复返回问题。693f215 Capture Mock UI已成功，单屏和生命周期仍运行，不取消。

0cd05cc/CI38852903588新诊断：阿拉伯语浅色ScaleSettings返回时childDestroyed=true、homeDestroyed=true、homeAttached=false、sameService=false，但serviceRunning=true。证据证明原测试等的是已销毁首页，不支持把它当按钮/字体缺陷。新增非阻断Home ActivityMonitor，仅在旧首页已销毁且捕获新实例同taskId时接续；仍要求实际focus、原service身份、语言/主题一致，全部业务/磁盘不变与240/60不删。不启动新页面、不延长超时、不重试场景；记录每次重建。Monitor在finally移除。原生产不改；精确新原生结果待验证。

首页重建跟踪候选测试APK编译22秒通过，diff check通过。待下次原生高级矩阵证明返回链路，不提前标失败已修复。
