# 机器计时证据与记录历时

- 需求与范围：历史主指标使用本杯机器整数计时，保留包含结束确认的记录历时，兼容旧记录；图表仍使用本地单调采样时钟。
- 来源/工作/目标：origin/develop 92cf2f9e → fix/shot-time-evidence → develop。
- 步骤：series 收集独立计时证据，按杯 ID 附加至历史，贯通长期记录与导出，更新中英文展示并做针对性单测/编译。
- 推送与PR：本轮不推送、不创建PR；后续按 Git 工作流与 create-pr 入口。
- 验证：针对 27s/31.2s、旧记录、新缺失值、跨杯隔离、时钟跳变与中断场景；相关 JVM、编译与资源校验。
- 协议/设备风险：不改协议、停止控制或 2800ms 安全确认；无设备操作，不宣称硬件验收。
- 发布/签名/升级：不发版、不安装、不改变包名签名版本；仅增加可选存储字段，旧记录保留。
- 回退：回退代码，保留记录；旧客户端无法读取扩展历史行，降级前须保留数据副本，不清数据。
- 未完成：实机界面与真实萃取未验证。

## 本地验证结果

- `./gradlew :mobile:compileMockKotlin :mobile:testMockUnitTest --tests '*ShotTimingEvidenceTest' --tests '*ShotHistoryTest' --tests '*ShotSeriesTest' --tests '*BrewJournalTest' --tests '*ShotHistoryArchiveTest'`：最终通过，36 个针对性 JVM 测试；涵盖 27s/31.2s、旧行兼容、新缺失证据、当前杯隔离、墙钟跳变不改变机器计时、journal schema1读取/schema2往返、终态二次写失败后补齐、ZIP 整秒与口径导出。墙钟跳变测试不表示旧 elapsedMs 已单调化。
- `python3 -m unittest discover -s localization/tests`：25 个通过。
- `python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`：通过，英文 997 键完整、生成资源无漂移。
- `git diff --check`：通过。
- Astra 独立只读复核通过；相关恢复与比较标签问题已修。编译早期发现的布局 helper/Fragment receiver 问题已修；一次重叠 Gradle 构建产生 classpath 缺类，最终串行重跑通过。
- 未提交、未推送、未创建 PR；无安装、设备控制、APK 发布或完整 CI/实机界面验证。

## 后续确认：机器计时缺失时的采样覆盖

- 机器计时优先；缺失时使用同一杯最早/最晚有效萃取采样的单调时间之差，标签为“采样覆盖”。至少两个不同时间点，单点/无样本缺失；中间断采后恢复不重置起点、不扣缺口，不以最后点相对记录起点时间代替覆盖。
- series独立保存首末边界，避免图表降采样改变边界；新杯清空，不合并不同ID。真实断连若既有控制层结束/放弃本杯，本轮不改变其身份与安全判定。
- 持久化 `observedSampleCoverageMs`，贯通长期复盘、分享与ZIP导出；journal schema3兼容有内容的schema1/2。
- 旧样本仅 `brewing=true` 可证明有效萃取，未知阶段不推断。复用三处既有异步样本读取，在已附着视图的主线程回填当前杯并通知长期档案，更新当前标签/数值；不启动扫描全部历史、不后台无锁修改history。无明确旧样本保留旧口径。动作打开详情时重取最新元数据，避免闭包旧快照。
- 详情辅助说明明确：首末有效采样间隔，可能含缺口，不代表完整萃取时长；旧记录主值改为覆盖后仍保留旧记录历时小字。
- 补充 `ShotSampleCoverageTest` 5例：gap恢复、断头边界、单点/重复/未知阶段、重启回填与长期往返、机器优先、图表降采样边界；补schema2有内容迁移及ZIP覆盖字段断言。

### 新增方向的最终验证

- `./gradlew :mobile:compileMockKotlin :mobile:testMockUnitTest --tests '*ShotSampleCoverageTest' --tests '*ShotTimingEvidenceTest' --tests '*ShotHistoryTest' --tests '*ShotSeriesTest' --tests '*BrewJournalTest' --tests '*ShotHistoryArchiveTest'`：42 个测试零失败，编译通过（最终日志 `/tmp/shot-coverage-final.log`）。
- localization 25测试、catalog校验（998键）、生成资源无漂移、`git diff --check` 再次通过。
- Astra新增量只读复核无阻断。无实机/界面运行、安装、设备控制、提交、推送、PR或发布；旧样本按查看回填，未查看记录不会启动批量扫描，缺失/未知阶段不外推。

## 创建与合入授权

- 用户随后明确授权本计时修复创建PR并在必需检查/审查通过后合入develop。当前来源仍为origin/develop `92cf2f9e`；不触碰并行一键萃取PR。
- 通过仓库 `scripts/create-pr` 完整本地门禁创建PR，精确头提交必需CI通过且无未解决讨论后常规合入，不绕过保护。若develop变更则核对完整差异，必要整合冲突、相称重测并复核。
- 仍不授权设备操作/安装、手工发布或标签；合并后只读检查既有自动发布状态。早先“本轮不提交/不推送/不创建PR”仅记录当时边界，本节为更新后的授权。

- 完整PR门禁发现3处资源数量快照仍为原994/993；本次新增4键后改为998/997，逐键、格式、去重与品牌覆盖断言保持不变。追加修正后重跑完整门禁，不能用先前目标单测替代完整验证。

- 创建PR #67后，一键萃取PR #66先合入develop `5ac1a611`。本分支正常merge该基线，不重写历史；MobileService自动合并无冲突：#66仅新增快捷启动许可/snapshot修订跟踪，本计时修复仅在finishSeries附加证据，原安全控制保留。合并组合需再次通过完整本地门禁与精确头GitHub必需CI，并独立复核共享服务语义。
- PR创建前第2轮完整门禁已通过，包括全部JVM/Debug与Mock构建/lint、APK资源3133项、12项release配置与隔离签名fixture（无安装）；GitHub PR正文由入口保存精确头/基线与命令。
