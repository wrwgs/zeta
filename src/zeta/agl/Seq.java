package zeta.agl;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;


/** The basic aggregate class of sequences. This abstract class is
  * superclass of diverse implementation variants of sequences.
  *
  * @version $Id: Seq.pizza,v 1.4 2000/01/02 17:35:17 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * {@code Seq} was ported together with {@code ArraySeq} and {@code BufferSeq}, because
  * Pizza mangled with the <em>declaring</em> class: a 2002 {@code ArraySeq} overriding
  * {@code sort} declared {@code zeta$agl$ArraySeq$sort} plus a {@code zeta$agl$Seq$sort}
  * bridge, so porting {@code Seq} alone would have let {@code s.sort(cmp)} silently bypass the
  * override.  The mangled bridges are gone now that no binary class is left.
  *
  * The cluster is exactly {Seq, ArraySeq, BufferSeq}: nothing outside {@code zeta.agl} extends
  * {@code Aggregate}, and {@code ArraySeq.elems} -- which the implementations read directly --
  * is touched by no other class, so it is also the point at which Pizza's array wrapper can be
  * dropped from the representation.  {@code zeta.agl.Util} existed only to allocate and copy
  * those wrappers and has no other callers, so it is not ported at all.
  *
  * The lifted closure statics ({@code $closure$zeta$agl$Seq$0d}, {@code 1d}, {@code 2s}) and
  * the generated {@code Seq$$closures} class go: the only class that ever named them was
  * {@code Seq} itself.
  */
public abstract class Seq<A> extends Aggregate<A> {

  /** Return the first element of a sequence.
    * @exception IndexOutOfBoundsException   if sequence is empty. */
  public A head() throws IndexOutOfBoundsException {
    return at(0);
  }

  /** Return the last element of a sequence.
    * @exception IndexOutOfBoundsException   if sequence is empty. */
  public A last() throws IndexOutOfBoundsException {
    return at(size() - 1);
  }

  /** Return the sub-sequence without the first element.
    * @exception IndexOutOfBoundsException   if sequence is empty. */
  public Seq<A> tail() throws IndexOutOfBoundsException {
    return subseq(1);
  }

  /** Return the element at given index. Indices start at 0.
    * @exception IndexOutOfBoundsException   if index not in [0..size()-1].
    */
  // PORT CHANGE (off-by-one).  The original tested `if (i == 1) return e.current();` while
  // counting i down from `index`, so this generic fallback returned element 0 for at(1) and
  // threw for at(0) -- against its own javadoc, and against head() == at(0).  It is
  // unreachable in the archived system, because ArraySeq and BufferSeq both override `at` and
  // nothing else extends Seq, which is why no baseline moves; re-encoding the off-by-one in
  // the port would only leave a trap for the next implementation of Seq.
  public A at(int index) throws IndexOutOfBoundsException {
    if (index < 0) throw new IndexOutOfBoundsException(index + "th");
    int i = index;
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      if (i == 0) return e.current();
      i--;
    }
    throw new IndexOutOfBoundsException(index + "th");
  }

  /** Update the element at the given index.
   * @exception IndexOutOfBoundsException   if index not in [0..size()-1].
   */
  public Seq<A> update(int index, A elem) {
    if (index == 0)
      return tail().prepend(elem);
    else if (index + 1 == size())
      return subseq(0, index).append(elem);
    return subseq(0, index).append(elem).appendAll(subseq(index + 1));
  }

  /** Append element. */
  public Seq<A> append(A elem) {
    return insertAll(size(), new ArraySeq<A>(elem));
  }

  /** Append elements from other aggregate. */
  public Seq<A> appendAll(Aggregate<A> other) {
    return insertAll(size(), other);
  }

  /** Prepend element. */
  public Seq<A> prepend(A elem) {
    return insertAll(0, new ArraySeq<A>(elem));
  }

  /** Prepend elements from other aggregate. */
  public Seq<A> prependAll(Aggregate<A> other) {
    return insertAll(0, other);
  }

  /** Insert element at given index.
    * @exception IndexOutOfBoundsException   if index not in [0..size()].
    */
  public Seq<A> insert(int at, A elem) throws IndexOutOfBoundsException {
    return insertAll(at, new ArraySeq<A>(elem));
  }

  /** Insert elements from other aggregate at given index.
    * @exception IndexOutOfBoundsException   if index not in [0..size()].
    */
  public abstract Seq<A> insertAll(int at, Aggregate<A> other)
    throws IndexOutOfBoundsException;

  /** Return the index of the first occurence of element which
    * equals (according to Object equality) to the given element.
    * Return <code>-1</code> if no such element exists.
    */
  public int indexOf(A elem) {
    int i = 0;
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      if (e.current().equals(elem)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  /** Return the index of the first occurence of element for which the
    * given predicate holds.  Return <code>-1</code> if no such
    * element exists.  */
  public int indexOf(Predicate<A> p) {
    int i = 0;
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      if (p.test(e.current())) {
        return i;
      }
      i++;
    }
    return -1;
  }

  /** Return the index of the last occurence of element which
    * equals (according to Object equality) to the given element.
    * Return <code>-1</code> if no such element exists.
    */
  public int lastIndexOf(A elem) {
    int i = 0;
    int lasti = -1;
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      if (e.current().equals(elem)) {
        lasti = i;
      }
      i++;
    }
    return lasti;
  }

  /** Return the index of the last occurence of element for which the
    * given predicate holds.  Return <code>-1</code> if no such
    * element exists.  */
  public int lastIndexOf(Predicate<A> p) {
    int i = 0;
    int lasti = -1;
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      if (p.test(e.current())) {
        lasti = i;
      }
      i++;
    }
    return lasti;
  }

  /** Return the sub-sequence which starts at the specified index
    * and extents until the end.
    * @exception IndexOutOfBoundsException   if index not in [0..size()].
    */
  public Seq<A> subseq(int at) throws IndexOutOfBoundsException {
    return subseq(at, size());
  }

  /** Return the sub-sequence which starts at the specified index
    * and extends until index <code>end-1</code>.
    * @exception IndexOutOfBoundsException   if start index not in [0..size()],
                          or end index not in [0..size()]
  */
  public Seq<A> subseq(int at, int end) {
    return subseq(at, end, x -> true);
  }

  /** Return the sub-sequence which starts at the specified index
    * and extents as long as predicate holds or until the end.
    * @exception IndexOutOfBoundsException   if index not in [0..size()].
    */
  public Seq<A> subseq(int at, Predicate<A> p) throws IndexOutOfBoundsException {
    return subseq(at, size(), p);
  }

  /** Return the sub-sequence which starts at the specified index
    * and extents as long as predicate holds but not longer then
    * <code>end-1</code>.
    * @exception IndexOutOfBoundsException   if start index not in [0..size()],
                          or end index not in [0..size()]
    */
  public abstract Seq<A> subseq(int at, int end, Predicate<A> p)
    throws IndexOutOfBoundsException;

  /** Return sorted contents of this sequence. Sorting
    * is done on base of the passed total ordering.
    * @see zeta.agl.Ordered
    */
  public abstract Seq<A> sort(Comparator<A> ecompare);

  /** Revert order of elements in sequence. */
  public abstract Seq<A> revert();

  /** Map a function over a sequence. */
  public abstract <B> Seq<B> map(Function<A, B> f);

  /** Filter a sequence. */
  public abstract Seq<A> filter(Predicate<A> p);

  /** Partition a sequence. */
  public Pair<Seq<A>, Seq<A>> partition(Predicate<A> p) {
    // FIXME: customized implementations
    return Pair.Pair(filter(p), filter(x -> !p.test(x)));
  }

  /** Flat a sequence of sequences of lists.  Use <code>empty</code>
    * if the list is empty. */
  public static <B> Seq<B> flatten(Seq<B> empty, Seq<Seq<B>> s) {
    if (s.isEmpty()) return empty;
    else return s.fold((s1, s2) -> s1.appendAll(s2));
  }

  /** Implementation of <code>Aggregate</code> method. Overwritten for
    * reasons of efficiency. */
  public boolean equals(Object other) {
    if (other instanceof Seq) {
      return equalsSeq(this, (Seq) other);
    } else {
      return super.equals(other);
    }
  }

  private static boolean equalsSeq(Seq s1, Seq s2) {
    int s = s1.size();
    if (s == s2.size()) {
      for (int i = 0; i < s; i++) {
        if (!s1.at(i).equals(s2.at(i))) return false;
      }
      return true;
    } else {
      return false;
    }
  }

  /** Implementation of <code>Aggregate</code> method. Overwritten for
    * reasons of efficiency. */
  public int hashCode() {
    int n = size();
    int i = 0;
    int hash = 0;
    while (i < n) {
      hash += at(i++).hashCode();
    }
    return hash;
  }
}
