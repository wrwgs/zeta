package zeta.tools.zchecker;

import zeta.agl.ArraySeq;
import zeta.agl.Enum;
import zeta.agl.HashSet;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.zirp.DeclAttr;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Item;
import zeta.content.zirp.MSZNames;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.NameDecl;
import zeta.content.zirp.Predicate;
import zeta.content.zirp.State;
import zeta.content.zirp.TermVisitor;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.Used;
import zeta.format.Format;
import zeta.session.ComputationInterrupted;
import zeta.session.DebugMessage;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.util.Annotation;
import zeta.util.FatalError;
import zeta.util.Name;

/**
 * A class for type checking units.
 *
 * @version $Id: UnitChecker.pizza,v 1.3 1998/09/13 15:24:11 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p><b>The local class.</b>  <code>propagateLocator</code> declared a local class
 * <code>PutLocatorVisitor</code>, which Pizza named <code>UnitChecker$0$PutLocatorVisitor</code>;
 * javac cannot produce that name for a local class (it would say
 * <code>UnitChecker$1PutLocatorVisitor</code>).  The class is referenced only from
 * <code>UnitChecker</code> itself (checked against the constant pools of all reference class
 * files; the only other mention is a comment in the ported <code>TermVisitor</code>), so its
 * name is free.  It became the private static nested class
 * <code>UnitChecker$PutLocatorVisitor</code>, with the captured locator as an explicit
 * constructor argument -- exactly what the local class compiled to
 * (<code>val$loc</code>).  It overrides only <code>visit(Annotation)</code> and so inherits
 * <code>TermVisitor</code>'s quirks unchanged (see its PORT NOTEs); nothing is compensated
 * here.</p>
 *
 * <p><b>Matching.</b>  The <code>DeclAttr</code> switch in <code>deriveItems</code> dispatches
 * on the tag, as Pizza did (<code>Data</code>, <code>Port</code>, <code>Init</code>,
 * <code>Property</code> are constants, and an attribute read from a deserialised absy is not
 * identical to them).  Term cases are matched by type.</p>
 *
 * <p><b>Closures.</b>  The three <code>apply</code> folds in <code>makeSchema</code> and
 * <code>makeType</code> fill an array through a captured index; they became loops over
 * <code>enumerate()</code>.  The lifted <code>$closure$</code> statics and
 * <code>UnitChecker$$closures</code> are gone.</p>
 *
 * <p>One 1998 defect is preserved, marked PORT NOTE at its site.</p>
 *
 * <p>PORT CHANGE (cancellation): the worker threads' <code>ThreadDeath</code> handler is
 * replaced by one for <code>zeta.session.ComputationInterrupted</code>; the protocol is in
 * {@link Env}'s class comment.</p>
 */
public class UnitChecker {

  // DeclAttr tags (ABI; read from the reference with harness/adt_tags.py).
  private static final int A_DATA = 2;
  private static final int A_PORT = 3;
  private static final int A_INIT = 4;
  private static final int A_PROPERTY = 5;
  private static final int A_ALIAS = 6;

  // METHODS

  /**
   * Check and annotate the given section. Return diagnostics
   * produced by checking. The given environment must be setup
   * to contain definitions for all parent units. <p>
   *
   * If checking succeeds (the category Error is not contained
   * in the returned diagnostics) then the following can be asserted:
   *
   * <UL>
   * <LI> the unit is type-correct
   * <LI> the items in the unit are sorted definition-before-use
   * <LI> all expressions have a type annotation
   * <LI> all name applications of generic names have an actualization
   *      annotation
   * <LI> the passed unit environment is enriched by the constant
   *      declarations of this unit
   * </UL>
   */
  public static Diag checkSection(Session session,
                                  UnitEnv unitEnv, UnitAbsy absy) {
    if (absy instanceof UnitAbsy.Section sec) {
      Item[] items = sec.items;
      Env env = Env.sectionEnv(unitEnv);
      for (int i = 0; i < items.length; i++) {
        CheckerThread checker = new CheckerThread(session, env, items[i]);
        env.registerChecker(checker, intendedDefs(items[i]));
      }
      env.runCheckers();
      return env.getAndClearDiag();
    } else
      throw FatalError.unexpectedCase("expected a section");
  }

  /**
   * Check and annotate the given class. Like the section checker,
   * but takes in addition an array of environments of the classes
   * which are enriched by this class. Furthermore, the given
   * abstract syntax is enriched by implicite definitions
   * of types and schemas belonging to this class.
   */
  public static Diag checkClass(Session session,
                                UnitEnv unitEnv, UnitEnv[] enrichedEnvs,
                                UnitAbsy absy) {
    if (absy instanceof UnitAbsy.ClassDef cdef) {
      Item[] items = cdef.items;
      Env env = Env.classEnv(unitEnv, enrichedEnvs, cdef.formals);
      cdef.items = items =
        new ArraySeq<Item>(items)
                    .appendAll(deriveItems(unitEnv, enrichedEnvs, cdef))
                    .toArray(Item.class);
      for (int i = 0; i < items.length; i++) {
        CheckerThread checker = new CheckerThread(session, env, items[i]);
        env.registerChecker(checker, intendedDefs(items[i]));
      }
      env.runCheckers();
      return env.getAndClearDiag();
    } else
      throw FatalError.unexpectedCase("expected a class");
  }


  /**
   * Thread class for checking items in parallel.
   */
  private static class CheckerThread extends Thread {

    private Session session;
    private Env env;
    private Item item;

    CheckerThread(Session session, Env env, Item item) {
      this.session = session;
      this.env = env;
      this.item = item;
    }

    public void run() {
      try {
        // run the term checker
        new TermChecker(env.newLocal()).check(item);
      }
      catch (Exception e) {
        session.send(new Name("ZCHECKER"),
                     new DebugMessage(Format.build(Locator.get(item.an))));
        session.abort(e);
        e.printStackTrace();
      }
      catch (ComputationInterrupted e) {
        // PORT CHANGE (cancellation): was catch (ThreadDeath e) { throw e; }, which kept a
        // stopped thread away from the catch (Error) below.  This checker is unwinding
        // because Env.runCheckers has cancelled the check; the master owns the interrupt
        // and reports it (Env class comment), so the thread just ends -- through the
        // finally, which counts it out.  Rethrowing would only print it on stderr.
      }
      catch (Error e) {
        session.send(new Name("ZCHECKER"),
                     new DebugMessage(Format.build(Locator.get(item.an))));
        session.abort(e);
        e.printStackTrace();
      }
      finally {
        // PORT CHANGE: the original unregistered first and wrote `Used` afterwards, outside
        // any monitor.  `unregisterChecker` hands control on -- to the next checker, or, for
        // the last one, by notifying the master in `runCheckers` -- so the master could return,
        // and the caller read the checked absy, before this write had happened.  Under CPU
        // load `ReducerAdaptor` then found `Used == null` on an item and died with an NPE
        // (the 1998 comment there, "STRANGE BUG IN JDK 1.2 / LINUX -- solved calling gc above",
        // is this race; the gc merely gave the late write time to land).  Traced in the port:
        // the reducer's read came 30-190 ms after the master returned, the checker's write
        // a few ms later still.
        //
        // Doing both under `env`'s monitor closes it.  The master leaves `wait()` in
        // `runCheckers` only by reacquiring that monitor, which the last checker holds until
        // its write is done; and every earlier checker's write precedes its own release of
        // the monitor, which precedes the next unregistration (only one checker runs at a
        // time, and each unregistration needs the monitor).  So every `Used` write
        // happens-before `runCheckers` returns.  Nothing else changes: the hand-off order,
        // and with it the order of diagnostics, is decided inside `unregisterChecker` as
        // before; the next checker can at most block briefly at its first `env` call.
        synchronized (env) {
          Set<Name> usage = env.unregisterChecker();
          Used.put(item.an, usage.toArray(Name.class));
        }
      }
    }

  }

  /**
   * Calculate the approximated intended definitions of an item.
   * This is an approximation because of arbitrary schema
   * expressions in axiomatic definitions, but helps to
   * detect deadlocks of checker threads.
   */
  private static Set<Name> intendedDefs(Item item) {
    if (item instanceof Item.AxiomaticDef a) {
      Expr schema = a.def;
      if (schema instanceof Expr.Text text) {
        Expr.Decl[] decls = text.decls;
        Set<Name> names = new HashSet<>();
        for (int i = 0; i < decls.length; i++) {
          if (decls[i] instanceof Expr.Decl.Direct d) {
            NameDecl[] nds = d.names;
            for (int j = 0; j < nds.length; j++) {
              names = names.include(nds[j].name);
            }
          } else if (decls[i] instanceof Expr.Decl.Eqn eq) {
            names = names.include(eq.name.name);
          } else {
            // Inclusion: cannot decide
          }
        }
        return names;
      } else if (schema instanceof Expr.GivenType gt) {
        return new HashSet<Name>().include(gt.name.name);
      } else if (schema instanceof Expr.FreeType ft) {
        Expr.Branch[] branches = ft.branches;
        Set<Name> names = new HashSet<Name>().include(ft.name.name);
        for (int i = 0; i < branches.length; i++) {
          if (branches[i] instanceof Expr.Branch.Constant c) {
            names = names.include(c.name.name);
          } else if (branches[i] instanceof Expr.Branch.Function f) {
            names = names.include(f.name.name);
          } else {
            // Pizza: `new Error()`.
            throw FatalError.unexpectedCase("free type branch");
          }
        }
        return names;
      } else {
        // nothing known
        return new HashSet<>();
      }
    } else {
      return new HashSet<>();
    }
  }




  // MSZ: BUILDING DERIVED ITEMS

  /**
   * Construct the derived items of a class, and define
   * mSZ class-related information in environment.
   */
  private static Seq<Item> deriveItems(UnitEnv env,
                                       UnitEnv[] enrichedEnvs,
                                       UnitAbsy.ClassDef absy) {

    Set<Name> datas = new HashSet<>();
    Set<Name> ports = new HashSet<>();
    Set<Name> inits = new HashSet<>();
    Set<Name> props = new HashSet<>();
    Set<Name> states = new HashSet<>();

    Item[] items = absy.items;

    // visit items to sample information
    for (int i = 0; i < items.length; i++) {
      Item item = items[i];
      if (item instanceof Item.AxiomaticDef a) {
        if (a.def instanceof Expr.Text text) {
          Expr.Decl[] decls = text.decls;
          for (int j = 0; j < decls.length; j++) {
            if (decls[j] instanceof Expr.Decl.Eqn eq) {
              NameDecl ndec = eq.name;
              DeclAttr attr = DeclAttr.get(ndec.an);
              if (attr != null) {
                // PORT NOTE -- 1998 defect, preserved.  The source wraps this switch in
                // `do { ... case Alias(DeclAttr baseAttr, _): attr = baseAttr; continue; }
                // while (false)`, meant to retry with the alias's base attribute; but
                // `continue` in a do-while jumps to the (false) condition, so an aliased
                // declaration is never classified at all.  Confirmed in the 2002 class file
                // (the Alias arm stores baseAttr and leaves the loop).  It decides which
                // names enter the derived \DATA/\PORT/\INIT/\PROPERTY schemas, i.e. the
                // checked absy and env of every class using aliased attributes; kept.
                switch (attr.zeta$content$zirp$DeclAttr$$tag) {
                case A_DATA:
                  datas = datas.include(ndec.name);
                  break;
                case A_PORT:
                  ports = ports.include(ndec.name);
                  break;
                case A_INIT:
                  inits = inits.include(ndec.name);
                  break;
                case A_PROPERTY:
                  props = props.include(ndec.name);
                  break;
                case A_ALIAS:
                  attr = ((DeclAttr.Alias) attr).baseAttr;
                  break;
                default:
                  break;
                }
              }
            }
          }
        }
      } else if (item instanceof Item.StatechartDef sc) {
        states = states.includeAll(sampleStates(sc.root));
      } else if (item instanceof Item.EnrichDef ed) {
        NameAppl nap = ed.name;
        for (int j = 0; j < enrichedEnvs.length; j++) {
          if (enrichedEnvs[j].getName().equals(nap.name)) {
            datas = datas.includeAll(
                      new ArraySeq<>(enrichedEnvs[j].getDataSchemas())
                    );
            ports = ports.includeAll(
                      new ArraySeq<>(enrichedEnvs[j].getPortSchemas())
                    );
            inits = inits.includeAll(
                      new ArraySeq<>(enrichedEnvs[j].getInitSchemas())
                    );
            props = props.includeAll(
                      new ArraySeq<>(enrichedEnvs[j].getPropertySchemas())
                    );
            states = states.includeAll(
                       new ArraySeq<>(enrichedEnvs[j].getStateNames())
                     );
          }
        }
        // if we didn't find an environment, then this is
        // reported as an error elsewhere
      }
    }

    // set class information in environment
    env.setDataSchemas(datas.toArray(Name.class));
    env.setPortSchemas(ports.toArray(Name.class));
    env.setInitSchemas(inits.toArray(Name.class));
    env.setPropertySchemas(props.toArray(Name.class));
    env.setStateNames(states.toArray(Name.class));

    // build the derived items
    Locator loc = env.getLocator();
    /* FIXME: what to do here?
      new FreeLocator(
            env.getLocator().toString() + "[implicite declarations]"
          );
    */

    Item stateType =
      makeType(
        loc,
        MSZNames.stateType,
        states
      );
    Item controlDef =
      makeSchema(
        loc,
        MSZNames.controlData,
        DeclAttr.Data,
        new HashSet<Name>().include(MSZNames.controlDefaultData)
      );
    Item historyDef =
      makeSchema(
        loc,
        MSZNames.historyData,
        DeclAttr.Data,
        new HashSet<Name>().include(MSZNames.historyDefaultData)
      );
    Item portDef =
      makeSchema(
        loc,
        MSZNames.classPort,
        DeclAttr.Port,
        ports
      );
    Item dataDef =
      makeSchema(
        loc,
        MSZNames.classData,
        DeclAttr.Data,
        datas.include(MSZNames.classPort)
             .include(MSZNames.controlData)
             .include(MSZNames.historyData)
      );
    Item propDef =
      makeSchema(
        loc,
        MSZNames.classProperty,
        DeclAttr.Property,
        new HashSet<Name>().include(MSZNames.classData),
        props
      );
    Item initDef =
      makeSchema(
        loc,
        MSZNames.classInit,
        DeclAttr.Init,
        new HashSet<Name>().include(MSZNames.classData),
        inits
      );
    return new ArraySeq<Item>(new Item[]{ stateType, controlDef, historyDef,
                                          portDef, dataDef, propDef, initDef });
  }

  /**
   * Sample declared state names.
   */
  private static Set<Name> sampleStates(State state) {
    if (state instanceof State.BasicState b) {
      return new HashSet<Name>().include(b.name.name);
    } else if (state instanceof State.RefState) {
      return new HashSet<>();
    } else if (state instanceof State.SuperState s) {
      // CHECKME: do connectors count? Supposedly not.
      State[] substates = s.substates;
      Set<Name> states = new HashSet<Name>().include(s.name.name);
      for (int i = 0; i < substates.length; i++) {
        states = states.includeAll(sampleStates(substates[i]));
      }
      return states;
    } else
      // Pizza: `new Error()`.
      throw FatalError.unexpectedCase("state");
  }

  /**
   * Make a schema definition.
   */
  private static Item makeSchema(Locator loc, Name name, DeclAttr attr,
                                 Set<Name> included) {
    return makeSchema(loc, name, attr, included, new HashSet<>());
  }

  /**
   * Make a schema definition.
   */
  private static Item makeSchema(Locator loc, Name name, DeclAttr attr,
                                 Set<Name> included, Set<Name> referred) {
    Expr.Decl[] decls = new Expr.Decl[included.size()];
    int i = 0;
    for (Enum<Name> e = included.enumerate(); e.more(); e.advance()) {
      decls[i] = Expr.Decl.Inclusion(makeVar(e.current()));
      i++;
    }
    Predicate[] props = new Predicate[referred.size()];
    i = 0;
    for (Enum<Name> e = referred.enumerate(); e.more(); e.advance()) {
      props[i] = Predicate.Schema(makeVar(e.current()));
      i++;
    }
    NameDecl nd = new NameDecl(name);
    Locator.put(nd.an, loc);
    if (attr != null) DeclAttr.put(nd.an, attr);
    Item def =
      Item.AxiomaticDef(
        new NameDecl[0],
        Expr.Text(
          new Expr.Decl[]{Expr.Decl.Eqn(nd, Expr.Text(decls, props))},
          new Predicate[0]
        )
      );
    propagateLocator(loc, def);
    return def;
  }

  /**
   * Make a variable application.
   */
  private static Expr makeVar(Name name) {
    return new Expr.Variable(new NameAppl(name, new Expr[0]));
  }

  /**
   * Make a free type definition.
   */
  private static Item makeType(Locator loc, Name name, Set<Name> constants) {
    Item def;
    NameDecl nd = new NameDecl(name);
    Locator.put(nd.an, loc);
    if (constants.size() == 0) {
      def =
        Item.AxiomaticDef(
          new NameDecl[0],
          Expr.GivenType(nd)
        );
    } else {
      Expr.Branch[] branches = new Expr.Branch[constants.size()];
      int i = 0;
      for (Enum<Name> e = constants.enumerate(); e.more(); e.advance()) {
        NameDecl d = new NameDecl(e.current());
        Locator.put(d.an, loc);
        branches[i] = Expr.Branch.Constant(d);
        i++;
      }
      def =
        Item.AxiomaticDef(
          new NameDecl[0],
          Expr.FreeType(nd, branches)
        );
    }
    propagateLocator(loc, def);
    return def;
  }


  /**
   * Propgate locator annotation throughout term.
   */
  // FIXME: move to library
  // PROBLEM: TermVisitor doesn't visits NameDecl (and it shouldn't,
  // because of efficiency)
  private static void propagateLocator(final Locator loc, Item item) {
    new PutLocatorVisitor(loc).visit(item);
  }

  /** Was the local class of propagateLocator; see the port notes. */
  private static class PutLocatorVisitor extends TermVisitor {
    private final Locator loc;

    PutLocatorVisitor(Locator loc) {
      this.loc = loc;
    }

    public void visit(Annotation an) {
      Locator.put(an, loc);
    }
  }

}
