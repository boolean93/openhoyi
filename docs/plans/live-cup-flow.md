# 实时杯流速

- 需求与范围：实时四指标将秤重曲线/绿色轴改为秤重估算 FLOW g/s；复用 CupFlowProjection，不改变总重量、控制、存储及历史重量。
- 来源分支/基线：develop / 4b5cdc2；按用户当时要求保留现有 develop，不新建分支。
- PR目标/推送/合并：本轮无提交、推送、PR、合并；改动保留工作树。预存 .learnings/ERRORS.md 不覆盖。
  （历史事实：正因为未提交，后续从 develop 构建的 v53/dev-55/dev-56 覆盖平板后，流速显示丢失；恢复见文末。）
- 步骤：显式历史/实时四指标集合；实时 current/reference 同投影；同步轴、图例、无值、inspect与文案；最小投影测试、Mock编译与授权Mock实屏检查。
- 验证：历史默认不变；匀速及参考同单位；断秤/减重/非brew断线与空轴；范围扩展不收缩。
- 协议与设备风险：仅显示投影，无协议/控制/存储变更；秤样本沿用机器通知时间，时间精度有限，非机器 mL/s。
- 发布/升级：不发版。仅用户授权Mock保留数据递增版本覆盖安装；先核验签名/包名/版本，不操作Debug/真实机器。
- 回退：撤销本任务显示修改；设备不卸载、不清记录，必要时递增版本恢复。
- 验证结果：30项相关单元测试通过；Mock v51及MockAndroidTest编译通过；git diff --check通过。证据：工作树同级live-flow-delivery（APK/patch/build.log/tests.txt/签名/元信息）。
- 未完成与阻塞：ADB无在线设备，未安装、未实屏截图、未验证设备原签名与升级；未操作Debug或真实机器。

## 后续平板授权与验收

用户明确授权“双包都装，验Mock”。同源Debug与Mock版本51已install-r，原签名匹配，旧数据文件无丢失（Debug两项运行状态文件有更新，详同级live-flow-delivery/tablet/acceptance.md）。Mock正常实时四轴FLOW g/s、总重量、历史g已截图；自然结束并留正常页。暂停/缺秤实屏未测，无真实硬件验收。无提交/推送/合并。

## 2026-11-06 恢复并合入 develop

- 来源：从未提交工作树补丁 `source.patch`（Codex 任务副本 `.../live-flow-delivery/`）恢复到当前 `origin/develop` 0cef488，新分支 `feature/live-cup-flow`。
- 差异：同旧补丁；仅补齐 ar/es/ja/ko/ru/th 的英文回退 `live_flow.xml`，移除不在 `LANGUAGES` 配置内的 `values-zh-rCN`，避免编译资源校验出现多余配置。
- 验证：FourMetricChartProjectionTest 22、CupFlowProjectionTest 6、ChartInspectionTest 2 全部通过；`localization/android_resources.py --check`、`localization/validate_catalog.py`、`localization/tests`、本地 `verify_apk_resources.py` 通过（11513 条编译字符串匹配）；`git diff --check` 通过。
- 设备：同签名递增版本 v57 覆盖安装 Debug + Mock（用户授权），签名 c0f426…，包名不变，firstInstallTime/UID 与原数据保留；Mock 实屏确认实时四轴为 压力/累计注水/流速 g/s/温度，历史仍为秤读数 g。
- 目标：PR → develop；与旧记录不同，本日按用户要求走 PR 合入，避免改动再次只留在未提交工作树。
- 未验证：真实机器与秤的物理效果、暂停/缺秤实屏专项；Mock 不证明硬件行为。
