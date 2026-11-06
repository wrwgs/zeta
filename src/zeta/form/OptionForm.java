package zeta.form;

import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of optional forms. <p>
 *
 * A optional form represents a form a user can optionally select.
 *
 * @version $Id: OptionForm.pizza,v 1.4 1998/10/27 11:04:21 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * {@code Style} is the one-case shape with the case declared *without* parentheses: the
 * reference has a `public static final Style CHECKBOX`, a **public no-argument**
 * constructor, and -- because a single case needs no discriminant -- no `$$tag` field at all.
 * Java's implicit default constructor of a public nested class is exactly that public
 * no-argument constructor, so none is written out.
 *
 * {@code State} is a single case whose name equals the class name: public field `selected`
 * (not final, as the reference has it), public constructor, static factory `State(boolean)`
 * returning the case type.
 *
 * As in {@link ChoiceForm}, the `style` field hides `Form.style` and the nested `Style`
 * shadows `Form.Style`; both are the compiled shape and both are kept.
 *
 * PORT NOTE on `parse`: when the subform fails, the option is deselected and the *original*
 * `start` is returned, but whatever state the subform mutated before failing is left behind.
 * That is the 1998 behaviour and it is relied upon -- `ChoiceForm.parse` probes its
 * alternatives the same way -- so it is reproduced unchanged.
 */
public class OptionForm extends CompoundForm {

  /** The subform of the optional form. */
  public final Form subform;

  /** A display style for this optional form. */
  public final Style style;

  /** The class of display styles for optional forms. */
  public static class Style {
    public static final Style CHECKBOX = new Style();
  }

  /** The state of this optional form. */
  public final State state;

  /** The class of states for optional forms. */
  public static class State extends FormState {
    public boolean selected;

    public State(boolean selected) {
      this.selected = selected;
    }

    /** Pizza's generated case factory. */
    public static State State(boolean selected) {
      return new State(selected);
    }
  }

  /**
   * Construct an optional form.
   */
  public OptionForm(Style style, State state, Form subform) {
    this.subform = subform;
    this.style = style;
    this.state = state;
  }

  /**
   * Construct an optional form.
   */
  public OptionForm(State state, Form subform) {
    this(Style.CHECKBOX, state, subform);
  }

  /**
   * Parse the given sequence of words starting at index.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start)
  throws Failures.FormFailed {
    int start1 = subform.parse(session, locator, words, start);
    boolean old = state.selected;
    if (start1 < 0) {
      // deselect option
      state.selected = false;
      if (old != state.selected) state.notifyChange(this);
      return start;
    } else {
      // select option
      state.selected = true;
      if (old != state.selected) state.notifyChange(this);
      return start1;
    }
  }

  /**
   * Unparse the internal state of the form.
   */
  public String[] unparse(Session session) {
    if (state.selected)
      return subform.unparse(session);
    else
      return new String[]{};
  }

  /**
   * Return the direct subforms.
   */
  public Form[] getSubforms() {
    return new Form[]{subform};
  }

  /**
   * Get syntax description
   */
  public String getSyntax() {
    return "[ " + subform.getSyntax() + " ]";
  }

}
