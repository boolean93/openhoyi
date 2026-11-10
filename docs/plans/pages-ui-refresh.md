# 刷新产品站真实界面截图

- 需求与范围：用户授权合并App改版后更新并发布Pages；保留原三张横屏首页/实时/历史版式与资源路径，仅更新真实截图、来源/alt和对应证据校验，不重设计站点。
- 来源分支/基线：develop，App合入提交58bbd96ff12f6f72d47130ac6f7bade4e788eef4（M）。
- 工作分支：chore/pages-ui-refresh；独立工作树openhoyi-pages-refresh，不修改既有候选或PR工作树。
- PR目标：develop。Sol实现及真机Mock取证，Astra只读复核；root负责通过scripts/create-pr、合并与Pages部署核验，本实现者不自行创建PR/合并。
- 步骤：M构建原签名同版本Debug+Mock，安装前核空闲/双包版本及签名/四明确非凭据业务文件，成对保数据覆盖；仅Mock取三张新横屏原图；保留原图且仅裁系统栏作展示图，记录原图/展示hash、APK及M映射；同步网站及测试。
- 验证：PNG尺寸/SHA/裁切像素一致、静态站既有测试、差异检查；浏览器桌面和窄屏原版式/灯箱。记录App源码M和后续站点部署SHA分别对应的含义，未部署前不写成功。
- 协议与设备：不改App/协议代码、不启动真实Debug业务、不发实体咖啡机命令、不清数据、不模拟器。按用户本地包规则沿用已装76，用源码/APKhash区分；安装数据保留与后续主动Mock样例记录分开，截图新增杯保留不删。
- 发布：仅授权静态Pages，非APK Release/发版；root合并站点PR触发Product Pages，核验实际head_sha、Pages部署及线上资产。
- 回退：通过站点revert PR还原旧图与文案重新部署，不改设备记录或共享历史。
- 未完成：待实际M双包构建/取证、站点检查、独立复核、PR及部署。

## 实际完成与验证

- M源码未修改App；`assembleDebug assembleMock -PhoyiVersionCode=76 -PhoyiVersionName=0.2.0-preview.76`构建成功（1分18秒）。原签名为`c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`；Mock APK SHA256为`ec64c35acbf549b960e0ed7d0b518b261bd3ddedba60bab388f89c08c67506ce`，实际已装APK拉回与构建字节一致。仅确认源码/已装二进制映射，不声称可重现构建、正式认证或硬件验收。
- 安装阶段Debug/Mock原包名同76成对覆盖，firstInstallTime及四明确非凭据业务文件hash分别保持；之后为截图新增一杯Mock，实际00:07运行图具有四线、四跟随值及单位。该杯自然结束，最终记录32.1秒/22.40g；晚到的停止坐标打开新确认框，已取消，未开始第二杯，不宣称主动stop验证通过。记录保留，未清理。错误的`live-idle`（实际Home）、`history`（实际确认框）未入站；历史正确来源为`history-actual`。
- 三原图均来自M构建的实际M2105K81AC横屏2560×1600；展示图仅裁`[0,60,2560,1564]`系统栏，2560×1504，Pillow解码逐像素等同原图连续裁切。原图与展示图SHA、UTC采集时间、M、Git树和APK身份已记录`site/assets/screens/provenance.json`；原图保留用于灯箱，全部截图URL带`v=58bbd96f`避旧缓存。
- 保留网站原版式、三个资源路径及其它内容；仅更新新图/真实alt/源码来源，正文实时枚举纠正为压力、温度、流速、累计注水。没有新增手机展示栏目、改动下载去向或发布APK Release。
- `python3 -m unittest discover -s scripts -p test_product_pages.py -v`：14项通过；来源断言由旧v53/未核验更为M/76/已核安装hash，保留PNG尺寸/SHA、链接、无闲置资产、可访问性、下载边界与部署范围检查。`git diff --check`通过。对应日志和安装/像素证据在任务工作区`evidence/pages-refresh/`，不随站点公开本地设备文件。
- 独立Astra逐张亲看三张原图，核原图SHA/裁切像素/M树/双包签名及安装数据保护，确认来源scope合理；其指出的正文“秤读数”与实时“流速”不一致已修。最终diff复核结论由协调者汇总。
- root实际IAB浏览：1280×720首页灯箱、放大开关、Escape返回正常；390×844功能段新实时图/历史图完整且无横向溢出，原图灯箱关闭、放大及原图链接可达，文案明确非手机截图；已reset viewport。本次不以此声称手机App硬件验收或M所有合入后CI通过。
- 设备UI会话已关闭并交回root，最后为Mock Home；本轮没有修改WM、density、font或rotation，没有启动真实Debug业务或实体咖啡机命令。后续独立短横屏修复不由本任务改App或占设备。
- 发布仍待root通过现有`create-pr`入口、必需检查、合并站点PR及核验Product Pages实际部署head_sha。App来源为M；后续站点merge/deploy SHA另记，未部署前不宣称线上已更新。
