#!/bin/sh
# Build libZAM.so for aarch64 Linux with g++ 14.
#
# Replaces the 1998 ETMakefile (ET build tool, long dead) and the VC6 NMAKE
# MAKEFILE. Same 11 translation units, same order, same -g -Wall as the
# ETMakefile shipped with (its -O3 line was already commented out there, and
# we keep -O0 deliberately -- see NOTES.md, "Optimisation level").
#
# Usage:  ./build.sh            build objects + libZAM.so
#         ./build.sh sizes      additionally build+run the layout probe
#
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
cd "$HERE"

JDK=${JDK:-/home/wrw/zeta/toolchain/jdk21}
[ -f "$JDK/include/jni.h" ] || { echo "no jni.h under $JDK/include" >&2; exit 1; }

BUILD=build
mkdir -p "$BUILD"

# From ETMakefile: CFILES, in its order. zamutil.cpp is effectively empty but
# is a real TU and is kept for fidelity.
TUS="zamutil session zammem print loader natives data admin unify interp javadriver"

INC="-I. -I$JDK/include -I$JDK/include/linux"

# -fPIC            : shared library
# -g -O0           : as ETMakefile (CCFLAGS = -g -Wall); -O0 avoids adding
#                    strict-aliasing and alignment assumptions on top of a
#                    code base full of char*/void* punning (see NOTES.md).
# -Wall -Wextra    : we want the narrowing/sign warnings visible, not -w
# no -Werror       : the 1998 code warns a lot and that is expected
CXXFLAGS=${CXXFLAGS:-"-fPIC -g -O0 -Wall -Wextra"}

echo "== compiling (${CXXFLAGS})"
fail=0
for f in $TUS; do
  if g++ -c $CXXFLAGS $INC -o "$BUILD/$f.o" "$f.cpp" 2> "$BUILD/$f.log"; then
    printf 'ok   %-11s %s warnings\n' "$f" "$(grep -c 'warning:' "$BUILD/$f.log" || true)"
  else
    printf 'FAIL %-11s see %s\n' "$f" "$BUILD/$f.log"
    fail=1
  fi
done
[ "$fail" = 0 ] || { echo "compile failed" >&2; exit 1; }

# -Wl,--no-undefined is essential: plain `g++ -shared` happily produces a
# .so with unresolved symbols, so a link without it proves nothing.
echo "== linking libZAM.so"
g++ -shared -fPIC -o libZAM.so "$BUILD"/*.o -Wl,--no-undefined

echo "== exported JNI entry points"
n=$(nm -D --defined-only libZAM.so | grep -c ' T Java_zeta_tools_nzap_ZamJniIface_')
nm -D --defined-only libZAM.so | sed -n 's/.* T \(Java_zeta.*\)/  \1/p'
echo "   $n of 13 natives declared in zeta_tools_nzap_ZamJniIface.h"
[ "$n" = 13 ] || { echo "WRONG NUMBER OF JNI ENTRY POINTS" >&2; exit 1; }

echo "== done: $HERE/libZAM.so"
file libZAM.so

if [ "${1:-}" = sizes ]; then
  echo "== layout probe (diagnostic, not part of the library)"
  g++ -O0 -w $INC -o "$BUILD/sizes" sizes.cpp \
      "$BUILD"/zamutil.o "$BUILD"/session.o "$BUILD"/zammem.o "$BUILD"/print.o \
      "$BUILD"/loader.o "$BUILD"/natives.o "$BUILD"/data.o "$BUILD"/admin.o \
      "$BUILD"/unify.o "$BUILD"/interp.o
  "$BUILD/sizes"
fi

# Verify the checked-in 1998 JNI header still matches the compiled Java class:
#   javap -s -p -constants -cp <classes> zeta.tools.nzap.ZamJniIface
# See NOTES.md, "JNI header". Not run here because it needs work/ref-classes.
