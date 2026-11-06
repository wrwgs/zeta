#!/usr/bin/env python3
"""Dump ZETA content types for a unit, via zdrive.py.

  zdump.py --dir <session-dir> --load <file.tex> --unit <UNIT> --out <prefix> [--types a,b,c]

Writes <prefix><type>.txt in the session directory for each content type, using ZETA's
`make -u <unit> <type> -d <file>` command.  Prints one line per type with its size.
"""
import argparse
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

ALL_TYPES = ["text", "lang-config", "latex-fragment",
             "unchecked-absy", "unchecked-env",
             "checked-absy", "checked-env", "nmz", "pretty-text"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True)
    ap.add_argument("--load", required=True)
    ap.add_argument("--unit", required=True)
    ap.add_argument("--out", default="dump_")
    ap.add_argument("--types", default=",".join(ALL_TYPES))
    ap.add_argument("--classes", default=None)
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    types = [t for t in args.types.split(",") if t]
    cmds = ["load " + args.load]
    for t in types:
        cmds.append("make -u %s %s -d %s%s.txt" % (args.unit, t, args.out, t))

    cmd = [sys.executable, "-I", os.path.join(HERE, "zdrive.py"), "--cwd", args.dir]
    if args.classes:
        cmd += ["--classes", args.classes]
    cmd += cmds
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=1800)
    if not args.quiet:
        for line in p.stdout.splitlines():
            if line.startswith("! ") or "TIMEOUT" in line:
                print("   " + line)

    missing = []
    for t in types:
        path = os.path.join(args.dir, "%s%s.txt" % (args.out, t))
        if os.path.exists(path):
            if not args.quiet:
                print("   %-16s %8d bytes" % (t, os.path.getsize(path)))
        else:
            missing.append(t)
    if missing:
        print("   not dumped: %s" % ", ".join(missing))
    return 0


if __name__ == "__main__":
    sys.exit(main())
