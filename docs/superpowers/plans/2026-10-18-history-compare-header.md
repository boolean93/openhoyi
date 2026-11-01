# 历史详情对比入口归位

沿用history-detail-v2，feature/task-first-ui→develop PR#4及docs/git-workflow.md。用户平板0d6fb9c history-detail-filled-top-fixture.png的对比按钮占右侧记录首行，稿件为标题栏。HistoryDetailActivity保留现有header，标准宽屏≥1000dp/font≤1.1作为renderHistoryDetail的可选comparisonHost传入；参数置于openFullPage回调之前，保留集合页末尾lambda调用契约。仅该host存在且记录可复盘时把同一对比动作放入header，maxWidth200dp、原文字/shotId传参，其他布局保留原leading位置。字段、chart和控制/历史存储不改。

验证Mock及AndroidTest构建、diff，保留实际历史业务与多语言/大字测试；新实图核对标题及右侧起始位置，不将编译等同验收。不安装/合并/发版/签名变化，合批普通push，回退追加revert。
