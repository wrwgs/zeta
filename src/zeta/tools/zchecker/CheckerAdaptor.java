package zeta.tools.zchecker;

import java.util.Date;


import zeta.agl.BufferSeq;
import zeta.agl.Fun;
import zeta.agl.HashSet;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.zirp.Item;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZirpContentTypes;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.SeqForm;
import zeta.form.TopLevelForm;
import zeta.form.UnitForm;
import zeta.format.Format;
import zeta.session.ComputationInterrupted;
import zeta.session.ContentType;
import zeta.session.DefaultAdaptor;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.tools.util.SessionUtil;
import zeta.util.FatalError;
import zeta.util.Name;

/** The Z/DZ/mSZ checker adaptor.
  *
  * @version $Id: CheckerAdaptor.pizza,v 1.19 2000/07/21 06:45:15 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p><b>The form action.</b>  The reference body (<code>$closure$...$2d</code>) caught
  * <code>Failures.FormFailed</code> over its whole extent and rethrew it wrapped in
  * <code>pizza.support.ExceptionWrapper</code>, for <code>TopLevelForm.perform</code> to
  * unwrap.  <code>TopLevelForm.Action</code> declares <code>FormFailed</code>, so
  * <code>performCheck</code> now throws it directly.</p>
  *
  * <p><b>Closures.</b>  <code>e.map(Fun.fst)</code> / <code>e.map(Fun.snd)</code> were lifted
  * into two static closures that call <code>zeta.agl.Fun.fst</code>/<code>snd</code> and cast
  * the result; they are lambdas doing the same.  The lifted <code>$closure$</code> methods and
  * <code>CheckerAdaptor$$closures</code> are gone; nothing outside this class referenced
  * them.</p>
  *
  * <p><code>toArray(UnitEnv.class)</code> and <code>toArray(Target.class)</code> were
  * <code>Class.forName</code>; neither class registers a slot.</p>
  *
  * <p>PORT CHANGE (cancellation; see <code>zeta.session.ComputationInterrupted</code>).  The
  * checker annotates the <em>unchecked</em> absy in place -- it is the parser's content, not
  * a copy (only the env is copied) -- and <code>checkClass</code> also appends the derived
  * items to it.  A check that is interrupted therefore leaves the parser's content
  * half-annotated and, for a class, carrying derived items that a re-check would append a
  * second time.  So when an interrupt unwinds out of the check, <code>tryCompute</code>
  * outdates <code>uncheckedAbsy</code> before rethrowing; the next query re-parses the unit
  * and the re-check starts from a fresh absy.  Neither checked content is defined, as the
  * exception leaves before the <code>define</code>s.  (A check that <em>fails</em> leaves
  * the absy in the same state; that is the 1998 behaviour and is not changed here.)</p>
  */
public class CheckerAdaptor extends DefaultAdaptor {


  // CONSTANTS

  /*
   * The name of this adaptor.
   */
  private static final Name thisName = new Name("ZCHECKER");


  /*
   * The startup message of this adaptor.
   */
  private static final String startupMessage =
    "installed Z type checker v1.5";

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
    return thisName;
  }

  /**
   * Initialize a newly created instance of this adaptor.
   */
  public void initialize(Session session, StorageHandle handle) {
    super.initialize(session, handle);
    sendProgress(startupMessage);
    session.registerContentType(ZirpContentTypes.uncheckedAbsy);
    session.registerContentType(ZirpContentTypes.uncheckedEnv);
    session.registerContentType(ZirpContentTypes.checkedAbsy);
    session.registerContentType(ZirpContentTypes.checkedEnv);
    session.registerForm(checkForm);
  }


  // ADAPTOR METHODS

  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{ ZirpContentTypes.checkedAbsy,
                              ZirpContentTypes.checkedEnv   };
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

    Set<Target> deps = new HashSet<>();

    // query unchecked absy and env of this unit
    UnitAbsy absy = (UnitAbsy) spec.unit.query(ZirpContentTypes.uncheckedAbsy);
    UnitEnv env = (UnitEnv) spec.unit.query(ZirpContentTypes.uncheckedEnv);
    deps = deps.include(new Target(spec.unit,
                                   ZirpContentTypes.uncheckedAbsy))
               .include(new Target(spec.unit,
                                   ZirpContentTypes.uncheckedEnv));

    // make a copy of the environment, to avoid side-effects if we
    // are failing. FIXME: this is to expansive for the new guessing mode,
    // we should first try if we can query all dependencies, then enrich
    // the environment
    env = env.copy();

    // query for environments of parent units and enriched classes,
    // and type check
    Diag diag;
    if (absy instanceof UnitAbsy.Section sec) {
      deps = deps.includeAll(importSections(Locator.get(absy.an),
                                            env,
                                            sec.parents));
      long start = 0;
      if (timing)
        start = System.currentTimeMillis();
      sendProgress("type checking section " + spec.unit.getName());
      try {
        diag = UnitChecker.checkSection(session, env, absy);
      }
      catch (ComputationInterrupted e) {
        // PORT CHANGE (cancellation): see the class comment.
        spec.unit.outdate(ZirpContentTypes.uncheckedAbsy);
        throw e;
      }
      if (timing)
        sendDebug("type checking " +
                  (System.currentTimeMillis() - start) + "ms");
    } else if (absy instanceof UnitAbsy.ClassDef cdef) {
      deps = deps.includeAll(importSections(Locator.get(absy.an),
                                            env,
                                            cdef.parents));
      long start = 0;
      if (timing)
        start = System.currentTimeMillis();
      Seq<Pair<Target, UnitEnv>> e =
        getEnrichments(Locator.get(absy.an), env, cdef.items);
      deps = deps.includeAll(e.map(p -> Fun.fst(p)));
      sendProgress("type checking class " + spec.unit.getName());
      try {
        diag = UnitChecker.checkClass(session, env,
                                      e.map(p -> Fun.snd(p))
                                       .toArray(UnitEnv.class),
                                      absy);
      }
      catch (ComputationInterrupted ci) {
        // PORT CHANGE (cancellation): see the class comment.
        spec.unit.outdate(ZirpContentTypes.uncheckedAbsy);
        throw ci;
      }
      if (timing)
        sendDebug("type checking " +
                  (System.currentTimeMillis() - start) + "ms");
    } else
      // Pizza: `new Error()` for a case not handled by the switch.
      throw FatalError.unexpectedCase("unit abstract syntax");

    // define the result
    if (!diag.isEmpty()) sendDiag(diag);
    if (diag.contains(Diag.Category.Error)) {
      // computation failed
      throw new Failures.ComputationFailed(spec);
    } else {
      // define checked absy, and link unchecked absy to checked one
      Date definedAt = new Date();
      spec.unit.define(ZirpContentTypes.checkedAbsy,
                       absy,
                       true,
                       definedAt,
                       deps.toArray(Target.class),
                       this
                      );
      // spec.unit.link(ZirpContentTypes.checkedAbsy,
      //                ZirpContentTypes.uncheckedAbsy);
      // mark environment to be final,
      // define checked env, and link unchecked env to checked one
      spec.unit.define(ZirpContentTypes.checkedEnv,
                       env,
                       true,
                       definedAt,
                       deps.toArray(Target.class),
                       this
                      );
      // spec.unit.link(ZirpContentTypes.checkedEnv,
      //             ZirpContentTypes.uncheckedEnv);
    }
  }

  /**
   * Import the given parent sections.
   */
  private Set<Target> importSections(Locator loc,
                                     UnitEnv env, Name[] parents)
  throws Failures.CannotCompute, Failures.ComputationFailed {
    Set<Target> deps = new HashSet<>();

    for (int i = 0; i < parents.length; i++) {
      try {
        Unit imp = session.lookupUnit(parents[i]);
        UnitEnv impEnv = (UnitEnv) imp.query(ZirpContentTypes.checkedEnv);
        if (impEnv.isClass()) {
          env.addDiag(new Diag(loc,
                               Diag.Category.Error,
                               "`" + impEnv.getName() +
                               "' must be a section but is a class"));
        } else {
          env.importConstantsFrom(impEnv);
          deps = deps.include(new Target(imp,
                                         ZirpContentTypes.checkedEnv));
        }
      }
      catch (Failures.UnresolvableName e) {
        reportUnresolvable(env, loc, parents[i]);
      }
      catch (Failures.AmbigiousName e) {
        reportAmbigious(env, loc, parents[i], e.fullNames);
      }
    }
    return deps;
  }

  /**
   * Get enrichments of class.
   */
  private Seq<Pair<Target, UnitEnv>> getEnrichments(Locator loc, UnitEnv env, Item[] items)
  throws Failures.CannotCompute, Failures.ComputationFailed {
    Seq<Pair<Target, UnitEnv>> enrichment = new BufferSeq<>();
    for (int i = 0; i < items.length; i++) {
      if (items[i] instanceof Item.EnrichDef ed) {
        NameAppl nap = ed.name;
        Locator eloc = Locator.get(nap.an);
        try {
          Unit enriched = session.lookupUnit(nap.name);
          UnitEnv enrichedEnv =
            (UnitEnv) enriched.query(ZirpContentTypes.checkedEnv);
          if (!enrichedEnv.isClass()) {
            env.addDiag(new Diag(eloc,
                                 Diag.Category.Error,
                                 "`" + enrichedEnv.getName() +
                                 "' must be a class but is a section"));
          } else {
            enrichment =
              enrichment.append(
                Pair.Pair(new Target(enriched,
                                     ZirpContentTypes.checkedEnv),
                          enrichedEnv)
              );
          }
        }
        catch (Failures.UnresolvableName e) {
          reportUnresolvable(env, eloc, nap.name);
        }
        catch (Failures.AmbigiousName e) {
          reportAmbigious(env, eloc, nap.name, e.fullNames);
        }
      }
    }
    return enrichment;
  }

  private void reportUnresolvable(UnitEnv env, Locator loc, Name name) {
    env.addDiag(new Diag(loc,
                         Diag.Category.Error,
                         "`" + name + "' cannot be resolved as a unit"));
  }

  private void reportAmbigious(UnitEnv env, Locator loc,
                               Name name, Name[] fullNames) {
    env.addDiag(new Diag(loc,
                         Diag.Category.Error,
                         Format.describe(
                           "`" + name + "' is ambigious",
                           "possible completions",
                           Format.list(Format.empty,
                                       Format.literal(", "),
                                       Format.empty,
                                       (Object[]) fullNames))));
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

  // FORM

  private TopLevelForm checkForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setToolIcon("@tcheck.gif;CK")
                                   .setModal(false),
      new SeqForm(
            new LiteralForm("c").describe("Type Check"),
            new UnitForm().style(Form.Style.INVISIBLE)
          ),
      "Type-check a unit",
      (s, l) -> performCheck(s, l)
    );

  /**
   * The body of the check form's action.
   */
  private void performCheck(Session session, Locator locator) throws Failures.FormFailed {
    if (UnitForm.defaultState.unit == null) {
      session.send(
        locator.getTool(),
        new Diag(locator,
                 Diag.Category.Error,
                 "unit needed for checking")
      );
      throw new Failures.FormFailed("TypeCheck");
    }
    if (SessionUtil.query(session,
                          locator,
                          new Target(UnitForm.defaultState.unit,
                                     ZirpContentTypes.checkedEnv))
       == null) {
      // errors occured (which have already been reported)
      throw new Failures.FormFailed("TypeCheck");
    }
  }


}
