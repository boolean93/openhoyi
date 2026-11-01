# M5 完整 case 原件与轻量 summary 索引

- 需求与范围：修复真实 M5 跨 case 强引用大 proof 的容量问题。summary 改为 `M5_EXTRACTION_CASE_REFS_V1` 受限元信息索引，每份完整 `case.json`、原 proof/PNG、fixture/isolation 和全部业务/窗口门保留。只改 ExtractionHistory 测试、其 host reader/脚本及相关测试，不改共享 writer、产品、Scale 草稿、settings 或矩阵。
- 来源分支/基线提交：develop `35798d22e1dcbe2dd404bdbc744ffd486c0c8e50` 已正常 merge；按用户授权继续现有 `fix/ci-page-service`，本块开始 HEAD `49e9ab6908f47848e805f53c92268be9ac6de294`，不另建分支、不重写历史。规则见 [Git工作流](../git-workflow.md)。
- PR目标：现有 PR7 → develop；root 统一正常 push，不直接推 develop，不自行 merge。
- 真实失败证据：49e M5 run `41289101505` / job `124796447831` / artifact `13077076083`，checkout `13e9afcbfc6e4c869984be365891f7504316d04e`。light/font1.3 的历史参考真实 on/off/restored 已 PASS（20 eligible、21行、index1→0、32→0→0参考点、当前点/trace/journal/固定停止/滚动恢复不变）；随后第12个 `journal_unavailable` 初始 captureTarget/typography 建80B对象时201326592B heap OOM。summary 原始序列化大小146366626B，toggle case35292205B。Astra静态确认 `cases.put(proof)` 始终持有前面完整 proof；无 HPROF，不声称定量 heap 归因。workspace原始小证据和摘要在 `settings-evidence/ci-diagnosis/history-49e9ab6/`，不以首配置 PASS 冒称完整 M5 通过。
- 改动文件与步骤：producer 先用现有严格原子 `writeStrict` 保存原 case，成功后才算原始字节 SHA/大小并追加 ordinal/case/path/source/profile/shot/status 索引；索引只存字符串/数字，跨迭代不持 full proof。失败 case 原件与 FAIL 索引保留；写失败仍抛原首错并附加次错，summary 初始 completed=false，只有固定12份都落盘且 PASS 才 completed=true。
- host：明确拒绝旧 inline schema。先核固定12个case/顺序/完整性与受限索引字段，再按固定 owned `<source>/<profile>/extraction/special-states/<case>/case.json` 路径逐个读；检查源/profile/shot/status、大小及原始字节 SHA、非symlink的目录链/文件及读取前后身份。只 parse 当前 case，yield 后运行原 `for case` 的全部业务、frame、isolation、原件相等门，结束即释放当前 case；不构造全部case列表。
- 验证证据：新 host 13项测试已通过：原件等价与顺序、lazy读取、缺失/截断/同长度篡改、schema/完成/count/order/重复、绝对/越界/大小写/换case路径、非法大小/SHA/status/identity、额外full proof混入、原件身份及FAIL、file/目录/profile symlink逃逸、读取中修改/父目录换symlink、错误evidence root/旧inline均拒绝。相关31项回归也已通过（总44项）；分支检查、bash-n、diff-check通过。JDK17 `:mobile:compileMockAndroidTestKotlin`成功30s/47 tasks。日志`settings-evidence/ci-diagnosis/history-49e9ab6/m5-case-refs-{tests,compile}.log`。另用AST对比49e与本次host extraction case循环，确认原业务body完全相同（只迭代来源换为reader，末尾增加del case）。Astra（`/root/settings_failure_review`）独审无blocking并独立复跑13新测试通过，准这5文件独立提交。本机测试不是 Android 容量验收，35MB单case峰值和完整16/60/48仍必须新精确head CI证明。
- 推送与合并：本地tests/compile → Astra独审 → 仅本任务文件独立commit → root待settings矩阵/修复协调一次push → 精确head四required、真实M5及settings目标完整证明后由root判断。不得用旧49e三阶段成功或旧required绿替代新头验证。
- 协议与设备风险：只改变 CI 证据存储/读取方式；不增加 heap、超时、重试，不删/截断/跳过 proof，不改变协议、命令、单位、固件、秤或模拟输入。无用户设备操作、无安装，不删除任何未知记录。
- 发布：不发版，不打标签，不做签名或升级安装；现有包名/签名/versionCode边界不变。
- 回退：一起 revert 本块 producer/schema/host/test，保留所有已有原件和失败证据，不用清数据或删除记录回退。
- 未完成与阻塞：本块编译/44项回归/独审已完成；真实完整M5和settings仍待新CI。单case内峰值未测，不能承诺引用化独自解决所有内存压力。Scale另案/缓存计划均不混本提交。
