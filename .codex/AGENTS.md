# Project working agreements

## Scope and collaboration

- Load project preferences from this file, not the root `AGENTS.md`.
- Store Codex-specific notes, scripts, and artifacts under `.codex/`; temporary build and test artifacts may use ignored build directories or `/tmp`.
- Keep patches focused and reviewable. When working through findings, implement the requested finding without folding in unrelated cleanup.
- Preserve unrelated working-tree changes and machine-local configuration, including `settings.gradle`.
- Do not run `git add` or create commits. Leave changes for manual review; suggest a concise commit title when asked.
- Format commit title suggestions as lowercase Conventional Commit titles: `fix: tcp uid recovery in close_wait sessions`. Use an appropriate lowercase prefix such as `fix:`, `perf:`, `refactor:`, `test:`, or `docs:` and keep the description lowercase.
- Ask before adding production dependencies. Continue routine implementation and validation within the already authorized scope without repeatedly asking for confirmation.
- When asked to discuss or analyze without changes, keep that work read-only.

## Implementation

- Do not introduce `goto`.
- Preserve commented-out diagnostic logging and leave unused declarations unless their removal is requested.
- Preserve behavior unless a behavior change is requested or necessary to fix the agreed finding. Explain any intentional change.
- Consider JNI callers and native code together: ownership, reference lifetimes, exceptions, shutdown ordering, and retry behavior can cross the language boundary.
- Balance speed, battery consumption, and aggregate memory usage on mobile devices. Avoid unnecessary allocations, scans, formatting, polling, and wake-ups.
- Keep compatibility with supported older Android versions and ARMv7 as well as ARM64. Do not assume modern platform APIs, proc layouts, or permissions are available everywhere.

## Regression, functional, and memory tests

- For behavioral fixes, add or extend a regression that exercises the actual failure and asserts the intended result. Demonstrate failure before the fix when practical.
- Use standard Android JUnit instrumentation tests with native C fixtures for native behavior. Do not introduce Python test runners or a Linux-specific native build.
- Group tests by the production C source file where practical; keep them runnable from Android Studio and Gradle.
- Check observable behavior, not just implementation details: policy decisions, packet output, forwarded bytes, session transitions, and recovery after failures.
- Use real local sockets, pipes, and descriptors where they improve functional coverage. Use controlled callbacks and fault injection for deterministic errors, timing, policy, and routing.
- Cover relevant IPv4/IPv6 paths, legacy proc and JNI lookups, unknown and root UIDs, stale records/events, boundary values, and sequence wraparound when affected.
- For memory or lifecycle changes, verify allocation, socket, file, and JNI-reference balance across success, failure, retry, and cleanup. Exercise allocation failure and partial initialization where relevant.
- For concurrency changes, check overlapping start/stop/reload, cleanup completion, cancellation, stale callbacks, and resource ownership where applicable.
- Run ASan/UBSan on supported Android devices for native memory changes. Do not use LeakSanitizer or claim sanitizer success proves there are no leaks; retain explicit resource-balance checks.
- Do not add tests for documentation-only or trivial reversible edits unless there is behavior to verify.

## Device validation

- Check the available SDK, NDK, JDK, connected device, Android version, and ABI before choosing build and test commands. Do not assume a previously connected phone is still selected.
- Prefer the dedicated test APK. Distinguish instrumentation tests, real app/VPN lifecycle checks, and live external-network tests.
- Start with affected tests; run the full relevant suite after meaningful native behavior changes. Repeat checks when a change, failure, or unresolved concern warrants it.
- Use `tordnscrypt/tests/native/README.md` for current commands. The standalone suite is `:native-tests:connectedDebugAndroidTest`; use `--configure-on-demand`, explicit device selection when needed, and supported sanitizer/baseline options.
- Verify old Android `/proc` behavior on an appropriate connected device when available. Synthetic proc fixtures do not establish real-device proc compatibility.
- Allow time for firewall approval during real network tests. Investigate network or device restrictions separately from code failures; report blocked checks accurately.
- Investigate the first native assertion failure before interpreting later failures, since interrupted native fixtures may leave the instrumentation process unsuitable for further tests.
- When observing logcat during user testing, continue until the user finishes and assess the complete lifecycle sequence, including exit and cleanup. Do not diagnose memory corruption from ordinary network errors alone.

## Documentation and review

- Update the relevant test README alongside behavior and regression changes. Describe new coverage, intentional limitations, and changed commands or test counts when maintained there.
- Keep documentation consistent with the final implementation, including fixes completed in preceding patches when coverage descriptions were missed.
- For a review request, inspect the current uncommitted diff and relevant callers/tests without editing it. Prioritize actionable correctness, race, resource, compatibility, and regression issues; avoid speculative findings and cosmetic churn.
- Report findings with file locations, a concrete trigger, impact, and a focused remedy. If none are found, say so plainly.
- Distinguish static review, successful compilation by ABI, device test results, and unrun real app/network checks. Never present an earlier test run as a new one.
- Summarize what changed and why, validation results, expected skips, and any material remaining limitations. State when tests were not rerun.
