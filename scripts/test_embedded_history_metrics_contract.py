"""Exercise the actual host gate for native mixed-metric layout evidence."""
import ast
import copy
from pathlib import Path
import unittest

SOURCE=Path(__file__).with_name('check_extraction_history_visual_migration.sh')


def evidence(width=1299, insufficient=True):
    def candidate(mask,gap):
        widths=[pair[1 if mask & (1<<index) else 0] for index,pair in enumerate(((226,412),(283,553),(252,441)))]
        heights=[pair[1 if mask & (1<<index) else 0] for index,pair in enumerate(((214,135),(214,135),(156,77)))]
        total=sum(widths)+gap*2
        return dict(inlineMask=mask,gapPx=gap,naturalWidthsPx=widths,naturalHeightsPx=heights,
                    measuredWidthsPx=widths[:],measuredHeightsPx=heights[:],resolvedInlineMask=mask,
                    measuredHeightPx=max(heights),totalNaturalWidthPx=total,completeSingleRow=total<=width)
    original=candidate(0,54)
    candidates=[] if not insufficient else [candidate(mask,54) for mask in range(8)]
    fits=[c for c in candidates if c['completeSingleRow'] and c['measuredHeightPx']<214]
    if insufficient and not fits:
        candidates += [candidate(mask,36) for mask in range(8)]
        fits=[c for c in candidates if c['completeSingleRow'] and c['measuredHeightPx']<214]
    selected=min(fits,key=lambda c:(c['measuredHeightPx'],bin(c['inlineMask']).count('1'),c['inlineMask'])) if fits else original
    reason=('budget_sufficient' if not insufficient else 'no_complete_shorter_candidate' if not fits else
            'original_spacing_shorter' if selected['gapPx']==54 else 'compact_spacing_shorter')
    arrangement=dict(schema='EMBEDDED_HISTORY_MEASURED_METRICS_V1',availableWidthPx=width,
                     defaultGapPx=54,compactGapPx=36,layoutDirection=0,budgetInsufficient=insufficient,cellIdentities=[101,102,103],
                     original=original,candidates=candidates,selected=copy.deepcopy(selected),selectionReason=reason)
    return dict(metricsArrangement=arrangement,verticalMinimumContentPx=1416 if insufficient else 800,
                contentViewportPx=873,metricsResolvedHeightPx=selected['measuredHeightPx'],
                metricsInline=selected['resolvedInlineMask']==7,metricsInlineNaturalWidthPx=1514)


class EmbeddedHistoryMetricsContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        code=SOURCE.read_text().split("<<'VERIFY_PY'\n",1)[1].split('\nVERIFY_PY',1)[0]
        functions=[n for n in ast.parse(code).body if isinstance(n,ast.FunctionDef) and n.name in ('require_embedded_metrics_budget','require_text_layout')]
        namespace={}
        exec(compile(ast.Module(body=functions,type_ignores=[]),str(SOURCE),'exec'),namespace)
        cls.actual_validate=staticmethod(namespace['require_embedded_metrics_budget'])

    def nodes(self,budget):
        a=budget['metricsArrangement'];s=a['selected'];width=a['availableWidthPx']
        tags=['product/history/elapsed/cell','product/history/weight/cell','product/history/sampled-water/cell']
        nodes=[dict(semanticId='product/history/metrics',rectPx=[0,0,width,s['measuredHeightPx']],complete=True,textLineBoxes=[])]
        x=0;y=0;row=0
        for index,(w,h) in enumerate(zip(s['measuredWidthsPx'],s['measuredHeightsPx'])):
            if x and x+w>width:y+=row+18;x=0;row=0
            left=width-x-w if a['layoutDirection']==1 else x
            nodes.append(dict(semanticId=tags[index],rectPx=[left,y,left+w,y+h],complete=True,textLineBoxes=[]))
            x+=w+s['gapPx'];row=max(row,h)
        return nodes

    def validate(self,budget,nodes=None):
        return self.actual_validate(budget,self.nodes(budget) if nodes is None else nodes)

    def test_retained_native_budget_selects_mixed_16dp_only_after_24dp_has_no_gain(self):
        budget=evidence();self.validate(budget)
        selected=budget['metricsArrangement']['selected']
        self.assertEqual((selected['inlineMask'],selected['totalNaturalWidthPx'],selected['measuredHeightPx']),(3,1289,156))

    def test_normal_budget_and_no_full_shorter_candidate_keep_original(self):
        self.validate(evidence(insufficient=False))
        self.validate(evidence(width=1288))
        self.assertEqual(evidence(width=1288)['metricsArrangement']['selectionReason'],'no_complete_shorter_candidate')

    def test_original_spacing_is_kept_when_its_measured_width_fits(self):
        budget=evidence(width=1325);self.validate(budget)
        self.assertEqual(budget['metricsArrangement']['selected']['gapPx'],54)
        self.assertEqual(len(budget['metricsArrangement']['candidates']),8)

    def test_shrunk_or_clipped_measurements_cannot_claim_complete(self):
        for key,value in [('measuredWidthsPx',[400,540,240]),('measuredHeightsPx',[120,120,150]),
                          ('resolvedInlineMask',1),('measuredHeightPx',155),('completeSingleRow',False)]:
            budget=evidence();budget['metricsArrangement']['selected'][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):self.validate(budget)

    def test_native_dimensions_must_stay_the_same_for_each_cell_mode(self):
        budget=evidence();candidate=budget['metricsArrangement']['candidates'][1]
        candidate['naturalWidthsPx'][0]-=1;candidate['measuredWidthsPx'][0]-=1;candidate['totalNaturalWidthPx']-=1
        with self.assertRaises(AssertionError):self.validate(budget)

    def test_wrong_selected_mode_gap_height_reason_or_legacy_flag_is_rejected(self):
        for field,value in [('selectionReason','original_spacing_shorter'),('cellIdentities',[1,1,2])]:
            budget=evidence();budget['metricsArrangement'][field]=value
            with self.subTest(field=field),self.assertRaises(AssertionError):self.validate(budget)
        for field,value in [('metricsResolvedHeightPx',155),('metricsInline',True),('metricsInlineNaturalWidthPx',1400)]:
            budget=evidence();budget[field]=value
            with self.subTest(field=field),self.assertRaises(AssertionError):self.validate(budget)

    def test_chosen_budget_must_match_the_actual_view_rectangles_and_gap(self):
        for node_index,coordinate in ((0,2),(0,3),(1,2),(2,3),(3,0)):
            budget=evidence();nodes=self.nodes(budget);nodes[node_index]['rectPx'][coordinate]+=1
            with self.subTest(node=node_index,coordinate=coordinate),self.assertRaises(AssertionError):self.validate(budget,nodes)
        budget=evidence();nodes=self.nodes(budget);nodes.append(copy.deepcopy(nodes[1]))
        with self.assertRaises(AssertionError):self.validate(budget,nodes)

    def test_rtl_and_narrow_wrapping_keep_original_node_order(self):
        budget=evidence();budget['metricsArrangement']['layoutDirection']=1
        self.validate(budget)
        narrow=evidence(width=400)
        for item in [narrow['metricsArrangement']['original'],narrow['metricsArrangement']['selected']]:item['measuredHeightPx']=620
        narrow['metricsResolvedHeightPx']=620
        self.validate(narrow)

    def test_missing_reordered_or_unnecessary_compact_candidates_are_rejected(self):
        for change in (lambda c:c.pop(),lambda c:c.reverse()):
            budget=evidence();change(budget['metricsArrangement']['candidates'])
            with self.assertRaises(AssertionError):self.validate(budget)
        budget=evidence(width=1325)
        budget['metricsArrangement']['candidates']+=evidence()['metricsArrangement']['candidates'][8:]
        with self.assertRaises(AssertionError):self.validate(budget)


if __name__=='__main__':unittest.main()
