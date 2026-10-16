# Review inline workspace

Approved brew-review-v3 concept, spec §7/8/9, F5. Continue feature/task-first-ui/develop draft PR#4, recorded develop66ebda9/ed01de0 base, docs/git-workflow.md. No merge/release/device install/schema/control/inventory writes. Additive revert only.

Implement stable parameter and notes regions: bean/dose/free-text grind left, expectation/taste/next three multiline fields right at actual>=840 and min widths; compact stacks bounded parameters above notes. Keep same field instances when resizing, fixed save/details outside, native inline input rather than forced field dialogs. Local region scrolls handle long text. Each note retains16000 character filter and raw draft; text viewport adapts to actual region height without shrinking text. Keep original bean identity even if absent from current inventory; original note saving/trim/dose validation and no inventory mutation unchanged.

New TaskReviewSections owns stable two scroll regions and jump selector, focus/caret/scroll restoration; no business state. BrewReview uses it and restores existing raw draft/selection. Error jump returns to dose group.

Adapt ChineseFormWindowChecks to actual inline fields and keyboard; preserve long-text top/end scrolling,16000 limit, native accessibility editing/selection, raw text and fixed actions; modal expectations only apply to non-inline controls. Existing17 business paths still verify old bean identity, saved notes/recreation and no extra inventory events. Build then native all-window/font/IME screenshots; no local emulator means native red evidence pending. Independent review pending.

## 原生长文本坐标修正

ff1ef65的phone/tablet-landscape/tablet-large-font中文复盘均在键盘弹出后的longEditor可见性失败；此前长文本top/end两状态已通过。源码证据Android34 View.requestRectangleOnScreen明确接收content coordinates，并向父层换算时减child.scrollY；测试在EditText已滚到末尾后仍传(0,0,width,height)，请求的是文本起点而非当前可见编辑框，导致外层ScrollView错误滚动。使用getDrawingRect取得含自身scrollX/Y的当前视口，保留所有完整可见/IME遮挡/文本/本地滚动/16000限制断言。键盘后检查前增加截图，若仍失败有直接可视证据。不修改App文本/焦点/滚动门槛或缩小需求。精确新提交原生重跑仍需确认。

## 横屏IME短输入的实际高度修正

2773b29横屏日志已完成8个longStates、inlineObserved/imeObserved=true；新的失败在ChineseFormWindowChecks:505短粉量字段，65px输入仅60px可见，不是旧长文本坐标问题。TaskReviewSections参数区最低48dp，原生短输入最低52dp。按参数区短EditText实际测量高度+面板上下padding确定最低预算（不是缩小字体或输入目标）；长文本既有自适应/16000过滤/滚动断言不变。继续现有横屏原生输入/IME断言验证。
