# 语言运行时接入前审计

本轮依据当前生产代码。完整目录已生成并打包Android资源；键和编译文字检查不等于母语/布局/Context验收，选择入口仍关闭。

## 当前调用事实

- `ThemedActivity.attachBaseContext`已统一语言/深浅色配置，`onResume`比较主题及Locale后重建；当前无语言选择调用方，重建/前后台过渡仍待Android验证。
- `MobileApplication`资源读取已走原始base构造的语言Context provider；Service动态资源读取同一Application资源。独立app_language/tag偏好仅成功commit后发布进程值。Toast/通知实际一致性和缓存配置失效仍待Android验证，详见unified-language-context.md。
- `MobileSnapshot.message` 已改为 `SnapshotMessage`：资源事件保留ID/冻结参数，日志使用产生时的 `initialText`，Home只重新解析显示。140处直接资源事件、3处条件资源事件、扫描/Mock直存消息已迁移；外部诊断、运行时/gate返回的String仍为raw。恢复阻止提示已保留资源ID，设置变更嵌套资源已通过ResourceText/ResourceSequence及changeMessage保存；启动拒绝/告警已保留嵌套资源身份（见start-rejection-resource-messages.md）；预热取消等其他已解析子提示仍待迁移，因此当前还不能声称全部持续提示可刷新。
- `manualSafetyMessage`已由资源ID动态解析，服务内部警告存在性检查使用ID，避免依赖显示文字。设置未知结果提示和 `safetyMessage` 也参与通知刷新。`refreshSafetyNotification`仍用警告字符串是否变化避免重复通知。新增尚无调用方的`refreshNotificationDisplay`，供Service语言Context更新后强制刷新同ID通道和通知；错误只诊断，不重放事件或改未确认状态。Android异常与重响仍待运行时验证。
- `screenVisible` 在无页面可见后延迟500毫秒调用hub.background。切换语言导致重建时要覆盖此过渡，不能把显示重建变成新的扫描/自动连接窗口或停止现有设备会话。
- Manifest已经supportsRtl=true；不能据此认定当前固定宽度、左右位置、长标签和横屏布局都通过阿语验收。
- app_name在default为OpenHOYI Alpha，Mock资源覆盖为HOYI Mock。正式转换语言资源时须从共享翻译目录排除app_name，保留变体身份；完整草稿中的中文源身份不是覆盖Mock名称的指令。

## 后续接入条件

1. 七种完整目录通过源哈希、键集合、参数索引/类型/补零宽度/次数、换行与非空检查。测试通过只能确认机械格式，不等于母语准确性。
2. 用独立语言偏好，写入失败不应用；规范tag与显示标签分离，旧包私有数据不自动读取。UI入口和服务语言资源使用同一已提交偏好。
3. 为需要刷新而已经产生的事件保留资源身份及不可变参数，与原始历史/诊断原因分开。切换时只重算显示，不调用event再记一次日志，不重放命令或重复成功/失败动作。未知的原始用户内容保留原值。
4. 同时覆盖Activity、Application、Service、前台/安全通知和通知通道名称。重建期间保持服务及停止入口可达；不改generation、队列、超时、恢复状态、当前曲线或去皮进度。
5. 以Mock离线/云端验证每语言深浅色、紧凑/横屏、长确认/未知结果、RTL与图表。科学时间轴、原始HEX/包名/单位按各自语义显示，不反转数值和点序列。
6. 曲线/历史模板可翻译；原曲线名字、用户输入、厂商tips和规范历史原因不能直接重写成译文。内置稳定ID可加展示映射，但必须与控制元数据独立。

消息存储阶段已修改Service显示代码，但不改蓝牙命令、资格判断、定时器和恢复状态。统一资源Context已接入；正式Android语言资源已打包，选择入口尚未开放。剩余嵌套提示与通知/显示生命周期验证完成后再开放入口，不能仅凭纯偏好测试声明运行时切换安全。
