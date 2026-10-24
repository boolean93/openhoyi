# CurveActivity 浅色1.0限定图审

Astra，2026-10-24。source `58ae69ed99c1bbde98311fed24c8bf9c056b9134`，push run `39915609521`。仅 `tablet-user-landscape-light-font-1.0/library`，不签全 M3 或四配置。

原件 `build/task-first-evidence/ci-58ae69e/curve-artifact.zip`，定向提取该页到 `library-light1-review/`。实际看了批准 `curve-library-v2.png` 与六张原 PNG：`window.png`、`states/non-first-selection/window.png`、`states/list-restored/window.png`、`library-special-scope/preset-eligibility-and-slots/{chooser,slot-5,restored}/window.png`。主图 SHA256 `cf4ec0f76a09401202cd61f20d401e71f2c15d89bfacc872f57362fa4df79bb0` 与 manifest 对应值一致。原尺寸2560×1600，density360、font1.0、zh-Hans；只用对应真实测量解释画面，不以 manifest 的机械 PASS 代替看图。

## 已接受范围

主态、非首项选择与列表恢复三图的 G1–G4 **LIMITED_PASS**：

- 左侧搜索/管理、排序、横向分类、数量、连续列表完整，右侧名称/来源/资格、三列数值、真实压力图和固定双操作保留批准构图。browser `[216,254,1000,1429]`、detail `[1054,254,2524,1429]`，栏间54px=24dp；左右页内距36px=16dp。原列表约34%保留。搜索117px=52dp自然高；底部详情/使用按钮完整，无旧同行外框越界。
- 原图中字形、粗细、数字单位层次清楚，无乱码或重叠。实际字体SHA为冻结 `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`；title54px=24sp、section/metric45px=20sp、action/row36px=16sp、aux31.5px=14sp。93/℃与36.00/g两组baseline均585px、delta0。未反推未知概念图字体身份。
- 主图四阶段2/9/8/6bar与后续两阶段2/9bar、0/6/12轴字完整可读，未伪造时间轴。Canvas title到最高刻度18.719px、plot到阶段字24.925px，均超过既定18px间隔；保护区2dp保持。可见正文/辅助灰、青绿选中/主操作和蓝压力线与冻结浅色层次一致。
- 非首项图是query `factory-v3`、DARK、MOST_USED 的25项连续列表；选中factory-v3-008。上边缘半行是已有scroll位置，不是固定详情裁切。恢复图仍同选中项/列表位置。业务证据before/afterDetailsReturn/afterRecreate均position2、offset−65；只改selected，无机器命令、usage与其它模型未变。此只证明既定Mock流程，不是机器验收。

主图捕获第一attempt曾S1_S2=252而拒绝；同一5s内第二attempt accepted，S1_W1/S1_W2/S1_S2/D1_D2_app均0。没有忽略失败attempt或降低四零门槛。

## 槽位图的明确边界

chooser原图标题及槽位1–5完整，原生Dialog文字可辨、白卡/暗背景层次可接受，仅此弹窗画面。**slot-5与restored不能签固定底栏完整**：slot-5画面仍有“已将Espresso深烘放入槽位3”Toast，restored的只读FlowRaw画面仍有槽位4Toast，均覆盖底部详情区域。这是实看原图所见的旧提示残留，不由slot文件名推定提示应属于该槽，也不据此断言槽位写错；不在本批擅改产品/采集器。

其余slot1–4、路由target/restored、特殊长标题/只读/无压力/短窗与逐行滚达原图虽然已在ZIP内，**本次尚未看完，保持未验收**。已提取该页66张window.png不等于已审66张。其余三主配置、Import/Preview、原IME失败均未被本报告关闭。下一步继续现有原件增量图审，不新增场景或门禁。
