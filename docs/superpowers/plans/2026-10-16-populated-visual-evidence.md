# 正常态截图与字体测量配对

用户要求开发与认可图在字体/间距一致，task-frame仅无extras打开页面，复盘等为缺失记录态，不能证明正常态。沿用single-screen现有命名Mock记录/豆子/曲线fixture，不增加或替换用户记录，在中文浅深色正常态（曲线/历史完成已有选中步骤后、表单IME操作前）导出同一窗口的PNG与TaskTypographyEvidence测量，按语言主题页面单独目录保存。

遵守docs/git-workflow.md，develop66ebda9+ed01de0依赖、feature/task-first-ui→develop草稿PR#4。保持原336检查与失败条件、固定字体身份断言；每个页面等待真实绘制后捕获，失败截图仍保留。只增加证据，不把文件存在当视觉验收，不改变数据/机器/应用代码。需编译测试APK并由CI导出实物；原生图仍必须与当前认可版本同状态对照。不发版安装合并，普通push/PR与revert回退。

新增采集双主题正常态测量的测试APK编译23秒成功，diff check通过；原生文件尚未生成，不宣称配对截图已交付。现有数据/状态基线和336页面断言保持。
