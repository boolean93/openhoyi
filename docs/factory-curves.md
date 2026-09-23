# 工厂曲线导入与控制边界

旧版 UniApp 的 `app-service.js` 模块 `0ffc` 内置100条 `factory: true` 曲线，深烘、中烘、浅烘、超萃各25条。它们不依赖服务端更新。旧版启动时的 `normalizeFactoryChartFlow` 会修正总水量和最后一段水量；Alpha 资源保存的是这一步之后的结果。

`scripts/extract_factory_curves.py` 只提取模块中的数组字面量，用 Node VM 单独解析，不执行整个旧版应用。它检查数量、分类、字段及类型，按原顺序生成 `mobile/src/main/assets/factory_curves_v3.tsv`。资源首行记录原 bundle 的 SHA-256；ID `factory-v3-001` 至 `factory-v3-100` 稳定。一次性将100条导入结果与旧版函数运行结果逐条比较，差异为0。

`FactoryCurveCatalog` 在运行时严格校验字段数量、ID顺序、分类数量及各段水量之和；`CurveLibrary` 将工厂曲线与3条采集曲线分开。工厂曲线的 `controlProfile` 仍为 `null`，由 `FactoryCurveAdapter` 根据当前电子秤状态临时生成控制参数。`scripts/generate_factory_wire_oracle.py` 只执行旧版 `startTempChart` 和依赖的编码函数，分别生成有秤、无秤两套启动帧，100条共200帧；生成结果保存在 `factory_wire_v1.tsv`，关联旧版 bundle SHA-256。运行时 `FactoryWireProof` 对每条曲线的两种帧逐字节检查，缺文件或有差异即关闭工厂曲线启动入口。通过校验的帧集合经 `MobileService → NativeDeviceHub → DeviceSession` 传到最终写入门禁；会话层仍拒绝集合之外的启动帧。服务层发送前再次检查已选ID、完整参数、帧和确认时的秤连接模式。

旧版连接电子秤时，最大水量按目标重量扩大：`max(flowMl, ceil(4 × weightGram))`；无秤时使用曲线水量，且不按重量停止。当前实现分别验证这两种报文；进入启动确认时展示模式、目标重量和最大水量。报文等同旧版不代表机器实测等同，工厂曲线仍待人工监督下的实机验收。旧版 `hy_chartLib` 用户曲线迁移和曲线编辑未完成；旧版私有数据不能从不同包名的 Alpha 直接读取，未来需用户主动导出/导入。

旧版首页五个槽位调用 `startChart(slot, 1)`，与曲线库的临时槽位7不同。相同脚本另外生成 `factory_slot_wire_v1.tsv`：100条工厂曲线 × 5个槽位 × 有秤/无秤，共1000条旧版启动帧。运行时逐条核对后才把许可集合交给 `DeviceSession`。本地快捷槽位默认对应前五条工厂曲线，可在曲线库改为任意工厂曲线；点选槽位只进入确认页，不直接写机器。停止报文沿用本次启动槽位，历史记录也保存槽位。五槽位仍待实机验收。
