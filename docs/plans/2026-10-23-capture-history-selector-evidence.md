# History「查看详情」选择器歧义最小补证

- 需求与范围：只补`mock_ui_tap.py`歧义失败当次XML及同源候选链，不改产品UI/匹配语义、不选第一个、不新增点击或dump。依据 [selector-source-diagnosis](../../build/task-first-evidence/ci-5d08fd6-capture-push/selector-source-diagnosis.md)：旧head5d08失败XML缺失；标题more与footer full-page只是源码候选，不能断言实际命中。
- 来源分支/基线：本任务延续用户授权的全24页迁移工作；当前已推3a78c4c，工作树检查为空。不是另建新功能，沿现短分支；若脱离本合批任务，按规则从origin/develop另建chore短分支。
- 工作分支/PR目标：`feature/task-first-ui` → `develop`；遵守 [git-workflow](../git-workflow.md) 与 [task-template](task-template.md)。现27项CI仍live，本计划不触发CI、不将旧5d08失败当3a78结果。
- Owner：同Sol串行，仅`scripts/mock_ui_tap.py`、`scripts/capture_mock_ui.sh`及针对该helper的现有/窄单元测试。不是独自工作，不覆盖别人变更；禁止改Activity/资源/选择器调用文案或CI门禁。

## 可执行步骤

1. 将现find_target遍历所得候选信息随专门的歧义异常保留（可用ValueError子类，仍维持原错误消息/非零退出）。使用**同一次ET解析树和同一parent map**，不要为诊断再实现不同的匹配器。text/content-desc精确或contains条件、首个enabled clickable祖先、ListView子项回退、bounds合法性、中心去重规则逐字保持。
2. 每个匹配节点记录稳定树路径、全部原始attributes（至少text/content-desc/class/resource-id/enabled/clickable/bounds）、匹配来源text或content-desc；记录从命中节点到所选祖先的路径与属性、选中原因（clickable祖先/ListView子项）、原bounds、中心或无效原因。保留无合法中心的匹配节点，不能只输出最后两个中心。另记按原逻辑去重后的排序中心集合与原始候选中心列表，便于解释同中心合并。
3. `ensure_mock()`把**已经读取并通过Mock校验**的foreground原文返回，成功行为不变。每轮在原位置保存该值与attempt/direction历史；标明它是本轮read_hierarchy之前的foreground检查，不声称与XML原子同刻。歧义分支不得再调用dumpsys、read_hierarchy、dump、tap或swipe。
4. 增加可选`--diagnostics-dir`，由capture_mock_ui.sh现`tap_control`传入`$output_dir/selector-diagnostics`。只在歧义时创建唯一子目录（不可覆盖前次失败；文件名不用原text作路径），保存原xml字符串未经ET重序列化的`hierarchy.xml`、其UTF-8 SHA-256、同源`candidates.json`、上述已有foreground原文。记录selector、contains、attempt、已发生scroll方向和`NO_TAP_AFTER_AMBIGUITY`，不要把它叫协议2认证证据。
5. 保存后照旧抛原歧义异常；若写盘失败也保留原歧义为主因并报告诊断写入错误，不恢复选择、不继续下一scroll。原shell ERR钩子的failure.png/activity/logcat保留；其时间晚于XML，不能与之混称同帧。正常唯一命中仍沿原第二次ensure_mock后tap；零命中仍沿原scroll顺序，不写成ambiguous。

## 验证与交接

- 纯离线测试：两个不同中心命中必须仍失败，报告完整两链且原XML逐字一致；两个命中同中心仍唯一；ListView子项路径/中心与旧规则相同；无合法bounds仍零目标；contains与精确匹配不互换。伪adb记录应证实歧义后没有额外dump/tap/swipe/dumpsys；写盘失败仍歧义失败。测试XML仅验证工具逻辑，不代替失败现场证据。
- 验证`python3 -m py_compile scripts/mock_ui_tap.py`、该helper单测、`bash -n scripts/capture_mock_ui.sh`、`git diff --check`；冻结diff/hash交Astra。无需为脚本补证构建Android、连接设备或手工重跑capture。
- root检查工作树→合批验证→仅提交所属文件→普通推送现PR并补正文规划/证据→必需CI通过才可合并；不微推、不force/no-verify、不直推main/develop。下一合法capture如果再歧义，独立审真实XML后再决定是否按预期动作收窄选择器；本计划不预授权猜修。
- 协议与设备风险：仅Mock脚本失败诊断，无编码/单位/队列/停止/未知写入变化，不新增业务fixture、不操作真实硬件、不以Mock当硬件验收。
- 发布与回退：不发版/安装/分发；必要时仅revert诊断增量，保留旧失败原件和未知记录，不卸载清数据。
- 未完成：旧失败当刻XML不可追回；新诊断未实施、未运行，不声称History UI或选择器已修复。3a78当前CI独立等待原结果。

## Astra 最终静态审核：通过，现场歧义仍待新证据

已读三文件实际diff并核冻结SHA：mock_ui_tap.py `2c3146546e6b6e4c8880fc04998c12952dc7edfa55d8cb9430f9d111190faa70`；capture_mock_ui.sh `6c08024de343398141238dc2a39c1fa4d23b18a5ea497fba51b7f9e91d3d020b`；test_mock_ui_tap.py `5b6ffaf6cb259b470b490b9b7cc8dce8d27889d0fdd381beffff31b11098792f`。

原text/content-desc精确或contains语义、首enabled clickable祖先/ListView子项优先级、bounds有效条件、同中心去重与正常点击前第二次Mock校验均未改变。候选来自同一次ET解析树/parents，保留全部匹配节点、双来源、实际祖先链、选中节点、无合法中心的原因；歧义异常仍是ValueError子类并保留原消息。原XML字符串直接UTF-8落盘并hash，唯一目录不覆盖旧证据。foreground保存在JSON的`foregroundCheckBeforeHierarchy`，明确先于XML，不冒称原子同帧；无新增dumpsys/dump/点击/滚动。OSError写盘失败打印附加原因后仍抛原歧义，不继续执行。

独立重跑`python3 -m unittest discover -s scripts -p test_mock_ui_tap.py`：9项通过；`bash -n`及三文件`git diff --check`通过。仅离线验证，未连接设备、未运行capture/Android构建/CI、未提交推送。该实现可纳入下批合批验证，不单独微推；旧History失败XML不可追补，是否确为more/full-page两候选仍等待下一合法运行原XML，不能宣布选择器或UI问题已修复。
