package zeta.tools.util;

/** System command executor.
 *
 *  @version $Id: Executor.pizza,v 1.4 1999/03/09 13:20:47 buessow Exp $
 *
 *  Auxiliary class to execute system commands.
 *
 *  Executes system commands and produces diagnostics if execution fails
 *  (because of {@code IOException}, {@code InterruptedException},
 *  or exit value is not equal to zero).
 *
 * <h2>Port notes</h2>
 *
 * <p>Straight translation.  {@code ExecutorLocator} is a non-static inner class in both
 * the source and the jar (the jar carries a {@code this0} field of type
 * {@code Executor}).</p>
 *
 * <p>No {@code Thread.stop} is present.  The original
 * {@code Thread.currentThread().sleep(100)} (calling a static method via an instance
 * reference) is replaced with the idiomatic {@code Thread.sleep(100)};
 * the behaviour is identical.</p>
 *
 * <p>PORT CHANGE (cancellation; see {@link ComputationInterrupted}).  An
 * {@code InterruptedException} from the polling {@code sleep} was reported as
 * "execution of ... interrupted" and turned into a {@code false} return -- which consumed the
 * interrupt and left the process running.  Now the poll loop is a checkpoint (the
 * {@code sleep} and, for a process that keeps writing without ever producing the terminator,
 * an explicit check per byte pair), and an interrupt destroys the process, sends the same
 * diagnostic, and unwinds with {@code ComputationInterrupted}.  Nothing in the ported tree
 * calls {@code Executor}; this keeps it from swallowing a cancellation should anything start
 * to.</p>
 */

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Adaptor;
import zeta.session.ComputationInterrupted;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.util.Name;


public class Executor {

  private Session session;
  private Adaptor tool;
  private Locator default_locator;

  private class ExecutorLocator extends Locator {
    public Name getTool() { return tool.getName(); }
    public Format toFormat(FormatInfo info) {
      return tool.getName().toFormat(info);
    }
  }


  /** Create Executor.
   *
   *  @param session  current session to send diagnostics.
   *  @param tool     calling tool (also for diagnostics).
   */
  public Executor(Session session, Adaptor tool) {
    this.session = session;
    this.tool = tool;
    this.default_locator = new ExecutorLocator();
  }

  /** Create Executor.
   *
   *  @param session          current session to send diagnostics.
   *  @param tool             calling tool (also for diagnostics).
   *  @param default_locator  locator to be used for diagnostics if no
   *                          specific locator is given.
   */
  public Executor(Session session, Adaptor tool, Locator default_locator) {
    this.session = session;
    this.tool = tool;
    this.default_locator = default_locator;
  }


  /** Execute command. */
  public boolean exec(String[] command, String terminator) {
    return exec(default_locator, command, terminator);
  }

  /** Execute command.
   *  Execute command and use {@code locator} for diagnostics. */
  public boolean exec(Locator locator, String[] command, String terminator) {
    Process p = null;
    try {
      p = Runtime.getRuntime().exec(command);

      StringBuffer out = new StringBuffer();
      StringBuffer err = new StringBuffer();
      InputStream out_stream = new BufferedInputStream(p.getInputStream());
      InputStream err_stream = new BufferedInputStream(p.getErrorStream());
      int matches = 0;
      byte[] term = terminator.getBytes();
      while (matches < term.length) {
        // PORT CHANGE (cancellation): checkpoint; see the class comment.
        ComputationInterrupted.checkpoint();

        while (out_stream.available() + err_stream.available() == 0)
          // PORT CHANGE: Thread.currentThread().sleep(100) -> Thread.sleep(100);
          // calling a static method via an instance reference is identical behaviour.
          Thread.sleep(100);

        if (out_stream.available() > 0) {
          byte b = (byte) out_stream.read();
          if (b == term[matches])
            matches++;
          else
            matches = 0;
          String s = new String(new byte[] { b });
          out.append(s);
        }
        if (err_stream.available() > 0) {
          byte b = (byte) err_stream.read();
          String s = new String(new byte[] { b });
          err.append(s);
        }
      }

      out_stream.close();
      err_stream.close();
      p.getOutputStream().close();

      /* it is somewhat hard to test, whether a process has exited
       * (still possible ;)
       */
      int exit;
      try {
        exit = p.exitValue();
      } catch (java.lang.IllegalThreadStateException e) {
        sendDiag(e.getMessage(), locator);
        p.destroy();
        exit = -1;
      }

    } catch (IOException e) {
      sendDiag("cannot execute `" + command[0] + "'", locator);
      return false;
    } catch (InterruptedException e) {
      // PORT CHANGE (cancellation): was `return false', with the process left running.
      throw interrupted(p, command, locator, ComputationInterrupted.of(e));
    } catch (ComputationInterrupted e) {
      throw interrupted(p, command, locator, e);
    }
    return true;
  }

  public boolean exec(Locator locator, String com1, String terminator) {
    return exec(locator, new String[] { com1 }, terminator);
  }

  public boolean exec(Locator locator, String com1, String com2,
                      String terminator) {
    return exec(locator, new String[] { com1, com2 }, terminator);
  }

  public boolean exec(String com1, String com2, String terminator) {
    return exec(new String[] { com1, com2 }, terminator);
  }

  public boolean exec(Locator locator, String com1, String com2,
                      String com3, String terminator) {
    return exec(locator, new String[] { com1, com2, com3 }, terminator);
  }

  public boolean exec(Locator locator, String com1, String com2, String com3,
                      String com4, String terminator) {
    return exec(locator,
                new String[] { com1, com2, com3, com4 },
                terminator);
  }

  public boolean exec(Locator locator, String com1,
                      String com2, String com3, String com4, String com5,
                      String terminator) {
    return exec(locator,
                new String[] { com1, com2, com3, com4, com5 },
                terminator);
  }

  public boolean exec(Locator locator, String com1,
                      String com2, String com3, String com4, String com5,
                      String com6, String terminator) {
    return exec(locator,
                new String[] { com1, com2, com3, com4, com5, com6 },
                terminator);
  }


  /** Kill the process of an interrupted execution and report it (PORT CHANGE, class
    * comment).  Returns the exception for the caller to throw. */
  private ComputationInterrupted interrupted(Process p, String[] command, Locator locator,
                                             ComputationInterrupted e) {
    p.destroy();
    sendDiag("execution of `" + command[0] + "' interrupted", locator);
    return e;
  }

  private void sendDiag(String msg, Locator locator) {
    if (session != null && locator != null)
      session.send(tool.getName(),
                   new Diag(locator,
                            Diag.Category.Error,
                            msg));
    else
      System.err.println(msg);
  }

}
