#!/bin/sh
# Regenerate the LaTeX scanner of zeta.tools.latex from its JavaCC grammar.
#
#   harness/gen-parser-latex.sh
#
# Input:  grammar/zeta/tools/latex/LatexScanner.jj
# Output: src/zeta/tools/latex/{LatexScanner,LatexScannerTokenManager,
#         LatexScannerConstants,Token,ParseException,TokenMgrError,CharStream}.java
#
# Uses JavaCC 3.2, the version the project already builds the grammars with
# (zesurrect-plan.md 3.1).  The generated files are kept as source and must not be edited by
# hand; change the grammar and rerun this script.  Idempotent: generation happens in a fresh
# temporary directory and the seven files are then copied over, so stale helper files from
# a previous run (JavaCC does not overwrite existing helpers) can never survive.
set -e
ZETA=$(cd "$(dirname "$0")/.." && pwd)
JAVA=$ZETA/toolchain/jdk21/bin/java
JAVACC=$ZETA/toolchain/javacc/javacc-3.2.jar
GRAMMAR=$ZETA/grammar/zeta/tools/latex/LatexScanner.jj
OUT=$ZETA/src/zeta/tools/latex

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

"$JAVA" -cp "$JAVACC" org.javacc.parser.Main -OUTPUT_DIRECTORY="$TMP" "$GRAMMAR" \
    > "$TMP/javacc.log" 2>&1 || { cat "$TMP/javacc.log"; exit 1; }
grep -q 'Parser generated successfully' "$TMP/javacc.log" || { cat "$TMP/javacc.log"; exit 1; }
# JavaCC's warnings (if any) are worth seeing on every regeneration.
grep -i 'warning' "$TMP/javacc.log" || true

mkdir -p "$OUT"
for f in LatexScanner LatexScannerTokenManager LatexScannerConstants Token \
         ParseException TokenMgrError CharStream; do
    [ -f "$TMP/$f.java" ] || { echo "missing generated $f.java"; exit 1; }
    cp "$TMP/$f.java" "$OUT/$f.java"
done
echo "generated 7 file(s) into $OUT"
