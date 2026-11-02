# 入库底栏与稿件同行

feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。用户平板0d6fb9c bean-entry-new-mode.png显示底栏说明独占一行，第三日期在首屏部分不可见；认可bean-entry-v2为说明/取消/保存同行。仅BeanEntryActivity底栏在可用宽度≥624dp时说明weight1、操作320dp同行，窄宽保持上下；复用原按钮实例与回调，保留14sp说明、16sp输入、48dp触摸及日期/身份/独立草稿。原局部滚动、键盘恢复不变。

验证Mock及AndroidTest构建、diff，后续平板新/已有两模式、大字/键盘与三日期截图必须复核。单个可逆排版调整不增加镜像单测，原业务门禁保留。不安装、不改库存/协议、不合并发版或签名变化；与分享空间修正合批push，回退追加revert。
