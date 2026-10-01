# 设置变更提示保留嵌套资源

## 现有缺口与实现

ResourceMessage之前只能保存标量或冻结未知对象。Service外层排队提示虽然有资源ID，内层settingsPresentation.change已是String，不能在语言变化时刷新。

新增sealed ResourceText，当前只有两个final实现：ResourceMessage与ResourceSequence。前者保留不可变子资源并每次render先解析子资源，再把结果作为外层参数；后者复制传入数组成员列表，按原顺序拼接。均不持有Context/解析器/设置对象，不发命令；未知对象和非精确BigInteger/BigDecimal子类仍冻结为文字，标量Formatter类型及每次新参数数组规则保留。ResourceText子参数面向当前%s显示组合，不承诺当作数值格式参数。

SnapshotMessage允许两类资源树作为根，initialText仍只捕获一次；后续重画不更改原日志文字。诊断toString只显示ID/数量，raw消息不格式化、null与空串含义保持。

MachineSettingsPresentation新增changeMessage覆盖现有12种MachineSettingChange，包括运行/供水/睡眠计划、待机时间/温度、拨杆、萃取/补偿/蒸汽温度和三种开关。原change(String)接口保留，只解析资源树；其它读回/周计划函数逐字不变。Service整份源码相对a9385a8仅两处change(change)换为changeMessage(change)，实际设置对象/出队资格/命令构建/事件kind/日志字段不变。

## 证据

新嵌套测试先出现ComparisonFailure（子资源被当作诊断文字冻结），再实现后通过。新增组合成员冻结/空译文/诊断与17条明确中文设置预期，验证内外资源重画而initialText不变。

从a9385a8实际change函数提取临时旧实现，1098种有效设置×8语言共8784组逐一比较；包括所有待机分钟/温度组合、所有温度/补偿合法整数及布尔/拨杆模式。新change默认文字、捕获initialText及八语言重画与迁移前函数一致。通过后删除临时旧实现和对照测试，保留正式案例测试；不把相互调用同一实现当成独立等价证据。

最终完整离线协议39,823检查/32,856通知回放、会话92场景、共享JUnit69项；Alpha/Mock各136项（135通过、1缺真实旧导出跳过），三APK构建成功。19Python测试、生成一致性和6种AAPT转义fixture通过；两个APK各6969编译资源值仍逐字一致。独立静态审查未发现实质问题。

本机没有emulator执行程序或AVD，虽然SDK有系统镜像；没有运行Android Context/页面/RTL/通知试验，也未连接真机。本阶段未开放选择入口。启动拒绝原因、预热取消阻止原因等其它已经解析的嵌套字符串仍待迁移；页面生命周期和完整安全验收继续待完成。
