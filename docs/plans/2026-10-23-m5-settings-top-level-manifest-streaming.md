# M5 / Settings 顶层 manifest 无损流式写盘

- 需求与范围：仅替换两个最终顶层`manifest.json`整体字符串写点，消除已证`JSONObject.toString(2)`扩容；不删字段/嵌套proof、不改引用、格式、key顺序、SHA清单、采样或门禁。
- 基线/分支：现已授权迁移批次`feature/task-first-ui`，已推head `3a78c4c4db4fa9b2fb9af688924c19431fa3d04e`，PR source `5b32705446f285afc12e0d403484f3ab66d01495`；目标develop。遵守[git-workflow](../git-workflow.md)与[task-template](task-template.md)，同Sol串行，不覆盖其他owner；另立任务才按develop新短分支规则。
- 依据：`build/task-first-evidence/ci-3a78c4c/m5-first-failure-diagnosis.md`及`settings-first-failure-diagnosis.md`。两栈均在最终manifest.toString(2)请求75497480-byte扩容，非PNG/PixelCopy。M5首History light1.3五特殊态已完成到最终写盘；Settings App已有更早full verify失败，Machine原catch异常又被finally OOM遮蔽。

## 已核实现及最小设计

现`mobile/src/androidTest/java/io/openhoyi/mobile/WindowProofStreamingWriter.java`逐key按JSONObject.keys原顺序、逐array按原index输出；两空格缩进、Android34转义及numberToString保持。8192-char缓冲，不构造全树字符串；同目录owned临时文件，关闭成功后ATOMIC_MOVE发布，失败删除临时文件并保原异常。支持普通JSONObject/JSONArray、null、String/Boolean及Byte/Short/Integer/Long/Float/Double；其他类型或非有限数字回退whole-tree，这是此次复用必须封住的路径。

**允许Sol范围：**以下两个Kotlin最终写点、同一Java writer增加严格入口、现`scripts/WindowProofStreamingWriterOracle.java`与`scripts/test_window_proof_streaming_writer.py`追加针对性验证。不改其他manifest、special-case持久化、NativeWindowFrameEvidence、runner schema、生产或Gradle依赖。不重构通用证据框架。

1. writer增加`writeStrict(JSONObject, File)`：复用现streamable遍历，若非支持值树直接fail-closed，不调用whole-tree fallback；支持树沿同一writeValue/临时文件/原子发布实现。原write API与兼容fallback行为保持，避免扩大到所有proof调用。严格预检不复制树、不序列化整树、不排序keys、不转换不支持值。可在内部传布尔strict共用路径，不能另写一套JSON编码器。
2. 仅将 `ExtractionHistoryVisualMigrationChecks.run`、`SettingsVisualMigrationChecks.run` 的最终 `File(directory,"manifest.json").writeText(manifest.toString(2))` 换成严格入口。`fileSha256`枚举仍在创建临时文件之前、仍排除顶层manifest，所有put顺序/cleanup/状态字段保持；不可提前写临时文件再让它进入清单。
3. 写盘失败必须非零，不把旧canonical或半文件当本次完成。若已有业务failure，写盘异常作为suppressed保留原failure并继续原抛出路径；没有原failure则抛写盘异常。不得为了保存manifest吞原错误或修改PASS标记；异常情况下原canonical不被部分覆盖。

## 验证与交接

- 复用未修改SDK34 org.json的字节oracle：加入真实已有Settings App顶层manifest及M5已落特殊态summary，比较strict输出与原toString(2)逐字节/SHA，包含嵌套proof、中文/转义、数字、null、空对象/数组和key原顺序。这两个输入是代表性值树，不冒称缺失的OOM顶层原件已恢复。
- 加入不支持对象/Number或JSONObject子类反例：strict明确失败、无fallback和无canonical替换；原write兼容测试仍过。保留write/close/publish失败注入与临时文件清理检查。
- 有限堆oracle复用原192MiB压力方案并走strict；合成顶层嵌套压力只证明内存路径，不冒称Android运行成功。两个真实运行最终manifest的strict检查仍负责运行时值树验证。
- `python3 scripts/test_window_proof_streaming_writer.py`、相关语法及`git diff --check`后冻结所属文件SHA交Astra。root唯一完整构建owner；统一双包构建、下批正常推送原PR，必需CI通过后才可合并，不微推/force/no-verify/直推主干。
- 不删嵌套字段、不改pretty JSON、不拆引用schema、不减少页面/状态、不改5秒四零差、不增加heap或升级依赖。若仍OOM，保新栈后另定位，不趁此扩大所有写点。

## 保留失败与边界

流式写盘最多关闭两处序列化分配路径，不解决所有常驻JSON/bitmap内存。Settings App `FAILED_ORIGINAL_FULL_VERIFY`与Machine先前未知异常必须独立保留；新manifest写出后才可读取Machine原failure。M5首格/后续格、清理font_scale失败与全矩阵均不能提前签过。未实施、未构建/触发CI；不在本任务并行做M8视觉或Curve/Legacy规划。

协议/设备风险：测试输出路径无业务编码/单位/队列/写设备变化，不重发未知。发布：不发版、不安装、不分发。回退：仅revert本窄写盘增量，原失败ZIP与未知记录保留，不清数据。

## Astra 静态终审（2026-10-23）

结论：本窄增量静态通过，可交 root 统一构建；不等于 Android 运行或视觉验收通过。

- 独立读取五文件实际 diff。strict 在创建 owned 临时文件之前检查完整值树；不支持类型明确抛错，不能进入原 whole-tree fallback。支持树仍共用原编码器、key/index 顺序、两空格缩进、缓冲与原子发布。旧 write API 保持兼容。
- M5 / Settings 仅替换最终写点；fileSha256 仍先枚举，cleanup、put 顺序、状态与原采证门禁未变。已有 failure 保留为主异常，写盘错误 suppressed；单独写盘失败也沿原 failure 抛出，不会返回成功。
- 已读离线 report.json：32 项 SDK34 org.json oracle、真实既有输入逐字节等价、strict 非支持值拒绝与 write/close/publish 故障保留 canonical，以及 192MiB 合成压力均通过。真实输入为旧 App 顶层与 M5 特殊态 summary，不是丢失的 OOM 顶层原件；此次审查未重复运行 oracle。独立 git diff --check 通过。
- 冻结 SHA256：writer `179632d621e6d78f30469c90de7878d1de04ff8c73d4390f3f4f973ecef21f8f`；M5 `f4cda3e73989bf3a1df2bb03f3cbfce6e56b3420fbc3141e1db7627a98f818ba`；Settings `9fbf9d54703bf02075a8a20dd25190fb10ad47ab873591dfb335f5560145c063`；Java oracle `219c232b3ebdfaf36c34fbc57b8f4cd0b508baadc9925a2813a91f79af21d65c`；Python oracle `4995fa314d0fccfd48c3e8dca01f6808dabeb3e8f4a8042ccf3a8be66b567411`。
- Settings 更早 App full verify 与 Machine 被遮蔽的原异常仍未关闭；M5 后续配置、font_scale 清理失败、其余内存路径与全矩阵仍待真实证据。未改源码、未构建、未提交推送。
