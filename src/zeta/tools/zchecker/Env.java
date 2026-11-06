package zeta.tools.zchecker;

import java.util.NoSuchElementException;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.MaxStrongComp;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.zirp.Expr;
import zeta.content.zirp.TermVisitor;
import zeta.content.zirp.Type;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZNames;
import zeta.format.Format;
import zeta.session.ComputationInterrupted;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.util.FatalError;
import zeta.util.Name;
import zeta.util.Term;

/**
 * The class Env holds all information belonging to the state of
 * a typecheck.
 *
 * @version $Id: Env.pizza,v 1.8 2000/07/06 09:18:24 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p><b>Shapes.</b>  <code>Instance</code> is a single-case class whose case name equals the
 * class name: fields, public constructor, static factory, no tag.  <code>Entry</code> is a
 * two-case ADT (<code>Defined</code> 1, <code>Undefined</code> 2, read with
 * <code>harness/adt_tags.py</code>) whose base extends nothing and is concrete in the
 * reference; it is kept concrete and unsealed.  <code>CheckerInfo</code> is a private
 * single-case class; its private static factory is kept although nothing calls it.  The
 * private field order of <code>Env</code> and of <code>CheckerInfo</code> is the 2002
 * order.  Pizza's <code>access$NNN</code> bridges, the lifted
 * <code>$closure$...</code> methods and the two <code>$$closures</code> classes are gone;
 * nothing outside this package references any of them (checked against all reference class
 * files).</p>
 *
 * <p><b>Closures.</b>  Every Pizza <code>fun</code> here was passed to
 * <code>apply</code> as a fold that assigns to a captured local (<code>graph</code>,
 * <code>locs</code>, <code>acts</code>, <code>subs</code>, the array index <code>i</code>);
 * those became loops over <code>enumerate()</code>, which is the order
 * <code>Aggregate.apply</code> visits in.  The two closures whose <em>result</em> is used
 * (<code>Seq.map</code> in <code>handleDeadlock</code>, <code>Seq.filter</code> in
 * <code>get</code>) stay lambdas, so the returned sequence has the receiver's class as
 * before.  <code>registerChecker</code>'s closure only assigns a field and stays a
 * lambda.</p>
 *
 * <p><b>Threads.</b>  The checker threads, their monitors and the order of every
 * <code>wait</code>/<code>notify</code> are transcribed one-for-one; the order in which
 * suspended checkers are resumed decides the order of diagnostics, which the negative
 * suites pin.</p>
 *
 * <p>PORT CHANGE (cancellation; see <code>zeta.session.ComputationInterrupted</code>).  The
 * master's and the checkers' <code>wait()</code>s turned <code>InterruptedException</code>
 * into a <code>FatalError</code> -- harmless while nothing interrupted them, since the 2002
 * shell used <code>Thread.stop</code>, which stopped only the master and left the checker
 * threads parked forever.  Now the shell interrupts the master (the evaluation thread), and
 * {@link #runCheckers} turns that into a cancellation of the whole check:</p>
 * <ol>
 * <li>under this monitor it sets <code>cancelled</code>, from which point
 * <code>runNext</code> hands control to nobody, so no further checker starts or resumes;</li>
 * <li>it interrupts every checker thread that has been started.  A checker parked in
 * {@link #get} wakes with <code>InterruptedException</code>; the one that is running sees
 * <code>cancelled</code> at its next {@link #get} (one per global name lookup) or else
 * finishes its item.  Either way it unwinds with <code>ComputationInterrupted</code> through
 * <code>UnitChecker.CheckerThread</code>'s <code>finally</code>, i.e. through
 * {@link #unregisterChecker}, which in cancelled mode only counts it out
 * (<code>liveCheckers</code>) -- no deadlock resolution, no hand-off;</li>
 * <li>it waits until <code>liveCheckers</code> is zero, and only then throws
 * <code>ComputationInterrupted</code> to its caller.  So when the command ends no checker
 * thread is left blocked, and none still touches the unit environment or the absy.</li>
 * </ol>
 * <p>The master does not lock the checkers' <code>CheckerInfo</code>s to wake them, as
 * <code>runNext</code> does: the running checker may hold its own <code>CheckerInfo</code>
 * while waiting for this monitor in {@link #get}, which the master holds -- a lock-order
 * inversion.  An interrupt needs no lock.  A second interrupt of the master while it waits
 * for the checkers to unwind is absorbed: the cancellation it asks for is already under way
 * and ends in the same exception.  Nothing here runs unless an interrupt arrives, so an
 * uninterrupted check -- its hand-off order and its diagnostics -- is unchanged.</p>
 */
public class Env {

  // STRUCTURES

  /**
   * The class of type actualizations.
   */
  public static class Instance extends Term {
    public Expr type;
    public Expr[] variables;

    public Instance(Expr type, Expr[] variables) {
      this.type = type;
      this.variables = variables;
    }

    /** Pizza's generated case factory. */
    public static Instance Instance(Expr type, Expr[] variables) {
      return new Instance(type, variables);
    }
  }

  /**
   * The class of environment entries, holding information about a name.
   */
  public static class Entry {
    // FIXME: replace entry, remove Undefined case

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$tools$zchecker$Env$Entry$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_DEFINED = 1;
    private static final int TAG_UNDEFINED = 2;

    Entry(int tag) {
      this.zeta$tools$zchecker$Env$Entry$$tag = tag;
    }

    public static class Defined extends Entry {
      public Name name;
      public Locator locator;
      public Seq<Name> genericity;
      public Expr type;

      public Defined(Name name, Locator locator, Seq<Name> genericity,
                     Expr type) {
        super(TAG_DEFINED);
        this.name = name;
        this.locator = locator;
        this.genericity = genericity;
        this.type = type;
      }
    }

    public static class Undefined extends Entry {
      public Name name;

      public Undefined(Name name) {
        super(TAG_UNDEFINED);
        this.name = name;
      }
    }

    /** Pizza's generated case factory. */
    public static Entry Defined(Name name, Locator locator, Seq<Name> genericity,
                                Expr type) {
      return new Defined(name, locator, genericity, type);
    }

    /** Pizza's generated case factory. */
    public static Entry Undefined(Name name) {
      return new Undefined(name);
    }
  }


  // FIELDS

  /**
   * The diagnostics accumulated in this environment.
   */
  private Diag diag = new Diag();


  /**
   * The unit environment.
   */
  private final UnitEnv unitEnv;

  /**
   * A mapping of unit names to unit environments of enriched classes.
   */
  private final Map<Name, UnitEnv> enrichedEnvs;

  /**
   * The master thread waiting for checkers to terminate.
   */
  private Thread masterThread = null;

  /**
   * The current running checker thread.
   */
  private Thread currentChecker = null;

  /**
   * The checker threads which are waiting round-robin.
   */
  private Seq<Thread> waitingCheckers = new BufferSeq<>();

  /**
   * The checker threads suspended for name definitions; factorized by name.
   */
  private Map<Name, Seq<Thread>> suspendedCheckers = new HashMap<>();

  /**
   * The intention of checker threads for defining names; factorized by name.
   */
  private Map<Name, Seq<Thread>> intentionMap = new HashMap<>();


  /**
   * Information about checker threads.
   */
  private Map<Thread, CheckerInfo> checkerInfos = new HashMap<>();

  private static class CheckerInfo {
    private Locator suspendedAt;
    private Set<Name> intendedDefs;
    private Set<Name> usage;
    /** Set by runNext before it notifies this checker; guards the wait in get().
      * PORT CHANGE (guarded waits): see runCheckers. Guarded by this CheckerInfo. */
    private boolean resumed;

    CheckerInfo(Locator suspendedAt, Set<Name> intendedDefs, Set<Name> usage) {
      this.suspendedAt = suspendedAt;
      this.intendedDefs = intendedDefs;
      this.usage = usage;
    }

    /** Pizza's generated case factory (private, as declared). */
    private static CheckerInfo CheckerInfo(Locator suspendedAt,
                                           Set<Name> intendedDefs,
                                           Set<Name> usage) {
      return new CheckerInfo(suspendedAt, intendedDefs, usage);
    }
  }


  // CONSTRUCTION

  /**
   * Create a new environment based on the given unit environment.
   */
  private Env(UnitEnv unitEnv, Map<Name, UnitEnv> enrichedEnvs) {
    this.unitEnv = unitEnv;
    this.enrichedEnvs = enrichedEnvs;
  }

  /**
   * Create a new environment for section checking, based
   * on the given unit environment which contains definitions for
   * all parent units.
   */
  public static Env sectionEnv(UnitEnv unitEnv) {
    return new Env(unitEnv, new HashMap<>());
  }

  /**
   * Create a new environment for class checking, based
   * on the given unit environment which contains definitions for
   * all parent units, the array of environments belonging to
   * enriched classes, and formal parameters of the unit.
   */
  public static Env classEnv(UnitEnv unitEnv, UnitEnv[] enrichedEnvs,
                             Name[] formals) {
    Map<Name, UnitEnv> eenvs = new HashMap<>();
    for (int i = 0; i < enrichedEnvs.length; i++) {
      eenvs = eenvs.define(enrichedEnvs[i].getName(), enrichedEnvs[i]);
    }
    Env env = new Env(unitEnv, eenvs);
    Local lenv = env.newLocal();
    for (int i = 0; i < formals.length; i++) {
      lenv.defineUnique(formals[i], unitEnv.getLocator(),
                        makePower(makeGiven(formals[i])));
    }
    lenv.makeGlobal(new ArraySeq<>());
    return env;
  }



  // DIAGNOSTICS

  /**
   * Add diagnostics to environment.
   */
  public synchronized void addDiag(Locator locator,
                                   Diag.Category category,
                                   Format message) {
    diag = diag.add(locator, category, message);
  }

  /**
   * Get and clear diagnostics.
   */
  public synchronized Diag getAndClearDiag() {
    Diag d = diag;
    diag = new Diag();
    return d;
  }

  // UNIT ENVS

  /**
   * Get the unit environment embedded in this environment.
   */
  public UnitEnv getUnitEnv() {
    return unitEnv;
  }

  /**
   * Get the unit environment of enriched named class, or null,
   * if not existent.
   */
  public UnitEnv getEnrichedEnv(Name unitName) {
    try {
      return enrichedEnvs.get(unitName);
    }
    catch (NoSuchElementException e) {
      return null;
    }
  }



  // CHECKER THREADS, GLOBAL NAME DEFINITION AND LOOKUP

  /**
   * Register the given thread to be a checker working on this
   * environment. The thread is expected to be not started.  The
   * thread must call the method unregisterChecker() once it
   * terminates.  <p>
   *
   * The names which are (approximately) going to be defined by this
   * checker are passed as an argument. This information is used for
   * enhancing diagnostics for undeclared/cyclic declared names.
   */
  public synchronized void registerChecker(Thread thread,
                                           Set<Name> intendedDefs) {
    checkerInfos = checkerInfos.define(thread,
                                       new CheckerInfo(null,
                                                       intendedDefs,
                                                       new HashSet<>()));
    waitingCheckers = waitingCheckers.append(thread);
    intendedDefs.apply(
      n -> {
        try {
          intentionMap =
            intentionMap.define(n, intentionMap.get(n).append(thread));
        }
        catch (NoSuchElementException e) {
          intentionMap =
            intentionMap.define(n, new ArraySeq<>(thread));
        }
      }
    );
  }

  /**
   * Run the registered checker threads until all are terminated.
   */
  public synchronized void runCheckers() {
    if (waitingCheckers.size() > 0) {
      masterThread = Thread.currentThread();
      checkersDone = false;
      runNext();
      try {
        // PORT CHANGE (guarded waits): was a single wait(). Java permits spurious wakeups,
        // and a spurious return here would let the caller read the checked units before the
        // checkers have finished -- the same symptom as the Used race fixed in
        // UnitChecker.CheckerThread. The flag is set by unregisterChecker under this
        // monitor immediately before its notify(), so the loop exits exactly when the 1998
        // code meant it to; the hand-off order between checkers is unchanged. The checker
        // side (get) gets the same treatment through CheckerInfo.resumed.
        while (!checkersDone)
          wait();
      }
      catch (InterruptedException e) {
        // PORT CHANGE (cancellation): was FatalError.unexpectedCase("checker master
        // interrupted"); see the class comment.
        throw cancelCheckers(e);
      }
    }
  }

  /** Set (once) by cancelCheckers when the master is interrupted.  Guarded by this Env's
    * monitor.  PORT CHANGE (cancellation), see the class comment. */
  private boolean cancelled;

  /** Checker threads started and not yet unregistered.  Guarded by this Env's monitor.
    * PORT CHANGE (cancellation), see the class comment. */
  private int liveCheckers;

  /** Cancel the check on behalf of the interrupted master, and return the exception for it
    * to throw once every started checker has unwound.  Called with this monitor held.
    * PORT CHANGE (cancellation), see the class comment. */
  private ComputationInterrupted cancelCheckers(InterruptedException cause) {
    cancelled = true;
    for (Enum<Thread> te = checkerInfos.domain().enumerate(); te.more(); te.advance()) {
      Thread t = te.current();
      if (t.isAlive()) t.interrupt();
    }
    while (liveCheckers > 0) {
      try {
        wait();
      }
      catch (InterruptedException again) {
        // already cancelling; see the class comment
      }
    }
    return ComputationInterrupted.of(cause);
  }

  /** The checkers' safe point: unwind if the check has been cancelled.  Called with this
    * monitor held.  PORT CHANGE (cancellation), see the class comment. */
  private void checkCancelled() {
    if (cancelled) throw new ComputationInterrupted();
  }

  /** Set by unregisterChecker when the last checker terminates; guards the master's wait in
    * runCheckers. Guarded by this Env's monitor. */
  private boolean checkersDone;

  /** Run the next waiting thread. */
  private synchronized void runNext() {
    // PORT CHANGE (cancellation): a cancelled check hands control to nobody.
    if (cancelled) return;
    Thread next = waitingCheckers.head();
    waitingCheckers = waitingCheckers.tail();
    if (!next.isAlive()) {
      liveCheckers++;
      next.start();
    } else {
      CheckerInfo info = checkerInfos.get(next);
      synchronized (info) {
        info.resumed = true;
        info.notify();
      }
    }
  }

  /**
   * Unregister the running thread as a checker working on
   * this environment. Returns the set of global names lookuped
   * by this thread.
   */
  public synchronized Set<Name> unregisterChecker() {
    Thread self = Thread.currentThread();
    CheckerInfo info = checkerInfos.get(self);
    // checkerInfos = checkerInfos.undefine(self);
    liveCheckers--;
    if (cancelled) {
      // PORT CHANGE (cancellation): only count the checker out; the master is waiting for
      // the last one in cancelCheckers.
      if (liveCheckers == 0) notify();
      return info.usage;
    }
    if (waitingCheckers.size() == 0 && suspendedCheckers.size() > 0) {
      // deadlock/cycle.
      handleDeadlock();
    }
    if (waitingCheckers.size() == 0) {
      // resume master thread now
      checkersDone = true;
      notify();
    } else {
      // resume the next checker in waiting list
      runNext();
    }
    return info.usage;
  }

  /**
   * Handle a deadlock, by awaking appropriate suspended thread(s)
   * and declaring the names they are waiting for to be undefined.
   */
  private synchronized void handleDeadlock() {
    // strategy: build the maximal strong components of name dependencies
    // according to usage and intentions, and resolve
    // the lowest cycle.

    Map<Name, Set<Name>> graph = new HashMap<>();
    for (Enum<Pair<Name, Seq<Thread>>> pe = suspendedCheckers.enumerate();
         pe.more(); pe.advance()) {
      Pair<Name, Seq<Thread>> p = pe.current();
      Name waitedFor = p.fst;
      Seq<Thread> waiting = p.snd;
      if (!graph.defines(waitedFor))
        graph = graph.define(waitedFor, new HashSet<>());
      for (Enum<Thread> te = waiting.enumerate(); te.more(); te.advance()) {
        CheckerInfo info = checkerInfos.get(te.current());
        for (Enum<Name> ne = info.intendedDefs.enumerate(); ne.more(); ne.advance()) {
          Name n = ne.current();
          try {
            graph = graph.define(n, graph.get(n).include(waitedFor));
          }
          catch (NoSuchElementException e) {
            graph = graph.define(n, new HashSet<Name>().include(waitedFor));
          }
        }
      }
    }
    Seq<Seq<Name>> msc = MaxStrongComp.msc(graph);
    Seq<Name> cycle = msc.head();
    if (cycle.size() <= 1) {
      // a simple undefined name
      Name name = cycle.head();
      Seq<Locator> locs =
        suspendedCheckers.get(name).map(t -> checkerInfos.get(t).suspendedAt);
      addDiag(locs.head(),
              Diag.Category.Error,
              Report.undeclared(name, locs.tail()));
      // define the name (awaking suspended threads)
      define(name, locs.head(), new ArraySeq<>(), Type.anyType);
    } else {
      // a real cycle
      Seq<Locator> locs = new BufferSeq<>();
      for (Enum<Name> ne = cycle.enumerate(); ne.more(); ne.advance()) {
        Seq<Thread> waiting = suspendedCheckers.get(ne.current());
        for (Enum<Thread> te = waiting.enumerate(); te.more(); te.advance()) {
          locs = locs.append(checkerInfos.get(te.current()).suspendedAt);
        }
      }
      addDiag(locs.head(),
              Diag.Category.Error,
              Report.cyclic(cycle, locs.tail()));
      // define the names (awaking suspended threads)
      for (Enum<Name> ne = cycle.enumerate(); ne.more(); ne.advance()) {
        define(ne.current(), locs.head(), new ArraySeq<>(), Type.anyType);
      }
    }
  }

  /**
   * Define the given name in the global environment.
   *
   * If the name is already defined, type compatibility
   * with the older definition will be checked, and
   * diagnostics may be produced.
   *
   * If threads are waiting for the definition of the given name they
   * will be resumed.
   */
  public synchronized
  void define(Name name, Locator locator, Seq<Name> genericity, Expr type) {
    // System.out.println("define global " + name);
    unitEnv.declareConstant(locator, name, genericity.toArray(Name.class),
                            type);
    diag = diag.combine(unitEnv.getAndClearDiag());

    // awake threads waiting for this definition
    try {
      Seq<Thread> suspended = suspendedCheckers.get(name);
      suspendedCheckers = suspendedCheckers.undefine(name);
      waitingCheckers = waitingCheckers.appendAll(suspended);
    }
    catch (NoSuchElementException e) {}
  }

  /**
   * Get the definition of the given name in the global environment.
   *
   * If the name is not yet defined, the calling thread will wait
   * until its definition.
   *
   * @see lookup
   */
  public Entry get(Name name, Locator at) {
    // System.out.println("get global " + name);
    Thread self = Thread.currentThread();
    CheckerInfo cinfo;
    try {
       cinfo = checkerInfos.get(self);
    }
    catch (NoSuchElementException e) {
      // self not registered as a checker thread -- assume no threads are
      // running
      UnitEnv.ConstantInfo info = unitEnv.getConstant(name);
      if (info != null) {
        return Entry.Defined(info.name, info.locator,
                             new ArraySeq<>(info.generics),
                             info.type);
      } else {
        addDiag(at,
                Diag.Category.Error,
                Report.undeclared(name, new ArraySeq<>()));
        return Entry.Defined(name, at, new ArraySeq<>(), Type.anyType);
      }
    }
    // begin parallel lookup mode
    synchronized (cinfo) {
      synchronized (this) {
        // PORT CHANGE (cancellation): the checkers' safe point; see the class comment.
        checkCancelled();

        // account usage
        cinfo.usage = cinfo.usage.include(name);

        // look for definition in unit environment
        UnitEnv.ConstantInfo info = unitEnv.getConstant(name);
        if (info != null) {
          return Entry.Defined(info.name, info.locator,
                               new ArraySeq<>(info.generics),
                               info.type);
        } else {

          // suspend to wait for definition
          cinfo.suspendedAt = at;
          cinfo.resumed = false;
          if (suspendedCheckers.defines(name)) {
            suspendedCheckers =
              suspendedCheckers.define(name,
                                       suspendedCheckers.get(name)
                                                        .append(self));
          } else {
            suspendedCheckers =
              suspendedCheckers.define(name, new BufferSeq<Thread>().append(self));
          }

          if (waitingCheckers.isEmpty()) {
            // handle a deadlock, by defining some names
            handleDeadlock();
            info = unitEnv.getConstant(name);
            if (info != null) {
              // the deadlock handler has defined our name;
              // continue immediatly (must remove self from waiting list)
              waitingCheckers =
                waitingCheckers.filter(t -> t != self);
              return Entry.Defined(info.name, info.locator,
                                   new ArraySeq<>(info.generics),
                                   info.type);
            }
          }
          // activate the next thread
          runNext();
        }
      }
      try {
        // PORT CHANGE (guarded waits): was a single cinfo.wait(); see runCheckers.
        while (!cinfo.resumed)
          cinfo.wait();
      }
      catch (InterruptedException e) {
        // PORT CHANGE (cancellation): was FatalError.unexpectedCase("checker thread
        // interrupted").  Only cancelCheckers interrupts a checker; see the class comment.
        throw ComputationInterrupted.of(e);
      }
      // System.out.println(self + " resumed for " + name);

      synchronized (this) {
        // PORT CHANGE (cancellation): resumed just before the check was cancelled.
        checkCancelled();
        UnitEnv.ConstantInfo info = unitEnv.getConstant(name);
        if (info != null) {
          return Entry.Defined(info.name, info.locator,
                               new ArraySeq<>(info.generics),
                               info.type);
        } else {
          // this must not happen
          throw FatalError.unexpectedCase(
                  "name still not defined for resumed checker thread");
        }
      }
    }
  }

  /**
   * Marke the given global name to be used in this context.
   */
  public void markUsed(Name name) {
    // System.out.println("get global " + name);
    Thread self = Thread.currentThread();
    CheckerInfo cinfo;
    try {
       cinfo = checkerInfos.get(self);
    }
    catch (NoSuchElementException e) {
      // self not registered as a checker thread -- assume no threads are
      // running
      return;
    }
    // begin parallel lookup mode
    synchronized (cinfo) {
      synchronized (this) {
        // account usage
        cinfo.usage = cinfo.usage.include(name);
      }
    }
  }



  // LOCAL ENVIRONMENTS

  /**
   * Create a new local environment embedding the given global
   * environment.
   */
  public Local newLocal() {
    return new Local();
  }

  /**
   * The class of local environments. A local environment extends
   * a global environment by the possibility to open and close local
   * scopes.
   */
  public class Local {

    /*
     * The current (topmost) scope.
     */
    private Map<Name, Entry> current = new HashMap<>();

    /*
     * The stack of outer scopes.
     */
    private Seq<Map<Name, Entry>> outer = new BufferSeq<>();

    public Local() {
    }

    /*
     * Return the global environment embedded in this local environment.
     */
    public Env global() {
      return Env.this;
    }

    /*
     * Add diagnostics to the embedded global environment.
     */
    public void addDiag(Locator loc, Diag.Category category, Format msg) {
      Env.this.addDiag(loc, category, msg);
    }

    /**
     * Return the set of all names defined in the current scope.
     */
    public Set<Name> getDefinedNames() {
      return current.domain();
    }

    /**
     * Get the definition of name in the current scope, or null,
     * if no definition.
     */
    public Entry.Defined get(Name name) {
      try {
        return (Entry.Defined) current.get(name);
      }
      catch (NoSuchElementException e) {
        return null;
      }
    }

    /**
     * Define the given local name in the innermost scope of this local
     * environment. If a definition of this name already exists, it
     * will be checked for compatibility, and diagnostics may be
     * produced.
     */
    public void define(Name name, Locator loc, Expr type) {
      try {
        Entry old = current.get(name);
        if (old instanceof Entry.Defined d) {
          checkRedeclaration(name,
                             loc, d.locator, new ArraySeq<>(), new ArraySeq<>(),
                             type, d.type);
          return;
        }
      }
      catch (NoSuchElementException e) {}
      current = current.define(name,
                               new Entry.Defined(name, loc,
                                                 new ArraySeq<>(),
                                                 type));
    }

    /**
     * Define the given local name in the innerest scope of this local
     * environment. If a definition for the name already exists,
     * diagnostics will be produced.
     */
    public void defineUnique(Name name, Locator loc, Expr type) {
      try {
        Entry old = current.get(name);
        if (old instanceof Entry.Defined) {
          // PORT NOTE -- as in 1998, the diagnostic names the *new* locator as the
          // "previous declaration" (the source binds the old one as loc1 and never uses
          // it); confirmed in the 2002 class file.  The negative suites pin this text.
          addDiag(loc, Diag.Category.Error,
                  Report.redeclaration(name, loc));
          return;
        }
      }
      catch (NoSuchElementException e) {}
      current = current.define(name,
                               new Entry.Defined(name, loc,
                                                 new ArraySeq<>(),
                                                 type));
    }

    /**
     * Open a new (empty) scope. Future defines will be addressed
     * to the newly opened scope. For lookups, names defined in
     * more recent opened scopes shadow names in outer scopes.
     * One scope is opened by default in a newly created local
     * environment.
     */
    public void open() {
      outer = outer.prepend(current);
      current = new HashMap<>();
    }


    /**
     * Close the last opened scope.
     */
    public void close() {
      current = outer.head();
      outer = outer.tail();
    }

    /**
     * Make a signature of the definitions in the current scope.
     */
    public Expr.Signature makeSignature() {
      Name[] names = new Name[current.size()];
      Expr[] types = new Expr[names.length];
      int i = 0;
      for (Enum<Pair<Name, Entry>> e = current.enumerate(); e.more(); e.advance()) {
        if (e.current().snd instanceof Entry.Defined d) {
          // System.out.println("define " + name);
          names[i] = d.name;
          types[i] = d.type;
          i++;
        }
      }
      return Env.makeSignature(names, types);
    }

    /**
     * Commit the definitions in the current scope to be members
     * of the global scope. Each definition is augmented by the
     * given generics.
     */
    public void makeGlobal(Seq<Name> generics) {
      for (Enum<Pair<Name, Entry>> e = current.enumerate(); e.more(); e.advance()) {
        if (e.current().snd instanceof Entry.Defined d) {
          // System.out.println("define " + name);
          Env.this.define(d.name, d.locator, generics, d.type);
        }
      }
    }

    /**
     * Lookup a name in the local environment and return its type
     * instance. Local scopes are tried out innermost-out, finally
     * the global environment is consulted. An "undeclared" error
     * is generated if no definition is found. If the name is generic,
     * an instance with fresh type variables will be generated.
     */
    public Instance lookup(Name name, Locator loc) {
      try {
        Entry entry = current.get(name);
        if (entry instanceof Entry.Defined d) {
          return new Instance(d.type, new Expr[0]);
        }
      }
      catch (NoSuchElementException e) {}
      for (int i = 0; i < outer.size(); i++) {
        try {
          Entry entry = outer.at(i).get(name);
          if (entry instanceof Entry.Defined d) {
            return new Instance(d.type, new Expr[0]);
          }
        }
        catch (NoSuchElementException e) {}
      }
      Entry entry = Env.this.get(name, loc);
      if (entry instanceof Entry.Defined d) {
        Seq<Name> generics = d.genericity;
        Expr type = d.type;
        if (generics.isEmpty())
          return new Instance(type, new Expr[0]);
        else {
          Map<Name, Expr> subs = new HashMap<>();
          Seq<Expr> acts = new BufferSeq<>();
          for (Enum<Name> e = generics.enumerate(); e.more(); e.advance()) {
            Name n = e.current();
            Expr var = makeVar(new Name("%" + n.toString()));
            acts = acts.append(var);
            subs = subs.define(n, var);
          }
          return new Instance(substitute(type, subs),
                              acts.toArray(Expr.class));
        }
      } else
        throw FatalError.unexpectedCase();
    }

  } // end class Local


  // AUXILIARIES

  /**
   * Check for compatible redeclaration of name.
   */
  private void checkRedeclaration(Name name,
                                  Locator loc, Locator oldOrig,
                                  Seq<Name> generics, Seq<Name> oldGenerics,
                                  Expr type, Expr oldExpr) {
    if (generics.size() != oldGenerics.size()) {
      addDiag(loc,
              Diag.Category.Error,
              Report.redeclarationIncompatGenerics(name, oldOrig,
                                                   oldGenerics, generics));
    } else {
      if (generics.size() > 0) {
        // perform alpha conversion
        type =
          substitute(type,
                     new HashMap<Name, Expr>().defineAll(generics,
                                                         oldGenerics.map(Env::makeGiven))
                    );
      }
      // check compatibility
      try {
        Type.unify(oldExpr, type);
      }
      catch (Type.UnificationFailure e) {
        addDiag(loc,
                Diag.Category.Error,
                Report.redeclarationIncompatExprs(name, oldOrig,
                                                  oldExpr, type, e));
      }
    }
  }

  /**
   * Substitute name in type expression.
   */
  public Expr substitute(Expr expr, Map<Name, Expr> subs) {
    Expr newExpr = new Substitutor(subs).visit(expr);
    return newExpr != null ? newExpr : expr;
  }

  /**
   * Substitute names in array of type expression.
   */
  public Expr[] substitute(Expr[] exprs, Map<Name, Expr> subs) {
    Expr[] newExprs = new Substitutor(subs).visit(exprs);
    return newExprs != null ? newExprs : exprs;
  }

  /**
   * Term visitor class for substitution.
   */
  private static class Substitutor extends TermVisitor {
    private final Map<Name, Expr> subs;

    Substitutor(Map<Name, Expr> subs) {
      this.subs = subs;
    }
    public Expr visit(Expr.Variable expr) {
      try {
        return subs.get(expr.name.name);
      } catch (NoSuchElementException e) {
        return super.visit(expr);
      }
    }
  }

  /**
   * Make a given type of according name.
   */
  public static Expr makeGiven(Name name) {
    return Type.makeGiven(name);
  }

  /**
   * Make a generic given type of according name.
   */
  public static Expr makeGenGiven(Name name, Seq<Expr> actuals) {
    return Type.makeGenGiven(name, actuals.toArray(Expr.class));
  }

  /**
   * Make a free type variable. The given name has only descriptive
   * purposes in error messages.
   */
  public static Expr makeVar(Name name) {
    return Type.makeVar(name);
  }

  /**
   * Make a powerset type.
   */
  public static Expr makePower(Expr elemType) {
    return Type.makePower(elemType);
  }

  /**
   * Make a product type.
   */
  public static Expr makeProduct(Expr[] elemTypes) {
    return Type.makeProduct(elemTypes);
  }

  /**
   * Make a signature type.
   */
  public static Expr.Signature makeSignature(Name[] names, Expr[] types) {
    sort(names, types, 0, names.length - 1);
    return (Expr.Signature) Type.makeSignature(names, types);
  }

  // PORT NOTE -- a Hoare-partition quicksort, unstable, kept exactly and not replaced by
  // Arrays.sort: equal keys do occur (a binding with a duplicated name, which is reported
  // but still enters the signature), and their relative order -- hence which type ends up
  // first -- is whatever this algorithm produces.
  private static void sort(Name[] names, Expr[] types, int fst, int lst) {
    if (fst >= lst) return;
    String pivot = names[fst].getRepr();
    int i = fst - 1;
    int j = lst + 1;
    while (i < j) {
      while (pivot.compareTo(names[++i].getRepr()) > 0);
      while (pivot.compareTo(names[--j].getRepr()) < 0);
      if (i < j) {
        Name tname = names[i];
        Expr ttype = types[i];
        names[i] = names[j];
        types[i] = types[j];
        names[j] = tname;
        types[j] = ttype;
      }
    }
    sort(names, types, fst, j);
    sort(names, types, j + 1, lst);
  }

  /**
   * The type used for denotations.
   */
  public static Expr denotationType =
    makeGiven(ZNames.denotation);



  // UNIFICATION

  /**
   * Unify the two types. Produce diagnostics if unification fails.
   */
  public void unify(Locator loc, Expr type1, Expr type2) {
    try {
      Type.unify(type1, type2);
    }
    catch (Type.UnificationFailure e) {
      addDiag(loc,
              Diag.Category.Error,
              Report.typeIncompat(type1, type2, e));
    }
  }

}
