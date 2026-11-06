package zeta.content.zirp;

import zeta.util.AnnotatedTerm;

/** The abstract syntax of Statechart Transitions.
  *
  * A transition connects starts and ends at states or at connectors.
  * Connectors.  Connectors can belong to a sub-state.  In this case
  * the transition enters or exits a sub-state directly.
  *
  * @version $Id: Transition.pizza,v 1.2 1998/09/06 15:24:32 wg Exp $
  */
public class Transition extends AnnotatedTerm {

  public NameAppl source;
  public NameAppl target;
  public LabelExpr label;

  /** A transition.
    * @param source  the source node
    * @param target  the target node
    * @param label   the label.
    */
  public Transition(NameAppl source, NameAppl target, LabelExpr label) {
    this.source = source;
    this.target = target;
    this.label = label;
  }

  /** Pizza's generated case factory. */
  public static Transition Transition(NameAppl source, NameAppl target, LabelExpr label) {
    return new Transition(source, target, label);
  }
}
