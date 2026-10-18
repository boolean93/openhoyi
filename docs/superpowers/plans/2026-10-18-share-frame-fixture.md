# 通用分享截图传入现有曲线

沿用feature/task-first-ui→develop PR#4及docs/git-workflow.md。4d308a7分享页截图缺CURVE_ID，错误态不用于正常页验收。复用AdvancedPageChecks的只读来源选择：找已有可转换文档的采集曲线，传原ID，不新增或保存数据。断言标题含真实曲线名、复制入口存在且未导出文件不可分享；输出只读来源ID和阶段数，曲线store前后相同。不读取剪贴板、不点击分享/复制/导出；原错误/业务流程测试不变，四阶段完整业务夹具仍由AdvancedBusinessUiChecks覆盖。AndroidTest构建和diff，待CI实图。无设备/签名/安装/发版；下批推送，回退仅测试启动参数和断言。
