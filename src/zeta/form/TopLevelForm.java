package zeta.form;

import java.util.function.Consumer;

import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of top-level forms. <p>
 *
 * A top-level form represents an interaction which can be registered
 * at the session system to be made available to the user.
 *
 * @version $Id: TopLevelForm.pizza,v 1.4 2000/05/14 17:43:32 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * <b>The action of `make` is a {@link TopLevelForm.Action}.</b>  Pizza erased the arrow type
 * `(Session,Locator) throws Failures.FormFailed -&gt; void` to `pizza.support.Closure`, and a
 * Pizza closure could not declare a checked exception, so an action wrapped its `FormFailed`
 * in `pizza.support.ExceptionWrapper` and the reference `perform` unwrapped it:
 *
 * <pre>
 *   try { function.$apply(session, locator); }
 *   catch (ExceptionWrapper w) {
 *     if (w.thrown instanceof Failures.FormFailed) throw (Failures.FormFailed) w.thrown;
 *     throw new UndeclaredException();
 *   }
 * </pre>
 *
 * PORT CHANGE: with `Closure` gone, `Action.perform` declares `throws Failures.FormFailed`
 * itself, so a `FormFailed` propagates as it is and the wrapper protocol disappears.  This is
 * the same behaviour, not merely an equivalent one: the only checked exception an action may
 * throw is `FormFailed`, so that is all that was ever wrapped (every `new ExceptionWrapper(..)`
 * in the port took one), the `UndeclaredException` branch was unreachable, and an unchecked
 * exception from an action escaped `perform` unchanged before and does now.  `setup` is a
 * `Consumer<Session>`: `(Session) -&gt; void` declared no exception.
 *
 * <b>The four generated anonymous classes.</b>  The reference holds
 * `TopLevelForm$1$$anonymous_class0` and `TopLevelForm$3$$anonymous_class2` (compiled from
 * `TopLevelForm.pizza`) plus `TopLevelForm$5$$anonymous_class4` and
 * `TopLevelForm$7$$anonymous_class6` (compiled from `form/TopLevelForm.pizza`): two
 * compilations of the same two `fun` literals, with the second pair left in `classes.jar` as
 * orphans -- nothing, not even `TopLevelForm`, constructs them.  Pizza's numbering is
 * `$N$$anonymous_classM` with N odd; javac will name the port's two anonymous subclasses
 * `TopLevelForm$1` and `TopLevelForm$2`.  Renaming them is safe: the only references to the
 * 1998 names are from `TopLevelForm` itself (being replaced) and from the orphans, which name
 * only themselves.  A lambda cannot be used here -- the target is an abstract *class*, and
 * the second `make` overrides two methods.
 *
 * {@code Style} is a six-case ADT of mixed shape: `Shell()`, `Source()`, `Function()` and
 * `Config()` are declared with *empty* parentheses and so became nested classes with
 * no-argument constructors plus factories, `MenuAt(String[])` carries data, and `HIDDEN` has
 * no parentheses and so became a `public static final Style` constant -- which keeps the base
 * concrete and rules out `sealed`.  Tags read from the reference: `Shell` 1, `Source` 2,
 * `Function` 3, `Config` 4, `MenuAt` 5, `HIDDEN` 6.  Unusually for a ZETA ADT, `Style` also
 * carries two mutable public instance fields of its own, `isModal` and `toolIcon`; the
 * reference declares them *before* the constant and the discriminant, and that order is
 * preserved (`abi_gate.py` checks it).
 *
 * The nested type `Style` shadows the inherited `Form.Style`, and the `style` field hides
 * `Form.style`; both are the compiled shape.
 */
public abstract class TopLevelForm extends CompoundForm {

  /**
   * The form associated with this top-level form.
   */
  public final Form subform;


  /**
   * The style of this top-level form, if handled by a GUI.
   */
  public final Style style;

  /**
   * The class of styles, for GUIs.
   */
  public static class Style {

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_SHELL = 1;
    private static final int TAG_SOURCE = 2;
    private static final int TAG_FUNCTION = 3;
    private static final int TAG_CONFIG = 4;
    private static final int TAG_MENU_AT = 5;
    private static final int TAG_HIDDEN = 6;

    /** If this flag is set, then the dialog (if any, i.e. if the top-level
        form isn't atomic) is made modal. False by default.
    */
    public boolean isModal = false;

    /** If this string is not null, it should be a file name of an
        image relative to the images directory, and the form can be
        also reached via the tool bar under this icon. By default null. */
    public String toolIcon = null;

    /** Make this top-level form only available in the command interpreter. */
    public static final Style HIDDEN = new Style(TAG_HIDDEN);

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$form$TopLevelForm$Style$$tag;

    Style(int tag) {
      this.zeta$form$TopLevelForm$Style$$tag = tag;
    }

    /** Make this top-level form available in the shell menu. */
    public static class Shell extends Style {
      public Shell() {
        super(TAG_SHELL);
      }
    }

    /** Make this top-level form available in the source menu. */
    public static class Source extends Style {
      public Source() {
        super(TAG_SOURCE);
      }
    }

    /** Make this top-level form available in the function menu. */
    public static class Function extends Style {
      public Function() {
        super(TAG_FUNCTION);
      }
    }

    /** Make this top-level form available in the configuration menu. */
    public static class Config extends Style {
      public Config() {
        super(TAG_CONFIG);
      }
    }

    /** Make this top-level form available in some arbitrary other menu,
        specified by the given path. */
    public static class MenuAt extends Style {
      public String[] path;

      public MenuAt(String[] path) {
        super(TAG_MENU_AT);
        this.path = path;
      }
    }

    /** Pizza's generated case factory. */
    public static Style Shell() {
      return new Shell();
    }

    /** Pizza's generated case factory. */
    public static Style Source() {
      return new Source();
    }

    /** Pizza's generated case factory. */
    public static Style Function() {
      return new Function();
    }

    /** Pizza's generated case factory. */
    public static Style Config() {
      return new Config();
    }

    /** Pizza's generated case factory. */
    public static Style MenuAt(String[] path) {
      return new MenuAt(path);
    }

    /** Set the modal-flag of this style. Return this style. */
    public Style setModal(boolean yes) {
      this.isModal = yes;
      return this;
    }

    /** Set the tool-icon of this style. Return this style. */
    public Style setToolIcon(String toolIcon) {
      this.toolIcon = toolIcon;
      return this;
    }


  }

  /**
   * The long description of this top-level form.
   */
  public final String longDescr;

  /**
   * A method which is called before the form is evaluated.
   * May be overwritten in order to initialize state variables.
   */
  public void setup(Session session) {}


  /**
   * A method representing the action associated with this top-level
   * form. Passed is the session under which the form is evaluated.
   */
  public abstract void perform(Session session, Locator locator)
  throws Failures.FormFailed;

  /**
   * Construct a top-level form.
   */
  public TopLevelForm(Style style, Form subform, String longDescr) {
    this.style = style;
    this.subform = subform;
    this.longDescr = longDescr;
  }

  /**
   * The action of a top-level form made by {@link #make}.
   */
  @FunctionalInterface
  public interface Action {
    void perform(Session session, Locator locator) throws Failures.FormFailed;
  }

  /**
   * Make a top-level form, where the action is given as
   * a function.
   */
  public static TopLevelForm
  make(Style style, Form subform, String longDescr,
       final Action function) {
    return
      new TopLevelForm(style, subform, longDescr) {
        public void perform(Session session, Locator locator)
        throws Failures.FormFailed {
          function.perform(session, locator);
        }
      };
  }

  /**
   * Make a top-level form, where setup and action are given as
   * functions.
   */
  public static TopLevelForm
  make(Style style, Form subform, String longDescr,
       final Consumer<Session> setupFun,
       final Action performFun) {
    return
      new TopLevelForm(style, subform, longDescr) {
        public void setup(Session session) {
          setupFun.accept(session);
        }
        public void perform(Session session, Locator locator)
        throws Failures.FormFailed {
          performFun.perform(session, locator);
        }
      };
  }

  /**
   * Parse the given sequence of words starting at index. By default,
   * this parses the subform and performs the action on success.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start)
  throws Failures.FormFailed {
    int start1 = subform.parse(session, locator, words, start);
    if (start1 >= 0) {
      perform(session, locator);
      return start1;
    } else
      return -1;
  }

  /**
   * Unparse the internal state of the form.
   */
  public String[] unparse(Session session) {
    return subform.unparse(session);
  }

  /**
   * Get the sub-forms.
   */
  public Form[] getSubforms() {
    return new Form[]{subform};
  }

  /**
   * Get syntax description
   */
  public String getSyntax() {
    if (longDescr != null)
      return subform.getSyntax() + " - " + longDescr + ".";
    else
      return subform.getSyntax() + ".";
  }

}
