package zeta.util;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.format.Formattable;
import zeta.format.impl.FormatAppend;
import zeta.format.impl.FormatLine;
import zeta.format.impl.FormatText;

/** The abstract class Term serves as a superclass of classes
  * realizing "tree"-like data structures, defined by Pizza class
  * cases.  It implements some generic operations,
  * such as equality, cloning, formatting, internalization,
  * and serialization.
  *
  * @version $Id: Term.pizza,v 1.2 1998/11/23 00:05:50 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * This is the reflective heart of the ADT representation, and the reason
  * <b>`$$tag` must keep its exact spelling for the whole port, bridges or no bridges</b>:
  * {@link #getTagField} looks the field up by name,
  * {@code type.getName().replace('.','$') + "$$tag"}, so a case class whose tag field were
  * renamed would silently lose its discriminant here — and {@code equals} would then report
  * two different cases of the same ADT as equal.
  *
  * {@code Term} has no mangled methods: nothing in its signatures mentions a type parameter.
  * So it ports alone, and so does every one of the 280-odd classes that extend it.
  *
  * Two things are deliberately left as they are, and the reason is recorded here rather than
  * acted on. **Nothing in the 2002 jar calls `clone()`, `intern()` or `internArray()`** — the
  * constant pools show `intern`/`internArray` referenced only from this class, recursively,
  * and `clone` from nowhere at all. So:
  *
  * <ul>
  * <li>{@code clone()} is broken twice over and unreachable: its own FIXME notes that
  *     {@code newInstance()} needs a no-argument constructor, which most ZIRP classes do not
  *     have, and its copy loop handles only primitive field types — every reference field is
  *     left null. The {@code Cloneable} contract would be served by {@code super.clone()},
  *     which needs no constructor and copies every field; that is a design change to a public
  *     method, so it is flagged, not made.</li>
  * <li>{@code intern()} plus {@link TermDescr} and its three cases are a complete hash-consing
  *     facility for the abstract syntax that was never wired up — roughly 200 lines and four
  *     classes of dead code. Kept because it is public API and records real design intent;
  *     deleting it is a decision for the author, and is reversible in only one direction.</li>
  * </ul>
  *
  * Where the code is live it is modernised: `Vector` becomes `ArrayList`, which changes nothing
  * observable since both are appended to and copied out in order. `internTab` stays a
  * `Hashtable` — it belongs to the dead code above, and its synchronisation is the one
  * property worth not quietly dropping.
  *
  * One caveat carried over unchanged: `Class.getFields()` and `getDeclaredFields()` return
  * fields in <b>unspecified</b> order, and {@code toFormat} prints them in that order. HotSpot
  * returns declaration order in practice, which is why the dumps are reproducible, but it is
  * not guaranteed. Sorting would make it guaranteed and would change every `absy` artifact, so
  * it is left alone and noted.
  */
public abstract class Term implements Cloneable, Serializable, Formattable {

  /** Create a clone of the term. */
  // FIXME: newInstance() does only works for classes which
  // provide 0-ary constructor function, which most of the ZIRP
  // classes do not. Use method as done for intern()
  public Object clone() {
    Class type = getClass();
    Field[] fields = type.getFields();
    if (fields.length == 0) {
      // assume term is immutable
      return this;
    }
    try {
      Object inst = type.getDeclaredConstructor().newInstance();
      for (int i = 0; i < fields.length; i++) {
        Class fieldType = fields[i].getType();
        if (fieldType.equals(boolean.class)) {
          fields[i].setBoolean(inst, fields[i].getBoolean(this));
        } else if (fieldType.equals(byte.class)) {
          fields[i].setByte(inst, fields[i].getByte(this));
        } else if (fieldType.equals(char.class)) {
          fields[i].setChar(inst, fields[i].getChar(this));
        } else if (fieldType.equals(double.class)) {
          fields[i].setDouble(inst, fields[i].getDouble(this));
        } else if (fieldType.equals(float.class)) {
          fields[i].setFloat(inst, fields[i].getFloat(this));
        } else if (fieldType.equals(int.class)) {
          fields[i].setInt(inst, fields[i].getInt(this));
        } else if (fieldType.equals(long.class)) {
          fields[i].setLong(inst, fields[i].getLong(this));
        } else if (fieldType.equals(short.class)) {
          fields[i].setShort(inst, fields[i].getShort(this));
        }
      }
      return inst;
    } catch (Exception e) {
      throw new Error(e.getMessage());
    }
  }

  /** Create a format of the term. */
  public Format toFormat(FormatInfo info) {

    Class type = getClass();
    try {
      Field tagField = getTagField(type);

      // try to fold constant Pizza constructor
      Field[] fields = type.getDeclaredFields();
      for (int i = 0; i < fields.length; i++) {
        int mod = fields[i].getModifiers();
        if (Modifier.isStatic(mod)
            && Modifier.isPublic(mod)
            && Modifier.isFinal(mod)
            && fields[i].getType().equals(type)
            && (tagField != null
                ? tagField.getInt(fields[i].get(type)) == tagField.getInt(this)
                : fields[i].get(type) == this)) {
          // use name of the field as output
          return new FormatText(fields[i].getName());
        }
      }
    } catch (Exception e) { /* no tag field, or not accessible: fall through */ }
    try {
      if (info.visited(this)) {
        return new FormatText("<" + info.visitNumber(this) + ">");
      }
      int ref = info.visit(this);
      // make term format
      Field[] subterms = type.getFields();
      List<Format> subfmts = new ArrayList<Format>();
      for (int i = 0; i < subterms.length; i++) {
        int md = subterms[i].getModifiers();
        if (!Modifier.isStatic(md) && Modifier.isPublic(md)
            && subterms[i].getName().indexOf("$") < 0) {
          Object val = subterms[i].get(this);
          Format valfmt;
          if (val == null) {
            valfmt = new FormatText("null");
          } else if (val instanceof Term term) {
            valfmt = term.toFormat(info);
          } else if (val instanceof Term[] vals) {
            if (vals.length > 0) {
              Format[] valfmts = new Format[vals.length];
              for (int j = 0; j < vals.length; j++) {
                String sep = j == vals.length - 1 ? "}" : ",";
                valfmts[j] =
                  new FormatAppend(new Format[]{vals[j].toFormat(info),
                                                new FormatText(sep)});
              }
              valfmt =
                new FormatAppend(new Format[]{
                  new FormatText("{"),
                  new FormatLine(valfmts)});
            } else {
              valfmt = new FormatText("{}");
            }
          } else {
            valfmt = Format.buildFormat(info, val);
          }
          subfmts.add(new FormatAppend(new Format[]{
            valfmt,
            new FormatText(i == subterms.length - 1 ? ")" : ",")}));
        }
      }
      Format[] subfmtsArray = subfmts.toArray(new Format[0]);
      String className = type.getName();
      int di = className.lastIndexOf(".");
      if (di >= 0) className = className.substring(di + 1);
      className = className.replace('$', '.');
      return
        new FormatLine(new Format[]{
          new FormatText("<" + ref + ">" + className),
          new FormatAppend(new Format[]{
            new FormatText("("),
            new FormatLine(subfmtsArray)}),
          });
    } catch (IllegalAccessException e) {
      return Format.literal("<cannot access>");
    } catch (Exception e) {
      return Format.literal("<error: " + e.getMessage() + ">");
    }
  }

  /** Create a string for the term. Uses the toFormat() method. */
  public String toString() {
    return toFormat(new FormatInfo()).toString();
  }

  /** Compares two terms for structural equality.  Two terms are
    * structural equal, if they are build from the same class case,
    * and all their public fields the type of which is a subclass of Term
    * or Term[] are structural equal again. Note that this
    * equality does not consider annotations, and thus terms with
    * different annotations may be identified.
    */
  // PORT CHANGE (Object contract).  The original went straight to `other.getClass()` after
  // the identity test, so `term.equals(null)` threw NullPointerException instead of
  // returning false.  Unlike the defects in zeta.agl this one IS reachable -- `Term.equals`
  // has three callers in the jar, and any collection holding a null would reach it -- and
  // the contract leaves no room for a judgement call: equals(null) is false.
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;

    Class type = getClass();

    if (type.equals(other.getClass())) {
      try {
        Field tagField = getTagField(type);
        if (tagField.getInt(this) != tagField.getInt(other)) {
          return false;
        }
      } catch (Exception e) {
        // ignore tag if not present or cannot be accessed
      }
      Field[] fields = getSubtermFields(type);
      for (int i = 0; i < fields.length; i++) {
        try {
          Class fieldType = fields[i].getType();
          if (fieldType.isArray()) {
            Term[] arr = (Term[]) fields[i].get(this);
            Term[] arr1 = (Term[]) fields[i].get(other);
            for (int j = 0; j < arr.length; j++) {
              if (!arr[j].equals(arr1[j])) return false;
            }
          } else {
            if (!fields[i].get(this).equals(fields[i].get(other)))
              return false;
          }
        } catch (Exception e) {
          // ignore if it cannot be accessed
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /** Internalize a term. Any two internalized terms which are
    * equal according to the equals() method are also referential equal.
    * If a term contains public fields which are terms or term arrays again,
    * and if their exists a constructor whose argument types
    * matches the types of these fields in order, then the
    * value of the fields will be recursively internalized as well.
    * In this case, the internalized term will be newly created
    * -- fields which are not subterms, such as annotations, are reset
    * to their default value.
    *
    * Unreachable in the archived system; see the class comment.
    *
    * @see zeta.util.Term#equals */
  public Term intern() {
    if (interned) return this;

    Class type = getClass();
    Field[] fields = getSubtermFields(type);
    TermDescr descr;
    if (fields.length > 0) {

      // internalize subterms
      Class[] fieldTypes = new Class[fields.length];
      Object[] subterms = new Object[fields.length];
      for (int i = 0; i < fields.length; i++) {
        fieldTypes[i] = fields[i].getType();
        try {
          if (fieldTypes[i].isArray()) {
            subterms[i] = internArray((Term[]) fields[i].get(this));
          } else {
            subterms[i] = ((Term) fields[i].get(this)).intern();
          }
        } catch (IllegalAccessException e) {
          // this should not happen, since we are restricted to public fields
          throw new Error(e.getMessage());
        }
      }
      try {
        Constructor cons = type.getConstructor(fieldTypes);
        descr = new TermNode(cons, subterms);
      } catch (NoSuchMethodException e) {
        // no matching constructor, treat as leaf
        descr = new TermLeaf(this);
      }
    } else {
      // no subterms, treat as leaf
      descr = new TermLeaf(this);
    }
    Term repr = (Term) internTab.get(descr);
    if (repr == null) {
      repr = (Term) descr.makeInstance();
      repr.interned = true;
      internTab.put(descr, repr);
    }
    return repr;
  }

  /** Transient field which holds a flag indicating whether term is
    * interned.
    */
  protected transient boolean interned = false;

  /** Hashtable for interned terms. */
  private static Hashtable internTab = new Hashtable(256, 0.5f);

  /** Internalize an array of terms.
    *
    * Unreachable in the archived system; see the class comment.
    *
    * @see #intern
    */
  public static Term[] internArray(Term[] arr) {
    Class elemType = arr.getClass().getComponentType();
    Term[] arr1 = (Term[]) Array.newInstance(elemType, arr.length);
    for (int i = 0; i < arr.length; i++) {
      arr1[i] = arr[i].intern();
    }
    TermDescr descr = new TermArray(arr1);
    Term[] repr = (Term[]) internTab.get(descr);
    if (repr == null) {
      repr = (Term[]) descr.makeInstance();
      internTab.put(descr, repr);
    }
    return repr;
  }

  /** Get public Term fields. */
  private static Field[] getSubtermFields(Class type) {
    Field[] fields = type.getDeclaredFields();
    List<Field> vfields = new ArrayList<Field>();
    for (int i = 0; i < fields.length; i++) {
      int mod = fields[i].getModifiers();
      if (!Modifier.isStatic(mod) && Modifier.isPublic(mod)) {
        Class fieldType = fields[i].getType();
        if ((fieldType.isArray()
             && Term.class.isAssignableFrom(fieldType.getComponentType()))
            || Term.class.isAssignableFrom(fieldType)) {
          vfields.add(fields[i]);
        }
      }
    }
    return vfields.toArray(new Field[0]);
  }

  /** Get field representing a tag of a Pizza class case. */
  private static Field getTagField(Class type)
      throws NoSuchFieldException, SecurityException {
    try {
      String name = type.getName().replace('.', '$') + "$$tag";
      return type.getField(name);
    } catch (NoSuchFieldException e) {
      // try superclass
      Class superType = type.getSuperclass();
      if (superType != null) {
        return getTagField(superType);
      } else {
        throw e;
      }
    }
  }
}

/** A descriptor of a term as used for internalization. */
abstract class TermDescr {
  public abstract Object makeInstance();
}

/** Not a record: `equals` compares subterms by IDENTITY, which is the point -- they have
  * already been interned, so reference equality is the cheap test a record would not give. */
class TermNode extends TermDescr {
  Constructor cons;
  Object[] subterms;

  TermNode(Constructor cons, Object[] subterms) {
    this.cons = cons;
    this.subterms = subterms;
  }

  /** Pizza's generated case factory. */
  static TermNode TermNode(Constructor cons, Object[] subterms) {
    return new TermNode(cons, subterms);
  }

  public int hashCode() {
    int code = cons.hashCode();
    for (int i = 0; i < subterms.length; i++) {
      code += subterms[i].hashCode();
    }
    return code;
  }

  public boolean equals(Object other) {
    if (other instanceof TermNode node) {
      if (cons.equals(node.cons)
          && subterms.length == node.subterms.length) {
        for (int i = 0; i < subterms.length; i++) {
          if (subterms[i] != node.subterms[i]) return false;
        }
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  public Object makeInstance() {
    try {
      return (Term) cons.newInstance(subterms);
    } catch (Exception e) {
      // this shouldn't happen
      throw new Error("constructor `" + cons
                      + "' failed during internalization of term: "
                      + e.getMessage());
    }
  }
}

class TermLeaf extends TermDescr {
  Term term;

  TermLeaf(Term term) {
    this.term = term;
  }

  /** Pizza's generated case factory. */
  static TermLeaf TermLeaf(Term term) {
    return new TermLeaf(term);
  }

  public int hashCode() {
    return term.hashCode();
  }

  public boolean equals(Object other) {
    if (other instanceof TermLeaf leaf) {
      return term.equals(leaf.term);
    } else {
      return false;
    }
  }

  public Object makeInstance() {
    return term;
  }
}

class TermArray extends TermDescr {
  Term[] arr;

  TermArray(Term[] arr) {
    this.arr = arr;
  }

  /** Pizza's generated case factory. */
  static TermArray TermArray(Term[] arr) {
    return new TermArray(arr);
  }

  public boolean equals(Object other) {
    if (other instanceof TermArray ta) {
      Term[] arr1 = ta.arr;
      if (arr.length == arr1.length
          && arr.getClass().getComponentType()
                .equals(arr1.getClass().getComponentType())) {
        for (int i = 0; i < arr.length; i++) {
          if (arr[i] != arr1[i]) return false;
        }
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  public int hashCode() {
    int code = 0;
    for (int i = 0; i < arr.length; i++) {
      code += arr[i].hashCode();
    }
    return code;
  }

  public Object makeInstance() {
    return arr;
  }
}
