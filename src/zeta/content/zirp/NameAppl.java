package zeta.content.zirp;

import zeta.util.AnnotatedTerm;
import zeta.util.Name;

/** A NameAppl is an annotated term which aggregates a Name together
 * with the actual generic parameters of its application.
 *
 * `actuals` is filled in by the type checker where the name denotes a generic global
 * constant; {@link Act} is the annotation slot that records the inferred actualization.
 */
public class NameAppl extends AnnotatedTerm {

  public Name name;
  public Expr[] actuals;

  public NameAppl(Name name, Expr[] actuals) {
    this.name = name;
    this.actuals = actuals;
  }

  /** Pizza's generated case factory. */
  public static NameAppl NameAppl(Name name, Expr[] actuals) {
    return new NameAppl(name, actuals);
  }
}
