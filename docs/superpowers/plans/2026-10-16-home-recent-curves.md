# 准备页真实最近配方

按认可brew-preparation-v2补图下最近3条真实使用曲线。复用CurveUsageLedger/CurveOrdering，排除从未使用及已不存在曲线，不伪造最近项，不写使用计数。点选仅写现有待选曲线偏好并刷新准备页，当前有活动杯禁止更换；不可执行曲线保留只读提示，不启动、不扣库存，原确认流程不变。

主区有空间时展示列表，按实际字体测量其自然高度；保留阶段图原96dp最低预算，放不下则在辅助滚动区显示最近使用入口，展示相同条目。无记录与记录不可用分别说明。返回首页刷新条目，不在250ms遥测循环反复排序历史。中文优先，复用8语言已有文案。

遵守docs/git-workflow.md：develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。先验证最近记录过滤/限数/不写入，再实现UI、双APK及原生短高/大小字/恢复验证。无硬件操作、不发版安装合并，普通push/PR与revert回退。

最近记录测试先因recentUsed缺失编译失败；实现后完整JVM299 tests/0 failures/0 errors/1既有skip，双Mock APK50秒构建成功，diff check通过。HomeRecentCurves只在页面启动/构建刷新列表，250ms循环只同步活动杯禁用状态；短窗同项对话框在离开页面关闭。真实三条记录、点选后不启动/不写用量、短高入口及大小字截图还需原生验证，不能据本地结果宣称视觉完成。

补原生HomeWorkbenchChecks：在不替换已有记录的前提下追加3个命名Mock使用fixture（冲突则失败），随后建立用量/库存/笔记基线。新增960×720组件探针，核对真实最近顺序、点选改变待选ID与阶段预览但service/shotState不变；恢复原选择后继续既有正常/180dp短高/恢复探针。短高要求入口完整可滚动露出，不仅isShown；最后核对用量、库存、准备和笔记基线未变。脚本成功标记同步为4探针与recentSelection=true，未删旧检查。需编译及实际CI执行。

最近配方原生验证双Mock APK编译42秒成功，shell语法与diff check通过，尚未原生执行。旧9744053 lifecycle已终态失败，下载音频报告明确Native audio did not stabilize active=false；不将该失败解释为UI已通过，也不删除音频门禁，原因仍待核对。
