package zeta.form;

import java.io.File;

import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;

/**
 * The class of file selector forms. <p>
 *
 * A file form represents file input.
 *
 * @version $Id: FileForm.pizza,v 1.3 1998/10/27 11:04:19 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * Public field order on {@code FileForm} is {@code state}, then {@code mode} -- the source
 * order, which is also what the reference has. The nested {@code Mode} class is declared
 * between them in the source; that does not affect field order.
 *
 * {@code Mode} is the only multi-case ADT in this file: three cases declared <em>without</em>
 * parentheses, so they are {@code public static final} constants on a concrete base with a
 * package-private {@code Mode(int)} constructor and a public final
 * {@code zeta$form$FileForm$Mode$$tag}. Field order is the three constants, then the tag, as
 * Pizza emitted it. Tag values 1/2/3 were read from the reference with
 * {@code harness/adt_tags.py}, not derived from declaration order (they happen to agree).
 *
 * {@code State} is a single-case class whose case name equals the class name: no tag, one
 * public non-final field {@code file}, a public constructor and the static factory.
 *
 * Pizza's {@code switch (mode)} over the constants becomes a switch on the tag. The
 * {@code TAG_} constants are private to {@code Mode} but visible to {@code parse}, which
 * lives in the same top-level class. The source's {@code EXDIRECTORY} arm falls off the end
 * of the switch and the source has no default arm, so the rewritten switch has an empty
 * {@code default} -- which is what {@code PLAIN} always took.
 */
public class FileForm extends Form {

  /** The state of the file form. */
  public final State state;

  /** The class of file form states. */
  public static class State extends FormState {

    public File file;

    public State(File file) {
      this.file = file;
    }

    /** Pizza's generated case factory. */
    public static State State(File file) {
      return new State(file);
    }
  }

  /** The mode of the file form. */
  public final Mode mode;

  /** The class of file form modes. */
  public static class Mode {

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_PLAIN = 1;
    private static final int TAG_EXFILE = 2;
    private static final int TAG_EXDIRECTORY = 3;

    /** Some arbitrary file name. */
    public static final Mode PLAIN = new Mode(TAG_PLAIN);

    /** Must be existent regular file. */
    public static final Mode EXFILE = new Mode(TAG_EXFILE);

    /** Must be existent directory. */
    public static final Mode EXDIRECTORY = new Mode(TAG_EXDIRECTORY);

    /** Pizza's discriminant. Public and final because compiled consumers read it. */
    public final int zeta$form$FileForm$Mode$$tag;

    Mode(int tag) {
      this.zeta$form$FileForm$Mode$$tag = tag;
    }
  }

  /**
   * Construct a file form from a mode and state.
   */
  public FileForm(Mode mode, State state) {
    this.mode = mode;
    this.state = state;
  }

  /**
   * Parse the given sequence of words starting at index. Takes
   * the first word in the sequence and stores it in the state.
   * According to mode, existence of a file or directory is checked,
   * and an exception may be thrown.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start)
  throws Failures.FormFailed {
    if (start < words.length) {
      File file = new File(words[start]);
      switch (mode.zeta$form$FileForm$Mode$$tag) {
      case Mode.TAG_EXFILE:
        if (!file.isFile()) {
          session.send(locator.getTool(),
                       new Diag(locator,
                                Diag.Category.Error,
                                "file `" + file + "' does not exists"));
          throw new Failures.FormFailed("FileForm");
        }
        break;
      case Mode.TAG_EXDIRECTORY:
        if (!file.isDirectory()) {
          session.send(locator.getTool(),
                       new Diag(locator,
                                Diag.Category.Error,
                                "directory `" + file + "' does not exists"));
          throw new Failures.FormFailed("FileForm");
        }
        break;
      default:
        break;
      }
      state.file = file;
      return start + 1;
    } else
      return -1;
  }

  /**
   * Unparse the internal state of the form. Returns the name of the
   * file.
   */
  public String[] unparse(Session session) {
    return new String[]{state.file != null ? state.file.toString() : ""};
  }

  /**
   * Get syntax description
   */
  public String getSyntax() {
    return "<FILENAME>";
  }

}
