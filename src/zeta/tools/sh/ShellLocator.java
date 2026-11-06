package zeta.tools.sh;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Locator;
import zeta.util.Name;

/**
 * The class of locators used by the shell.
 *
 * @version $Id: ShellLocator.pizza,v 1.1 1998/10/22 09:02:34 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * A package-private single-case class whose case name equals the class name: field, constructor
 * and factory are all package-private, as in the class file; no {@code $$tag}.
 *
 * <p>PORT NOTE: {@code equals} is overridden without {@code hashCode} (which stays the
 * identity hash inherited through {@code Term}), exactly as in 1998.  Kept: changing it could
 * only alter the behaviour of a hash container holding shell locators, and preserving the
 * 2002 behaviour there is the point of the port.
 */
class ShellLocator extends Locator {

  String msg;

  ShellLocator(String msg) {
    this.msg = msg;
  }

  /** Pizza's generated case factory. */
  static ShellLocator ShellLocator(String msg) {
    return new ShellLocator(msg);
  }

  public Format toFormat(FormatInfo info) {
    return Format.literal(msg);
  }

  public Name getTool() {
    return ShellState.shellName;
  }

  public boolean equals(Object other) {
    if (other instanceof ShellLocator)
      return msg.equals(((ShellLocator) other).msg);
    else
      return false;
  }

}
