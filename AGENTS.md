# Agent Keyboard

This is a private, independently developed fork of the HeliBoard commit pinned
in `upstream.lock`. Read `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and
`docs/VALIDATION.md` before continuing implementation.

- One writer at a time; review agents only read unless the user changes this rule.
- Implement one bounded stage, validate it, and commit it before the next stage.
- Preserve the existing keyboard engine; keep edits to InputLogic,
  RichInputConnection and touch tracking narrowly scoped and independently reviewed.
- Keep ordinary typing offline. Never log editor contents, results or credentials.
- Learning and clipboard retention are opt-in. New features respect sensitive
  editor flags, incognito and device lock state.
- Do not add fake AI results. Local utilities are explicitly labelled local.
- Do not make upstream issues, pull requests or comments on the user's behalf.
  Upstream contribution/AI-use documents concern that project's contributions;
  the user's authorization covers this private fork.
- Run relevant `python3 tools/dev.py` checks. Record infrastructure blockers and
  inherited failures honestly; missing device tests are not passes.
- Keep generated APKs, local toolchains and secrets outside version control.
