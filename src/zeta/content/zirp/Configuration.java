package zeta.content.zirp;

import zeta.util.AnnotatedTerm;

/** Classes for configurations.
  *
  * mSZ, like {@link State} and {@link Transition}: the corpus does not exercise it, so this
  * is ported against the ABI and reviewed rather than tested.
  *
  * Note that `Configuration` itself does not extend `Term` -- only its four nested classes do.
  * Kept as found.
  */
public class Configuration {

  public Aggregation[] aggregs;
  public Association[] assocs;
  public Binding[] bindings;

  public Configuration(Aggregation[] aggregs,
                       Association[] assocs,
                       Binding[] bindings) {
    this.aggregs = aggregs;
    this.assocs = assocs;
    this.bindings = bindings;
  }

  /** Pizza's generated case factory. */
  public static Configuration Configuration(Aggregation[] aggregs,
                                            Association[] assocs,
                                            Binding[] bindings) {
    return new Configuration(aggregs, assocs, bindings);
  }

  /** Aggregated processes. */
  public static class Aggregation extends AnnotatedTerm {
    public NameDecl proc_name;
    public NameAppl class_name;

    public Aggregation(NameDecl proc_name, NameAppl class_name) {
      this.proc_name = proc_name;
      this.class_name = class_name;
    }

    /** Pizza's generated case factory. */
    public static Aggregation Aggregation(NameDecl proc_name, NameAppl class_name) {
      return new Aggregation(proc_name, class_name);
    }
  }

  /** Associated processes. */
  public static class Association extends AnnotatedTerm {
    public NameAppl class_name;

    public Association(NameAppl class_name) {
      this.class_name = class_name;
    }

    /** Pizza's generated case factory. */
    public static Association Association(NameAppl class_name) {
      return new Association(class_name);
    }
  }

  /** Port binding. */
  public static class Binding extends AnnotatedTerm {
    public Port[] ports;

    public Binding(Port[] ports) {
      this.ports = ports;
    }

    /** Pizza's generated case factory. */
    public static Binding Binding(Port[] ports) {
      return new Binding(ports);
    }
  }

  public static class Port extends AnnotatedTerm {

    /** Pizza's discriminant. */
    public final int zeta$content$zirp$Configuration$Port$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_SELF_PORT = 1;
    private static final int TAG_EXTERNAL_PORT = 2;
    private static final int TAG_INTERNAL_PORT = 3;
    private static final int TAG_ASSOCIATED_PORT = 4;

    Port(int tag) {
      this.zeta$content$zirp$Configuration$Port$$tag = tag;
    }

    /** Self port.  Port of the class itself.
      * @param port_name the name of the port.
      */
    public static class SelfPort extends Port {
      public NameAppl port_name;

      public SelfPort(NameAppl port_name) {
        super(TAG_SELF_PORT);
        this.port_name = port_name;
      }
    }

    /** External port.  Introduces a new (external) port that exports
      * the bound ports to the environment.
      * @param port_name the name of the port.
      */
    public static class ExternalPort extends Port {
      public NameDecl port_name;

      public ExternalPort(NameDecl port_name) {
        super(TAG_EXTERNAL_PORT);
        this.port_name = port_name;
      }
    }

    /** Internal port.  Identifies a port of an aggregated process.
      * @param proc_name name of the aggregated process.
      * @param port_name name of the process' port.
      */
    public static class InternalPort extends Port {
      public NameAppl proc_name;
      public NameAppl port_name;

      public InternalPort(NameAppl proc_name, NameAppl port_name) {
        super(TAG_INTERNAL_PORT);
        this.proc_name = proc_name;
        this.port_name = port_name;
      }
    }

    /** Associated port.  Identifies a port of an associated process.
      * @param class_name name of the associated class.
      * @param port_name name of the process' port.
      */
    public static class AssociatedPort extends Port {
      public NameAppl class_name;
      public NameAppl port_name;

      public AssociatedPort(NameAppl class_name, NameAppl port_name) {
        super(TAG_ASSOCIATED_PORT);
        this.class_name = class_name;
        this.port_name = port_name;
      }
    }

    /** Pizza's generated case factory. */
    public static Port SelfPort(NameAppl port_name) {
      return new SelfPort(port_name);
    }

    /** Pizza's generated case factory. */
    public static Port ExternalPort(NameDecl port_name) {
      return new ExternalPort(port_name);
    }

    /** Pizza's generated case factory. */
    public static Port InternalPort(NameAppl proc_name, NameAppl port_name) {
      return new InternalPort(proc_name, port_name);
    }

    /** Pizza's generated case factory. */
    public static Port AssociatedPort(NameAppl class_name, NameAppl port_name) {
      return new AssociatedPort(class_name, port_name);
    }
  }
}
