# Mock subsecond value progression

Continue existing feature/settings-compact; no new branch or push. Root cause: Mock callbacks are scheduled250ms but pressure/water/weight previously used floor(elapsedMs/1000), making numerical changes1Hz. Real BLE data is about5Hz and unchanged by this fix.

Compute only synthetic values from elapsedMs, quantize to existing protocol units, preserve integer-second anchors, pressure cap, four-second flow phase and32-second end. Preserve the last sampled fractional synthetic weight on stop. Keep Mock callback250ms and all real BLE, ShotSeries, storage, interpolation and UI formatting unchanged.

Validate direct runtime samples at quarter seconds/quantization/phase boundary/stop/restart/end and existing scenario pause/no-offer semantics; compile and independent review offline first. Real recorded input and new Mock value change rates must be compared separately from animation callback timing. No tablet actions while user is acceptingv42. Later installation requires same-source increasing dual packages and data backup; no real machine commands.
