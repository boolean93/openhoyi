# 上一杯显式一键萃取

- 需求与范围：上一杯「使用」复用五槽位的一次性直启流程；关闭设置仍只导航。保留历史、草稿指纹、温度及服务门禁，不增加预热或机器操作。
- 来源分支/基线提交：origin/develop df108928。
- 工作分支：fix/last-shot-direct-start；PR目标 develop。
- 改动文件与步骤：提取共享入口与源有效性检查；捕获上一杯历史身份；补单测及 Mock 实窗用例。
- 推送与合并：本轮不推送、不创建PR；后续按 docs/git-workflow.md，通过 scripts/create-pr 和必需检查。
- 验证证据：针对性 JVM 单测、Mock/Debug 编译；实窗仅编译，不安装、不运行设备。
- 协议与设备风险：不改协议及启动流水线；历史/页面/设置变化撤销许可，不队列、不重放。机器拨杆入口不变。
- 发布：不发版、不改版本签名、不安装、不操作机器。
- 回退：代码 revert，保留历史、曲线与设置，不清数据。
- 未完成与阻塞：独立 Astra 复核及已授权后的实窗执行由协调者安排。

## 本地验证（2026-11-12）

- `python3 scripts/git_policy.py branch`、`git diff --check`：通过；现有 `core.hooksPath=.githooks` 未修改。
- JDK 17 / `ANDROID_HOME=/Users/boolean93/Library/Android/sdk` 下执行 `./gradlew :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMockAndroidTest --console=plain`：最终串行运行通过，115 tasks（15 executed）。Mock JVM 共 118 suites / 631 tests，0 failures / errors。
- 新增历史身份变化/历史不可读的许可撤销单测；扩展已有 Mock 实窗检查：上一杯关闭仅导航、开启冷机仅导航不预热、不接受旧历史身份。实窗代码编译通过，未执行。
- 首轮因实现者重叠启动同工作树构建出现 Bluetooth 类型 classpath 错误；后续构建及最终无并发串行运行均通过。后续同工作树必须串行构建，不能把中途错误当源码缺陷修改无关文件。
- 未安装、未操作设备、未 push/PR、未发版。待 Astra 独立复核，设备体验验证另行授权。

## 授权推进与整合（2026-11-12）

- 后续用户授权：提交 PR，并在检查通过后合入；协调者负责 push、PR、合并。本实现者仅提交本任务、merge 最新 develop 并运行本地完整门禁，不安装、不操作平板。
- 先提交原任务 `0cab2d39`，再以 merge（不 rebase）整合 `origin/develop 56e3b9ac`，合并提交 `fd527495`。自动合并后核对 `HomeDirectPresetSlots.visible(showLever)` 与 `layoutDirectPresets(leverRecord != null)` 均保留，隐藏空拨杆卡行为未丢失。
- Astra 已独立审查；协调者转交的整合 QA 记录明确无新增阻塞。本分支的最终夹具清理修正仍需本地完整门禁。
- 实际 Mock 平板实窗证据（由协调者提供）：`openhoyi-pending-qa-batch/build/tablet-qa-evidence/last-shot-dark-fixed.txt`、`RESULT.md`。覆盖上一杯 OFF 仅导航、ON 低温只进入并提示、过期历史引用拒绝、设置同步与重建不重放；无 BLE、无真实萃取。该证据来自独立整合树（production `72c76469` / test `aba7658f`），不是本分支最终 SHA 的设备验收。
- QA 发现原测试的 `last-curve/use` tag 被既有 `quick-launch` 覆盖，现移除重复 tag，测试统一使用既有 `product/home/quick-launch`。
- QA 发现活跃 UI 回调在 finally 恢复后又写入 true：现先 finish、限时等待 `isDestroyed` 和主线程空闲，再恢复持久偏好并跨主线程 barrier 校验原始 key/value。此清理修正本轮仅编译与门禁验证，不重新操作平板；整合 QA 当时已停止 Mock 并核验恢复原偏好。
- 删除功能尚未合入本基线；继续使用当前 `CustomCurveStore.documents` 夹具，不引入其他任务的 state API。
- 体验限制保持：未验证真实硬件萃取/物理停水，也不声称完整竖屏、TalkBack 或手势动画矩阵通过。
