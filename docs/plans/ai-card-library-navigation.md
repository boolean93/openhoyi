# AI 卡片查看曲线路由

- 需求：查看曲线只浏览库里对应ID和内容版本；不选中萃取、不修改、不启动机器。主‘确定’仍预填编辑器，完整参数/diff保留独立文字展开。
- 来源/分支：既有develop来源feature/ai-chat；保留所有dirty改动，未来目标develop，本轮不推送/PR/合并。
- 当前已改：AiCurveLibraryRoute纯policy、focused fake测试；CardView/Presentation可选onViewCurve和available；Activity新增未接线handler。
- 绑定：待Main/CurveFragment唯一owner整合/tmp/ai-route-main.patch与/tmp/ai-route-fragment.patch后，AiChatActivity构造AiChatPresentation追加onViewCurve=::viewCurveInLibrary、curveLibraryAvailable=::curveLibraryAvailable。未整合前保持callback=null，不露出未接通按钮。
- 安全：推荐匹配完整snapshot；REPLACE仅原before；COPY_FACTORY仅factorySource；CREATE没有已存库目标。发送与接收重校验完整fingerprint/ID，失效拒绝不按名称替换。仅浏览，不写selected prefs，不写曲线。
- 验证：已源码自查；AiCurveLibraryRouteTest两项待执行；编译/lint由唯一build owner后续安排，尚未接通不称交付完成。无设备/API操作。
- 发布/签名：不构包/安装，不修改142已冻结/安装包或R8配置；新候选需完整home/history基线。
- 回退：仅精确本轮文件增量；不回滚他人代码/数据。
- 阻碍：首页owner已由协调者核实idle并交接写权；Main/CurveFragment与PR119 fa17e28a及142完整R8快照逐文件SHA一致后应用精确路由，Activity已接线。编译及测试待统一owner执行，尚无真实Android导航证据。

- Astra后修：宿主pending inert reference在stateSaved时保留，onPostResume实际show后重新解析当前库再消费，配置恢复不丢导航；失败提示走AppDialog统一字体。仅导航证据持久化不携带写许可。
- A/D视觉：已实际查看本机materialized选择图；同一组件名称/徽标同排，摘要按真实测量宽度同行或换行。浅色蓝目标线、深色青目标线，单单位去冗余刻度但保目标+单位，混合单位保双轴。不画时间采样/跨段插值，真实参数未改。图48dp下限并随文字增高，保持14sp/16sp500与48dp动作。待统一编译/Android视觉确认。
