package zeta.content.zirp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.NoSuchElementException;
import java.util.function.Function;

import zeta.agl.ArraySeq;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.text.Lexem;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.util.AnnotatedTerm;
import zeta.util.FatalError;
import zeta.util.Name;
import zeta.util.Term;

/**
 * The class of unit environments. <p>
 *
 * A unit environment gives information about macros, fixities,
 * and global names defined in a ZIRP unit. <p>
 *
 * Unit environments may be queried using the session system.
 *
 * @see ZirpContentTypes
 * @see zeta.session.Unit#query
 *
 * @version $Id: UnitEnv.pizza,v 1.10 2000/02/10 10:20:34 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * <p>The four information records are Pizza single-case classes whose case name equals the
 * class name, so each compiles to its fields, a public constructor and a static factory of
 * the same name -- and carries no <code>$$tag</code>.  They extend
 * {@link zeta.util.AnnotatedTerm}, whose <code>an</code> field the import methods below copy
 * across; <code>LexemInfo</code> is the one record that extends nothing at all.</p>
 *
 * <p>The private field order is the 2002 field order (note that <code>constants</code> sits
 * between <code>contextParentNames</code> and <code>fixities</code>, and
 * <code>macros</code> after <code>closingKeywords</code>).  No field here is public, so
 * <code>Term.toFormat</code> cannot see any of them -- this class overrides
 * <code>toFormat</code> anyway -- but the order is kept because it is what the reference
 * declares.</p>
 *
 * <p>The seven Pizza closures became lambdas unchanged: none of them assigns to a captured
 * local.  They write either to a field of <code>this</code> (<code>macros</code>,
 * <code>fixities</code>, <code>pragmas</code>, <code>diag</code>, by way of
 * <code>declareMacro</code> and friends) or to a field of the visited record
 * (<code>info.origin</code>), both of which a lambda may do.  <code>Map.apply</code> is
 * taken in its two-argument <code>BiConsumer</code> form, which forwards to
 * <code>Aggregate.apply</code> and therefore visits in the same enumeration order.</p>
 *
 * <p>Two defects of the 1998 code are preserved deliberately, each marked PORT NOTE at its
 * site; both feed <code>diag</code>, i.e. exactly the warning paths the negative suites
 * pin.</p>
 */
public class UnitEnv extends Term {

  // DATA STRUCTURES

  /**
   * Information record for a pragma declaration. May carry an annotation.
   */
  public static class PragmaInfo extends AnnotatedTerm {
    public Name origin;
    public Name name;
    public Locator locator;
    public Lexem[] value;

    public PragmaInfo(Name origin,
                      Name name,
                      Locator locator,
                      Lexem[] value) {
      this.origin = origin;
      this.name = name;
      this.locator = locator;
      this.value = value;
    }

    /** Pizza's generated case factory. */
    public static PragmaInfo PragmaInfo(Name origin,
                                        Name name,
                                        Locator locator,
                                        Lexem[] value) {
      return new PragmaInfo(origin, name, locator, value);
    }

    public Format toFormat(FormatInfo info) {
      return Format.block(
               Format.literal(origin + ": " + name),
               Format.list(Format.empty, Format.space, Format.empty, value)
             );
    }
  }


  /**
   * Information record for a macro declaration. May carry an annotation.
   */
  public static class MacroInfo extends AnnotatedTerm {

   /**
   * @param name    the name
   * @param locator  the declaration locator
   * @param argc    the number of arguments of the macro
   * @param subs    the lexical substitution.
   */
    public Name origin;
    public Name name;
    public Locator locator;
    public int argc;
    public Lexem[] subs;

    public MacroInfo(Name origin,
                     Name name, Locator locator, int argc, Lexem[] subs) {
      this.origin = origin;
      this.name = name;
      this.locator = locator;
      this.argc = argc;
      this.subs = subs;
    }

    /** Pizza's generated case factory. */
    public static MacroInfo MacroInfo(Name origin,
                                      Name name, Locator locator, int argc,
                                      Lexem[] subs) {
      return new MacroInfo(origin, name, locator, argc, subs);
    }

    public Format toFormat(FormatInfo info) {
      return Format.block(
               Format.literal(origin + ": " + name + " " + argc + " "),
               Format.list(Format.empty, Format.space, Format.empty, subs)
             );
    }

    /** For effiency. */
    private void writeObject(ObjectOutputStream s)
    throws IOException {
      s.writeObject(origin);
      s.writeObject(name);
      s.writeObject(locator);
      s.writeInt(argc);
      s.writeObject(subs);
    }
    private void readObject(ObjectInputStream s)
    throws IOException, ClassNotFoundException {
      origin = (Name)s.readObject();
      name = (Name)s.readObject();
      locator = (Locator)s.readObject();
      argc = s.readInt();
      subs = (Lexem[])s.readObject();
    }

  }

  /**
   * Information record for a fixity declaration. May carry an annotation.
   */
  public static class FixityInfo extends AnnotatedTerm {

   /*
   * @param name    the name
   * @param locator  the declaration locator
   * @param fixity  the fixity definition
   */
    public Name origin;
    public Name name;
    public Locator locator;
    public Fixity fixity;

    public FixityInfo(Name origin,
                      Name name, Locator locator, Fixity fixity) {
      this.origin = origin;
      this.name = name;
      this.locator = locator;
      this.fixity = fixity;
    }

    /** Pizza's generated case factory. */
    public static FixityInfo FixityInfo(Name origin,
                                        Name name, Locator locator,
                                        Fixity fixity) {
      return new FixityInfo(origin, name, locator, fixity);
    }

    public Format toFormat(FormatInfo info) {
      return Format.block(
               Format.buildFormat(origin + ". "),
               Format.buildFormat(fixity),
               Format.append(Format.literal("; "),
                             Format.buildFormat(locator)).setIndent(4)
             );
    }

    /** For effiency. */
    private void writeObject(ObjectOutputStream s)
    throws IOException {
      s.writeObject(origin);
      s.writeObject(name);
      s.writeObject(locator);
      s.writeObject(fixity);
    }
    private void readObject(ObjectInputStream s)
    throws IOException, ClassNotFoundException {
      origin = (Name)s.readObject();
      name = (Name)s.readObject();
      locator = (Locator)s.readObject();
      fixity = (Fixity)s.readObject();
    }
  }

  /**
   * Information record for a constant declaration. May carry an annotation.
   * In particular, for mSZ schemas with role a <code>DeclAttr</code> is
   * attached.
   *
   * @see DeclAttr
   */
  public static class ConstantInfo extends AnnotatedTerm {

   /*
   * @param name      the name
   * @param locator    the declaration locator
   * @param generics  the genericity of the constant
   * @param type      the type of the constant
   */
    public Name origin;
    public Name name;
    public Locator locator;
    public Name[] generics;
    public Expr type;

    public ConstantInfo(Name origin, Name name,
                        Locator locator, Name[] generics, Expr type) {
      this.origin = origin;
      this.name = name;
      this.locator = locator;
      this.generics = generics;
      this.type = type;
    }

    /** Pizza's generated case factory. */
    public static ConstantInfo ConstantInfo(Name origin, Name name,
                                            Locator locator, Name[] generics,
                                            Expr type) {
      return new ConstantInfo(origin, name, locator, generics, type);
    }

    public Format toFormat(FormatInfo info) {
      return Format.block(
               Format.append(
                 Format.literal(origin + "." + name),
                 generics.length > 0 ?
                   Format.list(Format.literal("["), Format.literal(","),
                               Format.literal("]: "), generics) :
                   Format.literal(": ")
               ),
               Type.toPrettyFormat(type).setIndent(2),
               Format.append(Format.literal("; "),
                             Format.buildFormat(locator)).setIndent(4)
             );
    }

    /** For effiency. */
    private void writeObject(java.io.ObjectOutputStream s)
    throws IOException {
      s.writeObject(origin);
      s.writeObject(name);
      s.writeObject(locator);
      s.writeObject(generics);
      s.writeObject(type);
    }
    private void readObject(java.io.ObjectInputStream s)
    throws IOException, ClassNotFoundException {
      origin = (Name)s.readObject();
      name = (Name)s.readObject();
      locator = (Locator)s.readObject();
      generics = (Name[])s.readObject();
      type = (Expr)s.readObject();
    }
  }


  // PRIVATE DATA

  /**
   * Name of the unit this environment belongs to.
   */
  private Name unitName;

  /**
   * Locator of this unit environment.
   */
  private Locator unitLocator;

  /**
   * Whether this environment is for a mSZ class.
   */
  private boolean isClass = false;

  /**
   * The formal parameters of an mSZ class.
   */
  private Name[] formals = new Name[0];

  /**
   * The ports schemas of an mSZ class.
   */
  private Name[] portNames = new Name[0];

  /**
   * The data schemas of an mSZ class.
   */
  private Name[] dataNames = new Name[0];

  /**
   * The property schemas of an mSZ class.
   */
  private Name[] propertyNames = new Name[0];

  /**
   * The init schemas of an mSZ class.
   */
  private Name[] initNames = new Name[0];

  /**
   * The states of an mSZ class.
   */
  private Name[] stateNames = new Name[0];


  /**
   * Names of parents from which syntax has been imported.
   */
  private Set<Name> syntaxParentNames = new HashSet<Name>();

  /**
   * Names of parents from which constants have been imported.
   */
  private Set<Name> contextParentNames = new HashSet<Name>();


  /**
   * The map of declared constants.
   */
  private Map<Name,ConstantInfo> constants = new HashMap<Name,ConstantInfo>();


  /**
   * Fixities known in this environment.
   */
  private Map<Name, FixityInfo> fixities = new HashMap<Name,FixityInfo>();

  /**
   * Set of names which are keywords of expression fixities.
   */
  private Set<Name> keywords = new HashSet<Name>();

  /**
   * Set of names which are keywords of relation fixities.
   */
  private Set<Name> pkeywords = new HashSet<Name>();

  /**
   * Set of names which are left-open keywords of fixities.
   */
  private Set<Name> lkeywords = new HashSet<Name>();

  /**
   * Set of names which are right-close keywords of fixities.
   */
  private Set<Name> rkeywords = new HashSet<Name>();

  /**
   * Set of names of fixities which put Linsep at the left offside.
   */
  private Set<Name> loffkeywords = new HashSet<Name>();

  /**
   * Set of names of fixities which put Linsep at the right offside.
   */
  private Set<Name> roffkeywords = new HashSet<Name>();


  /**
   * Set of names which are special keywords of fixities which
   * are individually handled by the parser.
   */
  private Set<Name> specialKeywords =
    new HashSet<Name>()
    .include(new Name(Lexem.Lgroup.toString()))
    .include(new Name(Lexem.Rgroup.toString()))
    .include(new Name(Lexem.Power.toString()))
    .include(new Name(Lexem.Cross.toString()));


  /**
   * Mapping of left-open keywords to corresponding right-close keywords.
   */
  private Map<Name,Name> closingKeywords = new HashMap<Name,Name>();

  /**
   * Macros known in this environment.
   */
  private Map<Name, MacroInfo> macros = new HashMap<Name,MacroInfo>();

  /**
   * Accumulated diagnostics.
   */
  private Diag diag = new Diag();

  /**
   * Pragmas.
   */
  private Seq<PragmaInfo> pragmas = new ArraySeq<PragmaInfo>();



  // CONSTRUCTION

  /**
   * Construct a new empty environment for the named unit.
   */
  private UnitEnv(Name unitName, Locator locator) {
    this.unitName = unitName;
    this.unitLocator = locator;
  }

  /**
   * Construct a new empty environment for the named unit representing
   * a Z section.
   */
  public static UnitEnv newSection(Name unitName, Locator locator) {
    return new UnitEnv(unitName, locator);
  }

  /**
   * Construct a new empty environment for the named unit representing
   * a mSZ class.
   */
  public static UnitEnv newClass(Name unitName, Locator locator,
                                 Name[] formals) {
    UnitEnv env = new UnitEnv(unitName, locator);
    env.isClass = true;
    env.formals = formals;
    return env;
  }


  /**
   * Make a shallow copy of the given environment, including any
   * annotations of information records.
   */
  public UnitEnv copy() {
    UnitEnv e = new UnitEnv(unitName, unitLocator);
    e.isClass = isClass;
    e.formals = formals;
    e.portNames = portNames;
    e.dataNames = dataNames;
    e.propertyNames = propertyNames;
    e.initNames = initNames;
    e.stateNames = stateNames;
    e.importSyntaxFrom(this);
    e.importConstantsFrom(this);
    return e;
  }


  /**
   * Import the syntactic information of the given environment into this
   * environment. This method includes all
   * macros and fixities of the given environment in this
   * environment.
   */
  public void importSyntaxFrom(UnitEnv parent) {
    if (!syntaxParentNames.contains(parent.unitName)) {
      syntaxParentNames =
        syntaxParentNames.includeAll(parent.syntaxParentNames)
                         .include(parent.unitName);

      parent.macros.apply(
        (Name key, MacroInfo p) -> {
          declareMacro(p.locator, p.name, p.argc, p.subs);
          MacroInfo info = macros.get(p.name);
          p.an.putInto(info.an);
          info.origin = p.origin;
        }
      );
      parent.fixities.apply(
        (Name key, FixityInfo p) -> {
          declareFixity(p.locator, p.fixity);
          FixityInfo info = fixities.get(p.name);
          p.an.putInto(info.an);
          info.origin = p.origin;
        }
      );
      parent.pragmas.apply(
        (PragmaInfo p) -> {
          PragmaInfo info = addPragma(p.locator, p.name, p.value);
          p.an.putInto(info.an);
          info.origin = p.origin;
        }
      );
    }
  }

  /**
   * Import the context information of the given environment into this
   * environment. This includes all constants of the given environment
   * in this environment.
   */
  public void importConstantsFrom(UnitEnv parent) {
    if (!contextParentNames.contains(parent.unitName)) {
      contextParentNames =
        contextParentNames.includeAll(parent.contextParentNames)
                          .include(parent.unitName);

      parent.constants.apply(
        (Name key, ConstantInfo p) -> {
          declareConstant(p.locator, p.name,
                          p.generics, p.type);
          ConstantInfo info = constants.get(p.name);
          p.an.putInto(info.an);
          info.origin = p.origin;
        }
      );
    }
  }

  /*
   * Import the actualized context information of the given
   * environment into this environment. The given environment must
   * represent a a class. This method behaves identically to declaring
   * all constants of the given environment after actualization of
   * their types in this environment, and adding all annotations of
   * information records of the given environment to the records of
   * this environment.
  public void importActualizedConstantsFrom(UnitEnv classParent,
                                            Name[] formals,
                                            Expr[] actuals){
    throw FatalError.notImplemented();
  }
  */

  /**
   * Forget the import origin of the named parent units. All objects
   * with origin of the named units will receive the origin of the
   * unit of this environment, as if they have been declared in
   * it. The named units will be removed from the parent list.  */
  public void forgetOrigins(Name[] origins) {
    Set<Name> names = HashSet.from(new ArraySeq<Name>(origins));
    macros.apply(
      (Name key, MacroInfo p) -> {
        if (names.contains(p.origin))
          p.origin = unitName;
      }
    );
    fixities.apply(
      (Name key, FixityInfo p) -> {
        if (names.contains(p.origin))
          p.origin = unitName;
      }
    );
    constants.apply(
      (Name key, ConstantInfo p) -> {
        if (names.contains(p.origin))
          p.origin = unitName;
      }
    );
    syntaxParentNames = syntaxParentNames.excludeAll(names);
    contextParentNames = contextParentNames.excludeAll(names);
  }


  /**
   * Enter a macro declaration. Warnings are produced if
   * the macro overrides an older definition.
   */
  public void declareMacro(Locator locator, Name name,
                           int argc, Lexem[] subs) {
    if (macros.defines(name)) {
      MacroInfo old = macros.get(name);
      if (old.locator.equals(locator)) {
        // redeclaration from same locator, do nothing
        return;
      }
      else if (old.argc != argc || !equalsSubs(old.subs, subs)) {
        diag = diag.add(locator,
                        Diag.Category.Warning,
                        Format.describe(
                          "overwriting macro definition for `" + name + "'",
                          "previous declaration",
                          Format.buildFormat(macros.get(name).locator)
                        )
                       );
      }
    }
    macros = macros.define(name,
                           new MacroInfo(unitName, name, locator, argc, subs));
  }

  // PORT NOTE -- 1998 defect, preserved: when the two arrays have different lengths the
  // loop is skipped and the method falls through to `return true`, i.e. reports substitution
  // lists of different length as EQUAL.  The only caller is declareMacro, which tests
  // `old.argc != argc || !equalsSubs(...)`, so the lost case is a macro redeclared from a
  // different locator with the same argument count but a different-length body -- for which
  // no "overwriting macro definition" warning is issued.  That warning is baselined by the
  // negative suites, so the defect is left in place rather than repaired.
  private static boolean equalsSubs(Lexem[] s1, Lexem[] s2) {
    if (s1.length == s2.length)
      for (int i = 0; i < s1.length; i++)
        if (!s1[i].equals(s2[i])) return false;
    return true;
  }


  /**
   * Enter a fixity declaration. Warnings will be produced if
   * the fixities redefines keyword assignments.
   */
  public void declareFixity(Locator locator, Fixity fixity) {
    Name name = fixity.makeName();
    // CHECKME: more sanity checks?
    if (fixities.defines(name)) {
      FixityInfo old = fixities.get(name);
      if (old.locator.equals(locator)) {
        // redeclaration from same locator, do nothing
        return;
      }
      if (!old.fixity.equals(fixity)) {
        diag = diag.add(locator,
                        Diag.Category.Warning,
                        Format.describe(
                          "overwriting fixity definition for `" + name + "'",
                          "previous declaration",
                          Format.buildFormat(fixities.get(name).locator)
                        )
                       );
      }
    }

    fixities = fixities.define(name,
                               new FixityInfo(unitName, name,
                                              locator, fixity));
    // add keywords
    for (int j = 0; j < fixity.components.length; j++) {
      switch (fixity.components[j]) {
      case Fixity.Component.Keyword kw -> {
        Name kname = kw.name;
        if (specialKeywords.contains(kname)) continue;
        if (j > 0)
          loffkeywords = loffkeywords.include(kname);
        if (j < fixity.components.length-1)
          roffkeywords = roffkeywords.include(kname);
        if (lkeywords.contains(kname)) {
          diag =
            diag.add(locator,
                     Diag.Category.Warning,
                     "overwriting `" + kname +
                     "' (previously list-open, now expression)");
          lkeywords = lkeywords.exclude(kname);
        }
        // PORT NOTE -- 1998 defect, preserved: this branch is entered because `kname` is a
        // list-CLOSE keyword, and the message says so, but it then excludes `kname` from
        // `lkeywords` (list-open) instead of from `rkeywords`.  So the keyword stays in
        // rkeywords and the later `keywords.include(kname)` leaves it in both sets, which
        // getLexemInfo resolves in favour of Keyword/Pkeyword since it tests those first.
        // The warning itself is unaffected.  Repairing it would change the rewritten lexem
        // of such a keyword and hence the token stream; left alone.
        if (rkeywords.contains(kname)) {
          diag =
            diag.add(locator,
                     Diag.Category.Warning,
                     "overwriting `" + kname +
                     "' (previously list-close, now expression)");
          lkeywords = lkeywords.exclude(kname);
        }
        if (fixity.prio < 0) {
          if (keywords.contains(kname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + kname +
                       "' (previously expression, now relation)");
            keywords = keywords.exclude(kname);
          }
          pkeywords = pkeywords.include(kname);
        } else {
          if (pkeywords.contains(kname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + kname +
                       "' (previously relation, now expression)");
            pkeywords = pkeywords.exclude(kname);
          }
          keywords = keywords.include(kname);
        }
      }
      case Fixity.Component.OperandList ol -> {
        Name lname = ol.lname;
        Name rname = ol.rname;
        if (specialKeywords.contains(lname)) continue;
        if (specialKeywords.contains(rname)) continue;
        roffkeywords = roffkeywords.include(lname);
        loffkeywords = loffkeywords.include(rname);
        if (j > 0)
          loffkeywords = loffkeywords.include(lname);
        if (j < fixity.components.length-1)
          roffkeywords = roffkeywords.include(rname);
        if (!specialKeywords.contains(lname)) {
          if (keywords.contains(lname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + lname +
                       "' (previously expression, now list-open)");
            keywords = keywords.exclude(lname);
          }
          if (pkeywords.contains(lname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + lname +
                       "' (previously relation, now list-open)");
            pkeywords = pkeywords.exclude(lname);
          }
          if (rkeywords.contains(lname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + lname +
                       "' (previously list-close, now list-open)");
            rkeywords = rkeywords.exclude(lname);
          }
          lkeywords = lkeywords.include(lname);
        }
        if (!specialKeywords.contains(rname)) {
          if (keywords.contains(rname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + rname +
                       "' (previously expression, now list-close)");
            keywords = keywords.exclude(rname);
          }
          if (pkeywords.contains(rname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + rname +
                       "' (previously relation, now list-close)");
            pkeywords = pkeywords.exclude(rname);
          }
          // PORT NOTE -- 1998 defect, preserved: the test is on `rname` but the exclusion is
          // applied to `lname`.  So a keyword that was list-open and is now also list-close
          // keeps its lkeywords membership (and `lname`, which the branch above has just
          // included, loses it).  Confirmed against the 2002 class file.  Left alone: it
          // decides which of Lkeyword/Rkeyword getLexemInfo produces.
          if (lkeywords.contains(rname)) {
            diag =
              diag.add(locator,
                       Diag.Category.Warning,
                       "overwriting `" + rname +
                       "' (previously list-open, now list-close)");
            lkeywords = lkeywords.exclude(lname);
          }
          rkeywords = rkeywords.include(rname);
        }

        if (closingKeywords.defines(lname) &&
            !closingKeywords.get(lname).equals(rname)) {
          diag =
            diag.add(locator,
                     Diag.Category.Warning,
                     "overwriting closing keyword for `" + lname +
                     "' (previously `" + closingKeywords.get(lname) +
                     "', now `" + rname + "')");
        }
        closingKeywords = closingKeywords.define(lname, rname);
      }
      // Pizza's switch had no arm for Fixity.Component.Operand and no default; an operand
      // contributes no keyword.  A pattern switch statement must be exhaustive in Java, so
      // the fall-through is spelled out.
      default -> { }
      }
    }
  }

  /**
   * Enter a constant declaration. Errors will be produced
   * if the name overwrites an older declaration with an incompatible
   * type or genericity.
   *
   * @see Type
   */
  public void declareConstant(Locator locator, Name name,
                              Name[] generics, Expr type) {
    if (!constants.defines(name)) {
      constants = constants.define(name,
                                   new ConstantInfo(unitName,
                                                    name,
                                                    locator,
                                                    generics,
                                                    type));
    } else {
      // check compatibility
      ConstantInfo info = constants.get(name);
      if (info.locator.equals(locator)) {
        // redeclaration from same locator, do nothing
        return;
      }
      else if (generics.length != info.generics.length) {
        diag =
          diag.add(
            locator,
            Diag.Category.Error,
            Format.describe(
              "incompatible genericity of redeclared `" + name + "'",
              "previous declaration", Format.buildFormat(info.locator),
              "previous genericity",
              Format.literal(info.generics.length > 0 ?
                             info.generics.length + " parameters" : "none"),
              "new genericity",
              Format.literal(generics.length > 0 ?
                             generics.length + " parameters" : "none")
            )
          );
      }
      else {
        // perform alpha conversion of type
        type =
          Type.substitute(type, generics,
                          new ArraySeq<Name>(info.generics)
                                      .map((Function<Name,Expr>) Type::makeGiven)
                                      .toArray(Expr.class));
        try {
          Type.unify(info.type, type);
          // okay, add the declaration locator
          /*
          System.out.println("combine" + name +
                             Format.buildFormat(locator) +
                             Format.buildFormat(info.locator));
          */
          info.locator = info.locator.combine(locator);
          constants = constants.define(name, info);
        }
        catch (Type.UnificationFailure e) {
          diag =
            diag.add(
              locator,
              Diag.Category.Error,
              Format.describe(
                "incompatible type of redeclared `" + name + "'",
                "previous declaration", Format.buildFormat(info.locator),
                "previous type", Type.toPrettyFormat(info.type),
                "new type", Type.toPrettyFormat(type)
              )
            );
        }
      }
    }
  }

  /**
   * Add a pragma to the environment.
   */
  public PragmaInfo addPragma(Locator locator, Name name, Lexem[] value) {
    PragmaInfo info = new PragmaInfo(unitName, name, locator, value);
    pragmas = pragmas.append(info);
    return info;
  }



  /**
   * Add diagnostics to the environment.
   */
  public void addDiag(Diag d) {
    diag = diag.combine(d);
  }

  /**
   * Get and clear accumulated diagnostics of environment.
   */
  public Diag getAndClearDiag() {
    Diag d = diag;
    diag = new Diag();
    return d;
  }




  // RETRIEVAL

  /**
   * Get the name of the unit this environment is defined for.
   */
  public Name getName() {
    return unitName;
  }

  /**
   * Get the locator of this unit environment.
   */
  public Locator getLocator() {
    return unitLocator;
  }


  /**
   * Get the names of the (transitive) parent units this environment
   * does import.
   */
  public Name[] getParents() {
    return syntaxParentNames.includeAll(contextParentNames).toArray(Name.class);
  }


  /**
   * If the given name represents a macro, return information about it,
   * otherwise null.
   */
  public MacroInfo getMacro(Name name) {
    try {
      return macros.get(name);
    }
    catch (NoSuchElementException e) {
      return null;
    }
  }

  /**
   * If the given name has a a fixity declaration, return information
   * about it, otherwise null.  */
  public FixityInfo getFixity(Name name) {
    try {
      return fixities.get(name);
    }
    catch (NoSuchElementException e) {
      return null;
    }
  }

  /**
   * If the given name has a constant declaration, return information about it,
   * otherwise null.
   */
  public ConstantInfo getConstant(Name name) {
    try {
      return constants.get(name);
    }
    catch (NoSuchElementException e) {
      return null;
    }
  }

  /**
   * Return array of all macros declared in this environment.
   */
  public MacroInfo[] getMacros() {
    return macros.range().toArray(MacroInfo.class);
  }

  /**
   * Return array of all fixities declared in this environment.
   */
  public FixityInfo[] getFixities() {
    return fixities.range().toArray(FixityInfo.class);
  }

  /**
   * Return array of all constants declared in this environment.
   */
  public ConstantInfo[] getConstants() {
    return constants.range().toArray(ConstantInfo.class);
  }

  /**
   * Return array of all pragmas in this environment.
   */
  public PragmaInfo[] getPragmas() {
    return pragmas.toArray(PragmaInfo.class);
  }


  // LEXICAL INFORMATION FOR FIXITIES

  /**
   * Information about a lexem.
   * @param rewritten    the rewritten value
   * @param leftOffside  whether this lexem puts line-separators at the left
                         offside
   * @param rightOffside  whether this lexem puts line-separators at the right
                         offside
   */
  public static class LexemInfo {
    public Lexem rewritten;
    public boolean leftOffside;
    public boolean rightOffside;

    public LexemInfo(Lexem rewritten,
                     boolean leftOffside, boolean rightOffside) {
      this.rewritten = rewritten;
      this.leftOffside = leftOffside;
      this.rightOffside = rightOffside;
    }

    /** Pizza's generated case factory. */
    public static LexemInfo LexemInfo(Lexem rewritten,
                                      boolean leftOffside,
                                      boolean rightOffside) {
      return new LexemInfo(rewritten, leftOffside, rightOffside);
    }
  }

  /**
   * Give information about a lexem. Returns null if no special
   * information induced by fixities is present.
   */
  public LexemInfo getLexemInfo(Lexem lexem) {
    switch (lexem) {
    case Lexem.Word word: {
      Name name = new Name(word.image);
      Lexem rewritten;
      if (keywords.contains(name))
        rewritten = new Lexem.Keyword(name);
      else if (pkeywords.contains(name))
        rewritten = new Lexem.Pkeyword(name);
      else if (lkeywords.contains(name))
        rewritten = new Lexem.Lkeyword(name);
      else if (rkeywords.contains(name))
        rewritten = new Lexem.Rkeyword(name);
      else
        rewritten = new Lexem.SimpleName(name);
      return new LexemInfo(rewritten,
                           loffkeywords.contains(name),
                           roffkeywords.contains(name));
    }
    default:
      return null;
    }
  }

  /** Return the list-closing keyword for given list-opening keyword. */
  public Name getClosingKeyword(Name name) {
    try {
      return closingKeywords.get(name);
    }
    catch (NoSuchElementException e) {
      throw FatalError.assertionFailed("expected a list-opening keyword");
    }
  }


  // SPECIAL INFORMATION FOR mSZ CLASSES

  /**
   * Return true if this environment represents an mSZ class.
   */
  public boolean isClass() {
    return isClass;
  }

  /**
   * Get the formal parameters of this (class) environment.
   */
  public Name[] getFormals() {
    return formals;
  }

  /**
   * Set the formal parameters of this (class) environment.
   */
  public void setFormals(Name[] formals) {
    this.formals = formals;
  }

  /**
   * Get the PORT schemas of this (class) environment.
   */
  public Name[] getPortSchemas() {
    return portNames;
  }

  /**
   * Set the PORT schemas of this (class) environment.
   */
  public void setPortSchemas(Name[] names) {
    this.portNames = names;
  }

  /**
   * Get the DATA schemas of this (class) environment.
   */
  public Name[] getDataSchemas() {
    return dataNames;
  }

  /**
   * Set the DATA schemas of this (class) environment.
   */
  public void setDataSchemas(Name[] names) {
    this.dataNames = names;
  }

  /**
   * Get the PROPERTY schemas of this (class) environment.
   */
  public Name[] getPropertySchemas() {
    return propertyNames;
  }

  /**
   * Set the PROPERTY schemas of this (class) environment.
   */
  public void setPropertySchemas(Name[] names) {
    this.propertyNames = names;
  }

  /**
   * Get the INIT schemas of this (class) environment.
   */
  public Name[] getInitSchemas() {
    return initNames;
  }

  /**
   * Set the INIT schemas of this (class) environment.
   */
  public void setInitSchemas(Name[] names) {
    this.initNames = names;
  }

  /**
   * Get the state names of this (class) environment.
   */
  public Name[] getStateNames() {
    return stateNames;
  }

  /**
   * Set the state names of this (class) environment.
   */
  public void setStateNames(Name[] names) {
    this.stateNames = names;
  }


  // FORMATTING

  /** */
  public Format toFormat(FormatInfo info) {
    return Format.describe(
             "UnitEnv",
             new String[]{ "name",
                           "locator",
                           "isClass",
                           "formals",
                           "syntactic parents",
                           "context parents",
                           "pragmas",
                           "macros",
                           "fixities",
                           "expr-keywords",
                           "pred-keywords",
                           "open-keywords",
                           "close-keywords",
                           "leftoffside-keywords",
                           "rightoffside-keywords",
                           "constants",
                           "ports",
                           "datas",
                           "properties",
                           "inits",
                           "states"
                         },
             new Format[]{ Format.buildFormat(unitName),
                           Format.buildFormat(unitLocator),
                           Format.literal("" + isClass),
                           Format.buildFormat(formals),
                           Format.buildFormat(syntaxParentNames),
                           Format.buildFormat(contextParentNames),
                           Format.beneath(pragmas.map(Format::build)),
                           Format.beneath(macros.range().map(Format::build)),
                           Format.beneath(fixities.range().map(Format::build)),
                           Format.buildFormat(keywords),
                           Format.buildFormat(pkeywords),
                           Format.buildFormat(lkeywords),
                           Format.buildFormat(rkeywords),
                           Format.buildFormat(loffkeywords),
                           Format.buildFormat(roffkeywords),
                           Format.beneath(constants.range().map(Format::build)),
                           Format.buildFormat(portNames),
                           Format.buildFormat(dataNames),
                           Format.buildFormat(propertyNames),
                           Format.buildFormat(initNames),
                           Format.buildFormat(stateNames)
                         }
           );
  }


  // SERIALIZATION

  private void writeObject(ObjectOutputStream s)
  throws IOException {
    s.writeObject(unitName);
    s.writeObject(unitLocator);
    s.writeBoolean(isClass);
    s.writeObject(formals);
    s.writeObject(portNames);
    s.writeObject(dataNames);
    s.writeObject(propertyNames);
    s.writeObject(initNames);
    s.writeObject(stateNames);
    s.writeObject(contextParentNames.toArray(Name.class));
    s.writeObject(syntaxParentNames.toArray(Name.class));
    s.writeObject(macros.range().toArray(MacroInfo.class));
    s.writeObject(fixities.range().toArray(FixityInfo.class));
    s.writeObject(constants.range().toArray(ConstantInfo.class));
    s.writeObject(keywords.toArray(Name.class));
    s.writeObject(pkeywords.toArray(Name.class));
    s.writeObject(lkeywords.toArray(Name.class));
    s.writeObject(rkeywords.toArray(Name.class));
    s.writeObject(loffkeywords.toArray(Name.class));
    s.writeObject(roffkeywords.toArray(Name.class));
    Seq<Pair<Name, Name>> sq = ArraySeq.from(closingKeywords);
    Name[] a1 = sq.map((Function<Pair<Name, Name>, Name>) p -> p.fst)
                  .toArray(Name.class);
    Name[] a2 = sq.map((Function<Pair<Name, Name>, Name>) p -> p.snd)
                  .toArray(Name.class);
    s.writeObject(a1);
    s.writeObject(a2);
    s.writeObject(pragmas.toArray(PragmaInfo.class));
  }

  private void readObject(ObjectInputStream s)
  throws IOException, ClassNotFoundException {
    unitName = (Name)s.readObject();
    unitLocator = (Locator)s.readObject();
    isClass = s.readBoolean();
    formals = (Name[])s.readObject();
    portNames = (Name[])s.readObject();
    dataNames = (Name[])s.readObject();
    propertyNames = (Name[])s.readObject();
    initNames = (Name[])s.readObject();
    stateNames = (Name[])s.readObject();
    contextParentNames = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    syntaxParentNames = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    {
      macros = new HashMap<Name,MacroInfo>();
      MacroInfo[] infos = (MacroInfo[])s.readObject();
      for (int i = 0; i < infos.length; i++) {
        macros = macros.define(infos[i].name, infos[i]);
      }
    }
    {
      fixities = new HashMap<Name,FixityInfo>();
      FixityInfo[] infos = (FixityInfo[])s.readObject();
      for (int i = 0; i < infos.length; i++) {
        fixities = fixities.define(infos[i].name, infos[i]);
      }
    }
    {
      constants = new HashMap<Name,ConstantInfo>();
      ConstantInfo[] infos = (ConstantInfo[])s.readObject();
      for (int i = 0; i < infos.length; i++) {
        constants = constants.define(infos[i].name, infos[i]);
      }
    }
    keywords = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    pkeywords = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    lkeywords = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    rkeywords = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    loffkeywords = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));
    roffkeywords = HashSet.from(new ArraySeq<Name>((Name[])s.readObject()));

    Name[] a1 = (Name[])s.readObject();
    Name[] a2 = (Name[])s.readObject();
    closingKeywords = new HashMap<Name,Name>().defineAll(new ArraySeq<Name>(a1),
                                                         new ArraySeq<Name>(a2));

    pragmas = new ArraySeq<PragmaInfo>((PragmaInfo[])s.readObject());
    diag = new Diag();
  }

}
