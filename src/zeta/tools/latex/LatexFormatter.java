package zeta.tools.latex;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashSet;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.text.LaTeXFragment;
import zeta.content.text.Text;
import zeta.content.text.TextContentTypes;
import zeta.format.Format;
import zeta.session.ComputationInterrupted;
import zeta.session.Diag;
import zeta.session.Failures;
import zeta.session.Session;
import zeta.session.Target;
import zeta.session.Unit;
import zeta.tools.util.SessionUtil;
import zeta.util.Name;

/**
 * Runs LaTeX on a source to compute the `dvi` content of its units.
 *
 * <h2>Port notes</h2>
 *
 * `zeta.agl.Enum` is imported explicitly: with Java 5, `Enum` alone is ambiguous with
 * `java.lang.Enum`.
 *
 * `getIncludes0` passed the method `getIncludes1` itself to `reduce` (a Pizza method
 * reference, compiled to a closure); here it is `this::getIncludes1`.  The lambda in `run`
 * that defines the content of every unit of the source is kept as `Seq.apply` over the same
 * sequence; it captures only effectively final locals.
 *
 * `InterruptedException` was caught in `run` though nothing in its body can throw it; Java
 * rejects that catch clause, so it is dropped (PORT NOTE: unreachable, hence unobservable).
 *
 * PORT CHANGE (cancellation; see `zeta.session.ComputationInterrupted`).  Under
 * `Thread.stop` an interrupted LaTeX run stopped the reading thread and left the `latex`
 * process running.  Now `filterLatexOutput` checks for an interrupt once per line of LaTeX
 * output, and `run` destroys the process before letting the interrupt unwind; no `dvi` is
 * defined.  `latex` runs in `
onstopmode` and writes its log line by line, so a line is the
 * natural granularity; a read blocked on a silent `latex` cannot be woken by an interrupt
 * (it is a pipe read), but `
onstopmode` never waits for input.
 */
class LatexFormatter {

  private static final int MAXERRORS = 40;


  private final Session session;
  private final LatexAdaptor client;

  private LatexFormatter(Session session, LatexAdaptor client) {
    this.session = session;
    this.client = client;
  }

  static void computeDoc(Session session, LatexAdaptor client,
                         Target target,
                         File file, Seq<Unit> units)
  throws Failures.CannotCompute, Failures.ComputationFailed {
    new LatexFormatter(session, client).run(target, file, units);
  }

  private void run(Target target, File file, Seq<Unit> units)
  throws Failures.CannotCompute, Failures.ComputationFailed {
    try {
      String name = file.getPath(); // FIXME: remove me
      Seq<Target> deps = writeZetaInputs(ArraySeq.from(getIncludes(units)));
      client.sendProgress("runnnig latex for source " + name);
      Process p = Runtime.getRuntime().exec(new String[] {
        System.getProperty("zeta.prog.latex"),
        "\\nonstopmode\\input{" + name + "}"
      });
      // blocks under Windows NT if we do not close the output stream
      // we can use the LaTeX log file instead
      // p.getInputStream().close();
      // p.waitFor();
      // int i = name.lastIndexOf(".");
      // InputStream in = new BufferedInputStream(new FileInputStream(
      //   (i < 0 ? name : name.substring(0, i)) + ".log"));
      // client.sendProgress("analyzing latex output");
      // SEEMS TO BE FIXED IN JDK 1.2
      InputStream in = new BufferedInputStream(p.getInputStream());

      boolean ok;
      try {
        ok = filterLatexOutput(in, name);
      }
      catch (ComputationInterrupted e) {
        // PORT CHANGE (cancellation): see the class comment.
        p.destroy();
        throw e;
      }
      if (!ok) {
        p.destroy();
        throw new Failures.ComputationFailed(target);
      } else {
        p.destroy();
        File outFile;
        int i = name.lastIndexOf(".");
        if (i >= 0)
          outFile = new File(name.substring(0, i) + ".dvi");
        else
          outFile = new File(name + ".dvi");
        Date definedAt = new Date();
        units.apply(
          (Unit unit) -> {
            unit.define(TextContentTypes.dvi,
                        outFile,
                        true,
                        definedAt,
                        units.map(
                          (Unit u) -> new Target(u, TextContentTypes.text)
                        ).appendAll(deps).toArray(Target.class),
                        client
                       );
          }
        );
      }
    } catch (IOException e) {
      client.sendDiag(new Diag(
                            LatexAdaptor.thisLocator,
                            Diag.Category.Error,
                            "execution of latex command failed: I/O error: " +
                            e.getMessage()));
      return;
    }
  }

  private char last;

  private String readUntil(InputStream in, char e1) throws IOException {
    return readUntil(in, new char[] { e1 });
  }

  private String readUntil(InputStream in, char e1, char e2)
  throws IOException {
    return readUntil(in, new char[] { e1, e2 });
  }

  private String readUntil(InputStream in, char[] end) throws IOException {
    StringBuffer s = new StringBuffer();
    while (in.available() > 0) {
      char c = (char) in.read();
      for (int i = 0; i < end.length; i++)
        if (c == end[i]) {
          last = c;
          return new String(s);
        }
      s.append(c);
    }
    return new String(s);
  }

  private boolean filterLatexOutput(InputStream in, String name)
  throws IOException {
    int errors = 0;
    File actFile = client.locateFile(new File(name));
    if (actFile == null) actFile = new File(name);
    Seq<File> file_stack = new BufferSeq<File>().append(actFile);
    BufferedReader rd = new BufferedReader(new InputStreamReader(in));
    String line;
    while ((line = rd.readLine()) != null) {
      // PORT CHANGE (cancellation): checkpoint; see the class comment.
      ComputationInterrupted.checkpoint();
      if (line.startsWith("! ")) {
        errors++;
        Seq<Format> lines = new BufferSeq<Format>();
        lines = lines.append(Format.literal(line.trim()));
        int lno = -1;
        int cno = -1;
        int limit = 8;
        for (int i = 0; i < limit; i++) {
          line = rd.readLine();
          if (line == null) break;
          if (line.startsWith("l.")) {
            line = line.substring(2);
            int j = 0;
            while (Character.isDigit(line.charAt(j))) {
              j++;
            }
            lno = Integer.parseInt(line.substring(0, j));
            cno = line.length() - j - 1;
            lines = lines.append(Format.literal(line.substring(j)));
            break;
          } else {
            if (lno >= 0) lines = lines.append(Format.literal(line));
          }
        }
        if (errors > MAXERRORS) {
          // do nothing
        } else if (errors == MAXERRORS) {
          client.sendDiag(
            new Diag(
                  LatexAdaptor.thisLocator,
                  Diag.Category.Error,
                  Format.literal("more then " + errors + " errors")
                )
          );
        } else if (file_stack.isEmpty() || lno < 0) {
          client.sendDiag(
            new Diag(
                  LatexAdaptor.thisLocator,
                  Diag.Category.Error,
                  Format.beneath(
                    Format.literal("LaTeX top-level error:"),
                    Format.indent(Format.beneath(lines))
                  )
                )
          );
        } else {
          client.sendDiag(
            new Diag(
                  new TextLocator(new Name(file_stack.head().getPath()),
                                  (short) lno, 1, // FIXME: cast errors
                                  (short) lno, (short) cno),
                  Diag.Category.Error, // FIXME: always error??
                  Format.beneath(lines))
          );
        }
      } else {
        // normal input line: scan for file opens
        for (int i = 0; i < line.length(); i++) {
          char c = line.charAt(i);
          if (c == '(') {
            String file_name = "";
            while (i + 1 < line.length()
                   && line.charAt(i + 1) != '['
                   && line.charAt(i + 1) != ')') {
              file_name += line.charAt(++i);
            }
            file_name = file_name.trim();
            if (file_name.endsWith(".tex"))
              file_name = file_name.substring(0, file_name.length() - 4);
            actFile = client.locateFile(new File(file_name));
            if (actFile == null) actFile = new File(file_name);
            file_stack = file_stack.prepend(actFile);
          } else if (c == ')') {
            if (file_stack.isEmpty())
              // this seems to happen at emergency stop, ignore it
              ;
            else
              file_stack = file_stack.tail();
          }
        }
      }
    }
    return errors == 0;
  }

  private Set<Name> getIncludes(Seq<Unit> units)
  throws Failures.CannotCompute, Failures.ComputationFailed {
    Set<Name> names = new HashSet<Name>();
    for (Enum<Unit> en = units.enumerate(); en.more(); en.advance()) {
      Unit unit = en.current();
      Text text = (Text) unit.query(TextContentTypes.text);
      names = names.includeAll(getIncludes1(new BufferSeq<Name>(), text));
    }
    return names;
  }

  private Seq<Name> getIncludes(Unit unit)
  throws Failures.CannotCompute, Failures.ComputationFailed {
    Text text = (Text) unit.query(TextContentTypes.text);
    return getIncludes1(new BufferSeq<Name>(), text);
  }

  private Seq<Name> getIncludes0(Seq<Name> includes, Text[] text) {
    return new ArraySeq<Text>(text).reduce(includes, this::getIncludes1);
  }

  private Seq<Name> getIncludes1(Seq<Name> includes, Text text) {
    switch (text) {
      case Text.DocMarkup t:
        return getIncludes0(includes, t.subtexts);
      case Text.ModelInclusion t:
        return includes.append(t.name);
      case Text.ZedSection t:
        return getIncludes0(includes, t.subtexts);
      case Text.ZedClass t:
        return getIncludes0(includes, t.subtexts);
      case Text.ZedPragma t:
        return getIncludes0(includes, t.subtexts);
      case Text.ZedMarkup t:
        includes = getIncludes0(includes, t.header);
        includes = getIncludes0(includes, t.decls);
        return getIncludes0(includes, t.props);
      default:
        return includes;
    }
  }

  private String defCommand(String com, String def) {
    return "\\expandafter\\def\\csname " + com + "\\endcsname{" + def + "}";
  }

  private Seq<Target> writeZetaInputs(Seq<Name> includes)
  throws IOException, Failures.ComputationFailed, Failures.CannotCompute {
    PrintWriter w = new PrintWriter(new FileWriter("zeta-in.tex"));
    Seq<Target> deps = new BufferSeq<Target>();
    for (Enum<Name> e = includes.enumerate(); e.more(); e.advance()) {
      Unit unit = SessionUtil.lookupUnit(session,
                                         LatexAdaptor.thisLocator,
                                         e.current());
      if (unit == null) {
        // diagnostics have been produced
        continue;
      }

      LaTeXFragment tfrag = (LaTeXFragment)
                            unit.query(TextContentTypes.latexFragment);
      deps = deps.append(new Target(unit, TextContentTypes.latexFragment));
      w.println(tfrag.header);
      w.println(defCommand(e.current().getRepr(), tfrag.content));
    }
    w.close();
    return deps;
  }


}
