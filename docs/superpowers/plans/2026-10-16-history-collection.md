# 历史记录列表详情实施规划

已认可规格§7 History/F5；横屏优先，21页范围不变。遵守docs/git-workflow.md，develop66ebda9/ed01de0依赖 → feature/task-first-ui → develop草稿PR#4。仅普通推送，不合并/发版/安装设备。追加修复或revert，不清数据。

1. 抽取HistoryDetailActivity的已有展示为ThemedActivity共享渲染方法。独立详情与列表右栏复用全部图、真实状态/未知提示、复盘/对比、完整来源/流速明细。异步样本返回只更新仍挂载的当前图，不覆盖后选记录。
2. HistoryActivity接TaskCollectionLayout。左栏保留日期分组、三来源和所有原导入导出迁移入口；右栏选中记录并提供全屏详情。按来源保存选中ID，重建恢复；删除/来源不可用清理选择，不猜记录。列表当前位置在进入详情前保存。窄高度回退时提供返回列表，系统返回同义。
3. SingleScreenListStateChecks继续真实滑动和重建检查，历史条目点击改为先选中右栏再显式全屏详情；增加选中记录恢复断言。不放松原位置/偏移/所有权检查。
4. 本地完整JVM与两MockAPK构建，精确源码横屏CI与实际图表截图验证。独立审查待执行；没有图像/运行证据不宣称全部完成。

文件：HistoryActivity.kt、HistoryDetailActivity.kt、新HistoryDetailContent.kt、SingleScreenListStateChecks.kt。无协议、库存、历史持久化schema或设备动作改动。

追加CI证据分类修正：922752c用户横屏336场景和14列表恢复原生通过，后置PNG总数为338，新增inventory-selected-restored.png与legacy-curve-selected-restored.png混入原336页面统计。脚本将这两个明确命名恢复证据单独强制存在并检查PNG，其余仍严格336，其他64/44+/2/6门槛不变。实际下载artifact运行精确修改后校验通过。
