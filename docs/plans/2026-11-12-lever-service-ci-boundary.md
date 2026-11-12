# 拨杆配方合入后的 Service 边界回归

- 需求与范围：修复 PR #62 合入后 Mock upgrade 暴露的未 attach Service 读取拨杆存储时空 Application 崩溃；调查 lifecycle 层级采集失败，不降低断言或发布门禁。
- 来源分支/基线提交：develop / 12f76324e34b82213ec2601c6a492bd4f2471645。
- 工作分支：fix/lever-service-ci-boundary；PR 目标 develop。
- 改动与验证：最小修正 Application 存储来源，补代表性回归；本地按 scripts/create-pr 完整检查，精确头提交 branch-policy/verify 通过后合入。合入后等待原有 push upgrade/lifecycle，不启动额外本地模拟器或真机验收。
- 协议与设备风险：不改协议，不清未知记录，不重发任何机器命令；不能为了测试放松保护。
- 发布：沿用自动预发布，四项 push 检查通过才可分配发布；已授权 floor=91，下一分配至少92。不手动发版、不安装。
- 回退：回退修复代码，保留用户存储与未知写入记录；不卸载或清数据。
- 现有证据：upgrade run 43172182669 的 verify.txt 指向 MobileService.getLeverStore 的 null application 强转；lifecycle run 43172182537 报 could not get idle state，继续区分测试采集与应用事件回归。develop verify 43172182526 成功。

## 调查与修复

- upgrade：测试用 ContextWrapper(app) attachBaseContext 构造 Service，不调用生命周期或蓝牙。Service.application 为空，但 applicationContext 是真实进程 Application。只改 journal 获取来源，不创建新 store、不捕获异常后放行。新增 detached Service 同一 store 身份及不可用 journal 必须阻断的窗口回归；原 MockUpgradeChecks 不变。
- lifecycle：idle-accessibility.txt 记录首卡 bounds 167,695–393,845 两秒产生八次 TYPE_WINDOW_CONTENT_CHANGED，暂停首页刷新后零次，服务继续工作。首卡每 250ms 重设背景，而其它五卡已有 dirty 更新。将静态主题背景/文字色移到 View 创建，描述/可见性/预览仅变化时赋值；UNKNOWN 或跨设备无记录时清空旧预览。新增多轮刷新对象保持及 UNKNOWN 清空断言。
- 不改测试采集 backend，不重试掩盖应用问题，不跳过无障碍 idle、升级数据或发布门禁。

## 本地验证

- Astra 独立只读复核通过；git diff --check 通过。
- compileDebugKotlin / compileMockKotlin 通过。
- compileMockAndroidTestKotlin 与 testMockUnitTest 最终单 worker、in-process 执行通过。首次并行构建出现 Kotlin daemon 读取 classes.jar 内部异常，未将该失败记为通过，停止并行后重跑成功。
- 新增实窗断言仅编译，未执行设备 instrumentation；继续保留合入后同 SHA push lifecycle/upgrade 门禁。

## 后续上下文所有者补充

- PR #63 / develop 0f3cee6a 的 push upgrade 43176029142 已成功；lifecycle 43176029171 通过原首页 idle/navigation 阶段，继续到语言检查中 ServiceOwnerCleanupChecks 才失败。
- 该旧夹具使用隔离 applicationContext wrapper 以保护原数据，同时正确设置 Service.mApplication。获取 journal 必须优先 Android 提供的 Service.application，只有未 attach 的测试构造才回落 applicationContext；两条路径均严格要求原进程 MobileApplication，不得新建 store 或吞异常放行。
- 补修分支 fix/lever-service-context-owner 来自上述最新 develop，补齐双上下文回归；继续 scripts/create-pr、本地完整检查、PR门禁及合入后的发布门禁，不修改现有夹具保护和断言。
