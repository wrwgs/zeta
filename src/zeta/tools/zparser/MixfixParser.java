package zeta.tools.zparser;

import java.util.Stack;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Functions;
import zeta.agl.HashMap;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.content.text.Lexem;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Fixity;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.Predicate;
import zeta.content.zirp.UnitEnv;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.util.FatalError;
import zeta.util.Name;
import zeta.util.Term;

/**
 * The Mixfix parser.
 *
 * @version $Id: MixfixParser.pizza,v 1.4 1998/09/13 15:24:13 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p><b>Imports.</b> The source's <code>import Fixity.*;</code> was a Pizza class-case import
 * of <code>zeta.content.zirp.Fixity</code>'s nested <code>Component</code>; the cases are
 * written <code>Fixity.Component.Operand</code> etc. here.</p>
 *
 * <p><b>ADTs.</b> <code>CFixity</code> is a single-case class whose case name equals the class
 * name: fields, a public constructor and a factory of the same name, no tag.  <code>Tree</code>
 * has five cases with the 2002 tags (read with <code>harness/adt_tags.py</code>), declared in
 * the source order.  Its <code>toString</code> and <code>getLocator</code> named every case,
 * so Pizza gave them a default throwing <code>java.lang.Error</code>; here that default is
 * <code>FatalError.unexpectedCase</code>, equally unreachable.</p>
 *
 * <p><b>Shape changes</b> (none visible outside this package, and nothing outside it refers to
 * this class at all):</p>
 * <ul>
 * <li><code>CFixity.reducer</code> was Pizza's erased <code>pizza.support.Closure</code> for
 *     the arrow type <code>(CFixity, Locator, Seq&lt;Tree&gt;)-&gt;Tree</code>; it is a
 *     <code>Functions.Fun3</code> now, and the five reducers are passed as method references.
 *     <code>CFixity</code> is never rendered (its <code>Term</code> field walk is reached only
 *     from commented-out debugging code), so the type change is not observable.</li>
 * <li><code>Parser</code>'s two private stacks were <code>pizza.util.Stack</code>; they are
 *     <code>java.util.Stack</code>, which has the same LIFO behaviour and, like Pizza's, admits
 *     <code>null</code> -- which matters, because <code>binRelReducer</code> returns
 *     <code>null</code> when the right operand is a predicate with no left operand, and that
 *     <code>null</code> is pushed.  The debugging helper <code>dumpStack</code> follows.</li>
 * <li>The Pizza closure classes, the lifted <code>$closure$...</code> methods and the
 *     <code>access$...</code> accessors are gone; nothing outside this class named them.</li>
 * </ul>
 *
 * <p><b>Order.</b> The priority table is built exactly as before: builtins first, then the user
 * fixities in the order <code>UnitEnv.getFixities</code> returns them, appended per priority to
 * a <code>BufferSeq</code>, then sorted by priority.  Priorities are the keys of a map, so they
 * are distinct and the (unstable) <code>ArraySeq</code> sort has no ties to order.
 * <code>Map&lt;int,...&gt;</code> is <code>Map&lt;Integer,...&gt;</code>; Pizza boxed with
 * <code>java.lang.Integer</code> too.  The parse itself is unchanged, including the order in
 * which fixities are tried and the order diagnostics are added.</p>
 *
 * <p><b>Null on the stack.</b> Where Pizza did <code>switch (right.top())</code> a
 * <code>null</code> top raised <code>NullPointerException</code> from the tag read; the port
 * keeps a <code>switch</code> statement at those two sites, which raises the same exception on
 * <code>null</code>.  (<code>AmbigiousException</code> is declared and caught but never thrown,
 * in the 2002 class files as in the source.)</p>
 */
class MixfixParser {

  /**
   * Internal class for representing compiled information about fixities.
   */
  static public class CFixity extends Term {
    public Name name;
    public int prio;
    public boolean isGeneric;
    public Fixity.Component[] components;
    public Functions.Fun3<CFixity, Locator, Seq<Tree>, Tree> reducer;

    public CFixity(Name name,
                   int prio,
                   boolean isGeneric,
                   Fixity.Component[] components,
                   Functions.Fun3<CFixity, Locator, Seq<Tree>, Tree> reducer){
      this.name = name;
      this.prio = prio;
      this.isGeneric = isGeneric;
      this.components = components;
      this.reducer = reducer;
    }

    /** Pizza's generated case factory. */
    public static CFixity CFixity(Name name,
                                  int prio,
                                  boolean isGeneric,
                                  Fixity.Component[] components,
                                  Functions.Fun3<CFixity, Locator, Seq<Tree>, Tree> reducer){
      return new CFixity(name, prio, isGeneric, components, reducer);
    }
  }

  /**
   * Class for holding syntax for mixfix parser.
   */
  static public class Tree extends Term {

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_KEYWORD = 1;
    private static final int TAG_OPERAND = 2;
    private static final int TAG_OPERAND_LIST = 3;
    private static final int TAG_PREDICATE_OPERAND = 4;
    private static final int TAG_CART = 5;

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$tools$zparser$MixfixParser$Tree$$tag;

    Tree(int tag){
      this.zeta$tools$zparser$MixfixParser$Tree$$tag = tag;
    }

    /** A keyword. */
    public static class Keyword extends Tree {
      public Name name;
      public Locator locator;
      public Keyword(Name name, Locator locator){
        super(TAG_KEYWORD);
        this.name = name;
        this.locator = locator;
      }
    }

    /** A expression operand. */
    public static class Operand extends Tree {
      public Expr expr;
      public Operand(Expr expr){
        super(TAG_OPERAND);
        this.expr = expr;
      }
    }

    /** A operand list. */
    public static class OperandList extends Tree {
      public Name lname;
      public Name rname;
      public Expr expr;
      public OperandList(Name lname, Name rname, Expr expr){
        super(TAG_OPERAND_LIST);
        this.lname = lname;
        this.rname = rname;
        this.expr = expr;
      }
    }

    /** A reduced predicate operand. */
    public static class PredicateOperand extends Tree {
      public Predicate pred;
      public PredicateOperand(Predicate pred){
        super(TAG_PREDICATE_OPERAND);
        this.pred = pred;
      }
    }

    /** An unreduced cartesian product. For internal use only. */
    public static class Cart extends Tree {
      public Seq<Expr> opers;
      public Cart(Seq<Expr> opers){
        super(TAG_CART);
        this.opers = opers;
      }
    }

    /** Pizza's generated case factory. */
    public static Tree Keyword(Name name, Locator locator){
      return new Keyword(name, locator);
    }

    /** Pizza's generated case factory. */
    public static Tree Operand(Expr expr){
      return new Operand(expr);
    }

    /** Pizza's generated case factory. */
    public static Tree OperandList(Name lname, Name rname, Expr expr){
      return new OperandList(lname, rname, expr);
    }

    /** Pizza's generated case factory. */
    public static Tree PredicateOperand(Predicate pred){
      return new PredicateOperand(pred);
    }

    /** Pizza's generated case factory. */
    public static Tree Cart(Seq<Expr> opers){
      return new Cart(opers);
    }

    /** Return expression represented by tree, or null if none.
        Reduces cartesian products. */
    Expr getExpr(){
      if (this instanceof Operand operand){
        return operand.expr;
      } else if (this instanceof Cart cart){
        Seq<Expr> opers = cart.opers;
        Expr res = Expr.Product(opers.toArray(Expr.class));
        Locator.put(res.an,
                   ItemParser.upto(Locator.get(opers.at(0).an),
                                Locator.get(
                                  opers.at(opers.size()-1).an)));
        return res;
      } else {
        return null;
      }
    }

    /** Return predicate represented by tree.
        Converts an expression into a schema reference if necessary. */
    Predicate getPredicate(){
      if (this instanceof PredicateOperand operand){
        return operand.pred;
      } else {
        Expr expr = getExpr();
        if (expr != null){
          Predicate res = Predicate.Schema(expr);
          Locator.put(res.an, Locator.get(expr.an));
          return res;
        } else
          return null;
      }
    }

    /** Convert tree into string. */
    public String toString(){
      if (this instanceof Keyword keyword){
        return keyword.name.toString();
      } else if (this instanceof Operand || this instanceof PredicateOperand){
        return "_";
      } else if (this instanceof OperandList list){
        return list.lname + ",," + list.rname;
      } else if (this instanceof Cart cart){
        Seq<Expr> opers = cart.opers;
        StringBuffer res = new StringBuffer("_");
        for (int i = 1; i < opers.size(); i++){
          res.append(Lexem.Cross.toString());
          res.append("_");
        }
        return res.toString();
      } else {
        throw FatalError.unexpectedCase("MixfixParser.Tree.toString");
      }
    }

    /* Get locator of tree. */
    Locator getLocator(){
      if (this instanceof Keyword keyword){
        return keyword.locator;
      } else if (this instanceof Operand operand){
        return Locator.get(operand.expr.an);
      } else if (this instanceof OperandList list){
        return Locator.get(list.expr.an);
      } else if (this instanceof PredicateOperand operand){
        return Locator.get(operand.pred.an);
      } else if (this instanceof Cart cart){
        Seq<Expr> opers = cart.opers;
        return ItemParser.upto(Locator.get(opers.at(0).an),
                            Locator.get(opers.at(opers.size()-1).an));
      } else {
        throw FatalError.unexpectedCase("MixfixParser.Tree.getLocator");
      }
    }

  }


  /**
   * Array of array of compiled fixities with increasing priority.
   */
  private final CFixity[][] fixities;

  /**
   * Diagnostics produced during mixfix parsing.
   */
  private Diag diag = new Diag();

  /**
   * Create a new mixfix parser. This operation is relative expansive
   * since it does some calculations in advance, and hence should be
   * performed only once for a parse job.
   * @param fixities  the user-defined fixities to consider.
   */
  MixfixParser(UnitEnv.FixityInfo[] fixities){
    Map<Integer, Seq<CFixity>> map = new HashMap<>();

    // add builtin fixities
    map = add( // juxtaposed function application
            map,
            new CFixity(
                  null,
                  Fixity.applyPrio,
                  false,
                  new Fixity.Component[]{
                    Fixity.Component.Operand(Fixity.applyPrio),
                    Fixity.Component.Operand(Fixity.applyPrio+1) },
                  MixfixParser::juxtaposedReducer
                )
          );
    map = add( // powerset
            map,
            new CFixity(
                  null,
                  Fixity.powerPrio,
                  false,
                  new Fixity.Component[]{
                    Fixity.Component.Keyword(
                      new Name(Lexem.Power.toString())),
                    Fixity.Component.Operand(Fixity.powerPrio) },
                                                     // CHECKME: +1?
                  MixfixParser::powerReducer
                )
          );
    map = add( // cartesian product
            map,
            new CFixity(
                  null,
                  Fixity.cartPrio,
                  false,
                  new Fixity.Component[]{
                    Fixity.Component.Operand(Fixity.cartPrio+1),
                    Fixity.Component.Keyword(
                      new Name(Lexem.Cross.toString())),
                    Fixity.Component.Operand(Fixity.cartPrio) },
                  MixfixParser::cartReducer
                )
          );


    // add user-defined fixities
    for (int i = 0; i < fixities.length; i++){
      Fixity f = fixities[i].fixity;
      CFixity cf = null;
      if (f.prio < 0 && f.components.length > 1){
        if (f.components[0] instanceof Fixity.Component.Operand
            && f.components[f.components.length-1] instanceof Fixity.Component.Operand){
          // binary relation
          cf = new CFixity(
                     f.makeName(),
                     -1,
                     false,
                     new ArraySeq<Fixity.Component>(f.components)
                                 .subseq(0, f.components.length-1)
                                 .append(Fixity.Component.Operand(-1))
                                 .toArray(Fixity.Component.class),
                     MixfixParser::binRelReducer
                   );
        }
      }
      if (cf == null){
        Functions.Fun3<CFixity, Locator, Seq<Tree>, Tree> reducer =
          f.prio < 0 ? MixfixParser::relReducer : MixfixParser::funReducer;
        cf = new CFixity(
                   f.makeName(),
                   f.prio,
                   f.isGeneric,
                   f.components,
                   reducer
                 );
      }
      map = add(map, cf);
    }
    // System.out.println("fixity map: ");
    // System.out.println(map);
    this.fixities =
      ArraySeq.from(map)
              .sort((Pair<Integer, Seq<CFixity>> p1, Pair<Integer, Seq<CFixity>> p2) ->
                      p1.fst - p2.fst)
              .map((Pair<Integer, Seq<CFixity>> p) -> p.snd.toArray(CFixity.class))
              .toArray(CFixity[].class);
  }

  /**
   * Add a fixity to priority mapping.
   */
  private static Map<Integer, Seq<CFixity>> add(Map<Integer, Seq<CFixity>> map,
                                                CFixity f){
    if (map.defines(f.prio)){
      return map.define(f.prio, map.get(f.prio).append(f));
    } else {
      return map.define(f.prio, new BufferSeq<CFixity>().append(f));
    }
  }

  /**
   * Parse the given forest as predicate.
   * Returns null if parsing wasn't successful. Diagnostics
   * can be retrieved with getAndClearDiag().
   */
  Predicate predicate(Tree[] forest){
    Tree tree = new Parser().parse(Fixity.relationPrio, new ArraySeq<Tree>(forest));
    if (tree == null) return null;
    return tree.getPredicate();
  }

  /**
   * Parse the given forest as expression.
   * Returns null if parsing wasn't successfull. Diagnostics
   * can be retrieved with getAndClearDiag().
   */
  Expr expr(Tree[] forest){
    Tree tree = new Parser().parse(Fixity.minExprPrio, new ArraySeq<Tree>(forest));
    if (tree == null) return null;
    return tree.getExpr();
  }

  /**
   * Get and clear accumulated diagnostics.
   */
  public Diag getAndClearDiag(){
    Diag d = diag;
    diag = new Diag();
    return d;
  }


  /**
   * The parser class.
   */
  class Parser {

    private Stack<Tree> left = new Stack<>();
    private Stack<Tree> right = new Stack<>();
    int rightCnt = 0;

    /**
     * Parse the given forest at priority and try to reduce it to a tree.
     * Return null and produce diagnostics if parsing fails.
     */
    Tree parse(int prio, Seq<Tree> forest) {
      for (int i = forest.size()-1; i >= 0; i--){
        right.push(forest.at(i));
        rightCnt++;
      }
      try {
        parse(prio);
        Tree current = right.pop();
        rightCnt--;
        if (rightCnt == 0 &&
            (current instanceof Tree.Operand ||
             current instanceof Tree.PredicateOperand ||
             current instanceof Tree.Cart))
          return current;
        else {
          Seq<Tree> unparsable = new BufferSeq<Tree>().append(current);
          while (!right.isEmpty()){
            unparsable = unparsable.append(right.pop());
          }
          diag =
            diag.add(locator(unparsable),
                     Diag.Category.Error,
                     Format.describe("cannot reduce mixfix expression",
                                   "unreduced expression",
                                   showForest(unparsable)));
          return null;
        }
      }
      catch (AmbigiousException e){
        diag =
          diag.add(
            locator(forest),
            Diag.Category.Error,
            Format.describe("ambigious mixfix expression",
                            "pattern", showForest(forest),
                            "matches",
                            Format.beneath(
                              e.cands
                               .map(
                                 (Pair<CFixity, Pair<Tree, Seq<Tree>>> cand) -> {
                                   Pair<Tree, Seq<Tree>> alt = cand.snd;
                                   return
                                     Format.beneath(
                                       showFixity(cand.fst),
                                       Format.space(3),
                                       Format.indent(
                                         alt.fst
                                             .getLocator()
                                             .toFormat(new FormatInfo())
                                       )
                                     );
                                 }
                               )
                            )
                           )
          );
        return null;
      }
    }

    private void parse(int prio) throws AmbigiousException {
      int i = fixities.length-1;
      while (i >= 0 && fixities[i][0].prio >= prio){
        if (rightCnt == 1 && !(right.peek() instanceof Tree.OperandList))
          return;
        int j = 0;
        while (j < fixities[i].length){
          if (tryFixity(fixities[i][j]))
            j = 0;
          else
            j++;
        }
        i--;
      }
    }

    private boolean tryFixity(CFixity fixity) throws AmbigiousException {
      // System.out.println("trying " + fixity);
      // System.out.println("<<<<<<");
      // dumpStack(left);
      // System.out.println(">>>>>>");
      // dumpStack(right);
      Fixity.Component[] comps = fixity.components;
      for (int i = 0; i < comps.length; i++){
        if (right.isEmpty()) {
          retract(i);
          return false;
        }
        Fixity.Component comp = comps[i];
        if (comp instanceof Fixity.Component.Operand operand){
          if (i > 0){
            parse(operand.prio);
          }
          // a switch statement, so that a null top throws as Pizza's tag read did
          switch (right.peek()) {
            case Tree.Operand t -> { }
            case Tree.Cart t -> { }
            case Tree.PredicateOperand t -> { }
            default -> {
              retract(i);
              return false;
            }
          }
          left.push(right.pop());
          rightCnt--;
        } else if (comp instanceof Fixity.Component.Keyword keyword){
          // a switch statement, so that a null top throws as Pizza's tag read did
          switch (right.peek()) {
            case Tree.Keyword t -> {
              if (!keyword.name.equals(t.name)){
                retract(i);
                return false;
              }
            }
            default -> {
              retract(i);
              return false;
            }
          }
          left.push(right.pop());
          rightCnt--;
        } else if (comp instanceof Fixity.Component.OperandList list){
          switch (right.peek()) {
            case Tree.OperandList t -> {
              if (!list.lname.equals(t.lname) || !list.rname.equals(t.rname)) {
                retract(i);
                return false;
              }
            }
            default -> {
              retract(i);
              return false;
            }
          }
          left.push(right.pop());
          rightCnt--;
        } else {
          throw FatalError.unexpectedCase("MixfixParser.Parser.tryFixity");
        }
      }
      // success, reduce
      Seq<Tree> operands = new BufferSeq<>();
      Locator rightOrig = left.peek().getLocator();
      Locator leftOrig = null;
      for (int i = comps.length; i > 0; i--){
        Tree tree = left.pop();
        if (i == 1) leftOrig = tree.getLocator();
        if (tree instanceof Tree.Operand || tree instanceof Tree.PredicateOperand
            || tree instanceof Tree.Cart){
          operands = operands.prepend(tree);
        } else if (tree instanceof Tree.OperandList list){
          operands = operands.prepend(Tree.Operand(list.expr));
        }
      }
      right.push(fixity.reducer.apply(fixity,
                                      ItemParser.upto(leftOrig, rightOrig),
                                      operands));
      rightCnt++;
      // System.out.println("success: >>>>>>");
      // dumpStack(right);
      return true;
    }

    private void retract(int i){
      rightCnt += i;
      while (i-- > 0) right.push(left.pop());
    }


  }

  /**
   * For debugging.
   */
  static void dumpStack(Stack<Tree> stack){
    if (!stack.isEmpty()){
      Tree t = stack.pop();
      System.out.println(t);
      dumpStack(stack);
      stack.push(t);
    }
  }


  /**
   * The standard function reducer.
   */
  private static
  Tree funReducer(CFixity fixity, Locator locator, Seq<Tree> operands){
    Expr res;
    if (fixity.isGeneric){
      NameAppl nap = new NameAppl(fixity.name, toExprArray(operands));
      Locator.put(nap.an, locator);
      res = new Expr.Variable(nap);
      Locator.put(res.an, locator);
    } else {
      NameAppl nap = new NameAppl(fixity.name, new Expr[0]);
      Locator.put(nap.an, locator);
      Expr var = new Expr.Variable(nap);
      Locator.put(var.an, locator);
      Expr[] elems = toExprArray(operands);
      if (elems.length == 0)
        res = var;
      else {
        Expr oper;
        if (elems.length == 1) {
          oper = elems[0];
        } else {
          // CHECKME: no opers?
          oper = Expr.Tuple(elems);
          Locator.put(oper.an, locator);
        }
        res = Expr.Binary(Expr.BinaryKind.Apply, var, oper);
        Locator.put(res.an, locator);
      }
    }
    return Tree.Operand(res);
  }


  /**
   * The standard relation reducer.
   */
  private static
  Tree relReducer(CFixity fixity, Locator locator, Seq<Tree> operands){
    NameAppl nap = new NameAppl(fixity.name, new Expr[0]);
    Locator.put(nap.an, locator);
    Expr var = new Expr.Variable(nap);
    Locator.put(var.an, locator);
    Expr[] elems = toExprArray(operands);
    Expr oper;
    if (elems.length == 1){
      oper = elems[0];
    } else {
      // CHECKME: no opers?
      oper = Expr.Tuple(elems);
      Locator.put(oper.an, locator);
    }
    Predicate res = Predicate.Test(oper, var);
    Locator.put(res.an, locator);
    return new Tree.PredicateOperand(res);
  }

  /**
   * The binary relation reducer.
   */
  private static
  Tree binRelReducer(CFixity fixity, Locator locator, Seq<Tree> operands){
    NameAppl nap = new NameAppl(fixity.name, new Expr[0]);
    Locator.put(nap.an, locator);
    Expr var = new Expr.Variable(nap);
    Locator.put(var.an, locator);
    Expr rightOper;
    Predicate rightPred;
    Tree last = operands.at(operands.size()-1);
    if (last instanceof Tree.PredicateOperand operand){
      Predicate pred = operand.pred;
      // _ R1 (_ R2 _ ...)
      rightOper = getLeftOperand(pred);
      // PORT NOTE: the null returned here is pushed onto the parser's right stack by
      // tryFixity, as in the original; see the class comment.
      if (rightOper == null) return null;
      rightPred = pred;
    } else if (last instanceof Tree.Operand operand){
      rightOper = operand.expr;
      rightPred = null;
    } else {
      throw FatalError.unexpectedCase();
    }
    Expr[] elems = toExprArray(operands.subseq(0,operands.size()-1)
                                       .append(Tree.Operand(rightOper)));
    Locator loc = ItemParser.upto(Locator.get(elems[0].an),
                               Locator.get(elems[elems.length-1].an));
    Expr oper = Expr.Tuple(elems);
    Locator.put(oper.an, loc);
    Predicate pred = Predicate.Test(oper, var);
    Locator.put(pred.an, loc);
    if (rightPred != null){
      loc = ItemParser.upto(Locator.get(pred.an),
                          Locator.get(rightPred.an));
      pred = Predicate.Binary(Predicate.BinaryKind.And,
                              pred, rightPred);
      Locator.put(pred.an, loc);
    }
    return Tree.PredicateOperand(pred);
  }

  private static Expr getLeftOperand(Predicate pred){
    if (pred instanceof Predicate.Binary binary
        && binary.kind == Predicate.BinaryKind.And){
      return getLeftOperand(binary.left);
    } else if (pred instanceof Predicate.Test test
               && test.elem instanceof Expr.Tuple tuple){
      return tuple.elements[0];
    } else {
      return null;
    }
  }

  /**
   * The reducer for powerset-construction.
   */
  private static
  Tree powerReducer(CFixity fixity, Locator locator, Seq<Tree> operands){
    Expr res = Expr.Unary(Expr.UnaryKind.Power,
                          toExprArray(operands)[0]);
    Locator.put(res.an, locator);
    return new Tree.Operand(res);
  }

  /**
   * The reducer for cartesian product.
   */
  private static
  Tree cartReducer(CFixity fixity, Locator locator, Seq<Tree> operands){
    if (operands.at(1) instanceof Tree.Cart cart){
      return Tree.Cart(cart.opers.prepend(operands.at(0).getExpr()));
    } else {
      return Tree.Cart(new ArraySeq<Expr>(operands.at(0).getExpr(),
                                          operands.at(1).getExpr()));
    }
  }

  /**
   * The reducer for juxtaposed application.
   */
  private static
  Tree juxtaposedReducer(CFixity fixity, Locator locator, Seq<Tree> operands){
    Expr[] elems = toExprArray(operands);
    Expr res = Expr.Binary(Expr.BinaryKind.Apply, elems[0], elems[1]);
    Locator.put(res.an, locator);
    return new Tree.Operand(res);
  }

  /**
   * Convert sequence of supposed operands to array of expressions.
   */
  private static Expr[] toExprArray(Seq<Tree> operands){
    Expr[] exprs = new Expr[operands.size()];
    for (int i = 0; i < operands.size(); i++){
      exprs[i] = operands.at(i).getExpr();
      if (exprs[i] == null) throw FatalError.unexpectedCase();
    }
    return exprs;
  }

  /**
   * Exception thrown in case of ambigious parse.
   */
  private static class AmbigiousException extends Exception {
    Seq<Pair<CFixity, Pair<Tree, Seq<Tree>>>> cands;
    AmbigiousException(Seq<Pair<CFixity, Pair<Tree, Seq<Tree>>>> cands){
      this.cands = cands;
    }
  }

  /**
   * Show the given forest for diagnostics.
   */
  private static Format showForest(Seq<Tree> forest){
    return
      Format.block(
        forest.map((Tree t) -> Format.literal(t.toString() + " "))
      );
  }

  /**
   * Show the given fixity for diagnostics.
   */
  private static Format showFixity(CFixity fixity){
    return Format.literal(fixity.name.getRepr());
  }

  /**
   * Construct locator form start to end tree.
   */
  private static Locator locator(Tree start, Tree end){
    return ItemParser.upto(start.getLocator(), end.getLocator());
  }

  /**
   * Construct locator from non-empty forest.
   */
  static Locator locator(Seq<Tree> forest){
    if (forest.size() == 1)
      return forest.head().getLocator();
    else
      return locator(forest.head(), forest.at(forest.size()-1));
  }

  /**
   * Construct locator from non-empty forest.
   */
  static Locator locator(Tree[] forest){
    return locator(new ArraySeq<Tree>(forest));
  }
}
