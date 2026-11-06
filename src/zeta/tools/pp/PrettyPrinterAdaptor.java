package zeta.tools.pp;

/** The Pretty Printer.
  *
  * @version $Id: PrettyPrinterAdaptor.pizza,v 1.8 2000/05/16 08:10:47 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>{@code ToolLocator} is a single-case public static nested class (no {@code $$tag}):
  * one public field {@code msg} (String), constructor, and factory with the same
  * signature, as the jar requires.  The source declares it in a slightly unusual order
  * (field initialiser before the class definition), which is harmless in Java because
  * the class is public static and the field reference is from the outer class's own
  * constructor.</p>
  *
  * <p>The prettyForm closure originally written as
  * {@code fun (Session session, Locator locator) throws Failures.FormFailed -> void}
  * is a {@code TopLevelForm.Action} lambda, which declares
  * {@link zeta.session.Failures.FormFailed} and throws it directly.</p>
  *
  * <p>The Pizza-generated {@code $closure$...} method is not replicated; nothing outside
  * this class references it ({@code command grep} confirmed).</p>
  */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import zeta.agl.HashSet;
import zeta.agl.Set;
import zeta.content.text.TextContentTypes;
import zeta.content.zirp.ZirpContentTypes;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.form.FileForm;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.OptionForm;
import zeta.form.SeqForm;
import zeta.form.TopLevelForm;
import zeta.form.UnitForm;
import zeta.session.ContentType;
import zeta.session.DefaultAdaptor;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.ProgressMessage;
import zeta.session.ResultMessage;
import zeta.session.Session;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.tools.latex.LatexConfig;
import zeta.tools.util.SessionUtil;
import zeta.util.Name;


public class PrettyPrinterAdaptor extends DefaultAdaptor {


  // CONSTANTS

  /**
   * The startup message of this adaptor.
   */
  private static final String startupMessage =
    "installed pretty printer v1.0";

  /**
   * Whether we should issue timing messages.
   */
  private static final boolean timing =
    System.getProperty("zeta.debug.timing") != null;

  /**
   * The name of this adaptor.
   */
  private static final Name toolName = new Name("PP");

  /**
   * A locator associated with this adaptor.
   */
  private Locator toolLocator = new ToolLocator("<pretty-printer>");

  public static class ToolLocator extends Locator {
    public String msg;

    public ToolLocator(String msg) {
      this.msg = msg;
    }

    /** Static factory (ABI: matches jar descriptor). */
    public static ToolLocator ToolLocator(String msg) {
      return new ToolLocator(msg);
    }

    public Format toFormat(FormatInfo info) {
      return Format.literal(msg);
    }

    public Name getTool() {
      return toolName;
    }
  }


  // ADAPTOR INITIALIZATION

  /**
   * Return the name of this adaptor.
   */
  public Name getName() {
    return toolName;
  }

  /**
   * Initialize a newly created instance of this adaptor.
   */
  public void initialize(Session session, StorageHandle handle) {
    super.initialize(session, handle);
    this.session = session;
    sendProgress(startupMessage);
    session.registerContentType(TextContentTypes.prettyText);
    session.registerForm(prettyForm);
  }


  // ADAPTOR METHODS

  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{ TextContentTypes.prettyText };
  }

  /** Always throws. */
  public Unit lookupUnit(Name name)
  throws Failures.UnresolvableName {
    throw new Failures.UnresolvableName(name);
  }

  /**
   * Run the adaptor to compute the specified content.
   */
  public void tryCompute(Target target)
  throws Failures.ComputationFailed, Failures.CannotCompute {

    Set<Target> deps = new HashSet();

    // query def and env of this unit
    UnitAbsy absy =
      (UnitAbsy) target.unit.query(ZirpContentTypes.uncheckedAbsy);
    UnitEnv env =
      (UnitEnv) target.unit.query(ZirpContentTypes.uncheckedEnv);
    deps =
      deps.include(new Target(target.unit, ZirpContentTypes.uncheckedAbsy))
          .include(new Target(target.unit, ZirpContentTypes.uncheckedEnv));

    // rewrite
    Date definedAt = new Date();
    Format text = new PrettyPrinter(new LatexConfig(), env).pretty(absy);
    target.unit.define(target.type,
                       text,
                       false,
                       definedAt,
                       deps.toArray(Target.class),
                       this);
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

  // COMMANDS

  private final OptionForm.State fileOption = new OptionForm.State(false);
  private final FileForm.State prettyFile =
    new FileForm.State(new File("zeta.pp"));
  private final TopLevelForm prettyForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setModal(true),
      new SeqForm(
        new LiteralForm("pp").describe("Print Z Syntax"),
        new UnitForm().style(Form.Style.INVISIBLE),
        new OptionForm(fileOption,
                       new FileForm(FileForm.Mode.PLAIN, prettyFile))
                      .describe("Output to File")
      ),
      "Pretty-Print Z-Syntax",
      (_session, _locator) -> {
        Object res =
          SessionUtil.query(_session,
                            _locator,
                            new Target(UnitForm.defaultState.unit,
                                       TextContentTypes.prettyText));
        if (res == null) {
          // errors occured (which have already been reported)
          throw new Failures.FormFailed("TypeCheck");
        }
        _session.send(toolName, new ProgressMessage(
                        Format.literal("formatting")));
        Format fmt = (Format) res;
        if (fileOption.selected) {
          try {
            PrintWriter writer =
              new PrintWriter(new FileWriter(prettyFile.file));
            fmt.printFormat(writer, 75);
            writer.close();
          }
          catch (IOException e) {
            _session.send(toolName,
                          new Diag(_locator,
                                   Diag.Category.Error,
                                   Format.literal("could not open file `" +
                                     prettyFile.file.getName() + "'")));
            throw new Failures.FormFailed("PrettyPrint");
          }
        } else {
          _session.send(toolName, new ResultMessage(fmt));
        }
      }
    );

}
