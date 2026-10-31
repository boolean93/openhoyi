# 入库页双模式选择

依据已认可bean-entry-v2，继续develop66ebda9+ed01de0→feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。既有工作树，无其他未提交文件。

1. BeanEntryActivity以“新豆款 / 选择已有豆款”两个等宽48dp以上按钮替代混合模式下拉；已有模式下才显示真实豆款选择器，无库存时禁用已有模式。采用16sp文字、8dp内距、浅色选中面与清晰焦点边框。
2. 新名称草稿与已有豆款身份分开保存。新→已有→新不覆盖输入的新名称；已有→新→已有记住所选ID；重建保存模式、两种草稿。异步Spinner回调只在已有模式写入selectedBeanId。日期、重量、事件ID、库存保存与幂等保持原逻辑。
3. 八语言补已有豆款标签。日期区前增加细分隔，现有字段与短高滚动不删。
4. AdvancedBusinessUiChecks在新增批次后打开第二个未保存表单，验证切换、重建保持草稿和既有ID，取消后库存完全不变；ChineseFormWindowChecks验证两个模式按钮完整与48dp点击尺寸。构建Mock/test APK，原业务与横屏CI运行后再读取实图。无本地设备安装权限，运行验收由现有CI完成，不声称本地instrumentation红绿通过。
5. 普通提交/推送现有分支，不强推、不取消运行中CI、不合并/发版/安装。不改协议、签名、版本、schema；追加revert可回退UI，保留用户数据。

## 运行后计数修正

ff651be/run39081920735的business-flows已记录18条独立路径，新增模式切换/重建/取消检查及后续全部路径均已执行；失败精确位于末尾691行仍要求paths.size==17。同步Kotlin末尾断言、完整字段标记、shell两处成功标记为18，不删除任何检查，不降低库存/草稿/字段要求。修正后重新构建test APK、shell语法/diff检查，再由新CI确认全部收尾与恢复不变量。
