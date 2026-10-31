# 图表说明自然换行

沿用develop66ebda9+ed01de0来源feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。bcda211的gaps-and-reference原生组件实图中，14sp说明的强制三行及参考说明占用过多绘图区。保留每条原文、参考身份、缺口/重置语义和14sp字号，将说明及参考段分别用中点连接，自然换行，不截断、不隐藏、不改轴/数据/无障碍说明。

验证Mock/AndroidTest构建和diff；原四指标夹具及真实萃取截图CI继续检查绘图区/缺口/当前值，不修改门禁。只有新图证明空间改善后才能关闭差异。与机器设置/底部取证整批推送。无设备/安装/合并/发布/版本签名变更，回退两处joinToString即可。
