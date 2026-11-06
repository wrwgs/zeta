package zeta.content.zirp;

import zeta.util.Annotation;
import zeta.util.FatalError;
import zeta.util.Name;

/**
 * A class for visiting expression and predicate terms of the ZIRP
 * abstract syntax. <p>
 *
 * For each term class (including Pizza cases) a visitor method is
 * provided by this class. A visitor returns either null, indicating
 * that no term rewriting has taken place, or a new term, giving the
 * rewritten term. The default implementation of the visitor methods
 * visits subterms of a given term, and reconstruct the term if
 * subterms have been changed. <p>
 *
 * Here is, for example, a visitor which substitutes variables in
 * expressions:
 *
 * <pre>
 * class Substitutor extends TermVisitor {
 *   Map&lt;Name,Expr&gt; theSubs;
 *   Expr visit(Expr.Variable var){
 *     if (theSubs.defines(var.name.name))
 *       return subs.get(var.name.name);
 *     else
 *       return super.visit(var); // DEFAULT TREATMENT (substitute actuals)
 *   }
 * }
 * </pre>
 *
 * As another example, here is a visitor which just samples
 * information, and doesn't changes the term:
 *
 * <pre>
 * class FreeVarSampler extends TermVisitor {
 *   Set&lt;Name&gt; free;      // the free variables
 *   Set&lt;Name&gt; bounded;   // currently locally bounded variables
 *   Expr visit(Expr.Variable var){
 *     if (!bounded.contains(var.name.name))
 *       free = free.include(var.name.name);
 *     return null;
 *   }
 *   Expr visit(Expr.Quantor quant){
 *     visit(quant.matrix);
 *     Set&lt;Name&gt; savedBounded = bounded;
 *     bounded = bounded.includeAll(declaredVariables(quant.matrix));
 *     visit(quant.range);
 *     bounded = savedBounded;
 *     return null;
 *   }
 *   Expr visit(Predicate.Quantor quant){
 *     ...
 *   }
 * }
 * </pre>
 *
 *
 * @version $Id: TermVisitor.pizza,v 1.7 2000/07/06 09:18:24 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * <p>Nothing in this class was ever name-mangled by Pizza: every method is a plain
 * <code>visit</code> overload, so the override chain of the still-binary subclasses
 * (<code>zeta.tools.zchecker.TermChecker$TypeUnifier</code>,
 * <code>$TypeFinalizer</code>, <code>$TypeVarResolver</code>,
 * <code>UnitChecker$0$PutLocatorVisitor</code>, <code>Env$Substitutor</code>) and of the
 * already-ported <code>Type.Substitutor</code> is ordinary virtual dispatch.  The erased
 * descriptors below are therefore load-bearing and are matched one-for-one against the 2002
 * class file -- in particular every case visitor returns the *base* type
 * (<code>Expr</code>, <code>Expr.Decl</code>, <code>Expr.Branch</code>,
 * <code>Predicate</code>, <code>State</code>, <code>Item</code>, <code>LabelExpr</code>,
 * <code>UnitAbsy</code>), never the case type.</p>
 *
 * <p>Pizza's <code>switch (term)</code> over an ADT needed no default; none of the ZIRP bases
 * is sealed (the <code>zeta.tools.*</code> packages are outside this file), so each dispatch
 * switch here carries a <code>default</code> that raises
 * {@link FatalError#unexpectedCase(String)}.  Pizza emitted <code>new Error()</code> on the
 * same arm, so this is the same unreachable behaviour with a better message.</p>
 *
 * <p>Four defects of the 1998 code are preserved deliberately; each is marked PORT NOTE at
 * its site.  All four are present in the 2002 class file -- verified with
 * <code>javap -c</code> -- and all four are reachable from the binary checker, so
 * "fixing" any of them would change the artifacts the oracle pins.</p>
 */
public abstract class TermVisitor {

  // CONFIGURATION

  /**
   * A boolean flag which indicates that this visitor doesn't rewrites
   * terms, that is always returns null. If set, speeds up some of the
   * default visitor implementations.
   */
  protected boolean dontRewrites = false;



  // ANNOTATIONS

  /**
   * Visit annotation, doing some side-effect on annotation.
   * Does nothing by default.
   */
  public void visit(Annotation an) {
    return;
  }

  // NAMES

  public Name visit(Name name) {
    return null;
  }

  // NAME APPLICATIONS

  /**
   * Visit name application.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public NameAppl visit(NameAppl nappl) {
    Name newName = visit(nappl.name);
    Expr[] newActs = visit(nappl.actuals);
    visit(nappl.an);
    if (newName != null || newActs != null) {
      NameAppl newnappl = new NameAppl(
        newName != null ? newName : nappl.name,
        newActs != null ? newActs : nappl.actuals);
      nappl.an.copyInto(newnappl.an);
      return newnappl;
    } else
      return null;
  }

  /**
   * Visit an array of name applications.
   */
  // PORT NOTE -- 1998 defect, preserved.  Unlike every other array visitor in this class
  // (and unlike visit(NameDecl[]) right below it), this one never calls
  // visit(nappls[i]): it copies the element, then tests the *copy* for null and sets
  // `changed` whenever the element was non-null.  So it returns a fresh but element-wise
  // identical array for any non-empty array of non-null elements, and visits nothing.
  // Confirmed in the 2002 class file: the loop body is aaload/aastore with no
  // invokevirtual at all.  It is reachable -- visit(Item.AttributeDef) calls it on
  // `item.name` -- so a subclass's visit(NameAppl) override is silently skipped there.
  // Repairing it would start invoking those overrides (Env$Substitutor,
  // UnitChecker$PutLocatorVisitor are still binary) and would move artifacts; left alone.
  public NameAppl[] visit(NameAppl[] nappls) {
    NameAppl[] n_nappls = new NameAppl[nappls.length];
    boolean changed = false;
    for (int i = 0; i < nappls.length; i++) {
      n_nappls[i] = nappls[i];
      if (n_nappls[i] == null)
        n_nappls[i] = nappls[i];
      else
        changed = true;
    }
    if (changed) return n_nappls; else return null;
  }



  // NAME DECLARATIONS

  /**
   * Visit name declarations.
   */
  public NameDecl visit(NameDecl ndecl) {
    Name newName = visit(ndecl.name);
    visit(ndecl.an);
    if (newName != null) {
      NameDecl newdecl = new NameDecl(newName);
      ndecl.an.copyInto(newdecl.an);
      return  newdecl;
    }
    return null;
  }

  /**
   * Visit an array of name declarations.
   */
  public NameDecl[] visit(NameDecl[] ndecls) {
    NameDecl[] n_ndecls = new NameDecl[ndecls.length];
    boolean changed = false;
    for (int i = 0; i < ndecls.length; i++) {
      n_ndecls[i] = visit(ndecls[i]);
      if (n_ndecls[i] == null)
        n_ndecls[i] = ndecls[i];
      else
        changed = true;
    }
    if (changed) return n_ndecls; else return null;
  }



  // EXPRESSIONS

  /**
   * Visit number literal.
   * By default, visits annotation.
   */
  public Expr visit(Expr.Number expr) {
    visit(expr.an);
    return null;
  }

  /**
   * Visit denotation literal.
   * By default, visits annotation.
   */
  public Expr visit(Expr.Denotation expr) {
    visit(expr.an);
    return null;
  }

  /**
   * Visit variable.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Variable expr) {
    NameAppl nappl = visit(expr.name);
    visit(expr.an);
    if (nappl != null) {
      Expr newExpr = Expr.Variable(nappl);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit tuple.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Tuple expr) {
    Expr[] newExprs = visit(expr.elements);
    visit(expr.an);
    if (newExprs != null) {
      Expr newExpr = Expr.Tuple(newExprs);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit product.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Product expr) {
    Expr[] newExprs = visit(expr.elements);
    visit(expr.an);
    if (newExprs != null) {
      Expr newExpr = Expr.Product(newExprs);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit binding.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Binding expr) {
    // treatment not regular, since Expr.Decl.Eqn[] appears in the absy ...
    if (dontRewrites) {
      for (int i = 0; i < expr.bindings.length; i++) {
        visit(expr.bindings[i].def);
      }
      visit(expr.an);
      return null;
    } else {
      Expr.Decl.Eqn[] newBindings = new Expr.Decl.Eqn[expr.bindings.length];
      boolean change = false;
      for (int i = 0; i < expr.bindings.length; i++) {
        newBindings[i] = new Expr.Decl.Eqn(expr.bindings[i].name,
                                           visit(expr.bindings[i].def));
        expr.bindings[i].an.copyInto(newBindings[i].an);
        if (newBindings[i].def == null)
          newBindings[i].def = expr.bindings[i].def;
        else
          change = true;
      }
      visit(expr.an);
      if (change) {
        Expr newExpr = Expr.Binding(newBindings);
        expr.an.copyInto(newExpr.an);
        return newExpr;
      } else
        return null;
    }
  }

  /**
   * Visit signature.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Signature expr) {
    Expr[] newExprs = visit(expr.types);
    visit(expr.an);
    if (newExprs != null) {
      Expr newExpr = Expr.Signature(expr.names, newExprs);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit unification variable.
   * By default, visits annotation and operand (if bound), and reconstructs
   * the term if the operand has changed.
   */
  public Expr visit(Expr.UniVar expr) {
    if (expr.oper != null) {
      Expr newOper = visit(expr.oper);
      visit(expr.an);
      if (newOper != null) {
        Expr newExpr = Expr.UniVar(expr.name, newOper);
        expr.an.copyInto(newExpr.an);
        return newExpr;
      } else
        return null;
    } else {
      visit(expr.an);
      return null;
    }
  }

  /**
   * Visit display.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Display expr) {
    Expr[] newExprs = visit(expr.elements);
    visit(expr.an);
    if (newExprs != null) {
      Expr newExpr = Expr.Display(newExprs);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit conditional.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Cond expr) {
    Predicate cond = visit(expr.cond);
    Expr thenPath = visit(expr.thenPath);
    Expr elsePath = visit(expr.elsePath);
    visit(expr.an);
    if (cond != null || thenPath != null || elsePath != null) {
      Expr newExpr = Expr.Cond(cond != null ? cond : expr.cond,
                               thenPath != null ? thenPath : expr.thenPath,
                               elsePath != null ? elsePath : expr.elsePath);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit quantor expression.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Quantor expr) {
    Expr matrix = visit(expr.matrix);
    Expr range = visit(expr.range);
    visit(expr.an);
    if (matrix != null || range != null) {
      Expr newExpr = Expr.Quantor(expr.kind,
                                  matrix != null ? matrix : expr.matrix,
                                  range != null ? range : expr.range);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit binding selection.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Select expr) {
    Expr oper = visit(expr.oper);
    NameAppl name = visit(expr.name);
    visit(expr.an);
    if (oper != null || name != null) {
      Expr newExpr = Expr.Select(oper != null ? oper : expr.oper,
                                 name != null ? name : expr.name);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit expression unary.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Unary expr) {
    Expr oper = visit(expr.oper);
    visit(expr.an);
    if (oper != null) {
      Expr newExpr = Expr.Unary(expr.kind, oper);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit expression binary.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Binary expr) {
    Expr left = visit(expr.left);
    Expr right = visit(expr.right);
    visit(expr.an);
    if (left != null || right != null) {
      Expr newExpr = Expr.Binary(expr.kind,
                                 left != null ? left : expr.left,
                                 right != null ? right : expr.right);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit schema text.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.Text expr) {
    Expr.Decl[] decls = visit(expr.decls);
    Predicate[] prop = visit(expr.props);
    visit(expr.an);
    if (decls != null || prop != null) {
      Expr newExpr = Expr.Text(decls != null ? decls : expr.decls,
                               prop != null ? prop : expr.props);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit given type.
   * By default, visits annotation.
   */
  public Expr visit(Expr.GivenType expr) {
    NameDecl name = visit(expr.name);
    visit(expr.an);
    if (name != null) {
      Expr newExpr = new Expr.GivenType(name);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit free type.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Expr visit(Expr.FreeType expr) {
    NameDecl name = visit(expr.name);
    Expr.Branch[] branches = visit(expr.branches);
    visit(expr.an);
    if (name != null || branches != null) {
      Expr newExpr = Expr.FreeType(name != null ? name : expr.name,
                                   branches != null
                                     ? branches : expr.branches);
      expr.an.copyInto(newExpr.an);
      return newExpr;
    } else
      return null;
  }

  /**
   * Visit some expression.
   * Calls the visitor for the according expression subclass.
   */
  public Expr visit(Expr expr) {
    return switch (expr) {
      case Expr.Number n -> visit(n);
      case Expr.Denotation d -> visit(d);
      case Expr.Variable v -> visit(v);
      case Expr.Tuple t -> visit(t);
      case Expr.Product p -> visit(p);
      case Expr.Binding b -> visit(b);
      case Expr.Signature s -> visit(s);
      case Expr.UniVar u -> visit(u);
      case Expr.Display d -> visit(d);
      case Expr.Cond c -> visit(c);
      case Expr.Quantor q -> visit(q);
      case Expr.Select s -> visit(s);
      case Expr.Unary u -> visit(u);
      case Expr.Binary b -> visit(b);
      case Expr.Text t -> visit(t);
      case Expr.GivenType g -> visit(g);
      case Expr.FreeType f -> visit(f);
      default -> throw FatalError.unexpectedCase("expression");
    };
  }

  /**
   * Visit array of expressions.
   * By default, visits elements, and reconstructs
   * the array if some element is changed.
   */
  public Expr[] visit(Expr[] exprs) {
    if (dontRewrites) {
      for (int i = 0; i < exprs.length; i++) {
        visit(exprs[i]);
      }
      return null;
    } else {
      Expr[] newExprs = new Expr[exprs.length];
      boolean change = false;
      for (int i = 0; i < exprs.length; i++) {
        newExprs[i] = visit(exprs[i]);
        if (newExprs[i] == null)
          newExprs[i] = exprs[i];
        else
          change = true;
      }
      return change ? newExprs : null;
    }
  }


  // DECLARATIONS

  /**
   * Visit direct declaration.
   * By default, visits annotation and type expression, and reconstructs
   * the term if subterms are changed.
   */
  public Expr.Decl visit(Expr.Decl.Direct decl) {
    NameDecl[] names = visit(decl.names);
    Expr type = visit(decl.type);
    visit(decl.an);
    if (names != null || type != null) {
      Expr.Decl newDecl = Expr.Decl.Direct(names != null ? names : decl.names,
                                           type != null ? type : decl.type);
      decl.an.copyInto(newDecl.an);
      return newDecl;
    } else
      return null;
  }

  /**
   * Visit equational declaration.
   * By default, visits annotation and defining expression, and reconstructs
   * the term if subterms are changed.
   */
  public Expr.Decl visit(Expr.Decl.Eqn decl) {
    NameDecl name = visit(decl.name);
    Expr def = visit(decl.def);
    visit(decl.an);
    if (name != null || def != null) {
      Expr.Decl newDecl = Expr.Decl.Eqn(name != null ? name : decl.name,
                                        def != null ? def : decl.def);
      decl.an.copyInto(newDecl.an);
      return newDecl;
    } else
      return null;
  }

  /**
   * Visit inclusion declaration.
   * By default, visits annotation and inclusion expression, and reconstructs
   * the term if subterms are changed.
   */
  public Expr.Decl visit(Expr.Decl.Inclusion decl) {
    Expr schema = visit(decl.schema);
    visit(decl.an);
    if (schema != null) {
      Expr.Decl newDecl = Expr.Decl.Inclusion(schema);
      decl.an.copyInto(newDecl.an);
      return newDecl;
    } else
      return null;
  }

  /**
   * Visit some declaration.
   * Calls the visitor for the according declaration case.
   */
  public Expr.Decl visit(Expr.Decl decl) {
    return switch (decl) {
      case Expr.Decl.Direct d -> visit(d);
      case Expr.Decl.Eqn e -> visit(e);
      case Expr.Decl.Inclusion i -> visit(i);
      default -> throw FatalError.unexpectedCase("declaration");
    };
  }

  /**
   * Visit array of declarations.
   * By default, visits elements, and reconstructs
   * the array if some element is changed.
   */
  public Expr.Decl[] visit(Expr.Decl[] decls) {
    if (dontRewrites) {
      for (int i = 0; i < decls.length; i++) {
        visit(decls[i]);
      }
      return null;
    } else {
      Expr.Decl[] newDecls = new Expr.Decl[decls.length];
      boolean change = false;
      for (int i = 0; i < decls.length; i++) {
        newDecls[i] = visit(decls[i]);
        if (newDecls[i] == null)
          newDecls[i] = decls[i];
        else
          change = true;
      }
      return change ? newDecls : null;
    }
  }


  // FREE TYPE BRANCHES

  /**
   * Visit constant branch.
   * By default, visits annotation.
   */
  public Expr.Branch visit(Expr.Branch.Constant branch) {
    NameDecl name = visit(branch.name);
    visit(branch.an);
    if (name != null) {
      Expr.Branch newBranch = new Expr.Branch.Constant(name);
      branch.an.copyInto(newBranch.an);
      return newBranch;
    } else
      return null;
  }

  /**
   * Visit function branch.
   * By default, visits annotation and type expression, and reconstructs
   * the term if subterms are changed.
   */
  public Expr.Branch visit(Expr.Branch.Function branch) {
    NameDecl name = visit(branch.name);
    Expr type = visit(branch.type);
    visit(branch.an);
    if (name != null || type != null) {
      Expr.Branch newBranch = Expr.Branch.Function(
        name != null ? name : branch.name,
        type != null ? type : branch.type);
      branch.an.copyInto(newBranch.an);
      return newBranch;
    } else
      return null;
  }

  /**
   * Visit some branch.
   * Calls the visitor for the according branch case.
   */
  public Expr.Branch visit(Expr.Branch branch) {
    return switch (branch) {
      case Expr.Branch.Constant c -> visit(c);
      case Expr.Branch.Function f -> visit(f);
      default -> throw FatalError.unexpectedCase("free type branch");
    };
  }

  /**
   * Visit array of branches.
   * By default, visits elements, and reconstructs
   * the array if some element is changed.
   */
  public Expr.Branch[] visit(Expr.Branch[] branchs) {
    if (dontRewrites) {
      for (int i = 0; i < branchs.length; i++) {
        visit(branchs[i]);
      }
      return null;
    } else {
      Expr.Branch[] newBranchs = new Expr.Branch[branchs.length];
      boolean change = false;
      for (int i = 0; i < branchs.length; i++) {
        newBranchs[i] = visit(branchs[i]);
        if (newBranchs[i] == null)
          newBranchs[i] = branchs[i];
        else
          change = true;
      }
      return change ? newBranchs : null;
    }
  }



  // PREDICATES

  /**
   * Visit schema reference.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Predicate visit(Predicate.Schema pred) {
    Expr schema = visit(pred.schema);
    visit(pred.an);
    if (schema != null) {
      Predicate newPred = Predicate.Schema(schema);
      pred.an.copyInto(newPred.an);
      return newPred;
    } else
      return null;
  }

  /**
   * Visit element test.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  // PORT NOTE -- 1998 defect, preserved.  The guard reads `elem != null || pred != null`
  // where `set != null` was plainly meant; `pred` is the parameter and is never null, so
  // every Predicate.Test is rebuilt and this visitor never returns null.  Confirmed in the
  // 2002 class file (the second null test loads aload_1, the parameter).  Visible to the
  // callers that test "did anything change": a Test inside a schema text makes
  // visit(Predicate[]) report a change, hence reconstructs its enclosing term.  Changing it
  // would change which terms are rebuilt throughout the checker; left alone.
  public Predicate visit(Predicate.Test pred) {
    Expr elem = visit(pred.elem);
    Expr set = visit(pred.set);
    visit(pred.an);
    if (elem != null || pred != null) {
      Predicate newPred = Predicate.Test(elem != null ? elem : pred.elem,
                                         set != null ? set : pred.set);
      pred.an.copyInto(newPred.an);
      return newPred;
    } else
      return null;
  }

  /**
   * Visit fact.
   * By default, visits annotation.
   */
  public Predicate visit(Predicate.Fact pred) {
    visit(pred.an);
    return null;
  }

  /**
   * Visit predicate unary.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Predicate visit(Predicate.Unary pred) {
    Predicate oper = visit(pred.oper);
    visit(pred.an);
    if (oper != null) {
      Predicate newPred = Predicate.Unary(pred.kind, oper);
      pred.an.copyInto(newPred.an);
      return newPred;
    } else
      return null;
  }

  /**
   * Visit predicate binary.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Predicate visit(Predicate.Binary pred) {
    Predicate left = visit(pred.left);
    Predicate right = visit(pred.right);
    visit(pred.an);
    if (left != null || right != null) {
      Predicate newPred =
        Predicate.Binary(pred.kind,
                         left != null ? left : pred.left,
                         right != null ? right : pred.right);
      pred.an.copyInto(newPred.an);
      return newPred;
    } else
      return null;
  }

  /**
   * Visit predicate quantor.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Predicate visit(Predicate.Quantor pred) {
    Expr matrix = visit(pred.matrix);
    Predicate range = visit(pred.range);
    visit(pred.an);
    if (matrix != null || range != null) {
      Predicate newPred =
        Predicate.Quantor(pred.kind,
                          matrix != null ? matrix : pred.matrix,
                          range != null ? range : pred.range);
      pred.an.copyInto(newPred.an);
      return newPred;
    } else
      return null;
  }

  /**
   * Visit some predicate.
   * Calls the visitor for the according predicate case.
   */
  public Predicate visit(Predicate pred) {
    return switch (pred) {
      case Predicate.Schema s -> visit(s);
      case Predicate.Test t -> visit(t);
      case Predicate.Fact f -> visit(f);
      case Predicate.Unary u -> visit(u);
      case Predicate.Binary b -> visit(b);
      case Predicate.Quantor q -> visit(q);
      default -> throw FatalError.unexpectedCase("predicate");
    };
  }

  /**
   * Visit array of predicate.
   * By default, visits elements, and reconstructs
   * the array if some element is changed.
   */
  public Predicate[] visit(Predicate[] preds) {
    if (dontRewrites) {
      for (int i = 0; i < preds.length; i++) {
        visit(preds[i]);
      }
      return null;
    } else {
      Predicate[] newPreds = new Predicate[preds.length];
      boolean change = false;
      for (int i = 0; i < preds.length; i++) {
        newPreds[i] = visit(preds[i]);
        if (newPreds[i] == null)
          newPreds[i] = preds[i];
        else
          change = true;
      }
      return change ? newPreds : null;
    }
  }

  // STATES

  /**
   * Visit some state.
   * Calls the visitor for the according state case.
   */
  public State visit(State state) {
    return switch (state) {
      case State.BasicState b -> visit(b);
      case State.RefState r -> visit(r);
      case State.SuperState s -> visit(s);
      default -> throw FatalError.unexpectedCase("state");
    };
  }

  /**
   * Visit basic state. Visits annotation and returns null.
   */
  public State visit(State.BasicState state) {
    NameDecl name = visit(state.name);
    visit(state.an);
    if (name != null) {
      State newState = new State.BasicState(name);
      state.an.copyInto(newState.an);
      return newState;
    } else
      return null;
  }

  /**
   * Visit state reference. Visits annotation and returns null.
   */
  public State visit(State.RefState state) {
    NameDecl name = visit(state.name);
    visit(state.an);
    if (name != null) {
      State newState = new State.RefState(name);
      state.an.copyInto(newState.an);
      return newState;
    } else
      return null;
  }

  /**
   * Visit a super-state.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public State visit(State.SuperState state) {
    NameDecl name = visit(state.name);
    State[] substates = visit(state.substates);
    NameDecl[] connectors = visit(state.connectors);
    Transition[] transitions = visit(state.transitions);
    visit(state.an);
    if (name != null || substates != null || connectors != null
        || transitions != null) {
      State newState =
        State.SuperState(name != null ? name : state.name,
                         state.kind,
                         substates != null ? substates : state.substates,
                         connectors != null ? connectors : state.connectors,
                         transitions != null ? transitions :
                                                   state.transitions);
      state.an.copyInto(newState.an);
      return newState;
    } else
      return null;
  }

  /**
   * Visit array of states.
   * By default, visits elements, and reconstructs
   * the array if some element has changed.
   */
  public State[] visit(State[] states) {
    if (dontRewrites) {
      for (int i = 0; i < states.length; i++) {
        visit(states[i]);
      }
      return null;
    } else {
      State[] newStates = new State[states.length];
      boolean change = false;
      for (int i = 0; i < states.length; i++) {
        newStates[i] = visit(states[i]);
        if (newStates[i] == null)
          newStates[i] = states[i];
        else
          change = true;
      }
      return change ? newStates : null;
    }
  }

  // TRANSITIONS

  /**
   * Visit a transition.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Transition visit(Transition trans) {
    NameAppl source = visit(trans.source);
    NameAppl target = visit(trans.target);
    LabelExpr label = visit(trans.label);
    visit(trans.an);
    if (source != null || target != null || label != null) {
      Transition newTrans =
        new Transition(source != null ? source : trans.source,
                       target != null ? target : trans.target,
                       label != null ? label : trans.label);
      trans.an.copyInto(newTrans.an);
      return newTrans;
    } else
      return null;
  }

  /**
   * Visit array of transitions.
   * By default, visits elements, and reconstructs
   * the array if some element has changed.
   */
  public Transition[] visit(Transition[] transitions) {
    if (dontRewrites) {
      for (int i = 0; i < transitions.length; i++) {
        visit(transitions[i]);
      }
      return null;
    } else {
      Transition[] newTransitions = new Transition[transitions.length];
      boolean change = false;
      for (int i = 0; i < transitions.length; i++) {
        newTransitions[i] = visit(transitions[i]);
        if (newTransitions[i] == null)
          newTransitions[i] = transitions[i];
        else
          change = true;
      }
      return change ? newTransitions : null;
    }
  }


  // LABEL EXPRESSIONS

  /**
   * Visit some label expression.
   * Calls the visitor for the according case.
   */
  public LabelExpr visit(LabelExpr label) {
    return switch (label) {
      case LabelExpr.Guarded g -> visit(g);
      case LabelExpr.Temporal t -> visit(t);
      case LabelExpr.Reference r -> visit(r);
      default -> throw FatalError.unexpectedCase("label expression");
    };
  }

  /**
   * Visit a guarded label expression.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public LabelExpr visit(LabelExpr.Guarded g) {
    Predicate guard = visit(g.guard);
    Predicate action = visit(g.action);
    visit(g.an);
    if (guard != null || action != null) {
      LabelExpr ng = LabelExpr.Guarded(
                       guard != null ? guard : g.guard,
                       action != null ? action : g.action
                     );
      g.an.copyInto(ng.an);
      return ng;
    } else
      return null;
  }

  /**
   * Visit a temporal label expression.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public LabelExpr visit(LabelExpr.Temporal l) {
    Predicate p = visit(l.predicate);
    visit(l.an);
    if (p != null) {
      LabelExpr nl = LabelExpr.Temporal(p);
      l.an.copyInto(nl.an);
      return nl;
    } else
      return null;
  }

  /**
   * Visit a transition reference label expression.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  // PORT NOTE -- 1998 defect, preserved.  The annotation of the old label is copied into
  // `nap.an` -- the annotation of the freshly visited *name application* -- and not into
  // `nl.an`, the new label, as every sibling visitor does.  So a rewritten
  // LabelExpr.Reference loses its own annotation and the new NameAppl gains it.  Confirmed in
  // the 2002 class file (offsets 31-32 load aload_2, the NameAppl).  Reaches the artifacts
  // through any annotation slot on a reaction label; left alone.
  public LabelExpr visit(LabelExpr.Reference l) {
    NameAppl nap = visit(l.name);
    visit(l.an);
    if (nap != null) {
      LabelExpr nl = LabelExpr.Reference(nap);
      l.an.copyInto(nap.an);
      return nl;
    } else
      return null;
  }


  /**
   * Visit array of label expressions.
   * By default, visits elements, and reconstructs
   * the array if some element has changed.
   */
  public LabelExpr[] visit(LabelExpr[] labelExprs) {
    if (dontRewrites) {
      for (int i = 0; i < labelExprs.length; i++) {
        visit(labelExprs[i]);
      }
      return null;
    } else {
      LabelExpr[] newLabelExprs = new LabelExpr[labelExprs.length];
      boolean change = false;
      for (int i = 0; i < labelExprs.length; i++) {
        newLabelExprs[i] = visit(labelExprs[i]);
        if (newLabelExprs[i] == null)
          newLabelExprs[i] = labelExprs[i];
        else
          change = true;
      }
      return change ? newLabelExprs : null;
    }
  }




  // ITEMS

  /**
   * Visit some item.
   * Calls the visitor for the according item case.
   */
  public Item visit(Item item) {
    return switch (item) {
      case Item.AxiomaticDef a -> visit(a);
      case Item.FixityDef f -> visit(f);
      case Item.EmbeddedText e -> visit(e);
      case Item.ConfigDef c -> visit(c);
      case Item.StatechartDef s -> visit(s);
      case Item.TransitionDef t -> visit(t);
      case Item.StaticReactionDef s -> visit(s);
      case Item.AttributeDef a -> visit(a);
      case Item.EnrichDef e -> visit(e);
      default -> throw FatalError.unexpectedCase("item");
    };
  }

  /**
   * Visit axiomatic definition.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Item visit(Item.AxiomaticDef item) {
    NameDecl[] formals = visit(item.formals);
    Expr def = visit(item.def);
    visit(item.an);
    if (formals != null || def != null) {
      Item newItem =
        Item.AxiomaticDef(formals != null ? formals : item.formals,
                          def != null ? def : item.def);
      item.an.copyInto(newItem.an);
      return newItem;
    } else
      return null;
  }

  /**
   * Visit fixity definition.
   * By default, visits annotation and returns null.
   */
  public Item visit(Item.FixityDef item) {
    visit(item.an);
    return null;
  }

  /**
   * Visit text item.
   * By default, visits annotation and returns null.
   */
  public Item visit(Item.EmbeddedText item) {
    visit(item.an);
    return null;
  }

  /**
   * Visit configuration item.
   * By default, visits annotation and returns null.
   */
  public Item visit(Item.ConfigDef item) {
    visit(item.an);
    return null;
  }

  /**
   * Visit statechart item.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Item visit(Item.StatechartDef item) {
    State root = visit(item.root);
    visit(item.an);
    if (root != null) {
      Item newItem =
        Item.StatechartDef(root);
      item.an.copyInto(newItem.an);
      return newItem;
    } else
      return null;
  }

  /**
   * Visit transition item.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Item visit(Item.TransitionDef item) {
    NameDecl name = visit(item.name);
    LabelExpr[] reactions = visit(item.reactions);
    visit(item.an);
    if (name != null || reactions != null) {
      Item newItem =
        Item.TransitionDef(name != null ? name : item.name,
                           reactions != null ? reactions : item.reactions);
      item.an.copyInto(newItem.an);
      return newItem;
    } else
      return null;
  }

  /**
   * Visit static reaction item.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Item visit(Item.StaticReactionDef item) {
    NameDecl name = visit(item.name);
    LabelExpr[] reactions = visit(item.reactions);
    visit(item.an);
    if (name != null || reactions != null) {
      Item newItem =
        Item.StaticReactionDef(name != null ? name : item.name,
                               reactions != null ? reactions : item.reactions);
      item.an.copyInto(newItem.an);
      return newItem;
    } else
      return null;
  }

  /**
   * Visit attribute item.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Item visit(Item.AttributeDef item) {
    NameAppl[] name = visit(item.name);
    visit(item.an);
    if (name != null) {
      Item newItem = Item.AttributeDef(item.attrib, name);
      item.an.copyInto(newItem.an);
      return newItem;
    } else
      return null;
  }


  /**
   * Visit enrichment item.
   * By default, visits annotation and subterms, and reconstructs
   * the term if subterms are changed.
   */
  public Item visit(Item.EnrichDef item) {
    NameAppl nap = visit(item.name);
    visit(item.an);
    if (nap != null) {
      Item newItem =
        Item.EnrichDef(nap);
      item.an.copyInto(newItem.an);
      return newItem;
    } else
      return null;
  }


  public Item[] visit(Item[] items) {
    boolean changed = false;
    Item[] n_items = new Item[items.length];
    for (int i = 0; i < items.length; i++) {
      n_items[i] = visit(items[i]);
      if (n_items[i] == null)
        n_items[i] = items[i];
      else
        changed = true;
    }
    if (changed) return n_items; else return null;
  }


    // UNITABSY

  // PORT NOTE -- asymmetry of the 1998 code, preserved: this method visits `unitabsy.an`,
  // and visit(UnitAbsy.Section) visits `sec.an` a *second* time, while
  // visit(UnitAbsy.ClassDef) does not visit the annotation at all.  Both confirmed against
  // the 2002 class file.  visit(Annotation) is a side-effecting hook (PutLocatorVisitor in
  // the binary checker uses it), so the repeat is observable; left alone.
  public UnitAbsy visit(UnitAbsy unitabsy) {
    visit(unitabsy.an);
    UnitAbsy newUnitAbsy = switch (unitabsy) {
      case UnitAbsy.ClassDef cd -> visit(cd);
      case UnitAbsy.Section sec -> visit(sec);
      default -> throw FatalError.unexpectedCase("unit abstract syntax");
    };
    if (newUnitAbsy == null)
      return null;
    else {
      unitabsy.an.copyInto(newUnitAbsy.an);
      return newUnitAbsy;
    }
  }

  public UnitAbsy visit(UnitAbsy.ClassDef cdef) {
    Item[] items = visit(cdef.items);
    if (items == null)
      return null;
    else
      return new UnitAbsy.ClassDef(cdef.name,
                                   cdef.parents,
                                   cdef.formals,
                                   items);
  }

  public UnitAbsy visit(UnitAbsy.Section sec) {
    Item[] items = visit(sec.items);
    visit(sec.an);
    if (items == null)
      return null;
    else
      return new UnitAbsy.Section(sec.name,
                                  sec.parents,
                                  items);
  }
}
