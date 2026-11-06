package zeta.session;

import zeta.util.Term;

/**
 * The abstract class of Messages send by tools to the session system.
 *
 * @see Listener
 * @see Session#send
 * @see Diag
 * @see DebugMessage
 * @see ProgressMessage
 * @see ResultMessage
 *
 * @version $Id: Message.pizza,v 1.3 1998/09/06 22:38:14 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * Deliberately NOT sealed, unlike the ADTs in this package: besides the five message classes
 * here, `zeta.tools.sh.EvalMessage` and `FinishedMessage` extend it, and the shell is not
 * ported yet. It is an open extension point rather than a closed set of cases.
 */
public abstract class Message extends Term {

}
