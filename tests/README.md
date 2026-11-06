# ZETA test suites

Negative tests for the tool chain, measured against a diagnostic catalogue extracted from the
checker's own source (`harness/negtest.py --catalogue`). See `../zesurrect-plan.md` §7.5b/§7.5c.

    ../harness/negtest.py  --run    # mutation-based: mutate real specs, classify what fires
    ../harness/targeted.py --run    # one minimal spec per diagnostic kind

`negative/<source>/<operator>-<nn>/` — a mutant, with `mutation.json` (operator and site),
`exp_orig` (the pristine transcript), `exp` (the mutant's), and `verdict.json`
(`detected` / `silent-observable` / `undetected`, plus the kinds and subsystems involved).

`targeted/<name>/` — a minimal specification, its `exp` transcript, and `verdict.json` recording
the expected kind and what actually fired.

Combined coverage is 17 of 25 kinds; the 8 remaining are accounted for individually in §7.5c
(one is dead code, one is masked by an earlier check, five need mSZ/DZ dialect markup that was
never archived, one is a shell-level path).

These files pin ZETA 1.5's *current* behaviour, bugs included. That is what a port needs —
bug-compatible first, fixes second — but they are not a statement about what is correct.
