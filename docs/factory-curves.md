# 工厂曲线导入与控制边界

旧版 UniApp 的 `app-service.js` 模块 `0ffc` 内置100条 `factory: true` 曲线，深烘、中烘、浅烘、超萃各25条。它们不依赖服务端更新。旧版启动时的 `normalizeFactoryChartFlow` 会修正总水量和最后一段水量；Alpha 资源保存的是这一步之后的结果。

`scripts/extract_factory_curves.py` 只提取模块中的数组字面量，用 Node VM 单独解析，不执行整个旧版应用。它检查数量、分类、字段及类型，按原顺序生成 `mobile/src/main/assets/factory_curves_v3.tsv`。资源首行记录原 bundle 的 SHA-256；ID `factory-v3-001` 至 `factory-v3-100` 稳定。一次性将100条导入结果与旧版函数运行结果逐条比较，差异为0。

`FactoryCurveCatalog` 在运行时严格校验字段数量、ID顺序、分类数量及各段水量之和；`CurveLibrary` 将工厂曲线与3条采集曲线分开。工厂曲线只携带展示元数据，`controlProfile` 固定为 `null`。即使用户设为当前曲线，`ExtractionActivity` 也禁用启动；`MobileService.startShot` 仍只接受 `CurveCatalog` 中的三条采集曲线，并再次校验已选ID和启动帧。

尚未完成：旧版 `hy_chartLib` 中的用户曲线迁移、100条工厂曲线与真实下发报文对应关系的验证、工厂曲线编辑和萃取。旧版私有数据不能从不同包名的 Alpha 直接读取；未来需用户主动导出/导入。此导入仅解决原生曲线库的本地浏览。
