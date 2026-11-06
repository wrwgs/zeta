package zeta.session;

import zeta.util.FatalError;

/**
 * The entry point to the session system: creates and opens projects.
 *
 * @version $Id: SessionManager.pizza,v 1.3 1998/09/06 22:38:16 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * The implementation is loaded reflectively by name, which is why
 * `zeta.tools.sman.SessionManagerImpl` has no `Class` constant pointing at it anywhere and
 * `abi_gate.py` has to seed entry points explicitly (§9.5).
 *
 * `openProject`/`createProject` take a project *name*, which `SessionManagerImpl` turns into
 * `new File(projectName)` -- a relative path, so the store lives in the process's working
 * directory. That is what makes the harness's one-session-per-artifact isolation work, and
 * also what `Storage.create` then fails to create (`docs/serialization.md`).
 */
public abstract class SessionManager {

  /** */
  public static Session create(String projectName) {
    return getManager().createProject(projectName);
  }

  /** */
  public static Session open(String projectName)
      throws Failures.NoSuchEntity {
    return getManager().openProject(projectName);
  }

  /** */
  private static SessionManager manager = null;

  /** */
  public static SessionManager getManager() {
    if (manager == null) {
      // FIXME: setting up the name of the session manager by a Java property
      try {
        Class managerClass = Class.forName("zeta.tools.sman.SessionManagerImpl");
        manager = (SessionManager) managerClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new FatalError("cannot create a default session manager: " + e);
      }
    }
    return manager;
  }

  /**
   * Set the default session manager.
   */
  public static void setManager(SessionManager manager) {
    SessionManager.manager = manager;
  }

  /**
   * The creation method of a session manager.
   */
  public abstract Session createProject(String projectName);

  /**
   * The open method of a session manager.
   */
  public abstract Session openProject(String projectName)
      throws Failures.NoSuchEntity;
}
