package zeta.tools.nmzreducer;

import zeta.agl.ArraySeq;
import zeta.agl.HashSet;
import zeta.agl.Set;
import zeta.content.zirp.Used;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Locator;
import zeta.util.AnnotatedTerm;
import zeta.util.FatalError;
import zeta.util.Name;

/** Utilities for reducing.
  *
  * @version $Id: ReducerUtil.pizza,v 1.3 2000/06/06 17:53:45 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * A straight translation. The static initialiser runs in the reference's order --
  * `reducerName`, `memoizeFunc`, `reducerLocator`, `checkDecls = true`, then
  * `readProperties()` -- which is the declaration order below.
  *
  * `ReducerLocator` was declared `private static` in the source, but Pizza dropped the
  * `private`: the 2002 class file is package-private with a package-private constructor. The
  * port declares it the way the class file has it. Nothing outside this class names it.
  */
class ReducerUtil {

  /** The name of the reducer as a tool adaptor. */
  static final Name reducerName = new Name("NMZREDUCER");

  /** The name of the memoize function. */
  static Name memoizeFunc = new Name("\\MEMOIZE");

  /** The locator associated with the reducer as a tool adaptor. */
  static final Locator reducerLocator =
    new ReducerLocator();

  // PORT NOTE: `private static` in the source; package-private in the 2002 class file.
  static class ReducerLocator extends Locator {
    public Format toFormat(FormatInfo info) {
      return Format.literal("<mZ-Reducer>");
    }
    public Name getTool() {
      return reducerName;
    }
    public boolean equals(Object other) {
      return other instanceof ReducerLocator;
    }
  }

  /** Get type annotation of expression. */
  static zeta.content.zirp.Expr getTypeAn(zeta.content.zirp.Expr expr) {
    if (!zeta.content.zirp.Type.isProvided(expr.an)) {
      throw FatalError.assertionFailed(
              "required ZIRP type annotation at " +
              getLocatorAn(expr)
            );
    }
    return zeta.content.zirp.Type.get(expr.an);
  }

  /** Get locator of annotated term. */
  static Locator getLocatorAn(AnnotatedTerm term) {
    if (Locator.isProvided(term.an)) {
      return Locator.get(term.an);
    } else {
      return reducerLocator;
    }
  }

  /** Get used names of annotated term. */
  static Set<Name> getUsedAn(AnnotatedTerm term) {
    if (Used.isProvided(term.an)) {
      return HashSet.from(new ArraySeq<Name>(Used.get(term.an)));
    } else {
      throw FatalError.assertionFailed(
              "required ZIRP used-names annotation at " +
              getLocatorAn(term)
            );
    }
  }

  /** Whether declarations should be checked. */
  public static boolean checkDecls = true;


  /** Setup options read from system properties. */
  static void readProperties() {
    checkDecls = System.getProperty("zeta.zap.checkdecls") != null;
  }

  static {
    readProperties();
  }


}
