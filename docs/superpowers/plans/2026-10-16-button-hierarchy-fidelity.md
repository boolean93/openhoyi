# 共用按钮状态层级

按已认可稿和规格§触控/主操作、Google与Apple既有研究：次要操作不使用常驻阴影抢层级，禁用态不采用表示活跃的绿底。开发来源develop66ebda9+ed01de0依赖，feature/task-first-ui → develop草稿PR#4，遵守docs/git-workflow.md。

实读693f215萃取/历史截图发现次按钮仍有阴影，HoyiUi.button仅设置elevation=0但保留系统stateListAnimator；禁用态统一accent_soft。修正为禁用系统抬升动画并清除默认背景tint；禁用中性色；原生RippleDrawable按压反馈、键盘焦点描边，保留按钮语义、48/56dp、文字/字重/圆角与全部点击门禁。主按钮填充、危险语义不变。停止按钮在页面中另有固定危险底色，现有行为不改变。

仅样式，不改设备/数据/业务，不发版/安装/合并/改签名版本。验证双Mock APK与现有原生浅深色/大小字/可达检查；逐页真实截图才可视觉验收，不新增镜像样式断言。旧0cd05ccCI运行时不推送打断，普通追加提交/revert回退。

本地按钮样式双Mock APK成功38秒；补充TaskTypographyEvidence的elevation/translationZ/stateListAnimator和Ripple内容圆角测量后，测试APK编译成功21秒。状态层测量排除mask，不将mask误作按钮实际底板。diff check通过，原生效果待新截图，0cd05cc任务仍运行。
