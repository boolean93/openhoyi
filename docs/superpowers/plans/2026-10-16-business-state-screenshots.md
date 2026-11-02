# 业务状态截图补全

沿用feature/task-first-ui（develop66ebda9+ed01de0依赖），目标develop PR #4；遵守docs/git-workflow.md。不改应用代码、协议、签名或schema，不安装实机、不合并、不发版。普通推送触发现有CI，回退追加revert，不清用户记录。

- 在AdvancedBusinessUiChecks既有流程内采集复盘已保存记录、导入无冲突和冲突三张截图；每次截图先断言当前窗口及预期操作存在，避免空态充当正常态。
- 使用既有Mock自有fixture及现有业务断言，不新增或删除曲线、库存事件与记录；冲突保留/副本流程照常执行。
- 输出到advanced-page-screenshots/business，由既有collect上传；脚本核验三个PNG存在且为1600×1000。截图不改变既有17路径/完整字段断言。
- 构建测试APK、bash -n和diff检查后推送；CI成功后实读截图，分别记录状态、尺寸、主题、字体范围。仅中文浅色标准字号，不冒称用户平板或大字号完成。

已实施采集和文件校验；测试APK构建24秒成功（/tmp/task-business-screenshots-build.log），bash -n与git diff --check通过。原生执行及实际截图审读未完成。
