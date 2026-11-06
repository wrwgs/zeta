package zeta.content.zirp;

import java.io.ObjectStreamException;

import zeta.util.AnnotatedTerm;
import zeta.util.CaseConstants;
import zeta.util.Name;
import zeta.util.Term;

/** The abstract syntax of Z expressions.
  *
  * Expressions and schema expressions are unified in this syntax.
  *
  * Schema expressions are generalized to allow the introduction
  * of given and free type declarations.
  *
  * @version $Id: Expr.pizza,v 1.2 2000/07/06 09:18:24 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * Pure data-structure file: no method bodies in the Pizza source, so there are no switches
  * to translate and no closures to convert.
  *
  * {@code QuantorKind} and {@code BinaryKind} have only nullary cases (no parentheses in the
  * Pizza source), so they compile to {@code public static final} constant fields on a concrete
  * base.
  *
  * {@code UnaryKind} mixes shapes: {@code Power}, {@code Theta}, {@code Not}, {@code Delta},
  * {@code Xi}, {@code Pre}, {@code StateFlat}, and {@code StepFlat} are nullary constants,
  * while {@code SelectiveDelta}, {@code Hide}, {@code Renaming}, and {@code Decorate} carry
  * data and compile to nested classes with factories.
  *
  * {@code Rename} is a single-case class whose case name equals the class name: no
  * {@code $$tag}, just fields, a public constructor, and a static factory.
  *
  * {@code Decl} and {@code Branch} extend {@link AnnotatedTerm} (not {@link Term}), as in
  * the reference.
  *
  * {@code Binding} takes {@code Decl.Eqn[]} -- the specific Eqn case class -- matching the
  * erased descriptor in the reference ({@code Expr$Decl$Eqn[]}).
  *
  * Tag values were read from the 2002 class files, not derived from declaration order.
  * Public-field declaration order in the constant-only inner ADTs follows the reference:
  * constants first, {@code $$tag} last.
  */
public class Expr extends AnnotatedTerm {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$zirp$Expr$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_NUMBER = 1;
  private static final int TAG_DENOTATION = 2;
  private static final int TAG_VARIABLE = 3;
  private static final int TAG_TUPLE = 4;
  private static final int TAG_PRODUCT = 5;
  private static final int TAG_BINDING = 6;
  private static final int TAG_DISPLAY = 7;
  private static final int TAG_COND = 8;
  private static final int TAG_QUANTOR = 9;
  private static final int TAG_SELECT = 10;
  private static final int TAG_UNARY = 11;
  private static final int TAG_BINARY = 12;
  private static final int TAG_TEXT = 13;
  private static final int TAG_GIVENTYPE = 14;
  private static final int TAG_FREETYPE = 15;
  private static final int TAG_UNIVAR = 16;
  private static final int TAG_SIGNATURE = 17;

  Expr(int tag) {
    this.zeta$content$zirp$Expr$$tag = tag;
  }

  /** Number literal.
    * @param value the string representing the number literal. */
  public static class Number extends Expr {
    public String value;
    public Number(String value) {
      super(TAG_NUMBER);
      this.value = value;
    }
  }

  /** Denotation literal.
    * @param value the string representing the denotation literal. */
  public static class Denotation extends Expr {
    public String value;
    public Denotation(String value) {
      super(TAG_DENOTATION);
      this.value = value;
    }
  }

  /** Reference to a variable.
    * @param name  a name application, containing actualizations
    * of generics.
    */
  public static class Variable extends Expr {
    public NameAppl name;
    public Variable(NameAppl name) {
      super(TAG_VARIABLE);
      this.name = name;
    }
  }

  /** Tuple expression, with elements of heterogeneous type.
    * @param elements  the element expressions of the tuple, an
    *  array with length greater then <code>1</code>. */
  public static class Tuple extends Expr {
    public Expr[] elements;
    public Tuple(Expr[] elements) {
      super(TAG_TUPLE);
      this.elements = elements;
    }
  }

  /** Cartesian product expression.
    * @param elements the element expressions of the product.
    */
  public static class Product extends Expr {
    public Expr[] elements;
    public Product(Expr[] elements) {
      super(TAG_PRODUCT);
      this.elements = elements;
    }
  }

  /** Binding expression.
    * @param bindings  a sequence of equations describing
    *  the binding.
    */
  public static class Binding extends Expr {
    public Decl.Eqn[] bindings;
    public Binding(Decl.Eqn[] bindings) {
      super(TAG_BINDING);
      this.bindings = bindings;
    }
  }

  /** Set display expression, with elements of homogeneous type.
    * @param elements  the elements, all of the same type.
    */
  public static class Display extends Expr {
    public Expr[] elements;
    public Display(Expr[] elements) {
      super(TAG_DISPLAY);
      this.elements = elements;
    }
  }

  /** Conditional expression.
    * @param cond  the condition, a predicate.
    * @param thenPath the path to be taken if <code>cond</code> is true.
    * @param elsePath the path to be taken if <code>cond</code> is false.
   */
  public static class Cond extends Expr {
    public Predicate cond;
    public Expr thenPath;
    public Expr elsePath;
    public Cond(Predicate cond, Expr thenPath, Expr elsePath) {
      super(TAG_COND);
      this.cond = cond;
      this.thenPath = thenPath;
      this.elsePath = elsePath;
    }
  }

  /** Quantor expression.
    * @param kind  the quantor kind, such as lambda, mu, set-comprehension,
    *   schema quantification and so on.
    * @param matrix  an expression denoting a schema.
    * @param range  expression the <code>matrix</code> ranges over.
    */
  public static class Quantor extends Expr {
    public QuantorKind kind;
    public Expr matrix;
    public Expr range;
    public Quantor(QuantorKind kind, Expr matrix, Expr range) {
      super(TAG_QUANTOR);
      this.kind = kind;
      this.matrix = matrix;
      this.range = range;
    }
  }

  /** Binding kind. */
  public static class QuantorKind extends Term {
    /** Lambda expressions. */
    public static final QuantorKind Lambda;
    /** Mu description. */
    public static final QuantorKind Mu;
    /** Let expressions. */
    public static final QuantorKind Let;
    /** Set comprehension. */
    public static final QuantorKind Set;
    /** Schema universal quantification. */
    public static final QuantorKind Forall;
    /** Schema existential quantification. */
    public static final QuantorKind Exists;
    /** Schema unique existential quantification. */
    public static final QuantorKind Exists1;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Expr$QuantorKind$$tag;

    private static final int TAG_LAMBDA = 1;
    private static final int TAG_MU = 2;
    private static final int TAG_LET = 3;
    private static final int TAG_SET = 4;
    private static final int TAG_FORALL = 5;
    private static final int TAG_EXISTS = 6;
    private static final int TAG_EXISTS1 = 7;

    QuantorKind(int tag) {
      this.zeta$content$zirp$Expr$QuantorKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, QuantorKind.class);
    }

    static {
      Lambda = new QuantorKind(TAG_LAMBDA);
      Mu = new QuantorKind(TAG_MU);
      Let = new QuantorKind(TAG_LET);
      Set = new QuantorKind(TAG_SET);
      Forall = new QuantorKind(TAG_FORALL);
      Exists = new QuantorKind(TAG_EXISTS);
      Exists1 = new QuantorKind(TAG_EXISTS1);
    }
  }

  /** Binding selection.
    * @param oper  an expressions denoting a binding.
    * @param name  a named component of this binding.
    */
  public static class Select extends Expr {
    public Expr oper;
    public NameAppl name;
    public Select(Expr oper, NameAppl name) {
      super(TAG_SELECT);
      this.oper = oper;
      this.name = name;
    }
  }

  /** Unary operator.
    * @param kind  the operation kind; mostly schema calculus operators.
    * @param oper  the operand.
    */
  public static class Unary extends Expr {
    public UnaryKind kind;
    public Expr oper;
    public Unary(UnaryKind kind, Expr oper) {
      super(TAG_UNARY);
      this.kind = kind;
      this.oper = oper;
    }
  }

  /** Kind of Unaries. */
  public static class UnaryKind extends Term {
    /** Power set construction. */
    public static final UnaryKind Power;
    /** Binding formation. */
    public static final UnaryKind Theta;
    /** Schema negation. */
    public static final UnaryKind Not;
    /** Schema Delta operator. */
    public static final UnaryKind Delta;
    /** Schema Xi operator. */
    public static final UnaryKind Xi;
    /** Schema precondition operator. */
    public static final UnaryKind Pre;
    /** Schema state flattening (DZ only). */
    public static final UnaryKind StateFlat;
    /** Schema step flattening (DZ only). */
    public static final UnaryKind StepFlat;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Expr$UnaryKind$$tag;

    private static final int TAG_POWER = 1;
    private static final int TAG_THETA = 2;
    private static final int TAG_NOT = 3;
    private static final int TAG_DELTA = 4;
    private static final int TAG_SELECTIVEDELTA = 5;
    private static final int TAG_XI = 6;
    private static final int TAG_PRE = 7;
    private static final int TAG_HIDE = 8;
    private static final int TAG_RENAMING = 9;
    private static final int TAG_DECORATE = 10;
    private static final int TAG_STATEFLAT = 11;
    private static final int TAG_STEPFLAT = 12;

    UnaryKind(int tag) {
      this.zeta$content$zirp$Expr$UnaryKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, UnaryKind.class);
    }

    static {
      Power = new UnaryKind(TAG_POWER);
      Theta = new UnaryKind(TAG_THETA);
      Not = new UnaryKind(TAG_NOT);
      Delta = new UnaryKind(TAG_DELTA);
      Xi = new UnaryKind(TAG_XI);
      Pre = new UnaryKind(TAG_PRE);
      StateFlat = new UnaryKind(TAG_STATEFLAT);
      StepFlat = new UnaryKind(TAG_STEPFLAT);
    }

    /** Schema selective delta operator. */
    public static class SelectiveDelta extends UnaryKind {
      public NameAppl[] names;
      public SelectiveDelta(NameAppl[] names) {
        super(TAG_SELECTIVEDELTA);
        this.names = names;
      }
    }

    /** Schema hiding operator.
     * @param names  the names to hide in schema. */
    public static class Hide extends UnaryKind {
      public NameAppl[] names;
      public Hide(NameAppl[] names) {
        super(TAG_HIDE);
        this.names = names;
      }
    }

    /** Schema renaming operator.
      * @param renames  the renaming pairs applied to schema .
      */
    public static class Renaming extends UnaryKind {
      public Rename[] renames;
      public Renaming(Rename[] renames) {
        super(TAG_RENAMING);
        this.renames = renames;
      }
    }

    /** Schema decoration operator.
      * @param decore  a string representing name decoration.
      */
    public static class Decorate extends UnaryKind {
      public String decore;
      public Decorate(String decore) {
        super(TAG_DECORATE);
        this.decore = decore;
      }
    }

    /** Pizza's generated case factory. */
    public static UnaryKind SelectiveDelta(NameAppl[] names) {
      return new SelectiveDelta(names);
    }

    /** Pizza's generated case factory. */
    public static UnaryKind Hide(NameAppl[] names) {
      return new Hide(names);
    }

    /** Pizza's generated case factory. */
    public static UnaryKind Renaming(Rename[] renames) {
      return new Renaming(renames);
    }

    /** Pizza's generated case factory. */
    public static UnaryKind Decorate(String decore) {
      return new Decorate(decore);
    }
  }

  /** Renaming pair. */
  public static class Rename extends AnnotatedTerm {
    public NameAppl old;
    public NameDecl _new;

    /** Renaming.
      * @param old  the old name
      * @param _new  the new name
      */
    public Rename(NameAppl old, NameDecl _new) {
      this.old = old;
      this._new = _new;
    }

    /** Pizza's generated case factory. */
    public static Rename Rename(NameAppl old, NameDecl _new) {
      return new Rename(old, _new);
    }
  }

  /** Binary operator.
    * @param kind  the operation kind; mostly schema calculus operators.
    * @param left  the left operand.
    * @param right  the right operand.
    */
  public static class Binary extends Expr {
    public BinaryKind kind;
    public Expr left;
    public Expr right;
    public Binary(BinaryKind kind, Expr left, Expr right) {
      super(TAG_BINARY);
      this.kind = kind;
      this.left = left;
      this.right = right;
    }
  }

  /** Kind of binary operators. */
  public static class BinaryKind extends Term {
    /** Function application. */
    public static final BinaryKind Apply;
    /** Schema conjunction. */
    public static final BinaryKind And;
    /** Schema disjunction. */
    public static final BinaryKind Or;
    /** Schema implication. */
    public static final BinaryKind Implies;
    /** Schema equivalence. */
    public static final BinaryKind Iff;
    /** Schema sequential composition. */
    public static final BinaryKind Compose;
    /** Schema projection. */
    public static final BinaryKind Project;
    /** Schema piping. */
    public static final BinaryKind Pipe;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Expr$BinaryKind$$tag;

    private static final int TAG_APPLY = 1;
    private static final int TAG_AND = 2;
    private static final int TAG_OR = 3;
    private static final int TAG_IMPLIES = 4;
    private static final int TAG_IFF = 5;
    private static final int TAG_COMPOSE = 6;
    private static final int TAG_PROJECT = 7;
    private static final int TAG_PIPE = 8;

    BinaryKind(int tag) {
      this.zeta$content$zirp$Expr$BinaryKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, BinaryKind.class);
    }

    static {
      Apply = new BinaryKind(TAG_APPLY);
      And = new BinaryKind(TAG_AND);
      Or = new BinaryKind(TAG_OR);
      Implies = new BinaryKind(TAG_IMPLIES);
      Iff = new BinaryKind(TAG_IFF);
      Compose = new BinaryKind(TAG_COMPOSE);
      Project = new BinaryKind(TAG_PROJECT);
      Pipe = new BinaryKind(TAG_PIPE);
    }
  }

  /** Schema text.
    * @param decls the sequence of declarations
    * @param props the sequence of properties.
    */
  public static class Text extends Expr {
    public Decl[] decls;
    public Predicate[] props;
    public Text(Decl[] decls, Predicate[] props) {
      super(TAG_TEXT);
      this.decls = decls;
      this.props = props;
    }
  }

  /** Declarations. */
  public static class Decl extends AnnotatedTerm {

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Expr$Decl$$tag;

    private static final int TAG_DIRECT = 1;
    private static final int TAG_EQN = 2;
    private static final int TAG_INCLUSION = 3;

    Decl(int tag) {
      this.zeta$content$zirp$Expr$Decl$$tag = tag;
    }

    /** Direct declaration.
      * @param names the names being declared.
      * @param type the type of the declared names.  */
    public static class Direct extends Decl {
      public NameDecl[] names;
      public Expr type;
      public Direct(NameDecl[] names, Expr type) {
        super(TAG_DIRECT);
        this.names = names;
        this.type = type;
      }
    }

    /** Equated declaration.
      * @param name the name being declared.
      * @param type the definition of the name. */
    public static class Eqn extends Decl {
      public NameDecl name;
      public Expr def;
      public Eqn(NameDecl name, Expr def) {
        super(TAG_EQN);
        this.name = name;
        this.def = def;
      }
    }

    /** Inclusion.
      * @param schema  expression denoting a schema to be included.
      */
    public static class Inclusion extends Decl {
      public Expr schema;
      public Inclusion(Expr schema) {
        super(TAG_INCLUSION);
        this.schema = schema;
      }
    }

    /** Pizza's generated case factory. */
    public static Decl Direct(NameDecl[] names, Expr type) {
      return new Direct(names, type);
    }

    /** Pizza's generated case factory. */
    public static Decl Eqn(NameDecl name, Expr def) {
      return new Eqn(name, def);
    }

    /** Pizza's generated case factory. */
    public static Decl Inclusion(Expr schema) {
      return new Inclusion(schema);
    }
  }

  /** Given Type. Constructs a schema which contains the declaration
    * of the given type in the signature.
    * @param name the name of the given type. */
  public static class GivenType extends Expr {
    public NameDecl name;
    public GivenType(NameDecl name) {
      super(TAG_GIVENTYPE);
      this.name = name;
    }
  }

  /** Free Type. Constructs a schema which contains the declaration
    * of the free type in the signature.
    * @param name the name of the free type.
    * @param branches the branches of the free type. */
  public static class FreeType extends Expr {
    public NameDecl name;
    public Branch[] branches;
    public FreeType(NameDecl name, Branch[] branches) {
      super(TAG_FREETYPE);
      this.name = name;
      this.branches = branches;
    }
  }

  /** Free type branch. */
  public static class Branch extends AnnotatedTerm {

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Expr$Branch$$tag;

    private static final int TAG_CONSTANT = 1;
    private static final int TAG_FUNCTION = 2;

    Branch(int tag) {
      this.zeta$content$zirp$Expr$Branch$$tag = tag;
    }

    /** Constant constructor.
      * @param name the name of the constructor. */
    public static class Constant extends Branch {
      public NameDecl name;
      public Constant(NameDecl name) {
        super(TAG_CONSTANT);
        this.name = name;
      }
    }

    /** Function constructor.
      * @param name the name of the constructor.
      * @param type expression describing the domain of the constructor
      *   function. */
    public static class Function extends Branch {
      public NameDecl name;
      public Expr type;
      public Function(NameDecl name, Expr type) {
        super(TAG_FUNCTION);
        this.name = name;
        this.type = type;
      }
    }

    /** Pizza's generated case factory. */
    public static Branch Constant(NameDecl name) {
      return new Constant(name);
    }

    /** Pizza's generated case factory. */
    public static Branch Function(NameDecl name, Expr type) {
      return new Function(name, type);
    }
  }

  /** A unification variable. The operand may be null to indicate
      that the variable is not bound. Does not appear usually in
      interchanged ZIRP. */
  public static class UniVar extends Expr {
    public Name name;
    public Expr oper;
    public UniVar(Name name, Expr oper) {
      super(TAG_UNIVAR);
      this.name = name;
      this.oper = oper;
    }
  }

  /** Signature expression. Does only appears in type expressions.
    * Describes the type of a binding, that is a set of bindings.
    * @param names  array of names in the signature
    * @param types  array of types assigned to names at the corresponding
        index in <code>names</code>. The types are given as expressions.
    */
  public static class Signature extends Expr {
    public Name[] names;
    public Expr[] types;
    public Signature(Name[] names, Expr[] types) {
      super(TAG_SIGNATURE);
      this.names = names;
      this.types = types;
    }
  }

  /* ------------------------------------------------- Pizza's generated case factories. */

  /** Pizza's generated case factory. */
  public static Expr Number(String value) {
    return new Number(value);
  }

  /** Pizza's generated case factory. */
  public static Expr Denotation(String value) {
    return new Denotation(value);
  }

  /** Pizza's generated case factory. */
  public static Expr Variable(NameAppl name) {
    return new Variable(name);
  }

  /** Pizza's generated case factory. */
  public static Expr Tuple(Expr[] elements) {
    return new Tuple(elements);
  }

  /** Pizza's generated case factory. */
  public static Expr Product(Expr[] elements) {
    return new Product(elements);
  }

  /** Pizza's generated case factory. */
  public static Expr Binding(Decl.Eqn[] bindings) {
    return new Binding(bindings);
  }

  /** Pizza's generated case factory. */
  public static Expr Display(Expr[] elements) {
    return new Display(elements);
  }

  /** Pizza's generated case factory. */
  public static Expr Cond(Predicate cond, Expr thenPath, Expr elsePath) {
    return new Cond(cond, thenPath, elsePath);
  }

  /** Pizza's generated case factory. */
  public static Expr Quantor(QuantorKind kind, Expr matrix, Expr range) {
    return new Quantor(kind, matrix, range);
  }

  /** Pizza's generated case factory. */
  public static Expr Select(Expr oper, NameAppl name) {
    return new Select(oper, name);
  }

  /** Pizza's generated case factory. */
  public static Expr Unary(UnaryKind kind, Expr oper) {
    return new Unary(kind, oper);
  }

  /** Pizza's generated case factory. */
  public static Expr Binary(BinaryKind kind, Expr left, Expr right) {
    return new Binary(kind, left, right);
  }

  /** Pizza's generated case factory. */
  public static Expr Text(Decl[] decls, Predicate[] props) {
    return new Text(decls, props);
  }

  /** Pizza's generated case factory. */
  public static Expr GivenType(NameDecl name) {
    return new GivenType(name);
  }

  /** Pizza's generated case factory. */
  public static Expr FreeType(NameDecl name, Branch[] branches) {
    return new FreeType(name, branches);
  }

  /** Pizza's generated case factory. */
  public static Expr UniVar(Name name, Expr oper) {
    return new UniVar(name, oper);
  }

  /** Pizza's generated case factory. */
  public static Expr Signature(Name[] names, Expr[] types) {
    return new Signature(names, types);
  }
}
