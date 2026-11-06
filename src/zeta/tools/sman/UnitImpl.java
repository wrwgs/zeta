package zeta.tools.sman;

/**
 * The unit class implementation.
 *
 * @version $Id: UnitImpl.pizza,v 1.8 2000/05/12 07:42:56 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * {@link ContentInfo} is a two-case ADT (not sealable: nothing outside the file extends it,
 * but it is not abstract in the reference -- it has the base tag constructor -- and can't be
 * made abstract because that is an observable ABI change).  Switched on the tag.
 *
 * {@link PersistentContentInfo} is similarly two-case; not sealed for the same reason.
 *
 * {@link PersistentContents} is a single-case ADT (case name = class name): no tag.
 *
 * {@link UptodateChecker} is a package-private nested static class.  The 2002 jar had a
 * Pizza-generated {@code access$000()} accessor on {@code UnitImpl} that the checker used to
 * reach {@code contents} through the closure mechanism; in the port the checker is a static
 * inner class with direct access to the outer instance.  The accessor is not reproduced because
 * nothing outside {@code sman} references it.
 *
 * The {@code specialMap} field holds a {@code Map<char,char>} in the source (Pizza allowed
 * primitive type parameters), erased to raw {@code Map} in the jar.  Ported as raw {@code Map}
 * and kept as such until the full port replaces AGL with generic Java collections.
 *
 * <p>PORT CHANGE (cancellation; see {@link ComputationInterrupted}): {@link #query} is the
 * make loop -- every target a command computes, and every dependency of it, is computed by a
 * nested {@code query} -- so it is the checkpoint between targets: before asking an adaptor
 * to compute a target that is not up to date, it unwinds if the evaluation has been
 * interrupted.  An up-to-date target is returned without a check, since nothing is computed.
 * A target is marked computed only by the adaptor's own {@link #define}, at the end of its
 * computation, so a target whose computation is interrupted is never marked: the exception
 * leaves {@code tryCompute} before the {@code define} (it is not a
 * {@code Failures.CannotCompute}, so the loop over adaptors does not try the next one).
 * Contents finished before the interrupt stay defined and up to date; they are complete.
 */

import java.io.Serializable;
import java.util.Date;
import java.util.NoSuchElementException;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Set;
import zeta.agl.Seq;
import zeta.session.Adaptor;
import zeta.session.ComputationInterrupted;
import zeta.session.ContentType;
import zeta.session.Errors;
import zeta.session.Failures;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.util.FatalError;
import zeta.util.Name;

class UnitImpl implements Unit {

  // STRUCTURES

  /**
   * A class giving information about a content in a running session.
   */
  static class ContentInfo {

    /** Pizza's discriminant. Public and final because compiled consumers read it. */
    public final int zeta$tools$sman$UnitImpl$ContentInfo$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_DEFINED = 1;
    private static final int TAG_LINKED  = 2;

    ContentInfo(int tag) {
      this.zeta$tools$sman$UnitImpl$ContentInfo$$tag = tag;
    }

    /**
     * A computed content, calculated by a tool adaptor.
     */
    static class Defined extends ContentInfo {
      StorageHandle handle;
      Set dependencies;
      Adaptor definedBy;
      Date definedAt;
      boolean persistent;

      Defined(StorageHandle handle, Set dependencies, Adaptor definedBy,
              Date definedAt, boolean persistent) {
        super(TAG_DEFINED);
        this.handle = handle;
        this.dependencies = dependencies;
        this.definedBy = definedBy;
        this.definedAt = definedAt;
        this.persistent = persistent;
      }
    }

    /**
     * A linked content.
     */
    static class Linked extends ContentInfo {
      ContentType masterType;

      Linked(ContentType masterType) {
        super(TAG_LINKED);
        this.masterType = masterType;
      }
    }

    /** Pizza's generated case factory. */
    static ContentInfo Defined(StorageHandle handle, Set dependencies,
                                Adaptor definedBy, Date definedAt,
                                boolean persistent) {
      return new Defined(handle, dependencies, definedBy, definedAt, persistent);
    }

    /** Pizza's generated case factory. */
    static ContentInfo Linked(ContentType masterType) {
      return new Linked(masterType);
    }
  }

  /**
   * A class giving information about a content suitable for
   * persistent storage.
   */
  static class PersistentContentInfo implements Serializable {

    /** Pizza's discriminant. Public and final because compiled consumers read it. */
    public final int zeta$tools$sman$UnitImpl$PersistentContentInfo$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_PDEFINED = 1;
    private static final int TAG_PLINKED  = 2;

    PersistentContentInfo(int tag) {
      this.zeta$tools$sman$UnitImpl$PersistentContentInfo$$tag = tag;
    }

    static class PDefined extends PersistentContentInfo {
      ContentType type;
      StorageHandle handle;
      Name[] depNames;
      ContentType[] depTypes;
      Name definedBy;
      Date definedAt;

      PDefined(ContentType type, StorageHandle handle,
               Name[] depNames, ContentType[] depTypes,
               Name definedBy, Date definedAt) {
        super(TAG_PDEFINED);
        this.type = type;
        this.handle = handle;
        this.depNames = depNames;
        this.depTypes = depTypes;
        this.definedBy = definedBy;
        this.definedAt = definedAt;
      }
    }

    static class PLinked extends PersistentContentInfo {
      ContentType type;
      ContentType masterType;

      PLinked(ContentType type, ContentType masterType) {
        super(TAG_PLINKED);
        this.type = type;
        this.masterType = masterType;
      }
    }

    /** Pizza's generated case factory. */
    static PersistentContentInfo PDefined(ContentType type, StorageHandle handle,
                                           Name[] depNames, ContentType[] depTypes,
                                           Name definedBy, Date definedAt) {
      return new PDefined(type, handle, depNames, depTypes, definedBy, definedAt);
    }

    /** Pizza's generated case factory. */
    static PersistentContentInfo PLinked(ContentType type, ContentType masterType) {
      return new PLinked(type, masterType);
    }
  }

  /**
   * A class giving information about all unit contents suitable for
   * persistent storage.
   */
  static class PersistentContents implements Serializable {
    PersistentContentInfo[] infos;

    PersistentContents(PersistentContentInfo[] infos) {
      this.infos = infos;
    }

    /** Pizza's generated case factory. */
    static PersistentContents PersistentContents(PersistentContentInfo[] infos) {
      return new PersistentContents(infos);
    }
  }


  // FIELDS

  /**
   * The session this unit belongs to.
   */
  private final SessionImpl session;

  /*
   * The Name of this unit.
   */
  private final Name name;

  /*
   * The content information of this unit.
   */
  private Map<ContentType, ContentInfo> contents = new HashMap();

  /*
   * The transient contents.
   */
  private Map<ContentType, Serializable> transientContents = new HashMap();

  // VALIDATE: taken from OPAL, should be perhaps reduced
  private static final String specials = "'!@#$%^&*+|~-=\\`{}[]:;<>./?,";
  private static final String placebos = "Q12345678pStmebqOCocialgdsuL";

  @SuppressWarnings("rawtypes")
  private static Map specialMap = new HashMap();
  // FIXME: use an array here for efficiency
  static {
    for (int i = 0; i < specials.length(); i++) {
      specialMap = specialMap.define(specials.charAt(i), placebos.charAt(i));
    }
  }


  // CONSTRUCTION

  UnitImpl(SessionImpl session, Name name) {
    this.session = session;
    this.name = name;
  }


  // PERSISTENCY

  /**
   * Save the information about unit contents into a serializable object.
   */
  Serializable saveContents() {
    Seq<PersistentContentInfo> pinfos = new BufferSeq();
    for (zeta.agl.Enum<Pair<ContentType, ContentInfo>> e = contents.enumerate();
         e.more(); e.advance()) {
      Pair<ContentType, ContentInfo> p = e.current();
      ContentType pFst = p.fst;
      ContentInfo pSnd = p.snd;
      switch (pSnd.zeta$tools$sman$UnitImpl$ContentInfo$$tag) {
      case ContentInfo.TAG_DEFINED: {
        ContentInfo.Defined d = (ContentInfo.Defined) pSnd;
        if (d.persistent) {
          int n = d.dependencies.size();
          Name[] depNames = new Name[n];
          ContentType[] depTypes = new ContentType[n];
          int[] j = {0};
          d.dependencies.apply(s_ -> {
            Target s = (Target) s_;
            depNames[j[0]] = s.unit.getName();
            depTypes[j[0]] = s.type;
            j[0]++;
          });
          pinfos = pinfos.append(
            PersistentContentInfo.PDefined(
              pFst,
              d.handle,
              depNames, depTypes,
              d.definedBy.getName(),
              d.definedAt)
          );
        }
        break;
      }
      case ContentInfo.TAG_LINKED: {
        ContentInfo.Linked l = (ContentInfo.Linked) pSnd;
        pinfos = pinfos.append(
          PersistentContentInfo.PLinked(pFst, l.masterType));
        break;
      }
      default:
        throw FatalError.unexpectedCase(
          "UnitImpl.saveContents: unexpected ContentInfo tag");
      }
    }
    return new PersistentContents(
      pinfos.toArray(PersistentContentInfo.class));
  }

  /**
   * Restore the information about unit contents from a serializable
   * object. It is expected that for the session the adaptors and
   * units have been already initialized.
   */
  void restoreContents(Serializable ser) {
    if (!(ser instanceof PersistentContents))
      throw new Errors.DataInconsistency("unit contents");

    PersistentContentInfo[] pinfos = ((PersistentContents) ser).infos;

    for (int i = 0; i < pinfos.length; i++) {
      switch (pinfos[i].zeta$tools$sman$UnitImpl$PersistentContentInfo$$tag) {
      case PersistentContentInfo.TAG_PDEFINED: {
        PersistentContentInfo.PDefined pd =
          (PersistentContentInfo.PDefined) pinfos[i];
        Set<Target> deps = new HashSet();
        for (int j = 0; j < pd.depNames.length; j++) {
          try {
            deps = deps.include(new Target(
                                  session.getUnit(pd.depNames[j]),
                                  pd.depTypes[j]
                                ));
          }
          catch (Failures.NoSuchEntity ex) {
            throw new Errors.DataInconsistency(
                "referred unit does not exists: " + pd.depNames[j]);
          }
        }
        Adaptor adaptor;
        try {
          adaptor = session.lookupAdaptor(pd.definedBy);
        }
        catch (Failures.NoSuchEntity ex) {
          throw new Errors.DataInconsistency(
              "referred adaptor instance does not exists: " + pd.definedBy
          );
        }
        contents =
          contents.define(pd.type,
                          new ContentInfo.Defined(
                            pd.handle, deps, adaptor, pd.definedAt, true));
        break;
      }
      case PersistentContentInfo.TAG_PLINKED: {
        PersistentContentInfo.PLinked pl =
          (PersistentContentInfo.PLinked) pinfos[i];
        contents = contents.define(pl.type,
                                   new ContentInfo.Linked(pl.masterType));
        break;
      }
      default:
        throw FatalError.unexpectedCase(
          "UnitImpl.restoreContents: unexpected PersistentContentInfo tag");
      }
    }
  }


  // METHODS

  /** */
  public Name getName() {
    return name;
  }

  /** */
  public String getFileName() {
    String s = name.toString();
    int n = s.length();
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < n; i++) {
      char ch = s.charAt(i);
      try {
        char tch = (Character) specialMap.get(ch);
        buf.append("$");
        buf.append(tch);
      }
      catch (NoSuchElementException e) {
        buf.append(ch);
      }
    }
    return buf.toString();
  }

  /** */
  public Serializable query(ContentType type)
    throws Failures.ComputationFailed, Failures.CannotCompute {
    Target spec = new Target(this, type);
    if (new UptodateChecker().run(spec) == null) {
      // try to compute it
      Adaptor[] adaptors = session.findAdaptors(type);
      for (int i = 0; i < adaptors.length; i++) {
        // PORT CHANGE (cancellation): checkpoint between targets; see the class comment.
        ComputationInterrupted.checkpoint();
        try {
          adaptors[i].tryCompute(spec);
          if (!contents.defines(type)) {
            throw new Errors.AssertionFailed(
                "computation of `" + adaptors[i].getName() +
                "' didn't defined demanded content `" + spec + "'");
          }
          return getContent(type, contents.get(type));
        }
        catch (Failures.CannotCompute e) {
          // continue with next adaptor
        }
      }
      // no adaptor found
      throw new Failures.CannotCompute(spec);
    } else {
      try {
        return getContent(type, contents.get(type));
      }
      catch (NoSuchElementException e) {
        // FIXME: this may happen by concurrent behavior. Simply
        // synchronizing query is a bad idea, since tool adaptors may
        // work concurrently on the same unit (its not clear yet how to
        // make the session system thread save).
        throw new Failures.CannotCompute(spec);
      }
    }
  }

  /**
   * Return the actual contents, resolving links.
   */
  private Serializable getContent(ContentType type, ContentInfo info) {
    switch (info.zeta$tools$sman$UnitImpl$ContentInfo$$tag) {
    case ContentInfo.TAG_LINKED: {
      ContentInfo.Linked l = (ContentInfo.Linked) info;
      try {
        return getContent(l.masterType, contents.get(l.masterType));
      }
      catch (NoSuchElementException e) {
        throw new Errors.DataInconsistency("linked content does not exist");
      }
    }
    case ContentInfo.TAG_DEFINED: {
      ContentInfo.Defined d = (ContentInfo.Defined) info;
      if (d.persistent)
        return session.load(d.handle);
      else {
        try {
          return transientContents.get(type);
        }
        catch (NoSuchElementException e) {
          return null;
        }
      }
    }
    default:
      throw FatalError.unexpectedCase(
        "UnitImpl.getContent: unexpected ContentInfo tag");
    }
  }

  /** */
  public synchronized boolean isUptodate(ContentType type) {
    return
      new UptodateChecker().run(new Target(this, type)) != null;
  }

  /*
   * Recursively check for up-to-dateness. For efficiency, its
   * important here to call adaptor methods for the up-to-date check
   * only once for each transient dependency.  For deep dependency
   * trees, this might otherwise easily lead to hundreds of redundant
   * adaptor checks -- and an adaptor check may be expansive, by
   * retrieving for example file modification stamps. */
  // FIXME: concurrency may lead to outdating during the execution of
  // checks. A conceptual clean solution would need to lock the
  // dependency tree first.
  static class UptodateChecker {
    Map<Target, Date> done = new HashMap();

    Date run(Target spec) {
      if (done.defines(spec))
        return done.get(spec);
      else {
        UnitImpl unit = (UnitImpl) spec.unit;
        if (unit.contents.defines(spec.type)) {
          ContentInfo info = unit.contents.get(spec.type);
          switch (info.zeta$tools$sman$UnitImpl$ContentInfo$$tag) {
          case ContentInfo.TAG_DEFINED: {
            ContentInfo.Defined d = (ContentInfo.Defined) info;
            // set done for cyclic visits
            done = done.define(spec, null);
            if (d.dependencies.forall(dep_ -> {
                  Target dep = (Target) dep_;
                  Date date = run(dep);
                  return date != null && !date.after(d.definedAt);
                }) && d.definedBy.isUptodate(spec, d.definedAt)) {
              // okay
              done = done.define(spec, d.definedAt);
              return d.definedAt;
            } else {
              // outdate
              unit.outdate(spec.type);
              return null;
            }
          }
          case ContentInfo.TAG_LINKED: {
            ContentInfo.Linked l = (ContentInfo.Linked) info;
            done = done.define(spec, null);
            Date date = run(new Target(spec.unit, l.masterType));
            if (date == null) {
              unit.outdate(spec.type);
              return null;
            } else {
              done = done.define(spec, date);
              return date;
            }
          }
          default:
            throw FatalError.unexpectedCase(
              "UptodateChecker.run: unexpected ContentInfo tag");
          }
        } else {
          done = done.define(spec, null);
          return null;
        }
      }
    }
  }

  /** */
  public synchronized void outdate(ContentType type) {
    if (contents.defines(type)) {
      ContentInfo info = contents.get(type);
      contents = contents.undefine(type);
      switch (info.zeta$tools$sman$UnitImpl$ContentInfo$$tag) {
      case ContentInfo.TAG_DEFINED: {
        ContentInfo.Defined d = (ContentInfo.Defined) info;
        if (d.persistent) {
          if (!d.definedBy.notifyOutdate(new Target(this, type),
                                         d.definedAt,
                                         d.handle)) {
            session.free(d.handle);
          }
        }
        break;
      }
      default:
        // TAG_LINKED: no action needed
        break;
      }
    }
  }

  /** */
  public synchronized void define(ContentType type,
                                   Serializable content,
                                   boolean persistent,
                                   Date createdAt,
                                   Target[] deps,
                                   Adaptor adaptor) {
    /////////////////////////////////////////////////////////////
    // treat all unit contents as transient (there are serious efficiency
    // problems with serialization)
    persistent = false;
    ////////////////////////////////////////////////////////////
    StorageHandle handle = null;
    if (contents.defines(type)) {
      ContentInfo info = contents.get(type);
      switch (info.zeta$tools$sman$UnitImpl$ContentInfo$$tag) {
      case ContentInfo.TAG_DEFINED: {
        ContentInfo.Defined d = (ContentInfo.Defined) info;
        handle = d.handle;
        break;
      }
      default:
        break;
      }
    }
    if (persistent) {
      if (handle == null) {
        handle = session.allocate();
      }
      session.store(handle, content);
    } else {
      transientContents = transientContents.define(type, content);
    }
    contents = contents.define(
                 type,
                 ContentInfo.Defined(handle,
                                     HashSet.from(new ArraySeq(deps)),
                                     adaptor,
                                     createdAt,
                                     persistent)
               );
    System.gc();
  }

  /** */
  public void link(ContentType otherType, ContentType type) {
    throw FatalError.notImplemented("content links");
    /*
    if (contents.defines(type)){
      switch (contents.get(type)) {
      case Defined(StorageHandle oldHandle, _, _, _, _):
        session.free(oldHandle);
      }
      contents = contents.undefine(type);
    }
    if (!contents.defines(otherType))
      // handle like outdate
      return;
    contents = contents.define(
                 type,
                 ContentInfo.Linked(otherType)
               );
    */
  }

}
