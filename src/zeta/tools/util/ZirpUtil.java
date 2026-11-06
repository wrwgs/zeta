package zeta.tools.util;

/**
 * Tool utilities for working on ZIRP. Contains in particular
 * utilities for entering ZIRP expressions and predicates from strings.
 *
 * @version $Id: ZirpUtil.pizza,v 1.5 1998/12/14 09:35:18 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Straight translation.  No Pizza features used in this class.  All
 * collaborators ({@code LatexScanner}, {@code ItemParser}, {@code TermChecker},
 * {@code Env}) are still binary; their call shapes are taken from the reference with
 * {@code javap}.</p>
 */

import java.io.Serializable;

import zeta.content.zirp.Expr;
import zeta.content.zirp.Predicate;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZirpContentTypes;
import zeta.session.Diag;
import zeta.session.Session;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.tools.latex.LatexConfig;
import zeta.tools.latex.LatexScanner;
import zeta.tools.zchecker.Env;
import zeta.tools.zchecker.TermChecker;
import zeta.tools.zparser.ItemParser;
import zeta.util.Name;


public class ZirpUtil {

  /**
   * Try to parse and type-check a ZIRP expression given by a string in the
   * context of the given unit.  Send diagnostics to the session
   * and return null on failure.
   */
  public static Expr enterExpr(Session session, Unit context,
                                Name tool, Name inputName,
                                int lineStart, int columnStart,
                                String input) {
    // scan the string.
    // FIXME: a way to choose between different concrete languages?
    //        perhaps on base of the language of the context unit?
    LatexConfig config = new LatexConfig();
    LatexScanner.ScanResult sres = LatexScanner.scanString(config,
                                                            inputName,
                                                            lineStart,
                                                            columnStart,
                                                            input);
    if (!sres.diag.isEmpty()) session.send(tool, sres.diag);

    if (sres.diag.contains(Diag.Category.Error)) return null;

    // query for the type-checked environment of context unit
    Serializable obj =
      SessionUtil.query(session,
                        SessionUtil.toolLocator(tool),
                        new Target(context, ZirpContentTypes.checkedEnv));
    if (obj == null) return null;
    UnitEnv env = (UnitEnv) obj;

    // parse the scanned string
    ItemParser.ParseResult pres = ItemParser.parseExpr(config, env,
                                                       sres.tokens);
    if (!pres.diag.isEmpty()) session.send(tool, pres.diag);
    if (pres.diag.contains(Diag.Category.Error)) return null;
    Expr expr = (Expr) pres.term;

    // type-check the parsed expression
    Env cenv = Env.sectionEnv(env);
    new TermChecker(cenv.newLocal()).check(expr);
    Diag diag = cenv.getAndClearDiag();
    if (!diag.isEmpty()) session.send(tool, diag);

    // return result
    return diag.contains(Diag.Category.Error) ? null : expr;
  }

  /**
   * Try to parse and type-check a ZIRP predicate in the
   * context of the given unit.  Send diagnostics to the session
   * and return null on failure.
   */
  public static Predicate enterPredicate(Session session, Unit context,
                                          Name tool,
                                          Name inputName,
                                          int lineStart, int columnStart,
                                          String input) {
    // scan the string.
    // FIXME: a way to choose between different concrete languages?
    //        perhaps on base of the language of the context unit?
    LatexConfig config = new LatexConfig();
    LatexScanner.ScanResult sres = LatexScanner.scanString(config,
                                                            inputName,
                                                            lineStart,
                                                            columnStart,
                                                            input);
    if (!sres.diag.isEmpty()) session.send(tool, sres.diag);

    if (sres.diag.contains(Diag.Category.Error)) return null;

    // query for the type-checked environment of context unit
    Serializable obj =
      SessionUtil.query(session, SessionUtil.toolLocator(tool),
                        new Target(context, ZirpContentTypes.checkedEnv));
    if (obj == null) return null;
    UnitEnv env = (UnitEnv) obj;

    // parse the scanned string
    ItemParser.ParseResult pres = ItemParser.parsePredicate(config, env,
                                                             sres.tokens);
    if (!pres.diag.isEmpty()) session.send(tool, pres.diag);
    if (pres.diag.contains(Diag.Category.Error)) return null;
    Predicate pred = (Predicate) pres.term;

    // type-check the parsed predicate
    Env cenv = Env.sectionEnv(env);
    new TermChecker(cenv.newLocal()).check(pred);
    Diag diag = cenv.getAndClearDiag();
    if (!diag.isEmpty()) session.send(tool, diag);

    // return result
    return diag.contains(Diag.Category.Error) ? null : pred;
  }

}
