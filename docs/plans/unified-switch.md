# 全App统一轻微内凹胶囊Switch

- 需求与范围：已确认参考图A。8处Switch统一原生Switch子类视觉；不改业务回调/持久化/异步状态。140ms标准非线性曲线，同步轨道颜色，尊重系统动画缩放/关闭。保留原生键盘/无障碍/RTL/拖动语义及至少48dp触控。
- 来源分支/基线提交：develop / 56e3b9ac（最初基线df108928，提交前同步最新develop）。
- 工作分支：fix/unified-switch；PR目标：develop。
- 改动文件与步骤：HoyiSwitch集中样式和视觉动画，替换8处构造，移除原有tint；自动更新卡片由其负责工作树接入HoyiSwitch(context)。
- 推送与合并：用户已授权提交、推送、创建PR；先经完整本地门禁，以scripts/create-pr创建draft，独立复核与必需CI通过后由协调者安排合并。遵守[Git工作流](../git-workflow.md)。
- 验证证据：完整本地门禁、针对性组件回归及用户平板视觉动效验收；未做萃取硬件操作验收。
- 协议与设备风险：无协议、队列、固件改动；保留真实checked和原有失败恢复，不新增设备操作。
- 发布：不发版、不改版本/签名；本提交整理阶段不额外安装或操作设备。
- 回退：回退本任务代码，无数据结构迁移，不卸载不清记录。
- 未完成与阻塞：等待最终独立复核及PR必需CI，由协调者安排合并顺序。

## 实现与验证记录

- 全局主源码盘点：8处构造全部使用 `HoyiSwitch`，未发现XML Switch；锅炉Button和schedule CheckBox不动。
- 视觉：40×22dp胶囊，细边与浅上缘渐变，白色thumb；浅/深ON/OFF分别有色相和位置区别，disabled另降透明度，不将OFF当禁用。
- 动画：实际140ms `PathInterpolator(0.2,0,0,1)`，轨道同步插值。主动停止不可见的原生250ms动画；触摸期间只读取原生Drawable bounds，原生独立裁决drag/slop/fling/cancel。转场中再次按下会先完成旧动画，避免可见与命中位置不同；不排队不弹跳。系统动画缩放由ValueAnimator处理，关闭立即到真实终点。
- 业务：原有listener完全保留；setChecked返回后读取最终isChecked，覆盖同步保存拒绝；不写偏好，不引入乐观业务状态。深色偏好保存后的既有立即recreate会截断旧View动画，新View直接正确终态，未为动画擅自延迟业务。
- 针对性设备入口：`BrewAudioInstrumentation` 参数 `unifiedSwitchChecks=true`，离屏验证浅深ON/OFF/disabled、RTL、≥48dp、拒绝保存、原生恢复、重复点按；无偏好/服务/设备写入。该入口已在集成QA平板执行PASS；MIUI离屏夹具增加WRAP_CONTENT LayoutParams，避免RTL设置触发空布局参数异常。
- 已通过：`:mobile:testDebugUnitTest`，117 suites / 622 tests，零失败；`python3 scripts/git_policy.py branch`；`git diff --check`。最终`:mobile:assembleDebug :mobile:assembleMock :mobile:assembleMockAndroidTest :mobile:lintDebug`通过（BUILD SUCCESSFUL，lint零Error/Fatal）。
- 设备证据：集成QA `switch-test-rerun.txt` 返回 `UNIFIED_SWITCH_PASSED lightDark=true states=true rtl=true rejectedSave=true restoration=true repeated=true noBle=true`；用户已实机验收外观与动效并明确表示「很棒」「开关这个可以提交了」。HoyiSwitch与集成验收源码逐字节一致。TalkBack及系统动画关闭等未单独执行的检查不宣称通过。
- 并行自动更新卡片：其工作树自行将 `Switch(context)` 替换为 `HoyiSwitch(context)`，避免重复tint；pending/成功/失败由原有逻辑保持，本工作树没有该卡片，不能宣称已验收。

- 提交前最终完整门禁：`scripts/verify-pr-checks full` exit 0；含Python规则、本地化、全模块JVM、Debug/Mock构建、lint、APK资源与一次性签名Release fixture（无安装）。
