package zeta.content.zirp;

import zeta.util.Annotation;
import zeta.util.Name;
import zeta.util.Slot;

/** Used names annotation slot.
  *
  * This slot is provided at all <code>absy.Item</code>
  * terms which contain Z fragments. It lists all the global
  * names (schemas or constants) which are applied in the
  * Z fragment. Names locally bounded are not listed.
  *
  * @version $Id: Used.pizza,v 1.1.1.1 1998/09/01 10:51:18 wg Exp $
  *
  * A slot class with no ADT of its own: the slot's value type is `Name[]`.  This is what the
  * session manager's dependency graph is built from -- the names an item uses are what decide
  * which other units it depends on.
  */
public class Used {

  /** The slot object. */
  public static final Slot slot = Slot.register(Used.class.getName(),
                                                Name[].class);

  /** Set value of used annotation slot. */
  public static void put(Annotation an, Name[] names) {
    slot.put(an, names);
  }

  /** Get value of used annotation slot. */
  public static Name[] get(Annotation an) {
    return (Name[]) slot.get(an);
  }

  /** Test whether used annotation slot is provided. */
  public static boolean isProvided(Annotation an) {
    return slot.isProvided(an);
  }
}
