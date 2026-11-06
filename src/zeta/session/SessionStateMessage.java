package zeta.session;

import zeta.format.Format;
import zeta.format.FormatInfo;

/**
 * Messages about changes of the session state.
 *
 * @version $Id: SessionStateMessage.pizza,v 1.2 1998/09/06 22:38:17 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * The first place where sealing pays for itself. The Pizza original was
 *
 * <pre>
 *   switch (this){
 *   case UnitAdded(Unit unit):        return Format.literal(...);
 *   case ContentTypeAdded(...):       return ...;
 *   case AdaptorAdded(...):           return ...;
 *   }
 * </pre>
 *
 * with no `default` and no fall-through return -- legal only because Pizza knew the cases were
 * exhaustive. Plain Java would reject that method as possibly not returning a value, and the
 * usual workaround is a `default` that throws `FatalError.unexpectedCase()`. Sealing the
 * hierarchy instead gives the compiler the same knowledge Pizza had, so the switch below needs
 * no `default` and a case added later is a compile error rather than a run-time one.
 *
 * The base is `abstract` here, where the reference has it concrete: Java needs that for the
 * switch below to be exhaustive, since a sealed but instantiable base is itself a possible
 * value. Safe because only the three cases invoke `SessionStateMessage(int)`, and they are
 * ported with it.
 */
public abstract sealed class SessionStateMessage extends Message {

  // Tag values are fixed by the ABI: 1-based, declaration order.
  private static final int TAG_UNIT_ADDED = 1;
  private static final int TAG_CONTENT_TYPE_ADDED = 2;
  private static final int TAG_ADAPTOR_ADDED = 3;

  /** Pizza's discriminant.  Public and final because compiled consumers read it directly. */
  public final int zeta$session$SessionStateMessage$$tag;

  SessionStateMessage(int tag) {
    this.zeta$session$SessionStateMessage$$tag = tag;
  }

  /** The given unit was added. */
  public static final class UnitAdded extends SessionStateMessage {
    public Unit unit;

    public UnitAdded(Unit unit) {
      super(TAG_UNIT_ADDED);
      this.unit = unit;
    }
  }

  /** The given content type was added. */
  public static final class ContentTypeAdded extends SessionStateMessage {
    public ContentType type;

    public ContentTypeAdded(ContentType type) {
      super(TAG_CONTENT_TYPE_ADDED);
      this.type = type;
    }
  }

  /** The named tool adaptor was added. */
  public static final class AdaptorAdded extends SessionStateMessage {
    public Adaptor adaptor;

    public AdaptorAdded(Adaptor adaptor) {
      super(TAG_ADAPTOR_ADDED);
      this.adaptor = adaptor;
    }
  }

  /** Pizza's generated case factory. */
  public static SessionStateMessage UnitAdded(Unit unit) {
    return new UnitAdded(unit);
  }

  /** Pizza's generated case factory. */
  public static SessionStateMessage ContentTypeAdded(ContentType type) {
    return new ContentTypeAdded(type);
  }

  /** Pizza's generated case factory. */
  public static SessionStateMessage AdaptorAdded(Adaptor adaptor) {
    return new AdaptorAdded(adaptor);
  }

  /** */
  public Format toFormat(FormatInfo info) {
    return switch (this) {
      case UnitAdded m -> Format.literal("added unit " + m.unit.getName());
      case ContentTypeAdded m -> Format.literal("added type " + m.type);
      case AdaptorAdded m -> Format.literal("added adaptor " + m.adaptor.getName());
    };
  }
}
