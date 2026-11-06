package zeta.agl;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;


/** An implementation variant of sets based on object equality and
  * hash code. <p>
  *
  * This variant of sets is good suited for sets which will become
  * relative large. Its efficiency depends on a suitable
  * implementation of the Object.hashCode() method for the element
  * type. <p>
  *
  * The implementation supports single threadedness w.r.t. to
  * adding and removing elements, such that in a consecutive sequence
  * of operations
  *
  * <pre>
  *   set = set.include(x1);
  *   set = set.exclude(x2);
  *   set = set.include(x3);
  *   ...
  * </pre>
  *
  * no copies of the contents of the aggregate are necessary for the
  * intermediate set values; thus the complexity is in average
  * O(1 + hashCosts). <p>
  *
  * The implementation uses a technique known under the catchword
  * of ``version arrays''. A hash set is basically represented by a
  * hash table (similar to the java.util.Hashtable implementation) and
  * a changes record.  When adding or removing elements from a hash
  * set, the new hash set inherits an updated version of the hash
  * table of the old one, meanwhile the changes on the table are
  * recorded in the old set.  If the old set is actually accessed
  * again, the changes record will be evaluated to reconstruct the old
  * value of the hashtable.  The memory allocated for recording
  * changes will be retrieved as soon as the old sets are subject of
  * garbage collection.
  *
  * @version $Id: HashSet.pizza,v 1.1.1.1 1998/09/01 10:51:11 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * The bucket array, the chaining, the growth rule and the enumeration order are ported line
  * for line, because <b>iteration order is observable in every dump</b>: `put` prepends to its
  * chain, `rehash` grows to {@code 2n+1} and re-inserts walking buckets downwards, and
  * {@code Enumerator} walks buckets from the top down. Those three together decide the order in
  * which a `Set<Name>` appears in `nmz`, `checked-absy` and the rest. Swapping in
  * {@code java.util.LinkedHashSet} would be a different order, not a better one.
  *
  * What changed is the representation of the change record. In Pizza it was a private
  * four-case ADT compiled to `HashSet$Change` + four subclasses carrying a `$$tag`; being
  * private, no class outside `HashSet` ever referenced it (confirmed from the constant pools),
  * so it is now a sealed interface of records and {@code restore} is a `switch` over patterns
  * rather than over tags. That is the one place in AGL where the port is free to change shape
  * rather than merely re-spell.
  *
  * {@code HashSetTab} stays a package-private top-level class in this file, as in the Pizza
  * source. Its `synchronized` methods are kept: the class comment's "FIXME: synchronous
  * behaviour" is still unaddressed, and dropping the locks would be a silent semantic change.
  */
public class HashSet<A> extends Set<A> {

  /** The hash table. */
  HashSetTab<A> table;

  /** The change performed on the hash table (null if none). */
  Change<A> change;

  /** Representation of a change: what was done to the table that this set's descendant now
    * owns, and therefore what must be undone to reconstruct this set's own value. */
  sealed interface Change<A> {
    HashSet<A> descendant();

    record Included<A>(A elem, HashSet<A> descendant) implements Change<A> {}
    record IncludedAgg<A>(Aggregate<A> elems, HashSet<A> descendant) implements Change<A> {}
    record Excluded<A>(A elem, HashSet<A> descendant) implements Change<A> {}
    record ExcludedAgg<A>(Aggregate<A> elems, HashSet<A> descendant) implements Change<A> {}
  }

  /** Construct an empty hash set with the specified initial capacity
    * and load factor.
    * @param initialCapacity the initial number of buckets
    * @param loadFactor a number between 0.0 and 1.0, it defines
    *		the threshold for rehashing the hashtable into
    *		a bigger one.
    * @exception IllegalArgumentException If the initial capacity
    * is less than or equal to zero.
    * @exception IllegalArgumentException If the load factor is
    * less than or equal to zero. */
  public HashSet(int initialCapacity, float loadFactor) {
    initialize(initialCapacity, loadFactor);
  }

  private void initialize(int initialCapacity, float loadFactor) {
    if ((initialCapacity <= 0) || (loadFactor <= 0.0)) {
      throw new IllegalArgumentException();
    }
    this.table = new HashSetTab<A>(initialCapacity, loadFactor);
    this.change = null;
  }

  /** Construct an empty hash set. */
  public HashSet() {
    initialize(10, 0.75f);
  }

  /** Construct a hash set which contains the enumerated elements of
    * another aggregate. This static method is optimized according to
    * the actual representation of the other aggregate.  In particular,
    * if the other aggregate is a hash set again, it is simply returned. */
  @SuppressWarnings("unchecked")
  public static <A> Set<A> from(Aggregate<A> other) {
    if (other instanceof HashSet) {
      return (HashSet<A>) other;
    } else {
      HashSet<A> hset = new HashSet<A>((int) (other.size() * 0.75f) + 1, 0.75f);
      for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
        hset.table.put(e.current());
      }
      return hset;
    }
  }

  /** Construct a hash set which contains the given element. */
  public HashSet(A elem) {
    this();
    table.put(elem);
  }

  /** Construct a hash set from its internal representation. Only for
    * members of this package. */
  HashSet(HashSetTab<A> table, Change<A> change) {
    this.table = table;
    this.change = change;
  }

  /** Compact this hash set, by evaluating the changes history. This method
    * has no semantic effect, but if frees auxiliary memory requested
    * for this set. */
  public void compact() {
    if (change != null) restore();
  }

  /* Restore changes in hashtable. */
  private void restore() {
    restore(change);
    change = null;
  }

  /** Depth-first restoring of changes. */
  private void restore(Change<A> change) {
    // FIXME: remove recursion
    HashSet<A> descendant = change.descendant();
    if (descendant.change != null) {
      descendant.restore();
    }
    table = descendant.table.copy();
    switch (change) {
      case Change.Included<A> c -> table.remove(c.elem());
      case Change.Excluded<A> c -> table.put(c.elem());
      case Change.IncludedAgg<A> c -> {
        for (Enum<A> e = c.elems().enumerate(); e.more(); e.advance()) {
          table.remove(e.current());
        }
      }
      case Change.ExcludedAgg<A> c -> {
        for (Enum<A> e = c.elems().enumerate(); e.more(); e.advance()) {
          table.put(e.current());
        }
      }
    }
  }

  /** Test for equality of this hash set with another object.  An hash
    * set equals to another object if the other is an instance
    * of class <code>Set</code>, contains the same number of elements,
    * and each element of the other set is contained in this hash set. */
  public boolean equals(Object other) {
    if (other instanceof Set) {
      return equalsSet((Set) other);
    } else {
      return false;
    }
  }

  private boolean equalsSet(Set other) {
    if (change != null) restore();
    if (other.size() == table.size()) {
      for (Enum e = other.enumerate(); e.more(); e.advance()) {
        if (!table.containsObject(e.current())) return false;
      }
      return true;
    } else {
      return false;
    }
  }

  /** */
  public int hashCode() {
    if (change != null) restore();
    return table.hashCode();
  }

  /** Return an enumeration of the elements. Each element
    * in the set is returned exactly once, but the order of
    * how elements are enumerated is not determined. As a
    * result, the <code>compare</code> method will not
    * coincides with equality on hash sets.
    */
  public Enum<A> enumerate() {
    if (change != null) restore();
    return table.copy().enumerate(); // FIXME: copy
  }

  /** */
  public int size() {
    if (change != null) restore();
    return table.size();
  }

  /** */
  public boolean contains(A elem) {
    if (change != null) restore();
    return table.contains(elem);
  }

  /** */
  public Set<A> include(A elem) {
    if (change != null) restore();
    if (table.put(elem)) {
      HashSet<A> descendant = new HashSet<A>(table, null);
      this.change = new Change.Included<A>(elem, descendant);
      return descendant;
    } else
      return this;
  }

  /** */
  public Set<A> includeAll(Aggregate<A> other) {
    if (other == this) return this;
    if (change != null) restore();
    boolean included = false;
    for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
      included |= table.put(e.current());
    }
    if (included) {
      HashSet<A> descendant = new HashSet<A>(table, null);
      this.change = new Change.IncludedAgg<A>(other, descendant);
      return descendant;
    } else {
      return this;
    }
  }

  /** */
  public Set<A> exclude(A elem) {
    if (change != null) restore();
    if (table.remove(elem)) {
      HashSet<A> descendant = new HashSet<A>(table, null);
      this.change = new Change.Excluded<A>(elem, descendant);
      return descendant;
    } else
      return this;
  }

  /** */
  public Set<A> excludeAll(Aggregate<A> other) {
    if (other == this) return new HashSet<A>();
    if (change != null) restore();
    boolean excluded = false;
    for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
      excluded |= table.remove(e.current());
    }
    if (excluded) {
      HashSet<A> descendant = new HashSet<A>(table, null);
      this.change = new Change.ExcludedAgg<A>(other, descendant);
      return descendant;
    } else {
      return this;
    }
  }

  /** */
  public Set<A> intersect(Aggregate<A> other) {
    if (change != null) restore();
    HashSetTab<A> newtab = table.deriveNew();
    for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
      A elem = e.current();
      if (table.contains(elem)) {
        newtab.put(elem);
      }
    }
    return new HashSet<A>(newtab, null);
  }

  /** */
  public <B> Set<B> map(Function<A, B> f) {
    if (change != null) restore();
    HashSetTab<B> newtab = table.deriveNew();
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      newtab.put(f.apply(e.current()));
    }
    return new HashSet<B>(newtab, null);
  }

  /** */
  public Set<A> filter(Predicate<A> p) {
    if (change != null) restore();
    HashSetTab<A> newtab = table.deriveNew();
    for (Enum<A> e = enumerate(); e.more(); e.advance()) {
      A elem = e.current();
      if (p.test(elem)) newtab.put(elem);
    }
    return new HashSet<A>(newtab, null);
  }


}

/** Auxiliary class for the implementation of hash-sets. */

/*
 * Copyright (c) 1994 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL purposes and without
 * fee is hereby granted provided that this copyright notice
 * appears in all copies. Please refer to the file "copyright.html"
 * for further important copyright and licensing information.
 *
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

final class HashSetTab<A> {

  /** Collision list entry. */
  private static class Entry<A> {

    A elem;
    int hash;
    Entry<A> next;

    Entry(A elem, int hash, Entry<A> next) {
      this.elem = elem;
      this.hash = hash;
      this.next = next;
    }

    public Entry<A> copy() {
      return new Entry<A>(elem, hash, (next != null) ? next.copy() : null);
    }
  }

  /** The hash table data. */
  private Entry<A>[] table;

  /**
    * The total number of entries in the hash table.
    */
  private int count;

  /**
    * Rehashes the table when count exceeds this threshold.
    */
  private int threshold;

  /**
    * The load factor for the hashtable.
    */
  private float loadFactor;

  @SuppressWarnings("unchecked")
  private static <B> Entry<B>[] buckets(int capacity) {
    return (Entry<B>[]) new Entry[capacity];
  }

  /**
    * Constructs a new, empty hashtable with the specified initial
    * capacity and the specified load factor.
    * @param initialCapacity the initial number of buckets
    * @param loadFactor a number between 0.0 and 1.0, it defines
    *		the threshold for rehashing the hashtable into
    *		a bigger one.
     * @exception IllegalArgumentException If the initial capacity
     * is less than or equal to zero.
     * @exception IllegalArgumentException If the load factor is
     * less than or equal to zero.
     */
  HashSetTab(int initialCapacity, float loadFactor) {
    if ((initialCapacity <= 0) || (loadFactor <= 0.0)) {
      throw new IllegalArgumentException();
    }
    this.loadFactor = loadFactor;
    table = buckets(initialCapacity);
    threshold = (int) (initialCapacity * loadFactor);
  }

  /** */
  public int hashCode() {
    int hash = count;
    for (int i = 0; i < table.length; i++) {
      Entry<A> e = table[i];
      while (e != null) {
        hash += e.hash;
        e = e.next;
      }
    }
    return hash;
  }

  /**
    * Returns the number of elements contained in the hash table
    */
  int size() {
    return count;
  }

  /**
    * Returns an enumeration of the elements.
    */
  Enum<A> enumerate() {
    return new Enumerator<A>(table);
  }

  /** A hash table enumerator class.
    *
    * Buckets are walked from the TOP down, and each chain front to back; `put` prepends, so
    * within a bucket the most recently added element comes first.  This is the order every
    * `Set` dump is in. */
  private static class Enumerator<A> implements Enum<A> {
    int index;
    Entry<A>[] table;
    Entry<A> entry;

    Enumerator(Entry<A>[] table) {
      this.table = table;
      this.index = table.length;
      while (entry == null && index > 0) {
        index--;
        entry = table[index];
      }
    }

    public boolean more() {
      return entry != null;
    }

    public A current() {
      if (entry != null) {
        return entry.elem;
      } else {
        throw new NoSuchElementException();
      }
    }

    public void advance() {
      if (entry != null) {
        entry = entry.next;
        while (entry == null && index > 0) {
          index--;
          entry = table[index];
        }
      } else {
        throw new NoSuchElementException();
      }
    }

  }

  /** Test if equal element is contained. */
  synchronized boolean contains(A elem) {
    return containsObject(elem);
  }

  /** Test if equal element is contained, object version. */
  synchronized boolean containsObject(Object elem) {
    Entry<A>[] tab = table;
    int hash = elem.hashCode();
    int index = (hash & 0x7FFFFFFF) % tab.length;
    for (Entry<A> e = tab[index]; e != null; e = e.next) {
      if ((e.hash == hash) && e.elem.equals(elem)) {
        return true;
      }
    }
    return false;
  }

  /**
    * Puts the specified element into the hash table. Does nothing
    * if equal element is already in hash table.
    * @return true iff equal element was not yet in hash table
    */
  synchronized boolean put(A elem) {
    // Makes sure the elem is not already in the hash table
    int hash = elem.hashCode();
    int index = (hash & 0x7FFFFFFF) % table.length;
    for (Entry<A> e = table[index]; e != null; e = e.next) {
      if ((e.hash == hash) && elem.equals(e.elem)) {
        return false;
      }
    }

    if (count >= threshold) {
      // Rehash the table if the threshold is exceeded
      rehash();
      index = (hash & 0x7FFFFFFF) % table.length;
    }

    // Creates the new entry.
    table[index] = new Entry<A>(elem, hash, table[index]);
    count++;
    return true;
  }

  /**
    * Removes the element corresponding to the elem. Does nothing if the
    * elem is not present.
    * @return true iff `elem' was in hash table
    */
  synchronized boolean remove(A elem) {
    int hash = elem.hashCode();
    int index = (hash & 0x7FFFFFFF) % table.length;
    for (Entry<A> e = table[index], prev = null; e != null; prev = e, e = e.next) {
      if ((e.hash == hash) && elem.equals(e.elem)) {
        if (prev != null) {
          prev.next = e.next;
        } else {
          table[index] = e.next;
        }
        count--;
        return true;
      }
    }
    return false;
  }

  /**
    * Rehashes the content of the table into a bigger table.
    * This method is called automatically when the hash table
    * size exceeds the threshold.
    */
  private void rehash() {
    int oldCapacity = table.length;
    Entry<A>[] oldTable = table;

    int newCapacity = oldCapacity * 2 + 1;
    Entry<A>[] newTable = buckets(newCapacity);

    threshold = (int) (newCapacity * loadFactor);
    table = newTable;

    for (int i = oldCapacity; i-- > 0;) {
      for (Entry<A> old = oldTable[i]; old != null;) {
        Entry<A> e = old;
        old = old.next;

        int index = (e.hash & 0x7FFFFFFF) % newCapacity;
        e.next = newTable[index];
        newTable[index] = e;
      }
    }
  }

  /**
    * Creates a copy of the hash table. A shallow copy is made,
    * the elements themselves are NOT cloned.
    */
  synchronized HashSetTab<A> copy() {
    HashSetTab<A> t = new HashSetTab<A>(table.length, loadFactor);
    for (int i = table.length; i-- > 0;) {
      if (table[i] != null) t.table[i] = table[i].copy();
    }
    t.count = count;
    return t;
  }

  /** Creates a new empty hash table which inherits
    * attributes such as capacity, loadFactor, and element descriptor. */
  synchronized <B> HashSetTab<B> deriveNew() {
    return new HashSetTab<B>(table.length, loadFactor);
  }
}
