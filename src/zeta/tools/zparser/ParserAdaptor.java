package zeta.tools.zparser;

import java.util.Date;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.text.Config;
import zeta.content.text.Text;
import zeta.content.text.TextContentTypes;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZirpContentTypes;
import zeta.format.Format;
import zeta.session.ContentType;
import zeta.session.DefaultAdaptor;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Locator;
import zeta.session.Session;
import zeta.session.StorageHandle;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.util.FatalError;
import zeta.util.Name;

/** The Z/DZ/mSZ parser adaptor.
  *
  * @version $Id: ParserAdaptor.pizza,v 1.14 2000/07/21 06:45:15 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>Loaded by name from <code>zetarc</code>
  * (<code>plugin zeta.tools.zparser.ParserAdaptor</code>), so its public no-argument
  * constructor is the one external entry point.  The two
  * <code>switch (text)</code> statements become type tests in the same order; the first named
  * <code>ZedSection</code> and <code>ZedClass</code> with a default raising
  * <code>FatalError.assertionFailed</code>, kept as is.</p>
  *
  * <p>The order of everything the oracle sees is unchanged: the parent units are looked up in
  * the iteration order of the <code>HashSet</code> they are collected into (as before), the
  * included units in the order of <code>UnitParser.extractInclusions</code>'s set, and the
  * progress message, the optional timing message and the diagnostics are sent in the original
  * order.</p>
  */
public class ParserAdaptor extends DefaultAdaptor {


  // CONSTANTS

  /*
   * The name of this adaptor.
   */
  static final Name thisAdaptor = new Name("ZPARSER");

  /*
   * The startup message of this adaptor.
   */
  private static final String startupMessage =
    "installed Z parser v1.5";

  /**
   * Whether we should issue timing messages.
   */
  private static final boolean timing =
    System.getProperty("zeta.debug.timing") != null;




  // ADAPTOR INITIALIZATION

  /**
   * Initialize a newly created instance of this adaptor.
   */
  public void initialize(Session session, StorageHandle handle){
    super.initialize(session, handle);
    this.session = session;
    sendProgress(startupMessage);
    session.registerContentType(ZirpContentTypes.uncheckedAbsy);
    session.registerContentType(ZirpContentTypes.uncheckedEnv);
    session.registerContentType(ZirpContentTypes.checkedEnv);
  }


  // ADAPTOR METHODS

  /**
   * Get the descriptive name of this adaptor.
   */
  public Name getName() {
    return thisAdaptor;
  }

  /** Always throws. */
  public Unit lookupUnit(Name name)
  throws Failures.UnresolvableName, Failures.AmbigiousName {
    throw new Failures.UnresolvableName(name);
  }

  /**
   * Return the content types which this adaptor can generally
   * compute.
   */
  public ContentType[] computedTypes() {
    return new ContentType[]{ ZirpContentTypes.uncheckedAbsy,
                              ZirpContentTypes.uncheckedEnv   };
  }

  /**
   * Run the adaptor to compute the specified content.
   */
  public void tryCompute(Target spec)
  throws Failures.ComputationFailed, Failures.CannotCompute {
    Seq<Target> deps = new BufferSeq<>();

    // query for the lexical unit contents
    Text text = (Text)spec.unit.query(TextContentTypes.text);
    Config config = (Config)spec.unit.query(TextContentTypes.config);
    deps = deps.append(new Target(spec.unit, TextContentTypes.text))
           .append(new Target(spec.unit, TextContentTypes.config));

    // build initial unit environment from imports
    UnitEnv env;
    Name[] parents;
    Set<Name> included;
    if (text instanceof Text.ZedSection section){
      included = UnitParser.extractInclusions(section.subtexts);
      env = UnitEnv.newSection(spec.unit.getName(), text.locator);
      parents = HashSet.from(new ArraySeq<Name>(section.parents))
                       .toArray(Name.class);

    } else if (text instanceof Text.ZedClass zedClass){
      included = UnitParser.extractInclusions(zedClass.subtexts);
      env = UnitEnv.newClass(spec.unit.getName(), text.locator, zedClass.formals);
      parents = HashSet.from(new ArraySeq<Name>(zedClass.parents))
                       .includeAll(HashSet.from(
                                     new ArraySeq<Name>(
                UnitParser.extractEnriched(zedClass))))
                       .toArray(Name.class);
    } else {
      throw FatalError.assertionFailed("unexpected text kind of unit");
    }

    // query imported environments
    for (int i = 0; i < parents.length; i++){
      try {
        Unit imp = session.lookupUnit(parents[i]);
        // query unchecked environment and import it
        UnitEnv impEnv = (UnitEnv)imp.query(ZirpContentTypes.uncheckedEnv);
        env.importSyntaxFrom(impEnv);
        deps = deps.append(new Target(imp,
                                      ZirpContentTypes.uncheckedEnv));
      }
     catch (Failures.UnresolvableName e){
       reportUnresolvable(env, env.getLocator(), parents[i]);
     }
     catch (Failures.AmbigiousName e){
       reportAmbigious(env, env.getLocator(), parents[i], e.fullNames);
     }
    }

    // query included absys
    Map<Name,UnitAbsy> inclusionMap = new HashMap<>();
    for (Enum<Name> en = included.enumerate(); en.more(); en.advance()){
      Name name = en.current();
      try {
        Unit imp = session.lookupUnit(name);
        UnitAbsy incAbsy = (UnitAbsy)imp.query(ZirpContentTypes.uncheckedAbsy);
        inclusionMap = inclusionMap.define(name, incAbsy);
        deps = deps.append(new Target(imp,
                                      ZirpContentTypes.uncheckedAbsy));
      }
     catch (Failures.UnresolvableName e){
       reportUnresolvable(env, env.getLocator(), name);
     }
     catch (Failures.AmbigiousName e){
       reportAmbigious(env, env.getLocator(), name, e.fullNames);
     }
    }


    // run the unit parser
    Diag diag = env.getAndClearDiag();
    Date date = new Date();
    if (text instanceof Text.ZedSection){
      sendProgress("parsing section " + spec.unit.getName());
    } else {
      sendProgress("parsing class " + spec.unit.getName());
    }
    long start = 0;
    if (timing)
      start = System.currentTimeMillis();
    UnitParser unitParser = new UnitParser(config, env, inclusionMap);
    UnitAbsy absy = unitParser.run(text);
    if (timing)
      sendDebug("parsing " +
                (System.currentTimeMillis()-start) + "ms");
    sendDiag(diag.combine(unitParser.getAndClearDiag()));

    // define contents if no errors have occured
    if (!diag.contains(Diag.Category.Error) && absy != null){
      spec.unit.define(ZirpContentTypes.uncheckedAbsy,
                       absy,
                       true,
                       date,
                       deps.toArray(Target.class),
                       this
                      );
      spec.unit.define(ZirpContentTypes.uncheckedEnv,
                       env,
                       true,
                       date,
                       deps.toArray(Target.class),
                       this
                      );
    } else {
      // throw failure
      throw new Failures.ComputationFailed(spec);
    }
  }

  private void reportUnresolvable(UnitEnv env, Locator loc, Name name){
    env.addDiag(new Diag(loc,
                         Diag.Category.Error,
                         "`" + name + "' cannot be resolved as a unit"));
  }

  private void reportAmbigious(UnitEnv env, Locator loc,
                               Name name, Name[] fullNames){
    env.addDiag(new Diag(loc,
                         Diag.Category.Error,
                         Format.describe(
                           "`" + name + "' is ambigious",
                           "possible completions",
                           Format.list(Format.empty,
                                       Format.literal(", "),
                                       Format.empty,
                                       fullNames))));
  }


  /** */
  public boolean isUptodate(Target spec, Date definedAt){
    return true;
  }

  /** */
  public boolean notifyOutdate(Target spec, Date definedAt,
                               StorageHandle handle){
    return false;
  }

  /** */
  public void checkExternalData(){
  }

  /** */
  public void flush(){
  }


}
