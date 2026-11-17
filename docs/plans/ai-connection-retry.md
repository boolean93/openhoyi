# AI 连接阶段有限重试与错误反馈

- 需求：最多5次递增等待连接重试，进度可见／可停止；不重跑agent、工具、提案或已保存摘要，不对可能已发送的请求盲重试。
- 来源／工作分支：复用 `feature/ai-chat`，基线 `599670aab75d94070bf836cb813239e30e3e46e7`，来源develop、未来目标develop；保留协议final修复、140日志与其他所有未提交内容，不纳新storage。
- 分工／文件：Sol独占RetryPolicy／SafeTransport／Engine／Activity／Recorder／中英资源和RetryTest；错误语义独立模块由对应owner提供，卡片与复制展示owner独立。Astra源码独审，Gradle由协调sole owner串行。
- 推送／发布／签名：本轮不PR、不推送、不合入、不安装、不操作设备或机器、不调用付费API。候选141由父协调在完整基线上构建，包名／签名／递增版本沿用现有规则。
- 安全边界：仅当前HTTP的本地CONNECT阶段、独立writeBoundary=false且异常为DNS／ConnectException／SocketTimeout可申请重试。TLS、其他异常、HTTP／auth／rate／协议／响应读取及WRITE以后不自动重发。writeBoundary在获取outputStream前保守置true，不按共享diagnostics猜测是否写过。
- 范围／预算：重试默认只由Engine显式启用，standalone transport保原单次行为；同一reply的compact／chat／search共享最多5次额度，即单HTTP最多6 attempts且共享预算可更早耗尽。等待为1／2／4／8／16秒。保留compact与chat原各自60秒窗口和15秒连接／30秒读取约束，重试等待计入当前窗口、不重新计时，不承诺跑满5次。
- 生命周期：取消／Key revision变化／close在delay前后及新连接／取Key后检查；closeSignal与等待竞争并取消timer，长等待也可立即退出。当前HTTP失败仅重建其连接，工具及摘要流程不重启。bytes整个logical send结束才清零，失败attempt不提前清零；连接逐attempt断开，普通失败不close整transport。
- 用量：一logical HTTP只begin一次、跨连接重试沿用同usageId，最终只按已有规则完成记录；失败仍保守unknown，不虚增为6次模型请求，也不宣称未WRITE等于账单0。
- UI／诊断：progress仅retry index／上限5／等待时间／闭合连接错误kind，可停止；固定超时不保证跑满。retry trace仅reply UUID、模型闭合名、次数、等待、stage、kind，不含URL／Key／正文。错误用语义模块区分协议、本地处理、服务、连接／读取／总超时；Mock额外显示闭合多行诊断和当前失败attempt UUID，仅内存；正式版保持简洁。复制成功／失败资源同包补齐。
- 验证：新增AiRetryTest六项fake，覆盖原bytes未清零恢复、最多6 attempts／共享5重试、WRITE／READ／HTTP／auth／rate／TLS／protocol零重试、取消／deadline／Key变化和长等待close后无新连接、真实Koog已执行工具不重跑、闭合进度日志；delay全部注入fake，不等待31秒。现有transport／usage／protocol／semantic／Markdown／diagnostic最小回归由协调统一运行，当前不预称通过。
- 独审：Astra源码审通过安全边界与closeSignal增量，测试设计相称；仍需编译、fake实跑与实际Android进度／错误展示验收，fake不等于真实服务。
- 回退：仅回退本次功能代码，保留会话／草稿／曲线／Key及日志未知记录，不以清数据或卸载回退。
