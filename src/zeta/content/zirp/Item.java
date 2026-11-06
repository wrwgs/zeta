package zeta.content.zirp;

import java.io.ObjectStreamException;

import zeta.util.CaseConstants;

/** The abstract syntax of Z items.
  *
  * @version $Id: Item.pizza,v 1.5 1998/09/14 13:48:55 wg Exp $
  *
  * <h2>Port notes</h2>
  * <p>{@link AttributeKind} has only nullary constant cases (no parentheses in Pizza):
  * the base is not abstract, the constants are {@code static final} instances, and there
  * are no case subclasses.  The reference field order places the three public constants
  * <em>before</em> the $$tag instance field, which is preserved here via {@code static{}}
  * initialisation.  Tags were read from the 2002 class files; Item tags 1-9, AttributeKind
  * tags 1-3.</p>
  */
public class Item extends zeta.util.AnnotatedTerm {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$zirp$Item$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_AXIOMATIC_DEF = 1;
  private static final int TAG_FIXITY_DEF = 2;
  private static final int TAG_EMBEDDED_TEXT = 3;
  private static final int TAG_CONFIG_DEF = 4;
  private static final int TAG_STATECHART_DEF = 5;
  private static final int TAG_TRANSITION_DEF = 6;
  private static final int TAG_STATIC_REACTION_DEF = 7;
  private static final int TAG_ATTRIBUTE_DEF = 8;
  private static final int TAG_ENRICH_DEF = 9;

  Item(int tag) {
    this.zeta$content$zirp$Item$$tag = tag;
  }

  /** Axiomatic definition.
    * @param formals the sequence of generic formals this definition
    *   depends on.
    * @param def an expression denoting a schema. The names of
    * this schema are introduced as global constants. <p>
    *
    * Note that schema definitions are treated like abbreviations in
    * Standard Z. In turn, abbreviations are treated as schema text
    * with equational declarations.  Henceforth a schema definition
    * such as <code> S =^= E </code> is represented as:
    *
    <pre>
      AxiomaticDef({}, Expr.Text({Expr.Decl.Eqn("S", "E")}, {}))
    </pre>
    *
    */
  public static class AxiomaticDef extends Item {
    public NameDecl[] formals;
    public Expr def;

    public AxiomaticDef(NameDecl[] formals, Expr def) {
      super(TAG_AXIOMATIC_DEF);
      this.formals = formals;
      this.def = def;
    }
  }

  /** Fixity definition.
   * @param fixity  the fixity being defined.
   */
  public static class FixityDef extends Item {
    public Fixity fixity;

    public FixityDef(Fixity fixity) {
      super(TAG_FIXITY_DEF);
      this.fixity = fixity;
    }
  }

  /**
   * Text object embedded in abstract syntax, containing eg.
   * references, macros, and pragmas.
   * @param text  the text object.
   */
  public static class EmbeddedText extends Item {
    public zeta.content.text.Text text;

    public EmbeddedText(zeta.content.text.Text text) {
      super(TAG_EMBEDDED_TEXT);
      this.text = text;
    }
  }

  /** Configuration definition. mSZ only, inside of class definitions.
    * NOTE: not yet elaborated. */
  public static class ConfigDef extends Item {
    public Configuration config;

    public ConfigDef(Configuration config) {
      super(TAG_CONFIG_DEF);
      this.config = config;
    }
  }

  /** Statechart definition. mSZ only, inside of class definitions.
    * @param root  the root state. */
  public static class StatechartDef extends Item {
    public State root;

    public StatechartDef(State root) {
      super(TAG_STATECHART_DEF);
      this.root = root;
    }
  }

  /** Named transition definition. mSZ only, inside of class definitions.
    * @param name   name of the transition
    * @param reactions  a sequence of lable expressions describing the
    *              reactions of the transition
    *
    * */
  public static class TransitionDef extends Item {
    public NameDecl name;
    public LabelExpr[] reactions;

    public TransitionDef(NameDecl name, LabelExpr[] reactions) {
      super(TAG_TRANSITION_DEF);
      this.name = name;
      this.reactions = reactions;
    }
  }

  /** Static reactions. mSZ only, inside of class definitions.
    * @param name  the name of the state this
    *              static reaction is associated with. <code>null</code>
    *              is used for the root state
    * @param reactions a sequence of lable expressions describing the
                   static reactions in the state <code>name</code>
    */
  public static class StaticReactionDef extends Item {
    public NameDecl name;
    public LabelExpr[] reactions;

    public StaticReactionDef(NameDecl name, LabelExpr[] reactions) {
      super(TAG_STATIC_REACTION_DEF);
      this.name = name;
      this.reactions = reactions;
    }
  }

  /** Attribute definition. mSZ only, inside of class definitions.
    * @param kind  the kind of attriute
    * @param names the names the attribute is attached to
    */
  public static class AttributeDef extends Item {
    public AttributeKind attrib;
    public NameAppl[] name;

    public AttributeDef(AttributeKind attrib, NameAppl[] name) {
      super(TAG_ATTRIBUTE_DEF);
      this.attrib = attrib;
      this.name = name;
    }
  }

  /** Kind of attributes. */
  public static class AttributeKind extends zeta.util.Term {

    // Public constants must be declared BEFORE $$tag to match reference field order.
    /** No special meaning in the kernel of mSZ. */
    public static final AttributeKind Plain;
    /** Variable only allowed as input. */
    public static final AttributeKind Input;
    /** Variable is derived. */
    public static final AttributeKind Derived;

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$content$zirp$Item$AttributeKind$$tag;

    private static final int TAG_PLAIN = 1;
    private static final int TAG_INPUT = 2;
    private static final int TAG_DERIVED = 3;

    static {
      Plain = new AttributeKind(TAG_PLAIN);
      Input = new AttributeKind(TAG_INPUT);
      Derived = new AttributeKind(TAG_DERIVED);
    }

    AttributeKind(int tag) {
      this.zeta$content$zirp$Item$AttributeKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, AttributeKind.class);
    }
  }

  /** Enrichment of class. mSZ only, inside of class definitions.
    * @param name name of the enriched class, inclusive of actualizations
    * of formals. */
  public static class EnrichDef extends Item {
    public NameAppl name;

    public EnrichDef(NameAppl name) {
      super(TAG_ENRICH_DEF);
      this.name = name;
    }
  }

  /** Pizza's generated case factory. */
  public static Item AxiomaticDef(NameDecl[] formals, Expr def) {
    return new AxiomaticDef(formals, def);
  }

  /** Pizza's generated case factory. */
  public static Item FixityDef(Fixity fixity) {
    return new FixityDef(fixity);
  }

  /** Pizza's generated case factory. */
  public static Item EmbeddedText(zeta.content.text.Text text) {
    return new EmbeddedText(text);
  }

  /** Pizza's generated case factory. */
  public static Item ConfigDef(Configuration config) {
    return new ConfigDef(config);
  }

  /** Pizza's generated case factory. */
  public static Item StatechartDef(State root) {
    return new StatechartDef(root);
  }

  /** Pizza's generated case factory. */
  public static Item TransitionDef(NameDecl name, LabelExpr[] reactions) {
    return new TransitionDef(name, reactions);
  }

  /** Pizza's generated case factory. */
  public static Item StaticReactionDef(NameDecl name, LabelExpr[] reactions) {
    return new StaticReactionDef(name, reactions);
  }

  /** Pizza's generated case factory. */
  public static Item AttributeDef(AttributeKind attrib, NameAppl[] name) {
    return new AttributeDef(attrib, name);
  }

  /** Pizza's generated case factory. */
  public static Item EnrichDef(NameAppl name) {
    return new EnrichDef(name);
  }

}
