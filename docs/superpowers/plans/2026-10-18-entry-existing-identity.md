# 已有豆款去除重复身份展示

feature/task-first-ui→develop PR#4及docs/git-workflow.md。0649834实际平板existing-mode图中选择器与只读名称重复，开封日期首屏不可见；new-mode三日期已完整。保留原name控件/固定label/值/独立草稿，只在已有模式将name及其label设GONE，选择器继续展示实际选中名称且contentDescription不变；返回新模式恢复VISIBLE及草稿。原保存identity与状态恢复不改，不清任何存储。

先把既有assertExistingIdentity从要求重复框可见改为要求label/name均不显示且name禁用、值仍等于选中名称；保留choice可见/选中名称与真实bean.id一致断言，既有切回新模式草稿、重建、取消不写入测试不删。然后改renderIdentity。旧截图已证明新可见性要求不成立；新运行须由CI验证，不用编译冒充红绿运行。

验证Mock/AndroidTest构建、diff，后续实际existing-mode三日期截图/模式恢复检查。与分享间距候选合批普通push，不安装/设备/合并/发布/签名改变，回退追加revert。
