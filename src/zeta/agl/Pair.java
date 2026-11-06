package zeta.agl;

import java.io.Serializable;

/** A pair of values.
  *
  * PORT NOTE: replaces {@code pizza.lang.Pair}, Pizza's one-case class
  * {@code class Pair<A,B> { case Pair(A fst, B snd); }}, which the 2002 jar carried erased
  * (two {@code Object} fields) and which therefore forced {@code Map}'s raw element type.  The
  * shape is kept where it is observable: the class's simple name is {@code Pair}, its public
  * instance fields are {@code fst} then {@code snd}, erased to {@code Object} -- which is all
  * {@code FormatPrinter.buildObjectFormat} and {@code Term.toFormat} read -- and they stay
  * mutable, because {@code zeta.tools.zparser.Preprocessor} assigns {@code snd}.
  * {@code equals}, {@code hashCode} and {@code toString} reproduce the 2002 bytecode exactly,
  * including the {@code NullPointerException} of {@code hashCode} when both components are
  * null: pairs are hashed into {@code HashSet}/{@code HashMap}, whose iteration order reaches
  * the dumps.  A single-case Pizza class has no {@code $$tag}.  The static factory of the
  * case class is kept, so that {@code Pair.Pair(a, b)} reads as it did in the Pizza source.
  */
public class Pair<A, B> implements Serializable {

  public A fst;

  public B snd;

  public Pair(A fst, B snd) {
    this.fst = fst;
    this.snd = snd;
  }

  public static <A, B> Pair<A, B> Pair(A fst, B snd) {
    return new Pair<A, B>(fst, snd);
  }

  public int hashCode() {
    Object f = fst;
    Object s = snd;
    if (f == null) return s.hashCode();
    if (s == null) return f.hashCode();
    return f.hashCode() * s.hashCode();
  }

  private static boolean equals(Object a, Object b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  public boolean equals(Object other) {
    if (other instanceof Pair p) {
      return equals(fst, p.fst) && equals(snd, p.snd);
    }
    return false;
  }

  public String toString() {
    return "Pair(" + fst + "," + snd + ")";
  }
}
