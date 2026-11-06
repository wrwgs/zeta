#!/usr/bin/env python3
"""Store round trip: does a project saved with `squit` read back in a later session?

Every other check starts from a fresh session, so none of them ever reads a store.  This one
saves a project in one session and reopens it in a NEW session in the same directory, then
dumps the same artifacts as baseline.py and compares.

What a store holds (zeta.tools.sman, read from the source -- see doc/serialization.md):

  * `squit` -> SessionImpl.exit() -> flush(): every adaptor's flush(), then a ProjectData
    (adaptor names and classes, unit names, each unit's PersistentContents), all written
    under ./ZETA/.  `quit` -> abort() writes nothing.
  * Unit contents are NEVER persisted: UnitImpl.define forces `persistent = false` ("serious
    efficiency problems with serialization"), so every PersistentContents is empty and every
    content type is recomputed in the second session.  There is nothing the store could
    provide "instead of re-running the tools".
  * The one adaptor that persists anything is the LaTeX adaptor: search path, per source its
    file, mtime, diagnostics and LatexConfig (macro tables of Lexems), and per unit the
    scanned but not yet delivered Text.  That Text is full of Pizza constant cases (Lexem,
    Text.ZedMarkupKind), which is what this check exercises.
  * A reopened session runs SessionImpl.initialize() (re-registers the stored adaptors, then
    the units, then their contents) BEFORE the zetarc plugin lines, so every `plugin` line
    reports "already installed".  That is the 2002 shell's behaviour, not checked here.

Two modes, because of the LaTeX adaptor's delivered-once design:

  loaded  session 1: `load`, `squit`.  Session 2 (one per artifact, all reading the same,
          unchanged store -- the second session ends with `quit`, which saves nothing):
          `make -u U T -d ...`.  Compared against baselines-ported/.  This is the mode in
          which the persisted Text is actually parsed.
  made    per artifact, in its own directory: session 1 `load`, `make -u U T -d ...`,
          `squit`; session 2 the same `make`.  Compared against session 1 (and session 1
          against baselines-ported/).  Session 1 delivers U's text, which sets the adaptor's
          undeliveredText to null and persists THAT; since the text content itself was
          transient, session 2 cannot get it back unless the source changes ("compute called
          for up-to-date text").  So text-derived artifacts are expected NOT to come back in
          this mode -- a 1998 design limitation this script reports rather than hides.

    store_roundtrip.py [--mode loaded|made|both] [--only bbook,...] [--artifacts PATTERN]
                       [--classes CP] [--jobs N]

Needs a build that can write a store (Storage.create creates its directory), i.e. the port;
the 2002 jar fails on the first write.  So --classes defaults to the ported tree.
"""
import argparse
import concurrent.futures
import fnmatch
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)                     # works under `python3 -I` and from any directory
import baseline                                                       # noqa: E402
import canonicalize                                                   # noqa: E402

ZETA = os.path.dirname(HERE)
WORK = os.path.join(ZETA, "work/store-roundtrip")
PORTED_BASELINES = os.path.join(ZETA, "baselines-ported")
DEFAULT_CLASSES = "%s/classes" % ZETA


def run_session(session_dir, commands, classes, label):
    """Drive one session; return (transcript, problem-or-None).

    zdrive.py exits 0 when ZETA itself dies (it sees end-of-output, not a timeout), and a
    crash while reading a store is precisely what this check looks for, so the transcript is
    inspected too: a fatal-error report, or a command that never reported completion.
    """
    cmd = [sys.executable, "-I", os.path.join(HERE, "zdrive.py"), "--cwd", session_dir,
           "--classes", classes] + commands
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    out = p.stdout
    with open(os.path.join(session_dir, label + ".transcript"), "w") as f:
        f.write(out)
    lines = out.split("\n")
    if p.returncode != 0:
        bad = [ln for ln in lines if ln.startswith("### ")]
        return out, "driver exit %d%s" % (p.returncode, ": " + "; ".join(bad) if bad else "")
    for i, ln in enumerate(lines):
        if ln.startswith("An unexpected exception has occured"):
            detail = [x.strip() for x in lines[i + 1:i + 4] if x.strip()]
            return out, "ZETA died: " + " / ".join(detail)
    for c in commands:
        if c == "squit":
            if "= good bye." not in lines:
                return out, "squit did not complete"
        elif ("- " + c) not in lines:
            return out, "no completion for `%s'" % c
    return out, None


def copy_sources(src_dir, session):
    os.makedirs(session)
    for f in os.listdir(src_dir):
        if f.endswith((".tex", ".sty")):
            shutil.copy2(os.path.join(src_dir, f), session)


def read_dump(path, t):
    if not os.path.exists(path):
        return None
    text = open(path, encoding="latin-1").read()
    return canonicalize.canon_nmz(text) if t == "nmz" else canonicalize.canon_paths(text)


def ported_baseline(name, key):
    path = os.path.join(PORTED_BASELINES, name, key)
    return open(path, encoding="latin-1").read() if os.path.exists(path) else None


def artifacts(units, keep):
    for u in units:
        for t in baseline.TYPES:
            key = "%s.%s" % (baseline.safe(u), t)
            if not keep or keep(key):
                yield u, t, key


def run_loaded(name, src, load, units, args, keep, results):
    root = os.path.join(WORK, name, "loaded")
    shutil.rmtree(root, ignore_errors=True)
    copy_sources(src, root)
    _out, problem = run_session(root, ["load " + load, "squit"], args.classes, "save")
    if problem or not os.path.isdir(os.path.join(root, "ZETA")):
        results.append((name, "loaded", "*", "FAIL", "saving: %s" % (problem or "no ZETA/")))
        return

    def one(u, t, key):
        dump = baseline.dump_name(u, t)
        cmd = "make -u %s %s -d %s" % (u, t, dump)
        _o, problem = run_session(root, [cmd], args.classes, "reopen-" + key)
        got = read_dump(os.path.join(root, dump), t)
        want = ported_baseline(name, key)
        if got is None:
            return (name, "loaded", key, "FAIL", "not dumped" + (": " + problem if problem else ""))
        if problem:
            return (name, "loaded", key, "FAIL", problem)
        if want is None:
            return (name, "loaded", key, "dumped", "no ported baseline to compare")
        return (name, "loaded", key, "ok" if got == want else "FAIL",
                "" if got == want else "differs from baselines-ported")

    with concurrent.futures.ThreadPoolExecutor(args.jobs) as ex:
        results.extend(ex.map(lambda a: one(*a), list(artifacts(units, keep))))


def run_made(name, src, load, units, args, keep, results):
    def one(u, t, key):
        session = os.path.join(WORK, name, "made", key)
        shutil.rmtree(session, ignore_errors=True)
        copy_sources(src, session)
        d1, d2 = "first." + baseline.dump_name(u, t), "second." + baseline.dump_name(u, t)
        _o, p1 = run_session(session,
                             ["load " + load, "make -u %s %s -d %s" % (u, t, d1), "squit"],
                             args.classes, "first")
        first = read_dump(os.path.join(session, d1), t)
        if p1 or first is None:
            return (name, "made", key, "FAIL", "first session: %s" % (p1 or "not dumped"))
        want = ported_baseline(name, key)
        note = ""
        if want is not None and first != want:
            note = "; first session differs from baselines-ported"
        _o, p2 = run_session(session, ["make -u %s %s -d %s" % (u, t, d2)],
                             args.classes, "second")
        second = read_dump(os.path.join(session, d2), t)
        if second is None:
            return (name, "made", key, "FAIL",
                    "second session: not dumped" + (" (%s)" % p2 if p2 else "") + note)
        if p2:
            return (name, "made", key, "FAIL", "second session: %s%s" % (p2, note))
        if second != first:
            return (name, "made", key, "FAIL", "second session differs from first" + note)
        return (name, "made", key, "FAIL" if note else "ok", note[2:])

    with concurrent.futures.ThreadPoolExecutor(args.jobs) as ex:
        results.extend(ex.map(lambda a: one(*a), list(artifacts(units, keep))))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["loaded", "made", "both"], default="both")
    ap.add_argument("--classes", default=DEFAULT_CLASSES)
    ap.add_argument("--only", default=None, help="comma-separated example names")
    ap.add_argument("--artifacts", default=None,
                    help="fnmatch pattern on artifact names, e.g. '*.nmz'")
    ap.add_argument("--jobs", type=int, default=1,
                    help="sessions to run at once (each artifact is independent)")
    args = ap.parse_args()
    if not os.environ.get("ZETA_JAVA"):
        # the ported classes need JDK 21; zdrive.py's own default is the 2002 JDK 8
        os.environ["ZETA_JAVA"] = os.path.join(ZETA, "toolchain/jdk21/bin/java")

    baseline.enable_all_types()
    wanted = set(args.only.split(",")) if args.only else None
    keep = (lambda k: fnmatch.fnmatch(k, args.artifacts)) if args.artifacts else None
    modes = ["loaded", "made"] if args.mode == "both" else [args.mode]

    results = []
    for name, src, load, units in baseline.CORPUS:
        if wanted and name not in wanted:
            continue
        for mode in modes:
            print("==> %s (%s)" % (name, mode), flush=True)
            n = len(results)
            (run_loaded if mode == "loaded" else run_made)(
                name, src, load, units, args, keep, results)
            for r in results[n:]:
                if r[3] != "ok":
                    print("    %-6s %-40s %s" % (r[3], r[2], r[4]), flush=True)

    for mode in modes:
        rs = [r for r in results if r[1] == mode]
        counts = {s: sum(1 for r in rs if r[3] == s) for s in ("ok", "dumped", "FAIL")}
        print("\n%s: %d artifacts: %d round-trip, %d dumped without a baseline, %d FAIL"
              % (mode, len(rs), counts["ok"], counts["dumped"], counts["FAIL"]))
    print("transcripts and dumps under %s" % WORK)
    return 1 if any(r[3] == "FAIL" for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
