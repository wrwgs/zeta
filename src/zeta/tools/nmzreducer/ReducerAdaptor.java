package zeta.tools.nmzreducer;

import java.util.Date;

import zeta.agl.ArraySeq;
import zeta.agl.Enum;
import zeta.agl.HashSet;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.nmz.MZ;
import zeta.content.nmz.MZConstants;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Item;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.Used;
import zeta.content.zirp.ZNames;
import zeta.content.zirp.ZirpContentTypes;
import zeta.session.ContentType;
import zeta.session.DefaultAdaptor;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.util.FatalError;
import zeta.util.Name;

/** The Z to MicroZ reducer.
  *
  * @version $Id: ReducerAdaptor.pizza,v 1.15 2000/07/21 06:45:15 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * Loaded reflectively by name from `lib/zetarc`, so the class name and the public no-argument
  * constructor are ABI.
  *
  * The two closures over `globals` became loops. The first assigned to the captured
  * `globals` while `apply` was enumerating it: the enumeration is taken from the set as it was
  * *before* the loop (Pizza evaluated the receiver once), and each `include` yields a new set
  * that the next iteration continues to extend. The loop keeps exactly that: the receiver of
  * `enumerate()` is a separate local holding the old set. `Aggregate.apply` visits in
  * enumeration order, so the order of `include` calls -- and therefore the iteration order of
  * the resulting `zeta.agl.HashSet`, which decides the order of `newVar` calls and of the
  * `GLOBALS` line in every `nmz` artifact -- is unchanged.
  *
  * Pizza compiled each exhaustive `switch (absy)` with an implicit `default` that throws
  * `java.lang.Error`; `UnitAbsy` has exactly the two cases handled, so that branch is
  * unreachable. The port raises `FatalError.unexpectedCase` there instead.
  *
  * `deps.toArray(Target.class)`: Pizza compiled the literal as `Class.forName`, which
  * initialises `Target`; `Target` has no static initialiser with a side effect.
  */
public class ReducerAdaptor extends DefaultAdaptor {


  // CONSTANTS

  /*
   * The startup message of this adaptor.
   */
  private static final String startupMessage =
    "installed Z-to-MicroZ reducer v2.0";

  /**
   * Whether we should issue timing messages.
   */
  private static final boolean timing =
    System.getProperty("zeta.debug.timing") != null;





  // ADAPTOR INITIALIZATION

  /**
   * Return the name of this adaptor.
   */
  public Name getName() {
    return ReducerUtil.reducerName;
  }

  /**
   * Initialize a newly created instance of this adaptor.
   */
  public void initialize(Session session, StorageHandle handle) {
    super.initialize(session, handle);
    this.session = session;
    sendProgress(startupMessage);
    session.registerContentType(MZConstants.mzContentType);
  }


  // ADAPTOR METHODS

  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{ MZConstants.mzContentType };
  }

  /** Always throws. */
  public Unit lookupUnit(Name name)
  throws Failures.UnresolvableName {
    throw new Failures.UnresolvableName(name);
  }


  /**
   * Run the adaptor to compute the specified content.
   */
  public void tryCompute(Target spec)
  throws Failures.ComputationFailed, Failures.CannotCompute {

    ReducerUtil.readProperties();

    Set<Target> deps = new HashSet<Target>();

    // query checked absy and env of this unit
    UnitAbsy absy = (UnitAbsy) spec.unit.query(ZirpContentTypes.checkedAbsy);
    UnitEnv env = (UnitEnv) spec.unit.query(ZirpContentTypes.checkedEnv);
    deps = deps.include(new Target(spec.unit,
                                   ZirpContentTypes.checkedAbsy))
               .include(new Target(spec.unit,
                                   ZirpContentTypes.checkedEnv));

    // calculate the set of global names. Uses the "Used"
    // annotation of items, and the type of an item
    Set<Name> globals = new HashSet<Name>();
    {
      Item[] items = switch (absy) {
        case UnitAbsy.Section s -> s.items;
        case UnitAbsy.ClassDef c -> c.items;
        default -> throw FatalError.unexpectedCase("unit absy");
      };
      // PORT CHANGE: the original called `System.gc()` here, with the comment "STRANGE BUG
      // IN JDK 1.2 / LINUX -- solved calling gc above" at the `Used.get` below.  The bug was
      // a race in the type checker: a checker thread wrote `Used` only after handing control
      // back, so `runCheckers` could return before the write, and `Used.get` gave null under
      // CPU load (reproduced in the port: ~7% of runs with 18 sessions in parallel, gc call
      // or not).  The gc only widened the window in which the late write could land.
      // `UnitChecker.CheckerThread` now writes `Used` under the checker environment's
      // monitor, so every write happens-before `runCheckers` returns; see the PORT CHANGE
      // there.  With the cause gone the gc call has nothing to do, and it is dropped.
      for (int i = 0; i < items.length; i++) {
        // add applied names
        Name[] names = Used.get(items[i].an);
        // System.err.println(names.length);
        Seq<Name> snames = new ArraySeq<Name>(names);
        // System.err.println(snames.size());
        globals = globals.includeAll(snames);
        // add declared names
        if (items[i] instanceof Item.AxiomaticDef ad) {
          Expr def = ad.def;
          if (ReducerUtil.getTypeAn(def) instanceof Expr.Unary u
              && u.kind == Expr.UnaryKind.Power
              && u.oper instanceof Expr.Signature sig) {
            globals = globals.includeAll(new ArraySeq<Name>(sig.names));
          }
          // add generated names for type information
          switch (def) {
            case Expr.GivenType g ->
              globals =
                globals.include(MZConstants.makeGivenTypeInfoName(g.name.name))
                       .include(MZConstants.makeGivenTypeGenName(g.name.name));
            case Expr.FreeType f ->
              globals =
                globals.include(MZConstants.makeGivenTypeInfoName(f.name.name))
                       .include(MZConstants.makeGivenTypeGenName(f.name.name));
            default -> { }
          }
        }
      }
    }
    // enrich globals by names for "universal instance" of generics
    // was: globals.apply(fun (Name n)->void { ... globals = globals.include(...); })
    Set<Name> enriched = globals;
    for (Enum<Name> e = globals.enumerate(); e.more(); e.advance()) {
      Name n = e.current();
      UnitEnv.ConstantInfo info = env.getConstant(n);
      if (info != null && info.generics.length > 0) {
        enriched = enriched.include(new Name(n.getRepr() + "[]"));
      }
    }
    globals = enriched;


    // also add "builtin" names
    // FIXME: these should be done by the type checker
    globals = globals.include(ZNames.baseNum)
                     .include(ZNames.num)
                     .include(ZNames.nat)
                     .include(ZNames._seq)
                     .include(ZNames.pfun)
                     .include(ZNames._fun)
                     .include(ZNames.rel)
                     .include(ZNames.subseteq)
                     .include(ZNames.equal)
                     .include(ZNames.notEqual)
                     .include(ZNames.ran)
                     .include(ZNames.in)
                     .include(ReducerUtil.memoizeFunc)
                     .include(ZNames.emptyset);


    // reduce
    Date definedAt = new Date();
    sendProgress("reducing " + spec.unit.getName());
    Reducer reducer = new Reducer(env);
    reducer.enterContext();
    Locator globalLoc = ReducerUtil.getLocatorAn(absy);
    // was: globals.apply(fun (Name n)->void { reducer.newVar(globalLoc, n.getRepr()); })
    for (Enum<Name> e = globals.enumerate(); e.more(); e.advance()) {
      reducer.newVar(globalLoc, e.current().getRepr());
    }
    boolean errors = false;
    Name name = null;
    Name[] parents = null;
    switch (absy) {
      case UnitAbsy.Section s -> {
        Item[] items = s.items;
        name = s.name; parents = s.parents;
        for (int i = 0; i < items.length; i++) {
          reducer.reduce(items[i]);
          Diag diag = reducer.getAndClearDiag();
          if (!diag.isEmpty()) sendDiag(diag);
          errors = errors || diag.contains(Diag.Category.Error);
        }
      }
      case UnitAbsy.ClassDef c -> {
        Item[] items = c.items;
        name = c.name; parents = c.parents;
        for (int i = 0; i < items.length; i++) {
          reducer.reduce(items[i]);
          Diag diag = reducer.getAndClearDiag();
          if (!diag.isEmpty()) sendDiag(diag);
          errors = errors || diag.contains(Diag.Category.Error);
        }
      }

      /*
    default:
      sendDiag(new Diag(Locator.get(absy.an),
                        Diag.Category.Error,
                        "cannot reduce this kind of unit currently"));
      errors = true;
      */
      default -> throw FatalError.unexpectedCase("unit absy");
    }

    if (errors) {
      // computation failed
      throw new Failures.ComputationFailed(spec);
    } else {
      // define reduction result

      MZ.UnitDef def =
        reducer.exitContextWithUnit(name, new ArraySeq<Name>(parents),
                                    ArraySeq.from(globals),
                                    ReducerUtil.reducerLocator);

      spec.unit.define(MZConstants.mzContentType, def,
                       false,
                       definedAt,
                       deps.toArray(Target.class),
                       this
                      );
    }
  }

  /** */
  public boolean isUptodate(Target spec, Date definedAt) {
    return true;
  }

  /** */
  public boolean notifyOutdate(Target spec, Date definedAt,
                               StorageHandle handle) {
    return false;
  }

  /** */
  public void checkExternalData() {
  }

  /** */
  public void flush() {
  }

}
