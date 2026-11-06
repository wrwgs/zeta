package zeta.tools.nzap;

import zeta.format.Format;
import zeta.session.Diag;
import zeta.session.ProgressMessage;
import zeta.session.Session;
import zeta.util.FatalError;
import zeta.util.Name;

/** Abstract interface to the ZAM.
  *
  * @version $Id: ZamIface.pizza,v 1.9 2000/06/12 18:30:09 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>This class is a contract with native code, not only with Java.  libZAM's
  * <code>javadriver.cpp</code> calls back into <code>sendProgress</code> and
  * <code>sendError</code> by name and descriptor
  * (<code>GetMethodID(clazz, mth, "(Ljava/lang/String;)V")</code> on
  * <code>zeta/tools/nzap/ZamIface</code>), and throws <code>ZamIface$Fail</code> by
  * <code>FindClass</code>, which JNI's <code>ThrowNew</code> instantiates through its
  * <code>(String)</code> constructor.  The class name, those two methods, the nested
  * class name and that constructor are therefore fixed.  <code>SOLUTION</code>,
  * <code>MORE</code>, <code>NOMORE</code> and <code>UNDEF</code> are mirrored as
  * <code>#define</code>s in <code>port/zam/zeta_tools_nzap_ZamJniIface.h</code>.</p>
  *
  * <p>The static field order is the 2002 one: <code>zamToolName</code> is initialised
  * before <code>impl</code>, and <code>impl</code> is assigned in the static
  * initialiser, after its <code>null</code> default -- exactly as the reference
  * does.  The commented-out <code>zeta.zap.zamproc</code> switch is kept as a comment:
  * <code>ZamProcIface</code> never shipped in 1.5.</p>
  *
  * <p><code>Fail</code>'s constructor is package-private in the jar and stays so; JNI
  * <code>ThrowNew</code> does not check access.</p>
  */
public abstract class ZamIface {

  /** A session object. */
  private Session session = null;

  /** Set the session object. */
  public void setSession(Session s) {
    session = s;
  }

  /** Send a progress message. An interface for the actual
      driver */
  public void sendProgress(String message) {
    if (session != null) {
      session.send(zamToolName,
                   new ProgressMessage(Format.text(message, "")));
    } else
      FatalError.assertionFailed("ZAM IFACE session initialized");
  }
  private static Name zamToolName = new Name("ZAM");

  /** Send an error message. An interface for the actual
      driver */
  public void sendError(String message) {
    if (session != null) {
      session.send(zamToolName,
                   new Diag(ZapUtil.zamLocator,
                            Diag.Category.Warning,
                            Format.text(message, "")));
    } else
      FatalError.assertionFailed("ZAM IFACE session initialized");
  }

  /** Exceptions of the ZAM. */
  public static class Fail extends Exception {
    Fail(String msg) {
      super(msg);
    }
  }

  /** Initialize this interface to the ZAM. */
  public abstract void init() throws Fail;


  /** Start ZAM with the given units. */
  public abstract void start(String[] fnames)
  throws Fail;

  /** Perform number of execution steps. Return if a solution is found
      even with lesser steps. */
  public abstract int step(int steps)
  throws Fail;

  /** Tell whether there are (possibly) more solutions. */
  public abstract boolean possiblyMore()
  throws Fail;

  /** Return value of step function. */
  public static final int SOLUTION = 0;
  public static final int MORE = 1;
  public static final int NOMORE = 2;
  public static final int UNDEF = 3;

  /** Tell whether the variables binding is a set. */
  public abstract boolean isSet(String varName)
  throws Fail;

  /** Tell whether the variables binding is a sequence. return the
      length if so, otherwise -1. */
  public abstract int isSeqGet(String varName)
  throws Fail;

  /** Tell whether the variables binding is a term. Return name
   * of this term in mixfix notation, "_" representing placeholders
   * for arguments, other null. */
  public abstract String isTermGet(String varName)
  throws Fail;

  /** Set the current target to given variable, which must denote a
      set. Return name for the elements of this set. */
  public abstract String setSetTarget(String varName)
  throws Fail;


  /** Set the current target to ith field of the given variable, which must
      denote a term. Return name for the field of this term. */
  public abstract String setTermTarget(String varName, int i)
  throws Fail;


  /** Set the current target to ith element of the given variable, which must
      denote a sequence. Return name for the field of this term. */
  public abstract String setSeqTarget(String varName, int i)
  throws Fail;


  /** Get string representation of binding for variable. */
  public abstract String getBinding(String varName)
  throws Fail;

  /** Return from a sub-target. */
  public abstract void retSubTarget()
  throws Fail;

  /** Return profile information. */
  public abstract String profileInfo()
  throws Fail;

  /** Return no of steps. */
  public abstract long noOfSteps()
  throws Fail;


  /** The chosen implementation. */
  public static ZamIface impl = null;

  static {
    /*
    if (System.getProperty("zeta.zap.zamproc") != null)
      impl = new ZamProcIface();
    else
    */
    impl = new ZamJniIface();
  }


}
