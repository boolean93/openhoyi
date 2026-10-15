# 任务工作台改版：实施证据

2026-10-15。此文件只记录实际完成的批次，不把依赖或概念图算成新UI完成。

## 分支与依赖基线

- 工作树：`openhoyi-task-first`，分支`feature/task-first-ui`，从`origin/develop`的`66ebda95dc7986eec66f9fa0b7e6cbd565c74506`创建。
- 显式本地依赖合并：`ed01de0f1b6f6449257e3480053340a9bb75b160`，随后合入已确认设计和计划；未合并、关闭或修改任何远端旧PR。
- 实施前基线提交：`6f4d10489e55308aae4f85240adfd91bdcd122d3`。该点的`mobile`、`bean-core`、`device-session`、`protocol-core`、`bluetooth-android`与`ed01de0`逐文件Git差异为空。
- 依赖相对develop的239文件差异不冒称为本轮新UI；本轮真正实现按`ed01de0..HEAD`单列。

## 本地单测基线

正常Gradle命令先受到缓存锁沙盒权限限制；获得构建缓存访问后，又因用户全局Gradle配置指向不可用的`127.0.0.1:7890`代理，无法下载Maven版aapt2。已核实本机SDK35的aapt2可执行，只对本次命令指定其路径并离线运行；未修改全局代理、项目Gradle依赖或签名配置。

```bash
./gradlew :mobile:testMockUnitTest --offline -Pandroid.aapt2FromMavenOverride=/Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt2 --no-daemon --console=plain
```

实际退出0，`BUILD SUCCESSFUL`。JUnit XML汇总63个测试类、266项、0失败、0错误、1跳过。已有Android API弃用及aapt2覆盖的实验选项警告仍存在，不声称无警告构建。忽略目录中的本机详细记录：`build/task-first-evidence/baseline.json`。

这是继承代码的JVM单测基线，不证明新导航、四指标图、21页、IME、设备控制或真实窗口已验证。没有安装APK或连接硬件。

## 当前交付边界

首批窗口策略已实现于`45494cb`，仅新增`TaskLayoutPolicy.kt`及其测试：600dp导航切换、840dp及实测内容约束的双栏许可、短高窗口工具迁移、320dp绘图区的轴布局切换。没有Activity、服务、库存或回调依赖，尚未接入真实View。

实现者先运行新增测试，观察缺少策略类型/枚举的预期编译失败，再添加实现；定向测试7项通过，完整Mock单测XML汇总64个测试类、273项、0失败、0错误、1跳过。控制者独立读取XML确认这些计数。实际执行使用与基线相同的离线aapt2参数，不把原先受代理阻挡的普通命令记为通过。

独立规格审查通过；随后独立代码质量审查通过，无Critical/Important问题。一个非阻塞建议是增加精确内容fit差1dp及极大Int约束的防回归场景；当前实现已用Long计算且审查未发现溢出，此建议不代表现有用例覆盖了这些输入。

真实View组件、四指标图和21页迁移尚未完成，页面外观未改变。未推送或发版，不将该文件当成总体完成报告。
