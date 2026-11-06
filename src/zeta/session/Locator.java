package zeta.session;

import java.io.File;
import java.net.URL;

import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.form.Form;
import zeta.util.Annotation;
import zeta.util.Name;
import zeta.util.Slot;
import zeta.util.Term;

/**
 * A locator identifies a position in a source, and knows how to display and visit it.
 *
 * @version $Id: Locator.pizza,v 1.9 2000/05/08 07:07:43 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * Deliberately **not** sealed: thirteen classes across `zeta.tools` extend `Locator`, and
 * every adaptor is expected to define its own. It is the clearest example in the system of an
 * open extension point, as against the closed case sets of `Errors` and `Failures`.
 *
 * `combine` returned an anonymous subclass, which Pizza compiled to
 * `Locator$1$$anonymous_class0`; here it is an ordinary anonymous class. Note that the
 * original captured `this` into a local `thisLocator` *and* also used `Locator.this` in one
 * method -- both forms are kept as written, since they denote the same object and rewriting
 * one into the other would be churn.
 *
 * `VisitCapability` is a four-case ADT with the mixed shape §9.6 describes: `NativeVisit` is
 * declared without parentheses and so is a `public static final` constant, while the other
 * three are nested classes with factories. The base is therefore concrete, and cannot be
 * sealed-and-switched; code that dispatches on it switches on the tag.
 *
 * `compareTo` here has nothing to do with `zeta.agl.Ordered` -- it is a plain method, and the
 * reducer calls it directly. It compares rendered mnemonics, so it is stable across runs.
 */
public abstract class Locator extends Term {

  // LOCATOR METHODS

  /**
   */
  public abstract Name getTool();

  /**
   */
  public abstract Format toFormat(FormatInfo info);

  /**
   */
  public String getMnemonic() {
    return toFormat(new FormatInfo()).toString(1000);
  }

  /**
   */
  public int compareTo(Locator loc) {
    return toFormat(new FormatInfo()).toString(1000)
             .compareTo(loc.toFormat(new FormatInfo()).toString(1000));
  }

  /**
   */
  public Locator combine(final Locator other) {
    final Locator thisLocator = this;
    return
      new Locator() {
        public Name getTool() {
          return Locator.this.getTool();
        }
        public Format toFormat(FormatInfo info) {
          return Format.block(
                   Format.append(thisLocator.toFormat(info),
                                 Format.literal(";")),
                   other.toFormat(info)
                 );
        }
        public int compareTo(Locator loc) {
          return thisLocator.compareTo(loc);
        }
        public Locator getPrimary() {
          return thisLocator.getPrimary();
        }
        public Locator getSecondary() {
          return other;
        }
        public VisitCapability[] getVisitCapabilities() {
          return getPrimary().getVisitCapabilities();
        }
      };
  }

  /**
   */
  public Locator extend(Locator other) {
    return combine(other);
  }

  /**
   */
  public Locator getPrimary() {
    return this;
  }

  /**
   */
  public Locator getSecondary() {
    return null;
  }

  /**
   */
  public VisitCapability[] getVisitCapabilities() {
    return new VisitCapability[0];
  }

  /**
   */
  public static class VisitCapability {

    // Tag values are fixed by the ABI: 1-based, declaration order.
    private static final int TAG_NATIVE_VISIT = 1;
    private static final int TAG_HTML_VISIT = 2;
    private static final int TAG_TEXT_VISIT = 3;
    private static final int TAG_FORM_VISIT = 4;

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$session$Locator$VisitCapability$$tag;

    VisitCapability(int tag) {
      this.zeta$session$Locator$VisitCapability$$tag = tag;
    }

    /** Provides native visiting.
     * @see Locator#nativeVisit
     */
    public static final VisitCapability NativeVisit =
      new VisitCapability(TAG_NATIVE_VISIT);

    /** Provides visiting in an HTML viewer via the given URL. */
    public static class HTMLVisit extends VisitCapability {
      public URL url;

      public HTMLVisit(URL url) {
        super(TAG_HTML_VISIT);
        this.url = url;
      }
    }

    /** Provides visiting in the given text file, with specified region in
     * the file. */
    public static class TextVisit extends VisitCapability {
      public File file;
      public int firstRow;
      public int firstCol;
      public int lastRow;
      public int lastCol;

      public TextVisit(File file, int firstRow, int firstCol,
                       int lastRow, int lastCol) {
        super(TAG_TEXT_VISIT);
        this.file = file;
        this.firstRow = firstRow;
        this.firstCol = firstCol;
        this.lastRow = lastRow;
        this.lastCol = lastCol;
      }
    }

    /** Provides visiting via the given form. */
    public static class FormVisit extends VisitCapability {
      public Form form;

      public FormVisit(Form form) {
        super(TAG_FORM_VISIT);
        this.form = form;
      }
    }

    /** Pizza's generated case factory. */
    public static VisitCapability HTMLVisit(URL url) {
      return new HTMLVisit(url);
    }

    /** Pizza's generated case factory. */
    public static VisitCapability TextVisit(File file, int firstRow, int firstCol,
                                            int lastRow, int lastCol) {
      return new TextVisit(file, firstRow, firstCol, lastRow, lastCol);
    }

    /** Pizza's generated case factory. */
    public static VisitCapability FormVisit(Form form) {
      return new FormVisit(form);
    }
  }

  /**
   */
  public void nativeVisit() {
    throw Errors.AssertionFailed("native visit not provided: " + this);
  }

  // ANNOTATION SLOT

  /** The slot object. */
  public static final Slot slot = Slot.register(Locator.class.getName(),
                                                Locator.class);

  /** Set value of locator annotation slot.
    * @param an    the annotation
    * @param locator  the locator
    */
  public static void put(Annotation an, Locator locator) {
    slot.put(an, locator);
  }

  /** Get value of locator annotation slot.
    * @param an    the annotation
    * @return the locator, or null, if no one is provided.
    */
  public static Locator get(Annotation an) {
    return (Locator) slot.get(an);
  }

  /** Test whether locator annotation slot is provided.
    * @param an   the annotation
    * @return true if the locator annotation slot is provided
    */
  public static boolean isProvided(Annotation an) {
    return slot.isProvided(an);
  }
}
