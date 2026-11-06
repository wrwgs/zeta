#!/bin/sh
# Compile the whole ported tree from scratch.
#
# Two things here are deliberate.
#
# 1. classes/ is wiped first.  Ported classes sit AHEAD of the 2002 reference on the
#    runtime classpath (zesurrect-plan.md 9.1), so a stale .class left behind by a rename or a
#    deletion does not merely linger -- it shadows the reference class it was meant to replace,
#    and the oracle passes for the wrong reason.
#
# 2. Every ported source is compiled in ONE javac invocation.  Listed sources take precedence
#    over the classpath, which is what lets a ported file refer to `Enum<A>`: the 2002
#    `zeta.agl.Enum` carries no Signature attribute, so resolving that name to the reference
#    class fails with "type Enum does not take parameters".  Classpath order alone does not
#    fix this -- the sources have to be in the same compilation.
set -e
ZETA=$(cd "$(dirname "$0")/.." && pwd)
JAVAC=$ZETA/toolchain/jdk21/bin/javac
rm -rf "$ZETA/classes"
mkdir -p "$ZETA/classes"
find "$ZETA/src" -name '*.java' -print > "$ZETA/classes/sources.txt"
# 3. No classpath (2026-11-07).  The port is complete and the Pizza runtime is gone, so the
#    tree must compile on its own; putting the 2002 classes on the classpath would let a stray
#    reference to one of them compile silently.
"$JAVAC" -nowarn -encoding latin1 \
    -d "$ZETA/classes" \
    @"$ZETA/classes/sources.txt"
echo "compiled $(wc -l < "$ZETA/classes/sources.txt") file(s)"
