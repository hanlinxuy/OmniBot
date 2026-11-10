# Mika and mobile benchmark follow-up (2026-11-10)

## Source pins and evaluation scope

- MobileWorld: `Tongyi-MAI/MobileWorld@aa2ea47052dd15729bfca1f27fa6aca4630607a9`, 201 task definitions.
- iOSWorld: `ljang0/iOSWorld@c59287b6ec9a9d0d7269613ad873f6aa9db0f42c`, 133 tasks built around 26 seeded iOS applications.
- Mika: `wangzan101/Mika@238533f5da6f2424bec52972b7b9248c4f3a86d0`.

These are inventory counts, not completed tasks. MobileWorld's `docs/real-devices.md` supports real Android task execution; its prepared app state and evaluators do not automatically transfer to a personal vivo phone. iOSWorld requires its simulator/application fixtures; native Android equivalents are adaptations, not official iOSWorld scores.

The current phone Pi comparison uses the same 37-tool catalog, isolated ACP sessions, task fixtures and answer/readback checks for three configured models. System APIs and files are available first; GUI remains available when a supported system route cannot complete the task. Calls to shared `run_gui` use OmniFlow's configured inner model and must not be attributed to the outer model's own visual ability.

## What Mika actually does

The reviewed implementation does not establish a universal anti-risk bypass. `WebBarrier.kt` detects CAPTCHA frames, short access-blocked pages, relevant HTTP status plus text, and API error messages. These become `NEEDS_HUMAN`; unsupported embedded browsers instead suggest a supported installed-app route or an incomplete outcome. `PageDigest.kt` treats CAPTCHA/access-blocked content as refused input.

`ChannelAdvisor.kt` summarizes installed apps, known signed-in sites, routes and recent challenges for channel selection. This is useful capability selection, not proof that a blocked account can be made usable.

`PagePacing.kt` adds bounded per-site pacing, counting model thinking/reading time toward dwell time. The default second page is 4–7 seconds; later pages add 600–1,000 ms up to 12 seconds, with a 20-page cap between user answers. The careful profile is slower. It also exempts a site from that task's page cap after an outward action; that policy should not be copied into OmniBot without a concrete need.

`SettlePolicy.kt` combines bounded DOM quiet with relevant in-flight network work. Its content check compares length/hash over consecutive samples and reports failure when a sample is missing or the deadline expires. This is a stronger readiness contract than a fixed sleep, but it is not independent proof of task success.

The prior OmniBot work already added local wheel selection, bounded UI action sequences and condition waits. This pass will extend those owners only when a new failing task exposes a specific limitation. It will not add a second Agent lifecycle or another retry loop.

## Concrete next implementation targets

1. Mobile document reading: avoid expensive native parser builds in Android Alpine; a discovered PDF skill provides a pure Python parser and bounded page/search extraction. Validate both skill discovery and actual model use. Merely publishing a skill is not a behavioral fix.
2. Device-local date/time: expose an offset-bearing current time, local date and IANA zone through the existing environment tool, so relative-time tasks need not infer the phone zone from a UTC terminal.
3. Task fixtures: preserve original source bytes/questions when possible, replace destructive global resets with uniquely scoped data, independently inspect results and then delete only owned fixtures.
4. Broader apps: locate the official test APK and its dependencies before attempting seeded mail workflows. Do not substitute a real personal account or count dependency discovery as a completed task.

No benchmark answer or benchmark-specific page number belongs in a production skill.
