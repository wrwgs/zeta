package zeta.form;

import zeta.agl.ArraySeq;
import zeta.format.Format;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.Unit;
import zeta.util.Name;

/**
 * The class of unit forms. <p>
 *
 * A unit form inputs an existing unit of the session system.
 *
 * @version $Id: UnitForm.pizza,v 1.5 2000/02/16 09:10:38 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * {@code State} is a single-case class whose case name equals the class name: no
 * {@code $$tag}, one public non-final field {@code unit}, a public constructor and the static
 * factory.
 *
 * As in {@link ContentTypeForm}, the {@code fun} literal in {@code getRange} becomes a
 * lambda, which removes the bridge {@code $closure$zeta$form$UnitForm$State$0d(Unit,Object[])}
 * and the generated class {@code UnitForm$State$$closures}. Only those two members of the
 * reference mention the bridge, so nothing compiled can miss them.
 *
 * PORT NOTE on static initialisation order: the reference's static fields are
 * {@code defaultState}, then {@code defaultForm}, and {@code defaultForm}'s initialiser reads
 * {@code defaultState}. That is also the public field order the ABI gate checks, so the two
 * requirements agree: declaring them in this order is both shape-correct and the reason
 * {@code defaultForm.state} is the shared default state rather than null. Swapping them would
 * compile (a static field can be read before its initialiser runs) and silently give
 * {@code defaultForm} a null state.
 *
 * Note that {@code parse} consults the <em>static</em> {@code defaultState.unit} when no
 * {@code -u} is given, not {@code ustate}; that is the source's behaviour and is how "the
 * current unit" is threaded through the shell. Kept verbatim.
 */
public class UnitForm extends ListForm {

  /** The class of unit form states. */
  public static class State extends ListForm.State {

    public Unit unit;

    public State(Unit unit) {
      this.unit = unit;
    }

    /** Pizza's generated case factory. */
    public static State State(Unit unit) {
      return new State(unit);
    }

    /**
     * Get the range list -- all existing units in the session.
     */
    public String[] getRange(Session session) {
      return new ArraySeq<Unit>(session.getUnits())
                         .map(u -> u.getName().toString())
                         .toArray(String.class);
    }

    /** Get the index of the selected object in the range, or -1 if no
        selection. */
    public int getSelected(Session session) {
      if (unit != null) {
        Unit[] units = session.getUnits();
        for (int i = 0; i < units.length; i++) {
          if (units[i] == unit) return i;
        }
      }
      return -1;
    }

    /** Set the index of the selected object in the range. */
    public void setSelected(Session session, int i, Object source) {
      Unit[] units = session.getUnits();
      if (i >= 0 && i < units.length)
        unit = units[i];
      notifyChange(source);
    }
  }

  /** A static default state. */
  public static State defaultState = new State(null);

  /** A static default unit form using the the default state. This
      may be displayed tailored in a GUI,
      and shall be used wherever a form works on the "current" unit.
      @see #defaultState
  */
  public static UnitForm defaultForm = new UnitForm(defaultState);


  /**
   * Construct a unit form from a state.
   */
  public UnitForm(State state) {
    super(state);
  }

  /**
   * Construct a unit form for the default state.
   */
  public UnitForm() {
    this(defaultState);
  }


  /**
   * Parse the given sequence of words starting at index. If
   * the first word in the sequence is "-u", try to resolve the next
   * name as a unit. Throws if resolution fails. If the first
   * word does not starts with "-u", we use the default state,
   * if it is defined.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start)
  throws Failures.FormFailed {
    State ustate = (State)state;
    if (start >= words.length || !words[start].startsWith("-u")) {
      if (defaultState.unit == null) {
        session.send(locator.getTool(),
                     new Diag(locator,
                              Diag.Category.Error,
                              "a unit to default to was never specified"));
        throw new Failures.FormFailed("UnitForm");
      }
      ustate.unit = defaultState.unit;
      return start;
    } else if (start + 1 < words.length) {
      Name name = new Name(words[start + 1]);
      try {
        Unit oldUnit = ustate.unit;
        ustate.unit = session.lookupUnit(name);
        if (ustate.unit != oldUnit) ustate.notifyChange(this);
        return start + 2;
      }
      catch (Failures.UnresolvableName e) {
        session.send(locator.getTool(),
                     new Diag(locator,
                              Diag.Category.Error,
                              "`" + name + "' cannot be resolved as a unit"));
        throw new Failures.FormFailed("UnitForm");
      }
      catch (Failures.AmbigiousName e) {
        session.send(
          locator.getTool(),
          new Diag(locator,
                   Diag.Category.Error,
                   Format.describe(
                     "`" + name + "' is ambigious",
                     "possible completions",
                     Format.list(Format.empty,
                                 Format.literal(", "),
                                 Format.empty,
                                 e.fullNames)))
        );
        throw new Failures.FormFailed("UnitForm");
      }
    } else {
      return -1;
    }
  }

  /**
   * Unparse the internal state of the form. Returns the name of the
   * unit.
   */
  public String[] unparse(Session session) {
    State ustate = (State)state;
    if (ustate.unit != null)
      return new String[]{"-u", ustate.unit.getName().toString()};
    else
      return new String[]{""};
  }


  /**
   * Get syntax description
   */
  public String getSyntax() {
    return "[-u <UNITNAME>]";
  }

}
