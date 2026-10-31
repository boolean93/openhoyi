# 历史详情横屏构图实施计划

**目标：** 对齐history-detail-v2的左图右记录、独立结果数值及单主操作，不改历史列表的嵌入详情布局。

**边界：** 现有develop基线66ebda9+ed01de0依赖，沿用feature/task-first-ui，目标develop PR #4。遵守docs/git-workflow.md。不合并、不发版、不安装；签名、升级、schema和设备协议不变。回退为追加revert，保留全部历史与未知结果。

- [x] HistoryDetailActivity调用renderHistoryDetail时显式传standalone=true；默认false保留HistoryActivity行为。
- [x] HistoryDetailContent全屏路径创建TaskWorkspaceLayout；primaryBody放40sp/700结果值、14sp标签与原图表，secondaryBody放已有笔记六字段（14sp标签/16sp内容、8/16dp分组）和完整信息入口。右栏局部滚动，主编辑入口放TaskActionDock固定可达；对比为右栏顶部次操作。
- [x] 时长/秤读数沿用原数据格式与缺失值；UNKNOWN告警保留在固定摘要，不删除完整详情/流速入口；采样异步加载及归属检查不变。
- [ ] 运行双Mock APK构建和git diff --check。布局改动使用既有task-frame、single-screen与lifecycle原生检查；重点核对全屏/列表嵌入、长笔记、字体1.3、短高和导航恢复。不写镜像实现的静态测试。
- [ ] 记录构建与截图分别证明的范围，普通推送后等待精确提交CI；确认实际截图前不声明视觉完成。系统栏裁定与字体间距按视觉验收记录执行。

本地最终双Mock APK构建23秒成功，日志/tmp/task-history-composition-build.log；git diff --check通过。原生检查尚未执行本候选，尤其四指标图96dp可用高度、长笔记右栏滚动、1.3字体与短高未验证；不将构建成功标为视觉验收。

418fec6用户平板实屏已验证左右构图，但元数据和空笔记占高过多。后续仅全屏路径将曲线/日期合成可展开查看的14sp摘要，与状态同行；豆款、粉量、研磨使用14sp标签/16sp值双列且允许换行，三项感受笔记继续上下分组并强调16sp标题。保留全部字段、UNKNOWN固定告警与完整信息入口，不改列表内预览。双APK构建、既有窗口矩阵和新截图验证；Git及不发版边界同前。
