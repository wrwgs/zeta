package zeta.tools.nzap;

import java.io.File;

import zeta.agl.HashMap;
import zeta.agl.Map;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.ContentType;
import zeta.session.Locator;
import zeta.util.DebugWriter;
import zeta.util.Name;
import zeta.util.Term;

/** Configuration and utilities.
  *
  * @version $Id: ZapUtil.pizza,v 1.7 2000/06/28 08:04:22 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p>Static initialisation order is the 2002 order (<code>toolName</code>, the two
  * locators, <code>debug</code>, <code>zapUnit</code>, <code>unitNameMap</code>,
  * <code>genDebugInfo</code>, then <code>readProperties()</code>).</p>
  *
  * <p><code>ZapFile</code> is a single-case class whose case name equals the class name:
  * public fields, public constructor and a same-named static factory, no
  * <code>$$tag</code>.  <code>ZapFile.class</code> in <code>zapUnit</code> was
  * <code>Class.forName</code> in Pizza; <code>ZapFile</code> registers no slot, and is
  * initialised by the time a unit is computed anyway.</p>
  *
  * <p>The two locators are <code>private</code> nested classes as in the source; the jar
  * shows them package-private at the class-file level, which is how a 1.1 compiler
  * records a private member class (the access lives in the InnerClasses attribute).
  * Nothing outside this file names them.  As in the reference, they override
  * <code>equals</code> but not <code>hashCode</code>.</p>
  */
class ZapUtil {

  /** The name of ZAP as a tool adaptor. */
  static final Name toolName = new Name("ZAP");

  /** The locator associated with ZAP as a tool adaptor. */
  static final Locator toolLocator =
    new ToolLocator();

  /** The locator associated with the ZAM. */
  static final Locator zamLocator =
    new ZamLocator();

  private static class ToolLocator extends Locator {
    public Format toFormat(FormatInfo info) {
      return Format.literal("<ZAP>");
    }
    public Name getTool() {
      return toolName;
    }
    public boolean equals(Object other) {
      return other instanceof ToolLocator;
    }
  }

  private static class ZamLocator extends Locator {
    public Format toFormat(FormatInfo info) {
      return Format.literal("<ZAM>");
    }
    public Name getTool() {
      return toolName;
    }
    public boolean equals(Object other) {
      return other instanceof ZamLocator;
    }
  }

  /** The version of ZAP. */
  static final String version = "2.1";

  /** A debug writer for ZAP. */
  static DebugWriter debug;

  /** A detailed debug writer for ZAP. */
  // static DebugWriter ddebug;

  /**
   * The content type of a ZAP compiled unit.
   */
  static final ContentType zapUnit =
    new ContentType(new Name("zap"), ZapFile.class);

  public static class ZapFile extends Term {
    public Name unitName;
    public File unitFile;

    public ZapFile(Name unitName, File unitFile) {
      this.unitName = unitName;
      this.unitFile = unitFile;
    }

    /** Pizza's generated case factory. */
    public static ZapFile ZapFile(Name unitName, File unitFile) {
      return new ZapFile(unitName, unitFile);
    }
  }

  /** A transformation of unit names to builtin class names. */
  private static Map<Name, String> unitNameMap =
    new HashMap<Name, String>().define(new Name("zrm"),
                                       "zap.rts.IntNumKit")
                               .define(new Name("zrm+units"),
                                       "zap.rts.UnitRatNumKit")
                               .define(new Name("zrm+checkedunits"),
                                       "zap.rts.UnitRatNumKit")
                               .define(new Name("zrm+realunits"),
                                       "zap.rts.UnitRealNumKit")
                               .define(new Name("msz"),
                                       "zap.rts.MszKit");

  /** Try to transform a unit name to its according builtin class name, or
      return null, if no translation is defined. */
  public static String tryBuiltinName(Name name) {
    // FIXME: its not clear how to do this
    String repr = name.getRepr();
    int i = repr.indexOf(":");
    if (i >= 0) repr = repr.substring(i + 1);
    i = repr.indexOf("#");
    if (i >= 0) repr = repr.substring(0, i);
    name = new Name(repr);
    if (unitNameMap.defines(name))
      return unitNameMap.get(name);
    else
      return null;
  }



  /** Whether debug information in the generated code should be included. */
  public static boolean genDebugInfo = true;


  /** Setup options read from system properties. */
  static void readProperties() {
    debug = DebugWriter.make(toolName, "zeta.debug.zap.print");
    // debug = DebugWriter.make(toolName, true);
  }

  static {
    readProperties();
  }


}
