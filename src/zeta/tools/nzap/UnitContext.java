package zeta.tools.nzap;

import java.util.NoSuchElementException;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.HashMap;
import zeta.agl.Map;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.content.text.Lexem;
import zeta.content.zirp.UnitEnv;
import zeta.util.FatalError;
import zeta.util.Name;

/** Schema context.
  *
  * @version $Id: UnitContext.pizza,v 1.6 2000/05/08 07:07:43 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p>The unit context assigns the table indices -- of denotations, globals,
  * constructors, natives and schemas -- that end up in the unit file, in first-use
  * order.  The private field order is the jar's.  Pizza's <code>Map&lt;Name,int&gt;</code>
  * and <code>Seq&lt;boolean&gt;</code> are <code>Map&lt;Name,Integer&gt;</code> and
  * <code>Seq&lt;Boolean&gt;</code>.</p>
  *
  * <p><b>Matching on constant lexems.</b>  The pragma parsers matched
  * <code>Lparenth</code>, <code>Arg</code>, ... as Pizza compiled them: a
  * <code>lookupswitch</code> on <code>Lexem</code>'s <code>$$tag</code> (confirmed in the
  * 2002 class file), not object identity.  The two differ for a lexem that was
  * deserialised from a stored environment, since <code>Lexem</code> has no
  * <code>readResolve</code>.  The port therefore switches on the tag, with the
  * <code>L_*</code> constants below read from <code>harness/adt_tags.py</code>.
  * <code>Word</code> carries data and is a class; the type test is equivalent to its tag
  * (100), as exactly one class has it.</p>
  *
  * <p>PORT CHANGE in {@link #addCode}: the growth loop tested <code>fill*s &gt;= newl</code>
  * where <code>fill+s</code> was meant.  For <code>fill, s &gt;= 2</code> the product
  * bounds the sum, so the buffer only over-grows -- and its capacity is unobservable,
  * since {@link #finish} copies out exactly <code>fill</code> bytes.  Only when
  * <code>fill</code> or <code>s</code> is 0 or 1 can the product fall below the sum (at
  * <code>fill = 0</code>, the first constraint, it is 0 and the loop never runs), and
  * then a constraint longer than about twice the current buffer made
  * <code>System.arraycopy</code> throw.  Testing the sum keeps every successful run
  * byte-identical and removes only that exception.</p>
  *
  * <p>The one closure (the parent-name map in <code>finish</code>) is a lambda; the
  * lifted <code>$closure$</code> method and <code>UnitContext$$closures</code> are gone,
  * nothing outside this class referenced them.  The class literals in
  * <code>toArray(...)</code> name <code>String</code> and <code>ZAM</code>'s records, none
  * of which registers a slot.</p>
  */
class UnitContext {

  // Lexem tags (ABI; harness/adt_tags.py zeta.content.text.Lexem).
  private static final int L_ARG = 4;
  private static final int L_COMMA = 11;
  private static final int L_LISTARG = 52;
  private static final int L_LPARENTH = 53;
  private static final int L_RPARENTH = 77;
  private static final int L_STROKEIN = 85;
  private static final int L_STROKEOUT = 86;

  /** A mapping of native names to a sequence of argument
      characterizations (flex=true,nonflex=false). */
  private Map<Name, Seq<Boolean>> natives;

  /** Name of the unit. */
  private Name name;

  /** Parent units. */
  private Seq<Name> parents;

  /** Get the name of the unit. */
  Name getName() {
    return name;
  }

  /** Test if context has natives. */
  boolean hasNatives() {
    return natives.size() > 0;
  }

  /** Construct a unit context. */
  UnitContext(UnitEnv env, Name name, Seq<Name> parents) {
    this.name = name; this.parents = parents;
    UnitEnv.PragmaInfo[] pragmas = env.getPragmas();
    natives = new HashMap<Name, Seq<Boolean>>();
    Name zapPragma = new Name("zap");
    for (int i = 0; i < pragmas.length; i++) {
      UnitEnv.PragmaInfo p = pragmas[i];
      if (p.name.equals(zapPragma) && p.value.length > 1) {
        if (p.value[0] instanceof Lexem.Word w) {
          if (w.image.equals("native")) {
            Pair<Name, Integer> on = parseOpName(p.value, 1);
            if (on != null) {
              Seq<Boolean> b = parseNativeSpec(p.value, on.snd);
              if (b != null) {
                natives = natives.define(on.fst, b);
              }
            }
          }
        }
      }
    }
  }

  private static int tag(Lexem l) {
    return l.zeta$content$text$Lexem$$tag;
  }

  private Seq<Boolean> parseNativeSpec(Lexem[] toks, int pos) {
    int state = 0;
    Seq<Boolean> b = new BufferSeq<Boolean>();
    while (pos < toks.length) {
      switch (state) {
        case 0: // start
          switch (tag(toks[pos])) {
            case L_LPARENTH:
              pos++;
              state = 1;
              continue;
            default:
              return null;
          }
        case 1: // characteristic
          switch (tag(toks[pos])) {
            case L_STROKEIN:
              b = b.append(false);
              pos++;
              state = 2;
              continue;
            case L_STROKEOUT:
              b = b.append(true);
              pos++;
              state = 2;
              continue;
            default:
              return null;
          }
        case 2: // next
          switch (tag(toks[pos])) {
            case L_COMMA:
              pos++;
              state = 1;
              continue;
            case L_RPARENTH:
              return ArraySeq.from(b);
            default:
              return null;
          }
        default:
          return null;
      }
    }
    return null;
  }

  // Pizza's Pair<Name,int>: `fst` is the name, `snd` the next position.
  private Pair<Name, Integer> parseOpName(Lexem[] toks, int pos) {
    int state = 0;
    String repr = "";
    while (pos < toks.length) {
      switch (state) {
        case 0: // start
          if (toks[pos] instanceof Lexem.Word w) {
            return Pair.Pair(new Name(w.image), pos + 1);
          }
          switch (tag(toks[pos])) {
            case L_LPARENTH:
              pos++;
              state = 1;
              continue;
            default:
              return null;
          }
        case 1: // components
          if (toks[pos] instanceof Lexem.Word w) {
            repr += w.image;
            pos++;
            continue;
          }
          switch (tag(toks[pos])) {
            case L_ARG:
              repr += "_";
              pos++;
              continue;
            case L_LISTARG:
              repr += ",,";
              pos++;
              continue;
            case L_RPARENTH:
              return Pair.Pair(new Name(repr), pos + 1);
            default:
              return null;
          }
        default:
          return null;
      }
    }
    return null;
  }



  /** A hash for denotations. */
  private Map<String, Integer> denoMap = new HashMap<String, Integer>();
  private Seq<String> denoTab = new BufferSeq<String>();

  /** A hash for globals. */
  private Map<Name, Integer> globalMap = new HashMap<Name, Integer>();
  private Seq<ZAM.Global> globalTab = new BufferSeq<ZAM.Global>();

  /** A hash for constructors. */
  private Map<Name, Integer> constructorMap = new HashMap<Name, Integer>();
  private Seq<ZAM.Constructor> constructorTab = new BufferSeq<ZAM.Constructor>();

  /** A hash for native functions. */
  private Map<Name, Integer> nativeFuncMap = new HashMap<Name, Integer>();
  private Seq<ZAM.NativeFunc> nativeFuncTab = new BufferSeq<ZAM.NativeFunc>();

  /** A hash for native predicates. */
  private Map<Name, Integer> nativePredMap = new HashMap<Name, Integer>();
  private Seq<ZAM.NativePred> nativePredTab = new BufferSeq<ZAM.NativePred>();

  /** A hash for homomorphisms. */
  private Map<Name, Integer> nativeHomMap = new HashMap<Name, Integer>();
  private Seq<ZAM.NativeHom> nativeHomTab = new BufferSeq<ZAM.NativeHom>();

  /** Table of schemas. */
  private Seq<ZAM.Schema> schemaTab = new BufferSeq<ZAM.Schema>();

  /** Code buffer. */
  private byte[] code = new byte[2048];
  private int fill = 0;


  /** Get table index of denotation. */
  int getDenotationIndex(String deno) {
    try {
      return denoMap.get(deno);
    }
    catch (NoSuchElementException e) {
      int i = denoTab.size();
      denoTab = denoTab.append(deno);
      denoMap = denoMap.define(deno, i);
      return i;
    }
  }

  /** Get table index of native function. */
  int getNativeFuncIndex(Name name, int arity) {
    try {
      return nativeFuncMap.get(name);
    }
    catch (NoSuchElementException e) {
      int i = nativeFuncTab.size();
      nativeFuncTab =
        nativeFuncTab.append(new ZAM.NativeFunc(name.getRepr(), arity));
      nativeFuncMap = nativeFuncMap.define(name, i);
      return i;
    }
  }

  /** Get table index of native predicate. */
  int getNativePredIndex(Name name, int arity) {
    try {
      return nativePredMap.get(name);
    }
    catch (NoSuchElementException e) {
      int i = nativePredTab.size();
      nativePredTab =
        nativePredTab.append(new ZAM.NativePred(name.getRepr(), arity));
      nativePredMap = nativePredMap.define(name, i);
      return i;
    }
  }

  /** Get table index of homomorphism. */
  int getHomIndex(Name name) {
    try {
      return nativeHomMap.get(name);
    }
    catch (NoSuchElementException e) {
      int i = nativeHomTab.size();
      nativeHomTab =
        nativeHomTab.append(new ZAM.NativeHom(name.getRepr()));
      nativeHomMap = nativeHomMap.define(name, i);
      return i;
    }
  }

  /** Get table index of global. */
  int getGlobalIndex(Name name) {
    try {
      return globalMap.get(name);
    }
    catch (NoSuchElementException e) {
      int i = globalTab.size();
      globalTab =
        globalTab.append(new ZAM.Global(name.getRepr(),
                                        !name.getRepr().startsWith("%")));
                                                                  // FIXME
      globalMap = globalMap.define(name, i);
      return i;
    }
  }

  /** Get table index of constructor. */
  int getConstructorIndex(Name name, int arity) {
    try {
      return constructorMap.get(name);
    }
    catch (NoSuchElementException e) {
      int i = constructorTab.size();
      constructorTab =
        constructorTab.append(new ZAM.Constructor(name.getRepr(), arity));
      constructorMap = constructorMap.define(name, i);
      return i;
    }
  }

  /** Get argument specification of native. */
  Seq<Boolean> getArgSpec(Name name) {
    try {
      return natives.get(name);
    }
    catch (NoSuchElementException e) {
      throw FatalError.assertionFailed("not native: " + name);
    }
  }

  /** Add a schema. */
  int addSchema(ZAM.Schema inten) {
    int i = schemaTab.size();
    schemaTab = schemaTab.append(inten);
    return i;
  }

  /** Add code. */
  int addCode(ConstrCoder coder) {
    int s = coder.getCodeSize();
    if (fill + s >= code.length) {
      int newl = code.length * 2;
      // PORT CHANGE: was `fill*s >= newl`; see class notes.
      while (fill + s >= newl) {
        newl = newl * 2;
      }
      byte[] newcode = new byte[newl];
      System.arraycopy(code, 0, newcode, 0, fill);
      code = newcode;
    }
    System.arraycopy(coder.getCode(), 0, code, fill, s);
    int offs = fill;
    fill += s;
    return offs;
  }

  /** Add a constructor. */
  void addConstructor(Name name, int argc) {
    int i = getConstructorIndex(name, argc);
  }

  /** Return ZAM Unit. */
  ZAM.Unit finish(int initSchemaIndex) {
    byte[] fcode = new byte[fill];
    System.arraycopy(code, 0, fcode, 0, fill);
    return new ZAM.Unit(
                 name.getRepr(),
                 parents.map((Name n) -> n.getRepr())
                        .toArray(String.class),
                 globalTab.toArray(ZAM.Global.class),
                 constructorTab.toArray(ZAM.Constructor.class),
                 nativeFuncTab.toArray(ZAM.NativeFunc.class),
                 nativePredTab.toArray(ZAM.NativePred.class),
                 nativeHomTab.toArray(ZAM.NativeHom.class),
                 denoTab.toArray(String.class),
                 initSchemaIndex,
                 schemaTab.toArray(ZAM.Schema.class),
                 fcode
               );
  }
}
