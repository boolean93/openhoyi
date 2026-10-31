# 曲线编辑阶段分段选择

依据已认可curve-editor-v1，继续现有feature/task-first-ui（develop66ebda9+ed01de0）→develop PR#4，遵守docs/git-workflow.md。

- 新CurveStageCountTabs，用四个等宽按钮替换Spinner。数字16sp，48dp最小点击区，当前项accent实色/白字；焦点边框与涟漪保留，辅助描述沿用本地化阶段名称。容器192dp，与标题同一行，不新增垂直空间。
- CurveEditorActivity初始化当前段数，不触发额外回调；用户选择改变stageCount并调用原visibility/previewChanged。原四阶段字段实例、保存/恢复和验证不变，不删除隐藏字段内容。
- AdvancedBusinessUiChecks改为真实按钮点击，继续检查四阶段持久化与恢复。ChineseFormWindowChecks点击第四段及恢复原段数，检查四按钮完整且至少48dp，原11输入、IME及预览动作全显断言保留。
- 构建Mock与instrumentation APK，检查diff/分支；CI再验证窗口和业务。没有新图不宣称视觉通过。
- 普通push现有PR，不安装/设备操作/合并/发版；无协议/schema/签名变化。追加revert回退，不清用户数据。
