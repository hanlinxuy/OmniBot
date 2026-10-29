# Isolated ZIP line-count adaptation — 2026-10-30

A host-created, uniquely named test directory under Downloads contained one ZIP with a known four-line `file_1.txt`. The ZIP hash was verified on device before submission. One normal-chat ACP turn reached its persisted `end_turn`; its tool result records a successful `terminal_execute` reading the specified ZIP entry, and its final answer was exactly `4`. The answer independently matches the fixture contents.

After the terminal, the host confirmed the owned directory contained only the fixture and its SHA-256 was unchanged, deleted that ZIP, removed the now-empty directory and verified the directory absent. Fixture setup and cleanup were host-assisted; no broader Downloads listing or deletion was performed. Raw conversation and paths are private and ignored.

This is an isolated adaptation of `CountFileLinesTask`, not a pass of its original benchmark: it supplies an explicit test ZIP rather than identifying the earliest July ZIP, and does not provide the original benchmark fixture or evaluator. It is one observed run, not a reliability result, and is not yet a repeatable three-gate runner case.
