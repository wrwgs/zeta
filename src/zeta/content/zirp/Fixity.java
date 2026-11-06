package zeta.content.zirp;

/** Fixity definitions. <p>
  *
  * @version $Id: Fixity.pizza,v 1.2 1998/09/13 15:13:01 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  * <p>Fixity is a single-case ADT whose case name equals the class name: Pizza emits no
  * $$tag, just the fields and a matching static factory.  The closure in
  * {@link #toFormat} used {@code Format.list(..., pizza.support.Closure, Aggregate)}
  * in the original; the ported {@code Format} offers a generic
  * {@code Function}-overload that is used instead.</p>
  */
public class Fixity extends zeta.util.Term {

  /** Priority assigned to relations (= -1). */
  public static final int relationPrio = -1;

  /** Minimal priority for expressions (= 0). */
  public static final int minExprPrio = 0;

  /** Maximal priority for expressions (= 9999). */
  public static final int maxExprPrio = 9999;

  /** Starting point for precedences of generics. */
  public static final int genericsPrio = 2;

  /** Precedences of cartesian product. */
  public static final int cartPrio = 8;

  /** Starting point for precedence of functions. */
  public static final int functionPrio = 10;

  /** Precedence of powerset construction. */
  public static final int powerPrio = 80;

  /** Starting point for precedence of generic prefixes. */
  public static final int prefixPrio = 90;

  /** Starting point for precedence of postfixes. */
  public static final int postfixPrio = 100;

  /** Precedence of juxtaposed application. */
  public static final int applyPrio = 900;

  /** A fixity definition.
    * @param prio  the binding priority of the entire construct
    * @param components  the components
    */
  public int prio;
  public boolean isGeneric;
  public Component[] components;

  public Fixity(int prio, boolean isGeneric, Component[] components) {
    this.prio = prio;
    this.isGeneric = isGeneric;
    this.components = components;
  }

  /** Pizza's generated case factory. */
  public static Fixity Fixity(int prio, boolean isGeneric, Component[] components) {
    return new Fixity(prio, isGeneric, components);
  }

  /** The class of components of fixity definitions. */
  public static class Component extends zeta.util.Term {

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$content$zirp$Fixity$Component$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_OPERAND = 1;
    private static final int TAG_OPERAND_LIST = 2;
    private static final int TAG_KEYWORD = 3;

    Component(int tag) {
      this.zeta$content$zirp$Fixity$Component$$tag = tag;
    }

    /** An operand with given priority. */
    public static class Operand extends Component {
      public int prio;

      public Operand(int prio) {
        super(TAG_OPERAND);
        this.prio = prio;
      }
    }

    /** A list of operands enclosed by names. */
    public static class OperandList extends Component {
      public zeta.util.Name lname;
      public zeta.util.Name rname;

      public OperandList(zeta.util.Name lname, zeta.util.Name rname) {
        super(TAG_OPERAND_LIST);
        this.lname = lname;
        this.rname = rname;
      }
    }

    /** A keyword, represented by the given name. */
    public static class Keyword extends Component {
      public zeta.util.Name name;

      public Keyword(zeta.util.Name name) {
        super(TAG_KEYWORD);
        this.name = name;
      }
    }

    /** Pizza's generated case factory. */
    public static Component Operand(int prio) {
      return new Operand(prio);
    }

    /** Pizza's generated case factory. */
    public static Component OperandList(zeta.util.Name lname, zeta.util.Name rname) {
      return new OperandList(lname, rname);
    }

    /** Pizza's generated case factory. */
    public static Component Keyword(zeta.util.Name name) {
      return new Keyword(name);
    }

    /** Make format of component. */
    public zeta.format.Format toFormat(zeta.format.FormatInfo info) {
      return switch (this) {
        case Operand o ->
          zeta.format.Format.literal("(" + o.prio + ")");
        case OperandList ol ->
          zeta.format.Format.literal(ol.lname + ",," + ol.rname);
        case Keyword k ->
          zeta.format.Format.literal(k.name.toString());
        default ->
          throw zeta.util.FatalError.unexpectedCase("Fixity.Component.toFormat");
      };
    }
  }

  /** Return the name associated with a fixity. */
  public zeta.util.Name makeName() {
    StringBuffer res = new StringBuffer();
    for (int i = 0; i < components.length; i++) {
      switch (components[i]) {
        case Component.Keyword k -> res.append(k.name.toString());
        case Component.Operand o -> res.append("_");
        case Component.OperandList ol -> res.append(ol.lname + ",," + ol.rname);
        default -> throw zeta.util.FatalError.unexpectedCase("Fixity.makeName");
      }
    }
    return new zeta.util.Name(res.toString());
  }

  /** Make format of fixity. */
  public zeta.format.Format toFormat(zeta.format.FormatInfo info) {
    zeta.format.Format prefix;
    if (isGeneric)
      prefix = zeta.format.Format.literal("[" + prio + "]: ");
    else
      prefix = zeta.format.Format.literal("(" + prio + "): ");
    return zeta.format.Format.block(
      prefix,
      zeta.format.Format.list(
        zeta.format.Format.empty,
        zeta.format.Format.space,
        zeta.format.Format.empty,
        (Component c) -> zeta.format.Format.buildFormat(c),
        new zeta.agl.ArraySeq<>(components))
    );
  }

}
