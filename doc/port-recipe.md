# Porting a Pizza class to modern Java: the recipe

Derived from the 65 files ported so far (`zeta.agl`, `zeta.util`, `zeta.session`,
`zeta.format.Format`, `zeta.content.text`, `zeta.content.nmz`). Every rule here exists because
getting it wrong broke something real; the parenthetical notes say what.

## Ground rules

1. **The 2002 class files are the specification, not the archived source.** The recovered
   `.pizza` sources are a *later* revision in places. `zeta.session.Locator$VisitCapability`
   has a case `FileVisit` in the jar that the source does not mention at all, and
   `zeta.tools.sh.FileVisitor` still calls it. Always read the shape you must match with
   `javap`, and take only names, comments and logic from the source.

2. **Never guess a `$$tag` value.** They are ABI: compiled consumers dispatch with
   `getfield $$tag` + `lookupswitch`, so a wrong number silently takes another case's branch.
   All 359 of them are tabulated in `doc/adt-tags.txt`; `harness/adt_tags.py <class>` prints
   any one of them.

3. **Keep the public shape exactly.** Same class/nested-class names, same member names, same
   erased descriptors, same field *order* (see below). Internals are free.

4. Encoding is **latin1**, not UTF-8. Indent 2 spaces, no tabs, lines under ~96 chars.

## The ADT recipe

Pizza compiled a `case` declaration in one of three ways, and which one depends on the
parentheses:

| source | compiled form |
|---|---|
| `public case Foo(T x);` | nested `public static class Foo extends Base`, public field `x`, public constructor, **and** a static factory `Base.Foo(T)` returning `Base` |
| `public case Foo();` — *empty* parens | the same, with a no-argument constructor |
| `public case Foo;` — *no* parens | a `public static final Base Foo` **constant** on the base; the base is therefore **not abstract** |

Every multi-case base also gets `public final int zeta$pkg$Class$$tag`, 1-based in declaration
order, assigned by a **package-private** `Base(int tag)` constructor. A single-case class whose
case name equals the class name gets *no* tag — just the fields, the constructor and the
factory.

```java
public class Base extends Super {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$pkg$Base$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_FOO = 1;

  Base(int tag) { this.zeta$pkg$Base$$tag = tag; }

  public static class Foo extends Base {
    public T x;
    public Foo(T x) { super(TAG_FOO); this.x = x; }
  }

  /** Pizza's generated case factory. */
  public static Base Foo(T x) { return new Foo(x); }
}
```

### Field order is observable — do not tidy it

`zeta.util.Term.toFormat` renders a term by walking `getClass().getFields()`, skips any name
containing `$` (so `$$tag` is invisible), and picks the closing separator with
`i == subterms.length - 1` — an index into *all* the fields, skipped ones included. So whether
the tag is last decides whether any field gets the closing `)`.

Pizza appends `$$tag` **after** the fields declared in the source. If the base declares its own
instance field (as `zeta.content.text.Text` declares `locator`), that field must come
**before** the tag. Declaring the tag first, which reads better, changed every `text` artifact
and several `absy` ones. `harness/abi_gate.py` now checks this.

### Sealing: only when the case set is provably closed

`sealed` turns Pizza's `switch (this)`-with-no-`default` back into legal Java, and is worth it
— but only if nothing outside the file extends the base. Checked against all 905 reference
classes: `zeta.session.Errors`, `Failures` and `SessionStateMessage` qualify;
`zeta.session.Message` and `Locator` do not, because `zeta.tools.*` extends them.

Sealing also needs the base `abstract` (a sealed-but-instantiable base is itself a possible
value, so the switch is not exhaustive) and the cases `final`. Both are ABI changes that
`abi_gate.py` judges from evidence: *became abstract* is unsafe iff something binary constructs
it, *became final* iff something extends it.

A base with a **constant** case cannot be abstract, so it cannot be sealed either. Switch on
the tag, or use type patterns with a `default` that raises
`zeta.util.FatalError.unexpectedCase("...")` — which is exactly what that method is for.

## Translating Pizza constructs

| Pizza | Java |
|---|---|
| `switch (x) { case Foo(T a): ... }` | `switch (x) { case Foo f -> ... f.a ... }`, plus `default` unless sealed |
| `case Foo(_, T a):` | type pattern; just read the field you want |
| `fun (A a)->B { ... }` | lambda. If the target is a still-binary method taking `pizza.support.Closure`, wrap with `zeta.agl.Closures.of` |
| `(A)->B` as a type | `java.util.function.Function<A,B>` (and `BiFunction`, `Consumer`, `Predicate`, `Comparator`; `zeta.agl.Functions` has the three-argument and indexed forms) |
| `f(x)` where `f` is a closure variable | `f.apply(x)` |
| `Pair<A,B>` | raw `pizza.lang.Pair`; it is not generic in the 2002 jar |
| `new Pair(a,b)`, `new DebugMessage(f)` | `Pair.Pair(a,b)`, `DebugMessage.DebugMessage(f)` — **see below** |
| `Vector` | `ArrayList` (local use only; it is not observable) |
| `enum` as an identifier | rename; it is a Java keyword since 5 |

### Two traps that are not obvious

**javac cannot see the constructor of a still-binary Pizza case class.** Each carries a static
factory with the same name as the class, and with that present `new pizza.lang.Pair(a, b)`
fails with *cannot find symbol: constructor* — even fully qualified, even though `javap` shows
the constructor. Use Pizza's own idiom, `Pair.Pair(a, b)`, whose body is that very `new`. It is
specific to reading the 1998 class files: a **ported** case class has the same pair of members
and `new Foo(...)` compiles against it fine.

It applies to **nested** case classes too, which is less obvious: `new Expr.Variable(na)` fails
while `Expr` is binary, because the factory `Expr.Variable(NameAppl)` and the nested type
`Expr.Variable` share a name inside `Expr`'s member namespace. The factory returns the base
type, so a cast is needed where the target is the case type:

```java
public static final Expr.Variable anyType =
  (Expr.Variable) Expr.Variable(new NameAppl(new Name("<any>"), new Expr[0]));
```

**A class literal is not the same bytecode it used to be.** Pizza compiled `X.class` as
`Class.forName("X")`, which **initialises** X; modern javac emits an `ldc` of a class constant,
which does **not**. That is invisible unless X's static initialiser has a side effect — and in
ZETA it sometimes does: `Layout`, `DeclAttr`, `Used`, `Act` and `Locator` all call
`Slot.register` from their initialisers, and `Slot.register` assigns indices in *registration
order*. Slot indices decide the order values sit in an `Annotation`'s chain, which decides the
order they are rendered and serialised. So a class literal naming one of those classes can
shift slot indices and move an artifact.

Nothing ported so far is affected (`ZirpContentTypes` names only `UnitAbsy` and `UnitEnv`,
neither of which has an initialiser). But if you write `X.class` where X registers a slot, say
so in your report.

**`\u` in a comment is a unicode escape.** `\usepackage` in a javadoc does not compile. Write
`&#92;usepackage`. Pizza's scanner did not care.

### Mutable capture

Pizza let a `fun` literal assign to a captured local. Java requires effectively-final. Where
the closure was only a way of spelling a fold, write the loop — it is provably the same,
because `Aggregate.apply` visits in enumeration order:

```java
// was: es.apply(fun (Expr e)->void { vs = e.vars(hidden, vs); });
for (Enum<Expr> e = es.enumerate(); e.more(); e.advance()) vs = e.current().vars(hidden, vs);
```

But where the closure's *result* matters because `Seq.map` returns a sequence of the same
implementation class as its receiver, keep the lambda and move only the flag into a
one-element array. Rewriting `MZ.Expr.subsSeq` as a loop would have changed which `Seq` class
came back.

## How work is checked

Do not run these yourself if you are a subagent; they serialise on one class tree.

| check | what it catches |
|---|---|
| `harness/build.sh` | compiles the whole ported tree; wipes `classes` first, so never run it while a check is in flight |
| `harness/abi_gate.py` | every symbolic reference from a still-binary class still resolves; plus modifier and field-order changes |
| `harness/adt_tags.py --check` | every ported ADT case still carries the reference's tag |
| `harness/baseline.py --check` ×2 | 111 artifacts against the **2002** build, 175 against the last ported build |
| `harness/targeted.py --check`, `harness/negtest.py --check` | the type checker's error paths: 17 targeted specs, 53 mutants |

`harness/verify.sh` runs all of them.
