package zeta.tools.zparser;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.text.Config;
import zeta.content.text.Lexem;
import zeta.content.text.Text;
import zeta.content.zirp.DeclAttr;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Item;
import zeta.content.zirp.NameDecl;
import zeta.content.zirp.State;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.util.FatalError;
import zeta.util.Name;

/** The Unit Parser.
  *
  * @version $Id: UnitParser.pizza,v 1.7 1998/12/08 09:30:28 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>Pizza's <code>switch</code> over <code>Text</code>, <code>Item</code>,
  * <code>Expr</code> and <code>UnitAbsy</code> becomes type tests in the same case order.  A
  * pattern naming a nullary case (<code>ZedToken(Enrich)</code>,
  * <code>ZedMarkup(Unboxed, ...)</code>, the <code>ZedMarkupKind</code> and
  * <code>DeclAttr</code> constants) is a comparison with the constant, as elsewhere in the port
  * (see <code>zeta.content.zirp.Type</code>).  Where a Pizza switch named every case it got a
  * default throwing <code>java.lang.Error</code> (the <code>ZedMarkupKind</code> and
  * <code>UnitAbsy</code> switches in <code>parse</code>); here that default is
  * <code>FatalError.unexpectedCase</code>, equally unreachable.</p>
  *
  * <p>The one closure, in <code>run</code>, became a lambda; it calls
  * <code>checkOnlyClassItem</code>, which updates the field <code>diag</code>, and
  * <code>Aggregate.apply</code> visits in sequence order as before.  The lifted
  * <code>$closure$...$0d</code> and <code>UnitParser$$closures</code> are gone; nothing outside
  * this class referenced them.</p>
  *
  * <p>Two defects of the original crashed and are fixed, each marked PORT CHANGE at its site:
  * <code>extractInclusions</code> recursed on its own argument instead of a
  * <code>DocMarkup</code>'s subtexts (a <code>StackOverflowError</code>), and
  * <code>extract</code> read past the end of the class text after a trailing
  * <code>\Enrich</code> (an <code>ArrayIndexOutOfBoundsException</code>).</p>
  */
public class UnitParser {

  // FIELDS

  /**
   * Field holding the language configuration.
   */
  private final Config config;

  /**
   * Field holding the unit environment.
   */
  private final UnitEnv env;

  /**
   * Field holding the preprocessor instance.
   */
  private Preprocessor prepro = null;

  /**
   * Field holding the item parser instance.
   */
  private ItemParser itemParser = null;

  /**
   * Field holding the accumulated Z items.
   */
  private Seq<Item> items = new BufferSeq<>();

  /**
   * Field holding diagnostics.
   */
  private Diag diag = new Diag();

  /**
   * Field holding inclusion map.
   */
  private Map<Name,UnitAbsy> inclusionMap;



  // CONSTRUCTION

  /**
   * Extract the enriched class units found in the given class
   * text. These are needed for building the initial environment
   * of a unit parser working on a class.
   */
  public static Name[] extractEnriched(Text.ZedClass text){
    return extract(text.subtexts).toArray(Name.class);
  }
  private static Set<Name> extract(Text[] texts){
    Set<Name> enriched = new HashSet<>();
    for (int i = 0; i < texts.length; i++){
      if (texts[i] instanceof Text.DocMarkup markup){
        enriched = enriched.includeAll(extract(markup.subtexts));
      } else if (texts[i] instanceof Text.ZedMarkup markup
                 && markup.kind == Text.ZedMarkupKind.Unboxed){
        enriched = enriched.includeAll(extract(markup.props));
      } else if (texts[i] instanceof Text.ZedToken token && token.lexem == Lexem.Enrich){
        i++;
        // skip layout, preprocessor hasn't run!
        // PORT CHANGE: the original indexed texts[i] here with no bound check, so an
        // \Enrich ending the class text threw ArrayIndexOutOfBoundsException out of
        // ParserAdaptor.tryCompute.  Stopping at the end instead finds no name, as for any
        // other \Enrich not followed by a word, and leaves the error to the item parser,
        // which rejects the dangling \Enrich.  Only that crashing input behaves differently.
        while (i < texts.length
               && texts[i] instanceof Text.ZedToken layout && layout.lexem == Lexem.Linesep){
          i++;
        }
        if (i < texts.length
            && texts[i] instanceof Text.ZedToken word
            && word.lexem instanceof Lexem.Word w){
          // found some usable info
          enriched = enriched.include(new Name(w.image));
        }
      }
    }
    return enriched;
  }

  /**
   * Extract the included  units.
   */
  public static Set<Name> extractInclusions(Text[] texts){
    Set<Name> incs = new HashSet<>();
    for (int i = 0; i < texts.length; i++){
      if (texts[i] instanceof Text.DocMarkup markup){
        // PORT CHANGE: the original recursed on `texts` itself, not on the markup's
        // subtexts, so any DocMarkup directly in a section or class (an ordinary LaTeX
        // environment such as itemize inside a zsection does it -- LatexScanner wraps it in
        // a DocMarkup) recursed without end and threw StackOverflowError out of
        // ParserAdaptor.tryCompute.  The 2002 class file has the same defect, so no input on
        // which the reference completes can reach this branch: the fix changes only inputs
        // that crashed.  Recursing on the subtexts is what extract, preparse and parse all
        // do with a DocMarkup.
        incs = incs.includeAll(extractInclusions(markup.subtexts));
      } else if (texts[i] instanceof Text.ModelInclusion inclusion){
        incs = incs.include(inclusion.name);
      }
    }
    return incs;
  }


  /**
   * Create a new unit parser, basing on the given language configuration
   * and unit environment. The environment is expected to hold
   * syntactic information about macros and fixities of the imported units
   * and enriched classes.
   */
  public UnitParser(Config config, UnitEnv env, Map<Name,UnitAbsy> incMap){
    this.config = config;
    this.env = env;
    this.inclusionMap = incMap;
  }

  // METHODS

  /**
   * Get and clear diagnostics of the last run of the unit parser.
   */
  public Diag getAndClearDiag(){
    Diag d = diag;
    diag = new Diag();
    return d;
  }


  /**
   * Parse the given text under the initialized language configuration
   * and unit environment. Returns the absy of the parsed unit, or null,
   * if parsing fails.
   */
  public UnitAbsy run(Text text){

    // enter directives of this unit
    preparse(text);

    // now create preprocessor and parser instance
    prepro = new Preprocessor(config, env);
    itemParser = new ItemParser(env);

    // parse text of this unit
    parse(text);

    // check for items only allowed in class
    if (text instanceof Text.ZedSection){
      items.apply((Item item) -> {
        if (item != null)
          checkOnlyClassItem(item);
      });
    }

    // return contents if no errors have occured
    if (!diag.contains(Diag.Category.Error)){
      if (text instanceof Text.ZedSection){
        UnitAbsy absy = UnitAbsy.Section(
                          env.getName(),
                          env.getParents(), items.toArray(Item.class));
        Locator.put(absy.an, text.locator);
        return absy;
      } else if (text instanceof Text.ZedClass zedClass){
        UnitAbsy absy = UnitAbsy.ClassDef(env.getName(),
                                          env.getParents(),
                                          zedClass.formals,
                                          items.toArray(Item.class));
        Locator.put(absy.an, text.locator);
        return absy;
      } else {
        throw FatalError.assertionFailed("unexpected text kind of unit");
      }
    } else
      return null;
  }

  /**
   * Preparse - declare fixity and macro declarations.
   */
  private void preparse(Text text){
    switch (text) {
      case Text.DocMarkup markup -> {
        Text[] subtexts = markup.subtexts;
        for (int i = 0; i < subtexts.length; i++)
          preparse(subtexts[i]);
      }
      case Text.ZedSection section -> {
        Text[] subtexts = section.subtexts;
        for (int i = 0; i < subtexts.length; i++)
          preparse(subtexts[i]);
      }
      case Text.ZedClass zedClass -> {
        Text[] subtexts = zedClass.subtexts;
        for (int i = 0; i < subtexts.length; i++)
          preparse(subtexts[i]);
      }
      case Text.ZedPragma pragma -> {
        Text[] subtexts = pragma.subtexts;
        Seq<Lexem> value = new BufferSeq<>();
        for (int i = 0; i < subtexts.length; i++){
          if (subtexts[i] instanceof Text.ZedToken token)
            value = value.append(token.lexem);
        }
        env.addPragma(text.locator, pragma.name, value.toArray(Lexem.class));
      }
      case Text.ZedFixity fixity -> {
        env.declareFixity(text.locator, fixity.fixity);
        checkDiag(env.getAndClearDiag());
      }
      case Text.ZedMacro macro -> {
        env.declareMacro(text.locator, macro.name, macro.argc, macro.def);
        checkDiag(env.getAndClearDiag());
      }
      case Text.ModelInclusion inclusion -> {
        if (inclusionMap.defines(inclusion.name)){
          UnitAbsy absy = inclusionMap.get(inclusion.name);
          // FIXME: import fixities and macros
        }
      }
      default -> { }
    }
  }

  /**
   * Parse a text
   */
  private void parse(Text text){
    if (text instanceof Text.DocMarkup markup){
      Text[] subtexts = markup.subtexts;
      for (int i = 0; i < subtexts.length; i++)
        parse(subtexts[i]);
    } else if (text instanceof Text.ZedSection section){
      Text[] subtexts = section.subtexts;
      for (int i = 0; i < subtexts.length; i++)
        parse(subtexts[i]);
    } else if (text instanceof Text.ZedClass zedClass){
      Text[] subtexts = zedClass.subtexts;
      for (int i = 0; i < subtexts.length; i++)
        parse(subtexts[i]);
    } else if (text instanceof Text.ModelInclusion inclusion){
      Item etext = Item.EmbeddedText(text);
      Locator.put(etext.an, text.locator);
      items = items.append(etext);
      if (inclusionMap.defines(inclusion.name)){
        UnitAbsy absy = inclusionMap.get(inclusion.name);
        if (absy instanceof UnitAbsy.Section section){
          items = items.appendAll(new ArraySeq<Item>(section.items));
        } else if (absy instanceof UnitAbsy.ClassDef classDef){
          items = items.appendAll(new ArraySeq<Item>(classDef.items));
        } else {
          throw FatalError.unexpectedCase("UnitParser.parse: unit absy");
        }
      }
    } else if (text instanceof Text.ZedPragma || text instanceof Text.ZedFixity
               || text instanceof Text.ZedMacro){
      Item etext = Item.EmbeddedText(text);
      Locator.put(etext.an, text.locator);
      items = items.append(etext);
    } else if (text instanceof Text.ZedMarkup markup){
      Text.ZedMarkupKind kind = markup.kind;
      Text.ZedToken[] headerTokens = prepro.run(markup.header);
      Text.ZedToken[] declTokens = prepro.run(markup.decls);
      Text.ZedToken[] propTokens = prepro.run(markup.props);
      if (checkDiag(prepro.getAndClearDiag())){
        if (kind == Text.ZedMarkupKind.Unboxed){
          items =
            items.appendAll(
              new ArraySeq<Item>(itemParser.parseUnboxedItems(propTokens))
            );
        } else if (kind == Text.ZedMarkupKind.Schema){
          items =
            items.append(itemParser.parseSchemaBox(text.locator,
                                                   headerTokens,
                                                   declTokens,
                                                   propTokens));
        } else if (kind == Text.ZedMarkupKind.AxiomaticDef){
          items =
            items.append(itemParser.parseAxiomaticBox(text.locator,
                                                      headerTokens,
                                                      declTokens,
                                                      propTokens));
        } else if (kind == Text.ZedMarkupKind.XorState){
          items =
            items.append(itemParser.parseStateBox(State.StateKind.Xor,
                                                  text.locator,
                                                  headerTokens,
                                                  declTokens,
                                                  propTokens));
        } else if (kind == Text.ZedMarkupKind.AndState){
          items =
            items.append(itemParser.parseStateBox(State.StateKind.And,
                                                  text.locator,
                                                  headerTokens,
                                                  declTokens,
                                                  propTokens));
        } else if (kind == Text.ZedMarkupKind.Config){
          diag =
            diag.combine(new Diag(text.locator,
                                  Diag.Category.Warning,
                                  "mSZ configurations currently unsupported"));
        } else if (kind == Text.ZedMarkupKind.Reaction){
          items =
            items.append(itemParser.parseReactionBox(text.locator,
                                                     headerTokens,
                                                     declTokens,
                                                     propTokens));
        } else if (kind == Text.ZedMarkupKind.Transition){
          diag =
            diag.add(text.locator,
                     Diag.Category.Warning,
                     "mSZ transitions unsupported");
        } else {
          throw FatalError.unexpectedCase("UnitParser.parse: markup kind");
        }
        checkDiag(itemParser.getAndClearDiag());
      }
    } else {
      // ignore other texts
    }
  }

  /**
   * Check if a item is allowed in a section.
   */
  private void checkOnlyClassItem(Item item){
    if (item instanceof Item.AxiomaticDef axdef){
      // check if this doesn't contains a Port-or-so declaration attribute
      if (axdef.def instanceof Expr.Text schemaText){
        Expr.Decl[] decls = schemaText.decls;
        for (int i = 0; i < decls.length; i++){
          NameDecl[] nameDecls;
          if (decls[i] instanceof Expr.Decl.Direct direct){
            nameDecls = direct.names;
          } else if (decls[i] instanceof Expr.Decl.Eqn eqn){
            nameDecls = new NameDecl[]{eqn.name};
          } else {
            continue;
          }
          for (int j = 0; j < nameDecls.length; j++){
            DeclAttr attr = DeclAttr.get(nameDecls[j].an);
            if (attr != null){
              if (attr == DeclAttr.Data || attr == DeclAttr.Port
                  || attr == DeclAttr.Init || attr == DeclAttr.Property
                  || attr instanceof DeclAttr.Alias){
                diag = diag.add(Locator.get(nameDecls[j].an),
                                Diag.Category.Error,
                "mSZ schema role not allowed outside of a class definition"
                                );
              }
            }
          }
        }
      }
    } else if (item instanceof Item.FixityDef || item instanceof Item.EmbeddedText){
      // nothing to check
    } else {
      // the remaning are mSZ items currently
      // MAYFIXME: if other pure Z items are added
      diag = diag.add(Locator.get(item.an),
                      Diag.Category.Error,
                      "mSZ paragraph not allowed outside of a class definiton"
                     );
    }
  }

  /**
   * Accumulate diagnostics, return true if no errors in this
   * diagnostics.
   */
  private boolean checkDiag(Diag d){
    if (!d.isEmpty()){
      diag = diag.combine(d);
      return !d.contains(Diag.Category.Error);
    } else
      return true;
  }

}
