package zeta.format.impl;

import java.io.PrintWriter;

import zeta.format.Format;

/**
 * This is the Format class for spaces that can be ignored at the beginning
 * and end of a line.
 * The space can have a width that is more than 1.
 *
 * @author Jacob Wieland (ughosage@cs.tu-berlin.de)
 *
 * @see Format
 * @see FormatStream
 *
 * <h2>Port notes</h2>
 *
 * Plain Java 1.1; no changes needed beyond explicit imports and @Override.
 * Note: in the reference bytecode the accessor order is minHeight, minWidth,
 * minLastWidth, maxWidth, maxLastWidth -- matching the source order exactly,
 * which differs from the logical order in Format.  Kept as-is.
 */
public class FormatSpace extends Format {
  private int width;

  @Override
  public int minHeight()    { return 1; }
  @Override
  public int minWidth()     { return width; }
  @Override
  public int minLastWidth() { return width; }
  @Override
  public int maxWidth()     { return width; }
  @Override
  public int maxLastWidth() { return width; }

  public FormatSpace(int width) {
    super();
    this.width = width;
  }

  public FormatSpace() {
    this(1);
  }

  @Override
  public int appendText(int indentation, FormatStream stream) {
    stream.printSpaces(width);
    return width;
  }

  @Override
  public TextRectangle printTextBeside(int indentation, FormatStream stream,
                                       int width, int lastWidth) {
    return new TextRectangle(appendText(indentation, stream));
  }

  @Override
  public void dumpText(PrintWriter writer) {
    writer.write(" ");
  }
}
