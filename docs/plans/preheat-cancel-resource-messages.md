# 预热取消提示资源迁移

沿用native-language-runtime-audit第3项已经授权的资源身份设计。BrewWaitCancelGate增加blockMessage，旧block继续返回String；Mock取消预热也保留资源身份。Service内部取消返回ResourceMessage，公开String接口渲染，超时事件保存嵌套原因（原null.toString仍显示null）。公开brewWaitCancelBlock仍返回String，出队资格检查资源对象是否null。

不新增控制功能、不重试命令，不变更共享PreheatGate、clock次数、恢复记录、beginCancel/取消完成回调、600000ms定时及未知状态。构造资源树不持有运行状态；每次操作/出队仍各自采样检查，绝不复用捕获的旧许可。

- [x] 新测试先失败：状态/时间/睡眠/未知结果矩阵，空译文不放行，Mock取消仍更新相同内存数据。
- [x] 最小实现并跑定向测试；Service逐项正向变换核对。
- [x] 旧实际源码独立对照八语言；独立静态审查。
- [x] 完整离线回归、构建和编译资源检查，记录限制后提交。

## 结果与限制

两项永久新增测试先因缺API编译失败，再定向通过。Mock测试明确禁止取消阶段解析文字，随后允许sample展示并检查中途取消保留96.50°C、目标已清除。门控测试覆盖所有设备/萃取状态、未确认萃取、时间边界与睡眠数据，空译文仍拒绝；嵌套原因重画不改initialText。原中文/1500ms/未来时间/缺数据案例保留。

从3632494实际旧源码复制独立BeforeBrewWaitCancelGate/BeforeMockDeviceRuntime，八语言25920组取消检查（全部设备/萃取状态、两种pending、六种帧和五种时标）、80组Mock取消场景及重复取消比较一致。临时源码和对照测试已删除；本机结果/private/tmp/hoyi-preheat-resource-differential-result.xml。Service全文件按声明显示/返回类型替换正向重放一致；共享协议/会话源码未改。取消命令仍为既有setBrewWait(0)，这不是新增命令的安全证明。

完整离线协议39823检查/32856通知回放、会话92场景、共享69JUnit通过；Alpha/Mock各140项（139通过、1缺真实用户导出跳过），三APK构建成功。19Python、生成一致性、6项真实AAPT转义fixture、两APK各6969编译资源值核验通过。独立静态审查无实质问题。

资源解析次数可变化，出队检查不再为判断null先格式化文字；clock与共享门控采样位置不变，显示不作为许可。没有Android服务运行时/重建/RTL试验，没有真机操作。语言入口仍关闭，显示生命周期、全部功能覆盖和完整协议安全验收仍未完成。
