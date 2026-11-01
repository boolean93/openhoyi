# 活跃 Mock 页面真实树采集

- 需求与范围：修复截图任务运行中页面的 XML 采集，保留原截图、页面、主题、唯一选择、真实启动/结束杯、历史及主导航检查；不暂停遥测/动画，不接受空、旧或部分 XML。
- 来源分支/基线提交：develop `8967cc328a747fb25ce878aecd9cea6292a792a2`（PR4 已合并）。
- 工作分支：按本轮明确授权复用 `fix/ci-page-service`；已有 Scale 两份草稿保持未提交，不混入。
- PR目标：新 PR → develop；不直接推 develop，不复用已合并 PR4。
- 根因证据：`settings-evidence/ci-diagnosis/capture-postmerge-8967.log` 中 18 张静态图成功，开始模拟后 `uiautomator dump` 报 `could not get idle state`。公开 DumpCommand 先要求 1s accessibility idle，活跃 250ms UI 不保证该静默条件；日志不能确定具体事件源。
- 改动文件与步骤：测试 APK 增加无 Activity、仅 Android 公共 API 的 self-target instrumentation；直接读取真实 active root；nonce/原子文件、同包/窗口/bounds/完整遍历及结束根复核。原 runner 仍 target Mock，新增 runner 仅 target test 包，检查实际打包 manifest 和设备安装表。host 维持单次 30s 总预算、前后 foreground/PID 校验、无 fallback；原选择和页面断言不删。运行杯前后读取真实 PID/ServiceRecord/持久化 RUNNING 身份，结束后验证同杯 ENDED 及严格递增样本覆盖采集区间；不假定 Handler 精确频率。
- 触发范围：workflow 的原 on/paths 不变。本次既有脚本与 workflow 步骤修改会触发；未来 helper-only 修改不在既有 paths 中，须 workflow_dispatch 或同步入口修改。新增 paths 是独立建议，本次不实施，也不把测试类移入生产规避过滤。
- 推送与合并：本地验证 → Astra 独立审查 → 独立 capture commit → root 普通推工作分支/新 PR → 精确新 head CI 验证；共享构建另一个提交，便于归因。
- 验证证据：原15 selector 回归全部保留，新增 nonce/window/process/manifest/continuity 正负契约测试；`:mobile:assembleMockAndroidTest` 已成功，打包 manifest 两 runner targets 已核。最终日志与独审结论补入提交交接。无本机 emulator，不使用用户平板；采集 runner 是否能访问真实活跃树、PID/service/cup/sample 连续性必须由新 CI 实测，不能以编译替代。
- 协议与设备风险：仅 test APK 和 CI host 脚本；不改生产、BLE、单位、队列、采样频率或 Tracker。采集进程自 target，禁止复用原 Mock-target runner采集单帧以免重启杯；设备证据门不满足即失败保留。
- 发布：不发版、不安装用户设备，不混 v45/曲线分支。
- 回退：仅回退该采集提交；所有业务及未知记录保留，不卸载/清数据。
- 未完成与阻塞：真实 Android CI 运行待执行；原路径过滤的 helper-only 缺口明确保留待另行决定。
