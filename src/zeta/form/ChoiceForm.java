package zeta.form;

import zeta.session.Errors;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of choice forms. <p>
 *
 * A choice form represents a choice from a set of subforms.
 *
 * @version $Id: ChoiceForm.pizza,v 1.3 1998/10/27 11:04:14 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * Two nested Pizza ADTs, of the two degenerate shapes:
 *
 * {@code Style} has two cases declared *without* parentheses, so both became
 * `public static final Style` constants on a concrete base, which still carries
 * `zeta$form$ChoiceForm$Style$$tag` because there is more than one case.  Tags read from the
 * reference: `RADIOBUTTON` = 1, `TABBEDPANE` = 2.
 *
 * {@code State} is a single case whose name equals the class name, so it gets *no* tag -- just
 * the public field `selection` (not final, as the reference has it), a public constructor and
 * the static factory `State(int)`, which here returns the case type rather than a base type
 * because base and case coincide.
 *
 * The field `style` hides `Form.style`, which is a `Form.Style[]`; the nested type `Style`
 * likewise shadows the inherited `Form.Style`.  Both are exactly what Pizza compiled, and
 * both are legal Java, so they are kept rather than renamed -- the field name and the two
 * `style` overloads are ABI.
 *
 * `getSyntax` uses a `StringBuilder` where the source used a `StringBuffer`; the buffer is a
 * local and never escapes.
 */
public class ChoiceForm extends CompoundForm {

  /** The subforms of the choice form. */
  public final Form[] subforms;

  /** A display style for this choice form. */
  public Style style = Style.TABBEDPANE;

  /** The class of display styles for choice forms. */
  public static class Style {

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_RADIOBUTTON = 1;
    private static final int TAG_TABBEDPANE = 2;

    /** Radio button style. */
    public static final Style RADIOBUTTON = new Style(TAG_RADIOBUTTON);

    /** Tabbed pane. */
    public static final Style TABBEDPANE = new Style(TAG_TABBEDPANE);

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$form$ChoiceForm$Style$$tag;

    Style(int tag) {
      this.zeta$form$ChoiceForm$Style$$tag = tag;
    }
  }

  /** The state of this choice form. */
  public final State state;

  /** The class of states for choice forms. */
  public static class State extends FormState {
    public int selection;

    public State(int selection) {
      this.selection = selection;
    }

    /** Pizza's generated case factory. */
    public static State State(int selection) {
      return new State(selection);
    }
  }

  /**
   * Construct a choice form.
   */
  public ChoiceForm(State state, Form[] subforms) {
    this.subforms = subforms;
    this.state = state;
  }

  /**
   * Construct a choice form.
   */
  public ChoiceForm(Form[] subforms) {
    this(new State(0), subforms);
  }

  /**
   * Construct a choice form.
   */
  public ChoiceForm(State state, Form subform1, Form subform2) {
    this(state, new Form[]{subform1, subform2});
  }

  /**
   * Construct a choice form.
   */
  public ChoiceForm(State state, Form subform1, Form subform2, Form subform3) {
    this(state, new Form[]{subform1, subform2, subform3});
  }

  /**
   * Construct a choice form.
   */
  public ChoiceForm(State state, Form subform1, Form subform2,
                    Form subform3, Form subform4) {
    this(state, new Form[]{subform1, subform2, subform3, subform4});
  }

  /**
   * Construct a choice form.
   */
  public ChoiceForm(State state, Form subform1, Form subform2,
                    Form subform3, Form subform4, Form subform5) {
    this(state, new Form[]{subform1, subform2, subform3, subform4, subform5});
  }

  /**
   * Set style of choice form. Returns this form.
   */
  public ChoiceForm style(Style style) {
    this.style = style;
    return this;
  }

  /**
   * Parse the given sequence of words starting at index.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start)
  throws Failures.FormFailed {
    for (int i = 0; i < subforms.length; i++) {
      int start1 = subforms[i].parse(session, locator, words, start);
      if (start1 >= 0) {
        // select this path
        int old = state.selection;
        state.selection = i;
        if (old != state.selection) state.notifyChange(this);
        return start1;
      }
    }
    return -1;
  }

  /**
   * Unparse the internal state of the form.
   */
  public String[] unparse(Session session) {
    if (state.selection >= 0 && state.selection < subforms.length)
      return subforms[state.selection].unparse(session);
    else
      throw new Errors.AssertionFailed("invalid state of choice form");
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
    StringBuilder res = new StringBuilder("( ");
    for (int i = 0; i < subforms.length; i++) {
      if (i > 0) res.append(" | ");
      res.append(subforms[i].getSyntax());
    }
    res.append(" )");
    return res.toString();
  }

}
