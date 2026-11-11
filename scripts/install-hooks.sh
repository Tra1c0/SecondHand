#!/usr/bin/env bash
# Installs a git pre-commit hook that runs the docs check (scripts/docs_sync.py --check --staged).
# On the base branch (main) a failing hard rule blocks the commit; on other branches (agents'
# tracks) the problems are only printed. Skip once with: git commit --no-verify.
# Usage: bash scripts/install-hooks.sh        (again to update; --remove to uninstall)
set -euo pipefail
cd "$(dirname "$0")/.."
hooks=$(git rev-parse --git-path hooks)
hook="$hooks/pre-commit"
marker="# docs_sync pre-commit hook"
if [ "${1:-}" = "--remove" ]; then
  if [ -f "$hook" ] && grep -q "$marker" "$hook"; then rm -f "$hook"; echo "removed $hook"; fi
  exit 0
fi
if [ -f "$hook" ] && ! grep -q "$marker" "$hook"; then
  echo "$hook exists and is not ours; add this line to it instead:"
  echo '  python3 -I scripts/docs_sync.py --check --staged --no-commands'
  exit 1
fi
mkdir -p "$hooks"
cat > "$hook" <<'HOOK'
#!/usr/bin/env bash
# docs_sync pre-commit hook (installed by scripts/install-hooks.sh)
root=$(git rev-parse --show-toplevel) || exit 0
[ -f "$root/scripts/docs_sync.py" ] || exit 0
command -v python3 >/dev/null 2>&1 || exit 0
base=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1])).get("base_branch","main"))' \
  "$root/scripts/docs_sync.json" 2>/dev/null || echo main)
branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if python3 -I "$root/scripts/docs_sync.py" --check --staged; then exit 0; fi
if [ "$branch" = "$base" ]; then
  echo "pre-commit: docs check failed on $base (fix it, or skip once with git commit --no-verify)"
  exit 1
fi
echo "pre-commit: docs check failed (not blocking on branch $branch; the lead fixes the docs at merge)"
exit 0
HOOK
chmod +x "$hook"
echo "installed $hook"
