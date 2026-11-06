package zeta.content.nmz;

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
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Locator;
import zeta.util.FatalError;
import zeta.util.Name;
import zeta.util.Term;

/** The new version of the mZ abstract syntax.
  *
  * @version $Id: MZ.pizza,v 1.8 2000/07/26 16:00:37 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * MicroZ: what the reducer produces and the animator consumes, and the content type behind
  * the `nmz` artifacts in the oracle. `UnitDef.toFormat` below is literally what those files
  * contain, down to the `UNIT`/`PARENTS`/`GLOBALS`/`LOCALS`/`PROPERTIES` headings the
  * canonicaliser sorts.
  *
  * The interesting translation problem here is **mutable capture**. Pizza let a `fun` literal
  * assign to a captured local:
  *
  * <pre>
  *   static Set&lt;Name&gt; varsSeq(Set&lt;Name&gt; hidden, Set&lt;Name&gt; vs, Seq&lt;Expr&gt; es){
  *     es.apply(fun (Expr e)-&gt;void { vs = e.vars(hidden,vs); });
  *     return vs;
  *   }
  * </pre>
  *
  * Java requires a captured local to be effectively final. Where the closure was only a way
  * of writing a fold -- `varsSeq`, `calcFree`, the renaming loop in `subs`, the `Term` arm of
  * `fmt` -- the port writes the loop, which is both legal and clearer, and provably the same
  * because `apply` visits in enumeration order. Where the closure's *result* matters because
  * `map` preserves the sequence implementation (`subsSeq`), the lambda is kept and only the
  * flag moves into a one-element array; rewriting that as a loop would change which `Seq`
  * class comes back.
  *
  * `locator` is declared before the discriminant in both `Expr` and `Property`, matching the
  * reference: field order is observable through `Term.toFormat` (zesurrect-plan.md 9.5).
  */
public class MZ {

  /** Superclass of any MZ term. */
  public abstract static class MZTerm extends Term {
  }

  public static class Expr extends MZTerm {

    /** Locator. */
    public Locator locator = null;

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$content$nmz$MZ$Expr$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_DENOTATION = 1;
    private static final int TAG_CALL = 2;
    private static final int TAG_VARIABLE = 3;
    private static final int TAG_TERM = 4;
    private static final int TAG_APPLY = 5;
    private static final int TAG_SINGLETON = 6;
    private static final int TAG_HOM = 7;
    private static final int TAG_EMPTY = 8;
    private static final int TAG_UNIVERSAL = 9;
    private static final int TAG_INTERSECTION = 10;
    private static final int TAG_UNION = 11;
    private static final int TAG_COMPLEMENT = 12;
    private static final int TAG_CONDITIONAL = 13;
    private static final int TAG_TRANSLATE = 14;
    private static final int TAG_LET = 15;
    private static final int TAG_SCHEMA = 16;

    Expr(int tag) {
      this.zeta$content$nmz$MZ$Expr$$tag = tag;
    }

    /** Denotation. */
    public static class Denotation extends Expr {
      public String value;

      public Denotation(String value) {
        super(TAG_DENOTATION);
        this.value = value;
      }
    }

    /** Primitive function call. */
    public static class Call extends Expr {
      public Name name;
      public Seq<Expr> args;

      public Call(Name name, Seq<Expr> args) {
        super(TAG_CALL);
        this.name = name;
        this.args = args;
      }
    }

    /** Reference to a variable. */
    public static class Variable extends Expr {
      public Name name;

      public Variable(Name name) {
        super(TAG_VARIABLE);
        this.name = name;
      }
    }

    /** Constructor application. */
    public static class Term extends Expr {
      public Name name;
      public Seq<Expr> args;

      public Term(Name name, Seq<Expr> args) {
        super(TAG_TERM);
        this.name = name;
        this.args = args;
      }
    }

    /** Function application. */
    public static class Apply extends Expr {
      public Expr func;
      public Expr arg;

      public Apply(Expr func, Expr arg) {
        super(TAG_APPLY);
        this.func = func;
        this.arg = arg;
      }
    }

    /** Singleton set. */
    public static class Singleton extends Expr {
      public Expr arg;

      public Singleton(Expr arg) {
        super(TAG_SINGLETON);
        this.arg = arg;
      }
    }

    /** Homomorphisms. */
    public static class Hom extends Expr {
      public Name name;
      public Expr arg;

      public Hom(Name name, Expr arg) {
        super(TAG_HOM);
        this.name = name;
        this.arg = arg;
      }
    }

    /** The empty set. */
    public static class Empty extends Expr {
      public Empty() {
        super(TAG_EMPTY);
      }
    }

    /** The universal set. */
    public static class Universal extends Expr {
      public Universal() {
        super(TAG_UNIVERSAL);
      }
    }

    /** Set intersection. */
    public static class Intersection extends Expr {
      public Expr left;
      public Expr right;

      public Intersection(Expr left, Expr right) {
        super(TAG_INTERSECTION);
        this.left = left;
        this.right = right;
      }
    }

    /** Set union. */
    public static class Union extends Expr {
      public Expr left;
      public Expr right;

      public Union(Expr left, Expr right) {
        super(TAG_UNION);
        this.left = left;
        this.right = right;
      }
    }

    /** Set complement. */
    public static class Complement extends Expr {
      public Expr arg;

      public Complement(Expr arg) {
        super(TAG_COMPLEMENT);
        this.arg = arg;
      }
    }

    /** Conditional. */
    public static class Conditional extends Expr {
      public Expr cond;
      public Expr left;
      public Expr right;

      public Conditional(Expr cond, Expr left, Expr right) {
        super(TAG_CONDITIONAL);
        this.cond = cond;
        this.left = left;
        this.right = right;
      }
    }

    /** Set translation. */
    public static class Translate extends Expr {
      public Expr arg;
      public Expr source;
      public Expr target;

      public Translate(Expr arg, Expr source, Expr target) {
        super(TAG_TRANSLATE);
        this.arg = arg;
        this.source = source;
        this.target = target;
      }
    }

    /** Let. */
    public static class Let extends Expr {
      public Name name;
      public Expr def;
      public Expr scope;

      public Let(Name name, Expr def, Expr scope) {
        super(TAG_LET);
        this.name = name;
        this.def = def;
        this.scope = scope;
      }
    }

    /** Schema. */
    public static class Schema extends Expr {
      public Expr binder;
      public Set<Name> freeVars;
      public Seq<Property> props;

      public Schema(Expr binder, Set<Name> freeVars, Seq<Property> props) {
        super(TAG_SCHEMA);
        this.binder = binder;
        this.freeVars = freeVars;
        this.props = props;
      }
    }

    /* ------------------------------------------ Pizza's generated case factories. */

    public static Expr Denotation(String value) { return new Denotation(value); }

    public static Expr Call(Name name, Seq<Expr> args) { return new Call(name, args); }

    public static Expr Variable(Name name) { return new Variable(name); }

    public static Expr Term(Name name, Seq<Expr> args) { return new Term(name, args); }

    public static Expr Apply(Expr func, Expr arg) { return new Apply(func, arg); }

    public static Expr Singleton(Expr arg) { return new Singleton(arg); }

    public static Expr Hom(Name name, Expr arg) { return new Hom(name, arg); }

    public static Expr Empty() { return new Empty(); }

    public static Expr Universal() { return new Universal(); }

    public static Expr Intersection(Expr left, Expr right) {
      return new Intersection(left, right);
    }

    public static Expr Union(Expr left, Expr right) { return new Union(left, right); }

    public static Expr Complement(Expr arg) { return new Complement(arg); }

    public static Expr Conditional(Expr cond, Expr left, Expr right) {
      return new Conditional(cond, left, right);
    }

    public static Expr Translate(Expr arg, Expr source, Expr target) {
      return new Translate(arg, source, target);
    }

    public static Expr Let(Name name, Expr def, Expr scope) {
      return new Let(name, def, scope);
    }

    public static Expr Schema(Expr binder, Set<Name> freeVars, Seq<Property> props) {
      return new Schema(binder, freeVars, props);
    }

    /** Set locator field of expression. Returns this expression. */
    public final Expr set(Locator locator) {
      this.locator = locator;
      return this;
    }

    /** Set locator field of expression from other expression. */
    public final Expr setFrom(Expr other) {
      this.locator = other.locator;
      return this;
    }

    /** Return the free variables in expression. */
    public Set<Name> freeVars() {
      return vars(new HashSet<Name>(), new HashSet<Name>());
    }

    /** Return the free variables in expression excluding the given vars. */
    public Set<Name> freeVars(Set<Name> exclude) {
      return vars(exclude, new HashSet<Name>());
    }

    final Set<Name> vars(Set<Name> hidden, Set<Name> vs) {
      switch (this) {
        case Call e:
          return varsSeq(hidden, vs, e.args);
        case Variable e:
          if (!hidden.contains(e.name)) {
            return vs.include(e.name);
          } else
            return vs;
        case Term e:
          return varsSeq(hidden, vs, e.args);
        case Singleton e:
          return e.arg.vars(hidden, vs);
        case Hom e:
          return e.arg.vars(hidden, vs);
        case Complement e:
          return e.arg.vars(hidden, vs);
        case Intersection e:
          return e.right.vars(hidden, e.left.vars(hidden, vs));
        case Apply e:
          return e.arg.vars(hidden, e.func.vars(hidden, vs));
        case Union e:
          return e.right.vars(hidden, e.left.vars(hidden, vs));
        case Conditional e:
          return e.cond.vars(hidden, e.left.vars(hidden, e.right.vars(hidden, vs)));
        case Translate e:
          return e.arg.vars(hidden, vs);
        case Let e:
          return e.scope.vars(hidden.include(e.name), e.def.vars(hidden, vs));
        case Schema e:
          return Property.varsSeq(
                   hidden.includeAll(e.binder.freeVars()).includeAll(e.freeVars),
                   vs,
                   e.props);
        default:
          return vs;
      }
    }

    // was: es.apply(fun (Expr e)->void { vs = e.vars(hidden,vs); })
    static Set<Name> varsSeq(Set<Name> hidden, Set<Name> vs, Seq<Expr> es) {
      for (Enum<Expr> e = es.enumerate(); e.more(); e.advance()) {
        vs = e.current().vars(hidden, vs);
      }
      return vs;
    }

    /** Perform a substitution in expression. */
    public final Expr substitute(Map<Name, Expr> sb) {
      Expr expr = subs(sb, new HashSet<Name>(), calcFree(sb));
      return expr == null ? this : expr;
    }

    // was: sb.apply(fun (Pair<Name,Expr> p)->void { free = free.includeAll(...); })
    static Set<Name> calcFree(Map<Name, Expr> sb) {
      Set<Name> free = new HashSet<Name>();
      for (Enum<Pair<Name, Expr>> e = sb.enumerate(); e.more(); e.advance()) {
        free = free.includeAll(e.current().snd.freeVars());
      }
      return free.excludeAll(sb.domain());
    }

    @SuppressWarnings("unchecked")
    final Expr subs(Map<Name, Expr> sb, Set<Name> hidden, Set<Name> free) {
      switch (this) {
        case Call e: {
          Seq<Expr> args = subsSeq(sb, hidden, free, e.args);
          return args == null ? null : new Call(e.name, args).setFrom(this);
        }
        case Variable e:
          if (!hidden.contains(e.name)) {
            try {
              Expr s = sb.get(e.name);
              if (s instanceof Variable v1 && e.name.equals(v1.name)) return null;
              Expr s1 = s.subs(sb, hidden, free); // FIXME: efficiency
              return s1 == null ? s : s1;
            } catch (NoSuchElementException ex) {
              return null;
            }
          } else
            return null;
        case Term e: {
          Seq<Expr> args = subsSeq(sb, hidden, free, e.args);
          return args == null ? null : new Term(e.name, args).setFrom(this);
        }
        case Singleton e: {
          Expr arg = e.arg.subs(sb, hidden, free);
          return arg == null ? null : new Singleton(arg).setFrom(this);
        }
        case Hom e: {
          Expr arg = e.arg.subs(sb, hidden, free);
          return arg == null ? null : new Hom(e.name, arg).setFrom(this);
        }
        case Complement e: {
          Expr oper = e.arg.subs(sb, hidden, free);
          return oper == null ? null : new Complement(oper).setFrom(this);
        }
        case Intersection e: {
          Expr nleft = e.left.subs(sb, hidden, free);
          Expr nright = e.right.subs(sb, hidden, free);
          if (nleft != null || nright != null) {
            return new Intersection(nleft != null ? nleft : e.left,
                                    nright != null ? nright : e.right)
                     .setFrom(this);
          } else
            return null;
        }
        case Union e: {
          Expr nleft = e.left.subs(sb, hidden, free);
          Expr nright = e.right.subs(sb, hidden, free);
          if (nleft != null || nright != null) {
            return new Union(nleft != null ? nleft : e.left,
                             nright != null ? nright : e.right)
                     .setFrom(this);
          } else
            return null;
        }
        case Apply e: {
          Expr nleft = e.func.subs(sb, hidden, free);
          Expr nright = e.arg.subs(sb, hidden, free);
          if (nleft != null || nright != null) {
            return new Apply(nleft != null ? nleft : e.func,
                             nright != null ? nright : e.arg)
                     .setFrom(this);
          } else
            return null;
        }
        case Conditional e: {
          Expr ncond = e.cond.subs(sb, hidden, free);
          Expr nthen = e.left.subs(sb, hidden, free);
          Expr nelse = e.right.subs(sb, hidden, free);
          if (ncond != null || nelse != null || nthen != null) {
            return new Conditional(ncond != null ? ncond : e.cond,
                                   nthen != null ? nthen : e.left,
                                   nelse != null ? nelse : e.right)
                     .setFrom(this);
          } else
            return null;
        }
        case Schema e: {
          Expr binder = e.binder;
          Set<Name> fvars = e.freeVars;
          Seq<Property> properties = e.props;
          Set<Name> svars = fvars.includeAll(binder.freeVars());
          Map<Name, Expr> ren = new HashMap<Name, Expr>();
          // was: svars.apply(fun (Name n)->void { ... ren = ren.define(...); })
          for (Enum<Name> en = svars.enumerate(); en.more(); en.advance()) {
            Name n = en.current();
            if (free.contains(n)) {
              // FIXME: better solution here
              // potential name clash problem: must perform beta-conversion
              Name nn = new Name("%R" + (++renameCounter));
              ren = ren.define(n, Expr.Variable(nn).set(locator));
            }
          }
          if (!ren.isEmpty()) {
            final Map<Name, Expr> fren = ren;
            binder = binder.substitute(fren);
            fvars = fvars.map(n -> {
              if (fren.defines(n)) {
                return ((Expr.Variable) fren.get(n)).name;
              } else
                return n;
            });
            properties = properties.map(pr -> pr.substitute(fren));
          }
          properties = Property.subsSeq(sb,
                                        hidden.includeAll(binder.freeVars())
                                              .includeAll(fvars),
                                        free,
                                        properties);
          if (properties != null)
            return new Schema(binder, fvars, properties).setFrom(this);
          else
            return null;
        }
        default:
          return null;
      }
    }

    static int renameCounter = 0;

    // The lambda is kept rather than turned into a loop: `map` returns a Seq of the same
    // implementation class as its receiver, and that choice is visible downstream.
    static Seq<Expr> subsSeq(Map<Name, Expr> sb, Set<Name> hidden,
                             Set<Name> free,
                             Seq<Expr> exprs) {
      boolean[] change = {false};
      exprs = exprs.map(e -> {
        Expr ne = e.subs(sb, hidden, free);
        if (ne != null) {
          change[0] = true;
          return ne;
        } else
          return e;
      });
      if (change[0])
        return ArraySeq.from(exprs);
      else
        return null;
    }

    /** Format expression. */
    public Format toFormat(FormatInfo info) {
      Pair<Format, Integer> p = fmt(this);
      return p.fst;
    }

    private static int minPrio = 0;
    private static int ifPrio = 20;
    private static int impliesPrio = 26;
    private static int orPrio = 30;
    private static int andPrio = 40;
    private static int applyPrio = 50;
    private static int prePrio = 60;
    private static int maxPrio = 100;

    private static Pair<Format, Integer> fmt(Expr expr) {
      switch (expr) {
        case Denotation e:
          return Pair.Pair(Format.literal(e.value), maxPrio);
        case Call e:
          return Pair.Pair(Format.block(
                             Format.literal("$" + e.name.toString()),
                             Format.list(Format.literal("("),
                                         Format.literal(","),
                                         Format.literal(")"),
                                         fmtSub(minPrio),
                                         e.args)),
                           maxPrio);
        case Variable e: {
          String s = e.name.toString();
          return Pair.Pair(Format.literal(s), maxPrio);
        }
        case Term e: {
          String repr = e.name.toString();
          Seq<Format> fmts = new BufferSeq<Format>();
          int i = repr.indexOf("_");
          while (i >= 0) {
            fmts = fmts.append(Format.literal(repr.substring(0, i)));
            repr = repr.substring(i + 1);
            i = repr.indexOf("_");
          }
          if (fmts.size() == e.args.size() && e.args.size() > 0) {
            Seq<Format> res = new BufferSeq<Format>();
            // was: fmts.apply(fun (Format f)->void { res = ...; args = args.tail(); })
            Seq<Expr> args = e.args;
            for (Enum<Format> en = fmts.enumerate(); en.more(); en.advance()) {
              res = res.append(Format.append(en.current(),
                                             fmtSub(minPrio).apply(args.head())));
              args = args.tail();
            }
            return Pair.Pair(Format.block(res.append(Format.literal(repr))),
                             maxPrio);
          } else {
            return Pair.Pair(Format.block(
                               Format.literal("#" + e.name.toString()),
                               Format.list(Format.literal("("),
                                           Format.literal(","),
                                           Format.literal(")"),
                                           fmtSub(minPrio),
                                           e.args)),
                             maxPrio);
          }
        }
        case Apply e:
          return Pair.Pair(Format.block(
                             fmtSub(applyPrio).apply(e.func),
                             Format.space(1),
                             Format.indent(
                               fmtSub(applyPrio + 1).apply(e.arg)
                             )
                           ),
                           applyPrio);
        case Singleton e:
          return Pair.Pair(Format.append(Format.literal("{"),
                                         fmtSub(minPrio).apply(e.arg),
                                         Format.literal("}")),
                           maxPrio);
        case Hom e:
          return Pair.Pair(Format.append(Format.literal("/" + e.name),
                                         Format.space(1),
                                         fmtSub(maxPrio).apply(e.arg)),
                           maxPrio);
        case Empty e:
          return Pair.Pair(Format.literal("EMPTY"), maxPrio);
        case Universal e:
          return Pair.Pair(Format.literal("UNIV"), maxPrio);
        case Complement e:
          return Pair.Pair(Format.block(Format.append(Format.literal("~"),
                                                      Format.space),
                                        Format.indent(
                                          fmtSub(prePrio + 1).apply(e.arg))),
                           prePrio);
        case Intersection e:
          return Pair.Pair(Format.block(
                             Format.append(fmtSub(andPrio).apply(e.left),
                                           Format.space,
                                           Format.literal("*"),
                                           Format.space),
                             (fmtSub(andPrio + 1).apply(e.right))),
                           andPrio);
        case Union e:
          return Pair.Pair(Format.block(
                             Format.append(fmtSub(orPrio).apply(e.left),
                                           Format.space,
                                           Format.literal("+"),
                                           Format.space),
                             (fmtSub(orPrio + 1).apply(e.right))),
                           orPrio);
        case Conditional e:
          return
            Pair.Pair(Format.line(
                        Format.append(Format.literal("IF "),
                                      e.cond.toFormat(new FormatInfo())),
                        Format.space,
                        Format.line(Format.append(Format.literal("THEN "),
                                                  fmtSub(minPrio).apply(e.left)),
                                    Format.space,
                                    Format.append(Format.literal("ELSE "),
                                                  fmtSub(minPrio).apply(e.right)))
                      ),
                      ifPrio);
        case Translate e:
          return Pair.Pair(Format.line(fmtSub(prePrio + 1).apply(e.arg),
                                       Format.indent(
                                         Format.block(
                                           Format.literal("["),
                                           fmtSub(minPrio).apply(e.source),
                                           Format.append(
                                             Format.literal(" -> "),
                                             fmtSub(minPrio).apply(e.target),
                                             Format.literal("]")
                                           )
                                         )
                                       )
                                      ),
                           prePrio);
        case Let e:
          return Pair.Pair(
                   Format.block(
                     Format.append(
                       Format.literal("LET "),
                       e.name.toFormat(new FormatInfo()),
                       Format.literal(" == "),
                       fmtSub(minPrio).apply(e.def)
                     ),
                     Format.indent(
                       Format.append(Format.literal(" IN "),
                                     fmtSub(minPrio).apply(e.scope))
                     )
                   ),
                   ifPrio
                 );
        case Schema e: {
          Format vfmt;
          if (!e.freeVars.isEmpty()) {
            vfmt = Format.list(
                     Format.literal("\\"),
                     Format.literal(","),
                     Format.empty,
                     (Function<Name, Format>) name -> name.toFormat(new FormatInfo()),
                     e.freeVars
                   );
          } else {
            vfmt = Format.empty;
          }
          return Pair.Pair(Format.block(
                             Format.append(
                               Format.literal("{"),
                               fmtSub(minPrio).apply(e.binder),
                               vfmt,
                               Format.literal(" | ")
                             ),
                             Format.indent(
                               Format.list(
                                 Format.empty,
                                 Format.literal("; "),
                                 Format.literal("}"),
                                 (Function<Property, Format>)
                                   prop -> prop.toFormat(new FormatInfo()),
                                 e.props
                               )
                             )
                           ),
                           maxPrio);
        }
        default:
          throw FatalError.unexpectedCase("mZ expression");
      }
    }

    private static Function<Expr, Format> fmtSub(int demandedPrio) {
      return expr -> Format.parenth(fmt(expr), demandedPrio).fst;
    }
  }

  public static class Property extends MZTerm {

    public Locator locator;

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$content$nmz$MZ$Property$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_IS_TRUE = 1;
    private static final int TAG_EQUAL = 2;
    private static final int TAG_MEMBER = 3;
    private static final int TAG_SUBSET = 4;
    private static final int TAG_CALL = 5;

    Property(int tag) {
      this.zeta$content$nmz$MZ$Property$$tag = tag;
    }

    /** Truth. */
    public static class IsTrue extends Property {
      public Expr expr;

      public IsTrue(Expr expr) {
        super(TAG_IS_TRUE);
        this.expr = expr;
      }
    }

    /** Equality. */
    public static class Equal extends Property {
      public Expr left;
      public Expr right;

      public Equal(Expr left, Expr right) {
        super(TAG_EQUAL);
        this.left = left;
        this.right = right;
      }
    }

    /** Membership. */
    public static class Member extends Property {
      public Expr left;
      public Expr right;

      public Member(Expr left, Expr right) {
        super(TAG_MEMBER);
        this.left = left;
        this.right = right;
      }
    }

    /** Subset. */
    public static class Subset extends Property {
      public Expr left;
      public Expr right;

      public Subset(Expr left, Expr right) {
        super(TAG_SUBSET);
        this.left = left;
        this.right = right;
      }
    }

    /** Primitive Predicate. */
    public static class Call extends Property {
      public Name name;
      public Seq<Expr> args;

      public Call(Name name, Seq<Expr> args) {
        super(TAG_CALL);
        this.name = name;
        this.args = args;
      }
    }

    /* ------------------------------------------ Pizza's generated case factories. */

    public static Property IsTrue(Expr expr) { return new IsTrue(expr); }

    public static Property Equal(Expr left, Expr right) { return new Equal(left, right); }

    public static Property Member(Expr left, Expr right) { return new Member(left, right); }

    public static Property Subset(Expr left, Expr right) { return new Subset(left, right); }

    public static Property Call(Name name, Seq<Expr> args) { return new Call(name, args); }

    /** Set locator field of property. Returns this property. */
    public final Property set(Locator locator) {
      this.locator = locator;
      return this;
    }

    /** Set locator field of property from other property. */
    public final Property setFrom(Property other) {
      this.locator = other.locator;
      return this;
    }

    /** Return the free variables in property. */
    public Set<Name> freeVars() {
      return vars(new HashSet<Name>(), new HashSet<Name>());
    }

    /** Return the free variables in property excluding the given vars. */
    public Set<Name> freeVars(Set<Name> exclude) {
      return vars(exclude, new HashSet<Name>());
    }

    Set<Name> vars(Set<Name> hidden, Set<Name> vs) {
      return switch (this) {
        case Call p -> Expr.varsSeq(hidden, vs, p.args);
        case IsTrue p -> p.expr.vars(hidden, vs);
        case Equal p -> p.right.vars(hidden, p.left.vars(hidden, vs));
        case Member p -> p.right.vars(hidden, p.left.vars(hidden, vs));
        case Subset p -> p.right.vars(hidden, p.left.vars(hidden, vs));
        default -> throw FatalError.unexpectedCase("mZ property");
      };
    }

    // was: ps.apply(fun (Property p)->void { vs = p.vars(hidden,vs); })
    static Set<Name> varsSeq(Set<Name> hidden, Set<Name> vs, Seq<Property> ps) {
      for (Enum<Property> e = ps.enumerate(); e.more(); e.advance()) {
        vs = e.current().vars(hidden, vs);
      }
      return vs;
    }

    /** Perform a substitution in property. */
    public final Property substitute(Map<Name, Expr> sb) {
      Property prop = subs(sb, new HashSet<Name>(), Expr.calcFree(sb));
      return prop == null ? this : prop;
    }

    final Property subs(Map<Name, Expr> sb, Set<Name> hidden, Set<Name> free) {
      switch (this) {
        case Call p: {
          Seq<Expr> args = Expr.subsSeq(sb, hidden, free, p.args);
          return args == null ? null : new Call(p.name, args).setFrom(this);
        }
        case IsTrue p: {
          Expr nexpr = p.expr.subs(sb, hidden, free);
          if (nexpr != null) {
            return new IsTrue(nexpr).setFrom(this);
          } else
            return null;
        }
        case Equal p: {
          Expr nleft = p.left.subs(sb, hidden, free);
          Expr nright = p.right.subs(sb, hidden, free);
          if (nleft != null || nright != null) {
            return new Equal(nleft != null ? nleft : p.left,
                             nright != null ? nright : p.right)
                     .setFrom(this);
          } else
            return null;
        }
        case Member p: {
          Expr nleft = p.left.subs(sb, hidden, free);
          Expr nright = p.right.subs(sb, hidden, free);
          if (nleft != null || nright != null) {
            return new Member(nleft != null ? nleft : p.left,
                              nright != null ? nright : p.right)
                     .setFrom(this);
          } else
            return null;
        }
        case Subset p: {
          Expr nleft = p.left.subs(sb, hidden, free);
          Expr nright = p.right.subs(sb, hidden, free);
          if (nleft != null || nright != null) {
            return new Subset(nleft != null ? nleft : p.left,
                              nright != null ? nright : p.right)
                     .setFrom(this);
          } else
            return null;
        }
        default:
          throw FatalError.unexpectedCase("mZ property");
      }
    }

    static Seq<Property> subsSeq(Map<Name, Expr> sb, Set<Name> hidden,
                                 Set<Name> free,
                                 Seq<Property> props) {
      boolean[] change = {false};
      props = props.map(p -> {
        Property np = p.subs(sb, hidden, free);
        if (np != null) {
          change[0] = true;
          return np;
        } else
          return p;
      });
      if (change[0])
        return ArraySeq.from(props);
      else
        return null;
    }

    /** Format property. */
    public Format toFormat1(FormatInfo info) {
      return switch (this) {
        case Equal p -> Format.block(
                          Format.append(p.left.toFormat(new FormatInfo()),
                                        Format.literal(" = ")),
                          Format.indent(p.right.toFormat(new FormatInfo()))
                        );
        case IsTrue p -> Format.append(Format.literal("? "),
                                       p.expr.toFormat(new FormatInfo()));
        case Member p -> Format.block(
                           Format.append(p.left.toFormat(new FormatInfo()),
                                         Format.literal(" : ")),
                           Format.indent(p.right.toFormat(new FormatInfo()))
                         );
        case Subset p -> Format.block(
                           Format.append(p.left.toFormat(new FormatInfo()),
                                         Format.literal(" <= ")),
                           Format.indent(p.right.toFormat(new FormatInfo()))
                         );
        case Call p -> Format.block(
                         Format.literal("$" + p.name.toString()),
                         Format.list(Format.literal("("),
                                     Format.literal(","),
                                     Format.literal(")"),
                                     (Function<Expr, Format>) Format::build,
                                     p.args)
                       );
        default -> throw FatalError.unexpectedCase("mZ property");
      };
    }

    public Format toFormat(FormatInfo info) {
      return Format.block(
               locator.toFormat(info),
               Format.literal(": "),
               toFormat1(info)
             );
    }
  }

  public static class UnitDef extends MZTerm {

    /** Locator of a unit definition. */
    public Locator locator = null;

    public Name name;
    public Seq<Name> parents;
    public Seq<Name> globals;
    public Seq<Name> locals;
    public Seq<Property> props;

    /** Constructor of a unit definition. */
    public UnitDef(Name name,
                   Seq<Name> parents,
                   Seq<Name> globals,
                   Seq<Name> locals,
                   Seq<Property> props) {
      this.name = name;
      this.parents = parents;
      this.globals = globals;
      this.locals = locals;
      this.props = props;
    }

    /** Pizza's generated case factory. */
    public static UnitDef UnitDef(Name name,
                                  Seq<Name> parents,
                                  Seq<Name> globals,
                                  Seq<Name> locals,
                                  Seq<Property> props) {
      return new UnitDef(name, parents, globals, locals, props);
    }

    /** Set locator of a unit definition. */
    public UnitDef set(Locator locator) {
      this.locator = locator;
      return this;
    }

    public Format toFormat(FormatInfo info) {
      return Format.beneath(new Format[]{
               Format.literal("UNIT " + name.getRepr()),
               Format.block(
                 Format.literal("PARENTS "),
                 Format.indent(
                   Format.list(Format.empty, Format.literal(" "),
                               Format.empty,
                               (Function<Name, Format>) Format::build,
                               parents)
                 )
               ),
               Format.block(
                 Format.literal("GLOBALS "),
                 Format.indent(
                   Format.list(Format.empty, Format.literal(" "),
                               Format.empty,
                               (Function<Name, Format>) Format::build,
                               globals)
                 )
               ),
               Format.block(
                 Format.literal("LOCALS "),
                 Format.indent(
                   Format.list(Format.empty, Format.literal(" "),
                               Format.empty,
                               (Function<Name, Format>) Format::build,
                               locals)
                 )
               ),
               Format.block(
                 Format.literal("PROPERTIES "),
                 Format.indent(
                   Format.list(Format.empty, Format.literal("; "),
                               Format.empty,
                               (Function<Property, Format>) Format::build,
                               props)
                 )
               )
             });
    }
  }
}
