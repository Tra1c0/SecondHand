#!/usr/bin/env bash
# SessionStart hook: prints the current state (docs/HANDOFF.md), the start line of every
# docs/ARCHITECTURE.md section and the docs check summary (at most 15 lines), so a new session
# starts informed without reading files. Fail-safe: whatever happens, it exits 0 and never blocks.
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0" 2>/dev/null)/../..}" 2>/dev/null || exit 0
[ -f scripts/docs_sync.py ] || exit 0
command -v python3 >/dev/null 2>&1 || { echo "(docs state skipped: no python3)"; exit 0; }
if command -v timeout >/dev/null 2>&1; then
  timeout 20 python3 -I scripts/docs_sync.py --brief 2>/dev/null | head -n 15
else
  python3 -I scripts/docs_sync.py --brief 2>/dev/null | head -n 15
fi
exit 0
