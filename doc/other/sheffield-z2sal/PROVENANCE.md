# Recovered from a third-party mirror

Source: `https://staffwww.dcs.shef.ac.uk/people/A.Simons/z2sal/zdocs/`
(Anthony Simons, University of Sheffield — supporting documents for the Z2SAL project).
Fetched 2026-11-06.

This is **not** pristine ZETA material: it is a third party's working copy. Kept separate from
`recovered/src` and `recovered/dist` for that reason.

| file | what it is | status |
|---|---|---|
| `esz.sty` | ESZ LaTeX style, `\ProvidesPackage{esz}[98/01/12]`, v3.0c | **new** — not present anywhere else in this repo |
| `ZofZeTa.pdf` | "The Z of ZETA", 15 pp. | derived, see below |
| `zed.sty`, `oz.sty` | the standard Z / Object-Z LaTeX styles | context, not ZETA's own |

## `esz.sty` is the missing toolkit component

`recovered/web/uebb.cs.tu-berlin.de/~wg/README-esz` lists the ESZ/ZAP 2.2c toolkit's contents,
ending with "esz.sty -- a LaTeX style for Z and the ESPRESS notation mSZ". That file is in no
recovered archive. This is it: it defines `\MSZ`, and its preamble is line-for-line the
ancestor of the shipped `recovered/src/zeta-1.5/src/tools/zetasty/zeta.sty`
(`\ProvidesPackage{zeta}[00/07/12]`, v1.04) — same `\@and`/`\@or`/`\@not` macros, same
`\RequirePackage{amssymb,latexsym}` opening. Two and a half years separate them.

## `ZofZeTa.pdf` is a 2007 conversion, not an original

Its metadata reads `/Producer(ESP Ghostscript 815.02)` and `/CreationDate(D:20071106034411)`:
someone ran the PostScript through `ps2pdf` in November 2007. The original remains
`recovered/src/zeta-1.5/doc/Z.ps.gz` (dvips output, 26 July 2000), which has the better
provenance. The PDF is kept because it is the form that is still findable on the open web, and
because it renders without a PostScript interpreter.
