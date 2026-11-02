# 分享页面板与次要操作层级

f41b781平板浅深实图已确认左右结构，但导出/详情仍过重且缺少概念稿curve-share-v1轻边界。继续已批准方向：预览和操作面板16dp内边距、12dp圆角与1dp边界；右面板填满可用高度，内容多时仍局部滚动。分享文本主按钮、复制及文件分享按钮不变；导出和详情为48dp以上文本按钮，保留焦点框、涟漪及禁用色。

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4。修改CurveShareActivity/CurveSharePreview，新增TaskTextAction；TaskWorkspaceLayout仅新增默认false的填满辅助区选项，只有分享页开启。测量预算扣除面板padding，防止错误显示表格挤掉图。

验证Mock/test APK、既有single-screen与文档分享/选择器契约；最终同状态截图复核面板比例、字体/间距、短高和大字号。本次不变更导出/URI/权限/保存逻辑，不发送外部分享。普通push，不安装、不发布、不合并；追加revert回退保留数据。
