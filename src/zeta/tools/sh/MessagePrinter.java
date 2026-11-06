package zeta.tools.sh;

import java.io.PrintWriter;
import java.util.StringTokenizer;

import zeta.format.Format;
import zeta.session.DebugMessage;
import zeta.session.Diag;
import zeta.session.Listener;
import zeta.session.Message;
import zeta.session.ProgressMessage;
import zeta.session.ResultMessage;
import zeta.session.SessionStateMessage;
import zeta.util.Name;

/**
 * A listener which echos messages to print writers.
 *
 * @version $Id: MessagePrinter.pizza,v 1.3 2000/05/08 07:07:43 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>This class renders every line of a batch session transcript other than the prompt, so its
 * output is compared byte for byte by the differential harness: the prefixes ({@code "@ "}
 * debug, {@code "! "} diagnostic, {@code "= "} result, {@code "+ "} progress, {@code "- "}
 * everything else, notably {@link FinishedMessage}), the wrap width ({@code 79} minus the
 * prefix length) and the line splitting are all unchanged.
 *
 * <p>PORT CHANGE (concurrency defect, zesurrect-plan.md 6.1): {@link #printPrefixed} used to
 * emit each line as a {@code print(prefix)} followed by a {@code println(line)}, two
 * unsynchronised writer calls.  The shell evaluates each command on its own thread, and two
 * threads reach this listener concurrently -- {@code quit} together with end of input both
 * run {@code session.abort()}, which says {@code good bye.} -- so two messages interleaved
 * mid-line ({@code "= = good bye."}).  The port formats the whole prefixed message into one
 * string first and emits it with a single {@code write} followed by {@code flush}, both under
 * the writer's monitor.  For any sequence of messages delivered by one thread the bytes
 * written are identical to before (same prefix, same lines, same {@code println} line
 * separator, same flush at the end); only interleavings between threads change, and every
 * one of those was a corrupt transcript.  The prompter thread of {@link BatchCommander} still
 * writes {@code "? "} with its own calls; those are atomic per call, so a prompt can still
 * precede a message line ({@code "? = good bye."}) but can no longer split one.
 *
 * <p>PORT NOTE: the {@code unprintedEmpty} logic of {@link #printPrefixed} is dead -- a
 * {@code StringTokenizer} without {@code returnDelims} never yields an empty token, so empty
 * lines of a message are dropped, not printed as a bare prefix.  Kept as written, since that
 * is what the 2002 transcripts show.  The {@code EvalMessage} arm of the non-debug branch is
 * equally dead (EvalMessage is caught by the debug branch first) and is kept too.
 */
public class MessagePrinter implements Listener {

  /**
   * The print writer used for debug messages (or null if those messages
   * should not be shown).
   */
  private PrintWriter debugWriter;

  /**
   * The print writer used for other messages (or null if those messages
   * should not be shown).
   */
  private PrintWriter messageWriter;


  /**
   * Construct a print listener.
   */
  MessagePrinter(PrintWriter debugWriter, PrintWriter messageWriter) {
    this.debugWriter = debugWriter;
    this.messageWriter = messageWriter;
  }

  /** Receive method of a message listener. */
  public void receive(Name sender, Message message) {
    if (message instanceof DebugMessage ||
        message instanceof SessionStateMessage ||
        message instanceof EvalMessage) {
      if (debugWriter != null) {
        printPrefixed("@ " + sender + ": ", debugWriter,
                      Format.build(message));
      }
    } else {
      if (messageWriter != null) {
        if (message instanceof Diag) {
          printPrefixed("! ", messageWriter, Format.build(message));
        }
        else if (message instanceof ResultMessage) {
          printPrefixed("= ", messageWriter, Format.build(message));
        }
        else if (message instanceof ProgressMessage) {
          printPrefixed("+ ", messageWriter, Format.build(message));
        }
        else if (message instanceof EvalMessage) {
          // printPrefixed("~ ", messageWriter, Format.build(message));
        }
        else {
          printPrefixed("- ", messageWriter, Format.build(message));
        }
      }
    }
  }

  private static void printPrefixed(String prefix, PrintWriter writer,
                                    Format format) {
    String string = format.toString(79 - prefix.length());
    // PORT CHANGE: collect the whole message, then write it once (see class comment).
    // System.lineSeparator() is exactly what PrintWriter.println appended.
    String newline = System.lineSeparator();
    StringBuilder out = new StringBuilder();
    StringTokenizer tkn = new StringTokenizer(string, "\n\r");
    int unprintedEmpty = 0;
    while (tkn.hasMoreTokens()) {
      String line = tkn.nextToken();
      if (line.length() == 0)
        unprintedEmpty++;
      else {
        while (unprintedEmpty > 0) {
          out.append(prefix).append(newline);
          unprintedEmpty--;
        }
        out.append(prefix).append(line).append(newline);
      }
    }
    // ignore trailing empty lines
    synchronized (writer) {
      writer.write(out.toString());
      writer.flush();
    }
  }

}
