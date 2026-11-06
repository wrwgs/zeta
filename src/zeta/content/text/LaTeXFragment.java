package zeta.content.text;

import java.io.Serializable;

/**
 * A fragment of LaTeX input, used to resolve `&#92;IncludeModel` statements when LaTeX
 * documentation is generated.
 *
 * A Pizza single-case class, so the fields, the constructor and the same-named static factory
 * are all part of the compiled form.  It is one of the fourteen classes that declare
 * `Serializable` directly; see `docs/serialization.md` for why the port owes the 2002 store
 * nothing.
 */
public class LaTeXFragment implements Serializable {

  /** Additional header commands to load packages needed for the content
    * (e.g. {@code &#92;usepackage{epsfig}}). */
  public String header;

  /** The actual code to display the specific content.  Usually an {@code &#92;input} or
    * {@code &#92;epsfig} command. */
  public String content;

  /** LaTeX fragment.
    * A LaTeX fragment consists of two strings containing LaTeX commands.
    */
  public LaTeXFragment(String header, String content) {
    this.header = header;
    this.content = content;
  }

  /** Pizza's generated case factory. */
  public static LaTeXFragment LaTeXFragment(String header, String content) {
    return new LaTeXFragment(header, content);
  }
}
