# 曲线编辑器分组层次

依据已批准curve-editor-v1及229f798实际四阶段截图；不重新生成图追认偏差。

- develop66ebda9+ed01de0来源，feature/task-first-ui→develop现有PR#4，遵守docs/git-workflow.md。
- TaskCurveEditorForm的三元数据区、阶段区、预览区使用既有surface/border/12dp圆角及16dp内边距，保留16/24dp组间距、字段字号、稳定字段键和局部滚动。保持metadataScroll实际测量上限，不使用固定元数据高度。
- CurveEditorActivity将预览、完整规则入口放为同排文字按钮，保留48dp触摸和焦点反馈；保存仍为唯一强调操作。保留原解析/实时更新/保存/恢复/错误反馈。
- Mock及test APK编译，现有标准用户平板四阶段全部字段可见检查和横屏IME/字号1.3检查继续执行。检视新CI实际截图后才能声明对齐，不能仅凭编译。
- 普通推送现有分支，不合并、安装、发版或硬件操作；无协议/签名/schema变动。回退追加revert并保留用户数据。
