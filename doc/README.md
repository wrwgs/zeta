# Documentation

Two kinds of thing live here: **the ZETA documents** — the manuals, the papers and the thesis —
and **notes on the port**.

Each PDF sits beside the PostScript original it was made from. The `.ps`/`.ps.gz` is canonical;
the PDF is a convenience rendering, reproducible with `harness/make-doc-pdfs.sh`.

Where each file came from, what was searched for and established as lost, and what was examined
and excluded: `recovered-documents.md`.

## Notes on the port

| file | what |
|---|---|
| `port-recipe.md` | the conventions the ported Java follows, and why each exists |
| `serialization.md` | the 2002 store format, and why the port owes it nothing |
| `recovered-documents.md` | provenance: where every document here came from |

## The six ZETA 1.5 manuals — `zeta/`

Shipped as dvips PostScript plus LaTeX2HTML, with no `.tex` except for `zapnotes`.

| file | title | authors | date |
|---|---|---|---|
| `Z` | **The Z of ZETA** | R. Büssow, W. Grieskamp | 26 Jul 2000, v1.5 |
| `zeta` | The Operation of ZETA | W. Grieskamp | 26 Jul 2000, v1.5 |
| `guide` | The ZETA System Developers Guide (DRAFT) | R. Büssow, W. Grieskamp | 23 Jul 2000 |
| `notes` | Notes on Release 1.5 of ZETA | W. Grieskamp | 2000 |
| `zapnotes` | Notes on ZaP (DRAFT), for version 2 of ZaP | W. Grieskamp | 26 Jul 2000 |
| `install` | The Installation of ZETA | W. Grieskamp | 26 Jul 2000, v1.5 |

- `zeta/html/` — the complete LaTeX2HTML doc tree of the 1.5 distribution, including the javadoc.
- `zeta/src/` — the LaTeX and noweb sources that shipped with ZETA: `zapnotes.tex`, `zeta.nw`
  and the generated `zeta.sty`, the Z `Toolkit.tex`, and the example specifications.

## Thesis — `thesis/`

**A Set-Based Calculus and its Implementation**, W. Grieskamp, dissertation, TU Berlin FB 13,
accepted 26 Nov 1999. 174 pp. No LaTeX source has been recovered.

## Papers — `papers/`

| dir | title | authors | venue |
|---|---|---|---|
| `msz-lang97` | The μSZ Notation, Version 1.0 | Büssow, Geisler, Grieskamp, Klar | TR 97-26, TU Berlin, 1997 |
| `dynz97` | The Definition of Dynamic Z (DRAFT) | Büssow, Grieskamp, Lattemann, Lehmann | May 1997 |
| `asian97` | Combining Z and Temporal Interval Logics for the Formalization of Properties and Behaviors of Embedded Systems | Büssow, Grieskamp | ASIAN'97 |
| `fase98` | Specifying Embedded Systems with Statecharts and Z: An Agenda for Cyclic Software Components | Grieskamp, Heisel, Dörr | FASE'98 |
| `fm-trends98` | An Open Environment for the Integration of Heterogeneous Modelling Techniques and Tools | Büssow, Grieskamp, Heicking, Herrmann | Current Trends in Applied Formal Methods, 1998 |
| `zstan98` | Five proposals for the Z Standard (WG.48.1–48.5) | Grieskamp | Nov 1998 |
| `ifm99` | A Modular Framework for the Integration of Heterogeneous Notations and Tools | Büssow, Grieskamp | IFM'99 |
| `idl99` | The μZ Calculus and its Implementation | Grieskamp | IDL'99 |
| `zcompu00` | A Computation Model for Z Based on Concurrent Constraint Resolution | Grieskamp | Jan 2000 |
| `constr-prog-z00` | Constraint Programming in Z | Grieskamp, Lepper, Wieland | Mar 2000, unpublished |
| `zetawhite00` | A Modular Framework … (Extended Version) — the ZETA white paper | Büssow, Grieskamp | Jul 2000 |
| `icfem00` | Using Use Cases in Executable Z | Grieskamp, Lepper | ICFEM 2000 |
| `lpar00` | Encoding Temporal Logics in Executable Z: A Case Study for the ZETA System | Grieskamp, Lepper | LPAR 2000 |
| `apaqs01` | Testable Use Cases in the Abstract State Machine Language | Grieskamp, Lepper, Schulte, Tillmann | APAQS 2001 |

`zcompu00` holds two versions: an original PDF (18 pp) and the PostScript (19 pp), rendered
separately as `zcompu-ps.pdf`. `apaqs01` is about AsmL rather than Z, and follows on from
`icfem00`.

## Context — `other/`

| path | what |
|---|---|
| `esz/` | ESZ, the predecessor of ZETA: its homepage and the README of the ESZ/ZAP 2.2c toolkit |
| `latex-styles/` | the ESZ/ZETA LaTeX lineage: both `esz.sty` variants, `zeta.sty` and its noweb source |
| `pizza-agl/` | Grieskamp's Pizza pages, the AGL design notes and javadoc, an XEmacs pizza-mode add-on |
| `wg-homepage/` | Grieskamp's TU Berlin homepage and publication list, with 1997–2004 snapshots |
| `sheffield-z2sal/` | a third-party mirror; not original material, see its `PROVENANCE.md` |
