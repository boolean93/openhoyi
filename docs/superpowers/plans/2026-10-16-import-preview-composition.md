# 导入预览构图调整

以curve-import-preview-v1和视觉审计为准，左侧展示配方标题、参数摘要、阶段图和完整参数入口；右侧展示本地冲突及决策。无冲突时不伪造警告。主保存操作独立限宽，次操作与冲突说明就近。

沿用feature/task-first-ui，基线develop66ebda9+ed01de0依赖，PR #4目标develop；遵守docs/git-workflow.md。不合并、不发版、不安装实机，不改签名/schema/机器协议；回退追加revert，不删除记录。

- CurveImportPreviewActivity使用TaskWorkspaceLayout代替通用profileCard，避免分享/本地预览弹窗被本页布局牵连。左侧24sp标题、14sp指标标签/24sp数值，阶段图仍用CurveStageView；完整参数仍经profileSummary和原始文档查看，未支持流量不画压力图。
- 右侧保留现有冲突文本、本地预览、保留本地；保存/另存副本使用TaskActionDock。finishedSave、防重复点击、保存失败恢复及写入时再次检测冲突的分支保持原样。
- 构建双Mock APK、diff检查，再以既有advanced业务17路径、正常/冲突截图及single-screen多语言/字号验证。未看到新截图前不宣布字体间距对齐。小高度下保留参数/所有操作可达，不增加竖屏交付。

候选已实现，双Mock APK40秒成功（/tmp/task-import-composition-build.log），git diff --check通过。原生验证待执行。内嵌阶段参数表尚未实现，完整参数目前仍为详情入口；记录为视觉差距，不以这一版代替完整认可稿验收。
