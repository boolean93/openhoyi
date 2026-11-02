# Mock UI selector 真实 XML fixture 可移植修复

- 需求：修复Native verify在干净checkout中找不到本机build XML；保留真实歧义现场和全部15项测试，不skip、不合成替代、不降低断言。
- 基线/分支：当前迁移批次 `feature/task-first-ui`，head `4076c2d862f94608fa6ec3d405c91d23a6b6867c`，PR目标develop；遵守 [git-workflow](../git-workflow.md) 与 [task-template](task-template.md)。同Sol串行，保留其他owner改动，尤其Curve诊断文件。
- 依据：`build/task-first-evidence/ci-4076c2d/native-fixture-diagnosis.md`、实际test/verify.yml与原XML。Native PR39906809362在明确的 `python3 -m unittest discover -s scripts -p test_mock_ui_tap.py` 报FileNotFound；不是翻译内容错误。

## 最小改动：仅三个文件

1. 新增 `scripts/fixtures/mock_ui_tap/history-detail-ambiguous.xml`，按原字节复制本机 `build/task-first-evidence/ci-8b52379-capture-push/raw/selector-diagnostics/ambiguous-5yiwqhu8/hierarchy.xml`，不得pretty-print、换行归一化、删节点或重写bounds。原件34990 bytes、101个node，SHA256 `2bf2c04e93e3d7038e0c69393b64093660d20f4465c0944d46052ef6bac88336`。
2. `scripts/test_mock_ui_tap.py` 仅把REAL_HISTORY_XML改为基于 `Path(__file__).resolve().parent` 的上述fixture路径。原SHA常量、默认歧义、精确Button中心(2398,1366)、ImageButton中心(2470,317)、短名Button不匹配等所有断言保持；这些坐标只作此固定XML的离线预期，不能进入运行时脚本。缺文件/坏SHA仍硬失败，无本机build fallback、下载或skip。
3. 新增同目录 `README.md` 写来源：capture push run39893285336，artifact12476340526，原ZIP SHA256 `d7b1aef28745a380924295ebd07f602d0f441e6ed2987e574a433f71214faee0`，原成员路径 `selector-diagnostics/ambiguous-5yiwqhu8/hierarchy.xml`，完整字节数/SHA、用途与不可静默更新要求。新增fixture须正常Git跟踪，不改ignore或用force添加build原件。

本次不改 `mock_ui_tap.py`、capture调用、workflow、产品、Curve文件。已读原XML的text/content-desc/package/resource-id属性：仅Mock包及模拟历史数值、日期、UI文本，未见账户/联系方式/内容URI；这是本fixture入库审阅，不声称一般隐私审计。

## 验证与交接

- 拷贝后与原件逐字节比较并重算SHA/长度；核路径不被ignore、提交包含fixture及README。源XML缺失或hash不符即停止，不改常量适配未知文件。
- 原仓库执行workflow原命令，必须15项通过且无skip。再建临时目录，只复制 `scripts/mock_ui_tap.py`、`scripts/test_mock_ui_tap.py` 与新fixture（README可一并），不复制build、不设置仓库PYTHONPATH、不依赖adb/网络；从该目录执行同一discover命令，必须仍15项通过。这是真正无本机ignored输入的可移植验证，不移动/删除共享build来测试。
- 缺件/篡改负例可在临时副本验证硬失败，随后丢弃临时目录；不改共享原件。`git diff --check`后冻结三文件SHA交Astra，root统一构建/下批正常推送原PR。禁微推、force/no-verify或直推main/develop；必需CI通过后合并。

## Settings独立未覆盖项

`test_settings_language_host_contract.py` 的setUpClass仍直接依赖ignored本地134,596,394-byte ZIP，SHA `3bbb97e13964d7b116551f8dedf8d5df1c085d94cb05abb4fdb2a8dfbc55d315`。已核当前workflow仅明确调用指定test pattern，未调用该Settings测试，因此它是**本地离线重放通过，尚无CI覆盖**，不是此次Native首因。不得顺手加入全量test_*.py发现或谎称当前CI已跑其8项。

此文件和ZIP本次不改、不入库、不skip。后续单独规划可移植fixture或受控取件：必须保留完整单格manifest及所有引用闭包、真实runner重放、原身份/SHA与反例，核真实大小后再决定存储及workflow入口；仅拷8个Dialog文件不能代替完整单格重放。未来接入前先在无本机build环境验证，缺件不能静默跳过。

协议/设备：仅离线测试输入搬迁，无门禁、业务、硬件写入或未知重发变化。不发版、不安装。回退仅revert本三文件增量，保留原ZIP与XML证据；不清数据。当前仅计划，未改实现或执行新测试。

## Astra 静态与可移植验收（2026-10-23）

**本窄修复通过。** 已独立读取实际diff：测试仅改REAL_HISTORY_XML路径，原SHA、15项用例及所有选择/拒绝/无额外adb断言未改；生产选择器、capture与workflow未改。README来源run/artifact/ZIP/member/字节数/SHA和不可静默更新要求与原preservation一致。

独立逐字节比较新XML与保留原件相等，34990 bytes；`git check-ignore`无匹配（返回1，表示未被忽略）。冻结SHA均独立核对：test `98d6c661c972050e28d020e8a47c1c18a710c3e0e96c31005bb72bfef714b2bd`；XML `2bf2c04e93e3d7038e0c69393b64093660d20f4465c0944d46052ef6bac88336`；README `745c2122b3f39c1be9a025070b1f7761cfd7f17475ed776163e9881b874eeb44`。

独立创建临时最小目录，仅复制selector、test、fixture和README，无build，清除PYTHONPATH；从该目录执行workflow原discover命令，15项全部通过（0.009s，无skip）。同一临时副本删除XML时FileNotFoundError硬失败，末尾添加一个空格时SHA AssertionError硬失败。共享原件未移动/篡改，临时目录已清理。diff whitespace检查通过。

可交root统一批次提交，须包含两个新fixture文件；本地可移植通过不冒称新Native CI已完成。Settings大ZIP重放仍仅本地覆盖，未接入CI，其闭包方案另列待办。本轮仅追加结论，未改实现、运行Gradle、提交或推送。
