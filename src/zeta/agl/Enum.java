package zeta.agl;

import java.util.NoSuchElementException;

/** An interface describing enumerations.<p>
  *
  * An enumeration is a object which allows to traverse the
  * elements of an aggregate. <p>
  *
  * Enumerations have state and usually shouldn't be stored in other
  * objects or passed as parameters to public methods. They are
  * intended as temporary objects in code such as:
  *
  * <pre>
  *  for(Enum&lt;A&gt; e = agg.enumerate(); e.more(); e.advance()){
  *    ... e.current() ...
  *  }
  *  </pre>
  *
  * Ported from Enum.pizza.  Pizza mangled {@code current()} (its signature mentions the type
  * parameter) to {@code zeta$agl$Enum$current}; the port carried that as an abstract bridge
  * while 2002 implementors existed.  None is left, so {@code current()} is plain again.
  *
  * Note the loop variable above was spelled `enum` in the original source; that became a
  * keyword in Java 5, so uses of it are renamed as each file is ported.
  */
public interface Enum<A> {

  /** Test if the enumeration has more elements. */
  boolean more();

  /** Return current element. */
  A current() throws NoSuchElementException;

  /** Advance to the next element. */
  void advance();
}
