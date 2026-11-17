# 移除萃取反馈重播入口

- 需求与范围：删除萃取反馈“再听一次”按钮；保留首次自动鼓励音、完成按钮及现有音频生命周期。
- 来源/目标：develop b7c870d066d9a3d9bf327103f0f6adfe80f8322e；目标 develop。
- 工作分支：fix/feedback-remove-replay。
- 文件：BrewFeedbackDialog.kt 与中英文 strings.xml，仅删除按钮监听和废弃资源；本规划。
- 验证：现有 BrewFeedbackAudioTest / BrewFeedbackTrackerTest 定向回归、Debug Kotlin 编译、静态确认仅保留 autoplay 调用；结果交接时记录。不做无关完整矩阵。
- 推送与合并：本轮不推送、不创建 PR、不合并，交协调者决定后续。未来 PR 仍走 scripts/create-pr 及必需门禁。
- 协议与设备风险：不涉及机器命令、协议、反馈判定、偏好或持久化；不安装、不操作设备。
- 发布：不发版，不改变签名、包名、版本及升级边界。
- 回退：仅回退本次删除，不清数据或记录。
- 未验证：未实机试听或截图；自动音频行为使用既有逻辑及定向测试验证。

## 实际检查结果

- `:mobile:compileDebugKotlin :mobile:testDebugUnitTest --tests io.openhoyi.mobile.BrewFeedbackAudioTest --tests io.openhoyi.mobile.BrewFeedbackTrackerTest`：BUILD SUCCESSFUL，1m51s。
- 静态断言：无 neutral 按钮/监听；唯一 `audio.play` 仍在 autoplay 分支，配置恢复仍 `!restored`；完成按钮保留。
- 中英文 XML 解析与 `git diff --check` 通过。未安装、未推送、未合并、未实机试听。
