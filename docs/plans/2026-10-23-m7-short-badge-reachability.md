# M7 短窗只读徽标全文滚达及精确恢复

- 范围：原short-window Detail长名称fixture增加一个真实 `legacy/readonly` 徽标目标及恢复证据，不改产品/字号/徽标/窗口尺寸，不新增业务fixture。
- 依据：`build/task-first-evidence/ci-8b52379-legacy-pr/badge-reachability-evidence-index.json`；run39893290655/head8b5237989eaae99181ff137b8873896eb96bda7c，merge44d34c52a3f3b18537f59cb92607bcc7c6252d09，原ZIP SHA `bbbf0013a3872dcc75eef9d586743fbc24c7ca3a6fbf575e9b855f03fccb331d`。
- 分支/交接：当前授权feature/task-first-ui→develop批次，遵守[git-workflow](../git-workflow.md)、[task-template](task-template.md)；同Sol串行，root合批统一构建/推送，不微推，不覆盖Curve/Scale/M5其他改动。

## 已证缺口

light/dark1.3各23个真实630px短窗accepted帧，徽标complete与fullViewVisible均false；正常1391px内容高帧不充当短窗证明。初态徽标下缘被裁21px，现滚达outer scroll只取0/422/584，几何推导21..365可能完整，但不是实测可达结论。不得硬编码这个区间或21/422等偏移换通过。

## Sol最小范围：测试与现runner两文件

1. `LegacyVisualMigrationChecks.kt`：在原short-window初态capture后、chartReachability之前插入独立badgeReachability，四个原配置一致执行。使用已存在headingReadonly的语义对象，从原heading中唯一解析tag=`legacy/readonly`，保存其实际View引用与identity；shortDetailState同帧增加badge身份供初态/reach/restore对应，不改变已有观测字段。
2. 在main一次保存全部现ScrollView引用及精确scrollX/Y，核与初态target一致。若初态徽标已全文可见（如1.0），不人为移动来制造滚动；仍采对应reachable证据。否则只对该实际徽标请求一次 `requestRectangleOnScreen`，矩形使用徽标当前内容坐标 `scrollX/scrollY` 加实际width/height，immediate=true；不设硬编码outer滚动值、不循环尝试、不改窗口。然后沿原stableWindow及MeasuredShortActivity capture路径采 `short-window/badge-reachable`。
3. 只以该capture返回target判定，不在capture后另读View拼证：同一徽标identity/semanticId、文本仍为原初态全文；screenMappingVerified、globalVisible、rect/visibleRect完全一致，宽高为正；原textLineBoxes存在、layoutPresent、fullViewVisible、ellipsisCount=0、layoutHeight<=contentHeight，所有实际行left/right落在contentWidth内。实际原窗口/Activity/decor/token/display及workspace/content/support/chart/list对象身份不变，630px短窗尺寸不借正常窗口替代。单个徽标不是交互按钮，不另要求48dp触控；原按钮48dp/plot96dp合同照常执行。
4. finally按保存的全部ScrollView对象恢复精确x/y，随后采 `short-window/badge-restored`，比较returned target的完整scroll列表与原initial精确相等，并核所有原对象/徽标身份、原窗口attributes/尺寸。即使reach断言失败也执行恢复，原异常保持主异常，恢复/持久化失败suppressed；单独恢复失败仍失败。不得用正常窗口最终恢复代替这一步短窗恢复。
5. 报告中增加唯一badgeReachability，记录initialScrolls、固定semanticKey、reached/restored的引用与同帧state、completed。开始前挂报告并让失败已有证据可保存，不把成功字段设在检查前；复用shortFrameRefs及原完整性链，不复制不必要proof树。后续chart/button/全部轴字顺序、原业务/native不变量和最终窗口/scroll恢复保留，新增探针恢复完成后应回原初态才继续。

`check_legacy_visual_migration.sh`：现Detail short-window段要求badgeReachability唯一目标为legacy/readonly（缺项/额外项/重复引用均失败），以现short_detail_capture核两个精确路径/PNG-SHA/geometry/proof同源、protocol2/5秒/四零与SHORT_ACTIVITY。从原accepted geometry重算rect包含、行框全文和身份，不只信completed/fullViewVisible布尔。restored state必须等所引用geometry，scroll完整数组等initial；reach和restore均维持所有现fixed_identity再加徽标identity。现chart完整operation枚举/96dp、动作48dp、最终恢复断言不删不松。

## 验证与后续验收

静态核request矩形动态来源、初态保存/请求/采证/finally顺序；离线针对runner实际校验逻辑验证：漏目标、错路径/别帧、改徽标身份、裁21px、ellipsis/行框溢出、只恢复outer而遗漏其他scroll均拒绝；正常完整目标/精确恢复接受。不能把离线数学或旧normal图称新short实证。语法/diff检查后冻结两SHA交Astra，root统一构建和本批正常推送，必需CI通过后才合并。

新四配置reachable/restored实际PNG须再做G1–G4有限视觉验收，特别1.3徽标底边与全文；机械通过不等于视觉通过。若一次合法请求仍不能完整显示，保存实际target/窗口/scroll后停止，另定位真实布局原因，不扩大滚动尝试或改产品掩盖。

无设备编码/队列/写入/未知重试变化，不缩字/隐藏状态、不恢复竖屏、不改变5秒/四零差。不发版。回退仅revert此测试/runner增量，原ZIP和旧失败保留。当前仅规划，未实施/构建/推送。


## Astra 静态验收（2026-10-23，冻结增量）

结论：本次测试/runner 增量静态通过，可交 root 合批构建；尚无新 Android 执行或徽标实图，不能签视觉通过。

独立重算 SHA256 与冻结值一致：

- `LegacyVisualMigrationChecks.kt`：`6eb04a817a1775b409c29c6816ead1a0a99261d7e26fea29712d6b08b02221b6`
- `check_legacy_visual_migration.sh`：`79c003ead4fd23f895ad4e6c21ee3dcf9716ab3f514737bc323a7af47e2bc0ad`

已读实际完整两文件 diff 及原 observation/whole、shortDetailState、shortFrameRefs、stableWindow 和窗口恢复链：

1. 初态返回 target 先保存，badge 报告先挂载，再执行唯一徽标 helper，完成后才接原 chart 链。目标从原 heading 唯一解析；其 identity 纳入原状态及 runner fixed_identity。没有改产品或旧 chart/button 的预算与操作枚举。
2. 全 ScrollView 对象及精确 x/y 在 main 保存，并与初态完整数组核对。已完整初态不制造滚动；否则只调用一次实际目标的动态 scrollX/Y 内容矩形请求。没有硬编码偏移、重试、额外窗口或字号调整。
3. reachable/restored 都使用既有稳定帧与 MeasuredShortActivity 路径，geometry 写的是 capture 返回 target。可达性同时检查原文/semanticId、实际映射、正面积 rect==visibleRect，以及原 Layout/fullView/ellipsis/行宽和高度约束，不单信 complete。窗口、对象、列表和滚动对象集合身份对照初态。
4. 请求/采证/断言在 try 中；finally 恢复全部保存 ScrollView，重新采 returned target，核精确原数组和原 textLineBoxes。reach 主异常保持，restore 异常 suppressed；单独 restore 失败仍抛出。completed 只在全部通过后设置。外层失败另写已挂载的小报告，写失败 suppressed，不吞主错；其原正常窗口恢复链保留。
5. runner 限定唯一 semanticKey、精确报告键、两条不同引用和固定路径；通过原 SHA/geometry/proof 同源与 SHORT_ACTIVITY 验证，并独立重算全文约束。原协议 5 秒、四零差、固定 display 及原 plot96dp/动作48dp 未放宽。恢复不要求徽标在原初态完整（1.3 初态原本裁切），而要求原状态精确恢复，符合合同。

独立执行 `bash -n`、实际新增 Python verifier AST 解析、两文件 `git diff --check` 均通过。Sol 的离线正反例报告不替代本次静态复核，也不替代 root 构建或后续四配置 8 张新图 G1–G4 验收。未改源码、未构建、未提交/推送。
