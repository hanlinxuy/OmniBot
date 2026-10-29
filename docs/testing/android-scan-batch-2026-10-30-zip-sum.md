# Isolated ZIP multi-file line count — 2026-10-30

A natural normal-chat request asked for the total number of lines in all text files in one explicitly identified test ZIP. The host-created ZIP had two random-text entries of three and five newline-terminated lines; its device SHA-256 matched the local fixture. The request supplied neither the answer nor an implementation command, and restricted the Agent to reading the test ZIP without network or on-disk extraction.

The persisted ACP turn ended with `end_turn`; a successful `terminal_execute` opened that exact ZIP, iterated its entries, printed per-file newline counts of 3 and 5 and a total of 8, and the final answer was exactly `8`. The host independently checked the fixture definition and post-run hash, verified the unique test directory contained only the ZIP, removed it and the empty directory, and verified absence. There was no `memory_write_daily` tool call in this turn.

An earlier attempt was blocked by an unknown access state before the chat was submitted; its separate fixture was hash-checked and removed. Both are retained privately. This is one successful host-fixture-assisted adaptation, not a repeatable runner case, a reliability result, or the original `SumFileLinesTask`: the upstream earliest July ZIP selection and benchmark snapshot were not exercised. No Android product fix is claimed.
