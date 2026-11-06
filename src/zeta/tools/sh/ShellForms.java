package zeta.tools.sh;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;


import zeta.agl.ArraySeq;
import zeta.form.ChoiceForm;
import zeta.form.ContentTypeForm;
import zeta.form.FileForm;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.OptionForm;
import zeta.form.SeqForm;
import zeta.form.StringForm;
import zeta.form.TopLevelForm;
import zeta.form.UnitForm;
import zeta.format.Format;
import zeta.session.ContentType;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.Target;
import zeta.session.Unit;

/**
 * Basic forms provided by the shell.
 *
 * @version $Id: ShellForms.pizza,v 1.14 2000/07/23 07:10:30 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>The order in which {@link #registerPre} and {@link #registerPost} register their forms is
 * unchanged: it is the order of the {@code help} listing in every session transcript.
 *
 * <p>Form actions are {@code TopLevelForm.Action} lambdas that throw
 * {@code Failures.FormFailed} directly; Pizza delivered it inside a
 * {@code pizza.support.ExceptionWrapper} (see {@code TopLevelForm}).  The lifted
 * {@code $closure$} methods, the
 * {@code ShellForms$$closures} class and {@code ShellForms$$anonymous_class2} (the extern-file
 * {@code StringForm.State} of {@code evalState}, an ordinary anonymous class here) had no
 * referrers outside this package.
 *
 * <p>The source imported {@code zeta.content.graphics.*}, a package that is not in the archive
 * (nor in the jar); javac rejects an import of a missing package, so it is dropped.  Its only
 * use was the commented-out {@code msl-preview-gif} form, kept commented out below.
 *
 * <p>{@code quit} calls {@code session.abort()}, which prints {@code good bye.} and exits;
 * the saving variant is {@code squit} ({@code session.exit()}).  See zesurrect-plan.md 6.1.
 *
 * <p>The actions read the blank final {@code state} through {@link #shellState()}: the forms
 * are field initialisers, which run before the constructor assigns {@code state}, and javac's
 * definite-assignment check rejects {@code state} (and {@code this.state}) inside a lambda
 * there.  The actions only run after construction, exactly as the Pizza closures did, so
 * they see the assigned field.  The field keeps its {@code private final} shape.
 */
public class ShellForms {

  /*
   * The shell state
   */
  private final ShellState state;

  /**
   * Make a new object providing shell forms.
   */
  private ShellForms(ShellState state) {
    this.state = state;
  }

  /** The shell state, for the form actions (see the class comment). */
  private ShellState shellState() {
    return state;
  }

  /**
   * Register shell forms BEFORE other forms.
   */
  static void registerPre(ShellState state) {
    ShellForms forms = new ShellForms(state);
    state.session.registerForm(forms.evalForm);
    state.session.registerForm(forms.makeForm);
    state.session.registerForm(forms.pluginForm);

  }

  /**
   * Register shell forms AFTER other forms.
   */
  static void registerPost(ShellState state) {
    ShellForms forms = new ShellForms(state);
    state.session.registerForm(forms.interruptForm);
    state.session.registerForm(forms.quitForm);
    // hidden in GUI:
    state.session.registerForm(forms.saveAndQuitForm);
    state.session.registerForm(forms.helpForm);
    state.session.registerForm(forms.listUnitsForm);
    state.session.registerForm(forms.listTypesForm);
    state.session.registerForm(forms.setPropForm);
    // state.session.registerForm(forms.previewForm);
  }

  // EXITING

  private TopLevelForm quitForm =
    TopLevelForm.make(
      TopLevelForm.Style.Shell(),
      new LiteralForm("quit").describe("Quit"),
      "Quit ZETA Commander",
      (Session s, Locator l) -> {
        shellState().session.abort();
      }
    );

  private TopLevelForm saveAndQuitForm =
    TopLevelForm.make(
      TopLevelForm.Style.HIDDEN,
      new LiteralForm("squit").describe("Save & Quit (EXPERIMENTAL)"),
      "Save & Quit ZETA Commander",
      (Session s, Locator l) -> {
        shellState().sendProgress(
          "exiting, please wait while storing project data ...");
        shellState().session.exit();
      }
    );

  // INTERRUPTING

  private TopLevelForm interruptForm =
    TopLevelForm.make(
      TopLevelForm.Style.Shell().setToolIcon("@interrupt.gif;IR"),
      new LiteralForm("interrupt")
                     .describe("Interrupt Computation"),
      "Interrupt Computation",
      (Session s, Locator l) -> {
        shellState().killEvaluation();
      }
    );

  // MAKING

  private OptionForm.State dumpOption = new OptionForm.State(false);
  private OptionForm.State dumpFileOption = new OptionForm.State(true);
  private FileForm.State dumpFile = new FileForm.State(new File("zeta.dump"));

  private TopLevelForm makeForm =
    TopLevelForm.make(
      // TopLevelForm.Style.MenuAt(new String[]{"Unit"}),
      TopLevelForm.Style.Function().setModal(false),
      new SeqForm(
            new LiteralForm("make").describe("Make Content"),
            new UnitForm().style(Form.Style.INVISIBLE),
            new ContentTypeForm(),
            new OptionForm(dumpOption,
                           new SeqForm(
                             new LiteralForm("-d"),
                             new OptionForm(dumpFileOption,
                                            new FileForm(FileForm.Mode.PLAIN,
                                                         dumpFile))
                                           .style(new Form.Style.Border(
                                                        "Output File"))
                               )
                          )
                          // .style(new Form.Style.Dialog("dump ...",
                         //                            "Dump Contents"))
          ).style(Form.Style.HORIZONTAL),
      "Dump Content of Unit",
      (Session s, Locator locator) -> {
        Unit unit = UnitForm.defaultState.unit;
        ContentType type = ContentTypeForm.defaultState.type;
        Format fmt;
        try {
          Object ob = unit.query(type);
          shellState().sendProgress("formatting " + new Target(unit, type));
          fmt = Format.buildFormat(ob);
        }
        catch (Failures.ComputationFailed e) {
          // diagnostics will have been printed
          throw new Failures.FormFailed("Dump");
        }
        catch (Failures.CannotCompute e) {
          // e.printStackTrace();
          shellState().sendDiag(
            new Diag(
                  ShellState.defaultLocator,
                  Diag.Category.Error,
                  Format.describe(
                    "query not resovable",
                    "target", Format.buildFormat(e.target)
                  )
                )
          );
          throw new Failures.FormFailed("Dump");
        }
        if (dumpOption.selected) {
          if (dumpFileOption.selected) {
            try {
              PrintWriter writer =
                new PrintWriter(new FileWriter(dumpFile.file));
              fmt.printFormat(writer, 75);
              writer.close();
            }
            catch (IOException e) {
              shellState().sendDiag(new Diag(locator,
                                      Diag.Category.Error,
                                      Format.literal("could not open file `" +
                                                     dumpFile.file.getName() + "'")));
              throw new Failures.FormFailed("Dump");
            }
          } else
            shellState().sendResult(fmt);
        }
      }
    );

  // PREVIEW GIF

  /*
  private UnitForm.State previewUnit = new UnitForm.State(null);
  private TopLevelForm previewForm =
    TopLevelForm.make(
      TopLevelForm.Style.HIDDEN,
      new SeqForm(
            new LiteralForm("msl-preview-gif"),
            new UnitForm(previewUnit)
          ),
      "Result a file name containing a gif preview",
      fun (Session s, Locator locator) throws Failures.FormFailed -> void {
        if (previewUnit.unit != null){
          File f = (File)SessionUtil.query(s, locator,
                                           new Target(previewUnit.unit,
                                                      GraphicsContentTypes
                                                      .gif));
          if (f != null){
            state.sendResult(Format.literal(f.getAbsolutePath()));
          } else
            throw new Failures.FormFailed("Preview");
        } else {
          state.sendDiag(locator,
                         "unit required");
          throw new Failures.FormFailed("Preview");
        }
      }
    );
    */


  // GENERIC EVALUATION

  private StringForm.State evalState =
    new StringForm.State("") {
    public File getExternFile(Session s) {
      return new File("_ZETA_Command_");
    }
  };

  private TopLevelForm evalForm =
    TopLevelForm.make(
      TopLevelForm.Style.Shell().setModal(false).setToolIcon("@com.gif;CI"),
      new SeqForm(
            new LiteralForm("command").describe("Command Input"),
            new StringForm(evalState)
                          .style(new Form.Style.View(-1, -1))
                          .style(new Form.Style.PreferredSize(-40, -2))
                          .style(Form.Style.HISTORY)
                          .describe(null,
                                    "A text to be evaluated by the ZETA shell")

          ),
      "Evaluate the given command",
      (Session s, Locator locator) -> {
        String[] words = shellState().interpreter.tokenize(locator, evalState.string);
        shellState().doEval(new ChoiceForm(s.getForms()), locator, words);
      }
    );


  // LOADING PLUGINS

  private StringForm.State adaptorName = new StringForm.State("");

  private TopLevelForm pluginForm =
    TopLevelForm.make(
      TopLevelForm.Style.Shell().setModal(false),
      new SeqForm(
            new LiteralForm("plugin").describe("Plugin Adaptor"),
            new StringForm(adaptorName)
                          .style(new Form.Style.PreferredSize(-40, -1))
                          .style(Form.Style.FIXSIZE)
                          .describe("Class Name",
                                    "Class name of the adaptor to plugin")
          ),
      "Plugin a tool adaptor specified by its qualified class name",
      s -> {
        // adaptorName.string = "";
      },
      (Session s, Locator locator) -> {
        if (adaptorName.string.length() > 0) {
          try {
            shellState().session.registerAdaptor(Class.forName(adaptorName.string));
          } catch (IllegalArgumentException e) {
            shellState().sendDiag(ShellState.defaultLocator,
                           "cannot load class `" + adaptorName.string + "'");
            throw new Failures.FormFailed("plugin");
          } catch (ClassNotFoundException e) {
            shellState().sendDiag(ShellState.defaultLocator,
                           "cannot load class `" + adaptorName.string + "'");
            throw new Failures.FormFailed("plugin");
          } catch (Failures.EntityExists e) {
            shellState().sendDiag(ShellState.defaultLocator,
                           "already installed `" + adaptorName.string + "'");
            throw new Failures.FormFailed("plugin");
          }
        }
      }
    );

  // HELP
  private TopLevelForm helpForm =
    TopLevelForm.make(
      TopLevelForm.Style.HIDDEN,
      new LiteralForm("help"),
      "Print command syntax",
      (Session s, Locator locator) -> {
        shellState().sendResult(
          Format.beneath(
            new ArraySeq<TopLevelForm>(shellState().session.getForms()).map(
                  (TopLevelForm f) -> Format.literal(f.getSyntax())
                )
          )
        );
      }
    );

  // LIST

  private TopLevelForm listUnitsForm =
    TopLevelForm.make(
      TopLevelForm.Style.HIDDEN,
      new SeqForm(new LiteralForm("list"), new LiteralForm("units")),
      "List known unit names",
      (Session session, Locator locator) -> {
        shellState().sendResult(
          Format.list(
            Format.empty,
            Format.literal(","),
            Format.empty,
            (Unit u) -> Format.literal(u.getName().toString()),
            new ArraySeq<Unit>(session.getUnits())
          )
        );
      }
    );

  private TopLevelForm listTypesForm =
    TopLevelForm.make(
      TopLevelForm.Style.HIDDEN,
      new SeqForm(new LiteralForm("list"), new LiteralForm("types")),
      "List known content type names",
      (Session session, Locator locator) -> {
        shellState().sendResult(
          Format.list(
            Format.empty,
            Format.literal(","),
            Format.empty,
            (ContentType t) -> Format.literal(t.toString()),
            new ArraySeq<ContentType>(session.getContentTypes())
          )
        );
      }
    );


  // SET PROPERTY

  private StringForm.State propState = new StringForm.State("");
  private StringForm.State valState = new StringForm.State("");
  private TopLevelForm setPropForm =
    TopLevelForm.make(
      TopLevelForm.Style.HIDDEN,
      new SeqForm(new LiteralForm("set"),
                  new StringForm(propState)
                                .style(new Form.Style.PreferredSize(-16, -1))
                                .style(Form.Style.HISTORY),
                  new StringForm(valState)
                                .style(new Form.Style.PreferredSize(-16, -3))
                                .style(Form.Style.HISTORY)
                 ),
      "Set Java property",
      (Session s, Locator locator) -> {
        // looks dirty:
        System.getProperties().put(propState.string, valState.string);
      }
    );

}
