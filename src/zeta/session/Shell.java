package zeta.session;

import zeta.form.TopLevelForm;

/**
 * The interface a command shell provides to the session system.
 *
 * @version $Id: Shell.pizza,v 1.4 2000/05/08 07:07:43 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * Implemented by `zeta.tools.sh.BatchCommander` and `GuiCommander`, both still binary.
 */
public interface Shell {

  /** */
  public void exec(String prompt, TopLevelForm form)
      throws Failures.FormFailed;

  /** */
  public void notifyFormRegister(TopLevelForm form);

  /** */
  public void notifyFormUnregister(TopLevelForm form);
}
