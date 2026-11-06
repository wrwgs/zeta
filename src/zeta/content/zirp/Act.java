package zeta.content.zirp;

import zeta.util.Annotation;
import zeta.util.Slot;

/** Actualization annotation slots.
  *
  * This slot is provided under certain conditions
  * at terms of class  <code>absy.NameAppl</code>
  * if the syntax is context-checked.
  *
  * An actualization represents the sequence of instances of generic
  * type parameters as they are derived by a type checker.
  *
  * This annotation is only present at name applications which
  * represent generic global constants. It is not present at names
  * which represent mSZ classes.
  *
  * The expressions contained in actualizations are restricted
  * to type expressions, as defined in class <code>Type</code>.
  *
  * @see zeta.content.zirp.Type
  *
  * @version $Id: Act.pizza,v 1.1.1.1 1998/09/01 10:51:17 wg Exp $
  *
  * The commented-out `Term.internArray` call in `put` is left as found -- it is the only
  * place in the system that would have used the hash-consing facility in `zeta.util.Term`,
  * which is itself unreachable (see that class).
  */
public class Act {

  /** The slot object. */
  public static final Slot slot = Slot.register(Act.class.getName(),
                                                Expr[].class);

  /** Set value of actualization annotation slot. */
  public static void put(Annotation an, Expr[] act) {
    slot.put(an, act);
    // slot.put(an, Term.internArray(act));
  }

  /** Get value of actualization annotation slot. */
  public static Expr[] get(Annotation an) {
    return (Expr[]) slot.get(an);
  }

  /** Test wether actualization annotation slot is provided. */
  public static boolean isProvided(Annotation an) {
    return slot.isProvided(an);
  }
}
