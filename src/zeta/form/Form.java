package zeta.form;

import zeta.agl.ArraySeq;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of interaction forms. <p>
 *
 * Interaction forms represent an abstract way for tools to specify
 * communication with users. Sessions managers may interpret
 * forms in a shell style or by graphical user interfaces. <p>
 *
 * @see LiteralForm
 * @see TextForm
 * @see FileForm
 * @see UnitForm
 * @see ContentTypeForm
 * @see OptionForm
 * @see ChoiceForm
 * @see SeqForm
 *
 * @version $Id: Form.pizza,v 1.7 2000/05/14 17:43:32 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * `Form` itself is a plain abstract class -- no Pizza feature beyond the two `style` helpers,
 * which are rewritten with the generic {@link zeta.agl.ArraySeq} but keep their erased
 * descriptors (`(Lzeta/form/Form$Style;)Lzeta/form/Form;` and the array variant).
 *
 * The nested {@link Style} is a 13-case Pizza ADT with a mixed shape: `Border`, `Label`,
 * `Tip`, `PreferredSize`, `MinimumSize`, `MaximumSize`, `Dialog` and `View` are declared
 * *with* parentheses and so became nested classes plus static factories, while `FIXSIZE`,
 * `INVISIBLE`, `HISTORY`, `HORIZONTAL` and `VERTICAL` are declared *without* and so became
 * `public static final Style` constants.  Because of those constants the base is concrete,
 * which rules out `sealed`; it is extensible anyway -- `zeta.tools.sh` switches over the tag
 * with a `default` branch.
 *
 * Tag values were read from the 2002 class files with `harness/adt_tags.py`, not derived from
 * declaration order; they happen to agree here (1..13, counting the constants in place).
 *
 * Field order follows the reference exactly: the five constants first (all static), then
 * `zeta$form$Form$Style$$tag`.  `Label`'s field is named `stringOrIcon`, as the Pizza
 * parameter is, even though the javadoc above it says `textOrIcon`.
 *
 * {@code Style.class} appears in `style(..)` as the element type handed to
 * `Aggregate.toArray`.  Modern javac emits an `ldc` there, which -- unlike Pizza's
 * `Class.forName` -- does not run `Style`'s static initialiser.  That is harmless: the
 * initialiser only allocates the five constants, and any read of one of them triggers it.
 * No class literal in this package names a slot-registering class.
 */
public abstract class Form {

  /**
   * Parse the given sequence of words starting at index. Return the
   * index of the next word not consumed. Return -1 to indicate that
   * parsing has failed for syntactical reasons.  Throw a session
   * failure if parsing runs in a non-recoverable error. It is
   * expected that such errors are augmented with diagnostics by the
   * individual parse functions using the sessions message system. The
   * parsing result is entered into the forms internal state. The
   * session under which parsing happens is passed, also a locator to
   * be used in diagnostics produced by parsing.  */
  public abstract int parse(Session session, Locator locator,
                            String[] words, int start)
  throws Failures.FormFailed;

  /**
   * Unparse the internal state of the form converting it to a
   * sequence of words. Parsing the returned sequence should reconstruct
   * the forms internal state, if it succeeds. The session under
   * which unparsing happens is passed.
   */
  public abstract String[] unparse(Session session);


  /**
   * Get a syntactic description of this form.
   */
  public abstract String getSyntax();



  /*
   * PORT NOTE: already commented out in the Pizza source; kept for the record.
   *
   * Get the label of this form. If a short description is present, use
   * this. Else, if the form is a literal, use this. Else, if this is
   * a sequence, use the label of its head. Else use the passed
   * string, which may be null.
   *
   * public String getLabel(String fallback){
   *   if (shortDescr != null) return shortDescr;
   *   if (this instanceof LiteralForm)
   *     return ((LiteralForm)this).keyword;
   *   if (this instanceof SeqForm)
   *     return ((SeqForm)this).subforms[0].getLabel(fallback);
   *   return fallback;
   * }
   */

  /**
   * A field holding an array of general style configure options telling
   * how this form if displayed in a GUI. The options apply not to all
   * form subclasses; if they don't apply, they are silentely ignored.
   */
  public Style[] style = new Style[0];

  /**
   * Add a style to this form. Return this form. <p>
   *
   * Note that this method returns an object of the general Form type,
   * which wouldn't allow you to apply form specific style methods further
   * on. When using a functional style for constructing forms, first attach
   * specific, then general styles:
   * <pre>
   *   new TheForm(...).style(new TheForm.Style.Specific(...))
   *                   .style(new Form.Style.General1(...))
   *                   .style(new Form.Style.General2(...))
   *                   . ...
   * </pre>
   */
  public Form style(Style style) {
    this.style = new ArraySeq<Style>(this.style).append(style).toArray(Style.class);
    return this;
  }

  /**
   * Add an array of styles to this form. Return this form.
   */
  public Form style(Style[] styles) {
    this.style = new ArraySeq<Style>(this.style)
                   .appendAll(new ArraySeq<Style>(styles))
                   .toArray(Style.class);
    return this;
  }

  /**
   * The class of general form styles. Not all of them apply to all
   * variants of forms; if a style doesn't applies, it is ignored.
   * Note that individual forms may provide further styles.
   */
  public static class Style {

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_BORDER = 1;
    private static final int TAG_LABEL = 2;
    private static final int TAG_TIP = 3;
    private static final int TAG_PREFERRED_SIZE = 4;
    private static final int TAG_MINIMUM_SIZE = 5;
    private static final int TAG_MAXIMUM_SIZE = 6;
    private static final int TAG_FIXSIZE = 7;
    private static final int TAG_DIALOG = 8;
    private static final int TAG_INVISIBLE = 9;
    private static final int TAG_VIEW = 10;
    private static final int TAG_HISTORY = 11;
    private static final int TAG_HORIZONTAL = 12;
    private static final int TAG_VERTICAL = 13;

    /**
     * Fix the size of this form to its preffered size, setting minimal
     * and maximum size to the preferred size.
     */
    public static final Style FIXSIZE = new Style(TAG_FIXSIZE);

    /**
     * Whether this form is invisible.
     */
    public static final Style INVISIBLE = new Style(TAG_INVISIBLE);

    /**
     * If this is a form which provides a history, such as a TextForm,
     * add buttons for accessing the history.
     */
    public static final Style HISTORY = new Style(TAG_HISTORY);

    /**
     * If this is a compound form (such as a sequence or choice), layout
     * its components horizontally. The default for layouting depends on
     * the form type.
     */
    public static final Style HORIZONTAL = new Style(TAG_HORIZONTAL);

    /**
     * If this is a compound form (such as a sequence or choice), layout
     * its components vertically. The default for layouting depends on
     * the form type.
     */
    public static final Style VERTICAL = new Style(TAG_VERTICAL);

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$form$Form$Style$$tag;

    Style(int tag) {
      this.zeta$form$Form$Style$$tag = tag;
    }

    /**
     * Put a border around this form.
     * @param title   if non-null, use as a title embedded in the border
     */
    public static class Border extends Style {
      public String title;

      public Border(String title) {
        super(TAG_BORDER);
        this.title = title;
      }
    }

    /**
     * A label for this form. If given, is displayed in front of
     * the actual form. If given for a literal form, replaces the
     * entire display of the literal form.
     * @param stringOrIcon  a string specifiying the label text. If
     *                      starting with an @, specifies an
     *                      icon. The remaining string
     *                      is then used as a file name relative to the
     *                      images directory of the ZETA installation
     * @see LiteralForm
     */
    public static class Label extends Style {
      public String stringOrIcon;

      public Label(String stringOrIcon) {
        super(TAG_LABEL);
        this.stringOrIcon = stringOrIcon;
      }
    }

    /**
     * A tool-tip text provided for this form.
     */
    public static class Tip extends Style {
      public String text;

      public Tip(String text) {
        super(TAG_TIP);
        this.text = text;
      }
    }

    /**
     * A preferred size for this form. If the indidvidual width or
     * height is 0, take over the defaults of the GUI. If greater then
     * 0, take values as pixel units. If less then zero, take as units of
     * character width/height of the default font.
     */
    public static class PreferredSize extends Style {
      public int width;
      public int height;

      public PreferredSize(int width, int height) {
        super(TAG_PREFERRED_SIZE);
        this.width = width;
        this.height = height;
      }
    }

    /**
     * A minimal size for this form. If the indidvidual width or
     * height is 0, take over the defaults of the GUI. If greater then
     * 0, take values as pixel units. If less then zero, take as units of
     * character width/height of the default font.
     */
    public static class MinimumSize extends Style {
      public int width;
      public int height;

      public MinimumSize(int width, int height) {
        super(TAG_MINIMUM_SIZE);
        this.width = width;
        this.height = height;
      }
    }

    /**
     * A maximal size for this form. If the indidvidual width or
     * height is 0, take over the defaults of the GUI. If greater then
     * 0, take values as pixel units. If less then zero, take as units of
     * character width/height of the default font.
     */
    public static class MaximumSize extends Style {
      public int width;
      public int height;

      public MaximumSize(int width, int height) {
        super(TAG_MAXIMUM_SIZE);
        this.width = width;
        this.height = height;
      }
    }

    /**
     * Whether this form shall be put in a separate dialog.
     * @param textOrIcon    a text used in the button to invoke
     *                      the separate dialog. If
     *                      starting with an @, specifies an
     *                      icon. The remaining string is then
     *                      used as a file name relative to the
     *                      images directory of the ZETA installation
     * @param title         a text used as the title of the dialog
     */
    public static class Dialog extends Style {
      public String textOrIcon;
      public String title;

      public Dialog(String textOrIcon, String title) {
        super(TAG_DIALOG);
        this.textOrIcon = textOrIcon;
        this.title = title;
      }
    }

    /**
     * Whether this form shall be put in a scrollable view. Given are
     * the units of scrolling. If 0, take over the defaults of the
     * GUI. If greater then 0, take values as pixel units. If less then zero,
     * take values as units of character width/height of the default font.
     */
    public static class View extends Style {
      public int horiUnits;
      public int vertUnits;

      public View(int horiUnits, int vertUnits) {
        super(TAG_VIEW);
        this.horiUnits = horiUnits;
        this.vertUnits = vertUnits;
      }
    }

    /** Pizza's generated case factory. */
    public static Style Border(String title) {
      return new Border(title);
    }

    /** Pizza's generated case factory. */
    public static Style Label(String stringOrIcon) {
      return new Label(stringOrIcon);
    }

    /** Pizza's generated case factory. */
    public static Style Tip(String text) {
      return new Tip(text);
    }

    /** Pizza's generated case factory. */
    public static Style PreferredSize(int width, int height) {
      return new PreferredSize(width, height);
    }

    /** Pizza's generated case factory. */
    public static Style MinimumSize(int width, int height) {
      return new MinimumSize(width, height);
    }

    /** Pizza's generated case factory. */
    public static Style MaximumSize(int width, int height) {
      return new MaximumSize(width, height);
    }

    /** Pizza's generated case factory. */
    public static Style Dialog(String textOrIcon, String title) {
      return new Dialog(textOrIcon, title);
    }

    /** Pizza's generated case factory. */
    public static Style View(int horiUnits, int vertUnits) {
      return new View(horiUnits, vertUnits);
    }

  }

  /**
   * A shortcut for adding a label style.
   */
  public Form describe(String label) {
    return style(new Style.Label(label));
  }

  /**
   * A shortcut for adding a label and tool-tip style.
   */
  public Form describe(String label, String tip) {
    return style(new Style.Label(label)).style(new Style.Tip(tip));
  }

  /**
   * A shortcut for setting the preferred size in text units and
   * fixing it.
   */
  public Form textSize(int columns, int rows) {
    return style(new Style[]{ new Style.PreferredSize(-columns, -rows),
                              Style.FIXSIZE });
  }

  /**
   * A shortcut for setting the preferred size in pixels and
   * fixing it.
   */
  public Form size(int width, int height) {
    return style(new Style[]{ new Style.PreferredSize(width, height),
                              Style.FIXSIZE });
  }

}
