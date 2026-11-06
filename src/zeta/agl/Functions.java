package zeta.agl;

/** Functional interfaces the port needs and {@code java.util.function} does not provide.
  *
  * Pizza had first-class arrow types, so AGL's higher-order methods were written directly
  * against {@code (B,A)->B} and friends.  Modern Java expresses most of those with
  * {@code java.util.function} -- {@link java.util.function.Function},
  * {@link java.util.function.BiFunction}, {@link java.util.function.Consumer},
  * {@link java.util.function.Predicate}, {@link java.util.function.Comparator} -- but it has
  * no three-argument form and no form mixing an {@code int} with reference arguments, which
  * AGL's {@code reduceWithPos} needs.  Those two live here.
  */
public final class Functions {

  private Functions() {}

  /** A three-argument function. */
  public interface Fun3<A, B, C, R> {
    R apply(A a, B b, C c);
  }

  /** A reduction step that also receives the element's position. */
  public interface IndexedFun<B, A, R> {
    R apply(B acc, int pos, A elem);
  }
}
