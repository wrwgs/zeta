package zeta.tools.nzap;

/** JNI based interface to the ZAM.
  *
  * @version $Id: ZamJniIface.pizza,v 1.9 2000/06/12 18:30:09 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>The 13 <code>native</code> instance methods are implemented in
  * <code>libZAM.so</code> as <code>Java_zeta_tools_nzap_ZamJniIface_&lt;method&gt;</code>
  * (header <code>port/zam/zeta_tools_nzap_ZamJniIface.h</code>).  Their names,
  * descriptors, and the class name are the JNI binding and must not change; they are
  * declared here in the 2002 order with the 2002 descriptors.</p>
  *
  * <p>The empty static initialiser is kept: the jar has a <code>&lt;clinit&gt;</code>
  * for it, and it is where the library was once loaded.  Loading now happens in
  * {@link #init}, as in the source.</p>
  */
public class ZamJniIface extends ZamIface {

  public void init() throws Fail {
    try {
      System.loadLibrary("ZAM");
      // call some native function to get library actually loaded
      // there seems to be a problem under NT with lazy loading
      // from threads (?)
      try {
        possiblyMore();
      }
      catch (Fail e) {
        ;
      }
    }
    catch (UnsatisfiedLinkError e) {
      sendError("cannot load ZAM library: " + e);
    }
  }


  public native void start(String[] fnames)
  throws Fail;

  public native int step(int steps)
  throws Fail;

  public native boolean possiblyMore()
  throws Fail;

  public native String getBinding(String varName)
  throws Fail;

  public native String setSetTarget(String varName)
  throws Fail;

  public native String setTermTarget(String varName, int i)
  throws Fail;

  public native String setSeqTarget(String varName, int i)
  throws Fail;

  public native void retSubTarget()
  throws Fail;

  public native boolean isSet(String varName)
  throws Fail;

  public native int isSeqGet(String varName)
  throws Fail;

  public native String isTermGet(String varName)
  throws Fail;

  public native String profileInfo()
  throws Fail;

  public native long noOfSteps()
  throws Fail;


  static {
  }

}
