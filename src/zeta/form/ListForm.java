package zeta.form;

import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The abstract class of list forms. <p>
 *
 * A list form represents a variable collections of items where a selection
 * can be made from.
 *
 * @version $Id: ListForm.pizza,v 1.6 2000/05/14 17:43:32 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * Public field order is {@code style}, then {@code state}, as in the reference. As in
 * {@link TextForm}, {@code style} hides {@link Form}'s {@code Form.Style[] style} field and
 * {@code style(Style)} overloads rather than overrides {@code Form.style(Form.Style)}; the
 * nested type {@code ListForm.Style} shadows the inherited member type {@code Form.Style} for
 * the simple name {@code Style} inside this class, which is what makes that resolve as the
 * reference does.
 *
 * {@code Style} has a single case, {@code ComboBox(boolean,int)}, whose name differs from the
 * class name -- yet the reference still collapses it into the base class: the fields
 * {@code editable} and {@code maxPopupRows} sit on {@code ListForm$Style} itself, there is no
 * {@code ListForm$Style$ComboBox} class file, and there is no {@code $$tag}. So a
 * <em>single</em>-case ADT collapses whatever the case is called; only multi-case ADTs get a
 * tag. The factory {@code Style.ComboBox(boolean,int)} is kept, since compiled callers use
 * it.
 *
 * PORT NOTE: {@code parse} reads {@code words[start]} without first checking
 * {@code start &lt; words.length}, so a list form at the end of the input throws
 * {@code ArrayIndexOutOfBoundsException} instead of returning -1. This is a 1998 defect and
 * it is <em>not</em> fixed here: returning -1 would turn a crash into a syntax error, which
 * is a different observable behaviour, and the shell error paths are part of what the
 * negative suites pin down. Every other form in this package guards the access.
 */
public abstract class ListForm extends Form {

  /** The style of the list form. */
  public Style style = Style.ComboBox(false, 5);

  /** The class of styles of list forms. */
  public static class Style {

    /** Whether the contents of the combobox is editable. */
    public boolean editable;

    /** The maximal rows of the popup. */
    public int maxPopupRows;

    public Style(boolean editable, int maxPopupRows) {
      this.editable = editable;
      this.maxPopupRows = maxPopupRows;
    }

    /**
     * Display as a combobox. Pizza's generated case factory.
     * @param editable  whether the contents of the combobox is editable
     *                  (doesn't works currently)
     * @param maxPopupRows  the maximal rows of the popup. If the
     *                      size of the range succeeds this number, a
     *                      scrollbar will be used in the popup
     */
    public static Style ComboBox(boolean editable, int maxPopupRows) {
      return new Style(editable, maxPopupRows);
    }
  }

  /** The state of the list form. */
  public final State state;

  /** The abstract class of list form states. */
  public static abstract class State extends FormState {

    /** Get the range of this list form as a sequence of strings. */
    public abstract String[] getRange(Session session);

    /** Get the index of the selected object in the range. */
    public abstract int getSelected(Session session);

    /** Set the index of the selected object in the range. */
    public abstract void setSelected(Session session, int index,
                                     Object source);
  }


  /** Create a new list form. */
  public ListForm(State state) {
    this.state = state;
  }

  /** Attach a style to this list form. Return this list form. */
  public ListForm style(Style style) {
    this.style = style;
    return this;
  }

  public int
  parse(Session session, Locator locator, String[] words, int start)
  throws Failures.FormFailed {
    String[] range = state.getRange(session);
    for (int i = 0; i < range.length; i++)
      if (range[i].equals(words[start])) {
        state.setSelected(session, i, this);
        return start + 1;
      }
    return -1;
  }

  public String[] unparse(Session session) {
    int selected = state.getSelected(session);
    String[] range = state.getRange(session);
    if (selected < 0 || selected >= range.length)
      return new String[0];
    else
      return new String[]{range[selected]};
  }



}
