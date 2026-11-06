# ZETA 1.5 — restored

ZETA is a Z specification framework built at TU Berlin between 1997 and 2002 by Wolfgang
Grieskamp and Robert Büssow: a typesetter, parser and type checker for the Z notation, plus
ZAP/ZAM, an animator that executes a functional sublanguage of Z.

The original source was lost. This repository is the result of recovering it — from a code
archive and the Wayback Machine — and porting it to run on current hardware.

**It runs.** All 176 source files (62,463 lines) are Java 21. No Pizza, no 2002 `classes.jar`,
no JDK 8. The C++ animator is rebuilt for aarch64.

```sh
harness/build.sh    # compile everything in one javac invocation
run/zeta -b         # batch shell -- `help` lists commands
run/zeta -g         # Swing GUI
```

## You need a JDK 21

`toolchain/` is **gitignored** — it holds a 347 MB JDK that does not belong in version
control — so a fresh clone has no JVM and `run/zeta` will fail with a bare path error. Either
place a JDK 21 at `toolchain/jdk21`, or point the launcher at your own:

```sh
ZETA_JAVA=/path/to/jdk21/bin/java run/zeta -b
```

`harness/build.sh` likewise expects `toolchain/jdk21/bin/javac`. LaTeX is optional and
only needed for typesetting output; the paths are in `run/zetabase/etc/defaults.props`.

## Layout

| path | what it is |
|---|---|
| `src/` | the Java 21 port — this is ZETA |
| `zam/` | the C++ animator (ZAM), rebuilt for aarch64; `libZAM.so` is loaded via JNI |
| `grammar/` | the JavaCC grammars the parsers are generated from |
| `run/zeta` | the launcher; every path is relative, so the repo can live anywhere |
| `run/zetabase/` | ZETABASE at runtime: `zetarc`, toolkits, LaTeX styles, `libZAM.so` |
| `examples/` | the four Z corpora — bbook, graphs, ucases, zapnotes |
| `harness/` | build and regression tooling |
| `baselines-ported/`, `tests/` | recorded expected output |
| `doc/` | all documentation: the ZETA manuals, papers and thesis, plus the notes on the port |
| `toolchain/` | JDK 21 + JavaCC (gitignored) |
| `archive/` | retired tooling: the 2002 reference build and its oracle |
| `classes/` | build output (gitignored) |

## Verifying a change

```sh
harness/build.sh    # never while a check is in flight -- it wipes classes/ first
harness/verify.sh   # ~12 minutes
```

Four checks, each catching something the others cannot:

| check | what only it catches |
|---|---|
| ported oracle | divergence in any of 175 artifacts across nine content types |
| targeted negative suite | the type checker's **error** paths — no corpus input has a type error, so the artifact baselines say nothing about them |
| mutation suite | 53 deliberately broken inputs, confirming errors are still *detected* |
| animation oracle | ZAP/ZAM execution, which the static artifacts never exercise |

`examples/` holds the inputs every baseline is derived from: editing a `.tex` there changes the
recorded output. The same goes for anything whose order is observable — field declaration
order, hash-table iteration order, the unstable quicksort in `ArraySeq`. `doc/port-recipe.md`
has the conventions; `CLAUDE.md` has the invariants worth not rediscovering.

## The recovered material

The pristine recovered archive — the 1998–2002 Pizza source, the 2002 binary distribution, the
mirrored TU Berlin web pages and the papers with their LaTeX sources — is **not in this
repository**. It is 296 MB, nothing in the build reads it, and most of it is the only copy in
existence, so it lives as a separate archive with its own README and manifest
(`zeta-recovered-2026-11-07.tar.gz`).

`doc/recovered-documents.md` is the inventory: what survived, in what form, what is
established as lost, and how each was authenticated.

## Credits

ZETA was written by Wolfgang Grieskamp and Robert Büssow at the Technische Universität Berlin,
with the ESZ/mSZ work connected to the ESPRESS project. The restoration was carried out by
Wolfgang Grieskamp with Claude (Anthropic) in 2026.

## License

Apache License 2.0 — see `LICENSE`.

Two carve-outs, both recorded in `NOTICE`:

- The package-private hashtable classes in `zeta/agl/HashMap.java` and `zeta/agl/HashSet.java`
  derive from the 1994 JDK and carry a Sun notice limiting use to **non-commercial** purposes.
  That notice is narrower than Apache 2.0 and is not overridden by it, so commercial
  redistribution means replacing them. Neither is public, but neither is a drop-in either: they
  decide hash-bucket iteration order, which is observable in ZETA's output.
- `doc/` is recovered material under mixed terms. The manuals, papers and thesis are
  publications, not software, and some carry publishers' agreements. The LaTeX and Z sources
  under `doc/zeta/src` and `doc/other/latex-styles` *are* part of ZETA. `zed.sty` and `oz.sty`
  under `doc/other/sheffield-z2sal` are third-party styles with their own restrictions.
