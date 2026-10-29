---
name: omnibot-vivo-notes
description: Create, reopen, visually verify, and selectively clean up an isolated note in vivo Notes through OmniBot's GUI workflow.
---

Use for an authorized simple note in vivo Notes. Read the current GUI tool schema and submit the requested title and multiline body in one bounded task. The tested LI04 subcase creates a uniquely titled note, saves it, searches for its exact title, and reopens it. This is not validation of meal planning or every LI04 requirement.

Confirm the intended app and edit fields from a fresh observation. Preserve newlines when entering the body. Save before returning to the list, then search the exact title and reopen the unique matching result. AI search suggestions or loosely related notes are not proof of an exact match. If the result is ambiguous, do not open or delete a positional match.

The note body may be absent from accessibility text even when it is visible. After the task is terminal, inspect a fresh screenshot. The regression uses the observed `com.android.notes:id/line_edit_text` bounds and local OCR to verify the exact body lines in order, separately from the title; it also checks that the page and field bounds stayed stable around capture. Missing visual evidence means unverified, not empty content.

For an isolated regression, first verify the exact unique test title on the detail page, open More, delete, confirm deletion, and search again to verify absence. If a click reports an uncertain result during a page transition, read the resulting state before deciding what remains; never repeat deletion blindly. Do not clear the recycle bin or remove unrelated notes. For ordinary user notes, leave them saved unless deletion was requested.

See `../omnibot-android-regression/SKILL.md` for the executable case and separate execution, acceptance, and cleanup results. This skill is repository guidance and is not automatically loaded by the phone app.
