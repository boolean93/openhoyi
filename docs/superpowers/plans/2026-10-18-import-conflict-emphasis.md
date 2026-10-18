# 导入冲突警示层级

沿用 develop66ebda9+ed01de0 → feature/task-first-ui → develop PR#4，遵守 docs/git-workflow.md。使用已认可 curve-import-preview-v1，不生成新稿追认差异。

b6fc53c 的实际 import-conflict.png 中冲突提示只是右栏普通黑字，未呈现认可稿浅暖色警示区域。仅给现有冲突文案加入语义浅暖色背景、边框、16dp内边距和底部16dp组间距，20sp标题保持；深色采用独立配色。无需靠颜色单独传达状态，原明确文案仍完整保留。无冲突时不显示。

不动保存/保留/查看/冲突判定及数据持久化，不移动底部安全操作、不减少已有业务断言。验证XML解析、Mock与test APK构建、diff与branch-policy；现有业务fixture产生新的冲突截图后再验实图。浅深色/大字号和用户横屏均仍在原验收范围。无协议、签名、版本/schema、安装、设备写入、合并或发版；普通push，追加revert回退。
