package zeta.format.impl;

import zeta.format.Format;

/**
 * A TextRectangle records the width, height, and last-line width of text
 * that was printed by a format.
 *
 * @author Jacob Wieland (ughosage@cs.tu-berlin.de)
 * @version $Id: TextRectangle.java,v 1.1.1.1 1998/09/01 10:51:16 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * Plain Java 1.1; no changes needed beyond the explicit import.
 */
public class TextRectangle {
  public int width, height, lastWidth;

  public TextRectangle(int width, int height, int lastWidth) {
    this.width = width;
    this.height = height;
    this.lastWidth = lastWidth;
  }

  // A text-rectangle that only covers one line
  public TextRectangle(int width) {
    this(width, 1, width);
  }

  public TextRectangle nested(int indentation) {
    width += indentation;
    lastWidth += indentation;
    return this;
  }
}
