#!/bin/sh
# Regression checks for the Java 21 port of ZETA 1.5.
#
# The port is complete (2026-11-07), so this no longer compares against the 2002 build: its
# job was to prove the port behaves like 2002, which it did (111/111 reference artifacts
# throughout).  Retired with it: the reference oracle (baselines/), abi_gate.py and
# adt_tags.py, which guarded binary compatibility with still-running 2002 classes.  All of
# them are archived in archive/zeta-port-historic-2026-11-07.tar.gz (archive/README.md).
#
# What remains are regression checks against recordings of the ported system itself:
#
#   ported      all nine content types over 19 units (baselines-ported/, 175 artifacts)
#   targeted    the type checker's error paths, one minimal spec per diagnostic (17)
#   mutation    mutants of the corpus, pinning diagnostics and their order (53)
#   animation   the 13 zexec blocks of zapnotes.tex -- the only check that runs the ZAM
#
# A deliberate change in output is re-recorded, not canonicalised away:
#   baseline.py --update --all-types --classes classes --baselines baselines-ported
#   targeted.py --run --classes classes ; negtest.py --run --classes classes
#   zapexec.py --run --classes classes
# and the recording diff is reviewed.
#
# Do NOT run build.sh while this is running: the build wipes classes, and a session
# that loses its classpath mid-run fails in ways that look like a regression.
set -e
ZETA=$(cd "$(dirname "$0")/.." && pwd)
export ZETA_JAVA=${ZETA_JAVA:-$ZETA/toolchain/jdk21/bin/java}
CP="$ZETA/classes"

echo "=== ported oracle (all nine content types)"
python3 -I "$ZETA/harness/baseline.py" --check --all-types --classes "$CP" \
    --baselines "$ZETA/baselines-ported" | grep -E "artifacts:|DIFFERS|MISSING"

echo "=== targeted negative suite"
python3 -I "$ZETA/harness/targeted.py" --check --classes "$CP" | tail -1

echo "=== mutation suite"
python3 -I "$ZETA/harness/negtest.py" --check --classes "$CP" | tail -1

echo "=== animation oracle"
python3 -I "$ZETA/harness/zapexec.py" --check --classes "$CP" | tail -1
