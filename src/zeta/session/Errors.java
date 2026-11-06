package zeta.session;

/**
 * Errors of the session system: conditions a caller is not expected to recover from, as
 * opposed to the expected outcomes in {@link Failures}.
 *
 * @version $Id: Errors.pizza,v 1.3 1998/09/06 22:38:12 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * A four-case Pizza ADT that is also a `RuntimeException`. The compiled shape is reproduced
 * exactly: `public final int zeta$session$Errors$$tag` with 1-based values in declaration
 * order, a package-private `Errors(int)` constructor, one public nested class per case, and a
 * static factory per case returning the base type. The base stays **abstract**, as the
 * reference has it -- note that `RemoteMethodInvocationFailed` is declared *with* parentheses
 * in the Pizza source and so became a class, where a case declared without them (as
 * `zeta.agl.Option.None` is) would have become a static constant and forced the base to be
 * concrete.
 *
 * `sealed` is added: across all 905 reference classes the only subtypes of `Errors` are the
 * four cases below, so the set really is closed, and closing it lets every `switch` over an
 * `Errors` be exhaustive without the `default` branch that `FatalError.unexpectedCase()`
 * exists to fill.
 */
public abstract sealed class Errors extends RuntimeException {

  // Tag values are fixed by the ABI: 1-based, declaration order.
  private static final int TAG_PHYSICAL_ERROR = 1;
  private static final int TAG_DATA_INCONSISTENCY = 2;
  private static final int TAG_ASSERTION_FAILED = 3;
  private static final int TAG_REMOTE_METHOD_INVOCATION_FAILED = 4;

  /** Pizza's discriminant.  Public and final because compiled consumers read it directly. */
  public final int zeta$session$Errors$$tag;

  Errors(int tag) {
    this.zeta$session$Errors$$tag = tag;
  }

  /**
   * A physical error of the underlying system layers, which
   * does not represent an expected failure.
   */
  public static final class PhysicalError extends Errors {
    public Exception exception;

    public PhysicalError(Exception exception) {
      super(TAG_PHYSICAL_ERROR);
      this.exception = exception;
    }
  }

  /**
   * A consistency error of the underlying data model.
   */
  public static final class DataInconsistency extends Errors {
    public String message;

    public DataInconsistency(String message) {
      super(TAG_DATA_INCONSISTENCY);
      this.message = message;
    }
  }

  /**
   * An assertion failed: a method of the session systems is called
   * with arguments violating the specification, or a tool adaptor
   * does not behave has expected.
   */
  public static final class AssertionFailed extends Errors {
    public String message;

    public AssertionFailed(String message) {
      super(TAG_ASSERTION_FAILED);
      this.message = message;
    }
  }

  /**
   * Remote Exception occured during RMI.
   */
  public static final class RemoteMethodInvocationFailed extends Errors {
    public RemoteMethodInvocationFailed() {
      super(TAG_REMOTE_METHOD_INVOCATION_FAILED);
    }
  }

  /** Pizza's generated case factory. */
  public static Errors PhysicalError(Exception exception) {
    return new PhysicalError(exception);
  }

  /** Pizza's generated case factory. */
  public static Errors DataInconsistency(String message) {
    return new DataInconsistency(message);
  }

  /** Pizza's generated case factory. */
  public static Errors AssertionFailed(String message) {
    return new AssertionFailed(message);
  }

  /** Pizza's generated case factory. */
  public static Errors RemoteMethodInvocationFailed() {
    return new RemoteMethodInvocationFailed();
  }
}
