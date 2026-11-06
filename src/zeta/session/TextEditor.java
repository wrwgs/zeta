package zeta.session;

import java.io.File;
import java.io.IOException;

/**
 * The interface of a text editor attached to a session.
 *
 * @version $Id: TextEditor.pizza,v 1.3 2000/05/08 07:07:43 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * Note the parameter type: `Locator.VisitCapability.TextVisit` is one of the nested case
 * classes of {@link Locator}, which is why that ADT's nesting had to be reproduced exactly
 * rather than flattened.
 */
public interface TextEditor {

  /** */
  public String getButtonName();

  /** */
  public void commit() throws IOException;

  /** */
  public void add(Locator.VisitCapability.TextVisit cap);

  /** */
  public void visit(Locator.VisitCapability.TextVisit cap);

  /** */
  public void reset();

  /** */
  public void open(File file);

  /** */
  public void unlock(File file);

  /** */
  public void reopen(File file);

  /** */
  public void close(File file, boolean dontSave);

  /** */
  public void save(File file, boolean silently);

  /** */
  public void saveRelated();
}
