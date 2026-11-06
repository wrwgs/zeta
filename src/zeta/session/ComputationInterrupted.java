package zeta.session;

/**
 * Unwinds a computation that the user has interrupted.
 *
 * <h2>Port notes</h2>
 *
 * <p>PORT CHANGE (new class; replaces {@code Thread.stop}).  In 2002 the shell's
 * {@code interrupt} command cancelled a running command with {@code Thread.stop()}, which
 * raised {@code ThreadDeath} at an arbitrary instruction of the evaluation thread.
 * {@code Thread.stop} throws {@code UnsupportedOperationException} since JDK 20, so
 * cancellation is now cooperative: {@code ShellState.killEvaluation} sets the evaluation
 * thread's interrupt flag, long-running work tests it at safe points with
 * {@link #checkpoint}, and unwinds with this exception, which {@code ShellState.doEval}
 * reports as {@code computation interrupted} -- the diagnostic the {@code ThreadDeath}
 * handler sent.
 *
 * <p><b>Why an {@code Error}.</b>  It takes over {@code ThreadDeath}'s role one-for-one, and
 * {@code ThreadDeath} is an {@code Error} so that ordinary {@code catch (Exception e)}
 * handlers do not intercept it.  Being one too, this exception passes exactly the handlers
 * {@code ThreadDeath} passed, and the only handlers that see it are the ones that already had
 * to decide what to do with {@code ThreadDeath} ({@code catch (Throwable)} in
 * {@code ShellState.doEval}, {@code catch (Error)} in the checker's worker threads).  It is
 * not a case of {@link Errors}: that is a sealed ADT whose shape is ABI, and its cases are
 * fatal session errors that {@code Session.abort} turns into an exit.
 *
 * <p><b>Why here.</b>  It is part of the contract between the shell, which interrupts and
 * reports, and the tools, which check and throw -- the same position as {@link Failures}
 * (whose {@code FormFailed} {@code doEval} also catches) and {@link Errors}.
 *
 * <p><b>The interrupt flag.</b>  As with {@code InterruptedException}, the request is
 * carried either by the thread's interrupt flag or by this exception in flight, never by
 * both and never by neither: {@link #checkpoint} clears the flag when it throws, and an
 * {@code InterruptedException} (which the JVM has already cleared the flag for) is converted
 * with {@link #of}.  Whoever catches this exception owns the request: {@code doEval} reports
 * it; any other handler must rethrow it.
 */
public final class ComputationInterrupted extends Error {

  private static final long serialVersionUID = 1L;

  /** Construct the exception. */
  public ComputationInterrupted() {
    super("computation interrupted");
  }

  /** Convert an {@code InterruptedException} caught at a blocking checkpoint. */
  public static ComputationInterrupted of(InterruptedException e) {
    ComputationInterrupted ci = new ComputationInterrupted();
    ci.initCause(e);
    return ci;
  }

  /**
   * A safe point: if the current thread has been interrupted, clear the request and unwind.
   * Call it only where the state the caller leaves behind is consistent, or where the
   * caller's handler restores it before rethrowing.
   */
  public static void checkpoint() {
    if (Thread.interrupted()) throw new ComputationInterrupted();
  }
}
