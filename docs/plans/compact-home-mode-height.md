# 修复短横屏首页模式区高度回归

- 需求与范围：修复 PR #93 后 720×360dp 首页快捷槽位被裁切，导致 Mock lifecycle 失败并阻断 Develop APK 发布。仅调整短横屏布局，不降低原验收断言；同步修正 #88 后生命周期脚本仍匹配旧开关状态标记的误报。
- 来源分支/基线提交：develop / 0cab39347a353a23bfc0c4b170411d37e78fa4fb。
- 工作分支：fix/compact-home-mode-height；PR 目标 develop。
- 步骤：短横屏保留原机器状态/上一杯的宽度分配，模式控件复用快捷标题行，萃取时仍保留禁用模式控件；其它屏幕保持原布局。
- 补充根因：机器模式每250ms无条件重设相同文字会清空TextView layout；改为文字有变化才更新，并验证重复render保留layout。短横屏相对margin即时解析并断言实际16dp间距。
- 测试契约：快捷萃取已批准为默认开启、首页无开关，脚本匹配 defaultOn=true/homeToggle=false，并增加脚本与 instrumentation 完整成功标记一致性的本地回归。
- 验证：本地完整 scripts/create-pr 检查、独立代码复核；GitHub Mock lifecycle 完整几何断言，检查常规横屏/大字号等现有回归。不得以关闭检查解除发布阻碍。
- 协议与设备风险：不修改编码、门禁、命令、重试或停水；不操作真实机器。
- 推送与合并：遵守 [Git 工作流](../git-workflow.md)，普通推送，scripts/create-pr，必需检查通过后合入。
- 发布：仅既有 Develop 自动预发布，保留四项 push 检查与原签名/版本/升级规则，不手动打标签或替换附件。
- 回退：revert 本 PR，不修改用户数据。
- 未完成与阻塞：完整本地检查、CI 和发布验证待执行。
