package zeta.tools.sh;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * The driver.
 *
 * @version $Id: Driver.pizza,v 1.9 2000/07/26 16:00:38 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Unchanged in behaviour: print the banner, require {@code zeta.home}, load
 * {@code $zeta.home/etc/defaults.props} as a properties layer whose defaults are the current
 * system properties, install it with {@code System.setProperties}, choose the commander from
 * {@code -b}/{@code -g} (GUI by default; the batch shell when there is no display -- see
 * the PORT CHANGE in main), and invoke its {@code main} reflectively with the
 * remaining arguments.  The config scripts ({@code zetarc}) are read later, by the commander,
 * through {@code CommandInterpreter.readAndEvalConfigScripts}.
 *
 * <p>The {@code FileInputStream} is not closed, as in 1998; the driver reads it once at start
 * up and the leak is immaterial.  The unused {@code File dir = new File("ZETA")} is kept with
 * the commented-out project-directory code it belongs to.
 */
public class Driver {


  public static void main(String[] argv) {

    System.out.println(
    "ZETA 1.5, (c) 1997-2000 Wolfgang Grieskamp, Robert Buessow (restored for Java 21, 2026)"
    );

    String home = System.getProperty("zeta.home");
    String fsep = System.getProperty("file.separator");

    if (home == null) {
      System.err.println("ZETA initialization error: property `zeta.home' not defined.");
      System.exit(1);
      return;
    }

    try {
      Properties props = new Properties(System.getProperties());
      props.load(new FileInputStream(
                       new File(home + fsep + "etc" + fsep + "defaults.props")
                     ));

      System.setProperties(props);
    }
    catch (IOException e) {
      System.err.println("ZETA property file not found: " + e);
      System.exit(1);
      return;
    }


    String ui = "zeta.tools.sh.GuiCommander";
    boolean uiChosen = false;

    int argc = 0;
    while (argc < argv.length) {
      if (argv[argc].equals("-h") || argv[argc].equals("--help")) {
        System.err.println("usage: zeta { -b|--batch | -g|--gui | -h|--help } { <source> }");
        System.exit(1);
      } else if (argv[argc].equals("-b") || argv[argc].equals("--batch")) {
        ui = "zeta.tools.sh.BatchCommander";
        uiChosen = true;
        argc++;
      } else if (argv[argc].equals("-g") || argv[argc].equals("--gui")) {
        ui = "zeta.tools.sh.GuiCommander";
        uiChosen = true;
        argc++;
      } else {
        break;
      }
    }
    // PORT CHANGE (2026): the GUI is the default, as in 2000, but on a machine without a
    // display (no X11/Wayland, e.g. over ssh) Swing throws HeadlessException, which reached
    // the user as "An unexpected exception has occured: programming error".  Without an
    // explicit choice, fall back to the batch shell there; with an explicit -g, say why it
    // cannot start.  Every run with a display, and every run with -b, is unchanged.
    if (ui.equals("zeta.tools.sh.GuiCommander") && GraphicsEnvironment.isHeadless()) {
      if (uiChosen) {
        System.err.println("zeta: the GUI needs a display (no DISPLAY is set); "
                           + "use -b for the batch shell.");
        System.exit(1);
        return;
      }
      System.err.println("zeta: no display -- starting the batch shell (-b); "
                         + "type `help' for the commands.");
      ui = "zeta.tools.sh.BatchCommander";
    }

    String[] files = new String[argv.length - argc];
    System.arraycopy(argv, argc, files, 0, argv.length - argc);

    // actually obsolete since we don't keep session data persistent
    File dir = new File("ZETA");
    /*
    if (!dir.isDirectory() && !dir.mkdir()){
      System.err.println("Cannot create project folder `" + dir + "'");
      System.exit(1);
    }
    */

    // branch to the main function of the according ui
    try {
      Class<?> uiClass = Class.forName(ui);
      Method mainMethod = uiClass.getMethod("main",
                                            new Class[]{ String[].class });
      mainMethod.invoke(null,
                        new Object[]{files});
    }
    catch (Exception e) {
      System.err.println("Cannot invoke commander `" + ui + "':\n " + e);
      System.exit(1);
    }
  }

}
