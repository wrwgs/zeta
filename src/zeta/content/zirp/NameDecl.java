package zeta.content.zirp;

import zeta.util.AnnotatedTerm;
import zeta.util.Name;

/** A NameDecl is an annotated term which aggregates a Name. In the
 * abstract syntax it marks the *declaring* occurrence of a name, as
 * against {@link NameAppl} which marks an applied one.
 *
 * A Pizza single-case class whose case name equals the class name, so the compiled form is
 * the field, the constructor and a static factory of the same name -- and no `$$tag`, since
 * there is nothing to discriminate.  The annotation field `an` comes from
 * {@link AnnotatedTerm}; `DeclAttr` and `Layout` are the slots usually found in it here.
 */
public class NameDecl extends AnnotatedTerm {

  public Name name;

  public NameDecl(Name name) {
    this.name = name;
  }

  /** Pizza's generated case factory. */
  public static NameDecl NameDecl(Name name) {
    return new NameDecl(name);
  }
}
