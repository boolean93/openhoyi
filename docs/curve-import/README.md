# 官方参数截图搬运

将同版本 Release 的 `OpenHOYI-curve-import-prompt.zh-CN.txt` 下载后，与完整官方参数截图一起上传支持图片的 AI，或复制文件内容。外部 AI 不一定能打开 GitHub URL，无法打开时上传 TXT/粘贴全文。导入前对照截图核对，不把曲线图视为完整参数。

模板 `photo-to-json.zh-CN.txt` 是唯一人工维护文本；`examples/` 是两份格式/单位示例，不是默认配方。构建从 APK 的准确源码目录生成版本头和完整模板+样例，不能从发布控制器分支取模板。

`contract.json` 锁定实际解析、适配、执行规则及模式文案源码和模板/示例哈希。相关逻辑变化会使 Python 检查失败；维护者需核对自然语言和新旧格式后运行 `python3 scripts/curve_import_prompt.py lock --source .` 并检查 diff；语义或格式更新时先递增 contract.json 的 revision。更新锁不代替真实 JVM 合同测试。示例由真实 decode/encode 往返、adapter/start policy 检查，不维护另一个模拟 parser。

历史 Release 附件不覆盖。固定文件名位于各版本标签下：`https://github.com/boolean93/openhoyi/releases/download/dev-<版本号>/OpenHOYI-curve-import-prompt.zh-CN.txt`。使用与 APK 匹配的版本；不要用 releases/latest 代替开发版定位。
