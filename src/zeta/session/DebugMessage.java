package zeta.session;

import zeta.format.Format;
import zeta.format.FormatInfo;

/**
 * Debug messages of tool operation.
 *
 * @version $Id: DebugMessage.pizza,v 1.1 1998/09/06 15:26:32 wg Exp $
 *
 * A Pizza single-case class whose case name equals the class name, so it compiles to the
 * fields plus a constructor plus a static factory of the same name -- and that factory is why
 * `new DebugMessage(fmt)` does not compile against the 2002 class file. Keep using the factory
 * from ported code until every caller is ported.
 *
 * `message` is not final, even though the Pizza source says `final Format message`: Pizza did
 * not carry that through to the field, and the port matches the compiled form rather than the
 * source, since binary code may assign it.
 */
public class DebugMessage extends Message {

  /** A format describing the message. */
  public Format message;

  /**
   * @param message  a format describing the message
   */
  public DebugMessage(Format message) {
    this.message = message;
  }

  /** Pizza's generated case factory. */
  public static DebugMessage DebugMessage(Format message) {
    return new DebugMessage(message);
  }

  /** */
  public Format toFormat(FormatInfo info) {
    return message;
  }
}
