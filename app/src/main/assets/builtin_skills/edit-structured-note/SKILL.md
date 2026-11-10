---
name: edit-structured-note
description: Update an existing note or document while preserving its content, placing edits in the intended section, and avoiding invented factual details. Use for adding bullets, status updates, or corrections to structured notes.
---

# Edit a structured note

Read the target's full current body and identify it by its observed title, folder and, where available, stable ID. A search preview is not the complete body.

Choose the section that matches the requested update. Appending to the end of a document can accidentally put a completed item under a final “Blockers” heading. Inspect the actual tool semantics: an append operation is appropriate only when the end is the intended location. Otherwise use a supported targeted edit, or read-modify-write the one note while preserving all unrelated text and metadata.

Use facts supplied by the user or present in the source. Do not invent an issue number, component, failure cause, merge status or date to make a brief update sound specific. When the request itself supplies enough information for a generic statement, use that limited statement. Ask for missing details only when they are necessary to complete the requested edit accurately.

Read back the persisted body after writing. Check both the new text and its section, plus preservation of the original content. A successful append receipt alone does not verify placement. If a verified edit landed under the wrong heading, correct that same authorized edit before reporting completion; do not ask for approval merely to finish the original request. If a write's result is uncertain, inspect the note before attempting another write to avoid duplicate bullets.
