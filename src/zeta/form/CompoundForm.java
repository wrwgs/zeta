package zeta.form;

/**
 * The abstract class of compound forms. <p>
 *
 * A compound form is composed from subforms.
 *
 * @version $Id: CompoundForm.pizza,v 1.1 1998/10/21 11:42:50 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * A straight translation.  The Pizza source imports `zeta.session.Session` and
 * `zeta.session.Failures` without using either; both are dropped.
 *
 * Not `sealed`: `zeta.tools.sh.ShellForms` and others derive from `TopLevelForm`, and
 * `zeta.form` itself has seven more compound subclasses, so the set is not closed.
 */
public abstract class CompoundForm extends Form {

  /** Return the direct subforms of this compound form. */
  public abstract Form[] getSubforms();

}
