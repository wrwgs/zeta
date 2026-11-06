package zeta.content.text;

import java.io.File;

import zeta.format.Format;
import zeta.session.ContentType;
import zeta.util.Name;

/**
 * This class defines constants for content types related to text.
 *
 * @see zeta.session.Unit#query
 *
 * @version $Id: TextContentTypes.pizza,v 1.4 1998/09/23 00:03:27 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * These five constants are where the names in the oracle come from: `text`, `lang-config`,
 * `pretty-text` and `latex-fragment` are four of the nine content types `baseline.py` dumps,
 * and `dvi` is the one that needs a working LaTeX installation.  The symbolic name, not the
 * class, is what `ContentType.equals` compares.
 */
public class TextContentTypes {

  /**
   * The content type of the text of a unit.
   * Its symbolic name is "text", its content class
   * <code>Text</code>.
   */
  public static final ContentType text =
    new ContentType(new Name("text"), Text.class);

  /**
   * The content type of the concrete language configuration of a
   * unit. Its symbolic name is "lang-config", its content class
   * <code>Config</code>. <p>
   */
  public static final ContentType config =
    new ContentType(new Name("lang-config"), Config.class);

  /**
   * The content type of the pretty-printed syntax of a unit.
   */
  public static final ContentType prettyText =
    new ContentType(new Name("pretty-text"), Format.class);

  /** LaTeX fragment content type.
    * A fragment of LaTeX input that is used to resolve
    * {@code &#92;IncludeModel} statements when LaTeX documentation is
    * created.
    */
  public static final ContentType latexFragment =
    new ContentType(new Name("latex-fragment"), LaTeXFragment.class);

  /** DVI documentation content type. Contains the DVI file name.
    */
  public static final ContentType dvi =
    new ContentType(new Name("dvi"), File.class);

}
