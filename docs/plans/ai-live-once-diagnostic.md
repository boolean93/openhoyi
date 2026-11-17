# 已授权的一次性真实合成诊断

- 沿用feature/ai-chat，目标仍develop；不提交/推送/PR，不修改main/Mock业务逻辑。Sol实现临时androidTest入口与host测试，Astra独审，平板worker负责安装test APK和显式单次执行。
- 用户已允许平板已配置DeepSeek账号排查，总预算人民币1元。入口只使用固定合成问题，无历史、设备状态、曲线、萃取数据；应用内部在固定DeepSeek发送边界使用已存Key，Key不经过ADB/助手，不输出或保存原请求/响应。
- 真实Android SDK先capture-only生成首轮，不联网；仅将max_tokens限制为32，其余模型/安全/工具设置保留，最终UTF8请求≤8192字节。实际网络只有一次POST，无重试/后续模型回合/工具数据读取；发送前createNewFile持久原子one-shot标记，失败或未知结果也不自动重试。
- 依据当前官方人民币高峰价（输入2元/百万，输出8元/百万），此请求估算远低于1元；本轮整笔1元预算预留给这一次，不做追加probe。真实账单仍以服务商为准，不宣称估算等于已扣费用。
- HTTP沿用固定HTTPS、标准TLS、禁redirect、输出32、响应最多4096+1字节，30秒独立断连watchdog；Key mutation断连、finally注销watcher/取消deadline。
- 诊断只输出固定HTTP/阶段/错误枚举、合理范围usage计数及受限错误词线索。先去除当前credential，再将ASCII完整token映射为固定枚举，最多16项/受限总长；不输出任意substring/Unicode/数字/ID，不把词线索当已验证根因。
- 不用Computer Use、不动系统安全设置、不操作咖啡机、不卸载或清除已有数据。Instrumentation可能重启目标进程，执行前需协调瞬时草稿/生命周期边界；没有确认前不启动入口。
- 本地先fake验证词表/脱敏/上限/一次性与超时边界，再编译test APK、独审。回退为不执行该入口；one-shot标记保留作为防重复证据，不删除Key/业务存储。临时测试入口不进入正式APK。

## 一次实测结果与非付费修复

平板执行一次成功抵达服务端，HTTP400，固定词线索为TOOLS/CAN/ONLY/USED/TOGETHER/WITH/SAFETY_CHECK/THINKING；无usage，不能报告实际扣费为零。整笔1元预留保持，不再触发真实API。

修复方向保留high safety和三只读工具，开启低强度thinking。官方thinking工具模式要求跨用户轮完整保留reasoning_content，故补仅内存完整协议历史（含assistant/tool配对），UI仍只显示最终答案；失败/取消不提交半轮，新会话/关闭/Key变化清理。独立总64KiB/128消息上限，不截断reasoning或工具组；保持4请求/6工具/60s/maxTokens2000原预算。不把fake通过当真实服务已恢复。

## 完成与撤除

- 修复后真实生产SDK/transport第一轮验收：B126 HTTP200，1请求，NONEMPTY_REPLY，输入680/输出2。
- 随后经同一1元总预算授权的工具/跨用户轮场景：HTTP200×3，3请求，固定合成current_status一次，2用户轮，TOOL_AND_MULTITURN_OK，输入2178/输出26。没有读取真实机器/曲线/萃取数据，Key只在应用内固定发送边界使用。
- 按当前人民币高峰输入2元/M、输出8元/M估算，已知成功请求合计0.00594元；初次400无usage，保留0.01664元保守估计，总估算≤0.02258元，不作为账单实扣证明。所有请求已停止。
- 已测主Mock126归档SHA256 `3da13accad0b02b42421e15876de5db9d020701654bebbe6b7967c1d5a6e4b49`；同源码Debug126 SHA256 `86a46b740b66fe400c818d21db5d20f4480cf6a5497f1bb5f081a6e591f11ff3`，安装同步由设备worker核实，不能误报已完成。
- 临时真实收费instrumentation源码已移出仓库，manifest撤除live入口；纯脱敏/预算policy移至host test，不进入主APK或默认test APK。保留设备one-shot marker，不清Key或业务数据；中性test APK仅覆盖还原测试工具，不再运行任何付费入口。
- 工具链PR此前审核阻塞仍待明确批准，不推送/创建PR/绕过。模型选择仅另写后续规划，不假定账户GET模型或新提供商/安全/费用选择已获验证。
