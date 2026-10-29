# Android user-experience scans — 2026-10-30

## Maximum brightness

A natural normal-chat request to maximize brightness reached its persisted ACP terminal. The first GUI run failed after 20 actions / 104.384 seconds, including repeated slider gestures and navigation; a second GUI run succeeded in one action / 10.758 seconds. A retained screenshot showed the slider at its right end, and an independent system setting read was 255. The Agent disabled automatic brightness, which had been enabled before the request. This is a successful observed setting outcome with host-assisted restoration, not a clean one-run GUI success or an upstream benchmark pass.

The host restored the recorded automatic mode, system brightness setting and auto-adjustment. The mode remained enabled; after an intervening read-only GUI query the setting and adjustment had changed again, with cause unestablished. They were restored a second time and matched five seconds later. A later adjustment differed by roughly 0.00025 while automatic mode remained enabled; automatic brightness changes over time, so persistent exact real-time luminance is not established.

A device-side read-only probe found `cmd display get-brightness` requires display ID `0` on this device, while the source's privileged brightness reader omits it. This is a compatibility concern, not a verified failure through the product: a targeted normal-chat query established `android_privileged_action` was not available in this session. No permissions or configuration were changed to enable it, and no native product fix is claimed.

## Synthetic training note

A natural normal-chat request asked to record a fictional two-exercise training plan, with no completion claim. Its persisted ACP turn ended; the GUI component RunLog `gui-6db5a96a-124f-4f89-bcb4-e6c316bb0c8a` succeeded. After reopening the unique note, independent body-region OCR matched both exercises, sets, repetitions and duration. The host deleted the exact note and verified search absence.

The Agent also invoked `memory_write_daily` for this marked fictional test record, although the request only asked for a note. This is an observed extra write, not a demonstrated user preference. The host used Memory Center's single-entry selection and confirmation, which showed the exact marker, to delete only that short-memory entry; it then observed the marker absent and the other three visible cards unchanged. The app's existing daily-memory deletion path also removes derived search chunks for the affected day. Off-screen memory state and external synchronization were not independently audited. The source HE08 scenario involves more than this isolated plan-to-note adaptation and remains unverified.

The default Agent prompt encourages recording process details in daily memory, but one run does not establish why the model chose this write or a safe general policy change. No product code was changed on that inference. Raw notes, memory content, conversations, and captures remain private and ignored.
