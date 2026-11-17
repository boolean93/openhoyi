# 附件密文原子发布事务

- 范围：仅未来附件写入发布与同目录事务锁。区分own未发布temp、已发布ciphertext、后续manifest引用；不扫描/删旧unknown/temp/损坏/未恢复/跨模型引用文件，不为quota驱逐。保留上一轮SIZE检查、AAD remove和全部未提交实现。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。不提交/推送/PR/合入，遵守[Git工作流](../git-workflow.md)及[模板](task-template.md)。已读AGENTS；无.agents目录。
- 分工：Sol唯一写者及串行Gradle；实现前root/Astra审接口与不覆盖发布方案，Astra最终独立只读审。
- 文件/接口：AiAttachmentVault保留原storage四方法并加默认可重入transaction(action)，现有内存fake兼容。Android adapter移至AiAttachmentPublication.kt；AiAttachmentPublishOps提供writeAndSync、publishNoReplace、syncDirectory故障注入缝。新PublicationTest及本计划，不改Activity/Session/schema/epoch。
- 选择依据：[Android Os](https://developer.android.com/reference/android/system/Os)的link/open/fsync从API21可用，项目min26。普通rename可覆盖；[Files.move](https://developer.android.com/reference/java/nio/file/Files)的ATOMIC_MOVE不能作为不覆盖承诺。因此采用同目录Os.link原子创建final（存在则失败），再清own alias；不是复制或REPLACE_EXISTING，不支持硬链接/目录sync时失败关闭，无覆盖fallback。JVM测试用标准createLink验证同类语义，不冒充Android syscall/掉电实测。

## 状态与故障边界

1. 同目录OS FileChannel.lock加JVM monitor排他，可重入；Vault密钥existing/create、加密与发布在同一lease，load及认证→delete也同锁。路径只接规范UUID；control必须常规文件，final读取拒绝symlink。控制文件为本实现固定名字，仅空常规control不计hasAny/quota；非空control、unknown、旧temp一律计内容，丢Key时不换密钥。
2. quota在锁内检查，未知/temp都计128件/64MiB，拒绝超额，不清旧物。随机pending名字createNew独占成功后才建立本次所有权；write+fd.sync完成前final不存在。
3. 标记publishAttempted后调用no-replace link；最终目录fsync成功才返回可引用id。target存在的预检仅优化，真正不覆盖保证来自link syscall。
4. publish前write/同步失败只尝试删除本次独占created temp；不能证明创建成功或发生进程终止就保留未知。publish调用抛错可能已完成；publish尝试后任何异常均保留temp及可能final，不据exists推断结果、不回滚final。
5. link+目录sync已确认后仅尝试unlink本次已证同inode别名temp；清理失败仍返回已发布成功，残alias保留占额。无startup恢复扫描/删除，反复重开读取不改变文件。目录sync完成不等于所有OEM/介质/硬件掉电耐久保证。
6. manifest提交在caller层，只有writeNew成功后才可引用。manifest失败或进程死于两者间，published未引用密文仍保留，不能在此层擅删。原有显式无引用清理仍须AAD/metadata认证；本轮不增加自动回收。

## 验证与操作边界

- 最小7例：完整同步前无final/成功后无own temp；write半途、publish前未知、publish后未知、dirsync失败四代表阶段；重复恢复/重复id；target抢占与双实例只发布完整一个；unknown quota/路径；empty-control首次key及unknown阻止替换key；manifest保存失败保留published。另复用Vault5例AES/epoch/metadata/丢key/副本恢复，并补1例事务退出失败时未交付明文清零。测试目录全部本次合成且独占，结束后仅销毁这些fixture目录，不操作应用数据。
- 未完成/风险：不运行Android真实Os/Keystore/电源故障/磁盘满，fake目录sync是注入点而非真实primitive证据。未知temp/未引用published占额可能使后续添加失败，后续显式管理另任务；不做重启自动清扫或quota驱逐。
- 发布/签名/升级：不发版、不改版本签名、不安装，旧133/132兼容保留；旧UUID密文格式路径不变，新control/temp不当附件id。
- 回退：保留旧密文读兼容；只能回退本轮代码，不扫描删temp/unknown/manifest未引用的final。回到旧写入实现会失去新原子发布保证，不能用清数据降级。
- 本轮禁止：设备/真实Key/用户数据/付费API/Computer Use/咖啡机/PR/push；不改变全局设置。

## 实际结果与独立审查

- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ANDROID_HOME="$ANDROID_HOME" ./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiAttachmentPublicationTest --tests io.openhoyi.mobile.AiAttachmentVaultTest`。
- 首编译发现Android SDK无公开O_DIRECTORY，已核对本地android35 public API及官方OsConstants，改O_RDONLY|O_NOFOLLOW|O_CLOEXEC打开后fstat/S_ISDIR核实目录再fsync，没有猜常量或降级覆盖。
- Astra预审发现事务action返回后锁release/close仍可能失败，load不能在action内提前放弃正文擦除责任；已将content所有权转移移到整个transaction成功返回后，并补post-action抛异常fake验证未返回buffer全零。
- 最终13项（Publication7/Vault6）0失败/错误/跳过，BUILD SUCCESSFUL in 51s；compileMockKotlin实际执行通过，资源UP-TO-DATE。无无关全量、lint/assemble或设备测试。
- Astra最终独立只读审查通过，无阻断；逐一核对源码hash与最终13项JUnit结果。
- 最终日志`/tmp/openhoyi-attachment-publication-tests-final.log`；首轮编译失败日志`/tmp/openhoyi-attachment-publication-tests.log`保留；计数`/tmp/openhoyi-attachment-publication-test-counts.json`。
- before `/tmp/openhoyi-attachment-publication-before`，精确5文件diff `/tmp/openhoyi-attachment-publication.diff`，前后SHA `/tmp/openhoyi-attachment-publication-manifest.json`。测试使用本次独占合成临时目录和JVM硬链接；Android Os.link、目录fsync、SELinux/OEM/实际掉电仍未执行验证。
- 无设备/真实Key/用户文件/付费API/CU/机器操作、无版本/签名/安装/PR/push。旧133不变，manifest/密文schema与132epoch兼容未改。回退若采用旧adapter，空control可能被旧hasAny当内容且失去新发布保证；保留此adapter或向前修复，不以删除未知文件恢复。
