# 历史记录列表详情实施规划

已认可规格§7 History/F5；横屏优先，21页范围不变。遵守docs/git-workflow.md，develop66ebda9/ed01de0依赖 → feature/task-first-ui → develop草稿PR#4。仅普通推送，不合并/发版/安装设备。追加修复或revert，不清数据。

1. 抽取HistoryDetailActivity的已有展示为ThemedActivity共享渲染方法。独立详情与列表右栏复用全部图、真实状态/未知提示、复盘/对比、完整来源/流速明细。异步样本返回只更新仍挂载的当前图，不覆盖后选记录。
2. HistoryActivity接TaskCollectionLayout。左栏保留日期分组、三来源和所有原导入导出迁移入口；右栏选中记录并提供全屏详情。按来源保存选中ID，重建恢复；删除/来源不可用清理选择，不猜记录。列表当前位置在进入详情前保存。窄高度回退时提供返回列表，系统返回同义。
3. SingleScreenListStateChecks继续真实滑动和重建检查，历史条目点击改为先选中右栏再显式全屏详情；增加选中记录恢复断言。不放松原位置/偏移/所有权检查。
4. 本地完整JVM与两MockAPK构建，精确源码横屏CI与实际图表截图验证。独立审查待执行；没有图像/运行证据不宣称全部完成。

文件：HistoryActivity.kt、HistoryDetailActivity.kt、新HistoryDetailContent.kt、SingleScreenListStateChecks.kt。无协议、库存、历史持久化schema或设备动作改动。

追加CI证据分类修正：922752c用户横屏336场景和14列表恢复原生通过，后置PNG总数为338，新增inventory-selected-restored.png与legacy-curve-selected-restored.png混入原336页面统计。脚本将这两个明确命名恢复证据单独强制存在并检查PNG，其余仍严格336，其他64/44+/2/6门槛不变。实际下载artifact运行精确修改后校验通过。

## 原生失败与设计稿复核

849e1c1标准横屏CI38847380282：部分语言HistoryDetail缺实际样本；新防串杯判断在视图尚未挂载时丢弃快速IO结果。改为chart.post等待挂载后执行，并保留终止/已移除视图拒绝更新。列表恢复位置/偏移其实完全一致，失败来自图高223/890不足40%主区域要求。对照history-list-v3：来源切换与列表工具应归左栏，而不是占满整页图上方。将原来源tabs/count/actions原实例移入browserBody，保留全部入口、回调和滚动门槛，给右侧图完整高度；不降低图高或样本断言。精确CI仍待验证。

新增实际选中历史图的恢复后截图，先等待已有fixture样本、执行原完整页面检查再拍图；脚本单独强制collection-details/history-selected-restored.png存在和PNG有效，不混入336页计数。截图产物同时附系统字体fallback配置、构建指纹、density/fontScale以支持视觉一致性审计；读配置失败保留缺口，不称精确字体已认证。

截图流程同步：849e1c1 Capture Mock UI38847375558已完成模拟萃取，但仍在点击历史条目后等待旧HistoryDetailActivity跳转。改为断言仍在HistoryActivity、捕获history-selected-dark，再通过实际“查看详情”按钮进入并断言原全屏详情。沿用完整模拟萃取与页面/PNG检查，不直接启动详情绕过交互。bash语法检查通过，原生新流程待执行。

实读be3a538标准横屏collection-details/history-selected-restored.png仍是读取中，尽管native已等待模型points>=2：截图可能在新帧提交前取到旧帧，不能当有数据视觉证据。增加API35硬件绘制帧提交回调等待，再截图；保留样本和原布局门槛，5秒超时失败，不以任意固定sleep掩盖。此截图仍未验收。

be3a538用户平板报告确认336页面通过，但历史恢复阶段chart434/1402违反原40%门槛；不是列表位置恢复错误。根因是集合详情再套TaskWorkspace，右栏可用宽不足624dp后额外堆叠支持区。对照history-list-v3改为完整宽度图、图下豆子/感受两行摘要和同一操作栏，完整字段/原因/流量明细仍由原详情对话框提供；全屏入口合并到操作栏而非顶部独占一行。保留原40%和14恢复断言，使用已失败CI作为回归证据，不放宽阈值。截图帧同步测试已本地编译23秒通过，原生仍待验证。

全宽图候选本地验证：testMockUnitTest + assembleMock + assembleMockAndroidTest成功45秒，297 tests / 0 failures / 0 errors / 1既有skip；git diff --check通过。三横屏原生截图与字体断言待新CI，不称已修复实屏。
