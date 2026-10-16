# 固定中文字体基线

依据用户新增字体一致性要求和已认可视觉规格，不将候选字体声称为ImageGen原图精确字体。建立可复现中文实现基线后须按原稿逐页审计字形、字号/字重、行高/间距。遵守docs/git-workflow.md；develop66ebda9/ed01de0依赖，feature/task-first-ui → develop草稿PR#4；仅普通推送，不发版/安装设备，追加revert保留数据。

采用已核验Google Fonts固定revision的NotoSansSC变量文件（hash见docs/evidence/task-first-font-candidate.json），保持原始字体不子集化；OFL许可随APK assets打包。字体本身默认wght100，XML字体资源明确300/400/500/700；正文400、强调500、主数字700，秤大读数沿用300。

仅中文locale采用中文字体主题覆盖，不替换日/韩/阿/泰字体。原生主题、共用HoyiUi、显式Scale/Extraction字体和四类Canvas标签共用TaskTypography；native原生bold继承family内700。XML fontVariationSettings由本地Android34 FontResourcesParser确认支持；最低API26不使用API28专属权重调用。缓存不可变Typeface，避免每次绘图重载17.8MB资源。

验证：字体源SHA256不变、编译/完整JVM/两MockAPK、APK内font/license及压缩体积；native字体权重/完整汉字/真实文本测量和截图后续CI，不能以代码已设fontFamily当完全视觉一致。Android官方字体资源说明：https://developer.android.com/develop/ui/views/text-and-emoji/fonts-in-xml 。

实现后复核：原setTypeface(null,BOLD)会绕开主题固定字体，已替换为统一700接口。native TaskTypographyEvidence检查默认字体真实测量、API28+权重及theme bold，并给24页截图附字号/字重/行高/padding/可见文字边界JSON。最终本地JVM/两MockAPK成功（/tmp/task-fixed-font-complete-build.log），297/0/0/1。APK内原字体/OFL哈希与来源相同，字体压缩11,276,545字节；native渲染与设计稿对照尚未执行，不宣称完全一致。
