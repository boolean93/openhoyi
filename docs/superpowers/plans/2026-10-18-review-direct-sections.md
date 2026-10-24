# 复盘直接填写与底部操作

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。依据复盘v3及63a490c已填写实图。

移除右上易误认为豆款选择的分组Spinner，五项始终存在、参数/记录独立滚动；校验错误仍能跳转定位，焦点/滚动/草稿保存不变。输入统一16sp，查看记录改文字操作，底部补取消。取消仅finish，原保存语义不变。中文长文本/IME检查改通过对应labelFor实际字段滚动，不减少五组/长文检查；既有复盘重新打开场景点取消验证notes不变。

Mock及测试包构建，后续CI截图和长文本/IME回归。无设备/安装/合并/发版，无版本/签名/schema变更；普通分支批次push，回退本批展示改动。

候选完成，Mock/MockAndroidTest构建45s成功（`/tmp/task-review-direct-sections-build.log`），diff/分支规则通过。五分组检查与长文/IME检查编译通过，运行仍待CI；不以移除Spinner减少检查覆盖。
