# 阶段压力预览的量纲与字体留白

认可curve-library-v2为阶段分类轴、0–12bar刻度与压力蓝；当前CurveStageView只有3条无刻度横线且压力用操作强调绿。按既有Google/Apple图表语义原则补0/3/6/9/12物理刻度与bar单位，统一chart_pressure色。完整预览使用12sp实际字体metrics计算顶底留白、以12刻度实际宽度计算左边距；阶段和目标标签保留。缩略图仍简化，不挤入不可读坐标，不将片段连接成时间预测；CurveStageParameters数据与投影不变。

遵守docs/git-workflow.md，develop66ebda9+ed01de0依赖，feature/task-first-ui→develop草稿PR#4。仅绘制，不改协议或曲线数据，不安装发版合并。验证双APK、现有阶段投影测试及后续原生浅深色/大小字预览截图，不能以Canvas代码代替视觉验收；普通push/PR，revert回退。

本批JVM与双Mock APK47秒成功，diff check通过；原生阶段坐标/标签位置待新截图，不认定视觉完成。
