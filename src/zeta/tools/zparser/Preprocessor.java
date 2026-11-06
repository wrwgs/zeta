package zeta.tools.zparser;

import java.util.ArrayList;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashMap;
import zeta.agl.HashSet;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.text.Config;
import zeta.content.text.Lexem;
import zeta.content.text.Text;
import zeta.content.zirp.UnitEnv;
import zeta.session.Diag;
import zeta.util.Name;

/** The token pre-processor. Handles expansion of macros and
  * offside rules.
  *
  * @version $Id: Preprocessor.pizza,v 1.3 1999/01/06 11:43:44 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p>The output buffer was a <code>java.util.Vector</code>; it is a private
  * <code>ArrayList</code> here (not observable: it is only appended to, copied out and
  * cleared).  <code>Map&lt;int,Text[]&gt;</code> is <code>Map&lt;Integer,Text[]&gt;</code>:
  * Pizza boxed the <code>int</code> type argument as <code>java.lang.Integer</code> too (the
  * 2002 class file calls <code>new Integer(int)</code>), and the map is only probed with
  * <code>defines</code>/<code>get</code>, never enumerated.  The private
  * <code>scanArg</code>/<code>scanArgGroup</code> return a <code>Pair</code> of the
  * argument and the next position.</p>
  *
  * <p>A switch on a nullary <code>Lexem</code> case (<code>Linesep</code>,
  * <code>Lgroup</code>, <code>Rgroup</code>) is a comparison with the constant, as elsewhere in
  * the port (see <code>zeta.content.zirp.Type</code>): those cases are singletons.</p>
  *
  * <p>Two oddities of the original are kept as they are.  The set <code>expanded</code> of
  * macros being expanded is threaded through every call but never consulted, so recursive
  * macros are cut off only by <code>MAXREC</code>.  And an argument reference
  * <code>#n</code> is expanded under an empty substitution (the source's own FIXME), so an
  * argument mentioning an outer macro argument loses it.  Both are behaviour the oracle sees
  * whenever a macro is used.</p>
  */
class Preprocessor {

  /** The concrete language configuration. */
  private Config config;

  /** The unit environment. */
  private UnitEnv unitEnv;

  /** Diagnostics produced during pre-processing. */
  private Diag diag = new Diag();

  /** The output buffer. */
  private ArrayList<Text.ZedToken> output = new ArrayList<>(1024);

  /** The token waiting to be put into the output buffer. */
  private Text.ZedToken last;

  /** The lexical info about the last token, if any. */
  private UnitEnv.LexemInfo lastInfo;


  /** If the last token is a line-separator token. */
  private boolean lastIsLinesep;

  /** Create a new preprocessor, working on the given concrete language
    * configuration. */
  Preprocessor(Config config, UnitEnv unitEnv){
    this.config = config;
    this.unitEnv = unitEnv;
  }

  /** Run the preprocessor on the given sequence of texts,
    * producing a sequence of Z token texts. */
  Text.ZedToken[] run(Text[] texts){
    last = null;
    lastIsLinesep = false;
    lastInfo = null;
    process(new HashSet<Name>(), new HashMap<Integer, Text[]>(), texts);
    if (last != null){
      output.add(last);
    }
    Text.ZedToken[] result = output.toArray(new Text.ZedToken[0]);
    output.clear();
    last = null;
    return result;
  }

  /** Return and clear diagnostics produced by preprocessor. The diagnostics
    * are accumlated over several runs. */
  Diag getAndClearDiag(){
    Diag d = diag;
    diag = new Diag();
    return d;
  }

  /** Output a Z token, thereby processing offside
    * rules. */
  private void put(Text.ZedToken token, UnitEnv.LexemInfo info){
    if (token.lexem == Lexem.Linesep){
      if (lastIsLinesep){
        // last token already line separator, ignore this one
      } else
        if (last != null &&
            (lastInfo == null && config.isRightOffside(last.lexem) ||
             lastInfo != null && lastInfo.rightOffside)){
          // last token puts this line separator offside, ignore this one
        }
        else {
          // shift
          if (last != null) output.add(last);
          last = token;
          lastIsLinesep = true;
          lastInfo = null;
        }
    } else {
      if (lastIsLinesep &&
          (info == null && config.isLeftOffside(token.lexem) ||
           info != null && info.leftOffside)){
        // this token puts the last line separator offside, overwrite it
        last = token;
        lastIsLinesep = false;
        lastInfo = info;
      } else {
        // shift
        if (last != null) output.add(last);
        last = token;
        lastIsLinesep = false;
        lastInfo = info;
      }
    }
  }

  private int recCount = 0;
  private static int MAXREC = 2048;

  /** Process texts, doing macro expansion and lexem substitution */
  private void process(Set<Name> expanded,
                       Map<Integer, Text[]> subs,
                       Text[] texts){
    int i = 0;
    while (i < texts.length){
      switch (texts[i]) {
        case Text.DocMarkup markup -> {
          // recursively process
          process(expanded, subs, markup.subtexts);
          i++;
        }
        case Text.ZedToken zedToken -> {
          Lexem lexem = zedToken.lexem;
          Text.ZedToken token;
          UnitEnv.LexemInfo info = unitEnv.getLexemInfo(lexem);
          if (info != null){
            lexem = info.rewritten;
            token = new Text.ZedToken(lexem);
            token.locator = texts[i].locator;
          } else
            token = (Text.ZedToken)texts[i];
          switch (lexem) {
            case Lexem.SimpleName simpleName -> {
              Name name = simpleName.image;
              UnitEnv.MacroInfo mac = unitEnv.getMacro(name);
              if (mac != null){
                if (recCount < MAXREC){
                  // expand macro
                  recCount++;
                  i = expand(expanded.include(name),
                             (Text.ZedToken)texts[i], mac, i+1, texts);
                  recCount--;
                } else {
                  diag = diag.add(texts[i].locator,
                                  Diag.Category.Warning,
                                  "expansion of macro `" +
                                  name +
                                  "' exceeds recursion limit");
                  i++;
                }
              } else {
                put(token, info);
                i++;
              }
            }
            case Lexem.MacroArg macroArg -> {
              String image = macroArg.image;
              int argn;
              try {
                argn = Integer.parseInt(image.substring(1));
              }
              catch (NumberFormatException e){
                throw new Error(e.getMessage());
              }
              if (subs.defines(argn)){
                // process argument substitution
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // FIXME: need outer substitution here !!!!!!!!!!!!!!!!
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                process(expanded, new HashMap<Integer, Text[]>(), subs.get(argn));
                i++;
              } else {
                // ignore (error reported elsewhere)
                i++;
              }
            }
            default -> {
              put(token, info);
              i++;
            }
          }
        }
        default ->
          // ignore other variants
          i++;
      }
    }
  }



  /** Expand macro. */
  private int expand(Set<Name> expanded,
                     Text.ZedToken token,
                     UnitEnv.MacroInfo mac,
                     int i,
                     Text[] texts){
    Map<Integer, Text[]> subs = new HashMap<>();
    for (int argc = 0; argc < mac.argc; argc++){
      Pair<Seq<Text>, Integer> argAndRest = scanArg(token, texts, i);
      Seq<Text> arg = argAndRest.fst;
      subs = subs.define(argc+1, arg.toArray(Text.class));
      i = argAndRest.snd;
    }
    Text[] def = new Text[mac.subs.length];
    for (int k = 0; k < def.length; k++){
      def[k] = new Text.ZedToken(mac.subs[k]);
      def[k].locator = token.locator;
    }
    process(expanded, subs, def);
    return i;
  }

  /** Scan macro argument. Returns the argument paired with the position after it. */
  private Pair<Seq<Text>, Integer> scanArg(Text start,
                                           Text[] texts, int i){
    if (i < texts.length){
      if (texts[i] instanceof Text.ZedToken zedToken && zedToken.lexem == Lexem.Lgroup){
        Pair<Seq<Text>, Integer> res = scanArgGroup(texts[i], texts, i+1);
        if (res.snd < texts.length){
          res.snd = res.snd + 1;
        }
        return res;
      } else {
        return Pair.Pair(new ArraySeq<Text>(texts[i]), i+1);
      }
    } else {
      diag = diag.add(start.locator,
                      Diag.Category.Error,
                      "missing arguments for macro");
      return Pair.Pair(new ArraySeq<Text>(), i);
    }
  }

  /** Scan macro argument group. Result as for <code>scanArg</code>. */
  private Pair<Seq<Text>, Integer> scanArgGroup(Text start,
                                                Text[] texts, int i){
    Seq<Text> arg = new BufferSeq<>();
    while (i < texts.length){
      if (texts[i] instanceof Text.ZedToken zedToken && zedToken.lexem == Lexem.Lgroup){
        arg = arg.append(texts[i]);
        Pair<Seq<Text>, Integer> res = scanArgGroup(texts[i], texts, i+1);
        i = res.snd;
        Seq<Text> inner = res.fst;
        arg = arg.appendAll(inner);
        if (i < texts.length){
          arg = arg.append(texts[i]);
          i++;
        }
      } else if (texts[i] instanceof Text.ZedToken zedToken
                 && zedToken.lexem == Lexem.Rgroup){
        return Pair.Pair(arg, i);
      } else {
        arg = arg.append(texts[i]);
        i++;
      }
    }
    diag = diag.add(start.locator,
                    Diag.Category.Error,
                    "unclosed macro argument group");
    return Pair.Pair(arg, i);
  }

}
