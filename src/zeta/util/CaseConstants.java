package zeta.util;

import java.io.InvalidClassException;
import java.io.ObjectStreamException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Canonicalisation of Pizza constant cases on deserialisation.
 *
 * <p>New in the port; there is no Pizza original.</p>
 *
 * <h2>The problem</h2>
 *
 * <p>A Pizza case declared without parentheses (<code>public case Power;</code>) compiles to
 * a <code>public static final Base Power</code> constant on a concrete base class, created
 * once by the base's static initialiser with a package-private <code>Base(int tag)</code>
 * constructor.  Pizza compiled every match against such a case as a comparison of the
 * <code>$$tag</code> field.  The port compares by identity
 * (<code>kind == Expr.UnaryKind.Power</code>), which is equivalent for every object built in
 * memory, because the constant is the only instance of the base class with that tag.</p>
 *
 * <p>It is not equivalent for an object read back by <code>ObjectInputStream</code>.  The
 * bases are <code>Serializable</code> (most through {@link Term}), and without a
 * <code>readResolve</code> deserialisation creates a fresh instance carrying the same tag, so
 * every identity test on it fails.  Saving a project (<code>squit</code>) became possible when
 * <code>zeta.tools.sman.Storage.create</code> was fixed, and the LaTeX adaptor's persistent
 * data holds scanned but not yet parsed <code>Text</code> (full of <code>Lexem</code> and
 * <code>Text.ZedMarkupKind</code> constants) and the <code>LatexConfig</code> macro tables.  A
 * session reopened on such a store died in <code>UnitParser.parse</code> with
 * <i>unexpected case: markup kind</i>.  <code>docs/serialization.md</code> records the
 * reproduction.</p>
 *
 * <h2>The fix, and why it is safe</h2>
 *
 * <p>Each such base declares <code>private Object readResolve()</code> calling
 * {@link #resolve}.  That returns the static constant with the same tag when the object is an
 * instance of the base class <i>itself</i>, and the object unchanged otherwise.</p>
 *
 * <ul>
 * <li><b>Only constants are replaced.</b>  Data-carrying cases are subclasses, so their exact
 *     class is never the base.  And no exact-base instance is ever created except by the
 *     static initialiser that fills the constants: checked over the bytecode of every class in
 *     <code>port/classes</code> and the 2002 jar, where every <code>new Base</code> of these
 *     classes sits in <code>&lt;clinit&gt;</code>, once per constant.  So an exact-base object
 *     in a stream was a constant when written.</li>
 * <li><b>Nothing is lost.</b>  A constant carries no state but its tag, with one exception:
 *     <code>Text</code> declares a mutable <code>locator</code>, which its constant case
 *     <code>FormatEnd</code> inherits.  Only the pretty printer produces
 *     <code>FormatEnd</code> (appending the shared constant), and no code assigns the locator
 *     of a <code>FormatEnd</code>; and since the constant is shared in memory anyway, its
 *     locator could not have been per-occurrence state to begin with.</li>
 * <li><b>The serialized form is unchanged.</b>  <code>readResolve</code> is consulted only
 *     when reading; it is private, so it is neither ABI nor part of the default
 *     <code>serialVersionUID</code> computation (which hashes non-private methods only); no
 *     field is added.</li>
 * <li><b>An unknown tag is a version error.</b>  An exact-base object whose tag names no
 *     constant can only come from a store written by a build with a different case list.
 *     {@link #resolve} reports that as <code>InvalidClassException</code>, which
 *     <code>Storage.read</code> wraps in <code>Errors.PhysicalError</code> and
 *     <code>SessionImpl.abort</code> routes to its "project data is incompatible with this
 *     version" message, which is exactly the right diagnosis.</li>
 * </ul>
 *
 * <p>Bases that are not <code>Serializable</code> (<code>zeta.agl.Option</code>, the
 * <code>zeta.form</code> styles and modes, <code>Locator.VisitCapability</code>) need nothing:
 * they are never read from a stream.</p>
 */
public final class CaseConstants {

  private CaseConstants() {}

  /** Per base class: tag value to its constant, built on first use. */
  private static final ClassValue<Map<Integer, Object>> TABLES =
    new ClassValue<Map<Integer, Object>>() {
      @Override
      protected Map<Integer, Object> computeValue(Class<?> base) {
        return buildTable(base);
      }
    };

  /**
   * The canonical object for <code>obj</code>, a just-deserialised instance of the ADT base
   * <code>base</code> or of one of its case classes.  Intended to be the whole body of the
   * base's <code>readResolve</code>:
   *
   * <pre>
   *   private Object readResolve() throws ObjectStreamException {
   *     return CaseConstants.resolve(this, Base.class);
   *   }
   * </pre>
   *
   * @return the <code>public static final</code> constant of <code>base</code> with the same
   *         <code>$$tag</code> if <code>obj</code>'s class is exactly <code>base</code>;
   *         otherwise <code>obj</code> itself
   * @throws InvalidClassException if <code>obj</code> is an exact-base instance whose tag
   *         matches no constant (a store written by a build with different cases)
   */
  public static Object resolve(Object obj, Class<?> base) throws ObjectStreamException {
    if (obj.getClass() != base) return obj;
    int tag;
    try {
      tag = tagField(base).getInt(obj);
    } catch (ReflectiveOperationException e) {
      throw new FatalError("CaseConstants: no readable tag on " + base.getName() + ": " + e);
    }
    Object canonical = TABLES.get(base).get(tag);
    if (canonical == null) {
      throw new InvalidClassException(base.getName(),
                                      "no constant case with tag " + tag);
    }
    return canonical;
  }

  /** The Pizza discriminant field of <code>base</code>, as {@link Term} names it. */
  private static Field tagField(Class<?> base) throws NoSuchFieldException {
    return base.getField(base.getName().replace('.', '$') + "$$tag");
  }

  /**
   * Scan <code>base</code>'s public static final fields of its own type.  Two different
   * objects with one tag would make the answer ambiguous; that would be a defect in the base,
   * so it is fatal rather than resolved arbitrarily.
   */
  private static Map<Integer, Object> buildTable(Class<?> base) {
    Map<Integer, Object> table = new HashMap<Integer, Object>();
    try {
      Field tag = tagField(base);
      for (Field f : base.getDeclaredFields()) {
        int mod = f.getModifiers();
        if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)
            && f.getType() == base) {
          Object constant = f.get(null);
          if (constant == null || constant.getClass() != base) continue;
          Object prev = table.put(tag.getInt(constant), constant);
          if (prev != null && prev != constant) {
            throw new FatalError("CaseConstants: two constants of " + base.getName()
                                 + " share tag " + tag.getInt(constant));
          }
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new FatalError("CaseConstants: cannot scan " + base.getName() + ": " + e);
    }
    return table;
  }
}
