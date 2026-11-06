package zeta.util;

/** 
 * A class for managing debug prints. <p>
 *
 * Provides several methods for convenient printing of debug messages.
 * By default, debug messages are send to standard error. It is
 * possible to statically configure another output channel or a
 * session to receive the debug messages. <p>
 *
 * Debug printing itself is done on an instance of a DebugWriter --
 * when creating such an instance, it is decided on base of a parameter
 * whether debug prints are actually done or not. <p>
 *
 * Typical usage is as follows:
 *
 * <pre>
 * class MyClass {
 *   static DebugWriter debug = 
 *      DebugWriter.make(toolName,"debug.MyClass");
 *
 *   .... {
 *      debug.send("reaching foo");
 *      ...
 *      debug.send("reaching bar");
 *      ...
 *   }
 * }
 * </pre>
 *
 * @version $Id: DebugWriter.pizza,v 1.2 1998/09/13 15:09:16 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * A straight translation: this file used no Pizza feature at all, so only the imports and the
 * file name change. It keeps its two-receiver design -- messages go to a `Session` if one is
 * registered, else to a `PrintWriter` on stderr -- and the pair of Active/Deactive subclasses
 * that makes a disabled debug writer cost nothing at the call site.
 *
 * `zeta.session.Session` and `DebugMessage` are still binary here; neither has a mangled
 * method, so there is nothing to bridge.
 *
 * One Pizza artefact survives at the call sites below: `new DebugMessage(fmt)` does not
 * compile, because `DebugMessage` is a `case` class and carries a static factory of the same
 * name as the class, which hides the constructor from javac entirely ("cannot find symbol:
 * constructor DebugMessage(Format)").  `DebugMessage.DebugMessage(fmt)` -- whose body is that
 * very `new` -- is what Pizza-compiled callers use, and what is used here.  It disappears when
 * `zeta.session` is ported and the factories go.
 */
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import zeta.format.Format;
import zeta.session.DebugMessage;
import zeta.session.Session;

public abstract class DebugWriter {

  /**
   * The PrintWriter for debug messages.
   */
  private static PrintWriter writer =
    new PrintWriter(new FileOutputStream(FileDescriptor.err), true);

  /**
   * A session to receive debug messages.
   */
  private static Session session = null;


  /**
   * Set the given print-writer to be used for all debug messages.
   * An older print-writer or session is overwritten as a receiver.
   */
  public static void setReceiver(PrintWriter writer){
    DebugWriter.writer = writer; DebugWriter.session = null;
  }

  /**
   * Set the given session to be used for all debug messages.
   * An older print-writer or session system is overwritten as a receiver.
   */
  public static void setReceiver(Session session){
    DebugWriter.writer = null; DebugWriter.session = session;
  }

  /**
   * Return an instance of a debug writer, using the given tool name in
   * session messages. If the given boolean is true, then the returned
   * debug writer will issue its messages to the currently configured
   * receiver.  Otherwise all output to the returned debug writer will
   * be ignored.  */
  public static DebugWriter make(Name name, boolean active){
    if (active)
      return new ActiveWriter(name);
    else
      return new DeactiveWriter();
  }

  /**
   * Return an instance of a debug writer, using the given tool name in
   * session messages. If the given property string
   * is defined in the system property set, then the returned debug
   * writer will issue its messages to the currently configured receiver.
   * Otherwise all output to the returned debug writer will be ignored.
   */
  public static DebugWriter make(Name name, String property){
    return make(name, System.getProperty(property) != null);
  }

  /**
   * Send the given string to this debug printer.
   */
  public abstract void send(String message);

  /**
   * Send the given format to this debug printer.
   */
  public abstract void send(Format format);

  /**
   * Send the given string and the given object to this debug printer.
   * If this is an active printer, the object will be formatted for
   * printing using the generic format method.
   *
   * @see Format.buildFormat
   */
  public abstract void send(String message, Object object);

  /**
   * Send the given string and the given two objects to this debug printer.
   * If this is an active printer, the object will be formatted for
   * printing using the generic format method.
   *
   * @see Format.buildFormat
   */
  public abstract void send(String message, Object object1, Object object2);

  /**
   * Send the given string and the given three objects to this debug printer.
   * If this is an active printer, the object will be formatted for
   * printing using the generic format method.
   *
   * @see Format.buildFormat
   */
  public abstract void send(String message, Object object1, Object object2, 
			    Object object3);

  /**
   * Return true if this is an active debug writer.
   */
  public abstract boolean isActive();


  /**
   * The class of active writers.
   */
  private static class ActiveWriter extends DebugWriter {

    private Name thisName;

    private ActiveWriter(Name name){
      thisName = name;
    }
    
    public void send(String s){
      if (session != null)
	session.send(thisName, DebugMessage.DebugMessage(Format.literal(s)));
      else
	writer.println(s);
    }

    public void send(Format f){
      if (session != null)
	session.send(thisName, DebugMessage.DebugMessage(f));
      else
	writer.println(f);
    }

    public void send(String s, Object ob){
      Format fmt = Format.block(Format.literal(s + ": "),
				Format.indent(Format.buildFormat(ob)));
      if (session != null)
	session.send(thisName, DebugMessage.DebugMessage(fmt));
      else
	writer.println(fmt);
    }

    public void send(String s, Object ob1, Object ob2){
      Format fmt = Format.block(Format.literal(s + ": "),
				Format.indent(
				  Format.line(
				    Format.append(Format.buildFormat(ob1),
						 Format.literal(";")),
				    Format.buildFormat(ob2)
				  )
				));
      if (session != null)
	session.send(thisName, DebugMessage.DebugMessage(fmt));
      else
	writer.println(fmt);
    }

    public void send(String s, Object ob1, Object ob2, Object ob3){
      Format fmt = Format.block(Format.literal(s + ": "),
				Format.indent(
				  Format.line(
				    Format.append(Format.buildFormat(ob1),
						  Format.literal(";")),
				    Format.append(Format.buildFormat(ob2),
						  Format.literal(";")),
				    Format.buildFormat(ob3)
				  )
				));
      if (session != null)
	session.send(thisName, DebugMessage.DebugMessage(fmt));
      else
	writer.println(fmt);
    }

    public boolean isActive(){
      return true;
    }
  }

  /**
   * The class of deactive writers.
   */
  private static class DeactiveWriter extends DebugWriter {
    public void send(String s){}
    public void send(Format f){}
    public void send(String s, Object ob){}
    public void send(String s, Object ob1, Object ob2){}
    public void send(String s, Object ob1, Object ob2, Object ob3){}
    public boolean isActive(){ return false; }
  }

  
}

