# 曲线分享页构图修正

按已认可的 curve-share-v1.png 与整合规格实施，不改变设计方向。使用 writing-plans / executing-plans 流程，在当前已隔离工作树直接执行。

- 来源：develop 66ebda9 + ed01de0 依赖；当前 feature/task-first-ui，PR #4 → develop。
- 范围：CurveShareActivity 的预览和分享操作布局；新增 CurveSharePreview.kt 承载左侧温度/目标重量、阶段图和压力阶段表。保留通用 profileCard，避免改变其他弹窗。
- 步骤：用 TaskWorkspaceLayout 替换页面底部动作矩阵；右侧按分享文本、复制、导出、分享文件排序。新增八语言“分享方式”和导出前提示。保留原 onActivityResult、文档快照、URI 恢复、JSON、ClipData 和授权标志。
- 左侧：24sp 标题，14sp 标签，24sp/700 数值，16dp 纵向分组；图表与表格共用现有单位和精度。短高度下优先图表，完整参数入口始终保留。
- 验证：构建 Mock 与 instrumentation APK；既有 advanced 复制业务、文档契约/系统选择器测试与 single-screen 用户平板/大字体 CI；检查同状态分享页截图，不能把构建成功当视觉一致。
- 推送：只提交本次文件及此前本任务证据，普通推送 feature/task-first-ui；不合并、不发版、不安装、不操作硬件。不变更签名、版本、schema、设备协议或未知记录。
- 回退：追加 revert，保留数据。未完成：精确新源码截图与完整21+3页视觉验收。
