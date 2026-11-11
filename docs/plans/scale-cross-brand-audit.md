# 电子秤跨品牌链路审计

审计基线：`develop@31890d58`，包含 PR #55 的通用读数新鲜度修复（本地摘取为 `bcea5f8b`）。本轮工作分支为 `fix/scale-data-unification`。下述能力是代码与协议证据，不代表本次已完成实机验收。

## 根因与修复边界

最早产品仅使用 BOOKOO，后续已经引入 `ScaleObservation`、`ScaleCapabilities` 与品牌协议 adapter，但业务迁移不完整：通用实时读数与旧 `BookooSample/weightAt` 同时存在。新品牌能显示重量，不代表启动门禁、结束记录、诊断包和 Mock 都读取了同一来源。

本轮将业务数据统一为 `ScaleObservation`，品牌差异留在协议、GATT、初始化、能力及证据层，不伪造 BOOKOO 数据，也不把“能读重量”直接等同于“能安全按重量停机”。

| 已确认问题 | 影响 | 处理方向 |
| --- | --- | --- |
| 历史终重两条结束路径读取旧 BOOKOO 字段 | 非原始 BOOKOO adapter 的正常萃取终重缺失 | 两条路径共用有效、新鲜的通用读数；只读秤也可记录，0 和负值不误删；过期或断连留空，不补 0、不倒填旧记录 |
| Mock、界面和采样仍保留旧数据回退 | 测试看似通过但未覆盖真实新品牌链路，后续易再漏迁 | 删除业务旧字段与回退，Mock 直接产生通用合成观测，保持模拟证据与真机证据分开 |
| Lab 丢失扫描协议 ID，只监听旧重量回调 | 非 BOOKOO 可能按错误协议连接，或收不到重量 | 扫描、连接、保存、恢复显式携带协议 ID；Lab 读取通用观测 |
| 选秤列表统一显示 documented 无目标液重 | Felicita、DiFluid 的能力提示与实际及连接后页面矛盾 | 按 adapter 的目标液重能力选择既有提示，不改变控制资格 |
| 非 DiFluid GATT 在属性过滤后才判唯一 | 重复 UUID、不同属性时可能选错特征 | 先判原始 endpoint 唯一性，再判属性；驱动实际解析也拒绝 service/characteristic 歧义 |
| GATT close 异常被吞掉 | 单秤槽未能落实关闭不确定时禁止创建下一 owner | 粘性记录关闭不确定，仍完整结算队列；禁止该 owner 重开；仅在 owner 关闭边界报告，使槽阻止替换，不把异常直接抛入普通 tick/断连回调 |

新连接 API 不再默认 BOOKOO。只有明确的旧存储迁移保留“已保存地址但没有协议 ID → BOOKOO”，未知或空协议仍拒绝。内部 `DeviceRole.BOOKOO` 是历史兼容角色名，不据此选择协议，也不在本轮机械重命名持久化/诊断字段。

## 能力矩阵

“启动前归零”是按目标液重启动的必需确认步骤；“启动后归零”是无目标萃取的自动动作，两者不能混同。

| 协议 | 实时克数 | 手动归零 | 目标液重 | 目标启动前归零 | 无目标启动后归零 | 称豆稳定采集 |
| --- | --- | --- | --- | --- | --- | --- |
| BOOKOO 原始协议 | 是 | 是 | 是 | 是 | 是 | 是 |
| BOOKOO Ultra legacy | 是 | 是 | 否 | 否 | 否 | 否 |
| Felicita ASCII legacy | 是 | 是 | 协议级支持 | 是 | 否 | 否 |
| Felicita 旧只读 | 是 | 否 | 否 | 否 | 否 | 否 |
| TIMEMORE DOT / Basic3 Link | 确认克单位后 | 是，需 ACK 与后续零值 | 否 | 否 | 否 | 否 |
| TIMEMORE 旧只读 | 是，含次传感器 | 否 | 否 | 否 | 否 | 否 |
| Acaia A legacy | 确认单位后 | 是 | 否 | 否 | 否 | 否 |
| Acaia AP legacy | 确认单位后 | 否 | 否 | 否 | 否 | 否 |
| DiFluid EE / DD | 带单位或已确认克单位后 | 是 | 协议级支持 | 是 | 否 | 否 |
| 离线候选 | 仅离线解析 | 否 | 否 | 否 | 否 | 否 |
| Mock | 合成数据 | 不执行真实归零 | 仅模拟流程 | 仅模拟流程 | 无真实操作 | 否 |

所有在线协议的有效新鲜重量均应进入显示、曲线采样、历史和导出，不按目标控制资格过滤。目标控制能力、称豆 VERIFIED 门槛、单位核验、新鲜度、近零确认及未知操作不得自动重试的边界不放宽。限制应在对应界面说明，不能只留下没有原因的灰按钮。

## 已核验、无需新增机制的路径

- 扫描结果按对应 adapter 连接；本代有效通知使会话 READY 后才保存选择。连接 ticket、generation、端点过滤和每代新 decoder 隔离旧回调。
- 过期读数不显示为实时值、不允许按目标液重开始。READY 后静默已有“断开 → 重试”恢复路线，保留地址和协议 ID，真实创建新连接；活动萃取及未知结果门禁仍有效。
- 不增加全品牌固定周期 watchdog。泰摩协议明确 active 状态每 100 ms 上报，但不能推广至所有型号。DiFluid 的 auto-send 开关不等于静止时最大间隔保证。
- 归零写成功不等于物理归零成功；必须等后续近零样本，泰摩还需 ACK；未知结果不重发。

周期证据：[TIMEMORE 固定厂商协议](https://raw.githubusercontent.com/TIMEMORE-COFFEE/open-scale-protocol/e31dce5087494ea3d572a89e689e48dc014eee2c/Timemore%20Black%20Mirror%20SCALE%20BLE%20Protocol.md)、[DiFluid 固定协议](https://raw.githubusercontent.com/DiFluid/difluid-sdk-demo/df4e0731dc646b9110479cb3b39bb69346e0a640/docs/protocolMicrobalance.md)。其余品牌的已核对材料不足以证明全部型号固定周期；因此本轮不以推测强制断连重连。

补充参考：[BOOKOO 厂商协议](https://raw.githubusercontent.com/BooKooCode/OpenSource/main/bookoo_mini_scale/protocols.md)说明克单位、帧格式和工作模式，但未承诺固定周期；[Felicita 固定实现说明](https://raw.githubusercontent.com/graphefruit/Beanconqueror/b975d39319588c80b47f0531b207e00df55c8743/src/classes/devices/felicita/felicita-readme.md)提供通知样本，属于实现作者资料而非厂商全型号保证；[Acaia 固定开源实现](https://raw.githubusercontent.com/gaggimate/esp-arduino-ble-scales/180070fda5a1c795d9b73d6579b172ff5149e4cb/src/scales/acaia.cpp)提供解析、单位和连接流程参考，同样不当作厂商全系列验收。

## 验证与交付

实现、测试命令与独立审查结果记录在同目录 `scale-data-unification.md`。应覆盖真实 adapter 通知进入通用业务、两种结束历史、归档/采样、Mock、协议选择，以及 GATT 歧义和关闭不确定回归，不能仅用手工构造 BOOKOO 快照证明兼容。

本次不使用模拟器、不安装、不执行真实萃取，不冒充多品牌实机验收。新型号的真实连接、归零、持续上报和断线恢复仍需要对应硬件证据。

初始阶段仅本地审计。用户于 2026-11-11 补充授权：“好的。可以带着前面这些所有的修复创建pr并合并。”因此既有 #56、#55 按各自审查与必需检查顺序合入，本分支随后基于最新 `develop` 排除重复 #55 提交，单独创建整体审计修复 PR；仍须通过本地入口与精确头提交的远端必需检查，不包含安装、真实萃取或手动发版授权。
