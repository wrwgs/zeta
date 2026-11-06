package zeta.agl;

/**  A class for optional data.
 *  @author  Martin Odersky
 *  @version 1.1
 *  @since pizza 1.0alpha8 96/11/22
 *
 *  Ported from Option.pizza -- the first of the algebraic data types, and the template for
 *  the rest.  The Pizza original declared
 *
 *      public case None;
 *      public case Some(A value);
 *
 *  and compiled that to a shape the 2002 classes depend on, so the port reproduces it exactly:
 *
 *   - a nullary case becomes a `public static final` constant on the base class, NOT a
 *     subclass, so `None` is an instance of `Option` itself and `Option` cannot be abstract;
 *   - an n-ary case becomes a public nested class `Option$Some` with public fields and a
 *     public constructor, plus a static factory `Some(...)` on the base;
 *   - every instance carries `public final int zeta$agl$Option$$tag`, and consumers compiled
 *     by Pizza dispatch with `getfield $$tag` + `lookupswitch`, so **the tag values are part
 *     of the ABI**.  They are 1-based in declaration order: None = 1, Some = 2.
 *
 *  The tag also keeps zeta.util.Term's reflective printer working, so it stays even after the
 *  bridges go (zesurrect-plan.md 9.1).  What modernises now is the inside: generics, and
 *  `instanceof` type patterns instead of a hand-rolled tag switch.
 *
 *  `Some.value` must stay declared as Object: binary callers do
 *  `getfield Option$Some.value:Ljava/lang/Object;`.  The typed view is the generic
 *  {@link #value()} below.
 */
public class Option<A> {

  // Tag values are fixed by the ABI; see the class comment.
  private static final int TAG_NONE = 1;
  private static final int TAG_SOME = 2;

  /** Pizza's discriminant.  Public and final because compiled consumers read it directly. */
  public final int zeta$agl$Option$$tag;

  Option(int tag) {
    this.zeta$agl$Option$$tag = tag;
  }

  /** Store no data. */
  public static final Option None = new Option(TAG_NONE);

  /** Store a value. */
  public static class Some extends Option {
    public Object value;
    public Some(Object value) {
      super(TAG_SOME);
      this.value = value;
    }
  }

  /** Store a value. */
  public static Option Some(Object value) {
    return new Some(value);
  }

  /**
   * Return the stored value.
   * @exception AssertionError if Option has no value
   */
  @SuppressWarnings("unchecked")
  public A value() {
    if (this instanceof Some s) {
      return (A) s.value;
    }
    // PORT CHANGE: the 2002 build threw pizza.lang.MatchError("no value"), an Error.  Pizza's
    // runtime is gone; AssertionError keeps it an Error -- so no `catch (Exception)` on the
    // way up starts swallowing it -- and nothing in ZETA catches MatchError by name.
    throw new AssertionError("no value");
  }

  /** Is a value stored? */
  public boolean hasValue() {
    return this instanceof Some;
  }

  /** Convert from null or the stored value. Only provided for options
    * which carry an object. */
  public static Option from(Object b) {
    return b == null ? None : Some(b);
  }

  /** Convert to null or the stored value. Only provided for options
    * which carry an object. */
  public static Object to(Option ob) {
    return ob instanceof Some s ? s.value : null;
  }
}
