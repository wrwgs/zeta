package zeta.tools.sh;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Locator;
import zeta.session.Message;

/**
 * Evaluation messages, indicating that a form evaluation has been
 * initiated by the GUI.
 *
 * @version $Id: EvalMessage.pizza,v 1.3 2000/05/08 07:07:43 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * A Pizza single-case class whose case name equals the class name: public fields in source
 * order, a public constructor, and a static factory of the same name; no {@code $$tag}.  The
 * fields are not final although the source says {@code final}: Pizza did not carry that
 * through to the class file, and the port matches the class file.
 */
public class EvalMessage extends Message {

  /**
   * The eval message, containg the locator and the sequence of words
   * evaluated.
   */
  public Locator locator;
  public String[] words;

  public EvalMessage(Locator locator, String[] words) {
    this.locator = locator;
    this.words = words;
  }

  /** Pizza's generated case factory. */
  public static EvalMessage EvalMessage(Locator locator, String[] words) {
    return new EvalMessage(locator, words);
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
