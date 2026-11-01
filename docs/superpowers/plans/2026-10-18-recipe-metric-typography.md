# 配方数字与单位统一层级

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。831aa1c typography证实预览/分享的数字与单位均54px=24sp/700，概念图单位为辅助层级。新增共享RECIPE_VALUE_SP=24；CurveMetricTypography生成Spanned文本，数字保留24sp/700，末尾单位14sp/400（共享SUPPORTING_SP和固定字体），兼容API26，不改变原字符串/数值格式/无重量文案。曲线库、导入预览、分享三处复用，替代曲线库原0.6比例产生14.4sp。无BLE/状态/保存/导出改变。Mock/AndroidTest构建、diff；后续typography metricRuns与实图验证，不能凭源码称最终一致。不安装/合并/签名/发版，本地候选下批推送；回退共享helper及三处调用。
