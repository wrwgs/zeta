package zeta.content.zirp;

import java.util.function.Function;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashMap;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.format.Format;
import zeta.util.Annotation;
import zeta.util.FatalError;
import zeta.util.Name;
import zeta.util.Slot;

/** Type annotation slot and auxilary methods for types.
  *
  * This slot is provided at all <code>Expr</code>
  * terms if the syntax is context-checked.
  *
  * A type is represented as an expression of a restricted form, which
  * matches one of the following patterns:
  * <pre>
  *      Expr.Variable(NameAppl(Name, {Type1,...,TypeN}))
  *      Expr.Product({Type1,...,TypeN})
  *      Expr.Signature(_,{Type1,...,TypeN})
  *      Expr.Unary(Expr.UnaryKind.Power,Type)
  * </pre>
  * where <code>TypeI</code> is a type expression again, and <code>
  * Name </code> refers to a given type.<p>
  *
  * Furthermore, types may contain unification variables
  * (<code>Expr.UniVar</code>), yet these do not appear
  * in annotations of type-checked expressions.<p>
  *
  * In contrast to normal expressions, the expressions used for
  * type annotations do not have annotations again.<p>
  *
  * @version $Id: Type.pizza,v 1.5 2000/05/08 07:07:43 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * The Z type unifier. Two Pizza pattern forms here have no Java equivalent and are the whole
  * of the translation work:
  *
  * <ul>
  * <li><b>Nested deconstruction.</b> `case Variable(NameAppl(Name n, Expr[] a)):` matched an
  *     `Expr.Variable` *and* reached through its `NameAppl` in one pattern. Java 21 has record
  *     patterns, but these ADT cases are not records -- they have public mutable fields and
  *     must keep the compiled shape -- so the port matches the outer type and reads the fields.
  * <li><b>Matching against a constant case.</b> `case Unary(Expr.UnaryKind.Power, Expr t):`
  *     matched only when the `kind` field is the `Power` case. Pizza compiled that as a tag
  *     comparison; here it is `u.kind == Expr.UnaryKind.Power`, which is equivalent because
  *     `Power` is declared without parentheses and is therefore a singleton constant (9.6).
  *     The four `UnaryKind` cases that carry data -- `Hide`, `Renaming`, `Decorate`,
  *     `SelectiveDelta` -- are classes instead, and are not compared this way anywhere here.
  * </ul>
  *
  * `unify`'s two leading `for(;;) switch` loops are Pizza's idiom for "follow the binding chain
  * of a unification variable"; they become `while` loops with an `instanceof` guard. The rest
  * is structurally unchanged, including the asymmetric `anyType` propagation and the
  * destructive `l.oper = r` in `assign`, which is what makes unification stateful.
  */
public class Type {

  /** The slot object. */
  public static final Slot slot = Slot.register(Type.class.getName(),
                                                Expr.class);

  /** Set value of type annotation slot.
    * @param an    the annotation
    * @param type  the expression representing the type.
    *   This should be a valid type expression as explained
    *   above (no automatic consistency checks are currently made).
    */
  public static void put(Annotation an, Expr type) {
    slot.put(an, type);
  }

  /** Get value of type annotation slot.
    * @param an    the annotation
    * @return the type, or null, if no one is provided.
    */
  public static Expr get(Annotation an) {
    return (Expr) slot.get(an);
  }

  /** Test whether type annotation slot is provided.
    * @param an   the annotation
    * @return true if the type annotation slot is provided
    */
  public static boolean isProvided(Annotation an) {
    return slot.isProvided(an);
  }

  /** Make a given type expression. Returns an expression of
    * form <code>Expr.Variable(NameAppl(Name,{})</code>.  */
  public static Expr makeGiven(Name name) {
    return Expr.Variable(new NameAppl(name, new Expr[0]));
  }

  /** Make a generic given type expression. Returns an expression of
    * form <code>Expr.Variable(NameAppl(Name,Generics)</code>.  */
  public static Expr makeGenGiven(Name name, Expr[] actuals) {
    return Expr.Variable(new NameAppl(name, actuals));
  }

  /** Make a powerset type expression. Returns an expression of
    * form <code>Expr.Unary(Expr.UnaryKind.Power,Type)</code>. */
  public static Expr makePower(Expr type) {
    return Expr.Unary(Expr.UnaryKind.Power, type);
  }

  /** Make a product type expression. Returns an
    * expression of form <code>Expr.Product({Type1,...,TypeN})</code>
    */
  public static Expr makeProduct(Expr[] components) {
    if (components.length == 1)
      return components[0];
    else
      return Expr.Product(components);
  }

  /** Make a signature type expression. Returns an expression
    * of form
    * <code>Expr.Signature({Name1,...,NameN},{Type1,...,TypeN})</code> */
  public static Expr makeSignature(Name[] names, Expr[] types) {
    return Expr.Signature(names, types);
  }

  /** Make a free type variable expression. Returns an expression
    * of form <code>Expr.UniVar(Name, null)</code>. */
  public static Expr makeVar(Name name) {
    return Expr.UniVar(name, null);
  }

  /**
   * Substitute in type the given names by the given types.
   */
  public static Expr substitute(Expr type, Name[] names, Expr[] types) {
    Expr newType =
      new Substitutor(new HashMap<Name, Expr>().defineAll(new ArraySeq<Name>(names),
                                                          new ArraySeq<Expr>(types)))
        .visit(type);
    return newType != null ? newType : type;
  }

  /**
   * Term visitor class for substitution.
   */
  private static class Substitutor extends TermVisitor {
    private final Map<Name, Expr> subs;

    Substitutor(Map<Name, Expr> subs) {
      this.subs = subs;
    }

    public Expr visit(Expr.Variable expr) {
      if (subs.defines(expr.name.name))
        return subs.get(expr.name.name);
      else
        return super.visit(expr);
    }
  }

  /**
   * Pretty-print the given type expression
   */
  public static Format toPrettyFormat(Expr type) {
    return showP(type).fst;
  }

  private static Pair<Format, Integer> showP(Expr expr) {
    switch (expr) {
      case Expr.Variable v: {
        NameAppl na = v.name;
        Expr[] acts = na.actuals;
        if (acts.length == 0)
          return Pair.Pair(Format.literal(na.name.getRepr()), 1000);
        else
          return Pair.Pair(Format.list(Format.literal(na.name.getRepr() + "["),
                                       Format.literal(","),
                                       Format.literal("]"),
                                       (Function<Expr, Format>)
                                         e -> showP(e).fst,
                                       new ArraySeq<Expr>(acts)
                                      ),
                           1000);
      }
      case Expr.Product p:
        return Pair.Pair(Format.list(Format.empty,
                                     Format.literal(" ** "),
                                     Format.empty,
                                     (Function<Expr, Format>)
                                       e -> Format.parenth(showP(e), 101).fst,
                                     new ArraySeq<Expr>(p.elements)
                                    ),
                         100);
      case Expr.Signature s: {
        Seq<Pair<Name, Expr>> zipped = new BufferSeq<Pair<Name, Expr>>(); // FIXME: Seq.zip!!!
        for (int i = 0; i < s.names.length; i++) {
          zipped = zipped.append(Pair.Pair(s.names[i], s.types[i]));
        }
        return Pair.Pair(Format.list(Format.literal("["),
                                     Format.literal("; "),
                                     Format.literal("]"),
                                     (Function<Pair<Name, Expr>, Format>) p -> Format.line(
                                       Format.literal(p.fst.getRepr() + ": "),
                                       Format.indent(showP(p.snd).fst)
                                     ),
                                     zipped
                                    ),
                         1000);
      }
      case Expr.Unary u:
        if (u.kind == Expr.UnaryKind.Power) {
          return Pair.Pair(Format.append(Format.literal("P "),
                                         Format.indent(
                                           Format.parenth(showP(u.oper), 201).fst)),
                           200);
        }
        throw FatalError.unexpectedCase("type expression");
      case Expr.UniVar uv:
        if (uv.oper != null)
          return showP(uv.oper);
        else
          return Pair.Pair(Format.buildFormat(uv.name), 1000);
      default:
        throw FatalError.unexpectedCase("type expression");
    }
  }

  /**
   * A constant for representing "any" type (error type) in types.
   */
  // The factory rather than `new Expr.Variable(...)`, which the Pizza source wrote: while
  // `Expr` is still binary javac cannot see that constructor at all, because the static
  // factory `Expr.Variable(NameAppl)` and the nested type `Expr.Variable` share a name inside
  // `Expr`'s member namespace.  The cast is needed because the factory is declared to return
  // the base type.  See docs/port-recipe.md.
  public static final Expr.Variable anyType =
    (Expr.Variable) Expr.Variable(new NameAppl(new Name("<any>"), new Expr[0]));

  /**
   * Unify the two types. Throws exception if unification fails.<p>
   *
   * Type variables (<code>Expr.UniVar</code>) will be bound by the
   * unification algorithm. The special type <code>anyType</code>,
   * if apppearing in some of the type terms, will be treated.
   */
  public static void unify(Expr l, Expr r) throws UnificationFailure {

    // follow the binding chain of a bound unification variable
    while (l instanceof Expr.UniVar uv && uv.oper != null) {
      l = uv.oper;
    }
    while (r instanceof Expr.UniVar uv && uv.oper != null) {
      r = uv.oper;
    }

    if (l == r) return;

    if (l instanceof Expr.UniVar) {
      assign((Expr.UniVar) l, r);
      return;
    }
    if (r instanceof Expr.UniVar) {
      assign((Expr.UniVar) r, l);
      return;
    }

    // propagate anyType
    if (r == anyType) {
      Expr temp = r;
      r = l;
      l = temp;
    }
    if (l == anyType) {
      switch (r) {
        case Expr.Variable v: {
          Expr[] a = v.name.actuals;
          for (int i = 0; i < a.length; i++)
            unify(anyType, a[i]);
          break;
        }
        case Expr.Unary u:
          if (u.kind == Expr.UnaryKind.Power) {
            unify(anyType, u.oper);
            break;
          }
          throw FatalError.unexpectedCase("type expression");
        case Expr.UniVar uv:
          if (uv.oper != null)
            unify(anyType, uv.oper);
          break;
        case Expr.Product p:
          for (int i = 0; i < p.elements.length; i++)
            unify(anyType, p.elements[i]);
          break;
        case Expr.Signature s:
          for (int i = 0; i < s.types.length; i++)
            unify(anyType, s.types[i]);
          break;
        default:
          throw FatalError.unexpectedCase("type expression");
      }
      return;
    }

    // do hard unification
    if (l instanceof Expr.Variable lv && r instanceof Expr.Variable rv) {
      if (lv.name.name.equals(rv.name.name)) {
        unifyArray(l, r, lv.name.actuals, rv.name.actuals);
        return;
      }
    } else if (l instanceof Expr.Unary lu && lu.kind == Expr.UnaryKind.Power
               && r instanceof Expr.Unary ru && ru.kind == Expr.UnaryKind.Power) {
      unify(lu.oper, ru.oper);
      return;
    } else if (l instanceof Expr.Product lp && r instanceof Expr.Product rp) {
      unifyArray(l, r, lp.elements, rp.elements);
      return;
    } else if (l instanceof Expr.Signature ls && r instanceof Expr.Signature rs) {
      if (ls.names.length != rs.names.length)
        throw new UnificationFailure(l, r);
      for (int i = 0; i < ls.names.length; i++) {
        if (!ls.names[i].equals(rs.names[i]))
          throw new UnificationFailure(l, r);
      }
      unifyArray(l, r, ls.types, rs.types);
      return;
    }
    throw new UnificationFailure(l, r);
  }

  /** Unify two arrays of type expressions. */
  private static void unifyArray(Expr l, Expr r, Expr[] ls, Expr[] rs)
      throws UnificationFailure {
    int n1 = ls.length;
    int n2 = rs.length;
    if (n1 != n2) {
      throw new UnificationFailure(l, r);
    } else {
      for (int i = 0; i < n1; i++) {
        unify(ls[i], rs[i]);
      }
    }
  }

  /**
   * Perform type assignment for unification.
   */
  private static void assign(Expr.UniVar l, Expr r) throws UnificationFailure {
    if (occures(l, r)) throw new UnificationFailure(l, r);
    l.oper = r;
  }

  /**
   * Perform occurence test for unification.
   */
  private static boolean occures(Expr.UniVar v, Expr t) {
    if (t == v) return true;
    else {
      switch (t) {
        case Expr.Variable x:
          return occures(v, x.name.actuals);
        case Expr.Product x:
          return occures(v, x.elements);
        case Expr.Signature x:
          return occures(v, x.types);
        case Expr.Unary x:
          if (x.kind == Expr.UnaryKind.Power)
            return occures(v, x.oper);
          throw FatalError.unexpectedCase("type expression");
        case Expr.UniVar x:
          if (x.oper != null)
            return occures(v, x.oper);
          else
            return false;
        default:
          throw FatalError.unexpectedCase("type expression");
      }
    }
  }

  /**
   * Perform occurence test for unification.
   */
  private static boolean occures(Expr.UniVar v, Expr[] ts) {
    for (int i = 0; i < ts.length; i++) {
      if (occures(v, ts[i])) return true;
    }
    return false;
  }

  /**
   * Exception class representing a unification failure,
   * holding the critical pair where the unification failed.
   */
  public static class UnificationFailure extends Exception {
    public Expr type1;
    public Expr type2;

    public UnificationFailure(Expr type1, Expr type2) {
      this.type1 = type1;
      this.type2 = type2;
    }

    /** Pizza's generated case factory. */
    public static UnificationFailure UnificationFailure(Expr type1, Expr type2) {
      return new UnificationFailure(type1, type2);
    }
  }
}
