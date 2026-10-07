# 独立去皮发送前授权

**Goal:** 按钮去皮排队后，原 Hub/秤身份和当前无萃取状态仍成立才允许发送。

**Architecture:** `StandaloneTareDispatchPermit` 只判断调用方资格；Service 捕获原 Hub/READY 秤地址，Hub 将回调与既有 `DeviceConnectionGate` 合并传入真实 ScaleSessionControl/DeviceSession 队列。现有单参数 Hub 方法委托 true，保持兼容。报文、去皮结果和人工重试语义不变，不新增自动重发。

**Tech Stack:** Kotlin、Android Service、真实 GattQueue + 假驱动。

- [x] 先写参数测试：正常、地址大小写、空/换地址、Hub 变化、非 READY、手动萃取、App 活跃萃取均覆盖；临时无保护实现应出现预期失败，再实现判断。
- [x] Hub 增加 READY `scaleAddress`，`tareScale(beforeDispatch,done)` 保留原提取状态 gate；Service 初始资格不减弱，增加发送前动态资格，旧 Hub 回调不能发布新 Hub 去皮状态。
- [x] 新 Android fixture 调实际 Service.tareScale，Discover 阻塞真实秤队列后变更资格；允许场景核对精确去皮字节、传输后仍等待新解码零重量；拒绝场景不能写、不能补发。使用隔离偏好、脱离生命周期 Service、假驱动，禁止系统/权限/组件调用。
- [ ] 完整本地编译/测试/Lint、独立审查、提交推送后读云端实际 marker/result；编译不作为 Android 运行通过证明。

不要求咖啡机 READY 才能独立去皮；只有已知手动萃取和原有 App 萃取 gate 会阻止。本轮不新增机器写入，不使用 BLE 或真机。
