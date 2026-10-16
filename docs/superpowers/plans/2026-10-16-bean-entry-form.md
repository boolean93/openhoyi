# 入库表单构图

依据bean-entry-v2、bean-entry-keyboard-v2及f41b781用户平板真实截图。沿用已批准方向。

- develop66ebda9+ed01de0来源；feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。
- TaskFormSections当前只由BeanEntryActivity使用。改为居中最大680dp的单卡表单，16dp内边距，删除重复组标题/跳转下拉框；保留三个真实分区和错误定位、局部滚动、字段焦点/选区保存恢复。
- BeanEntryActivity日期标签和输入同排，标签14sp/输入18sp，最小52dp输入；豆款/名称/重量保持真实控件。保存移入同卡底部限宽主按钮，不改数量、日期、豆款身份校验或库存写入流程。
- ChineseFormWindowChecks通过真实字段滚动定位三个分区，不再操纵已删除的跳转控件；保留所有可见性/IME/恢复断言。AdvancedBusinessUiChecks既有三日期持久化与无效输入检查不变。
- 编译Mock及test APK；运行CI后检查用户平板全表单、长文本、font1.3与键盘截图，编译不代替视觉通过。日期选择器/新旧豆款分段切换仍属于后续未对齐项，不生成新图追认。
- 普通推送功能分支，不合并/安装/发版/设备操作；无协议、签名、schema变化。回退追加revert，保留库存记录。
