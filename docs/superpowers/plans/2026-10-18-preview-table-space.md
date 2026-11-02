# 参数表空间：收紧相关内容间距

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。831aa1c正常预览/分享实图中表格被原高度预算隐藏，图却扩张；代码规定160dp图最小高度＋固定内容＋表格高度。先消除固定内容的额外占用：两页metrics上下间距由16dp改共享RELATED_GAP_DP=8dp；导入压力图可见说明复用已用于分享的八语言task_form_stage_caption，完整profiles_stage_caption保留为contentDescription。不改字号、表格行数、阶段数据、160dp阈值或高度断言；不强行隐藏正文或假称表格已恢复。原短高表格fallback/详情保持，正常四阶段导入及有数据分享夹具用于下一轮验证，四阶段分享仍需高级夹具。Mock/AndroidTest构建、diff；下批推送，无设备/协议/数据/签名/安装/发版，回退两处展示调整。

追加验收：TaskFrameChecks有效预览/分享图保存后记录table-space尺寸，并在≥1000×600dp、fontScale≤1.1的用户平板目标窗口要求参数表可见且边界完整。断言不接受详情入口替代表格；若失败按新实图继续修正，不放松门禁。
