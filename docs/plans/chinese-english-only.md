# OpenHOYI 仅保留中文和英文

- 需求与范围：应用仅支持简体中文 `zh-Hans` 和英文 `en`。删除俄、泰、阿拉伯、日、韩、西班牙语选项、Android 资源、catalog/drafts、CI 组合和专用语言测试；中英文与非语言安全门禁保留。历史证据、历史规划不改写。
- 来源分支/基线：`develop`，本工作树起点 `0163a503`；整合者在交付后同步更新后的 develop，不在并行实现期间改基线。
- 工作分支：`feature/chinese-english-only`。PR 目标：`develop`，遵守 [Git 工作流](../git-workflow.md)。
- 改动与分工：Sol 实现 AppLanguage、偏好回退、JVM/仪器检查、Gradle 打包限制及当前产品支持说明；两个 Sol 工作者分别独占 `.github/`/`scripts/` 和 `localization/`/六语资源删除/站点语言入口核对。网站当前只有中文，无其它语言入口。
- 行为：缺失、非法或已撤销的持久语言均使用中文；不重写无关偏好、会话和业务数据。仅保留一次通用“不支持的持久语言→中文”升级场景。语言选择保留独立提交、失败不切换和原服务连续性。
- 打包：沿用现有应用内语言入口，不新建会被独立偏好覆盖的系统语言入口；Gradle 限制应用和依赖语言资源，验证最终 Debug/Mock APK 的资源配置，不只检查源码目录。
- 验证：精简运行语言/格式/偏好/升级契约相关检查、CI 脚本策略与机械资源校验；完成 JVM、Android 编译、lint、Debug/Mock APK 打包及实际 APK locale/品牌验证。编译与脚本结果不等于设备或视觉验收。无设备操作，不创建本地 AVD，不替代远端必需检查。
- 推送与合并：本轮只本地交付，由协调者同步、提交并处理 PR；不推送、不改远端、不替代 `branch-policy`、`verify` 必需门禁。
- 协议与设备风险：无 BLE 协议、编码、单位、队列、重发/停止门禁、持久未知记录改动；不操作机器、不安装、不清数据。
- 发布与升级：不发版，不变更签名身份、版本分配策略或发布门禁。旧语言偏好兼容回退中文；升级夹具保留加密凭证及所有原有数据完整性检查。
- 回退：通过新的 revert PR 恢复应用代码、资源和测试；不得卸载、清记录或重写历史。未知设备记录保留。
- 验证结果（本地）：
  - 先收缩语言身份测试，旧实现 7 项中 3 项按预期失败；移除六语后完整 JVM 回归通过。Debug 491 项、Mock 498 项均 0 失败，各 1 项既有真实旧曲线导出路径未提供而跳过（`LegacyCurveWireAuditTest.auditsUserExportWhenPathsAreProvided`）。持久无效值集合包含一个撤销语言标签，回退中文；升级夹具也只保留这一种通用旧值，不做六语矩阵。
  - `./gradlew --offline :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:assembleMockAndroidTest :mobile:lintDebug` 通过（首次完整构建 2m46s，最终输入复核 1s）；补充持久旧标签断言后的双 JVM 再次通过（18s）。所有 Gradle 命令使用本机 Java 17 及既有离线缓存。
  - 117 项相关 Python 检查通过，包括资源工具、CI/分片/隔离/截图门禁、语言反馈、升级及分支规则。可复现命令：`PYTHONPATH=scripts:localization/tests python3 -m unittest test_embedded_history_metrics_contract test_settings_matrix_isolation test_settings_shard_gate test_settings_language_error_contract test_settings_measured_dialog_contract test_settings_weekly_editor_route test_settings_observation_reuse_contract test_settings_shared_frame_reachability test_ci_workflow_policy test_settings_language_host_contract test_mock_upgrade test_git_policy test_android_resources test_validate_catalog test_apk_resources`。
  - 965 键源/英文 catalog 校验通过，964 条英文基础资源生成无漂移；`localization/verify_apk_resources.py` 对 Debug/Mock 各匹配 2897 模板、6 项 AAPT2 转义往返，并对整个 APK 配置（含依赖及非字符串资源）只允许默认/中英。未新增系统 localeConfig，继续使用应用内选择。
  - `.github/` 本身没有独立语言轴，无需删除工作流；实际语言枚举、脚本和成功计数收为 2 语：主要布局 96→24、advanced 240→60、单屏 336→84、初始/填充截图 64→16、footer 12→4。中文专项、14 个列表恢复、其它安全与发布门禁不变。Shell 和内嵌 Python 语法检查通过。
  - 两个过时测试预期按现有门禁修正：设置工作流实际只手动触发；历史 ZIP 原 `realMockService=false` 必须被当前服务门禁先拒绝，文本类别防伪仍独立精确拒绝。未放松谓词，未修改历史 ZIP。该 ZIP 复用原工作树只读文件，其 SHA 在测试中校验。
  - `git diff --check` 通过；当前产品支持说明已更新，带日期的历史记录保留。未执行仪器测试、真实升级、设备或视觉验收，未提交/推送；后续整合后的精确头 CI 由协调者处理。

