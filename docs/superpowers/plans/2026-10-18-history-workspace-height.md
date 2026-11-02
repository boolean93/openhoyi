# 历史详情图高与笔记分区

沿用develop66ebda9+ed01de0 → feature/task-first-ui → develop PR#4，遵守docs/git-workflow.md。依据history-detail-v2与63a490c实际结束截图。

根因是独立详情用TaskWorkspaceLayout且复盘dock在body末尾，左图也被扣除整行操作高度。复用实时页已有TaskExtractionWorkspace，复盘dock固定在支持栏下方；左图使用剩余完整高度，右栏笔记局部滚动，短高回退沿用现有布局策略。集合内预览不改。为右栏三个参数行和后三项笔记加1dp语义边线，保留原文字/间距/完整信息入口，不删未填写字段。

仅展示变更，无数据/采样/状态/库存/启动逻辑改变。已有实际图作为几何差异证据，构建Mock/test APK、diff/branch-policy；既有全页面/大字号/多语言/图最小高度检查保留，新截图确认左图延伸到操作区旁、固定按钮完整才关闭该差距。无安装/机器操作/签名版本schema修改/合并/发版，普通push，追加revert回退。

运行补证：712913e/run39085501557用户平板深色history-detail-dark.png已读取，主单位缩小/图表延伸页底/右侧分隔均已生效；非有复盘内容状态，不代表整页最终验收。
