package zeta.tools.sh;

import zeta.agl.Enum;
import zeta.agl.HashSet;
import zeta.agl.Set;
import zeta.form.Form;
import zeta.format.Format;
import zeta.session.DebugMessage;
import zeta.session.ComputationInterrupted;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.ProgressMessage;
import zeta.session.ResultMessage;
import zeta.session.Session;
import zeta.util.Name;

/**
 * The state object shared by the classes of the shell. Provides
 * session initialization, session object, and shortcuts for session
 * operations.
 *
 * @version $Id: ShellState.pizza,v 1.11 2000/05/14 17:43:32 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p><b>The evaluation protocol is unchanged</b> (zesurrect-plan.md 6), because the harness
 * driver depends on it: {@link #doEval} runs each command on a fresh thread, at a priority one
 * below the caller's, serialised against other evaluations by the monitor of the
 * {@link CommandInterpreter}; it sends an {@link EvalMessage}, evaluates, sends a
 * {@link FinishedMessage} on success, swallows {@code FormFailed} (diagnostics were already
 * sent), reports {@link ComputationInterrupted} as {@code computation interrupted}, and
 * hands any other throwable to {@code session.abort}.  In a {@code finally} the thread removes
 * itself from {@code evaluations}, and if that leaves the set empty it does
 * {@code synchronized (state) state.notifyAll()} -- which is what wakes the prompter thread of
 * {@link BatchCommander} to print the next prompt.
 *
 * <p>PORT CHANGE (evaluations, 1998 race fixed): {@code evaluations} was updated by
 * read-modify-write without a lock -- {@code include} on the reader thread in
 * {@link #doEval}, {@code exclude} on each evaluation thread in its {@code finally}, and
 * enumerated by {@link #killEvaluation} on the reader thread.  It is a {@code zeta.agl.HashSet},
 * a persistent set built on version arrays where an update hands the table to the new value,
 * so unsynchronised concurrent updates do not merely lose one -- they can corrupt the shared
 * table.  A lost or corrupt update leaves a finished thread in the set (no prompt after the
 * last command: the harness sees a hang) or drops a running one (an early prompt).  All
 * accesses now hold this ShellState's monitor, the one the protocol already uses for the
 * prompter's wait/notifyAll; the exclude and the emptiness test are one atomic step with the
 * notify.  The protocol and every printed byte are unchanged: only interleavings that were
 * data races are excluded.
 *
 * <p>PORT CHANGE (cancellation; zesurrect-plan.md 2): {@link #killEvaluation} called
 * {@code Thread.stop()} on every evaluation thread, which throws
 * {@code UnsupportedOperationException} since JDK 20 -- so {@code interrupt} during a running
 * command ended the session.  It now calls {@code Thread.interrupt()}, and the tools unwind
 * cooperatively with {@link ComputationInterrupted} at their safe points (see that class).
 * {@link #doEval} catches it where it caught {@code ThreadDeath} and sends the same
 * {@code computation interrupted} diagnostic.  Two differences, both forced:
 * <ul>
 * <li>it does not rethrow.  {@code ThreadDeath} had to be rethrown for the thread to die, and
 * the default uncaught-exception handler ignores it silently; any other throwable escaping
 * {@code run} is printed to stderr.  Returning normally ends the thread through the same
 * {@code finally}, so the {@code evaluations}/prompt protocol is unchanged.
 * <li>an evaluation that was still queued on the interpreter's monitor when the interrupt
 * came (2002: stopped as well, since {@code killEvaluation} stopped every thread in
 * {@code evaluations}) checks once after acquiring it, and is reported as interrupted without
 * running.  The flag cannot reach it earlier: a thread blocked on monitor entry ignores
 * interrupts.
 * </ul>
 * {@code killEvaluation} no longer locks each thread before signalling it.  That
 * {@code synchronized (t)} was the other half of a critical-section protocol for
 * {@code Thread.stop}: code that must not be stopped half-way (the native ZAM step in
 * {@code ZapAdaptor}) ran inside {@code synchronized (Thread.currentThread())}.  An interrupt
 * only sets a flag, so there is nothing left to exclude, and the lock would merely block the
 * reader thread for the length of a ZAM step.  With no evaluation running {@code interrupt} is
 * a no-op, as before; it still prints no prompt of its own (it is handled on the reader
 * thread, not as an evaluation), also as before.
 *
 * <p>The Pizza closure in {@code killEvaluation} assigned the captured local {@code killed};
 * it is a loop over the same enumeration here, which is what {@code Aggregate.apply} does.
 * The lifted {@code $closure$} method, the {@code ShellState$$closures} class and the
 * {@code access$} accessors are gone; nothing outside this package referenced them.
 */
public class ShellState {

  /**
   * The session object
   */
  final Session session;

  /**
   * The command interpreter.
   */
  CommandInterpreter interpreter = null;

  /**
   * An object holding the GUI commander component, for uses as
   * a parent in dialogs. May be null.
   */
  Object master = null;


  /**
   * The name of the shell.
   */
  static final Name shellName = new Name("SHELL");


  /**
   * A default locator to be taken for diagnostics associated with the shell.
   */
  static Locator defaultLocator = new ShellLocator("<zeta-shell>");


  /**
   * Make a new shell state.
   */
  ShellState(Session session) {
    this.session = session;
  }


  /**
   * Send a shell debug message.
   */
  void sendDebug(String message) {
    session.send(shellName, new DebugMessage(Format.literal(message)));
  }

  /**
   * Send a shell debug message.
   */
  void sendDebug(Format message) {
    session.send(shellName, new DebugMessage(message));
  }

  /**
   * Send a shell progress.
   */
  void sendProgress(String message) {
    session.send(shellName, new ProgressMessage(Format.literal(message)));
  }

  /**
   * Send a shell result.
   */
  void sendResult(Format format) {
    session.send(shellName, new ResultMessage(format));
  }

  /**
   * Send a shell diagnostics.
   */
  void sendDiag(Locator loc, String message) {
    sendDiag(new Diag(loc,
                      Diag.Category.Error,
                      message));
  }

  /**
   * Send a shell diagnostics.
   */
  void sendDiag(Diag diag) {
    session.send(shellName, diag);
  }

  /**
   * Evaluate the sequence of words in an asynchronous thread.
   * Dispatches eval message for listeners interested in that evaluation
   * has started.<p>
   *
   * During evaluation, the command interpreter is locked in order
   * to avoid interleaving evaluations.
   */
  void doEval(final Form form,
              final Locator locator,
              final String[] words) {
    sendDebug(Format.build(words).toString());
    // FIXME: EFFICIENCY: getForms
    final Thread client = Thread.currentThread();
    final ShellState state = this;

    ///////////////////////////////////////////////////////////////////////
    // HACK FOR INTERRUPT
    if (words.length > 0 && words[0].equals("interrupt")) {
      killEvaluation();
      return;
    }
    ///////////////////////////////////////////////////////////////////////

    Thread evaluation =
      new Thread() {
        public void run() {
          // lower priority relative to client: Whenever the
          // client (GUI) can run, give it precedence
          // PROBLEM: Java seems to eat idle time
          Thread self = Thread.currentThread();
          self.setPriority(client.getPriority() - 1);
          Thread.yield();
          try {
            synchronized (interpreter) {
              session.send(locator.getTool(), new EvalMessage(locator, words));
              try {
                // PORT CHANGE: an interrupt that came while this evaluation was queued on
                // the interpreter's monitor (see the class comment).
                ComputationInterrupted.checkpoint();
                interpreter.eval(form, locator, words);
                session.send(locator.getTool(),
                             new FinishedMessage(locator, words));
              }
              catch (Failures.FormFailed e) {
                // errors reported
              }
              catch (ComputationInterrupted e) {
                // PORT CHANGE: was catch (ThreadDeath e) { ...; throw e; }.  Same diagnostic;
                // not rethrown, since the thread ends here anyway (see the class comment).
                session.send(locator.getTool(),
                             new Diag(locator,
                                      Diag.Category.Error,
                                      "computation interrupted"));
              }
              catch (Throwable e) {
                session.abort(e);
              }
            }
          }
          finally {
            synchronized (state) {
              evaluations = evaluations.exclude(this);
              if (evaluations.size() == 0) {
                state.notifyAll();
              }
            }
          }
        }
      };
    synchronized (this) {
      evaluations = evaluations.include(evaluation);
    }
    evaluation.start();
  }

  /** Running evaluation threads. Guarded by the monitor of this ShellState (see the class
    * comment, "PORT CHANGE (evaluations)"). */
  private Set<Thread> evaluations = new HashSet<Thread>();


  /**
   * Kill the current evaluation. Returns true if there was an
   * evaluation to kill, false otherwise. The evaluation is
   * interrupted cooperatively and unwinds at its next safe point
   * (see the class comment); this method does not block.
   */
  boolean killEvaluation() {
    boolean killed = false;
    synchronized (this) {
      for (Enum<Thread> e = evaluations.enumerate(); e.more(); e.advance()) {
        // PORT CHANGE: was synchronized (t) { t.stop(); } -- see the class comment.
        e.current().interrupt();
        killed = true;
      }
    }
    return killed;
  }

}
