package zeta.format.impl;

import java.io.PrintWriter;

import zeta.format.Format;

/**
 * This class is a basic Format containing a String without
 * newlines. All spaces within the text will never be ignored.
 * <p>
 * A further implementation could allow newlines, as well, but would
 * have to implement a different width/height behaviour as well as
 * a different print-behaviour, of course.
 * <p>
 * The text may also have an own indentation.
 *
 * @author Jacob Wieland (ughosage@cs.tu-berlin.de)
 *
 * @see Format
 * @see NestedFormat
 * @see FormatStream
 *
 * <h2>Port notes</h2>
 *
 * Plain Java 1.1; no changes needed beyond explicit imports and @Override.
 */
public class FormatText extends NestedFormat {
  public String text; // a text without any newlines

  @Override
  public int minHeight()    { return 1; }
  @Override
  public int minWidth()     { return text.length(); }
  @Override
  public int maxWidth()     { return text.length(); }
  @Override
  public int minLastWidth() { return text.length(); }
  @Override
  public int maxLastWidth() { return text.length(); }

  public FormatText(int indentation, String text) {
    super(indentation);
    this.text = text;
  }

  public FormatText(String text) {
    this(0, text);
  }

  @Override
  public int appendText(int indentation, FormatStream stream) {
    stream.printText(indentation, text);
    return text.length();
  }

  @Override
  public TextRectangle printTextBeside(int indentation, FormatStream stream,
                                       int width, int lastWidth) {
    return new TextRectangle(appendText(indentation, stream));
  }

  @Override
  public void dumpText(PrintWriter writer) {
    writer.write(text);
  }

}
