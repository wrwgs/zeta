package zeta.agl;

import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.format.impl.FormatAppend;
import zeta.format.impl.FormatBlock;
import zeta.format.impl.FormatLine;
import zeta.format.impl.FormatSpace;
import zeta.format.impl.FormatText;

/** The basic aggregate class of finite mappings. This abstract class
  * is superclass of diverse implementation variants of mappings.
  *
  * @version $Id: Map.pizza,v 1.2 1998/12/15 12:05:24 buessow Exp $
  *
  * <h2>Port notes</h2>
  *
  * The element type is {@code Pair<A,B>}, as the Pizza source has it.  While
  * {@code pizza.lang.Pair} (not generic in the 2002 jar) was in use it had to be raw.
  *
  * Ported together with `HashMap`, which overrode nine of these under their mangled names
  * (see {@link Seq} for why that forced the cluster). `Map` uses `HashSet` in `domain()`, so
  * the `Set` cluster goes first; nothing in the `Set` group refers back.
  */
public abstract class Map<A, B> extends Aggregate<Pair<A, B>> {

  /** Test if element is defined in this mapping. */
  public abstract boolean defines(A elem);

  /** Test if all the elements of another aggregate are defined
    * in this mapping. */
  public boolean definesAll(Aggregate<A> other) {
    for (Enum<A> e = other.enumerate(); e.more(); e.advance()) {
      if (!defines(e.current())) return false;
    }
    return true;
  }

  /** Define element to map to value, perhaps overwriting an older
    * definition. */
  public Map<A, B> define(A elem, B value) {
    return defineAll(new ArraySeq<Pair<A, B>>(Pair.Pair(elem, value)));
  }

  /** Take the pairs from another aggregate and define them in the map. */
  public abstract Map<A, B> defineAll(Aggregate<Pair<A, B>> other);

  /** Take elements and values from two other aggregates and pairwise
    * define them in the map.
    * @exception NoSuchElementException if the aggregates have not
    * the same size.
    */
  // The `||` is deliberate, not a typo for `&&`: when one enumeration runs out first,
  // `current()` on it throws NoSuchElementException, which is the documented behaviour for
  // aggregates of unequal size.
  public Map<A, B> defineAll(Aggregate<A> elems, Aggregate<B> values)
      throws NoSuchElementException {
    Map<A, B> res = this;
    Enum<A> edom = elems.enumerate();
    Enum<B> eran = values.enumerate();
    while (edom.more() || eran.more()) {
      res = res.define(edom.current(), eran.current());
      edom.advance();
      eran.advance();
    }
    return res;
  }

  /** Replace a definition, returning its old value.
    * @exception  NoSuchElementException if element is not defined
    * in map.
    */
  public Pair<Map<A, B>, B> replace(A elem, B value) throws NoSuchElementException {
    return Pair.Pair(define(elem, value), get(elem));
  }

  /** Undefine element in the map, if it has a definition. */
  public Map<A, B> undefine(A elem) {
    return undefineAll(new ArraySeq<A>(elem));
  }

  /** Undefine elements from another aggregate. */
  public abstract Map<A, B> undefineAll(Aggregate<A> other);

  /** Get value element is mapped to.
    * @exception  NoSuchElementException if element is not defined
    * in mapping.
    */
  public abstract B get(A elem) throws NoSuchElementException;

  /** Get value element is mapped to, or null if their is no mapping.
    * Only provided for mappings to objects.
    */
  public static <C, D> D tryGet(Map<C, D> map, C elem) {
    try {
      return map.get(elem);
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  /** Get sequence of values assigned to elements of the given
    * aggregate in this map.
    * @exception NoSuchElementException if any of the elements in
    *           aggregate is not defined in map.
    */
  public Seq<B> getAll(Aggregate<A> elems) throws NoSuchElementException {
    Seq<B> buf = new BufferSeq<B>(0, 50, -100);
    for (Enum<A> e = elems.enumerate(); e.more(); e.advance()) {
      buf = buf.append(get(e.current()));
    }
    return ArraySeq.from(buf);
  }

  /** Get the domain of this mapping as a set. */
  public Set<A> domain() {
    Set<A> set = new HashSet<A>();
    for (Enum<Pair<A, B>> e = enumerate(); e.more(); e.advance()) {
      set = set.include(e.current().fst);
    }
    return set;
  }

  /** Get the range of this mapping as a sequence. */
  public Seq<B> range() {
    Seq<B> buf = new BufferSeq<B>(0, 50, -100);
    for (Enum<Pair<A, B>> e = enumerate(); e.more(); e.advance()) {
      buf = buf.append(e.current().snd);
    }
    return ArraySeq.from(buf);
  }

  /** Map a function over the range of this mapping, with domain
    * element passed. */
  public abstract <C> Map<A, C> mapWithDom(BiFunction<A, B, C> f);

  /** Map a function over the range of this mapping. */
  public <C> Map<A, C> map(Function<B, C> f) {
    return mapWithDom((x, y) -> f.apply(y));
  }

  /** Filter this mapping. */
  public Map<A, B> filter(BiPredicate<A, B> p) {
    Map<A, B> res = this;
    for (Enum<Pair<A, B>> e = enumerate(); e.more(); e.advance()) {
      Pair<A, B> c = e.current();
      if (!p.test(c.fst, c.snd)) res = res.undefine(c.fst);
    }
    return res;
  }

  /** Compose another mapping with this mapping.
    * @exception NoSuchElementException  if any element of the range
    *   of this mapping is not defined in the domain of the other
    *   mapping.
    */
  public <C> Map<A, C> compose(Map<B, C> other) throws NoSuchElementException {
    return map(b -> other.get(b));
  }

  /** Merge another mapping with this mapping.
    * @param mf the entry merging function. It is called for each element
    *   in the domain of this or the other mapping. The range
    *   values of both mappings are passed as optional values,
    *   where either one or both will be available. If the returned
    *   option is not available, no definition will be made in
    *   the resulting mapping.
    */
  @SuppressWarnings("unchecked")
  public <C, D> Map<A, D> merge(
      Map<A, C> other,
      Functions.Fun3<A, Option<B>, Option<C>, Option<D>> mf) {
    Set<A> thisDomain = domain();
    Set<A> otherDomain = other.domain();
    Set<A> common = thisDomain.intersect(otherDomain);
    Set<A> thisOnly = thisDomain.excludeAll(common);
    Set<A> otherOnly = otherDomain.excludeAll(common);

    // hmmm ... perhaps aggregates should provide a method for the
    // hack below?
    Map<A, D> res = this.undefineAll(thisDomain)
                        .map(y -> { throw new InternalError(); });

    for (Enum<A> e = common.enumerate(); e.more(); e.advance()) {
      A x = e.current();
      Option<D> r = mf.apply(x, Option.Some(this.get(x)), Option.Some(other.get(x)));
      if (r instanceof Option.Some s) res = res.define(x, (D) s.value);
    }

    for (Enum<A> e = thisOnly.enumerate(); e.more(); e.advance()) {
      A x = e.current();
      Option<D> r = mf.apply(x, Option.Some(this.get(x)), Option.None);
      if (r instanceof Option.Some s) res = res.define(x, (D) s.value);
    }

    for (Enum<A> e = otherOnly.enumerate(); e.more(); e.advance()) {
      A x = e.current();
      Option<D> r = mf.apply(x, Option.None, Option.Some(other.get(x)));
      if (r instanceof Option.Some s) res = res.define(x, (D) s.value);
    }

    return res;
  }

  /** FIXME: to be implemented more efficiently */
  public void apply(BiConsumer<A, B> f) {
    apply((java.util.function.Consumer<Pair<A, B>>) p -> f.accept(p.fst, p.snd));
  }

  /** Generate a default format for the contents of the mapping. */
  public Format toFormat(FormatInfo info) {
    if (info.visited(this)) {
      return new FormatText("<" + info.visitNumber(this) + ">");
    }
    int ref = info.visit(this);
    String className = getClass().getName();
    int di = className.lastIndexOf(".");
    if (di >= 0) className = className.substring(di + 1);
    className = className.replace('$', '.');
    return new FormatLine(new Format[]{
      new FormatText("<" + ref + ">" + className),
      new FormatAppend(new Format[]{
        new FormatText("{"),
        toFormatLine(new FormatText(","), elem -> new FormatLine(new Format[]{
          new FormatAppend(new Format[]{
            Format.buildFormat(info, elem.fst),
            new FormatSpace(1),
            new FormatText("=>"),
            new FormatSpace(1)}),
          new FormatBlock(1, new Format[]{
            Format.buildFormat(info, elem.snd)})})),
        new FormatText("}")})});
  }
}
