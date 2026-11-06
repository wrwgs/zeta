package zeta.tools.pp;

/** The Pretty Printer.
  *
  * @version $Id: PrettyPrinter.pizza,v 1.4 1999/02/13 10:35:35 wg Exp $
  * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
  *
  * <h2>Port notes</h2>
  *
  * <p>All Pizza ADT switch patterns are translated to Java 21 pattern switches.  Multi-case
  * ADTs ({@code Expr}, {@code Predicate}, {@code Item}, {@code State}, {@code LabelExpr},
  * {@code Expr.Decl}, {@code Expr.Branch}) use {@code case Type.Sub st ->} arrow syntax.
  * Constant-case ADTs ({@code Expr.BinaryKind}, {@code Expr.UnaryKind},
  * {@code Expr.QuantorKind}, {@code Predicate.*Kind}, {@code State.StateKind},
  * {@code Item.AttributeKind}, {@code DeclAttr}) use {@code if / else if} chains with
  * {@code ==} comparisons.</p>
  *
  * <p>{@code zeta.agl.Enum} is imported explicitly to shadow {@code java.lang.Enum}:
  * the type parameter {@code Enum<Seq<Text>>} would otherwise resolve to the wrong type.</p>
  *
  * <p>{@code Fixity.Component.OperandList} has fields {@code lname} and {@code rname}
  * in the jar; the Pizza source calls them {@code left} and {@code right}.  The port uses
  * the jar field names throughout (PORT NOTE at each site).</p>
  *
  * <p>{@code Map<Lexem, Fixity> specialFixities} is kept as a typed declaration; the jar
  * declares it raw, but the field is private and the ABI only checks its existence and type
  * erasure.</p>
  *
  * <p>The {@code Seq<Text>[]} array in {@code collectSubstates} requires an unchecked
  * cast; this is unavoidable in Java because generic arrays cannot be created directly.</p>
  *
  * <p>The pizza source uses {@code import zeta.content.text.Lexem.*;}, which made all Lexem
  * constant names like {@code And}, {@code Or}, {@code Comma} etc. available unqualified.
  * In the port, every such name is qualified with {@code Lexem.}. Similarly for
  * {@code Text.*} (now {@code Text.Literal}, {@code Text.ZedToken}, etc.),
  * {@code Text.FormatKind.*} (now {@code Text.FormatKind.Block}, etc.),
  * {@code Text.ZedMarkupKind.*} (now {@code Text.ZedMarkupKind.Schema}, etc.), and
  * {@code Layout.*} (now {@code Layout.VerticalBox}, etc.).</p>
  */

import java.util.NoSuchElementException;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashMap;
import zeta.agl.Map;
import zeta.agl.Seq;
import zeta.content.text.Config;
import zeta.content.text.Lexem;
import zeta.content.text.Text;
import zeta.content.zirp.Configuration;
import zeta.content.zirp.DeclAttr;
import zeta.content.zirp.Expr;
import zeta.content.zirp.Fixity;
import zeta.content.zirp.Item;
import zeta.content.zirp.LabelExpr;
import zeta.content.zirp.Layout;
import zeta.content.zirp.NameAppl;
import zeta.content.zirp.NameDecl;
import zeta.content.zirp.Predicate;
import zeta.content.zirp.State;
import zeta.content.zirp.Transition;
import zeta.content.zirp.UnitAbsy;
import zeta.content.zirp.UnitEnv;
import zeta.content.zirp.ZNames;
import zeta.format.Format;
import zeta.util.FatalError;
import zeta.util.Name;


public class PrettyPrinter {
  private Config config;
  private UnitEnv environment;
  private Map<Lexem, Fixity> specialFixities = new HashMap();

  /** Create a pretty-printer from the given language configuration. */
  public PrettyPrinter(Config config, UnitEnv environment) {
    super();
    this.config = config;
    this.environment = environment;
    specialFixities =
      specialFixities
        .define(Lexem.If,
                new Fixity(110, false, new Fixity.Component[]{}))
        .define(Lexem.Dot,
                new Fixity(10000, false, new Fixity.Component[] {}))
        .define(Lexem.Cross,
                new Fixity(10000, false, new Fixity.Component[] {}))
        // Quantifiers
        .define(Lexem.Exists, getQuantorFixity())
        .define(Lexem.Exists1, getQuantorFixity())
        .define(Lexem.Forall, getQuantorFixity())
        .define(Lexem.Lambda, getQuantorFixity())
        .define(Lexem.Mu, getQuantorFixity())
        .define(Lexem.Let, getQuantorFixity())
        // schemaExpr
        .define(Lexem.Pipe, getInfixFixity(200, Lexem.Pipe))
        .define(Lexem.Compose, getAssocInfixFixity(210, Lexem.Compose))
        .define(Lexem.Hide, getInfixFixity(220, Lexem.Hide))
        .define(Lexem.Project, getInfixFixity(230, Lexem.Project))
        // schemaExpr, predicate
        .define(Lexem.Iff, getInfixFixity(300, Lexem.Iff))
        .define(Lexem.Implies, getInfixFixity(305, Lexem.Implies))
        .define(Lexem.Or, getAssocInfixFixity(310, Lexem.Or))
        .define(Lexem.And, getAssocInfixFixity(315, Lexem.And))
        .define(Lexem.Choice, getAssocInfixFixity(320, Lexem.Choice))
        .define(Lexem.Compute, getInfixFixity(325, Lexem.Compute))
        .define(Lexem.Preempt, getInfixFixity(330, Lexem.Preempt))
        .define(Lexem.Leadsto, getInfixFixity(335, Lexem.Leadsto))
        .define(Lexem.Trans, getInfixFixity(340, Lexem.Trans))
        .define(Lexem.Chop, getInfixFixity(345, Lexem.Chop))
        // expr
        .define(Lexem.Not, getPrefixFixity(700, Lexem.Not))
        .define(Lexem.Power, getPrefixFixity(710, Lexem.Power))
        .define(Lexem.Pre, getPrefixFixity(720, Lexem.Pre))
        .define(Lexem.Theta, getPrefixFixity(730, Lexem.Theta))
        .define(Lexem.Xi, getPrefixFixity(740, Lexem.Xi))
        .define(Lexem.Delta, getPrefixFixity(745, Lexem.Delta))
        // predicates
        .define(Lexem.Await, getPrefixFixity(750, Lexem.Await))
        .define(Lexem.Everywhere, getPrefixFixity(760, Lexem.Everywhere))
        .define(Lexem.Somewhere, getPrefixFixity(770, Lexem.Somewhere))
        .define(Lexem.Repeat, getPrefixFixity(780, Lexem.Repeat))
        .define(Lexem.Prefix, getPrefixFixity(790, Lexem.Prefix))
        ;
  }

  public Format pretty(Item item) {
    return
      config.prettySubtexts(prettyItem(item), Text.FormatKind.Block(0));
  }

  public Format pretty(UnitAbsy unit) {
    return
      config.prettySubtexts(prettyUnitAbsy(unit), Text.FormatKind.Beneath(0));
  }

  public Format pretty(Expr expr) {
    return
      config.prettySubtexts(prettyExpr(expr), Text.FormatKind.Block(0));
  }

  public Format pretty(Predicate predicate) {
    return
      config.prettySubtexts(prettyPredicate(predicate), Text.FormatKind.Block(0));
  }

  private Seq<Text> prettyUnitAbsy(UnitAbsy unit) {
    Seq<Text> buffer = new BufferSeq<>();
    switch (unit) {
      case UnitAbsy.ClassDef cd -> {
        for (int i = 0, l = cd.items.length; i < l; i++)
          buffer = buffer.appendAll(prettyItem(cd.items[i]));
        Text[] it = buffer.toArray(Text.class);
        return new ArraySeq<>(
          Text.ZedClass(cd.name, cd.parents, cd.formals, it));
      }
      case UnitAbsy.Section s -> {
        for (int i = 0, l = s.items.length; i < l; i++)
          buffer = buffer.appendAll(prettyItem(s.items[i]));
        Text[] it = buffer.toArray(Text.class);
        return new ArraySeq<>(
          Text.ZedSection(s.name, s.parents, it));
      }
      default -> throw FatalError.unexpectedCase("unit");
    }
  }

  private Seq<Text> prettyRename(Expr.Rename rename) {
    // Expr.Rename is a single-case ADT; fields are old and _new.
    Seq<Text> buffer = new BufferSeq<>();
    buffer = open(buffer);
      buffer = buffer.appendAll(prettyNameDecl(rename._new));
      buffer = buffer.append(Text.ZedToken(Lexem.Slash));
      buffer = buffer.appendAll(prettyNameAppl(rename.old));
    buffer = close(buffer);
    return buffer;
  }

  private Seq<Text> prettyActualizations(Expr[] actuals) {
    Seq<Text> buffer = new BufferSeq<>();
    int l = actuals.length;

    buffer = open(buffer);
      buffer = buffer.append(Text.ZedToken(Lexem.Lbrack));
      buffer = open(buffer);
        Seq<Seq<Text>> b = new BufferSeq<>();
        for (int i = 0; i < l; i++)
          b = b.append(prettyExpr(actuals[i]));
        buffer = buffer.appendAll(enumerate(b, Lexem.Comma));
      buffer = close(buffer);
      buffer = buffer.append(Text.ZedToken(Lexem.Rbrack));
    buffer = close(buffer);
    return buffer;
  }

  private Seq<Text> prettyName(Name name) {
    Seq<Text> buffer;
    Fixity fixity = getFixity(name);

    if (fixity != null) {
      buffer = new BufferSeq<>();
      int f = fixity.components.length;

      buffer = open(buffer, Text.FormatKind.Append(0));
        for (int i = 0; i < f; i++) {
          switch (fixity.components[i]) {
            case Fixity.Component.Operand op -> {
              buffer = buffer.append(Text.ZedToken(Lexem.Arg));
            }
            case Fixity.Component.OperandList ol -> {
              // PORT NOTE: source says `left`/`right`; jar ABI uses `lname`/`rname`.
              buffer = buffer.appendAll(prettyName(ol.lname));
              buffer = buffer.append(Text.ZedToken(Lexem.Arg));
              buffer = buffer.appendAll(prettyName(ol.rname));
            }
            case Fixity.Component.Keyword kw -> {
              buffer = buffer.append(
                Text.ZedToken(Lexem.Word(kw.name.toString())));
            }
            default -> throw FatalError.unexpectedCase("component");
          }
        }
      buffer = close(buffer);
    } else { // no fixity -> simple name
      buffer = new ArraySeq<>(Text.Literal(name.toString()));
    }
    return buffer;
  }

  private Seq<Text> prettyNameDecl(NameDecl decl) {
    return
      prettyDeclAttr(DeclAttr.get(decl.an)).appendAll(prettyName(decl.name));
  }

  private Seq<Text> prettyNameAppl(NameAppl appl) {
    // NameAppl is a single-case ADT; fields are name and actuals.
    Seq<Text> buffer = new BufferSeq<>();
    Name name = appl.name;
    Expr[] actuals = appl.actuals;
    Fixity fixity = getFixity(name);

    int l = actuals.length;
    int f, ari;
    ari = getArity(fixity);
    if (fixity != null && l < ari) {
      f = fixity.components.length;
      if (l > 0) {
        buffer = new BufferSeq<>();
        buffer = open(buffer);
          // not enough actualizations
          // --> (name)[param1,...,paramN]
          buffer = enclose(buffer,
                                     Text.ZedToken(Lexem.Lparenth),
                                     Text.ZedToken(Lexem.Rparenth));
          buffer = buffer.appendAll(prettyActualizations(actuals));
        buffer = close(buffer);
      } else {
        buffer = enclose(prettyName(name),
                                   Text.ZedToken(Lexem.Lparenth),
                                   Text.ZedToken(Lexem.Rparenth));
      }
    } else if (fixity != null && l > 0) {
      // there is a fixity and there are actualizations,
      // so they must be enough
      buffer = new BufferSeq<>();
      buffer = open(buffer);
        int act = 0; // act contains the actualized params found
        f = fixity.components.length;
        for (int i = 0; i < f; i++)
          switch (fixity.components[i]) {
            case Fixity.Component.Operand op -> {
              buffer =
                buffer.appendAll(parenthesize(prettyExpr(actuals[act]),
                                           getFixity(actuals[act]),
                                           op.prio));
              act++;
            }
            case Fixity.Component.OperandList ol -> {
              // PORT NOTE: source says `left`/`right`; jar ABI uses `lname`/`rname`.
              buffer =
                buffer.appendAll(enclose(
                                    prettyExpr(actuals[act]),
                                    prettyName(ol.lname),
                                    prettyName(ol.rname)));
              act++;
            }
            case Fixity.Component.Keyword kw -> {
              buffer = buffer.append(Text.Literal(kw.name.toString()));
            }
            default -> throw FatalError.unexpectedCase("component");
          }
      buffer = close(buffer);
    } else if (l > 0) { // no fixity, but actualizations -> append
      buffer = open(buffer);
        buffer = buffer.appendAll(prettyName(name));
        buffer = buffer.appendAll(prettyActualizations(actuals));
      buffer = close(buffer);
    } else { // no fixity no actualizations
      buffer = prettyName(name);
    }
    return buffer;
  }

  @SuppressWarnings("unchecked")
  private Seq<Text>[] collectSubstates(Seq<Text> superstate, State[] sub) {
    Seq<Seq<Text>> b;
    Seq<Text>[] subresult, result;
    Seq<Text> s;

    result = (Seq<Text>[]) new Seq[sub.length + 1];
    result[0] =
      superstate
        .prepend(Text.ZedToken(Lexem.Lglue))
        .append(Text.ZedToken(Lexem.Rglue));
    for (int i = 0, l = sub.length; i < l; i++) {
      s = superstate
            .append(Text.ZedToken(Lexem.Colon))
            .appendAll(prettyNameDecl(getStateName(sub[i])));
      switch (sub[i]) {
        case State.BasicState bs -> {
          s = close(s);
          result[i + 1] =
            s .prepend(Text.ZedToken(Lexem.Lglue))
              .append(Text.ZedToken(Lexem.Rglue));
        }
        case State.SuperState ss -> {
          subresult = collectSubstates(s, ss.substates);
          b = new BufferSeq<>();
          for (int j = 0, k = subresult.length; j < k; j++)
            b = b.append(subresult[j]);
          result[i + 1] = enumerate(b, Lexem.Comma);
        }
        default -> throw FatalError.unexpectedCase("state");
      }
    }
    return result;
  }

  private Seq<Text> prettySchemaBox(NameDecl name,
                                    NameDecl[] formals,
                                    Expr def) {
    Seq<Text> buffer = new BufferSeq<>();
    Seq<Seq<Text>> b;
    Text[] hd, it, pr;

    buffer = buffer.appendAll(prettyNameDecl(name));
    int l;
    if ((l = formals.length) > 0) {
      b = new BufferSeq<>();
      for (int i = 0; i < l; i++)
        b = b.append(prettyNameDecl(formals[i]));
      buffer = buffer.appendAll(enclose(enumerate(b, Lexem.Comma),
                                                  Text.ZedToken(Lexem.Lbrack),
                                                  Text.ZedToken(Lexem.Rbrack)));
    }
    if (def instanceof Expr.Text et) {
      hd = buffer.toArray(Text.class);
      if ((l = et.decls.length) > 0) {
        b = new BufferSeq<>();
        for (int i = 0; i < l; i++)
          b = b.append(prettyDecl(et.decls[i]));
        it = enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(0)).toArray(Text.class);
      } else {
        it = new Text[0];
      }
      if ((l = et.props.length) > 0) {
        b = new BufferSeq<>();
        for (int i = 0; i < l; i++)
          b = b.append(prettyPredicate(et.props[i]));
        pr = enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(0)).toArray(Text.class);
      } else {
        pr = new Text[0];
      }
      return
        new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Schema, hd, it, pr));
    }
    // can't happen
    return null;
  }

  private Seq<Text> prettyAxiomaticDef(NameDecl[] formals, Expr def) {
    Seq<Text> buffer;
    Seq<Seq<Text>> b;
    Text[] hd, it, pr;

    if (def instanceof Expr.Text et) {
      if (et.props.length == 0 && et.decls.length == 1) {
        if (et.decls[0] instanceof Expr.Decl.Eqn eq) {
          if (eq.def instanceof Expr.Text &&
              (!Layout.isProvided(eq.def.an) ||
               Layout.get(eq.def.an) == Layout.VerticalBox))
            return prettySchemaBox(eq.name, formals, eq.def);
          else
            return prettyAbbrev(et.decls[0], formals);
        }
      }
      if (formals.length > 0) {
        b = new BufferSeq<>();
        for (int i = 0, l = formals.length; i < l; i++)
          b = b.append(prettyNameDecl(formals[i]));
        buffer = enclose(enumerate(b, Lexem.Comma),
                                   Text.ZedToken(Lexem.Lbrack),
                                   Text.ZedToken(Lexem.Rbrack));
        hd = buffer.toArray(Text.class);
      } else {
        hd = new Text[0];
      }
      int l;
      if ((l = et.decls.length) > 0) {
        b = new BufferSeq<>();
        for (int i = 0; i < l; i++)
          b = b.append(prettyDecl(et.decls[i]));
        it = enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(0)).toArray(Text.class);
      } else {
        it = new Text[0];
      }
      if ((l = et.props.length) > 0) {
        b = new BufferSeq<>();
        for (int i = 0; i < l; i++)
          b = b.append(prettyPredicate(et.props[i]));
        pr = enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(0)).toArray(Text.class);
      } else {
        pr = new Text[0];
      }
      buffer =
        new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.AxiomaticDef, hd, it, pr));
    } else {
      hd = pr = new Text[0];
      it = prettyExpr(def).toArray(Text.class);
      buffer =
        new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Unboxed, hd, it, pr));
    }
    return buffer;
  }

  private Seq<Text> prettyItem(Item item) {
    Seq<Text> buffer;
    Seq<Seq<Text>> b;
    Text[] hd, it, pr;

    switch (item) {
      case Item.AxiomaticDef ad -> {
        return prettyAxiomaticDef(ad.formals, ad.def);
      }
      case Item.ConfigDef cd -> {
        return new ArraySeq<>();
      }
      case Item.StatechartDef sd -> {
        buffer = prettyState(sd.root);
      }
      case Item.StaticReactionDef srd -> {
        hd = prettyNameDecl(srd.name).toArray(Text.class);
        buffer = new BufferSeq<>();
        int l;
        if ((l = srd.reactions.length) > 0) {
          b = new BufferSeq<>();
          for (int i = 0; i < l; i++)
            b = b.append(prettyLabelExpr(srd.reactions[i]));
          it = enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(0)).toArray(Text.class);
        } else {
          it = new Text[0];
        }
        pr = new Text[0];
        buffer = new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Reaction, hd, it, pr));
      }
      case Item.TransitionDef td -> {
        hd = prettyNameDecl(td.name).toArray(Text.class);
        it = new Text[0];
        int l;
        if ((l = td.reactions.length) > 0) {
          b = new BufferSeq<>();
          for (int i = 0; i < l; i++)
            b = b.append(prettyLabelExpr(td.reactions[i]));
          pr = enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(0)).toArray(Text.class);
        } else {
          pr = new Text[0];
        }
        buffer = new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Transition, hd, it, pr));
      }
      case Item.AttributeDef atd -> {
        buffer = new BufferSeq<>();
        Item.AttributeKind attrib = atd.attrib;
        if (attrib == Item.AttributeKind.Derived) {
          buffer = buffer.append(Text.ZedToken(Lexem.Derived));
        } else if (attrib == Item.AttributeKind.Input) {
          buffer = buffer.append(Text.ZedToken(Lexem.Input));
        }
        // case Plain: nothing
        b = new BufferSeq<>();
        for (int i = 0, l = atd.name.length; i < l; i++)
          b = b.append(prettyNameAppl(atd.name[i]));
        buffer = buffer.appendAll(enumerate(b, Lexem.Comma));
        buffer = buffer.append(Text.ZedToken(Lexem.Linesep));
        hd = pr = new Text[0];
        it = buffer.toArray(Text.class);
        buffer = new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Unboxed, hd, it, pr));
      }
      case Item.EnrichDef ed -> {
        buffer = new BufferSeq<>();
        buffer = open(buffer);
          buffer = buffer.append(Text.ZedToken(Lexem.Enrich));
          buffer = buffer.appendAll(prettyNameAppl(ed.name));
          buffer = buffer.append(Text.ZedToken(Lexem.Linesep));
        buffer = close(buffer);
        hd = pr = new Text[0];
        it = buffer.toArray(Text.class);
        buffer = new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Unboxed, hd, it, pr));
      }
      case Item.EmbeddedText et -> {
        buffer = new ArraySeq<>(et.text);
      }
      case Item.FixityDef fd -> {
        buffer = new ArraySeq<>(Text.ZedFixity(fd.fixity));
      }
      default -> throw FatalError.unexpectedCase("item");
    }
    return buffer;
  }

  private NameDecl getStateName(State state) {
    return switch (state) {
      case State.BasicState bs -> bs.name;
      case State.SuperState ss -> ss.name;
      case State.RefState rs -> rs.name;
      default -> throw FatalError.unexpectedCase("state");
    };
  }

  private Lexem getStateLexem(State state) {
    return switch (state) {
      case State.BasicState bs -> Lexem.BasicState;
      case State.SuperState ss -> {
        if (ss.kind == State.StateKind.Xor) yield Lexem.XorState;
        else yield Lexem.AndState;
      }
      case State.RefState rs -> Lexem.RefState;
      default -> throw FatalError.unexpectedCase("state");
    };
  }

  private Seq<Text> prettyState(State state) {
    switch (state) {
      case State.SuperState ss -> {
        Text[] hd = prettyNameDecl(ss.name).toArray(Text.class);
        Seq<Text> buffer = new BufferSeq<>();
        Seq<Seq<Text>> b = new BufferSeq<>();
        int l = ss.substates.length;
        int i;
        for (i = 0; i < l; i++) {
          buffer = open(buffer);
            buffer = open(buffer);
              buffer =
                buffer.appendAll(prettyNameDecl(getStateName(ss.substates[i])));
              buffer = buffer.append(Text.ZedToken(Lexem.Colon));
            buffer = close(buffer);
            buffer = buffer.append(Text.ZedToken(getStateLexem(ss.substates[i])));
            buffer = buffer.append(Text.ZedToken(Lexem.Linesep));
          buffer = close(buffer);
        }
        b = new BufferSeq<>();
        l = ss.connectors.length;
        if (l > 0) {
          for (i = 0; i < l; i++)
            b = b.append(prettyNameDecl(ss.connectors[i]));
          buffer = open(buffer);
            buffer = open(buffer);
              buffer = buffer.appendAll(enumerate(b, Lexem.Comma));
              buffer = buffer.append(Text.ZedToken(Lexem.Colon));
            buffer = close(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.Connector));
          buffer = close(buffer);
        }
        Text[] it = buffer.toArray(Text.class);
        b = new BufferSeq<>();
        l = ss.transitions.length;
        for (i = 0; i < l; i++)
          b = b.append(prettyTransition(ss.transitions[i]));
        Text[] pr = enumerate(b, Lexem.Linesep).toArray(Text.class);
        buffer = new BufferSeq<>();
        Text.ZedMarkupKind k;
        if (ss.kind == State.StateKind.And)
          k = Text.ZedMarkupKind.AndState;
        else
          k = Text.ZedMarkupKind.XorState;
        buffer = buffer.append(Text.ZedMarkup(k, hd, it, pr));
        l = ss.substates.length;
        for (i = 0; i < l; i++)
          buffer = buffer.appendAll(prettyState(ss.substates[i]));
        return buffer;
      }
      default -> { /* fall through to empty return */ }
    }
    return new ArraySeq<>();
  }

  private Seq<Text> prettyMatrix(Expr expr) {
    return prettyMatrix(expr, 0);
  }

  private Seq<Text> prettyMatrix(Expr expr, int indent) {
    Seq<Text> buffer;
    Seq<Text> h;
    Seq<Seq<Text>> b;

    if (expr instanceof Expr.Text et) {
      buffer = new BufferSeq<>();
      b = new BufferSeq<>();
      for (int i = 0, l = et.decls.length; i < l; i++)
        b = b.append(prettyDecl(et.decls[i]));
      buffer = buffer.appendAll(enumerate(b, Lexem.Semi, Text.FormatKind.Line(indent)));
      if (et.props.length > 0) {
        h = buffer;
        buffer = new BufferSeq<>();
        buffer = open(buffer, Text.FormatKind.Line(indent));
          buffer = buffer.appendAll(h);
          buffer = buffer.append(Text.ZedToken(Lexem.Mid));
          b = new BufferSeq<>();
          for (int i = 0, l = et.props.length; i < l; i++)
            b = b.append(prettyPredicate(et.props[i]));
          buffer =
            buffer.appendAll(enumerate(b, Lexem.Linesep, Text.FormatKind.Beneath(2)));
        buffer = close(buffer);
      }
      return buffer;
    } else {
      return prettyExpr(expr, indent);
    }
  }

  private boolean shouldParenthesizeSelect(Expr oper) {
    return switch (oper) {
      case Expr.Select s -> false;
      case Expr.Binding b -> false;
      case Expr.Display d -> false;
      case Expr.Text t -> false;
      case Expr.Tuple t -> false;
      case Expr.Variable v -> false;
      default -> true;
    };
  }

  private int getTupleLength(Expr tuple) {
    if (tuple instanceof Expr.Tuple t)
      return t.elements.length;
    return 0;
  }

  private Seq<Text> prettyExpr(Expr expr) {
    return prettyExpr(expr, 0);
  }

  private Seq<Text> prettyExpr(Expr expr, int indent) {
    Seq<Text> buffer;
    Seq<Text> h; // auxiliary variable
    Seq<Seq<Text>> b; // auxiliary variable

    switch (expr) {
      case Expr.Number en -> {
        buffer = new ArraySeq<>(Text.Literal(en.value));
      }
      case Expr.Denotation ed -> {
        buffer = new ArraySeq<>(Text.Literal("\\ZD{" + ed.value + "}"));
      }
      case Expr.Variable ev -> {
        buffer = prettyNameAppl(ev.name);
      }
      case Expr.Tuple et -> {
        // (element1,...,elementN)
        buffer = new BufferSeq<>();
        b = new BufferSeq<>();
        for (int i = 0, l = et.elements.length; i < l; i++)
          b = b.append(prettyExpr(et.elements[i]));
        buffer = buffer.appendAll(enclose(enumerate(b, Lexem.Comma, 2),
                                                    Text.ZedToken(Lexem.Lparenth),
                                                    Text.ZedToken(Lexem.Rparenth)));
      }
      case Expr.Product ep -> {
        // set1 \cross ... \cross setN
        buffer = new BufferSeq<>();
        b = new BufferSeq<>();
        for (int i = 0, l = ep.elements.length; i < l; i++) {
          // if you _could_ put parantheses around, do so
          if (getFixity(ep.elements[i]) != null) {
            h = new BufferSeq<>();
            h = enclose(prettyExpr(ep.elements[i]),
                                  Text.ZedToken(Lexem.Lparenth),
                                  Text.ZedToken(Lexem.Rparenth));
          } else {
            h = prettyExpr(ep.elements[i]);
          }
          b = b.append(h);
        }
        buffer = buffer.appendAll(enumerate(b, Lexem.Cross));
      }
      case Expr.Binding eb -> {
        // <| name1 -> expr1, ..., nameN -> exprN |>
        b = new BufferSeq<>();
        for (int i = 0, l = eb.bindings.length; i < l; i++)
          b = b.append(prettyEqn(eb.bindings[i], Text.ZedToken(Lexem.Defeq)));
        buffer = enclose(enumerate(b, Lexem.Comma, 2),
                                   Text.ZedToken(Lexem.Lbind),
                                   Text.ZedToken(Lexem.Rbind));
      }
      case Expr.Signature es -> {
        // [ name1 : type1; ...; nameN : typeN ]
        buffer = new BufferSeq<>();
        b = new BufferSeq<>();
        for (int i = 0, l = es.names.length; i < l; i++) {
          h = new BufferSeq<>();
          h = open(h);
            h = h.appendAll(prettyName(es.names[i]));
            h = h.append(Text.ZedToken(Lexem.Colon));
            h = h.appendAll(prettyExpr(es.types[i], 2));
          h = close(h);
          b = b.append(h);
        }
        buffer =
          buffer.appendAll(
            enclose(enumerate(b, Lexem.Semi, indent),
            Text.ZedToken(Lexem.Lbrack),
            Text.ZedToken(Lexem.Rbrack))
          );
      }
      case Expr.Display ed -> {
        //  element_1, ..., element_N
        b = new BufferSeq<>();
        for (int i = 0, l = ed.elements.length; i < l; i++)
          b = b.append(prettyExpr(ed.elements[i]));
        buffer = enclose(enumerate(b, Lexem.Comma, indent),
                         Text.ZedToken(Lexem.Lset),
                         Text.ZedToken(Lexem.Rset));
      }
      case Expr.Cond ec -> {
        buffer = new BufferSeq<>();
        buffer = open(buffer, indent);
          buffer = open(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.If));
            buffer = buffer.appendAll(prettyPredicate(ec.cond, 2));
          buffer = close(buffer);
          buffer = open(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.Then));
            buffer = buffer.appendAll(prettyExpr(ec.thenPath, 2));
          buffer = close(buffer);
          buffer = open(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.Else));
            buffer = buffer.appendAll(prettyExpr(ec.elsePath, 2));
          buffer = close(buffer);
        buffer = close(buffer);
      }
      case Expr.Quantor eq -> {
        // <quantor> <declarations> [ | <predicate> ] [ @ <expression> ]
        // or
        // { <declarations> [ | <predicate> ] [ @ <expression> ] }
        buffer = new BufferSeq<>();
        Lexem quantor = getQuantorLexem(eq.kind);

        if (eq.kind == Expr.QuantorKind.Set) {
          if (eq.range != null) {
            buffer = open(buffer, 2);
              buffer = buffer.appendAll(prettyMatrix(eq.matrix));
              buffer = buffer.append(Text.ZedToken(Lexem.Spot));
              buffer = buffer.appendAll(prettyExpr(eq.range, 2));
            buffer = close(buffer);
          } else { // set without range
            buffer = buffer.appendAll(prettyMatrix(eq.matrix, 2));
          }
          buffer = enclose(buffer,
                                     Text.ZedToken(Lexem.Lset),
                                     Text.ZedToken(Lexem.Rset));
        } else {
          buffer = open(buffer);
            if (eq.range != null) {
              buffer = open(buffer, Text.FormatKind.Block(indent));
                buffer = buffer.append(Text.ZedToken(quantor));
                buffer = open(buffer, 2);
                  buffer = buffer.appendAll(prettyMatrix(eq.matrix));
                  buffer = buffer.append(Text.ZedToken(Lexem.Spot));
                buffer = close(buffer);
              buffer = close(buffer);
              buffer = buffer.appendAll(prettyExpr(eq.range, 2));
            } else { // can only happen if for quantor Mu
              buffer = buffer.append(Text.ZedToken(quantor));
              buffer = buffer.appendAll(prettyMatrix(eq.matrix, 2));
            }
          buffer = close(buffer);
        }
      }
      case Expr.Text et -> {
        // stand-alone it looks like:
        // \lbrack <declarations> [ | <predicate> ] \rbrack
        // but in the context of a quantor, the \lbrack and \rbrack vanish
        // in the context of a schema definition, it is displayed vertically
        buffer = enclose(prettyMatrix(expr, 2),
                                   Text.ZedToken(Lexem.Lbrack),
                                   Text.ZedToken(Lexem.Rbrack));
      }
      case Expr.Select es -> {
        buffer = new BufferSeq<>();
        buffer = open(buffer, indent);
          if (shouldParenthesizeSelect(es.oper)) {
            buffer = buffer.appendAll(enclose(prettyExpr(es.oper, 2),
                                                        Text.ZedToken(Lexem.Lparenth),
                                                        Text.ZedToken(Lexem.Rparenth)));
          } else {
            buffer = buffer.appendAll(prettyExpr(es.oper));
          }
          buffer = open(buffer, Text.FormatKind.Append(2));
            buffer = buffer.append(Text.ZedToken(Lexem.Dot));
            buffer = buffer.appendAll(prettyNameAppl(es.name));
          buffer = close(buffer);
        buffer = close(buffer);
      }
      case Expr.Unary eu -> {
        buffer = new BufferSeq<>();
        Fixity fix = getFixity(expr);
        Expr.UnaryKind kind = eu.kind;

        if (kind == Expr.UnaryKind.StateFlat || kind == Expr.UnaryKind.StepFlat) {
          // FIXME: What does this look like?
        } else if (kind == Expr.UnaryKind.Pre   ||
                   kind == Expr.UnaryKind.Power  ||
                   kind == Expr.UnaryKind.Theta  ||
                   kind == Expr.UnaryKind.Not    ||
                   kind == Expr.UnaryKind.Xi     ||
                   kind == Expr.UnaryKind.Delta) {
          // lets assume the fixity looks like this: (i) : keyword (j)
          buffer = open(buffer);
            buffer =
              buffer.append(Text.Literal(getKeyword(fix.components[0])));
            buffer =
              buffer.appendAll(parenthesize(prettyExpr(eu.oper, 2),
                                         getFixity(eu.oper),
                                         getComponentPrio(fix.components[1])));
          buffer = close(buffer);
        } else if (kind instanceof Expr.UnaryKind.Hide uh) {
          // assume fixity: (i) : \hide \lpar (j ,) \rpar
          buffer = open(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.Hide));
            b = new BufferSeq<>();
            for (int i = 0, l = uh.names.length; i < l; i++)
              b = b.append(prettyNameAppl(uh.names[i]));
            buffer =
              buffer.appendAll(enclose(enumerate(b, Lexem.Comma, 2),
                                                 Text.ZedToken(Lexem.Lparenth),
                                                 Text.ZedToken(Lexem.Rparenth)));
          buffer = close(buffer);
        } else if (kind instanceof Expr.UnaryKind.SelectiveDelta usd) {
          // assume fixity: (i) : \delta \lpar (j ,) \rpar (k)
          buffer = open(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.Delta));
            b = new BufferSeq<>();
            for (int i = 0, l = usd.names.length; i < l; i++)
              b = b.append(prettyNameAppl(usd.names[i]));
            buffer =
              buffer.appendAll(enclose(enumerate(b, Lexem.Comma, 2),
                                                 Text.ZedToken(Lexem.Lparenth),
                                                 Text.ZedToken(Lexem.Rparenth)));
            buffer =
              buffer.appendAll(parenthesize(prettyExpr(eu.oper),
                                         getFixity(eu.oper),
                                         0));
          buffer = close(buffer);
        } else if (kind instanceof Expr.UnaryKind.Renaming ur) {
          // (i) : (j) \lbrack (k ,) \rbrack
          buffer = open(buffer);
            buffer =
              buffer.appendAll(parenthesize(prettyExpr(eu.oper),
                                         getFixity(eu.oper),
                                         0));
            b = new BufferSeq<>();
            for (int i = 0, l = ur.renames.length; i < l; i++)
              b = b.append(prettyRename(ur.renames[i]));
            buffer = buffer.appendAll(enclose(enumerate(b, Lexem.Comma),
                                                        Text.ZedToken(Lexem.Lbrack),
                                                        Text.ZedToken(Lexem.Rbrack)));
          buffer = close(buffer);
        } else if (kind instanceof Expr.UnaryKind.Decorate ud) {
          // fixity (i) : (j) (k)
          buffer = open(buffer);
            buffer =
              buffer.appendAll(parenthesize(prettyExpr(eu.oper),
                                         getFixity(eu.oper),
                                         0));
            buffer = buffer.appendAll(prettyDecoration(ud.decore));
          buffer = close(buffer);
        }
      }
      case Expr.Binary eb -> {
        buffer = new BufferSeq<>();
        Fixity fix = getFixity(expr);

        if (eb.kind == Expr.BinaryKind.Apply) {
          if (eb.left instanceof Expr.Variable lv) {
            Name name = lv.name.name;
            Expr[] actuals = lv.name.actuals;
            int ari = getArity(fix);

            if (fix != null && actuals.length == 0 &&
                (ari == 1 ||
                 (ari > 1 &&
                  eb.right instanceof Expr.Tuple &&
                  getTupleLength(eb.right) == ari))) {
              int j = 0;
              Expr[] components;

              if (eb.right instanceof Expr.Tuple &&
                  getTupleLength(eb.right) == ari) {
                components = ((Expr.Tuple) eb.right).elements;
              } else {
                components = new Expr[] { eb.right };
              }
              buffer = open(buffer, indent);
                for (int i = 0, l = fix.components.length; i < l; i++) {
                  switch (fix.components[i]) {
                    case Fixity.Component.Keyword kw -> {
                      buffer = buffer.append(Text.Literal(kw.name.toString()));
                    }
                    case Fixity.Component.Operand op -> {
                      buffer =
                        buffer.appendAll(parenthesize(
                                          prettyExpr(components[j], 2),
                                          getFixity(components[j]),
                                          op.prio));
                      j++;
                    }
                    case Fixity.Component.OperandList ol -> {
                      // PORT NOTE: source says `leftp`/`rightp`; jar ABI uses `lname`/`rname`.
                      buffer =
                        buffer.appendAll(enclose(
                                          prettyExpr(components[j]),
                                          prettyName(ol.lname),
                                          prettyName(ol.rname)));
                      j++;
                    }
                    default -> throw FatalError.unexpectedCase("component");
                  }
                }
              buffer = close(buffer);
            }
          }
          if (buffer.size() == 0) {
            // don't distribute operands
            // assume fixity: (i) : (j) (k)
            fix = getApplyFixity();

            buffer = open(buffer, indent);
              buffer = buffer.appendAll(parenthesize(
                                          prettyExpr(eb.left),
                                          getFixity(eb.left),
                                          getComponentPrio(
                                            fix.components[0])));
              buffer = buffer.appendAll(parenthesize(
                                          prettyExpr(eb.right, 2),
                                          getFixity(eb.right),
                                          getComponentPrio(
                                            fix.components[1])));
            buffer = close(buffer);
          }
        } else if (eb.kind == Expr.BinaryKind.And   ||
                   eb.kind == Expr.BinaryKind.Or     ||
                   eb.kind == Expr.BinaryKind.Implies ||
                   eb.kind == Expr.BinaryKind.Iff    ||
                   eb.kind == Expr.BinaryKind.Compose ||
                   eb.kind == Expr.BinaryKind.Project ||
                   eb.kind == Expr.BinaryKind.Pipe) {
          // assume fixity: (i): (j) <keyword> (k)
          buffer = open(buffer, indent);
            buffer =
              buffer.appendAll(parenthesize(prettyExpr(eb.left),
                                         getFixity(eb.left),
                                         getComponentPrio(fix.components[0])));
            buffer =
              buffer.append(Text.Literal(getKeyword(fix.components[1])));
            buffer =
              buffer.appendAll(parenthesize(prettyExpr(eb.right, 2),
                                         getFixity(eb.right),
                                         getComponentPrio(fix.components[0])));
          buffer = close(buffer);
        }
      }
      case Expr.GivenType eg -> {
        buffer = enclose(prettyNameDecl(eg.name),
                                   Text.ZedToken(Lexem.Lbrack),
                                   Text.ZedToken(Lexem.Rbrack));
      }
      case Expr.FreeType ef -> {
        buffer = new BufferSeq<>();
        buffer = open(buffer);
          buffer = buffer.appendAll(prettyNameDecl(ef.name));
          buffer = buffer.append(Text.ZedToken(Lexem.Defsyn));
          // don't use enumerate here
          buffer = open(buffer, Text.FormatKind.Line(2));
            for (int i = 0, l = ef.branches.length; i < l; i++) {
              h = prettyBranch(ef.branches[i]);
              if (i > 0) {
                buffer = open(buffer, Text.FormatKind.Append(0));
                  buffer = buffer.append(Text.ZedToken(Lexem.Mid));
                  buffer = buffer.appendAll(h);
                buffer = close(buffer);
              } else {
                buffer = buffer.appendAll(h);
              }
            }
          buffer = close(buffer);
        buffer = close(buffer);
      }
      case Expr.UniVar euv -> {
        // FIXME: what does this look like?
        buffer = new BufferSeq<>();
      }
      default -> throw FatalError.unexpectedCase("expression");
    }
    return buffer;
  }

  private Seq<Text> prettyLabelExpr(LabelExpr expr) {
    return prettyLabelExpr(expr, 0);
  }

  private Seq<Text> prettyLabelExpr(LabelExpr expr, int indent) {
    return switch (expr) {
      case LabelExpr.Guarded lg -> {
        Seq<Text> buffer = new BufferSeq<>();
        buffer = open(buffer, indent);
          buffer = open(buffer);
            buffer = buffer.appendAll(prettyPredicate(lg.guard));
            buffer = buffer.append(Text.ZedToken(Lexem.Trans));
          buffer = close(buffer);
          buffer = buffer.appendAll(prettyPredicate(lg.action));
        buffer = close(buffer);
        yield buffer;
      }
      case LabelExpr.Temporal lt -> prettyPredicate(lt.predicate, indent);
      case LabelExpr.Reference lr -> prettyNameAppl(lr.name);
      default -> throw FatalError.unexpectedCase("label expr");
    };
  }

  private Seq<Text> prettyDecl(Expr.Decl decl) {
    return switch (decl) {
      case Expr.Decl.Direct dd -> {
        // name1, ..., nameN : type
        Seq<Text> buffer = new BufferSeq<>();
        Seq<Seq<Text>> b = new BufferSeq<>();

        int l = dd.names.length;
        buffer = open(buffer);
          buffer = open(buffer);
            for (int i = 0; i < l; i++)
              b = b.append(prettyNameDecl(dd.names[i]));
            buffer = buffer.appendAll(enumerate(b, Lexem.Comma));
            buffer = buffer.append(Text.ZedToken(Lexem.Colon));
          buffer = close(buffer);
          buffer = buffer.appendAll(prettyExpr(dd.type, 2));
        buffer = close(buffer);
        yield buffer;
      }
      case Expr.Decl.Inclusion di -> prettyExpr(di.schema);
      case Expr.Decl.Eqn de -> prettyEqn(de, Text.ZedToken(Lexem.Defeq));
      default -> throw FatalError.unexpectedCase("declaration");
    };
  }

  private Seq<Text> prettyBranch(Expr.Branch branch) {
    return switch (branch) {
      case Expr.Branch.Constant bc -> prettyNameDecl(bc.name);
      case Expr.Branch.Function bf -> {
        Seq<Text> buffer = new BufferSeq<>();
        buffer = open(buffer, Text.FormatKind.Append(0));
          buffer = buffer.appendAll(prettyNameDecl(bf.name));
          buffer = buffer.appendAll(enclose(prettyExpr(bf.type),
                                                      Text.ZedToken(Lexem.Ldata),
                                                      Text.ZedToken(Lexem.Rdata)));
        buffer = close(buffer);
        yield buffer;
      }
      default -> throw FatalError.unexpectedCase("free type branch");
    };
  }

  private Seq<Text> prettyPredicate(Predicate pred) {
    return prettyPredicate(pred, 0);
  }

  private Seq<Text> prettyPredicate(Predicate pred, int indent) {
    Seq<Text> buffer = new BufferSeq<>();
    Seq<Seq<Text>> b;

    switch (pred) {
      case Predicate.Schema ps -> {
        return prettyExpr(ps.schema, indent);
      }
      case Predicate.Test pt -> {
        if (pt.set instanceof Expr.Variable sv) {
          Fixity fix = getFixity(pt.set);
          int ari = getArity(fix);
          if (fix != null && sv.name.actuals.length == 0 &&
              ari > 0 &&
              (ari == 1 || pt.elem instanceof Expr.Tuple)) {
            // (a,b) \elem (\_<=\_)    --> a <= b
            // a \elem (good\_)        --> good a
            // aber: x \elem (\_<=\_)  --> x \elem (\_<=\_)
            int j = 0;
            Expr[] components;

            if (pt.elem instanceof Expr.Tuple et) {
              components = et.elements;
            } else {
              components = new Expr[] { pt.elem };
            }

            buffer = open(buffer, Text.FormatKind.Block(indent));
            for (int i = 0, l = fix.components.length; i < l; i++) {
              switch (fix.components[i]) {
                case Fixity.Component.Keyword kw -> {
                  buffer = buffer.append(Text.Literal(kw.name.toString()));
                }
                case Fixity.Component.Operand op -> {
                  if (ari == 1) // only one operand
                    buffer =
                      buffer.appendAll(parenthesize(prettyExpr(pt.elem, 2),
                                                    getFixity(pt.elem),
                                                    op.prio));
                  else
                    buffer =
                      buffer.appendAll(parenthesize(
                                        prettyExpr(components[j], 2),
                                        getFixity(components[j]),
                                        op.prio));
                  j++;
                }
                case Fixity.Component.OperandList ol -> {
                  // PORT NOTE: source says `left`/`right`; jar ABI uses `lname`/`rname`.
                  buffer =
                    buffer.appendAll(enclose(
                                        prettyExpr(components[j]),
                                        prettyName(ol.lname),
                                        prettyName(ol.rname)));
                  j++;
                }
                default -> throw FatalError.unexpectedCase("component");
              }
            }
            return close(buffer);
          }
        }
        Fixity fix = getFixity(ZNames.in);
        // assume fixity (i) : (j) \in (k)
        buffer = open(buffer, indent);
          buffer = open(buffer);
            buffer =
              buffer.appendAll(parenthesize(
                                    prettyExpr(pt.elem),
                                    getFixity(pt.elem),
                                    getComponentPrio(fix.components[0])));
            buffer =
              buffer.append(Text.Literal(getKeyword(fix.components[1])));
          buffer = close(buffer);
          buffer =
            buffer.appendAll(parenthesize(
                                    prettyExpr(pt.set, 2),
                                    getFixity(pt.set),
                                    getComponentPrio(fix.components[2])));
        buffer = close(buffer);
      }
      case Predicate.Fact pf -> {
        if (pf.kind == Predicate.FactKind.False) {
          buffer = new ArraySeq<>(Text.ZedToken(Lexem.False));
        } else {
          buffer = new ArraySeq<>(Text.ZedToken(Lexem.True));
        }
      }
      case Predicate.Unary pu -> {
        buffer = new BufferSeq<>();
        Predicate.UnaryKind kind = pu.kind;

        if (kind == Predicate.UnaryKind.Await     ||
            kind == Predicate.UnaryKind.Everywhere ||
            kind == Predicate.UnaryKind.Not        ||
            kind == Predicate.UnaryKind.Prefix     ||
            kind == Predicate.UnaryKind.Repeat     ||
            kind == Predicate.UnaryKind.Somewhere) {
          Fixity fix = getFixity(pred);
          // assume fixity: (i) : <keyword> (j)
          buffer = open(buffer, indent);
            buffer =
              buffer.append(Text.Literal(getKeyword(fix.components[0])));
            buffer =
              buffer.appendAll(parenthesize(prettyPredicate(pu.oper, 2),
                                         getFixity(pu.oper),
                                         getComponentPrio(fix.components[1])));
          buffer = close(buffer);
        } else if (kind == Predicate.UnaryKind.TransLift) {
          buffer = enclose(prettyPredicate(pred),
                                     Text.ZedToken(Lexem.Ltrans),
                                     Text.ZedToken(Lexem.Rtrans));
        } else if (kind == Predicate.UnaryKind.StateLift) {
          buffer = enclose(prettyPredicate(pred),
                                     Text.ZedToken(Lexem.Lstate),
                                     Text.ZedToken(Lexem.Rstate));
        }
        // else: StepConstr, TimeConstr - fixme cases produce empty buffer
      }
      case Predicate.Binary pb -> {
        Predicate.BinaryKind kind = pb.kind;

        if (kind == Predicate.BinaryKind.And     ||
            kind == Predicate.BinaryKind.Or      ||
            kind == Predicate.BinaryKind.Choice  ||
            kind == Predicate.BinaryKind.Chop    ||
            kind == Predicate.BinaryKind.Compose ||
            kind == Predicate.BinaryKind.Iff     ||
            kind == Predicate.BinaryKind.Implies ||
            kind == Predicate.BinaryKind.Leadsto ||
            kind == Predicate.BinaryKind.Compute ||
            kind == Predicate.BinaryKind.Preempt) {
          // assume fixity: (i): (j) <keyword> (k)
          buffer = new BufferSeq<>();
          Fixity fix = getFixity(pred);
          buffer = open(buffer, indent);
            buffer =
              buffer.appendAll(parenthesize(prettyPredicate(pb.left),
                                         getFixity(pb.left),
                                         getComponentPrio(fix.components[0])));
            buffer =
              buffer.append(Text.Literal(getKeyword(fix.components[1])));
            buffer =
              buffer.appendAll(parenthesize(prettyPredicate(pb.right, 2),
                                         getFixity(pb.right),
                                         getComponentPrio(fix.components[2])));
          buffer = close(buffer);
        } else if (kind == Predicate.BinaryKind.Trans) {
          // FIXME: Is this a predicate of form: guard/action ?
          buffer = open(buffer, indent);
            buffer = buffer.appendAll(prettyPredicate(pb.left));
            buffer = buffer.append(Text.ZedToken(Lexem.Trans));
            buffer = buffer.appendAll(prettyPredicate(pb.right));
          buffer = close(buffer);
        } else if (kind == Predicate.BinaryKind.Guarded) {
          // FIXME: Is this a predicate of the form event[condition] ?
          buffer = open(buffer, indent);
            buffer = buffer.appendAll(prettyPredicate(pb.left));
            buffer =
              buffer.appendAll(enclose(prettyPredicate(pb.right),
                                                 Text.ZedToken(Lexem.Lbrack),
                                                 Text.ZedToken(Lexem.Rbrack)));
          buffer = close(buffer);
        }
      }
      case Predicate.Quantor pq -> {
        buffer = new BufferSeq<>();
        Predicate.QuantorKind kind = pq.kind;

        if (kind == Predicate.QuantorKind.Exists  ||
            kind == Predicate.QuantorKind.Exists1  ||
            kind == Predicate.QuantorKind.Forall   ||
            kind == Predicate.QuantorKind.Let) {
          // <quantor> <schema> @ <predicate>
          Lexem lexem = getQuantorLexem(kind);
          buffer = open(buffer, indent);
            buffer = open(buffer, Text.FormatKind.Block(0));
              buffer = buffer.append(Text.ZedToken(lexem));
              buffer = buffer.appendAll(prettyMatrix(pq.matrix, 2));
              buffer = buffer.append(Text.ZedToken(Lexem.Spot));
            buffer = close(buffer);
            buffer = buffer.appendAll(prettyPredicate(pq.range, 2));
          buffer = close(buffer);
        }
      }
      default -> throw FatalError.unexpectedCase("predicate");
    }
    return buffer;
  }

  private Seq<Text> prettyAbbrev(Expr.Decl eqn, NameDecl[] formals) {
    if (eqn instanceof Expr.Decl.Eqn de) {
      Seq<Text> buffer = new BufferSeq<>();
      Name name = de.name.name;
      Expr expr = de.def;
      Fixity fix = getFixity(name);
      if (fix != null && fix.isGeneric) {
        buffer = open(buffer);
          buffer = open(buffer);
            buffer = open(buffer, Text.FormatKind.Append(0));
              int op = 0;
              for (int i = 0; i < fix.components.length; i++)
                switch (fix.components[i]) {
                  case Fixity.Component.Operand cop -> {
                    buffer = buffer.appendAll(prettyNameDecl(formals[op++]));
                  }
                  case Fixity.Component.OperandList col -> {
                    // PORT NOTE: source says `left`/`right`; jar ABI uses `lname`/`rname`.
                    buffer = buffer.appendAll(enclose(
                                                prettyNameDecl(formals[op++]),
                                                prettyName(col.lname),
                                                prettyName(col.rname)));
                  }
                  case Fixity.Component.Keyword ckw -> {
                    buffer = buffer.append(Text.Literal(ckw.name.toString()));
                  }
                  default -> throw FatalError.unexpectedCase("component");
                }
            buffer = close(buffer);
            buffer = buffer.append(Text.ZedToken(Lexem.Defeq));
          buffer = close(buffer);
          buffer = buffer.appendAll(prettyExpr(expr, 2));
        buffer = close(buffer);
      } else {
        buffer = prettyEqn(eqn, Text.ZedToken(Lexem.Defeq));
      }
      return
        new ArraySeq<>(Text.ZedMarkup(Text.ZedMarkupKind.Unboxed,
                               new Text[0],
                               buffer.toArray(Text.class),
                               new Text[0]));
    } else {
      return null;
    }
  }

  private Seq<Text> prettyDeclAttr(DeclAttr attr) {
    if (attr != null) {
      if (attr == DeclAttr.Plain) {
        return new ArraySeq<>();
      } else if (attr == DeclAttr.Data) {
        return new ArraySeq<>(Text.ZedToken(Lexem.Data));
      } else if (attr == DeclAttr.Port) {
        return new ArraySeq<>(Text.ZedToken(Lexem.Port));
      } else if (attr == DeclAttr.Init) {
        return new ArraySeq<>(Text.ZedToken(Lexem.Init));
      } else if (attr == DeclAttr.Property) {
        return new ArraySeq<>(Text.ZedToken(Lexem.Property));
      } else if (attr instanceof DeclAttr.Alias da) {
        return new ArraySeq<>(Text.Literal(da.symbol));
      }
    }
    return new ArraySeq<>();
  }

  private Seq<Text> prettyEqn(Expr.Decl eqn, Text token) {
    if (eqn instanceof Expr.Decl.Eqn de) {
      Seq<Text> buffer = new BufferSeq<>();
      buffer = open(buffer);
        buffer = open(buffer);
          buffer = buffer.appendAll(prettyNameDecl(de.name));
          buffer = buffer.append(token);
        buffer = close(buffer);
        buffer = open(buffer, 2);
          buffer = buffer.appendAll(prettyExpr(de.def));
        buffer = close(buffer);
      buffer = close(buffer);
      return buffer;
    } else {
      return null;
    }
  }

  private Seq<Text> prettyTransition(Transition transition) {
    // Transition is a single-case ADT; fields are source, target, label.
    Seq<Text> buffer = new BufferSeq<>();
    buffer = open(buffer, Text.FormatKind.Line(0));
      buffer = buffer.appendAll(prettyNameAppl(transition.source));
      buffer = open(buffer, 2);
        buffer = buffer.append(Text.ZedToken(Lexem.When));
        buffer = buffer.appendAll(prettyLabelExpr(transition.label, 2));
      buffer = close(buffer);
      buffer = open(buffer, 2);
        buffer = buffer.append(Text.ZedToken(Lexem.Goto));
        buffer = buffer.appendAll(prettyNameAppl(transition.target));
      buffer = close(buffer);
    buffer = close(buffer);
    return buffer;
  }

  private int getArity(Fixity fixity) {
    int arity = 0;
    if (fixity == null)
      return 0;
    for (int i = fixity.components.length; i-- > 0; )
      switch (fixity.components[i]) {
        case Fixity.Component.Operand op -> arity++;
        case Fixity.Component.OperandList ol -> arity++;
        default -> { /* Keyword contributes no arity */ }
      }
    return arity;
  }

  private Lexem getQuantorLexem(Expr.QuantorKind kind) {
    if (kind == Expr.QuantorKind.Exists)  return Lexem.Exists;
    if (kind == Expr.QuantorKind.Exists1) return Lexem.Exists1;
    if (kind == Expr.QuantorKind.Forall)  return Lexem.Forall;
    if (kind == Expr.QuantorKind.Lambda)  return Lexem.Lambda;
    if (kind == Expr.QuantorKind.Let)     return Lexem.Let;
    if (kind == Expr.QuantorKind.Mu)      return Lexem.Mu;
    if (kind == Expr.QuantorKind.Set)     return Lexem.Lset;
    throw FatalError.unexpectedCase("quantor kind");
  }

  private Lexem getUnaryLexem(Expr.UnaryKind kind) {
    if (kind == Expr.UnaryKind.Power) return Lexem.Power;
    if (kind == Expr.UnaryKind.Theta) return Lexem.Theta;
    if (kind == Expr.UnaryKind.Not)   return Lexem.Not;
    if (kind == Expr.UnaryKind.Delta) return Lexem.Delta;
    if (kind == Expr.UnaryKind.Xi)    return Lexem.Xi;
    if (kind == Expr.UnaryKind.Pre)   return Lexem.Pre;
    // StateFlat, StepFlat: FIXME
    return null;
  }

  private Lexem getBinaryLexem(Expr.BinaryKind kind) {
    if (kind == Expr.BinaryKind.And)     return Lexem.And;
    if (kind == Expr.BinaryKind.Or)      return Lexem.Or;
    if (kind == Expr.BinaryKind.Implies) return Lexem.Implies;
    if (kind == Expr.BinaryKind.Iff)     return Lexem.Iff;
    if (kind == Expr.BinaryKind.Compose) return Lexem.Compose;
    if (kind == Expr.BinaryKind.Project) return Lexem.Project;
    if (kind == Expr.BinaryKind.Pipe)    return Lexem.Pipe;
    return null;
  }

  private Seq<Text> getDecoration(Name name) {
    return prettyDecoration(name.decoration());
  }

  private Seq<Text> prettyDecoration(String decoration) {
    int i, j;
    Seq<Text> buffer = new BufferSeq<>();

    for (i = decoration.length(); i-- > 0; ) {
      switch (decoration.charAt(i)) {
        case Name.input:
          buffer = buffer.prepend(Text.ZedToken(Lexem.StrokeIn));
          break;
        case Name.output:
          buffer = buffer.prepend(Text.ZedToken(Lexem.StrokeOut));
          break;
        case Name.prime:
          buffer = buffer.prepend(Text.ZedToken(Lexem.StrokePrime));
          break;
        case Name.index:
          j = i;
          while (i-- > 0 && decoration.charAt(i) != Name.index);
          buffer =
            buffer.prepend(Text.ZedToken(Lexem.StrokeIndex(decoration.substring(i + 1, j))));
          break;
        // no default case
      }
    }
    return buffer;
  }

  private int getComponentPrio(Fixity.Component component) {
    if (component instanceof Fixity.Component.Operand op)
      return op.prio;
    return 0;
  }

  private String getKeyword(Fixity.Component component) {
    if (component instanceof Fixity.Component.Keyword kw)
      return kw.name.toString();
    return null;
  }

  private Fixity getFixity(Name name) {
    try {
      return environment.getFixity(name).fixity;
    } catch (Exception e) {
      return null;
    }
  }

  /** Return fixity assignment of given special construct (or null if
    * none).  The following lexems have an assignment: {@code Lexem.Cross},
    * {@code Lexem.Theta}, {@code Lexem.Delta}, {@code Lexem.Xi},
    * {@code Lexem.Pre}, {@code Lexem.Dot}.
    */
  public Fixity specialFixity(Lexem lexem) {
    try {
      return specialFixities.get(lexem);
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  private Fixity getPrefixFixity(int prio, Lexem lexem) {
    return
      new Fixity(prio, false,
                 new Fixity.Component[] {
                   Fixity.Component.Keyword(Name.stdName(config.getRepr(lexem))),
                   Fixity.Component.Operand(prio + 1)
                 });
  }

  private Fixity getPostfixFixity(int prio, Lexem lexem) {
    return
      new Fixity(prio, false,
                 new Fixity.Component[] {
                   Fixity.Component.Operand(prio + 1),
                   Fixity.Component.Keyword(Name.stdName(config.getRepr(lexem)))
                 });
  }

  private Fixity getAssocInfixFixity(int prio, Lexem lexem) {
    return
      new Fixity(prio, false,
                 new Fixity.Component[] {
                   Fixity.Component.Operand(prio),
                   Fixity.Component.Keyword(Name.stdName(config.getRepr(lexem))),
                   Fixity.Component.Operand(prio)
                 });
  }

  private Fixity getInfixFixity(int prio, Lexem lexem) {
    return
      new Fixity(prio, false,
                 new Fixity.Component[] {
                   Fixity.Component.Operand(prio + 1),
                   Fixity.Component.Keyword(Name.stdName(config.getRepr(lexem))),
                   Fixity.Component.Operand(prio + 2)
                 });
  }

  private Fixity getQuantorFixity() {
    return
      new Fixity(100, false, new Fixity.Component[] {});
  }

  private Fixity getApplyFixity() {
    return
      new Fixity(900, false,
                 new Fixity.Component[] {
                   Fixity.Component.Operand(900),
                   Fixity.Component.Operand(901)
                 });
  }

  private Fixity getFixity(Expr e) {
    return switch (e) {
      case Expr.FreeType ef -> null;
      case Expr.GivenType eg -> null;
      case Expr.Text et -> null;
      case Expr.Tuple et -> null;
      case Expr.Denotation ed -> null;
      case Expr.Number en -> null;
      case Expr.Signature es -> null;
      case Expr.Binding eb -> null;
      case Expr.Display ed -> null; // never put parantheses around these
      case Expr.Product ep -> specialFixity(Lexem.Cross);
      case Expr.Cond ec -> specialFixity(Lexem.If);
      case Expr.Select es -> specialFixity(Lexem.Dot);
      case Expr.Quantor eq -> {
        if (eq.kind == Expr.QuantorKind.Set)
          yield null;
        else
          yield specialFixity(getQuantorLexem(eq.kind));
      }
      case Expr.Unary eu -> {
        Expr.UnaryKind kind = eu.kind;
        if (kind instanceof Expr.UnaryKind.Decorate) yield null;
        else if (kind instanceof Expr.UnaryKind.Renaming) yield null;
        else if (kind instanceof Expr.UnaryKind.SelectiveDelta) yield null;
        else if (kind instanceof Expr.UnaryKind.Hide) yield null;
        else yield specialFixity(getUnaryLexem(kind));
      }
      case Expr.Binary eb -> {
        if (eb.kind == Expr.BinaryKind.Apply) {
          if (eb.left instanceof Expr.Variable lv) {
            Fixity result;
            if ((result = getFixity(lv.name.name)) != null)
              yield result;
            yield new Fixity(900, false,
                             new Fixity.Component[] {
                               Fixity.Component.Keyword(lv.name.name),
                               Fixity.Component.Operand(901)
                             });
          } else {
            yield getApplyFixity();
          }
        } else {
          yield specialFixity(getBinaryLexem(eb.kind));
        }
      }
      case Expr.Variable ev -> getFixity(ev.name.name);
      case Expr.UniVar euv -> null;
      default -> throw FatalError.unexpectedCase("expression");
    };
  }

  private Lexem getQuantorLexem(Predicate.QuantorKind kind) {
    if (kind == Predicate.QuantorKind.Exists)  return Lexem.Exists;
    if (kind == Predicate.QuantorKind.Exists1) return Lexem.Exists1;
    if (kind == Predicate.QuantorKind.Forall)  return Lexem.Forall;
    if (kind == Predicate.QuantorKind.Let)     return Lexem.Let;
    throw FatalError.unexpectedCase("predicate quantor kind");
  }

  private Lexem getUnaryLexem(Predicate.UnaryKind kind) {
    if (kind == Predicate.UnaryKind.Not)        return Lexem.Not;
    if (kind == Predicate.UnaryKind.Await)      return Lexem.Await;
    if (kind == Predicate.UnaryKind.Everywhere) return Lexem.Everywhere;
    if (kind == Predicate.UnaryKind.Somewhere)  return Lexem.Somewhere;
    if (kind == Predicate.UnaryKind.Repeat)     return Lexem.Repeat;
    if (kind == Predicate.UnaryKind.Prefix)     return Lexem.Prefix;
    return null;
  }

  private Lexem getBinaryLexem(Predicate.BinaryKind kind) {
    if (kind == Predicate.BinaryKind.And)     return Lexem.And;
    if (kind == Predicate.BinaryKind.Or)      return Lexem.Or;
    if (kind == Predicate.BinaryKind.Implies) return Lexem.Implies;
    if (kind == Predicate.BinaryKind.Iff)     return Lexem.Iff;
    if (kind == Predicate.BinaryKind.Chop)    return Lexem.Chop;
    if (kind == Predicate.BinaryKind.Compose) return Lexem.Compose;
    if (kind == Predicate.BinaryKind.Leadsto) return Lexem.Leadsto;
    if (kind == Predicate.BinaryKind.Preempt) return Lexem.Preempt;
    if (kind == Predicate.BinaryKind.Compute) return Lexem.Compute;
    if (kind == Predicate.BinaryKind.Choice)  return Lexem.Choice;
    return null;
  }

  private Fixity getFixity(Predicate p) {
    return switch (p) {
      case Predicate.Fact pf -> null;
      case Predicate.Test pt -> null;
      case Predicate.Schema ps -> getFixity(ps.schema);
      case Predicate.Quantor pq -> specialFixity(getQuantorLexem(pq.kind));
      case Predicate.Unary pu -> {
        // FIXME - StepConstr and TimeConstr produce null
        yield specialFixity(getUnaryLexem(pu.kind));
      }
      case Predicate.Binary pb -> {
        if (pb.kind == Predicate.BinaryKind.Guarded)
          yield null; // FIXME
        else
          yield specialFixity(getBinaryLexem(pb.kind));
      }
      default -> throw FatalError.unexpectedCase("predicate");
    };
  }

  private Seq<Text> enumerate(Seq<Seq<Text>> s, Lexem sep) {
    return enumerate(s, sep, 0);
  }

  private Seq<Text> enumerate(Seq<Seq<Text>> s, Lexem sep, int indent) {
    return enumerate(s, sep, Text.FormatKind.Line(indent));
  }

  private Seq<Text> enumerate(Seq<Seq<Text>> s,
                              Lexem sep,
                              Text.FormatKind kind) {
    Seq<Text> buffer = new BufferSeq<>();
    Seq<Text> c;

    if (!s.isEmpty()) {
      Enum<Seq<Text>> e = s.enumerate();

      if (s.size() > 1) {
        buffer = open(buffer, kind);
          while (e.more()) {
            c = e.current();
            e.advance(); // look ahead
            if (e.more()) {
              buffer = open(buffer, Text.FormatKind.Append(0));
                buffer = buffer.appendAll(c);
                buffer = buffer.append(Text.ZedToken(sep));
              buffer = close(buffer);
            } else {
              buffer = open(buffer, Text.FormatKind.Block(0));
                buffer = buffer.appendAll(c);
              buffer = close(buffer);
            }
          }
        buffer = close(buffer);
      } else {
        buffer = open(buffer, kind);
          buffer = buffer.appendAll(e.current());
        buffer = close(buffer);
      }
    }
    return buffer;
  }

  private Seq<Text> enclose(Seq<Text> s, Seq<Text> left, Seq<Text> right) {
    Seq<Text> buffer = new BufferSeq<>();

    buffer = open(buffer, Text.FormatKind.Append(0));
      buffer = buffer.appendAll(left);
      buffer = open(buffer, Text.FormatKind.Block(2));
        buffer = buffer.appendAll(s);
      buffer = close(buffer);
      buffer = buffer.appendAll(right);
    buffer = close(buffer);
    return buffer;
  }

  private Seq<Text> enclose(Seq<Text> s, Text left, Text right) {
    return enclose(s, new ArraySeq<>(left), new ArraySeq<>(right));
  }

  private Seq<Text> parenthesize(Seq<Text> s, Fixity fix, int prio) {
    if (fix != null && fix.prio < prio) {
      return enclose(s, Text.ZedToken(Lexem.Lparenth), Text.ZedToken(Lexem.Rparenth));
    } else {
      return s;
    }
  }

  private Seq<Text> open(Seq<Text> text, Text.FormatKind kind) {
    return text.append(Text.FormatBegin(kind));
  }

  private Seq<Text> open(Seq<Text> text, int indent) {
    return open(text, Text.FormatKind.Block(indent));
  }

  private Seq<Text> open(Seq<Text> text) {
    return open(text, 0);
  }

  private Seq<Text> close(Seq<Text> text) {
    return text.append(Text.FormatEnd);
  }

  private Fixity.Component[] getComponents(Fixity fix) {
    // Fixity is a single-case ADT; components field is directly accessible.
    return fix.components;
  }

}
