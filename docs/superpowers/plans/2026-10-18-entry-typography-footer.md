# 入库字体与操作区对齐

依据入库v2与166594d字体JSON，沿用develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。

仅BeanEntryActivity字段辅助标签14sp、输入16sp，名称/数量标签与输入间距8dp；日期日历48dp目标不变。固定底部补新增批次说明和文字取消，主保存仍突出限宽；取消仅finish，不保存。使用既有业务草稿切换恢复场景改为点击取消，验证库存不增加；两模式截图要求两入口均可见。八语言资源与Mock/测试包构建，实际大字/IME截图待CI。

不改数据/schema、日期解析、保存幂等、签名，不安装/设备写入/合并/发版。下一批普通push；回退展示及取消入口即可。

实施完毕：Mock/MockAndroidTest BUILD SUCCESSFUL in 47s，日志`/tmp/task-entry-typography-footer-build.log`；八语言XML、diff和分支规则通过。取消路径及两模式按钮可见检查仅编译，运行和IME/大字截图仍待CI。
