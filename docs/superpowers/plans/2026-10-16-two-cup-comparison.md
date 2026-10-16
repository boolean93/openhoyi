# Two-cup focused comparison implementation plan

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Independent spec then quality reviews before validation claims.

## Goal

Accepted spec §5/7/9: select two actual saved cups, compare ONE focused metric on ONE shared-time plot with SAME physical scale, solid/dashed distinguish cups. Four switchable metrics PRESSURE/bar WATER/cumulative ml WEIGHT/raw g TEMPERATURE/raw °C; never8 lines/2 separate independent charts. Current ru phone failure has each old chart108px, below96dp. Implement real redesign, do not lower usable-plot checks. No machine control/storage changes. PR#4 draft, no release/install.

## Ownership

Worker only BrewComparisonActivity.kt, new TwoCupChartView.kt, all8 locales new task_comparison.xml. Root owns tests/CI/evidence; other workers own Extraction/Home/shared layout. Do not revert their files. No Gradle until root coordinated.

## Required behavior

- Preserve first/second intent IDs and savedInstanceState, full original dropdown labels/accessibility, date chip allowed short but full source remains available; add explicit swap, no duplicate load loop. Save selected metric too. Same cup selected shows original choose-different and no false comparison. Missing IDs do not silently compare wrong cup; current absent selection must be explained.
- Single native chart with focused metric selector (Spinner or bounded native2row controls >=48dp). Two sources clearly first/second+actual date and status, unknown warnings visible, shared same/different/unknown bean and same/different curve info retained. All original detail/notes entries remain reachable, complete notes and observation data unchanged. Supporting details may locally scroll, main plot and task stay fixed, never whole page scroll.
- Both samples async loaded once per source/version, callbacks reject destroyed/outdatedActivity. Empty/failed independently explained, never zero series, no errors swallowed as success. No storage writes.
- TwoCupChartView public-internal API firstPoints/secondPoints List<ShotPoint>, metric ChartMetric defaultPRESSURE. Expose data/internal projection for native tests if needed, not fake semantics. Use FourMetricChartProjection.build(align(first).points,align(second).points) for source values/combined range/gaps/reset/no inferred weights. Each chosen metric's current/ref share series.range and timeEndMs. A and B have NO reference/current future semantics, just two saved cups.
- Explicit per-source alignment observed-origin vs record-origin from ExtractionReference.align; never imply first observation is true extraction start without observedOrigin. Missing weight one/both show exact absence. Source raw temperature and raw scale labels clear. Machine cumulative water from samples, no integration/flow confusion.
- Solid A and dashed B plus labels, not color alone. Pure curves don't cross gaps, reset or missing data, singletons visible. Physical y min/max/unit and elapsed s axis labelled at>=11sp respecting fontScale; measure text extents/gutters, no clipped text. Usable normal chart>=96dp with actual plot; extreme height explicit local notice/recovery not silently blank. No shrink fonts/ fake default min values drawn as observed lines.
- Narrow windows toolbar/details locally scroll, do not split2charts. Do not alter shared HoyiUi/bean page helpers. Original controls that need4buttons can be supporting local panel keeping chart area primary.

## Root validation

Original ru failure is regression evidence, not new feature red proof. Add real native checks for one plot/first+second populated, focus toggles/shared physical axis/swap/context, missing/gap/longName/font1.3/recovery. Adapt original SingleScreenPageChecks equivalent contract from2ShotCharts to1TwoCupChart, keep >=96dp and all visibility. Existing business17/lifecycle/upgrade/data baselines remain. Run JVM295 and2APK builds then actual3window screenshots/8languages2themes, read actual output. No claim wholeF5 or21pages complete for this batch.

## Actual wide-font regression follow-up

Exact d86a599 advanced pages run38838413516: business passed; compact240page fixtures passed, wideFont failed at BrewComparison selected date2023-11-1422:13:20 ellipsis. Actual supporting column280–320dp splits into two too-small cells. Stack selectors below600dp measured selector width; retain stable instances/identities and use complete selected-date text (opt-in unlimited lines, others unchanged). No geometry or text-fit check removed. Repeat exact native CI before acceptance.
