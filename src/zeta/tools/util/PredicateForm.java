package zeta.tools.util;

/**
 * A form for entering a predicate.
 *
 * @version $Id: PredicateForm.pizza,v 1.9 2000/05/14 17:43:32 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Mirrors {@code ExprForm} exactly, with {@code Predicate} in place of
 * {@code Expr}, including {@code State.translator} as {@code Function<String, String>}.
 * {@code sinfo} is a static field of type {@code ExprForm.SourceInfo},
 * which is shared between the two form classes (matches the jar).</p>
 */

import java.io.File;
import java.util.function.Function;

import zeta.content.zirp.Predicate;
import zeta.form.TextForm;
import zeta.form.UnitForm;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;


public class PredicateForm extends TextForm {

  private static ExprForm.SourceInfo sinfo =
    new ExprForm.SourceInfo("Z_Predicate");

  /**
   * The class of predicate form states.
   */
  public static class State extends TextForm.State {
    public UnitForm.State unitState;
    public String text;
    public Function<String, String> translator;
    public Predicate pred;

    /**
     * @param unitState  a UnitForm-state which holds the unit in which
     *                   context this predicate is evaluated. Must be defined.
     * @param text       the textual representation of the predicate. May
     *                   be null.
     * @param translator a translator which may augment the text before
     *                   it is checked as a predicate. {@code null}
     *                   if no translation is present.
     * @param pred       the parsed predicate, if any. May be null.
     */
    public State(UnitForm.State unitState, String text,
                 Function<String, String> translator, Predicate pred) {
      this.unitState = unitState;
      this.text = text;
      this.translator = translator;
      this.pred = pred;
    }

    /** Static factory (ABI: matches jar descriptor). */
    public static PredicateForm.State State(UnitForm.State unitState,
                                            String text, Function<String, String> translator,
                                            Predicate pred) {
      return new PredicateForm.State(unitState, text, translator, pred);
    }

    /**
     * Return the text in the state.
     */
    public String getText(Session session) {
      PredicateForm.State pstate = (PredicateForm.State) this;
      if (pstate.text == null)
        return "";
      else
        return pstate.text;
    }

    /**
     * Set the draft text.
     */
    public boolean setDraftText(Session session, String text) {
      ((PredicateForm.State) this).text = text;
      return true;
    }

    /**
     * Confirm the draft text.
     */
    public void confirmText(Session session, Locator locator, Object source)
    throws Failures.FormFailed {
      PredicateForm.State pstate = (PredicateForm.State) this;
      if (pstate.unitState.unit == null) {
        session.send(
          locator.getTool(),
          new Diag(locator,
                   Diag.Category.Error,
                   "unit needed for predicate input")
        );
        throw new Failures.FormFailed("PredicateForm");
      }
      if (pstate.text == null || pstate.text.length() == 0) {
        session.send(
          locator.getTool(),
          new Diag(locator,
                   Diag.Category.Error,
                   "text needed for predicate input")
        );
        throw new Failures.FormFailed("PredicateForm");
      }
      sinfo.makeInput(session, locator, pstate.text);
      if (pstate.translator != null) {
        sinfo.nextInput = pstate.translator.apply(sinfo.nextInput);
      }
      Predicate pred =
        ZirpUtil.enterPredicate(session, pstate.unitState.unit,
                                locator.getTool(),
                                sinfo.nextInputName,
                                sinfo.nextInputLine,
                                sinfo.nextInputColumn,
                                sinfo.nextInput);
      if (pred != null) {
        pstate.pred = pred;
        addToHistory(pstate.text);
      } else
        throw new Failures.FormFailed("PredicateForm");
      pstate.notifyChange(source);
    }

    /**
     * We want a history.
     */
    public boolean hasHistory(Session s) {
      return true;
    }

    /**
     * We want external editing.
     */
    public File getExternFile(Session s) {
      return new File("_Z_Predicate_");
    }

  }

  /**
   * Construct a predicate form from a state.
   */
  public PredicateForm(State state) {
    super(state);
  }

  /**
   * Get syntax.
   */
  public String getSyntax() {
    return "<PREDICATE>";
  }

  /**
   * Overwrite parse method to eat text until the end of command.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start) throws Failures.FormFailed {
    if (start < words.length) {
      String cword = words[start];
      while (++start < words.length) {
        cword += " " + words[start];
      }
      state.setDraftText(session, cword);
      state.confirmText(session, locator, this);
      return start;
    } else
      return -1;
  }

}
