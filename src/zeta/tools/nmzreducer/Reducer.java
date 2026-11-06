package zeta.tools.nmzreducer;

import java.util.NoSuchElementException;
import java.util.function.Function;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.nmz.MZ;
import zeta.content.nmz.MZConstants;
import zeta.content.text.Lexem;
import zeta.content.zirp.Act;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Item;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.NameDecl;
import zeta.content.zirp.Predicate;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZNames;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.util.FatalError;
import zeta.util.Name;

/** Conversion from ZIRP terms to MZ expressions.
  *
  * @version $Id: Reducer.pizza,v 1.41 2000/07/26 13:40:21 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * The archived source is the revision the 2002 class was compiled from: every line number in
  * the class file's line tables falls on a code line of `Reducer.pizza` v1.41.
  *
  * <b>Fresh names.</b> The reducer invents variables (`%l`, `%m`, `%c`, `%a`, `%j`, ...) and
  * makes them unique with `awayof`, which appends `~n` against whatever the current context
  * chain binds. So the names in an `nmz` artifact depend on the exact order in which variables
  * enter scopes, and that order is the order of every traversal here -- including traversals
  * of hash-ordered `zeta.agl.HashMap`/`HashSet` aggregates (the scope in `exitContext`, the
  * substitution in `exitContextWithUnit`, the renaming in `rename`). Every traversal is kept in
  * its original order, over the same aggregate, with the same calls in between.
  *
  * <b>Closures.</b> Pizza let a `fun` literal assign to a captured local. Where the closure
  * was the argument of `apply` (a fold), the port writes the loop; this is the same because
  * `Aggregate.apply` visits in enumeration order and Pizza evaluated the receiver once. Where
  * the closure was the argument of `map` -- whose result keeps the receiver's implementation
  * class -- the lambda is kept and a mutable capture moves into a one-element array
  * (`composedPatterns`). Closure-typed parameters of private methods (`strictify`,
  * `composedPatterns`, `hidePatterns`, `addDecls`, `addStroke`) became
  * `java.util.function` types; nothing outside this class can name a private method.
  *
  * <b>Pattern matching.</b> Pizza dispatched on `$$tag`. A `switch` over an ADT value is kept
  * a Java `switch` -- both throw `NullPointerException` on a null selector, and that path is
  * reachable (`context.lookup` returns null for an unbound name, and `unify` then switches on
  * it). Nested sub-patterns become `when` guards, which fall through to the next case exactly
  * as a failing Pizza sub-pattern did. A sub-pattern against a constant case (`Power`,
  * `Lparenth`, `QuantorKind.Set`, ...) is an identity comparison, as in
  * `zeta.content.zirp.Type`: constant cases are singletons. Where a Pizza switch covered every
  * case of its ADT and had no `default`, Pizza generated a `default` throwing
  * `java.lang.Error`; those branches are unreachable and raise `FatalError.unexpectedCase`.
  *
  * Two 1998 defects are preserved and marked PORT NOTE: `sameSchemaSign` compares the
  * expressions rather than the signatures it computes, and `addDecls` builds the instance
  * pattern from the outer variables rather than the fresh ones.
  */
public class Reducer {

  // ========================================================================
  // Constructing a reducer


  /** ZIRP environment passed from the context. */
  private UnitEnv env;

  /** The native names declared in the environment. */
  private Set<Name> natives;

  /** The given names declared in the environment. */
  private Set<Name> givens;

  /** Make a new term reducer working on the given unit environment. */
  public Reducer(UnitEnv env) {
    this.env = env;
    UnitEnv.PragmaInfo[] pragmas = env.getPragmas();
    natives = new HashSet<Name>();
    givens = new HashSet<Name>();
    Name zapPragma = new Name("zap");
    for (int i = 0; i < pragmas.length; i++) {
      UnitEnv.PragmaInfo p = pragmas[i];
      if (p.name.equals(zapPragma) && p.value.length > 1) {
        switch (p.value[0]) {
          case Lexem.Word w -> {
            String image = w.image;
            if (image.equals("native")) {
              Pair<Name, Integer> on = parseOpName(p.value, 1);
              if (on != null)
                natives = natives.include(on.fst);
            } else if (image.equals("given")) {
              Pair<Name, Integer> on = parseOpName(p.value, 1);
              while (on != null) {
                givens = givens.include(on.fst);
                on = parseOpName(p.value, on.snd);
              }
            }
          }
          default -> { }
        }
      }
    }
  }

  // Pizza's Pair<Name,int>: `fst` is the name, `snd` the next position.
  private Pair<Name, Integer> parseOpName(Lexem[] toks, int pos) {
    int state = 0;
    String repr = "";
    while (pos < toks.length) {
      switch (state) {
        case 0: { // start
          Lexem t = toks[pos];
          if (t == Lexem.Lparenth) {
            pos++;
            state = 1;
            continue;
          } else if (t instanceof Lexem.Word w) {
            return Pair.Pair(new Name(w.image), pos + 1);
          } else {
            return null;
          }
        }
        case 1: { // components
          Lexem t = toks[pos];
          if (t == Lexem.Arg) {
            repr += "_";
            pos++;
            continue;
          } else if (t == Lexem.ListArg) {
            repr += ",,";
            pos++;
            continue;
          } else if (t instanceof Lexem.Word w) {
            repr += w.image;
            pos++;
            continue;
          } else if (t == Lexem.Rparenth) {
            return Pair.Pair(new Name(repr), pos + 1);
          } else {
            return null;
          }
        }
        default:
          return null;
      }
    }
    return null;
  }


  // ========================================================================
  // Diagnostics


  /** Diagnostics. */
  private Diag diag = new Diag();

  /** Get and clear diagnostics produced so far. */
  public Diag getAndClearDiag() {
    Diag d = diag;
    diag = new Diag();
    return d;
  }

  /** Issue an error message that the syntactic form cannot be handled. */
  private void cantHandleError(Locator where) {
    diag = diag.add(where,
                    Diag.Category.Error,
                    "cannot handle this form currently");
  }

  /** Issue a hint. */
  private void hint(Locator where, String message) {
    diag = diag.add(where,
                    Diag.Category.Hint,
                    message);
  }

  // ========================================================================
  // Retrieving Constructors

  /** A cache for holding constructor names introduced by binding types. */
  private Map<Seq<Name>, Name> bindingConsCache = new HashMap<Seq<Name>, Name>();

  /** A cache for holding constructor names introduced by tuple types. */
  private Map<Integer, Name> tupleConsCache = new HashMap<Integer, Name>();

  /** Get tuple constructor name of given arity. */
  private Name getTupleCons(int arity) {
    Name cons;
    try {
      cons = tupleConsCache.get(arity);
    }
    catch (NoSuchElementException e) {
      StringBuffer buf = new StringBuffer("(");
      for (int i = 0; i < arity; i++) {
        if (i > 0) buf.append(",_"); else buf.append("_");
      }
      buf.append(")");
      cons = new Name(buf.toString());
      tupleConsCache = tupleConsCache.define(arity, cons);
    }
    return cons;
  }

  /** Get binding constructor for given sorted sequence of field names. */
  private Name getBindingCons(Seq<Name> fields) {
    Name cons;
    try {
      cons = bindingConsCache.get(fields);
    }
    catch (NoSuchElementException e) {
      StringBuffer buf = new StringBuffer("<");
      boolean first = true;
      // was: fields.apply(fun (Name f)->void { ... first = false; ... })
      for (Enum<Name> en = fields.enumerate(); en.more(); en.advance()) {
        Name f = en.current();
        if (!first) buf.append(",");
        first = false;
        buf.append(f.toString());
        buf.append("==_");
      }
      buf.append(">");
      cons = new Name(buf.toString());
      bindingConsCache = bindingConsCache.define(fields, cons);
    }
    return cons;
  }

  // ========================================================================
  // Maintaing contexts

  /** The set of global names defined. */
  private Set<Name> defined = new HashSet<Name>();

  /** The sequence of formal given types. */
  private Seq<Name> currentFormals = new ArraySeq<Name>();


  /** Class for representing a strict context. */
  class Context {
    Map<Name, MZ.Expr> scope = new HashMap<Name, MZ.Expr>(); // name |-> Variable(name) or Expr
    Seq<MZ.Property> props = new BufferSeq<MZ.Property>();
    Set<Name> applied = new HashSet<Name>();
    Context parent;

    Context(Context parent) {
      this.parent = parent;
    }

    MZ.Expr lookup(Name name) {
      return lookup(name, false);
    }

    MZ.Expr lookup(Name name, boolean markApplied) {
      try {
        MZ.Expr expr = scope.get(name);
        if (markApplied)
          applied = applied.include(name);
        return expr;
      }
      catch (NoSuchElementException e) {
        if (parent != null)
          return parent.lookup(name, markApplied);
        else
          return null;
      }
    }

    int depth(Name name) {
      if (scope.defines(name) || parent == null)
        return 0;
      else
        return 1 + parent.depth(name);
    }

    boolean isNative(Name name) {
      try {
        return scope.get(name) != null
          && parent == null && natives.contains(name);
      }
      catch (NoSuchElementException e) {
        if (parent != null)
          return parent.isNative(name);
        else
          return false;
      }
    }

    boolean isGiven(Name name) {
      try {
        return scope.get(name) != null
          && parent == null && givens.contains(name);
      }
      catch (NoSuchElementException e) {
        if (parent != null)
          return parent.isGiven(name);
        else
          return false;
      }
    }

    boolean isBound(Name name) {
      return scope.defines(name) || parent != null && parent.isBound(name);
    }
  }

  /** The current context. */
  private Context context = null;


  /** A constant representing the empty set of names. */
  private static final Set<Name> emptyNames = new HashSet<Name>();


  /** Create a new variable expression. */
  public MZ.Expr.Variable newVar(Locator loc, String repr) {
    MZ.Expr.Variable var = new MZ.Expr.Variable(new Name(repr));
    var.locator = loc;
    context.scope = context.scope.define(var.name, var);
    return var;
  }

  /** Create a new anonymous variable expression. */
  public MZ.Expr.Variable newAnonVar(Locator loc, String repr) {
    MZ.Expr.Variable var = new MZ.Expr.Variable(awayof(new Name(repr)));
    var.locator = loc;
    context.scope = context.scope.define(var.name, var);
    return var;
  }

  /** Create a tuple of anonymous variable expressions. */
  public Seq<MZ.Expr>
  newAnonVars(Locator loc, String repr, int arity) {
    Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
    for (int i = 0; i < arity; i++) {
      vars = vars.append(newAnonVar(loc, repr + i));
    }
    return ArraySeq.from(vars);
  }

  /** Generate new variables for names. */
  private Seq<MZ.Expr> newVars(Locator locator, Seq<Name> names) {
    Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
    // was: names.apply(fun (Name n)->void { vars = vars.append(newVar(...)); })
    for (Enum<Name> e = names.enumerate(); e.more(); e.advance()) {
      vars = vars.append(newVar(locator, e.current().getRepr()));
    }
    return vars;
  }


  /** Make sequence of names to sequence of expressions. */
  private Seq<MZ.Expr> makeArgs(Seq<Name> names, Locator loc) {
    return names.<MZ.Expr>map(n -> new MZ.Expr.Variable(n).set(loc));
  }



  /** Enter a new context. */
  public void enterContext() {
    context = new Context(context);
  }


  /** Enter a generic scope. */
  private void setGenericScope(Locator locator,
                               Seq<Name> formals, Seq<Name> constants) {
    /*
    Seq<MZ.Expr> p = newVars(locator, formals);
    constants.apply(
      fun (Name n)->void {
        MZ.Expr res = new MZ.Expr.Variable(n).set(locator);
        if (!formals.isEmpty()){
          res = makeApply(locator,
                          res, makeTuple(p).set(locator)).set(locator);
        }
        addBound(n, res);
      }
    );
    */
    currentFormals = formals;
    defined = defined.includeAll(constants);
  }



  /** Exit a context, producing a schema representing the bindings in it,
      and a mapping for names from the scope which are substituted. */
  private Pair<Map<Name, MZ.Expr>, MZ.Expr>
  exitContext(MZ.Expr pat, Locator locator, boolean forceSchema) {
    if (!isPattern(pat, context.scope))
      throw FatalError.assertionFailed("no pat " + pat);
    // System.err.println("exit pat " + pat);
    // System.err.println("exit scope " + context.scope);
    Context curr = context;
    context = context.parent;
    if (curr.props == null) {
      // CHECKME hint(locator, "can never become true!");
      return Pair.Pair(new HashMap<Name, MZ.Expr>(),
                       new MZ.Expr.Empty().set(locator));
    }
    else {
      // analyze variables
      Set<Name> usedVars = new HashSet<Name>();
      Map<Name, MZ.Expr> subs = new HashMap<Name, MZ.Expr>();
      Seq<MZ.Property> props = new BufferSeq<MZ.Property>();
      // was: curr.scope.apply(fun (Pair<Name,MZ.Expr> p)->void { ... })
      for (Enum<Pair<Name, MZ.Expr>> e = curr.scope.enumerate(); e.more(); e.advance()) {
        Pair<Name, MZ.Expr> p = e.current();
        Name pfst = p.fst;
        MZ.Expr psnd = p.snd;
        switch (psnd) {
          case MZ.Expr.Variable v -> {
            if (v.name.equals(pfst)) {
              // free variable: suppose it is used
              usedVars = usedVars.include(pfst);
              continue; // was: return from the closure
            }
          }
          default -> { }
        }
        // bound variable:
        if (isPattern(psnd, curr.scope)) {
          // candidate for substitution.
          subs = subs.define(pfst, psnd);
        } else {
          // generate an equation for it
          usedVars = usedVars.include(pfst);
          props = props.append(
                    new MZ.Property.Equal(
                          new MZ.Expr.Variable(pfst).set(psnd.locator),
                          psnd
                        ).set(psnd.locator)
                  );
        }
      }
      props = props.appendAll(curr.props);
      if (!subs.isEmpty()) {
        // apply substitution to properties
        Seq<MZ.Property> newProps = new BufferSeq<MZ.Property>();
        // was: props.apply(fun (MZ.Property p)->void { ... newProps = ...; })
        for (Enum<MZ.Property> e = props.enumerate(); e.more(); e.advance()) {
          MZ.Property p = e.current();
          p = p.substitute(subs);
          boolean done = false;
          switch (p) {
            case MZ.Property.Equal eq
                when eq.left instanceof MZ.Expr.Variable v1
                     && eq.right instanceof MZ.Expr.Variable v2 -> {
              if (v1.name.equals(v2.name)) {
                // this equation becomes obsolete
                done = true;
              }
            }
            default -> { }
          }
          if (!done) {
            newProps = newProps.append(p);
          }
        }
        props = sortProps(newProps);
        pat = pat.substitute(subs);
      } else {
        props = sortProps(props);
      }
      if (!forceSchema && isExhaustive(pat) && props.isEmpty()) {
        return Pair.Pair(new HashMap<Name, MZ.Expr>(),
                         new MZ.Expr.Universal().set(locator));
      } else {
        return
          Pair.Pair(subs,
                    new MZ.Expr.Schema(
                          pat, usedVars.excludeAll(pat.freeVars()),
                          props)
                          .set(locator)
                   );
      }
    }
  }

  /** Sort properties regarding their locator position. */
  private Seq<MZ.Property> sortProps(Seq<MZ.Property> props) {
    return ArraySeq.from(props)
                   .sort((p1, p2) -> p1.locator.compareTo(p2.locator));
  }


  /** Exit a context, producing a schema for the bindings in it. */
  private MZ.Expr exitContextWithSchema(MZ.Expr pat, Locator locator) {
    return (MZ.Expr) exitContext(pat, locator, false).snd;
  }

  /** Exit a context, producing a mu-value of the bindings in it. */
  private MZ.Expr exitContextWithMu(MZ.Expr res, Locator locator) {
    if (!isPattern(res, context.scope)) {
      MZ.Expr.Variable var = newAnonVar(locator, "%m");
      addBound(var.name, res);
      res = var;
    }
    MZ.Expr schema = exitContextWithSchema(res, locator);
    switch (schema) {
      case MZ.Expr.Schema s when s.binder instanceof MZ.Expr.Variable v -> {
        if (s.props.size() == 1 && s.freeVars.isEmpty()) {
          switch (s.props.head()) {
            case MZ.Property.Equal eq when eq.left instanceof MZ.Expr.Variable v1 -> {
              if (v.name.equals(v1.name))
                return eq.right;
            }
            default -> { }
          }
        }
      }
      default -> { }
    }
    return new MZ.Expr.Hom(MZConstants.muHom, schema).set(locator);
  }

  /** Exit a context, producing a unit definition of it. */
  @SuppressWarnings("unchecked")
  public MZ.UnitDef exitContextWithUnit(Name unitName, Seq<Name> parents,
                                        Seq<Name> globals,
                                        Locator locator) {
    // make a tuple of the global names as a pattern for schema exit
    MZ.Expr pat = // FIXME EFFICIENCY: we can avoid this pseudo tuple
      new MZ.Expr.Term(getTupleCons(globals.size()),
                       globals.<MZ.Expr>map(n -> new MZ.Expr.Variable(n)));
    Pair<Map<Name, MZ.Expr>, MZ.Expr> p = exitContext(pat, locator, true);
    Map<Name, MZ.Expr> psubs = p.fst;
    MZ.Expr pexpr = p.snd;
    // System.err.println("exit ctx unit: " + p);
    switch (pexpr) {
      case MZ.Expr.Schema s -> {
        MZ.Expr gpat = s.binder;
        Set<Name> freeVars = s.freeVars;
        Seq<MZ.Property> props = s.props;
        // add equalities for global names which are substituted
        Set<Name> globalSet = HashSet.from(globals);
        // was: p.fst.apply(fun (Pair<Name,MZ.Expr> q)->void { ... props = ...; })
        for (Enum<Pair<Name, MZ.Expr>> e = psubs.enumerate(); e.more(); e.advance()) {
          Pair<Name, MZ.Expr> q = e.current();
          Name qfst = q.fst;
          MZ.Expr qsnd = q.snd;
          boolean doGen = false;
          switch (qsnd) {
            case MZ.Expr.Variable v ->
              doGen = !v.name.equals(qfst) && globalSet.contains(qfst);
            default ->
              doGen = globalSet.contains(qfst);
          }
          if (doGen) {
            props = props.append(
                      new MZ.Property.Equal(
                            new MZ.Expr.Variable(qfst).set(qsnd.locator),
                            qsnd.substitute(psubs)
                          ).set(qsnd.locator)
                    );
          }
        }
        props = sortProps(props);
        return new MZ.UnitDef(
                     unitName,
                     parents,
                     globals,
                     ArraySeq.from(freeVars.includeAll(gpat.freeVars())
                                           .excludeAll(globalSet)),
                     props
                   );
      }
      case MZ.Expr.Empty x -> {
        return new MZ.UnitDef(
                     unitName,
                     parents,
                     globals,
                     new ArraySeq<Name>(),
                     new ArraySeq<MZ.Property>(
                           new MZ.Property.IsTrue(
                                 new MZ.Expr.Empty().set(pexpr.locator)
                               ).set(pexpr.locator)
                         )
                   );
      }
      case MZ.Expr.Universal x -> {
        return new MZ.UnitDef(
                     unitName,
                     parents,
                     globals,
                     new ArraySeq<Name>(),
                     new ArraySeq<MZ.Property>()
                   );
      }
      default ->
        throw FatalError.assertionFailed("result of exitContext");
    }
  }


  /** Add a bound variable to the scope. */
  private void addBound(Name var, MZ.Expr def) {
    if (!context.scope.defines(var))
      throw FatalError.assertionFailed("bound variable not in scope");
    addProperty(
      new MZ.Property.Equal(new MZ.Expr.Variable(var).set(def.locator),
                            def
                           ).set(def.locator)
    );
  }


  /** Add a property to the current context. */
  public void addProperty(MZ.Property prop) {
    if (prop.locator == null) throw FatalError.assertionFailed("no locator");
    if (context.props != null) {
      // still a valid goal
      switch (prop) {
        case MZ.Property.IsTrue p -> {
          if (tryUnify(prop.locator, p.expr, truthSet)) return;
        }
        case MZ.Property.Equal p -> {
          MZ.Expr left = p.left;
          MZ.Expr right = p.right;
          if (tryUnify(prop.locator, left, right)) return;
          switch (reduce(left)) {
            case MZ.Expr.Variable v -> {
              if (v.name.equals(MZConstants.boolTruth)) {
                // true = right
                addProperty(new MZ.Property.IsTrue(right).set(prop.locator));
                return;
              }
            }
            case MZ.Expr.Singleton s when s.arg instanceof MZ.Expr.Term t -> {
              if (t.name.equals(MZConstants.unitCons)) {
                // true = right
                addProperty(new MZ.Property.IsTrue(right).set(prop.locator));
                return;
              }
            }
            default -> { }
          }
          switch (reduce(right)) {
            case MZ.Expr.Variable v -> {
              if (v.name.equals(MZConstants.boolTruth)) {
                // left = true
                addProperty(new MZ.Property.IsTrue(left).set(prop.locator));
                return;
              }
            }
            case MZ.Expr.Singleton s when s.arg instanceof MZ.Expr.Term t -> {
              if (t.name.equals(MZConstants.unitCons)) {
                // left = true
                addProperty(new MZ.Property.IsTrue(left).set(prop.locator));
                return;
              }
            }
            default -> { }
          }
        }
        case MZ.Property.Member p -> {
          MZ.Expr left = reduce(p.left);
          MZ.Expr right = reduce(p.right);
          switch (right) {
            case MZ.Expr.Universal x -> {
              return;
            }
            case MZ.Expr.Empty x -> {
              addFailure();
              return;
            }
            case MZ.Expr.Schema s -> {
              MZ.Expr pat = s.binder;
              Set<Name> fvars = s.freeVars;
              Seq<MZ.Property> props = s.props;
              Map<Name, MZ.Expr> savedScope = context.scope;
              Set<Name> svars = fvars.includeAll(pat.freeVars());
              Map<Name, MZ.Expr> ren = rename(svars, right.locator);
              // System.err.println("ren: " + ren);
              if (!ren.isEmpty()) {
                pat = pat.substitute(ren);
                // System.err.println("ren pat: " + pat);
              }
              try {
                context.scope = unify(context.scope, left, pat);
                // System.err.println("uni scope: " + context.scope);
                if (!ren.isEmpty()) {
                  props = props.map(q -> q.substitute(ren));
                }
                props.apply(
                  q -> {
                    addProperty(q);
                    // addProperty(p.set(prop.locator));
                  }
                );
                return;
              }
              catch (UnifyFailureException e) {
                context.scope = savedScope;
                addFailure();
                return;
              }
              catch (UnifyUnknownException e) {
                context.scope = savedScope;
              }
            }
            default -> { }
          }
        }
        case MZ.Property.Subset p -> {
          MZ.Expr right = p.right;
          switch (reduce(p.left)) {
            case MZ.Expr.Empty x -> {
              return;
            }
            case MZ.Expr.Singleton sx -> {
              MZ.Expr x = sx.arg;
              switch (reduce(right)) {
                case MZ.Expr.Singleton sy -> {
                  addProperty(new MZ.Property.Equal(x, sy.arg).setFrom(prop));
                  return;
                }
                default -> {
                  addProperty(new MZ.Property.Member(x, right).setFrom(prop));
                  return;
                }
              }
            }
            default -> { }
          }
          switch (reduce(right)) {
            case MZ.Expr.Universal x -> {
              return;
            }
            default -> { }
          }
        }
        default -> { }
      }
      context.props = context.props.append(prop);
    }
  }

  /** Make a renaming substitution for the given names away of the
      context names. */
  private Map<Name, MZ.Expr> rename(Set<Name> names, Locator loc) {
    Map<Name, MZ.Expr> ren = new HashMap<Name, MZ.Expr>();
    // was: names.apply(fun (Name name)->void { ... ren = ren.define(...); })
    for (Enum<Name> e = names.enumerate(); e.more(); e.advance()) {
      Name name = e.current();
      Name vari = awayof(name, names);
      MZ.Expr ve = new MZ.Expr.Variable(vari).set(loc);
      context.scope = context.scope.define(vari, ve);
      if (!vari.equals(name)) {
        ren = ren.define(name, ve);
      }
    }
    return ren;
  }

  private Name awayof(Name name) {
    return awayof(name, new HashSet<Name>());
  }

  private Name awayof(Name name, Set<Name> dontUse) {
    if (context.isBound(name) || dontUse.contains(name)) {
      Pair<String, Integer> p = splitName(name);
      return awayof(new Name(p.fst + "~" + (p.snd + 1)), dontUse);
    } else
      return name;
  }

  // Pizza's Pair<String,int>.
  private Pair<String, Integer> splitName(Name n) {
    String s = n.getRepr();
    int l = s.length() - 1;
    while (l >= 0 && Character.isDigit(s.charAt(l))) l--;
    if (l >= 0 && s.charAt(l) == '~') {
      // has a qualification
      return Pair.Pair(s.substring(0, l),
                       Integer.valueOf(s.substring(l + 1)).intValue());
    } else
      return Pair.Pair(s, 0);
  }




  /** Try to convert an equality to an assignment. */
  private boolean tryUnify(Locator locator, MZ.Expr left, MZ.Expr right) {
    // try to express by unification
    try {
      context.scope = unify(context.scope, left, right);
      return true;
    }
    catch (UnifyFailureException e) {
      addFailure();
      return true;
    }
    catch (UnifyUnknownException e) {
      return false;
    }
  }

  /* The source carries a commented-out `strictVars`/`strictVarsSeq` pair here (calculate
   * the strictly used variables of an expression); omitted, it was dead in 1998 as well. */


  /** Check whether the given expression is a pattern
   * over the given variables. */
  private boolean isPattern(MZ.Expr cand, Map<Name, MZ.Expr> subs) {
    switch (cand) {
      case MZ.Expr.Variable v -> {
        return subs.defines(v.name);
      }
      case MZ.Expr.Term t -> {
        return t.args.forall(e -> isPattern(e, subs));
      }
      default -> {
        return false;
      }
    }
  }

  /** Indicate that the current context fails. */
  private void addFailure() {
    context.props = null;
  }


  /** Resolve a name expression. */
  private MZ.Expr resolve(Locator locator, Name name,
                          Seq<MZ.Expr> actuals) {
    MZ.Expr res = context.lookup(name, true);
    if (res == null)
      throw FatalError.assertionFailed("unbound name: " + name);

    switch (res) {
      case MZ.Expr.Variable x -> { }
      case MZ.Expr.Empty x -> { }
      case MZ.Expr.Universal x -> { }
      default -> {
        // on resolve, actuallly yield the variable instead of its substitution
        // if this is not a pattern
        if (!isPattern(res, context.scope))
          res = new MZ.Expr.Variable(name).set(locator);
      }
    }
    if (actuals.size() == 0)
      return res;
    else {
      // check if we can use the universal instance
      if (actuals.forall(e -> e instanceof MZ.Expr.Universal)) {
        return makeUnivInst(locator, name);
      } else {
        return makeApply(locator,
                         res, makeTuple(actuals).set(locator)).set(locator);
      }
    }
  }

  /** Create the "universal instance" name expression of a generic. */
  private MZ.Expr makeUnivInst(Locator locator, Name name) {
    Name uiname = new Name(name.getRepr() + "[]");
    MZ.Expr res = context.lookup(uiname, true);
    if (res == null) {
      Context globals = context;
      while (globals.parent != null) globals = globals.parent;
      res = new MZ.Expr.Variable(uiname).set(locator);
      globals.scope = globals.scope.define(uiname, res);
    } else {
      res = new MZ.Expr.Variable(uiname).set(locator);
    }
    return res;
  }



  /** Get full evaluation information of expression. */
  private MZ.Expr reduce(MZ.Expr e) {
    switch (e) {
      case MZ.Expr.Variable v -> {
        Name name = v.name;
        try {
          MZ.Expr e1 = context.lookup(name);
          switch (e1) {
            case MZ.Expr.Variable v1 -> {
              if (name.equals(v1.name))
                return e;
              else
                return reduce(e1);
            }
            case MZ.Expr.Empty x -> {
              return e1;
            }
            case MZ.Expr.Universal x -> {
              return e1;
            }
            case MZ.Expr.Schema s -> {
              if (s.props.isEmpty()) {
                return e1;
              } else
                return e;
            }
            default -> {
              return e;
            }
          }
        }
        catch (NoSuchElementException ex) {
          return e;
        }
      }
      default -> { }
    }
    return e;
  }




  // ========================================================================
  // Lifting expressions


  /** Lift the given expressions to strict equations. */
  private MZ.Expr strictify(Seq<MZ.Expr> exprs, Function<Seq<MZ.Expr>, MZ.Expr> gen) {
    Seq<MZ.Expr> lifted = new BufferSeq<MZ.Expr>();
    // was: exprs.apply(fun (MZ.Expr expr)->void { ... lifted = ...; })
    for (Enum<MZ.Expr> e = exprs.enumerate(); e.more(); e.advance()) {
      MZ.Expr expr = e.current();
      if (isTrivial(expr))
        lifted = lifted.append(expr);
      else {
        MZ.Expr.Variable var = newAnonVar(expr.locator, "%l");
        addBound(var.name, expr);
        lifted = lifted.append(var);
      }
    }
    return gen.apply(lifted);
  }

  /** Lift the given single expression to a strict equation. */
  private MZ.Expr strictifyOne(MZ.Expr expr, Function<MZ.Expr, MZ.Expr> gen) {
    return strictify(new ArraySeq<MZ.Expr>(expr),
                     lifted -> gen.apply(lifted.head()));
  }

  /** Check whether the given expression is trivial and shall not be
      lifted. */
  private boolean isTrivial(MZ.Expr expr) {
    return switch (expr) {
      case MZ.Expr.Denotation x -> true;
      case MZ.Expr.Variable x -> true;
      case MZ.Expr.Empty x -> true;
      case MZ.Expr.Universal x -> true;
      case MZ.Expr.Schema x -> true;
      default -> false;
    };
  }


  // ========================================================================
  // Unification

  // PORT NOTE: declared `private class` in the source; Pizza compiled both package-private.
  class UnifyFailureException extends Exception {}
  class UnifyUnknownException extends Exception {}

  private Map<Name, MZ.Expr> unify(Map<Name, MZ.Expr> subs,
                                  MZ.Expr e1, MZ.Expr e2)
  throws UnifyFailureException, UnifyUnknownException {
    if (e1 == e2) return subs;
    Name f1 = null;
    Name f2 = null;
    MZ.Expr s1 = null;
    MZ.Expr s2 = null;
    switch (e1) {
      case MZ.Expr.Variable v1 -> {
        Name n1 = v1.name;
        switch (e2) {
          case MZ.Expr.Variable v2 -> {
            if (n1.equals(v2.name)) return subs;
          }
          default -> { }
        }
        try {
          s1 = subs.get(n1);
          switch (s1) {
            case MZ.Expr.Variable m -> {
              if (n1.equals(m.name)) {
                // a free variable
                f1 = n1;
              }
            }
            default -> { }
          }
        }
        catch (NoSuchElementException e) {
          // throw new UnifyUnknownException();
        }
      }
      default -> { }
    }
    switch (e2) {
      case MZ.Expr.Variable v2 -> {
        Name n2 = v2.name;
        try {
          s2 = subs.get(n2);
          switch (s2) {
            case MZ.Expr.Variable m -> {
              if (n2.equals(m.name)) {
                f2 = n2;
              }
            }
            default -> { }
          }
        }
        catch (NoSuchElementException e) {
          // throw new UnifyUnknownException();
        }
      }
      default -> { }
    }
    if (f1 != null && f2 != null) {
      // two different free variables
      if (moreRecent(f2, f1))
        return assignVar(subs, f2, e1);
      else
        return assignVar(subs, f1, e2);
    } else if (f1 != null) {
      return assignVar(subs, f1, e2);
    } else if (f2 != null) {
      return assignVar(subs, f2, e1);
    } else if (s1 != null) {
      return unify(subs, s1, e2);
    } else if (s2 != null) {
      return unify(subs, e1, s2);
    }

    switch (e1) {
      case MZ.Expr.Term t1 -> {
        switch (e2) {
          case MZ.Expr.Term t2 -> {
            if (t1.name.equals(t2.name)) {
              Seq<MZ.Expr> args1 = t1.args;
              Seq<MZ.Expr> args2 = t2.args;
              int s = args1.size();
              for (int i = 0; i < s; i++) {
                subs = unify(subs, args1.at(i), args2.at(i));
              }
              return subs;
            } else
              throw new UnifyFailureException();
          }
          default -> { }
        }
      }
      default -> { }
    }
    throw new UnifyUnknownException();
  }

  private Map<Name, MZ.Expr> assignVar(Map<Name, MZ.Expr> subs,
                                      Name n, MZ.Expr e)
    throws UnifyUnknownException {
    if (!isAdmissable(n, e)) {
      throw new UnifyUnknownException();
    }
    return subs.define(n, e);
  }

  private boolean isAdmissable(Name n, MZ.Expr e) {
    return !e.freeVars().contains(n);
    /*
    switch (e){
    case MZ.Expr.Variable(Name n1):
      return !n.equals(n1);
    case MZ.Expr.Term(_, Seq<MZ.Expr> args):
      return args.forall(
               fun (MZ.Expr arg)->boolean {
                 return isAdmissable(n, arg);
               }
             );
    default:
      return false;
    }
    */
  }

  private boolean moreRecent(Name n1, Name n2) {
    String r1 = n1.getRepr();
    String r2 = n2.getRepr();
    if (r1.startsWith("%") && !r2.startsWith("%"))
      return true;
    Pair<String, Integer> p1 = splitName(n1);
    Pair<String, Integer> p2 = splitName(n2);
    return p1.snd > p2.snd;
  }



  // ========================================================================
  // Miscellaneous

  /** Calculate the names of a binding type. */
  private static Seq<Name> getBindingSign(Expr schema) {
    Expr type = ReducerUtil.getTypeAn(schema);
    switch (type) {
      case Expr.Signature s -> {
        return new ArraySeq<Name>(s.names);
      }
      default ->
        throw FatalError.assertionFailed(
                "expected a binding type; found: " + type +
                "; at: " + ReducerUtil.getLocatorAn(schema)
              );
    }
  }

  /** Calculate the arity of a tuple. */
  private static int getTupleArity(Expr tuple) {
    Expr type = ReducerUtil.getTypeAn(tuple);
    switch (type) {
      case Expr.Product p -> {
        return p.elements.length;
      }
      default ->
        throw FatalError.assertionFailed(
                "expected a product type; found: " + type +
                "; at: " + ReducerUtil.getLocatorAn(tuple)
              );
    }
  }

  /** Calculate the names of a schema type. */
  private static Seq<Name> getSchemaSign(Expr schema) {
    Expr type = ReducerUtil.getTypeAn(schema);
    switch (type) {
      case Expr.Unary u
          when u.kind == Expr.UnaryKind.Power
               && u.oper instanceof Expr.Signature s -> {
        return new ArraySeq<Name>(s.names);
      }
      default ->
        throw FatalError.assertionFailed(
                "expected a schema type; found: " + type +
                "; at: " + ReducerUtil.getLocatorAn(schema)
              );
    }
  }

  /** Test whether schemas have the same signature. */
  // PORT NOTE: 1998 defect, preserved. The two signatures are computed (and sorted) but then
  // the *expressions* are compared, so this is true only for structurally equal operands and
  // `/\`, `\/`, `=>` almost always take the general, join-based reduction. Making it compare
  // `n1` and `n2` would change the reduction of every schema conjunction -- and so the `nmz`
  // artifacts -- so it stays. The two `getSchemaSign` calls are kept, in their order, because
  // each asserts a schema type and may throw.
  private static boolean sameSchemaSign(Expr s1, Expr s2) {
    // FIXME: efficiency
    Seq<Name> n1 = getSchemaSign(s2).sort(Name::lexCompare);
    Seq<Name> n2 = getSchemaSign(s1).sort(Name::lexCompare);
    return s1.equals(s2);
  }


  /** Add decore to a name */
  private static Function<Name, Name> addStroke(char stroke) {
    return n -> n.decorate(new String(new char[]{stroke}));
  }

  /** Add decore to a name */
  private static Function<Name, Name> addStroke(String stroke) {
    return n -> n.decorate(stroke);
  }

  /** Deconstruct a ZIRP pair. */
  private static Expr[] unpair(Expr expr) {
    switch (expr) {
      case Expr.Tuple t -> {
        if (t.elements.length == 2)
          return t.elements;
      }
      default -> { }
    }
    return null;
  }

  /** Deconstruct a ZIRP tuple (arity >= 1). */
  private static Expr[] untuple(Expr expr) {
    switch (expr) {
      case Expr.Tuple t -> {
        return t.elements;
      }
      default -> {
        return new Expr[]{expr};
      }
    }
  }


  /** A constant representing the boolean unit constructor term. */
  private static final MZ.Expr unitTerm =
    new MZ.Expr.Term(MZConstants.unitCons, new ArraySeq<MZ.Expr>());

  /** A constant representing the boolean truth set. */
  private static final MZ.Expr truthSet =
    new MZ.Expr.Singleton(unitTerm);


  /** Make a tuple expression. */
  private MZ.Expr makeTuple(Seq<MZ.Expr> args) {
    if (args.size() == 1)
      return args.head();
    else {
      Name cons = getTupleCons(args.size());
      return new MZ.Expr.Term(cons, args);
    }
  }

  /** Make a pair expression. */
  private MZ.Expr makePair(MZ.Expr e1, MZ.Expr e2) {
    return makeTuple(new ArraySeq<MZ.Expr>(e1, e2));
  }

  /** Make an apply expression. */
  private MZ.Expr makeApply(Locator loc, MZ.Expr e1, MZ.Expr e2) {
    return new MZ.Expr.Apply(e1, e2).set(loc);
    /*
    enterContext();
    MZ.Expr y = newVar(loc);
    addProperty(
      new MZ.Property.Member(
            makePair(e2, y)
                    .set(loc),
            e1
          ).set(loc)
    );
    MZ.Expr schema =
      exitContextWithSchema(y, loc);
    return new MZ.Expr.Mu(schema).set(loc);
    */
  }

  /** Check whether the given pattern is exhaustive. This is
   * the case if its constructors are all bindings and tuples
   * of local variables. */
  private boolean isExhaustive(MZ.Expr pat) {
    return isexh(new HashSet<Name>(), pat) != null;
  }

  private Set<Name> isexh(Set<Name> used, MZ.Expr pat) {
    switch (pat) {
      case MZ.Expr.Variable v -> {
        Name name = v.name;
        if (true || context.scope.defines(name)) {
          if (!used.contains(name)) {
            return used.include(name);
          } else
            // the pattern is not exhaustive since it is not linear
            return null;
        } else {
          return null;
        }
      }
      case MZ.Expr.Term t -> {
        String repr = t.name.getRepr();
        if (repr.startsWith("(") || repr.startsWith("<")) {
          // was: args.forall(fun (MZ.Expr spat)->boolean {
          //        used = isexh(used, spat); return used != null; })
          // `forall` stops at the first false, and so does this loop.
          for (Enum<MZ.Expr> e = t.args.enumerate(); e.more(); e.advance()) {
            used = isexh(used, e.current());
            if (used == null) return null;
          }
          return used;
        }
        else
          return null;
      }
      default -> {
        return null;
      }
    }
  }

  /** Make an undefined expression. */
  private MZ.Expr makeUndef(Locator loc) {
    return new MZ.Expr.Hom(MZConstants.muHom,
                           new MZ.Expr.Empty().set(loc)).set(loc);
  }

  /** Check whether the given expressions represents a total
      function. We are only able to identify constructor functions
      (where the definition of is known) to be total*/
  private boolean checkTotal(MZ.Expr expr) {
    switch (reduce(expr)) {
      case MZ.Expr.Schema s -> {
        return s.props.isEmpty();
      }
      default -> {
        return false;
      }
    }
  }



  // ========================================================================
  // Generating patterns

  /** Get pattern for schema.
   * In <code>p = schemaPattern(schema)</code>, p is the
   * pattern for the schema, with new variables added to the context. */
  private MZ.Expr schemaPattern(Expr schema) {
    Seq<Name> names = getSchemaSign(schema).sort(Name::lexCompare);
    Seq<MZ.Expr> p = newVars(ReducerUtil.getLocatorAn(schema), names);
    return new MZ.Expr.Term(getBindingCons(names), p);
  }

  /** Get patterns to relate schema with characteristic tuple.
   * In <code>(p,p') = ctuplePatterns(schema)</code>, p is the
   * pattern for the schema and p' for the tuple. */
  private
    Pair<MZ.Expr, MZ.Expr> ctuplePatterns(Expr schema) {
    MZ.Expr spat = schemaPattern(schema);
    Seq<MZ.Expr> comps = new BufferSeq<MZ.Expr>();
    switch (schema) {
      case Expr.Text t -> {
        Expr.Decl[] decls = t.decls;
        for (int i = 0; i < decls.length; i++) {
          switch (decls[i]) {
            case Expr.Decl.Direct d -> {
              NameDecl[] names = d.names;
              for (int j = 0; j < names.length; j++) {
                MZ.Expr var = Map.tryGet(context.scope, names[j].name);
                comps = comps.append(var);
              }
            }
            case Expr.Decl.Eqn q -> {
              comps = comps.append(Map.tryGet(context.scope, q.name.name));
            }
            case Expr.Decl.Inclusion inc -> {
              Seq<Name> names = getSchemaSign(inc.schema).sort(Name::lexCompare);
              comps = comps.append(
                        new MZ.Expr.Term(
                              getBindingCons(names),
                              makeArgs(names, ReducerUtil.getLocatorAn(decls[i]))
                            )
                      );
            }
            default -> throw FatalError.unexpectedCase("declaration");
          }
        }
      }
      default -> {
        Seq<Name> names = getSchemaSign(schema).sort(Name::lexCompare);
        comps = comps.append(
                  new MZ.Expr.Term(
                        getBindingCons(names),
                        makeArgs(names, ReducerUtil.getLocatorAn(schema))
                      )
                );
      }
    }
    return Pair.Pair(spat,
                     comps.size() == 1 ? comps.head() :
                     new MZ.Expr.Term(
                           getTupleCons(comps.size()),
                           comps
                         )
                    );
  }

  /** Get patterns for joined schemas.
   * In <code>(p,p1,p2) = joinedPatterns(res,schema1,schema2)</code>, p is the
   * pattern for the joined schema, p1 for schema1, and p2 for schema2.
   * The function returns null if no joining is necessary, since the
   * schemas have the same type. */
  private
    Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>>
    joinedPatterns(Expr res, Expr schema1, Expr schema2, boolean force) {
    Seq<Name> names1 = getSchemaSign(schema1).sort(Name::lexCompare);
    Seq<Name> names2 = getSchemaSign(schema2).sort(Name::lexCompare);
    Seq<Name> resNames = getSchemaSign(res).sort(Name::lexCompare);
    if (!force && names1.equals(names2) && names1.equals(resNames))
      return null;
    else {
      Seq<Name> allNames = ArraySeq.from(
                             HashSet.from(resNames)
                                    .includeAll(names1)
                                    .includeAll(names2)
                           ).sort(Name::lexCompare);
      Locator loc = ReducerUtil.getLocatorAn(res);
      Seq<MZ.Expr> p = newVars(loc, allNames);
      return
        Pair.Pair(
              (MZ.Expr) new MZ.Expr.Term(
                    getBindingCons(resNames),
                    makeArgs(resNames, loc)
                   ),
              Pair.Pair(
                    (MZ.Expr) new MZ.Expr.Term(
                          getBindingCons(names1),
                          makeArgs(names1, loc)
                        ),
                    (MZ.Expr) new MZ.Expr.Term(
                          getBindingCons(names2),
                          makeArgs(names2, loc)
                        )
                  )
            );
    }
  }


  /** Get patterns for composed schemas. */
  @SuppressWarnings("unchecked")
  private
    Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>>
    composedPatterns(Expr res, Expr schema1, Expr schema2,
                     Function<Name, Name> matcher) {
    Locator loc1 = ReducerUtil.getLocatorAn(schema1);
    Locator loc2 = ReducerUtil.getLocatorAn(schema2);
    Locator loc3 = ReducerUtil.getLocatorAn(res);
    Seq<Name> names1 = getSchemaSign(schema1).sort(Name::lexCompare);
    Seq<Name> names2 = getSchemaSign(schema2).sort(Name::lexCompare);
    Seq<Name> names3 = getSchemaSign(res).sort(Name::lexCompare);
    Map<Name, MZ.Expr> id1 = new HashMap<Name, MZ.Expr>();
    Map<Name, MZ.Expr> id2 = new HashMap<Name, MZ.Expr>();
    Set<Name> set1 = HashSet.from(names1);
    int i = 0;
    // was: names2.apply(fun (Name n)->void { ... ++i ... id1 = ...; id2 = ...; })
    for (Enum<Name> e = names2.enumerate(); e.more(); e.advance()) {
      Name n = e.current();
      Name n1 = matcher.apply(n);
      if (n1 != null && set1.contains(n1)) {
        MZ.Expr var = newAnonVar(loc3, "%j" + (++i));
        id1 = id1.define(n1, var);
        id2 = id2.define(n, var);
      }
    }
    final Map<Name, MZ.Expr> fid1 = id1;
    final Map<Name, MZ.Expr> fid2 = id2;
    // `idr` is assigned inside the `map` closures below; the closures are kept (see the
    // class notes), so it lives in a one-element array.
    Map<Name, MZ.Expr>[] idr = new Map[]{ new HashMap<Name, MZ.Expr>() };
    Seq<MZ.Expr> args1 =
      names1.<MZ.Expr>map(n -> {
                if (fid1.defines(n))
                  return fid1.get(n);
                else if (idr[0].defines(n)) {
                  return idr[0].get(n);
                } else {
                  MZ.Expr var = newAnonVar(loc1, n.getRepr());
                  idr[0] = idr[0].define(n, var);
                  return var;
                }
              });
    Seq<MZ.Expr> args2 =
      names2.<MZ.Expr>map(n -> {
                if (fid2.defines(n))
                  return fid2.get(n);
                else if (idr[0].defines(n)) {
                  return idr[0].get(n);
                } else {
                  MZ.Expr var = newAnonVar(loc1, n.getRepr());
                  idr[0] = idr[0].define(n, var);
                  return var;
                }
              });
    Seq<MZ.Expr> args3 =
      names3.<MZ.Expr>map(n -> {
                if (idr[0].defines(n)) {
                  return idr[0].get(n);
                } else {
                  MZ.Expr var = newAnonVar(loc1, n.getRepr());
                  idr[0] = idr[0].define(n, var);
                  return var;
                }
              });
    return
      Pair.Pair(
            (MZ.Expr) new MZ.Expr.Term(
                           getBindingCons(names3),
                           args3
                         ),
             Pair.Pair(
                   (MZ.Expr) new MZ.Expr.Term(
                                  getBindingCons(names1),
                                  args1
                                ),
                    (MZ.Expr) new MZ.Expr.Term(
                                   getBindingCons(names2),
                                   args2
                                 )
                 )
          );
  }



  /** Get patterns for projections.
   * In <code>(p,x) = projectionPatterns(oper,name)</code>, p is the
   * pattern to extract component name by variable x. */
  private Pair<MZ.Expr, MZ.Expr.Variable>
  projectionPatterns(Expr oper, Name name) {
    if (name.getRepr().startsWith("#")) {
      // tuple selection
      int pos = Integer.parseInt(name.getRepr().substring(1)) - 1;
      int arity = getTupleArity(oper);
      Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
      MZ.Expr.Variable var = null;
      Locator loc = ReducerUtil.getLocatorAn(oper);
      for (int i = 0; i < arity; i++) {
        MZ.Expr.Variable v = newVar(loc, "#" + i);
        if (i == pos) var = v;
        vars = vars.append(v);
      }
      return
        Pair.Pair(
              (MZ.Expr) new MZ.Expr.Term(getTupleCons(arity), vars),
               var
            );
    } else {
      Seq<Name> names = getBindingSign(oper).sort(Name::lexCompare);
      Seq<MZ.Expr> vars = newVars(ReducerUtil.getLocatorAn(oper), names);
      return
        Pair.Pair(
              (MZ.Expr) new MZ.Expr.Term(getBindingCons(names), vars),
               (MZ.Expr.Variable) vars.at(names.indexOf(name))
            );
    }
  }

  /** Get patterns for DELTA.
   * In <code>(p,p1,p2) = deltaPatterns(oper)</code>, p is the
   * pattern containing the primed and unprimed components, and
   * p1 and p2 are patterns for oper. */
  private Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>>
    deltaPatterns(Expr res, Expr oper) {
    Seq<Name> allNames = getSchemaSign(res).sort(Name::lexCompare);
    Seq<Name> baseNames = getSchemaSign(oper).sort(Name::lexCompare);
    Seq<Name> primeNames = baseNames.map(addStroke(Name.prime))
                                    .sort(Name::lexCompare);
    Locator loc = ReducerUtil.getLocatorAn(oper);
    Seq<MZ.Expr> p = newVars(loc, allNames);
    return
      Pair.Pair((MZ.Expr) new MZ.Expr.Term(
                               getBindingCons(allNames),
                               makeArgs(allNames, loc)
                             ),
                Pair.Pair(
                      (MZ.Expr) new MZ.Expr.Term(
                                     getBindingCons(baseNames),
                                     makeArgs(baseNames, loc)
                                   ),
                      (MZ.Expr) new MZ.Expr.Term(
                                     getBindingCons(baseNames),
                                     makeArgs(primeNames, loc)
                                   )
                    )
       );
  }

  /** Get patterns for decoration.
   * In <code>(p',p) = decoratedPatterns(oper, decore)</code>, p' is the
   * decorated version of p. */
  private Pair<MZ.Expr, MZ.Expr>
    decoratedPatterns(Expr oper, String decore) {
    Seq<Name> names = getSchemaSign(oper).sort(Name::lexCompare);
    Seq<Name> primeNames = names.map(addStroke(decore))
                                .sort(Name::lexCompare);
    Locator loc = ReducerUtil.getLocatorAn(oper);
    Seq<MZ.Expr> p = newVars(loc, primeNames);
    return
      Pair.Pair(
            (MZ.Expr) new MZ.Expr.Term(
                           getBindingCons(primeNames),
                           p
                         ),
             (MZ.Expr) new MZ.Expr.Term(
                            getBindingCons(names),
                            p // assumes lexical ordering!
                          )
          );
  }

  /** Get patterns for hiding.
   * In <code>(p',p) = hidePatterns(oper,pred)</code>, p' is the
   * pattern with names hidden according to pred. */
  private Pair<MZ.Expr, MZ.Expr>
    hidePatterns(Expr oper, java.util.function.Predicate<Name> hide) {
    Seq<Name> names = getSchemaSign(oper).sort(Name::lexCompare);
    Seq<Name> hnames = names.filter(n -> !hide.test(n));
    Locator loc = ReducerUtil.getLocatorAn(oper);
    Seq<MZ.Expr> p = newVars(loc, names);
    return
      Pair.Pair(
             (MZ.Expr) new MZ.Expr.Term(
                            getBindingCons(hnames),
                            makeArgs(hnames, loc)
                          ),
            (MZ.Expr) new MZ.Expr.Term(
                           getBindingCons(names),
                           p
                         )
          );
  }



  // ========================================================================
  // Reducing expressions


  /** Reduce an expression. */
  @SuppressWarnings("unchecked")
  public MZ.Expr ereduce(Expr expr) {

    MZ.Expr newExpr;
    Locator exprLocator = ReducerUtil.getLocatorAn(expr);

    switch (expr) {

      case Expr.Number num -> {
        newExpr = new MZ.Expr.Call(MZConstants.makeNumberPrim,
                                   new ArraySeq<MZ.Expr>(
                                         new MZ.Expr.Denotation(num.value)
                                               .set(exprLocator))
                                  );
      }

      case Expr.Denotation den -> {
        newExpr = new MZ.Expr.Denotation(den.value);
      }

      case Expr.Variable variable -> {
        NameAppl appl = variable.name;
        // check if the name is builtin
        if (appl.name.equals(ZNames.typeinfo)) {
          Expr type = getElemType(ReducerUtil.getTypeAn(appl.actuals[0]));
          newExpr = new MZ.Expr.Term(
                          ZNames.TypeInfo,
                          new ArraySeq<MZ.Expr>(makeTypeInfoDeno(exprLocator, type))
                        );
        } else if (appl.name.equals(ZNames.giventypeinfo)) {
          Expr type = getElemType(ReducerUtil.getTypeAn(appl.actuals[0]));
          switch (type) {
            case Expr.Variable app -> {
              newExpr =
                new MZ.Expr.Call(
                      MZConstants.givenTypeInfoFunc,
                      new ArraySeq<MZ.Expr>(
                            new MZ.Expr.Variable(
                                  MZConstants.makeGivenTypeInfoName(app.name.name)
                                ).set(exprLocator),
                            makeTypeInfoDeno(exprLocator,
                                             type)
                          )
                    );
            }
            default -> {
              diag = diag.add(exprLocator,
                              Diag.Category.Error,
                              "'giventypeinfo' only applicable for given types");
              newExpr = new MZ.Expr.Empty();
            }
          }
        } else if (false && appl.name.equals(ZNames.typegen)) {
          Expr type = getElemType(ReducerUtil.getTypeAn(appl.actuals[0]));
          switch (type) {
            case Expr.Variable app -> {
              newExpr =
                new MZ.Expr.Variable(
                      MZConstants.makeGivenTypeGenName(app.name.name)
                    ).set(exprLocator);
            }
            default -> {
              diag = diag.add(exprLocator,
                              Diag.Category.Error,
                              "'typgen' only applicable for given types");
              newExpr = new MZ.Expr.Empty();
            }
          }
        } else if (appl.name.equals(ZNames.emptyset)) {
          newExpr = new MZ.Expr.Empty();
        }
        // check if the name is forced to be given
        else if (context.isGiven(appl.name)) {
          newExpr = new MZ.Expr.Universal();
        } else {
          // normal procedure
          Seq<MZ.Expr> actuals;
          if (appl.actuals.length > 0) {
            // explicite actualization
            actuals = new ArraySeq<Expr>(appl.actuals).map(this::ereduce);
          } else if (Act.isProvided(appl.an)) {
            // actuals = new ArraySeq(Act.get(appl.an)).map(ereduce);
            // implicite actualization: make universal
            actuals =
              new ArraySeq<MZ.Expr>(Act.get(appl.an).length,
                                    i -> MZ.Expr.Universal());
          } else
            actuals = new ArraySeq<MZ.Expr>();
          newExpr = resolve(exprLocator, appl.name, actuals);
        }
      }

      case Expr.Tuple tuple -> {
        Expr[] elements = tuple.elements;
        Name cons = getTupleCons(elements.length);
        newExpr = new MZ.Expr.Term(cons,
                                   new ArraySeq<Expr>(elements).map(this::ereduce));
      }

      case Expr.Product product -> {
        Expr[] elements = product.elements;
        newExpr =
          strictify(
            new ArraySeq<Expr>(elements).map(this::ereduce),
            els -> {
              Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
              enterContext();
              int i = 1;
              // was: els.apply(fun (MZ.Expr ex)->void { ... i++ ... vars = ...; })
              for (Enum<MZ.Expr> en = els.enumerate(); en.more(); en.advance()) {
                MZ.Expr ex = en.current();
                MZ.Expr var = newAnonVar(ex.locator, "%c" + (i++));
                vars = vars.append(var);
                addProperty(
                  new MZ.Property.Member(var, ex).set(ex.locator)
                );
              }
              return exitContextWithSchema(
                       new MZ.Expr.Term(
                             getTupleCons(elements.length),
                             vars
                           ),
                       exprLocator
                     );
            }
          );
      }

      case Expr.Binding binding -> {
        Seq<Expr.Decl.Eqn> sorted =
          new ArraySeq<Expr.Decl.Eqn>(binding.bindings).sort(
                (e1, e2) -> Name.lexCompare(e1.name.name, e2.name.name)
              );
        Seq<Name> fields = new BufferSeq<Name>();
        Seq<MZ.Expr> args = new BufferSeq<MZ.Expr>();
        // was: sorted.apply(fun (Expr.Decl.Eqn e)->void { fields = ...; args = ...; })
        for (Enum<Expr.Decl.Eqn> en = sorted.enumerate(); en.more(); en.advance()) {
          Expr.Decl.Eqn e = en.current();
          fields = fields.append(e.name.name);
          args = args.append(ereduce(e.def));
        }
        Name cons = getBindingCons(fields);
        newExpr = new MZ.Expr.Term(cons, ArraySeq.from(args));
      }

      case Expr.Display display -> {
        Expr[] elements = display.elements;
        if (elements.length == 0) {
          newExpr = new MZ.Expr.Empty();
        } else {
          newExpr = null;
          for (int i = 0; i < elements.length; i++) {
            MZ.Expr ex = ereduce(elements[i]);
            ex = new MZ.Expr.Singleton(ex).set(ex.locator);
            if (newExpr == null)
              newExpr = ex;
            else
              newExpr = new MZ.Expr.Union(newExpr, ex).set(exprLocator);
          }
        }
      }

      case Expr.Cond cond -> {
        MZ.Expr prop = liftProp(cond.cond);
        enterContext();
        MZ.Expr e1 = exitContextWithMu(ereduce(cond.thenPath),
                                       ReducerUtil.getLocatorAn(cond.thenPath));
        enterContext();
        MZ.Expr e2 = exitContextWithMu(ereduce(cond.elsePath),
                                       ReducerUtil.getLocatorAn(cond.elsePath));
        newExpr =
          new MZ.Expr.Conditional(prop, e1, e2);
      }

      case Expr.Quantor quantor -> {
        Expr.QuantorKind kind = quantor.kind;
        Expr matrix = quantor.matrix;
        Expr range = quantor.range;
        if (kind == Expr.QuantorKind.Lambda) {
          // [[\\ matrix @ range]] ==
          //    LET t == [[matrix]] IN { (p,y) | p' in t; y = [E[range]] }
          //                where (E,p',p) = ctuplePatterns(matrix)
          // FIXME: optimizations!
          MZ.Expr mexpr = ereduce(matrix);
          enterContext();
          Pair<MZ.Expr, MZ.Expr> ctup = ctuplePatterns(matrix);
          MZ.Expr rexpr = ereduce(range);
          newExpr =
            strictifyOne(
              mexpr,
              mvar -> {
                MZ.Expr.Variable y = newAnonVar(rexpr.locator, "%r");
                addBound(y.name, rexpr);
                addProperty(
                  new MZ.Property.Member(ctup.fst, mvar)
                        .set(mexpr.locator)
                );
                return
                  exitContextWithSchema(
                    makePair(ctup.snd, y),
                    exprLocator
                  );
              }
            );
        } else if (kind == Expr.QuantorKind.Mu
                   || kind == Expr.QuantorKind.Let
                   || kind == Expr.QuantorKind.Set) {
          // [[MU matrix @ range]] ==
          //   LET t == [[matrix]] IN [MU]{y\X | p in t; y = [E[range]]}
          //      where (E,p) = binderPattern(matrix), X = vars(p)
          // FIXME: optimizations!
          MZ.Expr mexpr = ereduce(matrix);
          enterContext();
          MZ.Expr mpat = schemaPattern(matrix);
          MZ.Expr rexpr = ereduce(range);
          newExpr =
            strictifyOne(
              mexpr,
              t -> {
                MZ.Expr.Variable y = newAnonVar(rexpr.locator, "%b");
                addBound(y.name, rexpr);
                addProperty(
                  new MZ.Property.Member(mpat, t)
                        .set(mexpr.locator)
                );
                MZ.Expr schema = exitContextWithSchema(y, rexpr.locator);
                if (kind == Expr.QuantorKind.Set) {
                  return schema;
                } else {
                  return new MZ.Expr.Hom(MZConstants.muHom, schema)
                               .set(rexpr.locator);
                }
              }
            );

        } else if (kind == Expr.QuantorKind.Exists
                   || kind == Expr.QuantorKind.Exists1) {
          // [[EX matrix @ range]] ==
          //   LET t1 == [[matrix]] t2 == [[range]] IN {p\X|p1 in t1; p2 in t2}
          //       where (p,p1,p2) = quantifiedPatterns(matrix, range)
          //             X = vars(p1)
          newExpr =
            strictify(
              new ArraySeq<MZ.Expr>(ereduce(matrix), ereduce(range)),
              es -> {
                MZ.Expr t1 = es.at(0);
                MZ.Expr t2 = es.at(1);
                enterContext();
                Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> jpat =
                  joinedPatterns(expr, matrix, range, true);
                Pair<MZ.Expr, MZ.Expr> jsnd = jpat.snd;
                addProperty(
                  new MZ.Property.Member(jsnd.fst, t1)
                        .set(t1.locator)
                );
                addProperty(
                  new MZ.Property.Member(jsnd.snd, t2)
                        .set(t2.locator)
                );
                MZ.Expr schema = exitContextWithSchema(jpat.fst, exprLocator);
                if (kind == Expr.QuantorKind.Exists1) {
                  return new MZ.Expr.Hom(
                               MZConstants.exists1SchemaHom,
                               schema
                             ).set(schema.locator);
                } else {
                  return schema;
                }
              }
            );
        } else if (kind == Expr.QuantorKind.Forall) {
          // [[ALL matrix @ range]] ==
          //   LET t1 == [[matrix]] t2 == [[range]] IN
          //          {p\X|p1 in t1; p2 in t2; t1 <= t2[p2->p1]}
          //       where (p,p1,p2) = joinedPatterns(expr, matrix, range)
          //             X = vars(p1)
          // CHECKME, OPTIMIZE
          newExpr =
            strictify(
              new ArraySeq<MZ.Expr>(ereduce(matrix), ereduce(range)),
              es -> {
                MZ.Expr t1 = es.at(0);
                MZ.Expr t2 = es.at(1);
                enterContext();
                Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> jpat =
                  joinedPatterns(expr, matrix, range, true);
                Pair<MZ.Expr, MZ.Expr> jsnd = jpat.snd;
                addProperty(
                  new MZ.Property.Member(jsnd.fst, t1)
                        .set(t1.locator)
                );
                addProperty(
                  new MZ.Property.Member(jsnd.snd, t2)
                        .set(t2.locator)
                );
                addProperty(
                  new MZ.Property.Subset(t1,
                                         new MZ.Expr.Translate(
                                               t2,
                                               jsnd.snd, jsnd.fst
                                             ).set(t2.locator)
                                        ).set(exprLocator)
                );
                return
                  exitContextWithSchema(jpat.fst, exprLocator);
              }
            );
        } else {
          // Pizza's generated default of an exhaustive switch (all seven kinds are above).
          throw FatalError.unexpectedCase("quantor kind");
        }
          /*
        default:
          // FIXME: case Expr.QuantorKind.Exists1:
          cantHandleError(exprLocator);
          newExpr = makeUndef(exprLocator);
          */
      }

      case Expr.Select select -> {
        Expr oper = select.oper;
        Name name = select.name.name;
        // addProperty([[p = oper]]) |- x
        //    where (p,x) = projectionPattern(oper, name)
        MZ.Expr eoper = ereduce(oper);
        // enterContext();
        Pair<MZ.Expr, MZ.Expr.Variable> proj = projectionPatterns(oper, name);
        addProperty(
          new MZ.Property.Equal(proj.fst, eoper).set(eoper.locator)
        );
        newExpr = proj.snd; // exitContextWithMu(proj.snd, exprLocator);
      }


      case Expr.Unary unary -> {
        Expr.UnaryKind kind = unary.kind;
        Expr oper = unary.oper;

        if (kind == Expr.UnaryKind.Power) {
          // [[P oper]] == LET t = [[oper]] IN {x|x <= t}
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                MZ.Expr x = newAnonVar(exprLocator, "%s");
                addProperty(
                  new MZ.Property.Subset(x, t).set(exprLocator)
                );
                return
                  exitContextWithSchema(x, exprLocator);
              }
            );

        } else if (kind == Expr.UnaryKind.Theta) {
          // [[THETA oper']] == <n1==n1',...,nn==nn'>
          MZ.Expr newOper = ereduce(oper);
          Seq<Name> names = getSchemaSign(oper);
          String decore;
          switch (oper) {
            case Expr.Unary ou when ou.kind instanceof Expr.UnaryKind.Decorate d ->
              decore = d.decore;
            default ->
              decore = "";
          }
          Map<Name, MZ.Expr> args = new HashMap<Name, MZ.Expr>();
          // was: names.apply(fun (Name n)->void { ... args = args.define(...); })
          for (Enum<Name> en = names.enumerate(); en.more(); en.advance()) {
            Name n = en.current();
            String repr = n.getRepr();
            if (repr.endsWith(decore)) {
              repr = repr.substring(0, repr.length() - decore.length());
            } else {
              throw FatalError.assertionFailed(
                      "expected a name decorated with `" +
                      decore + "'; found: " + repr + " at " +
                      exprLocator
                    );
            }
            args = args.define(new Name(repr),
                               resolve(exprLocator, n, new ArraySeq<MZ.Expr>()));
          }
          Seq<Name> rnames =
            ArraySeq.from(args.domain()).sort(Name::lexCompare);
          final Map<Name, MZ.Expr> fargs = args;
          newExpr = new MZ.Expr.Term(getBindingCons(rnames),
                                     rnames.<MZ.Expr>map(n -> fargs.get(n))
                                    );

        } else if (kind == Expr.UnaryKind.Not) {
          newExpr = new MZ.Expr.Complement(ereduce(oper));

        } else if (kind == Expr.UnaryKind.Delta) {
          // [[DELTA oper]] == LET t = [[oper]] IN {p|p1 in t;p2 in t}
          //      where (p,p1,p2) = deltaPatterns(expr, oper)
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> delta =
                  deltaPatterns(expr, oper);
                Pair<MZ.Expr, MZ.Expr> dsnd = delta.snd;
                addProperty(
                  new MZ.Property.Member(dsnd.fst, t)
                        .set(exprLocator)
                );
                addProperty(
                  new MZ.Property.Member(dsnd.snd, t)
                        .set(exprLocator)
                );
                return exitContextWithSchema(delta.fst, exprLocator);
              }
            );

        } else if (kind instanceof Expr.UnaryKind.Decorate dk) {
          String decore = dk.decore;
          // [[oper ']] == LET t = [[oper]] IN {p|p1 in t}
          //      where (p,p1) = decoratedPatterns(oper, decore)
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                Pair<MZ.Expr, MZ.Expr> dpat = decoratedPatterns(oper, decore);
                addProperty(
                  new MZ.Property.Member(dpat.snd, t).set(exprLocator)
                );
                return exitContextWithSchema(dpat.fst, exprLocator);
              }
            );

        } else if (kind == Expr.UnaryKind.Xi) {
          // [[XI oper]] == LET t = [[oper]]
          //                IN {p|x1=x1';...;xn=xn';p1 in t;p2 in t}
          //      where (p,p1,p2) = deltaPatterns(oper)
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> delta =
                  deltaPatterns(expr, oper);
                Pair<MZ.Expr, MZ.Expr> dsnd = delta.snd;
                Seq<MZ.Expr> args1 = ((MZ.Expr.Term) dsnd.fst).args;
                Seq<MZ.Expr> args2 = ((MZ.Expr.Term) dsnd.snd).args;
                for (int i = 0; i < args1.size() && i < args2.size(); i++) {
                  addProperty(
                    new MZ.Property.Equal(args1.at(i), args2.at(i))
                          .set(exprLocator)
                  );
                }
                addProperty(
                  new MZ.Property.Member(dsnd.fst, t).set(exprLocator)
                );
                addProperty(
                  new MZ.Property.Member(dsnd.snd, t)
                        .set(exprLocator)
                );
                return exitContextWithSchema(delta.fst, exprLocator);
              }
            );

        } else if (kind == Expr.UnaryKind.Pre) {
          // [[PRE oper]] == LET t = [[oper]] IN {p|p1 in t}
          //       where (p,p1) = hidePatterns(oper, prime or output )
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                Pair<MZ.Expr, MZ.Expr> pat =
                  hidePatterns(oper,
                               n -> {
                                 String r = n.getRepr(); int s = r.length();
                                 return r.charAt(s - 1) == Name.prime ||
                                        r.charAt(s - 1) == Name.output;
                               }
                              );
                addProperty(
                  new MZ.Property.Member(pat.snd, t).set(exprLocator)
                );
                return exitContextWithSchema(pat.fst, exprLocator);
              }
            );

        } else if (kind instanceof Expr.UnaryKind.Hide hk) {
          NameAppl[] names = hk.names;
          // [[oper \ names]] == LET t = [[oper]] IN {p|p1 in t}
          //       where (p,p1) = hidePatterns(oper, names )
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                Pair<MZ.Expr, MZ.Expr> pat =
                  hidePatterns(oper,
                               n -> {
                                 for (int i = 0; i < names.length; i++) {
                                   if (names[i].name.equals(n)) return true;
                                 }
                                 return false;
                               }
                              );
                addProperty(
                  new MZ.Property.Member(pat.snd, t)
                        .set(exprLocator)
                );
                return exitContextWithSchema(pat.fst, exprLocator);
              }
            );


        } else if (kind instanceof Expr.UnaryKind.Renaming rk) {
          Expr.Rename[] renames = rk.renames;
          // [[oper[newi/oldi]] == LET t = [[oper]] IN {p|xi=yi;...;p1 in t}
          //       where (eqns,p,p1) = renamePatterns(oper, renames);
          newExpr =
            strictifyOne(
              ereduce(oper),
              t -> {
                enterContext();
                Seq<Name> names = getSchemaSign(oper).sort(Name::lexCompare);
                Seq<Name> resNames = getSchemaSign(expr).sort(Name::lexCompare);
                Seq<MZ.Expr> vars = newVars(ReducerUtil.getLocatorAn(oper),
                                            names);
                Seq<MZ.Expr> resVars = newVars(ReducerUtil.getLocatorAn(expr),
                                               resNames);
                for (int i = 0; i < renames.length; i++) {
                  addProperty(
                    new MZ.Property.Equal(
                          context.lookup(renames[i]._new.name),
                          context.lookup(renames[i].old.name)
                        ).set(ReducerUtil.getLocatorAn(renames[i]))
                  );
                }
                addProperty(
                  new MZ.Property.Member(
                        new MZ.Expr.Term(
                              getBindingCons(names),
                              vars
                            ),
                        t)
                        .set(exprLocator)
                );
                return exitContextWithSchema(
                         new MZ.Expr.Term(
                               getBindingCons(resNames),
                               resVars
                             ),
                         exprLocator
                       );
              }
            );

        } else {
          // MISSING: SelectiveDelta
          cantHandleError(exprLocator);
          newExpr = makeUndef(exprLocator);
        }
      }

      case Expr.Binary binary -> {
        Expr.BinaryKind kind = binary.kind;
        Expr left = binary.left;
        Expr right = binary.right;

        if (kind == Expr.BinaryKind.Apply) {
          newExpr = null;
          switch (left) {
            case Expr.Variable lv -> {
              Name name = lv.name.name;
              Expr[] act = lv.name.actuals;
              Expr[] args;
              // test for builtin transformations
              if (name.equals(ZNames.mapsto)) {
                newExpr = ereduce(right);
              }
              else if (name.equals(ZNames.intersect) && act.length == 0
                       && (args = unpair(right)) != null) {
                newExpr =
                  new MZ.Expr.Intersection(ereduce(args[0]), ereduce(args[1]));
              }
              else if (name.equals(ZNames.union) && act.length == 0
                       && (args = unpair(right)) != null) {
                newExpr =
                  new MZ.Expr.Union(ereduce(args[0]), ereduce(args[1]));
              }
              else if (name.equals(MZConstants.homWrapper)) {
                args = unpair(right);
                if (args != null) {
                  switch (args[0]) {
                    case Expr.Variable hv -> {
                      newExpr = new MZ.Expr.Hom(hv.name.name, ereduce(args[1]));
                    }
                    default -> {
                      args = null;
                    }
                  }
                }
                if (args == null) {
                  cantHandleError(exprLocator);
                  newExpr = makeUndef(exprLocator);
                }
              }
              // test for native call
              else if (context.isNative(name)) {
                // NB. Whether the argument has the right structure
                // (is a literal tuple) is unchecked
                newExpr =
                  new MZ.Expr.Call(name,
                                   new ArraySeq<Expr>(untuple(right)).map(this::ereduce));
              }
            }
            default -> { }
          }
          if (newExpr == null) {
            MZ.Expr f = ereduce(left);
            MZ.Expr a = ereduce(right);
            if (checkTotal(f)) {
              // lift to a member test
              newExpr = newAnonVar(exprLocator, "%y");
              addProperty(
                new MZ.Property.Member(
                      makePair(a, newExpr).set(exprLocator),
                      f
                    ).set(exprLocator)
              );
            } else {
              newExpr = makeApply(exprLocator, f, a);
            }
          }

        } else if (kind == Expr.BinaryKind.And) {
          if (sameSchemaSign(left, right)) {
            // both schemas have the same type
            newExpr = new MZ.Expr.Intersection(ereduce(left),
                                               ereduce(right));
          } else {
            // [[left /\ right]] ==
            //   LET t1 == [[left] t2 == [[right]] IN
            //   {p|p1 in left; p2 in right}
            //     where (p, p1,p2) = joinedPatterns(expr, left, right)
            newExpr =
              strictify(
                new ArraySeq<MZ.Expr>(ereduce(left), ereduce(right)),
                ts -> {
                  MZ.Expr t1 = ts.at(0);
                  MZ.Expr t2 = ts.at(1);
                  enterContext();
                  Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> pats =
                    joinedPatterns(expr, left, right, true);
                  Pair<MZ.Expr, MZ.Expr> psnd = pats.snd;
                  addProperty(
                    new MZ.Property.Member(
                          psnd.fst,
                          ts.at(0)
                        ).set(ts.at(0).locator)
                  );
                  addProperty(
                    new MZ.Property.Member(
                          psnd.snd,
                          ts.at(1)
                        ).set(ts.at(1).locator)
                  );
                  return exitContextWithSchema(pats.fst, exprLocator);
                }
              );
          }

        } else if (kind == Expr.BinaryKind.Or) {
          if (sameSchemaSign(left, right)) {
            // both schemas have the same type
            newExpr = new MZ.Expr.Union(ereduce(left),
                                        ereduce(right));
          } else {
            // [[left \/ right]] ==
            //   LET t1 == [[left] t2 == [[right]] IN
            //   {p|p1 in left} + {p| p2 in right}
            //     where (p, p1,p2) = joinedPatterns(expr, left, right)
            newExpr =
              strictify(
                new ArraySeq<MZ.Expr>(ereduce(left), ereduce(right)),
                ts -> {
                  MZ.Expr t1 = ts.at(0);
                  MZ.Expr t2 = ts.at(1);
                  enterContext();
                  Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> pats =
                    joinedPatterns(expr, left, right, true);
                  addProperty(
                    new MZ.Property.Member(
                          pats.snd.fst,
                          t1
                        ).set(t1.locator)
                  );
                  MZ.Expr schema1 = exitContextWithSchema(pats.fst, t1.locator);
                  enterContext();
                  // FIXME: avoid recalculate because of variables in ctx
                  pats = joinedPatterns(expr, left, right, true);
                  addProperty(
                    new MZ.Property.Member(
                          pats.snd.snd,
                          t2
                        ).set(t2.locator)
                  );
                  MZ.Expr schema2 = exitContextWithSchema(pats.fst, t2.locator);
                  return new MZ.Expr.Union(schema1, schema2).set(exprLocator);
                }
              );
          }

        } else if (kind == Expr.BinaryKind.Implies) {
          if (sameSchemaSign(left, right)) {
            // both schemas have the same type
            newExpr = new MZ.Expr.Union(
                            new MZ.Expr.Complement(ereduce(left))
                                  .set(exprLocator),
                            ereduce(right)
                          );
          } else {
            // [[left => right]] ==
            //   LET t1 == [[left] t2 == [[right]] IN
            //   ~{p|p1 in left} + {p|p2 in right}
            //       where (p,p1,p2) = joinedPatterns(expr, left, right)
            newExpr =
               strictify(
                 new ArraySeq<MZ.Expr>(ereduce(left), ereduce(right)),
                 ts -> {
                   MZ.Expr t1 = ts.at(0);
                   MZ.Expr t2 = ts.at(1);
                   enterContext();
                   Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> pats =
                     joinedPatterns(expr, left, right, true);
                   addProperty(
                     new MZ.Property.Member(
                           pats.snd.fst,
                           t1
                         ).set(t1.locator)
                   );
                   MZ.Expr schema1 = exitContextWithSchema(pats.fst, t1.locator);
                   enterContext();
                   pats = joinedPatterns(expr, left, right, true);
                   addProperty(
                     new MZ.Property.Member(
                           pats.snd.snd,
                           t2
                         ).set(t2.locator)
                   );
                   MZ.Expr schema2 = exitContextWithSchema(pats.fst, t2.locator);
                   return
                     new MZ.Expr.Union(
                           new MZ.Expr.Complement(schema1).set(schema1.locator),
                           schema2
                         );
                 }
               );

          }

        } else if (kind == Expr.BinaryKind.Compose
                   || kind == Expr.BinaryKind.Pipe) {
          newExpr =
            strictify(
              new ArraySeq<MZ.Expr>(ereduce(left), ereduce(right)),
              ts -> {
                MZ.Expr t1 = ts.at(0);
                MZ.Expr t2 = ts.at(1);
                enterContext();
                Function<Name, Name> matcher;
                if (kind == Expr.BinaryKind.Compose) {
                  matcher = n -> n.decorate("" + Name.prime);
                } else {
                  matcher = n -> {
                    String[] comps = n.getComponents();
                    if (comps.length > 1 &&
                        comps[comps.length - 1].equals("" + Name.input)) {
                      return n.undecorate(1)
                              .decorate("" + Name.output);
                    } else
                      return null;
                  };
                }
                Pair<MZ.Expr, Pair<MZ.Expr, MZ.Expr>> pats =
                  composedPatterns(expr, left, right, matcher);
                Pair<MZ.Expr, MZ.Expr> psnd = pats.snd;
                addProperty(
                  new MZ.Property.Member(
                        psnd.fst,
                        ts.at(0)
                      ).set(ts.at(0).locator)
                );
                addProperty(
                  new MZ.Property.Member(
                        psnd.snd,
                        ts.at(1)
                      ).set(ts.at(1).locator)
                );
                return exitContextWithSchema(pats.fst, exprLocator);
              }
            );

        } else {
          cantHandleError(exprLocator);
          newExpr = makeUndef(exprLocator);
          /*
        case Iff:
          throw FatalError.notImplemented();

        case Compose:
          throw FatalError.notImplemented();

        case Pipe:
          throw FatalError.notImplemented();

        case Project:
          throw FatalError.notImplemented();
          */
        }
      }

      case Expr.Text text -> {
        Predicate[] props = text.props;
        Pair<Map<Name, MZ.Expr>,
             Pair<Seq<Function<MZ.Expr, Seq<MZ.Property>>>, Seq<MZ.Expr>>> ds =
          liftDecls(text.decls);
        Map<Name, MZ.Expr> dsMapping = ds.fst;
        Pair<Seq<Function<MZ.Expr, Seq<MZ.Property>>>, Seq<MZ.Expr>> dsSnd = ds.snd;
        Seq<Function<MZ.Expr, Seq<MZ.Property>>> dsGens = dsSnd.fst;
        Seq<MZ.Expr> dsBounds = dsSnd.snd;
        newExpr =
          strictify(
            dsBounds,
            ts -> {
              enterContext();
              // was: ds.fst.apply(fun (Pair<Name,MZ.Expr> p)->void { ... })
              for (Enum<Pair<Name, MZ.Expr>> en = dsMapping.enumerate();
                   en.more(); en.advance()) {
                Pair<Name, MZ.Expr> p = en.current();
                context.scope = context.scope.define(p.fst, p.snd);
                addBound(p.fst, p.snd);
              }
              for (int i = 0; i < ts.size(); i++) {
                Seq<MZ.Property> dprops = dsGens.at(i).apply(ts.at(i));
                dprops.apply(
                  prop -> {
                    addProperty(prop);
                  }
                );
              }
              for (int i = 0; i < props.length; i++) {
                preduce(props[i]);
              }
              Seq<Name> sortedNames = ArraySeq.from(dsMapping.domain())
                                              .sort(Name::lexCompare);
              MZ.Expr pat =
                new MZ.Expr.Term(getBindingCons(sortedNames),
                                 makeArgs(sortedNames, exprLocator));
              return exitContextWithSchema(pat, exprLocator);
            }
          );

      }

      case Expr.Signature signature -> {
        Name[] names = signature.names;
        newExpr =
          strictify(
            new ArraySeq<Expr>(signature.types).map(this::ereduce),
            els -> {
              Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
              enterContext();
              int i = 0;
              // was: els.apply(fun (MZ.Expr ex)->void { ... names[i++] ... vars = ...; })
              for (Enum<MZ.Expr> en = els.enumerate(); en.more(); en.advance()) {
                MZ.Expr ex = en.current();
                MZ.Expr var = newVar(ex.locator, names[i++].getRepr());
                vars = vars.append(var);
                addProperty(
                  new MZ.Property.Member(var, ex).set(ex.locator)
                );
              }
              Seq<Name> snames = new ArraySeq<Name>(names).sort(Name::lexCompare);
              return exitContextWithSchema(
                       new MZ.Expr.Term(
                             getBindingCons(snames),
                             vars
                           ),
                       exprLocator
                     );
            }
          );
      }

      default -> {
        // MISSING on this level: GivenType, FreeType
        // throw FatalError.assertionFailed("cannot handle: " + expr);
        cantHandleError(exprLocator);
        newExpr = makeUndef(exprLocator);
      }
    }

    newExpr.locator = exprLocator;
    return newExpr;
  }


  /** Lifting declarations. */
  private Pair<Map<Name, MZ.Expr>,
               Pair<Seq<Function<MZ.Expr, Seq<MZ.Property>>>, Seq<MZ.Expr>>>
  liftDecls(Expr.Decl[] decls) {
    Map<Name, MZ.Expr> mapping = new HashMap<Name, MZ.Expr>();
    Seq<Function<MZ.Expr, Seq<MZ.Property>>> gens =
      new BufferSeq<Function<MZ.Expr, Seq<MZ.Property>>>();
    Seq<MZ.Expr> bounds = new BufferSeq<MZ.Expr>();
    for (int i = 0; i < decls.length; i++) {
      Locator declLoc = ReducerUtil.getLocatorAn(decls[i]);
      switch (decls[i]) {
        case Expr.Decl.Direct direct -> {
          NameDecl[] names = direct.names;
          Expr type = direct.type;
          MZ.Expr mtype;
          boolean trivial;
          if (ReducerUtil.checkDecls) {
            // declarations shall be incorporated
            mtype = ereduce(type);
            switch (mtype) {
              case MZ.Expr.Universal x ->
                trivial = true;
              default ->
                trivial = false;
            }
          } else {
            mtype = null;
            trivial = true;
          }
          Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
          for (int j = 0; j < names.length; j++) {
            MZ.Expr var;
            Name name = names[j].name;
            try {
              var = mapping.get(name);
            }
            catch (NoSuchElementException e) {
              var = new MZ.Expr.Variable(name).set(
                          ReducerUtil.getLocatorAn(names[j])
                        );
              mapping = mapping.define(name, var);
            }
            if (!trivial) {
              vars = vars.append(var);
            }
          }
          if (!trivial) {
            final Seq<MZ.Expr> fvars = vars;
            gens = gens.append(
                     bound ->
                       fvars.<MZ.Property>map(
                         v -> new MZ.Property.Member(v, bound)
                                    .set(declLoc)
                       )
                   );
            bounds = bounds.append(mtype);
          }
        }

        case Expr.Decl.Eqn eqn -> {
          NameDecl name = eqn.name;
          MZ.Expr mdef = ereduce(eqn.def);
          MZ.Expr var;
          try {
            var = mapping.get(name.name);
          }
          catch (NoSuchElementException e) {
            var = new MZ.Expr.Variable(name.name).set(declLoc);
            mapping = mapping.define(name.name, var);
          }
          final MZ.Expr fvar = var;
          gens = gens.append(
                   bound ->
                     new ArraySeq<MZ.Property>(
                           new MZ.Property.Equal(fvar, bound)
                                 .set(declLoc)
                         )
                 );
          bounds = bounds.append(mdef);
        }

        case Expr.Decl.Inclusion inclusion -> {
          Expr schema = inclusion.schema;
          MZ.Expr mschema = ereduce(schema);
          Seq<Name> snames = getSchemaSign(schema).sort(Name::lexCompare);
          Seq<MZ.Expr> vars = new BufferSeq<MZ.Expr>();
          // was: snames.apply(fun (Name n)->void { ... mapping = ...; vars = ...; })
          for (Enum<Name> en = snames.enumerate(); en.more(); en.advance()) {
            Name n = en.current();
            MZ.Expr var;
            try {
              var = mapping.get(n);
            }
            catch (NoSuchElementException e) {
              var = new MZ.Expr.Variable(n).set(declLoc);
              mapping = mapping.define(n, var);
            }
            vars = vars.append(var);
          }
          final Seq<MZ.Expr> fvars = vars;
          gens = gens.append(
                   bound ->
                     new ArraySeq<MZ.Property>(
                           new MZ.Property.Member(
                                 new MZ.Expr.Term(
                                       getBindingCons(snames),
                                       fvars
                                     ).set(declLoc),
                                 bound
                               ).set(declLoc)
                         )
                 );
          bounds = bounds.append(mschema);
        }

        // Pizza's generated default of an exhaustive switch.
        default -> throw FatalError.unexpectedCase("declaration");
      }
    }
    return Pair.Pair(mapping, Pair.Pair(gens, bounds));
  }


  // ========================================================================
  // Reducing predicates


  /** Reduce a predicate, adding it as a property to the current context. */
  private void preduce(Predicate pred) {

    Locator loc = ReducerUtil.getLocatorAn(pred);

    switch (pred) {

      case Predicate.Fact fact when fact.kind == Predicate.FactKind.True -> { }


      case Predicate.Fact fact when fact.kind == Predicate.FactKind.False -> {
        addFailure();
      }

      case Predicate.Schema sp -> {
        Expr schema = sp.schema;
        Seq<Name> snames = getSchemaSign(schema).sort(Name::lexCompare);
        MZ.Expr pat =
          new MZ.Expr.Term(
                getBindingCons(snames),
                snames.<MZ.Expr>map(n -> resolve(loc, n, new ArraySeq<MZ.Expr>()))
              ).set(loc);
        addProperty(
          new MZ.Property.Member(
                pat,
                ereduce(schema)
              ).set(loc)
        );
      }

      case Predicate.Test test -> {
        Expr elem = test.elem;
        Expr set = test.set;
        Expr[] args;
        switch (set) {
          case Expr.Variable sv -> {
            Name name = sv.name.name;
            // test for builtin transformations
            if (name.equals(ZNames.in) && (args = unpair(elem)) != null) {
              // test for membership on a native predicate
              switch (args[1]) {
                case Expr.Variable pv -> {
                  Name predName = pv.name.name;
                  if (context.isNative(predName)) {
                    addProperty(
                      new MZ.Property.Call(predName,
                                           new ArraySeq<Expr>(untuple(args[0]))
                                                       .map(this::ereduce))
                            .set(loc)
                    );
                    return;
                  }
                }
                default -> { }
              }
              addProperty(
                new MZ.Property.Member(ereduce(args[0]),
                                       ereduce(args[1]))
                      .set(loc)
              );
              return;
            } else if (name.equals(ZNames.equal) &&
                       (args = unpair(elem)) != null) {
              addProperty(
                new MZ.Property.Equal(ereduce(args[0]),
                                      ereduce(args[1]))
                      .set(loc)
              );
              return;
            } else if (name.equals(ZNames.subseteq) &&
                       (args = unpair(elem)) != null) {
              addProperty(
                new MZ.Property.Subset(ereduce(args[0]),
                                       ereduce(args[1]))
                      .set(loc)
              );
              return;
            }
          }
          default -> { }
        }
        addProperty(
          new MZ.Property.Member(ereduce(elem), ereduce(set))
                .set(loc)
        );
      }

      case Predicate.Unary unary when unary.kind == Predicate.UnaryKind.Not -> {
        addProperty(
          new MZ.Property.IsTrue(
                new MZ.Expr.Hom(MZConstants.isEmptyHom,
                                liftProp(unary.oper)
                               ).set(loc)
                ).set(loc)
        );
      }

      case Predicate.Binary binary when binary.kind == Predicate.BinaryKind.And -> {
        preduce(binary.left); preduce(binary.right);
      }

      case Predicate.Binary binary when binary.kind == Predicate.BinaryKind.Or -> {
        addProperty(
          new MZ.Property.IsTrue(
                new MZ.Expr.Hom(MZConstants.isNotEmptyHom,
                                new MZ.Expr.Union(
                                      liftProp(binary.left),
                                      liftProp(binary.right)
                                    ).set(loc)
                               ).set(loc)
              ).set(loc)
        );
      }

      case Predicate.Binary binary when binary.kind == Predicate.BinaryKind.Implies -> {
        addProperty(
          new MZ.Property.Subset(
                liftProp(binary.left),
                liftProp(binary.right)
              ).set(loc)
        );
      }

      case Predicate.Binary binary when binary.kind == Predicate.BinaryKind.Iff -> {
        addProperty(
          new MZ.Property.Equal(
                liftProp(binary.left),
                liftProp(binary.right)
              ).set(loc)
        );
      }

      case Predicate.Quantor quantor -> {
        Predicate.QuantorKind kind = quantor.kind;
        Expr matrix = quantor.matrix;
        Predicate range = quantor.range;
        if (kind == Predicate.QuantorKind.Exists
            || kind == Predicate.QuantorKind.Exists1
            || kind == Predicate.QuantorKind.Let) {
          // [[EX S @ P]] = [[fvars IN [S|P]]]
          // [[EX1 S @ P]] = [[HOM(EX1, {S|P}) = true]]
          Locator mloc = ReducerUtil.getLocatorAn(matrix);
          Seq<Name> names = getSchemaSign(matrix).sort(Name::lexCompare);
          Name cons = getBindingCons(names);
          enterContext();
          Seq<MZ.Expr> vars = newVars(mloc, names);
          MZ.Expr pat = new MZ.Expr.Term(cons, vars).set(mloc);
          addProperty(
            new MZ.Property.Member(pat, ereduce(matrix)).set(mloc)
          );
          preduce(range);
          MZ.Expr schema = exitContextWithSchema(pat, loc);
          if (kind == Predicate.QuantorKind.Exists) {
            Seq<MZ.Expr> nvars = newVars(loc, names); // possibly renamed for
                                                      // current context
            MZ.Expr npat = new MZ.Expr.Term(cons, nvars).set(loc);
            addProperty(
              new MZ.Property.Member(npat, schema).set(loc)
            );
          } else if (kind == Predicate.QuantorKind.Exists1
                     || kind == Predicate.QuantorKind.Let) {
            addProperty(
              new MZ.Property.IsTrue(
                    new MZ.Expr.Hom(MZConstants.exists1Hom,
                                    schema).set(loc)
                  ).set(loc)
            );
          }
        } else if (kind == Predicate.QuantorKind.Forall) {
          // [[ALL S @ P]] == [[S]] <= [[{sign(S) | P}]]
          Seq<Name> names = getSchemaSign(matrix).sort(Name::lexCompare);
          Name cons = getBindingCons(names);
          enterContext();
          Seq<MZ.Expr> vars = newVars(loc, names);
          preduce(range);
          MZ.Expr rhs =
            exitContextWithSchema(
              new MZ.Expr.Term(
                    cons,
                    vars
                  ).set(loc),
              loc
            );
          addProperty(
            new MZ.Property.Subset(
                  ereduce(matrix),
                  rhs
                ).set(loc)
          );
        } else {
          // Pizza's generated default of an exhaustive switch (all four kinds are above).
          throw FatalError.unexpectedCase("quantor kind");
        }
          /*
        default:
          cantHandleError(loc);
          */
      }

      default -> {
        cantHandleError(loc);
      }
    }

  }

  /** Reduce and lift a property to a boolean set expression. */
  private MZ.Expr liftProp(Predicate pred) {
    enterContext();
    preduce(pred);
    return exitContextWithSchema(unitTerm, ReducerUtil.getLocatorAn(pred));
  }

  // ========================================================================
  // Reducing items

  /** Reduce the given ZIRP item to MZ declarations. */
  public void reduce(Item item) {
    Locator locator = ReducerUtil.getLocatorAn(item);

    if (context.parent != null)
      throw FatalError.assertionFailed("context");

    switch (item) {
      case Item.AxiomaticDef axdef when axdef.def instanceof Expr.FreeType freeType -> {
        NameDecl[] formalDecls = axdef.formals;
        NameDecl name = freeType.name;
        Expr.Branch[] branches = freeType.branches;

        Seq<Name> names = new BufferSeq<Name>().append(name.name);
        for (int i = 0; i < branches.length; i++) {
          switch (branches[i]) {
            case Expr.Branch.Constant c -> {
              names = names.append(c.name.name);
            }
            case Expr.Branch.Function f -> {
              names = names.append(f.name.name);
            }
            // Pizza's generated default of an exhaustive switch.
            default -> throw FatalError.unexpectedCase("free type branch");
          }
        }
        Seq<Name> formals = getFormals(formalDecls);
        setGenericScope(locator, formals, names);

        MZ.Expr generator = null;
        for (int i = 0; i < branches.length; i++) {
          // constructor == {((x_1,...,x_n),#constructor(x1,...,x_n)) |
          //                                              (x_1,...,x_n) : dom}
          Locator bloc = ReducerUtil.getLocatorAn(branches[i]);
          switch (branches[i]) {
            case Expr.Branch.Constant c -> {
              Name constName = c.name.name;
              MZ.Expr def = new MZ.Expr.Term(constName, new ArraySeq<MZ.Expr>()).set(bloc);
              MZ.Expr gen = new MZ.Expr.Singleton(def).set(bloc);
              if (generator == null) {
                generator = gen;
              } else {
                generator = new MZ.Expr.Union(generator, gen)
                                  .set(generator.locator.combine(bloc));
              }
              addDecls(
                bloc,
                false,
                formals,
                new ArraySeq<Name>(constName),
                pat ->
                  new MZ.Property.Equal(
                        pat,
                        def
                      ).set(bloc)
              );
            }
            case Expr.Branch.Function f -> {
              Name constName = f.name.name;
              Expr type = f.type;
              addDecls(
                bloc,
                false,
                formals,
                new ArraySeq<Name>(constName),
                pat -> {
                  enterContext();
                  MZ.Expr def =
                    strictifyOne(
                      ereduce(type),
                      dom -> {
                        enterContext();
                        Seq<MZ.Expr> vars =
                          newAnonVars(
                            bloc, "%a",
                            getConsArity(ReducerUtil.getTypeAn(type))
                               );
                        MZ.Expr x = makeTuple(vars).set(bloc);
                        addProperty(
                          new MZ.Property.Member(x, dom).set(bloc)
                        );
                        return
                          exitContextWithMu(
                            exitContextWithSchema(
                              makePair(x,
                                       new MZ.Expr.Term(constName, vars)
                                             .set(bloc)
                                      ).set(bloc),
                              bloc
                            ),
                            bloc
                          );
                      }
                    );
                  return new MZ.Property.Equal(
                               pat,
                               def
                             ).set(bloc);
                }
              );
              MZ.Expr gen = new MZ.Expr.Apply(
                                  makeUnivInst(bloc, ZNames.ran),
                                  new MZ.Expr.Variable(constName).set(bloc)
                                ).set(bloc);
              if (generator == null) {
                generator = gen;
              } else {
                generator = new MZ.Expr.Union(generator, gen)
                                  .set(generator.locator.combine(bloc));
              }

            }
            // Pizza's generated default of an exhaustive switch.
            default -> throw FatalError.unexpectedCase("free type branch");
          }
        }
        if (generator == null)
          generator = new MZ.Expr.Empty().set(locator);
        final MZ.Expr fgenerator = generator;
        addDecls(
          locator,
          false,
          formals,
          new ArraySeq<Name>(name.name),
          pat ->
            new MZ.Property.Equal(
                  pat,
                  fgenerator // new MZ.Expr.Universal().set(locator)
                ).set(locator)
        );
        //      addBound(MZConstants.makeGivenTypeGenName(name.name),
                //       generator);
        addBound(MZConstants.makeGivenTypeInfoName(name.name),
                 new MZ.Expr.Term(
                       ZNames.GivenTypeInfo,
                       new ArraySeq<MZ.Expr>(
                             new MZ.Expr.Denotation(
                                   makeFreeTypeInfo(branches)
                                 ).set(locator)
                           )
                     ).set(locator)
                );
      }

      case Item.AxiomaticDef axdef when axdef.def instanceof Expr.GivenType givenType -> {
        NameDecl[] formals = axdef.formals;
        NameDecl name = givenType.name;
        if (context.isGiven(name.name)) {
          setGenericScope(locator, getFormals(formals),
                          new ArraySeq<Name>(name.name));
          addDecls(
            locator,
            false,
            getFormals(formals),
            new ArraySeq<Name>(name.name),
            pat ->
              new MZ.Property.Equal(
                    pat,
                    new MZ.Expr.Universal().set(locator)
                  ).set(locator)
          );
                //addBound(MZConstants.makeGivenTypeGenName(name.name),
                //         new MZ.Expr.Universal().set(locator));
          addBound(MZConstants.makeGivenTypeInfoName(name.name),
                   new MZ.Expr.Term(
                         ZNames.GivenTypeInfo,
                         new ArraySeq<MZ.Expr>(
                               new MZ.Expr.Denotation(
                                     "1"
                                   ).set(locator)
                             )
                       ).set(locator)
                  );
        }
      }

      case Item.AxiomaticDef axdef -> {
        Expr def = axdef.def;
        Seq<Name> formals = getFormals(axdef.formals);
        Seq<Name> names = getSchemaSign(def).sort(Name::lexCompare);
        setGenericScope(locator, formals, names);
        addDecls(
          locator,
          true,
          formals,
          names,
          pat ->
            new MZ.Property.Member(
                  pat,
                  ereduce(def)
                ).set(locator)
        );
      }

      case Item.FixityDef x -> {
        // no semantic meaning
      }
      case Item.EmbeddedText x -> {
        // no semantic meaning
      }

      default -> {
        diag = diag.add(locator,
                        Diag.Category.Warning,
                        "cannot handle this kind of paragraph currently");
        throw FatalError.assertionFailed("cannot handle: " + item);
      }
    }
  }

  private static Seq<Name> getFormals(NameDecl[] formals) {
    return
      new ArraySeq<Name>(formals.length,
                         i -> formals[i].name);
  }

  private void addDecls(Locator locator,
                        boolean forceBinding,
                        Seq<Name> formals,
                        Seq<Name> names,
                        Function<MZ.Expr, MZ.Property> def) {
    Name cons = null;
    if (forceBinding || names.size() != 1)
      cons = getBindingCons(names);
    if (formals.size() == 0) {
      Seq<MZ.Expr> vars =
        names.<MZ.Expr>map(n -> new MZ.Expr.Variable(n).set(locator));
      MZ.Expr pat = cons != null ? new MZ.Expr.Term(cons, vars).set(locator)
                                 : vars.head();
      addProperty(
        def.apply(pat)
      );
    } else {
      // code for arbitrary instance
      enterContext();
      // FIXME: possibility for name clash?
      Seq<MZ.Expr> fvars = newVars(locator, formals);
      Seq<MZ.Expr> vars = newVars(locator, names);
      MZ.Expr pat = cons != null ? new MZ.Expr.Term(cons, vars).set(locator)
                                 : vars.head();
      addProperty(
        def.apply(pat)
      );
      MZ.Expr schema =
        exitContextWithSchema(
          makePair(makeTuple(fvars).set(locator), pat).set(locator),
          locator
        );
      if (vars.size() != 1) {
        MZ.Expr gvar = newAnonVar(locator, "%gen");
        addProperty(
          new MZ.Property.Equal(
                gvar,
                schema
              ).set(locator)
        );
        schema = gvar;
      }
      // was: vars.apply(fun (MZ.Expr var)->void { ... })
      for (Enum<MZ.Expr> en = vars.enumerate(); en.more(); en.advance()) {
        MZ.Expr var = en.current();
        // var := {(fpat,var)\ovars| (fpat,pat) \in schema}
        enterContext();
        Seq<MZ.Expr> fvars1 = newVars(locator, formals);
        Seq<MZ.Expr> vars1 = newVars(locator, names);
        // PORT NOTE: 1998 quirk, preserved: the pattern is built from `vars`, not `vars1`.
        // Unobservable -- `newVars` makes `Variable(n)` with the same names and locator, so
        // both terms are equal and render identically.
        MZ.Expr pat1 =
          cons != null ? new MZ.Expr.Term(cons, vars).set(locator)
                       : vars.head();
        MZ.Expr fpat1 = makeTuple(fvars1).set(locator);
        addProperty(
          new MZ.Property.Member(
                makePair(fpat1, pat1).set(locator),
                schema
              ).set(locator)
        );
        addProperty(
          new MZ.Property.Equal(
                var,
                exitContextWithSchema(
                  makePair(fpat1, var).set(locator),
                  locator
                )
              ).set(locator)
        );
      }
      // code for universal instance
      Set<Name> oldGivens = givens;
      givens = givens.includeAll(formals);
      Seq<MZ.Expr> uivars =
        names.<MZ.Expr>map(n -> makeUnivInst(locator, n));
      pat = cons != null ? new MZ.Expr.Term(cons, uivars).set(locator)
                         : uivars.head();
      addProperty(
        def.apply(pat)
      );
      givens = oldGivens;
    }
  }


  private static int getConsArity(Expr type) {
    switch (type) {
      case Expr.Unary u
          when u.kind == Expr.UnaryKind.Power
               && u.oper instanceof Expr.Product p -> {
        return p.elements.length;
      }
      default -> {
        return 1;
      }
    }
  }

  // ========================================================================
  // Generating type information

  private String makeTypeInfo(Expr type) {
    switch (type) {
      case Expr.Variable v -> {
        Name name = v.name.name;
        Expr[] acts = v.name.actuals;
        int inx = currentFormals.indexOf(name);
        if (inx >= 0) {
          return "%%" + (inx + 1);
        } else {
          String res = name.toString();
          if (acts.length > 0) {
            res += "[";
            for (int i = 0; i < acts.length; i++) {
              if (i > 0) res += ",";
              res += makeTypeInfo(acts[i]);
            }
            res += "]";
          }
          return res;
        }
      }
      case Expr.Product p -> {
        Expr[] comps = p.elements;
        String res = "(";
        for (int i = 0; i < comps.length; i++) {
          if (i > 0) res += ",";
          res += makeTypeInfo(comps[i]);
        }
        return res + ")";
      }
      case Expr.Signature s -> {
        Name[] names = s.names;
        Expr[] types = s.types;
        String res = "[";
        for (int i = 0; i < names.length; i++) {
          if (i > 0) res += ";";
          res += names[i] + ":" + makeTypeInfo(types[i]);
        }
        return res + "]";
      }
      case Expr.Unary u when u.kind == Expr.UnaryKind.Power -> {
        return "!" + makeTypeInfo(u.oper);
      }
      default ->
        throw FatalError.assertionFailed(
                "expected a type at " +
                ReducerUtil.getLocatorAn(type)
              );
    }
  }

  private String makeFreeTypeInfo(Expr.Branch[] branches) {
    String res = "";
    for (int i = 0; i < branches.length; i++) {
      if (i > 0) res += "|";
      switch (branches[i]) {
        case Expr.Branch.Constant c -> {
          res += c.name.name.toString();
        }
        case Expr.Branch.Function f -> {
          res += f.name.name.toString() + "<"
                 + makeTypeInfo(getElemType(ReducerUtil.getTypeAn(f.type)))
                 + ">";
        }
        // Pizza's generated default of an exhaustive switch.
        default -> throw FatalError.unexpectedCase("free type branch");
      }
    }
    return res;
  }

  private MZ.Expr makeTypeInfoDeno(Locator loc, Expr type) {
    String info = makeTypeInfo(type);
    if (info.indexOf("%%") >= 0) {
      diag = diag.add(loc,
                      Diag.Category.Error,
                      "cannot be used with generic types");
    }
    return new MZ.Expr.Denotation(info).set(loc);
  }

  private static Expr getElemType(Expr type) {
    switch (type) {
      case Expr.Unary u when u.kind == Expr.UnaryKind.Power -> {
        return u.oper;
      }
      default ->
        throw FatalError.assertionFailed(
                "expected powerset type at " + ReducerUtil.getLocatorAn(type)
              );
    }
  }



}
