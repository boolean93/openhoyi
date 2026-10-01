# 持续消息保留资源身份

## 已实现

`ResourceMessage`保留资源ID及构造时冻结的参数；不保留Context、回调、设备或状态引用。String、基本数值包装类型及精确BigInteger/BigDecimal保留Formatter类型，其它对象（含这两类的子类）冻结为文本。每次解析提供新参数数组，解析器篡改数组不影响后续显示。此规则面向当前字符串/标量调用，不承诺自定义Formattable对象的格式语义。

`SnapshotMessage`将产生时的initialText与可重新显示的模板绑定为同一不可变对象；raw文本不解释百分号，显式空字符串不回退。诊断toString不暴露参数。MobileSnapshot只有null才显示初始提示。

MobileService迁移140处直接资源事件、扫描完成/连接方式/萃取准备3处条件资源事件及扫描、Mock直存提示。外部扫描错误保留raw；原event kind、ownerId、日志字段及产生时的文字保留。Home重画只解析文字，不再次event、不记新日志、不发送命令。

## 验证

7项ResourceMessage测试覆盖重复显示/快照状态、raw与空值、可变对象和解析器篡改数组、数值及null类型、可变BigInteger/BigDecimal子类、诊断脱敏及全部872默认模板格式对照。可变数值子类测试先出现ComparisonFailure，修正后通过；最初新API测试先因未实现无法编译。

相对f0023ff从旧Service正向重放140项直接替换及8项声明修改，整份源码与工作区一致；Home和Mock整份文件分别仅一个声明的显示替换。证明本轮未夹带其它控制源码改动，不证明物理行为。

最终完整离线回归：协议39,823检查及32,856条通知回放、会话92场景、device-session JUnit69项；Alpha/Mock各125项（124通过、1缺真实旧导出跳过）；Lab/Alpha/Mock构建成功。七语言目录校验及10项Python测试通过。独立静态审查发现的3处条件资源遗漏及数值子类漏洞已修正。

## 剩余边界

恢复阻止提示和manualSafetyMessage已在后续恢复显示迁移中保留资源ID；其它gate返回的已解析文字与通知警告缓存仍有raw文本；后续必须保留控制分类及空/null语义，不能通过重新执行gate或恢复动作来刷新历史提示。

尚未接入语言偏好、统一Activity/Application/Service语言Context、通知/通道刷新或选择入口；没有新增打包语言资源。本阶段也未连接真机，不能据离线回归宣称所有功能一致或无硬件风险。
