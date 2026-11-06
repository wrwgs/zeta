package zeta.form;

import zeta.agl.ArraySeq;
import zeta.session.ContentType;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.util.Name;

/**
 * The class of content type forms. <p>
 *
 * Inputs an existing content type of the session system.
 *
 * @version $Id: ContentTypeForm.pizza,v 1.3 1998/10/27 11:04:17 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * {@code State} is a single-case class whose case name equals the class name: no
 * {@code $$tag}, one public non-final field {@code type}, a public constructor and the static
 * factory.
 *
 * The {@code fun} literal in {@code getRange} becomes a lambda. That removes two members the
 * reference has: the package-private bridge
 * {@code $closure$zeta$form$ContentTypeForm$State$0d(ContentType,Object[])} and the generated
 * class {@code ContentTypeForm$State$$closures}. Nothing outside that pair refers to either
 * -- checked by searching all 905 reference class files for the bridge's name, which occurs
 * only in {@code ContentTypeForm$State} and in {@code ...$$closures} itself -- so dropping
 * both is invisible to compiled consumers. {@code map} on the ported {@code ArraySeq} takes a
 * {@code java.util.function.Function}, so no {@code Closures.of} wrapper is needed.
 *
 * {@code defaultState} is {@code public static} and <em>not</em> final in the reference; kept
 * that way, since it is a mutable session-wide default a GUI may replace.
 *
 * The {@code (State)state} downcasts are the source's own: {@code ListForm} stores the state
 * at its own {@code ListForm.State} type and this class never narrows the field.
 */
public class ContentTypeForm extends ListForm {

  /** The class of content type form states. */
  public static class State extends ListForm.State {

    public ContentType type;

    public State(ContentType type) {
      this.type = type;
    }

    /** Pizza's generated case factory. */
    public static State State(ContentType type) {
      return new State(type);
    }

    /**
     * Get the range list -- all existing content types in the session.
     */
    public String[] getRange(Session session) {
      return new ArraySeq<ContentType>(session.getContentTypes())
                         .map(t -> t.name.toString())
                         .toArray(String.class);
    }

    /** Get the index of the selected object in the range, or -1 if no
        selection. */
    public int getSelected(Session session) {
      if (type != null) {
        ContentType[] types = session.getContentTypes();
        for (int i = 0; i < types.length; i++) {
          if (types[i] == type) return i;
        }
      }
      return -1;
    }

    /** Set the index of the selected object in the range. */
    public void setSelected(Session session, int i, Object source) {
      ContentType[] types = session.getContentTypes();
      if (i >= 0 && i < types.length)
        type = types[i];
      notifyChange(source);
    }

  }

  /** A static default state. */
  public static State defaultState = new State(null);

  /**
   * Construct a content type form from a state.
   */
  public ContentTypeForm(State state) {
    super(state);
  }

  /**
   * Construct a content type form for the default state.
   */
  public ContentTypeForm() {
    this(defaultState);
  }

  /**
   * Parse the given sequence of words starting at index. Takes
   * the first word in the sequence and tries to resolve it as
   * a content type. Throws if resolution fails.
   */
  public int parse(Session session, Locator locator,
                   String[] words, int start)
  throws Failures.FormFailed {
    if (start < words.length) {
      Name name = new Name(words[start]);
      State cstate = (State)state;
      try {
        ContentType old = cstate.type;
        cstate.type = session.lookupContentType(name);
        if (old != cstate.type) cstate.notifyChange(this);
        return start + 1;
      }
      catch (Failures.NoSuchEntity e) {
        session.send(
          locator.getTool(),
          new Diag(locator,
                   Diag.Category.Error,
                   "content type `" + name + "' does not exists")
        );
        throw new Failures.FormFailed("ContentTypeForm");
      }
    }
    else
      return -1;
  }

  /**
   * Unparse the internal state of the form. Returns the name of the
   * content type.
   */
  public String[] unparse(Session session) {
    State cstate = (State)state;
    if (cstate.type != null)
      return new String[]{cstate.type.name.toString()};
    else
      return new String[]{""};
  }

  /**
   * Get syntax description
   */
  public String getSyntax() {
    return "<CONTENT-TYPE>";
  }

}
