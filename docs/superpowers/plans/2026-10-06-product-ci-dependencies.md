# 产品恢复验证的依赖触发范围

目标：Mobile产品恢复逻辑依赖共享会话、协议、Android BLE与采样模块；修改这些生产代码不能跳过生命周期与升级Mock检查。

现状：生命周期只跟踪部分Mobile文件，恢复状态/门禁变更无自动Mock；升级只跟踪两个RecoveryState名字，漏掉其它共享门禁。Mobile依赖bluetooth-android/trace-core，前者依赖device-session，device-session依赖protocol-core。

方案：两workflow的push paths统一覆盖五个实际依赖模块的src/main与build.gradle.kts、Mobile的src/mock变体输入、根build/settings脚本、Gradle配置/wrapper和Mobile distribution脚本。保持各自执行器/相关测试/工具路径。文档、普通JVM测试与无关Lab源码不触发这两个产品任务，完整native CI仍原样运行。新增生产文件由目录范围自动覆盖，无需维护类名清单。

仅CI触发配置，不改任务步骤、权限、签名、模拟器隔离或生产控制。沿用既有运行中任务，不手动重复触发。

- [x] 先用实际Git跟踪生产文件验证旧filters的遗漏。五模块147个main文件，生命周期漏117、升级漏133；不能把路径触发覆盖当功能测试覆盖。
- [x] 补两workflow依赖范围；YAML解析与路径匹配确认149个main/Mock文件、12个构建路径和未来新源文件均匹配，普通JVM测试/文档/Lab排除。复审指出Mock变体manifest/资源遗漏，已补src/mock并复验。
- [x] 复核job、权限、分支定义未改变，独立只读复审修正后无阻塞。提交推送后记录现有自动任务。
- [x] 核对5a82936三项任务均success，实际native日志、升级seed/verify/result与Mock完整产物读取；原13/78/13、busy10/58及原矩阵通过。不是后续18入口或12/69预热新增状态证据。

源码 `5a82936ecf7ce4140ea77dfc24e841dd44b46c90`；自动native `37376132381`、Mock生命周期 `37376132608`、Mock升级 `37376132464` 均已确认in_progress。尚未实际产物通过，不手动重复触发。

最终证据：`docs/evidence/product-ci-dependencies-2026-10-06.json`；实际产物 `/private/tmp/hoyi-product-ci-mock-37376132608`。
