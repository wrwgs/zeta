#!/usr/bin/env python3
"""Generate and run negative tests for the ZETA tool chain.

Why: the positive corpus (harness/baseline.py) pins the success path only -- no input in the
archive produces a type error, so the checker's ~24 diagnostics are entirely unexercised.  This
generates mutants of the real specifications, runs them, and reports which diagnostics each one
elicits, measured against a catalogue *extracted from the checker's own source* so the
denominator cannot drift.

Design points that matter:

  * Mutations are applied only INSIDE Z environments.  A `.tex` file in this corpus is two
    languages at once; a mutation landing in prose or in a macro definition produces a LaTeX
    adaptor error (that subsystem has the most Diag sites of any tool), which would otherwise
    read as type-checker coverage.  Every diagnostic is classified by the subsystem that
    reported it.

  * For every mutant the A/B pair is recorded: `exp_orig` (the pristine transcript) and `exp`
    (the mutant's).  A mutant whose `exp` equals its `exp_orig` is the interesting case -- the
    tool did not notice -- but that can equally mean the change was not observable through the
    artifacts we compare.  So each mutant also records which *artifacts* changed, which
    separates a genuine blind spot from a gap in the instrumentation:

        detected          a diagnostic was reported
        silent-observable no diagnostic, but pretty-text or nmz changed
        undetected        no diagnostic and no observable change  <- a finding

Usage:
    negtest.py --generate [--only bbook]      # write tests/negative/...
    negtest.py --run [--only bbook]           # run them, record exp/exp_orig, report coverage
    negtest.py --catalogue                    # print the diagnostic catalogue and exit
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import canonicalize                                                   # noqa: E402

ZETA = os.path.dirname(HERE)
EXAMPLES = os.path.join(ZETA, "examples")
# The ported checker's own source.  negtest parses it rather than the 1998 Report.pizza:
# the catalogue must describe the diagnostics the *running* checker emits, and the port is
# now that checker.  Verified equivalent -- both yield the same 20 entries with identical
# literals, because the regex is anchored at "\n\s*static Format" and so skips the private
# show() helpers the port added.
REPORT_SRC = os.path.join(ZETA, "src/zeta/tools/zchecker/Report.java")
TESTS = os.path.join(ZETA, "tests/negative")
WORK = os.path.join(ZETA, "work/negtest")

# Environments whose body is Z (not prose, not a macro definition).  zedgroup is a container
# and zdirectives/verbatim are not Z, so neither is mutated.
Z_ENVS = ("zed", "schema", "axdef", "gendef", "zexecexpr", "zexecpred")
# Only these have a `name: type` declaration part, so only these take the declaration-based
# operators.  `zed` holds given sets, free types and abbreviations; zexecexpr/zexecpred hold
# expressions.
DECL_ENVS = ("schema", "axdef", "gendef")

# The checker emits five diagnostics directly rather than through Report (UnitChecker.pizza);
# these are matched by text because they have no Report method to name them.
DIRECT_CHECKER_MSGS = {
    "must-be-section": "must be a section but is a class",
    "must-be-class": "must be a class but is a section",
    "unresolvable-unit": "cannot be resolved as a unit",
    "ambiguous-unit": "is ambigious",
    "unit-needed": "unit needed for checking",
}

# Unit lists are discovered per source (see discover_units); never hand-maintained.
SOURCES = [
    ("bbook", os.path.join(EXAMPLES, "bbook"), "bbook.tex", []),
    ("ucases", os.path.join(EXAMPLES, "ucases"), "ucases.tex", []),
    ("zapnotes", os.path.join(EXAMPLES, "zapnotes"), "zapnotes.tex", []),
]


# ----------------------------------------------------------------- the catalogue

def catalogue():
    """Diagnostic kinds, extracted from Report.java plus the direct checker messages.

    Each entry maps a kind name to the literal prefix the checker prints, so classification
    follows the source rather than a hand-kept list.
    """
    src = open(REPORT_SRC, encoding="latin-1").read()
    parts = re.split(r"\n\s*static Format ([a-zA-Z]+)\(", src)
    out = {}
    for i in range(1, len(parts), 2):
        name, body = parts[i], parts[i + 1]
        lits = [l.strip() for l in re.findall(r'"([^"]*)"', body) if len(l.strip()) > 4]
        if lits:
            out[name] = lits                    # ALL literals, not just the head
    for k, v in DIRECT_CHECKER_MSGS.items():
        out[k] = [v]
    return out


def head(cat, kind):
    return cat[kind][0]


def classify(diag_text, cat):
    """Best-matching diagnostic kind, or None.

    Scoring on every literal of a kind rather than its head, because heads overlap:
    `notSupported` ("currently not supported") is a substring of `DZnotSupported`, and
    `typeIncompat`/`typeIncompatInEnv`/`typeIncompatInMatch` all begin "type mismatch".  The
    discriminating text is in the later literals ("' in environment", "' joined with `"), so
    the kind matching the most -- and the most specific -- literals wins.
    """
    best, best_score = None, 0
    for kind, lits in cat.items():
        hits = [l for l in lits if l in diag_text]
        if not hits or lits[0] not in diag_text:
            continue
        score = sum(len(l) for l in hits) + 100 * len(hits)
        if score > best_score:
            best, best_score = kind, score
    return best


# ----------------------------------------------------------------- mutation

def find_envs(tex):
    """Yield (start, end, env, body_start, body_end) for each Z environment."""
    for m in re.finditer(r"\\begin\{(" + "|".join(Z_ENVS) + r")\}(\{[^}]*\})?",
                         tex):
        env = m.group(1)
        close = tex.find("\\end{%s}" % env, m.end())
        if close < 0:
            continue
        yield (m.start(), close + len("\\end{%s}" % env), env, m.end(), close)


def declarations(body):
    """[(line_index, name, type_text)] for the declaration part of a schema/axdef/gendef body.

    Deliberately strict, because a loose version produced junk mutants:

      * `(?![:=])` after the colon rejects free-type definitions (`INPUT ::= ...`) and
        abbreviations (`X := ...`), which an earlier version mutated into `INPUT : DATE`;
      * a type containing `.tex(` rejects source locators, which is how `LTX:bbook.tex(199.3)`
        inside a `\\yields` block was once mistaken for a declaration of a name `LTX`.
    """
    decl_part = body.split("\\where")[0]
    out = []
    for i, line in enumerate(decl_part.split("\n")):
        if line.lstrip().startswith("%"):
            continue
        m = re.match(r"\s*([A-Za-z][A-Za-z0-9_]*)\s*:(?![:=])\s*(.+?)\s*(\\\\)?\s*$", line)
        if m and ".tex(" not in m.group(2):
            out.append((i, m.group(1), m.group(2)))
    return out


def given_types(tex):
    """Given types introduced as [NAME,DATE] inside a zed environment."""
    names = set()
    for _s, _e, env, bs, be in find_envs(tex):
        if env != "zed":
            continue
        for m in re.finditer(r"\[([A-Za-z0-9_,\s]+)\]", tex[bs:be]):
            for n in m.group(1).split(","):
                if n.strip():
                    names.add(n.strip())
    return sorted(names)


def mutants(tex):
    """Yield (operator, detail, mutated_tex).  Each mutation touches exactly one site."""
    givens = given_types(tex)
    for _s, _e, env, bs, be in find_envs(tex):
        body = tex[bs:be]
        # A zexecexpr/zexecpred block states an expression AND, after \yields, the expected
        # result.  The expectation is the oracle, not the input, so it is never mutated.
        if "\\yields" in body:
            be = bs + body.index("\\yields")
            body = tex[bs:be]
        decls = declarations(body) if env in DECL_ENVS else []
        lines = body.split("\n")

        # 1. make a reference undeclared: rename the last use of a declared name
        for _i, name, _t in decls[:2]:
            uses = [m.start() for m in
                    re.finditer(r"(?<![A-Za-z0-9_\\])" + re.escape(name) + r"(?![A-Za-z0-9_])",
                                body)]
            if len(uses) >= 2:
                at = uses[-1]
                nb = body[:at] + "ZZundecl" + body[at + len(name):]
                yield ("undeclare-ref", "%s/%s -> ZZundecl" % (env, name), tex[:bs] + nb + tex[be:])
                break

        # 2. duplicate a declaration
        if decls:
            i, name, t = decls[0]
            nl = list(lines)
            nl.insert(i + 1, "  %s: %s \\\\" % (name, t)) if not nl[i].rstrip().endswith("\\\\") \
                else nl.insert(i + 1, "  %s: %s \\\\" % (name, t))
            if not nl[i].rstrip().endswith("\\\\"):
                nl[i] = nl[i].rstrip() + " \\\\"
            yield ("dup-decl", "%s/%s duplicated" % (env, name),
                   tex[:bs] + "\n".join(nl) + tex[be:])

        # 3. change a declared type to a different given type
        if decls and len(givens) >= 2:
            i, name, t = decls[0]
            for g in givens:
                if g not in t:
                    nl = list(lines)
                    nl[i] = re.sub(r":\s*.+?(\s*\\\\\s*)?$", ": %s\\1" % g, nl[i], count=1)
                    yield ("swap-type", "%s/%s : %s -> %s" % (env, name, t, g),
                           tex[:bs] + "\n".join(nl) + tex[be:])
                    break

        # 4. delete a declaration (its uses become undeclared)
        if len(decls) >= 2:
            i, name, _t = decls[0]
            nl = [l for j, l in enumerate(lines) if j != i]
            yield ("drop-decl", "%s/%s removed" % (env, name),
                   tex[:bs] + "\n".join(nl) + tex[be:])

        # 5. actualise a non-generic name
        if decls:
            _i, name, _t = decls[0]
            uses = [m.start() for m in
                    re.finditer(r"(?<![A-Za-z0-9_\\])" + re.escape(name) + r"(?![A-Za-z0-9_])",
                                body)]
            if uses and givens:
                at = uses[-1]
                nb = body[:at + len(name)] + "[%s]" % givens[0] + body[at + len(name):]
                yield ("generic-actual", "%s/%s[%s]" % (env, name, givens[0]),
                       tex[:bs] + nb + tex[be:])


# ----------------------------------------------------------------- running

def session(cwd, commands, classes=None):
    cmd = [sys.executable, "-I", os.path.join(HERE, "zdrive.py"), "--cwd", cwd]
    if classes:
        cmd += ["--classes", classes]
    p = subprocess.run(cmd + commands, capture_output=True, text=True, timeout=1800)
    return p.stdout


def discover_units(work, texfile):
    """Ask ZETA which units the file defines.

    Hand-picking the unit list was a real defect: mutations landing in a unit nobody checked
    produced no diagnostic and no artifact change, and so were reported as `undetected` -- a
    claim about the tool when it was actually a gap in the instrumentation.  33 of 53 mutants
    were mislabelled this way.
    """
    out = session(work, ["load " + texfile, "list units"])
    units = []
    for line in out.split("\n"):
        if line.startswith("= ") and "#" in line:
            for tok in line[2:].split(","):
                tok = tok.strip()
                if tok.startswith("LTX:"):
                    tok = tok[4:]
                if "#" in tok:
                    units.append(tok)
    return units


def diagnostics(transcript):
    """The `! ...` lines, with their reporting subsystem where it can be told."""
    out = []
    for line in transcript.split("\n"):
        if line.startswith("! "):
            out.append(line[2:].strip())
    return out


def subsystem(diag, cat):
    if classify(diag, cat):
        return "checker"
    low = diag.lower()
    if "latex" in low or "\\begin" in diag or "\\end" in diag or "environment" in low:
        return "latex"
    if "parse" in low or "encountered" in low or "expecting" in low or "lexical" in low:
        return "parser"
    return "other"


def run_source(name, src_dir, texfile, units, cat, only_generate=False,
               classes=None, check=False):
    base = os.path.join(TESTS, name)
    results = []

    # pristine run -> exp_orig
    work = os.path.join(WORK, name + "-orig")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work)
    for f in os.listdir(src_dir):
        if f.endswith((".tex", ".sty")):
            shutil.copy(os.path.join(src_dir, f), work)
    if not units:
        units = discover_units(work, texfile)
        print("    units: %s" % ", ".join(units))
    cmds = ["load " + texfile] + ["c -u " + u for u in units]
    cmds += ["make -u %s pretty-text -d pt_%s.txt" % (u, u.replace("#", "_")) for u in units]
    orig_t = session(work, cmds, classes)
    exp_orig = canonicalize.canon_transcript(orig_t, session_dir=work)
    orig_pt = {}
    for u in units:
        p = os.path.join(work, "pt_%s.txt" % u.replace("#", "_"))
        orig_pt[u] = open(p, encoding="latin-1").read() if os.path.exists(p) else None

    tex = open(os.path.join(src_dir, texfile), encoding="latin-1").read()
    counter = {}
    for op, detail, mtex in mutants(tex):
        counter[op] = counter.get(op, 0) + 1
        tid = "%s-%02d" % (op, counter[op])
        tdir = os.path.join(base, tid)
        os.makedirs(tdir, exist_ok=True)
        with open(os.path.join(tdir, texfile), "w", encoding="latin-1") as f:
            f.write(mtex)
        meta = {"source": texfile, "operator": op, "detail": detail, "units": units}
        with open(os.path.join(tdir, "mutation.json"), "w") as f:
            json.dump(meta, f, indent=2)
        if not check:
            with open(os.path.join(tdir, "exp_orig"), "w", encoding="latin-1") as f:
                f.write(exp_orig)
        if only_generate:
            results.append((tid, op, detail, None, None, None, None))
            continue

        mwork = os.path.join(WORK, "%s-%s" % (name, tid))
        shutil.rmtree(mwork, ignore_errors=True)
        os.makedirs(mwork)
        for f in os.listdir(src_dir):
            if f.endswith((".tex", ".sty")):
                shutil.copy(os.path.join(src_dir, f), mwork)
        with open(os.path.join(mwork, texfile), "w", encoding="latin-1") as f:
            f.write(mtex)
        mt = session(mwork, cmds, classes)
        exp = canonicalize.canon_transcript(mt, session_dir=mwork)

        diags = diagnostics(mt)
        kinds = sorted({k for k in (classify(d, cat) for d in diags) if k})
        subs = sorted({subsystem(d, cat) for d in diags})
        changed = []
        for u in units:
            p = os.path.join(mwork, "pt_%s.txt" % u.replace("#", "_"))
            now = open(p, encoding="latin-1").read() if os.path.exists(p) else None
            if now != orig_pt[u]:
                changed.append("pretty-text:" + u)
        if diags:
            verdict = "detected"
        elif changed:
            verdict = "silent-observable"
        else:
            verdict = "undetected"
        record = {"verdict": verdict, "kinds": kinds, "subsystems": subs,
                  "artifacts_changed": changed, "diagnostics": diags[:8]}
        note = None
        if check:
            note = compare(tdir, exp, record)
        else:
            with open(os.path.join(tdir, "exp"), "w", encoding="latin-1") as f:
                f.write(exp)
            with open(os.path.join(tdir, "verdict.json"), "w") as f:
                json.dump(record, f, indent=2)
        results.append((tid, op, detail, verdict, kinds, subs, note))
    return results


def compare(tdir, exp, record):
    """Compare a mutant's fresh transcript and verdict with the recorded ones.

    `diagnostics` is deliberately not compared: it is diagnostic *text*, including source
    locators that shift with the mutation, while `verdict` and `kinds` are the claims about the
    system that a port must not change.
    """
    notes = []
    path = os.path.join(tdir, "exp")
    if not os.path.exists(path):
        return "NO RECORDING"
    if open(path, encoding="latin-1").read() != exp:
        notes.append("transcript")
    path = os.path.join(tdir, "verdict.json")
    if os.path.exists(path):
        with open(path) as f:
            was = json.load(f)
        for key in ("verdict", "kinds", "subsystems", "artifacts_changed"):
            if was.get(key) != record.get(key):
                notes.append("%s %s -> %s" % (key, was.get(key), record.get(key)))
    return "DIFFERS: " + "; ".join(notes) if notes else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--generate", action="store_true")
    ap.add_argument("--run", action="store_true",
                    help="run the mutants and RECORD exp/exp_orig/verdict.json")
    ap.add_argument("--check", action="store_true",
                    help="run the mutants and COMPARE against the recordings; non-zero exit "
                         "on any difference.  With --classes, this is the gate for a ported "
                         "build.")
    ap.add_argument("--classes", default=None,
                    help="class tree(s) to run against, e.g. classes")
    ap.add_argument("--catalogue", action="store_true")
    ap.add_argument("--only", default=None)
    args = ap.parse_args()
    cat = catalogue()

    if args.catalogue:
        print("%d diagnostic kinds (from Report.pizza + direct checker messages)\n" % len(cat))
        for k in sorted(cat):
            print("  %-30s %s" % (k, head(cat, k)))
        return 0
    if not (args.generate or args.run or args.check):
        ap.error("choose --generate, --run, --check or --catalogue")

    wanted = set(args.only.split(",")) if args.only else None
    all_results, covered, differed = [], set(), []
    for name, src, tex, units in SOURCES:
        if wanted and name not in wanted:
            continue
        print("==> %s" % name)
        rs = run_source(name, src, tex, units, cat,
                        only_generate=not (args.run or args.check),
                        classes=args.classes, check=args.check)
        for tid, op, detail, verdict, kinds, subs, note in rs:
            if note:
                differed.append((tid, note))
            if verdict:
                print("    %-18s %-14s %-34s %s %s"
                      % (tid, verdict, detail[:34], ",".join(kinds or []), ",".join(subs or [])))
                covered |= set(kinds or [])
            else:
                print("    %-18s generated      %s" % (tid, detail[:40]))
        all_results += rs

    if args.run or args.check:
        verdicts = {}
        for r in all_results:
            verdicts[r[3]] = verdicts.get(r[3], 0) + 1
        print("\n%d mutants: %s" % (len(all_results),
                                    ", ".join("%s=%d" % kv for kv in sorted(verdicts.items()))))
        print("\ndiagnostic coverage: %d of %d kinds" % (len(covered), len(cat)))
        for k in sorted(cat):
            print("  %-4s %-30s %s" % ("HIT" if k in covered else "-", k, head(cat, k)[:46]))
    if args.check:
        print("\n%d of %d mutant(s) differ from the recording" % (len(differed),
                                                                  len(all_results)))
        for tid, note in differed:
            print("    %-18s %s" % (tid, note))
        return 1 if differed else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
