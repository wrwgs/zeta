# Recovered documents — inventory and provenance

What exists, in what form, and what is established as lost. Written during the document-recovery
pass; counts for the Wayback mirror are as of that pass and grow as `harness/wayback_mirror.py`
is re-run (it is resumable).

> **`recovered/` is no longer in this repository.** It was archived once the port stopped
> depending on it: `zeta-recovered-2026-11-07.tar.gz` (131 MB, 2,984 files), stored outside the
> repo and on Google Drive, with its own `README.md` and `manifest.txt`. Every `recovered/...`
> path below is relative to that archive once extracted. The four Z corpora and the live
> toolchain were promoted into the repo first, as `examples/` and `toolchain/`.


## The six core ZETA documents

All six shipped as LaTeX2HTML output plus `dvips` PostScript; no `.tex` was distributed with
them. **The PostScript is the original form.** Each is present twice, identically, in
`recovered/src/zeta-1.5/doc/` and `recovered/dist/zetadist1_5/doc/`.

A PDF of *The Z of ZETA* is findable on the open web, at Sheffield — but it is a `ps2pdf`
conversion made in November 2007, not an original artifact. See
`recovered/external/sheffield-z2sal/PROVENANCE.md`; a copy is kept there.

| file | title | authors | date |
|---|---|---|---|
| `Z.ps.gz` | **The Z of ZETA** | Robert Büssow, Wolfgang Grieskamp | 26 July 2000, for v1.5 |
| `zeta.ps.gz` | The Operation of ZETA | Wolfgang Grieskamp | 26 July 2000, for v1.5 |
| `guide.ps.gz` | The ZETA System Developers Guide (DRAFT) | Robert Büssow, Wolfgang Grieskamp | 23 July 2000 |
| `notes.ps.gz` | Notes on Release 1.5 of ZETA | Wolfgang Grieskamp | — |
| `zapnotes.ps.gz` | Notes on ZaP (DRAFT) | Wolfgang Grieskamp | 26 July 2000, for ZaP v2 |
| `install.ps.gz` | The Installation of ZETA | Wolfgang Grieskamp | 26 July 2000, for v1.5 |

Titles were read out of the PostScript itself (the `(text)show` strings), not inferred from
filenames.

### The seventh document, `smv`, never shipped

`doc/index.html` links `smv.html` and `smv.ps.gz` as "The ZETA SMV Adaptor", but the entry is
**inside an HTML comment** — commented out before release. Neither file is in the distribution,
in the source tree, or anywhere in the Wayback Machine's index of the host. It was Robert
Büssow's thesis work on top of ZETA and was excluded from the Core Edition. This is not a gap
in the recovery.

## The LaTeX sources — largely recoverable

The group kept a working directory per paper at `uebb.cs.tu-berlin.de/papers/internal/<paper>`,
and the Wayback Machine **did** crawl inside them. The index holds 452 successful captures
there, including:

| kind | count |
|---|---|
| `.tex` | 100 |
| `.spec.tex` (Z specification fragments) | 9 |
| `.bib` | 12 |
| `.eps` | 17 |
| `.sty` / `.cls` | 7 |
| `.dvi` | 8 |

By paper directory: `sets` (41), `opal2` (31), `aufzug` (13), `sas97` (12), `popl98` (12),
`gi97` (11), `amast` (11), `ifl97` (8), `monadic` (6), and single files under `lncs`,
`frontend-dfg97`, `coco`.

`sets/blub/` additionally holds **RCS archives** (`sets.tex,v`, `com.tex,v`,
`syntax-macros.tex,v`, `SET.spec.tex,v`, `EXTENDEDTABLE1.spec.tex,v`, the `Config*.eps,v` and
`Windows*.eps,v` figures). An RCS `,v` file carries every committed revision, not just the last
one, so these recover the editing history of that paper as well as its final text.

> An earlier pass of this document claimed these were lost. That was wrong: it rested on a CDX
> index dump that was still being written when it was read, and which happened to contain only
> the 404 and 302 rows for these paths. The full index has 16,484 rows, 14,469 of them
> HTTP 200.

What the distribution itself never contained is still true: the `doc/` trees hold LaTeX2HTML
output (`.html` + `.gif`) and `dvips` PostScript, and zero `.tex`. The one literate source that
did ship is `recovered/src/zeta-1.5/src/tools/zetasty/zeta.nw`, the noweb file `zeta.sty` is
generated from.

## Papers

13 recovered, in `recovered/web/uebb.cs.tu-berlin.de/~wg/papers/` (4 also copied to
`recovered/papers/`): `asian97.ps.gz`, `ConstrProgInZ.ps`, `CyclicAgendaTSE.ps.gz`, `dynz.ps`,
`fm-trends.ps.gz`, `GrieskampDiss.pdf`, `idl99.ps.gz`, `ifl99.ps.gz`, `ModularFramework.ps.gz`,
`msz-lang.ps.gz`, `zcompu.pdf`, `zcompu.ps`, `zetawhite.ps.gz`.

`GrieskampDiss.pdf` is the 1999 TU Berlin PhD thesis, *A Set-Based Calculus and its
Implementation* (174 pp., title page verified).

## The department bibliography

`uebb.cs.tu-berlin.de/cgi/singlelit.cgi?citation=<key>` was the group's BibTeX front-end; **192
archived page URLs** cover it (fewer distinct citations — some keys appear under several query
spellings, e.g. `dk96a` has four). Each is the raw output of a BibTeX run and carries the full
entry — title, authors, date, venue, abstract — and, where the paper was online, a link to the
file. `harness/bib_links.py` extracts both.

This is how the two document directories below were found; neither is reachable from any
surviving index page.

## Leads followed out of the bibliography

| location | HTTP 200 / all rows | note |
|---|---|---|
| `uebb.cs.tu-berlin.de/papers/internal/` | 452 / 753 | per-paper working dirs — the LaTeX sources |
| `uebb.cs.tu-berlin.de/papers/external/` | 553 / 764 | |
| `uebb.cs.tu-berlin.de/papers/published/` | 151 / 267 | department technical reports |
| `uebb.cs.tu-berlin.de/papers/opalrelated/` | 22 / 46 | |
| `uebb.cs.tu-berlin.de/papers/diplomarbeit/` | 14 / 24 | |
| `uebb.cs.tu-berlin.de/papers/accepted/` | 12 / 16 | |
| `uebb.cs.tu-berlin.de/papers/submitted/` | 9 / 10 | |
| `tfs.cs.tu-berlin.de/~espress/doc/own/` | not yet indexed | the ESPRESS project; mSZ work |

Co-author directories also survive on the same host and are not yet mirrored: `~lepper`
(Markus Lepper, 42 captures — his own bibliography CGI, including the joint `gl00a`, `gl00b`,
`glw00a` entries), `~kd` (59), `~gk` (22).

### Known but not recovered

- `papers/internal/mSZ/mSZ.ps` (428.5 kB), listed in the 2000 directory listing; no capture.
- `papers/internal/wg/Al.ps`, `Al.dvi`, listed but not captured; title unknown.
- ESPRESS documents: `tfs.cs.tu-berlin.de/~espress/doc/own/BGGK98.ps.gz` ("Integrating Z with
  Dynamic Modeling Techniques for the Specification of Reactive Systems", 1998) and
  `www.first.gmd.de/~espress/publications/msz.ps.gz` ("mSZ – Ein Ansatz zur systematischen
  Verbindung von Z und Statecharts", TR 96-32). Not mirrored.
- The publication list cites a "ZAP User's Guide" (Dec 1998) and Dec 1998 editions of *The Z of
  ZETA* and the *Developer's Guide*. Only the 2000 editions survive.

## Tooling

* `harness/wayback_mirror.py` — resumable mirror. Two things it must do that a naive `wget`
  cannot: query every URL-key spelling of a `~` path (`~wg`, `%7Ewg`, `%257Ewg` are three
  distinct CDX keys), and fetch with the `id_` suffix for original bytes rather than the
  toolbar-injected page. It also **rejects the Archive's outage interstitial**, which is served
  with HTTP 200 and would otherwise be written over every file in the worklist, silently and
  non-resumably.
* `harness/bib_links.py` — extracts citations and document URLs from the bibliography pages.
  The anchors are emitted with newlines *inside* the tag, so a line-oriented `href="..."` match
  finds nothing.

## Curation, 2026-11-07

At the user's request, every paper under `recovered/web/.../papers/` that neither Grieskamp nor
Lepper co-authored was deleted: **604 files to 321**.

Authorship was decided by one of two positive signals, not by a name appearing in the text:

* the name inside a brace-balanced `\author{...}` or `\institute{...}` block;
* `wg` or `lepper` as the **RCS committer** in a `,v` archive.

Compressed and binary formats had to be decoded before matching or the test silently fails
shut: `published/` is almost entirely gzipped PostScript, where author names survive only as
dvips `(text)show` strings and are routinely split mid-word ("Griesk amp"). An earlier
pass that read these as opaque bytes classified every one of Grieskamp's own published papers
as unauthored — `FUJI96`, `TR95-19`, `TR96-14` among them.

The user ruled that a **commented-out byline is not authorship**, which removed `aufzug`
(`% Klaus~Didrich, Wolfgang~Grieskamp, P.~Pepper`) and `lncs` (`%% OPAL Wolfgang Grieskamp`).

Surviving: `internal/{amast,coco,fuji96,gi97,ifl97,monadic,opal2,popl98,sets}`, 37 files in
`published/` including `lpar00.ps` and `ucases.ps` (both Grieskamp & Lepper),
`accepted/PSI96-EA.ps.gz`, `opalrelated/opalwin_toc.html`.

Two corrections this pass forced, both from guessing at a directory name instead of reading the
title block: `sets` **is** co-authored (an early revision lists Pepper, Betschko, Dick,
Didrich, Faulhaber, **Grieskamp**; only the final revision narrowed to Pepper), and `fuji96`
**is** Grieskamp's (*Temporal Semantics of a Concurrency Monad*, Frauenstein/Grieskamp/Südholt,
with `wg` the second-largest committer at 42 revisions).

Nothing is lost irrecoverably: `recovered/web-index/` plus `harness/wayback_mirror.py` re-fetch
any of it.

### One file was rescued from the deletion

`papers/internal/aufzug/esz.sty` would have gone with `aufzug`. It is not a paper but a toolkit
component, and it is the **mSZ** docstrip variant of the ESZ style (v2.0c, 1997/01/02, 473
macro definitions) from the group's own tree — a different artifact from the Sheffield copy
(v3.0c, 318 macros), not a later version of it. Both, plus `zeta.sty` and `zeta.nw`, are now in
`recovered/latex-styles/` with the lineage written out. `esz.dtx`, the literate source both
`.sty` files are generated from, survives nowhere in the host index.

## Examined and excluded

- `aufzug97` — *Spezifikationstechniken für sicherheitsrelevante Systeme — Algebraische Ansätze und Z im Vergleich* (Didrich, Grieskamp, Pepper, 1997, LaTeX only): removed 2026-11-07 under the author's ruling that a commented-out byline is not authorship (its `recovered/web` copy was deleted for the same reason). Its `esz.sty` v2.0c is kept in `other/latex-styles/`.


Each was identified by its title page (PDF/PS/DVI text), `\title`, or HTML `<title>`, not by its
filename. Reasons are given per item. Overrule freely.

**Bibliography/metadata, not documents**: `web/~lepper/cgi/*` (Lepper's BibTeX pages; the
`gl00a`/`gl00b`/`glw00a`/`glst01` entries were used for the venues above); `recovered/web-index/`
(CDX index).

**Partial duplicates of `zeta/html/`**: `web/zeta/zetadistdoc/*.gif` (a subset of LaTeX2HTML images);
`web/zeta/api/` (68 kB fragment of the ZETA javadoc, subset of `zeta/html/api/`);
`web/zeta/images/java-gui.gif`.

**KORSO / Spectrum work, not ZETA**:
- `web/papers/internal/sets/`, `sets/blub/` (RCS) — "Realizing Sets by Hash Tables: How to do it in
  KORSO", Pepper, Betschko, Dick, Didrich, 1994; its `*.spec.tex` are algebraic (Spectrum-style)
  specs, not Z. Not the set-based calculus. (Its `sets.dvi` also fails `dvipdfmx` without the eps.)
- `web/papers/published/realizing-sets-by-hashtables.dvi.gz`, `TR94-30.{dvi,ps}.gz`,
  `korso-sets.ps.gz`, `sets-kompromisspfeile.ps.gz`, `sets-standardpfeile.ps.gz` — the same paper.
- `korso-method.{dvi,ps}.gz`, `TR94-36.ps.gz`, `TR94-36-alt.ps.gz` — "Korso: A Methodology for the
  Development of Correct Software" (Pepper, Wirsing et al.; Grieskamp a contributor).
- `TR93-30.dvi.gz` — "Formal Development of an Efficient Implementation of a Lexical Scanner within
  the KorSo Methodology Framework" (Betschko, Dick, Didrich, Grieskamp).
- `web/papers/internal/lncs/lncs.dvi` — "Spectrum and the functional backend Opal" (Regensburger,
  Grieskamp, Gerke, 1993).
- `web/papers/internal/peter/lexkritik.tar.gz` — LexKritik (KORSO lexer critique sources).

**OPAL language / compiler** (Grieskamp co-authored several; not ZETA):
- `web/~wg/papers/ifl99.ps.gz` — "Reflections in Opal" (IFL'99).
- `published/opal2.ps.gz`, `internal/gi97/` (ConceptOpal2), `internal/popl98/`, `internal/ifl97/` —
  "Programming in the Large … Opal 2(α)".
- `published/TR96-3.ps.gz` — "Towards a Redesign of Opal".
- `published/DesignImplOpal.{dvi,ps}.gz`, `opalrelated/DesignImplOpal.dvi.gz`, `published/Zuerich.dvi.gz` — "Opal: Design and Implementation of an Algebraic Programming Language".
- `published/ExperiencesOpalAbstract.{dvi,ps}.gz`, `opalrelated/ExperiencesOpalAbstract.dvi.gz`; `published/integrating-algebraic-and-functional.ps.gz` — OPAL system papers.
- `published/UserGuideOcs.{dvi,ps}.gz`, `opalrelated/UserGuideOcs.dvi.gz` — OCS User's Guide (Maeder, Grieskamp).
- `published/IdentifyingBugsOcs.{dvi,ps}.gz`, `opalrelated/IdentifyingBugsOcs.dvi.gz`; `published/ReportOpal.{dvi,ps}.gz`; `published/TutorialOpal.{dvi,ps}.gz`; `published/VerifyingPartI.ps.gz`, `TR94-23.{dvi,ps}.gz`, `opalrelated/VerifyingPartI.dvi.gz`; `published/DocumentationToolOpal.ps.gz`, `accepted/DocumentationToolOpal.ps.gz`, `TR96-4.ps.gz` (DOSFOP); `published/CADE14.ps.gz`, `TR99-18.ps.gz` (Didrich, compiler support for proofs); `opalrelated/monadic-parallel-interface.dvi`; `opalrelated/*.html` (OPAL overview pages); `internal/opal2/` (grammar, report, Transform.dvi); `internal/sas97/` (name resolution, Pepper/Maeder); `published/tublalr.ps` (LR parsing, Pepper); `internal/frontend-dfg97/Frontend.tex` (DFG grant proposal, Pepper: "Frontend-Techniken" for polymorphic modular languages, OPAL).

**Concurrency monad / functional agents** (Grieskamp's OPAL-era work, not Z):
`published/FUJI96.ps.gz`, `submitted/tokyo.dvi.gz` ("Temporal Semantics of a Concurrency Monad with
Choice and Services"; `dvipdfmx` also fails: font `oxsz10` missing), `TR96-14.ps.gz`,
`TR95-19.ps.gz`, `PSI96-EA.ps.gz` (published/ and accepted/), `internal/amast/` ("Communicating
Functional Agents and Graphical User Interfaces"), `internal/copal/copal.dvi` ("Semantics and
Specification Model of Concurrent Opal"), `internal/coco/coco.tex` ("Functional communicating
processes"), `internal/monadic/` ("Monadic external choice"), `internal/fuji96/RCS-*` (RCS of the
FUJI96 paper).

**Parallel programming / other department work**: `published/TR92-23`, `TR93-16`,
`gi-fachgespraech-0693`, `HICSS97`, `TR96-2`, `PROCOMET94`, `SESC95`, `WG21-0297`, `SCP96`,
`cmpp00`, `SkeletonLifting` (Pepper, Südholt, Nitsche: skeletons, data distribution algebras);
`TR94-16`, `TR94-25` (Jmaiel, temporal logic protocols); `TR95-21-tf`, `TR96-19-tf` (bisimulation);
`TR96-40` (Bohlmann); `ConcurrentFunctionalProcesses` (Loogen et al.); `safecomp94` (PLC
language); `accepted/gratra+fp` (graph transformation); `Pepper-Slides-Institutstag-97`;
`internal/mp/nach-Karlsruhe-27sep93.tar.gz`; `internal/ifl97/ConsToPu.{ps,pdf}` (LNCS
consent-to-publish form); `internal/studienfuehrer.text`.

**Diploma/student theses (all OPAL or compiler topics)**: `diplomarbeit/Andreas_Reuleaux`
(name-space analysis), `Jacob_Schroeder.dvi` (strictness analysis), `Martin_Kempa` (parallel
linear algebra), `Olaf_Karatschai` (sharing analysis), `Torsten_Klein_2` (DosfOp),
`Ute_Goretzki` (Informix/Opal), `Wolfram_Webers` (MPI/Opal).

**Lepper's later XML / music / test work**: `~lepper/papers/asexse.ps`, `musxml000.ps` (also
`published/musxml000.ps`), `musxml110.ps`, `st4.ps`, `st5.ps`, `stson1.ps`, `stson3.ps` (music
philosophy newsletter); `published/er2001.ps`, `xse2001.ps`, `slidesddd00.ps` (D2D / XML
frontends); `published/TR02-08.ps` ("A Generic Architecture for Distributed Test Execution",
Lepper 2002 — not Z; borderline if TUB-TCI matters to you). `~lepper/*.htm` pages.

**Directory listings only** (`index.html__*`, `internal-gopher-*`) in every `papers/*` directory,
including `internal/{mSZ,wg,windows,esop96,psi96,dple97,education,TR95-19,TR96-14,TR96-2,wang}`,
which hold nothing else.

## Where each file in `doc/` came from

Origins are given as `src` = `recovered/src/zeta-1.5`, `dist` = `recovered/dist/zetadist1_5`,
`web` = `recovered/web/uebb.cs.tu-berlin.de`, all inside the archived
`zeta-recovered-2026-11-07.tar.gz`. Where the same file appeared twice, one copy was kept and
both origins are listed; identity was checked with `sha256sum`/`cmp`.

### ZETA 1.5 manuals: `zeta/`

All six were shipped as dvips PostScript + LaTeX2HTML, with no `.tex`. The originals are
byte-identical in `src/doc/` and `dist/doc/`, and the copies here are from `dist/doc/`.

| file | title | authors | date | formats | notes |
|---|---|---|---|---|---|
| `Z` | **The Z of ZETA** | R. Büssow, W. Grieskamp | 26 Jul 2000, v1.5 | ps.gz, pdf, html | ps2pdf, 15 pp. A 2007 third-party ps2pdf is in `other/sheffield-z2sal/` |
| `zeta` | The Operation of ZETA | W. Grieskamp | 26 Jul 2000, v1.5 | ps.gz, pdf, html | ps2pdf, 9 pp |
| `guide` | The ZETA System Developers Guide (DRAFT) | R. Büssow, W. Grieskamp | 23 Jul 2000 | ps.gz, pdf, html | ps2pdf, 13 pp |
| `notes` | Notes on Release 1.5 of ZETA | W. Grieskamp | (2000) | ps.gz, pdf, html | ps2pdf, 1 p |
| `zapnotes` | Notes on ZaP (DRAFT), for version 2 of ZaP | W. Grieskamp | 26 Jul 2000 | ps.gz, pdf, html, tex | ps2pdf, 12 pp. Source in `zeta/src/zapnotes/` |
| `install` | The Installation of ZETA | W. Grieskamp | 26 Jul 2000, v1.5 | ps.gz, pdf, html | ps2pdf, 4 pp |

`zeta/html/` is a verbatim copy of `dist/doc/` (title "ZETA distribution v1.5 (Core Edition)").
`dist/doc/` is a superset of `src/doc/`: it adds `api/index.html`, `names-pd.html`, some
`p-images`, `WARNINGS` and the LaTeX2HTML `*.old` files. Because the tree is copied verbatim,
it includes its own copies of the six `.ps.gz`, so its links work. `index.html` links an
"SMV Adaptor" document, but only inside an HTML comment. That document was never shipped
(see `doc/recovered-documents.md`).

### ZETA sources: `zeta/src/`

| path | what | origin | notes |
|---|---|---|---|
| `zapnotes/zapnotes.tex`, `zetadefs.sty`, `zetadefs.tex` | LaTeX source of *Notes on ZaP*. It is also a ZETA specification (11 units). | `src/examples/zapnotes/` (identical in `dist/examples/`) | the only shipped manual with a source |
| `zetasty/zeta.nw` | noweb literate source of `zeta.sty` (1552 lines, `zeta.nw,v 1.30`, 2000-07-22) | `src/src/tools/zetasty/` | no `noweave` on this machine, so no typeset version |
| `zetasty/zeta.sty` | `zeta.sty` v1.04 00/07/12, generated from `zeta.nw` | `src/src/tools/zetasty/` | `dist/lib/latex/zeta.sty` differs only in its RCS `$Id`/`$Revision` lines (sty rev 1.11 instead of nw rev 1.30) |
| `ztoolkit/Toolkit.tex` | the ZETA Z mathematical toolkit (`Toolkit.tex,v 1.2 2000/07/26 wg`) | `dist/lib/ztoolkits/` | `src/lib/ztoolkits/Toolkit.tex` is a **2007 HOL-Z revision** (`$Id … 2007-08-17 brucker`), not copied |
| `examples/bbook/bbook.tex` | "Test-Case Evaluation for the Birthday Book Example", W. Grieskamp | `dist/examples/bbook/` | |
| `examples/graphs/Shortest.tex` | small example: relational image, domain etc. (no title) | `dist/examples/graphs/` | |
| `examples/ucases/*.tex` | "Example: Use Cases in Executable Z", W. Grieskamp, M. Lepper | `dist/examples/ucases/` | the example version of the ICFEM 2000 paper (`papers/icfem00/`) |

### Thesis: `thesis/`

| file | title | author | date | notes |
|---|---|---|---|---|
| `GrieskampDiss.pdf` | **A Set-Based Calculus and its Implementation** (Dissertation, TU Berlin, FB 13) | W. Grieskamp | 1999 (accepted 1999-11-26) | original PDF, 174 pp. Identical in `recovered/papers/` and `web/~wg/papers/`. No LaTeX source has been recovered. |

### Papers: `papers/`

Venues and dates come from Grieskamp's publication list (`other/wg-homepage/publications-papiere.cgi.html`)
and Lepper's bibliography pages (`web/~lepper/cgi/`), checked against each title page.

| dir | title | authors | venue / date | formats | origin | notes |
|---|---|---|---|---|---|---|
| `zcompu00` | **A Computation Model for Z Based on Concurrent Constraint Resolution** | W. Grieskamp | Jan 2000 (bib), PDF created 2000-08-09 | pdf (original), ps, pdf from ps | `web/~wg/papers/zcompu.{pdf,ps}`. The PDF is also in `recovered/papers/`. | **two versions**: original PDF 18 pp, PS (`CompuForZ.dvi`) 19 pp. `zcompu-ps.pdf` = ps2pdf of the PS |
| `idl99` | The μZ Calculus and its Implementation | W. Grieskamp | IDL'99, Sep 1999 | ps.gz, pdf | `web/~wg/papers/idl99.ps.gz` | ps2pdf, 20 pp. micro-Z |
| `zetawhite00` | A Modular Framework for the Integration of Heterogeneous Notations and Tools (Extended Version) | R. Büssow, W. Grieskamp | 27 Jul 2000 | ps.gz, pdf | `recovered/papers/` = `web/~wg/papers/` (identical) | ps2pdf, 19 pp. The ZETA white paper |
| `ifm99` | A Modular Framework for the Integration of Heterogeneous Notations and Tools | R. Büssow, W. Grieskamp | IFM'99, Jun 1999 | ps.gz, pdf | `web/~wg/papers/ModularFramework.ps.gz` | ps2pdf, 19 pp (`ifm99.dvi`). The conference version of the paper above |
| `msz-lang97` | The μSZ Notation, Version 1.0 | R. Büssow, R. Geisler, W. Grieskamp, M. Klar | TR 97-26, TU Berlin, 1997 | ps.gz, pdf | `recovered/papers/` = `web/~wg/papers/` (identical) | ps2pdf, 34 pp. ESPRESS reference manual. The μ is lost in text extraction |
| `dynz97` | The Definition of Dynamic Z (DRAFT) | R. Büssow, W. Grieskamp, F. Lattemann, E. Lehmann | 28 May 1997 | ps, pdf | `web/~wg/papers/dynz.ps` | ps2pdf, 28 pp |
| `asian97` | Combining Z and Temporal Interval Logics for the Formalization of Properties and Behaviors of Embedded Systems | R. Büssow, W. Grieskamp | ASIAN'97, pp. 46–56 | ps.gz, pdf | `web/~wg/papers/asian97.ps.gz` | ps2pdf, 12 pp |
| `fase98` | Specifying Embedded Systems with Statecharts and Z: An Agenda for Cyclic Software Components | W. Grieskamp, M. Heisel, H. Dörr | FASE'98, pp. 88–106; this PS is dated 14 Oct 1998 | ps.gz, pdf | `web/~wg/papers/CyclicAgendaTSE.ps.gz` | ps2pdf, 27 pp. The filename suggests an extended (TSE journal?) version; unverified |
| `fm-trends98` | An Open Environment for the Integration of Heterogeneous Modelling Techniques and Tools | R. Büssow, W. Grieskamp, W. Heicking, S. Herrmann | Workshop on Current Trends in Applied Formal Methods, 1998 | ps.gz, pdf | `web/~wg/papers/fm-trends.ps.gz` | ps2pdf, 11 pp |
| `constr-prog-z00` | Constraint Programming in Z | W. Grieskamp, M. Lepper, J. Wieland | Mar 2000, unpublished (Lepper's bib: "REJECTED") | ps, pdf | `web/~wg/papers/ConstrProgInZ.ps` | ps2pdf, 15 pp |
| `icfem00` | Using Use Cases in Executable Z | W. Grieskamp, M. Lepper | ICFEM 2000, Sep 2000 | ps, pdf | `web/papers/published/ucases.ps` = `web/~lepper/papers/ucases.ps` (identical) | ps2pdf, 10 pp. Example source in `zeta/src/examples/ucases/` |
| `lpar00` | Encoding Temporal Logics in Executable Z: A Case Study for the ZETA System | W. Grieskamp, M. Lepper | LPAR 2000, Nov 2000 | ps, pdf | `web/papers/published/lpar00.ps` | ps2pdf, 12 pp |
| `apaqs01` | Testable Use Cases in the Abstract State Machine Language | W. Grieskamp, M. Lepper, W. Schulte, N. Tillmann | APAQS 2001 | ps, pdf | `web/papers/published/apaqs.ps` | ps2pdf, 8 pp. **Included tentatively**: it follows on from `icfem00` but is about AsmL, not Z |
| `zstan98` | Five proposals for the Z Standard: WG.48.1 Independence of Textual Order of Sections; WG.48.2 Δ and Ξ as Operators (`deltaxi`); WG.48.3 Independence of Textual Order of Paragraphs; WG.48.4 Mutual Recursive Free Types in Different Paragraphs; WG.48.5 Multiple Declarations | W. Grieskamp | 11 Nov 1998 | ps.gz, pdf, html | `web/~wg/zstan/` | ps2pdf of each, 1–3 pp. HTML is LaTeX2HTML. WG.48.2's title symbols are lost in text extraction; Δ and Ξ were read from the LaTeX2HTML `ALT` attributes |

### Other: `other/`

| path | what | origin | notes |
|---|---|---|---|
| `esz/esz.html` | "Homepage of ESZ", the predecessor of ZETA | `web/~wg/esz.html` | |
| `esz/README-esz` | README of the ESZ/ZAP toolkit pre-release 2.2c (ESZ type checker for Z and mSZ, ZAP compiler, ZIRP, Emacs modes, esz.sty) | `web/~wg/README-esz` | |
| `pizza-agl/index.html` | "Pizza", Grieskamp's Pizza page | `web/~wg/pizza/` | |
| `pizza-agl/pizza-mode-addon.el` | XEmacs cc-mode hook for Pizza | `web/~wg/pizza/` | |
| `pizza-agl/agl/index.html` | "Aggregate Library for Pizza" | `web/~wg/pizza/agl/` | |
| `pizza-agl/agl/DesignDecisions.html` | "Remarks on an Aggregate Library for Pizza", R. Büssow, W. Grieskamp, 10-97 | `web/~wg/pizza/agl/` | text identical to `src/src/agl/DesignDecisions.html` (only the `<body>` attributes differ) |
| `pizza-agl/agl/DesignDecisions-2003-wayback.html` | the same document, a **later revision** (the performance remarks were reworded) | `recovered/doc/AGLDesignDecisions.html` (Wayback capture of `uebb…/zeta/AGLDesignDecisions.html`, 2003-06-08) | still contains the Wayback toolbar scripts |
| `pizza-agl/agl/api/` | javadoc of AGL (`espress.util.pizza.agl`) and of `espress.util.java.format` | `web/~wg/pizza/agl/api/` | the pre-ZETA package names |
| `wg-homepage/index.html`, `wg.jpeg` | Grieskamp's homepage | `web/~wg/` | |
| `wg-homepage/publications-papiere.cgi.html` | "Publication List of Wolfgang Grieskamp" (30 entries, 1991–2000) with links | `web/~wg/cgi/papiere.cgi` | source of the venues above |
| `wg-homepage/snapshots/` | homepage 1997–2001 and publication list 1999–2004 | `recovered/web-snapshots/` | |
| `sheffield-z2sal/` | third-party mirror: `ZofZeTa.pdf` (2007 ps2pdf of *The Z of ZETA*), `esz.sty` v3.0c 98/01/12, `zed.sty`, `oz.sty`, `PROVENANCE.md` | `recovered/external/sheffield-z2sal/` | not original material; see PROVENANCE. Note: an older `esz.sty` (v2.0c) does exist — see `latex-styles/` |
| `latex-styles/` | the ESZ/ZETA LaTeX style lineage: `esz-2.0c-msz.sty` (v2.0c 1997/01/02, the group's own copy, with the mSZ option), `esz-3.0c.sty` (v3.0c 98/01/12), `zeta-1.04.sty` (v1.04 00/07/12, shipped), `zeta.nw` (noweb source of `zeta.sty`); its own `README.md` explains that the two `esz.sty` are sibling docstrip outputs of one lost `esz.dtx` | `recovered/latex-styles/` (collected by the document-recovery session from `papers/internal/aufzug/`, the Sheffield mirror and the distribution) | added 2026-11-07 |
