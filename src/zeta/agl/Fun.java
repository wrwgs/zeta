package zeta.agl;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Function combinators.
  *
  * Ported from Fun.pizza.  Pizza wrote these with arrow types and `fun` literals, e.g.
  *
  *     public static &lt;A,B,C&gt; (A)-&gt;C compose((B)-&gt;C f, (A)-&gt;B g) {
  *         return fun(A a)-&gt;C { return f(g(a)); };
  *     }
  *
  * and compiled each literal into a generated `Fun$$closures` subclass of
  * `pizza.support.Closure`.  The port writes the same combinators as lambdas over
  * `java.util.function` types (and {@link Functions.Fun3} for three arguments); an arrow
  * type returning `boolean` becomes a `Predicate` where it is unary and a
  * `BiFunction<..., Boolean>` where it is binary, as Pizza boxed it.
  */
public class Fun {

  public static <A> A id(A a) {
    return a;
  }

  /* do we need the next two functions? */

  /** {@code (A,B)->C} from {@code (A)->C}: ignore the second argument. */
  public static <A, B, C> BiFunction<A, B, C> first(Function<A, C> f) {
    return (a, b) -> f.apply(a);
  }

  /** {@code (A,B)->C} from {@code (B)->C}: ignore the first argument. */
  public static <A, B, C> BiFunction<A, B, C> second(Function<B, C> f) {
    return (a, b) -> f.apply(b);
  }

  public static <A, B> A fst(Pair<A, B> p) {
    return p.fst;
  }

  public static <A, B> B snd(Pair<A, B> p) {
    return p.snd;
  }

  /** {@code (A)->B} from {@code (A,A)->B}: pass the argument twice. */
  public static <A, B> Function<A, B> tee(BiFunction<A, A, B> f) {
    return a -> f.apply(a, a);
  }

  public static <A, B, C> Function<A, C> compose(Function<B, C> f, Function<A, B> g) {
    return a -> f.apply(g.apply(a));
  }

  public static <A, B, C> BiFunction<B, A, C> swap(BiFunction<A, B, C> f) {
    return (a, b) -> f.apply(b, a);
  }

  public static <A, B, C> Function<B, C> curryFstOf2(BiFunction<A, B, C> f, A a) {
    return b -> f.apply(a, b);
  }

  public static <A, B, C, D> BiFunction<B, C, D> curryFstOf3(Functions.Fun3<A, B, C, D> f,
                                                            A a) {
    return (b, c) -> f.apply(a, b, c);
  }

  public static <A, B, C> Function<A, C> currySndOf2(BiFunction<A, B, C> f, B b) {
    return a -> f.apply(a, b);
  }

  public static <A, B, C, D> BiFunction<A, C, D> currySndOf3(Functions.Fun3<A, B, C, D> f,
                                                            B b) {
    return (a, c) -> f.apply(a, b, c);
  }

  public static <A, B, C, D> BiFunction<A, B, D> curryThrdOf3(Functions.Fun3<A, B, C, D> f,
                                                             C c) {
    return (a, b) -> f.apply(a, b, c);
  }

  /** {@code (Pair<A,B>)->C} from {@code (A,B)->C}. */
  public static <A, B, C> Function<Pair<A, B>, C> toPair(BiFunction<A, B, C> f) {
    return p -> f.apply(p.fst, p.snd);
  }

  public static boolean eq(Object a1, Object a2) {
    return a1.equals(a2);
  }

  public static <A> Predicate<A> not(Predicate<A> f) {
    return a -> !f.test(a);
  }

  public static boolean not(boolean b) {
    return !b;
  }

  public static <A, B> BiFunction<A, B, Boolean> and(Predicate<A> f1, Predicate<B> f2) {
    return (a, b) -> Boolean.valueOf(f1.test(a) && f2.test(b));
  }

  public static <A, B> BiFunction<A, B, Boolean> or(Predicate<A> f1, Predicate<B> f2) {
    return (a, b) -> Boolean.valueOf(f1.test(a) || f2.test(b));
  }

  /** {@code (boolean,A)->boolean}: short-circuit on the already-computed boolean. */
  public static <A> BiFunction<Boolean, A, Boolean> or(Predicate<A> f) {
    return (b, a) -> Boolean.valueOf(b.booleanValue() || f.test(a));
  }

  public static <A> Predicate<A> and1(Predicate<A> f1, Predicate<A> f2) {
    BiFunction<A, A, Boolean> g = and(f1, f2);
    return a -> g.apply(a, a).booleanValue();
  }

  public static <A> Predicate<A> or1(Predicate<A> f1, Predicate<A> f2) {
    BiFunction<A, A, Boolean> g = or(f1, f2);
    return a -> g.apply(a, a).booleanValue();
  }
}
