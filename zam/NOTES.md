# ZAM port to aarch64 Linux / g++ 14.2

Working copy of `recovered/src/zeta-1.5/src/tools/nzap/zam/`, which is left
untouched. Build with `./build.sh`.

Status, stated narrowly:

| | |
|---|---|
| translation units compiling to real `.o` | **11 / 11** |
| `libZAM.so` links (`g++ -shared -fPIC -Wl,--no-undefined`) | **yes** |
| JNI entry points exported | **13 / 13**, matching the header |
| risks #1-#3 (GC_ALIGN, 32-bit number compare, address-derived order) | **fixed — section 1b** |
| library loads under a JVM (`+ installed ZAP/ZAM v2.1`, jdk8 and jdk21) | **yes** |
| ZAM executes animations | **yes — 13 zapnotes blocks, reproducible (3-run agreement), 8/13 match the 1999/2000 `\yields` literally; `harness/zapexec.py`** |

Seven source files differ from `recovered/`: `zammem.h`, `zamutil.h`,
`interp.cpp` (the compile fixes below), and — second pass, 2026-11-06 —
`zam.h`, `loader.cpp`, `unify.cpp`, `natives.cpp`, `javadriver.cpp` (the
pre-run fixes of section 1b). New files: `build.sh`, `NOTES.md`, `sizes.cpp`
(diagnostic only, not linked into the library).

---

## 1. Changes made

Line numbers below are in `zam/`; because the fixes add comments, they
sit a few lines later than in `recovered/`.

The four categories are exactly the four that were briefed. One number differs:
`g++ -c -fsyntax-only -w` over the 11 TUs gave me **141** errors, not 157 —
108 `next`/`prev` (63 + 45), 22 `GC`, 8 `uninitialized_copy` (6 + 2), 3 pointer
casts. No fifth category appeared, so the gap is a counting difference, not a
missed problem. The 108/22/8/3 split is reproduced exactly.

### (a) Dependent-base-class lookup — `next` / `prev` (108 errors)

`zamutil.h:157` declares `template <class T> struct Ring : public RLink<T>`
and the body then refers to the inherited `RLink<T>::next` / `::prev`
unqualified. `RLink<T>` is a *dependent* base, so ISO C++ two-phase lookup
binds the bare name at template **definition** time, where the base is not yet
known — and finds `std::next` / `std::prev` instead (pulled in by
`<iterator>`). gcc 2.95 and VC6 deferred the lookup to instantiation.

Fixed by writing `this->next` / `this->prev` at 15 sites: lines 158, 174, 200,
205, 206, 210, 211, 221, 222, 251, 255, 260, 263, 267, 293. The explanatory
comment is at `zamutil.h:149-155`.

Member accesses through an object (`x.next`, `l->next`, `ring->next`,
`_info.root->next`, `r->next`) were already fine and were left alone. The one
bare `next` inside the commented-out `push`/`clear` block (`zamutil.h:287-296`,
the use itself at line 293) was deliberately *not* touched, to keep dead code
byte-identical to 1998.

The generated code is unchanged: this is purely a name-lookup fix.

GCC reports a name like this only once per function scope, so the first
`-fsyntax-only` pass under-reported the sites; four more (174, 211, 222, 263)
surfaced only after the first wave was fixed.

### (b) `enum GCPlacement { GC }` declared after use (22 errors)

`GcHeap::collect<T>()` (`zammem.h:449`) and `GcHeap::gc<T>()` (`zammem.h:508`)
use the enumerator `GC` as a placement tag, but the enum was declared at the
old line 509 — *after* `struct GcHeap`. `GC` is a non-dependent name, so ISO
C++ requires it to be visible at template definition time.

The three declarations

```c++
enum GCPlacement { GC };
enum NCPlacement { NC };
enum TMPlacement { TM };
```

were moved verbatim to `zammem.h:253-255`, immediately above `struct GcHeap`
(now at line 261), with a note left behind at the old site (`zammem.h:526-529`). The
`operator new` / `operator delete` placement overloads stayed where they were —
they need `GcHeap` to be complete.

### (c) `std::uninitialized_copy` not declared (8 errors)

Two distinct causes:

* 6 errors in `zamutil.h` (lines 398, 415, 432, 505, 522, 539) — qualified
  `std::uninitialized_copy` with no `<memory>` include. Added
  `#include <memory>` at `zamutil.h:14`. The 1998 libstdc++ dragged it in
  transitively via `<algorithm>`/`<iterator>`.
* 2 errors in `interp.cpp` (now lines 319 and 381) — *unqualified*
  `uninitialized_copy`, with no `using namespace std;` in that TU. The 1998
  libstdc++ still exported the `<memory>` algorithms into the global
  namespace. Qualified both with `std::`; comment at `interp.cpp:316-318`.

### (d) `void*` → `unsigned` loses precision (3 errors)

All three are pointer hashes: `MemoApplTab::keyHash` (`interp.cpp:586`, casts
now at lines 596 and 609) and `MemoHomTab::keyHash` (`interp.cpp:642`, cast at
645). Rewritten as

```c++
static_cast<unsigned>(reinterpret_cast<uintptr_t>(static_cast<void*>(p)))
```

with `#include <cstdint>` added at `interp.cpp:17`, next to the casts that need
it. This discards the high 32 bits of the address, so it costs hash
**distribution**, not correctness: `keyEquals` still decides membership, and
store and lookup truncate identically. See risk #5 for the caveat.

These were the only pointer→integer casts in the whole body. That is worth
stating plainly, because it is the one piece of genuinely good news in this
port: the tagged-memory core does **not** pack tags into pointer bits. `kind`
and `ground` are ordinary struct fields (`zam.h:366-367`), and the GC uses an
explicit `forward` pointer plus an `int generation` rather than bit-stealing.
A WAM-style machine that stole low bits would have been a far worse port.

### What was *not* changed, deliberately

* `GC_ALIGN 1` (`zammem.h:21`) — see risk #1. The one-line fix is obvious, but
  it changes allocator behaviour and is outside the brief.
* `natives.h:79 long val` — see risk #3.
* Every other item in section 3.

---

## 1b. Pre-run fixes (2026-11-06): risks #1-#3 closed, plus one threading bug

Applied before first execution, per the exit criteria in `zesurrect-plan.md` §4.
Each site carries a `PORT CHANGE` comment with the full argument.

* **Risk #1, `GC_ALIGN` 1 → 8** (`zammem.h`). Strictly conservative: requests
  are only ever rounded up. Verified safe against the one hazard that would
  make it not so — nothing walks a page by stored object size (allocation is
  bump-only via `fill`; the GC traverses root rings and `forward` pointers;
  page accounting uses `fill`/`size` only). `./build.sh sizes` now shows the
  third object at `% 8 == 0` where it demonstrated `% 8 == 5` before.
* **Risk #3, `NativeNum::unifyWith`** (`natives.cpp`). Was
  `int c = val - other->val` on 64-bit `long`: values differing by a non-zero
  multiple of 2^32 unified. Now compares directly and throws sign -1/1 —
  avoiding both the truncation and the subtraction's own signed overflow
  (`LONG_MIN - 1`). Checked every consumer of `UnequalFailure.order` first:
  `data.cpp` (`compare`, qsort, `removeDups`, `sunion`/`sisect` at 424/454),
  `zamutil.h` `Ring::enqueue`, and the three `f.order = -f.order` swaps in
  `unify.cpp` — all sign-only, and -1/1 negates without overflow.
* **Risk #2, the set-extension ordering key** (`zam.h`, `loader.cpp`,
  `unify.cpp`). `ConstructorData` now carries a creation-order `index`,
  assigned in `Loader::lookupConstructor` (the only creation site, from
  `constructors.size()`), and `unify.cpp` throws
  `UnequalFailure(index1 < index2 ? -1 : 1)` instead of the pointer
  difference. Design argument: on i386 the NC space was bump-allocated in
  load order with no ASLR, so pointer order *was* creation order — the index
  reproduces the 2002 reference's set ordering exactly, which a lexicographic
  name order would not (and the `\yields` blocks in `zapnotes.tex` are
  recorded against that order). Constructors are interned by name, so
  distinct pointers imply distinct indices and the order is total.
  `sizeof(ConstructorData)` stays 16: the `int` landed in former tail padding
  (re-measured).
* **Not a 64-bit item, found on review: the cached `JNIEnv*`**
  (`javadriver.cpp`). `start()` stored its `JNIEnv*` in a static and the
  trace/error receivers used it from whatever thread later called `step()` —
  but a `JNIEnv*` is per-thread, and ZETA's shell runs each command on a
  fresh thread, so any progress message during stepping went through a stale
  env: undefined behaviour under native threads (1998 green-threads VMs
  masked it). Now caches the process-wide `JavaVM*` and calls `GetEnv` per
  callback. (`self` was already a `NewGlobalRef`, which is correct.)

Deliberately *not* changed, and now recorded as semantics rather than risk:
Z integer width. `long val` is 64-bit on LP64, so Z arithmetic wraps at 2^63
instead of 2^31 and `atol` parses wider literals — a semantic change to the
specification language relative to the i386 build, invisible to any oracle
recorded on this machine. Emulating 32-bit wrap would preserve a 1998
platform accident, not a design; left as-is, documented here.

## 2. JNI header: the checked-in 1998 header is still correct

`javah` is gone from JDK 10+, and there is no compilable Java for
`ZamJniIface`, so the header was verified rather than regenerated — and then
regenerated anyway as a cross-check, soundly:

1. `jdk21/bin/javap -s -p -constants -cp work/ref-classes
   zeta.tools.nzap.ZamJniIface` gives 13 `native` methods, all **instance**
   (non-static), in the order `start, step, possiblyMore, getBinding,
   setSetTarget, setTermTarget, setSeqTarget, retSubTarget, isSet, isSeqGet,
   isTermGet, profileInfo, noOfSteps`, with descriptors
   `([Ljava/lang/String;)V`, `(I)I`, `()Z`, `(Ljava/lang/String;)Ljava/lang/String;`,
   `(Ljava/lang/String;)Ljava/lang/String;`, `(Ljava/lang/String;I)Ljava/lang/String;`,
   `(Ljava/lang/String;I)Ljava/lang/String;`, `()V`, `(Ljava/lang/String;)Z`,
   `(Ljava/lang/String;)I`, `(Ljava/lang/String;)Ljava/lang/String;`,
   `()Ljava/lang/String;`, `()J`. These match the 13 declarations in
   `zeta_tools_nzap_ZamJniIface.h` one for one, including `jobject` (not
   `jclass`) as the second parameter.
2. Mangling needs no escapes here: no `_` in `zeta.tools.nzap.ZamJniIface` or
   in any method name, so no `_1`; no duplicate native names, so no `__`
   signature suffix. `Java_zeta_tools_nzap_ZamJniIface_<method>` is right.
3. The four constants come from the superclass `ZamIface`:
   `SOLUTION=0, MORE=1, NOMORE=2, UNDEF=3` — exactly the four `#define`s in
   the header. `zamToolName` is `private static` and `impl` is a
   reference-typed static, which is why 1998 `javah` emitted them as
   `/* Inaccessible static: ... */` comments.
4. Cross-check: a stub `.java` with the same package, class name and native
   declarations transcribed from `javap` (native names + descriptors are the
   only inputs to header generation) was run through `javac -h`. The result is
   **byte-identical** to the checked-in header apart from the two
   `/* Inaccessible static: ... */` comment lines, which modern `javac -h` no
   longer emits.

**Conclusion: do not regenerate.** The 1998 header is correct for the compiled
class, and `javadriver.cpp:13` includes it (checked), so any signature drift
between the 13 declarations and the 13 definitions would have been
a compile error — which it was not. `noOfSteps` is the one place where LP64
*helps*: `interp.h:117` returns `long`, which on i386 gave the Java `()J`
contract only 32 bits of counter and now gives it all 64.

---

## 3. Where 64-bit assumptions are still hiding

Compiling and linking proves almost nothing here. What follows is ordered by
how badly it can go wrong *silently*. Items #1-#3 are the ones I would chase
before trusting a single animation result: #1 is demonstrated below, #3 is a
newly reachable wrong-answer bug, and #2 is a reproducibility problem that a
differential harness will hit on its first run.

### #1 `GC_ALIGN 1` — the allocator produces misaligned objects. Demonstrated.

```c++
zammem.h:21     #define GC_ALIGN        1
zammem.h:188      osize = ((osize + GC_ALIGN - 1)/GC_ALIGN)*GC_ALIGN;   // alloc
zammem.h:204      osize = ((osize + GC_ALIGN - 1)/GC_ALIGN)*GC_ALIGN;   // allocArray
zammem.h:219      osize = ((osize + GC_ALIGN - 1)/GC_ALIGN)*GC_ALIGN;   // allocTemp
```

All three allocators are bump allocators over `calloc`'d 1 MB pages
(`zammem.cpp:29`) and round the request up to a *1-byte* boundary — i.e. not at
all. Alignment therefore holds only as long as every single request is a
multiple of 8. Every class type satisfies that (measured — section 4). Odd-sized
`char` arrays do not:

```c++
loader.cpp:152    char *buf = new (NC) char[l+1];                 // load time
natives.cpp:1211  char *r = new (GC) char[n+1];                   // gcstrdup
natives.cpp:1219  char *r = new (TM) char[n+1];                   // tmstrdup
natives.cpp:1227  char *r = new (NC) char[n+1];                   // ncstrdup
print.cpp:923     char* newname = new (GC) char[n+2];
natives.h:64      val = GcHeap::collectArrPrim(strlen(x.val)+1, x.val);
zammem.h:487      GcArrayObject* n = from->allocArray(size * sizeof(T));  // T = char
```

I confirmed this empirically rather than by inspection — `./build.sh sizes`
reproduces it. Allocating 24 bytes, then a 5-byte array, then 24 bytes again:

```
obj1 0xffff9459f010  % 8 = 0
str5 0xffff9459f038  % 8 = 0
obj2 0xffff9459f03d  % 8 = 5   <-- misaligned
```

From that point on *every* object in the page is misaligned, including its
vtable pointer and its `GcObject* forward` field. Note `natives.h:64`: the GC
itself re-creates the condition in to-space on every collection that copies a
`NativeDeno`, so this is not confined to load time.

On i386 the same arithmetic produced the same misalignment, but x86 tolerates
unaligned loads in hardware and gcc 2.95 did essentially no alignment-dependent
optimisation, so it never showed. Being honest about the aarch64 severity:
ARMv8 also permits unaligned `LDR`/`STR`/`LDP`/`STP` to normal memory, so this
will probably *appear* to work. It is nevertheless undefined behaviour, and the
concrete ways it bites are (a) `LDXR`/`STXR`/`LDADD` — any atomic, including
anything libstdc++ does under the hood — fault with **SIGBUS** on a misaligned
address, and (b) at `-O2` GCC is entitled to assume a `T*` is `alignof(T)`-aligned
and to vectorise on that basis. A SIGBUS deep inside a GC copy loop is exactly
the kind of failure that will look like heap corruption rather than an
alignment bug.

The fix is one line, `#define GC_ALIGN 8`, and it is strictly conservative:
it only ever rounds requests up. I did not apply it because it changes
allocator behaviour and is outside the brief, but I would apply it before
running anything.

### #2 Pointer difference used as an ordering key, truncated to `int`

```c++
unify.cpp:63            throw UnequalFailure(term1->cons - term2->cons);
zam.h:150-152    struct UnequalFailure : public Failure { int order; ... }
```

`Constructor` is `ConstructorData*` (`zam.h:47`), and `ConstructorData` objects
are allocated one at a time (`loader.cpp:56  new (NC) ConstructorData(...)`),
so this is a genuine pointer difference, `ptrdiff_t`, implicitly narrowed to
`int`. Clang flags it: `unify.cpp:63:41: implicit conversion loses integer
precision: 'long' to 'int'`.

**The headline problem is not the truncation, it is that the order is
address-derived at all.** `order` is the sort key for set extensions. On i386
with no ASLR to speak of and a single `fixed` space filled in load order, the
resulting order was stable across runs. On aarch64 Linux the pages come from
independent 1 MB `calloc`s above glibc's `mmap` threshold, placed by the kernel
with ASLR, so the *relative* order of two `ConstructorData` objects in
different pages can differ from run to run. Everything downstream inherits
that: the order of elements inside a printed set, and therefore `getBinding`
output, is now **run-to-run nondeterministic**, and will not match an i386
reference in any case. That is precisely what a differential harness will see
first, and no amount of widening the type fixes it — the order needs to come
from something stable, such as the constructor's load index.

Secondary, and much rarer: the narrowing itself. `ptrdiff_t` here counts
`ConstructorData` *elements*, which measure 16 bytes (section 4), so flipping
the sign needs two constructors |2^31| elements apart — about **32 GiB** of
address distance — and landing exactly on 0 needs a multiple of 64 GiB.
Adjacent `mmap`'d pages in one process do not do that. So this is unbounded in
principle and effectively unreachable in practice; I am not going to claim
otherwise. Clang does flag it: `unify.cpp:63:41: implicit conversion loses
integer precision: 'long' to 'int'`.

Either way, `order` flows into:

```c++
data.cpp:13-20   int compare(Value v1, Value v2){ try { Unify::equal(v1,v2); return 0; }
                                                  catch (UnequalFailure& f){ return f.order; } ... }
data.cpp:84-85     int c = compare(last, v);
                   if (c != 0) res.push(v);        // removeDups
data.cpp:54,61     int c = compare(values[++i], pivot);   // qsort over set extensions
data.cpp:485,500   sunion(...) / sisect(...)              // set union / intersection
zamutil.h:265,274  int c = compare(this->prev->getBase(), tx);  // Ring::enqueue
```

So a nondeterministic sign makes `compare` disagree with itself between runs,
and `qsort`, `sunion`, `sisect` and `removeDups` all consume that. `removeDups`
in particular drops an element whenever `compare` returns 0, and `sunion` /
`sisect` assume both inputs are sorted by the *same* order — if two sets were
sorted in different runs, or one came from a memo table built earlier, the
merge silently produces a wrong set.

(Equality itself is unaffected: `unify.cpp:57` already established
`term1->cons != term2->cons` by pointer comparison before the difference is
taken. Only the *ordering* is corrupted — but `compare() == 0` is read as
equality by `removeDups`.)

### #3 Z integer width silently doubled, and the comparison truncates

```c++
natives.h:79      long val;
natives.h:80      inline NativeNum(long _val) : OtherData(true), val(_val){}
natives.cpp:174   return new (GC) NativeNum(atol(deno->val));
```

`long` was 32 bits on i386 and is 64 bits on LP64. Z integer arithmetic
(`natives.cpp:181,187,193,200,210` — add, sub, mul, div, mod) therefore now
wraps at 2^63 instead of 2^31, and `atol` now parses literals an i386 build
would have saturated. That is a **semantic change to the specification
language**, not a bug in the usual sense: a differential harness comparing
against i386 output would see it, nothing else would.

Worse, the comparison was not widened with it:

```c++
natives.cpp:133   int c = val - getNativeNum(other)->val;
natives.cpp:134-5 if (c != 0) throw UnequalFailure(c);
```

This is `NativeNum::unifyWith`, i.e. the function that decides whether two Z
numbers are equal. `long - long` is now 64-bit and is truncated to `int`.
Any two numbers differing by a non-zero multiple of 2^32 give `c == 0` and
**unify successfully** — `x = 4294967296` would satisfy `x = 0`. On i386 this
was unreachable by construction, because `long` could not hold such a
difference. It is reachable now, and only now.

The same widening shows up at `natives.cpp:356 long inx = ...->val` (sequence
index, bounds-checked only against `< 1`, so a huge index becomes a long walk
rather than an error) and at `natives.cpp:1193 (int)num->val`.

### #4 `size_t` offset truncated to `int` inside GC ring relocation

```c++
zamutil.h:45      size_t offset; // FIXME: const, also copy method
zamutil.h:77                           : -1),
zamutil.h:167       int offset = l->offset;
zamutil.h:175         static_cast<char*>(static_cast<void*>(y)) + offset
```

`RLink<T>::offset` is the byte distance from the embedded link to its enclosing
object, used by `getBase()` / `getLink()` to recover the base pointer. Two
things:

* `Ring<T>::collect` (`zamutil.h:167`) copies it into an `int` and then adds it
  back to a `char*`. Benign in practice — real offsets are tens of bytes — but
  it is a silent 64→32 narrowing on the GC's pointer-recovery path, which is
  not where you want one.
* `zamutil.h:74-78` stores `-1` into the `size_t` as a sentinel for "this is a
  ring head, not an element". That is `0xFFFFFFFF` on i386 and
  `0xFFFFFFFFFFFFFFFF` here. Nothing compares against `-1`; the sentinel is
  safe only because `getBase()` is never called on a ring head. There is no
  assertion enforcing that.

### #5 Hash truncation — mine, and the pre-existing `unsigned`→`int`

My fix at `interp.cpp:596,609,645` keeps the low 32 bits of the address. I
deliberately do *not* claim an entropy figure for that: it would be tempting to
argue the low 3 bits are always zero because objects are 8-aligned, but risk #1
says they are not — `d->cons` lives in the fixed space, which also serves
`new (NC) char[l+1]` from `loader.cpp:152`, so `ConstructorData` pointers can
sit at any byte offset. Low 32 bits of a bump-allocated address is adequate
distribution for the table sizes in use, and that is all that can be said.

The hash is then stored and compared as a *signed* `int`:

```c++
zamutil.h:1107    int hash;                       // CacheEntry
zamutil.h:1179    int hash = keyHash(key);        // from a function returning unsigned
zamutil.h:1152,1169,1180    int index = (hash & 0x7FFFFFFF) % _size;
```

Consistent on both store and lookup, so this is a distribution and
memoisation-hit-rate concern only, not correctness. Mentioned because it means
the memo tables are not an alignment-free zone and a performance regression
here would be easy to misread as a logic change.

### #6 Heap budget constants, and a disabled out-of-memory check

```c++
zammem.h:13-15    #define GC_MINHEAPSIZE  (32 * 1024l * 1024)
                  #define GC_MAXHEAPSIZE  (96 * 1024 * 1024)
                  #define GC_MAXSPACESIZE (16 * 1024 * 1024)
zammem.h:151      static unsigned totalAlloc;
zammem.h:166      int size;                                  // GcSpace
zammem.cpp:28     // if (totalAlloc+csize > GC_MAXHEAPSIZE) Session::outOfMemory(totalAlloc);
zammem.cpp:147    int before = GcSpace::totalAlloc;
zammem.cpp:184    int max = GcSpace::totalAlloc;
```

These are byte budgets, tuned in 2000 against i386 object sizes. Pointer-dense
objects roughly doubled (section 4: `ThreadData` 264 bytes, `GoalData` 216,
`ChoiceData` 176, `VarData` 96), so the same specification now needs close to
twice the heap. The GC triggering thresholds, which are absolute byte counts,
did not move — so collection frequency and generation promotion behaviour have
changed even for a spec that still fits. And the explicit out-of-memory check
is commented out at `zammem.cpp:28`, so exhaustion arrives as `calloc`
returning 0 and `Session::assertFailed("out of virtual memory")`, not as the
intended `Session::outOfMemory` path.

`unsigned totalAlloc` assigned into `int before` / `int max` would break past
2 GB; unreachable at these budgets, but it is there.

### #7 `int`-typed byte sizes in the allocator interface

```c++
zammem.h:318,323,328,337   static inline void* gcAlloc(int size) ... noGcAllocArray(int size)
zammem.h:356               static inline void* tempAlloc(int gen, int size)
zammem.h:187,202,218       inline GcObject* alloc(unsigned osize) ...
```

The placement `operator new`s take `size_t` and narrow it to `int`/`unsigned`
on the way in (clang flags all six: `zammem.h:586,593,601,608,615,622`). Not
reachable with real object sizes, but it means a bad array length cannot be
caught — it wraps.

### #8 Checked and judged benign

**Hard-coded word sizes in the binary unit reader: none.** This is the category
I expected to find trouble in and did not. `Loader::readInt`
(`loader.cpp:125-140`) decodes a variable-length 7-bits-per-byte integer with
`getc`, terminated by a high bit:

```c++
loader.cpp:126    unsigned int s = 0;
loader.cpp:128      int c = getc(f);
loader.cpp:135      if (c >= 128){
loader.cpp:136        return (s << 7) + (c % 128);
loader.cpp:137      } else
loader.cpp:138        s = (s << 7) + c;
```

No `fread` of a struct, no `sizeof` in the file format, no endianness
assumption anywhere in `loader.cpp`. The same decoder appears at `data.h:15-24`
for the instruction stream and `print.cpp:678-684` for disassembly, and
`Instr::Unit` is `unsigned char` (`zam.h:119`), so the bytecode is byte-addressed
throughout. The on-disk format is word-size independent and the Java writer
needs no change.

**Pointer difference, bounded:**

```c++
print.cpp:670     int step = pc - thread->inten->schema->unit->code;
```

Difference within a single `Instr::Unit` code array, bounded by the code size
of one unit (itself read by `readInt`), so safe; listed only for completeness.

Likewise benign (`strlen`/`std::vector::size()`/`std::map` sizes narrowed to
`int`): `natives.h:64`, `natives.cpp:690,1210,1218,1226,1249,1268,1566`,
`print.cpp:922`, `loader.cpp:43,250`, `javadriver.cpp:114`,
`data.cpp:414,444,445`.

### #9 Not 64-bit, but load-bearing and fragile

* The hash tables are placement-new'd as a *single* pointer over an
  array-sized block, which default-initialises nothing:
  `zamutil.h:1138,1147,1214  new (_space->allocTemp(_size * sizeof(Entry*))) Entry*;`
  The table is only all-zero because `GcSpace::reserve` uses `calloc`
  (`zammem.cpp:29`) and because `GcSpace::release()` — which would reset
  `fill` and hand back dirty memory — is commented out (`zammem.cpp:55-67`).
  Switching that `calloc` to `malloc`, as the adjacent
  `// CHECKME char* base = ... malloc(csize)` invites, would corrupt every
  memo table.
* Global `operator new`/`delete` are *replaced* by `inline` functions in a
  header (`zammem.h:567-581`). GCC 14 warns that the matching sized
  `operator delete(void*, size_t)` is absent (11×). Currently consistent,
  because libstdc++'s sized delete forwards to the unsized one.
* `GcHeap::kb()` (`zammem.h:300-315`) returns `std::ostrstream::str()` from a
  local stream — a frozen, never-released buffer. Leaks once per GC trace
  line. `<strstream>` is deprecated-but-present in libstdc++ 14; the 11
  `backward_warning.h` warnings are this.
* Three × `-Wreturn-type` "control reaches end of non-void function", all in
  the Z-value text parser that backs `getBinding`/`setTermTarget`:
  `natives.cpp:1366` (`Scanner::getToken`), `natives.cpp:1432`
  (`FreeTypeParser::parse`), `natives.cpp:1450` (`SetParser::parse`). Each
  falls off the end after a call to `Scanner::error`, which always throws
  (`natives.cpp:1321-1327`), so the path is unreachable. But `error` is not
  marked `[[noreturn]]`, so GCC cannot prove it and does emit the
  fall-through, which on aarch64 would return whatever is in `x0` as a
  `Value`. Harmless as written; one `[[noreturn]]` would remove the doubt.

### Optimisation level

`build.sh` uses `-O0`, matching the ETMakefile (whose `-O3` line was already
commented out in 2000). This is deliberate: raising it to `-O2` adds *two*
independent hazards on top of everything above — strict aliasing, against a
code base that punts every pointer through `char*`/`void*`
(`zammem.h:425-440`, `zamutil.h:50-65,173-178`, and the
`static_cast<GcArrayObject*>(...) - 1` header trick throughout), and
alignment-based vectorisation, against the misalignment in #1. gcc 2.95 did
neither. If `-O2` is ever wanted, it should be `-O2 -fno-strict-aliasing`, and
only after `GC_ALIGN` is fixed.

---

## 4. Measured aarch64 layout

From `./build.sh sizes` (`sizes.cpp`, diagnostic only). `void*`=8, `long`=8,
`int`=4, `size_t`=8, `ptrdiff_t`=8:

```
GcObject        24   GcArrayObject   16   GcRootLink      16   GcRoot          32
GcRootObject    40   GcForwardInfo    8   GcWeakObject    32   GcPage          32
GcSpace         56   RLink<ValueData> 48  Ring<ThreadData> 56
ValueData       32   TermData        48   VarData         96   SetData         72
ConstructorData 16   ConstrData      40   SchemaData      72   GlobalData      16
IntenData       40   ThreadData     264   GoalData       216   ChoiceData     176
MachineData    128   NativeNum       40   NativeDeno      40   NativeSeq       48
```

Every one is a multiple of 8 and `alignof` 8 — which is why #1 is triggered
only by `char` arrays, not by object allocation. I had no 32-bit toolchain on
this machine, so the corresponding i386 figures are not measured here.

One structural check that came out clean: `GcArrayObject` is 16 bytes, and the
`static_cast<GcArrayObject*>(static_cast<void*>(x)) - 1` /
`a->forward + 1` header arithmetic (`zammem.h:425-440,457-472,481-496`, plus
`zammem.h:706` in `DynPtrArr::assign`) is
typed `GcArrayObject*` throughout, so `±1` scales to 16 correctly. That idiom
would have been a silent disaster had any of it been written in `char*` with a
hard-coded 8.

The anonymous `union { SearchDump search; }` at `zam.h:746` has a single member
and is tag-discriminated by `status`, so no layout punning there either.

---

## 5. Verification status (2026-11-06: the ZAM runs)

Everything the old version of this section listed as unverified has since been
verified, after the section-1b fixes:

* `libZAM.so` loads under both JVMs — `+ installed ZAP/ZAM v2.1` on jdk8
  (reference `classes.jar`) and jdk21 (ported classes); `run/zetabase/lib/
  native/Linux-aarch64/` now holds the library, `lib/zetarc` registers
  `ZapAdaptor`, and `run/zeta`/`harness/zdrive.py` derive `java.library.path`
  from `uname -m`.
* ZAM instructions execute correctly on real inputs: all 13 runnable
  `zexecexpr`/`zexecpred` blocks of `zapnotes.tex` animate; 8 of 13 match the
  in-line 1999/2000 `\yields` expectations byte for byte (including Peano
  arithmetic `add(three,three) = S(S(S(S(S(S(Z))))))`, schema-binding displays
  and the `\fromdeno` data-input round trip); the 5 differences are
  pre-explained (stale locators in pasted backtraces, one mislabelled block,
  two truncation-cap changes). The stepping protocol (`-n`/`-d`/`-o`) and
  `xprof` behave exactly as the document narrates.
* The GC survives collections during those runs (every `step` loop ends in
  `GcHeap::reclaim`), with `GC_ALIGN 8` keeping to-space aligned.
* Z set semantics and ordering are reproducible: every block is byte-identical
  across 3 independent runs (`harness/zapexec.py --run`'s agreement rule), and
  the set orders match the 1999 i386 recordings — the empirical confirmation
  that the constructor load index reproduces the original address order.
* The animation oracle lives in `tests/zapexec/` (`zapexec.py --check` gates;
  `--yields` reports the historical agreement).

**Fixed 2026-11-06 — a 1998 GC-barrier defect** (the paragraph below is the original
finding; the resolution follows it). Root cause, established with an instrumented build that
validated every root at every collection: `Admin::reset(goal, choice.bound)` passed the
**goal** as write-barrier context for a slot owned by the **choice**. That registered (or,
via the stale `_info.root` that `delForward` leaves behind, *reused*) a root in the goal's
older space for a slot in the younger choice; once the choice was evacuated, the root
dangled into freed memory. The instrumented run caught it as root #16621
(`forwardRef`, `VarDump`, context generation 3) whose slot lay in an evacuated space. The
invariant that makes root reuse safe is "a slot's root lives in its owner's space", i.e.
every barrier call must pass the slot's owner; an audit of all barrier calls found exactly
two violations — `Admin::reset` (admin.h) and `memoize` (natives.cpp, latent: nothing calls
`\MEMOIZE`). Both fixed, each with the argument at the site; `DynPtr`'s plain copy
constructor, which would also break the invariant, was confirmed unused (deleting it still
compiles). Verified: the naive query now runs cleanly past the old crash point (52 M steps,
557 collections, same three-space regime) and `zapexec.py --check` is unchanged (0 of 13).

**Second defect from the same query, also 1998, also fixed: a step-counter livelock.**
With the GC fixed, the naive search ran on until `SetTarget::step` hit 2^31 steps: it kept
`int stepCount` and computed `count = stepCount + count`, which overflowed negative, so
`stepCount >= count` held immediately and every call returned `MORE` without stepping.
`ZapAdaptor` then printed `still searching after 2147483 k-steps` in a tight loop (159 M
times in 15 minutes, a 6.4 GB transcript). Not a 64-bit issue — `int` is 32 bits on both
platforms — but unreachable in practice on 1999 hardware (hours of search) and reached in
minutes here. Fixed by widening the three step counters to `long` (`SetTarget::stepCount`,
`MachineData::profileSteps`, `ConstrData::profileSteps`; the JNI contract `noOfSteps()J` was
always 64-bit) and computing the limit in a separate `long`. Output below 2^31 steps is
unchanged (`zapexec.py --check` 0 of 13). Confirmed: the naive queens now counts straight
through 2^31 (to 2,241,649 k-steps when stopped by hand) with no livelock and no GC fault
— about 52× past the original crash point. The search itself does not terminate in that
time, as `zapnotes.tex` says it will not.

Original finding: **found 2026-11-06 by the naive 8-queens query** (`e -f \{qs: \power Pos |
(8,qs) \in queens\}` in `queens#zapnotes`): a deterministic SIGSEGV after 42,000 k-steps and
431 collections, in `GcRootObject::generation()` (reads `(*object)->generation` with
`*object == 0`, si_addr 0x10) called from `GcHeap::collectRoots` ← `doCollect` ← `reclaim`
← `Java_..._step`. Both write barriers (`forwardRef`, `DynPtrArr::assign`) unregister the
root when a slot is set to null, and `GcRootObject::collect()` itself tolerates a null slot,
so a live root on a null slot means either an unbarriered write or a **dangling root** (its
slot's containing object moved or its page freed). Leading hypothesis, not yet proven: the
multi-space path — the old generation crosses `GC_MAXSPACESIZE` (16 MB) and a third space
appears ("from 3g"), 225 collections before the crash; with objects ~2× their i386 size this
regime is reached at about half the i386 step count, so the 1999 build plausibly never got
here (the document calls this query a "virtual endless search"). **Not patched**: a null
check in `generation()` would hide a dangling-pointer bug. Next step is a watchpoint on the
slot or an ASan build to find who leaves the root dangling. The two terminating variants
(`queens_1`, `queens_2`) run clean: all 92 solutions, valid and distinct, ~1.2 s, 7.5 M
steps, 39 collections, peak 34 MB.

Still unexercised: heap pressure beyond the above (#6 — budgets are i386-tuned and the
out-of-memory check is commented out; `queens`, skipped for run time, is the
input that would reach it), the memo-table fragilities of #9 under long runs,
and the `zam` shell command's external-process path, which is dead code in 1.5
(`ZamProcIface` never shipped) but still calls `Thread.stop()` in a `finally`.
