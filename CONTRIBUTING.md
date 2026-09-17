# Contributing to Anima

We welcome contributions to Anima!

## Development Philosophy: Ponytail Minimalist Engineering

1. **Standard Library First:** Always evaluate whether standard library tools (`xml.etree.ElementTree`, `sqlite3`, `subprocess`, `urllib`) can solve the problem before adding third-party dependencies.
2. **Deterministic Replay Over Probabilistic Reasoning:** If a task can be cached or replayed with 0 LLM calls, compile it into SQLite.
3. **Hermetic Testability:** Every new feature, locator rule, or device adapter must include a unit test runnable via `python3 -m unittest`.
4. **Zero Shell Injections:** Never use `shell=True` or raw shell string concatenation for device/system interaction.

## Running Tests

```zsh
make test
# or
python3 -m unittest test_anima.py
```
