#!/usr/bin/env python3
"""Animation oracle for the ZAP/ZAM animator (zesurrect-plan.md section 4, exit criterion 3).

`zapnotes.tex` ships 13 zexecexpr/zexecpred blocks whose \\yields sections record what the
1999-2000 animator printed -- in-line expectations, the only animation oracle the archive
carries.  This tool extracts them, runs each through a live session (`e` / `p` commands),
and records the normalised transcript, mirroring the record-when-stable rule of
baseline.py --update and targeted.py: a recording is kept only when all --runs independent
runs agree, because a value taken from one unlucky run fails every later check.

Two distinct comparisons, deliberately separated:

  --run / --check   the regression oracle: does the animator still print what it printed
                    when the recording was made?  The transcript is compared whole.
  --yields          the historical cross-check: do the `= ` result lines match the \\yields
                    text from 1999/2000?  Informational -- some blocks cannot match
                    literally (diagnostic-valued \\yields embed stale source locators;
                    the Genericity truncation example shows 30 elements where the current
                    -t limit is 20), so this reports per-block without failing the run.

Blocks in section `queens` are skipped: zapnotes.tex:651 documents that the query does not
terminate in reasonable time ("some minutes ... many millions of inference steps").

Layout per block, tests/zapexec/<section>-L<line>/:
  query.json   section, unit, kind (expr|pred), source line, the query, the \\yields text
  exp          the recorded transcript (canon_transcript-normalised)

Usage:
  zapexec.py --list
  zapexec.py --run   [--runs N] [--classes CP]     record (only blocks whose runs agree)
  zapexec.py --check [--classes CP]                compare against recordings, exit 1 on diff
  zapexec.py --yields [--classes CP]               report \\yields agreement per block
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
ZETABASE = os.path.join(ZETA, "run/zetabase")
SRC_DIR = os.path.join(ZETA, "examples/zapnotes")
TEX = os.path.join(SRC_DIR, "zapnotes.tex")
TESTS = os.path.join(ZETA, "tests/zapexec")
WORK = os.path.join(ZETA, "work/zapexec-run")

# section -> reason for not running its blocks at all
SKIP_SECTIONS = {
    "queens": "documented non-termination (zapnotes.tex:651: 'some minutes', millions of steps)",
}

BEGIN_RE = re.compile(r"\\begin\{(zexecexpr|zexecpred)\}")
END_RE = re.compile(r"\\end\{(zexecexpr|zexecpred)\}")
SECTION_RE = re.compile(r"\\zsection\{([^}]*)\}")


def uncommented(line):
    """The part of a LaTeX line before an unescaped %."""
    out = []
    prev = ""
    for ch in line:
        if ch == "%" and prev != "\\":
            break
        out.append(ch)
        prev = ch
    return "".join(out)


def extract_blocks(path=TEX):
    """Parse the zexec blocks out of zapnotes.tex, with their enclosing \\zsection."""
    blocks = []
    section = None
    state = None          # None | "query" | "yields"
    cur = None
    with open(path, encoding="latin-1") as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.rstrip("\n")
            code = uncommented(line)
            m = SECTION_RE.search(code)
            if m:
                section = m.group(1).strip()
            if state is None:
                m = BEGIN_RE.search(code)
                if m:
                    kind = "expr" if m.group(1) == "zexecexpr" else "pred"
                    rest = code[m.end():]
                    cur = {"section": section, "kind": kind, "line": lineno,
                           "query_lines": [rest] if rest.strip() else [],
                           "yields_lines": []}
                    state = "query"
                continue
            if "\\yields" in code:
                state = "yields"
                continue
            if END_RE.search(code):
                blocks.append(cur)
                cur, state = None, None
                continue
            if state == "query":
                cur["query_lines"].append(code)
            else:
                # expectation text is verbatim material; take the raw line, not `code`
                cur["yields_lines"].append(line.rstrip())
    out = []
    for b in blocks:
        q = " ".join(" ".join(b["query_lines"]).replace("\\relax", " ").split())
        y = [ln.strip() for ln in b["yields_lines"]]
        while y and not y[0]:
            y.pop(0)
        while y and not y[-1]:
            y.pop()
        out.append({
            "id": "%s-L%d" % (b["section"], b["line"]),
            "section": b["section"],
            "unit": "%s#zapnotes" % b["section"],
            "kind": b["kind"],
            "line": b["line"],
            "query": q,
            "yields": y,
            "skip": SKIP_SECTIONS.get(b["section"]),
        })
    return out


def command_for(block):
    return "%s -u %s %s" % ("e" if block["kind"] == "expr" else "p",
                            block["unit"], block["query"])


def run_block(block, classes=None, timeout=180):
    """One fresh session per block (the isolation rule of baseline.py), normalised."""
    session = os.path.join(WORK, block["id"])
    shutil.rmtree(session, ignore_errors=True)
    os.makedirs(session)
    for f in os.listdir(SRC_DIR):
        if f.endswith((".tex", ".sty")):
            shutil.copy(os.path.join(SRC_DIR, f), session)
    cmd = [sys.executable, "-I", os.path.join(HERE, "zdrive.py"),
           "--cwd", session, "--timeout", str(timeout)]
    if classes:
        cmd += ["--classes", classes]
    cmd += ["load zapnotes.tex", command_for(block)]
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout * 3)
    if p.returncode != 0:
        # Same rule as baseline.run_session: a non-zero driver exit means the transcript is
        # truncated, and a truncated transcript compared against a recording looks exactly
        # like a regression in the animator.  Surface it as the harness's failure.
        print("    SESSION FAILED (driver exit %d) in %s -- transcript truncated, "
              "any difference is the harness, not the animator" % (p.returncode, block["id"]))
    return canonicalize.canon_transcript(p.stdout, zetabase=ZETABASE, session_dir=session)


def result_lines(transcript, block):
    """The `= ` payloads the block's own command produced (for the \\yields cross-check)."""
    marker = ">>> " + command_for(block)
    seen = False
    out = []
    for line in transcript.split("\n"):
        if line == marker:
            seen = True
            continue
        if not seen:
            continue
        if line.startswith("- "):
            break
        if line == "= good bye.":
            break
        if line.startswith("= "):
            out.append(line[2:].strip())
        elif line.startswith("! "):
            out.append(line)        # diagnostics are part of what \yields sometimes records
    return out


def runnable(blocks):
    return [b for b in blocks if not b["skip"]]


def do_list(blocks):
    for b in blocks:
        tag = "SKIP (%s)" % b["skip"] if b["skip"] else b["kind"]
        print("%-22s line %-5d %-5s %s" % (b["id"], b["line"], tag, b["query"][:70]))
    print("\n%d blocks, %d runnable" % (len(blocks), len(runnable(blocks))))


def do_run(blocks, args):
    recorded = unstable = 0
    for b in runnable(blocks):
        print("==> %s   %s" % (b["id"], command_for(b)))
        runs = [run_block(b, args.classes) for _ in range(max(1, args.runs))]
        if any(r != runs[0] for r in runs[1:]):
            unstable += 1
            print("    UNSTABLE across %d runs -- NOT recorded" % args.runs)
            continue
        d = os.path.join(TESTS, b["id"])
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "exp"), "w", encoding="latin-1") as f:
            f.write(runs[0])
        meta = {k: b[k] for k in ("section", "unit", "kind", "line", "query", "yields")}
        with open(os.path.join(d, "query.json"), "w") as f:
            json.dump(meta, f, indent=2)
        recorded += 1
    print("\n%d block(s) recorded, %d unstable" % (recorded, unstable))
    return 1 if unstable else 0


def do_check(blocks, args):
    differ = missing = same = 0
    for b in runnable(blocks):
        d = os.path.join(TESTS, b["id"])
        exp_path = os.path.join(d, "exp")
        if not os.path.exists(exp_path):
            missing += 1
            print("MISSING RECORDING  %s (run --run first)" % b["id"])
            continue
        got = run_block(b, args.classes)
        exp = open(exp_path, encoding="latin-1").read()
        if got == exp:
            same += 1
        else:
            differ += 1
            print("DIFFERS  %s" % b["id"])
            for g, e in zip(got.split("\n"), exp.split("\n")):
                if g != e:
                    print("    got: %s\n    exp: %s" % (g, e))
                    break
    print("\n%d of %d block(s) differ from the recording%s"
          % (differ, same + differ, (", %d missing" % missing) if missing else ""))
    return 1 if (differ or missing) else 0


def do_yields(blocks, args):
    """Historical cross-check against the 1999/2000 in-line expectations."""
    match = diff = 0
    for b in runnable(blocks):
        exp_path = os.path.join(TESTS, b["id"], "exp")
        if os.path.exists(exp_path) and not args.fresh:
            transcript = open(exp_path, encoding="latin-1").read()
        else:
            transcript = run_block(b, args.classes)
        got = result_lines(transcript, b)
        want = b["yields"]
        if got == want:
            match += 1
            print("MATCH  %-22s %s" % (b["id"], " | ".join(got)))
        else:
            diff += 1
            print("DIFF   %s" % b["id"])
            print("    1999 \\yields: %s" % (" | ".join(want) or "(empty)"))
            print("    now        : %s" % (" | ".join(got) or "(empty)"))
    print("\n%d of %d block(s) match their \\yields text (informational)"
          % (match, match + diff))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--run", action="store_true",
                    help="record; a block is kept only if all --runs runs agree")
    ap.add_argument("--check", action="store_true",
                    help="compare against the recordings; non-zero exit on any difference")
    ap.add_argument("--yields", action="store_true",
                    help="report agreement with the in-line 1999/2000 \\yields expectations")
    ap.add_argument("--fresh", action="store_true",
                    help="with --yields: run live instead of reading the recordings")
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--classes", default=None)
    args = ap.parse_args()
    blocks = extract_blocks()
    if args.list:
        return do_list(blocks)
    if args.run:
        return do_run(blocks, args)
    if args.check:
        return do_check(blocks, args)
    if args.yields:
        return do_yields(blocks, args)
    ap.error("choose --list, --run, --check or --yields")


if __name__ == "__main__":
    sys.exit(main())
