package zeta.content.text;

import java.io.ObjectStreamException;

import zeta.content.zirp.Fixity;
import zeta.session.Locator;
import zeta.util.CaseConstants;
import zeta.util.Name;
import zeta.util.Term;

/** The representation of specification text. <p>
  *
  * A top-level document is given by a value of the
  * document markup variant.
  *
  * @version $Id: Text.pizza,v 1.3 1998/09/06 15:25:40 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * Twelve cases and two nested ADTs, which is what makes it the first good test of the ADT
  * recipe at scale.  Every tag below was read out of the 2002 class files with
  * `harness/adt_tags.py`, not inferred from declaration order -- the two are easy to confuse
  * here, because the nested `ZedMarkupKind` and `FormatKind` declarations sit *between* cases
  * of the enclosing type, and `FormatEnd` is declared after `FormatBegin` but is a constant
  * rather than a class.
  *
  * The base cannot be sealed, and not only because `FormatEnd` is a constant on it: it also
  * carries instance state, the `locator` field common to every case.  That field is public and
  * mutable because the parser fills it in after construction, and `Term.toFormat` finds it
  * reflectively among the public fields.
  *
  * `FormatKind` does not extend {@link Term}; `ZedMarkupKind` does.  Kept as found -- the
  * difference decides whether `Term`'s reflective `equals` and `toFormat` apply.
  */
public class Text extends Term {

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_LITERAL = 1;
  private static final int TAG_DOC_MARKUP = 2;
  private static final int TAG_MODEL_INCLUSION = 3;
  private static final int TAG_ZED_SECTION = 4;
  private static final int TAG_ZED_CLASS = 5;
  private static final int TAG_ZED_FIXITY = 6;
  private static final int TAG_ZED_MACRO = 7;
  private static final int TAG_ZED_PRAGMA = 8;
  private static final int TAG_ZED_MARKUP = 9;
  private static final int TAG_ZED_TOKEN = 10;
  private static final int TAG_FORMAT_BEGIN = 11;
  private static final int TAG_FORMAT_END = 12;

  /** A literal text, with <code>contents</code> not broken into
    * further sub-texts. The contents may be an arbitrary string
    * (including newlines and such).
    */
  public static class Literal extends Text {
    public String contents;

    public Literal(String contents) {
      super(TAG_LITERAL);
      this.contents = contents;
    }
  }

  /** A document markup, grouping subtexts. */
  public static class DocMarkup extends Text {
    public String name;
    public String[] params;
    public Text[] subtexts;

    public DocMarkup(String name, String[] params, Text[] subtexts) {
      super(TAG_DOC_MARKUP);
      this.name = name;
      this.params = params;
      this.subtexts = subtexts;
    }
  }

  /** A reference to the context-depend content of the named unit. */
  public static class ModelInclusion extends Text {
    public Name name;

    public ModelInclusion(Name name) {
      super(TAG_MODEL_INCLUSION);
      this.name = name;
    }
  }

  /** A Z section. */
  public static class ZedSection extends Text {
    public Name name;
    public Name[] parents;
    public Text[] subtexts;

    public ZedSection(Name name, Name[] parents, Text[] subtexts) {
      super(TAG_ZED_SECTION);
      this.name = name;
      this.parents = parents;
      this.subtexts = subtexts;
    }
  }

  /** A mSZ class. */
  public static class ZedClass extends Text {
    public Name name;
    public Name[] parents;
    public Name[] formals;
    public Text[] subtexts;

    public ZedClass(Name name, Name[] parents, Name[] formals, Text[] subtexts) {
      super(TAG_ZED_CLASS);
      this.name = name;
      this.parents = parents;
      this.formals = formals;
      this.subtexts = subtexts;
    }
  }

  /** A Z fixity. */
  public static class ZedFixity extends Text {
    public Fixity fixity;

    public ZedFixity(Fixity fixity) {
      super(TAG_ZED_FIXITY);
      this.fixity = fixity;
    }
  }

  /** A Z macro. */
  public static class ZedMacro extends Text {
    public Name name;
    public int argc;
    public Lexem[] def;

    public ZedMacro(Name name, int argc, Lexem[] def) {
      super(TAG_ZED_MACRO);
      this.name = name;
      this.argc = argc;
      this.def = def;
    }
  }

  /** A Z pragma. */
  public static class ZedPragma extends Text {
    public Name name;
    public Text[] subtexts;

    public ZedPragma(Name name, Text[] subtexts) {
      super(TAG_ZED_PRAGMA);
      this.name = name;
      this.subtexts = subtexts;
    }
  }

  /** A Z or MSZ paragraph. */
  public static class ZedMarkup extends Text {
    public ZedMarkupKind kind;
    public Text[] header;
    public Text[] decls;
    public Text[] props;

    public ZedMarkup(ZedMarkupKind kind, Text[] header, Text[] decls, Text[] props) {
      super(TAG_ZED_MARKUP);
      this.kind = kind;
      this.header = header;
      this.decls = decls;
      this.props = props;
    }
  }

  /** The class of kinds of Z and MSZ paragraphs. */
  public static class ZedMarkupKind extends Term {

    private static final int TAG_UNBOXED = 1;
    private static final int TAG_SCHEMA = 2;
    private static final int TAG_AXIOMATIC_DEF = 3;
    private static final int TAG_XOR_STATE = 4;
    private static final int TAG_AND_STATE = 5;
    private static final int TAG_CONFIG = 6;
    private static final int TAG_REACTION = 7;
    private static final int TAG_TRANSITION = 8;

    /** Pizza's discriminant. */
    public final int zeta$content$text$Text$ZedMarkupKind$$tag;

    ZedMarkupKind(int tag) {
      this.zeta$content$text$Text$ZedMarkupKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, ZedMarkupKind.class);
    }

    public static final ZedMarkupKind Unboxed = new ZedMarkupKind(TAG_UNBOXED);
    public static final ZedMarkupKind Schema = new ZedMarkupKind(TAG_SCHEMA);
    public static final ZedMarkupKind AxiomaticDef = new ZedMarkupKind(TAG_AXIOMATIC_DEF);
    public static final ZedMarkupKind XorState = new ZedMarkupKind(TAG_XOR_STATE);
    public static final ZedMarkupKind AndState = new ZedMarkupKind(TAG_AND_STATE);
    public static final ZedMarkupKind Config = new ZedMarkupKind(TAG_CONFIG);
    public static final ZedMarkupKind Reaction = new ZedMarkupKind(TAG_REACTION);
    public static final ZedMarkupKind Transition = new ZedMarkupKind(TAG_TRANSITION);
  }

  /** A token, with <code>lexem</code> describing its lexical value. */
  public static class ZedToken extends Text {
    public Lexem lexem;

    public ZedToken(Lexem lexem) {
      super(TAG_ZED_TOKEN);
      this.lexem = lexem;
    }
  }

  /** Layout: format groups. */
  public static class FormatBegin extends Text {
    public FormatKind kind;

    public FormatBegin(FormatKind kind) {
      super(TAG_FORMAT_BEGIN);
      this.kind = kind;
    }
  }

  /** Layout: end of a format group. */
  public static final Text FormatEnd = new Text(TAG_FORMAT_END);

  /** Layout: kinds of format groups. */
  public static class FormatKind {

    private static final int TAG_BLOCK = 1;
    private static final int TAG_GROUP = 2;
    private static final int TAG_BESIDE = 3;
    private static final int TAG_BENEATH = 4;
    private static final int TAG_APPEND = 5;
    private static final int TAG_LINE = 6;

    /** Pizza's discriminant. */
    public final int zeta$content$text$Text$FormatKind$$tag;

    FormatKind(int tag) {
      this.zeta$content$text$Text$FormatKind$$tag = tag;
    }

    public static class Block extends FormatKind {
      public int indent;

      public Block(int indent) {
        super(TAG_BLOCK);
        this.indent = indent;
      }
    }

    public static class Group extends FormatKind {
      public int indent;

      public Group(int indent) {
        super(TAG_GROUP);
        this.indent = indent;
      }
    }

    public static class Beside extends FormatKind {
      public int indent;

      public Beside(int indent) {
        super(TAG_BESIDE);
        this.indent = indent;
      }
    }

    public static class Beneath extends FormatKind {
      public int indent;

      public Beneath(int indent) {
        super(TAG_BENEATH);
        this.indent = indent;
      }
    }

    public static class Append extends FormatKind {
      public int indent;

      public Append(int indent) {
        super(TAG_APPEND);
        this.indent = indent;
      }
    }

    public static class Line extends FormatKind {
      public int indent;

      public Line(int indent) {
        super(TAG_LINE);
        this.indent = indent;
      }
    }

    /** Pizza's generated case factories. */
    public static FormatKind Block(int indent) { return new Block(indent); }

    public static FormatKind Group(int indent) { return new Group(indent); }

    public static FormatKind Beside(int indent) { return new Beside(indent); }

    public static FormatKind Beneath(int indent) { return new Beneath(indent); }

    public static FormatKind Append(int indent) { return new Append(indent); }

    public static FormatKind Line(int indent) { return new Line(indent); }
  }

  /** Field common to all texts. If non-null, describes the locator
    * of this piece of text. */
  public Locator locator = null;

  // PORT NOTE -- the ORDER of the two fields above and below is observable, which is why the
  // tag is declared here at the end rather than at the top of the class where it would read
  // better.  `Term.toFormat` renders a term by walking `getClass().getFields()`, skips any
  // name containing `$` (so the tag itself is invisible), and chooses the closing separator
  // with `i == subterms.length - 1` -- an index into ALL the fields, skipped ones included.
  // So if the last field is the tag, nothing gets the closing ")" and every field gets ",";
  // if the last field is `locator`, it gets ")".  Pizza appends the tag after the declared
  // fields, so matching it means declaring `locator` first.  Getting this wrong changed every
  // `text` artifact and several `absy` ones -- the oracle caught it, nothing else could have.

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$text$Text$$tag;

  Text(int tag) {
    this.zeta$content$text$Text$$tag = tag;
  }

  // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
  // so identity tests on it hold as they do in memory (Pizza compared tags).  The
  // argument is in zeta.util.CaseConstants and docs/serialization.md.
  private Object readResolve() throws ObjectStreamException {
    return CaseConstants.resolve(this, Text.class);
  }

  /* ------------------------------------------------- Pizza's generated case factories. */

  public static Text Literal(String contents) {
    return new Literal(contents);
  }

  public static Text DocMarkup(String name, String[] params, Text[] subtexts) {
    return new DocMarkup(name, params, subtexts);
  }

  public static Text ModelInclusion(Name name) {
    return new ModelInclusion(name);
  }

  public static Text ZedSection(Name name, Name[] parents, Text[] subtexts) {
    return new ZedSection(name, parents, subtexts);
  }

  public static Text ZedClass(Name name, Name[] parents, Name[] formals, Text[] subtexts) {
    return new ZedClass(name, parents, formals, subtexts);
  }

  public static Text ZedFixity(Fixity fixity) {
    return new ZedFixity(fixity);
  }

  public static Text ZedMacro(Name name, int argc, Lexem[] def) {
    return new ZedMacro(name, argc, def);
  }

  public static Text ZedPragma(Name name, Text[] subtexts) {
    return new ZedPragma(name, subtexts);
  }

  public static Text ZedMarkup(ZedMarkupKind kind, Text[] header, Text[] decls,
                               Text[] props) {
    return new ZedMarkup(kind, header, decls, props);
  }

  public static Text ZedToken(Lexem lexem) {
    return new ZedToken(lexem);
  }

  public static Text FormatBegin(FormatKind kind) {
    return new FormatBegin(kind);
  }
}
