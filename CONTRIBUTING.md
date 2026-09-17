# Contributing to Anima

We welcome contributions to Anima!

## Start here

| Doc | What it's for |
| :--- | :--- |
| [dev_plan.md](dev_plan.md) | Architectural SSOT — the design, the harvested SOTA, the decisions log |
| [PLAN.md](PLAN.md) | The current phase: why it exists, what done looks like |
| [CHECKLIST.md](CHECKLIST.md) | Task status, each item tied to the command that proves it |
| [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md) | Running log + environment state (SDK paths, JDK pin, known traps) |

If you are picking work up mid-stream — with or without a coding agent — read `CURRENT_PROGRESS.md` first; it names the next action and the traps.

## Development Philosophy: Ponytail Minimalist Engineering

1. **Standard Library First:** Always evaluate whether standard library tools (`xml.etree.ElementTree`, `sqlite3`, `subprocess`, `urllib`) can solve the problem before adding third-party dependencies. `anima.py` has zero pip dependencies and keeps them; the Android module uses Kotlin + AndroidX only.
2. **Deterministic Replay Over Probabilistic Reasoning:** If a task can be cached or replayed with 0 LLM calls, compile it into SQLite.
3. **Hermetic Testability:** Every new feature, locator rule, or device adapter needs a test that runs with no device, no emulator and no network.
4. **Zero Shell Injections:** Never use `shell=True` or raw shell string concatenation for device/system interaction.
5. **Claims Are Checks:** Don't mark something complete because the code looks right. Phases 5–6 were marked COMPLETED for an Android module that had never once been compiled (see `dev_plan.md` §13.1). If CI doesn't run it, it isn't done.
6. **The Two Runtimes Stay in Sync:** `anima.py` and `android/…/engine/` share a SQLite schema, locator weights and JSON format on purpose. Changing one means changing both, and a test enforces it.

## Running tests

```zsh
make test-all      # Python: unit + end-to-end, hermetic, ~2s
make android-test  # Kotlin engine: pure-JVM unit tests, no device needed
make apk           # Build the debug APK
```

Android work needs a one-time toolchain setup — see [android/README.md](android/README.md).

## Commits

Atomic and conventional: one logical unit per commit, `type(scope): description`. Never bundle multiple features into a single commit. Commit with `--no-gpg-sign` (see `dev_plan.md` §11.5).
