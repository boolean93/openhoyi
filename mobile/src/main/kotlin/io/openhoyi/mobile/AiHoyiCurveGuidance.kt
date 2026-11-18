package io.openhoyi.mobile

/** App-tool adaptation of docs/curve-import/photo-to-json.zh-CN.txt, not a default recipe. */
internal object AiHoyiCurveGuidance {
    const val SCREENSHOT = """Ordinary chat images: use image/context to choose business intent. HOYI recipe screenshots use transcribe_screenshot with the App sourceAttachmentId beside the actual image; bean bags use bean research/recommendation tools. No separate mode. If the image is absent after compaction ask to resend; old answers/cards/summaries are not screenshot evidence.
HOYI schema=3. Checklist: all global/first-stage fields and 2–4 ordered stages in the tool schema. Missing/ambiguous/conflicting fields: ask BEFORE proposing; unseen never means false/0. Read labels/units/selected options, not switch color. Copy settings; do not tune or optimize. Graphs: setpoints, not measured timelines; infer no ramps/durations/flow.
变压: pressure, 恒压=false/跟随=true. 变流: flow, 慢速/Ls=pressureLogic false, 快速/Hs=true; firstSegmentFlowMode is false for first-stage bar, true for first-stage mL/s; later stages mL/s. Transcribe original labels/display values, not derived flags/scaled numbers; App maps schema3.
闷蒸=wait; seconds beside first water=first-stage time limit, not measured duration. 总流量: independent water ceiling, not inferred sum/unconditional hard stop. 总液重=weight target; 克重补偿=signed compensation. Zero weight disables weight stopping only; final-stage water is not an independent hard stop. Edits: preserve the complete source/hidden slots. A failed local lookup proves no identity/origin. Invalid units/ranges: ask, never clamp/round/drop stages."""
}
