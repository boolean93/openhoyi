# 官方 HOYI 截图曲线提取指导

- 需求与范围：复用截图导入规范，为 App AI 补齐官方模式、单位及时间语义；忠实转录，缺项先问，不擅自优化配方。
- 来源分支/基线提交：develop / bee4d968。
- 工作分支：fix/ai-hoyi-curve-guidance；PR 目标 develop。
- 改动文件与步骤：AI 提案工具参数中的紧凑 HOYI 知识、系统路由提示、代表性工具契约与原生编辑保存回归。
- 推送与合并：本地检查后由协调线程按 [Git 工作流](../git-workflow.md) 创建草稿 PR，精确头提交 branch-policy、verify 通过后合入；不运行本地 AVD。
- 验证证据：相关 JVM 单测、完整 `./scripts/create-pr --check-only`；不调用付费模型，离线假响应不证明模型识图质量。
- 协议与设备风险：不改协议、机器执行和授权门禁；提案只打开编辑器，必须用户显式保存。新增截图按 schema3 保留模式位与时间，不降级为基础曲线。
- 发布：不发版、不安装、不操作设备；签名、版本和升级行为不变。
- 回退：回退本次指导与测试，不清除任何曲线或历史数据。
- 未完成与阻塞：独立复核与 PR 检查由协调线程跟进。

## 语义来源与限制

- 复用 [截图转录规范](../curve-import/photo-to-json.zh-CN.txt)；适配 App 提案工具时不输出导入文件 ID/裸 JSON，使用工具的 `operation`/`curve` 包装，ID 由 App 分配。
- Astra 本轮只读核对官方 `1.2.4.apk`：SHA-256 `401afbd9d36494f5acc41b0c15f7dae5a59684573ecdc5d905c8fd8ed40ace21`；其中 `assets/apps/__UNI__7D80DAB/www/app-service.js` SHA-256 `70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1`。
- 该 bundle 第 8 行偏移 322500 为模式模板，325988 为首段 `time1` 输入；第 14 行偏移 671132 `segTargetUnit`、675740 `pSwChange`、687075 `saveTempChart` 对应单位、模式位及 `time1`/`time`/`flow`/`weight`/`wdelta` 保存。偏移只适用于上述精确文件。
- 既有证据：[变流编辑](../evidence/2026-11-11-flow-curve-edit.md)、[官方控制模式](../evidence/2026-11-13-official-control-modes.md)。不采用有修改痕迹的旧 readable 拆包作官方原版依据；不提交 APK 或用户截图。
- 日晒代表图：92℃、总流量78mL、总液重0g、补偿0g、闷蒸5s、变压/跟随；三段2/6/9bar与25/5/48mL，首段时限10s。编辑图连线不是实测时序或线性升压证据。总量限制也不构成每一阶段无条件硬停止保证。
- 离线回归验证知识实际进入工具描述、schema3字段和编辑保存边界；不证明线上模型识图准确率、机器执行效果或口味等同。
