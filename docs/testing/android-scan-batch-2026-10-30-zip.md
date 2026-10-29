# Isolated ZIP line-count adaptation — 2026-10-30

A host-created, uniquely named test directory under Downloads contained one ZIP with a known four-line `file_1.txt`. The ZIP hash was verified on device before submission. One normal-chat ACP turn reached its persisted `end_turn`; its tool result records a successful `terminal_execute` reading the specified ZIP entry, and its final answer was exactly `4`. The answer independently matches the fixture contents.

After the terminal, the host confirmed the owned directory contained only the fixture and its SHA-256 was unchanged, deleted that ZIP, removed the now-empty directory and verified the directory absent. Fixture setup and cleanup were host-assisted; no broader Downloads listing or deletion was performed. Raw conversation and paths are private and ignored.

This is an isolated adaptation of `CountFileLinesTask`, not a pass of its original benchmark: it supplies an explicit test ZIP rather than identifying the earliest July ZIP, and does not provide the original benchmark fixture or evaluator. It is one observed run, not a reliability result, and is not yet a repeatable three-gate runner case.

## Selection exploration and unified runner

A second isolated directory contained two July-dated ZIPs with different line counts. The normal-chat turn inspected the test directory's timestamps, read the earlier named ZIP and answered `4`; the host verified both fixture hashes and removed only those ZIPs and their empty directory. This is an isolated selection adaptation, not a test of the full Downloads directory or original benchmark snapshot.

The unified `count-file-lines-zip` case creates a new six-line fixture, all six lines terminated by `\n`. Its answer is defined as the number of newline bytes (`wc -l`); an unterminated final fragment would not count, and is not part of this fixture. Acceptance requires the owned persisted ACP `end_turn`, exactly one successful `terminal_execute` with the specified ZIP-entry read/count command and stdout `6`, and final text exactly `6`. Host setup and cleanup require the unique directory, ZIP hash and exact directory contents; cleanup removes only the owned ZIP and empty directory.

Attempt `REGd4d89956ea77` failed acceptance despite answering `6`: the required ZIP-entry read was not proven. It attempted multiple file/browser routes, including a navigation to `example.com`; cleanup passed. This failure is retained. The follow-up `REGf6307550da4b` explicitly instructed the read-only command and prohibited network/other paths; execution, independent acceptance and host cleanup passed. The prescribed command makes this a narrower tool-use regression, not proof the Agent independently chose the extraction strategy. The six-line fixture differs from the earlier four-line fixture; neither establishes multi-run reliability.
