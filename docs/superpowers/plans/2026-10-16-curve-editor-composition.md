# 曲线编辑页专用横屏布局

按已确认 curve-editor-v1.png / 整合规格实施。继续使用 writing-plans 与 executing-plans，在当前隔离工作树执行。

- 来源develop 66ebda9 + ed01de0；feature/task-first-ui → develop，现有草稿PR #4。
- 新建 TaskCurveEditorForm.kt：顶部三个基本参数容器，下方阶段编辑局部滚动及右侧预览；独立保存/恢复滚动、焦点与选区。保留原EditText和草稿key，不修改共享TaskFormSections。
- CurveEditorActivity仅替换布局接线，阶段数量仍1–4、同一Spinner；压力/水量并排，预览在右栏，保存用有限宽TaskActionDock。原解析、校验、存储、stageCount和draftId不变。
- 更新ChineseFormWindowChecks按实际字段检查编辑页，不再要求已移除的分组跳转Spinner；保持六组/四阶段、IME、长中文、焦点、选区、恢复与保存可达检查。AdvancedBusinessUiChecks四阶段检查以真实可见阶段字段取代旧Spinner数量，保留数据断言。
- 验证：Mock与instrumentation构建；CI用户平板/大字号全屏检查、advanced全字段保存恢复；正常四阶段实际截图与概念稿核对。现有截图已证明旧构图失败；本轮只改呈现，不新增业务。
- 普通推送当前分支，不合并、不发版、不安装、不操作硬件；签名、版本、schema、设备协议不变。追加revert回退，保留用户数据和未知记录。
- 未完成：新源码CI/截图审阅，整体21+3页验收。
