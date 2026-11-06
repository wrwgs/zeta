package zeta.tools.emacs;

/** The Emacs Editor Adaptor.
  *
  * @version $Id: EditorAdaptor.pizza,v 1.2 2000/05/16 08:10:47 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>{@code ToolLocator} appears in the 2002 jar as a public static nested
  * class of {@code EditorAdaptor} but is absent from the recovered Pizza source
  * (a later revision removed it).  The jar is the specification, so it is
  * re-added here, reconstructed from its bytecode: single-case Pizza ADT with
  * one public field {@code msg}, matching constructor and static factory, and
  * the two overriding methods whose implementations are readable from {@code
  * javap -c}.  No external class outside this package references
  * {@code EditorAdaptor$ToolLocator} (confirmed with {@code command grep}).
  * The field {@code toolLocator} present in the sister class
  * {@code PrettyPrinterAdaptor} was also removed from the later source and is
  * likewise absent from the jar; it is not restored here.</p>
  *
  * <p>The {@code commit()} closure
  * {@code fun (String form)->void { buf.append(" "); buf.append(form); }} is
  * translated to a {@code Consumer<String>} lambda passed to
  * {@link zeta.agl.Aggregate#apply(java.util.function.Consumer)}.
  * No mutable-capture issue: {@code buf} is a local {@code StringBuffer}
  * and lambdas may capture it because it is effectively final.</p>
  *
  * <p>The {@code editForm} closure
  * {@code fun (Session session, Locator locator) throws Failures.FormFailed -> void}
  * is a {@code TopLevelForm.Action} lambda, which throws
  * {@link zeta.session.Failures.FormFailed} directly; the 2002 closure wrapped it in
  * {@code pizza.support.ExceptionWrapper} for {@code TopLevelForm} to unwrap.</p>
  *
  * <p>The Pizza-generated {@code $closure$...} methods and the
  * {@code EditorAdaptor$$closures} companion class are dropped: nothing outside
  * this class references them (confirmed with {@code command grep}).</p>
  */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;


import zeta.agl.BufferSeq;
import zeta.agl.Seq;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.SeqForm;
import zeta.form.TopLevelForm;
import zeta.form.UnitForm;
import zeta.session.ContentType;
import zeta.session.DefaultAdaptor;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.TextEditor;
import zeta.session.Unit;
import zeta.util.Name;


public class EditorAdaptor extends DefaultAdaptor implements TextEditor {


  // CONSTANTS

  /**
   * Whether we are running on Windows.
   */
  static boolean onWin = System.getProperty("os.name").startsWith("Win");

  /**
   * The startup message of this adaptor.
   */
  private static final String startupMessage =
    "installed Emacs adaptor v1.0";

  /**
   * The name of this adaptor.
   */
  private static final Name toolName = new Name("Emacs");


  // NESTED CLASS (from 2002 jar; not in later Pizza source)

  /**
   * A locator associated with this adaptor.
   *
   * <p>PORT NOTE: this single-case Pizza ADT ({@code public case ToolLocator(String msg)})
   * was present in the 2002 jar but removed from the later Pizza source.  It is
   * reconstructed here from {@code javap -c} output.  {@code getTool()} returns
   * {@code toolName}; {@code toFormat(FormatInfo)} returns
   * {@code Format.literal(msg)}.  No {@code $$tag} because the case name matches
   * the class name.</p>
   */
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
    sendProgress(startupMessage);
    session.registerForm(editForm);
    session.registerTextEditor(this);
  }


  // ADAPTOR METHODS

  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{};
  }

  /** Always throws. */
  public Unit lookupUnit(Name name)
  throws Failures.UnresolvableName {
    throw new Failures.UnresolvableName(name);
  }

  /**
   * Always throws.
   */
  public void tryCompute(Target target)
  throws Failures.ComputationFailed, Failures.CannotCompute {
    throw new Failures.CannotCompute(target);
  }

  /** Always returns true. */
  public boolean isUptodate(Target spec, Date definedAt) {
    return true;
  }

  /** Always returns false. */
  public boolean notifyOutdate(Target spec, Date definedAt,
                               StorageHandle handle) {
    return false;
  }

  /** Does call saveRelated in the editor. */
  public void checkExternalData() {
    if (editorInUse) {
      saveRelated();
      try {
        commit();
      }
      catch (IOException e) {
        System.err.println(e);
      }
    }
  }

  /** Does nothing. */
  public void flush() {
  }


  // EDITOR METHODS

  /**
   * Whether we have used the editor.
   */
  boolean editorInUse = false;

  /**
   * A sequence of accumulated forms.
   */
  Seq<String> forms = new BufferSeq<String>();


  /** Return button name. */
  public String getButtonName() {
    return "@xemacs.gif;XE";
  }

  /**
   * Add the given visit intention for a subsequent commit.
   * For Emacs, we just accumulate the information.
   */
  public void add(Locator.VisitCapability.TextVisit cap) {
    forms = forms.append(
              "(zeta-server-register " + locatorRepr(cap) + ")"
            );
  }

  /**
   * Visit the given locator.
   */
  public void visit(Locator.VisitCapability.TextVisit cap) {
    forms = forms.append(
              "(zeta-server-visit " + locatorRepr(cap) + ")");
    editorInUse = true;
  }

  /**
   * Reset the added and committed visiting intentions.
   */
  public void reset() {
    if (editorInUse) {
      forms = forms.append("(zeta-server-clear)");
    }
  }

  /**
   * Open the given file for editing.
   */
  public void open(File file) {
    forms = forms.append(
              "(zeta-server-open " +
              quote("\"" + file.getAbsolutePath() + "\")"));
    editorInUse = true;
  }

  /**
   * Release any file-system locks on this file. Does nothing for Emacs.
   */
  public void unlock(File file) {
  }

  /**
   * Reopen this file for editing. Reopening a file which has not
   * yet been opened should behave as initial open.
   */
  public void reopen(File file) {
    forms = forms.append(
              "(zeta-server-reopen" +
              quote("\"" + file.getAbsolutePath() + "\")"));
    editorInUse = true;
  }

  /**
   * Close editing the given file. Does nothing for Emacs.
   */
  public void close(File file, boolean dontSave) {
  }

  /**
   * Save the given file.
   */
  public void save(File file, boolean silently) {
    forms = forms.append(
              "(zeta-server-save " + quote("\"" + file.getAbsolutePath() + "\"") +
              (silently ? " t)" : ")"));
  }

  /**
   * Save all related files.
   */
  public void saveRelated() {
    if (editorInUse)
      forms = forms.append("(zeta-server-save-related)");
  }

  /**
   * Convert a text cap into the string Emacs understands.
   */
  private String locatorRepr(Locator.VisitCapability.TextVisit cap) {
    return
      quote("\"" + cap.file.getAbsolutePath() + "\"") + " " +
      Integer.toString(cap.firstRow) + " " +
      Integer.toString(cap.firstCol) + " " +
      Integer.toString(cap.lastRow) + " " +
      Integer.toString(cap.lastCol);
  }

  /**
   * Commit the accumulated forms.
   */
  public void commit() throws IOException {
    if (forms.size() > 0) {
      StringBuffer buf = new StringBuffer();
      buf.append("(concat");
      forms.apply((String form) -> {
        buf.append(" ");
        buf.append(form);
      });
      buf.append(")");
      forms = new BufferSeq<String>();
      doit(buf.toString());
    }
  }

  /**
   * Let Emacs evaluate the given form.
   */
  private void doit(String form) throws IOException {
    doit(form, false);
  }

  private void doit(String form, boolean quickly) throws IOException {
    String gnudoit = System.getProperty("zeta.prog.gnudoit");
    if (gnudoit == null) {
      throw new IOException(
                  "Emacs adaptor does not knows how to call `gnudoit'.\n" +
                  "Property `zeta.prog.gnudoit' is not set."
                );
    }
    Process proc;
    // System.err.println("gnulient: " + form);
    proc = Runtime.getRuntime()
                  .exec(quickly ? new String[]{gnudoit, "-q", form}
                                : new String[]{gnudoit, form});
    if (quickly) return;
    String output = readResult(proc.getInputStream()).trim();
    // System.err.println("out: " + output);
    String err = readResult(proc.getErrorStream()).trim();
    // System.err.println("err: " + err);
    try {
      int p = proc.waitFor();
      if (p != 0) {
        throw new IOException(
                    "XEmacs client failed." +
                    // (error code " + p + ")" +
                    // (err.length() > 0 ? ":\n" + err : "") +
                    "\nEnsure that XEmacs is up and running gnuserv."
                  );
      }
    } catch (InterruptedException e) {
      throw new IOException(
                  "Emacs client interrupted: " + e
                );
    }
    if (output.length() > 0 || err.length() > 0) {
      throw new IOException("XEmacs client:\n" + output + "\n" + err);
    }
  }

  private static String readResult(InputStream stream) {
    String res = null;
    try {
      BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream));
      String line;
      while ((line = reader.readLine()) != null) {
        if (res != null)
          res += "\n" + line;
        else
          res = line;
      }
      reader.close();
    }
    catch (IOException e) {
    }
    return res == null ? "" : res;
  }

  private static String quote(String s) {
    StringBuffer res = new StringBuffer();
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      switch (c) {
      case '\\':
        res.append("\\\\");
        break;
      default:
        if (onWin && c == '"')
          res.append("\\\"");
        else
          res.append(c);
      }
    }
    return res.toString();
  }


  // COMMANDS

  private final TopLevelForm editForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setModal(true)
                  .setToolIcon("@xemacs.gif;XE"),
      new SeqForm(
            new LiteralForm("edit").describe("Edit source of unit"),
            new UnitForm().style(Form.Style.INVISIBLE)
          ),
      "Edit source of unit",
      (_session, _locator) -> {
        // FIXME: source of unit into zeta.session.Unit
        Locator l = _session.unitLocator(UnitForm.defaultState.unit);
        if (l != null) {
          Locator.VisitCapability[] caps = l.getVisitCapabilities();
          for (int i = 0; i < caps.length; i++) {
            if (caps[i] instanceof Locator.VisitCapability.TextVisit) {
              visit((Locator.VisitCapability.TextVisit) caps[i]);
              try {
                commit();
                return;
              } catch (IOException e) {
                sendError("could not edit source of unit via`" +
                          l + "':\n" + e.getMessage());
                throw new Failures.FormFailed("Edit");
              }
            }
          }
          sendError("`" + l + "'cannot be visited\nby the attached editor `" +
                    getName() + "'");
          throw new Failures.FormFailed("Edit");
        } else {
          sendError("source of `" +
                    UnitForm.defaultState.unit.getName() +
                    "' cannot be located");
          throw new Failures.FormFailed("Edit");
        }
      }
    );

  // PORT NOTE: the 2002 closure wrapped its Failures.FormFailed in
  // pizza.support.ExceptionWrapper for TopLevelForm to unwrap; a TopLevelForm.Action
  // declares FormFailed, so the action throws it directly (see TopLevelForm).

}
