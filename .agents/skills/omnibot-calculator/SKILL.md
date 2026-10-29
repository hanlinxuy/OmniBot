---
name: omnibot-calculator
description: Perform and verify simple calculations in the vivo calculator through OmniBot's existing GUI tool using the validated BA19 path.
---

Use when the user specifically wants the phone calculator operated. Read the current GUI tool schema and foreground state, then submit one bounded calculation goal through the existing GUI tool. The repository regression `ba19-calculator` validates the GUI component path for `12 × 34 = 408`; this does not prove all expressions or the normal-chat routing layer.

Locate calculator controls from a fresh accessibility tree. Use the actual button text, such as `1`, rather than invented text such as `1 button`. Clear once when starting a new calculation; enter the complete expression and evaluate. Do not repeatedly clear because the result is absent from accessibility text: this vivo calculator does not reliably expose its display value there.

Read the result from a fresh screenshot after the task is terminal. Restrict OCR to the calculator display, excluding the numeric keypad and status bar; require the whole expected result, not a substring. Report inability to verify if visual evidence is missing. A model completion message alone is insufficient.

For isolated regression only, clear the test expression after verification and independently check the display is blank. Do not erase calculator history or memory registers. For ordinary user calculations leave the verified result visible unless cleanup was requested. See `../omnibot-android-regression/SKILL.md` for the executable test and evidence rules. This repository file is not automatically installed in the phone app.
