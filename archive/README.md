# Archive

`zeta-port-historic-2026-11-07.tar.gz` holds the tooling and reference data that served the
Pizza→Java port and became obsolete when the port was completed (2026-11-07). Paths inside
are relative to the repository root; extract with `tar -xzf <file>` there to restore them.

| path | what it was |
|---|---|
| `baselines/` | the 111 reference artifacts recorded from the **2002** build — the oracle that showed the port behaves like 2002 (111/111 throughout) |
| `work/ref-classes/` | the 2002 `classes.jar`, unpacked — the reference for every `javap` during the port |
| `run/zetabase/lib/classes.jar` | the 2002 binary build itself |
| `run/zetabase/lib/native/{Linux-i386,SunOS-sparc,Win}/` | the 2002 ZAM libraries (the live one is `Linux-aarch64`, rebuilt from `zam/`) |
| `harness/abi_gate.py` | checked that still-binary 2002 classes resolve against the ported ones |
| `harness/adt_tags.py`, `doc/adt-tags.txt` | checked/tabulated the Pizza `$$tag` values against the 2002 jar |
| `harness/bridge_users.py` | which Pizza-mangled bridge methods a binary class still called |
| `harness/zparser-diff/` | random/mutated specifications through the 2002 and the ported parser |
| `harness/gen_lexem.py` | generated `zeta.content.text.Lexem` (tags read from the 2002 class files) |
| `harness/apply_pizza_java5_fixes.py`, `fix_pizza_emitted_java.py`, `collect_sources.py`, `build-from-source.sh` | the attempted from-source Pizza build (plan §3) |

Not archived, deliberately: `recovered/` — the pristine recovered material (2002 source,
binary distribution, web mirror, JDKs) stays in place and unmodified; `doc/` — the curated
documentation; the live harness (`baseline.py`, `targeted.py`, `negtest.py`, `zapexec.py`,
`store_roundtrip.py`, `zdrive.py`, `canonicalize.py`) and the recordings it checks against
(`baselines-ported/`, `tests/`).

Running the archived tools again would need the 2002 classes back on disk and, for the
reference build, `recovered/tools/jdk8`.
