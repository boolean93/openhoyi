# Live four-axis display

Source/target: existing feature/settings-compact at aaab3ce; no new branch or push.

Keep raw telemetry, ShotSeries, persistence, gaps and monotone Hermite unchanged. Live chart keeps pressure/temperature left, water/weight right with measured title/tick widths; empty weight retains an axis without fabricated samples. Endpoint names remain in metric tiles. Historical projection stays unchanged. Time domain includes reference and never shrinks in one cup; reveal resumes current presentation using actual update spacing.

Validate targeted geometry/projection tests, compile, offline real-sample replay, then Mock visual checks coordinated with parent. No real-machine actions. Rollback is reverting these display-only files; no release or install without coordinated device scope.

Narrow follow-up: the 328dp content / font1.3 Android probe exposed header readout clipping because the fixed gauge sidebar left ~98dp per tile. Measure natural name/quantity widths; when two columns do not fit beside the gauge, give the legend full width below the gauge/title row and stack each tile name above its atomic quantity. Restore original wide orientation/margins when width grows. Preserve the failed exploratory 360dp-total-height evidence; it is not a supported 360x360 promise. Validate reasonable 520dp total chart height with plot width and height >=96dp, all name/value/unit ancestor bounds, and same-view narrow/wide/narrow measurement. Product changes require increasing both installed packages from41 to42.
