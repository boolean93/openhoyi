# 粉量纠正后清除过期错误

沿用develop66ebda9+ed01de0 → feature/task-first-ui → develop PR#4，遵守docs/git-workflow.md。8c16949/run39082950054通过全部18业务路径，但dose-selected-draft实图显示18g/90→72g旁仍有上次18.0001触发的粉量错误。

根因TextWatcher只更新预览，没有清除已失效的dose_bad_amount。仅当当前输入可被BeanQuantity解析且>0，并且当前错误精确为dose_bad_amount时清空该错误及资源状态；其他保存/确认/称豆/未知恢复错误不清除，不做自动保存或库存写入。

扩展现有业务回归：18.0001提交后错误可见，改0仍可见，改18后该错误消失，原90→72预览和零写入断言保留，三状态截图与重复确认幂等保留。失败已有实际截图证据；本地构建Mock/test APK，CI运行才确认回归通过。无安装/设备操作/协议/签名版本schema修改/合并/发版，普通push，追加revert回退。
