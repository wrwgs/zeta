package zeta.session;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.io.StringWriter;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashSet;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.util.CaseConstants;
import zeta.util.FatalError;
import zeta.util.Term;

/**
 * Diagnostics: a sequence of items, each locating a problem in a source and describing it.
 *
 * @version $Id: Diag.pizza,v 1.6 2000/05/08 07:07:43 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * This is the class that carries every `! ERROR[...]` the negative suites check, so it is
 * worth being exact about. It is also the first ported class whose body is written against
 * ported AGL throughout -- `Seq`, `Set`, `BufferSeq`, `HashSet`, `ArraySeq` -- and the first
 * where `toArray(Class)` hands back a real Java array rather than a `pizza.support.array`
 * that the caller had to unwrap. `writeObject` therefore reads the same but says less.
 *
 * {@code Category} is Pizza's other kind of ADT: three nullary cases declared *without*
 * parentheses, so they compiled to `public static final` constants on a concrete base with a
 * `$$tag` -- effectively an enum that cannot be a Java `enum`, because it has to extend
 * {@link Term}. The constants and the tag values are kept; what goes is the shape of the
 * original `equals`, which switched over the other value and then over `this` in a nested
 * switch per case. Comparing tags is the same relation in one line, and `hashCode` returned
 * 1/2/3 for Error/Warning/Hint, which is precisely the tag.
 */
public class Diag extends Message {

  private Seq<Item> items = new BufferSeq<Item>();
  private Set<Category> categories = new HashSet<Category>();

  /** The class of diagnostic categories. */
  public static class Category extends Term {

    // Tag values are fixed by the ABI: 1-based, declaration order.
    private static final int TAG_ERROR = 1;
    private static final int TAG_WARNING = 2;
    private static final int TAG_HINT = 3;

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$session$Diag$Category$$tag;

    Category(int tag) {
      this.zeta$session$Diag$Category$$tag = tag;
    }

    // PORT CHANGE: a deserialised constant case is replaced by the canonical constant,
    // so identity tests on it hold as they do in memory (Pizza compared tags).  The
    // argument is in zeta.util.CaseConstants and docs/serialization.md.
    private Object readResolve() throws ObjectStreamException {
      return CaseConstants.resolve(this, Category.class);
    }

    /** Indicates an error, a fatal situation which lead to
	aborting the current operation. */
    public static final Category Error = new Category(TAG_ERROR);

    /** Indicates a warning, a critical situation which the user should
	notify. */
    public static final Category Warning = new Category(TAG_WARNING);

    /** Indicates a hint, additional information for the user. */
    public static final Category Hint = new Category(TAG_HINT);

    /** */
    public Format toFormat(FormatInfo info) {
      return switch (zeta$session$Diag$Category$$tag) {
        case TAG_ERROR -> Format.literal("ERROR");
        case TAG_WARNING -> Format.literal("WARNING");
        case TAG_HINT -> Format.literal("HINT");
        default -> throw FatalError.unexpectedCase("diagnostic category");
      };
    }

    /** */
    public boolean equals(Object other) {
      return other instanceof Category c
             && c.zeta$session$Diag$Category$$tag == zeta$session$Diag$Category$$tag;
    }

    /** */
    public int hashCode() {
      return zeta$session$Diag$Category$$tag;
    }
  }

  /**
   * An item, an element of diagnostics.
   */
  public static class Item extends Term {

    public Locator locator;
    public Category category;
    public Format message;

    /**
     * @param locator  the locator of this diagnostic item
     * @param category the category
     * @param message  additional information describing this item.
     */
    public Item(Locator locator, Category category, Format message) {
      this.locator = locator;
      this.category = category;
      this.message = message;
    }

    /** Pizza's generated case factory. */
    public static Item Item(Locator locator, Category category, Format message) {
      return new Item(locator, category, message);
    }

    /** */
    public Format toFormat(FormatInfo info) {
      return
        Format.block(
          Format.append(
            category.toFormat(info),
            locator != null
            ? Format.append(Format.literal("["),
                            Format.buildFormat(locator),
                            Format.literal("]: ")) : Format.literal(": ")
          ),
          Format.indent(message)
        );
    }
  }

  /** Construct from sequence of items. */
  private Diag(Seq<Item> items, Set<Category> categories) {
    this.items = items;
    this.categories = categories;
  }

  /** Construct empty diagnostic containing no items. */
  public Diag() {
  }

  /** Construct singelton diagnostic, containg just one item. The item
    * message is given as a format, allowing the diagnostic to be
    * printed for different line-widthes.  */
  public Diag(Locator locator, Category category, Format message) {
    items = items.append(new Item(locator, category, message));
    categories = categories.include(category);
  }

  /** Construct singleton diagnostic, containing just one item. The
    * item message is given as a string. The string is going to be
    * broken at whitespace (\t, \n, \r and \f) if necessary.
    *
    * @see zeta.format.Format#text
    */
  public Diag(Locator locator, Category category, String message) {
    this(locator, category, Format.text(message, " \t\f"));
  }

  /** Add an item to diagnostics.
    * @see Diag
    */
  public Diag add(Locator locator, Category category, Format message) {
    return new Diag(items.append(new Item(locator, category, message)),
                    categories.include(category));
  }

  /** Add an item to diagnostics.
    * @see Diag
    */
  public Diag add(Locator locator, Category category, String message) {
    return new Diag(items.append(new Item(locator, category,
                                          Format.text(message, " \t\f"))),
                    categories.include(category));
  }

  /** Combine other diagnostic with this diagnostic. */
  public Diag combine(Diag other) {
    return new Diag(items.appendAll(other.items),
                    categories.includeAll(other.categories));
  }

  /** Give the number of items in diagnostic. */
  public int size() {
    return items.size();
  }

  /** Return true iff diagnostics are empty. */
  public boolean isEmpty() {
    return items.isEmpty();
  }

  /** Test whether an item if the given category is present. */
  public boolean contains(Category category) {
    return categories.contains(category);
  }

  /** Return the items in diagnostics.
   */
  public Item[] getItems() {
    return items.toArray(Item.class);
  }

  /** Format diagnostics. */
  public Format toFormat(FormatInfo info) {
    return Format.beneath(items.map(Format::build));
  }

  /** Make string of diagnostics. */
  public String toString() {
    Format fmt = toFormat(new FormatInfo());
    StringWriter buf = new StringWriter();
    fmt.printFormat(new PrintWriter(buf), 75);
    return buf.toString();
  }

  /** */
  private void writeObject(ObjectOutputStream s) throws IOException {
    s.writeObject(items.toArray(Item.class));
    s.writeObject(categories.toArray(Category.class));
  }

  /** */
  private void readObject(ObjectInputStream s)
      throws IOException, ClassNotFoundException {
    items = new ArraySeq<Item>((Item[]) s.readObject());
    categories = HashSet.from(new ArraySeq<Category>((Category[]) s.readObject()));
  }
}
