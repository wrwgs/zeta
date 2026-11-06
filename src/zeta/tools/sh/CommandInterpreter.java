package zeta.tools.sh;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.StringTokenizer;

import zeta.agl.BufferSeq;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.form.ChoiceForm;
import zeta.form.Form;
import zeta.session.Diag;
import zeta.session.Errors;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.TextEditor;

/**
 * The shells command interpreter.
 *
 * @version $Id: CommandInterpreter.pizza,v 1.5 2000/05/14 17:43:32 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Config scripts are read in the 1998 order: {@code $zeta.home/lib/zetarc} (via
 * {@code session.getLibDir()}), then {@code ~/.zetarc}, then {@code ./zetarc}.
 *
 * <p>PORT NOTE: {@link #eval} wrapped {@code editor.saveRelated()} in
 * {@code catch (IOException e) {}}.  {@code TextEditor.saveRelated} declares no checked
 * exception (in the jar as in the port), so javac rejects the handler as unreachable, and it
 * is: every implementation is compiled by a checked-exception compiler.  The handler is
 * dropped; behaviour is unchanged.
 *
 * <p>{@code String.class} in {@link #tokenize} was a {@code Class.forName} under Pizza; String
 * has no initialiser side effects, so the plain literal is equivalent.
 */
class CommandInterpreter {

  private static final String newl = System.getProperty("line.separator");

  /**
   * The shell state.
   */
  private final ShellState state;

  /**
   * Create a new command interpreter.
   */
  CommandInterpreter(ShellState state) {
    this.state = state;
  }

  /**
   * Read and eval configuration scripts.
   */
  void readAndEvalConfigScripts()
  throws Failures.FormFailed {
    readAndEvalConfigScript(new File(state.session.getLibDir(), "zetarc"));
    readAndEvalConfigScript(
      new File(System.getProperty("user.home"), ".zetarc"));
    readAndEvalConfigScript(new File("zetarc"));
  }

  private void readAndEvalConfigScript(File file)
  throws Failures.FormFailed {
    if (file.canRead())
      readAndEvalFile(file);
    else
      state.sendDebug("config script `" + file.getPath() + "' not found");
  }

  /**
   * Read input from the given file and evaluate.
   * Throw if evaluation of fails; in this case,
   * diagnostics will have been already produced.
   */
  void readAndEvalFile(File file)
  throws Failures.FormFailed {
    try {
      LineNumberReader reader = new LineNumberReader(new FileReader(file));
      do {
        Form topForm = new ChoiceForm(state.session.getForms());
        try {
          if (!readAndEval(topForm, file.toString(), reader)) break;
        } catch (Failures.FormFailed e) {
          state.sendDebug(
            "evaluation of `" + file.getName() + "'failed before line " +
            reader.getLineNumber()
          );
        }
      } while (true);
    } catch (IOException e) {
      state.sendDiag(
        new Diag(
              ShellState.defaultLocator,
              Diag.Category.Error,
              "cannot open file `" + file.getName() + "'")
      );
      throw new Failures.FormFailed("EvalFile");
    }
  }


  /**
   * Read input from the given reader and evaluate it for the given
   * form.  Return true, if more data can be read from
   * <code>reader</code> and false if eof is encountered. Throw if
   * evaluation of form fails; in this case, diagnostics will have
   * been already produced.  The passed inputName is used in
   * diagnostics produced by this method.  */
  boolean readAndEval(Form form, String inputName, BufferedReader reader)
  throws Failures.FormFailed {
    Pair<String[], Locator> p = read(inputName, reader);
    if (p != null) {
      String[] words = p.fst;
      if (words.length > 0) eval(form, p.snd, words);
      return true;
    } else return false;
  }

  /**
   * Read input from the given string and evaluate it for the
   * given form.
   */
  boolean readAndEval(Form form, String inputName, String input)
  throws Failures.FormFailed {
    return readAndEval(form, inputName,
                       new BufferedReader(new StringReader(input)));
  }


  /**
   * Read one command.  Returns a pair of the command's words and its
   * locator, or null at end of input.
   */
  Pair<String[], Locator> read(String inputName,
            BufferedReader reader)
  throws Failures.FormFailed {
    String command = readCommand(reader);
    if (command == null)
      // eof reached
      return null;

    Locator loc;
    if (reader instanceof LineNumberReader)
      loc = new ShellLocator(inputName + " near line " +
                             ((LineNumberReader) reader).getLineNumber());
    else
      loc = new ShellLocator(inputName);
    String[] words = tokenize(loc, command);
    return Pair.Pair(words, loc);
  }

  /**
   * Evaluate the given array of words for the given form. Throw if
   * evaluation of form fails; in this case, diagnostics will have
   * been already produced using the given locator. */
  void eval(Form form, Locator loc, String[] words)
  throws Failures.FormFailed {
    if (words.length == 0 || words[0].startsWith("#")) {
      // ignore empty or comment line
      return;
    }
    TextEditor editor = state.session.getTextEditor();
    if (editor != null)
      // PORT NOTE: the unreachable `catch (IOException e) {}` is dropped (class comment).
      editor.saveRelated();
    state.session.checkExternalData();
    int end =
      form.parse(state.session, loc, words, 0);
    if (end < 0) {
      state.sendDiag(
        new Diag(loc, Diag.Category.Error,
                 "syntax error while evaluating form")
      );
      throw new Failures.FormFailed("TopLevel");
    }
    else if (!(end == words.length)) {
      state.sendDiag(
        new Diag(loc, Diag.Category.Error,
                 "unrecognized input while evaluating form: `" +
                 words[end] + " ...'")
      );
      throw new Failures.FormFailed("TopLevel");
    }
    state.session.checkExternalData();
  }

  /**
   * Read a command line from the given buffered reader. Return null
   * if eof is reached. */
  private String readCommand(BufferedReader reader) {
    String res = null;
    do {
      String line;
      try {
        line = reader.readLine();
      }
      catch (IOException e) {
        throw new Errors.PhysicalError(e);
      }
      if (line != null) {
        res = res != null ? res + newl + line : line;
        if (isBalanced(res)) break;
      } else
        break;
    } while (true);
    return res;
  }

  /**
   * Tokenize a string into a sequence of words, handling quoted words.
   * Throws (after producing diagnostics) if unbalanced quotes are encountered.
   */
  String[] tokenize(Locator loc, String s)
  throws Failures.FormFailed {
    StringTokenizer tkn = new StringTokenizer(s, " \n\t\r\"\\", true);
    Seq<String> buf = new BufferSeq<String>();
    String quoted = null;
    while (tkn.hasMoreTokens()) {
      String tk = tkn.nextToken();
      if (quoted != null) {
        if (tk.equals("\\") && tkn.hasMoreTokens()) {
          tk = tkn.nextToken();
          if (tk.equals("\"")) {
            // escaped "
            quoted += tk;
          } else {
            quoted += "\\";
            quoted += tk;
          }
        }
        else if (tk.equals("\"")) {
          buf = buf.append(quoted);
          quoted = null;
        } else
          quoted += tk;
      } else {
        if (tk.equals("\"")) {
          quoted = "";
        }
        else if (tk.equals("\\") && tkn.hasMoreTokens()) {
          // see if this \ has to be merged with next token
          tk = tkn.nextToken();
          if (!isSpaceToken(tk)) {
            buf = buf.append("\\" + tk);
          } else
            buf = buf.append("\\").append(tk);
        }
        else if (isSpaceToken(tk)) {
          // ignore whitespace
        } else {
          buf = buf.append(tk);
        }
      }
    }
    if (quoted != null) {
      state.sendDiag(
        new Diag(loc,
                 Diag.Category.Error,
                 "unclosed quote")
      );
      throw new Failures.FormFailed("tokenize");
    }
    return buf.toArray(String.class);
  }

  private static boolean isSpaceToken(String tk) {
    return tk.equals(" ") || tk.equals("\n") || tk.equals("\r") ||
                          tk.equals("\t");
  }

  private boolean isBalanced(String s) {
    boolean quoted = false;
    for (int i = 0; i < s.length(); i++) {
      switch (s.charAt(i)) {
      case '\\':
        i++;
        break;
      case '\"':
        quoted = !quoted;
      }
    }
    return !quoted;
  }

}
