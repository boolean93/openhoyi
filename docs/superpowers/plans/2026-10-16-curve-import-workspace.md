# Curve import workspace

Approved spec §7 CurveImport / §8 input recovery / §9 bounded input. Continue feature/task-first-ui from develop 66ebda9 with recorded ed01de0 dependency; target existing draft PR #4/develop, follow docs/git-workflow.md. WIP feature push may collect CI; independent review and full validation required before acceptance. No merge/release/install/hardware/schema changes. Revert through an additive UI-only commit without changing user records.

Current page puts all three actions below an oversized editor, and low-height tablet opens a separate editor. Only raw text is explicitly saved, losing native caret/scroll identity after recreation (generated field IDs cannot be relied on).

- Move explicit Paste/Open file actions into one source row above bounded raw text editor. Keep Preview as sole fixed primary action and existing inline errors. No clipboard reads outside explicit Paste; original SAF/UTF-8/size/decode callbacks unchanged.
- Mark the workspace as opt-in native inline editing; preserve bean-full-field for full-width multiline input, existing input types and limits. No changes to shared modal behavior or other pages.
- Save/restore raw text under original size guard plus selection, focus and local text scroll. Do not normalize or save invalid drafts into the curve store. File input returns continue original preview path; cancel leaves current field intact.
- Extend existing native business test before implementation: actual native field can focus inline; raw draft/caret survive recreation and store remains unchanged. Keep existing 17 path counts, three invalid documents, explicit preview/save, conflicts and idempotency assertions. Local compile then exact CI and real screenshots; missing native evidence stays pending.

Files: CurveImportActivity.kt, AdvancedBusinessUiChecks.kt, this plan/evidence. No subagent available before account reset; independent review pending, not claimed complete. Existing tests should fail new inline-focus assertion on old compact tablet behavior; native red run not available locally.
