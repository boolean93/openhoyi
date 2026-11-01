# single-screen 同 run 成对 APK 共享试验

- 需求与范围：只把 single-screen 三次相同 Gradle 构建改为一次同 run producer，原三个 profile/检查名、完整矩阵/脚本、focused 历史12配置、超时、触发与权限不变。capture 修复独立先运行；不共享 required/upgrade/probe 或跨 workflow 产物。
- 来源分支/基线提交：develop `8967cc328a747fb25ce878aecd9cea6292a792a2`，现有 capture 提交 `730721ad8620cb8773eb71673d4851da332a0e2e`；PR6 的现有运行不因本草稿提交而重启。
- 工作分支：按本轮授权复用 `fix/ci-page-service`，不新建分支；Scale 两草稿继续隔离。
- PR目标：develop；由 root 协调 PR6 当前 capture 验证完成后的普通推送时机，不直接推 develop。
- 改动文件与步骤：仅 single-screen workflow、新 `shared_mock_artifact.py` 及离线测试、本计划。producer 保原 checkout 事件语义（PR merge SHA）、原 JDK17/SDK35/Gradle命令；生成仅 app.apk/test.apk/manifest.json 的不可变 artifact。consumer `needs` 成功及完整 outputs 后显式 checkout producer SHA，按本 run artifact ID 下载至空目录，独立比对 needs manifest SHA、平台 repo/event/ref/run/workflow/PR 身份、SHA/tree/配置指纹、producer attempt、命令/properties/variant、实际工具链与两 APK hash/size/package/version/SDK/runner/apksigner debug证书。全通过才恢复两个原安装路径；不运行artifact脚本，不fallback重编。
- 安全与失败传播：固定官方 upload v6 SHA `b7c566a772e6b6bfb58ed0dc250532a479d7789f`、download v7 SHA `37930b1c2abaa49bbe596cd826c3c89aef350131`（2026-11-01 gh api 官方tag核实）；同 run 默认下载无额外 token/写权限。三 consumer 保 fail-fast:false；producer失败/缺输出显式fail，整 workflow取消仍取消。三文件白名单、符号链接/缺失/多余/损坏文件拒绝。manifest/hashes 提供传输一致性，不把不可信PR构建提升为发布信任。
- 工具链策略：首次试验严格比较完整 JDK版本和 runner镜像版本及实际 Gradle/aapt/apksigner/platform/build-tools/adb 指纹。跨镜像滚动或重跑可能安全拒绝合法产物；这是有意 fail-closed，不能宣称已支持跨镜像，遇到时以证据决定是否区分诊断元数据，不能删来源/hash/签名门。
- 推送与合并：独立 Astra 审查 → 仅本任务文件独立提交 → root 在 capture 结果明确后协调普通推送 → 新精确 head CI 三 consumer 全量实测。原四 required/保护不改。
- 验证证据：8项离线正负测试覆盖错run/SHA/tree/config/attempt/命令/版本/runner/证书、缺失/损坏/交换APK/多余文件/符号链接、producer失败/取消、manifest输出hash；真实本地两APK build 12s成功，SDK35 aapt/apksigner 对实际app1/0.1.0、test空版本、两个runner/targets及配对Debug证书解析通过。证据位于 settings-evidence/ci-diagnosis/shared-local-apk-build.log 与 shared-local-apk-inspection.json。本地含保留Scale草稿，真实APK解析只证明解析器兼容实际格式，不冒称该本地APK对应干净CI源码或GitHub跨job实测。
- 测量：以本轮 capture 基线730中完整成功的同一三个 profile 为首选对照（或已完整成功f56），用 Jobs/Steps API 保存各job起止/排队/setup/构建/传输/校验/模拟器/脚本/上传时间；candidate报告producer+3consumer总runner秒及触发至最后consumer墙钟、排队分别计量。失败早退不作为完整时长对照。三consumer必须同artifact ID/两APKhash/源SHA且原case数保持，不能预承诺墙钟更快。
- 已保存完整成功基线：f56 run41273605500 三profile总runner 2423秒、Gradle521秒（142/206/173）；运行创建至最后完成1021秒，各job排队2/11/12秒。仅为基线，不是共享candidate收益；原始jobs/summary在settings-evidence/ci-duration-audit/shared-baseline-f56-*.json。
- 协议与设备风险：不改生产、Mock频率/Tracker、BLE/设备/数据；仅 disposable CI模拟器安装，不碰用户平板/v45。
- 发布：不发版、不引入release密钥、不打用户安装包。
- 回退：恢复原三consumer各自Gradle步骤并移除producer下载，保capture及全部产品修复/数据/工作树。
- 未完成与阻塞：真实GitHub producer→3consumer、失败传播可见结果及净收益待受控CI；本地单测/签名校验不替代这些验收。若需改变触发/保护/检查名/质量合同则另报，不在此实施。
