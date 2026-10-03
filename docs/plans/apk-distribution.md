# 原生安装包与签名连续性

## 已核对的当前状态

2026-10-03，mobile/build.gradle.kts 的 Alpha applicationId 为 io.openhoyi.mobile，Mock 为 io.openhoyi.mobile.mock。两者目前来自 debug 配置，默认 versionCode 为 1，versionName 为 0.1.0；现在支持显式版本和外部 release signingConfig，未配置时 release 预检拒绝发布。云端工作流只构建/验证开发包，尚无正式分发流水线。

本机当前两 APK 已由 Android build-tools 35.0.0 apksigner 验签，使用相同证书；指纹及文件 SHA256 见 ../evidence/local-debug-signing-baseline.json。该证据只描述指定本机构建产物，不证明用户平板中的证书，也不作为公开正式发行信任根。不能假设不同机器/云端的 debug 证书一致。

## 实施顺序与验收

1. 导出可核对的分发清单：从实际 APK 读取包名、版本、变体、APK SHA256、签名证书 SHA256，不靠复制 Gradle 常量；Alpha/Mock 包名错误、未签名、签名不符或版本参数非法时拒绝导出。源码提交目前由构建方声明，清单使用 declaredSourceCommit/sourceCommitVerified=false，不假称 APK 内有可验证来源。
2. 本机开发分发继续沿用现有签名；不自动卸载、不清除历史/密码/未知写入标记。后续有设备时先只读取得已安装包证书，与待安装包逐一比较；不一致时停止覆盖安装。
3. 正式发行接入外部持久化 keystore 配置与明确版本递增，密钥和密码不入库、不写日志、缺配置拒绝生成可发布包。既有 io.openhoyi.mobile 安装不可无损换任意新证书，因此正式密钥启用策略需先核对已安装证书和数据迁移；不得默默改变包名或假称可覆盖升级。
4. 云端的开发/Mock APK 不标为可覆盖本机 Alpha 的正式更新包。正式分发需相同发行密钥、构建来源和验签结果；暂不新增下载并安装任意 APK 的产品入口，也不借用厂商旧更新接口。

纯清单解析/错误拒绝可离线测试，Android 工具验签验证实际产物。覆盖安装、进程退出后的未知写入保护/密码/历史保留仍须用户恢复测试设备后验收；不以 APK 签名正确代替机器协议安全或安装更新成功。

## 第一阶段工具

`scripts/apk_distribution.py` 已实现只读 APK 校验与原子清单输出；不签包、不安装、不下载、不更换包名。仅支持单签名身份，多签名包明确拒绝，正式支持签名轮换另行设计。检查期间或输出前 APK 内容改变也拒绝。默认拒绝覆盖既有文件，指定 --overwrite 才在完整通过后原子替换。

```sh
python3 scripts/apk_distribution.py \
  --apk mobile/build/outputs/apk/debug/mobile-debug.apk --variant alpha \
  --expected-cert-sha256 c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54 \
  --source-commit 0b94f39dd2a147df59895ce1732a85589fd13496 \
  --aapt /Users/boolean93/Library/Android/sdk/build-tools/35.0.0/aapt \
  --apksigner /Users/boolean93/Library/Android/sdk/build-tools/35.0.0/apksigner \
  --output /private/tmp/hoyi-alpha-distribution-manifest.json
```

上述指纹和提交仅对应本次本机构建，应按待发布来源/已确认签名更新，不把工具输出中的 sourceCommitVerified=false 当作出处已验。Mock 使用 .mock 包名与 --variant mock。12项 Python 检查及真实两 APK 正向/错误证书拒绝验证通过，错误证书不覆盖已有清单；显式版本递增及外部 release 签名配置已开发（见下文）；持久正式密钥选择和设备覆盖更新仍未验收。


## 发布预检配置

`mobile/distribution.gradle.kts` 处理构建配置检查；Android签名DSL留在mobile/build.gradle.kts。默认开发包仍使用版本1/0.1.0和原debug签名。可以显式提供 `-PhoyiVersionCode=2 -PhoyiVersionName=0.2.0` 作为本次版本，不自动猜测递增值。

release 必須额外提供 `-PhoyiPreviousVersionCode=1`（首次发布可声明0），要求当前值严格更高。它是构建方声明，不能替代已安装版本只读比对。版本名限定常见数字版本与可选后缀，不接受空白/控制文字。

以下环境变量仅在本机安全配置或CI秘密中提供，密码不要作为命令行Gradle属性：

- `HOYI_RELEASE_STORE_FILE`：仓库外keystore绝对路径。
- `HOYI_RELEASE_STORE_PASSWORD`：原样使用的store密码。
- `HOYI_RELEASE_KEY_ALIAS`、`HOYI_RELEASE_KEY_PASSWORD`：签名私钥身份和密码。
- `HOYI_RELEASE_CERT_SHA256`：预先确认的64位十六进制证书指纹。

`verifyReleaseDistribution`检查三项版本声明、递增、五项配置、仓库外规范路径、私钥与证书指纹。不生成密钥，错误只输出固定提示；`preReleaseBuild`依赖该检查，未配置不会静默输出可发布的unsigned APK。当前既有签名不能任意换新证书来覆盖安装，正式配置必须先确认升级身份。构建后的APK仍应通过前述实际产物工具验签/核对，声明预检不等于硬件安全、源代码出处或设备升级成功。

`scripts/check_release_configuration.py`用临时JKS运行实际Gradle拒绝/通过场景，默认不出APK；可加 `--assemble-fixture --sdk <SDK>` 在临时独立buildDir生成一次测试release APK并实际验签，退出后清理密钥与产物。该fixture不是正式发行包。CI已接入实际fixture检查。APK/AAB任务图均需预检在preReleaseBuild之前；完整12场景的最终本地/云端结果另见HANDOFF。
