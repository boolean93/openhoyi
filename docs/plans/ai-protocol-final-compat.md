# AI SDK 混合工具响应与协议最终校验

- 范围：修复当前Koog对工具后续响应同时含正文与tool_calls时提早结束；保留exact协议、未完成工具门禁和安全拒绝；协议错误独立提示，不误报网络超时。
- 来源／分支：继续既有 `feature/ai-chat`，基线 `599670aab75d94070bf836cb813239e30e3e46e7`（来源develop，未来目标develop）。保留全部其他未提交工作，新storage不纳入。
- 文件／分工：Sol唯一修改Engine／ProtocolHistory／错误枚举及Activity单点提示／资源／必要fake测试，保留140诊断hooks；Astra独立源码审，Gradle由协调唯一owner执行，不并发。
- 推送／发布：不PR、不推送、不合入，不安装、不设备、不付费API，不修改签名或包身份。候选构建由父协调另排。
- 证据边界：现场B140只有合并的PROTOCOL_FINAL码。截图9992ms与用户trace10140ms不能合并推断同一attempt；失败后另两次成功不代表失败轮提交，已可见卡可能来自历史。没有读取真实正文、reasoning、Key或用户存储。
- 实证：本机实际依赖agents1.3.0／DeepSeek1.3.0-beta／OpenAI base1.3.0。字节码显示正文不trim、reasoning独立；SDK默认工具后onTextMessage会接受同时存在tool_calls的Text。140独立snapshot真实Koog+fake transport两项probe通过，混合二轮结果requests2/toolReads1/completedfalse，触发原PROTOCOL_FINAL；普通正文首尾空白原样通过。证明可复现机制，不证明140真实响应一定同结构。
- 修复：原响应error/refusal校验和length早退之后，先记录exact content/reasoning/tool_calls，再仅给SDK的混合响应副本置content=null，避免提早文本结束。后续request用exact还原，最终正文不投影；不trim、不拼reasoning、不自动重发，不放松final正文相等及工具事务完整门禁。
- 错误与诊断：新增AiProblem.PROTOCOL独立短提示；旧PROTOCOL_FINAL保留兼容，新增MISSING／TEXT／TOOLS闭合子码，禁止正文／参数／Key日志。Turn使用Engine同一个diagnostics保留已观察HTTP状态与起点，140请求记录hooks保留。
- 验证：修复后probe改为严格3请求／2工具读取／最终成功；原混合正文和reasoning在history及下一请求exact一致、tool_result一一配对。另覆盖空白正文、final text mismatch、pending工具仍拒绝、shared HTTP诊断；复用protocol、response safety／length、context／compact等最小回归。当前新增修复尚待统一执行，不能沿用旧probe成功作为修复通过。
- 协议／设备风险：SDK内部投影不作为持久协议；不执行未完成工具参数，不越过拒绝，不修改曲线写权限或机器协议。无真实服务或设备验收。
- 回退：回退本次适配代码，保留会话／草稿／诊断与未知记录，不卸载或清数据；不得将未完成工具状态降级成成功回答。


## 首轮修复验证与旧测试helper兼容

- 修复后主编译成功，strict Koog 4项全部通过，包括3请求／2工具读取和exact混合协议重放；39项相关回归仅1项失败。
- 唯一失败定位在测试专用AiLiveProbePolicy.localOnlyTools旧6工具假设，与当前7工具不兼容；不是生产projection／final校验失败。已前向适配精确7个已知名称且拒重复／未知工具，仍只输出原3个本地读取工具，web／proposal／detail／card selector都过滤，不扩大探针权限。增加未知selector名称拒绝断言。
- 没有执行任何真实live probe／API；仅待假请求单测复跑和lint完成，尚不把首轮39项称全部通过。
