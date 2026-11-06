package zeta.tools.zparser;

import java.util.ArrayList;

import zeta.content.zirp.Expr;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.NameDecl;
import zeta.session.Locator;
import zeta.util.FatalError;

/**
 * Auxiliary functions for parsing.
 *
 * @version $Id: ParserAux.pizza,v 1.2 1998/09/06 15:28:08 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>The <code>Vector</code> is an <code>ArrayList</code> (local, not observable).  The switch
 * over <code>Expr.Decl</code> named all three cases, so Pizza gave it a default throwing
 * <code>java.lang.Error</code>; here that default is <code>FatalError.unexpectedCase</code>,
 * equally unreachable.</p>
 */
class ParserAux {

  /**
   * Build the characteristic tuple of a schema expression.
   */
  static Expr characteristicTuple(Expr expr){
    ArrayList<Expr> v = new ArrayList<>();
    if (expr instanceof Expr.Text text) {
      Expr.Decl[] decls = text.decls;
      for (int i = 0; i < decls.length; i++){
        switch (decls[i]) {
          case Expr.Decl.Direct direct -> {
            NameDecl[] names = direct.names;
            for (int j = 0; j < names.length; j++){
              NameAppl nap = new NameAppl(names[j].name, new Expr[0]);
              Locator loc = Locator.get(names[j].an);
              Locator.put(nap.an, loc);
              Expr var = Expr.Variable(nap);
              Locator.put(var.an, loc);
              v.add(var);
            }
          }
          case Expr.Decl.Eqn eqn -> {
            NameDecl name = eqn.name;
            NameAppl nap = new NameAppl(name.name, new Expr[0]);
            Locator loc = Locator.get(name.an);
            Locator.put(nap.an, loc);
            Expr var = Expr.Variable(nap);
            Locator.put(var.an, loc);
            v.add(var);
          }
          case Expr.Decl.Inclusion inclusion -> {
            Expr schema = inclusion.schema;
            Expr theta = Expr.Unary(Expr.UnaryKind.Theta, schema);
            Locator.put(theta.an, Locator.get(schema.an));
            v.add(theta);
          }
          default -> throw FatalError.unexpectedCase("ParserAux.characteristicTuple");
        }
      }
    } else {
      Expr theta = Expr.Unary(Expr.UnaryKind.Theta, expr);
      Locator.put(theta.an, Locator.get(expr.an));
      v.add(theta);
    }
    Expr[] exprs = v.toArray(new Expr[0]);
    switch (exprs.length){
    case 0: {
      Expr res = Expr.Binding(new Expr.Decl.Eqn[0]);
      Locator.put(res.an, Locator.get(expr.an));
      return res;
    }
    case 1:
      return exprs[0];
    default: {
      Expr res = Expr.Tuple(exprs);
      Locator.put(res.an,
                  ItemParser.upto(Locator.get(exprs[0].an),
                                  Locator.get(exprs[exprs.length-1].an)));
      return res;
    }
    }
  }

}
