package zeta.tools.sman;

/**
 * The session class implementation.
 *
 * @version $Id: SessionImpl.pizza,v 1.17 2000/07/26 16:00:38 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * {@link ProjectData} is a single-case ADT (case name = class name): no {@code $$tag} field.
 * The factory is package-private (no access modifier on the source {@code case}).
 *
 * <p><b>PORT NOTE</b> (1998 defect, preserved): {@link #exit} and {@link #abort()} both emit
 * "good bye." via {@link zeta.session.ResultMessage}.  A session told to {@code squit} AND
 * reaching end of input therefore says goodbye twice.  The harness's negtest suite relies on
 * exactly one {@code = good bye.} line on the quit ({@code abort}) path; the exit path is not
 * exercised by the harness.  Do not change the message text or which method emits it.
 *
 * <p><b>PORT NOTE</b> ({@link #versionError}): kept reachable.  It fires when
 * {@link zeta.session.Errors.PhysicalError#exception} is an
 * {@code InvalidClassException} -- the expected outcome when a serialized store written by
 * one build is read by an incompatible later build.  Once {@link Storage#create} creates its
 * directory (see {@code Storage} PORT CHANGE), stores can actually be written, so this handler
 * becomes exercisable.
 *
 * <p><b>No {@code Thread.stop} or interruption code</b> was present in the source.
 *
 * <p><b>No {@code X.class} literals</b> for slot-registering classes ({@code Layout},
 * {@code DeclAttr}, {@code Used}, {@code Act}, {@code Locator}) appear in this file.
 */

// FIXME: persistency disabled, re-enable it some day ...

import java.io.File;
import java.io.InvalidClassException;
import java.io.Serializable;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.form.TopLevelForm;
import zeta.format.Format;
import zeta.session.Adaptor;
import zeta.session.ContentType;
import zeta.session.Errors;
import zeta.session.Failures;
import zeta.session.Listener;
import zeta.session.Locator;
import zeta.session.Message;
import zeta.session.ResultMessage;
import zeta.session.Session;
import zeta.session.SessionStateMessage;
import zeta.session.Shell;
import zeta.session.StorageHandle;
import zeta.session.TextEditor;
import zeta.session.Unit;
import zeta.util.Name;

class SessionImpl implements Session {

  // FIELDS

  /*
   * The persistent storage manager.
   */
  private final Storage storage;

  /*
   * The registered adaptors and their instances.
   */
  private Map<Name, Adaptor> adaptors = new HashMap();
  private Adaptor[] sortedAdaptors = new Adaptor[0];

  /*
   * The registered units.
   */
  private Map<Name, UnitImpl> units = new HashMap();
  private Unit[] sortedUnits = new Unit[0];

  /*
   * The registered listeners.
   */
  private Seq<Pair<Class[], Listener>> listeners = new ArraySeq<Pair<Class[], Listener>>();

  /*
   * The registered content types.
   */
  private Set<ContentType> contentTypes = new HashSet();
  private ContentType[] sortedTypes = new ContentType[0];

  /*
   * The registered commands.
   */
  private Seq<TopLevelForm> forms = new BufferSeq();

  /*
   * The registered shell.
   */
  private Shell shell = null;

  /*
   * The registered editor.
   */
  private TextEditor editor = null;

  /*
   * The Zeta home directory
   */
  private File zetaHome;

  /*
   * The static handle used for the project dictionary
   */
  private static final StorageHandle projectDictHandle =
    Storage.makeStaticHandle("project");

  /**
   * The name of the session in messages.
   */
  private static final Name sessionMsgName =
    new Name("SessionManager");


  // CONSTRUCTORS

  /**
   * Construct a session.
   */
  private SessionImpl(Storage storage) {
    this.storage = storage;
    zetaHome = new File(System.getProperty("zeta.home"));
    if (zetaHome == null)
      throw new Errors.AssertionFailed("property `zeta.home' not defined");
  }

  /**
   * Construct a session to be found in the given directory.
   */
  static SessionImpl open(File dir)
  throws Failures.NoSuchEntity {
    SessionImpl session = new SessionImpl(Storage.open(dir));
    return session;
  }

  /**
   * Create a session in the given directory.
   */
  static SessionImpl create(File dir) {
    SessionImpl session = new SessionImpl(Storage.create(dir));
    return session;
  }


  // PERSISTENCY

  /**
   * A class to represent the persistent data of a project.
   */
  private static class ProjectData implements Serializable {
    Name[] adaptorNames;
    String[] adaptorClasses;
    Name[] unitNames;
    Serializable[] unitContents;

    ProjectData(Name[] adaptorNames, String[] adaptorClasses,
                Name[] unitNames, Serializable[] unitContents) {
      this.adaptorNames = adaptorNames;
      this.adaptorClasses = adaptorClasses;
      this.unitNames = unitNames;
      this.unitContents = unitContents;
    }

    /** Pizza's generated case factory. */
    static ProjectData ProjectData(Name[] adaptorNames, String[] adaptorClasses,
                                   Name[] unitNames, Serializable[] unitContents) {
      return new ProjectData(adaptorNames, adaptorClasses, unitNames, unitContents);
    }
  }

  /**
   * Save the project data.
   */
  public void flush() {
    // flush all adaptors
    adaptors.apply(p -> {
      ((Adaptor) p.snd).flush();
    });

    // flush project dictionary
    // FIXME: dirty flag?
    Name[] adaptorNames = new Name[adaptors.size()];
    String[] adaptorClasses = new String[adaptors.size()];
    int[] i = {0};
    adaptors.apply(p -> {
      adaptorNames[i[0]] = (Name) p.fst;
      adaptorClasses[i[0]] = p.snd.getClass().getName();
      i[0]++;
    });
    Name[] unitNames = new Name[units.size()];
    Serializable[] unitContents = new Serializable[units.size()];
    i[0] = 0;
    units.apply(p -> {
      unitNames[i[0]] = (Name) p.fst;
      unitContents[i[0]] = ((UnitImpl) p.snd).saveContents();
      i[0]++;
    });
    ProjectData data = new ProjectData(adaptorNames, adaptorClasses,
                                       unitNames, unitContents);
    storage.store(projectDictHandle, data);
    storage.flush();
  }

  /**
   * Initialize the session, loading the project dictionary.
   */
  public void initialize() {
    ProjectData data = (ProjectData) storage.load(projectDictHandle);
    if (data == null) {
      return;
    }
    for (int i = 0; i < data.adaptorNames.length; i++) {
      Class clazz;
      try {
        clazz = Class.forName(data.adaptorClasses[i]);
      }
      catch (ClassNotFoundException e) {
        throw new Errors.PhysicalError(e);
      }
      if (!Adaptor.class.isAssignableFrom(clazz)) {
        throw new Errors.AssertionFailed(clazz.getName() +
                                         " is not a tool adaptor");
      }
      Adaptor adaptor;
      try {
        adaptor = (Adaptor) clazz.newInstance();
      }
      catch (IllegalAccessException e) {
        throw new Errors.PhysicalError(e);
      }
      catch (InstantiationException e) {
        throw new Errors.PhysicalError(e);
      }
      if (!adaptor.getName().equals(data.adaptorNames[i])) {
        throw new Errors.AssertionFailed("adaptor `" + clazz.getName() +
                 "' does not have same name as in last session, `" +
                                         data.adaptorNames[i] + "'");
      }
      adaptors = adaptors.define(data.adaptorNames[i], adaptor);
      adaptor.initialize(this, makeAdaptorHandle(adaptor));
    }
    for (int i = 0; i < data.unitNames.length; i++) {
      units = units.define(data.unitNames[i],
                           new UnitImpl(this, data.unitNames[i]));
    }
    for (int i = 0; i < data.unitNames.length; i++) {
      units.get(data.unitNames[i]).restoreContents(data.unitContents[i]);
    }
  }

  /**
   * Make a unique storage handle for tool adaptor.
   */
  private StorageHandle makeAdaptorHandle(Adaptor adaptor) {
    String name = adaptor.getClass().getName().replace('.', '_');
    return Storage.makeStaticHandle(name);
  }


  // UNITS

  /** */
  public synchronized Unit lookupUnit(Name name)
  throws Failures.UnresolvableName, Failures.AmbigiousName {
    if (units.defines(name)) {
      return units.get(name);
    }
    String repr = name.getRepr();
    int i = repr.indexOf(":");
    if (i >= 0) {
      Name tool = new Name(repr.substring(0, i));
      if (adaptors.defines(tool))
        return adaptors.get(tool).lookupUnit(name);
      else
        throw new Failures.UnresolvableName(name);
    } else {
      Unit[] unit = {null};
      Set<Name> cands = new HashSet();
      // Mutable capture of cands: use array wrapper.
      Set<Name>[] candsRef = new Set[]{cands};
      for (zeta.agl.Enum<Pair<Name, Adaptor>> e = adaptors.enumerate();
           e.more(); e.advance()) {
        Pair<Name, Adaptor> p = e.current();
        Adaptor adaptor = p.snd;
        try {
          unit[0] = adaptor.lookupUnit(name);
          candsRef[0] = candsRef[0].include(unit[0].getName());
        }
        catch (Failures.UnresolvableName ex) {
        }
        catch (Failures.AmbigiousName ex) {
          candsRef[0] = candsRef[0].includeAll(new ArraySeq(ex.fullNames));
        }
      }
      cands = candsRef[0];
      switch (cands.size()) {
      case 0:
        throw new Failures.UnresolvableName(name);
      case 1:
        return unit[0];
      default:
        throw new Failures.AmbigiousName(cands.toArray(Name.class));
      }
    }
  }

  /** */
  public synchronized Unit getUnit(Name name)
  throws Failures.NoSuchEntity {
    if (!units.defines(name))
      throw new Failures.NoSuchEntity(name.getRepr());
    return units.get(name);
  }

  /** */
  public synchronized Unit getOrCreateUnit(Name name) {
    if (units.defines(name))
      return units.get(name);
    UnitImpl unit = new UnitImpl(this, name);
    units = units.define(name, unit);
    sortedUnits = units.range()
                       .sort((u1_, u2_) -> {
                               UnitImpl u1 = (UnitImpl) u1_;
                               UnitImpl u2 = (UnitImpl) u2_;
                               return
                                 u1.getName().getRepr()
                                   .compareTo(u2.getName().getRepr());
                             })
                       .toArray(Unit.class);
    send(sessionMsgName, new SessionStateMessage.UnitAdded(unit));
    return unit;
  }

  /** */
  public synchronized Unit[] getUnits() {
    return sortedUnits;
  }

  /** */
  public synchronized Locator unitLocator(Unit unit) {
    Locator[] l = {null};
    adaptors.exists(p -> {
      Adaptor adaptor = (Adaptor) p.snd;
      l[0] = adaptor.unitLocator(unit);
      return l[0] != null;
    });
    return l[0];
  }


  // TOOL ADAPTORS

  /** */
  public synchronized Adaptor lookupAdaptor(Name name)
  throws Failures.NoSuchEntity {
    if (!adaptors.defines(name))
      throw new Failures.NoSuchEntity(name.getRepr());
    return adaptors.get(name);
  }

  /** */
  public synchronized void registerAdaptor(Class adaptorClass)
  throws Failures.EntityExists {
    if (!Adaptor.class.isAssignableFrom(adaptorClass))
      throw new Errors.AssertionFailed(
              adaptorClass + " does not represent a tool adaptor");
    Adaptor adaptor;
    try {
      adaptor = (Adaptor) adaptorClass.newInstance();
    }
    catch (IllegalAccessException e) {
      throw new Errors.PhysicalError(e);
    }
    catch (InstantiationException e) {
      throw new Errors.PhysicalError(e);
    }
    Name name = adaptor.getName();
    if (adaptors.defines(name))
      throw new Failures.EntityExists(name.getRepr());
    adaptors = adaptors.define(name, adaptor);
    adaptor.initialize(this, makeAdaptorHandle(adaptor));
    sortedAdaptors = adaptors.range()
                             .sort((a1_, a2_) -> {
                                     Adaptor a1 = (Adaptor) a1_;
                                     Adaptor a2 = (Adaptor) a2_;
                                     return
                                       a1.getName().getRepr()
                                         .compareTo(a2.getName().getRepr());
                                   })
                             .toArray(Adaptor.class);
    send(sessionMsgName, new SessionStateMessage.AdaptorAdded(adaptor));
  }

  /**
   * Get all tool adaptors of this session.
   */
  public synchronized Adaptor[] getAdaptors() {
    return sortedAdaptors;
  }

  /**
   * Find tool adaptors which can compute the specified content type.
   */
  Adaptor[] findAdaptors(ContentType type) {
    // FIXME: precalculate this information
    Seq<Adaptor> cands = new BufferSeq();
    for (zeta.agl.Enum<Pair<Name, Adaptor>> e = adaptors.enumerate();
         e.more(); e.advance()) {
      Adaptor a = e.current().snd;
      ContentType[] types = a.computedTypes();
      for (int i = 0; i < types.length; i++) {
        if (types[i].equals(type)) {
          cands = cands.append(a);
          break;
        }
      }
    }
    return cands.toArray(Adaptor.class);
  }

  //
  // /** */
  // public File getToolDir(Name name){
  //  File dir = new File(storage.getBaseDir(), name.getRepr());
  //  if (!dir.isDirectory() && !dir.mkdir())
  //    throw new Errors.AssertionFailed("cannot open or create " + dir);
  //  return dir;
  //}

  /** */
  public File newTempFile(String suffix) {
    try {
      File f = File.createTempFile("zeta", suffix);
      f.deleteOnExit();
      return f;
    } catch (java.io.IOException e) {
      throw new Errors.AssertionFailed("cannot create temporary files: " + e);
    }
  }

  /** */
  public File getLibDir() {
    return new File(zetaHome, "lib");
  }

  /** */
  public File getClassDir() {
    return new File(zetaHome, "classes");
  }


  // UPDATING SOURCES

  /** */
  public void checkExternalData() {
    adaptors.apply(p -> {
      ((Adaptor) p.snd).checkExternalData();
    });
  }

  // MANAGING AUXILIARY PERSISTENT STORAGE

  /** */
  public StorageHandle allocate() {
    return storage.newHandle();
  }

  /** */
  public void free(StorageHandle handle) {
    storage.freeHandle(handle);
  }

  /** */
  public void store(StorageHandle handle, Serializable object) {
    storage.store(handle, object);
  }

  /** */
  public Serializable load(StorageHandle handle) {
    return storage.load(handle);
  }

  // MESSAGE DISPATCHING

  /** */
  public void registerListener(Class[] classes, Listener listener) {
    unregisterListener(listener);
    listeners = listeners.append(Pair.Pair(classes, listener));
  }

  public void unregisterListener(Listener listener) {
    listeners =
      listeners.filter(p -> p.snd != listener);
  }

  /** */
  public void send(Name sender, Message message) {
    listeners.apply(p -> {
      boolean interest = false;
      Class[] classes = p.fst;
      Listener lst = p.snd;
      if (classes == null)
        interest = true;
      else {
        Class mclass = message.getClass();
        for (int j = 0; j < classes.length; j++) {
          if (classes[j].isAssignableFrom(mclass)) {
            interest = true;
            break;
          }
        }
      }
      if (interest) lst.receive(sender, message);
    });
  }

  // CONTENT TYPES

  /** */
  public void registerContentType(ContentType type) {
    if (!contentTypes.contains(type)) {
      contentTypes = contentTypes.include(type);
      sortedTypes = ArraySeq.from(contentTypes)
                            .sort((t1_, t2_) -> {
                                    ContentType t1 = (ContentType) t1_;
                                    ContentType t2 = (ContentType) t2_;
                                    return
                                      t1.toString()
                                        .compareTo(t2.toString());
                                  })
                            .toArray(ContentType.class);
      send(sessionMsgName, new SessionStateMessage.ContentTypeAdded(type));
    }
  }

  /** */
  public ContentType[] getContentTypes() {
    return sortedTypes;
  }

  /** */
  public ContentType lookupContentType(Name name)
  throws Failures.NoSuchEntity {
    ContentType[] type = {null};
    if (contentTypes.exists(t -> {
                              ContentType ct = (ContentType) t;
                              if (ct.name.equals(name)) {
                                type[0] = ct;
                                return true;
                              } else
                                return false;
                            })) {
      return type[0];
    } else
      throw new Failures.NoSuchEntity(name.toString());
  }


  // COMMANDS

  /** */
  public void registerForm(TopLevelForm form) {
    forms = forms.append(form);
    if (shell != null) {
      shell.notifyFormRegister(form);
    }
  }

  /** */
  public void unregisterForm(TopLevelForm form) {
    forms = forms.filter(s -> s != form);
    if (shell != null) {
      shell.notifyFormUnregister(form);
    }
  }

  /** */
  public TopLevelForm[] getForms() {
    return forms.toArray(TopLevelForm.class);
  }

  /** */
  public void registerShell(Shell shell) {
    this.shell = shell;
  }

  /** */
  public void registerTextEditor(TextEditor editor) {
    this.editor = editor;
  }

  /** */
  public TextEditor getTextEditor() {
    return editor;
  }

  /** */
  public void execForm(String prompt, TopLevelForm form)
  throws Failures.FormFailed {
    if (shell != null) {
      shell.exec(prompt, form);
    } else
      throw new Errors.AssertionFailed("no session shell registered");
  }

  // EXITING

  /**
   * Exit a session and the running JVM. Makes actions to perform
   * necessary saves of the sessions data.
   */
  public void exit() {
    flush();
    send(new Name("SMAN"),
         new ResultMessage(Format.literal("good bye.")));
    System.exit(0);
  }

  /**
   * Abort a session and the running JVM. Changes on the sessions data
   * are not saved.
   * <p>
   * <b>PORT NOTE</b> (1998 defect, preserved): both {@code exit()} and {@code abort()}
   * emit "good bye." -- so a session told to quit AND reaching end of input says goodbye
   * twice.  The harness relies on exactly one {@code = good bye.} line on the quit path;
   * do not change the message or which method emits it.
   */
  public void abort() {
    // FIXME: instead of just exisiting here, we need a cleaner
    // way to shut down the storage manager preserving the
    // previous session state (but this currently should work,
    // since the session manager only flushes data if explicitely called)
    send(new Name("SMAN"),
         new ResultMessage(Format.literal("good bye.")));
    System.exit(0);
  }

  /**
   * Report about a runtime exeception, and let the session die.
   */
  public void abort(Throwable e) {
    if (e instanceof Errors.PhysicalError) {
      Errors.PhysicalError pe = (Errors.PhysicalError) e;
      if (pe.exception instanceof InvalidClassException) {
        // most probably a serialization version error
        // FIXME: catch this on lower levels
        versionError(pe.exception);
      } else {
        fatalError("session manager error",
                   ((Errors.PhysicalError) e).exception);
      }
    }
    else if (e instanceof Errors.DataInconsistency) {
      fatalError("project data inconsistency: " +
                 ((Errors.DataInconsistency) e).message,
                 e);
    }
    else if (e instanceof Errors.AssertionFailed) {
      fatalError("programming assertion failed: " +
                 ((Errors.AssertionFailed) e).message,
                 e);
    }
    else if (e instanceof RuntimeException) {
      fatalError("programming error", e);
    }
    else {
      fatalError("internal Java error", e);
    }
    send(new Name("SMAN"),
         new ResultMessage(Format.literal("anyway, good bye.")));
    System.exit(255);
  }

  private static void fatalError(String message, Throwable t) {
    System.err.println();
    System.err.println("An unexpected exception has occured:");
    System.err.println(message);
    System.err.println();
    t.printStackTrace();
    System.err.println();
    System.err.print("Please send a bug report ");
    System.err.println("to zeta@uebb.cs.tu-berlin.de.");
  }

  /**
   * <b>PORT NOTE</b> ({@link #versionError}): kept reachable via
   * {@link #abort(Throwable)}.  Fires when the stored project data was serialized by a
   * different build.  Once {@link Storage#create} creates its directory, stores can be written
   * and this path becomes exercisable.
   */
  private static void versionError(Throwable t) {
    System.err.println();
    System.err.println(
    "The project data is incompatible with this version of ZETA.");
    System.err.println();
    t.printStackTrace();
    System.err.println();
    System.err.println("Please remove the project data and start again.");
    System.err.println("Note that the project data is reconstructable from");
    System.err.println("your sources.");
  }

}
