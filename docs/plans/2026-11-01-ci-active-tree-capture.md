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

## 730721a 首轮运行与初连修正

- PR6 exact `730721ad8620cb8773eb71673d4851da332a0e2e`：capture run41277479694 在 home-light/curves-light 两次真实树及PNG成功后，05:41:12 新连接立即 activeRoot 为空；同源 lifecycle push run41277479789 在首次 selector 05:40:05 失败。失败时 Main topResumed/mCurrentFocus 正确，尚未进入两杯连续性验收，不能称runtime通过。
- 最小修正仅初始窗口readiness：先注册事件通知，再设置service flags，通过公共getWindows建立真实interactive window元数据；只有初始root为null时，在host传入的剩余原30秒预算内等待实际accessibility事件并再次查询。generation避免丢通知；不等全局idle、不固定sleep、不重启App/杯、不重跑已开始/失败的capture。非null root立即保原包/窗口/完整遍历校验；serialization后的root仍只读一次比对，不重试掩盖变化。XML保留就绪等待/查询次数/实际window信息。
- 本地30项host契约PASS，新增remaining budget参数门；原选择/页面/PNG/30秒deadline均不变。Android首次连接与两杯真实连续性仍须新exact head CI，不把编译当运行验收。

## 4e7e252 第二轮与正尺寸初始root

- exact4e7 capture run41278328568：静态图越过上轮curves-light，直到settings-dark后下一selector在05:56:35报`Empty Mock root bounds`，Main仍topResumed/focused。没有两杯验收，不能merge。原异常未标before/after，故不能凭错误文本断定阶段。
- 最小追加：初始readiness除nonnull还要求真实Mock root可见且与真实display交集正尺寸，归还未就绪node后只等真实accessibility事件，仍消耗原host30s；外包/未知window立即原失败、不等待。所有正bounds/完整树/after同窗及单次after读取保留。每个失败输出确切阶段和当前root/window原始元数据，以区分初连未就绪与序列化期间变更；不接受空树、不跨失败重采。
- 本轮共享producer及三个consumer传输已实际运行，仍等待完整矩阵。该capture追加不在矩阵完成前push，以保留同一轮净耗时证据。

## b1659d3 真实历史XML格式

- exactb165 manual capture run41279643363越过root初连与全部静态/选中/准备/确认页，真实Mock开始后，连续性脚本在解析history时失败，尚未采运行图/两杯。
- 精确`continuity-dark/before-history.xml`只有一条10列RUNNING、capture-2、observed=true记录；Android SharedPreferences serializer在末尾`&#10;`后输出4空格再关闭string。ET保留其text，splitlines产生额外纯空白行。原host把格式空白当成未知业务schema；不是应用写坏数据，也不是已证实断杯。
- 最小正修忽略纯空白格式行；非空未知行继续fail，不跳过未知业务。已知合法8/9/10列按生产ShotHistory.decode兼容，旧列默认observedRunning=false；真实新杯仍必须唯一RUNNING/capture-2/observed=true，身份/owner/PID/结束/样本跨区间门完全不改。增加真实escaped-tab/newline/缩进正例及非空坏行反例，避免裸放宽。
- 不写/删任何历史记录；只host parser与契约测试/本计划。原paths内capture入口脚本也未改，helper未来仅变更仍需既定dispatch；本次host continuity-only同样需手动原workflow验证，保触发范围不变。

## f9da139 公共后端作用域与运行页真实语义

- f9 push lifecycle run41280817377在collector after-window阶段触发原同窗失败；只能确认before/after元组不同，原before信息不足以判断具体字段，不把staleinitial作为已证因果。该公共脚本原调用仅静态nav/theme，active-capture需求不应无必要替换所有consumer。
- 按本轮明确授权恢复默认8967静态uiautomator backend（remove旧dump→真实dump→cat，缺文件失败不复用）；capture_mock_ui.sh整体显式active-tree，覆盖所有tap及assert_page，没有自动fallback/按页面偷偷选择。invalid backend在任何ADB前失败。保原15与所有新增检查，明确backend证据，不把静态后端绿称新helper稳定。
- f9 manual capture run41280866098已真实开始并成功取得91节点running XML，失败在原页面marker。实际唯一Button为`立即停止萃取`（源码home_stop一致），enabled/clickable/visible=true，rect[306,1321,2488,1447]，不是旧`立即停止`。当前迁精确全文，且额外共同要求唯一可达stop、RUNNING guidance、capture-2、完整Mock/不发蓝牙clock和before真实observed RUNNING杯。没有点击停止，也不以模糊contains“停止”充当页面证明；其余页面及Curve hint门保留。
- initial root新增唯一同ID、active、TYPE_APPLICATION正touchable-region的metadata关联。WindowInfo bounds是触碰区域而非根布局rect，不能强制两者相等；focused记录不硬加。原root正可见与after单次同窗门保持。initial/before/after根元组及窗口metadata分别保留，失败可识别变化字段；这是严格关联/readiness和诊断增强，不宣称已确认或实测修复此前after变更因果。
- 本地39项host回归、bash-n/diffcheck、测试APK编译36s通过；Astra独立复跑并用f9真实XML/before跑新运行页门通过。仅静态/保留XML验证，仍须新head完整两杯/原后续Native检查及required，不能用旧数据替代运行验收。
- scope/marker host与Native关联分别提交、一次普通push；本次修改既有capture及mock_ui_tap入口，按原paths会自动触发capture，先核自动run避免重复dispatch。保留所有此前失败及Scale两草稿，不改生产/触发/保护。
