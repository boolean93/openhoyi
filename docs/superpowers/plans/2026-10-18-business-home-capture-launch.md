# 业务截图首页启动超时修正

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。831aa1c advanced run39092501613业务任务十分钟超时：最后业务标记dose-invalid-no-write，截图已至dose-pending-confirmation；系统日志随后START HomeActivity flg0x10000000 result=3，activities仍topResumed BeanPreparationActivity。captureHomeDose调用startActivitySync(NEW_TASK)等待新首页，但既有首页任务被复用而没有新Activity，监测未返回。不能当产品崩溃或放宽总超时。

仅该截图helper改为从当前prep通过Activity.startActivity无NEW_TASK打开新的首页实例，注册monitor后10秒有界等待；检查实例不等于原home、同service/状态/库存不变，关闭后确认prep恢复焦点且未销毁。两处调用传当前prep。原业务写入/确认/幂等/撤销/清理断言和paths计数不变，不绕过任何页面。AndroidTest构建、diff，后续CI需证明能跨过首页并执行后续路径。无协议/设备/签名/安装/合并/发版，合批推送，回退仅helper。
