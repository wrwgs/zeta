package zeta.form;

import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of string forms, an instance of text forms for
 * inputting arbitrary strings. <p>
 *
 * @version $Id: StringForm.pizza,v 1.4 1998/11/23 00:05:23 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * {@code State} is a single-case class whose case name equals the class name, so there is no
 * {@code $$tag}: just the public field {@code string}, a public constructor and the static
 * factory. The factory's return type in the reference is {@code StringForm$State} itself (not
 * some base), because for this shape Pizza's "base" and "case" are the same class.
 *
 * {@code string} is <em>not</em> final in the reference ({@code setDraftText} assigns it),
 * and it is the only public field, so there is no field-order question.
 */
public class StringForm extends TextForm {


  /** The class of string form states. */
  public static class State extends TextForm.State {

    public String string;

    public State(String string) {
      this.string = string;
    }

    /** Pizza's generated case factory. */
    public static State State(String string) {
      return new State(string);
    }

    /**
     * Return the text in the string state.
     */
    public String getText(Session session) {
      if (string == null)
        return "";
      else
        return string;
    }

    /*
     * Set the draft text.
     */
    public boolean setDraftText(Session session, String text) {
      string = text;
      return true;
    }

    /*
     * Confirm the given text.
     */
    public void confirmText(Session session, Locator locator, Object source)
    throws Failures.FormFailed {
      addToHistory(string);
      super.confirmText(session, locator, source);
    }

    /**
     * Return true if this text state provides a history.
     */
    public boolean hasHistory(Session s) {
      return true;
    }

  }

  /**
   * Construct a string form from a state.
   */
  public StringForm(State state) {
    super(state);
  }

  /**
   * Get syntax description
   */
  public String getSyntax() {
    return "<STRING>";
  }

}
