package zeta.tools.sman;

/**
 * Persistent storage manager.
 *
 * @version $Id: Storage.pizza,v 1.5 1998/09/17 23:09:57 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * Two single-case ADTs ({@link Handle} and {@link Dictionary}) whose case name equals the
 * class name: no {@code $$tag} field is generated.  The factory for {@code Handle} is
 * {@code private static} (source says {@code private case}) and for {@code Dictionary} is
 * package-private static.
 *
 * The Pizza-generated inner-class accessor {@code Handle.access$000()} is not reproduced:
 * it existed only so that outer-class {@code Storage} closures could reach {@code Handle.name}
 * through the Pizza closure mechanism.  In the port, {@code Storage} is in the same
 * compilation unit and can access the private field directly.
 *
 * <p><b>PORT CHANGE</b> ({@link #create}): {@code Storage.create} in the 2002 build sets
 * {@code baseDir} but never creates the directory, so the first flush throws
 * {@code FileNotFoundException: ZETA/SMproject.n (No such file or directory)}.  The current
 * behaviour is an unconditional exception -- nothing can regress -- and the fix is a one-line
 * {@code mkdirs()} call.  See {@code docs/serialization.md} §3 for the full analysis.
 */

// FIXME: implement cache clearing (?)

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.BitSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Set;
import zeta.session.Errors;
import zeta.session.Failures;
import zeta.session.StorageHandle;
import zeta.util.DebugWriter;
import zeta.util.Name;

class Storage {

  // STRUCTURES

  /**
   * A class for representing storage handles.
   */
  static class Handle implements StorageHandle {
    private Name name;

    Handle(Name name) {
      this.name = name;
    }

    /** Pizza's generated case factory. */
    private static Handle Handle(Name name) {
      return new Handle(name);
    }

    public String toString() {
      return name.getRepr();
    }

    public boolean equals(Object other) {
      if (this == other)
        return true;
      else if (other instanceof Handle)
        return name.equals(((Handle) other).name);
      else
        return false;
    }

    public int hashCode() {
      return name.hashCode();
    }
  }

  /**
   * A class for storing the dictionary of this storage handler.
   */
  static class Dictionary implements Serializable {
    BitSet collectedHandles;
    int nextFreeHandle;

    Dictionary(BitSet collectedHandles, int nextFreeHandle) {
      this.collectedHandles = collectedHandles;
      this.nextFreeHandle = nextFreeHandle;
    }

    /** Pizza's generated case factory. */
    static Dictionary Dictionary(BitSet collectedHandles, int nextFreeHandle) {
      return new Dictionary(collectedHandles, nextFreeHandle);
    }
  }


  // CONSTANTS

  /**
   * Whether we should issue timing messages.
   */
  private static final boolean timing =
    System.getProperty("zeta.debug.timing") != null;

  /**
   * A debug writer.
   */
  private static DebugWriter debug =
    DebugWriter.make(new Name("STORAGE"), true);


  // FIELDS

  /**
   * The directory where to store files.
   */
  private File baseDir;

  /**
   * The dictionary.
   */
  private Dictionary dict;

  /**
   * Whether the dictory needs to be flushed.
   */
  private boolean dictNeedsFlush = false;

  /**
   * A cache of objects being hold in memory.
   */
  private Map<Handle, Serializable> cache = new HashMap();

  /**
   * A set of handles which need flushing.
   */
  private Set<Handle> needsFlush = new HashSet();

  /**
   * A counter for anonymous handles.
   */
  private int handleCounter = 0;

  /**
   * The name of the master file.
   */
  private static final String dictName = "SM.d";


  // METHODS

  /**
   * Create a storage manager in the given directory.
   * <p>
   * <b>PORT CHANGE</b>: the original did not create the directory, so the first flush
   * always threw {@code FileNotFoundException}.  {@code dir.mkdirs()} is called here; the
   * current behaviour is an unconditional exception so nothing can regress.
   */
  static synchronized Storage create(File dir) {
    Storage manager = new Storage();
    manager.baseDir = dir;
    // PORT CHANGE: original omitted this call; see class-level PORT CHANGE note.
    dir.mkdirs();
    manager.dict = new Dictionary(new BitSet(), 0);
    manager.dictNeedsFlush = true;
    return manager;
  }

  /**
   * Open an exisiting storage manager in the given directory.
   */
  static synchronized Storage open(File dir)
  throws Failures.NoSuchEntity {
    Storage manager = new Storage();
    manager.baseDir = dir;
    try {
      manager.readDict();
    }
    catch (Errors.PhysicalError e) {
      if (e.exception instanceof FileNotFoundException) {
        throw new Failures.NoSuchEntity(dir.toString());
      } else
        throw e;
    }
    return manager;
  }

  /**
   * Return base directory of storage manager.
   */
  public File getBaseDir() {
    return baseDir;
  }

  /**
   * Flush any unwritten data.
   */
  public void flush() {
    needsFlush.apply(h -> {
      if (cache.defines(h)) {
        write(new File(baseDir, h.name.getRepr()), cache.get(h));
      }
    });
    needsFlush = new HashSet();
    if (dictNeedsFlush) {
      writeDict();
      dictNeedsFlush = false;
    }
  }

  /**
   * Write out the dictionary.
   */
  private void writeDict() {
    write(new File(baseDir, dictName), dict);
  }

  /**
   * Read in the dictionary.
   */
  private void readDict() {
    try {
      dict = (Dictionary) read(new File(baseDir, dictName));
    }
    catch (ClassCastException e) {
      throw new Errors.PhysicalError(e);
    }
  }

  /**
   * Create a new anonymous handle.
   */
  synchronized StorageHandle newHandle() {
    int h = -1;
    for (int i = 0; i < dict.collectedHandles.size(); i++) {
      if (dict.collectedHandles.get(i)) {
        h = i;
        dict.collectedHandles.clear(i);
        break;
      }
    }
    if (h == -1) {
      h = ++dict.nextFreeHandle;
    }
    Handle handle = new Handle(new Name("SM" + h + ".a"));
    dictNeedsFlush = true;
    return handle;
  }

  /**
   * Create a new statically named handle.
   */
  static synchronized StorageHandle makeStaticHandle(String string) {
    return new Handle(new Name("SM" + string + ".n"));
  }

  /**
   * Remove an handle, and the storage associated with it.
   */
  synchronized void freeHandle(StorageHandle shandle) {
    Handle handle = (Handle) shandle;
    cache = cache.undefine(handle);
    String repr = handle.name.getRepr();
    new File(baseDir, repr).delete();
    if (repr.endsWith(".a")) {
      int h = Integer.parseInt(repr.substring(2, repr.length() - 2));
      dict.collectedHandles.set(h);
      dictNeedsFlush = true;
    }
  }

  /**
   * Store the given object under handle.
   */
  synchronized void store(StorageHandle shandle, Serializable object) {
    Handle handle = (Handle) shandle;
    cache = cache.define(handle, object);
    needsFlush = needsFlush.include(handle);
    // write(new File(baseDir, handle.name.getRepr()), object);
  }

  /**
   * Load the given object under handle. Returns null if an object
   * was never stored.
   */
  synchronized Serializable load(StorageHandle shandle) {
    Handle handle = (Handle) shandle;
    try {
      return cache.get(handle);
    }
    catch (java.util.NoSuchElementException e) {}
    try {
      Serializable object = read(new File(baseDir, handle.name.getRepr()));
      cache = cache.define(handle, object);
      return object;
    }
    catch (Errors.PhysicalError e) {
      if (e.exception instanceof FileNotFoundException)
        return null;
      else
        throw e;
    }
  }

  /**
   * Write the given object to file.
   */
  private void write(File file, Serializable object) {
    long start = 0;
    if (timing) {
      start = System.currentTimeMillis();
    }
    try {
      OutputStream stream =
        new GZIPOutputStream(new FileOutputStream(file), 4096);
      // new BufferedOutputStream(new FileOutputStream(file), 4096);
      ObjectOutputStream ostream = new ObjectOutputStream(stream);
      ostream.writeObject(object);
      ostream.close();
      if (timing) {
        debug.send("serializing " + file + " " +
                   (System.currentTimeMillis() - start) + "ms");
      }
    }
    catch (IOException e) {
      throw new Errors.PhysicalError(e);
    }
  }

  /**
   * Read an object from file.
   */
  private Serializable read(File file) {
    long start = 0;
    if (timing) {
      start = System.currentTimeMillis();
    }
    try {
      InputStream stream =
        new GZIPInputStream(new FileInputStream(file), 4096);
      // new BufferedInputStream(new FileInputStream(file), 4096);
      ObjectInputStream istream = new ObjectInputStream(stream);
      Serializable object = (Serializable) istream.readObject();
      istream.close();
      if (timing) {
        debug.send("de-serializing " + file + " " +
                   (System.currentTimeMillis() - start) + "ms");
      }
      return object;
    }
    catch (IOException e) {
      throw new Errors.PhysicalError(e);
    }
    catch (ClassNotFoundException e) {
      throw new Errors.PhysicalError(e);
    }
  }

}
