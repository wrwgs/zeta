package zeta.form;

import java.io.File;

import zeta.agl.ArraySeq;
import zeta.agl.Seq;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The abstract class of text forms. <p>
 *
 * An text form represents textual user input which may be
 * interpreted in several ways.
 *
 * @version $Id: TextForm.pizza,v 1.6 2000/05/14 17:43:32 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * Public field order is {@code style}, then {@code state}, as in the reference.
 * {@code style} <em>hides</em> {@link Form}'s {@code style} field (which is a
 * {@code Form.Style[]}), and {@code style(Style)} <em>overloads</em>, not overrides,
 * {@code Form.style(Form.Style)} -- both exactly as Pizza compiled it. Inside this class the
 * simple name {@code Style} denotes {@code TextForm.Style}, which shadows the inherited
 * member type {@code Form.Style}; that is what makes the overload resolve the way the
 * reference does.
 *
 * {@code Style} is an empty marker class in the reference too: the Pizza source declares no
 * cases in it, so there is no {@code $$tag} and no nested case class. Subclasses are expected
 * to supply their own styles.
 *
 * {@code TextForm.State}'s three history fields are private, so their order is not ABI, but
 * it is kept as declared ({@code history}, {@code hpos}, {@code savedDraft}) to match the
 * reference anyway. {@code history} is {@code Seq<String>}, which erases to the reference's
 * {@code Lzeta/agl/Seq;}.
 *
 * PORT NOTE: {@code stepHistory} recurses when stepping produced the same text. The
 * recursion terminates because every recursive call has first moved {@code hpos} strictly
 * towards one of the two bounds that return {@code false} ({@code hpos < 0} when advancing,
 * {@code hpos == history.size()-1} when going back), and {@code history} is not mutated in
 * between. Left as it was.
 */
public abstract class TextForm extends Form {

  /** The style of the text form. Null means adapt to the displayed text. */
  public Style style = null;

  /** The class of text form styles. */
  public static class Style {
  }

  /** The state of the text form. */
  public final State state;

  /** The abstract class of text form states. */
  public static abstract class State extends FormState {

    /**
     * Get the text contained in this state.
     */
    public abstract String getText(Session session);


    /* Set the "draft" text contained in the state.
     * May return false to indicate
     * that the draft text is syntactically not correct.
     */
    public abstract boolean setDraftText(Session session, String text);

    /**
     * Confirm the text contained in the state.  May throw to indicate
     * that the contained text last set by setDraftText is invalid.
     * By default, this method assumes the contained text is correct
     * and issues a notifyChange() call.
     */
    public void confirmText(Session session, Locator locator, Object source)
    throws Failures.FormFailed {
      notifyChange(source);
    }

    /**
     * Return true if this text state provides a history. By default,
     * returns false.
     */
    public boolean hasHistory(Session session) {
      return false;
    }

    /**
     * Step through history of this text state. Returns true if
     * a previous or next item exists in the history. Provides
     * a default implementation for history which is activated by
     * letting hasHistory return true.
     */
    public boolean stepHistory(Session session, boolean advance) {
      if (!hasHistory(session)) return false;
      String old = getText(session);
      if (advance) {
        if (hpos < 0)
          return false;
        else if (hpos == 0) {
          hpos--;
          setDraftText(session, savedDraft);
        }
        else {
          hpos--;
          setDraftText(session, history.at(hpos));
        }
      } else {
        if (hpos < history.size() - 1) {
          if (hpos == -1)
            savedDraft = old;
          hpos++;
          setDraftText(session, history.at(hpos));
        } else
          return false;
      }
      String next = getText(session);
      if (old == next || old.equals(next)) {
        return stepHistory(session, advance);
      } else {
        notifyChange(this);
        return true;
      }
    }
    private Seq<String> history = new ArraySeq<String>();
    private int hpos = -1;
    private String savedDraft = null;

    /**
     * Add the given string to the history. This should be performed
     * by confirmText, if this state wants to have a history.
     */
    public void addToHistory(String string) {
      if (history.isEmpty() || !history.head().equals(string)) {
        history = history.prepend(string);
      }
      hpos = -1;
    }

    /**
     * Return a file for external editing of this text, or null
     * if this feature shall not be provided.
     * By default returns null.
     */
    public File getExternFile(Session session) {
      return null;
    }


  }

  /** Create a new text form from the given state. */
  protected TextForm(State state) {
    this.state = state;
  }


  /**
   * Attach a style to an text form. Return this text form.
   */
  public TextForm style(Style style) {
    this.style = style;
    return this;
  }


  /**
   * Parse the given sequence of words starting at index. Takes
   * the first word in the sequence and sets and confirms it.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start) throws Failures.FormFailed {
    if (start < words.length) {
      state.setDraftText(session, words[start]);
      state.confirmText(session, locator, this);
      return start + 1;
    } else
      return -1;
  }

  /**
   * Unparse the internal state of the form. Returns the state of the
   * text.
   */
  public String[] unparse(Session session) {
    state.notifyRequired(this);
    return new String[]{state.getText(session)};
  }

}
