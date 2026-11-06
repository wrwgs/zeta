package zeta.content.zirp;

import java.io.ObjectStreamException;

import zeta.util.AnnotatedTerm;
import zeta.util.CaseConstants;
import zeta.util.Term;

/** The abstract syntax of a statechart state.
 *
 * @version $Id: State.pizza,v 1.2 1998/09/06 15:24:31 wg Exp $
 *
 * mSZ, not plain Z: the statechart constructs are the part of ZETA's input language the
 * recovered corpus never exercises (see 7.5c on the eight diagnostics that stay uncovered for
 * want of mSZ markup), so this class is ported against the ABI and reviewed, not tested.
 */
public class State extends AnnotatedTerm {

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$content$zirp$State$$tag;

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_BASIC_STATE = 1;
  private static final int TAG_REF_STATE = 2;
  private static final int TAG_SUPER_STATE = 3;

  State(int tag) {
    this.zeta$content$zirp$State$$tag = tag;
  }

  /** Basic state. */
  public static class BasicState extends State {
    public NameDecl name;

    public BasicState(NameDecl name) {
      super(TAG_BASIC_STATE);
      this.name = name;
    }
  }

  /** State reference. */
  public static class RefState extends State {
    public NameDecl name;

    public RefState(NameDecl name) {
      super(TAG_REF_STATE);
      this.name = name;
    }
  }

  /** Superstate. */
  public static class SuperState extends State {
    public NameDecl name;
    public StateKind kind;
    public State[] substates;
    public NameDecl[] connectors;
    public Transition[] transitions;

    public SuperState(NameDecl name, StateKind kind, State[] substates,
                      NameDecl[] connectors, Transition[] transitions) {
      super(TAG_SUPER_STATE);
      this.name = name;
      this.kind = kind;
      this.substates = substates;
      this.connectors = connectors;
      this.transitions = transitions;
    }
  }

  /** Super-state kind. */
  public static class StateKind extends Term {

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$State$StateKind$$tag;

    private static final int TAG_XOR = 1;
    private static final int TAG_AND = 2;

    StateKind(int tag) {
      this.zeta$content$zirp$State$StateKind$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, StateKind.class);
    }

    /** Xor composition. */
    public static final StateKind Xor = new StateKind(TAG_XOR);

    /** And composition. */
    public static final StateKind And = new StateKind(TAG_AND);
  }

  /** Pizza's generated case factory. */
  public static State BasicState(NameDecl name) {
    return new BasicState(name);
  }

  /** Pizza's generated case factory. */
  public static State RefState(NameDecl name) {
    return new RefState(name);
  }

  /** Pizza's generated case factory. */
  public static State SuperState(NameDecl name, StateKind kind, State[] substates,
                                 NameDecl[] connectors, Transition[] transitions) {
    return new SuperState(name, kind, substates, connectors, transitions);
  }
}
