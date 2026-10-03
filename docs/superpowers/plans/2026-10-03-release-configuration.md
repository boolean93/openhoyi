# 原生发布构建配置

**目标：** 落实apk-distribution.md第3阶段的构建能力，显式版本递增与外部签名验证，不切换当前开发签名，不安装或卸载。

**架构：** 独立mobile/distribution.gradle.kts处理版本属性和release专用预检。debug/mock沿用现有签名与默认版本；release的preReleaseBuild依赖预检。环境变量只持有外部密钥配置，不进源码/命令行；Java KeyStore读取密钥并核对公钥证书SHA256。缺配置或不匹配均固定错误拒绝，不打印秘密。

**配置：** hoyiVersionCode/hoyiVersionName显式版本，hoyiPreviousVersionCode声明上一版本（第一版可声明0）。发布必须提供三项且递增；这不证明平板已装版本。HOYI_RELEASE_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD/CERT_SHA256为外部签名环境变量，文件须位于仓库外。密码原样使用，不trim。

- [x] 新增scripts/check_release_configuration.py，用真实Gradle预检入口先复现缺API失败，再覆盖缺配置、缺版本、非法版本、不递增、证书不符、错误密码、仓库内密钥及有效外部签名配置。临时JKS仅用于测试并清理。
- [x] 新增独立Gradle脚本并接入mobile构建，预检挂到所有release的preReleaseBuild；默认debug/mock不要求秘密。
- [x] 实际Gradle验证及release任务依赖图；双开发APK构建与既有资源/签名核对。CI接入预检脚本与独立静态审查。
- [ ] 文档明确声明版本/证书校验边界；正式持久密钥选择、平板证书/数据和真实升级仍未验收。提交/推送并读云端结果。


本地证据：发布任务不存在的RED见/private/tmp/hoyi-release-missing-task.log；实现后11场景通过；补APK/AAB双任务图后的12场景最终日志/private/tmp/hoyi-release-configuration-final.log。隔离release实际签名/版本2/0.2.0/debuggable=false通过，日志/private/tmp/hoyi-release-apk-fixture.log；未生成持久密钥、不安装。开发回归/private/tmp/hoyi-release-dev-regression.log（170任务）成功，实际Alpha/Mock均默认1/0.1.0、原证书指纹一致，资源各7113模板与6转义回环通过。第一轮隔离构建因未缓存的macOS AAPT2及官方lint-gradle离线依赖失败，随后使用SDK AAPT2和已有显式aliyun缓存完成，没有跳过Lint或放宽签名检查。最后一项文档/推送/云端运行继续待完成。
