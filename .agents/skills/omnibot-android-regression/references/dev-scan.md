# Development scan: breadth first, evidence before promotion

Use this reference to continue scenario discovery and optimization in this repository. Read the current AGENTS.md, scenario inventory, regression manifest and latest result report. The source inventory is broader than the executable cases; six passing subcases do not establish full source-scenario coverage.

## Make the next pass useful quickly

Select a small batch of new low-impact cases from the inventory, inspect installed dependencies and permissions, then run the eligible ones serially. Preserve blocked cases with concrete reasons; do not spend the whole batch repeatedly trying one unavailable app. Add executable cases only when their target state can be independently verified and their changes isolated and cleaned. Fix reproducible defects, rerun affected cases, and publish a concise sanitized result batch to the user-authorized fork issue. Existing passes need reruns when their implementation or relevant boundary changes, not on every documentation edit.

The default plan makes no phone changes. Use the existing runner with repeated `--case` arguments for an ordered subset. Read `scripts/android-scenario-scan/README.md` for prerequisites. Keep runtime paths, device serials, current APK hashes and measured timings in the current handoff/result record rather than assuming they are permanent skill configuration.

## Proven integration lessons

- Only one process owns physical phone input. While an Agent task runs, poll its persisted RunLog or owning ACP projection; concurrent debug UI observation caused an ANR. Reuse one host backend across cases. Stop UIAutomator before Agent execution and verify it actually stopped; its stop method only controls processes owned by that instance. Restart it only after confirmed task completion.
- Preserve existing Conversation → ACP Session → Turn → Item ownership. Unique request markers locate test conversations; the persisted official PromptResponse projection establishes terminal state. A model's final prose or another run's newest file does not.
- An uncertain tap or setText may already have worked. Read the new state and verify the exact postcondition. Never replay the mutation to make an error disappear. Bound retries to read-only focus checks or fresh database snapshots; SQLite/WAL copying can race a writer.
- Reuse an already-correct app/tab state. Repeated launches and selected-tab clicks add cost and transient-window failures. Wait for input-blocking notifications to disappear before the first input; do not dismiss notifications or bypass the runtime access checks.
- Vivo Notes can show the same note in AI references and normal search results. Exclude the reference subtree and require one regular result. A search field containing a title is not a detail page: require `title_edit_text`. Its body can be absent from accessibility text; use fresh screenshot OCR within the observed `line_edit_text` bounds, checking order and title. Deleting a detail opened from search can return to search rather than the main list.
- Clock titles and bottom tabs share labels; identify the actual control. A target alarm row can be behind the delete toolbar: scroll it clear, re-resolve it, and verify exactly one selected item. Timer cleanup must confirm the owned timer stopped, restore the recorded picker, and compare visible presets. No native timer-cancel capability has been established.
- Calculator output needs display-region OCR, not numeric keypad text. Clear once; this calculator shows a blank display after clearing, not zero. Do not keep clearing because the accessibility tree lacks its result.
- Restore temporary device settings and independently read them back at session end. Keep raw screenshots, chat/DB snapshots, credentials and unrelated app contents out of Git and issue comments.

## Optimize measured costs, not assumed model slowness

The successful 2026-10-29 sample had timer total 146.03 s, including a 9-step/52.05 s GUI picker cleanup; notes total 148.73 s with a 12-step/53.31 s product GUI run. Host observations alone accumulated roughly 51–53 s per case. These are historical samples in the private `regression-20261029-final-6` report, not reliability or model-speed benchmarks; cumulative intervals may overlap.

First distinguish product execution, host preparation/acceptance and cleanup. Investigate a deterministic picker restore only if the actual widget exposes a verifiable capability; no alternative has yet been validated. Retain independent checks when removing redundant observations. Do not claim a faster user-facing Agent from a faster host test. Record actual before/after timings and failure outcomes without running unnecessary broad repeats.

Repository skills are guidance for readers, not proof that the Android app automatically loads them. Promote a successful path with its verified scope and limitations. Use `summarize.py` for public result fields and keep failed attempts rather than rewriting them after recovery.
