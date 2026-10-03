# 原生安装包与签名连续性

## 已核对的当前状态

2026-10-03，mobile/build.gradle.kts 的 Alpha applicationId 为 io.openhoyi.mobile，Mock 为 io.openhoyi.mobile.mock。两者目前来自 debug 配置，versionCode 固定为 1，versionName 为 0.1.0；release 尚无专用 signingConfig。云端工作流只构建/验证开发包，尚无正式分发流水线。

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

上述指纹和提交仅对应本次本机构建，应按待发布来源/已确认签名更新，不把工具输出中的 sourceCommitVerified=false 当作出处已验。Mock 使用 .mock 包名与 --variant mock。12项 Python 检查及真实两 APK 正向/错误证书拒绝验证通过，错误证书不覆盖已有清单；版本递增、外部正式签名配置和设备覆盖更新还未实现。
