package zeta.format;

import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Instances of the FormatInfo class are used in the process of
 * formatting with the <code>Format.buildFormat</code> method. They
 * are used to detect sharing and cylcles in object structures.
 *
 *
 * @see Format#buildFormat
 * @see Formattable
 *
 * @author Jacob Wieland (ughosage@cs.tu-berlin.de)
 * @version $Id: FormatInfo.java,v 1.1.1.1 1998/09/01 10:51:13 wg Exp $
 */

public class FormatInfo {

  /**
   * Return an object identity code.
   */
  // PORT CHANGE (correctness, not just determinism).  The original was
  //
  //     private static Integer code(Object ob){
  //       return ob == null ? new Integer(0)
  //                         : new Integer(System.identityHashCode(ob));
  //     }
  //
  // and `visited` was a Hashtable keyed by that Integer.  An identity hash code is not
  // unique, so two DIFFERENT objects that happen to share one collide in the table and the
  // second is reported as already visited -- the printer then emits a back-reference to an
  // unrelated node, silently misrepresenting the term graph.  With ~16,500 nodes in the
  // Toolkit's checked-absy dump a collision is a few percent likely per run by the birthday
  // bound, which is exactly the rate at which that dump was observed to vary.
  //
  // IdentityHashMap compares by reference, which is what "has this object been visited"
  // actually means: exact, and incidentally deterministic.  `code` is gone; null is a
  // legitimate key for an IdentityHashMap, so the null case needs no special value.
  
  /**
   * Fields.
   */
  private Map visited = new IdentityHashMap(100);
  // same identity-hash-collision defect as `visited` above; same fix
  private Map generalTypes = new IdentityHashMap(100);
  private Class ObjectClass = Object.class;

  /**
   * Test whether the given object has been visited before with
   * this FormatInfo.
   */
  public boolean visited(Object ob) {
    return visited.containsKey(ob);
  }

  /**
   * Return a format for representing a label of a previously
   * visited object. 
   */
  public Format getLabel(Object ob){
    return Format.literal("<" + visitNumber(ob) + "<");
  }

  /**
   * Visit the given object the first time, generating 
   * a label for it and returnings its format.
   */
  public Format visitAndGetLabel(Object ob){
    return Format.literal("<" + visit(ob) + "<");
  }

  /**
   * Access to the number of a label of visited object.
   */
  public int visitNumber(Object ob) {
    return ((Integer)visited.get(ob)).intValue();
  }

  /** 
   * Visit the given object and return its label number.
   */
  public int visit(Object ob) {
    if (visited.containsKey(ob)){
      return ((Integer)visited.get(ob)).intValue();
    }

    int n = visited.size() + 1;
    visited.put(ob, Integer.valueOf(n));

    return n;
  }

  /**
   * Remove the given object from the set of visited objects.
   * The next time it is formatted it will be treated has if it
   * had never been seen before.
   */
  public void unvisit(Object ob){
    visited.remove(ob);
  }


  /**
   * Assign a general type to the given object. This is used
   * by the internal generic format functions.
   */
  public void addGeneralType(Object ob, Class type) {
    if (ob != null && type != ObjectClass) {
      // only _one_ general type is allowed at any one time
      // some objects coule be part of several other objects/arrays
      // and thus have several general types (dependent on the context)
      if (generalTypes.containsKey(ob))
	generalTypes.remove(ob);
      generalTypes.put(ob,type);
    }
  }

  /**
   * Retrieve the general type of an object. Used by the internal
   * generic format functions.
   */
  public Class generalType(Object ob) {
    if (generalTypes.containsKey(ob))
      return (Class)generalTypes.get(ob);
    else
      return ObjectClass;
  }

}
