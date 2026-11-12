# 用户曲线归档后的实窗测试夹具适配

- 需求与范围：曲线删除 PR #76 已通过本地/PR 门禁并合入 develop，但合并后 lifecycle `43251947618` 在快捷槽测试启动前失败：`NoSuchFieldException: CustomCurveStore.documents`。修复三个仍读取旧私有字段的 Android 实窗夹具，生产逻辑、UI 与全部门禁断言不变。
- 来源分支/基线提交：最新 origin/develop `9b88daccf01c1cc6d718de75f1df4b94cc8ad0df`。
- 工作分支：fix/curve-store-test-fixtures；沿用曲线删除独立工作树，不修改其他工作树。
- PR目标：develop；通过 scripts/create-pr 完整本地门禁，必需 CI 通过后合并。
- 步骤：共享测试夹具保存完整 state；在纯内存 CustomCurveStore 中通过 public save 生成临时状态，实窗运行后恢复原 state；保留原始文件字节、历史与槽引用不变的既有断言。
- 验证证据：compileMockAndroidTestKotlin/assembleMockAndroidTest；现有完整 verify；合并后原 lifecycle 全场景及 upgrade 同源码检查。失败原始证据 `/tmp/openhoyi-curve-delete-lifecycle-43251947618/shortcut-compact-Light.txt`，对应 GitHub run https://github.com/boolean93/openhoyi/actions/runs/43251947618 。
- 协议与设备风险：只改 androidTest，不新增设备命令、不改生产状态/参数，不弱化 timeout、清理和 no-shot/no-BLE 断言。本地不操作设备/平板/AVD；设备检查由既有 GitHub Actions 完成。
- 发布：用户已授权曲线删除自行完成验证后上线。本修复合入后等待同 SHA 四项 push 检查，通过既有 Develop 自动测试渠道发布包含曲线删除的修复源码；不重跑必然失败的旧夹具源码，不假装原失败已通过。
- 回退：测试改动可回退，但会恢复已知夹具失败。生产 v2 数据仍无通用降级迁移；不得直接安装旧读取器或清数据。
- 未完成与阻塞：发布等待修复后的完整 lifecycle 成功；并行 PR 若更新 develop，按实际基线重新验证。

遵守 [Git工作流](../git-workflow.md)。

## 本地证据

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:assembleMockAndroidTest --offline`：PASS，74 tasks，46s。编译不代表实窗运行已通过。
- `/tmp/CurveWindowFixtureProbe.java` 对实际编译的 Android 夹具 class 与当前生产 store 做 JVM 探针：临时曲线可见、缺失场景恢复、归档名称/空槽元数据保持、原 storage 写入次数 0、revision 不变、最终状态恢复，全部通过；未复制夹具实现、未使用 Android 设备。
- `git diff --check`：PASS。三个夹具的原始文件、history、slot、no-shot/no-BLE 断言保持；搜索 androidTest 已无对旧 `documents` 字段的直接反射。
