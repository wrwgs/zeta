package zeta.agl;

/** An interface providing a total order on objects.
  *
  * Ported from Ordered.pizza.  The Pizza original was
  *
  *     public interface Ordered&lt;A&gt; { public int compareTo(A other); }
  *
  * which Pizza compiled to a single erased, mangled method
  * `int zeta$agl$Ordered$compareTo(Object)`.  The port carried that as an abstract bridge
  * while 2002 implementors existed.  None is left, so `compareTo` is plain again.
  */
public interface Ordered<A> {

  /** A total order on objects.
    * @return a value less then 0 if this is less then other,
    *   a value greater then 0 if this is greater then other,
    *   and 0 otherwise.
    */
  int compareTo(A other);
}
