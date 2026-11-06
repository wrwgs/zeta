package zeta.agl;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;


/** The basic aggregate class of sets. This abstract class is
  * superclass of diverse implementation variants of sets.
  *
  * @version $Id: Set.pizza,v 1.2 2000/01/02 17:35:17 wg Exp $
  *
  * Ported together with {@code HashSet} -- which overrode eight of the methods below under
  * their mangled names -- for the reason given in {@link Seq}.  The two clusters are
  * independent in the right direction: nothing in the `Set` group mentions the `Map` group,
  * while `Map.domain()` builds a `HashSet`, so `Set` goes first.
  */
public abstract class Set<A> extends Aggregate<A> {

  /** Test if element is in set. */
  public abstract boolean contains(A elem);

  /** Test if the elements of another aggregate are contained
    * in the given set. */
  public boolean containsAll(Aggregate<A> other) {
    for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
      if (!contains(e.current())) return false;
    }
    return true;
  }

  /** Retrieve some (arbitrary) element from the set.
    * @exception  NoSuchElementException if the set is empty.
    */
  public A arb() throws NoSuchElementException {
    return enumerate().current();
  }

  /** Add element to set. */
  public Set<A> include(A elem) {
    return includeAll(new ArraySeq<A>(elem));
  }

  /** Add elements from other aggregate. */
  public abstract Set<A> includeAll(Aggregate<A> other);

  /** Remove element. */
  public Set<A> exclude(A elem) {
    return excludeAll(new ArraySeq<A>(elem));
  }

  /** Remove elements from other aggregate. */
  public abstract Set<A> excludeAll(Aggregate<A> other);

  /** Intersect with elements from other aggregate.
    * The resulting set contains only those elements which
    * are in this set and which are also in the other aggregate. */
  public abstract Set<A> intersect(Aggregate<A> other);

  /** Check whether the intersection of this set with another aggregate's
    * elements is not empty. */
  public boolean hasIntersect(Aggregate<A> other) {
    return !intersect(other).isEmpty();
  }

  /** Map function over set. */
  public abstract <B> Set<B> map(Function<A, B> f);

  /** Filter set. */
  public abstract Set<A> filter(Predicate<A> p);
}
