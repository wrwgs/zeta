# Serialization in ZETA 1.5, and what the port owes it

Short answer: **nothing**. The reasoning is worth writing down, because the obvious first
answer was the opposite.

## What is serializable

284 `zeta.*` classes are `Serializable` — 14 declare it, the rest inherit it. `zeta.util.Term`
is one of the 14, and the entire abstract syntax extends `Term`: every case of
`content.zirp.Expr`, `Predicate`, `Item`, `UnitAbsy`, `content.nmz.MZ$Expr`,
`content.text.Text`, `Lexem`, and so on. `zeta.format.Format` is another, so all of
`zeta.format.impl` is serializable too. (Computed by walking supertypes across the jar; see
`harness/abi_gate.py`'s `Classpath.of`, which the script reuses.)

So if the serialized form had to be preserved, the port would have to pin a
`serialVersionUID` on 284 classes **and** freeze the field layout of the whole abstract
syntax — including fields whose types are Pizza erasures (`pizza.support.array`, raw
`pizza.lang.Pair`), which is exactly what the port exists to remove. That would be a severe,
permanent constraint.

## Why it does not have to be preserved

Three findings, in increasing order of how conclusive they are.

1. **The store is a cache, by the authors' own account.** `SessionImpl.versionError` — the
   handler for `InvalidClassException` — prints: *"The project data is incompatible with this
   version of ZETA. Please remove the project data and start again. Note that the project data
   is reconstructable from your sources."* A serialization version change is an anticipated,
   documented, recoverable event.

2. **The harness never writes a store anyway.** `quit` is `ShellForms.quitForm` →
   `session.abort()`, which by its own comment does *not* save; only `squit` ("Save & Quit
   (EXPERIMENTAL)") calls `session.exit()` → `flush()`. The project directory is
   `new File("ZETA")` — relative, so per working directory, which is why
   `baseline.py`'s one-session-per-artifact isolation works.

3. **Saving a new project does not work in the shipped build.** `Storage.create` sets
   `baseDir` and never creates the directory, so the first write fails:

   ```
   >>> squit
   + exiting, please wait while storing project data ...
   An unexpected exception has occured:
   session manager error
   java.io.FileNotFoundException: ZETA/SMproject.n (No such file or directory)
           at zeta.tools.sman.Storage.write(Storage.pizza:276)
           at zeta.tools.sman.Storage.flush(Storage.pizza:155)
           at zeta.tools.sman.SessionImpl.exit(SessionImpl.pizza:597)
   ```

   Reproduced against the unmodified `classes.jar`. Consistent with the command being
   labelled EXPERIMENTAL in the source.

So there is no ZETABASE written by the 2002 build that a ported build could need to read: the
only way to produce one was broken. **The port does not pin `serialVersionUID`.**

## What the port does owe

* **Fix `Storage.create` to create its directory** (`mkdirs()`), when `zeta.tools.sman` is
  ported. That makes an advertised feature work for the first time; it is a one-line fix and
  cannot regress anything, since the current behaviour is an exception.
* **Keep `versionError` reachable.** It fires only when `Errors.PhysicalError.exception` is an
  `InvalidClassException`. Once stores can actually be written, a store written by one ported
  build and read by a later one must land there rather than in `fatalError`.
* **Then add a round-trip check**: write a store with build A, read it with build A (must
  work), read it with build B whose serialized classes changed (must report the version error,
  not a corrupt read). No baseline can cover this — every harness run starts from a fresh
  session.

## Constant cases are canonicalised on reading

A Pizza case without parentheses (`public case Power;`) is a `public static final` constant on
a concrete base class.  Pizza matched it by comparing `$$tag`; the port compares by identity
(`kind == Expr.UnaryKind.Power`).  That is the same thing in memory -- the constant is the
only instance of the base class with its tag -- but not after `ObjectInputStream`, which
creates a fresh instance.  Once `Storage.create` was fixed this became reachable: the LaTeX
adaptor persists scanned, not yet parsed `Text` (full of `Lexem` and `Text.ZedMarkupKind`
constants), and a session reopened on a store written by `load bbook.tex; squit` died with

```
>>> make -u BBSpec#bbook nmz -d x.dump
zeta.util.FatalError: FATAL ERROR: assertion failed: unexpected case: UnitParser.parse: markup kind
        at zeta.tools.zparser.UnitParser.parse(UnitParser.java:366)
```

**PORT CHANGE.**  Every *serializable* base with constant cases declares a private
`readResolve` that calls `zeta.util.CaseConstants.resolve(this, Base.class)`: an instance of
exactly the base class is replaced by the constant with the same tag; case subclasses pass
through.  The argument for why that is complete and safe (only `<clinit>` ever creates an
exact-base instance; constants carry no state but the tag; no field is added and a private
method does not enter the default `serialVersionUID`; an unknown tag raises
`InvalidClassException`, i.e. lands in `versionError`) is in the javadoc of
`CaseConstants`.  The 16 bases: `content.text.Lexem`, `Text`, `Text.ZedMarkupKind`;
`content.zirp.DeclAttr`, `Layout`, `Expr.{Unary,Binary,Quantor}Kind`,
`Item.AttributeKind`, `Predicate.{Unary,Binary,Quantor}Kind`, `Predicate.ConstrRef`,
`Predicate.FactKind`, `State.StateKind`; `session.Diag.Category`.  Bases with constants
that are not serializable need nothing: `agl.Option`, the `zeta.form` styles and
`FileForm.Mode`, `Locator.VisitCapability`.

## What a store holds, and `harness/store_roundtrip.py`

* `squit` -> `SessionImpl.exit()` -> `flush()`: each adaptor's `flush()`, then a
  `ProjectData` (adaptor names and classes, unit names, per unit a `PersistentContents`),
  each object in its own gzipped file under `./ZETA/`.  Separate streams, so object sharing
  *across* files is not preserved.
* **Unit contents are never persisted.**  `UnitImpl.define` forces `persistent = false`
  ("serious efficiency problems with serialization"), so every `PersistentContents` is empty
  and every content type is recomputed after reopening.
* The only adaptor with persistent data is the LaTeX adaptor: search path, per source the
  file, scan time, diagnostics and `LatexConfig`; per unit the *undelivered* `Text`.
* Reopening runs `SessionImpl.initialize()` (stored adaptors, units, contents) **before**
  `zetarc`, so each `plugin` line then reports `already installed`.
* **Delivered text does not survive.**  Delivering a unit's `text` sets its
  `undeliveredText` to null, and that is what `squit` stores; the text content itself was
  transient.  A reopened session therefore cannot compute `text` -- or anything derived from
  it -- for a unit processed before the save, until the source changes ("compute called for
  up-to-date text").  A consequence of the 2000 decision to disable persistency, not of the
  port; left as is.
* `LatexConfig.readObject` rebuilds its hash collections from arrays, so the `lang-config`
  dump lists the same entries in a different order after a reload.

`store_roundtrip.py` checks both shapes: `loaded` (save right after `load`, then make each
artifact in a reopened session and compare against `baselines-ported/`) and `made` (make,
save, reopen, make again, compare against the first dump).

## The UIDs, recorded anyway

Computed with `serialver -classpath <ref-classes> <class>`, for
the 14 classes that declare `Serializable`. Kept for reference only — if a hand-made store
from some other source ever turns up, these are what it was written against.

> Both inputs that line needed are now archived rather than present: `work/ref-classes`
> (the unpacked 2002 `classes.jar`) went into `archive/zeta-port-historic-2026-11-07.tar.gz`,
> and the `classes.jar` it came from is in the recovered-material archive. Extract either to
> recompute these values; nothing in the current build needs them.

| class | serialVersionUID |
|---|---|
| `zeta.format.Format` | `6765125644190153698L` |
| `zeta.session.StorageHandle` | `-4003424689705078035L` |
| `zeta.util.Annotation` | `2817252240846168651L` |
| `zeta.util.Slot` | `3936595782732917169L` |
| `zeta.util.Term` | `8555553956863770677L` |
| `zeta.content.text.LaTeXFragment` | `-5138285088571629580L` |
| `zeta.tools.latex.LatexAdaptor$SourceInfo` | `-5751442353433819511L` |
| `zeta.tools.latex.LatexAdaptor$UnitInfo` | `1080824714416615837L` |
| `zeta.tools.latex.LatexAdaptor$PersistentData` | `-5120472058373501563L` |
| `zeta.tools.sh.GuiOptions` | `4734314436127998470L` |
| `zeta.tools.sman.Storage$Dictionary` | `-7535414439752883141L` |
| `zeta.tools.sman.UnitImpl$PersistentContentInfo` | `20328630640192074L` |
| `zeta.tools.sman.UnitImpl$PersistentContents` | `-933054521412776650L` |
| `zeta.tools.sman.SessionImpl$ProjectData` | `-1837302748931587117L` |
