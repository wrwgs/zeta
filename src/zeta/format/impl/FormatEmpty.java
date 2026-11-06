package zeta.format.impl;

import java.io.PrintWriter;

import zeta.format.Format;

/**
 * This class is a basic Format containing nothing and doing nothing
 * upon printing.
 *
 * @author Jacob Wieland (ughosage@cs.tu-berlin.de)
 *
 * @see Format
 * @see FormatStream
 *
 * <h2>Port notes</h2>
 *
 * Plain Java 1.1; no changes needed beyond explicit imports and @Override.
 */
public class FormatEmpty extends Format {
  @Override
  public int minHeight()    { return 0; }
  @Override
  public int minWidth()     { return 0; }
  @Override
  public int maxWidth()     { return 0; }
  @Override
  public int minLastWidth() { return 0; }
  @Override
  public int maxLastWidth() { return 0; }

  @Override
  public int appendText(int indentation, FormatStream stream) {
    // do nothing
    return 0;
  }

  @Override
  public TextRectangle printTextBeside(int indentation, FormatStream stream,
                                       int width, int lastWidth) {
    // do nothing
    return new TextRectangle(0, 0, 0); // no height, no width
  }

  @Override
  public void dumpText(PrintWriter writer) {
  }
}
