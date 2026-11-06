package zeta.tools.zchecker;

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
import zeta.content.zirp.Act;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Item;
import zeta.content.zirp.LabelExpr;
import zeta.content.zirp.MSZNames;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.NameDecl;
import zeta.content.zirp.Predicate;
import zeta.content.zirp.State;
import zeta.content.zirp.TermVisitor;
import zeta.content.zirp.Transition;
import zeta.content.zirp.Type;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZNames;
import zeta.format.Format;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.util.Annotation;
import zeta.util.FatalError;
import zeta.util.Name;

/**
 * A class for type checking ZIRP terms.
 *
 * @version $Id: TermChecker.pizza,v 1.13 2000/07/20 11:39:01 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p><b>Matching on constant kind cases.</b>  Pizza compiled
 * <code>case Unary(Expr.UnaryKind.Power, Expr t):</code> and every <code>switch</code> over
 * <code>Expr.UnaryKind</code>, <code>Expr.BinaryKind</code>, <code>Expr.QuantorKind</code>
 * and <code>Predicate.UnaryKind</code> as a comparison of the kind's <code>$$tag</code> --
 * confirmed in the 2002 class file -- not of object identity.  The two differ for a kind
 * object that was deserialised (none of these bases has a <code>readResolve</code>), e.g. in
 * a type imported from a stored parent environment.  The port therefore switches on the
 * tag, using the private <code>K_*</code> constants below, whose values were read from the
 * reference with <code>harness/adt_tags.py</code>.  Kind cases that carry data
 * (<code>SelectiveDelta</code>, <code>Hide</code>, <code>Renaming</code>,
 * <code>Decorate</code>) are classes and are matched by type; that is equivalent, as each
 * tag has exactly one class.  Cases of the term ADTs themselves (<code>Expr</code>,
 * <code>Item</code>, <code>Expr.Decl</code>, ...) are classes too and are matched by type.</p>
 *
 * <p><b>Shapes.</b>  The three visitors keep their names and constructor access; the
 * synthetic outer-instance field is javac's <code>this$0</code> instead of Pizza's
 * <code>this0</code> (package-internal; nothing outside the package names these classes).
 * Pizza's <code>access$NNN</code> bridges, the lifted <code>$closure$...</code> methods and
 * the <code>$$closures</code> classes are gone.  The private <code>matchSign</code> takes a
 * <code>Function&lt;Name,Name&gt;</code> where Pizza erased the arrow type to
 * <code>pizza.support.Closure</code>; it is private, so no binary class can see it.</p>
 *
 * <p><b>Closures.</b>  The folds in <code>matchSign</code> and <code>makeSignature</code>
 * (which assign captured locals) became loops over <code>enumerate()</code>.  The
 * <code>Seq.map</code> in <code>check(Item)</code> and <code>checkAxiomatic</code>, the
 * <code>Map.filter</code> of <code>Pre</code> and the <code>apply</code> of
 * <code>Renaming</code> stay lambdas.  The unused <code>ArraySeq.from(typeEnv)</code> in the
 * <code>Pre</code> case is dropped (it only enumerates a map).</p>
 *
 * <p><b>Unification mutates terms in place</b> -- <code>TypeVarResolver</code> assigns
 * <code>Expr.UniVar.oper</code> and <code>.name</code> -- by design, kept.</p>
 *
 * <p>Two 1998 defects are preserved; each is marked PORT NOTE at its site.</p>
 */
public class TermChecker {

  // Kind tags (ABI; read from the reference with harness/adt_tags.py).

  // Expr.UnaryKind
  private static final int K_POWER = 1;
  private static final int K_THETA = 2;
  private static final int K_NOT = 3;
  private static final int K_DELTA = 4;
  private static final int K_SELECTIVEDELTA = 5;
  private static final int K_XI = 6;
  private static final int K_PRE = 7;
  private static final int K_HIDE = 8;
  private static final int K_RENAMING = 9;
  private static final int K_DECORATE = 10;

  // Expr.QuantorKind
  private static final int K_LAMBDA = 1;
  private static final int K_MU = 2;
  private static final int K_LET = 3;
  private static final int K_SET = 4;
  private static final int K_FORALL = 5;
  private static final int K_EXISTS = 6;
  private static final int K_EXISTS1 = 7;

  // Expr.BinaryKind
  private static final int K_APPLY = 1;
  private static final int K_AND = 2;
  private static final int K_OR = 3;
  private static final int K_IMPLIES = 4;
  private static final int K_IFF = 5;
  private static final int K_COMPOSE = 6;
  private static final int K_PROJECT = 7;
  private static final int K_PIPE = 8;

  // Predicate.UnaryKind
  private static final int K_STATELIFT = 2;
  private static final int K_TRANSLIFT = 3;
  private static final int K_STEPCONSTR = 9;
  private static final int K_TIMECONSTR = 10;

  private static int tag(Expr.UnaryKind k) {
    return k.zeta$content$zirp$Expr$UnaryKind$$tag;
  }

  /** Pizza's <code>case Unary(Expr.UnaryKind.Power, ...)</code>: by tag. */
  private static boolean isPower(Expr e) {
    return e instanceof Expr.Unary u && tag(u.kind) == K_POWER;
  }

  // FIELDS

  /**
   * The local environment of this term checker.
   */
  private Env.Local env;

  // CONSTRUCTION

  /**
   * Make a new term checker working on the given environment.
   */
  public TermChecker(Env.Local env) {
    this.env = env;
  }

  // ITEM CHECKING

  /**
   * Check the given item. After checking, the names defined by the
   * item are introduced as globals in the global environment.
   */
  public void check(Item item) {
    if (item instanceof Item.AxiomaticDef a) {
      defineFormals(a.formals);
      checkAxiomatic(new ArraySeq<NameDecl>(a.formals).map(n -> n.name),
                     a.def);
    } else if (item instanceof Item.FixityDef || item instanceof Item.EmbeddedText) {
      // nothing
    } else if (item instanceof Item.StatechartDef s) {
      checkState(s.root);
    } else if (item instanceof Item.StaticReactionDef s) {
      checkReaction(s.name, s.reactions);
    } else if (item instanceof Item.EnrichDef e) {
      checkEnrich(e.name);
    } else {
      env.addDiag(Locator.get(item.an),
                  Diag.Category.Warning,
                  Report.notSupported("mSZ item"));
    }
    // System.out.println(item);
  }

  /**
   * Check the given expression as a an axiomatic definition.
   * The expression must denote a schema. The names introduced
   * by this schema are defined in the global environment.
   */
  private void checkAxiomatic(Seq<Name> generics, Expr schema) {
    if (schema instanceof Expr.Text text) {
      Expr.Decl[] decls = text.decls;
      Predicate[] props = text.props;
      TypeUnifier unif = new TypeUnifier();
      TypeFinalizer fin = new TypeFinalizer();
      // first check the declarations and commit them.
      // this enhances diagnostics when cycle errors occure,
      // since the names introduced by the schema are committed
      // independend of the usage of the property
      unif.visit(decls);
      fin.visit(decls);
      env.open();
      defineDecls(decls);
      Type.put(schema.an, Env.makePower(env.makeSignature()));
      env.makeGlobal(generics);
      // now check the properties
      unif.visit(props);
      fin.visit(props);
      env.close();
    } else {
      if (schema instanceof Expr.FreeType ft) {
        // declare the name as a given type and commit it.
        // this allows for cyclic free types.
        NameDecl nd = ft.name;
        env.global().define(
          nd.name,
          Locator.get(nd.an),
          generics,
          Env.makePower(Env.makeGenGiven(nd.name,
                                         generics.map(Env::makeGiven)))
        );
      } else if (schema instanceof Expr.GivenType gt) {
        // declare it here (we don't have the generics at hand at the
        // checker visitor)
        NameDecl nd = gt.name;
        env.global().define(
          nd.name,
          Locator.get(nd.an),
          generics,
          Env.makePower(Env.makeGenGiven(nd.name,
                                         generics.map(Env::makeGiven)))
        );
      }
      Expr.Signature sign = checkSchema(schema);
      if (sign != null) {
        env.open();
        defineSign(Locator.get(schema.an), sign);
        env.makeGlobal(generics);
        env.close();
      }
    }
  }

  /**
   * Check the given state.
   */
  private void checkState(State state) {
    // FIXME: state configuration checking
    check(state);
  }

  /**
   * Check the given static reaction.
   */
  private void checkReaction(NameDecl name, LabelExpr[] reactions) {
    // FIXME: state name checking
    for (int i = 0; i < reactions.length; i++) {
      check(reactions[i]);
    }
  }

  /**
   * Check mSZ class enrichment.
   */
  private void checkEnrich(NameAppl nap) {
    Expr[] sets = new Expr[nap.actuals.length];
    boolean errors = false;
    for (int i = 0; i < sets.length; i++) {
      sets[i] = checkSet(nap.actuals[i]);
      errors = errors || sets[i] == null;
    }
    UnitEnv eenv = env.global().getEnrichedEnv(nap.name);
    if (eenv == null) {
      // FIXME: move message to Report
      env.global().addDiag(
        Locator.get(nap.an),
        Diag.Category.Error,
        Format.literal("enriched class `" + nap.name + "' does not exists")
      );
    }
    else {
      Name[] formals = eenv.getFormals();
      if (formals.length != sets.length) {
        // FIXME: move message to Report
        env.addDiag(
          Locator.get(nap.an),
          Diag.Category.Error,
          Format.literal(
            "enriched class `" + nap.name + "' has incompatible genericity"
          )
        );
      }
      else if (!errors) {
        UnitEnv.ConstantInfo[] infos = eenv.getConstants();
        if (formals.length > 0) {
          Map<Name, Expr> subs =
            new HashMap<Name, Expr>().defineAll(new ArraySeq<>(formals),
                                                new ArraySeq<>(sets));
          for (int i = 0; i < infos.length; i++) {
            Seq<Name> ownGen = new ArraySeq<>(infos[i].generics);
            env.global().define(
              infos[i].name,
              Locator.get(nap.an),
              ownGen,
              env.global().substitute(
                infos[i].type,
                subs.undefineAll(ownGen)
              )
            );
          }
        } else {
          for (int i = 0; i < infos.length; i++) {
            Seq<Name> ownGen = new ArraySeq<>(infos[i].generics);
            env.global().define(
              infos[i].name,
              Locator.get(nap.an),
              ownGen,
              infos[i].type
            );
          }
        }
      }
    }
  }



  // EXPRESSION AND PREDICATE CHECKING

  /**
   * Check the given expression and return its type.
   * On type errors, diagnostics are produced, and the
   * type <code>Type.anyType</code> may be returned or embedded
   * in the returned type.
   */
  public Expr check(Expr expr) {
    new TypeUnifier().visit(expr);
    new TypeFinalizer().visit(expr);
    return typeOf(expr);
  }


  /**
   * Check the given expression as a schema. Calls
   * the general check on expression, and tests if the returned type
   * is a schema. If this is the case, the schemas
   * signature is returned, otherwise null.
   */
  public Expr.Signature checkSchema(Expr expr) {
    new TypeUnifier().visit(expr);
    new TypeFinalizer().visit(expr);
    return signOf(expr);
  }

  /**
   * Check the given expression as a set. Calls the general
   * check on expression, and tests if the returned type is a
   * powerset. Returns the element type of this set, or null, if it
   * is not a set.
   */
  public Expr checkSet(Expr expr) {
    Expr type = check(expr);
    if (type == Type.anyType)
      return null;
    else {
      if (isPower(type))
        return ((Expr.Unary) type).oper;
      env.addDiag(Locator.get(expr.an),
                  Diag.Category.Error,
                  Report.expectedSet(type));
      return null;
    }
  }

  /**
   * Check the given predicate.
   * On type errors, diagnostics are left in the environment. On success,
   * the term and it subterms are type-correct, and type
   * annotations and actualization annotations are found as specified.
   */
  public void check(Predicate pred) {
    new TypeUnifier().visit(pred);
    new TypeFinalizer().visit(pred);
  }

  /**
   * Check the given state.
   * On type errors, diagnostics are left in the environment. On success,
   * the term and it subterms are type-correct, and type
   * annotations and actualization annotations are found as specified.
   */
  public void check(State state) {
    new TypeUnifier().visit(state);
    new TypeFinalizer().visit(state);
  }

  /**
   * Check the given label expression.
   * On type errors, diagnostics are left in the environment. On success,
   * the term and it subterms are type-correct, and type
   * annotations and actualization annotations are found as specified.
   */
  public void check(LabelExpr lexpr) {
    new TypeUnifier().visit(lexpr);
    new TypeFinalizer().visit(lexpr);
  }

  /**
   * Check the given transition.
   * On type errors, diagnostics are left in the environment. On success,
   * the term and it subterms are type-correct, and type
   * annotations and actualization annotations are found as specified.
   */
  public void check(Transition trans) {
    new TypeUnifier().visit(trans);
    new TypeFinalizer().visit(trans);
  }



  // CHECKING ALGORITHMS

  /**
   * Type unification term visitor class. Implements a classical
   * type unification algorithm, where intermediate types may
   * contain free type variables. <p>
   *
   * This visitor doesn't treats items (beside of that their subterms
   * may be visited). <p>
   *
   * The following conjecture simplifies the algorithm compared to
   * full unification: when requesting for product or schema types
   * (e.g. in selections or schema expressions), we assume that the
   * bottom-up delivered type has already structure, and is not just
   * a type variable (provided the typing is correct). This is
   * justified by that there cannot be an outer context which contributes
   * to the structure of the type in such situations. This in turn
   * comes from that all variables are declared with type in Z. (Yet
   * we need unification e.g. for the empty set -- and its convenient
   * for checking generics).
   */

  class TypeUnifier extends TermVisitor {

    public TypeUnifier() {
      dontRewrites = true;
    }

    // EXPRESSIONS

    public Expr visit(Expr.Number expr) {
      Type.put(expr.an, Env.makeGiven(ZNames.baseNum));
      return null;
    }

    public Expr visit(Expr.Denotation expr) {
      Type.put(expr.an, Env.denotationType);
      return null;
    }

    public Expr visit(Expr.Variable expr) {
      NameAppl nap = expr.name;
      visit(nap.actuals);
      Locator loc = Locator.get(expr.an);
      Env.Instance inst = env.lookup(nap.name, loc);
      if (inst.type != Type.anyType) {
        if (nap.actuals.length > 0 &&
            inst.variables.length != nap.actuals.length) {
          env.addDiag(loc,
                      Diag.Category.Error,
                      Report.incompatActualization(nap.name,
                                                   nap.actuals.length,
                                                   inst.variables.length));
          // set variables to any
          for (int i = 0; i < inst.variables.length; i++) {
            env.global().unify(loc, inst.variables[i], Type.anyType);
          }
        } else {
          if (nap.actuals.length > 0) {
            for (int i = 0; i < nap.actuals.length; i++) {
              env.global().unify(Locator.get(nap.an),
                                 inst.variables[i],
                                 elemTypeOf(nap.actuals[i]));
            }
          }
          Act.put(nap.an, inst.variables);
        }
      }
      Type.put(expr.an, inst.type);
      return null;
    }

    public Expr visit(Expr.Tuple expr) {
      super.visit(expr);
      Expr[] types = new Expr[expr.elements.length];
      for (int i = 0; i < expr.elements.length; i++) {
        types[i] = typeOf(expr.elements[i]);
      }
      Type.put(expr.an, Env.makeProduct(types));
      return null;
    }

    public Expr visit(Expr.Product expr) {
      super.visit(expr);
      Expr[] types = new Expr[expr.elements.length];
      for (int i = 0; i < expr.elements.length; i++) {
        types[i] = elemTypeOf(expr.elements[i]);
      }
      Type.put(expr.an, Env.makePower(Env.makeProduct(types)));
      return null;
    }

    public Expr visit(Expr.Binding expr) {
      super.visit(expr);
      Name[] names = new Name[expr.bindings.length];
      Expr[] types = new Expr[expr.bindings.length];
      Map<Name, Locator> known = new HashMap<>();
      for (int i = 0; i < expr.bindings.length; i++) {
        Locator loc = Locator.get(expr.bindings[i].name.an);
        Name name = expr.bindings[i].name.name;
        names[i] = name;
        types[i] = typeOf(expr.bindings[i].def);
        if (known.defines(name)) {
          env.addDiag(loc,
                      Diag.Category.Error,
                      Report.redeclaration(name, known.get(name)));
        } else {
          known = known.define(name, loc);
        }
      }
      Type.put(expr.an, Env.makeSignature(names, types));
      return null;
    }

    public Expr visit(Expr.Display expr) {
      super.visit(expr);
      Expr elemType = Env.makeVar(new Name("%display-type"));
      for (int i = 0; i < expr.elements.length; i++) {
        env.global().unify(Locator.get(expr.elements[i].an),
                           elemType, typeOf(expr.elements[i]));
      }
      Type.put(expr.an, Env.makePower(elemType));
      return null;
    }

    public Expr visit(Expr.Cond expr) {
      super.visit(expr);
      // FIXME: better error message
      Expr thenType = typeOf(expr.thenPath);
      env.global().unify(Locator.get(expr.an),
                         thenType, typeOf(expr.elsePath));
      Type.put(expr.an, thenType);
      return null;
    }

    public Expr visit(Expr.Quantor expr) {
      visit(expr.matrix);
      Expr.Signature sign = signOf(expr.matrix);
      if (sign == null) {
        // CHECKME: right thing to not check on, because of
        //          possible followup errors?
        Type.put(expr.an, Type.anyType);
      } else {
        int kind = expr.kind.zeta$content$zirp$Expr$QuantorKind$$tag;
        switch (kind) {
        case K_LAMBDA: case K_MU: case K_LET: case K_SET:
          env.open();
          defineSign(Locator.get(expr.matrix.an), sign);
          visit(expr.range);
          env.close();
          switch (kind) {
          case K_LAMBDA:
            Type.put(expr.an,
                     Env.makePower(
                       Env.makeProduct(new Expr[]{ ctupleOf(expr.matrix),
                                                   typeOf(expr.range) })));
            break;
          case K_MU: case K_LET:
            Type.put(expr.an, typeOf(expr.range));
            break;
          case K_SET:
            Type.put(expr.an, Env.makePower(typeOf(expr.range)));
            break;
          default:
            throw FatalError.unexpectedCase();
          }
          break;
        case K_EXISTS: case K_EXISTS1: case K_FORALL:
          visit(expr.range);
          Expr.Signature rangeSign = signOf(expr.range);
          if (rangeSign == null) {
            Type.put(expr.an, Type.anyType);
          } else {
            Map<Name, Expr> typeEnv =
              new HashMap<Name, Expr>().defineAll(new ArraySeq<>(rangeSign.names),
                                                  new ArraySeq<>(rangeSign.types));
            for (int i = 0; i < sign.names.length; i++) {
              if (!typeEnv.defines(sign.names[i])) {
                env.addDiag(Locator.get(expr.range.an),
                            Diag.Category.Error,
                            Report.undeclaredField(sign.names[i], sign));
              } else {
                Expr rangeType = typeEnv.get(sign.names[i]);
                try {
                  Type.unify(sign.types[i], rangeType);
                }
                catch (Type.UnificationFailure e) {
                  env.addDiag(Locator.get(expr.an),
                              Diag.Category.Error,
                              Report.typeIncompatInJoin(sign.names[i],
                                                        sign.types[i],
                                                        rangeType));
                }
                typeEnv = typeEnv.undefine(sign.names[i]);
              }
            }
            Type.put(expr.an, Env.makePower(makeSignature(typeEnv)));
          }
          break;
        default:
          // Pizza: `new Error()` on an unknown kind tag.
          throw FatalError.unexpectedCase("quantor kind");
        }
      }
      return null;
    }

    public Expr visit(Expr.Select expr) {
      super.visit(expr);
      Expr operType = typeOf(expr.oper);
      Expr type;
      if (operType == Type.anyType) {
        type = Type.anyType;
      } else {
        // PORT CHANGE -- 1998 defect fixed.  The source read
        //     case UniVar(_, Expr bound):
        //       if (bound != null)
        //         operType = bound;
        //         continue;
        // i.e. the `continue` was unconditional despite the indentation (confirmed in the
        // 2002 class file, offsets 56-80: both arms of the null test reach `goto 32`), so
        // an *unbound* UniVar looped forever and hung the checker thread.  The intent is the
        // binding-chain walk used everywhere else (Type.unify and friends): follow bound
        // variables, stop at an unbound one.  With the brace restored an unbound variable
        // falls through to the existing undeclaredPos / undeclaredField diagnostics like any
        // other operand type that is neither a product nor a signature.  Safe: the old
        // behaviour on this input was a hang, so no recorded output can depend on it, and
        // every other input takes exactly the old path.
        for (;;) {
          if (operType instanceof Expr.UniVar uv) {
            Expr bound = uv.oper;
            if (bound != null) {
              operType = bound;
              continue;
            }
          }
          break;
        }
        if (expr.name.name.getRepr().startsWith("#")) {
          // tuple selection
          int pos = Integer.parseInt(expr.name.name.getRepr().substring(1)) - 1;
          if (operType instanceof Expr.Product p) {
            Expr[] elements = p.elements;
            if (pos >= 0 && pos < elements.length) {
              type = elements[pos];
            } else {
              env.addDiag(Locator.get(expr.an),
                          Diag.Category.Error,
                          Report.undeclaredPos(pos, operType));
              type = Type.anyType;
            }
          } else {
            env.addDiag(Locator.get(expr.an),
                        Diag.Category.Error,
                        Report.undeclaredPos(pos, operType));
            type = Type.anyType;
          }
        } else {
          // schema selection
          if (operType instanceof Expr.Signature s) {
            Name[] names = s.names;
            Expr[] types = s.types;
            int i = 0;
            while (i < names.length && !names[i].equals(expr.name.name)) i++;
            if (i < names.length) {
              type = types[i];
            } else {
              env.addDiag(Locator.get(expr.an),
                          Diag.Category.Error,
                          Report.undeclaredField(expr.name.name,
                                                 operType));
              type = Type.anyType;
            }
          } else {
            env.addDiag(Locator.get(expr.an),
                        Diag.Category.Error,
                        Report.undeclaredField(expr.name.name,
                                               operType));
            type = Type.anyType;
          }
        }
      }
      Type.put(expr.an, type);
      return null;
    }

    public Expr visit(Expr.Unary expr) {
      super.visit(expr);

      switch (tag(expr.kind)) {

      case K_POWER:
        Type.put(expr.an, Env.makePower(Env.makePower(elemTypeOf(expr.oper))));
        return null;

      case K_THETA: {
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          int strokes = 0;
          Expr oper = expr.oper;
          // PORT NOTE -- 1998 defect, preserved.  The source wraps this match in
          // `do { ... continue; } while (false)`, meant as a loop over nested
          // decorations; but `continue` in a do-while jumps to the (false) condition, so at
          // most one decoration is ever stripped.  Confirmed in the 2002 class file.  A
          // binding formation over a doubly decorated schema therefore undecorates its
          // names by one stroke only; kept, as it decides the checked type.
          if (oper instanceof Expr.Unary u && u.kind instanceof Expr.UnaryKind.Decorate) {
            strokes++;
            oper = u.oper;
          }
          Name[] unames = new Name[sign.names.length];
          for (int i = 0; i < sign.names.length; i++) {
            checkBind(Locator.get(expr.an), sign.names[i], sign.types[i]);
            unames[i] = sign.names[i].undecorate(strokes);
          }
          Type.put(expr.an, Env.makeSignature(unames, sign.types));
        }
        return null;
      }

      case K_NOT: {
        Expr.Signature sign = signOf(expr.oper); // to force check for schema
        Type.put(expr.an, typeOf(expr.oper));
        return null;
      }

      case K_DELTA: case K_XI: {
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          Name[] names = new Name[sign.names.length * 2];
          Expr[] types = new Expr[names.length];
          System.arraycopy(sign.names, 0, names, 0, sign.names.length);
          System.arraycopy(sign.types, 0, types, 0, sign.types.length);
          System.arraycopy(sign.types, 0, types, sign.types.length,
                           sign.types.length);
          for (int i = 0; i < sign.names.length; i++) {
            names[sign.names.length + i] = new Name(sign.names[i].getRepr() +
                                                    Name.prime);
          }
          Type.put(expr.an, Env.makePower(Env.makeSignature(names, types)));
        }
        return null;
      }

      case K_SELECTIVEDELTA: {
        NameAppl[] selNames = ((Expr.UnaryKind.SelectiveDelta) expr.kind).names;
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          Name[] names = new Name[sign.names.length + selNames.length];
          Expr[] types = new Expr[names.length];
          System.arraycopy(sign.names, 0, names, 0, sign.names.length);
          System.arraycopy(sign.types, 0, types, 0, sign.types.length);
          Map<Name, Expr> typeEnv =
            new HashMap<Name, Expr>().defineAll(new ArraySeq<>(sign.names),
                                                new ArraySeq<>(sign.types));
          for (int i = 0; i < selNames.length; i++) {
            Name name = selNames[i].name;
            names[sign.names.length + i] = new Name(name.getRepr() + Name.prime);
            if (!typeEnv.defines(name)) {
              env.addDiag(Locator.get(selNames[i].an),
                          Diag.Category.Error,
                          Report.undeclaredField(name, sign));
              types[sign.names.length + i] = Type.anyType;
            } else {
              types[sign.names.length + i] = typeEnv.get(name);
            }
          }
          Type.put(expr.an, Env.makePower(Env.makeSignature(names, types)));
        }
        return null;
      }

      case K_PRE: {
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          Map<Name, Expr> typeEnv =
            new HashMap<Name, Expr>().defineAll(new ArraySeq<>(sign.names),
                                                new ArraySeq<>(sign.types));
          typeEnv =
            typeEnv.filter(
              (name, type) -> {
                String[] comps = name.getComponents();
                return comps.length == 0 ||
                       !(comps[comps.length - 1].equals("" + Name.prime) ||
                         comps[comps.length - 1].equals("" + Name.output));
              }
            );
          Type.put(expr.an, Env.makePower(makeSignature(typeEnv)));
        }
        return null;
      }

      case K_HIDE: {
        NameAppl[] hidden = ((Expr.UnaryKind.Hide) expr.kind).names;
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          Map<Name, Expr> typeEnv =
            new HashMap<Name, Expr>().defineAll(new ArraySeq<>(sign.names),
                                                new ArraySeq<>(sign.types));
          for (int i = 0; i < hidden.length; i++) {
            Name name = hidden[i].name;
            if (!typeEnv.defines(name)) {
              env.addDiag(Locator.get(hidden[i].an),
                          Diag.Category.Error,
                          Report.undeclaredField(name, sign));
            } else {
              typeEnv = typeEnv.undefine(name);
            }
          }
          Type.put(expr.an, Env.makePower(makeSignature(typeEnv)));
        }
        return null;
      }

      case K_RENAMING: {
        Expr.Rename[] renames = ((Expr.UnaryKind.Renaming) expr.kind).renames;
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          final Map<Name, Expr> typeEnv =
            new HashMap<Name, Expr>().defineAll(new ArraySeq<>(sign.names),
                                                new ArraySeq<>(sign.types));
          Set<Name> notCaptured =
            new HashSet<Name>().includeAll(new ArraySeq<>(sign.names));
          env.open();
          for (int i = 0; i < renames.length; i++) {
            Name old = renames[i].old.name;
            if (!typeEnv.defines(old)) {
              // STD: old not required to be defined
              // env.addDiag(Locator.get(renames[i].old.an),
              //           Diag.Category.Error,
              //           Report.undeclaredField(old, sign));
            } else {
              notCaptured = notCaptured.exclude(old);
              env.define(renames[i]._new.name,
                         Locator.get(renames[i]._new.an),
                         typeEnv.get(old));
            }
          }
          notCaptured.apply(
            n -> env.define(n, Locator.get(expr.oper.an), typeEnv.get(n))
          );
          Type.put(expr.an, Env.makePower(env.makeSignature()));
          env.close();
        }
        return null;
      }

      case K_DECORATE: {
        String decore = ((Expr.UnaryKind.Decorate) expr.kind).decore;
        Expr.Signature sign = signOf(expr.oper);
        if (sign == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          // FIXME: check what the parser generates for indices
          Name[] dnames = new Name[sign.names.length];
          for (int i = 0; i < dnames.length; i++) {
            dnames[i] = new Name(sign.names[i] + decore);
          }
          Type.put(expr.an, Env.makePower(Env.makeSignature(dnames,
                                                            sign.types)));
        }
        return null;
      }

      default:
        env.addDiag(Locator.get(expr.an),
                    Diag.Category.Error,
                    Report.notSupported("expression variant"));
        Type.put(expr.an, Type.anyType);
        return null;
      }
    }

    public Expr visit(Expr.Binary expr) {
      super.visit(expr);
      int kind = expr.kind.zeta$content$zirp$Expr$BinaryKind$$tag;
      switch (kind) {
      case K_APPLY: {
        Expr funType = elemTypeOf(expr.left);
        Expr argType = typeOf(expr.right);
        // the following for better error messages: try to
        // narrow the point of a type conflict as far as possible
        if (funType instanceof Expr.Product fp) {
          Expr[] funTypes = fp.elements;
          if (funTypes.length == 2) {
            // result and parameter type known
            Expr paramType = funTypes[0];
            Expr resType = funTypes[1];
            if (paramType instanceof Expr.Product pp
                && argType instanceof Expr.Product ap
                && expr.right instanceof Expr.Tuple tp) {
              Expr[] paramTypes = pp.elements;
              Expr[] argTypes = ap.elements;
              Expr[] elems = tp.elements;
              if (paramTypes.length == argTypes.length &&
                  paramTypes.length == elems.length) {
                // can compare parameter and argument types one-by-one
                for (int i = 0; i < elems.length; i++) {
                  env.global().unify(Locator.get(elems[i].an),
                                     paramTypes[i],
                                     argTypes[i]);
                }
                Type.put(expr.an, resType);
                return null;
              }
            }
            env.global().unify(Locator.get(expr.right.an),
                               paramType,
                               argType);
            Type.put(expr.an, resType);
            return null;
          }
        }
        // general method
        Expr resType = Env.makeVar(new Name("%result-type"));
        env.global().unify(Locator.get(expr.an),
                           funType,
                           Env.makeProduct(
                             new Expr[]{ argType,
                                         resType }));
        Type.put(expr.an, resType);
        return null;
      }

      case K_AND: case K_OR: case K_IMPLIES: case K_IFF:
      case K_COMPOSE: case K_PIPE: case K_PROJECT: {
        Expr.Signature sign1 = signOf(expr.left);
        Expr.Signature sign2 = signOf(expr.right);
        if (sign1 == null || sign2 == null) {
          Type.put(expr.an, Type.anyType);
        } else {
          Expr.Signature sign;
          Locator loc = Locator.get(expr.an);
          switch (kind) {
          case K_AND: case K_OR: case K_IMPLIES: case K_IFF:
            sign = matchSign(loc, sign1, sign2, n -> null);
            break;
          case K_COMPOSE:
            sign = matchSign(
                     loc, sign1, sign2,
                     n -> n.decorate("" + Name.prime)
                   );
            break;
          case K_PIPE:
            sign = matchSign(
                     loc, sign1, sign2,
                     n -> {
                       String[] comps = n.getComponents();
                       if (comps.length > 1 &&
                           comps[comps.length - 1].equals("" + Name.input)) {
                         return new Name(n.undecorate(1).getRepr() +
                                         Name.output);
                       } else
                         return null;
                     }
                   );
            break;
          case K_PROJECT:
            env.open();
            defineSign(Locator.get(expr.an), sign1);
            for (int i = 0; i < sign2.names.length; i++) {
              checkBind(Locator.get(expr.an), sign2.names[i], sign2.types[i]);
            }
            env.close();
            sign = sign2;
            break;
          default:
            throw FatalError.unexpectedCase("binary kind");
          }
          Type.put(expr.an, Env.makePower(sign));
        }
        return null;
      }

        /*
      default:
        env.addDiag(Locator.get(expr.an),
                    Diag.Category.Error,
                    Report.notSupported());
        Type.put(expr.an, Type.anyType);
        */
      default:
        // Pizza: `new Error()` on an unknown kind tag.
        throw FatalError.unexpectedCase("binary kind");
      }
    }

    public Expr visit(Expr.Text expr) {
      visit(expr.decls);
      env.open();
      defineDecls(expr.decls);
      visit(expr.props);
      Type.put(expr.an, Env.makePower(env.makeSignature()));
      env.close();
      return null;
    }

    public Expr visit(Expr.GivenType expr) {
      env.open();
      Expr typeType = null;
      if (env.global().get(expr.name.name, Locator.get(expr.name.an))
          instanceof Env.Entry.Defined d) {
        typeType = d.type;
      }
      env.defineUnique(expr.name.name, Locator.get(expr.name.an),
                       typeType);
      Type.put(expr.an, Env.makePower(env.makeSignature()));
      env.close();
      return null;
    }

    public Expr visit(Expr.FreeType expr) {
      visit(expr.branches);
      env.open();
      Expr typeType = null;
      if (env.global().get(expr.name.name, Locator.get(expr.name.an))
          instanceof Env.Entry.Defined d) {
        typeType = d.type;
      }
      env.defineUnique(expr.name.name, Locator.get(expr.name.an),
                       typeType);
      Expr type = null;
      if (isPower(typeType)) {
        type = ((Expr.Unary) typeType).oper;
      }
      for (int i = 0; i < expr.branches.length; i++) {
        Expr.Branch branch = expr.branches[i];
        if (branch instanceof Expr.Branch.Constant c) {
          NameDecl nd = c.name;
          env.defineUnique(nd.name, Locator.get(nd.an), type);
        } else if (branch instanceof Expr.Branch.Function f) {
          NameDecl nd = f.name;
          Expr domainType = elemTypeOf(f.type);
          env.defineUnique(nd.name, Locator.get(nd.an),
                           Env.makePower(
                             Env.makeProduct(
                               new Expr[]{domainType, type}
                             )
                           )
                          );
        }
      }
      Type.put(expr.an, Env.makePower(env.makeSignature()));
      env.close();
      return null;
    }


    // PREDICATES

    public Predicate visit(Predicate.Test pred) {
      super.visit(pred);
      Locator loc = Locator.get(pred.an);
      Expr setElemType = elemTypeOf(pred.set);
      Expr elemType = typeOf(pred.elem);
      // the following for better error messages: try to narrow the
      // point of a type conflict as near as possible
      if (setElemType instanceof Expr.Product sp
          && elemType instanceof Expr.Product ep
          && pred.elem instanceof Expr.Tuple tp) {
        Expr[] setTypes = sp.elements;
        Expr[] types = ep.elements;
        Expr[] elems = tp.elements;
        if (setTypes.length == types.length &&
            setTypes.length == elems.length) {
          // here we are
          for (int i = 0; i < elems.length; i++) {
            env.global().unify(Locator.get(elems[i].an),
                               setTypes[i],
                               types[i]);
          }
          return null;
        }
      }
      // general method
      env.global().unify(loc, setElemType, elemType);
      return null;
    }

    public Predicate visit(Predicate.Schema pred) {
      super.visit(pred);
      Expr.Signature sign = signOf(pred.schema);
      if (sign != null) {
        Locator loc = Locator.get(pred.an);
        for (int i = 0; i < sign.names.length; i++) {
          checkBind(loc, sign.names[i], sign.types[i]);
        }
      }
      return null;
    }

    public Predicate visit(Predicate.Unary pred) {
      switch (pred.kind.zeta$content$zirp$Predicate$UnaryKind$$tag) {
      case K_STATELIFT: case K_TRANSLIFT:
      case K_STEPCONSTR: case K_TIMECONSTR:
        env.addDiag(Locator.get(pred.an),
                    Diag.Category.Warning,
                    Report.DZnotSupported());
        return null;
      default:
        return super.visit(pred);
      }
    }

    public Predicate visit(Predicate.Quantor pred) {
      visit(pred.matrix);
      Expr.Signature sign = signOf(pred.matrix);
      if (sign == null) {
        // CHECKME: dont continue because of followup errors (?)
        return null;
      } else {
        env.open();
        defineSign(Locator.get(pred.matrix.an), sign);
        visit(pred.range);
        env.close();
        return null;
      }
    }

    // LABEL EXPRESSIONS

    public LabelExpr visit(LabelExpr.Guarded lexpr) {
      // get signature of data space
      Env.Instance inst = env.lookup(MSZNames.classData, Locator.get(lexpr.an));
      if (inst != null) {
        if (isPower(inst.type)
            && ((Expr.Unary) inst.type).oper instanceof Expr.Signature sign) {
          env.open();
          defineSign(Locator.get(lexpr.guard.an), sign);
          visit(lexpr.guard);
          // add primed variants
          for (int i = 0; i < sign.names.length; i++) {
            env.define(new Name(sign.names[i].getRepr() + Name.prime),
                       Locator.get(lexpr.action.an),
                       sign.types[i]);
          }
          visit(lexpr.action);
          env.close();
          return null;
        }
        env.addDiag(Locator.get(lexpr.an),
                    Diag.Category.Error,
                    Report.expectedSchemaForImplicite(MSZNames.classData,
                                                      inst.type));
      }
      return null;
    }

    public LabelExpr visit(LabelExpr.Temporal lexpr) {
      env.addDiag(Locator.get(lexpr.an),
                  Diag.Category.Warning,
                  Report.notSupported(
          "single predicate in transition would be temporal. DZ"
                  )
                 );
      return null;
    }

    public LabelExpr visit(LabelExpr.Reference lexpr) {
      env.addDiag(Locator.get(lexpr.an),
                  Diag.Category.Error,
                  Report.notSupported("named transitions"));
      return null;
    }


  }



  /**
   * Type finalization term visitor class. <p>
   *
   * Visits all annotations, reports uninstantiated type
   * variables, and removes intermediate type variable constructors.
   */
  private class TypeFinalizer extends TermVisitor {

    TypeFinalizer() {
      dontRewrites = true;
    }

    public NameAppl visit(NameAppl nap) {
      super.visit(nap);
      if (Act.isProvided(nap.an)) {
        Expr[] acts = Act.get(nap.an);
        if (acts.length > 0) {
          TypeVarResolver resolver = new TypeVarResolver();
          Expr[] newActs = resolver.visit(acts);
          if (newActs != null)
            Act.put(nap.an, newActs);
          else
            newActs = acts;
          if (resolver.foundUnboundVariables())
            env.addDiag(Locator.get(nap.an),
                        Diag.Category.Error,
                        Report.incompleteActualization(nap.name, newActs));
        }
      }
      return null;
    }

    public void visit(Annotation an) {
      Expr type = Type.get(an);
      if (type != null) {
        TypeVarResolver resolver = new TypeVarResolver();
        Expr newType = resolver.visit(type);
        if (newType != null)
          Type.put(an, newType);
        else
          newType = type;
        if (resolver.foundUnboundVariables())
          // ASSERT: any annotation carrying a type has also an locator
          env.addDiag(Locator.get(an),
                      Diag.Category.Error,
                      Report.incompleteType(type));
      }
    }
  }

  /**
   * Type variable resolution term visitor class. <p>
   *
   * Resolves bound variables and detects unbounded ones.
   */

  private class TypeVarResolver extends TermVisitor {

    private boolean unboundVars = false;

    TypeVarResolver() {
    }

    boolean foundUnboundVariables() {
      return unboundVars;
    }

    public NameAppl visit(NameAppl app) {
      // mark names of inferred types to be used
      env.global().markUsed(app.name);
      return super.visit(app);
    }

    public Expr visit(Expr.UniVar expr) {
      if (expr.oper == null) {
        unboundVars = true;
        // set to any for future visits of the same variable
        expr.oper = Type.anyType;
        return expr.oper;
      } else if (expr.name == null) {
        // already resolved
        return expr.oper;
      } else {
        Expr newOper = visit(expr.oper);
        if (newOper != null) expr.oper = newOper;
        expr.name = null;
        return expr.oper;
      }
    }
  }


  // AUXILIARIES

  /**
   * Get the annotated type of an expression.
   */
  private Expr typeOf(Expr expr) {
    Expr type = Type.get(expr.an);
    if (type == null)
      throw FatalError.assertionFailed("null type at " +
                                       Locator.get(expr.an));
    return type;
  }

  /**
   * Get the annotated element type of an expression.
   * Produce an error if expression is not a set.
   */
  private Expr elemTypeOf(Expr expr) {
    Expr type = typeOf(expr);
    // the following for better error messages: if we know that
    // this expression is not a set, produce according diagnostics
    for (;;) {
      if (type == Type.anyType) {
        return Type.anyType;
      } else {
        if (isPower(type)) {
          return ((Expr.Unary) type).oper;
        } else if (type instanceof Expr.UniVar uv) {
          Expr bound = uv.oper;
          if (bound == null) {
            // free variable, may become later on a set
            Expr var = Env.makeVar(new Name("%elem-type"));
            env.global().unify(Locator.get(expr.an),
                               type, Env.makePower(var));
            return var;
          } else {
            // bound variable, continue
            type = bound;
            continue;
          }
        }
        // this may never become a powerset type
        env.addDiag(Locator.get(expr.an),
                    Diag.Category.Error,
                    Report.expectedSet(type));
        return Type.anyType;
      }
    }
  }

  /**
   * Get the signature of a schema expression.
   * Produce an error if epxression is not a schema.
   * Return null if signature is not computable.
   */
  private Expr.Signature signOf(Expr expr) {
    Expr type = elemTypeOf(expr);
    for (;;) {
      if (type == Type.anyType)
        return null;
      else {
        if (type instanceof Expr.Signature s) {
          return s;
        } else if (type instanceof Expr.UniVar uv) {
          Expr bound = uv.oper;
          if (bound != null) {
            // bound variable, continue
            type = bound;
            continue;
          }
        }
        env.addDiag(Locator.get(expr.an),
                    Diag.Category.Error,
                    Report.expectedSchema(type));
        return null;
      }
    }
  }

  /**
   * Check if the given name is bounded with the given type
   * in the current scope.
   */
  private void checkBind(Locator loc, Name name, Expr type) {
    Env.Instance inst = env.lookup(name, loc);
    if (inst != null) {
      // FIXME: more instructive error message?
      try {
        Type.unify(type, inst.type);
      }
      catch (Type.UnificationFailure e) {
        env.addDiag(loc,
                    Diag.Category.Error,
                    Report.typeIncompatInEnv(name,
                                             type,
                                             inst.type));
      }
    }
  }

  /**
   * Match two signatures, yielding the combined signature.
   */
  private Expr.Signature matchSign(Locator loc,
                                   Expr.Signature sign1,
                                   Expr.Signature sign2,
                                   Function<Name, Name> matcher) {
    Set<Name> matched = new HashSet<>();
    Map<Name, Expr> typeEnv1 =
      new HashMap<Name, Expr>().defineAll(new ArraySeq<>(sign1.names),
                                          new ArraySeq<>(sign1.types));
    Map<Name, Expr> typeEnv2 =
      new HashMap<Name, Expr>().defineAll(new ArraySeq<>(sign2.names),
                                          new ArraySeq<>(sign2.types));
    for (Enum<Name> e = typeEnv2.domain().enumerate(); e.more(); e.advance()) {
      Name n = e.current();
      Name mn = matcher.apply(n);
      if (mn != null && typeEnv1.defines(mn)) {
        Expr type1 = typeEnv1.get(mn);
        Expr type2 = typeEnv2.get(n);
        typeEnv1 = typeEnv1.undefine(mn);
        typeEnv2 = typeEnv2.undefine(n);
        try {
          Type.unify(type1, type2);
        }
        catch (Type.UnificationFailure ex) {
          env.addDiag(loc,
                      Diag.Category.Error,
                      Report.typeIncompatInMatch(mn, type1,
                                                 n, type2));
        }
      }
    }
    for (Enum<Name> e = typeEnv1.domain().intersect(typeEnv2.domain()).enumerate();
         e.more(); e.advance()) {
      Name n = e.current();
      Expr type1 = typeEnv1.get(n);
      Expr type2 = typeEnv2.get(n);
      try {
        Type.unify(type1, type2);
      }
      catch (Type.UnificationFailure ex) {
        env.addDiag(loc,
                    Diag.Category.Error,
                    Report.typeIncompatInJoin(n, type1, type2));
      }
    }
    return makeSignature(typeEnv1.defineAll(typeEnv2));
  }


  /**
   * Define the given signature in the current scope.
   */
  private void defineSign(Locator loc, Expr.Signature sign) {
    for (int i = 0; i < sign.names.length; i++) {
      env.define(sign.names[i], loc, sign.types[i]);
    }
  }

  /**
   * Define the given declarations in the current scope.
   */
  private void defineDecls(Expr.Decl[] decls) {
    Map<Name, Locator> directly = new HashMap<>();
    for (int i = 0; i < decls.length; i++) {
      Expr.Decl decl = decls[i];
      if (decl instanceof Expr.Decl.Direct d) {
        NameDecl[] names = d.names;
        Expr dtype = elemTypeOf(d.type);
        for (int j = 0; j < names.length; j++) {
          Name name = names[j].name;
          Locator loc = Locator.get(names[j].an);
          if (directly.defines(name))
            env.addDiag(loc,
                        Diag.Category.Error,
                        Report.redeclaration(name, directly.get(name)));
          else {
            env.define(name, loc, dtype);
            directly = directly.define(name, loc);
          }
        }
      } else if (decl instanceof Expr.Decl.Eqn eq) {
        NameDecl ndecl = eq.name;
        Name name = ndecl.name;
        Locator loc = Locator.get(ndecl.an);
        if (directly.defines(name))
          env.addDiag(loc,
                      Diag.Category.Error,
                      Report.redeclaration(name, directly.get(name)));
        else {
          env.define(name, loc, typeOf(eq.def));
          directly = directly.define(name, loc);
        }
      } else if (decl instanceof Expr.Decl.Inclusion inc) {
        Expr.Signature sign = signOf(inc.schema);
        if (sign != null)
          defineSign(Locator.get(inc.schema.an), sign);
      }
    }
  }

  /**
   * Define the given array of name declarations as formals.
   */
  private void defineFormals(NameDecl[] names) {
    for (int i = 0; i < names.length; i++) {
      env.defineUnique(names[i].name,
                       Locator.get(names[i].an),
                       Env.makePower(Env.makeGiven(names[i].name)));
    }
  }

  /**
   * Calculate the characteristic tuple type of a (guaranteed)
   * schema expression.
   */
  private Expr ctupleOf(Expr schema) {
    if (schema instanceof Expr.Text text) {
      Expr.Decl[] decls = text.decls;
      Seq<Expr> compTypes = new BufferSeq<>();
      for (int i = 0; i < decls.length; i++) {
        Expr.Decl decl = decls[i];
        if (decl instanceof Expr.Decl.Direct d) {
          Expr type = elemTypeOf(d.type);
          for (int j = 0; j < d.names.length; j++) {
            compTypes = compTypes.append(type);
          }
        } else if (decl instanceof Expr.Decl.Eqn eq) {
          compTypes = compTypes.append(typeOf(eq.def));
        } else if (decl instanceof Expr.Decl.Inclusion inc) {
          compTypes = compTypes.append(elemTypeOf(inc.schema));
        }
      }
      switch (compTypes.size()) {
      case 0:
        return Env.makePower(Env.makeSignature(new Name[0], new Expr[0]));
      case 1:
        return compTypes.head();
      default:
        return Env.makeProduct(compTypes.toArray(Expr.class));
      }
    } else {
      return elemTypeOf(schema);
    }
  }

  /**
   * Make a signature from a mapping.
   */
  private static Expr.Signature makeSignature(Map<Name, Expr> typeEnv) {
    Name[] names = new Name[typeEnv.size()];
    Expr[] types = new Expr[names.length];
    int i = 0;
    for (Enum<Pair<Name, Expr>> e = typeEnv.enumerate(); e.more(); e.advance()) {
      Pair<Name, Expr> p = e.current();
      names[i] = p.fst;
      types[i] = p.snd;
      i++;
    }
    return Env.makeSignature(names, types);
  }



}
