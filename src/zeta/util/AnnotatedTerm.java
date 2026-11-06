package zeta.util;

/** The abstract class AnnotatedTerm is the superclass of any term of
 * which carries an annotation. An annotated
 * term agregates an instance of class Annotation.
 *
 * @see zeta.util.Term
 * @see zeta.util.Annotation
 * @see zeta.util.Slot
 *
 * @version $Id: AnnotatedTerm.pizza,v 1.2 2000/03/13 06:57:11 wg Exp $
 *
 * The field is public and not final, and stays so: it is read and assigned directly by the
 * checker and the reducer, and `Term.toFormat` finds it reflectively among the public fields.
 * It is also why `Term.equals` documents that it ignores annotations -- `Annotation` is not a
 * `Term`, so `getSubtermFields` skips it.
 */
public abstract class AnnotatedTerm extends Term {

  /** The annotation field. */
  public Annotation an = new Annotation();

}
