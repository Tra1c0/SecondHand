#!/usr/bin/env python3
"""docs_sync: keeps a repository's working-memory docs complete, current and cheap to read.

Run with an isolated interpreter (stdlib only, Python 3.8+):

  python3 -I scripts/docs_sync.py --check     hard rules fail (exit 1); warnings only print
  python3 -I scripts/docs_sync.py --write     regenerate the <!-- gen:NAME --> blocks and the
                                              configured copies, then run the check
  python3 -I scripts/docs_sync.py --brief     session start: current state, map index, check summary
  python3 -I scripts/docs_sync.py --toc FILE  the "## " sections of a markdown file with line ranges

Options:
  --config PATH   config file (default: docs_sync.json next to this script)
  --root PATH     repository root (default: the parent of this script's directory)
  --staged        pre-commit mode: the warnings look at the staged changes only
  --base REF      diff base for the warnings (default: merge base with the base branch, else HEAD~1)
  --no-commands   skip the configured external commands (e.g. when CI runs them as own steps)

Everything project-specific lives in the config file (JSON; keys starting with "//" are comments).
Keys, all optional:
  docs        globs of markdown files that may hold generated blocks
  aliases     short path forms used in the map, e.g. {"Y/": "src/ui/"}; longest prefix wins
  map         {"file", "sources": [globs], "exclude": [globs], "match_stem": bool,
               "max_glob_files": 30, "search_roots": [dirs], "extensions": [".kt", ...]}
              every source file must be named in the map file (outside generated blocks) by
              file name, by stem (match_stem) or by a backticked glob such as `YtMini*.kt`;
              a glob matching more than max_glob_files sources is a rule, not a mention
  lists       {name: {"file", "pattern", "item"}}: items taken from a source file by a regex
              (multiline); "item" formats the groups, e.g. "`{1}`"
  blocks      {name: {"type": "list", "list", "template", "sep"}
                    | {"type": "inventory", "sources", "exclude", "title", "strip_ext", "width"}
                    | {"type": "toc"}}
  counts      [{"file", "pattern", "list"}]: every number the regex captures must equal the
              list's length (e.g. "14 designs" in a feature list)
  size_warnings {"file": lines}: a warning (never an error) when a file grows past this
  commands    [{"name", "run": [argv], "hint"}]: external checks; a non-zero exit is an error
  copies      [{"from", "to"}]: files that must stay identical (--write copies them)
  warn_if_changed  [{"when": [globs], "unless": [globs], "file", "need": "nonempty"|"changed",
                     "message"}]: soft rules about what a change should touch
  base_branch the branch the warnings diff against (default "main")
  state       {"file", "heading"}: the block --brief prints
  brief       {"max_lines": 15, "toc_file": path}
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import textwrap

SKIP_DIRS = {".git", "build", ".gradle", "node_modules", ".idea", ".cxx", "__pycache__", ".venv"}
GEN_RE = re.compile(r"<!-- gen:([\w-]+) -->(.*?)<!-- /gen:\1 -->", re.S)
OPEN_RE = re.compile(r"<!-- gen:([\w-]+) -->")
CLOSE_RE = re.compile(r"<!-- /gen:([\w-]+) -->")
SCRIPT = "python3 -I scripts/docs_sync.py"


# ---------------------------------------------------------------------------------------------
# globs and files


def glob_regex(pat):
    """A path glob as a regex: ** spans directories, * and ? stay inside one, {a,b} picks one."""
    out, i = [], 0
    while i < len(pat):
        if pat.startswith("**/", i):
            out.append("(?:[^/]+/)*")
            i += 3
        elif pat.startswith("**", i):
            out.append(".*")
            i += 2
        elif pat[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pat[i] == "?":
            out.append("[^/]")
            i += 1
        elif pat[i] == "{" and "}" in pat[i:]:
            j = pat.index("}", i)
            out.append("(?:" + "|".join(glob_regex(a) for a in pat[i + 1:j].split(",")) + ")")
            i = j + 1
        else:
            out.append(re.escape(pat[i]))
            i += 1
    return "".join(out)


def matches(path, globs):
    return any(re.fullmatch(glob_regex(g), path) for g in globs)


def walk(root, top):
    """All files below root/top as root-relative paths with forward slashes."""
    base = os.path.join(root, top) if top else root
    if os.path.isfile(base):
        return [top]
    found = []
    for d, dirs, files in os.walk(base):
        dirs[:] = sorted(x for x in dirs if x not in SKIP_DIRS)
        for f in files:
            found.append(os.path.relpath(os.path.join(d, f), root).replace(os.sep, "/"))
    return found


def glob_files(root, globs, exclude=()):
    """Files matching any glob (walking only each glob's fixed leading directories)."""
    found = set()
    for g in globs:
        parts = g.split("/")
        fixed = []
        for p in parts[:-1]:
            if any(c in p for c in "*?{"):
                break
            fixed.append(p)
        rx = re.compile(glob_regex(g))
        for path in walk(root, "/".join(fixed)):
            if rx.fullmatch(path):
                found.add(path)
    return sorted(p for p in found if not matches(p, exclude))


def read(root, rel):
    try:
        with open(os.path.join(root, rel), encoding="utf-8") as fh:
            return fh.read()
    except OSError:
        return None


def write(root, rel, text):
    with open(os.path.join(root, rel), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def git(root, *args):
    try:
        res = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    return res.stdout if res.returncode == 0 else None


# ---------------------------------------------------------------------------------------------
# config helpers


def strip_comments(obj):
    if isinstance(obj, dict):
        return {k: strip_comments(v) for k, v in obj.items() if not k.startswith("//")}
    if isinstance(obj, list):
        return [strip_comments(v) for v in obj]
    return obj


def alias_pairs(cfg):
    return sorted(cfg.get("aliases", {}).items(), key=lambda kv: -len(kv[1]))


def short(cfg, path):
    """A path in the map's short form (longest alias target first)."""
    for name, target in alias_pairs(cfg):
        if path.startswith(target):
            return name + path[len(target):]
    return path


def expand(cfg, token):
    for name, target in sorted(cfg.get("aliases", {}).items(), key=lambda kv: -len(kv[0])):
        if token.startswith(name):
            return target + token[len(name):]
    return token


def strip_gen(text):
    return GEN_RE.sub("", text)


# ---------------------------------------------------------------------------------------------
# generated blocks


def list_items(root, cfg, name):
    spec = cfg.get("lists", {}).get(name)
    if not spec:
        raise ValueError("list '%s' is not configured" % name)
    text = read(root, spec["file"])
    if text is None:
        raise ValueError("list '%s': %s not found" % (name, spec["file"]))
    items = []
    try:
        for m in re.finditer(spec["pattern"], text, re.M):
            groups = m.groups() or (m.group(0),)
            items.append(spec.get("item", "{0}").format(*groups))
    except (re.error, KeyError, IndexError) as e:
        raise ValueError("list '%s': bad pattern or item format (%s)" % (name, e))
    if not items:
        raise ValueError("list '%s': pattern found nothing in %s" % (name, spec["file"]))
    return items


def sections(text):
    """[(title, first_line, last_line, summary)] for every '## ' heading outside code fences."""
    lines = text.split("\n")
    heads, fence = [], False
    for i, line in enumerate(lines):
        if line.startswith("```"):
            fence = not fence
        elif not fence and line.startswith("## "):
            heads.append(i)
    out = []
    for n, start in enumerate(heads):
        end = heads[n + 1] - 1 if n + 1 < len(heads) else len(lines) - 1
        while end > start and not lines[end].strip():
            end -= 1
        summary = ""
        for line in lines[start + 1:end + 1]:
            if line.strip():
                if line.startswith("> "):
                    summary = line[2:].strip()
                break
        out.append((lines[start][3:].strip(), start + 1, end + 1, summary))
    return out


def render(root, cfg, name, doc_text):
    spec = cfg.get("blocks", {}).get(name)
    if spec is None:
        raise ValueError("no generator configured for block '%s'" % name)
    kind = spec.get("type")
    if kind == "list":
        items = list_items(root, cfg, spec.get("list", name))
        tpl = spec.get("template", "{count}: {items}")
        return tpl.format(count=len(items), items=spec.get("sep", ", ").join(items))
    if kind == "inventory":
        files = glob_files(root, spec.get("sources", cfg.get("map", {}).get("sources", [])),
                           spec.get("exclude", cfg.get("map", {}).get("exclude", [])))
        groups = {}
        for f in files:
            d, base = f.rsplit("/", 1) if "/" in f else ("", f)
            if spec.get("strip_ext", True):
                base = os.path.splitext(base)[0]
            groups.setdefault(short(cfg, d + "/"), []).append(base)
        width = spec.get("width", 100)
        lines = ["<details><summary>%s (%d files; generated, do not edit)</summary>"
                 % (spec.get("title", "File inventory"), len(files)), ""]
        for d in sorted(groups):
            head = "- `%s` (%d): " % (d, len(groups[d]))
            lines.extend(textwrap.wrap(head + ", ".join(sorted(groups[d])), width=width,
                                       subsequent_indent="  ", break_on_hyphens=False))
        lines += ["", "</details>"]
        return "\n".join(lines)
    if kind == "toc":
        lines = []
        for title, _, _, summary in sections(strip_gen(doc_text)):
            if title == spec.get("skip"):
                continue
            lines.append("- **%s**%s" % (title, " – " + summary if summary else ""))
        return "\n".join(lines)
    raise ValueError("block '%s': unknown type '%s'" % (name, kind))


def doc_files(root, cfg):
    return glob_files(root, cfg.get("docs", ["*.md", "docs/*.md"]))


def regenerate(root, cfg, rel, text):
    """The text with every generated block re-rendered, plus problems found on the way."""
    problems = []
    opened = [m.group(1) for m in OPEN_RE.finditer(text)]
    closed = [m.group(1) for m in CLOSE_RE.finditer(text)]
    if sorted(opened) != sorted(closed) or len(GEN_RE.findall(text)) != len(opened):
        problems.append("%s: unbalanced gen markers (each <!-- gen:X --> needs <!-- /gen:X -->)" % rel)
        return text, problems

    def sub(m):
        try:
            body = render(root, cfg, m.group(1), text)
        except (ValueError, KeyError, IndexError, re.error) as e:  # a config mistake, said plainly
            problems.append("%s: %s" % (rel, e))
            return m.group(0)
        return "<!-- gen:%s -->\n%s\n<!-- /gen:%s -->" % (m.group(1), body, m.group(1))

    return GEN_RE.sub(sub, text), problems


# ---------------------------------------------------------------------------------------------
# diff for the soft rules


def changed_files(root, cfg, staged, base):
    if staged:
        out = git(root, "diff", "--cached", "--name-only")
        return (set(out.split()) if out is not None else None), "staged changes"
    label = None
    if base is None:
        head = (git(root, "rev-parse", "HEAD") or "").strip()
        branch = cfg.get("base_branch", "main")
        for ref in (branch, "origin/" + branch):
            mb = (git(root, "merge-base", "HEAD", ref) or "").strip()
            if mb and mb != head:
                base, label = mb, "changes since " + branch
                break
        if base is None and git(root, "rev-parse", "--verify", "-q", "HEAD~1") is not None:
            base, label = "HEAD~1", "the last commit"
    out = git(root, "diff", "--name-only", base or "HEAD")
    if out is None:
        return None, None
    names = set(out.split())
    names |= set((git(root, "ls-files", "--others", "--exclude-standard") or "").split())
    return names, (label + " + uncommitted" if label else "uncommitted changes")


# ---------------------------------------------------------------------------------------------
# the check


class Report:
    def __init__(self):
        self.errors, self.warnings = [], []

    def error(self, head, details=(), fix=None):
        self.errors.append((head, list(details), fix))

    def warn(self, head, details=(), fix=None):
        self.warnings.append((head, list(details), fix))

    def print(self, out=sys.stdout, limit=8):
        gha = os.environ.get("GITHUB_ACTIONS") == "true"
        for kind, items in (("ERROR", self.errors), ("WARN", self.warnings)):
            for head, details, fix in items:
                tag = ("::error::" if kind == "ERROR" else "::warning::") if gha else kind + " "
                print(tag + head, file=out)
                for d in details[:limit]:
                    print("    " + d, file=out)
                if len(details) > limit:
                    print("    … and %d more" % (len(details) - limit), file=out)
                if fix:
                    print("    -> " + fix, file=out)
        print("docs_sync: %s, %d warning(s)" % (
            "OK" if not self.errors else "%d error(s)" % len(self.errors), len(self.warnings)),
            file=out)


def check_map(root, cfg, rep):
    spec = cfg.get("map")
    if not spec:
        return
    text = read(root, spec["file"])
    if text is None:
        rep.error("%s is missing" % spec["file"])
        return
    prose = strip_gen(text)
    words = set(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", prose))
    spans = re.findall(r"`([^`\n]+)`", prose)
    globs = [s.strip() for s in spans if any(c in s for c in "*{")]
    sources = glob_files(root, spec.get("sources", []), spec.get("exclude", []))
    # a glob names a small family of files; a broad one (a rule such as `*.kt`) names none
    broad = spec.get("max_glob_files", 30)
    rx = []  # (regex, matches the whole path?) per glob
    for g in globs:
        full = expand(cfg, g)
        r = re.compile(("(?:^|/)" if "/" in full else "^") + glob_regex(full) + "$")
        whole = "/" in full
        hits = sum(1 for p in sources if r.search(p if whole else p.rsplit("/", 1)[-1]))
        if hits <= broad:
            rx.append((r, whole))
    missing = []
    for path in sources:
        base = path.rsplit("/", 1)[-1]
        stem = os.path.splitext(base)[0]
        if base in prose or (spec.get("match_stem", True) and stem in words):
            continue
        if any(r.search(path if whole else base) for r, whole in rx):
            continue
        missing.append(short(cfg, path))
    if missing:
        rep.error("%s does not name %d source file(s)" % (spec["file"], len(missing)), missing,
                  "add each to the right area of %s (its name, or a backticked glob like `Name*.ext`); "
                  "then %s --write" % (spec["file"], SCRIPT))
    # names and globs in the map that point at nothing (renamed or deleted files)
    exts = tuple(spec.get("extensions", [".kt"]))
    index = set()
    for top in spec.get("search_roots", ["."]):
        index.update(walk(root, "" if top == "." else top))
    bases = {p.rsplit("/", 1)[-1] for p in index}
    stale = []
    for s in sorted(set(spans)):
        s = s.strip()
        if not s.endswith(exts) or " " in s:
            continue
        full = expand(cfg, s)
        if any(c in s for c in "*{"):
            r = re.compile(("(?:^|/)" if "/" in full else "^") + glob_regex(full) + "$")
            if not any(r.search(p if "/" in full else p.rsplit("/", 1)[-1]) for p in index):
                stale.append(s + "  (glob matches nothing)")
        elif "/" in full:
            if full not in index and not any(p.endswith("/" + full) for p in index):
                stale.append(s)
        elif full not in bases:
            stale.append(s)
    if stale:
        rep.warn("%s names %d file(s) that do not exist" % (spec["file"], len(stale)), stale,
                 "fix the name or drop the line (renamed or deleted file?)")


def check(root, cfg, args, rep, regen_problems=None):
    # generated blocks
    stale = []
    for rel in doc_files(root, cfg):
        text = read(root, rel) or ""
        if "<!-- gen:" not in text:
            continue
        new, problems = regenerate(root, cfg, rel, text)
        for p in problems:
            rep.error(p)
        if new != text:
            old_blocks = {m.group(1): m.group(0) for m in GEN_RE.finditer(text)}
            names = sorted(m.group(1) for m in GEN_RE.finditer(new)
                           if old_blocks.get(m.group(1)) != m.group(0))
            stale.append("%s [%s]" % (rel, ", ".join(names)))
    if stale:
        rep.error("generated blocks are out of date", stale, "run: %s --write" % SCRIPT)

    # copies that must not drift
    for c in cfg.get("copies", []):
        a, b = read(root, c["from"]), read(root, c["to"])
        if a is None or b is None:
            rep.error("copy %s -> %s: a file is missing" % (c["from"], c["to"]),
                      fix="run: %s --write" % SCRIPT)
        elif a != b:
            rep.error("%s differs from %s" % (c["to"], c["from"]), fix="run: %s --write" % SCRIPT)

    check_map(root, cfg, rep)

    # size notes (soft: content beats line counts; only a very large file is worth a look)
    for rel, limit in cfg.get("size_warnings", {}).items():
        text = read(root, rel)
        if text is None:
            rep.error("%s is missing" % rel)
            continue
        n = text.count("\n") + (0 if text.endswith("\n") or not text else 1)
        if n > limit:
            rep.warn("%s has %d lines (note at %d)" % (rel, n, limit),
                     fix="drop only what is no longer true or pure history (git keeps it)")

    # counts that must match a list
    for c in cfg.get("counts", []):
        try:
            want = len(list_items(root, cfg, c["list"]))
        except (ValueError, KeyError, IndexError, re.error) as e:
            rep.error(str(e))
            continue
        text = read(root, c["file"]) or ""
        found = [int(x) for x in re.findall(c["pattern"], strip_gen(text))]
        if not found:
            rep.error("%s: no match for /%s/ (expected the %s count %d)"
                      % (c["file"], c["pattern"], c["list"], want))
        bad = sorted(set(n for n in found if n != want))
        if bad:
            rep.error("%s says %s %s, the source has %d" % (
                c["file"], "/".join(map(str, bad)), c["list"], want),
                fix="update the number in %s" % c["file"])

    # external commands
    if not args.no_commands:
        for c in cfg.get("commands", []):
            try:
                res = subprocess.run(c["run"], cwd=root, capture_output=True, text=True, timeout=120)
                ok, out = res.returncode == 0, (res.stdout + res.stderr)
            except (OSError, subprocess.SubprocessError) as e:
                ok, out = False, str(e)
            if not ok:
                lines = [l.replace("::error::", "").strip() for l in out.splitlines()
                         if "error" in l.lower() or "missing" in l.lower()] or out.splitlines()[-5:]
                rep.error("%s failed" % c.get("name", " ".join(c["run"])), lines, c.get("hint"))

    # soft rules about what a change should touch
    rules = cfg.get("warn_if_changed", [])
    if rules:
        changed, label = changed_files(root, cfg, args.staged, args.base)
        if changed is not None:
            for r in rules:
                hits = sorted(p for p in changed
                              if matches(p, r.get("when", [])) and not matches(p, r.get("unless", [])))
                if not hits:
                    continue
                need = r.get("need", "changed")
                if need == "nonempty":
                    text = read(root, r["file"])
                    if text is not None and text.strip():
                        continue
                elif r["file"] in changed:
                    continue
                rep.warn("%s (%s)" % (r.get("message", "%s should change too" % r["file"]), label),
                         [short(cfg, h) for h in hits])


# ---------------------------------------------------------------------------------------------
# modes


def do_write(root, cfg):
    changed = []
    for rel in doc_files(root, cfg):
        text = read(root, rel) or ""
        if "<!-- gen:" not in text:
            continue
        new, problems = regenerate(root, cfg, rel, text)
        if new != text and not problems:
            write(root, rel, new)
            changed.append(rel)
    for c in cfg.get("copies", []):
        src, dst = os.path.join(root, c["from"]), os.path.join(root, c["to"])
        if os.path.isfile(src) and read(root, c["from"]) != read(root, c["to"]):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy(src, dst)  # keeps the mode (scripts stay executable)
            changed.append(c["to"])
    print("docs_sync --write: " + (", ".join(changed) if changed else "nothing to update"))


def do_toc(root, rel):
    text = read(root, rel)
    if text is None:
        print("no such file: " + rel)
        return 1
    for title, a, b, _ in sections(text):
        print("L%d-%d  %s" % (a, b, title))
    return 0


def do_brief(root, cfg, args):
    """At most brief.max_lines lines: state, map index, check summary. Never fails."""
    limit = cfg.get("brief", {}).get("max_lines", 15)
    rep = Report()
    try:
        check(root, cfg, args, rep)
        if rep.errors or rep.warnings:
            summary = ["docs check: %d error(s), %d warning(s); details: %s --check"
                       % (len(rep.errors), len(rep.warnings), SCRIPT)]
            for head, details, _ in (rep.errors + rep.warnings)[:3]:
                summary.append("  - " + head + (": " + ", ".join(details[:3]) if details else ""))
        else:
            summary = ["docs check: OK"]
    except Exception as e:  # the session must start whatever happens here
        summary = ["docs check could not run: %s" % e]
    index = []
    toc = cfg.get("brief", {}).get("toc_file") or cfg.get("map", {}).get("file")
    if toc and read(root, toc):
        skip = cfg.get("blocks", {}).get("toc", {}).get("skip")
        parts = ["%s %d" % (t, a) for t, a, _, _ in sections(read(root, toc)) if t != skip]
        index = ["Map %s, section start lines: %s" % (toc, "; ".join(parts))]
    state = []
    st = cfg.get("state")
    if st and read(root, st["file"]):
        for title, a, b, _ in sections(read(root, st["file"])):
            if title == st.get("heading", "Current state"):
                body = read(root, st["file"]).split("\n")[a:b]
                state = [l for l in body if l.strip() and not l.lstrip().startswith("<!--")]
                break
    room = max(0, limit - 1 - len(index) - len(summary))
    head = ["%s %s:" % (st["file"] if st else "state", st.get("heading", "") if st else "")]
    if len(state) > room:
        state = state[:max(0, room - 1)] + ["  … (rest in %s)" % st["file"]]
    print("\n".join((head + state if state else []) + index + summary))
    return 0


def main(argv=None):
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description="Check and regenerate the working-memory docs.")
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--brief", action="store_true")
    mode.add_argument("--toc", metavar="FILE")
    ap.add_argument("--config", default=os.path.join(here, "docs_sync.json"))
    ap.add_argument("--root", default=os.path.dirname(here))
    ap.add_argument("--staged", action="store_true")
    ap.add_argument("--base")
    ap.add_argument("--no-commands", action="store_true")
    args = ap.parse_args(argv)
    root = os.path.abspath(args.root)
    if args.toc:
        return do_toc(root, args.toc)
    try:
        with open(args.config, encoding="utf-8") as fh:
            cfg = strip_comments(json.load(fh))
    except (OSError, ValueError) as e:
        print("docs_sync: cannot read config %s: %s" % (args.config, e))
        return 0 if args.brief else 2
    if args.brief:
        try:
            return do_brief(root, cfg, args)
        except Exception as e:  # never block a session
            print("docs_sync --brief failed: %s" % e)
            return 0
    if args.write:
        do_write(root, cfg)
    rep = Report()
    check(root, cfg, args, rep)
    rep.print()
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
