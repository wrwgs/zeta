package zeta.form;

import java.util.ArrayList;
import java.util.List;

/**
 * The abstract class of form states, the data model being represented
 * by a form. Each state maintains a set of listeners which are
 * notified if the state is changed.
 *
 * @version $Id: FormState.pizza,v 1.2 2000/05/14 17:43:32 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * No Pizza feature is used here; only three things needed thought.
 *
 * `listeners` becomes an `ArrayList&lt;Listener&gt;` instead of a `Vector`.  The field is
 * private and every access sits inside a `synchronized` method, so `Vector`'s own locking was
 * redundant.  Behaviour is preserved down to the re-entrant case: both notify methods read
 * `size()` once and then index, so a listener that adds or removes a listener from inside its
 * own callback (same thread, re-entrant monitor) is tolerated exactly as before -- the new
 * listener is not visited, and `get(i)` keeps working across the backing-array growth that
 * `add` may cause.  Using an iterator here would *not* be equivalent: it would raise
 * `ConcurrentModificationException` where the 1998 code quietly continued.
 *
 * `java.util.BitSet` is imported by the Pizza source but never used; dropped.
 *
 * {@code Listener} carries one member the source does not mention: Pizza gave every member
 * interface the outer-instance field `public static final FormState this0`, left
 * uninitialised.  It is reproduced (as an explicit `null`) because the reference has it;
 * nothing in the 905 reference classes reads it, and it is static, so it is outside
 * `abi_gate.py`'s field-order check either way.
 */
public abstract class FormState {

  /** The registered listeners. */
  private final List<Listener> listeners = new ArrayList<Listener>();

  /** Whether we are currently visiting change listeners. */
  private boolean visiting = false;

  /** Whether we are currently visiting require listeners. */
  private boolean visitingRequired = false;

  /** The interface of state listener. */
  public interface Listener {

    /** PORT NOTE: Pizza's outer-instance field for a member interface; always null, and
      * read by nothing.  Kept so the compiled shape matches the 2002 class file. */
    FormState this0 = null;

    /** Method being called before the state is required. */
    public void stateRequired(FormState state, Object source);

    /** Method being called when the state changes. */
    public void stateChanged(FormState state, Object source);
  }

  /** An adaptor for state listeners. */
  public static class ListenerAdaptor implements Listener {
    public void stateRequired(FormState state, Object source) {}

    public void stateChanged(FormState state, Object source) {}
  }


  /** Add a state listener. */
  public synchronized void addListener(Listener listener) {
    listeners.add(listener);
  }

  /** Remove the given state listener. */
  public synchronized void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /** Notify all listeners that the state has changed by
      the given source. */
  public synchronized void notifyChange(Object source) {
    if (visiting) return;
    try {
      visiting = true;
      int i = 0;
      int n = listeners.size();
      while (i < n) {
        Listener l = listeners.get(i);
        l.stateChanged(this, source);
        i++;
      }
    }
    finally {
      visiting = false;
    }
  }

  /** Notify all listeners that the state is required by
      the given source. */
  public synchronized void notifyRequired(Object source) {
    if (visitingRequired) return;
    try {
      visitingRequired = true;
      int i = 0;
      int n = listeners.size();
      while (i < n) {
        Listener l = listeners.get(i);
        l.stateRequired(this, source);
        i++;
      }
    }
    finally {
      visitingRequired = false;
    }
  }

}
