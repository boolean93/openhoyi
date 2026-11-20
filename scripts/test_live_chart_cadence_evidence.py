import copy
import unittest
from live_chart_cadence_evidence import evaluate


class CadenceReportTests(unittest.TestCase):
    def setUp(self):
        schedule = list(range(200, 2801, 200)) + [4600, 4800, 5000]
        inputs, draws = [], []
        for i, raw in enumerate(schedule):
            pressure = 60 + max(0, min(8, i - 5)) * 4
            inputs.append(dict(elapsedMs=raw, actualNewPoints=1, plannedElapsedRealtimeMs=10000 + raw,
                               offeredAtRealMs=10000 + raw, pressureTenthsBar=pressure))
            t = raw - 200
            times = [t - 140, t - 60, t] if i in range(2, 14) else [t]
            for edge in times:
                p = pressure - 2 if 6 <= i < 14 and edge < t else pressure
                paths = [dict(verticesPx=[[0, 50], [min(t, 2600) / 10, 50]], interpolation="monotone-hermite")]
                if t >= 4400:
                    paths.append(dict(verticesPx=[[440, 37], [t / 10, 37]], interpolation="linear"))
                draws.append(dict(elapsedNanos=len(draws) + 1, observedMs=t, targetPressureTenthsBar=pressure,
                                  revealMs=edge, timeEndMs=40000, boundsPx=[0, 0, 4000, 100],
                                  pressureAnchorX=edge / 10, pressureAnchorY=100 - p / 1.2,
                                  pressureCaption=f"{pressure / 10:.1f} bar", pressurePaths=paths, flowPaths=[]))
        self.fixture = dict(complete=True, nativeBle=False, synthetic=True, inputs=inputs, draws=draws)

    def test_valid(self):
        self.assertEqual("PASS", evaluate(self.fixture)["status"])

    def test_jitter_is_not_product_failure(self):
        self.fixture["inputs"][1]["offeredAtRealMs"] += 100
        self.assertEqual("INCONCLUSIVE", evaluate(self.fixture)["status"])

    def test_device_trace_distinguishes_snapping_and_gap_bridge(self):
        case = copy.deepcopy(self.fixture)
        case["draws"] = [row for row in case["draws"] if row["observedMs"] == row["revealMs"]]
        self.assertIn("insufficient real", str(evaluate(case)["failures"]))
        self.fixture["draws"][-1]["pressurePaths"][0]["verticesPx"].append([445, 40])
        self.assertIn("missing interval", str(evaluate(self.fixture)["failures"]))

    def test_caption_must_be_newest_measured_value(self):
        self.fixture["draws"][8]["pressureCaption"] = "99.0 bar"
        self.assertIn("annotation", str(evaluate(self.fixture)["failures"]))
