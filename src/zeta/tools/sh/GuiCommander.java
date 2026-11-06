package zeta.tools.sh;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;


import zeta.agl.ArraySeq;
import zeta.form.ChoiceForm;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.OptionForm;
import zeta.form.SeqForm;
import zeta.form.StringForm;
import zeta.form.TopLevelForm;
import zeta.form.UnitForm;
import zeta.session.Failures;
import zeta.session.Listener;
import zeta.session.Locator;
import zeta.session.Message;
import zeta.session.Session;
import zeta.session.SessionManager;
import zeta.session.Shell;
import zeta.util.DebugWriter;
import zeta.util.Name;
import zeta.util.VerboseWriter;

/**
 * The GUI commander.
 *
 * @version $Id: GuiCommander.pizza,v 1.23 2000/05/18 09:54:57 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Ported by reading: the GUI ({@code zeta -g}, the driver's default) has no test coverage.
 * Look-and-feel handling, layout and listener wiring are as in 1998, including the
 * deprecated {@code show()} and {@code isFocusTraversable()}.  Startup order is unchanged:
 * the window is shown first, then the listener, the {@code ShellForms} pre-forms and
 * {@code gui-config} are registered, the session initialised, config scripts and command-line
 * files evaluated, and only then the post-forms and the shell registered -- so the GUI's form
 * order differs from the batch shell's, as it always did.
 *
 * <p>PORT NOTE ({@link #receive}): the source wrapped {@code SwingUtilities.invokeLater} in
 * handlers for {@code InvocationTargetException} and {@code InterruptedException}, left over
 * from a commented-out {@code invokeAndWait}.  {@code invokeLater} throws neither, so javac
 * rejects the handlers; they are dropped and nothing changes.
 *
 * <p>The {@code switch (form.style)} of {@link #updateView} became a chain of type tests on
 * the {@code TopLevelForm.Style} cases; {@code HIDDEN} (a constant) falls to the ignoring
 * default as before.  Form actions are {@code TopLevelForm.Action} lambdas (see
 * {@link ShellForms}).  The {@code GuiCommander$$closures}
 * class, lifted {@code $closure$} methods and the several stale
 * {@code GuiCommander$NN$$anonymous_classNN} files of the jar (left from earlier compilations)
 * had no referrers outside this package.
 */
public class GuiCommander extends JFrame implements Shell, Listener {

  // CONSTANTS

  // FIELDS

  /**
   * The shell state.
   */
  private ShellState state;

  /**
   * The menu tree
   */
  private FormTree formTree;

  /**
   * The overall panel
   */
  private JPanel panel;

  /**
   * The log panel
   */
  private LogPanel log;

  /**
   * The tool bar panel.
   */
  private JToolBar toolBar;

  /**
   * The last instance of the tool selector.
   */
  private JComponent toolSelector = null;

  // INITIALIAZATION

  public static void main(String[] files) {

    if (System.getProperty("zeta.home") == null) {
      System.err.println(
      "ZETA initialization error: property `zeta.home' not defined.");
      return;
    }

    String project = "ZETA";

    // Set look&feel
    String lookAndFeel = System.getProperty("zeta.gui.laf");
    if (lookAndFeel != null) {
      try {
        UIManager.setLookAndFeel(lookAndFeel);
      } catch (Exception e) {
        System.err.println("couldn't load the requested look & feel `" +
                           lookAndFeel + "'");
        System.exit(1);
      }
    }

    // Open or create session
    Session session;
    boolean created;
    try {
      session = SessionManager.open(project);
      created = false;
    }
    catch (Failures.NoSuchEntity e) {
      session = SessionManager.create(project);
      created = true;
    }

    // Session loaded, from now on catch user interrupts
    // Impure.driver.catchInterrupts();

    try {

      // Setup basic print message receiver
      DebugWriter.setReceiver(session);
      VerboseWriter.setReceiver(session);
      session.registerListener(
        null,
        new MessagePrinter(
              System.getProperty("zeta.debug") != null ?
                new PrintWriter(new OutputStreamWriter(System.err)) : null,
              System.getProperty("zeta.debug.echo") != null ?
                new PrintWriter(new OutputStreamWriter(System.out)) : null
            )
      );

      // Setup shell state
      ShellState state = new ShellState(session);
      state.interpreter = new CommandInterpreter(state);

      GuiOptions.setupDefaults(state);

      // Create gui
      new GuiCommander(files, state, created);
    }
    catch (Exception e) {
      session.abort(e);
    }
  }

  /**
   * Construct a GUI commander
   */
  @SuppressWarnings("deprecation") // show(), as in 1998
  private GuiCommander(String[] files,
                       final ShellState state, boolean created) {

    // initialize commander window
    super("ZETA Commander");
    this.state = state;
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          log.remove();
          state.session.abort();
        }
      }
    );

    log = new LogPanel(state);
    final Container cont = getContentPane();
    cont.setLayout(new BorderLayout());
    cont.add(log, BorderLayout.CENTER);

    /*
    cont.addMouseListener(
      new MouseAdapter(){
        public void mouseClicked(MouseEvent e){
          //  System.err.println("clicked: " + e);
                  // cont.requestFocus();
        }
        public void mouseEntered(MouseEvent e){
                  // System.err.println("entered: " + e);
                  // cont.requestFocus();
        }
      }
    );
    cont.addKeyListener(
      new KeyAdapter(){
        public void keyTyped(KeyEvent e){
          System.err.println("key: " + e);
        }
      }
    );
    */



    pack();
    show();
    state.master = log;

    // initialize session
    state.session.registerListener(null, this);
    ShellForms.registerPre(state);
    state.session.registerForm(guiConfigForm);
    if (true || created) { // FIXME: reading session state
      state.session.initialize();


      // execute startup scripts
      try {
        state.interpreter.readAndEvalConfigScripts();
      }
      catch (Failures.FormFailed e) {}

      // load files
      for (int i = 0; i < files.length; i++) {
        try {
          state.interpreter.readAndEval(
            new ChoiceForm(state.session.getForms()),
            "<parameter line>",
            "load " + files[i]
          );
        } catch (Failures.FormFailed e) {}
      }

    } else {
      state.session.initialize();
      // FIXME: read GUI options
      // ...
    }
    ShellForms.registerPost(state);
    state.session.registerShell(this);
    updateView();
  }


  /**
   * Update the view.
   */
  private void updateView() {
    formTree = FormTree.make();
    new ArraySeq<TopLevelForm>(state.session.getForms()).apply(
          (TopLevelForm form) -> {
            TopLevelForm.Style style = form.style;
            if (style instanceof TopLevelForm.Style.MenuAt m) {
              formTree.add(m.path, form);
            } else if (style instanceof TopLevelForm.Style.Shell) {
              formTree.add(new String[]{"Shell"}, form);
            } else if (style instanceof TopLevelForm.Style.Source) {
              formTree.add(new String[]{"Source"}, form);
            } else if (style instanceof TopLevelForm.Style.Function) {
              formTree.add(new String[]{"Operation"}, form);
            } else if (style instanceof TopLevelForm.Style.Config) {
              formTree.add(new String[]{"Configure"}, form);
            } else {
              // ignore
            }
          }
        );
    JMenuBar menu = formTree.makeMenu(this, state);
    menu.setDoubleBuffered(true);
    setJMenuBar(menu);
    Container cont = getContentPane();
    if (toolBar != null) cont.remove(toolBar);
    toolBar = new JToolBar();
    formTree.addToolBar(this, state, toolBar);
    toolBar.addSeparator();
    addSharedForms(toolBar);
    cont.add(toolBar, BorderLayout.NORTH);
    validate();
  }

  @SuppressWarnings("deprecation")
  public boolean isFocusTraversable() {
    return false;
  }


  /**
   * Add the shared default forms to the tool bar.
   */
  private void addSharedForms(Container where) {
    FormComponents gen = new FormComponents(state);
    where.add(gen.makeComponent(
                new UnitForm())); // .style(Form.Style.MaximumSize(-30,-1))));
  }


  /**
   * Implementation of shell method.
   */
  public void exec(String prompt,
                   TopLevelForm form) throws Failures.FormFailed {
    state.sendDiag(ShellState.defaultLocator, "modal forms NYI");
  }

  /**
   * Implementation of shell method.
   */
  public void notifyFormRegister(TopLevelForm form) {
    updateView();
  }

  /**
   * Implementation of shell method.
   */
  public void notifyFormUnregister(TopLevelForm form) {
    updateView();
  }

  /** Implement session message listener. */
  public void receive(final Name sender, final Message message) {
    if (SwingUtilities.isEventDispatchThread()) {
      // just add it to log
      log.addMessage(message);
    } else {
      // called from a thread, use invoke-later method
      /*
        SwingUtilities.invokeLater(
        new Runnable(){
        public void run(){
        log.addMessage(message);
        }
        }
        );
      */
      // force an update of the GUI
      // PORT NOTE: the unreachable InvocationTargetException / InterruptedException
      // handlers around this call are dropped (class comment).
      SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            log.addMessage(message);
            getToolkit().sync();
          }
        }
      );
      /*
      SwingUtilities.invokeAndWait(
        new Runnable(){
          public void run(){
          }
        }
      );
      */
    }
  }

  // GUI configuration form
  private OptionForm.State logProgressState = new OptionForm.State(true);
  private OptionForm.State collapsePreviousState = new OptionForm.State(true);
  private StringForm.State registerProgState = new StringForm.State("");
  private StringForm.State visitProgState = new StringForm.State("");
  private StringForm.State clearProgState = new StringForm.State("");

  private TopLevelForm guiConfigForm =
    TopLevelForm.make(
      TopLevelForm.Style.Config().setModal(false),
      new SeqForm(
            new LiteralForm("gui-config").describe("Configure Commander"),
            new SeqForm(
                  new OptionForm(logProgressState,
                                 new LiteralForm("-progress")
                                        .describe("Log progress messages")),
                  new OptionForm(collapsePreviousState,
                                 new LiteralForm("-collapse")
                              .describe("Collapse previous transactions"))
                ).style(Form.Style.VERTICAL)            //,
            //      new SeqForm(
                    //    new StringForm(registerProgState)
                    //                  .describe("Register"),
                    //    new StringForm(visitProgState)
                    //                  .describe("Visit"),
                    //    new StringForm(clearProgState)
                    //                  .describe("Clear")
                    //  ).style(Form.Style.VERTICAL)
                    //         .style(new Form.Style.Border(
                    //                      "File locator visiting programs"))
          ).style(Form.Style.VERTICAL),
      "Configure GUI",
      s -> {
        logProgressState.selected = GuiOptions.active.logProgress;
        logProgressState.notifyChange(this);

        collapsePreviousState.selected = GuiOptions.active.collapsePrevious;
        collapsePreviousState.notifyChange(this);

        /*
        registerProgState.setDraftText(
          s,
          GuiOptions.active.registerFileLocatorsProg
        );
        registerProgState.notifyChange(this);

        visitProgState.setDraftText(
          s,
          GuiOptions.active.visitFileLocatorProg
        );
        visitProgState.notifyChange(this);

        clearProgState.setDraftText(
          s,
          GuiOptions.active.clearFileLocatorsProg
        );
        clearProgState.notifyChange(this);
        */
      },
      (Session s, Locator l) -> {
        GuiOptions.active.logProgress = logProgressState.selected;
        GuiOptions.active.collapsePrevious = collapsePreviousState.selected;
        GuiOptions.active.registerFileLocatorsProg = registerProgState.getText(s);
        GuiOptions.active.visitFileLocatorProg = visitProgState.getText(s);
        GuiOptions.active.clearFileLocatorsProg = clearProgState.getText(s);
      }
    );

}
