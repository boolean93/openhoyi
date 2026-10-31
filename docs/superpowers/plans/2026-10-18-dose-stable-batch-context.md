# 投粉确认保持批次上下文

> 使用 superpowers:executing-plans 在当前工作树执行。

**Goal:** 让保存选择、确认扣减后的界面继续保持认可稿的左侧批次列表、右侧本杯详情结构。

**Architecture:** 只改变 BeanPreparationActivity 的展示位置。已保存选择时左侧显示真实库存的只读列表，标记当前批次；右侧显示原本杯摘要、余额预览或真实流水与状态。不会新增可以绕过取消/放弃准备的批次切换。

**Tech Stack:** Kotlin / 原生 LinearLayout、ScrollView、TextView / HoyiUi。

来源 develop66ebda9 + ed01de0，沿用 feature/task-first-ui → develop draft PR#4；遵守 docs/git-workflow.md。不新增分支，不改其他人的文件。

- [x] BeanPreparationActivity 新增局部 showCurrentContext：左侧 dose_batch 标题及真实 inventory.batches 的名称/批次/当前余额；选中批次用 accent_soft，其他用 surface，16dp水平/12dp垂直内边距、8dp条目间距、至少72dp行高；只读TextView，不添加点击监听。
- [x] 两个已有current状态分支调用该展示，原 dose_summary 移至右栏卡片，原扣减/重试/放弃/关联分支代码保持不变。当前批次不存在时仍保留真实摘要，不造库存条目。
- [x] 现有 AdvancedBusinessUiChecks 在待确认和确认后核对当前批次的只读列表项、余额90/72g；与既有幂等/重建/无重复扣减断言一起执行。
- [ ] git diff --check、branch policy、Mock/instrumentation构建；随后CI实图验证左右布局与短高/大字号。编译不当视觉通过。

普通push当前feature，沿用PR，不合并/发版/安装。不改签名、版本、schema、设备协议或任何写入。代码revert可回退，不清除库存/未知记录。仍未完成的粉量步进器及固定操作区比例单独保留差异，不把本次局部调整称为整页完成。

本地验证：最终Mock/instrumentation BUILD SUCCESSFUL in 24s、进程exit0，日志/tmp/task-dose-context-build-final.log。diff whitespace与branch policy通过。新运行断言/截图尚待CI，不标整页验收完成。
