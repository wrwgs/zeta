package zeta.agl;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;


/** Implementation variant of sequences which is optimzed for
  * appending and prepending. <p>
  *
  * This variant of sequences provides in average constant costs for
  * appending or prepending. It is nearly as fast as array sequences
  * according to access functions, but not as compact since it
  * allocates element storage in advance.  <p>
  *
  * The implementation of this sequence variant exploits single
  * threadedness w.r.t. to appending and prepending, such that in a
  * consecutive sequence of operations
  *
  * <pre>
  *   buf = buf.append(x1);
  *   buf = buf.prepend(x2);
  *   buf = buf.append(x3);
  *   ...
  * </pre>
  *
  * no copies of the contents of the aggregate are necessary for the
  * intermediate values; thus the complexity is in average
  * O(1). <p>
  *
  * Technically, buffers are represented as arrays with a
  * lower and an upper fillmark. Several buffers may share the same
  * array, but only one buffer can be the owner. Ownership is
  * inherited to the created buffer of append and prepend
  * operations. For these operations, if the array is owned and is
  * large enough, the new elements are destructively updated.  (This
  * implementation technique has been e.g. used for implementing
  * <code>mbuf</code>'s in TCP/IP implementations of BSD systems.)
  *
  * @version $Id: BufferSeq.pizza,v 1.1.1.1 1998/09/01 10:51:10 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * In Pizza this was a single-case algebraic data type:
  *
  * <pre>
  *   case BufferSeq(ArraySeq&lt;A&gt; array, int start, int end, int increment, boolean owner);
  * </pre>
  *
  * With one case and the case name equal to the class name, Pizza emitted no {@code $$tag} --
  * just the five package-private fields, a package-private constructor, and a static factory
  * {@code BufferSeq(...)} of the same signature.  All of that is reproduced: the fields are
  * read by this class and by binary code elsewhere in the package, and the factory is part of
  * the ABI.  The fields stay mutable, because the mbuf trick above depends on it -- a buffer
  * hands its array on and clears its own {@code owner} flag.
  *
  * Allocation goes through {@code ArraySeq}'s package-private size constructor, exactly as
  * before; what changed is only that the array inside it is now a Java array.
  */
public class BufferSeq<A> extends Seq<A> {

  /** The element data. */
  ArraySeq<A> array;
  /** The start index of the valid data in the array. */
  int start;
  /** The end index of the valid data in the array. */
  int end;
  /** The amount by which the capacity of the buffer is automatically incremented if its
    * size becomes greater than its capacity. */
  int increment;
  /** Whether this buffer owns the element data. */
  boolean owner;

  /** Respresentation of buffer sequences.
    * Only for members of this package. */
  BufferSeq(ArraySeq<A> array, int start, int end, int increment, boolean owner) {
    this.array = array;
    this.start = start;
    this.end = end;
    this.increment = increment;
    this.owner = owner;
  }

  /** Pizza's generated case factory.  Only for members of this package. */
  static BufferSeq BufferSeq(ArraySeq array, int start, int end, int increment,
                             boolean owner) {
    return new BufferSeq(array, start, end, increment, owner);
  }

  /** Construct an empty buffer with the specified front and back
    * initial capacities and capacity increment. The overall initial
    * capacity of the buffer is
    * <code>frontCapacity+backCapacity</code>.  The buffer needs to
    * grow after frontCapacity elements have been prepended,
    * and after backCapacity elements have been appended. If you plan
    * to add elements only to the one or the other end, use an initial
    * capacity of zero at the opposite end.  Negative values for
    * capacity increment are interpreted as percentage of the current
    * buffer size (hence -100 will double the capacity with each
    * reallocation).  */
  public BufferSeq(int frontCapacity, int backCapacity, int capacityIncrement) {
    this(new ArraySeq<A>(frontCapacity + backCapacity),
         frontCapacity, frontCapacity, capacityIncrement,
         true);
  }

  /** Construct an empty buffer. */
  public BufferSeq() {
    this(8, 8, -100);
  }

  /** Construct a buffer which contains the enumerated elements of
    * another aggregate. This static method is optimized according to
    * the actual representation of the other aggregate.  In particular,
    * if the other aggregate is a buffer or array sequence, the new buffer
    * simply inherits its internal representation. */
  @SuppressWarnings("unchecked")
  public static <A> Seq<A> from(Aggregate<A> other) {
    if (other instanceof BufferSeq) {
      return (BufferSeq<A>) other;
    } else if (other instanceof ArraySeq) {
      ArraySeq<A> array = (ArraySeq<A>) other;
      return new BufferSeq<A>(array, 0, array.elems.length, -100, false);
    } else {
      ArraySeq<A> array = new ArraySeq<A>(8 + other.size() + 8);
      int i = 8;
      for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
        array.elems[i] = e.current();
        i++;
      }
      return new BufferSeq<A>(array, 8, array.elems.length - 8, -100, true);
    }
  }

  /** Implementation of <code>Aggregate</code> method. */
  public Enum<A> enumerate() {
    return new Enum<A>() {
      private int index = 0;
      public boolean more() {
        return index < end - start;
      }
      public A current() throws NoSuchElementException {
        if (index < end - start)
          return at(index);
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
    return end - start;
  }

  /** Implementation of <code>Seq</code> method. */
  public A at(int index) throws IndexOutOfBoundsException {
    if (index < 0 || index >= end - start)
      throw new IndexOutOfBoundsException(index + "th");
    return array.elems[start + index];
  }

  /** Implementation of <code>Seq</code> method. */
  @SuppressWarnings("unchecked")
  public Seq<A> appendAll(Aggregate<A> other) {
    int s = other.size();
    BufferSeq<A> newbuf = allocAtEnd(s);
    if (other instanceof ArraySeq) {
      ArraySeq<A> sother = (ArraySeq<A>) other;
      System.arraycopy(sother.elems, 0, newbuf.array.elems, newbuf.end, s);
    } else if (other instanceof BufferSeq) {
      BufferSeq<A> bother = (BufferSeq<A>) other;
      System.arraycopy(bother.array.elems, bother.start,
                       newbuf.array.elems, newbuf.end, s);
    } else {
      int i = newbuf.end;
      for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
        newbuf.array.elems[i] = e.current();
        i++;
      }
    }
    newbuf.end += s;
    return newbuf;
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> append(A elem) {
    BufferSeq<A> newbuf = allocAtEnd(1);
    newbuf.array.elems[newbuf.end++] = elem;
    return newbuf;
  }

  /** Allocate a new buffer sequence with given size at end. */
  private BufferSeq<A> allocAtEnd(int s) {
    ArraySeq<A> newarray;
    if (end + s > array.elems.length) {
      int grow;
      if (increment > 0) grow = increment;
      else if (increment == 0) grow = array.elems.length;
      else grow = (array.elems.length * -increment) / 100;
      if (grow < end + s - array.elems.length)
        grow = end + s - array.elems.length;
      newarray = new ArraySeq<A>(array.elems.length + grow);
      System.arraycopy(array.elems, start, newarray.elems, start, end - start);
    } else if (!owner) {
      newarray = new ArraySeq<A>(array.elems.length);
      System.arraycopy(array.elems, start, newarray.elems, start, end - start);
    } else {
      owner = false;   // resign ownership
      newarray = array;
    }
    return new BufferSeq<A>(newarray, start, end, increment, true);
  }

  /** Implementation of <code>Seq</code> method. */
  @SuppressWarnings("unchecked")
  public Seq<A> prependAll(Aggregate<A> other) {
    int s = other.size();
    BufferSeq<A> newbuf = allocAtStart(s);
    if (other instanceof ArraySeq) {
      ArraySeq<A> sother = (ArraySeq<A>) other;
      System.arraycopy(sother.elems, 0, newbuf.array.elems, newbuf.start - s, s);
    } else if (other instanceof BufferSeq) {
      BufferSeq<A> bother = (BufferSeq<A>) other;
      System.arraycopy(bother.array.elems, bother.start,
                       newbuf.array.elems, newbuf.start - s, s);
    } else {
      int i = newbuf.start - s;
      for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
        newbuf.array.elems[i] = e.current();
        i++;
      }
    }
    newbuf.start -= s;
    return newbuf;
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> prepend(A elem) {
    BufferSeq<A> newbuf = allocAtStart(1);
    newbuf.array.elems[--newbuf.start] = elem;
    return newbuf;
  }

  /** Allocate a new buffer sequence with given size at start. */
  private BufferSeq<A> allocAtStart(int s) {
    ArraySeq<A> newarray;
    int newstart;
    int newend;
    if (start - s < 0) {
      int grow;
      if (increment > 0) grow = increment;
      else if (increment == 0) grow = array.elems.length;
      else grow = (array.elems.length * -increment) / 100;
      if (grow < -(start - s)) grow = -(start - s);
      newarray = new ArraySeq<A>(array.elems.length + grow);
      newstart = start + grow;
      newend = end + grow;
      System.arraycopy(array.elems, start, newarray.elems, newstart, end - start);
    } else if (!owner) {
      newarray = new ArraySeq<A>(array.elems.length);
      newstart = start;
      newend = end;
    } else {
      newarray = array;
      newstart = start;
      newend = end;
      owner = false;   // resign ownership
    }
    return new BufferSeq<A>(newarray, newstart, newend, increment, true);
  }

  /** Implementation of <code>Seq</code> method. */
  @SuppressWarnings("unchecked")
  public Seq<A> insertAll(int at, Aggregate<A> other) throws IndexOutOfBoundsException {
    if (at == 0) return prependAll(other);
    else if (at == end - start) return appendAll(other);
    else {
      ArraySeq<A> newarray = (ArraySeq<A>) array.insertAll(start + at, other);
      return new BufferSeq<A>(newarray,
                              start,
                              end + newarray.elems.length - array.elems.length,
                              increment,
                              true);
    }
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> subseq(int at, int end) throws IndexOutOfBoundsException {
    if (at < 0 || at > this.end - this.start) {
      throw new IndexOutOfBoundsException(at + "th");
    }
    if (end < 0 || end > this.end - this.start) {
      throw new IndexOutOfBoundsException(end + "th");
    }
    return array.subseq(this.start + at, this.start + end);
  }

  /** Implementation of <code>Seq</code> method. */
  public Seq<A> subseq(int at, int end, Predicate<A> p) throws IndexOutOfBoundsException {
    if (at < 0 || at > this.end - this.start) {
      throw new IndexOutOfBoundsException(at + "th");
    }
    if (end < 0 || end > this.end - this.start) {
      throw new IndexOutOfBoundsException(end + "th");
    }
    return array.subseq(this.start + at, this.start + end, p);
  }

  /** Implementation of <code>Seq</code> method. */
  @SuppressWarnings("unchecked")
  public Seq<A> sort(Comparator<A> ecompare) {
    // FIXME: avoid double allocation
    return new BufferSeq<A>((ArraySeq<A>) array.subseq(start, end).sort(ecompare),
                            0, end - start, increment, true);
  }

  /** Implementation of <code>Seq</code> method. */
  // PORT CHANGE (incomplete loop).  The original reverted with
  //
  //     int i = start; int j = end-1;
  //     while (i < j){ newarray.elems[i] = array.elems[j]; i++; j--; }
  //
  // which stops at the middle: it copies the back half into the front half and leaves
  // elems[(start+end)/2 .. end-1] null, so the result was neither reverted nor complete.  The
  // sibling in ArraySeq has the same shape but the right bound (`while (i < newelems.length)`),
  // which is what this now uses.  Unreachable in the archived system -- nothing in the jar
  // calls `revert` on any sequence -- so no baseline moves.
  public Seq<A> revert() {
    ArraySeq<A> newarray = new ArraySeq<A>(array.elems.length);
    int i = start;
    int j = end - 1;
    while (i < end) {
      newarray.elems[i] = array.elems[j];
      i++;
      j--;
    }
    return new BufferSeq<A>(newarray, start, end, increment, true);
  }

  /** Implementation of <code>Seq</code> method. */
  @SuppressWarnings("unchecked")
  public <B> Seq<B> map(Function<A, B> f) {
    // FIXME: avoid double allocation
    return new BufferSeq<B>((ArraySeq<B>) array.subseq(start, end).map(f),
                            0, end - start, increment, true);
  }

  /** Implementation of <code>Seq</code> method. */
  @SuppressWarnings("unchecked")
  public Seq<A> filter(Predicate<A> p) {
    // FIXME: avoid double allocation
    ArraySeq<A> sarr = (ArraySeq<A>) array.subseq(start, end).filter(p);
    return new BufferSeq<A>(sarr, 0, sarr.size(), increment, true);
  }


}
