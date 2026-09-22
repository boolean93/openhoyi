# 覆盖矩阵与多轮核验

这不是百分比覆盖率。包数量、断言数量、功能数量分别计算，不把回调success当作硬件执行成功。

| 要求 | 实现入口 | 证据 | 未完成项 |
|---|---|---|---|
| 协议无Android依赖 | protocol-core | JVM独立编译 | 无 |
| 咖啡机编码 | CoffeeCommands | 三条实际20字节启动、停止、温度、加热、睡眠及认证人工向量 | 未采集组合不开放发送 |
| 0x83/40/80解码 | HoyiCodec | 真实通知全量回放、字段人工断言、截断与随机输入 | 仅已观察长度；未知固件/新扩展拒绝或标未知 |
| BOOKOO | BookooCodec | 18,840条通知、XOR、正负号、去皮初始化 | 已知负质量对照、其他固件/其他秤 |
| 字节防篡改 | ByteFrame / GattOperation.Write | 输入、输出数组修改回归 | 无 |
| 双设备隔离 | 两个GattQueue/AndroidGattDriver | 独立队列测试 | 实机并发验证 |
| 队列与超时 | GattQueue | 串行、旧代次、超时关闭、异常回调、取消旧启动 | Android回调行为实测 |
| 协议就绪 | DeviceSession | 咖啡机认证+设置、秤初始化+首个样本、错误特征忽略 | 真机初始化耗时/兼容性 |
| 自动连秤 | ReconnectPolicy + NativeDeviceHub | 10分钟窗口、退避、手动取消测试 | 地址变化时重新扫描/绑定；宿主生命周期实测 |
| 去皮与重量停止 | ExtractionController / Policy | 去皮写成功后等待近零通知、重量时效、去重、掉秤保护停止、停止未知结果 | 真实秤延迟和业务阈值标定 |
| 三次萃取链路 | ReplayChecks + shots.tsv | 手动44.098s；流量结束无额外stop；重量17.786s | 不等于物理咖啡机回放；最终杯重未认证 |
| Android连接/服务/CCCD/写入 | AndroidGattDriver | SDK35编译、AAR构建、lint | 真机permission/revoke/disconnect/GATT回调 |
| 扫描 | ScanCoordinator | Android编译/lint；统一扫描两角色 | 真机扫描频率、位置权限与开关验证 |
| Android后台 | NativeDeviceHub宿主契约 | 不依赖Activity、主线程定时 | 前台Service宿主未实现，不能承诺进程被杀后的停液 |
| 日志 | diagnostic回调、codec原始帧、fixture来源哈希 | 密码默认toString脱敏 | 新版持久化结构化传输日志尚未实现；hex显式导出含密码，调用者不得直接记录认证帧 |
| 设置写入结果 | OperationResult | 区分Failed/Unknown/Cancelled/transport Success | 不提供“已应用”假状态；设置回读事务未实现 |
| 大包/MTU/通用Read | 首版明确不支持 | Android写入限制20字节 | 后续需真实协议分片证据后实现 |
| 管理命令/OTA | UnsupportedCommandGroup | 无执行入口 | 密码修改、校准、出厂、OTA独立验证 |
| 原生UI/持久化 | 不在第一阶段库范围 | 无 | 后续工程 |

## 本轮结果（2026-09-22）

标准Gradle全量命令成功。协议34,991次断言（其中32,856条通知，不是34,991个独立用例）；会话/策略/集成/回放共25个命名场景；Android debug AAR成功；lint 0 errors / 2 warnings。首次使用网络补齐依赖后，最终验证在offline模式完成。

## 检验轮次

1. 测试先行：协议API缺失、会话API缺失、策略缺失等红灯后实现；认证数字字节单独失败后修复。早期编译使用缓存Kotlin编译器，最终均走标准Gradle。
2. 真实数据：32,856通知解码、三条启动逐字节、三段2428条观察时序输入。来源文件哈希和匿名会话/seq保留；不包含MAC、认证密码或厂家bundle代码。
3. 状态故障：旧连接回调、超时、重复通知、陈旧或未来重量、错误会话、去皮未确认、掉线结果未知、非法启动参数、回调抛异常、排队启动被停止取消、队列满时紧急停止仍被接纳。
4. 规格核对：Android与纯Kotlin依赖边界、公开API传递依赖、控制能力门禁、未知结果、后台宿主边界、未验证功能清单。
5. 构建门禁：Gradle check、Android assembleDebug、lintDebug。lint 0错误，2警告为跨API属性与Gradle更新提示。

独立子智能体完成了部分协议开发后因服务额度限制中断，独立审查未完成。主任务接管并完成本轮规格核对与代码检查，不能将此记为独立审查通过。

## 发布前必须完成

- 另一个审查者独立复核控制和断线语义。
- 通过可安装测试宿主，先只连设备/接收，再人工控制下验证停止、去皮及三条曲线。
- 校准新旧时序差异、BOOKOO负值与重量单位；验证回到前台、锁屏及断连。
- 新UI接入前实现真实Android前台服务及持久化，明确断连/杀进程时的能力边界。
