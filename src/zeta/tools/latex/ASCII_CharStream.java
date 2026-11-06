package zeta.tools.latex;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * An implementation of interface CharStream, where the stream is assumed to
 * contain only ASCII characters (without unicode processing).
 *
 * <h2>Port notes</h2>
 *
 * Not generated: a hand port of the 2002 `zeta.tools.latex.ASCII_CharStream`, which an older
 * JavaCC emitted as the scanner's input stream.  JavaCC 3.2 would emit `SimpleCharStream`
 * instead, and that class differs in the one respect that matters here: this stream hands
 * the token manager every character masked to its low eight bits, and updates the line and
 * column bookkeeping with the masked character, while {@link #GetImage} and
 * {@link #GetSuffix} return the characters as read.  For input above U+00FF that changes
 * how the LaTeX scanner tokenizes (see the notes in `port/grammar/.../LatexScanner.jj`), so
 * the grammar is generated with `USER_CHAR_STREAM` and this class is the `CharStream`.
 *
 * Transcribed from the reference bytecode, member for member and in the same field order;
 * the only addition is `implements CharStream`, the interface JavaCC 3.2 generates for a
 * user char stream.  Nothing outside `zeta.tools.latex` refers to this class.
 */
public final class ASCII_CharStream implements CharStream {

  public static final boolean staticFlag = false;
  int bufsize;
  int available;
  int tokenBegin;
  public int bufpos = -1;
  private int[] bufline;
  private int[] bufcolumn;

  private int column = 0;
  private int line = 1;

  private boolean prevCharIsCR = false;
  private boolean prevCharIsLF = false;

  private Reader inputStream;

  private char[] buffer;
  private int maxNextCharInd = 0;
  private int inBuf = 0;

  private final void ExpandBuff(boolean wrapAround) {
    char[] newbuffer = new char[bufsize + 2048];
    int[] newbufline = new int[bufsize + 2048];
    int[] newbufcolumn = new int[bufsize + 2048];

    try {
      if (wrapAround) {
        System.arraycopy(buffer, tokenBegin, newbuffer, 0, bufsize - tokenBegin);
        System.arraycopy(buffer, 0, newbuffer, bufsize - tokenBegin, bufpos);
        buffer = newbuffer;

        System.arraycopy(bufline, tokenBegin, newbufline, 0, bufsize - tokenBegin);
        System.arraycopy(bufline, 0, newbufline, bufsize - tokenBegin, bufpos);
        bufline = newbufline;

        System.arraycopy(bufcolumn, tokenBegin, newbufcolumn, 0, bufsize - tokenBegin);
        System.arraycopy(bufcolumn, 0, newbufcolumn, bufsize - tokenBegin, bufpos);
        bufcolumn = newbufcolumn;

        maxNextCharInd = (bufpos += (bufsize - tokenBegin));
      } else {
        System.arraycopy(buffer, tokenBegin, newbuffer, 0, bufsize - tokenBegin);
        buffer = newbuffer;

        System.arraycopy(bufline, tokenBegin, newbufline, 0, bufsize - tokenBegin);
        bufline = newbufline;

        System.arraycopy(bufcolumn, tokenBegin, newbufcolumn, 0, bufsize - tokenBegin);
        bufcolumn = newbufcolumn;

        maxNextCharInd = (bufpos -= tokenBegin);
      }
    } catch (Throwable t) {
      throw new Error(t.getMessage());
    }

    bufsize += 2048;
    available = bufsize;
    tokenBegin = 0;
  }

  private final void FillBuff() throws IOException {
    if (maxNextCharInd == available) {
      if (available == bufsize) {
        if (tokenBegin > 2048) {
          bufpos = maxNextCharInd = 0;
          available = tokenBegin;
        } else if (tokenBegin < 0)
          bufpos = maxNextCharInd = 0;
        else
          ExpandBuff(false);
      } else if (available > tokenBegin)
        available = bufsize;
      else if ((tokenBegin - available) < 2048)
        ExpandBuff(true);
      else
        available = tokenBegin;
    }

    int i;
    try {
      if ((i = inputStream.read(buffer, maxNextCharInd, available - maxNextCharInd)) == -1) {
        inputStream.close();
        throw new IOException();
      } else
        maxNextCharInd += i;
      return;
    } catch (IOException e) {
      --bufpos;
      backup(0);
      if (tokenBegin == -1)
        tokenBegin = bufpos;
      throw e;
    }
  }

  public final char BeginToken() throws IOException {
    tokenBegin = -1;
    char c = readChar();
    tokenBegin = bufpos;

    return c;
  }

  private final void UpdateLineColumn(char c) {
    column++;

    if (prevCharIsLF) {
      prevCharIsLF = false;
      line += (column = 1);
    } else if (prevCharIsCR) {
      prevCharIsCR = false;
      if (c == '\n') {
        prevCharIsLF = true;
      } else
        line += (column = 1);
    }

    switch (c) {
      case '\r':
        prevCharIsCR = true;
        break;
      case '\n':
        prevCharIsLF = true;
        break;
      case '\t':
        column--;
        column += (8 - (column & 07));
        break;
      default:
        break;
    }

    bufline[bufpos] = line;
    bufcolumn[bufpos] = column;
  }

  public final char readChar() throws IOException {
    if (inBuf > 0) {
      --inBuf;
      return (char) ((char) 0xff & buffer[(bufpos == bufsize - 1) ? (bufpos = 0) : ++bufpos]);
    }

    if (++bufpos >= maxNextCharInd)
      FillBuff();

    char c = (char) ((char) 0xff & buffer[bufpos]);

    UpdateLineColumn(c);
    return (c);
  }

  /** Deprecated in JavaCC: use {@link #getEndColumn}. */
  public final int getColumn() {
    return bufcolumn[bufpos];
  }

  /** Deprecated in JavaCC: use {@link #getEndLine}. */
  public final int getLine() {
    return bufline[bufpos];
  }

  public final int getEndColumn() {
    return bufcolumn[bufpos];
  }

  public final int getEndLine() {
    return bufline[bufpos];
  }

  public final int getBeginColumn() {
    return bufcolumn[tokenBegin];
  }

  public final int getBeginLine() {
    return bufline[tokenBegin];
  }

  public final void backup(int amount) {
    inBuf += amount;
    if ((bufpos -= amount) < 0)
      bufpos += bufsize;
  }

  public ASCII_CharStream(Reader dstream, int startline, int startcolumn, int buffersize) {
    inputStream = dstream;
    line = startline;
    column = startcolumn - 1;

    available = bufsize = buffersize;
    buffer = new char[buffersize];
    bufline = new int[buffersize];
    bufcolumn = new int[buffersize];
  }

  public ASCII_CharStream(Reader dstream, int startline, int startcolumn) {
    this(dstream, startline, startcolumn, 4096);
  }

  public void ReInit(Reader dstream, int startline, int startcolumn, int buffersize) {
    inputStream = dstream;
    line = startline;
    column = startcolumn - 1;

    if (buffer == null || buffersize != buffer.length) {
      available = bufsize = buffersize;
      buffer = new char[buffersize];
      bufline = new int[buffersize];
      bufcolumn = new int[buffersize];
    }
    prevCharIsLF = prevCharIsCR = false;
    tokenBegin = inBuf = maxNextCharInd = 0;
    bufpos = -1;
  }

  public void ReInit(Reader dstream, int startline, int startcolumn) {
    ReInit(dstream, startline, startcolumn, 4096);
  }

  /**
   * PORT NOTE: decodes with the platform default charset, as the 2002 class did, and --
   * a defect of the JavaCC template, kept -- ignores `buffersize` in favour of 4096.
   */
  public ASCII_CharStream(InputStream dstream, int startline, int startcolumn,
                          int buffersize) {
    this(new InputStreamReader(dstream), startline, startcolumn, 4096);
  }

  public ASCII_CharStream(InputStream dstream, int startline, int startcolumn) {
    this(dstream, startline, startcolumn, 4096);
  }

  public void ReInit(InputStream dstream, int startline, int startcolumn, int buffersize) {
    ReInit(new InputStreamReader(dstream), startline, startcolumn, 4096);
  }

  public void ReInit(InputStream dstream, int startline, int startcolumn) {
    ReInit(dstream, startline, startcolumn, 4096);
  }

  public final String GetImage() {
    if (bufpos >= tokenBegin)
      return new String(buffer, tokenBegin, bufpos - tokenBegin + 1);
    else
      return new String(buffer, tokenBegin, bufsize - tokenBegin)
        + new String(buffer, 0, bufpos + 1);
  }

  public final char[] GetSuffix(int len) {
    char[] ret = new char[len];

    if ((bufpos + 1) >= len)
      System.arraycopy(buffer, bufpos - len + 1, ret, 0, len);
    else {
      System.arraycopy(buffer, bufsize - (len - bufpos - 1), ret, 0, len - bufpos - 1);
      System.arraycopy(buffer, 0, ret, len - bufpos - 1, bufpos + 1);
    }

    return ret;
  }

  public void Done() {
    buffer = null;
    bufline = null;
    bufcolumn = null;
  }

  /**
   * Method to adjust line and column numbers for the start of a token.
   */
  public void adjustBeginLineColumn(int newLine, int newCol) {
    int start = tokenBegin;
    int len;

    if (bufpos >= tokenBegin) {
      len = bufpos - tokenBegin + inBuf + 1;
    } else {
      len = bufsize - tokenBegin + bufpos + 1 + inBuf;
    }

    int i = 0, j = 0, k = 0;
    int nextColDiff = 0, columnDiff = 0;

    while (i < len && bufline[j = start % bufsize] == bufline[k = ++start % bufsize]) {
      bufline[j] = newLine;
      nextColDiff = columnDiff + bufcolumn[k] - bufcolumn[j];
      bufcolumn[j] = newCol + columnDiff;
      columnDiff = nextColDiff;
      i++;
    }

    if (i < len) {
      bufline[j] = newLine++;
      bufcolumn[j] = newCol + columnDiff;

      while (i++ < len) {
        if (bufline[j = start % bufsize] != bufline[++start % bufsize])
          bufline[j] = newLine++;
        else
          bufline[j] = newLine;
      }
    }

    line = bufline[j];
    column = bufcolumn[j];
  }
}
