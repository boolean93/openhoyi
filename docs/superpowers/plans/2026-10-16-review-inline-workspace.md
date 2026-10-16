# Review inline workspace

Approved brew-review-v3 concept, spec §7/8/9, F5. Continue feature/task-first-ui/develop draft PR#4, recorded develop66ebda9/ed01de0 base, docs/git-workflow.md. No merge/release/device install/schema/control/inventory writes. Additive revert only.

Implement stable parameter and notes regions: bean/dose/free-text grind left, expectation/taste/next three multiline fields right at actual>=840 and min widths; compact stacks bounded parameters above notes. Keep same field instances when resizing, fixed save/details outside, native inline input rather than forced field dialogs. Local region scrolls handle long text. Each note retains16000 character filter and raw draft; text viewport adapts to actual region height without shrinking text. Keep original bean identity even if absent from current inventory; original note saving/trim/dose validation and no inventory mutation unchanged.

New TaskReviewSections owns stable two scroll regions and jump selector, focus/caret/scroll restoration; no business state. BrewReview uses it and restores existing raw draft/selection. Error jump returns to dose group.

Adapt ChineseFormWindowChecks to actual inline fields and keyboard; preserve long-text top/end scrolling,16000 limit, native accessibility editing/selection, raw text and fixed actions; modal expectations only apply to non-inline controls. Existing17 business paths still verify old bean identity, saved notes/recreation and no extra inventory events. Build then native all-window/font/IME screenshots; no local emulator means native red evidence pending. Independent review pending.
