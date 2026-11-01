# Live narrow layout follow-up

Continue authorized feature/settings-compact from c93f0e0; no new branch or push. Follow docs/git-workflow.md. Astra designs/reviews; Sol implements and tests. No release/tag or BLE/data changes.

Actual v45 360dp/font1.3 showed clipped active readouts; portrait/font1.3 showed overlapping time labels. Measure live tile bodies at final column width before assigning their exact outer height, retain atomic number/unit, and compare actual time tick glyphs including the end unit. Preserve font sizes, four axes, 48dp controls, data and stop semantics. Omit only the optional middle time tick when spacing is insufficient.

Validate mobile unit/lint/build, existing isolated narrow Android probe with complete ancestor bounds and time-label gaps, then actual portrait/landscape/constrained display normal and active Mock/stop/reference. Capture fresh PNG and unique successful XML dumps only; sequential ADB outputs are not same-frame. Restore all display settings and audit original data versus legitimate added Mock shots. Freeze source and build both Debug and Mock version46 with original signature, backup before -r installs; never launch Debug or command real hardware. Roll forward with a corrected version; never uninstall/clear/downgrade.
