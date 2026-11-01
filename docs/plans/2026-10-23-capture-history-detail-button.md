# Capture History 独立详情按钮选择

> Sol 按 executing-plans 实施；root 统一构建、提交推送。只改本项文件，保留其他owner改动。

**Goal:** 让History独立详情这一调用明确匹配真实Button，不改变通用默认规则或产品。

**Architecture:** 给现匹配器增加可选精确节点class约束；只在该调用传android.widget.Button。保持同XML树、原候选祖先解析、中心去重、唯一性、失败诊断及前台Mock检查。

**Tech Stack:** Python XML matcher、Bash capture、现unittest。

## 依据与边界

读取 `ci-8b52379-capture-push/diagnosis.md`、preservation及实际XML。独立算得XML SHA256 `2bf2c04e93e3d7038e0c69393b64093660d20f4465c0944d46052ef6bac88336`。同XML确有两个enabled/clickable Mock节点：ImageButton用content-desc匹配，Button用text匹配；旧调用后立刻assert HistoryDetailActivity。因此此调用目标明确为独立详情Button，不是更多菜单ImageButton。

原run39893285336、head8b5237989eaae99181ff137b8873896eb96bda7c；ZIP SHA d7b1aef28745a380924295ebd07f602d0f441e6ed2987e574a433f71214faee0。新现场不能追溯证明旧5d08缺XML帧。前台检查先于hierarchy，并非原子快照，保持此说明。

沿授权feature/task-first-ui→develop批次，遵守 [git-workflow](../git-workflow.md) 与 [task-template](task-template.md)。仅允许 `scripts/mock_ui_tap.py`、`scripts/capture_mock_ui.sh`、`scripts/test_mock_ui_tap.py`。不改产品/Activity/resource，不加坐标、不选第一个、不增加dump或ADB重试。

## 最小实现步骤

- [ ] 先以原XML离线复算：默认find_target仍抛歧义；在同树按class精确过滤可得到唯一Button。真实原件不修改，读取时核SHA；测试需离线，无ADB。
- [ ] `find_target(xml,text,contains=False,node_class=None)`新增末尾可选参数，保原调用兼容；CLI新增`--node-class`。仅当显式传值时，在文本匹配节点上精确比较`node.get('class') == node_class`，不做包含/后缀/自动替代。不改变默认text/content-desc、contains、祖先和ListView处理规则。零合格目标仍走原not-found/原授权scroll，多个不同中心仍原AmbiguousMockControl，不能退回无class匹配。
- [ ] 同树诊断记录请求nodeClass；文字匹配但class不符者仍可作为候选记录，明确标记class-filtered、不进入ancestor解析/中心集合。合格候选保持原matchedSources、祖先/selectedNode/bounds/center/invalidReason与中心去重。所有旧默认诊断字段、原XML原样保存与写失败保原错误保持。不得为诊断再次dump或触发tap/scroll。
- [ ] shell helper将已有参数转发改为`"$@"`（原仅`"$1"`），只把history-selected-dark之后的调用改为：

```bash
tap_control '查看详情' --node-class android.widget.Button
assert_activity HistoryDetailActivity
```

原--scroll、diagnostics-dir、sleep及其他调用不变，不给所有“查看详情”全局套约束。精确class筛选后仍由实际XMLbounds动态求中心；运行实现不得硬编码本帧2398,1366。

## 验证与交接

- [ ] 扩现unittest：原XML默认仍歧义，新约束唯一Button；ImageButton约束可独立识别另一候选（仅离线，不在capture调用）；错class返回None而不是fallback；两个匹配Button不同中心仍歧义，同中心仍原去重；disabled/无合法bounds不tap。保留原9测试。
- [ ] main mock测试：约束参数确传给find_target；歧义保存的XML与候选来自同次输入，记录nodeClass/被过滤者；写诊断失败仍抛原错，无额外dump/tap/scroll。非歧义成功仍原两次前台Mock检查及一次tap，不能为了新选择多发ADB。
- [ ] `python3 -m unittest discover -s scripts -p test_mock_ui_tap.py`、`bash -n scripts/capture_mock_ui.sh`、Python语法及`git diff --check`。记录真实XML SHA与离线结果、冻结三文件SHA交Astra，不把真实XML离线选择成功称为Activity已跳转。
- [ ] root合批完整验证、普通推送原PR，不微推/force/no-verify/直推主干。下一真实capture仍必须assert HistoryDetailActivity及后续原证据链通过；失败保现场不自动改UI或放宽选择器。

## 风险、发布、回退

仅明确已证动作目标；未改设备编码/单位/队列/未知记录，不重发未知，不用Mock代替硬件验收。不发版/安装/分发。回退仅revert这三文件增量，保留原XML/ZIP与失败记录。当前只规划，未改代码/执行capture/触发CI；不处理M5或Legacy视觉。

## Astra 静态终审（2026-10-23）

静态通过。独立读取三文件实际diff并核冻结SHA：matcher `486f218c86b8d6569fee87b33921e48d74807267bd1c1bf715746322681dbf6a`；capture `e779d04a2a2b3f2d86ebb5a13e833af98cb7d0d2e9e754d6fbedba6fb98a13ea`；test `272268c297128a3c33db6a073467c4e37008ae8d213d45a3298c0082dc10755d`。

可选node_class默认None，原text/content-desc/contains、祖先/ListView、中心去重、唯一性均保持。显式约束精确筛matched node，无匹配不会回退；被过滤文字候选仍在同树诊断并标class-filtered，无中心或伪selectedNode。CLI将同一约束传给查找和诊断；歧义直接抛出、写盘失败保原错，没有额外dump/tap/scroll。成功仍原两次Mock前台检查及一次tap。shell仅helper参数转发和唯一目标调用两行变化，后续HistoryDetailActivity断言保留，没有坐标或首候选选择。

测试保留旧9项并新增6项，包含真实XML锁SHA、默认歧义/精确Button/错class、双Button歧义/同中心去重、无效按钮、main调用次数、约束诊断及写失败不tap。测试中的已知中心仅为原XML离线期望，不进入运行选择逻辑。root报告15测试通过；本审查不重复运行，独立bash -n与git diff --check通过。

可交root合批交付；未改代码、构建或推送。实际HistoryDetailActivity跳转及后续capture仍须新CI原件，不能把XML离线命中当运行成功。旧失败XML/ZIP与前台检查非原子时序边界保留。
