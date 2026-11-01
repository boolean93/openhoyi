# Bean runner 快照恢复薄适配计划

> root 已批准下述固定两项 profile 接口范围，可交 Sol；尚未实施。按 executing-plans 执行；不改产品/仪器测试、不接第三个runner。

## 结论与已批准边界

Scale 正常 run40056758145 与自然失败 run40056753959 均已实证 load 前保全、原业务退出码保留、absent/value/framework 精确恢复。可沿同一事务接 Bean，但**当前 helper 没有可配置的 runner 接口**。仅修改 Bean shell/workflow 会误跑 Scale，不能实现目标。

`scripts/ci_font_scale_snapshot_scope.py` 当前硬编码：ROOT/SCOPE、`font-scale-scale-` AVD 前缀、`SCALE_SNAPSHOT_SERIAL`、`scale_scope_`、设备 `scale-visual-migration` 路径、子进程 `check_scale_visual_migration.sh`、`SCALE_SNAPSHOT_SCOPE_FILE`、verify owner 路径。不得通过假冒Scale AVD/软链接证据目录、替换脚本、monkeypatch或复制整套恢复事务绕过。

**root 已明确批准 helper 仅固定 Scale/Bean 两项身份/路径映射扩展，默认 Scale，事务代码和契约不变。** 此裁定替代此前“不改helper”限制，不需额外用户确认；不新增设备验证门槛。

## 来源、范围与文件

- develop 基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；来源 `484b13766fd9ce0736802d52e2e8970ec73875b4` / `feature/task-first-ui`，PR #4 → develop。实施前重核HEAD/工作树，保留别人改动。
- 必改：`scripts/check_bean_workflow_visual_migration.sh`、`.github/workflows/bean-workflow-visual-ui.yml`。
- 已批准唯一接口扩展：`scripts/ci_font_scale_snapshot_scope.py` 固定profile映射及profile传递。不得改变 preserve/finish/operation/identity/state/退出码/进程隔离算法。
- 产品、BeanWorkflowVisualMigrationChecks、snapshot probe、原Scale runner/workflow及现有测试文件均不改；新增专用薄适配测试文件 `scripts/test_bean_snapshot_scope_adapter.py`，只测profile路径隔离和Bean入口契约，不复刻事务模型。

## 有限实现步骤

- [ ] 固定profile仅接受 `scale`/`bean`，默认scale保兼容；run/verify都显式传bean时使用同一配置。拒绝任意脚本路径/证据根/未知profile输入。Scope实例持有该profile配置，verify重建同profile，不能靠全局ROOT临时切换。

| 项 | Scale保持 | Bean新增 |
|---|---|---|
| host/remote业务目录尾段 | scale-visual-migration | bean-workflow-visual-migration |
| 子进程 | scripts/check_scale_visual_migration.sh | scripts/check_bean_workflow_visual_migration.sh |
| AVD前缀 | font-scale-scale- | font-scale-bean- |
| serial环境 | SCALE_SNAPSHOT_SERIAL | BEAN_SNAPSHOT_SERIAL |
| owner环境 | SCALE_SNAPSHOT_SCOPE_FILE | BEAN_SNAPSHOT_SCOPE_FILE |
| snapshot前缀 | scale_scope_ | bean_scope_ |

- [ ] scope认证防交叉：初始化owner新增固定 `scopeProfile`；verify检查请求profile、owner.scopeProfile（旧缺省仅按scale解释）、run identity、精确host owner路径、固定snapshot前缀与AVD名称均属于同profile，之后仍逐项核原device/source/APK/marker/list。Bean不得接受缺scopeProfile的旧Scale owner；Scale不得接受bean owner。ROOT/SCOPE、远端目录、子进程/环境从同一不可变profile表取得，不按owner提供任意路径。保证原Scale旧owner/默认测试兼容，但新Bean无缺省猜测。
- [ ] Bean shell在任何wm/settings/install业务变更前，若存在BEAN_SNAPSHOT_SCOPE_FILE，执行同helper `verify --profile bean --owner ...`，失败立即退出。未提供owner的原入口继续原严格字体还原，不自动降级。
- [ ] 复用Scale collect增量：先拉取原Bean部分证据/诊断；snapshot模式不提前force-stop、不写/delete font_scale，只写 `PENDING_SNAPSHOT_RESTORE` 与原码。写pending失败使原0转非0，原非0不覆盖。EXIT trap明确保存原码、禁递归trap、退出collect返回码。所有非snapshot原清理逻辑保留。
- [ ] wrapper仍在save ACK/独占身份/唯一owner验证后启动原Bean shell；仅把固定业务路径换Bean。沿既有preserve从Bean远端SHA目录逐文件hash+manifest、host库存fsync；确认进程组终止后才一次load；load后直接读真实presence/exact value/framework、同identity/owner；成功才delete本快照一次。原非0在所有恢复结果下仍非0原码，不以恢复PASS掩盖424资格或截图FAIL。
- [ ] Bean workflow添加 `CI_DISPOSABLE_AVD: font-scale-bean-${run_id}-${run_attempt}-bean-workflow-visual`、`BEAN_SNAPSHOT_SERIAL: emulator-5554`；emulator指定该avd-name、force-avd-creation:true、port5554，script改helper run --profile bean。原API34/image/profile/ram/45min/upload always目录保持。沿现有离线事务测试步骤运行原测试及新增adapter测试。
- [ ] 原Bean light/dark × 1.0/1.3 × preparation/batch/ledger顺序、12配置、light1.0的424单例、所有业务/whole/5s四零不改。不得复制Scale的1.3→1.0排序，也不修Scale本次5000ms截图失败。

## 最小验证与接受边界

1. 新adapter测试先证明默认Scale映射不变、Bean run/verify同路径、Bean owner不能由Scale verify接受、未知profile拒绝；可mock子进程入口，不能跑本地adb。复用原13项事务测试，既有测试内容不修改。命令：`python3 -m unittest discover -s scripts -p 'test_*snapshot_scope*.py'`；另执行 `python3 -m unittest discover -s scripts -p test_bean_snapshot_scope_adapter.py`。
2. `bash -n scripts/check_bean_workflow_visual_migration.sh`、Python编译/YAML解析、`git diff --check`。静态核原业务循环/断言区字节不变，交root精确SHA。只有shell/helper/workflow变更不重复构建相同APK；workflow保留原Mock双包构建。
3. root普通推送工作分支，原Bean CI产真实事务。验收按实际：保存/identity/ACK、load前完整或部分inventory、原退出码、恢复现态与删自己分别核。若业务失败，只签恢复事务，保业务FAIL；若正常完成，只签正常路径，不能外推Bean未发生的失败分支。已验证Scale失败路径是共用事务依据，不虚构Bean实际路径。
4. 不新增故障注入或四套新profile，不以扩大门槛阻塞薄适配。Bean原font_scale恢复FAIL仍是历史事实，等新原件才记新事务通过；424实际窗口资格及视觉审查独立。

## 合并、风险、回退

root经静态审/所需CI普通推送PR #4；四项必需CI与评审后合并，不直推main/develop、不强推。只有独占disposable CI AVD全快照，load前证据保全严格沿现有契约。无BLE/咖啡机/秤命令或库存业务修改，无正式设备/安装/发布/签名/升级授权。回退新revert提交移除Bean适配及新增profile，Scale默认行为保持；保留证据和未知数据，不卸载/清记录/重发操作。

## 2026-10-24 冻结实现静态验收

Astra 限定静态验收通过；无产品/APK修改，无需为此重复Gradle。Bean实际CI事务尚待原件，不能把Scale实证外推成Bean已运行成功。

| 文件 | SHA256 |
|---|---|
| `scripts/check_bean_workflow_visual_migration.sh` | `9d9994b14534e470437568eaac098d58935ad63b9e77f418131ae823fdd96e1e` |
| `.github/workflows/bean-workflow-visual-ui.yml` | `3cdb312fa1160b3bfc48ebec0426288b0ea460b98f6c606033c444192611c14a` |
| `scripts/ci_font_scale_snapshot_scope.py` | `e279deae1a4d679c9a4c5497ea171ee15cac1cef40ae479f5a4d3094e128409d` |
| `scripts/test_bean_snapshot_scope_adapter.py` | `d04e03522e7772f7018f987a60ad3e59fd9a5266d5f55806407e72c08aceba17` |

实际复核固定MappingProxyType/NamedTuple两项profile、CLI默认Scale及未知值拒绝；Bean remote/root/子进程/owner环境同源，verify精确host路径，旧缺scopeProfile只解释Scale，Bean拒绝旧Scale owner，snapshot前缀/run identity与remote marker路径防交叉，仍核原source/APK/device/marker/list。ROOT保留Scale原测试接缝，不暴露CLI任意路径；Bean固定自身root。

独立AST对比 `operation/finish/identity/state/names/marker/adb/checked/shell/git_head` 与HEAD完全相同。preserve仅固定profile路径替换，原逐文件/manifest/hash、fsync、load前证据与进程停止顺序保留；run仅子进程/owner环境替换，原失败码及durable business-exit未动。Bean shell先verify再业务，pending失败原0转1/原非0不变，snapshot分支不提前force-stop，不执行旧put/delete；非snapshot原严格还原保留，EXIT trap显式返回collect码。

独立逐字节对比Bean shell从原 `adb shell wm size 2560x1600` 起业务尾段与HEAD完全相同；原12配置/顺序/业务及424入口未改。workflow仅独占AVD/serial/固定bean wrapper与离线测试接线，原45min/构建/上传路径不变。

Astra实际运行 `python3 -m unittest discover -s scripts -p 'test_*snapshot_scope*.py'`：18 tests OK（含原13与新增5；disk full输出为预期错误保真负例）；`bash -n`、`git diff --check`通过，四文件SHA独立重算吻合。无设备执行/故障注入；Scale5000ms及Settings截图失败均不属此批修复。

## Root独立验证

18项snapshot_scope离线测试、11项原snapshot probe测试全部通过；bash语法、Python编译、Ruby YAML解析及diff-check通过。预期disk-full负例输出不代表本机磁盘故障。Root独立核原Bean `for product_theme` 至EOF 17,676字节完全不变，SHA256 `9e8f9fbcdc626323802d498e3c9c1c6174a2529c65633434636cd4cdd5ec1718`。无产品/Instrumentation/APK资源变化，不重复Gradle；新workflow仍原双包构建。Bean真实恢复事务仅待新CI，未安装/发布/合并。
