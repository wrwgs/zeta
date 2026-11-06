package zeta.content.zirp;

import zeta.util.AnnotatedTerm;

/** The abstract syntax of label expression.
  *
  * A label expression describes a statechart-transition.
  *
  * @version $Id: LabelExpr.pizza,v 1.1.1.1 1998/09/01 10:51:17 wg Exp $
  */
public class LabelExpr extends AnnotatedTerm {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$zirp$LabelExpr$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_GUARDED = 1;
  private static final int TAG_TEMPORAL = 2;
  private static final int TAG_REFERENCE = 3;

  LabelExpr(int tag) {
    this.zeta$content$zirp$LabelExpr$$tag = tag;
  }

  /** A guard/action pair. */
  public static class Guarded extends LabelExpr {
    public Predicate guard;
    public Predicate action;

    public Guarded(Predicate guard, Predicate action) {
      super(TAG_GUARDED);
      this.guard = guard;
      this.action = action;
    }
  }

  /** A temporal predicate. */
  public static class Temporal extends LabelExpr {
    public Predicate predicate;

    public Temporal(Predicate predicate) {
      super(TAG_TEMPORAL);
      this.predicate = predicate;
    }
  }

  /** A reference to a transition name. */
  public static class Reference extends LabelExpr {
    public NameAppl name;

    public Reference(NameAppl name) {
      super(TAG_REFERENCE);
      this.name = name;
    }
  }

  /** Pizza's generated case factory. */
  public static LabelExpr Guarded(Predicate guard, Predicate action) {
    return new Guarded(guard, action);
  }

  /** Pizza's generated case factory. */
  public static LabelExpr Temporal(Predicate predicate) {
    return new Temporal(predicate);
  }

  /** Pizza's generated case factory. */
  public static LabelExpr Reference(NameAppl name) {
    return new Reference(name);
  }

  /** Map a predicate to a label expression. For downwards compatibility. */
  static LabelExpr transform(Predicate pred) {
    LabelExpr res;
    if (pred instanceof Predicate.Binary b) {
      res = new Guarded(b.left, b.right);
    } else {
      res = new Temporal(pred);
    }
    return res;
  }
}
