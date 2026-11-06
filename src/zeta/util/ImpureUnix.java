package zeta.util;

import java.io.File;
import java.util.Date;

/**
 * This class provides the implementation of system dependent
 * functionality under Unix.
 *
 * <h2>Port notes</h2>
 *
 * Plain Java 1.1.  The native `catchInterrupts` / `readAndClearInterrupts`
 * declarations and the `System.loadLibrary("ZetaUnix")` call are all
 * commented out in the source; the concrete implementations are no-ops.
 * The empty static initialiser block is kept to match the reference bytecode
 * which has a `static {}` entry.
 */
public class ImpureUnix extends Impure {

  static {
    /*
    try {
       System.loadLibrary("ZetaUnix");
    }
    catch (UnsatisfiedLinkError e) {
       throw new FatalError(
           "cannot load Unix library (libZetaUnix.so).");

    }
    */
  }

  @Override
  public Date lastModified(File file) {
    // Under Linux at least, the value of lastModified seems to be
    // milliseconds since Jan 1, 1970. So we can do it as below.
    // FIXME: check for Solaris
    return file.isFile() ? new Date(file.lastModified()) : new Date();
  }

  @Override
  public synchronized void catchInterrupts() {}

  @Override
  public synchronized int readAndClearInterrupts() { return 0; }

  // /** */
  // public synchronized native void catchInterrupts();

  // /** */
  // public synchronized native int readAndClearInterrupts();

}
