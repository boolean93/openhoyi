# 共用按钮状态层级

按已认可稿和规格§触控/主操作、Google与Apple既有研究：次要操作不使用常驻阴影抢层级，禁用态不采用表示活跃的绿底。开发来源develop66ebda9+ed01de0依赖，feature/task-first-ui → develop草稿PR#4，遵守docs/git-workflow.md。

实读693f215萃取/历史截图发现次按钮仍有阴影，HoyiUi.button仅设置elevation=0但保留系统stateListAnimator；禁用态统一accent_soft。修正为禁用系统抬升动画并清除默认背景tint；禁用中性色；原生RippleDrawable按压反馈、键盘焦点描边，保留按钮语义、48/56dp、文字/字重/圆角与全部点击门禁。主按钮填充、危险语义不变。停止按钮在页面中另有固定危险底色，现有行为不改变。

仅样式，不改设备/数据/业务，不发版/安装/合并/改签名版本。验证双Mock APK与现有原生浅深色/大小字/可达检查；逐页真实截图才可视觉验收，不新增镜像样式断言。旧0cd05ccCI运行时不推送打断，普通追加提交/revert回退。

本地按钮样式双Mock APK成功38秒；补充TaskTypographyEvidence的elevation/translationZ/stateListAnimator和Ripple内容圆角测量后，测试APK编译成功21秒。状态层测量排除mask，不将mask误作按钮实际底板。diff check通过，原生效果待新截图，0cd05cc任务仍运行。

e6eedb8原生Home测量进一步显示：浏览曲线库按钮高度150px（density2.25，约66.7dp），includeFontPadding=true，默认上下padding各23px。统一关闭额外字体留白，并在背景设置后明确左右16dp、上下8dp；仍保持16sp/500、48/56dp最小触控高度、wrap-content及长文本换行，不设固定高度或缩字。页面已有显式padding覆盖仍保留。此项需构建与下一批原生截图验证，不能据代码推断最终高度或视觉通过。

本批双Mock APK构建37秒成功，git diff --check通过。e6eedb8高级页面CI已成功，单屏用户平板和大字体任务失败、整体仍运行；本批按钮修改尚未推送，不将旧CI结果归于新样式。

e34f8cd大字号原生发现共用padding导致固定48dp信息按钮ⓘ横向溢出；16dp左右仅剩16dp文字区，小于1.3字号图标自然宽。beanPage该图标按钮改左右8dp，保持48dp触控和原字号/语义；普通文字按钮仍16dp。新失败来自实际原生矩阵，不降低文本溢出检查。需重新双APK及下一批大字体原生验证。

该大字体报告208/336，128个页面失败均为ⓘ横向溢出；早期页面检查失败会阻断该页后续表单检查，不能据此判断旧IME裁切消失。图标内边距修正双Mock APK39秒通过，diff check通过，原生待下一批。
