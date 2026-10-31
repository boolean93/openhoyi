# CI recovery and coverage mapping

Continue feature/settings-compact (develop-derived; explicitly integrated task-first history), target PR #4 feature/task-first-ui -> develop. Append normal commits; no force, ruleset change, release tag or hardware action. Rollback by reverting the fix commit. Package/signing/upgrade identity unchanged.

| Check | Existing result / root | Local action | Coverage retained |
|---|---|---|---|
| branch-policy | success on ed66d29 | none | PR routing/body; push/tag release policy |
| verify | StringFormatMatches MachineSettingsControls | c377c25 explicit resource arity; local lint passed | all build/unit/lint/localization |
| lifecycle | idle probe completed; compact Home geometry failed | retain exact geometry assertion; propagate failure off main thread and log captured visibility plus ancestor clipping | full navigation, lifecycle, accessibility, compact geometry |
| upgrade | success on ed66d29 | none | upgrade/data/signature scenarios |
| settings visual | removed app scroll tag, removed machine launcher | target actual document; leave/return through primary navigation with audio stop/observer checks | text reachability, themes/languages/feedback/export and machine/scale pages |
| task-frame | removed app scroll tag | actual document scroll | scroll restoration, reachable fields, frame geometry |
| scale visual | connection frame complete comparisons exceeded 5s | diagnose only; not resolved | unchanged full pixel/window/display protocol |

Seven visual/task-frame workflows remove only the historical feature/task-first-ui push trigger. Their pull_request(develop), workflow_dispatch and entire jobs blocks are unchanged. Hash evidence: settings-evidence/ci-diagnosis/workflow-equivalence.json in task workspace. PR merge ref differs from push head: this does not claim same-SHA execution equivalence; standalone feature preflight is intentionally removed only for those seven, while complete integration candidate coverage remains on PR. Concurrency is workflow + event + PR/branch; cancellation only for superseded PR runs. Four required workflows and independent single-screen, advanced, font snapshot and capture workflows remain unchanged.

Evidence from ed66d29: lifecycle artifact 13031451768; scale artifact 13031556975. Scale failed attempt: geometry/window readiness/layer match/display stability all true; S1-W1, S1-W2, S1-S2, D1-D2 differences all zero. Capture stages ended at 1921ms; comparison complete 5730ms, withinMatchingBudget=false. No timeout or pixel tolerance relaxation. Compact Home original nonempty Rect is not proof getGlobalVisibleRect returned true; root cause requires fresh diagnostic execution. New compilation is evidence of source validity only, not runtime success. Do not merge until latest required and affected checks pass.
