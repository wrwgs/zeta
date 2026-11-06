package zeta.content.zirp;

import zeta.util.AnnotatedTerm;
import zeta.util.Name;

/** The abstract syntax of units.<p>
  *
  * Two types of the syntax of units are distinguished: standard Z
  * sections and mSZ classes.
  *
  * @version $Id: UnitAbsy.pizza,v 1.3 1998/09/06 15:24:33 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * This is the root of `unchecked-absy` and `checked-absy`, two of the nine content types the
  * oracle baselines, so its rendering through `Term.toFormat` is pinned by 19 artifacts each.
  */
public class UnitAbsy extends AnnotatedTerm {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$zirp$UnitAbsy$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_SECTION = 1;
  private static final int TAG_CLASS_DEF = 2;

  UnitAbsy(int tag) {
    this.zeta$content$zirp$UnitAbsy$$tag = tag;
  }

  /** A standard Z section. */
  public static class Section extends UnitAbsy {
    public Name name;
    public Name[] parents;
    public Item[] items;

    public Section(Name name, Name[] parents, Item[] items) {
      super(TAG_SECTION);
      this.name = name;
      this.parents = parents;
      this.items = items;
    }
  }

  /** An mSZ class. */
  public static class ClassDef extends UnitAbsy {
    public Name name;
    public Name[] parents;
    public Name[] formals;
    public Item[] items;

    public ClassDef(Name name, Name[] parents, Name[] formals, Item[] items) {
      super(TAG_CLASS_DEF);
      this.name = name;
      this.parents = parents;
      this.formals = formals;
      this.items = items;
    }
  }

  /** Pizza's generated case factory. */
  public static UnitAbsy Section(Name name, Name[] parents, Item[] items) {
    return new Section(name, parents, items);
  }

  /** Pizza's generated case factory. */
  public static UnitAbsy ClassDef(Name name, Name[] parents, Name[] formals, Item[] items) {
    return new ClassDef(name, parents, formals, items);
  }
}
