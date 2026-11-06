package zeta.content.zirp;

import java.io.ObjectStreamException;

import zeta.util.AnnotatedTerm;
import zeta.util.CaseConstants;
import zeta.util.Term;

/** The abstract syntax of Z predicates.
  *
  * @version $Id: Predicate.pizza,v 1.1.1.1 1998/09/01 10:51:17 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * Pure data-structure file: no method bodies in the Pizza source, so there are no switches
  * to translate and no closures to convert.
  *
  * {@code FactKind}, {@code BinaryKind}, {@code QuantorKind}, and {@code ConstrRef} have only
  * nullary cases (no parentheses in the Pizza source), so they compile to
  * {@code public static final} constant fields on a concrete base.  None of them can be
  * {@code abstract}.
  *
  * {@code UnaryKind} mixes shapes: {@code Not} through {@code Prefix} are nullary constants
  * (no parens in Pizza source), while {@code StepConstr} and {@code TimeConstr} carry data
  * and compile to nested classes with factories.
  *
  * Tag values were read from the 2002 class files, not derived from declaration order.
  * Public-field declaration order in the constant-only inner ADTs follows the reference:
  * constants first, {@code $$tag} last.
  */
public class Predicate extends AnnotatedTerm {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$zirp$Predicate$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_SCHEMA = 1;
  private static final int TAG_TEST = 2;
  private static final int TAG_FACT = 3;
  private static final int TAG_UNARY = 4;
  private static final int TAG_BINARY = 5;
  private static final int TAG_QUANTOR = 6;

  Predicate(int tag) {
    this.zeta$content$zirp$Predicate$$tag = tag;
  }

  /** Applying a schema as a predicate (`schema reference').
    * @param schema  expression which denotes a schema, that is
    * a set of bindings. */
  public static class Schema extends Predicate {
    public Expr schema;
    public Schema(Expr schema) {
      super(TAG_SCHEMA);
      this.schema = schema;
    }
  }

  /** Element Test. Also represents relations: R(e1,...,en) ==
    * Test((e1,...,en),R).
    * @param elem expression denoting the element to
    * test
    * @param set expression denoting the set to test
    */
  public static class Test extends Predicate {
    public Expr elem;
    public Expr set;
    public Test(Expr elem, Expr set) {
      super(TAG_TEST);
      this.elem = elem;
      this.set = set;
    }
  }

  /** Facts.
    * @param kind the kind of the fact.
    */
  public static class Fact extends Predicate {
    public FactKind kind;
    public Fact(FactKind kind) {
      super(TAG_FACT);
      this.kind = kind;
    }
  }

  /** Kinds of facts. */
  public static class FactKind extends Term {
    /** Truth. */
    public static final FactKind True;
    /** Falsity. */
    public static final FactKind False;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Predicate$FactKind$$tag;

    private static final int TAG_TRUE = 1;
    private static final int TAG_FALSE = 2;

    FactKind(int tag) {
      this.zeta$content$zirp$Predicate$FactKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, FactKind.class);
    }

    static {
      True = new FactKind(TAG_TRUE);
      False = new FactKind(TAG_FALSE);
    }
  }

  /** Unary operators.
    * @param kind  the kind of the unary operator, logical negation
    *   and a lot of DZ specific operators.
    * @param oper  the predicate to which the operation is applied to. */
  public static class Unary extends Predicate {
    public UnaryKind kind;
    public Predicate oper;
    public Unary(UnaryKind kind, Predicate oper) {
      super(TAG_UNARY);
      this.kind = kind;
      this.oper = oper;
    }
  }

  /** Kind of unary operators. */
  public static class UnaryKind extends Term {
    /** Logical negation. */
    public static final UnaryKind Not;
    /** State formula (DZ only). */
    public static final UnaryKind StateLift;
    /** Transition formula (DZ only). */
    public static final UnaryKind TransLift;
    /** Awaiting (DZ only). */
    public static final UnaryKind Await;
    /** Everywhere (DZ only). */
    public static final UnaryKind Everywhere;
    /** Somewhere (DZ only.) */
    public static final UnaryKind Somewhere;
    /** Repetition (DZ only.) */
    public static final UnaryKind Repeat;
    /** Prefix (DZ only.) */
    public static final UnaryKind Prefix;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Predicate$UnaryKind$$tag;

    private static final int TAG_NOT = 1;
    private static final int TAG_STATELIFT = 2;
    private static final int TAG_TRANSLIFT = 3;
    private static final int TAG_AWAIT = 4;
    private static final int TAG_EVERYWHERE = 5;
    private static final int TAG_SOMEWHERE = 6;
    private static final int TAG_REPEAT = 7;
    private static final int TAG_PREFIX = 8;
    private static final int TAG_STEPCONSTR = 9;
    private static final int TAG_TIMECONSTR = 10;

    UnaryKind(int tag) {
      this.zeta$content$zirp$Predicate$UnaryKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, UnaryKind.class);
    }

    static {
      Not = new UnaryKind(TAG_NOT);
      StateLift = new UnaryKind(TAG_STATELIFT);
      TransLift = new UnaryKind(TAG_TRANSLIFT);
      Await = new UnaryKind(TAG_AWAIT);
      Everywhere = new UnaryKind(TAG_EVERYWHERE);
      Somewhere = new UnaryKind(TAG_SOMEWHERE);
      Repeat = new UnaryKind(TAG_REPEAT);
      Prefix = new UnaryKind(TAG_PREFIX);
    }

    /** Step constraint (DZ only).
      * @param ref   which step is referred to by the constraint.
      * @param rel  a binary Z relation describing how the referred
      *   step is related to the <code>expr</code>.
      * @param expr expression denoting a number of steps which is
      * related via <code>rel</code> with the referred step.
      */
    public static class StepConstr extends UnaryKind {
      public ConstrRef ref;
      public NameAppl rel;
      public Expr expr;
      public StepConstr(ConstrRef ref, NameAppl rel, Expr expr) {
        super(TAG_STEPCONSTR);
        this.ref = ref;
        this.rel = rel;
        this.expr = expr;
      }
    }

    /** Real-time constraint (DZ only). Similar to <code>StepConstr</code>,
      * with the only difference that instead of steps real-time
      * is used.
      * @see zeta.content.zirp.Predicate.UnaryKind#StepConstr
      */
    public static class TimeConstr extends UnaryKind {
      public ConstrRef ref;
      public NameAppl rel;
      public Expr expr;
      public TimeConstr(ConstrRef ref, NameAppl rel, Expr expr) {
        super(TAG_TIMECONSTR);
        this.ref = ref;
        this.rel = rel;
        this.expr = expr;
      }
    }

    /** Pizza's generated case factory. */
    public static UnaryKind StepConstr(ConstrRef ref, NameAppl rel, Expr expr) {
      return new StepConstr(ref, rel, expr);
    }

    /** Pizza's generated case factory. */
    public static UnaryKind TimeConstr(ConstrRef ref, NameAppl rel, Expr expr) {
      return new TimeConstr(ref, rel, expr);
    }
  }

  /** Reference of a time or step constraint. */
  public static class ConstrRef extends Term {
    /** Referring to the relative length of the interval. */
    public static final ConstrRef Length;
    /** Referring to the starting point of the interval. */
    public static final ConstrRef Start;
    /** Referring to the end point of the interval. */
    public static final ConstrRef End;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Predicate$ConstrRef$$tag;

    private static final int TAG_LENGTH = 1;
    private static final int TAG_START = 2;
    private static final int TAG_END = 3;

    ConstrRef(int tag) {
      this.zeta$content$zirp$Predicate$ConstrRef$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, ConstrRef.class);
    }

    static {
      Length = new ConstrRef(TAG_LENGTH);
      Start = new ConstrRef(TAG_START);
      End = new ConstrRef(TAG_END);
    }
  }

  /** Binary operators.
    * @param kind  the kind of the binary operator (the usual logical
    *   connectivities and DZ specific operators).
    * @param left  the left operand predicate.
    * @param right  the right operand predicate.
    */
  public static class Binary extends Predicate {
    public BinaryKind kind;
    public Predicate left;
    public Predicate right;
    public Binary(BinaryKind kind, Predicate left, Predicate right) {
      super(TAG_BINARY);
      this.kind = kind;
      this.left = left;
      this.right = right;
    }
  }

  /** Kind of binaries. */
  public static class BinaryKind extends Term {
    /** Logical conjunction. */
    public static final BinaryKind And;
    /** Logical disjunction. */
    public static final BinaryKind Or;
    /** Logical implication. */
    public static final BinaryKind Implies;
    /** Logical equivalence. */
    public static final BinaryKind Iff;
    /** Guarded action (DZ only). */
    public static final BinaryKind Guarded;
    /** Chopping (DZ only). */
    public static final BinaryKind Chop;
    /** Overlapping chop (DZ only). */
    public static final BinaryKind Compose;
    /** Preemption (DZ only). */
    public static final BinaryKind Preempt;
    /** Leadsto (DZ only). */
    public static final BinaryKind Leadsto;
    /** Transition (DZ/mSZ only). */
    public static final BinaryKind Trans;
    /** Computation (DZ only). */
    public static final BinaryKind Compute;
    /** Choice (DZ only). */
    public static final BinaryKind Choice;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Predicate$BinaryKind$$tag;

    private static final int TAG_AND = 1;
    private static final int TAG_OR = 2;
    private static final int TAG_IMPLIES = 3;
    private static final int TAG_IFF = 4;
    private static final int TAG_GUARDED = 5;
    private static final int TAG_CHOP = 6;
    private static final int TAG_COMPOSE = 7;
    private static final int TAG_PREEMPT = 8;
    private static final int TAG_LEADSTO = 9;
    private static final int TAG_TRANS = 10;
    private static final int TAG_COMPUTE = 11;
    private static final int TAG_CHOICE = 12;

    BinaryKind(int tag) {
      this.zeta$content$zirp$Predicate$BinaryKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, BinaryKind.class);
    }

    static {
      And = new BinaryKind(TAG_AND);
      Or = new BinaryKind(TAG_OR);
      Implies = new BinaryKind(TAG_IMPLIES);
      Iff = new BinaryKind(TAG_IFF);
      Guarded = new BinaryKind(TAG_GUARDED);
      Chop = new BinaryKind(TAG_CHOP);
      Compose = new BinaryKind(TAG_COMPOSE);
      Preempt = new BinaryKind(TAG_PREEMPT);
      Leadsto = new BinaryKind(TAG_LEADSTO);
      Trans = new BinaryKind(TAG_TRANS);
      Compute = new BinaryKind(TAG_COMPUTE);
      Choice = new BinaryKind(TAG_CHOICE);
    }
  }

  /** Quantors.
    * @param kind  the kind of the quantor.
    * @param matrix the matrix of the quantor, an expression denoting
    *   a schema.
    * @param range  the range of the quantor, a predicate. */
  public static class Quantor extends Predicate {
    public QuantorKind kind;
    public Expr matrix;
    public Predicate range;
    public Quantor(QuantorKind kind, Expr matrix, Predicate range) {
      super(TAG_QUANTOR);
      this.kind = kind;
      this.matrix = matrix;
      this.range = range;
    }
  }

  /** Quantor kinds. */
  public static class QuantorKind extends Term {
    /** Universal quantification. */
    public static final QuantorKind Forall;
    /** Existential quantification. */
    public static final QuantorKind Exists;
    /** Unique existential quantification. */
    public static final QuantorKind Exists1;
    /** Constructive binding. */
    public static final QuantorKind Let;

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Predicate$QuantorKind$$tag;

    private static final int TAG_FORALL = 1;
    private static final int TAG_EXISTS = 2;
    private static final int TAG_EXISTS1 = 3;
    private static final int TAG_LET = 4;

    QuantorKind(int tag) {
      this.zeta$content$zirp$Predicate$QuantorKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, QuantorKind.class);
    }

    static {
      Forall = new QuantorKind(TAG_FORALL);
      Exists = new QuantorKind(TAG_EXISTS);
      Exists1 = new QuantorKind(TAG_EXISTS1);
      Let = new QuantorKind(TAG_LET);
    }
  }

  /* ------------------------------------------------- Pizza's generated case factories. */

  /** Pizza's generated case factory. */
  public static Predicate Schema(Expr schema) {
    return new Schema(schema);
  }

  /** Pizza's generated case factory. */
  public static Predicate Test(Expr elem, Expr set) {
    return new Test(elem, set);
  }

  /** Pizza's generated case factory. */
  public static Predicate Fact(FactKind kind) {
    return new Fact(kind);
  }

  /** Pizza's generated case factory. */
  public static Predicate Unary(UnaryKind kind, Predicate oper) {
    return new Unary(kind, oper);
  }

  /** Pizza's generated case factory. */
  public static Predicate Binary(BinaryKind kind, Predicate left, Predicate right) {
    return new Binary(kind, left, right);
  }

  /** Pizza's generated case factory. */
  public static Predicate Quantor(QuantorKind kind, Expr matrix, Predicate range) {
    return new Quantor(kind, matrix, range);
  }
}
