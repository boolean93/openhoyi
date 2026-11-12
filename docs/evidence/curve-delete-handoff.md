# 曲线删除：实现交接

## 范围和工作树

- 工作树：`/Users/boolean93/workspace/hoyi_app/openhoyi-curve-delete`
- 分支：`feature/curve-delete`，基线 `df108928b2f1aa88f3ba662c60320101cfde0359`。
- 仅本地实现；未推送、未创建 PR、未合并、未发版、未安装；未操作共享平板、机器、秤或 AVD。
- 规划：[curve-delete](../plans/curve-delete.md)。协调会话已转达 Astra 对 `a5648127` 独立审查无阻断；本记录中的自查和测试由实现者完成。

## 行为和实现

用户自建/原生导入 document（包括只读参数文档）在详情更多菜单末尾显示「删除曲线」。确认保留历史；占用快捷槽时提示关联槽清空。成功后返回列表，显示「曲线已删除 / 撤销」。Factory 与 LegacyCurveStore 不变。

`CustomCurveStore` 保留 document，原子提交 archive token 与失效快捷槽引用；写成功后才发布内存和 revision。v1 文件可读，无归档数据时继续写 v1；首次归档写带校验的 v2。不需要另一次 SharedPreferences 清理，也不存在清 preference 后回退工厂配方的窗口。

快捷槽保存独立绑定身份，删除后的原绑定派生为显式 `empty`。首页显示「选择曲线」，点击进入曲线库；所有读取入口和服务发包前都使用同一投影。撤销恢复原 ID，未修改原 preference，因此新绑定（含重新绑定同 ID）不被覆盖。重导同 ID/同内容恢复 document，但旧快捷槽保持空；不同内容走已有冲突/另存副本流程。

服务提供按曲线的正在启动、去皮、萃取及温度准备占用保护。确认操作最终提交时再次检查；既有 selectedCurve / library.validated 发包前校验保持。其它曲线仍可删除。旧编辑窗口提交需要源内容匹配且未归档，失效分配窗口也再次校验。

历史列表、详情、复盘使用归档名称。没有删除或改写萃取历史、usage、样本、已导出的分享文件或机器拨杆 name/frameHex/curveId 快照，没有新增设备命令。首页按曲线 revision 刷新最近、上次及快捷槽。

## 验证

通过以下离线检查（JDK 17，现有 Android SDK，未使用设备）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
ANDROID_HOME=/Users/boolean93/Library/Android/sdk \
./gradlew :mobile:testMockUnitTest \
  --tests 'io.openhoyi.mobile.CurveDeletionTest' \
  --tests 'io.openhoyi.mobile.CustomCurveTest' \
  --tests 'io.openhoyi.mobile.CustomShortcutTest' \
  --tests 'io.openhoyi.mobile.PresetSlotsTest' \
  --tests 'io.openhoyi.mobile.HomeLastCurveTest' \
  :mobile:assembleMock --offline
python3 -m unittest discover -s localization/tests
python3 localization/validate_catalog.py
python3 localization/android_resources.py --check
python3 scripts/git_policy.py branch
git diff --check
```

Gradle：`BUILD SUCCESSFUL`。共 29 项 JVM 测试，0 失败；其中新增 7 项涵盖原子失败、历史名称、缓存 profile 失效、首页上次/最近、快捷槽、不覆盖撤销、原生只读 document、旧编辑窗口、同 ID 重导及 v1 迁移/校验。资源测试 25 项通过，目录/生成校验无 drift。

Gradle 原始日志：`/tmp/curve-delete-build.log`。JUnit XML 与 HTML 位于本工作树 `mobile/build/test-results/testMockUnitTest/`、`mobile/build/reports/tests/testMockUnitTest/index.html`；后续过滤运行会替换该报告，当前最新报告为新增的 3 项离线兼容测试，原 29 项结果以本记录与原始构建日志为准。补充证据见 [升级与验收准备](curve-delete-upgrade-readiness.md)。

Mock APK：`mobile/build/outputs/apk/mock/mobile-mock.apk`。

- SHA256：`99219c33849d2b720154093b542c7b6a65936a6850c0e3d0286ce4bffca3ae1c`
- 包名：`io.openhoyi.mobile.mock`
- 当前离线构建使用仓库默认 `versionCode=1`、`versionName=0.1.0`，未分配发布号。**此产物不是平板覆盖安装候选**；以后获得设备许可时，先核对已装 versionCode/签名，再按日常覆盖规则以同源码重建。
- 本轮没有创建 PR，未宣称完整 verify/CI 通过。

## 审查重点与未验证范围

- 核对确认文案、菜单位置、列表返回及撤销栏的横竖屏实际体验；本轮没有设备许可，不能把离线通过当成 UI 验收。
- 核对活动曲线保护与实际服务状态/发包队列之间的衔接；单测验证保护策略和旧 profile 失效，未连接真实设备。
- 撤销只保留当前进程最近一次删除；进程重启后归档和空槽仍保留，撤销入口不保留。没有搭建回收站。
- 槽失效引用保留在原子文件中，SharedPreferences 可保留旧原始值；必须经 `PresetSlots.curveId(..., library)` 或 `route` 读取。已覆盖当前全部生产读取入口。
- 存在 v2 归档数据或空槽元数据时旧版本不能直接读取该文件。当前没有通用降级迁移工具；撤销/重导入不构成通用回退方案，整项 revert 会移除 v2 支持。回退须用保留 v2 语义的前向修复；不得卸载/清数据或直接降级。补充离线边界证据及待执行实窗步骤见 [升级与验收准备](curve-delete-upgrade-readiness.md)。
- GitHub CI、全量 lint、双包设备覆盖安装、横竖屏、真实机器/秤均未执行。本轮没有创建 PR，后续须按 create-pr 规则完成所需本地检查及必需 CI。

## 上线授权与基线整合

协调会话转达用户明确授权：曲线删除由助手自行完成测试后上线，不需要用户人工参与验收；整合 Mock 的确认/删除/撤销已通过，Astra 复核通过。本分支合入 `origin/develop=56e3b9ac`（合并提交 `9b3dcfe6`），无冲突；检查首页稳定槽索引与空拨杆隐藏保持兼容。后续通过仓库 `scripts/create-pr` 完整门禁创建独立 PR，等待精确头必需 CI 后合入 develop，再跟进同 SHA 四项 push 证据和自动测试发布。前文“未推送/待调度”等属于此前实现阶段记录。

本工作树继续不操作平板或真实硬件。v2 没有通用降级迁移的限制不变，不能把单次撤销/重导入当成可降级保证。
