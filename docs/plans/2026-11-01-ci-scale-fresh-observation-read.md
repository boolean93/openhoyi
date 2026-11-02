# Scale geometry observation read reuse

Status: reviewed design and compiled draft; not runtime-verified and not a claim of scale acceptance.

9be scale evidence: the first legacy-route-single-host attempt changed only the native right scrollbar (1080 pixels in a 9×120px rectangle). Attempt two had zero differences in every accepted pixel domain and equal before/after geometry, but completed comparison at5092ms and therefore correctly failed the unchanged5000ms budget. No prediction delay, scrollbar disable/mask or timeout increase is added.

The measured geometry-before/after stages cost about0.6–0.8s each. One geometry target contains470 node entries and1924 complete text line-box records, but only183 distinct complete serialized text records (182 distinct text/rect signatures). Main content is repeated under attached windows; every tagged ancestor repeatedly observes the same TextView layout and visibility.

Allocate one IdentityHashMap-based observation read for each synchronous main-thread geometry() invocation. Reuse an identical View observation and identical TextView line observation only within that call. A fresh read is allocated for each native before and after sample; none is cached across frames, capture attempts, or state changes. Text line visibility and parent node visibility remain independent objects, avoiding self-reference. Preserve every field, node order, repeated JSON position, complete layout line and chart/window proof. The JSON writer already serializes aliases by value.

Independent static review found the design equivalent. AndroidTest Kotlin compilation passes in32s (47 tasks). An offline replay of shared immutable JSON objects on the retained real artifact preserves all2812217 compact UTF-8 bytes, SHA2563f22b4fa9b3342c4715fd63f1dee67a4138dbd63ebfcb7da6573fcb21ad76d52. That replay demonstrates alias serialization semantics only; it does not execute Android View reads or prove speed improvement. New CI must still satisfy every fresh geometry/pixel comparison within the original deadline.
