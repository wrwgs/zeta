# The ZETA example corpus

The four specifications that ship with ZETA 1.5, copied verbatim out of the recovered source
tree (`zeta-1.5/examples/`) so that the regression harness no longer depends on `recovered/`
being present. The archive itself now lives outside the repository.

| corpus | entry | what it is |
|---|---|---|
| `bbook` | `bbook.tex` | the birthday book, Spivey's introductory example, plus its toolkit |
| `graphs` | `Shortest.tex` | shortest paths — the smallest input |
| `ucases` | `ucases.tex` | use-case modelling (`UseCases`, `UseCasesModel`, `CashDispenser`) |
| `zapnotes` | `zapnotes.tex` | the ZAP tutorial: a document that is simultaneously a specification, and the largest input in the archive at 993 lines, 11 units |

These are **inputs to the oracle**, so they are load-bearing: `harness/baseline.py`,
`harness/negtest.py` and `harness/zapexec.py` all read them, and every recorded artifact in
`baselines-ported/` is derived from them. Editing a `.tex` here changes the baselines.
