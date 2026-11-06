package zeta.format.impl;

import java.io.PrintWriter;

import zeta.format.Format;

/**
 * This is the basic Format class for forced newlines.
 * It can contain a number of newlines.
 * <p>
 * Upon printing, the contained number of newlines is given to the
 * FormatStream and printed. Afterwards, the stream is at the beginning
 * of a new line.
 *
 * @author Jacob Wieland (ughosage@cs.tu-berlin.de)
 *
 * @see Format
 * @see FormatStream
 *
 * <h2>Port notes</h2>
 *
 * Plain Java 1.1; no changes needed beyond explicit imports and @Override.
 * The `lines` field is package-private (no access modifier) in both the source
 * and the reference bytecode.
 */
public class FormatBreak extends Format {
  int lines;

  @Override
  public int minHeight()    { return lines + 1; }
  @Override
  public int minWidth()     { return 0; }
  @Override
  public int maxWidth()     { return 0; }
  @Override
  public int minLastWidth() { return 0; }
  @Override
  public int maxLastWidth() { return 0; }

  public FormatBreak(int lines) {
    // all widths of a break are 0
    super();
    this.lines = lines;
  }

  public FormatBreak() {
    this(1);
  }

  // This makes no sense and should never be called unless lines == 0
  @Override
  public int appendText(int indentation, FormatStream stream) {

    // do nothing

    return 0;
  }

  @Override
  public TextRectangle printTextBeside(int indentation, FormatStream stream,
                                       int width, int lastWidth) {

    stream.printBreaks(lines);

    return new TextRectangle(0, lines + 1, 0);
  }

  @Override
  public void dumpText(PrintWriter writer) {
    writer.println();
  }

}
