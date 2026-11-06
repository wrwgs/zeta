package zeta.tools.nzap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.NoSuchElementException;


import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.nmz.MZ;
import zeta.content.nmz.MZConstants;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZirpContentTypes;
import zeta.form.ChoiceForm;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.OptionForm;
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
import zeta.tools.nmzreducer.Reducer;
import zeta.tools.util.ExprForm;
import zeta.tools.util.PredicateForm;
import zeta.tools.util.SessionUtil;
import zeta.util.Name;

/** The ZAP compiler
  *
  * @version $Id: ZapAdaptor.pizza,v 1.30 2000/07/26 16:00:38 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>Loaded reflectively by name from <code>lib/zetarc</code>, so the class name and the
  * public no-argument constructor are ABI.  Field declaration order (and so form
  * construction order) is the jar's; <code>noOfSteps</code>, declared further down in
  * the source, is also last in the jar.</p>
  *
  * <p><b>Form actions.</b>  The reference bodies of <code>e</code>, <code>p</code> and
  * <code>xprof</code> catch <code>Failures.FormFailed</code> over their whole extent and
  * rethrow it in a <code>pizza.support.ExceptionWrapper</code>, which
  * <code>TopLevelForm.perform</code> unwrapped.  The actions are now
  * <code>TopLevelForm.Action</code> lambdas, which declare <code>FormFailed</code>, so
  * <code>performExpr</code>/<code>performPred</code> throw it directly.</p>
  *
  * <p><b>Mutable capture.</b>  The closure over <code>env.getParents()</code> in
  * <code>exec</code> assigned to <code>errors</code>, <code>obj</code> and
  * <code>imports</code>; it is a loop over the array in order, and its
  * <code>return</code> is the loop's <code>continue</code> -- every parent is still
  * visited, so the diagnostics of later parents are still produced after an earlier one
  * failed.</p>
  *
  * <p><b>Result display.</b>  {@link Solution#makeFormat}, the duplicate suppression in
  * <code>getSolutions</code> (a <code>zeta.agl.HashSet</code> used only for membership;
  * <code>elems</code> keeps first-occurrence order) and the <code>"..."</code> truncation
  * rules are transcribed unchanged; the animation oracle pins them.
  * <code>getSolution</code>/<code>getSolutions</code> returned a Pizza
  * <code>Pair&lt;...,long&gt;</code>; they are private, and return a private record
  * <code>Res</code> instead (value, steps).</p>
  *
  * <p><code>Solution</code> is sealed (abstract base, final cases; package-private as in
  * the jar).  Tags <code>Ground</code> 1, <code>Compound</code> 2, from
  * <code>docs/adt-tags.txt</code>.  Pizza's exhaustive <code>switch (this)</code> in
  * <code>equals</code>/<code>hashCode</code>/<code>makeFormat</code> is now an exhaustive
  * pattern switch.</p>
  *
  * <p>PORT CHANGE (cancellation; see <code>zeta.session.ComputationInterrupted</code>).
  * The reference was cancelled by <code>Thread.stop</code>; <code>execNext</code> caught
  * <code>ThreadDeath</code> to report "interrupted execution" and rethrow, and each native
  * <code>step</code> ran inside <code>synchronized (Thread.currentThread())</code> so that the
  * shell's <code>killEvaluation</code>, which locked the thread before stopping it, could not
  * stop it inside native code.  Now the two animation loops -- <code>execNext</code> and
  * <code>getSolutions</code>, the only places that call <code>step</code> -- check for an
  * interrupt before every call.  A step is at most 500000 ZAM steps
  * (<code>noOfSteps</code>, <code>enumAtOnce</code>), and the native ZAM cannot be
  * interrupted inside one, so that is the latency.  The self-lock is gone: an interrupt only
  * sets a flag.  <code>execNext</code> catches <code>ComputationInterrupted</code> where it
  * caught <code>ThreadDeath</code>, with the same diagnostic, and rethrows it to the shell,
  * which reports "computation interrupted" as before.</p>
  *
  * <p>The ZAM is left consistent, which <code>Thread.stop</code> did not do: enumerating a
  * result pushes temporary sub-targets (<code>setSetTarget</code>,
  * <code>setSeqTarget</code>, <code>setTermTarget</code>) and pops each with
  * <code>retSubTarget</code>; when an interrupt unwinds through one of those regions, its
  * handler pops the sub-target before rethrowing.  So after an interrupt the ZAM's target is
  * again <code>targets.head()</code>, and <code>e -n</code> continues the interrupted search
  * of that target.  A new <code>e</code> restarts the ZAM anyway
  * (<code>ZamIface.start</code> builds a fresh heap and interpreter).  Nothing changes for a
  * run that is not interrupted.</p>
  *
  * <p>PORT NOTE: the external-process <code>zam</code> command (<code>execute</code>,
  * <code>execForm</code>, with its <code>rr.stop(); dr.stop();</code> in a
  * <code>finally</code>) is already commented out in the 1.5 source and absent from the
  * jar; <code>ZamProcIface</code> never shipped.  The comment is carried over verbatim
  * at the end of this file.</p>
  *
  * <p>The lifted <code>$closure$</code> methods, <code>access$000</code> and the two
  * <code>$$closures</code> classes are gone; nothing outside this package names any
  * <code>zeta.tools.nzap</code> class.  Class literals in <code>toArray(...)</code> name
  * <code>Target</code> and <code>String</code>, neither of which registers a slot.</p>
  */
public class ZapAdaptor extends DefaultAdaptor {


  // CONSTANTS

  /**
   * Whether we should issue timing messages.
   */
  private static final boolean timing =
    System.getProperty("zeta.debug.timing") != null;


  // FIELDS

  /**
   * Mapping of unit names to temporary file names used for their
   * external storage.
   */
  Map<Name, File> tempFiles = new HashMap<Name, File>();



  // ADAPTOR INITIALIZATION

  /**
   * Return the name of this adaptor.
   */
  public Name getName() {
    return ZapUtil.toolName;
  }

  /**
   * Initialize a newly created instance of this adaptor.
   */
  public void initialize(Session session, StorageHandle handle) {
    super.initialize(session, handle);
    session.registerContentType(ZapUtil.zapUnit);
    session.registerForm(exprForm);
    session.registerForm(predForm);
    session.registerForm(profileForm);
    session.registerForm(configForm);
    ZamIface.impl.setSession(session);
    try {
      ZamIface.impl.init();
      sendProgress("installed ZAP/ZAM v" + ZapUtil.version);
    }
    catch (ZamIface.Fail e) {
    }
  }


  // ADAPTOR METHODS

  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{ ZapUtil.zapUnit };
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


    Set<Target> deps = new HashSet<Target>();

    // query MZ of this unit
    MZ.UnitDef unit = (MZ.UnitDef) spec.unit.query(MZConstants.mzContentType);
    UnitEnv env = (UnitEnv) spec.unit.query(ZirpContentTypes.checkedEnv);
    deps = deps.include(new Target(spec.unit, MZConstants.mzContentType))
               .include(new Target(spec.unit, ZirpContentTypes.checkedEnv));

    // compile
    ZapUtil.readProperties();
    Date definedAt = new Date();
    sendProgress("compiling " + spec.unit.getName());
    UnitContext uctx = new UnitContext(env, unit.name, unit.parents);
    if (!uctx.hasNatives()) {
      sendDiag(new Diag(ZapUtil.toolLocator,
                        Diag.Category.Error,
                        "No suitable toolkit for execution selected.\n" +
                        "Add the directive '%%toolkit \"zap\"'\n" +
                        "to the document preamble"));
      throw new Failures.ComputationFailed(spec);
    }
    SchemaContext sctx = new SchemaContext(uctx,
                                           unit.globals, unit.locals);
    sctx.compile(unit.props);
    ZAM.Unit res = uctx.finish(sctx.finish());
    ZapUtil.debug.send("compilation result", res);

    // write output file
    File outf = getTempFile(spec.unit.getName());
    try {
      OutputStream outs = new BufferedOutputStream(
                                new FileOutputStream(outf)
                              );
      res.write(outs);
      outs.close();
    }
    catch (IOException e) {
      sendDiag(new Diag(ZapUtil.toolLocator,
                        Diag.Category.Error,
                        "cannout open for writing: " + outf));
      throw new Failures.ComputationFailed(spec);
    }

    spec.unit.define(ZapUtil.zapUnit,
                     new ZapUtil.ZapFile(unit.name, outf),
                     false,
                     definedAt,
                     deps.toArray(Target.class),
                     this
                    );
  }

  /** Get temporary file name for ZAM code. */
  private File getTempFile(Name unit) {
    try {
      return tempFiles.get(unit);
    }
    catch (NoSuchElementException e) {
      File f = session.newTempFile(".zam");
      tempFiles = tempFiles.define(unit, f);
      return f;
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


  /** The top-level form. */

  // COMMANDS
  private static final int RAWMODE = 0;
  private static final int TRYMODE = 1;
  private static final int FORCEMODE = 2;
  private static final int EXPRKIND = 0;
  private static final int PREDKIND = 1;
  private static final int BEGINKIND = 3;
  private static final int NEXTKIND = 0;
  private static final int DOWNKIND = 1;
  private static final int UPKIND = 2;
  private static final int NOTIMING = 0;
  private static final int TIMING = 1;


  // PARAMETERS
  private static int enumAtOnce = 500000;
  private static int maxEnumDepth = 3;
  private static int maxEnumSolutions = 25;
  private static long maxEnumSteps = 500000;


  // FORM STATES
  private ChoiceForm.State evalKind = new ChoiceForm.State(EXPRKIND);
  private OptionForm.State exprWithMode = new OptionForm.State(false);
  private ChoiceForm.State exprMode = new ChoiceForm.State(TRYMODE);
  private OptionForm.State exprWithStep = new OptionForm.State(false);
  private ChoiceForm.State exprStep = new ChoiceForm.State(BEGINKIND);
  private ChoiceForm.State execTiming = new ChoiceForm.State(NOTIMING);
  private ExprForm.State exprState =
    new ExprForm.State(UnitForm.defaultState, null, null, null);
  private PredicateForm.State predicateState =
    new PredicateForm.State(UnitForm.defaultState, null, null, null);

  // FORMS
  private Form exprModeForm =
    new ChoiceForm(
          exprMode,
          new LiteralForm("-r").describe("No Enum",
                        "Do not try enumerating sets"),
          new LiteralForm("-t").describe("Try Enum",
                "Partially try enumerating sets (may be unresolvable)"),
          new LiteralForm("-f").describe("Force Enum",
                         "Force enumerating sets (may be unresolvable)")
        ).style(ChoiceForm.Style.RADIOBUTTON).style(Form.Style.HORIZONTAL);

  private Form exprStepForm =
    new ChoiceForm(
          exprStep,
          //      new LiteralForm("!").describe("Begin",
                          //                    "Begin evaluation"),
          new LiteralForm("-n").describe("Next",
                                        "Show next element of result set"),
          new LiteralForm("-d").describe("Down",
                                        "Step through inner result set"),
          new LiteralForm("-o").describe("Up",
                                        "Return to outer result set"),
          new ExprForm(exprState)
                      .style(new Form.Style.View(-1, -1))
                      .style(new Form.Style.PreferredSize(-40, -2))
                      .style(Form.Style.HISTORY)
                      .describe("Enter", "Enter expression")

        ); // style(ChoiceForm.Style.RADIOBUTTON).style(Form.Style.HORIZONTAL);



  private TopLevelForm exprForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setToolIcon("@expr.gif;EX").setModal(false),
      new SeqForm(
            new LiteralForm("e").describe("Execute Expression"),
            new UnitForm().style(Form.Style.INVISIBLE),
            new OptionForm(
                  exprWithMode,
                  exprModeForm
                ),
            exprStepForm
          ),
      "Execute expression",
      (s, l) -> performExpr(l)
    );

  /** The body of the expression form's action. */
  private void performExpr(Locator locator) throws Failures.FormFailed {
    if (!exprWithMode.selected) exprMode.selection = TRYMODE;
    switch (exprMode.selection) {
      case RAWMODE:
        maxEnumDepth = 0;
        maxEnumSteps = 0;
        maxEnumSolutions = 1;
        break;
      case TRYMODE:
        maxEnumDepth = 5;
        maxEnumSteps = 500000L;
        maxEnumSolutions = 20;
        break;
      case FORCEMODE:
        maxEnumDepth = Integer.MAX_VALUE;
        maxEnumSteps = Long.MAX_VALUE;
        maxEnumSolutions = Integer.MAX_VALUE;
        break;
    }
    evalKind.selection = EXPRKIND;
    // if (!exprWithStep.selected) exprStep.selection = BEGINKIND;
    switch (exprStep.selection) {
      case BEGINKIND:
        exec(locator, exprState.unitState.unit, exprState.expr);
        break;
      case NEXTKIND:
        execNext(locator);
        break;
      case DOWNKIND:
        execDown(locator);
        exprStep.selection = NEXTKIND;
        exprStep.notifyChange(null);
        break;
      case UPKIND:
        execUp(locator);
        exprStep.selection = NEXTKIND;
        exprStep.notifyChange(null);
    }
  }

  private TopLevelForm predForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setToolIcon("@pred.gif;PX")
                  .setModal(false),
      new SeqForm(
            new LiteralForm("p").describe("Execute Predicate"),
            new UnitForm().style(Form.Style.INVISIBLE),
            new PredicateForm(predicateState)
                             .style(new Form.Style.View(-1, -1))
                             .style(new Form.Style.PreferredSize(-40, -2))
                             .style(Form.Style.HISTORY)
          ),
      "Execute predicate",
      (s, l) -> performPred(l)
    );

  /** The body of the predicate form's action. */
  private void performPred(Locator locator) throws Failures.FormFailed {
    maxEnumDepth = Integer.MAX_VALUE;
    maxEnumSteps = Long.MAX_VALUE;
    maxEnumSolutions = 1;
    zeta.content.zirp.Expr e =
      new zeta.content.zirp.Expr.Text(
            new zeta.content.zirp.Expr.Decl[0],
            new zeta.content.zirp.Predicate[]{predicateState.pred}
          );
    zeta.content.zirp.Type.put(
      e.an,
      zeta.content.zirp.Type.makePower(
        zeta.content.zirp.Type.makeSignature(
          new Name[0],
          new zeta.content.zirp.Expr[0]
        )
      ));
    Locator.put(e.an, Locator.get(predicateState.pred.an));
    evalKind.selection = PREDKIND;
    exec(locator, predicateState.unitState.unit, e);
  }

  private TopLevelForm configForm =
    TopLevelForm.make(
      TopLevelForm.Style.Config().setModal(false),
      new SeqForm(
            new LiteralForm("xconf").describe("Configure execution"),
            new ChoiceForm(
                  execTiming,
                  new LiteralForm("toff").describe("Disable",
                                                  "Disable execution timing"),
                  new LiteralForm("ton").describe("Enable",
                                                  "Enable execution timing")
                ).describe("Execution Timing")
          ),
      "Configure execution",
      (s, l) -> {}
    );

  private TopLevelForm profileForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setModal(true),
      new LiteralForm("xprof").describe("Profile Last Execution"),
      "Profile last execution",
      (s, l) -> {
        try {
          sendResult(ZamIface.impl.profileInfo());
        }
        catch (ZamIface.Fail e) {
          sendDiag(e.getMessage());
        }
      }
    );


  /** Pseudo unit and result variable. */
  private String subjectVar = "ZAPRESULT";
  private Name subjectUnit = new Name("ZAPSCRATCHUNIT");

  private Seq<String> targets;


  /** Launch the ZAM to execute an expression. */
  private void exec(Locator locator, Unit unit, zeta.content.zirp.Expr expr)
  throws Failures.FormFailed {

    ZapUtil.readProperties();
    ///////////// RUBISH /////////////////////
    // should be done using a scratch session Unit

    // get environment of this unit
    Serializable obj = SessionUtil.query(
                         session,
                         Locator.get(expr.an),
                         new Target(unit, ZirpContentTypes.checkedEnv)
                       );
    if (obj == null) return;
    UnitEnv env = (UnitEnv) obj;

    // trigger compilation of this unit and its imports
    Map<Name, ZapUtil.ZapFile> imports = new HashMap<Name, ZapUtil.ZapFile>();
    obj =
      SessionUtil.query(session, locator,
                        new Target(unit, ZapUtil.zapUnit)
                       );
    if (obj == null) return;
    imports = imports.define(unit.getName(), (ZapUtil.ZapFile) obj);
    boolean errors = false;
    Name[] parents = env.getParents();
    for (int i = 0; i < parents.length; i++) {
      Name parent = parents[i];
      if (ZapUtil.tryBuiltinName(parent) == null) {
        Unit u = SessionUtil.lookupUnit(session, locator, parent);
        if (u == null) {
          errors = true; continue;
        }
        obj =
          SessionUtil.query(session, locator,
                            new Target(u, ZapUtil.zapUnit)
                           );
        if (obj == null) {
          errors = true; continue;
        }
        imports = imports.define(parent, (ZapUtil.ZapFile) obj);
      }
    }
    if (errors) return;

    // Reduce ZIRP to pseudo MZ unit
    Reducer reducer = new Reducer(env);
    reducer.enterContext();
    UnitEnv.ConstantInfo[] constants = env.getConstants();
    Set<Name> globals = new HashSet<Name>();
    for (int i = 0; i < constants.length; i++) {
      UnitEnv.ConstantInfo info = constants[i];
      MZ.Expr.Variable var = reducer.newVar(info.locator, info.name.getRepr());
      globals = globals.include(var.name);
    }
    MZ.Expr.Variable var =
      reducer.newVar(locator, subjectVar); // add our result name
    globals = globals.include(var.name);
    reducer.addProperty(
      new MZ.Property.Equal(
            var,
            reducer.ereduce(expr)
          ).set(locator)
    );
    Diag diag = reducer.getAndClearDiag();
    if (!diag.isEmpty()) {
      sendDiag(diag);
      return;
    }
    MZ.UnitDef def =
      reducer.exitContextWithUnit(subjectUnit, new ArraySeq<Name>(),
                                  ArraySeq.from(globals),
                                  locator);
    ZapUtil.debug.send("scratch unit reduction result", def);

    // Compile pseudo unit
    UnitContext uctx = new UnitContext(env, def.name, def.parents);
    SchemaContext sctx = new SchemaContext(uctx,
                                           def.globals, def.locals);
    sctx.compile(def.props);
    ZAM.Unit res = uctx.finish(sctx.finish());
    ZapUtil.debug.send("scratch unit compilation result", res);
    File outf = getTempFile(subjectUnit);
    try {
      OutputStream outs = new BufferedOutputStream(
                                new FileOutputStream(outf)
                              );
      res.write(outs);
      outs.close();
    }
    catch (IOException e) {
      sendDiag("cannot open for writing: " + outf);
      return;
    }

    // Start ZAM
    String[] fnames =
      ArraySeq.from(imports)
              .map((Pair<Name, ZapUtil.ZapFile> p) -> p.snd.unitFile.getPath())
              .append(outf.getPath())
              .toArray(String.class);
    try {
      ZamIface.impl.start(fnames);
      // enter execution loop
      targets = new BufferSeq<String>().append(subjectVar);
      execNext(locator);
    }
    catch (ZamIface.Fail e) {
      sendDiag(e.getMessage());
      return;
    }
  }

  /** Number of steps to request. */
  private int noOfSteps = 500000;

  /** Execution loop. */
  private void execNext(Locator locator) {
    try {
      long steps = 0;
      int solutions = 0;
      long start = System.currentTimeMillis();
    loop:
      while (true) {
        // PORT CHANGE: checkpoint; the self-lock that served Thread.stop is gone (class notes).
        ComputationInterrupted.checkpoint();
        int res = ZamIface.impl.step(noOfSteps);
        steps = ZamIface.impl.noOfSteps();
        switch (res) {
          case ZamIface.MORE:
            sendProgress("still searching after " + (steps / 1000) + " k-steps ");
            continue;
          case ZamIface.NOMORE:
            sendResult("no (more) solutions");
            break loop;
          case ZamIface.UNDEF:
            sendResult("*unknown*");
            break loop;
          case ZamIface.SOLUTION:
            /*
            if (execTiming.selection == TIMING){
              sendResult("(receipt after "
                         + (System.currentTimeMillis()-start) + "ms)");
            }
            */
            Res<Solution> s = getSolution(targets.head(), 0, steps);
            steps = s.steps();
            sendResult(s.value().makeFormat());
            if (ZamIface.impl.possiblyMore()) {
              sendResult("(possibly more solutions)");
            }
            break loop;
          default:
            sendDiag("unexpected output of ZAM");
            break loop;
        }
      }
      if (execTiming.selection == TIMING) {
        double t = (double) (System.currentTimeMillis() - start) / (double) 1000;
        sendResult("(" + t + " seconds, " + steps + " steps, " +
                   (int) ((double) steps / t) + " steps/sec)");
      }
    }
    catch (ZamIface.Fail e) {
      sendDiag(e.getMessage());
      return;
    }
    catch (ComputationInterrupted e) {
      // PORT CHANGE: was catch (ThreadDeath e); same report, same rethrow (class notes).
      sendDiag("interrupted execution");
      throw e;
    }
  }

  /** Pizza's Pair&lt;A,long&gt; result of getSolution/getSolutions. */
  private record Res<A>(A value, long steps) { }

  abstract static sealed class Solution
      permits Solution.Ground, Solution.Compound {

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$tools$nzap$ZapAdaptor$Solution$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_GROUND = 1;
    private static final int TAG_COMPOUND = 2;

    Solution(int tag) {
      this.zeta$tools$nzap$ZapAdaptor$Solution$$tag = tag;
    }

    static final class Ground extends Solution {
      String repr;
      Ground(String repr) {
        super(TAG_GROUND);
        this.repr = repr;
      }
    }

    static final class Compound extends Solution {
      String pattern;
      Seq<Solution> elems;
      Compound(String pattern, Seq<Solution> elems) {
        super(TAG_COMPOUND);
        this.pattern = pattern;
        this.elems = elems;
      }
    }

    /** Pizza's generated case factory. */
    static Solution Ground(String repr) {
      return new Ground(repr);
    }

    /** Pizza's generated case factory. */
    static Solution Compound(String pattern, Seq<Solution> elems) {
      return new Compound(pattern, elems);
    }

    public boolean equals(Object other) {
      if (other instanceof Solution osol) {
        switch (this) {
          case Ground g:
            if (osol instanceof Ground g1)
              return g.repr.equals(g1.repr);
            else
              return false;
          case Compound c:
            if (osol instanceof Compound c1)
              return c.pattern.equals(c1.pattern) && c.elems.equals(c1.elems);
            else
              return false;
        }
      } else
        return false;
    }

    public int hashCode() {
      return switch (this) {
        case Ground g -> g.repr.hashCode();
        case Compound c -> (c.pattern.hashCode() + c.elems.hashCode()) * 5;
      };
    }

    public Format makeFormat() {
      switch (this) {
        case Ground g:
          return Format.literal(g.repr);
        case Compound c:
          String pat = c.pattern;
          Seq<Solution> elems = c.elems;
          int i = pat.indexOf('%');
          int s = pat.length();
          if (i >= 0) {
            // list mode
            Format prefix;
            if (i > 0)
              prefix = Format.literal(pat.substring(0, i));
            else
              prefix = Format.empty;
            Format postfix;
            if (i + 1 < s)
              postfix = Format.literal(pat.substring(i + 1));
            else
              postfix = Format.empty;
            return Format.list(prefix, Format.literal(","), postfix,
                               (Solution e) -> e.makeFormat(), elems);
          } else {
            i = splitArg(pat, 0);
            if (i < 0) {
              // constant
              return Format.literal(pat);
            } else {
              // mixfix mode
              Format open;
              Format prefix;
              if (i > 0) {
                String str = pat.substring(0, i);
                if (str.startsWith("<") || str.startsWith("(")) {
                  open = Format.literal(str.substring(0, 1));
                  prefix = Format.literal(str.substring(1));
                } else {
                  prefix = Format.literal(str);
                  open = null;
                }
              } else {
                prefix = null;
                open = null;
              }
              int k = 0;
              Seq<Format> fmts = new BufferSeq<Format>();
              while (i < s) {
                int j = splitArg(pat, i + 1);
                Format delim = null;
                Format newprefix = null;
                if (j >= 0) {
                  if (i + 1 < j) {
                    String str = pat.substring(i + 1, j);
                    int l = str.indexOf(',');
                    if (l >= 0) {
                      delim = Format.literal(str.substring(0, l + 1));
                      if (l + 1 < str.length()) {
                        newprefix = Format.literal(str.substring(l + 1));
                      }
                    } else {
                      newprefix = Format.literal(str);
                    }
                  }
                  i = j;
                } else {
                  if (i + 1 < s)
                    delim = Format.literal(pat.substring(i + 1));
                  i = s;
                }
                Format fmt = elems.at(k++).makeFormat();
                if (delim != null) fmt = Format.append(fmt, delim);
                if (prefix != null) fmt = Format.block(prefix, fmt);
                fmts = fmts.append(fmt);
                prefix = newprefix;
              }
              if (open != null)
                return Format.append(open, Format.block(fmts));
              else
                return Format.block(fmts);
            }
          }
      }
    }
  }


  private Res<Solution> getSolution(String target,
                                    int depth,
                                    long steps) throws ZamIface.Fail {
    if (depth >= maxEnumDepth) {
      if (maxEnumDepth == 0)
        return new Res<>(Solution.Ground(ZamIface.impl.getBinding(target)),
                         steps);
      else
        return new Res<>(Solution.Ground("..."), steps);
    } else if (ZamIface.impl.isSet(target)) {
      // enumerate set
      Res<Seq<Solution>> p = getSolutions(target, depth, steps);
      steps = p.steps();
      if (evalKind.selection != PREDKIND) {
        return new Res<>(Solution.Compound("{%}", p.value()), steps);
      } else {
        // not very clean patch for predicates: convert to true/false
        if (p.value().size() == 0) {
          return new Res<>(Solution.Ground("*false*"), steps);
        } else {
          if (p.value().last() instanceof Solution.Ground g) {
            String s = g.repr;
            if (s.equals("*unknown*"))
              return new Res<>(Solution.Ground(s), steps);
            else
              return new Res<>(Solution.Ground("*true*"), steps);
          } else {
            return new Res<>(Solution.Ground("*true*"), steps);
          }
        }
      }
    } else {
      // try sequence
      int size = ZamIface.impl.isSeqGet(target);
      if (size >= 0) {
        Seq<Solution> elems = new BufferSeq<Solution>();
        int solutions = 0;
        for (int i = 0; i < size; i++) {
          if (steps >= maxEnumSteps && solutions > 0 ||
              solutions >= maxEnumSolutions) {
            elems = elems.append(new Solution.Ground("..."));
            break;
          }
          String subTarget = ZamIface.impl.setSeqTarget(target, i);
          Res<Solution> s;
          try {
            s = getSolution(subTarget, depth, steps);
          }
          catch (ComputationInterrupted e) {
            // PORT CHANGE: leave the ZAM at the outer target (class notes).
            retSubTargetUnwinding();
            throw e;
          }
          steps = s.steps();
          elems = elems.append(s.value());
          ZamIface.impl.retSubTarget();
          solutions++;
        }
        return new Res<>(Solution.Compound("<%>", elems), steps);
      } else {
        // try term
        String name = ZamIface.impl.isTermGet(target);
        if (name != null) {
          int arity = arityByName(name);
          Seq<Solution> elems = new BufferSeq<Solution>();
          for (int i = 0; i < arity; i++) {
            /*
            if (steps >= maxEnumSteps){
              elems = elems.append(new Solution.Ground("..."));
              continue;
            }
            */
            String subTarget = ZamIface.impl.setTermTarget(target, i);
            Res<Solution> s;
            try {
              s = getSolution(subTarget, depth, steps);
            }
            catch (ComputationInterrupted e) {
              // PORT CHANGE: leave the ZAM at the outer target (class notes).
              retSubTargetUnwinding();
              throw e;
            }
            steps = s.steps();
            elems = elems.append(s.value());
            ZamIface.impl.retSubTarget();
          }
          return new Res<>(Solution.Compound(name, elems), steps);
        } else {
          // return raw binding
          return new Res<>(Solution.Ground(ZamIface.impl.getBinding(target)),
                           steps);
        }
      }
    }
  }

  private int arityByName(String s) {
    int o = 0;
    int i = splitArg(s, 0);
    while (i >= 0) {
      o++;
      i = splitArg(s, i + 1);
    }
    return o;
  }

  private static int splitArg(String s, int i) {
    int j = s.indexOf('_', i);
    if (j >= 0) {
      if (j < s.length() - 1) {
        char c = s.charAt(j + 1);
        if (Character.isDigit(c) || c == '{') {
          // index stroke _n, _{...}
          return splitArg(s, j + 1);
        }
      }
    }
    return j;
  }


  private Res<Seq<Solution>> getSolutions(
                                    String target,
                                    int depth,
                                    long steps) throws ZamIface.Fail {
    String subTarget = ZamIface.impl.setSetTarget(target);
    try {
      return enumerateSet(subTarget, depth, steps);
    }
    catch (ComputationInterrupted e) {
      // PORT CHANGE: leave the ZAM at the outer target (class notes).
      retSubTargetUnwinding();
      throw e;
    }
  }

  /** The enumeration loop of <code>getSolutions</code>, run with the ZAM's target set to
    * the elements of the set; split off so that an interrupt can pop that target (PORT
    * CHANGE, class notes).  Every normal exit pops it itself, as before. */
  private Res<Seq<Solution>> enumerateSet(String subTarget, int depth, long steps)
  throws ZamIface.Fail {
    Seq<Solution> elems = new BufferSeq<Solution>();
    Set<Solution> hash = new HashSet<Solution>(); // for duplicates
    int solutions = 0;
    while (true) {
      // PORT CHANGE: checkpoint; the self-lock that served Thread.stop is gone (class notes).
      ComputationInterrupted.checkpoint();
      int res = ZamIface.impl.step(enumAtOnce);
      steps = ZamIface.impl.noOfSteps();
      switch (res) {
        case ZamIface.MORE:
          if (steps < maxEnumSteps || solutions == 0) {
            sendProgress("still searching after " + (steps / 1000) + " k-steps");
            continue;
          }
          ZamIface.impl.retSubTarget();
          elems = elems.append(
                    new Solution.Ground("...")
                                // ZamIface.impl.getBinding(target))
                  );
          return new Res<>(elems, steps);
        case ZamIface.NOMORE:
          ZamIface.impl.retSubTarget();
          return new Res<>(elems, steps);
        case ZamIface.UNDEF:
          ZamIface.impl.retSubTarget();
          elems = elems.append(
                    new Solution.Ground("*unknown*")
                  );
          return new Res<>(elems, maxEnumSteps);
        case ZamIface.SOLUTION:
          Res<Solution> s = getSolution(subTarget, depth + 1, steps);
          steps = s.steps();
          if (!hash.contains(s.value())) {
            elems = elems.append(s.value());
            hash = hash.include(s.value());
          }
          solutions++;
          if (solutions < maxEnumSolutions) continue;
          ZamIface.impl.retSubTarget();
          elems = elems.append(
                    new Solution.Ground("...")
                  );
          return new Res<>(elems, steps);
        default:
          throw new ZamIface.Fail("unexpected output of ZAM");
      }
    }
  }

  /** Pop a sub-target while an interrupt unwinds (PORT CHANGE, class notes).  A ZAM
    * failure here is reported, and the interrupt continues to unwind. */
  private void retSubTargetUnwinding() {
    try {
      ZamIface.impl.retSubTarget();
    }
    catch (ZamIface.Fail f) {
      sendDiag(f.getMessage());
    }
  }

  private void execDown(Locator locator) {
    try {
      if (ZamIface.impl.isSet(targets.head())) {
        targets = targets.prepend(
                    ZamIface.impl.setSetTarget(targets.head())
                  );
        execNext(locator);
      } else {
        sendDiag("target is not a set");
      }
    }
    catch (ZamIface.Fail e) {
      sendDiag(e.getMessage());
      return;
    }
  }

  private void execUp(Locator locator) {
    try {
      if (targets.size() > 1) {
        ZamIface.impl.retSubTarget();
        targets = targets.tail();
        // execNext(locator);
      } else {
        sendDiag("no outer target to return to");
      }
    }
    catch (ZamIface.Fail e) {
      sendDiag(e.getMessage());
      return;
    }
  }


}

/*
   PORT NOTE: the remainder is the 1.5 source's trailing comment, carried over
   verbatim (tabs expanded).  It is the dead external-process `zam` command:
   `rr.stop(); dr.stop();` in its `finally` would be Thread.stop, which JDK 20+
   no longer supports -- irrelevant while it stays a comment, as it was in 2002.
*/

/*



  private void execute(Unit unit, Locator locator)
  throws Failures.FormFailed {
    Object res = SessionUtil.query(session,
                                   locator,
                                   new Target(UnitForm.defaultState.unit,
                                              ZapUtil.zapUnit));
    if (res == null){
      throw new Failures.FormFailed("Exec");
    } else {
      ZapUtil.ZapFile file = (ZapUtil.ZapFile)res;
      Process p = null;
      Thread rr = null;
      Thread dr = null;
      try {
        rr = new ReaderMonitor(
                          new BufferedReader(
                                new InputStreamReader(
                                      new BufferedInputStream(
                                            p.getInputStream()))),
                          fun (String s)->void {
                            sendResult(s);
                          }
                        );
        try {
          rr.start();
          dr.start();
          p.waitFor();
          rr.join();
          dr.join();
        }
        finally {
          rr.stop();
          dr.stop();
          p.destroy();
        }
      }
      catch (Exception e){
        sendDiag(new Diag(ZapUtil.toolLocator,
                          Diag.Category.Error,
                          Format.literal("execution aborted: " + e)));
        throw new Failures.FormFailed("Exec");
      }
    }
  }

  private final TopLevelForm execForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setModal(false),
      new SeqForm(
            new LiteralForm("zam").describe("Call ZAM Machine"),
            new UnitForm()
          ),
      "Call ZAM Machine",
      fun (Session session, Locator locator) throws Failures.FormFailed
                                                            -> void {
        execute(UnitForm.defaultState.unit, locator);
      }
    );


}


*/
