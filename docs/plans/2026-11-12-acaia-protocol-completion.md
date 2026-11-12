# Acaia 公开证据解析补全

- 需求与范围：保留用户 Lunar 诊断修复，补全有公开证据的组合记录解析，避免未知高位重量被静默截断；不复制或引入限制性 SDK 实现。
- 来源：origin/develop@b8cf9869051e23892990acf631227b5ef9376ff2；分支 fix/acaia-protocol-completion，未来 PR 目标 develop。
- 分工：实现者新增独立 codec 与会话回归；Astra 核对公开来源、边界及最终 diff。
- 文件：LegacyLiveScaleCodec 及对应协议/会话/链路测试；迁入先前 Lunar 真实匿名通知回放，旧工作树不动。
- 依据：用户 17 字节通知与分包；MIT aioacaia 固定提交 0d542170b3c5e8db8dd3983d46318d1066bd1f6b 的公开 record-chain 文档/测试。见同日证据说明。不引入其代码或 SDK 二进制。
- 协议：先验证全帧长度/checksum；有界解析 weight5(6)、battery6(1)、timer7(3)、button8(1)、ack11(2)。仅完整已知链返回最后一条重量，未知 tag 整帧 Unknown，已知截断或任一无效重量整帧 Invalid。非重量记录不更新读数时效，不作为命令确认。
- 保守边界：公开参考仍只解 u16，不足以证明 u32/单位 bit7 语义，因此高两字节非零明确拒绝，不静默截断；单位仍仅原始2认可克。AP 端点及初始化在用户诊断已成功，不改握手、心跳或名称映射。
- 控制：不开放 Lunar/AP 去皮、计时、自动去皮或目标液重。官方 SDK API 并不证明本实现命令结果或实际停机，仍需实秤证据。旧 A 路径未新增控制权限。
- 验证：协议/会话完整测试、4项蓝牙链路回归；实际结果待补。无实秤、模拟器或机器操作。
- 推送合并：遵守 docs/git-workflow.md；本轮不推送、不 PR、不合并。
- 发布：不新发版、不安装；既有 Lunar 测试包内容不变，不能声称包含本轮新增补齐。
- 回退：仅本轮 codec/test/docs；无用户数据 schema 变化，不卸载或清记录。

## 本轮离线结果

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew --offline :protocol-core:test :device-session:test :bluetooth-android:testDebugUnitTest --tests io.openhoyi.bluetooth.LunarConnectionPathTest --console=plain`：BUILD SUCCESSFUL，26秒。
- 协议 verify 报告39,827项既有计数检查与32,856条历史通知回放；本轮另加组合记录排列、所有已知截断、未知后缀、任意分片边界、高位拒绝及单位原值检查。
- device-session JUnit：774 tests / 0 failures / 0 errors；会话自检103项通过。包含真实Lunar通知与构造组合事件的鲜度、单位撤销恢复、重连隔离、控制禁用验证。
- 蓝牙链路4项全部通过；`git diff --check`及`python3 scripts/git_policy.py branch`通过。
- 日志 `/tmp/acaia-completion-tests.log`；报告位于各模块build/test-results。
- 本轮没有实际设备操作、安装、发包或PR；已发出的原Lunar测试APK不包含本轮扩展。Astra 已只读复核全部 diff、untracked 文件及关联会话/stream，无阻断项；仅修正文案，不改变测试过的逻辑。

## 用户授权推进 PR

用户于本轮明确批准 Lunar 修复先合入。当前来源仍为最新 origin/develop b8cf9869；仅本任务 codec、回归与证据文件提交，通过 scripts/create-pr 本地门禁创建 PR，必要 CI 全通过后普通合并，不绕过保护。实体 Lunar 验收仍待用户反馈，不将合入或自动发布当作实秤验收。已另行授权发送真实 BLE Debug 测试 APK（基于本基线及工作树补丁，versionCode85、原签名）；未安装或操作设备。
