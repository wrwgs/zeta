package zeta.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** A Slot defines an entry for storing objects in an annotation.
  *
  * To add a new slot one typically uses the following code:
  * <pre>
  *   class Foo{
  *      public static final slot =
  *          Slot.register(Foo.class.getName(), Foo.class);
  *      public static Foo get(Annotation an){
  *          return (Foo)slot.get(an);
  *      }
  *      public static boolean isProvided(Annotation an){
  *          return slot.isProvided(an);
  *      }
  *      public static Annotation set(Annotation an, Foo foo){
  *         return slot.set(an, foo);
  *      }
  *      ... specific fields of Foo ...
  *   }
  * </pre>
  *
  * @version $Id: Slot.pizza,v 1.1.1.1 1998/09/01 10:51:12 wg Exp $
  * @author wg@cs.tu-berlin.de
  *
  * <h2>Port notes</h2>
  *
  * `registered` was a {@code pizza.util.Hashtable} -- Pizza's own generic collection, distinct
  * from `java.util.Hashtable` and carrying its own mangled methods. It is private and touched
  * only here, so it becomes a plain {@code HashMap} and this class stops depending on
  * `pizza.util` altogether.
  *
  * The serialized form is unchanged, and that is deliberate even though
  * `docs/serialization.md` concludes the port owes nothing to the 2002 store: `writeObject`
  * here writes only `name` and `type` explicitly, never calling `defaultWriteObject`, so the
  * stream does not depend on the field layout at all. Keeping the two methods as they are
  * costs nothing and keeps `Annotation`'s stream -- which embeds slots -- self-describing.
  *
  * Note what `readObject` does: it re-`register`s the slot by name, so a deserialized slot
  * picks up the *current* process's index. That is why the comment on it says reference
  * uniqueness cannot be guaranteed, and why indices are never serialized directly.
  */
public final class Slot implements Serializable {

  private static Map<String, Slot> registered = new HashMap<String, Slot>();
  private static Slot[] forIndex = new Slot[]{};
  private static int maxIndex = -1;

  private String name;
  private Class type;
  private int index;

  /** A flag which controls wether this slot should be excluded
    * from annotation formatting. */
  public boolean hideInFormat = false;

  /** Register a new annotation slot. If a slot of the given name
    * is already registered, it will be returned.
    * @param name  the name which identifies this annotation slot
    * @param type  the type of the object to be stored in the slot
    * @return  the slot object
    * @exception IllegalArgumentException  if a slot of this name
    *   is already registered, and the given type is not a subclass
    *   of the registered type
    */
  public static Slot register(String name, Class type)
      throws IllegalArgumentException {
    Slot slot = registered.get(name);
    if (slot != null) {
      if (!slot.type.isAssignableFrom(type)) {
        throw new IllegalArgumentException(
                    "re-registering slot " + name + ": "
                    + type.getName() + " is not a subclass of "
                    + slot.type.getName());
      }
      slot.type = type;
      return slot;
    }
    // create a new slot
    slot = new Slot();
    slot.name = name.intern();
    slot.type = type;
    slot.index = ++maxIndex;
    registered.put(name, slot);
    Slot[] newForIndex = new Slot[forIndex.length + 1];
    for (int i = 0; i < forIndex.length; i++) {
      newForIndex[i] = forIndex[i];
    }
    newForIndex[forIndex.length] = slot;
    forIndex = newForIndex;
    return slot;
  }

  private Slot() {}

  /** Return name of this annotation slot. */
  public String getName() {
    return name;
  }

  /** Return type of this annotation slot. */
  public Class getType() {
    return type;
  }

  /** Return application-unique index of this annotation slot. */
  public int getIndex() {
    return index;
  }

  /** Return maximal index currently assinged to a slot. */
  public static int getMaxIndex() {
    return maxIndex;
  }

  /** Return the slot associated with index. */
  public static Slot forIndex(int index) {
    return forIndex[index];
  }

  /** Get the value associated with this slot in annotation. */
  public Object get(Annotation an) {
    return an.get(index);
  }

  /** Test whether the value associated with this slot is provided
    * by annotation. */
  public boolean isProvided(Annotation an) {
    return an.isProvided(index);
  }

  /** Set the value associated with this slot in annotation.
    * @exception IllegalArgumentException  if object
    *   is not an instance of slot type.
    */
  public void put(Annotation an, Object object) {
    if (!type.isInstance(object)) {
      throw new IllegalArgumentException(
          "object is not an instance of slot type");
    }
    an.put(index, object);
  }

  /** Serialize slot. */
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(name);
    out.writeObject(type);
  }

  /** Unserialize annotation. */
  // because of this method we cannot guarantee reference
  // uniqueness of slots. However, slots are serialized only internally
  // in Annotation
  private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    name = (String) in.readObject();
    type = (Class) in.readObject();
    Slot slot;
    try {
      slot = Slot.register(name, type);
    } catch (IllegalArgumentException e) {
      throw new IOException("on deserialization of slot: " + e.getMessage());
    }
    index = slot.index;
    hideInFormat = slot.hideInFormat;
  }
}
