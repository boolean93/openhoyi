# Settings 模拟器证据容量最小计划

## 结论与范围

先采取**本工作流显式数据盘16G + 原始df/逐格用量取证**。不删设备/host原件，不改搬运校验和失败兜底，不缩减矩阵/PNG/proof，不提高heap或放宽时间。16G是有余量的候选，不是已经生效或完整矩阵足够的保证。

develop 来源的当前 `feature/task-first-ui` → develop，基线 b08adeca02326a6765973bf404385871af63c505。遵守 [git-workflow](../git-workflow.md)、[task-template](task-template.md)。Sol只改 `.github/workflows/settings-visual-ui.yml` 与 `scripts/check_settings_visual_migration.sh`；root统一验证/合批，不立即触发新CI、不微推。不改 settings_matrix_isolation.py 或其它工作流。

## 实证与预算

输入 `ci-4076c2d/settings-storage-diagnosis.md`：ENOSPC 在设备 `/storage/emulated/0`，非host。失败前已保存设备树5,810,937,124B，矩阵还未完；已搬运5,076,112,829B又由失败trap全树重复拉取，host原件合计10,887,049,953B，777MB压缩ZIP不代表容量需求。无旧df/inode数据，不能宣称已知原分区大小或已排除inode耗尽。

官方action本地原件 `ci-4076c2d/emulator-action-v2.yml` 与metadata，gitBlob `4f9767ce6c4ecbe37e35cf0c1246ec1c79dfd51a`，inputs明确支持 disk-size、G单位；当前workflow没有设置。依据其官方接口而非猜命令。16G约为已达原件下限的近3倍，但正常Machine后续特殊态、临时文件、其他应用数据仍未知。host还需AVD、构建、完整矩阵副本、失败全树副本及上传压缩临时空间；本轮保留重复兜底所以必须真实记录余量，不假装消除了host风险。

## 实施步骤

- [ ] 仅在 Settings 现 android-emulator-runner 的 with 下增加 `disk-size: 16G`；保 API34、ram2048M、profile、gpu、45分钟job、原脚本不变。不添加外置sdcard替代/data，不改通用action版本或直接修改既有用户AVD。
- [ ] 在同job模拟器启动前（构建后）增加只读host `df -kP`/`df -iP`，输出到既有 artifact 根的 capacity/pre-avd；不清理缓存或其他目录以换空间。保存执行时间和命令/退出码。此时路径只为主机证据，不标为设备容量。
- [ ] runner新增窄容量记录函数：使用当前 adb 连接和既有package/source变量，在 emulator已起且矩阵启动前、每格 after 完成之后、以及 EXIT collect 原pull之前/之后，写唯一阶段文件。成功格与失败格都留phase/theme/font/page/source/run标识；循环不会因为记录而重跑格。
- [ ] 每次记录原始命令 stdout/stderr/exitCode：设备 `df -k /data /storage/emulated/0`、`df -i /data /storage/emulated/0`、`du -sk` 仅本source证据目录；host `df -kP`、`df -iP` 对output所在文件系统，`du -sk` 对output目录。设备根目录未建立时记录不存在，不创造业务文件。保留原始单位/挂载点，不能把emulated与/data两个别名容量相加，也不按disk-size字符串伪造available。可额外记录已生成AVD config中的disk.dataPartition.size，仅作配置，不作实际分区证明。
- [ ] 采样在 instrumentation/capture/proof预算之外；不增加截图等待/sleep。每条外部命令设短有限超时（建议10秒），超时/不支持df -i时记录unknown和错误，不虚构0、无限重试或换算已知inode。记录函数不得覆盖现有首失败退出码；EXIT采样失败也不能阻断原pull/隔离/字体恢复。若最终运行没有有效初始/末尾设备df，标容量验收未完成，而非声明16G已证明足够。
- [ ] 文件均在原artifact目录供always上传，不写入手机业务目录。保原每格SHA inventory/pull/逐文件验证、12格ledger和失败trap全树兜底原样；本轮不做回收原件方案。不得清未知/失败目录，不新增rm、卸载或adb clear。

## 验证与严格验收

- [ ] 离线解析workflow：确认disk-size位于本job action with，只有本job变化；bash -n/diff --check。对记录函数用临时fake adb/df/du模拟正常、非零、timeout和不存在目录，验证独立阶段输出、原值/错误保留，且原失败码不被诊断覆盖、没有硬编码本机build依赖；不得启动设备作离线测试。
- [ ] Sol交实际diff/SHA/离线日志。Astra静态核scope和原collect保因；root本批合并后按原CI取证，不单独试跑、减页或缩图。
- [ ] 新run先确认action日志实际应用16G，再以df实际/data及emulated容量/可用量核是否扩容；记录12格前后和最终余量、inode可用性（未知单列）与host峰值。只完成全12格原件/隔离/完整校验且没有设备/host ENOSPC，才有限关闭本次容量阻断。若仍不足，保失败格原件和实际峰值另定容量，不循环扩大到绿。
- [ ] Machine tab48dp首败、App/Machine其它产品失败、dark1.0 ScaleSettings缺图分别保持；扩容成功不代表这些通过。5秒/四零、所有原始PNG/proof/manifest与source身份不改，Mock不当硬件验收。

不发版，不改协议编码/队列/未知记录/重试/停止。普通推送授权分支、PR到develop，必需CI通过后再合并。回退仅本workflow容量配置/采样增量，保留所有原始失败和未知记录，不卸载清数据。当前仅计划，配置生效、实际容量和完整12格证据均待新run。


## Astra 最终静态验收（2026-10-23）

通过，可交 root 合批；不是实际扩容/完整矩阵通过声明。独立重算冻结 SHA256：workflow `6b9f003b1837c7e3c47f579d1601f3235c4f652c299a8f98ffda003a2114ae29`；runner `e5d9e0445bdca6b34d3b01bd985e2da6c575b38467c7df042782f07eed1d74d6`。

已读实际两文件完整diff：仅本 Settings action增加disk-size16G，构建后AVD前host两项只读df及runner容量函数/调用点。API/内存/gpu/job时限/always上传不变。函数原样保存命令、stdout/stderr、退出码、单位注记及时间、run/source/格身份；非零、命令缺失、timeout有独立状态，不把unknown当容量0或成功。首尾df存在只产出 OBSERVED_PENDING_MATRIX_AND_ACTUAL_CAPACITY_REVIEW，不冒签足够；inode仍须读实际状态。

采样在初始化后矩阵前、每格after返回后（成功/失败分支均有）和原失败pull前后，未进入instrumentation/5秒采图预算。新增调用均 `|| true`，原首业务退出码选择、每格隔离/搬运SHA、break3、12格顺序、失败全树pull、字体key精确presence/value恢复及原最终退出链未改。原件不删、不另重跑，host重复副本风险仍待实际df评估。

独立 `bash -n`、两文件 `git diff --check` 通过。另从实际 heredoc 提取 Python 并以临时目录和 fake subprocess 执行正常/非零/不存在/timeout 四例，全部通过：原originalExitStatus23不变，缺目录不执行du，timeout保partial输出且exitCode为null；无真实adb/设备命令。本独立timeout测试模拟TimeoutExpired，不声称重新等待真实10秒；Sol报告的实际10秒fake测试另存其执行记录。

仍须新run验证16G配置实际生效、真实分区/host余量、全12格与原件完整性。Machine/App产品失败、缺失深1.0 ScaleSettings及其它视觉缺口不因此关闭。本次仅追加验收，无源码改动、构建、提交或推送。
