package zeta.session;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.util.Name;
import zeta.util.Term;

/**
 * The type of contents of a unit.
 *
 * @version $Id: ContentType.pizza,v 1.3 1998/09/06 22:38:12 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * Note that `equals` and `hashCode` consider only `name`, not `type` -- content types are
 * identified by name, and the class is a constraint on what may be stored under it. That is
 * also why {@link Term}'s reflective `equals` is overridden here: it would have compared
 * nothing at all, since neither `Name` nor `Class` is a `Term` and `getSubtermFields` skips
 * both.
 */
public class ContentType extends Term {

  /** The name of this content type. */
  public Name name;
  /** The least class each content of this type has. */
  public Class type;

  /**
   * Construct a content type.
   * @param name  the name of this content type
   * @param type  the least class each content of this type has
   */
  public ContentType(Name name, Class type) {
    this.name = name;
    this.type = type;
  }

  /** Pizza's generated case factory. */
  public static ContentType ContentType(Name name, Class type) {
    return new ContentType(name, type);
  }

  /**
   * Equality on content types.
   */
  public boolean equals(Object object) {
    if (object instanceof ContentType other) {
      return name.equals(other.name);
    } else
      return false;
  }

  /**
   * Hashcode of content types.
   */
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * Produce format.
   */
  public Format toFormat(FormatInfo info) {
    return Format.literal(name.toString());
  }
}
