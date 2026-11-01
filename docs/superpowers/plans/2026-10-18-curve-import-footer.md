# 曲线导入底部操作对齐

沿用feature/task-first-ui（develop66ebda9+ed01de0）→develop PR#4及docs/git-workflow.md。4d308a7实图对照curve-import-v2：原预览按钮横跨全页、缺取消和常驻说明。固定底部显示现有八语言profiles_import_hint（14sp，完整文本含64KiB限制），宽度≥624dp时说明左、320dp动作右，否则上下排；取消finish不保存，原预览回调保留。48dp目标和编辑框局部滚动不变，不读剪贴板直到用户动作，不修改文件读取/解码/导入或设备行为。验证Mock/AndroidTest构建、diff；原导入/重建/短高/语言夹具继续，视觉验收待CI。仅本地候选，后续合批推送，不安装/合并/发版，不改签名。回退此footer布局。
