# 固定字体后的原生空间回归

遵守docs/git-workflow.md，develop66ebda9+ed01de0依赖，feature/task-first-ui → develop草稿PR#4。693f215用户平板原生332/336，中文浅深色复盘IME长框360px仅150px可见，萃取图540/1391低于原40%。字体身份与字号断言已通过，不能撤回字体或放松布局门槛。

复盘代码根因：fitEditors在notes viewport为0时整体返回，且仅监听notes大小；IME压缩notes时，参数区研磨框仍保留旧160dp。参数区单独监听尺寸并独立适配，预算扣面板上下padding；不清空文字、不换editor、不改变选区/滚动/保存和库存。保留原长文本IME回归作为失败用例。

验证：JVM/双Mock APK与新原生三横屏/IME/保存恢复检查。萃取图仍单独待修，不据此宣称全部修复。不发版/安装/合并，不改签名版本，追加revert回退。

复盘候选本地验证47秒成功，297 tests/0 failures/0 errors/1既有skip，双Mock APK及diff check通过。693f215单屏已终态失败；研磨IME与图表高度仍需新原生证据，不提前称修复。萃取主数值应按原稿把辅助读数并入同一主读数行腾出图高，而不是缩字号或降低40%门槛，此项待实施。
