package zeta.agl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;


/** Implementation variant of sequences using arrays as the internal
  * representation.  <p>
  *
  * This variant of sequences is nearly as fast as Java arrays, as far as index-based
  * operations are concerned. Methods such as insertion and slicing are relative fast, since
  * they are realized by the native array method <code>System.arraycopy</code>. <p>
  *
  * Storage economy is as for arrays (no ``holes'' in the representing array).  <p>
  *
  * @version $Id: ArraySeq.pizza,v 1.2 2000/03/13 06:57:11 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * {@code elems} is now a real Java array.  In 2002 it was a {@code pizza.support.array}: a
  * Pizza {@code A[]} could not be a Java array, so Pizza boxed every array of
  * type-parameter type in a wrapper with {@code at(int)} / {@code at(int,Object)} accessors,
  * and {@code zeta.agl.Util} existed solely to allocate and copy those wrappers.  The field is
  * package-private and the constant pools of the 2002 classes show it read only by
  * {@code ArraySeq}, {@code BufferSeq} and {@code ArraySeq}'s own enumerator -- all ported
  * here -- so the wrapper can go, and with it {@code Util} and {@code ArrayHacks}.  (The name
  * {@code elems} does appear in other class files, but as their own field:
  * {@code HashSet$Change$IncludedAgg.elems} is an {@code Aggregate}.  Checking that needs the
  * constant pool, not a string search, which is why abi_gate.py reads it.)
  *
  * The 2002 jar's 33 callers of {@code ArraySeq(pizza.support.array)} are all ported and now
  * pass a real array to {@code ArraySeq(A[])}, which embeds it by reference exactly as the
  * wrapper constructor did ({@code ObjectArray.toObject()} returned its backing array).
  */
public class ArraySeq<A> extends Seq<A> {

  /** The elements. */
  A[] elems;

  @SuppressWarnings("unchecked")
  private static <B> B[] alloc(int size) {
    return (B[]) new Object[size];
  }

  /** Construct an empty sequence. */
  public ArraySeq() {
    this(0);
  }

  /** Construct a singelton sequence. */
  public ArraySeq(A e1) {
    this(1);
    elems[0] = e1;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2) {
    this(2);
    elems[0] = e1;
    elems[1] = e2;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2, A e3) {
    this(3);
    elems[0] = e1;
    elems[1] = e2;
    elems[2] = e3;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2, A e3, A e4) {
    this(4);
    elems[0] = e1;
    elems[1] = e2;
    elems[2] = e3;
    elems[3] = e4;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2, A e3, A e4, A e5) {
    this(5);
    elems[0] = e1;
    elems[1] = e2;
    elems[2] = e3;
    elems[3] = e4;
    elems[4] = e5;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2, A e3, A e4, A e5, A e6) {
    this(6);
    elems[0] = e1;
    elems[1] = e2;
    elems[2] = e3;
    elems[3] = e4;
    elems[4] = e5;
    elems[5] = e6;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2, A e3, A e4, A e5, A e6, A e7) {
    this(7);
    elems[0] = e1;
    elems[1] = e2;
    elems[2] = e3;
    elems[3] = e4;
    elems[4] = e5;
    elems[5] = e6;
    elems[6] = e7;
  }

  /** Construct a sequence with elements. */
  public ArraySeq(A e1, A e2, A e3, A e4, A e5, A e6, A e7, A e8) {
    this(8);
    elems[0] = e1;
    elems[1] = e2;
    elems[2] = e3;
    elems[3] = e4;
    elems[4] = e5;
    elems[5] = e6;
    elems[6] = e7;
    elems[7] = e8;
  }

  /** Construct a sequence from an array. The array is embedded by
    * reference into the array sequence, that is becomes its internal
    * representation. The effect of later indirect updates on the
    * given array is not specified. */
  public ArraySeq(A[] array) {
    elems = array;
  }

  /** Construct a sequence of given size which contains at the
    * the <code>i</code>th element the result of calling
    * <code>f(i)</code>.
    */
  public ArraySeq(int size, IntFunction<A> f) {
    elems = alloc(size);
    for (int i = 0; i < size; i++) {
      elems[i] = f.apply(i);
    }
  }

  /** Construct a array sequence which contains the enumerated elements of
    * another aggregate. This static method is optimized according to
    * the actual representation of the other aggregate.  In particular,
    * if the other aggregate is an array sequence again, it is simply
    * returned. */
  @SuppressWarnings("unchecked")
  public static <A> Seq<A> from(Aggregate<A> other) {
    if (other instanceof ArraySeq) {
      return (ArraySeq<A>) other;
    } else {
      A[] elems = alloc(other.size());
      int i = 0;
      for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
        elems[i] = e.current();
        i++;
      }
      return new ArraySeq<A>(elems);
    }
  }

  /** Construct a array sequence with undetermined elements of the given size.
    * Only for members of this package. */
  ArraySeq(int size) {
    elems = alloc(size);
  }

  /** Implementation of <code>Aggregate</code> method. */
  public Enum<A> enumerate() {
    return new Enum<A>() {
      private int index = 0;
      public boolean more() {
        return index < elems.length;
      }
      public A current() throws NoSuchElementException {
        if (index < elems.length)
          return elems[index];
        else
          throw new NoSuchElementException();
      }
      public void advance() {
        index++;
      }
    };
  }

  /** Implementation of <code>Aggregate</code> method. */
  public int size() {
    return elems.length;
  }

  /** Implementation of <code>Seq</code> method. */
  public A head() throws IndexOutOfBoundsException {
    return at(0);
  }

  /** Implementation of <code>Seq</code> method. */
  public A at(int index) throws IndexOutOfBoundsException {
    try {
      return elems[index];
    } catch (IndexOutOfBoundsException e) {
      throw new IndexOutOfBoundsException(index + "th");
    }
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> insertAll(int at, Aggregate<A> other) throws IndexOutOfBoundsException {
    if (at < 0 || at > elems.length)
      throw new IndexOutOfBoundsException(at + "th");
    int l = other.size();
    A[] newelems = alloc(elems.length + l);
    if (at > 0)
      System.arraycopy(elems, 0, newelems, 0, at);
    if (other instanceof ArraySeq) {
      System.arraycopy(((ArraySeq<A>) other).elems, 0, newelems, at, l);
    } else {
      int i = at;
      for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
        newelems[i] = e.current();
        i++;
      }
    }
    if (at < elems.length)
      System.arraycopy(elems, at, newelems, at + l, elems.length - at);
    return new ArraySeq<A>(newelems);
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> subseq(int at, int end) throws IndexOutOfBoundsException {
    if (at < 0 || at > elems.length)
      throw new IndexOutOfBoundsException(at + "th");
    if (end < 0 || end > elems.length)
      throw new IndexOutOfBoundsException(end + "th");
    if (at >= end)
      return new ArraySeq<A>();
    else {
      A[] newelems = alloc(end - at);
      System.arraycopy(elems, at, newelems, 0, end - at);
      return new ArraySeq<A>(newelems);
    }
  }

  /** Implementation of <code>Seq</code> method. */
  // PORT CHANGE (index arithmetic).  The original filled the result with
  //
  //     for (i = at; i < end && p(elems[i]); i++) newelems[i] = elems[i];
  //     if (i < newelems.length) { ... shrink to i ... }
  //
  // indexing the result -- whose length is end-at -- by the SOURCE index, and then comparing
  // that source index with the result's length.  Correct only for at == 0; for at > 0 it
  // writes past the end or truncates to the wrong length.  Unreachable in the archived system:
  // the two predicate overloads of `subseq` are called by nothing in the jar, and the plain
  // `subseq(int,int)` that Seq implements in terms of this one is overridden here and in
  // BufferSeq.  The port keeps the behaviour the code was written to have -- copy the leading
  // run of elements satisfying p, starting at `at` -- with the destination index counted
  // separately.
  public Seq<A> subseq(int at, int end, Predicate<A> p) throws IndexOutOfBoundsException {
    if (at < 0 || at > elems.length)
      throw new IndexOutOfBoundsException(at + "th");
    if (end < 0 || end > elems.length)
      throw new IndexOutOfBoundsException(end + "th");
    if (at >= end) {
      return new ArraySeq<A>();
    } else {
      A[] newelems = alloc(end - at);
      int n = 0;
      for (int i = at; i < end && p.test(elems[i]); i++) {
        newelems[n++] = elems[i];
      }
      if (n < newelems.length) {
        A[] finalelems = alloc(n);
        System.arraycopy(newelems, 0, finalelems, 0, n);
        return new ArraySeq<A>(finalelems);
      } else {
        return new ArraySeq<A>(newelems);
      }
    }
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> sort(Comparator<A> ecompare) {
    A[] newelems = alloc(elems.length);
    System.arraycopy(elems, 0, newelems, 0, elems.length);
    QuickSort<A> sorter = new QuickSort<A>(newelems, ecompare);
    sorter.run();
    return new ArraySeq<A>(newelems);
  }

  /** Implementation of <code>Seq</code> method.
    *
    * Ported line for line.  This is an unstable sort, so which of two elements the comparator
    * calls equal comes out first is decided by the partitioning, and that order reaches the
    * baselines through every sorted dump.  Replacing it with {@code Arrays.sort} -- a stable
    * merge sort -- would change artifacts without changing anything for the better. */
  private static class QuickSort<B> {
    // FIXME: inserted by cut and paste, check for correctness
    private final B[] AR;
    private final Comparator<B> ecompare;

    QuickSort(B[] AR, Comparator<B> ecompare) {
      this.AR = AR;
      this.ecompare = ecompare;
    }

    void run() {
      run(0, AR.length - 1);
    }

    private void run(int fst, int lst) {
      if (fst >= lst) return;
      B swap, pivot = AR[fst];
      int i = fst - 1, j = lst + 1;
      while (i < j) {
        while (ecompare.compare(pivot, AR[++i]) > 0);          // pivot > AR[i]
        while (ecompare.compare(pivot, AR[--j]) < 0);          // pivot < AR[j]
        if (i < j) {
          swap = AR[i];
          AR[i] = AR[j];
          AR[j] = swap;
        }
      }
      run(fst, j);
      run(j + 1, lst);
    }
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> revert() {
    A[] newelems = alloc(elems.length);
    int i = 0;
    int j = elems.length - 1;
    while (i < newelems.length) {
      newelems[i] = elems[j];
      i++;
      j--;
    }
    return new ArraySeq<A>(newelems);
  }

  /** Implementation of <code>Seq</code> method. */
  public <B> Seq<B> map(Function<A, B> f) {
    B[] newelems = alloc(elems.length);
    for (int i = 0; i < newelems.length; i++) {
      newelems[i] = f.apply(elems[i]);
    }
    return new ArraySeq<B>(newelems);
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> filter(Predicate<A> p) {
    A[] newelems = alloc(elems.length);
    int j = 0;
    for (int i = 0; i < elems.length; i++) {
      if (p.test(elems[i])) {
        newelems[j] = elems[i];
        j++;
      }
    }
    if (j < newelems.length) {
      A[] finalelems = alloc(j);
      System.arraycopy(newelems, 0, finalelems, 0, j);
      return new ArraySeq<A>(finalelems);
    } else {
      return new ArraySeq<A>(newelems);
    }
  }

  /** Implementation of <code>Serializable</code> method.
    *
    * Inert: neither this class nor any of its supertypes implements {@code Serializable}, so
    * these are never called -- only 14 classes in ZETA are serializable and none is in
    * {@code zeta.agl}.  They are kept because they record the author's intended persistent
    * form, and are now one line each: the 2002 versions had to go through
    * {@code ArrayHacks.readArray} to get a Java array into and out of a Pizza wrapper. */
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(elems);
  }

  /** Implementation of <code>Serializable</code> method. */
  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    elems = (A[]) in.readObject();
  }


}
