# ZETA resurrection — working instructions

ZETA 1.5 is a Z specification framework from TU Berlin (1997–2002), originally written in
**Pizza** (a Java 1.1 superset with generics, algebraic data types and closures) plus a C++
animator (ZAM). The source was lost and recovered from a code archive and the Wayback Machine.

**State (2026-11-07): the port is complete.** All of ZETA runs from `src/` (Java 21, no
Pizza runtime, no 2002 `classes.jar`), and the ZAM is rebuilt for aarch64 (`zam/`).
Run it with `run/zeta -b` (batch shell) or `run/zeta -g` (GUI).

Read `zesurrect-plan.md` for the history and open items, `doc/port-recipe.md` for the
conventions the ported code follows, `doc/serialization.md` and `zam/NOTES.md` for
those two sub-problems.

## Invariants — breaking any of these costs hours

- **The recovered material no longer lives in this repo.** It was archived to
  `/home/wrw/shared/uebb/zeta-recovered-2026-11-07.tar.gz` (131 MB, 2,984 files) once the port
  stopped depending on it, and a copy is on the user's Google Drive. Its `README.md` and
  `manifest.txt` sit beside it. **Do not reconstruct `recovered/` in the repo**; extract it
  somewhere else if you need to consult it, and treat it as read-only when you do — most of it
  is the only copy in existence.
- **All documentation lives under `doc/`** -- both the recovered ZETA documents (manuals,
  papers, thesis) and the notes on the port (`port-recipe.md`, `serialization.md`,
  `recovered-documents.md`). There is no separate `docs/`.
- **`examples/` and `toolchain/` are what the build actually needs.** `examples/` holds the four
  Z corpora (bbook, graphs, ucases, zapnotes) that every baseline is derived from — editing a
  `.tex` there changes the baselines. `toolchain/` holds JDK 21 and JavaCC and is **gitignored**,
  so a fresh clone has no JVM: `run/zeta` and the harness need `ZETA_JAVA` set, or a JDK 21
  placed at `toolchain/jdk21`.
- **No Pizza source remains.** `harness/negtest.py` derives its diagnostic catalogue from the
  ported `src/zeta/tools/zchecker/Report.java`, not the 1998 `Report.pizza` (verified
  equivalent: same 20 entries, identical literals — the port's extra `show()` helpers are
  `private`, and the regex is anchored at `\n\s*static Format`). Do not reintroduce a `.pizza`
  file as a live input.
- **Never edit `run/zetabase/lib/zetarc` or `run/zetabase/etc/defaults.props`** without saying
  so explicitly and re-recording the regression baselines: `zetarc` decides which tools load,
  so changing it changes every transcript.
- **Never run `harness/build.sh` while a check is in flight.** It deletes
  `classes/` first, and a check that loses its classpath mid-run fails in ways that look
  exactly like a regression.

## Build and check

```sh
harness/build.sh          # compiles all of src/ in one javac invocation, no classpath
harness/verify.sh         # the four regression checks below, ~12 minutes
```

| check | what it catches |
|---|---|
| `baseline.py --check --all-types --baselines baselines-ported` | any change in output, all nine content types over 19 units (175 artifacts) |
| `targeted.py --check`, `negtest.py --check` | the type checker's **error** paths — no input in the corpus has a type error, so the baselines say nothing about them |
| `zapexec.py --check` | the **animator** (ZAP + `libZAM.so`): 13 `zexec` blocks of `zapnotes.tex` — nothing else runs the ZAM |

These are regression checks against recordings of the ported system. A **deliberate** change
in output is re-recorded (see the header of `harness/verify.sh`) and the recording diff
reviewed — never canonicalised away.

Retired with the completed port and archived in `archive/zeta-port-historic-2026-11-07.tar.gz`
(see `archive/README.md`): the 2002 reference baselines, `classes.jar` and its unpacked
`work/ref-classes/`, the old i386/Sparc/Win ZAM libraries, `abi_gate.py`, `adt_tags.py`,
`zparser-diff/` and the Pizza-build scripts.

A check that reports `SESSION FAILED` is the harness, not the system. Re-run it on an idle
machine before believing a `DIFFERS`.

## Environment

- JDK 21: `toolchain/jdk21/bin/java` (the harness and `run/zeta` default to it; gitignored).
- Source encoding is **latin1**, not UTF-8. Two-space indent, explicit imports, no wildcards.
- `\u` in a Java *comment* is a unicode escape and will not compile. Write `&#92;`.
- The PDFs under `doc/` are generated from the PostScript originals beside them by
  `harness/make-doc-pdfs.sh`, which needs TeX Live with `pkfix`, `pkfix-helper` and `fontware`
  (`tlmgr install pkfix pkfix-helper fontware`), plus ghostscript and ImageMagick. Set
  `TEXLIVE_BIN` if TeX Live is not in `PATH`. Do not re-render them with a plain `ps2pdf`:
  the originals use PK bitmap fonts, and Ghostscript 10 turns those into Type 3 glyphs with
  degenerate bounding boxes, which render as **blank pages** in PDF.js and so on GitHub.

## Two traps specific to this machine

- **`grep` is aliased to skip binary files in zsh**, so searching class files from an
  interactive shell silently finds nothing. Use `command grep`, or a script run with `bash`.
  This once turned a real check into a vacuous one.
- Agents/subagents must write source only. Building and checking stay in one place, because
  both serialise on `classes/`. Other Claude sessions may work in this repo too — check
  before running the build or the checks.

## Conventions for the code

Follow `doc/port-recipe.md`. Leave a `PORT CHANGE` or `PORT NOTE` comment at any site where
behaviour had to be reasoned about — especially where a 1998 defect was fixed, with the
argument for why fixing it is safe.

Do not "tidy" anything whose order or identity is observable: field declaration order,
`$$tag` fields (zeta.util.Term reads them reflectively by name), hash-table iteration order,
or the unstable quicksort in `ArraySeq` — each decides the text of some output.
