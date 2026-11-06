package zeta.content.zirp;

import java.io.ObjectStreamException;

import zeta.util.Annotation;
import zeta.util.CaseConstants;
import zeta.util.Slot;
import zeta.util.Term;

/** Declaration attribute annotation slots.
  *
  * This slot may be provided at <code>NameDecl</code> terms,
  * and defines additional attributes for these names, as
  * for instance the role of an mSZ schema.
  *
  * @version $Id: DeclAttr.pizza,v 1.2 1998/09/09 18:04:19 buessow Exp $
  *
  * The mixed ADT shape: five nullary cases declared without parentheses become constants, and
  * `Alias` -- which carries data -- becomes a nested class with a factory.  Because of the
  * constants the base stays concrete, so a `switch` over a `DeclAttr` cannot be exhaustive
  * and code dispatching on it needs the tag or a default.
  */
public class DeclAttr extends Term {

  /** The slot object. */
  public static final Slot slot = Slot.register(DeclAttr.class.getName(),
                                                DeclAttr.class);

  /** Set value of annotation slot. */
  public static void put(Annotation an, DeclAttr attr) {
    slot.put(an, attr);
  }

  /** Get value of annotation slot. */
  public static DeclAttr get(Annotation an) {
    return (DeclAttr) slot.get(an);
  }

  /** Test wether annotation slot is provided. */
  public static boolean isProvided(Annotation an) {
    return slot.isProvided(an);
  }

  /** Pizza's discriminant. */
  public final int zeta$content$zirp$DeclAttr$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_PLAIN = 1;
  private static final int TAG_DATA = 2;
  private static final int TAG_PORT = 3;
  private static final int TAG_INIT = 4;
  private static final int TAG_PROPERTY = 5;
  private static final int TAG_ALIAS = 6;

  DeclAttr(int tag) {
    this.zeta$content$zirp$DeclAttr$$tag = tag;
  }

  // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
  // so identity tests on it hold as they do in memory (Pizza compared tags).  The
  // argument is in zeta.util.CaseConstants and docs/serialization.md.
  private Object readResolve() throws ObjectStreamException {
    return CaseConstants.resolve(this, DeclAttr.class);
  }

  /** Attribute of MSZ Plain schema definition. */
  public static final DeclAttr Plain = new DeclAttr(TAG_PLAIN);

  /** Attribute of MSZ Data schema definition. */
  public static final DeclAttr Data = new DeclAttr(TAG_DATA);

  /** Attribute of MSZ Port schema definition. */
  public static final DeclAttr Port = new DeclAttr(TAG_PORT);

  /** Attribute of MSZ Initialization schema definition. */
  public static final DeclAttr Init = new DeclAttr(TAG_INIT);

  /** Attribute of MSZ Property schema definition. */
  public static final DeclAttr Property = new DeclAttr(TAG_PROPERTY);

  /** Attribute of MSZ Alias for a basic role. */
  public static class Alias extends DeclAttr {
    public DeclAttr baseAttr;
    public String symbol;

    public Alias(DeclAttr baseAttr, String symbol) {
      super(TAG_ALIAS);
      this.baseAttr = baseAttr;
      this.symbol = symbol;
    }
  }

  /** Pizza's generated case factory. */
  public static DeclAttr Alias(DeclAttr baseAttr, String symbol) {
    return new Alias(baseAttr, symbol);
  }
}
