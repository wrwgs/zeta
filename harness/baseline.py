#!/usr/bin/env python3
"""Record and check behavioural baselines for the ZETA reference implementation.

This is the regression oracle the Pizza->Java port needs (zesurrect-plan.md §7).  For each
example specification it drives a ZETA session, dumps every content type that can be compared
reliably, canonicalises the output, and either records it as a baseline or checks it against the
recorded one.  It also records the session transcript, which captures the type checker's
diagnostics.

    baseline.py --update          # (re)record baselines into baselines/
    baseline.py --check           # compare the current build against them
    baseline.py --check --classes <jar-or-dir>     # ... for a different build

`--classes` is the point of the whole exercise: the same corpus can be run against the 2002
reference (the default) and against a ported implementation, and the outputs compared.

Excluded on purpose: unchecked-env, checked-absy and checked-env are not reproducible across
runs of the *reference itself* (identity-hash iteration order; see canonicalize.py).  They are
listed in the report as "not baselined" rather than compared unreliably.
"""
import argparse
import fnmatch
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# so the script works from any directory, and under `python3 -I` (which does not put the
# script's own directory on sys.path)
sys.path.insert(0, HERE)
import canonicalize                                                   # noqa: E402

ZETA = os.path.dirname(HERE)
EXAMPLES = os.path.join(ZETA, "examples")
ZETABASE = os.path.join(ZETA, "run/zetabase")
BASELINES = os.path.join(ZETA, "baselines-ported")  # baselines/ (2002) is archived
WORK = os.path.join(ZETA, "work/baseline-run")

# Dumps are taken in phases, each in its OWN session, because ZETA annotates the abstract
# syntax in place: once a unit has been type-checked, dumping "unchecked-absy" returns the
# annotated term, whose annotation order is unstable.  So the pre-check artifacts must be
# collected in a session where nothing has been checked.  `make <type>` pulls in whatever the
# target graph needs, so no explicit `c` is required for the later phase.
# The three richest dumps were unreproducible in the 2002 reference (identity-hash iteration
# order, plan 7.3) and so were never collected.  The port fixes both causes at the source --
# Name.hashCode/compareTo and FormatPrinter.buildHashtableFormat ordering -- which makes them
# deterministic, so --all-types adds them when measuring a build that contains those fixes.
EXTRA_TYPES = ["unchecked-env", "checked-env", "checked-absy"]

PHASES = [
    ("parse", ["text", "lang-config", "latex-fragment", "unchecked-absy"]),
    ("check", ["pretty-text", "nmz"]),
]
TYPES = [t for _p, ts in PHASES for t in ts]


def enable_all_types():
    """Add the previously-unreproducible dumps (only valid for a build with the fixes)."""
    PHASES[0] = (PHASES[0][0], PHASES[0][1] + ["unchecked-env"])
    PHASES[1] = (PHASES[1][0], PHASES[1][1] + ["checked-env", "checked-absy"])
    TYPES[:] = [t for _p, ts in PHASES for t in ts]

CORPUS = [
    # name, directory with the sources, file to load, units to examine
    ("bbook",  os.path.join(EXAMPLES, "bbook"),  "bbook.tex",
     ["BBSpec#bbook", "BBExec#bbook", "BBTest#bbook", "Toolkit#Toolkit"]),
    ("ucases", os.path.join(EXAMPLES, "ucases"), "ucases.tex",
     ["UseCases#ucases", "UseCasesModel#ucases", "CashDispenser#ucases"]),
    ("graphs", os.path.join(EXAMPLES, "graphs"), "Shortest.tex",
     ["Shortest#Shortest"]),
    # The ZAP tutorial: a ZETA *document* that is simultaneously a specification, and the
    # largest input in the archive at 993 lines.  All 11 units type-check clean.
    ("zapnotes", os.path.join(EXAMPLES, "zapnotes"), "zapnotes.tex",
     ["DataInput#zapnotes", "FreeTypes#zapnotes", "FreeTypes2#zapnotes",
      "Genericity#zapnotes", "Nat#zapnotes", "Specials#zapnotes", "Stepping#zapnotes",
      "facExec1#zapnotes", "facExec2#zapnotes", "facNonExec#zapnotes", "queens#zapnotes"]),
]


def run_session(session_dir, commands, classes=None):
    """Drive one ZETA session and return its transcript.

    The driver's exit status is NOT ignored.  `zdrive.py` returns non-zero when a command
    timed out or ZETA died, and in those cases the transcript is truncated -- which, compared
    against a baseline, shows up as an ordinary `DIFFERS` on whichever artifacts happened to be
    cut off.  That is the most misleading failure this harness can produce: it looks like a
    regression in the port.  It cost a confusing investigation when `Shortest.nmz` differed
    once under heavy load and then matched 8 runs out of 8, so the status is now surfaced.
    """
    cmd = [sys.executable, "-I", os.path.join(HERE, "zdrive.py"), "--cwd", session_dir]
    if classes:
        cmd += ["--classes", classes]
    cmd += commands
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    if p.returncode != 0:
        bad = [ln for ln in p.stdout.split("\n") if ln.startswith("### ")]
        print("    SESSION FAILED (driver exit %d) in %s%s"
              % (p.returncode, os.path.basename(session_dir),
                 ": " + "; ".join(bad) if bad else ""))
        print("      the transcript is truncated, so any difference below is the harness, "
              "not the port")
    return p.stdout


def fresh_session(name, tag, src_dir):
    session = os.path.join(WORK, "%s-%s" % (name, tag))
    shutil.rmtree(session, ignore_errors=True)
    os.makedirs(session)
    for f in os.listdir(src_dir):
        if f.endswith((".tex", ".sty")):
            shutil.copy(os.path.join(src_dir, f), session)
    return session


def collect(name, src_dir, load, units, classes=None):
    """Run one example and return {artifact-name: canonical-text}.

    Each (unit, content-type) gets its OWN session.  Sharing a session is measurably worse,
    twice over: processing one unit enlarges the name sets a later unit's output depends on,
    and dumping one content type perturbs what a later dump of the same unit produces.  Both
    show up as artifacts that are perfectly reproducible in isolation but flake inside a
    shared session.  One session per artifact also makes every comparison independent, so a
    regression during the port localises to a single dump.

    It also removes the need for a pre-check/post-check split: ZETA annotates the abstract
    syntax in place, so `unchecked-absy` is only trustworthy in a session where nothing has
    been checked -- which is automatic when the session does exactly one `make`.
    """
    out = {}
    for u in units:
        for t in TYPES:
            session = fresh_session(name, "%s-%s" % (safe(u), t), src_dir)
            run_session(session,
                        ["load " + load,
                         "make -u %s %s -d %s" % (u, t, dump_name(u, t))],
                        classes)
            path = os.path.join(session, dump_name(u, t))
            key = "%s.%s" % (safe(u), t)
            if not os.path.exists(path):
                out[key] = "### NOT DUMPED\n"
                continue
            text = open(path, encoding="latin-1").read()
            if t == "nmz":
                ok, lost, gained = canonicalize.verify_nmz(text)
                if not ok:
                    print("    !! canonicalisation corrupted %s: lost %s gained %s"
                          % (key, dict(lost), dict(gained)))
                out[key] = canonicalize.canon_nmz(text)
            else:
                out[key] = canonicalize.canon_paths(text)

    # the transcript captures the type checker's diagnostics, so it gets its own session
    session = fresh_session(name, "transcript", src_dir)
    transcript = run_session(session, ["load " + load] + ["c -u " + u for u in units], classes)
    out["session.transcript"] = canonicalize.canon_transcript(
        transcript, zetabase=ZETABASE, session_dir=session)
    return out


def safe(unit):
    return unit.replace("#", "_at_").replace("/", "_")


def dump_name(unit, t):
    return "%s.%s.dump" % (safe(unit), t)


EXCLUDED_FILE = os.path.join(BASELINES, "EXCLUDED")


def load_excluded():
    if not os.path.exists(EXCLUDED_FILE):
        return set()
    keys = set()
    for line in open(EXCLUDED_FILE):
        line = line.split("#", 1)[0].strip()
        if line:
            keys.add(line)
    return keys


def selftest(args, wanted):
    """Run the corpus N times against the reference and record which artifacts reproduce.

    An artifact that differs between runs of the *same* build cannot be a baseline.  The
    instability is probabilistic, not a fixed property of an artifact: it comes from
    identity-hash iteration order, so whether a given dump is affected depends on the JVM's
    address layout for that run.  Two runs therefore under-detect it -- hence --runs, and hence
    the rule that anything differing in ANY pair of runs is excluded.
    """
    unstable, stable = [], []
    for name, src, load, units in CORPUS:
        if wanted and name not in wanted:
            continue
        runs = []
        for i in range(args.runs):
            print("==> %s: run %d/%d" % (name, i + 1, args.runs))
            runs.append(collect(name, src, load, units, args.classes))
        for key in sorted(runs[0]):
            full = "%s/%s" % (name, key)
            values = {r.get(key) for r in runs}
            (stable if len(values) == 1 else unstable).append(full)
    os.makedirs(BASELINES, exist_ok=True)
    # keep exclusions for examples this run did not cover, or `--only` would silently drop them
    covered = {name for name, _s, _l, _u in CORPUS if not wanted or name in wanted}
    kept = sorted(k for k in load_excluded() if k.split("/", 1)[0] not in covered)
    unstable = sorted(set(unstable) | set(kept))
    with open(EXCLUDED_FILE, "w") as f:
        f.write("# Artifacts that differ between two runs of the SAME build, so they cannot\n"
                "# serve as baselines.  Generated by `baseline.py --selftest`.\n"
                "# The instability is probabilistic (JVM address layout), so this list is a\n"
                "# sample, not a proof: re-run --selftest with more --runs to harden it.\n"
                "# Root cause: Name.hashCode()/compareTo() are identity-hash based\n"
                "# (src/util/Name.pizza), so Set<Name>/Map<Name,_> iterate in a\n"
                "# JVM-address-dependent order.  Fixing that needs a from-source build\n"
                "# (plan §3.4 route A); until then these are not compared.\n")
        for k in unstable:
            f.write(k + "\n")
    print("\nreproducible: %d    not reproducible: %d" % (len(stable), len(unstable)))
    for k in unstable:
        print("    unstable  %s" % k)
    print("\nwrote %s" % EXCLUDED_FILE)
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--update", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--selftest", action="store_true",
                    help="run the corpus --runs times and record which artifacts reproduce")
    ap.add_argument("--runs", type=int, default=3,
                    help="runs per example for --selftest (default 3)")
    ap.add_argument("--classes", default=None)
    ap.add_argument("--only", default=None, help="comma-separated example names")
    ap.add_argument("--artifacts", default=None,
                    help="fnmatch pattern on artifact names (e.g. 'session.transcript', "
                         "'*.nmz'); restricts what is recorded or compared, leaving the rest "
                         "of the tree untouched.  Use it to re-record one kind of artifact "
                         "after a harness change, instead of rewriting baselines that nothing "
                         "asked to change.")
    ap.add_argument("--all-types", action="store_true",
                    help="also collect unchecked-env/checked-env/checked-absy; only "
                         "meaningful for a build with the determinism fixes")
    ap.add_argument("--baselines", default=None,
                    help="baseline tree to record/check against (default: ./baselines). "
                         "Use a separate tree when measuring a DIFFERENT build, so the "
                         "reference baselines are not overwritten.")
    args = ap.parse_args()
    if not (args.update or args.check or args.selftest):
        ap.error("choose --update, --check or --selftest")

    if args.all_types:
        enable_all_types()
    if args.baselines:
        global BASELINES, EXCLUDED_FILE
        BASELINES = os.path.abspath(args.baselines)
        EXCLUDED_FILE = os.path.join(BASELINES, "EXCLUDED")
        os.makedirs(BASELINES, exist_ok=True)
    wanted = set(args.only.split(",")) if args.only else None
    keep = (lambda k: fnmatch.fnmatch(k, args.artifacts)) if args.artifacts else None
    if args.selftest:
        return selftest(args, wanted)

    excluded = load_excluded()
    newly_excluded = []
    total = same = differ = new = skipped = 0

    for name, src, load, units in CORPUS:
        if wanted and name not in wanted:
            continue
        print("==> %s (%d units)" % (name, len(units)))
        arts = collect(name, src, load, units, args.classes)
        if keep:
            for k in [k for k in arts if not keep(k)]:
                del arts[k]
        if args.update:
            # --update is self-validating: record an artifact only if ALL of --runs
            # independent runs agree on it.  Stability here is probabilistic, so a value taken
            # from one run can be an unlucky sample -- that is exactly how a bad baseline gets
            # recorded and then "fails" every later check.  Two runs leave borderline
            # artifacts slipping through and crying wolf later, so the default is three.
            others = []
            for i in range(max(1, args.runs - 1)):
                print("    (verifying, run %d/%d)" % (i + 2, max(2, args.runs)))
                others.append(collect(name, src, load, units, args.classes))
            if keep:
                others = [{k: v for k, v in o.items() if keep(k)} for o in others]
            for key in sorted(arts):
                if any(arts[key] != o.get(key) for o in others):
                    full = "%s/%s" % (name, key)
                    if full not in excluded:
                        newly_excluded.append(full)
                        print("    UNSTABLE -> excluding  %s" % key)
                    else:
                        skipped += 1
                    path = os.path.join(BASELINES, name, key)
                    if os.path.exists(path):
                        os.remove(path)
                    del arts[key]
        for key in list(arts):
            if "%s/%s" % (name, key) in excluded:
                del arts[key]
                skipped += 1
                if args.update:
                    # drop any baseline left over from before the artifact was excluded
                    stale = os.path.join(BASELINES, name, key)
                    if os.path.exists(stale):
                        os.remove(stale)
                        print("    removed stale baseline  %s" % key)
        base_dir = os.path.join(BASELINES, name)
        os.makedirs(base_dir, exist_ok=True)
        for key in sorted(arts):
            total += 1
            path = os.path.join(base_dir, key)
            text = arts[key]
            if args.update:
                prev = open(path, encoding="latin-1").read() if os.path.exists(path) else None
                with open(path, "w", encoding="latin-1") as f:
                    f.write(text)
                if prev is None:
                    new += 1
                    print("    new      %s (%d bytes)" % (key, len(text)))
                elif prev != text:
                    differ += 1
                    print("    CHANGED  %s" % key)
                else:
                    same += 1
            else:
                if not os.path.exists(path):
                    new += 1
                    print("    MISSING BASELINE  %s" % key)
                elif open(path, encoding="latin-1").read() == text:
                    same += 1
                else:
                    differ += 1
                    print("    DIFFERS  %s" % key)

    if newly_excluded:
        with open(EXCLUDED_FILE, "a") as f:
            f.write("".join(k + "\n" for k in newly_excluded))
        print("\nappended %d newly-unstable artifact(s) to %s"
              % (len(newly_excluded), EXCLUDED_FILE))
        skipped += len(newly_excluded)

    print("\n%d artifacts: %d match, %d differ, %d new/missing" % (total, same, differ, new))
    if skipped:
        print("%d skipped per baselines/EXCLUDED (not reproducible in the reference)" % skipped)
    if canonicalize.UNCANONICALISABLE:
        print("compared raw (no canonicalisation available): %s"
              % ", ".join(canonicalize.UNCANONICALISABLE))
    return 1 if (args.check and (differ or new)) else 0


if __name__ == "__main__":
    sys.exit(main())
