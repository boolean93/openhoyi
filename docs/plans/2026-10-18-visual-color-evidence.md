# 当前渲染颜色取证补齐（不改视觉）

- 需求与范围：由 Astra 规划与验收，Sol 执行。扩充 `TaskTypographyEvidence` 的当前文字色、提示色、状态、字距与基线；补 drawable 当前状态及可确定独立背景 ARGB。不得以资源预期色冒充实测，不按当前实现倒推设计标准。
- 来源分支/基线提交：本任务复用已授权工作树 `feature/task-first-ui`；开始 HEAD `116d19d52b42cf912ce627d1ef8745d939a9694f`，与 `origin/develop` 共同基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`。
- 工作分支：`feature/task-first-ui`；本批不新建分支、不推送或合并。
- PR 目标：`develop`；后续由主任务按 [Git 工作流](../git-workflow.md) 处理普通推送、PR 和四项必需 CI。
- 改动文件与步骤：只扩展 `TaskTypographyEvidence.kt`，新增 `TaskVisualColorEvidence.kt`、对应 instrumentation checks 和本规划。保留原 JSON 字段与 `metricRuns`；复用原 `layout` 的 margin/bounds，不重复造 geometry。`capture` 首次执行合成 Android 取证回归断言，再采集实际 View。
- 验证证据：编译 `:mobile:assembleMock :mobile:assembleMockAndroidTest`。新增断言覆盖 enabled/selected 状态色、selector 当前 child、Gradient 状态色/非纯色、tint、半透明、未知 drawable/Ripple/超出内存限额不误报、JSON round-trip。当前无设备操作授权；编译与断言实际运行分开记录。
- 协议与设备风险：仅 androidTest 取证辅助代码，无协议、固件、队列、未知记录、重试或机器写入变化。禁止设备安装、控制和自动重发。
- 发布：不发版、不打标签、不生成发布签名或升级/实机结论。
- 回退：仅回退本批取证与文档提交，保留其他代理修改；不卸载、不清记录。

## 色值语义与解析边界

- `resolvedTextColorArgb` / `resolvedHintColorArgb` 是 TextView 当前 drawable state 解析出的逻辑文字颜色。字形抗锯齿、父级透明度、选中文字高亮等不等于此色值；屏幕最终合成色须核对对应 PNG。
- `colorRuns` 记录 CharacterStyle 的当前 TextPaint draw-state 色，保留既有每 span 字号/字重证据，不修改原 span 或 View。
- 背景树保留 drawable 的类型、bounds、state、alpha、level。StateList 记录实际 `current` child；Gradient 的配置 fill 单独标识，不把它当考虑 tint/stroke 后的最终颜色。
- 只对精确类型匹配的 ColorDrawable、GradientDrawable，用 `ConstantState.newDrawable(...).mutate()` 克隆当前公开状态后独立软件渲染；自定义子类不猜测支持。组合容器仅记录实际 current/layers/child，整体混色为 unknown，不重构容器的逐层 alpha。绝不改原对象 bounds/state/level/alpha；不使用反射猜测私有字段。
- 每次只分配一个最多 262144 像素（约 1 MiB）的 ARGB_8888 bitmap，另有一行像素缓冲（不超过 1 MiB），立即 recycle；无跨状态缓存。超过限额返回 unknown，不降采样。仅逐一检查原尺寸**全部像素**相同才给 `uniformColorArgb`；发现不同像素即可停止均匀性扫描，记录实际 checked/total 数，且只报告精确采样位置和 ARGB，不推导 fill/stroke。
- 独立 bitmap 是透明底上的 drawable 输出，不是最终屏幕背景色；保留 alpha，不自行与页面背景混合。即使颜色 known，也不构成设计参数通过。
- Ripple 的动画和硬件 effect 不能从静态软件克隆可靠重现，整体颜色为 unknown，子内容独立记录；动画、未知子类、缺失 ConstantState、超限、零尺寸、绘制异常均有 unknown/reason。
- GradientDrawable 没有公共 stroke 读取接口，描边颜色/宽度无法可靠分离时明确 unknown，不把边缘样本称为描边。渐变、圆角透明边缘、多层内容不能冒称单一纯色。

## 未覆盖

Canvas 产品自绘 Paint（图表、刻度、路径、文字等）没有在本批采集；无设备/模拟器运行证据；无 Astra 完整四项视觉验收结论。间距/字体/字号/背景色基线由 Astra 独立制定，本批只补取证能力。

## 本地验证记录

- 2026-10-18：测试先落地时，androidTest 编译因尚未实现 `TaskVisualColorEvidence` 明确失败；这是取证新增能力的编译红阶段，不是 footer 回归失败，也不是 Android 断言运行结果。
- 最终源文件稳定后执行：`ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2`。
- 结果：exit 0，`BUILD SUCCESSFUL in 28s`，`91 actionable tasks: 4 executed, 87 up-to-date`；本地日志 `/tmp/2026-10-18-task-visual-color-build.log`。包含当前 footer fixture 与取证代码；这是可编译性证据，不是设备/模拟器测试运行、PNG 验收或四项视觉验收通过。
- 初次普通沙盒构建不能写入已有 `~/.gradle` 缓存锁，随后以授权的 `require_escalated` 构建成功；没有修改 Gradle 配置、杀进程或绕过现有门禁。
- 本批文件 `git diff --check` 通过。Astra 独立代码审查结论待主任务记录。
