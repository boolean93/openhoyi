# 阶段预览与参数表的颜色一致性

按已认可curve-editor-v1 / curve-share-v1 / curve-import-preview-v1实施，不重新生成图。来源develop66ebda9+ed01de0，当前feature/task-first-ui，PR#4→develop。使用现有规划/执行流程。

- 新增CurveStagePalette及浅深主题四阶段色，统一青/蓝/橙/紫；不复用实时曲线指标色语义。阶段序号和数值保持，颜色不是唯一标识。
- CurveStageView各阶段用各自颜色绘制水平目标线、点、读数；非compact图加入低强调分区背景。保持0–12bar、输入0–120十分之一bar、最多4段、横轴阶段而非时间和无效数据处理。
- 参数表首列和编辑页阶段标题同步阶段色，不改变值/精度/导入导出/保存。
- 更新LanguageChartChecks原先只识别单蓝色的像素检查：识别四阶段各自颜色，并逐段确认对应列有真实像素，继续保证LTR/RTL科学顺序上升。原实时/历史压力图单色检查保持。
- 验证：XML解析、浅深色文字对比度、Mock/test APK构建、既有多语言Canvas渲染与截图CI。具体视觉仍需同状态实际图；编译不证明通过。
- 普通推送feature分支；不合并、不发布、不安装、不碰硬件。签名版本schema和协议无变化。追加revert回退，数据保留。
