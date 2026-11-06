# The ZETA / ESZ LaTeX style lineage

Collected here because the files are scattered across three provenances and one of them was
about to be deleted as part of a paper that is not Grieskamp's. These are toolkit components,
not papers.

| file | version | provenance |
|---|---|---|
| `esz-2.0c-msz.sty` | v2.0c, 1997/01/02 | `papers/internal/aufzug/esz.sty` on the UEBB host — the group's own tree |
| `esz-3.0c.sty` | v3.0c, 98/01/12 | a third party's working copy at Sheffield (see `../external/sheffield-z2sal/`) |
| `zeta-1.04.sty` | v1.04, 00/07/12 | shipped in the distribution |
| `zeta.nw` | `$Id: zeta.nw,v 1.30 2000/07/22 $` | shipped — the noweb source `zeta.sty` is generated from |

## The two esz.sty files are siblings, not revisions

`esz-2.0c-msz.sty` opens with a docstrip banner:

```
%% This is file `esz.sty', generated with the docstrip utility.
%% The original source files were:
%%   esz.dtx  (with options: `msz')
```

So both `.sty` files are *generated* from a single literate source `esz.dtx`, with different
docstrip options: the UEBB copy is the **mSZ** variant (473 macro definitions), the Sheffield
copy the plainer one (318). The larger file is the earlier version — size here tracks the
option set, not the date.

`esz.dtx` itself does not survive. It appears nowhere in the Wayback index of the host
(`../web-index/`), which holds only this one `esz.sty`; there is no `.dtx` or `.ins` anywhere on
`uebb.cs.tu-berlin.de`. The docstrip output is the earliest surviving form, exactly as the
compiled PostScript is for the manuals.

`README-esz` in `../web/uebb.cs.tu-berlin.de/~wg/` lists "esz.sty -- a LaTeX style for Z and the
ESPRESS notation mSZ" as a component of the ESZ/ZAP 2.2c toolkit; `esz-2.0c-msz.sty` is that
file.

## Continuity into ZETA

`zeta-1.04.sty` is the direct descendant: same `\@and`/`\@or`/`\@not` logic macros, same
`\RequirePackage{amssymb,latexsym}` opening, same `\esz@`-style internal naming conventions
carried over. Two and a half years separate `esz` v3.0c (98/01) from `zeta` v1.04 (00/07).
