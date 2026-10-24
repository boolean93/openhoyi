# UI 取证修正合批验证

本批在 `feature/task-first-ui`，PR #4 → `develop`；基础提交 `58ae69ed99c1bbde98311fed24c8bf9c056b9134`。遵守 `docs/git-workflow.md`。仅测试取证与验收记录；未安装、操作设备、合并或发版。

## 实现与验证

- Settings：同一次主线程观测内按真实TextView身份复用，全部原字段/展开次数保留；3项离线检查通过。Android synthetic待CI，不冒称已执行。
- M7：等待真实Toast hidden回调，原10秒及reader确认链保留；8项生命周期及4项轴合同通过。模型不等于Android内存验收。
- M5：Extraction单个case.json写点复用已有strict writer，writer不变。真实STARTING子对象23,318,106字节与SDK34原序列化逐字节相同，SHA `997428dbec51241e4f0cff86d645e76de98ec6cb349bf9f7f9f2ecac352720ae`；同树192MiB解析及流式写出匹配。实际CI OOM仍待新运行关闭。
- M2/Import：保留原解析顺序/短路和严格恢复断言，只补失败阶段、当刻计数/身份和原异常。原14 schema+1 lifecycle及新增分页面resolver模型直接执行通过；原恢复问题未关闭。

## 根构建

首次构建在 `ImeViewportTransitionProbe.kt:40` 报 `Unresolved reference phase`，日志保留 `build/task-first-evidence/ui-2026-10-24-batch/build.log`。仅将构造参数保存为private成员后重建。

`:mobile:assembleMock :mobile:assembleMockAndroidTest` 第二次退出0，37秒，91 tasks（4 executed）。日志同目录 `build-retry.log`。303个Kotlin源文件构建前后SHA一致，见 `source-retry-before.json` 与 `source-after.json`。

- Mock APK SHA：`11216d3d173e24392f36fe231a10b97ae1952af15b26424a483e923c1e876ccd`。
- 测试APK SHA：`f336154d86fa9a56d5d57a749ba97ebb5c04e420bfd843a251f581598cdd79c5`。

本批未改产品UI；既有视觉验收范围见对应M6/M8和曲线库记录。构建成功不能关闭24页全量G1–G4、IME恢复或硬件验收缺口。失败原件保留；回退以新提交撤销本批对应测试改动，不清数据、不重写历史。
