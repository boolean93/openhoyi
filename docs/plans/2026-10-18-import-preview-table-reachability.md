# 导入预览四阶段全表常驻与可达门禁

- 范围：按Astra的P0最小设计修正导入预览。先预留完整参数表实测行高，图仅分剩余且保留160dp最小高度；不足时仅左栏局部滚动，右栏动作保持固定。不缩字/减padding/删表行/移表到右栏。
- 来源/工作分支：沿用 `feature/task-first-ui`，开始本批时 `HEAD=9590a8c`；PR目标 `develop`、PR #4。遵守 [Git工作流](../git-workflow.md)，不重写历史、不提交他人文件。
- 文件：`CurveImportPreviewActivity.kt`、必要纯高度分配 `CurveImportPreviewLayoutPolicy.kt` 及JVM测试、`AdvancedPageChecks.kt`；本规划。不改样张、runner或workflow。
- 根因：旧预算不足就设参数表 `GONE`，加权图仍扩张。Advanced仅写 `tableShown/tableComplete` 后检查5行数量，未断言可见；TaskFrame旧门禁限font≤1.1，漏过1.3。
- 红证据：0f0beda/run39117264495虽green，实际 `wideFont/zh-Hans-light-CurveImportPreviewActivity-geometry.txt` 为1138×618dp/font1.3、4阶段、tableShown=false、tableComplete=false、表1470×431/图1470×750。本地对原产物执行全表常驻契约检查已因tableShown=false按预期失败；新增高度JVM测试先写，红阶段未单独执行编译，最终运行结果见下方阶段记录，不虚称完整JVM红绿循环。
- 实施：左侧原生ScrollView容纳标题/摘要/最小图/说明/全表；natural measure表与固定内容后分配图高；表始终VISIBLE。普通字号用户窗口要求首屏全表及最小图完整；1.3要求完整表始终存在，并逐行严格可达、表整体可容纳时滚动后严格完整。初始完整与滚动可达分开记账。
- 取证：滚动前后保存真实整窗与geometry；核对固定右动作位置不变、图≥160dp、5行与文字完整；返回顶端后保留原页面/存储/服务不变量。不点击保存/副本/外发动作。
- 验证：本批禁止独立Gradle，由样张批次统一Mock/AndroidTest/JVM构建；本地可做diff和脚本语法检查。新UI必须等精确源码CI运行，编译不当视觉或硬件验收。
- 推送/合并：仅本地修改，主任务合批普通推送，通过必需CI后按PR流程处理；本任务不推送/合并。
- 协议与设备：纯布局/Mock断言，不改格式、冲突/保存回调、协议、队列、未知记录、重试、停止、偏好或用户数据；不安装/连接/操作设备。
- 发布：不发版、不改包名/签名/版本/schema/升级路径。
- 回退：追加revert本批布局/检查，保留文档中的真实失败证据；不卸载清数据、不删未知记录。
- 未完成：精确源码CI的原生滚动/截图/1.0与1.3八语言浅深色运行及Astra读图验收仍待执行；本地编译、5项JVM测试和Astra源码复核已完成，均不替代原生运行或G1–G4验收。

## 本地阶段记录

- 红契约检查读取 `build/task-first-evidence/ci-0f0beda-advanced/advanced-mock-pages-pages/advanced-page-screenshots/wideFont/zh-Hans-light-CurveImportPreviewActivity-geometry.txt`，先确认stages=4，再要求tableShown=true；实际以 `AssertionError: RED: four-stage parameter table is absent despite green CI` 失败。该红灯证明原UI产物违反全表常驻契约，不冒称新增JVM/Android测试已经执行。
- 标准字号同目录landscape对照为stages=4、tableShown/tableComplete=true、表1470×356、图1470×461，保留该首屏完整要求。
- 新增5个JVM高度分配用例先于策略实现写入，覆盖实测356/431px表高、图剩余高度、160dp对应360px下限、阈值差1px、零视口与非法测量。最终统一构建实际运行结果为5项、0失败、0错误、0跳过，已读取 `mobile/build/test-results/testMockUnitTest/TEST-io.openhoyi.mobile.CurveImportPreviewLayoutPolicyTest.xml`；不把纯高度测试称为View滚动验收。
- Advanced新增常驻检查正好放在原表格geometry和5行数量检查旁；随后独立验证每行精确完整可达，表能放入viewport时要求滚动后整表精确完整，右操作区坐标不动。新增前后整窗放profile子目录 `import-preview-reachability/`，不改变原60张顶层PNG及240场景计数。
- Astra已无阻断通过本批P0与footer源码审查。稳定文件参加唯一最终样张共享构建，退出0、`BUILD SUCCESSFUL in 56s`、`96 actionable tasks: 16 executed, 80 up-to-date`，Mock应用及AndroidTest APK均完成；实际日志 `/tmp/2026-10-18-strict-native-batch-build.log` 已读取。构建含共享工作树未提交样张文件，本计划不把那些文件归为本任务提交或运行证据。
- `git diff --check`、footer脚本语法与内嵌Python语法检查已通过；本批没有安装、设备操作、推送或合并。新增整窗截图必须待后续CI生成，不能使用旧PNG替代。
