package zeta.session;

import zeta.util.Name;

/**
 * Failures of the session system: expected outcomes a caller is meant to handle, as opposed
 * to the unrecoverable conditions in {@link Errors}.
 *
 * @version $Id: Failures.pizza,v 1.4 1998/09/06 22:38:13 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * A seven-case Pizza ADT that is also a checked `Exception`.  As with {@link Errors}, the
 * compiled shape is reproduced exactly and the hierarchy is `sealed` -- the seven cases are
 * the only subtypes anywhere in the jar.  Unlike `Errors`, the base is **not** abstract in the
 * reference, so it is not abstract here either.
 *
 * `FormFailed` is the one worth knowing: `ShellState.doEval` swallows it, which is why a
 * specification with a type error produces no `FinishedMessage` and why `harness/zdrive.py`
 * cannot wait for one (§6).
 *
 * One deviation from the compiled form: the base is `abstract` here, where the reference has
 * it concrete. Java needs that for a `switch` over a `Failures` to be exhaustive without a
 * `default` -- a sealed but instantiable base is itself a possible value. It is safe because
 * the only code that invokes `Failures(int)` is the seven cases below, which are ported with
 * it; `abi_gate.py` checks exactly that, and reports the change with the evidence.
 */
public abstract sealed class Failures extends Exception {

  // Tag values are fixed by the ABI: 1-based, declaration order.
  private static final int TAG_NO_SUCH_ENTITY = 1;
  private static final int TAG_ENTITY_EXISTS = 2;
  private static final int TAG_FORM_FAILED = 3;
  private static final int TAG_UNRESOLVABLE_NAME = 4;
  private static final int TAG_AMBIGIOUS_NAME = 5;
  private static final int TAG_CANNOT_COMPUTE = 6;
  private static final int TAG_COMPUTATION_FAILED = 7;

  /** Pizza's discriminant.  Public and final because compiled consumers read it directly. */
  public final int zeta$session$Failures$$tag;

  Failures(int tag) {
    this.zeta$session$Failures$$tag = tag;
  }

  /** An entity does not exists. */
  public static final class NoSuchEntity extends Failures {
    public String name;

    public NoSuchEntity(String name) {
      super(TAG_NO_SUCH_ENTITY);
      this.name = name;
    }
  }

  /** An entity of the given name does already exists. */
  public static final class EntityExists extends Failures {
    public String name;

    public EntityExists(String name) {
      super(TAG_ENTITY_EXISTS);
      this.name = name;
    }
  }

  /** A failure in evaluating a form. */
  public static final class FormFailed extends Failures {
    public String message;

    public FormFailed(String message) {
      super(TAG_FORM_FAILED);
      this.message = message;
    }
  }

  /** A partial unit name is not resolvable. */
  public static final class UnresolvableName extends Failures {
    public Name name;

    public UnresolvableName(Name name) {
      super(TAG_UNRESOLVABLE_NAME);
      this.name = name;
    }
  }

  /** A partial unit name is ambigious resolvable. */
  public static final class AmbigiousName extends Failures {
    public Name[] fullNames;

    public AmbigiousName(Name[] fullNames) {
      super(TAG_AMBIGIOUS_NAME);
      this.fullNames = fullNames;
    }
  }

  /** A target cannot be computed. */
  public static final class CannotCompute extends Failures {
    public Target target;

    public CannotCompute(Target target) {
      super(TAG_CANNOT_COMPUTE);
      this.target = target;
    }
  }

  /** A computation of the specified target failed. */
  public static final class ComputationFailed extends Failures {
    public Target target;

    public ComputationFailed(Target target) {
      super(TAG_COMPUTATION_FAILED);
      this.target = target;
    }
  }

  /** Pizza's generated case factory. */
  public static Failures NoSuchEntity(String name) {
    return new NoSuchEntity(name);
  }

  /** Pizza's generated case factory. */
  public static Failures EntityExists(String name) {
    return new EntityExists(name);
  }

  /** Pizza's generated case factory. */
  public static Failures FormFailed(String message) {
    return new FormFailed(message);
  }

  /** Pizza's generated case factory. */
  public static Failures UnresolvableName(Name name) {
    return new UnresolvableName(name);
  }

  /** Pizza's generated case factory. */
  public static Failures AmbigiousName(Name[] fullNames) {
    return new AmbigiousName(fullNames);
  }

  /** Pizza's generated case factory. */
  public static Failures CannotCompute(Target target) {
    return new CannotCompute(target);
  }

  /** Pizza's generated case factory. */
  public static Failures ComputationFailed(Target target) {
    return new ComputationFailed(target);
  }
}
