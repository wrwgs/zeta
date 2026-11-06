package zeta.session;

import zeta.util.Name;

/**
 * The interface of message listeners.
 *
 * @version $Id: Listener.pizza,v 1.2 1998/09/06 22:38:13 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 */
public interface Listener {

  /**
   * Receive a message.
   */
  public void receive(Name sender, Message message);
}
