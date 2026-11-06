package zeta.session;

import java.io.Serializable;
import java.util.Date;

import zeta.util.Name;

/**
 * A unit of a session: a named container of contents, one per content type.
 *
 * @version $Id: Unit.pizza,v 1.6 1999/08/24 07:28:41 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * The interface of the session manager's make-style dependency graph: `query` computes a
 * content on demand, `define` records one together with the targets it was computed from, and
 * `isUptodate`/`outdate` are how the graph is invalidated. `Serializable` is the content type
 * here only because contents may be persisted; see `docs/serialization.md` for why the port
 * owes that nothing.
 */
public interface Unit {

  /** */
  public Name getName();

  /** */
  public String getFileName();

  /** */
  public Serializable query(ContentType type)
      throws Failures.ComputationFailed, Failures.CannotCompute;

  /** */
  public boolean isUptodate(ContentType type);

  /** */
  public void outdate(ContentType type);

  /** */
  public void define(ContentType type,
                     Serializable content,
                     boolean persistent,
                     Date createdAt,
                     Target[] dependencies,
                     Adaptor adaptor);

  /** */
  public void link(ContentType otherType, ContentType type);
}
