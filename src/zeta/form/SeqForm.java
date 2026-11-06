package zeta.form;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Seq;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of sequence forms. <p>
 *
 * A sequence form represents a sequence of subforms.
 *
 * @version $Id: SeqForm.pizza,v 1.4 1998/11/16 00:53:21 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * A straight translation; no Pizza feature is used.  The wildcard imports of `zeta.agl` and
 * `zeta.session` are replaced by the five names actually needed.
 *
 * `unparse` keeps the `BufferSeq` accumulator rather than collapsing into a plain
 * `ArrayList`: the result goes through `Seq.toArray`, and `appendAll` on a `BufferSeq`
 * preserves the amortised append that the 1998 code chose.  Element order is unchanged, which
 * is what the artifacts can see.
 *
 * `getSyntax` uses a `StringBuilder` where the source used a `StringBuffer`; the buffer is a
 * local and never escapes, so its synchronisation was dead.
 */
public class SeqForm extends CompoundForm {

  /** The subforms of the sequence form. */
  public final Form[] subforms;

  /**
   * Construct a sequence form.
   */
  public SeqForm(Form[] subforms) {
    this.subforms = subforms;
  }

  /**
   * Construct a sequence form.
   */
  public SeqForm(Form subform) {
    this(new Form[]{subform});
  }

  /**
   * Construct a sequence form.
   */
  public SeqForm(Form subform1, Form subform2) {
    this(new Form[]{subform1, subform2});
  }

  /**
   * Construct a sequence form.
   */
  public SeqForm(Form subform1, Form subform2, Form subform3) {
    this(new Form[]{subform1, subform2, subform3});
  }

  /**
   * Construct a sequence form.
   */
  public SeqForm(Form subform1, Form subform2, Form subform3, Form subform4) {
    this(new Form[]{subform1, subform2, subform3, subform4});
  }

  /**
   * Construct a sequence form.
   */
  public SeqForm(Form subform1, Form subform2, Form subform3, Form subform4,
                 Form subform5) {
    this(new Form[]{subform1, subform2, subform3, subform4, subform5});
  }


  /**
   * Parse the given sequence of words starting at index.
   */
  public int parse(Session session, Locator locator, String[] words, int start)
  throws Failures.FormFailed {
    for (int i = 0; i < subforms.length; i++) {
      int start1 = subforms[i].parse(session, locator, words, start);
      if (start1 < 0) return -1;
      start = start1;
    }
    return start;
  }

  /**
   * Unparse the internal state of the form.
   */
  public String[] unparse(Session session) {
    Seq<String> buf = new BufferSeq<String>();
    for (int i = 0; i < subforms.length; i++) {
      buf = buf.appendAll(new ArraySeq<String>(subforms[i].unparse(session)));
    }
    return buf.toArray(String.class);
  }

  /**
   * Return the direct subforms.
   */
  public Form[] getSubforms() {
    return subforms;
  }

  /**
   * Get syntax description
   */
  public String getSyntax() {
    StringBuilder res = new StringBuilder("");
    for (int i = 0; i < subforms.length; i++) {
      if (i > 0) res.append(" ");
      res.append(subforms[i].getSyntax());
    }
    return res.toString();
  }

}
