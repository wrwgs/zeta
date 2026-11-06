package zeta.tools.latex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;


import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.text.LaTeXFragment;
import zeta.content.text.Text;
import zeta.content.text.TextContentTypes;
import zeta.form.ChoiceForm;
import zeta.form.FileForm;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.SeqForm;
import zeta.form.StringForm;
import zeta.form.TopLevelForm;
import zeta.form.UnitForm;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.ContentType;
import zeta.session.DefaultAdaptor;
import zeta.session.Diag;
import zeta.session.Errors;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.tools.util.SessionUtil;
import zeta.util.FatalError;
import zeta.util.Impure;
import zeta.util.Name;

/**
 * Adaptor for LaTeX sources.
 *
 * @version $Id: LatexAdaptor.pizza,v 1.27 2000/07/17 05:09:11 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * The first stage of the pipeline: it scans LaTeX sources with {@link LatexScanner}, cuts
 * the result into sections and classes ({@link Sectioner}), and defines the `text`,
 * `lang-config`, `latex-fragment` and `dvi` contents of the units it finds.  Loaded by
 * `zetarc` as `plugin zeta.tools.latex.LatexAdaptor`, so the public no-argument constructor
 * and the `Adaptor` methods are the ABI; `TextLocator.getTool` and `LatexFormatter` read the
 * package-private `thisName` and `thisLocator`.
 *
 * <b>Iteration order is observable.</b>  `enterUnits` creates units in the iteration order of
 * the `agl` map of sections, and the order units are created in shows in every artifact
 * that lists them.  All collections stay the `agl` ones the source used, filled by the same
 * sequence of operations, so they iterate exactly as before.  Pizza closures became lambdas
 * where they captured nothing mutable; where they assigned a captured local (`tryCompute`,
 * `locateFile`, `loadSource`) they became loops over the same enumeration, which is what
 * `Aggregate.apply` and `Aggregate.exists` do (neither is overridden by the agl maps or
 * sequences used here) -- `exists` stops at the first hit, and so do the loops.
 *
 * <b>Form actions</b> are `TopLevelForm.Action` lambdas, which throw `Failures.FormFailed`
 * directly (Pizza delivered it wrapped in `pizza.support.ExceptionWrapper`; see
 * `TopLevelForm`).
 *
 * The four case classes `SourceInfo`, `UnitInfo` and `PersistentData` keep Pizza's shape:
 * public-or-package fields in source order, a constructor, and a static factory of the same
 * name.  `PersistentData` and `Sectioner` were `private` in the source; the 2002 class files
 * have them package-private, and so does the port.
 */
public class LatexAdaptor extends DefaultAdaptor {

  // STRUCTURES

  /**
   * Information about a source.
   */
  public static class SourceInfo implements Serializable {
    public File file;
    public File absFile;
    public File[] auxFiles;
    public Diag diag;
    public Date scannedAt;
    public LatexConfig config;

    public SourceInfo(File file, File absFile, File[] auxFiles,
                      Diag diag, Date scannedAt,
                      LatexConfig config) {
      this.file = file;
      this.absFile = absFile;
      this.auxFiles = auxFiles;
      this.diag = diag;
      this.scannedAt = scannedAt;
      this.config = config;
    }

    /** Pizza's generated case factory. */
    public static SourceInfo SourceInfo(File file, File absFile, File[] auxFiles,
                                        Diag diag, Date scannedAt,
                                        LatexConfig config) {
      return new SourceInfo(file, absFile, auxFiles, diag, scannedAt, config);
    }
  }

  /**
   * Information about a unit.
   */
  static class UnitInfo implements Serializable {
    Name name;
    SourceInfo source;
    Text undeliveredText;
    Locator locator;

    UnitInfo(Name name, SourceInfo source, Text undeliveredText,
             Locator locator) {
      this.name = name;
      this.source = source;
      this.undeliveredText = undeliveredText;
      this.locator = locator;
    }

    /** Pizza's generated case factory. */
    static UnitInfo UnitInfo(Name name, SourceInfo source, Text undeliveredText,
                             Locator locator) {
      return new UnitInfo(name, source, undeliveredText, locator);
    }
  }

  /**
   * A wrapper for serializing the adaptors state. AGL types cannot
   * be used here, since generic types cannot be cleanly serialized with
   * the current version of Pizza.
   */
  static class PersistentData implements Serializable {
    File[] searchPath;
    SourceInfo[] sourceInfos;
    UnitInfo[] unitInfos;

    PersistentData(File[] searchPath,
                   SourceInfo[] sourceInfos,
                   UnitInfo[] unitInfos) {
      this.searchPath = searchPath;
      this.sourceInfos = sourceInfos;
      this.unitInfos = unitInfos;
    }

    /** Pizza's generated case factory. */
    static PersistentData PersistentData(File[] searchPath,
                                         SourceInfo[] sourceInfos,
                                         UnitInfo[] unitInfos) {
      return new PersistentData(searchPath, sourceInfos, unitInfos);
    }
  }


  // CONSTANTS

  /**
   * The name of this adaptor.
   */
  static final Name thisName = new Name("LTX");

  /**
   * The locator of control operations of this adaptor.
   */
  static final Locator thisLocator =
    new ControlLocator();

  static class ControlLocator extends Locator {
    public Format toFormat(FormatInfo info) {
      return Format.literal("<LaTeX-Adaptor>");
    }
    public Name getTool() {
      return thisName;
    }
    public boolean equals(Object other) {
      return other instanceof ControlLocator;
    }
  }

  /**
   * The startup message of this adaptor.
   */
  private static final String startupMessage =
    "installed Z LaTeX adaptor v1.4";



  // STATE VARIABLES

  /**
   * The registered sources
   */
  private Map<File, SourceInfo> sourceInfos = new HashMap<File, SourceInfo>();

  /**
   * The units introduced by the registered sources.
   */
  private Map<Name, UnitInfo> unitInfos = new HashMap<Name, UnitInfo>();


  /**
   * The search path.
   */
  private Seq<File> searchPath = new ArraySeq<File>();

  /**
   * Whether persistent data needs to be flushed.
   */
  private boolean needsFlush = false;


  // ADAPTOR INITIALIZATION

  /**
   * Initialize a newly created instance of this adaptor.
   */
  public void initialize(Session session, StorageHandle handle) {
    super.initialize(session, handle);
    sendProgress(startupMessage);
    PersistentData data = (PersistentData) session.load(handle);
    if (data == null) {
      // initialized the first time for this project
      searchPath = new ArraySeq<File>(new File(System.getProperty("user.dir")));
      String latexPath = System.getProperty("zeta.latex.path");
      if (latexPath != null) {
        StringTokenizer tkn = new StringTokenizer(latexPath,
                                                  File.pathSeparator);
        while (tkn.hasMoreTokens())
          searchPath = searchPath.append(new File(tkn.nextToken()));
      }
      needsFlush = true;
    } else {
      // initialize from last session
      searchPath = new ArraySeq<File>(data.searchPath);
      for (int i = 0; i < data.sourceInfos.length; i++) {
        sourceInfos = sourceInfos.define(data.sourceInfos[i].file,
                                         data.sourceInfos[i]);
      }
      for (int i = 0; i < data.unitInfos.length; i++) {
        unitInfos = unitInfos.define(data.unitInfos[i].name,
                                     data.unitInfos[i]);
      }
      checkExternalData();
    }
    session.registerContentType(TextContentTypes.text);
    session.registerContentType(TextContentTypes.config);
    session.registerContentType(TextContentTypes.dvi);
    session.registerContentType(TextContentTypes.latexFragment);
    session.registerForm(loadForm);
    session.registerForm(pathForm);
    session.registerForm(dviForm);
    session.registerForm(viewForm);
  }


  /**
   * Flush internal persistent data.
   */
  public void flush() {
    if (needsFlush) {
      session.store(handle,
                    new PersistentData(
                          searchPath.toArray(File.class),
                          sourceInfos.range().toArray(SourceInfo.class),
                          unitInfos.range().toArray(UnitInfo.class)
                        )
                   );
      needsFlush = false;
    }
  }

  // ADAPTOR METHODS

  /**
   * Get name of this adaptor.
   */
  public Name getName() {
    return thisName;
  }


  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{ TextContentTypes.text, TextContentTypes.config,
                              TextContentTypes.dvi,
                              TextContentTypes.latexFragment };
  }

  /**
   * Lookup a unit by given (possible incomplete) name.  <p>
   *
   * The syntax understood by the LaTeX adaptor is
   *
   * <pre>
   *  [LTX:]section_or_classname[#sourcename]
   * </pre>
   *
   * The LaTeX adaptor first tries to complete missing components of
   * the name against the units it knows about. If several units match
   * the given name, the exception
   * <code>AmbigiousResolution</code> is thrown. If exactly one
   * such unit exists, it will be returned. <p>
   *
   * If no unit of the given name exists, then the adaptor tries to
   * create one. If a source name is given, this source is tried to be
   * located in the search path. If no source name is given, the
   * section_or_classname is tried as a source name. <p>
   *
   * If a source file has been found, than it is scanned and all
   * units found in it are entered into the session system. The
   * LaTeX adaptor then tries once more to complete the given MSL again
   * the known units. It throws <code>UnresolvableName</code> if no completion
   * is found, and <code>AmbigousName</code>, if completions aren't
   * unqiue.
   *
   */
  public Unit lookupUnit(Name name)
  throws Failures.UnresolvableName, Failures.AmbigiousName {
    String[] comps = extractNameComponents(name);
    try {
      return tryMatch(name, comps);
    }
    catch (Failures.UnresolvableName e) {
      File file;
      if (comps[2] != null)
        file = new File(comps[2]);
      else
        file = new File(comps[1]);
      File absFile = locateFile(file);
      if (absFile != null) {
        updateSource(file, absFile, new File[0]);
        return tryMatch(name, comps);
      } else
        throw new Failures.UnresolvableName(name);
    }
  }

  private String[] extractNameComponents(Name name) {
    String[] comps = new String[3];
    String repr = name.getRepr();
    int i = repr.indexOf(":");
    if (i >= 0) {
      comps[0] = repr.substring(0, i);
      repr = repr.substring(i + 1);
    } else
      comps[0] = thisName.getRepr();
    i = repr.indexOf("#");
    if (i >= 0) {
      comps[1] = repr.substring(0, i);
      comps[2] = repr.substring(i + 1);
    } else
      comps[1] = repr;
    // System.err.println("comps " + comps[0] + ", " +
    //                    comps[1] + ", " + comps[2]);
    return comps;
  }

  private Unit tryMatch(Name name, String[] comps)
  throws Failures.UnresolvableName, Failures.AmbigiousName {
    Set<Name> cands =
      unitInfos.domain().filter(
        (Name cand) -> {
          String[] ccomps = extractNameComponents(cand);
          return comps[0].equals(ccomps[0]) &&
                 comps[1].equals(ccomps[1]) &&
                 (comps[2] == null || comps[2].equals(ccomps[2]));
        }
      );
    switch (cands.size()) {
      case 0:
        throw new Failures.UnresolvableName(name);
      case 1:
        return session.getOrCreateUnit(cands.arb());
      default:
        throw new Failures.AmbigiousName(cands.toArray(Name.class));
    }
  }



  /**
   * Run the adaptor to compute the specified content.
   */
  public void tryCompute(Target target)
  throws Failures.ComputationFailed, Failures.CannotCompute {
    Name unitName = target.unit.getName();
    if (!unitInfos.defines(unitName)) {
      // this unit doesn't belongs to my sources
      sendDebug("not my unit: " + unitName);
      throw new Failures.CannotCompute(target);
    }
    UnitInfo info = unitInfos.get(unitName);
    if (target.type.equals(TextContentTypes.dvi)) {
      // compute DVI content
      Seq<Unit> unitsOfSource = new BufferSeq<Unit>().append(target.unit);
      for (Enum<Pair<Name, UnitInfo>> e = unitInfos.enumerate(); e.more(); e.advance()) {
        UnitInfo i = e.current().snd;
        if (i.source.equals(info.source))
          unitsOfSource = unitsOfSource.append(
                              // hmmm ... should exists, but thats not clean
                            session.getOrCreateUnit(i.name)
                          );
      }
      LatexFormatter.computeDoc(session, this, target,
                                info.source.file, unitsOfSource);
    }
    else if (target.type.equals(TextContentTypes.latexFragment)) {
      // compute LaTeX fragment contents.
      // FIXME: their should be a style
      // allowing us to include entire documents in documents
      LaTeXFragment frag = new LaTeXFragment(
                                 "",
                                 "\\includeDocument{" +
                                 info.source.absFile +
                                 "}"
                               );
      target.unit.define(
        target.type,
        frag,
        true,
        new Date(),
        new Target[0],
        this
      );
    }
    else if (target.type.equals(TextContentTypes.text)) {
      // compute the text content
      if (info.undeliveredText == null) {
        // PORT CHANGE (approved by the original author).  The source failed here with
        // "compute called for up-to-date text".  Within one session a delivered text stays
        // defined in its unit, so text is demanded again with no undelivered text left only
        // when the unit has lost its contents -- and contents are never persisted
        // (UnitImpl.define forces persistent = false), so that is the case for every unit
        // whose text was delivered before a `squit', once the project is reopened.  This
        // adaptor's own state is persisted and says the source is up to date, so nothing
        // ever rescanned it and the unit's text could not come back.  Recompute it from the
        // source instead; see rescanSource for why this does not disturb the other units.
        if (!info.source.absFile.isFile()) {
          // vanished since the last command's checkExternalData; it will deal with it
          sendDiag(new Diag(thisLocator,
                            Diag.Category.Error,
                            "file `" + info.source.absFile +
                            "' vanished, cannot recompute text"));
          throw new Failures.ComputationFailed(target);
        }
        rescanSource(info.source);
        if (!unitInfos.defines(unitName)) {
          // the source changed and no longer defines this unit: fail as for any unit
          // which isn't (or is no longer) defined by a source of this adaptor
          sendDebug("not my unit: " + unitName);
          throw new Failures.CannotCompute(target);
        }
        info = unitInfos.get(unitName);
      }
      target.unit.define(TextContentTypes.text,
                         info.undeliveredText,
                         true,
                         info.source.scannedAt,
                         new Target[0],
                         this);
      info.undeliveredText = null;
      needsFlush = true;
    } else {
      // compute the config content
      target.unit.define(TextContentTypes.config,
                         info.source.config,
                         true,
                         info.source.scannedAt,
                         new Target[0],
                         this);
    }
  }

  /**
   * Check if the specified content which was defined at date by this
   * adaptor is up-to-date.
   */
  public boolean isUptodate(Target target, Date definedAt) {
    // System.err.println("after update for " + target + ": " + unitInfos);
    // CHECKME: generically works for all content types?
    Name unitName = target.unit.getName();
    if (unitInfos.defines(unitName)) {
      UnitInfo info = unitInfos.get(unitName);
      return !definedAt.before(info.source.scannedAt);
    } else
      return false;
  }

  /**
   * Called to notify the adaptor that the specified content which was
   * defined at date by this adaptor has been outdated.
   */
  public boolean notifyOutdate(Target target, Date definedAt,
                               StorageHandle handle) {
    return false;
  }

  /**
   * Return a locator for the source of a unit.
   */
  public Locator unitLocator(Unit unit) {
    try {
      UnitInfo uinfo = unitInfos.get(unit.getName());
      if (uinfo.locator != thisLocator)
        return uinfo.locator;
      else
        return new TextLocator(
                     new Name(uinfo.source.absFile.getPath()),
                     0, 0, 0, 0
                   );
    }
    catch (NoSuchElementException e) {
      return null;
    }
  }



  // LOCATING FILES

  /**
   * Locate a file in the search path, trying standard extensions such
   * as .tex.  Returns existing file if file is found, otherwise null.
   */
  File locateFile(File file) {
    if (file.isAbsolute()) {
      return locate(file);
    } else {
      // try to locate it in the working directory
      File actFile = locate(file);
      if (actFile != null) return actFile;
      // try to locate it via search path
      for (Enum<File> e = searchPath.enumerate(); e.more(); e.advance()) {
        actFile = locate(new File(e.current(), file.getPath()));
        if (actFile != null)
          return actFile;
      }
      return null;
    }
  }

  private File locate(File f) {
    if (f.isFile())
      return f;
    else {
      File actFile = new File(f.getPath() + ".tex");
      if (actFile.isFile())
        return actFile;
      else
        return null;
    }
  }

  /**
   * Make diagnostic for a file-not-found error.
   */
  Diag reportFileNotFound(String refKind, Locator locator, String fname) {
    return new Diag(locator,
                    Diag.Category.Error,
                    Format.describe(
                      refKind + " `" + fname + "' not found",
                      "search path",
                      Format.list(Format.empty,
                                  Format.literal(File.pathSeparator),
                                  Format.empty,
                                  (File f) -> Format.literal(f.toString()),
                                  searchPath
                                  )
                    ));
  }


  // LOADING SOURCES

  /**
   * Load source.
   */
  private boolean loadSource(File file) {
    // normalize file name
    String name = file.getPath();
    if (name.endsWith(".tex"))
      file = new File(name.substring(0, name.length() - 4));
    // locate the master file of this source
    if (file.isAbsolute() && file.getParent() != null) {
      // add parent's path to search path
      searchPath = searchPath.prepend(new File(file.getParent()));
    }
    File actFile = locateFile(file);
    if (actFile == null) {
      sendDiag(reportFileNotFound("file", thisLocator, file.getPath()));
      sourceInfos = sourceInfos.undefine(file);
      return false;
    } else {
      if (!sourceInfos.defines(file)) {
        // if this file is an auxiliary file of a known source, load that source instead
        search:
        for (Enum<Pair<File, SourceInfo>> e = sourceInfos.enumerate();
             e.more(); e.advance()) {
          SourceInfo info = e.current().snd;
          for (int i = 0; i < info.auxFiles.length; i++) {
            if (file.equals(info.auxFiles[i]) ||
                actFile.equals(info.auxFiles[i])) {
              file = info.file;
              actFile = info.absFile;
              break search;
            }
          }
        }
      }
      updateSource(file, actFile, new File[0]);
      return true;
    }
  }

  /**
   * Reload all sources (after search path update and adaptor
   * initialization).
   */
  private void reloadSources() {
    sourceInfos.apply(
      (Pair<File, SourceInfo> p) -> {
        loadSource(p.snd.file);
      }
    );
  }

  /**
   * Update all sources. In contrast to reloading, this doesn't checks
   * if files have been relocated if they still exist.
   */
  public void checkExternalData() {
    sourceInfos.apply(
      (Pair<File, SourceInfo> p) -> {
        SourceInfo snd = p.snd;
        if (snd.absFile.isFile()) {
          updateSource(snd.file, snd.absFile, snd.auxFiles);
        } else {
          sendDiag(new Diag(thisLocator,
                            Diag.Category.Warning,
                            "file `" + snd.absFile +
                            "' vanished, will retry to locate it"));
          sourceInfos = sourceInfos.undefine(p.fst);
          needsFlush = true;
          loadSource(snd.file);
        }
      }
    );
  }

  /**
   * Update a source, scanning its contents if not up-to-date.
   * The absFile is supposed to represent the existing source file.
   */
  private void updateSource(File file, File absFile, File[] auxFiles) {
    if (sourceInfos.defines(file) &&
        isScanUptodate(sourceInfos.get(file), absFile, auxFiles)) {
      return;
    }
    scanSource(file, absFile, null);
  }

  /**
   * Whether the last scan of a loaded source still reflects the given files.
   *
   * PORT NOTE: factored out of updateSource unchanged, for rescanSource.
   */
  private boolean isScanUptodate(SourceInfo info, File absFile, File[] auxFiles) {
    // sendDebug("checking " + Format.build(info));
    if (absFile.equals(info.absFile) &&
        auxFiles.length == info.auxFiles.length) {
      Date modifiedAt = Impure.driver.lastModified(absFile);
      if (!modifiedAt.after(info.scannedAt)) {
        // main file is up-to-date
        boolean uptodate = true;
        // sendDebug("uptodate " + absFile);
        for (int i = 0; uptodate && i < auxFiles.length; i++) {
          uptodate = auxFiles[i].equals(info.auxFiles[i]) &&
                     !Impure.driver.lastModified(auxFiles[i])
                            .after(info.scannedAt);
          // if (uptodate) sendDebug("uptodate " + auxFiles[i]);
        }
        return uptodate;
      }
    }
    return false;
  }

  /**
   * Rescan a loaded source unconditionally, to recompute the text of a unit which has lost
   * its (never persisted) contents.  See tryCompute.
   *
   * PORT CHANGE.  If the files are unchanged since the last scan, the rescan keeps that
   * scan's date: it reproduces the very texts that date stands for, it is not a new version
   * of the source.  This is what keeps the rescan from perturbing the other units.  Contents
   * this adaptor defined are up to date exactly while their definition date is not before
   * the source's scannedAt (isUptodate), and none of them is defined before the scannedAt
   * current when it was defined.  A fresh date would outdate every content of the source's
   * units that is still present -- the other units' delivered text and everything derived
   * from it, lang-config, latex-fragment, dvi -- and all of it would be recomputed.  With the date kept, isUptodate answers as before for all
   * of them.  enterUnits' other effects on the source's units are harmless (see there).
   * If the files did change, this is exactly the rescan updateSource would do, with a fresh
   * date, and outdating what was derived from the old version is then the point.
   */
  private void rescanSource(SourceInfo info) {
    if (isScanUptodate(info, info.absFile, info.auxFiles))
      scanSource(info.file, info.absFile, info.scannedAt);
    else
      scanSource(info.file, info.absFile, null);
  }

  /**
   * Scan a source, replacing the unit infos of its previous scan, and enter the units it
   * defines.  The absFile is supposed to represent the existing source file.
   *
   * PORT NOTE: the scanning half of updateSource, factored out unchanged for rescanSource,
   * except that the scan date can be given: `keptDate', or if that is null the time the scan
   * completed (which is what the source always took).
   */
  private void scanSource(File file, File absFile, Date keptDate) {
    if (sourceInfos.defines(file)) {
      // remove all unit infos which have been defined
      // by this outdated source
      unitInfos =
        unitInfos.filter(
          (Name name, UnitInfo unitInfo) -> {
            return !unitInfo.source.file.equals(file);
          }
        );
      needsFlush = true;
    }
    // scan the source
    sendProgress("scanning " + absFile);
    InputStream input;
    try {
      input = new java.io.FileInputStream(absFile);
    }
    catch (java.io.IOException e) {
      // this shouldn't happen, so we can throw an error
      throw new Errors.PhysicalError(e);
    }
    LatexConfig config = new LatexConfig();
    LatexScanner scanner =
      new LatexScanner(this, config, input, new Name(absFile.getPath()));
    Text[] texts = scanner.run();
    Diag diag = scanner.getAndClearDiag();
    diag = config.getAndClearDiag().combine(diag);
    SourceInfo sinfo = new SourceInfo(file, absFile,
                                      scanner.getIncludedFiles(),
                                      diag, keptDate != null ? keptDate : new Date(),
                                      config);
    sourceInfos = sourceInfos.define(file, sinfo);
    needsFlush = true;
    sendDiag(diag);
    if (!diag.contains(Diag.Category.Error)) {
      enterUnits(sinfo, scanner, texts);
    }
  }

  /**
   * Enter the given array of texts, adding the units it defines
   * to the session.
   *
   * PORT NOTE: what this does to the units of a source, which matters for rescanSource.
   * `session.getOrCreateUnit' returns a unit which exists unchanged -- no message, contents
   * untouched (units are never removed from a session); only a unit new to the session is
   * announced (`UnitAdded').  Every unit of the source gets a fresh UnitInfo whose
   * undeliveredText is the newly scanned text, also a unit whose text is still delivered and
   * present: that text is stored again at the next flush, and is what the unit gets if its
   * text is ever demanded again -- the same text, if the source is unchanged.  The
   * Sectioner's diagnostics (and scanSource's) are sent again, as on every scan.  Nothing
   * here outdates a content; only the scan date in the SourceInfo can (isUptodate).
   */
  private void enterUnits(SourceInfo sinfo, LatexScanner scanner,
                          Text[] texts) {
    Sectioner scn = new Sectioner();
    scn.run(sinfo.file, scanner.getToolkits(), texts);
    if (scn.sections.size() == 0) {
      // create at least a dummy section
      Name name = new Name(sinfo.file.toString());
      Text sect = Text.ZedSection(name, new Name[0], new Text[0]);
      sect.locator = thisLocator;
      scn.sections = scn.sections.define(name, sect);
    }

    scn.sections.apply(
      (Pair<Name, Text> section) -> {
        Name sname = section.fst;
        Text stext = section.snd;
        Name uname = new Name(thisName + ":" + sname +
                              "#" + sinfo.file);
        session.getOrCreateUnit(uname);
        // sendDebug("defining unit " + uname);
        unitInfos =
          unitInfos.define(
            uname,
            new UnitInfo(uname, sinfo, stext, stext.locator)
          );
        // System.err.println(unitInfos);
        needsFlush = true;
      }
    );
  }

  /**
   * Split the given array of texts into those texts associated
   * to no section and those associated to a section.
   */
  class Sectioner {

    Map<Name, Text> sections = new HashMap<Name, Text>();
    Name current = null;
    Seq<Text> buffer = new BufferSeq<Text>();
    boolean inClass = false;
    File file;
    Name[] toolkits;

    void run(File file, Name[] toolkits, Text[] texts) {
      this.file = file;
      this.toolkits = toolkits;
      run(texts);
    }

    private void run(Text[] texts) {
      for (int i = 0; i < texts.length; i++) {
        switch (texts[i]) {
          case Text.ZedSection t -> {
            if (inClass) {
              sendDiag(new Diag(texts[i].locator,
                                Diag.Category.Error,
                                "Z sections inside of classes not allowed"));
            } else {
              commit();
              openSection(texts[i].locator, t.name, t.parents);
            }
          }
          case Text.ZedClass t -> {
            Name[] parents = t.parents;
            if (inClass) {
              sendDiag(new Diag(texts[i].locator,
                                Diag.Category.Error,
                                "inner classes not allowed"));
            } else {
              commit();
              if (current != null) {
                // add current section to be parent of this class
                parents = HashSet.from(new ArraySeq<Name>(parents))
                                 .include(new Name(current.getRepr()))
                                 .toArray(Name.class);
              }
              Name oldCurrent = current;
              openClass(texts[i].locator, t.name, parents, t.formals);
              inClass = true;
              run(t.subtexts);
              inClass = false;
              current = oldCurrent;
            }
          }
          case Text.DocMarkup t -> run(t.subtexts);
          case Text.Literal t -> {
            // ignore
          }
          default -> buffer = buffer.append(texts[i]);
        }
      }
      commit();
    }

    /** Open a section. */
    private void openSection(Locator orig, Name name,
                             Name[] parents) {
      if (sections.defines(name)) {
        // enriching existing section
        Text old = sections.get(name);
        if (old instanceof Text.ZedSection o) {
          Text sect =
            Text.ZedSection(
              name,
              HashSet.from(new ArraySeq<Name>(parents))
                     .includeAll(HashSet.from(
                                   new ArraySeq<Name>(o.parents)
                                 ))
                     .toArray(Name.class),
              o.subtexts
            );
          sect.locator = old.locator.combine(orig);
          sections = sections.define(name, sect);
          current = name;
        } else {
          sendDiag(
            new Diag(
                  orig,
                  Diag.Category.Error,
                  Format.describe(
                    "name clash between section and class `" + name + "'",
                    "class locator", Format.buildFormat(old.locator)
                  )
                )
          );
        }
      } else {
        // defining new section
        Text sect =
          Text.ZedSection(
            name,
            parents,
            new Text[0]
          );
        sect.locator = orig;
        sections = sections.define(name, sect);
        current = name;
      }
    }

    /** Open a class. */
    private void openClass(Locator orig, Name name, Name[] parents,
                           Name[] formals) {
      if (sections.defines(name)) {
        // enriching existing class
        Text old = sections.get(name);
        if (old instanceof Text.ZedClass o) {
          if (!new ArraySeq<Name>(formals).equals(new ArraySeq<Name>(o.formals))) {
            sendDiag(
              new Diag(
                    orig,
                    Diag.Category.Error,
                    Format.describe(
                      "formal parameters of re-opened class incompatible",
                      "former class locator", Format.buildFormat(old.locator)
                    )
                  )
            );
          } else {
            Text sect =
              Text.ZedClass(
                name,
                HashSet.from(new ArraySeq<Name>(parents))
                       .includeAll(HashSet.from(
                                     new ArraySeq<Name>(o.parents)
                                   ))
                       .toArray(Name.class),
                formals,
                o.subtexts
              );
            sect.locator = old.locator.combine(orig);
            sections = sections.define(name, sect);
            current = name;
          }
        } else {
          sendDiag(
            new Diag(
                  orig,
                  Diag.Category.Error,
                  Format.describe(
                    "name clash between class and section `" + name + "'",
                    "section declared at", Format.buildFormat(old.locator)
                  )
                )
          );
        }
      } else {
        // defining new class
        Text sect =
          Text.ZedClass(
            name,
            parents,
            formals,
            new Text[0]
          );
        sect.locator = orig;
        sections = sections.define(name, sect);
        current = name;
      }
    }

    /**
     * Commit last buffered texts to last opened section or class.
     * If no class or section was opened, create a default one.
     */
    private void commit() {
      if (!buffer.isEmpty()) {
        if (current == null) {
          // create an implicite section for the entire source
          openSection(buffer.head().locator,
                      new Name(file.toString()), toolkits);
        }
        try {
          Text old = sections.get(current);
          switch (old) {
            case Text.ZedSection o ->
              o.subtexts =
                new ArraySeq<Text>(o.subtexts).appendAll(buffer).toArray(Text.class);
            case Text.ZedClass o ->
              o.subtexts =
                new ArraySeq<Text>(o.subtexts).appendAll(buffer).toArray(Text.class);
            default -> { }
          }
        }
        catch (NoSuchElementException e) {
          throw FatalError.unexpectedException(e);
        }
        buffer = new BufferSeq<Text>();
      }
    }

  }


  // FORMS

  /**
   * Load a source file.
   */
  private FileForm.State loadFileState = new FileForm.State(null);
  private TopLevelForm loadForm =
    TopLevelForm.make(
      TopLevelForm.Style.Source().setToolIcon("@load-latex.gif;LD")
                  .setModal(false),
      new SeqForm(
            new LiteralForm("load").describe("Load LaTeX File"),
            new FileForm(FileForm.Mode.PLAIN, loadFileState)
                        .style(Form.Style.FIXSIZE)
                        .describe(
                          "File Name",
                          "Name of the LaTeX file to load into session"
                        )
          ),
      "Load LaTeX Document into session",
      (Session s, Locator l) -> {
        if (loadFileState.file != null)
          loadSource(loadFileState.file);
      }
    );

  /**
   * Modify the search path.
   */
  private FileForm.State pathFileState = new FileForm.State(null);
  private ChoiceForm.State pathChoiceState = new ChoiceForm.State(0);
  private StringForm.State pathTextState = new StringForm.State("");
  private TopLevelForm pathForm =
    TopLevelForm.make(
      TopLevelForm.Style.Config().setModal(false),
      new SeqForm(
            new LiteralForm("path").describe("LaTeX Path"),
            new ChoiceForm(
                  pathChoiceState,
                  new SeqForm(
                        new LiteralForm("add"),
                        new FileForm(FileForm.Mode.EXDIRECTORY,
                                     pathFileState)
                                    .style(Form.Style.FIXSIZE)
                      ).style(Form.Style.Label("Add Directory")),
                  new SeqForm(
                        new LiteralForm("set"),
                        new StringForm(pathTextState)
                                      .style(new Form.Style.View(-1, -1))
                                      .textSize(40, 2)
                      ).style(Form.Style.Label("Set Path"))
                )
          ),
      "Modify LaTeX search path",
      s -> {
        // setup
        if (searchPath.isEmpty()) {
          pathTextState.setDraftText(session, "");
        } else {
          pathTextState.setDraftText(session,
            searchPath.map((File f) -> f.toString())
                      .fold((String s1, String s2) -> s1 + File.pathSeparator + s2)
          );
        }
      },
      (Session s, Locator l) -> {
        if (pathChoiceState.selection == 0) {
          // add to path
          if (pathFileState.file != null) {
            searchPath = searchPath.append(pathFileState.file);
            pathTextState.setDraftText(session,
              searchPath.map((File f) -> f.toString())
                        .fold((String s1, String s2) -> s1 + File.pathSeparator + s2)
            );
            pathTextState.notifyChange(this);
          }
        } else {
          StringTokenizer tkn =
            new StringTokenizer(pathTextState.getText(session),
                                File.pathSeparator);
          while (tkn.hasMoreTokens()) {
            searchPath =
              searchPath.append(new File(tkn.nextToken()));
          }
          /*
          session.send(
            thisName,
            new ResultMessage(
                  Format.list(Format.empty,
                              Format.literal(File.pathSeparator),
                              Format.empty,
                              fun (File f)->Format {
                                return Format.literal(f.toString());
                              },
                              searchPath)
                )
          );
          */
          reloadSources();
        }
      }
    );

  /**
   * Produce dvi documentation.
   */
  private TopLevelForm dviForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setToolIcon("@run-latex.gif;LT")
                  .setModal(true),
      new SeqForm(
            new LiteralForm("latex").describe("Run LaTeX"),
            new UnitForm().style(Form.Style.INVISIBLE)
          ),
      "Run LaTeX to produce DVI documentation",
      (Session s, Locator l) -> {
        // always force running LaTeX
        UnitForm.defaultState.unit.outdate(TextContentTypes.dvi);
        if (SessionUtil.query(s, l,
                              new Target(UnitForm.defaultState.unit,
                                         TextContentTypes.dvi)) == null) {
          throw new Failures.FormFailed("Run LaTeX");
        }
      }
    );

  /**
   * Launch the viewer.
   */
  private TopLevelForm viewForm =
    TopLevelForm.make(
      TopLevelForm.Style.Function().setToolIcon("@run-viewer.gif;VW")
                  .setModal(true),
      new SeqForm(
            new LiteralForm("view").describe("Launch Viewer"),
            new UnitForm().style(Form.Style.INVISIBLE)
          ),
      "Launch Viewer on DVI documentation",
      (Session s, Locator l) -> {
        File dvi = (File) SessionUtil.query(s, l,
                              new Target(UnitForm.defaultState.unit,
                                         TextContentTypes.dvi));
        if (dvi != null) {
          try {
            Process p = Runtime.getRuntime().exec(
                          new String[]{
                            System.getProperty("zeta.prog.viewer"),
                            dvi.getAbsolutePath()
                               });
          } catch (IOException e) {
            sendDiag(new Diag(thisLocator,
                              Diag.Category.Error,
                              "Cannot launch viewer: " + e));
            throw new Failures.FormFailed("Launch Viewer");
          }
        } else
          throw new Failures.FormFailed("Launch Viewer");
      }
    );


}
