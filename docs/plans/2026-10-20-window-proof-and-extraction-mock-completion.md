# Window终态落盘优化与Extraction原范围补齐

Astra，2026-10-20。两项独立、顺序owner；仅本计划授权边界，不代表运行通过。

## A. NativeWindow：只移除重试间的proof序列化/IO

依据dc5875c两个独立失败样本：Scale重试间proof约846ms；Settings约976ms。先不动geometry比较、SHA、diff扫描或任何像素算法。

唯一实现owner建议Sol_window；唯一生产修改范围是androidTest `NativeWindowFrameEvidence.kt`（不是app生产）。当前不接受attempt分支在保存retained后调用`writeProof(false)`；移除此中间调用，继续将每次完整attempt、budget/stage/timings、原始before/after几何与diff全字段保存在原内存JSONArray。不得裁字段、只保最后attempt、用摘要替代全geometry，也不得提前recycle被retained持有的bitmap。

终态成功仍persist所选成功原件再writeProof(true)；终态异常/真实deadline耗尽仍先保原retained失败原件，再writeProof(false,originalFailure)，全部attempt一并序列化一次。原最新完整candidate优先于deadline尾部partial、图片命名/哈希/失败上下文与两种mapping诊断顺序不变。成功匹配判定仍在原started+5000内完成；终态序列化/PNG输出不回填withinMatchingBudget，不重新计时。不存在成功匹配就保持FAIL，即使落盘后像素看似相同。

writeProof异常不得掩原采集异常：失败分支以suppressed保存写盘错后重新抛原错；成功分支持久化失败必须失败，不能返回无证明的accepted结果。timing sidecar记录一次终态proof写入及全部attempt阶段；进程强杀时可能没有终态proof，明确为证据未完成，不能补造accepted。保留原Bitmap回收所有权和有限retained数量，不在内存保存每轮六张bitmap。

检查：diff核只有中间writeProof移除/终态异常优先级必要修正；编译；通过已有真实样本结构或小型契约测试确认多attempt字段全保、终态一次写、原失败不被写盘错覆盖。下一CI看真实matching elapsed及正式四零差，不事先宣称能过。不改runner/5000/等待策略/scrollbar/隐藏API，不先融合SHA或改geometry等价算法。

## B. Extraction：原要求缺失状态的合法Mock模型输入接口

现能力不足不是删除原状态理由。原`MockDeviceRuntime.start/stop`直达RUNNING/ENDED；真实`ExtractionController`已有STARTING、STOP_REQUESTED、OUTCOME_UNKNOWN归约。Service现mockTick只sample→snapshot→recordMachinePoint，没有走真实onCoffeeFrame的passiveShot.observe。因此不能用slot6直start假充manual。

### 唯一owner和最小文件范围

建议现Extraction owner Sol_bcc先完成已合法测试分支，冻结后由同一owner实现此补齐，避免两个owner改M5测试。新增独立 `MockExtractionScenario.kt`，必要接线限定 `MockDeviceRuntime.kt`、`MobileService.kt`；测试仍现 `ExtractionHistoryVisualMigrationChecks.kt` + M5 runner，并新增有意义的Mock事件序列单测。**不改device-session的ExtractionController/真实CoffeeSessionControl/真实BLE协议、生产Activity布局或真实恢复策略。** parent可按此常规Mock范围直接安排，不需用户重复批准。

### 输入接口，而非state setter

1. 只在BuildConfig.MOCK_MODE、当前唯一真实Service、已有同源clock owner、明确scope token下安装一次。默认null路径逐字保留原Mock行为；real构建拒绝安装。撤销须匹配owner，Activity/Service销毁及回调排空后撤销，不能提前恢复原模型。所有报告明确synthetic=true/hardwareMeasurement=false；不把synthetic完成当设备ACK。
2. 专用scenario内部复用原`ExtractionController`，以实现原`CoffeeControl/ScaleControl`的synthetic适配器接收真实合法StartParameters和beforeDispatch条件。只提供有限输入事件：开始/停止请求、明确synthetic operation completion、设备ready变化、合法typed机器telemetry、秤observation、同源clock tick。禁止setState(enum)、直接写UI/snapshot标签或反射private state。operation completion必须留synthetic标记、请求owner/顺序，过期/错owner拒绝；真实CoffeeSessionControl从不使用它。
3. Service的mock opt-in startShot/stopShot走同一已批准启动校验/本杯身份/series/watchShot流程，唯一差异是Mock端将命令交scenario controller而非旧runtime直跳。不得为了新接口复制并修改真实启动门禁；owner先给具体接点diff，确保真实分支无变更。`shotState`只读scenario.controller.state；没有安装仍原mock?.shotState。少量接线无法保留原流程时停止扩大并报告具体冲突，不偷改session算法。
4. telemetry提供分离的“当前帧/没有新帧”语义，只有真实offer的新typed帧进入同一记录链；无新帧不制造重复样本，不靠冻结View或停handler。用scope clock合法单调推进，匹配5s仍真实SystemClock。为manual提供设备自主brewing帧而不发app start；通过原onCoffeeFrame同源观察路径触发PassiveShotDetector，绝不直接调detector状态。scope内的journal/recovery仍用既有隔离对象，不能动用户持久恢复记录。

### 需落地的可证伪事件序列

| 缺口 | 合法序列与必要断言 |
|---|---|
| STARTING | 原start请求、synthetic command保持未完成且不发active帧；controller实际STARTING，真实页面显示启动中；原超时/取消语义仍在 |
| STOP_REQUESTED | start经明确synthetic成功进入RUNNING；调用原stop，暂不完成stop且无idle确认；实际STOP_REQUESTED，不写ENDED，不自动重发 |
| UNKNOWN_live | 已RUNNING后ready=false并controller.tick，或原请求显式synthetic Unknown；记录选定唯一机制，实际OUTCOME_UNKNOWN，最后有效样本不伪实时 |
| missing_samples | 合法start synthetic完成进入RUNNING，尚无任何机器/秤采样offer；series真实0点，图无path及空样本说明；不能用立即stop后的ENDED替代 |
| water_reset | 逐个offer合法当前杯机器帧，累计水量先增后降但时间严格单调；原投影断线，不改FourMetricChartProjection，不从runtime固定增长式硬反推 |
| manual | 无appShotInProgress，offer设备自主active帧，经原onCoffeeFrame观察链自然触发manualShotActive/series，slot不是充分证据；页面和journal身份对齐 |

其他no_scale/stale/no_service_ready/reference/gap/journal分支沿已有合法入口；各状态应核原模型结果再截图，不凭fixture命名判进入成功。无服务未ready与普通IDLE要读实际readiness；gap需真实不连续采样时间并核断线。可先完成合法子集，但runner/manifest保缺项，最后固定12状态逐一闭合，不能把缺项过滤为PASS。

先通过事件序列单测：延迟完成/失联/零样本/水量重置/manual观察，以及错误owner、重复安装、过期completion拒绝、默认Mock和real不可安装回归。再运行原M5四配置实际原图；新状态按原G1–G4和协议验收，真实硬件ACK仍不认证。
