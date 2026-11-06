package zeta.tools.sh;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Locator;
import zeta.session.Message;

/**
 * Evaluation finished messages, indicating that a form evaluation has been
 * finished.
 *
 * @version $Id: FinishedMessage.pizza,v 1.2 2000/05/08 07:07:43 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * Same shape as {@link EvalMessage}: a single-case class (no {@code $$tag}) with non-final
 * public fields, a public constructor and a static factory.  The batch shell prints this
 * message through {@link MessagePrinter}'s fall-through branch, i.e. as {@code "- "} followed
 * by the command's words -- the completion marker of a command in a session transcript.
 */
public class FinishedMessage extends Message {

  /**
   * The finished message, containg the locator and the sequence of words
   * finished evaluation.
   */
  public Locator locator;
  public String[] words;

  public FinishedMessage(Locator locator, String[] words) {
    this.locator = locator;
    this.words = words;
  }

  /** Pizza's generated case factory. */
  public static FinishedMessage FinishedMessage(Locator locator, String[] words) {
    return new FinishedMessage(locator, words);
  }

  /** */
  public Format toFormat(FormatInfo info) {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < words.length; i++) {
      if (i > 0) buf.append(" ");
      String word = words[i].replace('\n', ' ');
      word = word.replace('\r', ' ');
      if (word.indexOf(" ") >= 0) { // FIXME: more cases for quote
        buf.append("\"");
        buf.append(word);
        buf.append("\"");
      } else
        buf.append(word);
    }
    return Format.literal(buf.toString());
  }
}
