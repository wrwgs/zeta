package zeta.content.zirp;

import java.io.ObjectStreamException;

import zeta.util.Annotation;
import zeta.util.CaseConstants;
import zeta.util.Slot;
import zeta.util.Term;

/** The class and slot of layout annotations.
  *
  * A layout slot is optionally provided at item terms. It  gives
  * information about the external representation.
  *
  * @version $Id: Layout.pizza,v 1.2 1998/09/01 17:34:12 wg Exp $
  *
  * Both an ADT and its own annotation slot -- the two nullary cases are declared without
  * parentheses, so they are constants on a concrete base (9.6), which is also why this class
  * can hold the static `slot` alongside them.
  */
public class Layout extends Term {

  /** The slot object. */
  public static final Slot slot = Slot.register(Layout.class.getName(),
                                                Layout.class);

  /** Set value of layout annotation slot. */
  public static void put(Annotation an, Layout layout) {
    slot.put(an, layout);
  }

  /** Get value of layout annotation slot. */
  public static Layout get(Annotation an) {
    return (Layout) slot.get(an);
  }

  /** Test whether layout annotation slot is provided. */
  public static boolean isProvided(Annotation an) {
    return slot.isProvided(an);
  }

  /** Pizza's discriminant. */
  public final int zeta$content$zirp$Layout$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_HORIZONTAL_BOX = 1;
  private static final int TAG_VERTICAL_BOX = 2;

  Layout(int tag) {
    this.zeta$content$zirp$Layout$$tag = tag;
  }

  // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
  // so identity tests on it hold as they do in memory (Pizza compared tags).  The
  // argument is in zeta.util.CaseConstants and docs/serialization.md.
  private Object readResolve() throws ObjectStreamException {
    return CaseConstants.resolve(this, Layout.class);
  }

  /** Horizontal box layout. */
  public static final Layout HorizontalBox = new Layout(TAG_HORIZONTAL_BOX);

  /** Vertical box layout. */
  public static final Layout VerticalBox = new Layout(TAG_VERTICAL_BOX);
}
