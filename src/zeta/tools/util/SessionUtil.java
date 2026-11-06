package zeta.tools.util;

/**
 * Tool utilities for working with session objects.
 *
 * @version $Id: SessionUtil.pizza,v 1.2 1998/09/23 00:03:37 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Straight translation.  {@link ToolLocator} is a private static nested class in the
 * source but is package-private in the jar (the Pizza compiler dropped the outer-class
 * visibility); the port matches the jar.  All fields and methods match the reference
 * descriptors.</p>
 */

import java.io.Serializable;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.Target;
import zeta.util.Name;


public class SessionUtil {

  /**
   * Return a locator for the given named tool, with the given
   * textual description.
   */
  public static Locator toolLocator(Name name, String descr) {
    return new ToolLocator(name, descr);
  }

  /**
   * Return a locator designating control operation of the given named
   * tool.
   */
  public static Locator toolLocator(Name name) {
    return new ToolLocator(name, "<control>");
  }

  static class ToolLocator extends Locator {
    private final Name tool;
    private final String descr;

    ToolLocator(Name tool, String descr) {
      this.tool = tool;
      this.descr = descr;
    }

    public Format toFormat(FormatInfo info) {
      return Format.literal(tool + ":" + descr);
    }

    public Name getTool() {
      return tool;
    }

    public boolean equals(Object other) {
      if (other instanceof ToolLocator) {
        ToolLocator oloc = (ToolLocator) other;
        return tool.equals(oloc.tool) && descr.equals(oloc.descr);
      } else {
        return false;
      }
    }

    public int hashCode() {
      return descr.hashCode() + tool.hashCode();
    }
  }

  /**
   * Try to resolve the given unit name referenced at given
   * locator.  If resolution fails, send diagnostics to session.
   * @param session             the session object to work on
   * @param locator             the locator associated with the partial unit name.
   *                            Used in diagnostics if resolution fails.
   * @param partialUnitName     the partial unit name to resolve
   * @return the unit on success, null if resolution fails.
   * @see Session#lookupUnit
   */
  public static zeta.session.Unit lookupUnit(Session session,
                                              Locator locator,
                                              Name partialUnitName) {
    try {
      return session.lookupUnit(partialUnitName);
    }
    catch (Failures.UnresolvableName e) {
      session.send(
        locator.getTool(),
        new Diag(locator,
                 Diag.Category.Error,
                 "`" + partialUnitName + "' cannot be resolved as a unit"
                )
      );
      return null;
    }
    catch (Failures.AmbigiousName e) {
      session.send(
        locator.getTool(),
        new Diag(locator,
                 Diag.Category.Error,
                 Format.describe(
                   "`" + partialUnitName + "' is ambiguous",
                   "possible completions",
                   Format.list(Format.empty,
                               Format.literal(", "),
                               Format.empty,
                               e.fullNames)
                 )
                )
      );
      return null;
    }
  }


  /**
   * Try to query the given target. If querying fails, send diagnostics
   * to the given session.
   *
   * @param session  the session object to work on
   * @param locator  the locator associated with the target.
   *                 Used in diagnostics if querying fails.
   * @param target   the target queried for
   * @return the content on success, null if querying fails.
   * @see zeta.session.Unit#query
   */
  public static Serializable query(Session session, Locator locator,
                                   Target target) {
    try {
      return target.unit.query(target.type);
    }
    catch (Failures.ComputationFailed e) {
      // diagnostics are sent by the according tool adaptor
      return null;
    }
    catch (Failures.CannotCompute e) {
      session.send(
        locator.getTool(),
        new Diag(locator,
                 Diag.Category.Error,
                 Format.describe(
                   "query not resolvable",
                   "target", Format.buildFormat(e.target)
                 )
                )
      );
      return null;
    }
  }

}
