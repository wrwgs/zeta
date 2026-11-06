package zeta.session;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.util.Term;

/**
 * A target of a computation: a unit together with the type of contents wanted from it.
 *
 * @version $Id: Target.pizza,v 1.3 1998/09/06 22:38:17 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * This is the node type of the session manager's make-style dependency graph, which is why
 * `equals`/`hashCode` go through `unit.getName()` rather than the unit's identity: two
 * `Unit` objects for the same name must denote the same target.
 */
public class Target extends Term {

  /** The unit. */
  public Unit unit;
  /** The type of the contents in the unit. */
  public ContentType type;

  /**
   * @param unit  the unit
   * @param type  the type of the contents in the unit
   */
  public Target(Unit unit, ContentType type) {
    this.unit = unit;
    this.type = type;
  }

  /** Pizza's generated case factory. */
  public static Target Target(Unit unit, ContentType type) {
    return new Target(unit, type);
  }

  /** */
  public boolean equals(Object other) {
    if (other instanceof Target dep) {
      return unit.getName().equals(dep.unit.getName()) && type.equals(dep.type);
    } else
      return false;
  }

  /** */
  public int hashCode() {
    return unit.getName().hashCode() * type.hashCode();
  }

  /** */
  public Format toFormat(FormatInfo info) {
    return Format.literal(unit.getName() + "/" + type);
  }
}
