# 补齐旧版蓝牙秤兼容

- 需求与范围：用户在核查旧版迁移缺口后明确要求“补吧”。补 BOOKOO / ULTRA 发现、Felicita 显式去皮、Acaia A/AP 及 DiFluid EE/DD 的实时连接与克单位读数，逐项核对有证据的手动控制能力。旧型号/固件仍需实机验证，不把品牌名当全系列承诺。
- 来源分支/基线：功能最初来自 `origin/develop` / `3f9df29`；本轮依赖尚未合并的泰摩适配与多秤 owner 机制，从 `feature/timemore-scales` 的 `c0bd80e` 继续同一秤适配 PR。开始已检查并 fetch；PR #14 仍开放、目标 `develop`。不直接推送集成分支，不强推。
- 并发归属：开始时发现其他任务在改 `mobile` 下 ExtractionActivity、ExtractionFragment、MobileService、ShotGate 及 ShotGateTest，本任务不编辑、不暂存、不撤销这些文件。Lead 写会话/BLE/适配器与文档，复用协议工作者仅写新的 LegacyLiveScaleCodec 及其测试；实际 Astra 模型负责方案和最终关键路径的独立只读复核。
- 可观察验收：明确广播名被扫描并选对持久协议；只接受对应唯一 GATT 端点，不猜测或遍历写入；初始化后必须收到本代有效重量才 READY；支持有证据的分片、单位、小数和负值，损坏帧不刷新读数；周期心跳遵守单队列、连接代次与有限超时；显式去皮仍须后续新鲜近零通知才确认，未知结果不重发。
- 协议风险与边界：Acaia 两套 GATT 分开；AP 不臆造旧版不存在的去皮；DiFluid EE/DD 分开，不搬猜偏移和写失败遍历。Felicita 沿用严格 ASCII 克读数，写入仅去皮白名单；不复制旧解码 bug。新增秤不获得按重启动/自动停机或自动去皮资格；不远程启动秤计时，不改机器控制门禁，不自动补发未知命令。
- 来源：原始 HOYI 1.2.4 APK 已核对连接分支，raw JS SHA-256 `70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1`；旧流程核查见[此前记录](timemore-scales.md)。协议工作者补充校验、单位和重组来源；来源存在不等于实机验收，也不复制未授权第三方实现。
- 改动步骤：Astra 确认最小安全设计 → 独立协议编解码和小样本测试 → 会话/心跳/适配器接入及发现 → 代表性离线回归 → Astra 独立复核并关闭问题 → 更新 README 与既有 PR。
- 验证：优先协议、会话、BLE 相关检查与必要 Android 编译；不重复无关完整矩阵。共享目录若仍有他人未提交改动，构建结果不冒充提交源码的精确认证；必要时用隔离 worktree 验证本任务提交。精确 CI 结果按实际记录。
- 推送与合并：仅暂存本任务文件，普通推送同一功能分支、更新 PR #14；遵守[Git 工作流](../git-workflow.md)，合并仍需四项必需 CI，不自行合并。
- 发布/签名/升级：本轮不发版、不安装、不操作设备；不改包名、签名、版本或数据 schema。不以现有平板安装授权冒充新增协议硬件验收。
- 回退：追加 revert PR 或文档还原，不改写共享历史；保留旧协议 ID 的兼容选择，不把未知 ID 默认为 BOOKOO。不卸载、清历史或删除未确认操作记录。
- 当前进度：协议/会话/扫描实现、相关离线回归、Android Debug 编译与实际 Astra 最终独立复核通过；通过原 PR 提交。所有新增秤的实机验收仍待后续授权。

## 已实现的兼容与边界

| 发现名 | 持久化协议 ID | 接入/手动能力 |
|---|---|---|
| `BOOKOO` | 原 `bookoo` | 原能力不变；初始化不去皮 |
| `ULTRA` | `bookoo-ultra-legacy` | 复用 BOOKOO 字节协议，观察改为 DOCUMENTED，保留手动去皮但禁自动控制 |
| `FELICITA` | `felicita-ascii-legacy` | FFE0/FFE1，严格 ASCII 克通知，订阅后零写入；显式去皮仅 `54` |
| `PROCHBT` / `ACAIA` | `acaia-a-legacy` | 1820/2A80，确认克单位后重量；旧版 A 去皮帧 |
| `LUNAR` / `PEARL` / `PYXIS` | `acaia-ap-legacy` | 49535343 服务；8841 写/1e4d 通知；无去皮 |
| `Microbalance` / `difluid` | `difluid-ee-legacy` | 00EE 服务，AA02 写/AA01 通知，克通知与手动去皮 |
| `Mb Ti` | `difluid-dd-legacy` | 00DD 服务，AA02 写/AA01 通知，克通知与手动去皮 |

名字只用于发现，最终必须匹配唯一的服务/特征及属性；不会按失败遍历。`BOOKOO` 名称的原选择保留，只有 `ULTRA` 的旧分支走受限新 ID。所有新增协议 `transportVerified=false`、`documentedTransport=true`，不因品牌名继承 VERIFIED/自动去皮/目标重量控制。旧离线 `acaia`/`difluid` 候选继续离线，不升级已有未知记录。

旧 `felicita-ascii-read-only` ID 与禁写不变量保留，不静默迁移；用户重新扫描选择新协议才启用手动去皮。可写空初始化仍验证写特征；订阅成功只进入 SYNCHRONIZING，有效克通知后才 READY。旧 TIMEMORE 双通道只读机制不变。

A 初始化为 500ms ID、1000ms 请求通知、1200ms 心跳；AP 为 2500ms ID、5000ms 心跳、5500ms 请求通知。完成后固定 4500ms 心跳走同一 GATT 队列，每代至多一项 pending/in-flight，成功后按当前时钟加间隔再排，不补发错过的周期。失败/超时/断连停止，旧回调不能清除或重排新代心跳。只在初始化完成之后的 SYNCHRONIZING/READY 心跳；5 秒无有效读数的就绪期限不放松。

DiFluid 初始化仅订阅、设克与读单位，手动去皮仅 `DFDF03020101C5`；不控制秤计时或锁单位。每个完整传感器帧自带单位，只有该帧单位为 0 才映射克，1/2/未知值撤销旧读数；单位 ACK 本身不能变成重量或 READY。7 字节控制响应不会误当重量。当前严格支持官方 13 字节 payload，最低 Microbalance V300 / Ti V010；更旧固件无单位的帧不猜。负重量按固定偏移 signed32 解析，溢出拒绝，绝不置零。

Acaia live decoder 不引用旧离线候选：有界组帧、固定长度/完整 checksum、固定 weight event 与小数字段；新 decoder 不继承上代单位。status=08 的 unit=2 才允许克，5/未知值及坏帧撤销信任。单位状态允许在初始化中更新，但订阅完成之前的通知不能播种单位。不会复制旧 JS 的 `Message` 未绑定错误。AP 去皮、秤端计时、电量等不因看到其它社区功能而推定开放。

## 协议依据

- 原 APK 中已核对 `ConnectScaleBle`、GATT 选择、通知和初始化/去皮分支；只记录协议事实，不提交原始拆包源码。此前审计结果是当时迁移状态，不改写为已完成历史。
- Acaia 帧完整性、状态单位等另核对 MIT 社区的 [acaia.cpp](https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/acaia.cpp) 和同仓库 `acaia.h`；独立实现字节级协议，不复制实现代码。
- DiFluid [官方 v1.0.0 文档](https://github.com/DiFluid/difluid-sdk-demo/blob/master/docs/protocolMicrobalance.md)（2024-12-24）明确 Data12 单位和 `weight gram:x10`，官方 76g golden 为 `DFDF03000D000002F800000000000A27B000A9`。文档表格写 payload12，但详细布局与完整样本为13；本实现选明确的13，不猜12。官方 EE 特征说明 AA01/FF01 自相矛盾，因此仍只用旧 APK 明确的 AA01 通知/AA02 写，不新增 FF01 探测。signed32 负重另核对 MIT [difluid.cpp](https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/difluid.cpp)。
- Felicita 继续沿用原严格 ASCII 解析，旧 APK 的 `0x54` 仅作为手动去皮依据；无证据的确认 ACK 不臆造，归零确认仍由 StandaloneTare 等待写后新鲜近零通知。

## 验证与独立复核

- 首轮 `:protocol-core:test :device-session:test :bluetooth-android:testDebugUnitTest` 全部 exit 0。协议自检 39,823 条与 32,856 条既有捕获重放保持通过，新 legacy golden/流检查另由 `legacyLiveScaleChecks()` 执行；这些捕获不是新增秤实机数据。
- 实际 `openai/gpt-6-astra` 完成方案与最终只读实现复核，修复后重新读代码，明确通过，无剩余实现阻断。发现并关闭一处 P2 重入：SYNCHRONIZING observer 同步送合法读数变 READY 时，不能因此漏排 Acaia 心跳。心跳 due 先发布、断连先 detach；同步 READY observer 回归通过。Astra 未运行构建，其审查结论不替代下列 Lead 测试或硬件验收。
- 修复后 `:device-session:test :mobile:assembleDebug` exit 0：session 101 项既有自检及 703 项 JUnit（含 13 项新 legacy 会话测试）通过；BLE 4 项 JUnit 通过。Android 编译包含八语言 XML；额外校验 XML 可解析且无重复 resource name。`git diff --check`、`python3 scripts/git_policy.py branch` 通过。未跑无关完整组合矩阵。
- 参考固定至 DiFluid 文档提交 `df4e0731dc646b9110479cb3b39bb69346e0a640` 与 MIT 社区仓库提交 `180070fda5a1c795d9b73d6579b172ff5149e4cb`；官方文档证明字节布局，不证明旧 APK 的 AA02/AA01 对全部固件都兼容。
- 没有运行设备 UI、没有截图/物理去皮或已加载重量对照证据；离线帧、Mock 与 GATT Success 均不替代实际归零、连接稳定性和恢复验收。新增设备不得按重量驱动机器。
- 并发任务在本轮期间独立提交并推送 `e0daeb3`（Mock/ShotGate 修正），不属于本任务，未编辑/暂存它的五个 Kotlin 文件。其已提交后工作树仅余本轮秤改动，后续本任务提交/构建需以实际源码为准。

## README 后续文案整理

- 用户指出 ULTRA 不必单列、“补”属于开发过程用语。仅整理 README：ULTRA 移为兼容广播名备注；Felicita/Acaia/DiFluid 的状态统一写“已接入，待实机验证”，保留功能及固件限制，不改协议代码或权限。
- 续用本 PR 的 `feature/timemore-scales` / `621a715`，原集成基线仍为 `develop` / `3f9df29`，目标 `develop`；检查工作树并 fetch 后，只提交两份文档，普通推送 PR #14，不自行合并。
- 验证仅文案 diff、`git diff --check` 与分支策略；代码与设备行为不变，不重跑无关构建或委派审查。无发布/安装/设备操作、签名/版本/schema 改动；回退通过追加文档修正或 revert PR，不清数据。
- 用户再次要求精简表后说明：从 `541b7b2` 续改同分支/PR，仅保留一句适配范围说明，删除 README 中五段技术交代；协议依据、未验收边界与 ULTRA 兼容路径仍记录在本计划，不改实现、表内功能或验证状态。验证、推送和发布边界同上。
