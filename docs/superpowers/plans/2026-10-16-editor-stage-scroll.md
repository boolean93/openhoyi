# 编辑器阶段表高度修复

baf1155 single-screen38884756522：用户平板中文浅/深色第四阶段EditText117px只可见107px；font1.3中文浅/深色IME时阶段表头text layout34px仅15px内容高度。原断言继续生效，不能归为CI噪音。

- 来源develop66ebda9+ed01de0；feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。
- CurveEditorActivity元数据标签保持14sp，仅上下留白由8/6dp改4/4dp，回收6dp，保留16dp卡片内边距及18sp/52dp输入。
- TaskCurveEditorForm将阶段数量行与列标题移入同一阶段ScrollView，避免短高时固定标题挤压被AT_MOST测量裁切；四阶段行和字段键、选择器身份不变，阶段区域仍局部滚动。
- 原用户平板11字段/选择器全显、IME文字布局断言及新图注/操作全显断言保留。编译Mock/test APK；新CI前不宣告运行修复。
- 无解析/库存/协议/schema/签名变动；仅普通推送，不合并/安装/发版/设备操作。回退追加revert保留数据。
