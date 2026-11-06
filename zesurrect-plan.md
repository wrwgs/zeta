# ZETA Resurrection Plan

Goal: compile and run ZETA (the Z specification analysis/animation framework, TU Berlin,
1997–2002) on modern hardware, and from there port it off Pizza to modern Java.

**Status 2026-11-06.**

- **ZETA 1.5 runs.** It type-checks the birthday book, the use-case specification and the
  1013-line mathematical Toolkit without errors, on Debian 13 / aarch64 / JDK 8, and dumps
  every intermediate representation to text. Phase 2 done (§2).
- **A regression oracle exists**: 42 baselined artifacts over 8 units, stable across repeated
  runs, comparable against any other build via one flag (verified for both a jar and a
  directory). Built and working (§7.6). It gates the scanner and the parser well — including
  1.77 MB of Toolkit AST — and the type checker hardly at all; §7.3 now *quantifies* what
  deferring route A costs rather than asserting it.
- **The from-source build is blocked in Pizza, not in ZETA.** With 30 Java-5 fixes applied,
  Pizza 1.1's front end accepts all 186 units with zero errors and its translator emits 262
  Java files with zero errors; its own back-end type checker then fails with ~100 errors, each
  one flagged by Pizza itself as a probable translation bug (§3.2). **Route B chosen**: treat
  the prebuilt `classes.jar` as the reference and defer the from-source build (§3.4).
- **The ZAM animates (2026-11-06).** Phase 4 done: the aarch64 rebuild runs with the three
  64-bit defects fixed, `ZapAdaptor` is a registered plugin, `zapnotes.tex` animates
  reproducibly under both the 2002 reference and the ported build, and 8 of 13 zexec blocks
  match their 1999/2000 `\yields` expectations byte for byte (§4, `harness/zapexec.py`).
- Remaining: the Pizza→Java port of `form` and the tool adaptors (§9.6 — `zeta.content` is
  now fully ported), and the Phase 5 repackaging (§8).

---

## 0. What has been recovered (verified, staged on disk)

> **`recovered/` is no longer in this repository.** It was archived once the port stopped
> depending on it: `zeta-recovered-2026-11-07.tar.gz` (131 MB, 2,984 files), stored outside the
> repo and on Google Drive, with its own `README.md` and `manifest.txt`. Every `recovered/...`
> path below is relative to that archive once extracted. The four Z corpora and the live
> toolchain were promoted into the repo first, as `examples/` and `toolchain/`.
> The paths in the table below are as they were while the port was being done.


| Artifact | Location | Provenance | Size / check |
|---|---|---|---|
| ZETA 1.5 source tree | `recovered/src/zeta-1.5/` | `git.lmf.cnrs.fr/wolff/hol-csp2.0` → `Event-B/archiv/hol-z/zeta/zeta` (master `6865929`) | 8.2 MB, 964 files |
| **ZETA 1.5 binary distribution** | `recovered/dist/zetadist1_5.class`, extracted to `recovered/dist/zetadist1_5/` | Wayback Machine, `uebb.cs.tu-berlin.de/zeta/zetadist1_5.class`, snapshot **2002-03-05** | 3,928,302 B, sha256 `18ce8bfa…b1f3d` |
| Pizza 1.1 compiler | `recovered/tools/pizza-1.1.jar` | SourceForge `pizzacompiler` (Jan 2002) | 454,985 B, sha256 `7aa3a6b5…ace3f` |
| Temurin JDK 8 (aarch64) | `recovered/tools/jdk8/` | Adoptium `jdk8u504-b01` | runs Pizza and ZETA |
| Zulu JDK 6 class library | `recovered/tools/jdk6-rt/rt.jar` (+`jce`,`jsse`) | Azul `zulu6.22.0.3-jdk6.0.119` (x64 tarball; `rt.jar` is architecture-neutral) | 54 MB, Pizza `-bootclasspath` |
| Runnable ZETABASE | `run/zetabase/` + `run/zeta` | copy of the distribution, with `etc/defaults.props` and `lib/zetarc` written for this machine | working |
| JavaCC 3.2 and 7.0.13 | `recovered/tools/javacc/` | Maven Central (`javacc:javacc:3.2`, `net.java.dev.javacc:javacc:7.0.13`) | 3.2 is the one to use (§3.1) |
| Session driver | `harness/zdrive.py` | written here (§6) | working |
| Makefile source-list extractor | `harness/collect_sources.py` | written here | working |
| Java-5 source/generated fixes | `harness/apply_pizza_java5_fixes.py` | written here (§3) | 30 + 6 edits, idempotent |
| Pizza `-s` output repair | `harness/fix_pizza_emitted_java.py` | written here (§3.3) | 11 sites; partial route only |
| Baseline oracle | `harness/baseline.py` + `baselines/`, `baselines-ported/` | written here (§7.6) | 111 reference / 175 ported artifacts |
| Canonicaliser | `harness/canonicalize.py` | written here (§7.3) | needed for the reference build only |
| Negative suites | `harness/negtest.py`, `harness/targeted.py` + `tests/` | written here (§7.5b-d) | 53 mutants, 17 targeted specs |
| ABI gate | `harness/abi_gate.py` | written here (§9.5) | constant-pool based; 921 references |
| ADT tag checker + table | `harness/adt_tags.py`, `doc/adt-tags.txt` | written here (§9.5d) | 359 cases in 51 ADTs |
| Bridge coverage | `harness/bridge_users.py` | written here (§9.5a) | which bridges anything actually calls |
| Port build + verification | `harness/build.sh`, `harness/verify.sh` | written here (§9.5c) | the five checks, one command |
| `Lexem` generator | `harness/gen_lexem.py` | written here (§9.6) | 106 cases from source + class files |
| **The ported tree** | `src/` → `classes/` | written here (§9.6) | 73 files, ~14,000 lines |
| **The ZAM, built and running** | `zam/` incl. `libZAM.so`, `build.sh`, `NOTES.md`; installed under `run/zetabase/lib/native/Linux-aarch64` | written here (§4) | aarch64 ELF, 13 JNI entry points; 64-bit fixes applied (§4) |
| Animation oracle | `harness/zapexec.py` + `tests/zapexec/` | written here (§4) | 13 blocks from `zapnotes.tex`'s in-line `\yields`; 8/13 literal matches to 1999 |
| Port recipe | `doc/port-recipe.md` | written here | the rules, and why each exists |
| Serialization finding | `doc/serialization.md` | written here (§9.5b) | why the port owes the 2002 store nothing |

The binary distribution is a self-extracting Java class with an appended ZIP (975 entries):
`lib/classes.jar` (940 classes, **all bytecode major version 45**, i.e. Java 1.1 format, still
loadable by every current JVM) which also embeds the original Pizza compiler (`pizza.compiler.Main`,
plus `pizza/v39`); `lib/native/{Linux-i386,SunOS-sparc}/libZAM.so` and `lib/native/Win/ZAM.dll`;
`lib/latex/zeta.sty`; `lib/ztoolkits/Toolkit.tex`; `lib/fonts/*` (Z symbol X11 fonts);
`lib/emacs/*`; `doc/` (full HTML + PostScript); `examples/`.

### Source ↔ binary correspondence (checked)

181 source units (`.pizza`/`.java`); 168 have a matching class in `classes.jar`. The 13 without
are `.etstate.java` build-state leftovers and dead/dev files — and the ETMakefiles independently
confirm the same set is excluded from the build (§5). Tool package sets match exactly. **The
archived source is the 1.5 source, and `classes.jar` is a valid bootstrap and reference.**

### Code size / Pizza feature load

- 148 `.pizza` (42,221 lines) + 33 `.java` (4,359 lines); 166 units in the official build.
- **1,170 `case` declarations across 85 files** (algebraic data types). An earlier count of
  "2,040" was wrong: it counted `case` *tokens*, which conflates a case declaration with a
  `case` label in a `switch` over one (2,158 tokens in all). Of the 1,170 declarations,
  **359 belong to 51 multi-case ADTs** — the ones that carry a `$$tag` — and the remainder are
  single-case classes, which have no discriminant. The tags are tabulated in
  `doc/adt-tags.txt`.
- 24 generic class/interface declarations; ~346 arrow-type sites; 287 uses of `fun`.
- 399 `switch` statements, mostly ADT pattern matches.
- 12,188 lines of C++ in `src/tools/nzap/zam` (ZAM); 3 JavaCC grammars; 7 files with `native` methods.

### Corrections to the Google-sourced starting plan

- The clone command (`git clone https://cnrs.fr`) was not a real URL. Use
  `https://git.lmf.cnrs.fr/wolff/hol-csp2.0.git` (mirror: `gitlab.lisn.upsaclay.fr/burkhart.wolff/hol-csp2.0`),
  sparse + blobless.
- There is **no `zeta/src/tools/ztypecheck`** and **no `src/core`**. The type checker is
  `src/tools/zchecker`; the parser `src/tools/zparser`; the framework core is `src/session`
  (session manager, content types, targets), with `src/content` (ZIRP abstract syntax, text,
  MicroZ), `src/form`/`src/format` (forms and layout), `src/agl` (the Aggregate Library, §9.4),
  `src/meta`, `src/util`. (`TextToken.pizza` *does* exist — in `src/tools/zparser`, not in
  `src/content/text`.)
- "Transpile first" is the wrong order, and we now know why concretely: the port needs the
  reference implementation as an oracle (§7), and the oracle needs the reference *running* (§6).
- A Dockerised JDK 1.1/1.2 image is **not** needed, and would not have helped: the class format
  is fine on modern JVMs; the real constraints are a few removed/changed *APIs* (§3, §5).

---

## 1. Environment

Debian 13 (trixie), **aarch64**, TeX Live 2026 present, `gcc`/`g++`/`make` present, no system JVM,
no `javacc`, no Emacs. JDKs live under `recovered/tools/`.

**aarch64 matters only for ZAP**: the prebuilt `libZAM.so` is Linux i386. Everything else —
LaTeX adaptor, parser, type checker, MicroZ reducer, pretty printer, shell, Swing GUI — is pure
Java and runs as-is (verified, §2).

---

## 2. Phase 2 — Run the binary distribution ✅ DONE

Two things were missing and had to be reconstructed; neither is in the archived payload because
the 2002 GUI installer generated them:

1. **`$ZETABASE/etc/defaults.props`** — shipped with the original author's paths
   (`/usr/local/bin/latex`, `/opt/kde/bin/kdvi`, `/usr/X11/bin/xemacs`). Rewritten for this
   machine; `run/zetabase/etc/defaults.props`.
2. **`$ZETABASE/lib/zetarc`** — *absent entirely*, and without it ZETA starts with **no tools at
   all** (the shell has only `help`/`quit`/`plugin`, which is what the first run looked like).
   The template survives as `src/tools/sh/zetarc.site`; `run/zetabase/lib/zetarc` now contains:

   ```
   plugin zeta.tools.latex.LatexAdaptor
   plugin zeta.tools.zparser.ParserAdaptor
   plugin zeta.tools.zchecker.CheckerAdaptor
   plugin zeta.tools.nmzreducer.ReducerAdaptor
   plugin zeta.tools.pp.PrettyPrinterAdaptor
   ```
   `zeta.tools.nzap.ZapAdaptor` is deliberately left out until ZAM is rebuilt (Phase 4), and
   `zeta.tools.emacs.EditorAdaptor` until the editor story is settled (Phase 5).

Working invocation (`run/zeta` wraps this; it replaces the 2000-era `src/tools/sh/zeta`, which
hardcoded `ZETABASE=/home/uebb/wg/et` and set the obsolete `-Djava.compiler=` and `THREADS_FLAG`):

```sh
recovered/tools/jdk8/bin/java -Xmx512m \
  -Dzeta.home=$ZETABASE \
  -Dzeta.latex.path=".:$ZETABASE/lib/latex:$ZETABASE/lib/ztoolkits:" \
  -Djava.library.path=$ZETABASE/lib/native/Linux-i386 \
  -cp $ZETABASE/lib/classes.jar zeta.tools.sh.Driver -b
```

**Superseded 2026-11-07:** `run/zeta` now runs the Java 21 port — JDK 21 and `classes`,
every path resolved relative to the script, no `classes.jar` and no Pizza runtime
(`run/zeta -b` batch shell, `run/zeta -g` GUI; `ZETA_JAVA`/`ZETA_CLASSES`/`ZETABASE` override).
The paragraph below describes the 2002 build as first brought up.

`run/zeta` wrapped exactly this and was verified end to end (`echo quit | run/zeta -b` installs all
five adaptors). It does *not* need to source `lib/zetarc` — ZETA reads `$ZETABASE/lib/zetarc`
itself. If it reports "Permission denied", `chmod +x run/zeta`.

**Do not use JDK 20+.** ZETA calls `Thread.stop()`, removed there, at
`ShellState.pizza:183` (`killEvaluation`), `ZapAdaptor.pizza:923-924`, `Impure.pizza:96,120`.
JDK 8 verified working; 11/17 expected fine. Note this is not cosmetic: `Thread.stop` is how
ZETA cancels a running evaluation, so the eventual fix is a real design change
(cooperative/interrupt-based cancellation), not a rename.

### What was verified working

```
+ installed Z LaTeX adaptor v1.4      + installed Z parser v1.5
+ installed Z type checker v1.5       + installed Z-to-MicroZ reducer v2.0
+ installed pretty printer v1.0
```

| Example | Result |
|---|---|
| `examples/bbook/bbook.tex` | 3 units (`BBSpec`, `BBExec`, `BBTest`); all type-check clean, together with `Toolkit#Toolkit` |
| `examples/ucases/ucases.tex` | 3 units (`UseCases`, `UseCasesModel`, `CashDispenser`); all type-check clean |
| `examples/graphs/Shortest.tex` | loads, 1 unit |
| `lib/ztoolkits/Toolkit.tex` (1013 lines) | parses and type-checks as a dependency of every run |

Full shell surface (from `help`): `load`, `c` (type-check), `pp`, `latex`, `view`,
`make [-u UNIT] <CONTENT-TYPE> [-d [FILE]]`, `list units`, `list types`, `default`, `path`,
`set`, `command`, `plugin`, `interrupt`, `quit`, `squit`.

### LaTeX ✅ works, after one fix to `zeta.sty`

`latex -u BBSpec#bbook` now produces a 7-page DVI with **zero LaTeX warnings or errors**, and
the Z typesets correctly — schema boxes, Δ/Ξ, ∈, ∉, ↦, ⊲, ℙ, ℕ. Also verified: `ucases`
(8 pages) and the 1013-line `Toolkit` itself.

One genuine 25-year-old incompatibility had to be fixed, in `lib/latex/zeta.sty` around line
753 (patch: `patches/zeta-sty-modern-latex.patch`). The original is

```tex
\edef\esz@origtilde{~}          % capture the original meaning of active ~
\def\esztildespace#1{\def~{\ifmmode#1\else\esz@origtilde\fi}}
```

The intent is to save what `~` means, then redefine it to insert a thin space in math mode and
behave normally elsewhere. `\edef` captures the *expansion*, which in 2000 was a `\penalty`.
In modern LaTeX `~` is a protected `\nobreakspace` whose expansion reaches `~` again, so the
`\edef` recurses until TeX dies with `TeX capacity exceeded [input stack size=10000]`.
`\let` is not enough either — it still recursed in text mode. The fix states the original
meaning outright, which is stable across both eras:

```tex
\ifx\nobreakspace\undefined
  \def\esz@origtilde{\penalty\@M\ }%
\else
  \def\esz@origtilde{\leavevmode\nobreak\ }%
\fi
```

Two residuals are **input dependencies, not ZETA defects**: `zapnotes.tex` pulls in
`html.sty` (the `latex2html` package, not installed here), and `examples/graphs/zetarc` begins
with `xconf ton`, a command belonging to a tool that is not plugged in — the DVI still builds.

### Not yet exercised

Swing GUI (`-g`), ZAP animation (needs Phase 4), the XEmacs front end (Phase 5).

---

## 3. Phase 3 — Reproducible build from source (Pizza) — 18 errors from clean

The install documentation (`doc/install.html` §3.1) warns that **the nodes have cyclic
dependencies and Pizza has no `-dep` compilation**, which is why the source distribution needed
the binary distribution to build itself, and that `etmake boot` "has not been tested". With
`classes.jar` in hand this is a non-issue: compile all sources in **one whole-program Pizza
invocation** (what `etmake boot` does) with `classes.jar` on the classpath.

Verified working command (173→166 sources, see §5):

```sh
recovered/tools/jdk8/bin/java -Xmx2g -cp recovered/tools/pizza-1.1.jar \
  net.sf.pizzacompiler.compiler.Main -nowarn \
  -bootclasspath recovered/tools/jdk6-rt/rt.jar:…/jce.jar:…/jsse.jar \
  -classpath $CLASSES_JAR:recovered/tools/pizza-1.1.jar \
  -d work/build/classes $(cat sources_mk.txt)
```

Progress: all sources + JDK 8 library → 100+ errors → JDK 6 library via `-bootclasspath` →
18 errors → official makefile source list → **18 errors, all one root cause.**

### The one remaining root cause

**Pizza 1.1 predates Java 5 and cannot compile against a Java 5+ class library.** It has no
covariant-return override resolution, so `java.lang` members resolve to the wrong declarations.
None of the 18 is a defect in ZETA — the source is valid Java 1.4:

| Error | Cause | Sites |
|---|---|---|
| `unreported exception: java.io.IOException` on `buf.append(c)` | resolves to `Appendable.append` (Java 5) instead of `StringBuffer.append` | 13 |
| `found AbstractStringBuilder, required StringBuffer` | `StringBuffer.append(String)` resolves to the Java 5 package-private superclass `AbstractStringBuilder` | 2 (`LatexConfig.pizza:1510,1512`) |
| `reference to Enum is ambiguous` | `zeta.agl.Enum` vs `java.lang.Enum` (Java 5) | 3 (`LatexFormatter`, `PrettyPrinter`) |

### Resolving it — and why the obvious answer is not available

The preferred fix is to give Pizza a genuine pre-Java-5 class library as `-bootclasspath`:
zero source changes, all 18 errors vanish together, and the reference build stays
byte-comparable to `classes.jar` — which matters because the Pizza build *is* the
differential-testing reference for the port (§7), and a reference should stay frozen.

**A Java 1.4 `rt.jar` turned out not to be readily obtainable** (checked, 2026-11-06):

- Wayback's CDX index for `java.sun.com` has **no** archived `j2sdk`/`j2re` 1.4 Linux
  `.bin`/`.rpm`. Blackdown's Linux JDK mirrors (`blackdown.org`, `ibiblio.org/pub/mirrors/blackdown`)
  are likewise not archived — notable, since `classes.jar`'s manifest reads
  `Created-By: 1.2.2 (Blackdown Java-Linux Team)`.
- archive.org *does* hold `j2re-1_4_2_03-windows-i586-p.exe` (15 MB, item `j2sdk-1_4_2-src-scsl`)
  and `j2re-1_4_2_06-windows-i586-p`. Downloaded and inspected: InstallShield + 7 MSCF
  cabinets, needing `cabextract`/`7z` (neither installed) — **and the payload ships `rt.pack`,
  not `rt.jar`**: the installer unpacks it at install time via a bundled DLL
  (`DLL13 -l "[LOGFILE]" "[INSTALLDIR]\lib\rt.pack`). Sun's 1.4-era pack predates standard
  pack200, so this needs the proprietary unpacker. (Untested long shot: JDK 8's `unpack200`
  may read the 1.4 format. Root *is* available on this machine, so `cabextract` could be
  installed to find out — two unverified steps.)
- The 1.4.2 SCSL **source** release is archived, but building a 2003 JDK from source is not a
  sane dependency.

So the plan must not lead with that fetch. Options, with recommendation:

| | Approach | Verdict |
|---|---|---|
| **A** | Extract a 1.4 `rt.jar` (cabextract → `rt.pack` → `unpack200`) | Try once, time-boxed. If `unpack200` reads it, this is the best outcome; non-redistributable, so the repo documents the fetch. |
| **B** | **Recommended fallback.** Keep the free Zulu 6 library and carry the 18 fixes as a *versioned patch file* the build applies to a pristine copy of the recovered source | The source on disk stays the 2000 original, the divergence is explicit, reviewable and revertible, and the build is fully reproducible from free artifacts. 3 of the 18 (`import zeta.agl.Enum;`) are permanently correct and wanted by any modern port; the other 15 are `StringBuffer` call sites that a modern `javac` compiles as written, so they are build-definition, not source rot. |
| **C** | Prepend a pre-Java-5 `java.lang` from a historical free library (early GNU Classpath, which targets JDK 1.1/1.2) ahead of Zulu 6's `rt.jar` | Redistributable and not hand-written, but unverified: needs an early Classpath release that still has a Pizza-readable `StringBuffer`, and the layering has to leave Swing resolving from `rt.jar`. Only worth exploring if B proves awkward. |

**Outcome (2026-11-06): option A was attempted and failed; option B was implemented and works.**
`cabextract` reaches only the MSI bootstrap cabinets of the archived installer; the JRE payload
sits behind PE → embedded MSI → cab → `rt.pack`. Option B is in
`harness/apply_pizza_java5_fixes.py`: 30 edits (13+13 `append(char)`, 3 self-assignments,
2 `import zeta.agl.Enum;`), applied to a copy, each valid in both Java 1.4 and current Java.
With them, **Pizza's front end accepts the entire system with zero errors** (§3.2).

Also ruled out: Pizza's `-source 1.4`/`-source 1.3` flags (they govern source-language
features, not the class library — still 18 errors), and deriving a 1.4-equivalent library by
deleting `java/lang/{Enum,Appendable,AbstractStringBuilder}.class` from Zulu 6's `rt.jar`
(Pizza hard-errors on a missing superclass/interface rather than ignoring it).

The original compiler bundled in `classes.jar` (`pizza.compiler.Main`, the v39-era one) is
**not** a way out: it has no `-bootclasspath` and accepts only class-file version **45.3**
(Java 1.1), rejecting Zulu 6's 49.0 outright. It would need a JDK 1.1 class library, which is
further out of reach than 1.4.

### 3.1 JavaCC — solved

The three grammars must be regenerated, and the generator version matters:

- **JavaCC 7.0.13 with `-JDK_VERSION=1.4`**: generates, but its output still trips Pizza —
  40 errors (`illegal character`, `non-static variable this ... static context`).
- **JavaCC 3.2** (`javacc:javacc:3.2` on Maven Central, `recovered/tools/javacc/javacc-3.2.jar`):
  clean. 40 → 19 errors, all of them the same Java-5 `StringBuffer` issue, in JavaCC's stock
  `addEscapes` template. Those 6 sites (`ParseException.java`, `TokenMgrError.java` × 3 dirs)
  are handled by `apply_pizza_java5_fixes.py --generated`, which runs after generation because
  those files are build products. The other 13 are in `tools/latex/LatexScanner.jj`, i.e. ZETA's
  own code inside the grammar's `PARSER_BEGIN` block, and are part of the source patch.

Build order: `--sources` → JavaCC 3.2 → `--generated` → compile. Also: the output directory must
**not** be on Pizza's classpath, and `classes.jar` must not be either — a whole-program compile
needs no prior classes, and mixing stale ones in produces a flood of bogus errors.

The official source list is 166 files; with the generated parsers, 186.

### 3.2 The actual blocker: Pizza 1.1's back end is broken on this codebase

With everything above in place, Pizza reports ~100 errors of two shapes —
`class X should be declared abstract; it does not define method toFormat(FormatInfo) in
interface zeta.format.Formattable` and `variable an not found in class zeta.content.zirp.Expr`.
Both are false: `Term` implements `toFormat`, `AnnotatedTerm` declares `an`, and every affected
class inherits them.

**Every one of the 100 errors is preceded by Pizza's own banner**:

```
Suspicious error message --
there might be something wrong with the Pizza->Java translation.
Please file a bug report at Sourceforge.net
```

100 banners for 100 errors. These come from the phase *after* Pizza→Java translation: the
compiler loses inherited interface implementations and inherited fields for translated class
cases. The 100 errors fall into exactly two families, which is consistent with a single defect
but equally with two, or with one general defect in how a translated class case inherits from a
grandparent. Route A's cost below rests on that being cheap to resolve — an inference from the
error shape, not a measurement.

Two independent confirmations that the front end is fine:

- Compiling with `src/agl/Benchmarks.pizza` included (a dead dev file with 4 genuine errors of
  its own) makes all 100 vanish — Pizza aborts in the front end and never reaches the broken
  phase. That is also why earlier bisects looked inconsistent.
- `pizza -s`, which stops after translation and emits Java instead of class files,
  **translates all 186 sources with zero errors** and writes 262 `.java` files.

### 3.3 Why `-s` + javac is not the short way round

Tempting — Pizza as translator, a real javac as compiler — and it gets close: `-source 1.4`
handles the variables Pizza legitimately names `enum` (68 sites, not a keyword before Java 5),
and `harness/fix_pizza_emitted_java.py` repairs the emitter's one syntactic defect (a
brace-less `try <stmt> catch`, 11 sites in 4 files; the nested case is disambiguated by Pizza's
synthetic `e$0` parameter naming). That takes javac from unparseable to **zero syntax errors**.

But 296 semantic errors remain, and the cause is structural: **the emitter writes binary
(mangled) names where source-level names are required** — `new ZAM$CodeFormatter(...)`,
`zeta.content.text.Text$FormatKind.Block(0)` instead of `ZAM.CodeFormatter` and
`Text.FormatKind` — and it scatters nested classes into subdirectory files such as
`zeta/agl/MaxStrongComp/MaxStrongComp$DFS$$closures.java`. Pizza's `-s` output is a debugging
aid, not re-compilable source. Un-mangling it means distinguishing nested-class `$` from
Pizza's own synthetic `$` (`e$0`, `$$closures`, `zeta$agl$Seq$append`) across 262 files — a
riskier transformation than it looks.

Two smaller, genuine issues surfaced alongside, worth recording:
- `src/tools/zparser/MixfixParser.pizza:20` has `import Fixity.*;` — a **Pizza class-case
  import**, i.e. importing an ADT's constructors unqualified, with no pre-Java-5 equivalent.
  Only one live occurrence (the other, in the dead `Main.pizza`, is `import Command.*;`).
  Any port must qualify these.
- `src/tools/sh/ShellForms.pizza:14` has `import zeta.content.graphics.*;` — a package whose
  source **is not in the archive** (the graphics adaptor is one of the disabled `xSUBNODES`).
  Pizza tolerates the unused on-demand import; javac does not.

### 3.4 Where this leaves Phase 3

A from-source build needs one of:

| | Route | Assessment |
|---|---|---|
| **A** | **Fix Pizza 1.1's back end.** `pizza-1.1-src.zip` (812 KB) is on SourceForge, and Pizza is itself written in Pizza, so the binary we have can bootstrap it. The bug is self-flagged and looks contained: inherited interface implementations and inherited fields are lost for translated class cases. | Most principled: a compiler fix and the build works as designed, producing authentic bytecode. Needed if ZETA's Pizza source is ever to be *modified* (e.g. the `Name.hashCode` fix). |
| **B** | **Treat the prebuilt `classes.jar` as the reference and defer the from-source build.** It already runs (§2). Canonicalise dumps in the harness rather than at the source, and port directly from Pizza source to modern Java using `classes.jar` as the oracle. | Pragmatic, and sufficient: the port needs a *running* reference, not a buildable one. Cost: ZETA's behaviour cannot be changed until the port reaches a given node. |
| **C** | Keep hunting a pre-Java-5 class library. | Lowest expected value — 1.4 is behind `rt.pack`, and the v39 compiler needs 1.1. |

Everything up to the back end is reproducible today via `harness/apply_pizza_java5_fixes.py`,
JavaCC 3.2 and the recorded Pizza invocation; `harness/fix_pizza_emitted_java.py` additionally
gets the translated Java to parse. That is the state to resume from.

Note for the runtime classpath: the source tree imports `net.sf.pizzacompiler.lang.Pair`
(39 files) and `pizza.support.*` (3 files); `pizza-1.1.jar` provides both, so it is needed at run
time as well as compile time. The 2002 `classes.jar` instead embeds the older `pizza/lang` —
i.e. the archived source is marginally *newer* than the binary distribution in this respect.

---

## 4. Phase 4 — Rebuild ZAM for aarch64

12,188 lines of C++ in `src/tools/nzap/zam` → `libZAM.so` via JNI. Only the final animation step
needs it: the `nmz` (MicroZ) reduction already works in pure Java (verified, §6).

It is not wired up at all today, which is worth being explicit about: `lib/native/` ships
`libZAM.so` only for **Linux-i386** and **SunOS-sparc**, and the reconstructed `lib/zetarc`
registers five plugins — `latex`, `zparser`, `zchecker`, `nmzreducer`, `pp` — with **no
`ZapAdaptor`**. So `zapnotes.tex`, the document about ZAP, is scanned, parsed, type-checked,
reduced to MicroZ and pretty-printed, but never executed. The `nmz` artifacts in the oracle are
exactly what ZAM would consume.

### It compiles and links ✅ (`zam/`)

`g++ 14.2`, aarch64: **11 of 11 translation units compile, `libZAM.so` links, 13 JNI entry
points export** — verified independently of the agent that did it, including a fresh relink
with `-Wl,--no-undefined` (plain `g++ -shared` succeeds with unresolved symbols, so a bare
link proves nothing). Exactly **three** source files needed changing: `zammem.h`, `zamutil.h`,
`interp.cpp`. `recovered/` is untouched; `zam/build.sh` reproduces the result.

The 141 errors fell into four kinds, and only the last is interesting:

| count | error | fix |
|---|---|---|
| 108 | `'next'`/`'prev'` not declared | members of a **dependent base** (`Ring<T> : RLink<T>`); modern C++ needs `this->`. **15 sites**, not 108 — GCC reports a name once per function scope, so four only appeared after the first wave was fixed |
| 22 | `'GC'` not declared | `enum GCPlacement { GC };` sat at `zammem.h:509`, *after* its uses at 432 and 491; moved above `GcHeap` |
| 8 | `std::uninitialized_copy` missing | **two distinct causes**: six in `zamutil.h` were qualified but wanted `<memory>`; two in `interp.cpp` were *unqualified*, relying on 1998 libstdc++ exporting those algorithms globally |
| 3 | `void*` → `unsigned` loses precision | `uintptr_t` then an explicit narrowing, in the three `keyHash` functions |

**The JNI header needs no regeneration.** `javah` is gone, but the checked-in 1998
`zeta_tools_nzap_ZamJniIface.h` still matches the compiled class: 13 natives, all instance
methods, same order and descriptors; no `_` in any name so no `_1` escapes, no overloads so no
`__` suffixes. Cross-checked by regenerating from a transcribed stub through `javac -h` —
byte-identical but for two `/* Inaccessible static */` comments modern `javac` no longer emits.

### What compiling does *not* settle: the 64-bit risk list

Structurally the best possible news: **there is no pointer tagging anywhere.** The three casts
above were the only pointer↔integer conversions in 12,188 lines. `kind`/`ground` are ordinary
fields, the GC uses an explicit `forward` pointer plus an `int generation`, `GcArrayObject`
header arithmetic is typed throughout so `±1` scales, and the unit reader (`loader.cpp:125`)
is a byte-at-a-time 7-bit varint — no `fread` of structs, no endianness assumption, so the
on-disk format is word-size independent.

What remains, worst first:

1. **`GC_ALIGN 1` (`zammem.h:21`) — demonstrated, not inferred.** The bump allocators round to
   a *one-byte* boundary. Every class type is size- and align-8 (measured), but odd-sized
   `char` arrays leave the pointer anywhere — and `natives.h:64` re-does one on **every GC**
   that copies a `NativeDeno`. The probe in `zam/sizes.cpp` shows a 24-byte object, then
   `char[5]`, then a third object landing at `%8 == 5`, vtable pointer and all. ARMv8 tolerates
   unaligned loads to normal memory so it will probably *appear* to work, but it is UB, any
   atomic on a misaligned address is SIGBUS, and `-O2` may vectorise on the assumption.
   One-line fix: `GC_ALIGN 8`. Not yet applied.
2. **`natives.cpp:133` — Z number equality is computed in 32 bits.** `NativeNum::unifyWith`
   does `int c = val - getNativeNum(other)->val` where `val` is `long`, now 64-bit. Two numbers
   differing by a non-zero multiple of 2^32 unify: `x = 4294967296` would satisfy `x = 0`.
   Unreachable on i386 by construction, newly reachable here. Relatedly, Z integer arithmetic
   now wraps at 2^63 instead of 2^31 — a change to the specification language's semantics.
3. **`unify.cpp:63` — the sort key for set extensions is address-derived.** Under ASLR the
   relative order of two `ConstructorData` can change run to run, so printed set order and
   `getBinding` output are nondeterministic. It feeds `compare()` → `qsort`, `removeDups`
   (which *drops* an element when the comparison is 0), `sunion`/`sisect`, `Ring::enqueue`.
   This is the same shape of defect as `Name.hashCode` in the Java half (§9.2), and it has the
   same consequence: **an animation oracle cannot be recorded until it is fixed.** The fix is a
   stable constructor load index — a design decision, not a patch.
4. `size_t offset` → `int` on the GC's pointer-recovery path (`zamutil.h:167`); a `-1` sentinel
   stored into a `size_t` (`zamutil.h:74`), safe only because `getBase()` is never called on a
   ring head, and that is not asserted.
5. Hash truncation (mine) plus a pre-existing `unsigned`→`int` (`zamutil.h:1107`). Distribution
   only, and consistent between store and lookup.
6. Heap budgets are absolute byte counts tuned to i386 object sizes (`zammem.h:13-15`) while
   objects roughly doubled (`ThreadData` 264, `GoalData` 216, `ChoiceData` 176), so GC frequency
   and promotion behaviour have shifted. The out-of-memory check is commented out
   (`zammem.cpp:28`), so exhaustion arrives as `calloc` returning 0.
7. Six `size_t`→`int` narrowings across the allocator interface (`zammem.h:318-356`).
8. Not 64-bit but fragile: memo tables are placement-new'd as a single `Entry*` over an
   array-sized block and are only zeroed because `GcSpace::reserve` uses `calloc` and
   `release()` is commented out — switching to the adjacent commented `malloc` would corrupt
   every memo table.

`build.sh` stays at `-O0`, matching the `ETMakefile` whose `-O3` was already commented out in
2000. Raising it means `-O2 -fno-strict-aliasing` (the code punts every pointer through
`char*`/`void*`) and only after `GC_ALIGN`.

### Exit criteria — ✅ all three met (2026-11-06)

1. **Fixed, in order** (details and the full argument per site: `zam/NOTES.md` §1b):
   `GC_ALIGN` → 8, verified against the one hazard that could make rounding-up unsafe
   (nothing walks a page by stored object size); the 32-bit number comparison → direct
   64-bit compare throwing sign −1/1 (every consumer of `UnequalFailure.order` was checked
   to be sign-only first); the address-derived sort key → a creation-order `index` on
   `ConstructorData`, chosen over lexicographic name order because on i386 pointer order
   *was* load order — so the index reproduces the ordering the 2002 reference computed and
   the `\yields` oracles were recorded against. A fourth, non-64-bit defect was fixed on
   review: `javadriver.cpp` cached `start()`'s per-thread `JNIEnv*` in a static and used it
   from other threads' calls; it now caches the `JavaVM*` and gets the env per callback.
2. **Installed and wired.** `libZAM.so` (rebuilt, 13/13 natives) sits under
   `lib/native/Linux-aarch64`; `run/zeta` and `harness/zdrive.py` derive the library path
   from the machine architecture; `lib/zetarc` registers `zeta.tools.nzap.ZapAdaptor`
   between the reducer and the pretty printer, exactly where `zetarc.site` had it. The
   feared baseline cost did not materialise: dumps are untouched (nzap registers no
   `Slot`), and the transcript recordings now carry the sixth `+ installed` line — all
   five checks green against the flipped zetarc (111/111, 175/175, gate, tags, both
   negative suites).
3. **Animated, reproducibly.** `e -u Nat#zapnotes \{x:N | x ~ less ~ three\}` prints
   `= {Z,S(Z),S(S(Z))}` — byte-identical across runs, identical between the 2002 reference
   build (jdk8) and the ported build (jdk21), and **equal to the `\yields` expectation
   recorded in `zapnotes.tex` in 1999/2000, element order included** — which is the
   empirical confirmation of the load-index design choice. The stepping narration
   (`{1,2}` → `-n` `{3,4}` → `-d` `3` → `-n` `4` → `-o`) matches the document, and
   `xprof` attributes all steps to source locators. The animation oracle is
   `harness/zapexec.py`: it extracts the 13 `zexecexpr`/`zexecpred` blocks and their
   in-line `\yields` expectations from `zapnotes.tex` (the only animation oracle the
   archive carries), runs each in its own session, and records transcripts under
   `tests/zapexec/` with the same record-only-when-all-runs-agree rule as
   `baseline.py --update`; `--check` gates, `--yields` reports the historical agreement
   (informational, since diagnostic-valued expectations embed stale locators and the
   Genericity truncation example predates the current `-t` solution cap). `queens` is
   skipped: the document itself says it needs millions of steps.

   Recorded: **13 of 13 runnable blocks, 0 unstable across 3 runs each; `--check` 0 differ;
   8 of 13 match their 1999/2000 `\yields` text literally** — including `add(three,three)`
   = `S(S(S(S(S(S(Z))))))`, the binding display `{<x==x,y==1,z==1>,<x==x,y==2,z==2>}`, and
   the `\typeinfo`/`\fromdeno` data-input round trip. The 5 non-literal ones are exactly
   the predicted kinds: two constraint backtraces whose locators shifted when the document
   was edited after the expectation was pasted in (the *backtrace structure* matches); the
   mislabelled `Nat` block that puts a set-valued query in `zexecpred` (today: a type
   error, pinned as such); and two truncation-length differences (`-t` now caps at 20
   solutions where the 1999 text shows 30, resp. a hand-elided `...`).

---

## 5. The official source list (do not use `find`)

`harness/collect_sources.py` reads each node's `ETMakefile` `PIZZA_FILES`/`JAVA_FILES`, which is
what `etmake -s sources` collects. This matters: compiling everything `find` returns produced
~70 spurious errors from **dead files** that reference a long-removed API
(`Command`, `Command.Token`, `Session.getCommand()`, `Failures.CannotExecute`,
`Locator.VisitCapability.FileVisit`). Excluded, and independently absent from `classes.jar`:

`agl/Benchmarks.pizza`, `agl/CastHack.pizza`, `format/impl/FormatList.java`,
`tools/sh/Main.pizza`, `tools/sh/FileVisitor.pizza`, and the two JavaCC-generated
`meta/genlexis/*.java` (regenerated).

---

## 6. The session driver (`harness/zdrive.py`)

ZETA's batch commander cannot simply be fed a script. `ShellState.doEval` runs **each command on
its own thread**; the read loop does not wait, so at stdin EOF `BatchCommander.exec1` calls
`session.abort()` and kills pending work — observed as `+ scanning bbook.tex` arriving *after*
`= good bye.`

Command completion is signalled by a `FinishedMessage`, which `MessagePrinter.receive` renders
through its fall-through branch, i.e. the **`- ` prefix followed by the command's words**. Message
prefixes: `@ ` debug, `! ` diagnostic, `= ` result, `+ ` progress, `- ` finished.
**What the driver actually synchronises on is the prompt**, and that took two attempts to get
right.

A `FinishedMessage` is not enough: a command that fails with `Failures.FormFailed` never sends
one, because `ShellState.doEval` swallows that exception — which is the *normal* outcome for a
specification with a type error, since those diagnostics carry the source locator rather than
`<command input>`. Waiting for a marker that never arrives cost a full timeout per failing
command, and was why generating negative tests looked slow when ZETA does the work in ~0.3s.

The first fix was to end a command after a period of silence. That worked but was
load-sensitive: a GC pause or a busy machine can exceed the window mid-command and truncate the
transcript. It showed up exactly as you would expect such a thing to — **one** mutation test
out of 53 differing in one run, and reproducing 6/6 identically in isolation.

The right signal was already there. `ShellState.doEval` runs each command on its own thread
and, in a `finally`, does `synchronized (state) state.notifyAll()` once the last evaluation
ends; `BatchCommander.commandLoop`'s prompter thread sits in `state.wait()` and answers by
printing another prompt. **So a prompt follows every completed command, marker or not**, and
nothing about it depends on timing.

Reading it requires characters rather than lines: the prompter writes `"? "` with no newline,
so a line-oriented reader holds it in a buffer forever. The driver now emits two kinds of token
— a completed line, and PROMPT whenever the pending buffer is exactly the prompt — which also
parses `? = good bye.` properly as prompt-then-line, instead of needing the `?` stripped
afterwards.

One transcript change came with it, and it is a correction: the startup banner and the
`+ installed …` lines now appear *before* the first `>>> command`, where they belong. Draining
used to begin only after the first command was sent, so they were attributed to it. Everything
else is byte-identical, which is the evidence that the rewrite preserved behaviour.

```sh
python3 harness/zdrive.py --cwd work/session \
    "load bbook.tex" "c -u BBSpec#bbook" "make -u BBSpec#bbook nmz -d nmz.txt"
```

`--classes` points it at a different class tree, which is exactly what the port needs: the same
script against reference and ported build.

### 6.1 Shutdown is a race, and the transcript was papering over it

Two things reach `SessionImpl.abort()`, which prints `good bye.` before `System.exit`: the
`quit` command (`ShellForms.quitForm` → `session.abort()` — the *saving* variant is a separate
command, `squit`), and end of input, which makes `BatchCommander.exec1`'s `read()` return null
and call `session.abort()` as well. Writing `quit` and closing stdin together therefore ran
`abort()` **twice**, on two threads, and `MessagePrinter.printPrefixed` emits the `= ` prefix
and the text in **separate** `writer.print`/`println` calls with no lock — so the two identical
messages interleaved. The tail of a transcript came out as either

```
? = good bye.                 = = good bye.
= good bye.          or       good bye.
```

about one run in four — a genuine, if cosmetic, concurrency defect in the 2000 shell, to be
fixed when `zeta.tools.sh` is ported (`printPrefixed` should format the whole message and write
it once).

The harness had been masking it twice over, and both maskings are gone:

* `canon_transcript` stripped trailing `= good bye.` lines. That hid the common case and still
  let the interleaved rendering through as a spurious difference — which is exactly how it
  showed up: as `graphs/session.transcript` failing the reference oracle about one run in four,
  looking like a port regression.
* the driver's final drain polled the queue's `empty()`, but it is fed by the reader thread, so
  an empty-looking queue only means that thread has not been scheduled yet. It now drains to the
  reader's EOF sentinel.

The driver now writes `quit` and waits for the JVM to exit **before** closing stdin, so only one
shutdown path runs. Measured 8/8 identical, and the line is worth keeping: it is the evidence
that the session shut down cleanly rather than being killed.

`baseline.py --artifacts <pattern>` exists for exactly this situation — re-recording one kind of
artifact after a harness change, instead of rewriting baselines that nothing asked to change.

### 6.2 The harness used to blame the port for its own failures

`baseline.py` ignored the driver's **exit status**. `zdrive.py` returns non-zero when a command
times out or ZETA dies, and in that case the transcript is truncated — which, compared against
a baseline, appears as an ordinary `DIFFERS` on whichever artifacts happened to be cut off.
That is the most misleading failure this harness can produce, because it looks exactly like a
regression in the port.

It cost one confusing investigation: `Shortest.nmz` differed once while four agents and a gate
run were competing for the machine, then matched 8 runs out of 8 in isolation. The status is
now surfaced, with the difference explicitly attributed to the harness rather than the port.

---

## 7. Differential testing for the Pizza→Java port

This answers the question the whole port hinges on. Short version: **the oracle is strong, but
there are zero tests — the problem is inputs, not infrastructure, and one real hazard
(non-determinism) must be fixed before any baseline is meaningful.**

### 7.1 The oracle: the target/content-type graph

ZETA is organised as a dependency-driven `make` over `Target(unit, content-type)` pairs
(`session/Target.pizza`, `sman/UnitImpl.pizza`, and every adaptor's `getDependencies`). Every
intermediate artifact is individually addressable *and* printable to a file via
`make -u <unit> <type> -d <file>`. `list types` reports:

```
checked-absy, checked-env, dvi, lang-config, latex-fragment, nmz,
pretty-text, text, unchecked-absy, unchecked-env
```

So the pipeline can be diffed **stage by stage** — scan → parse → environment → typecheck →
MicroZ reduction → pretty-print — instead of only at the console. This is the genuinely strong
property, and it survives the port: the target graph is ordinary Java-shaped code, not
Pizza-encoded. Verified on `BBSpec#bbook`: `checked-absy` 56,783 B, `checked-env` 47,173 B,
`nmz` 2,750 B, `pretty-text` 980 B.

### 7.2 Which dumps are usable — measured, not assumed

`harness/baseline.py --selftest` runs the whole corpus N times against the reference and keeps
only artifacts that reproduce. Result over 4 runs of 8 units (§7.6):

| content type | reproducible | note |
|---|---|---|
| `text` | **8 / 8** | scanner output; embeds absolute paths, normalised by the harness |
| `lang-config` | **8 / 8** | |
| `latex-fragment` | **8 / 8** | |
| `unchecked-absy` | **4 / 8** | parser output; the Toolkit's 1.77 MB dump is unstable and excluded |
| `pretty-text` | **5 / 8** | |
| `nmz` | **7 / 8** | recovered from 1/8 by canonicalisation (§7.3), incl. the Toolkit |
| `unchecked-env`, `checked-absy`, `checked-env` | 0 | never dumped; no safe canonicalisation |

So the **scanner and parser are well covered and the type checker is barely covered at all.**
Its only stable artifact is the session transcript, and a transcript is a *progress trace*, not
a result: it contains the banner, the five `+ installed` lines, `+ scanning`,
`+ parsing section`, `+ type checking section` and the completion marker. It pins that the
checker ran and reported nothing — close to a smoke test. The checker's actual *output* is
unpinned, not merely its error path: `checked-absy`/`checked-env` are never dumped (§7.3) and
nothing in the corpus produces a type error. That makes the negative tests of §7.4 the
highest-value next addition by a wide margin.

Two corrections to earlier assumptions in this document, both found by measuring:

- `pretty-text` is **not** universally stable. It is stable for 5 of 8 units; the failures are
  units that import others (`BBExec` extends `BBSpec`, `BBTest` extends `BBExec`,
  `CashDispenser` within `ucases`), where the name sets are larger and hash order shows through.
- `unchecked-absy` is stable **only in a session where nothing has been type-checked**. ZETA
  annotates the abstract syntax in place, so after `c` the "unchecked" dump returns the
  annotated term, whose annotation order is unstable. The harness therefore takes pre-check and
  post-check artifacts in separate sessions.

And one further asymmetry that still holds: `checked-absy`/`checked-env` come from
`zeta.util.Term.toFormat`, which is *reflection over Pizza's encoding of class cases* — it looks
up a tag field via `getTagField(type)`, walks `getDeclaredFields()`, and folds `static public
final` fields whose type equals the enclosing type. Port the ADTs to records + sealed interfaces
and that printer stops working, so even if they were stabilised the ported side would have to
reimplement it, and that printer is itself ported code. `FileDump`/`StreamDumper` are no help
either: plain `ObjectOutputStream`, whose bytes embed class names and serialVersionUIDs.

### 7.3 Root cause of the instability, and why canonicalisation only goes so far

Dumping the same target twice **within one JVM run** gives identical output, so this is
startup-dependent rather than a race. Root cause in `src/util/Name.pizza`:

```java
public int hashCode(){
  // since representations are interned, the code of the reference itself can be used
  return System.identityHashCode(repr);
}
public int compareTo(Name other){
  return System.identityHashCode(this.repr) - System.identityHashCode(other.repr);
}
```

Consequences, in increasing order of how much trouble they cause:

1. Every `Set<Name>`/`Map<Name,_>` iterates in JVM-address-dependent order, so it varies per run.
2. **`Name.compareTo` is itself identity-hash-based**, with an explicit comment that the order
   "does not necessarily coincide with the lexical ordering". There is no lexical name order in
   the system, so *sorting cannot canonicalise anything* unless the harness supplies its own key
   (the string representation). Also latent: that subtraction can overflow, so it is not even a
   valid total order — a real bug independent of determinism.
3. The instability is **probabilistic, not a fixed property of an artifact**: whether a given
   dump is affected depends on the address layout of that run. Two runs under-detect it, which
   is why `--selftest` takes `--runs` and why `--update` records a value only when two
   independent runs agree. An artifact "verified" by one lucky pair will otherwise fail every
   later check — observed exactly once, with `BBExec#bbook.unchecked-absy`.

What canonicalisation reaches (`harness/canonicalize.py`). This went considerably further than
first assumed — an earlier draft of this document claimed it would require parsing the MicroZ
expression language, which turned out to be wrong. Sorting is *sound* here precisely because
the reference's own order carries no information: there is nothing to lose. Four distinct
unordered constructs were found by repeated sampling and are now handled, each verified by
`--selftest`:

1. **Absolute paths** in locators (machine-specific).
2. **`PARENTS`, `GLOBALS`, `LOCALS`** — unordered name sets which may begin on the section
   header line and wrap across continuation lines. `PARENTS` is declared `Seq<Name>` in
   `MZ.UnitDef` but is built by walking a hash-ordered collection; measured at 3 of 12 runs.
3. **`PROPERTIES`** — unordered `;`-terminated definitions, split at brace depth 0, and the
   same for the **nested** property sequences inside schema expressions
   (`{… | A; B}` vs `{… | B; A}`; `MZ.Expr.Schema`'s third component). Only the
   brace/bar/semicolon skeleton is parsed, never the expression grammar.
4. **Bound-variable lists and generated names.** `MZ.Expr.Schema`'s second component is
   literally a `Set<Name>`, rendered after `\` and before ` | `, so sorting it is sound by
   construction. And the reducer invents fresh names with a counter whose value depends on a
   hash-ordered walk, so the same specification reduces to `%R1` in one run and `%R2` in the
   next — handled by an alpha-conversion that renumbers the generated families (`%R<n>`,
   `%c<n>`, `%a<n>`, `%l~<n>`) in order of first appearance over the already-sorted text.
   Decorations on user names (`DATE%typegen`) are deliberately left alone.

5. **Generated names numbered by order of *definition*, not first appearance.** First
   appearance fails: a generated name's first occurrence is normally in the binder list, which
   has already been sorted, so two runs map the names identically even when the underlying
   objects are swapped (`%l~2 = Interaction …` vs `%l~3 = Interaction …`). Definition sites
   work because the properties containing them are already sorted on a locator-led key, so
   walking definitions in textual order visits the same *definitions* in both runs whatever
   names they carry. Names with no definition fall back to first appearance. The binder lists
   are then sorted *after* renaming, and the top-level properties re-sorted, since renaming
   changes their keys — hence a 5-step pipeline rather than a set of independent rewrites.

Effect: **`nmz` went from 1 of 8 units baselined to 7 of 8**, including `Toolkit#Toolkit` —
the whole mathematical toolkit reduced to MicroZ.

**What is still out of reach.** `BBTest#bbook`'s `nmz`, and the reflective
`*-env`/`checked-absy` renderings for the original reason: hash-ordered collections
interleaved with multi-line wrapped entries and no entry delimiter.

**Three honest caveats.**

- Canonicalisation can only ever *mask* a difference, never fabricate one, since it is applied
  identically to both sides — but masking is exactly the risk for the port's sensitivity, so
  the raw dumps are kept.
- `--selftest` proves *stability*, not correctness, and that gap bit twice: two successive
  versions of the rewrite silently **corrupted names** while producing perfectly stable output
  (one dropped the backslash from `\sigma`, the other turned `%a1~2` into `%a<n>~2`).
  `canonicalize.verify_nmz` now asserts the rewrite is a pure reorder-and-renumber by comparing
  name multisets, `baseline.py` runs it on every `nmz` dump, and
  `canonicalize.py --verify <dump>` does it standalone.
- The instability is probabilistic enough that the selftest's own verdict moves by an artifact
  or two between invocations, so `--update` records a value only when all `--runs` agree
  (default 3) and `baselines/EXCLUDED` remains a sample rather than a proof.

**Resolved, and not by route A.** Everything above describes the *reference* build, where the
type checker's output could not be baselined at all and half the `nmz` artifacts stayed out.
The cure was the one named here — `Name.hashCode` → `repr.hashCode()` (deterministic for
`String`) and `compareTo` → lexical on `repr` — but it was applied in the **port**, as its
first class, which route B allows after all. With that and two further defects (§9.2) the
ported build baselines all 19 units × 9 content types with nothing excluded and nothing
canonicalised. The canonicalisation above is kept because the reference is still the
differential oracle for each ported class, and it is applied to both sides, so it stays sound.
### 7.4 Inputs: there are no tests

`find` for `*test*`, `*.exp`, `*regress*` across the whole tree returns **nothing**. There is no
harness and no baseline. What exists is a corpus of **~3,374 lines of Z/LaTeX**:

| Input | Lines | Exercises |
|---|---|---|
| `lib/ztoolkits/Toolkit.tex` | 1013 | the mathematical toolkit — broadest single type-check |
| `examples/zapnotes/zapnotes.tex` | 993 | ZAP animation (needs Phase 4) |
| `examples/bbook/bbook.tex` | 522 | core Z; 3 units incl. an executable and a test section |
| `examples/ucases/*.tex` | 453 | larger structured spec, use-case style, LaTeX markup |
| `examples/graphs/Shortest.tex` | 78 | algorithmic spec |

A working reference turns "we need tests" into "we need **inputs**", which is a much easier
problem: every input is self-checking, because the reference's output *is* the expected output.
Ways to grow it, in order of expected yield:

1. **Multiply over the stage dimension.** 5 inputs × ~8 content types × ~11 units already gives
   a few hundred independent comparisons, each covering a different code path.
2. **Round-trip and idempotence**, which need no new inputs at all: `pretty-text` re-parsed must
   re-check identically, and re-pretty-printing must be a fixed point. These catch parser and
   printer regressions on their own.
3. **Mine external Z specifications.** CZT (Community Z Tools, `github.com/community-z-users/czt`)
   carries a Z corpus. **Verify before counting on it:** ZETA's Z predates ISO Standard Z and
   carries its own dialect and extensions (`.esz`/`.msz`, ZIRP, `zeta.sty` markup, `\zsection`),
   so the discriminating question is whether external specs parse unmodified. Run one CZT spec
   through the reference before promising a corpus.
4. **Negative tests.** Nothing in the corpus currently produces a type error, so the error paths
   of a 23-class type checker are entirely uncovered. Mutate the examples mechanically
   (rename a variable, drop a declaration, break an arity) and baseline the diagnostics — the
   `! ERROR[locator]` output is already precise and diffable.

### 7.5 Order of work for the port

1. Freeze the reference: Phase 3 clean build, verified against `classes.jar` (§3).
2. Fix determinism (§7.3) and build the baseline harness on top of `zdrive.py`.
3. Grow the corpus (§7.4), including negative tests. Only now is the oracle trustworthy.
4. Port node by node — `util` → `agl` → `format`/`form` → `content` → `session` → `tools/*` —
   keeping the Pizza build green for the untranslated remainder. Pizza compiles `.java` too
   (`java.makefile` already routes both through it), so a mixed tree is viable throughout.
5. Reimplement the `Term.toFormat` printer on the ported side early (§7.2); until it exists,
   the ported side can only be compared via `pretty-text` and `nmz`.

Superseded by §9 below, which records what was actually measured. Two claims made here
earlier were wrong and are corrected there: replacing `src/agl` with `java.util` is **not**
better than porting it, and the ADTs must **not** become records. Step 4's "keep the Pizza
build green for the untranslated remainder" is also dead — there is no Pizza build (§3.2); the
mixed tree is never compiled, only *linked* at run time.

---

### 7.5a Corpus: zapnotes, and where the rest would have to come from

`examples/zapnotes/zapnotes.tex` (993 lines) was the largest unused input in the archive, and it
is exactly the "pipe the documentation through the tool" idea: a ZETA *document* that is
simultaneously a specification. It yields **11 units** — `DataInput`, `FreeTypes`, `FreeTypes2`,
`Genericity`, `Nat`, `Specials`, `Stepping`, `facExec1`, `facExec2`, `facNonExec`, `queens` —
and all 11 type-check clean. Adding it took the corpus from 8 units to **19** and the baseline
set from 42 artifacts to 111, with **67 of 67 zapnotes artifacts reproducible on the first
measurement** (its units are small and import-shallow, so the name sets that drive §7.3's
instability stay small).

Other sources considered:

- **Grieskamp's dissertation**, *A Set-Based Calculus and its Implementation* (TU Berlin,
  accepted 1999-11-26), whose record states that all its formal material is written in
  type-checked Z — i.e. the thesis source *was* a ZETA document, and it introduces micro-Z
  (`nmz`, §7.1). Only the **PDF** survives, archived at
  `edocs.tu-berlin.de/diss/1999/grieskamp_wolfgang.pdf`; no `.tex` is in the ZETA archive, on
  the TU Berlin server, or anywhere in the ~16,500-entry Wayback index for
  `uebb.cs.tu-berlin.de`. Z's symbol set would not survive PDF extraction intact, so it is
  useful as the *language description* for designing tests, not as a corpus.
- **The ZETA documentation** is HTML + PostScript only; no `.tex` sources were archived.
- **CZT's Z corpus** remains untried, and still needs the dialect check of §7.4 item 3.

### 7.5b Negative tests (`harness/negtest.py`)

The positive corpus pins only the success path. This generates mutants of the real
specifications and measures which diagnostics they elicit, against a catalogue **extracted from
the checker's own source** so the denominator cannot drift: `Report.pizza` contributes 20 kinds
(one, `undeclaredInSign`, has no call site and is dead code) and `UnitChecker.pizza` emits 5
more directly, for **25** in total. `negtest.py --catalogue` prints them.

Three design constraints, each there for a measured reason:

1. **Mutations are confined to Z environments** (`zed`, `schema`, `axdef`, `gendef`,
   `zexecexpr`, `zexecpred`). A corpus `.tex` is two languages at once, and a mutation landing
   in prose or a macro definition produces a LaTeX-adaptor error — that subsystem has 13 `Diag`
   sites, more than any other tool — which would otherwise be mistaken for type-checker
   coverage. Every diagnostic is additionally classified by reporting subsystem.
2. **Classification scores every literal of a kind, not just its head**, because the heads
   overlap: `notSupported` ("currently not supported") is a substring of `DZnotSupported`, and
   `typeIncompat`, `typeIncompatInEnv` and `typeIncompatInMatch` all begin "type mismatch". The
   discriminating text sits in the later literals.
3. **Each mutant records which artifacts changed, not merely whether output differed.** A
   mutant whose `exp` equals its `exp_orig` is the interesting case — the tool did not notice —
   but that is indistinguishable from a change this harness cannot observe unless the artifact
   delta is recorded. Hence three verdicts: `detected` (a diagnostic fired),
   `silent-observable` (no diagnostic, but `pretty-text` changed), and `undetected` (neither) —
   only the last is a finding about the tool.

Layout per mutant, `tests/negative/<source>/<operator>-<nn>/`: the mutated `.tex`,
`mutation.json` (operator and site), `exp_orig`, `exp`, and `verdict.json`.

Operators: `undeclare-ref`, `dup-decl`, `swap-type`, `drop-decl`, `generic-actual`.

**First results: 53 mutants over the three sources, all 53 detected, 4 of 25 kinds covered**
(`undeclared` ×31, `incompatActualization` ×16, `redeclaration` ×15, `typeIncompat` ×2).

Two instrumentation bugs had to be fixed before those numbers meant anything, and both are
worth remembering because each produced a *confident wrong claim* first:

- A first pass reported **33 of 53 mutants as `undetected`** — apparently blind spots in the
  checker. They were not. The unit lists were hand-picked, so mutations landing in a unit
  nobody type-checked (`Specials`, `queens`, …) produced no diagnostic and no artifact change.
  Unit lists are now discovered by asking ZETA (`list units`), never maintained by hand, and
  the undetected count went to zero.
- The declaration regex was too loose: it matched free-type definitions (`INPUT ::= ...`) as
  declarations, and matched `LTX:bbook.tex(199.3)` inside a `\yields` block as a declaration of
  a name `LTX`. The junk mutants it produced were also inflating coverage to 6 kinds by
  accident. Declaration operators are now restricted to `schema`/`axdef`/`gendef`, and the
  colon must not be followed by `:` or `=`.

Also discovered: **`zexecexpr`/`zexecpred` blocks already carry `\yields` sections with expected
output** — the archive ships animator expectations in-line. Those are an oracle, not an input,
so they are never mutated; and they are a ready-made test source once ZAM is rebuilt (Phase 4).

**The gap, stated plainly: generic mutation reaches 4 of 25 kinds, and that is close to its
ceiling.** The remaining 21 need purpose-built minimal specifications, one per kind, derived
from the grammar (`ItemParser.jj`) and the toolkit — e.g. `must-be-section`/`must-be-class`
need `\zsection`-versus-class misuse, `unresolvable-unit`/`ambiguous-unit` a bad import,
`cyclic` mutual recursion, `DZnotSupported` a temporal operator, `undeclaredPos`/`undeclaredField`
tuple and binding projections, `expectedSet`/`expectedSchema` a non-set or non-schema in a
position demanding one. That is the part where working from the language description pays, and
it is the next concrete task.

Caveat on all of these: the `.exp` files pin *current* behaviour, including any bug. For a port
that is what is wanted — bug-compatible first, fixes second — but they record what 1.5 did, not
what is correct.

### 7.5c Targeted negative tests (`harness/targeted.py`) — 17 of 25 kinds covered

One minimal specification per diagnostic, written from the grammar (`ItemParser.jj`) and the
checker's call sites. **Combined with the mutation suite this covers 17 of the 25 kinds**
(`tests/targeted/<kind>/` holds the `.tex`, the `exp` transcript and a `verdict.json`).

Covered: `ambiguous-unit`, `cyclic`, `expectedSchema`, `expectedSet`, `incompatActualization`,
`incompleteActualization`, `redeclaration`, `redeclarationIncompatExprs`,
`redeclarationIncompatGenerics`, `typeIncompat`, `typeIncompatInEnv`, `typeIncompatInJoin`,
`typeIncompatInMatch`, `undeclared`, `undeclaredField`, `undeclaredPos`, `unresolvable-unit`.

Reading the call sites beat guessing every time, and three cases had to be corrected after
firing the wrong diagnostic — each correction is a fact about the checker worth recording:

- `n \in n` is a plain **`typeIncompat`**, not `expectedSet`.
- A non-schema in a schema declaration part gives **`expectedSet`**, because `elemTypeOf` runs
  first; `signOf` only demands a *signature* once the operand is already a set. So
  `expectedSchema` needs an operand that **is** a set but not a schema — a `\power \nat`
  variable, not a `\nat` one.
- `typeIncompatInEnv` comes from `checkBind` (TermChecker:603), reached via a `\theta`/decorated
  schema reference whose components clash with the enclosing scope — not from a binding display.
- `typeIncompatInMatch` is the schema **composition** path (`Op1 \semi Op2`), where the primed
  names of the first are matched against the plain names of the second.

The 8 not covered, with the reason each is out of reach:

| kind | why |
|---|---|
| `undeclaredInSign` | **dead code** — 0 call sites; unreachable by construction, so the honest denominator is 24 |
| `incompleteType` | **masked**: every input that leaves a type variable unresolved is caught earlier by the actualization check and reported as `incompleteActualization` |
| `must-be-section`, `must-be-class`, `expectedSchemaForImplicite`, `notSupported` | need **mSZ** input (classes; `notSupported`'s own texts are "mSZ item", "expression variant", "named transitions") |
| `DZnotSupported` | needs **Dynamic Z** temporal operators |
| `unit-needed` | a shell-level path (UnitChecker:312), not reachable from a specification |

The mSZ/DZ group is not a quick win. The grammar does carry the tokens
(`@@TOKEN LaTeX,Z,DZ,MSZ,LEX`), but the only LaTeX support shipped is `lib/latex/zeta.sty`,
nothing in the archive uses mSZ or DZ markup, and the mSZ-specific style is not present — so
reaching those five means reconstructing the dialect markup first. Worth noting that
`notSupported` and `DZnotSupported` are *refusals*: covering them pins that ZETA 1.5 rejects
those dialects, which is behaviour a port should preserve, not functionality it must implement.

### 7.5d The negative suites are now gates, not recorders

Both suites originally only *wrote* their expectations: `--run` recorded `exp` / `exp_orig` /
`verdict.json` and printed a classification. Nothing compared anything, and neither took
`--classes`, so neither could say whether a ported build still produced the same diagnostics.
They now have:

* `--classes <tree>` — run against a different class tree, as `baseline.py` does;
* `--check` — run and **compare** against the recording, non-zero exit on any difference. The
  transcript is compared whole; of the verdict, the fields compared are the ones that are a
  claim about the system (`verdict`, `kinds`, `subsystems`, `artifacts_changed`) and not
  `diagnostics`, whose text carries source locators that a mutation shifts without the
  classification changing.
* `targeted.py --runs N` (default 3) — a recording is kept only if all N runs agree, the same
  rule as `baseline.py --update`, and for the same reason: under the **reference** build the
  `cyclic` case reports the cycle from either end (`involving names: a,b` or `b,a`, with the
  locators swapped), so a single run records an unlucky sample that then fails every later
  check. Under the ported build it is stable across five runs, so the recordings are taken
  there — the same split as `baselines/` versus `baselines-ported/`.

Current state against the ported build: **53 mutants, all detected, 0 differ**; **17 targeted
cases, 0 differ**, eliciting 15 diagnostic kinds.

### 7.6 The baseline harness (built, working)

```sh
harness/baseline.py --selftest [--runs N]   # measure which artifacts reproduce -> baselines/EXCLUDED
harness/baseline.py --update                # record baselines (self-validating: two runs must agree)
harness/baseline.py --check                 # compare the current build against them
harness/baseline.py --check --classes DIR   # ... compare a DIFFERENT build: the port's oracle
```

`--classes` is the whole point: the same corpus runs against the 2002 reference and against a
ported implementation, and the outputs are compared artifact by artifact.

Current state — **42 artifacts, stable across three consecutive `--check` runs**:

| | count |
|---|---|
| `text`, `lang-config`, `latex-fragment` | 8 each (all units) |
| `unchecked-absy` | 4 |
| `pretty-text` | 5 |
| `nmz` | 7 (incl. `Toolkit#Toolkit`) |
| session transcripts | 3 |

9 artifacts are listed in `baselines/EXCLUDED` with the reason; all of them trace to §7.3.

Corpus: 8 units over 3 examples — `BBSpec`/`BBExec`/`BBTest`#bbook, `Toolkit#Toolkit`,
`UseCases`/`UseCasesModel`/`CashDispenser`#ucases, `Shortest#Shortest`.

Design points worth keeping:

- **One session per (unit, phase).** Sharing a session across units is measurably worse:
  processing one unit enlarges the name sets later units' output depends on, so artifacts that
  reproduce in isolation go unstable when preceded by another unit. It also makes each artifact
  an independent comparison.
- **`make <type>` pulls in whatever the target graph needs**, so no explicit `c` is required to
  produce `pretty-text`/`nmz` — and no explicit `c` must be issued before the pre-check dumps.
- Transcripts get their own session and are path-normalised. Today they are progress traces
  and amount to a smoke test; they become real type-checker coverage only once negative tests
  put `! ERROR[locator]` lines into them (§7.4).
- **Verified on both classpath forms**: `--classes <jar>` and `--classes <clean directory>`
  each reproduce 38/38. (A directory that has been used as a Pizza output directory will *not*
  work — partially-written classes break startup. Unpack `classes.jar` fresh.)

Supporting scripts: `harness/zdrive.py` (synchronous session driver, §6),
`harness/zdump.py` (dump one unit's content types), `harness/canonicalize.py` (§7.3).

## 8. Phase 5 — Modern repackaging

1. Git repo: recovered source, the binary distribution as the bootstrap/reference artifact (with
   provenance and checksums), the harness, the build scripts. Check the original `LICENSE`
   before publishing anything.
2. `Dockerfile` pinning JDK 17 (runtime) + JDK 8 (Pizza) + TeX Live + JavaCC + g++, building ZAM
   and the classes from source. Containerise *modern* tools, not a JDK 1.2 image.
3. Editor story: `lib/emacs/{zeta-mode,vtex-*}.el` (~50k of Emacs Lisp) targets XEmacs 20 with
   `gnuserv`/`gnudoit`. (a) Ignore it — Swing GUI + batch already work; (b) port to GNU Emacs 29+
   with `emacsclient` (`src/tools/sh/xemacs_*` documents the protocol); (c) an LSP server in
   front of `zchecker` — larger, but the right long-term answer.
4. CI running the Phase 3 build plus the §7 baselines.

---

## 9. Phase 6 — The port: strategy, and step 0 (done)

### 9.1 Strategy: ABI-preserving drop-in, bottom-up

The mixed tree is never compiled — Pizza cannot parse modern Java and there is no Pizza build
anyway (§3.2). Instead each ported package is compiled with `javac` to **binary-compatible**
classes and placed on the classpath *ahead of* `classes.jar`, so the system keeps running with
a mix of ported and 2002 classes and **the entire oracle applies after every step**:

```sh
ZETA_JAVA=recovered/tools/jdk21/bin/java \
harness/baseline.py --check --all-types \
    --classes "classes:work/ref-classes" --baselines baselines-ported
```

**JDK 21 is the target, verified**: the unmodified 2002 `classes.jar` runs on JDK 21 and
reproduces all 111 reference artifacts byte-for-byte. `Thread.stop` only throws on the
`interrupt` path, which the oracle never takes — so the §2 warning is a GUI/interactive
constraint, not a build constraint.

**The ABI cost is small and unevenly distributed** (measured with `javap -p` over the 905
reference classes): mangled method names appear in `zeta.agl` (181), `zeta.util` (1 — `Name`'s
`zeta$agl$Ordered$compareTo` bridge) and `zeta.content.text` (3); everywhere else, zero.
`pizza.support.Closure` appears in public signatures only in `agl`, and ships inside
`classes.jar`. So compatibility is a thin, **deletable layer**: write the idiomatic method, add
a one-line bridge beside it, and delete the bridges in a final pass once no binary consumer
remains — the compiler then finds every stale caller. That is a mechanical deletion, not a
second port.

Two corrections to earlier assumptions, both load-bearing:

- **ADTs must not become records.** The checker *mutates* case fields in place —
  `Expr$UniVar.oper` is assigned during unification, likewise `Expr$UniVar.name`,
  `Fixity$Component$Operand.prio`, `UnitAbsy$ClassDef.items`. The right target is **sealed
  abstract classes with final nested case classes and type-pattern `switch`**, which is both
  idiomatic Java 21 and what Pizza's encoding already compiles to: consumers do
  `getfield Base$$tag` + `tableswitch` + `checkcast Base$Case`, and `Base$Case` nesting is
  Java's own naming, not a Pizza-ism. Keeping the `$$tag` field also keeps
  `zeta.util.Term.toFormat`'s reflective printer working (§7.2).
- **`zeta.agl` is ported, not replaced.** Binary consumers call its mangled methods with Pizza
  semantics, so `java.util` cannot be dropped in at all; its persistence and structural
  equality are load-bearing; and its iteration order determines the textual order of exactly
  the dumps the canonicaliser cannot reach. Its API (`Seq`, `Set`, `Map`, `Fun`, `Option`) *is*
  the idiom worth keeping.

### 9.2 Step 0 — determinism, done: every dump type is now reproducible

Two source-level fixes, both compiled with `javac 21` and dropped in ahead of `classes.jar`:

1. **`zeta.util.Name`** (`src/zeta/util/Name.java`) — `hashCode()` now returns
   `repr.hashCode()` instead of `System.identityHashCode(repr)`, and `compareTo` compares
   representations lexically instead of subtracting identity hashes (which also removes an
   overflow bug that made it not a valid total order). The file is otherwise the original: all
   14 `case` occurrences in `Name.pizza` are ordinary `switch` labels, so it ports verbatim —
   the only ABI concession is the raw `Ordered` and the hand-written erasure bridge.
2. **`zeta.format.impl.FormatPrinter.buildHashtableFormat`** — rendered a `Hashtable` in
   iteration order, and `zeta.util.Annotation.toFormat` keys that table by freshly constructed
   `FormatLine` objects, which do not override `hashCode`. So annotation slots were emitted in
   identity-hash order. Now sorted by the key's rendered text: deterministic, meaningful
   (keys read `<type> <slotName>`), and side-effect free.

A third **bug in ZETA** turned up while chasing the last flake, and it is a correctness bug,
not merely a reproducibility one. `zeta.format.FormatInfo` detects sharing and cycles while
printing a term, and it keyed its "already visited" table by
`new Integer(System.identityHashCode(ob))`:

```java
private Hashtable visited = new Hashtable(100);
private static Integer code(Object ob){ return new Integer(System.identityHashCode(ob)); }
```

An identity hash is not unique. Two *different* objects that share one collide in that table,
and the second is then reported as already visited — so the printer emits a back-reference to
an **unrelated** node and silently misrepresents the term graph. It was caught as a rare
difference in the Toolkit's `checked-absy` (1.77 MB, ~16,500 nodes, where a collision is a few
percent likely per run by the birthday bound): one run emitted `<16502> Annotation { }` where
another emitted `<11823>`, and every subsequent label shifted. After the fix all runs agree on
the *majority* output, which confirms the majority was correct and the minority corrupted.
`generalTypes` in the same class had the identical defect. Both now use an `IdentityHashMap`,
which compares by reference — which is what "has this object been visited" actually means:
exact, and incidentally deterministic.

A fourth change was needed in the harness, not the system: **one session per (unit, content
type)**. Sharing a session perturbs results twice over — processing one unit enlarges the name
sets a later unit depends on, and dumping one content type perturbs a later dump of the same
unit. Both show up as artifacts that are perfectly reproducible in isolation yet flake inside a
shared session, which is what the last stragglers turned out to be. It also removes the need
for the old pre-check/post-check split: `unchecked-absy` is only trustworthy in a session where
nothing has been checked, and that is automatic when a session does exactly one `make`.

Result:

| | reference | ported |
|---|---|---|
| `unchecked-env`, `checked-env` | never reproducible | **deterministic** |
| `checked-absy` (incl. the 206 KB `BBTest` dump) | never reproducible | **deterministic** |
| content types under the oracle | 6 | **9** |
| baselined artifacts | 111 (9 excluded) | **174** (1 excluded) |

So the type checker's full output is now under the oracle — the gap §7.2 called the most
serious. The reference baselines in `baselines/` are kept as they are; the ported build has its
own tree in `baselines-ported/`.

### 9.3 Order of work

**Measured, after finishing AGL: `zeta.agl` was essentially the whole of Pizza's generics
surface, and therefore the whole of the hard part.** Across all 526 `zeta.*` classes, only
**19** declare a mangled method, and **18 of them are in `zeta.agl`** — the nineteenth is
`zeta.content.text.Config$Stack`, with three. Every *cluster root* in the system (a base whose
mangled methods another class redeclares, so the two cannot be ported separately) is likewise
in `zeta.agl`:

```
zeta.agl.Aggregate  <- ArraySeq, BufferSeq, HashMap, HashSet
zeta.agl.Seq        <- ArraySeq, BufferSeq          zeta.agl.Set   <- HashSet
zeta.agl.Map        <- HashMap                      zeta.agl.Enum  <- the four enumerators
zeta.agl.Ordered    <- zeta.util.Name
```

All of those are now ported, so **from here the port proceeds class by class**: no further
cluster constraint exists, and the bridge layer is nearly closed. Confirmed on the two classes
everything else depends on — `zeta.util.Term` (which the whole abstract syntax extends) and
`zeta.format.Format` (which all of `format.impl` extends) have **no mangled methods at all**.

What remains in the other ~38,000 lines is not generics but the other three Pizza features,
all of them local to one class at a time: `case` ADTs (the `Option` pattern, scaled —
**359 cases in 51 multi-case ADTs**, read out of the class files into `doc/adt-tags.txt`,
plus the single-case classes, which carry no tag), `switch` over cases → pattern matching, and
`fun` literals → lambdas. The
`$$tag` convention has to survive the whole port regardless of bridges, because
`zeta.util.Term` reads it *reflectively* (`getTagField`) to implement `toFormat`, `equals` and
`intern`.

0. ✅ determinism drop-in (`Name`, `FormatPrinter`, `FormatInfo`); JDK 21; `--all-types`
   baselines.
1. ✅ `agl` — done, all 18 files, in four clusters.
2. `format` (`Format.pizza` plus `format.impl`, which is **already plain Java**) and `util`
   (`Term`, `AnnotatedTerm`, `Annotation`, `Slot`, `DebugWriter`, `VerboseWriter`, `Impure`,
   `FatalError`).
3. `content/zirp`, `content/text`, `content/nmz` — the ADT bulk. Discriminated by
   `unchecked-absy`, `checked-absy`/`checked-env` and `nmz`.
4. `session` + `form` — plain OO, little Pizza.
5. Adaptors in pipeline order, one per step: `latex` → `zparser` → `zchecker` →
   `nmzreducer` → `pp` → `sh`. Each is a leaf, and each step has a discriminating artifact
   type (`latex-fragment`, `unchecked-absy`, diagnostics, `nmz`, `pretty-text`).
6. Final pass: delete the bridges and `zeta.agl.Closures`, drop `pizza.*` from the classpath
   (replacing `pizza.lang.Pair`, which is what forces `Map`'s raw element type), replace
   `Thread.stop`. Then `nzap`/ZAM and the editor front end, neither of which the oracle
   covers.

### 9.4 What AGL is, and why it is ported rather than replaced

`zeta.agl` is the **Aggregate Library**. The primary source is its design note, *"Remarks on an
Aggregate Library for Pizza"* by Robert Büssow and Wolfgang Grieskamp, 10-97, recovered from
the Wayback Machine (`uebb.cs.tu-berlin.de/zeta/AGLDesignDecisions.html`) and saved to
`recovered/doc/AGLDesignDecisions.html`. (An earlier draft of this plan glossed AGL as
"another generic library" — that was a guess and it was wrong.)

Its stated design, which is exactly why `java.util` is not a substitute:

- **Three aggregate types only** — sequences, sets and finite maps. Bags were considered and
  deliberately excluded as rarely needed; graphs were left out as having too many
  representations, with the note that `Map<Node,Set<Node>>` serves.
- **Aggregates are values**: immutable state, "just as `java.lang.String`". The note argues
  explicitly *against* also providing mutable variants, on the grounds that it "significantly
  overloads the concept space, providing technically different interfaces for conceptually
  identical things".
- **Persistence is implemented, not just promised.** The cost of immutability is kept
  amortised-constant for single-threaded use by propagating ownership of internal storage as
  values are transformed — the *version array* technique from pure functional languages, and
  the *mbuf* technique from the BSD TCP/IP stack. `BufferSeq` is the mbuf-based sequence;
  `HashSet` and `HashMap` are hashing plus version arrays.
- The note even carries 1997 benchmarks (JDK 1.1.3, Pentium 200) putting the immutable
  versions at roughly 1.2–2.0× the mutable `java.util`/`pizza.util` equivalents.

So the library is a deliberate persistent-collections design with a documented rationale, and
its iteration order is observable in ZETA's output (§7.3). Porting it faithfully — modern
generics and lambdas, same structure and same hashing behaviour — is the correct move; swapping
in `java.util` would discard the design, change the dumps, and break the binary consumers that
still call its mangled methods during the port.

### 9.5 The ABI gate (`harness/abi_gate.py`)

The gate answers exactly one question, and resolving it precisely turned out to matter more
than it looked:

> for every symbolic reference in every class that is **still binary**, does the runtime
> classpath (ported classes first, reference behind) still provide something it resolves to?

References are read from the **constant pool** — `Fieldref` / `Methodref` /
`InterfaceMethodref` entries carry owner class, name and descriptor exactly as the verifier
will see them — and resolved by walking the supertype chain the way the JVM does.

This replaced a first version that compared declared member sets, which was both too strict
and too loose:

* **too strict.** `ArraySeq.elems` is package-private and read only by `ArraySeq`,
  `BufferSeq` and `ArraySeq`'s own enumerator. Once all three are ported, its type is free to
  change — which is the whole reason Pizza's `pizza.support.array` wrapper can be dropped from
  the sequence representation rather than threaded through the port. A declared-member
  comparison forbids that. A string search for the name `elems` cannot decide it either:
  the name also occurs as `HashSet$Change$IncludedAgg.elems`, with type `Aggregate`. Only the
  constant pool distinguishes them.
* **too loose,** on nested-class constructors: `javap` prints them as `Outer$Inner(...)`, and
  the first version mis-parsed the name, so it silently never checked them. Fixing the parse
  immediately surfaced `Option$Some.<init>` as referenced by `HashMapTab` and `Map`.

It also compares each replaced class's **own modifiers**, because resolving members is blind
to the two changes the port makes deliberately: sealing an ADT base (concrete → abstract, so a
`switch` over it can be exhaustive) and making its cases `final`. Rather than keep a list of
allowed exceptions, the gate decides from the facts, since each change is safe exactly when no
still-binary class does the thing the new modifier forbids:

| change | unsafe iff |
|---|---|
| became abstract | some binary class constructs it |
| became final | some binary class extends it |
| class ↔ interface | some binary class mentions the type at all |
| dropped abstract or final | never — the new form is less restrictive |
| **public instance fields reordered** | some binary class mentions the type at all |

That last row is not a nicety. **Field order is observable behaviour**, through
`zeta.util.Term.toFormat`: it renders a term by walking `getClass().getFields()`, skips any
name containing `$` — so `$$tag` is invisible — and picks the closing separator with
`i == subterms.length - 1`, an index into *all* the fields including the skipped ones. If the
tag happens to be last, nothing gets the closing `)` and every field gets `,`; if it is not,
the last real field gets `)`. Pizza appends `$$tag` after the fields declared in the source.

Writing it first, which reads better, is what I did in `zeta.content.text.Text` — the one
ported ADT whose base carries another instance field (`locator`). It changed **every `text`
artifact and several `absy` ones**. Nothing but the oracle could have caught it; the gate now
can.

**A third bug, and the worst of the three, surfaced only when a binary caller happened to
exercise it.** The gate identified a member as a constructor whenever its name equalled the
class's simple name — which is true of the *static case factory* of every Pizza single-case
class, since there the case name **is** the class name. So for `ContentType`, `Target`,
`DebugMessage`, `NameDecl`, `MZ.UnitDef`, `Fixity` and the rest, the gate had been quietly
filing the factory under `<init>` and was therefore unable to check it at all. It reported
`Fixity.Fixity(int,boolean,Component[])` as unresolved when the ported class had it — and
would equally have missed one that was genuinely *absent*. `zeta.tools.pp.PrettyPrinter` is
simply the first still-binary class to call such a factory. The two are now told apart by
whether a return type is present:

```
public zeta...Fixity(int, boolean, Component[])         <- constructor
public static zeta...Fixity Fixity(int, boolean, ...)   <- factory
```

Adding the field-order check immediately caught two more bugs in itself: `HashSet$Change`/`HashMap$Change`
became interfaces and were reported as unconditionally unsafe when nothing binary mentions
them, and `zeta.agl.Enum` was reported as "now abstract" because **Pizza did not set
ACC_ABSTRACT on its interfaces** while javac always does — a difference the verifier ignores.
It now prints each accepted change with the evidence:

```
zeta.agl.HashSet$Change             class <-> interface (nothing binary mentions it)
zeta.agl.HashSet$Change$Included    now final (nothing binary extends it)
zeta.session.Failures               now abstract (nothing binary constructs it)
```

It also classifies **unreachable** binary classes, which it must: Pizza compiled every `fun`
literal into a generated `X$$closures` whose only referrer was `X` itself, so porting `X`
leaves that class holding references to lifted statics that no longer exist. A dangling
reference inside a class nothing live can reach cannot fail at run time and must not fail the
gate — otherwise it cries wolf on nearly every class in AGL. Dead means *nothing mentions it
but classes that are themselves dead*, computed as a fixpoint, with `Class` constants **and**
`Utf8` strings naming a known class counted as mentions (ZETA loads its tool adaptors
reflectively from `lib/zetarc`, so they have no `Class` constant pointing at them).

Current state: **99 references checked, 0 unresolved**, 20 unresolved-but-unreachable inside
`Aggregate$$closures`, `Seq$$closures`, `Fun$$closures`, `ArraySeq$1$$anonymous_class0`.

### 9.5a Which bridges are actually exercised (`harness/bridge_users.py`)

The gate checks a bridge is *present*; it says nothing about whether anything calls it. This
tool lists, per ported class, which mangled methods a still-binary class references — so the
claim after a green oracle can be honest about what was tested rather than merely compiled:

```
zeta$agl$Aggregate$toArray      35 callers       zeta$agl$Aggregate$compare      NOBODY
zeta$agl$Aggregate$apply        30 callers       zeta$agl$Aggregate$copyInto     NOBODY
zeta$agl$Aggregate$enumerate    15 callers       zeta$agl$Aggregate$reduceWithPos NOBODY
zeta$agl$Enum$current           19 callers       zeta$agl$Option$value           NOBODY
...                                             zeta$agl$Ordered$compareTo      NOBODY
```

After the whole of `zeta.agl`: **56 of 110 bridges have a binary caller, 54 have none.**

One nuance that matters for reading that number. A bridge with no caller of its *own* does not
mean the code under it is untested: when a binary class calls `zeta$agl$Set$map` on a
`HashSet`, the base class's bridge forwards virtually and `HashSet.map` runs. What is untested
is the mangled entry point — the one-line wrapper — not the ported logic behind it. The
genuinely unexercised cases are the methods nothing calls at all under any name:
`Aggregate.compare`/`copyInto`/`reduceWithPos`, `Seq.insert`, `Map.merge`/`compose`/`getAll`,
`Option.value`, and every combinator in `Fun`.

Two further results worth recording. **`zeta.agl.Ordered` is dead**: only `Ordered` itself and
`zeta.util.Name` even mention it, and nothing calls `compareTo` through it — ordering of names
goes through `Name.lexCompare` or an explicit comparator closure. So the `Name.compareTo` fix
is a latent-defect fix, not an observable one; the observable half of that change was
`Name.hashCode`. And **the whole of `Fun` beyond `fst`/`snd` is unused** by the 2002 code
(`CheckerAdaptor` calls exactly those two), so its combinators and the `Closure`/boxing
assumptions in them are reviewed, not tested.

### 9.5b Serialization: looks like a hard constraint, turns out not to be one

**284** `zeta.*` classes are `Serializable` — 14 declare it, the rest inherit. `zeta.util.Term`
is one of the 14 and the whole abstract syntax extends `Term`; `zeta.format.Format` is another,
so all of `zeta.format.impl` is in too. If the serialized form had to be preserved, the port
would have to pin a `serialVersionUID` on all 284 **and** freeze the field layout of the entire
abstract syntax, including fields whose types are Pizza erasures — i.e. exactly what the port
exists to remove.

It does not have to be preserved, and the decisive evidence is empirical: **saving a project
does not work in the shipped build.** `Storage.create` sets `baseDir` and never creates the
directory, so `squit` dies on its first write with
`java.io.FileNotFoundException: ZETA/SMproject.n`, reproduced against the unmodified
`classes.jar`. (The command is labelled "EXPERIMENTAL" in the source, which fits.) There is
therefore no 2002-written ZETABASE for a ported build to be compatible with.

Two supporting findings: `SessionImpl.versionError` — the handler for `InvalidClassException` —
already tells the user *"the project data is reconstructable from your sources"*, so the store
is a cache by the authors' own account; and the harness never writes one regardless, because
`quit` is `session.abort()`, which does not save. (`new File("ZETA")` is relative, which is also
why `baseline.py`'s one-session-per-artifact isolation works at all.)

**So the port does not pin `serialVersionUID`.** What it does owe, recorded with the UIDs and
the full argument in `doc/serialization.md`: fix `Storage.create` to create its directory when
`zeta.tools.sman` is ported — a one-line fix that makes an advertised feature work for the
first time and cannot regress anything, since the current behaviour is an exception — keep
`versionError` reachable once stores can be written, and then add a write/read round-trip
check, which no baseline can cover.

### 9.5d ADT tags (`harness/adt_tags.py`)

Tag values are ABI, and they are the one ABI property **neither** of the other checks can see:
`abi_gate.py` resolves members and a tag is a *value*, while the oracle notices a wrong tag
only if some specification happens to exercise the mis-tagged branch. A compiled
`lookupswitch` given the wrong number does not fail to link — it quietly takes another case's
arm. With 359 tagged cases to port, that has to be mechanical.

The tool reads the numbers out of the class files, for both of the shapes Pizza emits: a case
with parameters is a subclass whose constructor calls `super(N)`, so the tag is the constant
pushed before that `invokespecial`; a case without parentheses is a constant built in the
base's `<clinit>`. (Reading the second one needs a looser match — inside a class's own code
`javap` prints a self-reference as `Method "<init>":(I)V`, with no owner, which is why a first
version found every subclass case and none of the constants.)

`--check` compares every ADT the port has replaced against the reference, and it earned its
place immediately by finding that **the archived source is not the source of the jar**:

```
zeta.session.Locator$VisitCapability.FileVisit (tag 3) left to the 2002 class file
zeta.session.Locator$VisitCapability: tag 3 is shared by FileVisit and TextVisit
                                      -- two revisions in one jar
```

The jar holds five `VisitCapability` case classes for four tags. `Locator.pizza` as archived
declares only `TextVisit`; the jar also has a `FileVisit` with the same tag, and
`zeta.tools.sh.FileVisitor` — still binary — uses it. So the recovered source is a *later*
revision in which that case was renamed, and porting from the source alone silently drops a
case that live code still calls. Nothing breaks here, because the port does not shadow
`FileVisit` and the 2002 class still extends the ported base, which is what the check now
tests for rather than reporting any absent case as a loss. But it is a standing hazard for the
`content` and `tools` packages, where the source/jar skew is likely to recur.

### 9.5c The verification loop (`harness/verify.sh`)

One step of the port is one run of this, after `harness/build.sh`:

| check | question it answers |
|---|---|
| `abi_gate.py` | does every symbolic reference from a still-binary class still resolve? |
| `baseline.py --check --baselines baselines` | does the ported build still behave like the **2002** one, on the 111 artifacts the reference itself can reproduce? |
| `baseline.py --check --all-types --baselines baselines-ported` | does it still behave like the last ported build, on all **175** — including the three content types the reference could never baseline? |
| `targeted.py --check`, `negtest.py --check` | do the type checker's **error paths** still report what they reported? The baselines cannot answer this: no input in the corpus has a type error. |

One operational rule, learned the hard way: do not run `build.sh` while a check is
running. The build wipes `classes` first (deliberately — see the script), and a running
session that loses its classpath fails in ways that look exactly like a regression.

### 9.6 Progress

| package / class | state |
|---|---|
| `zeta.util.Name` | ported, determinism fixed |
| `zeta.format.impl.FormatPrinter` | ported, ordering fixed |
| `zeta.format.FormatInfo` | ported, identity-hash collision bug fixed |
| `zeta.agl.Ordered`, `zeta.agl.Enum` | ported — the mangled-interface bridge pattern |
| `zeta.agl.Option` | ported — the first ADT, and the template for the other 358 tagged cases |
| `zeta.agl.Closures` | new — lambda ↔ `pizza.support.Closure` adapters (bridge layer, deleted with `pizza.*`) |
| `zeta.agl.Functions` | new — the two functional interfaces `java.util.function` lacks; **not** part of the bridge layer, these stay |
| `zeta.agl.Fun` | ported — the higher-order pattern |
| `zeta.agl.Aggregate` | ported — the higher-order base; `pizza.support.array` confined to two bridges |
| `zeta.agl.Seq`, `ArraySeq`, `BufferSeq` | ported **as one cluster** (see below); Pizza's array wrapper gone from the representation |
| `zeta.agl.Set`, `HashSet` (+`HashSetTab`) | ported as one cluster; version-array change record is now a sealed interface of records |
| `zeta.agl.Map`, `HashMap` (+`HashMapTab`) | ported as one cluster |
| `zeta.agl.MaxStrongComp` | ported |
| `zeta.agl.Util`, `zeta.agl.ArrayHacks` | **not ported — now dead.** They existed only to allocate and copy Pizza array wrappers, and had no callers outside the sequence classes. `abi_gate.py --verbose` confirms both in the unreachable set |
| `zeta.agl.CastHack`, `zeta.agl.Benchmarks` | never part of the system: `CastHack.pizza` declares `package test`, and neither is in the jar |
| `zeta.format.Format` | ported — 778 lines, no mangling; one `Closure` bridge on `list` |
| `zeta.util.Term` (+`TermDescr`/`TermNode`/`TermLeaf`/`TermArray`), `AnnotatedTerm` | ported — the reflective ADT machinery; `equals(null)` NPE fixed |
| `zeta.util.Slot`, `Annotation` (+`Store`), `FatalError`, `Impure`, `DebugWriter`, `VerboseWriter` | ported — `Slot` drops its `pizza.util.Hashtable` |
| `zeta.session` — all 20 files | ported; `Errors`, `Failures`, `SessionStateMessage` are now **sealed**, `Diag.Category` keeps its constants-plus-tag shape |
| `zeta.content.text` — all 5 files | ported; `Lexem`'s 106 cases **generated** (see below), `Config`'s private generic `Stack` loses its bridges |
| `zeta.content.nmz` — both files | ported; `MZ` is where mutable closure capture had to be unwound |
| `zeta.content.zirp` — **all 20 live files** | ported: the whole ADT layer, `Type` (the Z unifier), and — 2026-11-06, by two parallel subagents — `UnitEnv` (shapes verified member-identical against the jar; three 1998 defects preserved with the argument at each site: the `equalsSubs` length fall-through, and the two wrong-set `declareFixity` exclusions) and `TermVisitor` (68 visit overloads, descriptors identical; four 1998 defects preserved, incl. `visit(NameAppl[])` dropping its recursive call and the constant-true guard in `visit(Predicate.Test)`) |
| `zeta.content.zirp.DZNames`, `FileDump`, `StreamDumper`, `StreamUndumper` | **not ported — dead.** All four are in the gate's unreachable set; the last two are already plain Java |
| `zeta.form` — all 14 files | ported by an unidentified session; independently reviewed method by method against the Pizza source (2026-11-06): faithful, no findings |
| `zeta.tools` batch 1 (2026-11-06): `latex`, `zparser`, `zchecker`, `nmzreducer`, `pp`, `util`, `sman` | ported by six parallel subagents. JavaCC grammars ported to Java actions under `grammar/`, regenerated with JavaCC 3.2 by `harness/gen-parser-{latex,zparser}.sh`, generated Java kept as source. Fixed 1998 defects, each argued at its site: `Storage.create` (saving a project works for the first time), a type-checker hang on selection from an unbound type variable, two parser crashes. All six checks green (423 classes replaced); the parser additionally passes `harness/zparser-diff/` (random/mutated specs vs the 2002 parser, differences only where the 2002 parser crashes) |
| `zeta.tools` batch 2 (2026-11-07): `sh`, `nzap` | **every tool package ZETA 1.5 runs is now ported** (526 classes replaced). `nzap`: all 46 compiled `.zam` units byte-identical to the 2002 classes' output. `sh`: `MessagePrinter.printPrefixed` emits each message in one locked write (the interleaving of 6.1 is gone); `FileVisitor`/`Main` not ported — unreachable. `interrupt` during a running command still calls `Thread.stop` and on JDK 21 ends the session: the cancellation redesign is the final pass |
| ADT constants (2026-11-07) | `zeta.util.CaseConstants` + a private `readResolve` on the 16 serializable ADT bases: a deserialised constant case resolves to its canonical instance, so the port's `==` tests mean what Pizza's tag tests meant. Before: reloading a saved project crashed the parser (`unexpected case: markup kind`). Round-trip test `harness/store_roundtrip.py` (not yet a gate: see the open text-loss decision below) |
| Concurrency (2026-11-07) | three 1998 races fixed, each argued at its site: (1) the type checker wrote each item's `Used` annotation *after* handing control on, so the reducer could read it first — the NPE the 2000 code suppressed with `System.gc()` ("STRANGE BUG IN JDK 1.2"); 7.3% of runs under load before, 0/1000 after; (2) single unguarded `wait()`s in the checker's master/worker protocol, now guarded by flags; (3) the shell's `evaluations` set updated without a lock. Stress: 600 parallel sessions, 0 failures, 600 byte-identical outputs |
| **Milestone (2026-11-07): no live `zeta.*` class loads from the 2002 jar** | last ported: `zeta.format.impl` (14), `Formattable`, `ImpureUnix` (no data-structure changes, so layout output cannot move) and the XEmacs `EditorAdaptor` (not enabled here; installs identically). 175 source files, 544 classes replaced, all six checks green. What still loads from `classes.jar`: 16 classes of the Pizza runtime (`pizza.support.*`, `pizza.lang.Pair`), referenced from ported signatures — the final pass. `zeta.format.impl.FormatState`, `zeta.meta.genlexis` (a build-time generator) and the embedded Pizza compiler are unreachable and not ported |
| **Final pass (2026-11-07): the port is standalone** | 115 Pizza-mangled bridges deleted, `zeta.agl.Closures` deleted, `pizza.support.Closure`/`array`/`ExceptionWrapper`/`UndeclaredException`, `pizza.lang.Pair`/`MatchError` replaced (own `zeta.agl.Pair` with the 2002 `equals`/`hashCode`/`toString`; a typed `TopLevelForm.Action` for the form-action protocol). `build.sh` compiles with **no classpath**; `verify.sh` runs the system with **only `classes`**; no class file references `pizza/`. All six checks green. `$$tag` fields, field order, hash tables and the quicksort are kept — they are behaviour, not bridges |
| `harness/abi_gate.py` (2026-11-07) | liveness is now reachability from the roots (shell drivers, `zetarc` and `zetarc.site` plugins, all ported classes); the old "every mentioner is dead" fixpoint could not kill unreachable *cycles* and reported a false failure once `LogPanel` was ported |

**Done: `zeta.agl`, `zeta.util`, `zeta.session`, `zeta.format.Format`, `zeta.content.text`,
`zeta.content.nmz`, and the whole of `zeta.content.zirp`** — 75 ported
source files, ~16,500 lines, and the whole of the structurally hard part. Every step: ABI gate clean, **175/175** on the ported oracle
(19 units × 9 content types, nothing excluded), **111/111** on the reference oracle, and both
negative suites clean (53 mutants, 17 targeted cases). The reference oracle is the one that
proves the drop-in still behaves like the 2002 system; the negative suites are the only check
that reaches the type checker's error paths.

What is left of `zeta.util`: nothing — `Name` was step 0, and `ImpureUnix` was already Java.
What is left of `zeta.format`: the thirteen classes of `zeta.format.impl`, **already plain
Java** in 1998, of which `FormatPrinter` is ported; the rest need only generics and
collections, and `FormatState` is in the unreachable set.

Remaining Pizza, measured: `content` **0** (`UnitEnv` and `TermVisitor` landed 2026-11-06),
`form` 1,635 in 14, `meta` 595 in 1 (a code generator, not needed at run time), `tools`
26,019 in 57 — of which `nzap` is the Java side of the C++/JNI animator, now covered by the
animation oracle of §4 (`harness/zapexec.py`) rather than by the content-type baselines.

#### Generated source, kept as source (`harness/gen_lexem.py`)

`zeta.content.text.Lexem` has **106 cases** — 96 nullary constants naming Z's tokens and 10
carrying an image — and the Pizza original was itself generated, carrying
`// generated by genlexis:` markers. Transcribing that by hand is the error-prone way to port
it, and the likely error is silent: a case given the wrong tag makes a compiled
`lookupswitch` take another token's branch.

So `harness/gen_lexem.py` reads the three things that must be right from where each is
authoritative — case names, parameter types and doc comments from the `.pizza`; the string
image of each case from its `@@TOIMAGE` switch; and **every tag from the 2002 class files**. The
output is kept as ordinary reviewed source, not regenerated at build time. Regenerating `Lexem`
from the lexis description itself would mean porting `zeta.meta.genlexis`, which emits Pizza
syntax — worth doing only if the lexis ever changes.

#### Delegation, and what it did and did not catch

The ADT layer of `zeta.content.zirp` was ported by three subagents working in parallel from
`doc/port-recipe.md`, one file set each, while a fourth took the ZAM (§4). They wrote source
only; building and checking stayed in one place, because `build.sh` wipes `classes`
and two concurrent runs would corrupt each other.

That worked because the output is **mechanically checkable** — shapes against `javap`, tags
against `doc/adt-tags.txt`, behaviour against the oracle — so none of it had to be taken on
trust. Their seven files were right: identical class-file sets, identical field order on all 47
classes of `Expr`/`Predicate`, no public member lost. One of them worked out three things the
brief had not supplied (that `Expr.Decl` and `Expr.Branch` extend `AnnotatedTerm` rather than
`Term`, that `Expr.Rename` is a single-case class with no tag, and that `Expr.UnaryKind`'s
constants have a *gap* at tag 5 because `SelectiveDelta` is a class case between two
constants).

Both defects this round surfaced in the **tooling**, not the ported code: the gate's
factory-versus-constructor confusion (§9.5) and the nested-class form of the constructor-masking
trap (`doc/port-recipe.md`). Which is the argument for delegating only work whose correctness
something other than the author can decide.

#### Clusters, not classes — the one hazard that neither check would have caught

Pizza mangled with the **declaring** class, so a 2002 `ArraySeq` overriding `sort` declares
`zeta$agl$ArraySeq$sort` *plus* a `zeta$agl$Seq$sort` bridge. It overrides no plain `sort`.
Had `Seq` been ported alone with the idiomatic name carrying the implementation, a ported
caller writing `s.sort(cmp)` would have run the **base-class** version on an `ArraySeq`,
bypassing the override — no link error, nothing for the ABI gate to see, and visible to the
oracle only if that path happened to be exercised through the idiomatic name.

So the unit of porting is the **cluster**: a class together with every still-binary subclass
that overrides any of its methods. Then the idiomatic names are the real override chain and
the mangled names are pure bridges. Preconditions, both checked against the jar:

* nothing outside `zeta.agl` extends `Aggregate` — the hierarchy is exactly
  `Aggregate → {Seq → {ArraySeq, BufferSeq}, Set → HashSet, Map → HashMap}`;
* the fields the implementations share (`ArraySeq.elems`) are read by no other class.

`Aggregate` itself was safe to port alone only by luck, and it was worth verifying rather than
assuming: the only `zeta$agl$Aggregate$*` method any subclass overrides is `enumerate`, for
which the port already keeps the mangled name as the dispatch root. Everything else a subclass
overrides (`size`, `equals`, `hashCode`, `toFormat`, `compact`) was never mangled, so ordinary
virtual dispatch works.

A ported subclass still declares its **self**-mangled names (`zeta$agl$ArraySeq$subseq`),
because external callers do use those; it does not need to redeclare the base's
(`zeta$agl$Seq$subseq`), because the base's bridge forwards virtually and the gate resolves
through the hierarchy.

#### Three latent defects in the sequence classes

All three are unreachable in the archived system, which is why no baseline moves — and all
three are fixed rather than re-encoded, with the reachability argument recorded at the site:

| defect | why unreachable |
|---|---|
| `Seq.at(int)` tests `i == 1` while counting down from `index`, so the generic fallback returns element 0 for `at(1)` and throws for `at(0)` — against its own javadoc and against `head() == at(0)` | `ArraySeq` and `BufferSeq` both override `at`, and nothing else extends `Seq` |
| `ArraySeq.subseq(int,int,p)` indexes the result — length `end-at` — by the **source** index, then compares that index with the result's length; correct only for `at == 0` | nothing in the jar calls either predicate overload of `subseq`, and the plain `subseq(int,int)` that `Seq` implements in terms of it is overridden in both implementations |
| `BufferSeq.revert()` loops `while (i < j)`, copying the back half over the front half and leaving the rest `null` — neither reverted nor complete | nothing in the jar calls `revert` on any sequence |

#### Where the port is free to change shape, and where it is not

The hash tables are ported line for line -- bucket array, chaining, the `2n+1` growth rule,
`put` prepending to its chain, `rehash` walking buckets downwards, and `Enumerator` walking
buckets from the top down. Those decide the order in which a `Set<Name>` or `Map<Name,_>`
appears in `nmz`, `checked-absy` and every other dump. `java.util.LinkedHashSet` would give a
different order, not a better one.

The *change record* is the opposite case. `HashSet`/`HashMap` implement persistence by version
arrays: a mutation hands the table to a new value and records the inverse operation in the old
one, which replays it on first access (the `mbuf` technique the 1997 design note cites). In
Pizza the record was a four- and a three-case ADT compiled to `HashSet$Change`/`HashMap$Change`
plus subclasses with `$$tag`; both are package-private and the constant pools show no class
outside their own file ever named them, so they are now **sealed interfaces of records** and
`restore` is a `switch` over patterns. That is the one place in AGL where the port changes
shape rather than re-spelling, and the oracle covers it: every `include`/`exclude`/`define`
in the corpus goes through it.

`Map`'s element type is written as raw `Pair`. The source says
`Map<A,B> extends Aggregate<Pair<A,B>>`, but `pizza.lang.Pair` in the jar is not generic -- two
`Object` fields -- so `Pair<A,B>` cannot be written against it. The raw type is the honest
spelling of the erasure that is already there; the typing returns when `pizza.*` is replaced,
as one global change rather than a cast per method.

`ArraySeq`'s quicksort is ported line for line, including its `FIXME`. It is an unstable sort,
so which of two comparator-equal elements comes first is decided by the partitioning, and that
order reaches the baselines through every sorted dump; `Arrays.sort` would be a stable merge
sort and would change artifacts for no gain.

#### Pizza's arrays

A Pizza `A[]` erased to `pizza.support.array`, a wrapper with `at(int)` / `at(int,Object)`
accessors. It survives in exactly two places now, both bridges: `Aggregate`'s
`toArray`/`copyInto` (35 binary classes call `zeta$agl$Aggregate$toArray` and unwrap the
result) and `ArraySeq(pizza.support.array)` (33 callers; documented to embed the array by
reference, and `ObjectArray.toObject()` returns the backing `Object[]` itself, so unwrapping
preserves the aliasing exactly — it would not for a primitive-backed wrapper, and no `zeta.*`
class mentions any of Pizza's primitive array classes).

#### One trap that recurs at every still-binary case class

**javac cannot see the constructor of a Pizza `case` class.** Every such class carries a static
factory with the same name as the class, and in its presence

```java
new pizza.lang.Pair(a, b)        // error: cannot find symbol: constructor Pair(String,String)
new zeta.session.DebugMessage(f) // error: cannot find symbol: constructor DebugMessage(Format)
```

even fully qualified, even though `javap` shows the public constructor plainly. Constructors of
1998 Pizza classes *without* a same-named static resolve fine (`new zeta.agl.HashSet()`,
`new pizza.support.ObjectArray(...)`), so it is the factory that does it — not simple-name
obscuring, which would not apply to a qualified name anyway.

The fix is Pizza's own idiom: `Pair.Pair(a, b)`, `DebugMessage.DebugMessage(f)` — the factory's
body is that very `new`, and it is what every Pizza-compiled caller invokes.

It is about reading the 1998 class files, not about the shape: a **ported** case class has
exactly the same pair of members, and `new ProgressMessage(fmt)` compiles against it without
complaint. So the workaround is needed only while the target is still binary, and each ADT
fixes its own callers as it is ported.

#### The three patterns the rest of the port follows

*Higher-order methods* (`Fun`, and all of `Aggregate`): Pizza compiled each `fun` literal into
a generated `X$$closures` subclass of `pizza.support.Closure` dispatching to a lifted static
`$closure$...$Ns`. Public signatures therefore carry `Closure` wherever the source said
`(A)->B`. `zeta.agl.Closures` converts in both directions in one line, so ported bodies are
written with ordinary lambdas and the generated machinery simply disappears. Dropping those
lifted statics is safe where nothing outside the class references them — which `abi_gate.py`
now decides from the constant pool rather than by scanning for name strings (a `grep` aliased
to skip binary files made that check silently vacuous once; see §9.5).

**The two patterns the rest of the port follows.**

*Mangled interfaces* (`Ordered`, `Enum`): Pizza mangled exactly those methods whose signature
mentions a type parameter — `Enum.current()` is mangled, `more()` and `advance()` are not. The
mangled form stays **abstract** so 2002 classes still implement the interface, and the
idiomatic name becomes a `default` forwarding to it. A ported implementor overrides the
idiomatic method and declares a one-line bridge; a 2002 implementor supplies only the bridge
and inherits the default. Neither arrangement recurses.

*ADTs* (`Option`, and the rest): an n-ary case becomes a public nested class with
public fields plus a static factory on the base, and every instance carries
`public final int ...$$tag`, **whose values are part of the ABI** because compiled consumers
dispatch with `getfield $$tag` + `lookupswitch`. They are 1-based in declaration order. Fields
keep their erased types (`public Object value`) because callers read them directly; the typed
view is a generic accessor beside it. Internals modernise freely — `Option` now dispatches with
`instanceof` type patterns instead of a tag switch, and `HashSet`/`HashMap`'s private change
records became sealed interfaces of records.

**A nullary case compiles two different ways, and the parentheses decide which.** This is not
cosmetic — getting it wrong changes the class hierarchy:

| source | compiled form |
|---|---|
| `public case None;` | a `public static final Option None` **constant** on the base; the base is therefore **not abstract** |
| `public case RemoteMethodInvocationFailed();` | a nested **class** `Errors$RemoteMethodInvocationFailed` plus a static factory; the base **is** abstract |

Both appear in ZETA — `zeta.agl.Option.None` is the first, `zeta.session.Errors` the second —
so the port has to read the compiled form rather than the source to decide. `$$tag` must keep
its exact spelling regardless of bridges, because `zeta.util.Term.getTagField` looks it up
*by name* (§9.3).

## 10. Risks and open questions

1. **`Thread.stop()`** — 5 sites, hard-blocks JDK 20+. Pin JDK 17 now; the proper fix is
   cooperative cancellation, which is a design change (§2).
2. **Pizza needs a pre-Java-5 class library, and a Java 1.4 `rt.jar` is not readily
   obtainable** — §3. The free-artifact fallback (option B, a versioned 18-line patch) is
   available, so this is a cost, not a blocker. The port is what retires the dependency
   permanently; that is a substantive argument for doing the port.
3. **Dump non-determinism, caused by identity-hash-based `Name.hashCode`/`compareTo`** — §7.3.
   Blocks all baselining until fixed, and sorting is not a fix.
4. **No tests at all, and no negative tests** — §7.4. The type checker's error paths are the
   least-covered part of the system and the most likely to regress silently in a port.
5. **Swing from 1998 on a modern JVM** — not yet exercised; expect look-and-feel/font breakage.
   Batch mode is the functional fallback.
6. **LaTeX** — `zeta.sty` (49 KB) against TeX Live 2026, not yet exercised. `latex2html`/`noweb`
   are only needed to regenerate docs, which already exist as HTML+PS.
7. **Z symbol rendering** — `lib/fonts/*.pcf`/`.bdf` are X11 bitmap fonts for XEmacs. Modern path:
   Unicode Z symbols + a TTF, treating `vtex-input.el`'s keystroke table as the input-method spec.
8. **Missing adaptors.** The project site documented SMV, Statemate, HOL-Z and prover adaptors;
   `src/tools/ETMakefile` lists them in a disabled `xSUBNODES` and their sources are **not** in the
   archive (nor their classes in `classes.jar`). ZETA 1.5 as recovered = LaTeX adaptor, parser,
   type checker, MicroZ reducer, pretty printer, ZAP animator, shell/GUI, session manager,
   `zetasty`. The HOL-Z side (Isabelle/SML: `ZAbsy.sml`, `Z.thy`, `ZPure.thy`, `ZFinite.thy`)
   *does* survive in the same CNRS repo at `Event-B/archiv/hol-z/src/` — a separate project.
9. **Other archive snapshots.** Only the 2002-03-05 snapshot of `zetadist1_5.class` was examined;
   the Wayback CDX index for `uebb.cs.tu-berlin.de` has ~16,500 entries including the full
   `zeta/` and `zeta/zetadistdoc/` trees. Look there first for anything else missing.

---

## 11. Immediate next actions

Route **B** was chosen (§3.4) and has held up: the `Name` fix that route A was wanted for was
made in the port instead, as the first ported class, and it removed the need for
canonicalisation altogether.

1. ~~Build the baselining harness~~ — **done** (§7.6).
2. ~~Add negative tests~~ — **done** (§7.5b, §7.5c): 53 mutants, all detected; 16 targeted
   specs covering 17 of the 25 diagnostic kinds.
3. ~~Make every dump type reproducible~~ — **done** (§9.2): three source-level defects fixed;
   all 19 units × 9 content types now baseline with nothing excluded.
4. **Continue the port** (§9.6). Done: `agl`, `util`, `session`, `format.Format`,
   `content.text`, `content.nmz`, and **all of `content.zirp`** — `UnitEnv` and `TermVisitor`
   landed 2026-11-06 (two parallel subagents, shapes verified member-identical, seven 1998
   defects preserved and documented at their sites). Next, in order: `form` (1,635 lines,
   14 files) → the adaptors in pipeline order (`latex` → `zparser` → `zchecker` →
   `nmzreducer` → `pp` → `sh`), each a leaf with a discriminating artifact type — and now
   also `nzap`'s Java side, which `harness/zapexec.py` finally covers. `zeta.format.impl`'s
   thirteen classes were already plain Java in 1998 and need modernising rather than
   porting. The cluster rule no longer binds: every cluster root was in `zeta.agl` (§9.3).
5. ~~Wire `--classes` through `negtest.py` and `targeted.py`~~ — **done** (§7.5d): both now
   take `--classes` and have a real `--check` mode, and both are clean against the ported
   build.
6. ~~Pin `serialVersionUID` on the serializable classes~~ — **not needed** (§9.5b,
   `doc/serialization.md`): saving a project is broken in the shipped build, so no 2002-written
   store exists. Instead: fix `Storage.create` to create its directory when `zeta.tools.sman`
   is ported, and add a store write/read round-trip check at that point.
7. Exercise the untested surfaces: Swing GUI (`-g`), `latex`/`view` against TeX Live 2026, and
   one CZT spec through the parser (§7.4 item 3).
8. ~~Phase 4~~ — **done** (§4, 2026-11-06): the three 64-bit defects are fixed (plus a
   cached-`JNIEnv*` threading bug found on review), `ZapAdaptor` is registered in
   `lib/zetarc`, the ZAM animates `zapnotes.tex` reproducibly on aarch64 under both the
   2002 reference build and the ported build, and `harness/zapexec.py` holds the animation
   oracle: 13 blocks recorded (3-run agreement), 8 of 13 matching their 1999/2000
   `\yields` text literally, the other 5 differing only in pre-explained,
   version-dependent ways. Then stress-tested with 8-queens: the two controlled variants
   (`queens_1`, `queens_2`) find all 92 solutions in ~1.2 s; the naive `queens` exposed a
   **1998 GC write-barrier defect** — a deterministic SIGSEGV at 42 M steps
   (`Admin::reset` passed the goal instead of the slot's owner as barrier context, leaving
   a dangling root once the choice was evacuated; a second instance in `memoize`). Fixed
   at both sites (`zam/NOTES.md`); the naive search now runs cleanly past 300 M steps
   and 3,700 collections with a flat heap. Still open: the commented-out out-of-memory
   check (§4 item 6).

**The open defects found but not yet fixed.**

In Java, in code not yet ported: `MessagePrinter.printPrefixed` writes a message's prefix and
text unsynchronised, so concurrent messages interleave mid-line (§6.1); `SessionImpl.exit()`
and `abort()` both announce the shutdown, so a session told to quit *and* reaching end of input
says goodbye twice; and `Storage.create` never creates its own directory, which is why saving a
project has never worked (§9.5b).

In C++, in the ZAM: ~~the three 64-bit defects~~ fixed 2026-11-06 (§4, `zam/NOTES.md`
§1b). Still open there: the i386-tuned heap budgets and the commented-out out-of-memory
check (§4 items 6-8), and the seven preserved 1998 defects in `UnitEnv`/`TermVisitor`
(§9.6), each documented at its site with the reachability argument.

**Decided by the author (2026-11-07):** (1) after a reload, recompute non-persisted contents
from the sources; (2) replace `Thread.stop` with cooperative cancellation — `interrupt` sets
the thread's interrupt flag, long-running loops (ZAM step loop, checker, session make) check it
at safe points and stop with the same "computation interrupted" diagnostic. **Both done
2026-11-07.** (1) `LatexAdaptor.tryCompute` rescans a unit's source when its text was
delivered before a save (keeping the scan date if the files are unchanged, so sibling units'
contents are not outdated); round-trip `made` mode 19/171 → 152/171, the rest being the
cosmetic `lang-config` reorder. (2) `zeta.session.ComputationInterrupted` (an `Error`, like
the `ThreadDeath` it replaces) with checkpoints before every native ZAM step, in the session
manager's make loop, in the checker's lookups and waits (the master cancels and drains all
checker threads), and in the external-process loops (process destroyed); interrupted targets
are never marked computed, and the checker outdates the in-place-annotated `unchecked-absy`.
No live `Thread.stop` remains. Open follow-up: `EditorAdaptor.waitFor` would report an
interrupt as an I/O error (adaptor not enabled here).

**Saving and reloading a processed project loses unit text (decided: recompute).**
`UnitImpl.define` forces `persistent = false` (2000), so unit contents are never stored; but
the LaTeX adaptor stores its "text already delivered" state, so after a reload it believes the
text is current while the text itself is gone, and the reopened session produces nothing for
those units. Never visible before because saving never worked. Options: recompute
non-persisted contents after a reload (recommended: consistent with the 2000 choice), persist
the text in the adaptor, or re-enable persistent contents. Also: `lang-config` lists entries
in a different order after a reload (`LatexConfig.readObject` rebuilds hash sets) — cosmetic.

**ADT constants and deserialisation — done 2026-11-07** (see the table in 9.6); the
original note follows. Ported code
compares nullary ADT cases by identity (`kind == Expr.UnaryKind.Power`, `t.lexem ==
Lexem.Lgroup`, `kind == Text.ZedMarkupKind.Unboxed`, ...) where Pizza compiled a
`$$tag` comparison. Equivalent for in-memory objects (each constant is a singleton) but not
for deserialised ones: the bases have no `readResolve`, so a constant read back from a saved
project is a distinct object with the same tag. Unreachable while saving was broken; reachable
now that the `sman` port fixes `Storage.create`. Fix at the root rather than per call site:
one private `readResolve` per ADT base mapping a deserialised constant to its canonical
instance by tag — then `==` is exact everywhere. Do it together with the store write/read
round-trip test `doc/serialization.md` owes.

In Java, newly in scope now that the plugin is live: `ZapAdaptor` calls `Thread.stop()` on
its *external-process* path only (`rr.stop()`/`dr.stop()` in a `finally`, the `zam` shell
command) — the JNI animation path is clean on JDK 21, but the `zam` command would throw
there, and the fix belongs to the eventual `nzap` Java port.

Carried over from the port rather than found in it: `zeta.util.Term.clone()` is broken two ways
and unreachable, and `Term.intern()` plus `TermDescr` and its three cases are a complete
hash-consing facility that was never wired up — roughly 200 lines and four classes of dead
public API, kept because deleting it is the author's call.
