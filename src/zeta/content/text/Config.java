package zeta.content.text;

import java.util.EmptyStackException;

import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.Seq;
import zeta.content.text.Text.FormatKind;
import zeta.content.zirp.Fixity;
import zeta.format.Format;
import zeta.session.Diag;
import zeta.util.FatalError;
import zeta.util.Name;
import zeta.util.Term;

/**
 * The abstract class of configurations associated with concrete languages. <p>
 *
 * Objects of this class give generic information about a concrete language,
 * to be used by the parser and unparser. <p>
 *
 * @version $Id: Config.pizza,v 1.4 1998/12/08 09:30:09 wg Exp $
 * @author Wolfgang Grieskamp (wg@cs.tu-berlin.de)
 *
 * <h2>Port notes</h2>
 *
 * This class drives the `pretty-text` content type, so every line of it is covered by the
 * oracle. It is also the only class outside `zeta.agl` that had mangled methods -- three of
 * them, on its private `Stack` -- because `Stack<A>` is generic and its methods mention `A`.
 * Being private, nothing binary can reach it, so the port drops the bridges and writes the
 * stack idiomatically.
 *
 * The two private helper ADTs are modernised, since nothing outside this file can see them:
 * `Tree` and `Stack` are sealed interfaces of records, so the switches over them are
 * exhaustive without a `default`. The public ADTs they carry -- `Text` and
 * `Text.FormatKind` -- are not sealed and cannot be, so switches over those keep an explicit
 * `default` that raises `FatalError.unexpectedCase`, which is what that method exists for.
 *
 * `prettyText` needs one special case the type patterns cannot express: `Text.FormatEnd` is a
 * *constant of the base class*, not a subclass (see 9.6 on how Pizza compiles a nullary case
 * declared without parentheses), so it matches no `case Text.X t` arm and has to be
 * recognised by identity in the `default`.
 */
public abstract class Config extends Term {

  /**
   * Accumulated diagnostics of directive evaluation.
   * Subclasses may add diagnostics to this field.
   */
  protected transient Diag diag = new Diag();

  /**
   * Make a copy of this configuration.
   */
  public Config copy() {
    return this;
  }

  /**
   * Enter and evaluate directives found in given array of text. The
   * configuration will be enriched if directives are found which have
   * effect for the concrete language.
   */
  public abstract void enter(Text[] texts);

  /**
   * Get and clear accumulated diagnostics produced by directive
   * evaluation.
   */
  public Diag getAndClearDiag() {
    Diag d = diag;
    diag = new Diag();
    return d;
  }

  /**
   * Tell wether one ore more line separators (Lexem.Linesep) at the
   * left of this lexem are `offside'. An offside line separator is
   * ignored by the parser. (Resp. can be inserted as layout by the
   * unparser).
   */
  public boolean isLeftOffside(Lexem lexem) {
    return false;
  }

  /**
   * Tell wether one ore more line separators (Lexem.Linesep) at the
   * right of this lexem are `offside'. An offside line separator is
   * ignored by the parser. (Resp. can be inserted as layout by the
   * unparser).
   */
  public boolean isRightOffside(Lexem lexem) {
    return false;
  }

  /**
   * Return wether there have to be spaces put between left and right
   * lexems.
   */
  public boolean needSpaceBetween(Text left, Text right) {
    return true;
  }

  /**
   * Return representation of the given lexem in the concrete language.
   */
  public abstract String getRepr(Lexem lexem);

  /** Compute a Text object into a Format of this configuration's language */
  public Format prettyText(Text text) {
    return switch (text) {
      case Text.Literal t -> prettyLiteral(t.contents);
      case Text.DocMarkup t -> prettyDocMarkup(t.name, t.params, t.subtexts);
      case Text.ModelInclusion t -> prettyModelInclusion(t.name);
      case Text.ZedSection t -> prettyZedSection(t.name, t.parents, t.subtexts);
      case Text.ZedClass t -> prettyZedClass(t.name, t.parents, t.formals, t.subtexts);
      case Text.ZedFixity t -> prettyZedFixity(t.fixity);
      case Text.ZedMacro t -> prettyZedMacro(t.name, t.argc, t.def);
      case Text.ZedPragma t -> prettyZedPragma(t.name, t.subtexts);
      case Text.ZedMarkup t -> prettyZedMarkup(t.kind, t.header, t.decls, t.props);
      case Text.ZedToken t -> prettyZedToken(t.lexem);
      case Text.FormatBegin t -> Format.empty;
      default -> {
        // FormatEnd is a constant on the base class, so no type pattern matches it
        if (text == Text.FormatEnd) yield Format.empty;
        throw FatalError.unexpectedCase("text");
      }
    };
  }

  protected Format prettyLiteral(String contents) {
    return Format.literal(contents);
  }

  /** Return a format for the given DocMarkup parameters */
  protected Format prettyDocMarkup(String name,
                                   String[] params,
                                   Text[] subtexts) {
    return Format.block(prettySubtexts(subtexts, FormatKind.Beneath(0)),
                        Format.brk);
  }

  /** Return a model inclusion format for the given Name */
  protected abstract Format prettyModelInclusion(Name name);

  /** Return a zed section format with given name, parents and subtexts */
  protected abstract Format prettyZedSection(Name name,
                                             Name[] parents,
                                             Text[] subtexts);

  /** Return a zed class format with given name, parents, formal parameters
      and subtexts */
  protected abstract Format prettyZedClass(Name name,
                                           Name[] parents,
                                           Name[] formals,
                                           Text[] subtexts);

  /** Return a format for the given zed fixity */
  protected abstract Format prettyZedFixity(Fixity fixity);

  /** Return a zed macro format with given name, argument number
      and definition */
  protected abstract Format prettyZedMacro(Name name, int argc, Lexem[] def);

  /** Return a format for the given ZedPragma */
  protected abstract Format prettyZedPragma(Name name, Text[] subtexts);

  /** Return a format for the given ZedMarkup parameters */
  protected abstract Format prettyZedMarkup(Text.ZedMarkupKind kind,
                                            Text[] header,
                                            Text[] decls,
                                            Text[] props);

  /** Return a format for the given zed token */
  protected Format prettyZedToken(Lexem lexem) {
    return Format.literal(getRepr(lexem));
  }

  /** Return wether a break should be done between the left and right text */
  protected abstract boolean needBreakBetween(Text left, Text right);

  private <A, B> A rightmostLeaf(Tree<A, B> tree) {
    return switch (tree) {
      case Tree.Leaf<A, B> leaf -> leaf.data();
      case Tree.Root<A, B> root -> {
        Seq<Tree<A, B>> children = root.children();
        if (children.isEmpty())
          yield null;
        else
          yield rightmostLeaf(children.at(children.size() - 1));
      }
    };
  }

  private <A, B> A leftmostLeaf(Tree<A, B> tree) {
    return switch (tree) {
      case Tree.Leaf<A, B> leaf -> leaf.data();
      case Tree.Root<A, B> root -> {
        Seq<Tree<A, B>> children = root.children();
        if (children.isEmpty())
          yield null;
        else
          yield leftmostLeaf(children.head());
      }
    };
  }

  private Format prettyTree(Tree<Text, FormatKind> tree) {
    return switch (tree) {
      case Tree.Leaf<Text, FormatKind> leaf -> prettyText(leaf.data());
      case Tree.Root<Text, FormatKind> root -> {
        Seq<Tree<Text, FormatKind>> children = root.children();
        Enum<Tree<Text, FormatKind>> e = children.enumerate();
        Seq<Format> formats = new BufferSeq<Format>();
        if (e.more()) {
          Tree<Text, FormatKind> last;
          Tree<Text, FormatKind> current;
          Text left;
          Text right;

          last = e.current();
          formats = formats.append(prettyTree(last));
          for (e.advance(); e.more(); e.advance()) {
            current = e.current();
            left = rightmostLeaf(last);
            right = leftmostLeaf(current);
            if (left != null && right != null) {
              if (needBreakBetween(left, right))
                formats = formats.append(Format.brk);
              else if (needSpaceBetween(left, right))
                formats = formats.append(Format.space);
            }
            formats = formats.append(prettyTree(current));
            last = current;
          }
          yield wrapFormats(root.nodeinfo(), formats.toArray(Format.class));
        } else {
          yield Format.empty;
        }
      }
    };
  }

  private Format wrapFormats(FormatKind kind, Format[] formats) {
    return switch (kind) {
      case FormatKind.Block k -> Format.block(formats).indent(k.indent);
      case FormatKind.Group k -> Format.group(formats).indent(k.indent);
      case FormatKind.Line k -> Format.line(formats).indent(k.indent);
      case FormatKind.Beneath k -> Format.beneath(formats).indent(k.indent);
      case FormatKind.Append k -> Format.append(formats).indent(k.indent);
      case FormatKind.Beside k -> Format.beside(formats).indent(k.indent);
      default -> throw FatalError.unexpectedCase("format kind");
    };
  }

  private Tree<Text, FormatKind> buildTree(Text[] subtexts, FormatKind k) {
    Stack<Seq<Tree<Text, FormatKind>>> stack = Stack.empty();
    Seq<Tree<Text, FormatKind>> buffer = new BufferSeq<Tree<Text, FormatKind>>();

    // Backwards, which is what makes the grouping brackets nest correctly: a FormatBegin or
    // a left glue CLOSES the group being accumulated, and a FormatEnd or right glue OPENS one.
    for (int i = subtexts.length; i-- > 0;) {
      Text t = subtexts[i];
      if (t instanceof Text.FormatBegin fb) {
        buffer = stack.top().prepend(new Tree.Root<Text, FormatKind>(buffer, fb.kind));
        stack = stack.pop();
      } else if (t instanceof Text.ZedToken zt && zt.lexem == Lexem.Lglue) {
        // left grouping bracket
        buffer = stack.top().prepend(
                   new Tree.Root<Text, FormatKind>(buffer, FormatKind.Block(2)));
        stack = stack.pop();
      } else if (t == Text.FormatEnd
                 || (t instanceof Text.ZedToken zt2 && zt2.lexem == Lexem.Rglue)) {
        // right grouping bracket
        stack = stack.push(buffer);
        buffer = new BufferSeq<Tree<Text, FormatKind>>();
      } else {
        buffer = buffer.prepend(new Tree.Leaf<Text, FormatKind>(t));
      }
    }
    return new Tree.Root<Text, FormatKind>(buffer, k);
  }

  /* compute a compound Format of the given kind from the given
     array of subtexts */
  public Format prettySubtexts(Text[] subtexts, FormatKind kind) {
    return prettyTree(buildTree(subtexts, kind));
  }

  /* compute a compound Format of the given kind from the given
     sequence of subtexts */
  public Format prettySubtexts(Seq<Text> subtexts, FormatKind kind) {
    return prettySubtexts(subtexts.toArray(Text.class), kind);
  }

  // INNER CLASSES

  /** A tree of data.
      Each node is either a leaf or has a Sequence of children and
      additional info. */
  private sealed interface Tree<A, B> {
    record Root<A, B>(Seq<Tree<A, B>> children, B nodeinfo) implements Tree<A, B> {}

    record Leaf<A, B>(A data) implements Tree<A, B> {}
  }

  /** A persistent stack of data.  Immutable: `push` and `pop` return new stacks, which is what
    * `buildTree` relies on. */
  private sealed interface Stack<A> {

    record EmptyStack<A>() implements Stack<A> {}

    record FullStack<A>(A top, Stack<A> rest) implements Stack<A> {}

    @SuppressWarnings("unchecked")
    static <A> Stack<A> empty() {
      return new EmptyStack<A>();
    }

    /** Get the top element of the stack. */
    default A top() throws EmptyStackException {
      return switch (this) {
        case EmptyStack<A> s -> throw new EmptyStackException();
        case FullStack<A> s -> s.top();
      };
    }

    /** Push a new element on top of the stack. */
    default Stack<A> push(A elem) {
      return new FullStack<A>(elem, this);
    }

    /** Take top element from stack and return the rest. */
    default Stack<A> pop() throws EmptyStackException {
      return switch (this) {
        case EmptyStack<A> s -> throw new EmptyStackException();
        case FullStack<A> s -> s.rest();
      };
    }

    /** Test wether stack is empty. */
    default boolean isEmpty() {
      return this instanceof EmptyStack;
    }
  }
}
