# 历史详情 C

## 当前合入范围与门槛（2026-11-16）

用户授权历史 C 单独 PR 到 develop，但须先完成真实多尺寸验收。仅带历史 UI 与相关检查，不带 AI、工具链、补齐资产或无用本地化资源。当前工作分支 `feature/history-detail-c`，fetch 后 develop 基线仍为 `fc309bc792218b6a01feeb6f780bb979cc99c42d`。最终独立页顶部中央自然宽图例与轴标题同带，范围点选可达；正常状态与采样/保存提示不常驻，异常、未完成、加载、无样本、失败反馈保留。

九个提取文件已逐一证明 AI 冻结包原始共享文件、AI 基线599670aa与历史基线fc309bc7内容一致，最终只带其历史改动。三方SHA与旧工作备份保存在本地 `openhoyi-local-materials/history-only-extraction/`。此前整合 APK 含 AI，不能作为本历史-only分支二进制，也不能把单平板实拍冒充多尺寸验收。后续可按协调方确定的手动CI方案提交与推分支；目前不创建PR、不合并、不发版，不操作设备。旧文档中的禁止推送/下移图例属于早期阶段，最新授权与此节优先。

- 需求与范围：曲线主导，紧凑图例、萃取数据下移；保持实时图配色线型刻度、轴单位、范围、采样与详情功能。参考三张本地 materialize 图片已亲自查看；C 为设计示意、extraction.png 为新版实时 Mock，均不冒充实拍。
- 来源分支/基线提交：origin/develop `fc309bc792218b6a01feeb6f780bb979cc99c42d`。
- 工作分支：`feature/history-detail-c`；独立工作树 `openhoyi-history-detail-c`。
- PR目标：develop；本轮不提交、不推送、不创建 PR、不合并。
- 改动文件与步骤：历史详情 Activity/Content、图表 standalone 专用布局入口；先绘图、后完整紧凑图例与数据；测量不足时自然纵向滚动，不裁切文字、不缩小触控区。实时和嵌入历史不改变布局。
- 推送与合并：本轮禁止；后续如授权，遵守 [Git工作流](../git-workflow.md) 和 create-pr 入口及必需 CI。
- 验证证据：相关 JVM 单测、Debug/Mock 编译、lint、资源与历史布局 contract；精确命令与结果交付时补充。独立 Astra 只读复核由协调方安排。
- 协议与设备风险：只读展示，无协议、持久化、重试、停止或设备控制变更；不操作 adb/平板/咖啡机、不用真实 Key、用户数据、付费 API、Computer Use。
- 发布：不发版，不改版本或签名，不安装。
- 回退：仅撤回本任务代码；数据结构与未知记录保留。
- 未完成与阻塞：禁止设备和 AVD，真实界面与触控验收待后续授权；离线结果不能替代真实视觉验收。

## 实现与离线验证记录

- 真实布局顺序：身份/状态与必要警告 → 同实时 renderer 曲线 → 完整紧凑图例（保留原 name/number/unit/range、48dp 点击区和样本检查）→ 计时/结束时秤读数/末次采样注水、采样状态、复盘/对比/完整详情。身份与长文本自然换行；宽度不足整体控制换行。
- 仅 dedicated standalone 使用新纵向滚动宿主，避开实时 workspace 的竖屏固定 320dp 路径。实际绘图区最小高度 160dp，剩余可用高度分配给曲线；自然内容过高可滚动，动作不压缩曲线。宽屏底部汇总与动作按真实宽度同行，否则逐组下移。轴布局与物理刻度算法未改。
- 图例保留完整范围文本。个人记录仍由实际只读详情弹窗显示，六项字段及原始流速采样详情没有移除。未修改历史记录模型、协议或设备路径。
- 独立 Astra 已只读复核，修复名称继承 MATCH_PARENT、仅高度变化重测、曲线证据下限及旧测试层级假设。
- `python3 -m unittest discover -s scripts -p test_standalone_history_header_contract.py`：16 项通过，包括丢失范围/行覆盖/触控区/恢复/真实曲线下限的负向检查。
- `python3 -m unittest discover -s localization/tests`：25 项通过。
- `python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`：通过，无资源漂移。
- `python3 scripts/git_policy.py branch`、`git diff --check`：通过。
- Gradle 命令（既有本地 JDK17/SDK；offline、no-daemon、max-workers=1）：`:mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:lintDebug :mobile:compileMockAndroidTestKotlin`。最终 `BUILD SUCCESSFUL in 3m 24s`，146 actionable tasks（31 executed、115 up-to-date）；日志 `/tmp/history-detail-c-gradle-final.log`。Debug JVM 780 项（1 skipped），Mock JVM 787 项（1 skipped），均零失败/错误；lint 成功，报告 0 errors、521 warnings；Mock 仪器测试代码编译成功，未运行设备。
- 没有运行完整 `verify-pr-checks full` 或无关发布矩阵。本轮无 PR/推送/合并。仪器测试仅编译，host contract 不代表真实 UI 已运行。
- 未验范围：真实 80:47 绘图区占比、各横竖屏/小窗/深浅色/三档字号实际像素与点击体验；无平板、adb、AVD 或硬件操作。不能据此宣称用户验收通过。

### 本地 APK（未分发、未安装）

产物对应本次未提交工作树内容，基线 SHA 不宣称为 APK 的冻结源码提交。

- `mobile/build/outputs/apk/debug/mobile-debug.apk`：SHA-256 `8710753c57f778a20133c6e439bd140f209f7d7a22964663a633a0de61e6fba9`。
- `mobile/build/outputs/apk/mock/mobile-mock.apk`：SHA-256 `385cdc398499794ea9746cf84e0c8c9a4f58153b3fc0b9df1e5241c8c30f211b`。

## 云端独审修复（2026-11-16，尚未完成真实验证）

- 修复固定轴越界提示的零尺寸布局，并把提示自然高度计入独立历史页滚动内容预算。
- 保留原生曲线说明控件，独立历史页只主动展示断点、注水重置及压力暂停异常；普通观察结束/操作提示不常驻。异常出现时取消图例与轴标题同行，按自然高度排列，避免遮盖或裁切。
- 仅正常 ENDED 且有采样时隐藏冗余采样状态；非结束且有采样继续展示 `history_samples_partial`，空样本与读取失败保持反馈。
- 个人记录不可用时恢复页面内提示；无永久记录但存储正常时仍使用原 `journal_migrate_hint`，不把迁入说明解释为未保存。
- 新增5项源码接线回归检查（不是渲染测试），37项历史 contract 全通过；25项本地化测试与资源校验通过。真实 CI 入口新增 fixed overflow、gap/reset、partial、journal failure 原生可见性/完整文本/截图断言，**尚未运行**。
- 工具链已可运行官方 JDK17 与 Gradle8.13 `--offline --version`；Android SDK 尚无且许可待明确，未安装。最终 Kotlin 编译、JVM/lint、7配置和真实 task resize 仍未完成。
- 当前只读/机械测试不能代替实际触摸或像素验收；现有 performClick 不代表物理触摸。无 PR、设备、AVD、发布或付费 API 操作。
