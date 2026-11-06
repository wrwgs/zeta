package zeta.util;

import java.io.File;
import java.util.Date;

/**
 * This class provides system depend functionality, not
 * supported by "100% pure Java".
 *
 * This stuff has become obsolte since JDK 1.2 and ZETA 1.5,
 * but is still here for pure laziness.
 *
 * <h2>Port notes</h2>
 *
 * The author's own note above is now entirely true: `lastModified` is `File.lastModified` with
 * a `Date` wrapper, and both interrupt methods are no-ops in the only implementation
 * ({@link ImpureUnix}), whose `native` declarations and `loadLibrary` call are commented out.
 * So the whole abstraction is vestigial and could collapse into two helper methods -- but the
 * `driver` field is public and read elsewhere, so that is a change for the pass that removes
 * the bridge layer, not now.
 *
 * `runInterruptable` is the interesting one: its real body, a watchdog thread calling
 * `Thread.stop()`, is commented out and replaced by `runnable.run()`. That is lucky, because
 * `Thread.stop()` was removed in JDK 20 and is one of the two reasons the 2002 build cannot
 * simply be run on a current JVM (the other is in `ShellState.killEvaluation`). The commented
 * code is kept verbatim: it documents what the feature was meant to do, and the port's
 * eventual answer -- cooperative interruption -- has to replace that intent, not this stub.
 */
public abstract class Impure {

  /**
   * The configured "impure" driver.
   */
  public static Impure driver = null;

  static {
    // Select the impure implementation
    // FIXME: use properties!
    try {
      Class impureClass = Class.forName("zeta.util.ImpureUnix");
      driver = (Impure) impureClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new FatalError(
          "cannot load implementation of system dependend functionality: " + e);
    }
  }

  /**
   * Check the real modification date of a file.<p>
   *
   * The standard method <code>File.lastModified</code> does unfortunately
   * only return a system dependend long value, which is not
   * guaranteed to be convertible to a date.
   *
   * @see java.io.File#lastModified
   */
  public abstract Date lastModified(File file);

  /**
   * Configure the running JVM to catch process interrupts (SIGINT). <p>
   *
   * The standard API seem not to provide a way for doing so.
   */
  public abstract void catchInterrupts();

  /**
   * Read and clear the number of process interrupts catched so far. <p>
   *
   * Since there is most likely no portable way to do any
   * Java operation from within a signal handler, interrupts can be
   * recognized by the application only by polling with this method.
   */
  public abstract int readAndClearInterrupts();

  /**
   * A generic implementation for running an interruptable operation.
   * Runs the given runnable. If a process interrupt occures,
   * execution of the runnable will be terminated, and the
   * method returns false, otherwise true. <p>
   *
   * The runnable may implement non-interrruptable critical sections
   * by synchronizing on itself. Interruption does only occures if
   * a lock can be obtained for the runnable.  <p>
   *
   * By default, this method is implemented by a daemon which polls with
   * method <code>readAndClearProcessInterrupts</code> in regular time
   * intervalls (approx. 100ms). For some systems the method may provide
   * an implementation not based on polling.
   */
  public boolean runInterruptable(final Runnable runnable) {
    runnable.run();
    return true;
    /*
    final Thread oper = new Thread(runnable);
    final boolean[] stopped = new boolean[]{false};
    final Thread watchdog =
      new Thread(){
	public void run(){
	  Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
	  Thread.yield();
	  do {
	    if (readAndClearInterrupts() > 0){
	      stopped[0] = true;
	      synchronized (runnable){
	        oper.stop();
	      }
	      Thread.yield();
	      Thread.yield();
	      return;
	    }
	    try {
	      Thread.sleep(pollingIntervall);
	    }
	    catch (InterruptedException e){}
	  } while (true);
	}
      };
    readAndClearInterrupts();
    watchdog.start();
    oper.start();
    try {
      oper.join();
    }
    catch (InterruptedException e){}
    catch (NoClassDefFoundError e){
      // this seems to happen if we get interrupts during IO
      // hopefully, it is harmless
    }
    watchdog.stop();
    try {
      watchdog.join();
    }
    catch (InterruptedException e){}
    return !stopped[0];
    */
  }

  private static long pollingIntervall = 100;
}
