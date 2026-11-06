#!/usr/bin/env python3
"""Synchronous driver for ZETA's batch commander.

ZETA's BatchCommander reads commands from stdin and evaluates each on its own
thread (ShellState.doEval), so piping a script into it races: stdin hits EOF,
BatchCommander calls session.abort(), and pending work is killed.  Completion of
a command is signalled by a FinishedMessage, which MessagePrinter renders with
the "- " prefix followed by the command's words.  This driver sends one command
at a time and waits for that marker, which makes transcripts deterministic and
therefore usable as regression/differential baselines.

Message prefixes (MessagePrinter.receive):
  "@ " debug   "! " diagnostic   "= " result   "+ " progress   "- " finished

Usage: zdrive.py [--classes JAR_OR_DIR] [--cwd DIR] [--timeout SECS] cmd [cmd ...]
Prints the transcript on stdout; exits non-zero if a command times out.
"""
import argparse
import os
import platform
import queue
import subprocess
import sys
import threading

ZETABASE = "/home/wrw/zeta/run/zetabase"
# Override with ZETA_JAVA to test the system on a different JDK.
JAVA = os.environ.get("ZETA_JAVA", os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "toolchain/jdk21/bin/java"))
# The Java 21 port.  The 2002 classes.jar and the JDK 8 needed to run it are archived
# (archive/zeta-port-historic-2026-11-07.tar.gz).
DEFAULT_CLASSES = "/home/wrw/zeta/classes"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--classes", default=DEFAULT_CLASSES)
    ap.add_argument("--cwd", default=".")
    ap.add_argument("--zetabase", default=ZETABASE)
    ap.add_argument("--timeout", type=float, default=300.0,
                    help="limit on waiting for the next prompt or output token")
    ap.add_argument("--prompt", default="? ",
                    help="the shell prompt, i.e. zeta.sh.prompt (default '? ')")
    ap.add_argument("--debug", action="store_true", help="enable zeta.debug")
    ap.add_argument("commands", nargs="+")
    args = ap.parse_args()

    prompt = args.prompt
    texinputs = ".:%s/lib/latex:%s/lib/ztoolkits:" % (args.zetabase, args.zetabase)
    cmd = [JAVA, "-Xmx512m",
           "-Dzeta.home=" + args.zetabase,
           "-Dzeta.latex.path=" + texinputs,
           # Linux-<machine>: Linux-aarch64 on this machine (holds the rebuilt
           # libZAM.so); the 2002 distribution shipped only Linux-i386.
           "-Djava.library.path=" + args.zetabase + "/lib/native/Linux-"
               + platform.machine(),
           "-cp", args.classes,
           "zeta.tools.sh.Driver", "-b"]
    if args.debug:
        cmd.insert(1, "-Dzeta.debug=yes")

    env = dict(os.environ, TEXINPUTS=texinputs)
    p = subprocess.Popen(cmd, cwd=args.cwd, env=env,
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT, text=True, bufsize=1)

    # Read characters, not lines, so the trailing prompt can be seen.
    #
    # The prompter writes "? " WITHOUT a newline (BatchCommander.commandLoop), so a
    # line-oriented reader holds it in a buffer forever and can never report that ZETA is
    # ready.  Here the reader emits two kinds of token: a completed output line, and PROMPT
    # whenever the pending buffer is exactly the prompt.  That also parses `? = good bye.`
    # correctly -- prompt, then the line -- instead of needing the output stripped afterwards.
    PROMPT = object()
    tokens = queue.Queue()

    def reader():
        buf = ""
        while True:
            chunk = p.stdout.read(1)
            if not chunk:
                break
            buf += chunk
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                tokens.put(line.rstrip("\r"))
            if buf == prompt:
                tokens.put(PROMPT)
                buf = ""
        if buf:
            tokens.put(buf)
        tokens.put(None)

    threading.Thread(target=reader, daemon=True).start()

    transcript, failed, saw_eof = [], False, False

    def drain(label):
        """Collect output up to the next prompt, which means ZETA is ready for more.

        This is an exact signal rather than a heuristic.  `ShellState.doEval` runs each command
        on its own thread and, in a `finally`, does `synchronized (state) state.notifyAll()`
        once the last evaluation ends; the prompter thread is blocked in `state.wait()` and
        answers by printing another prompt.  So a prompt arrives after EVERY completed command
        -- including one that failed with `Failures.FormFailed` and therefore sent no
        FinishedMessage, which is the normal outcome for a specification with a type error.

        The previous version ended a command after `--quiet` seconds of silence.  That was
        load-sensitive: a GC pause or a busy machine could exceed the window mid-command and
        truncate the transcript, which showed up as a single mutation test differing in one run
        out of several and reproducing 6/6 in isolation.  Nothing about the prompt depends on
        timing.
        """
        nonlocal failed, saw_eof
        while True:
            try:
                tok = tokens.get(timeout=args.timeout)
            except queue.Empty:
                transcript.append("### TIMEOUT waiting for: %s" % label)
                failed = True
                return False
            if tok is None:
                saw_eof = True
                return False
            if tok is PROMPT:
                return True
            if tok:
                transcript.append(tok)
            # A form-level failure (bad command syntax, unresolved unit) is reported as a Diag
            # against the "<command input>" locator; the prompt still follows, but recording
            # the failure here keeps the exit status meaningful.
            if tok.startswith("! ") and "[<command input>]" in tok:
                failed = True

    # the banner and the plugin lines arrive before the first prompt
    drain("startup")

    for c in args.commands:
        transcript.append(">>> " + c)
        try:
            p.stdin.write(c + "\n")
            p.stdin.flush()
        except (BrokenPipeError, ValueError):
            # ZETA died early (e.g. a broken classpath): report it rather than tracebacking
            transcript.append("### ZETA EXITED before accepting: %s" % c)
            failed = True
            break
        if not drain(c):
            break
    # Shut down through `quit` ONLY, and do not close stdin until the JVM is gone.
    #
    # Two things reach SessionImpl.abort(), and it prints "good bye." before System.exit:
    # the `quit` command (ShellForms.quitForm -> session.abort(); the *saving* variant is
    # `squit`, which calls exit()), and end-of-input, which makes BatchCommander.exec1's
    # read() return null and call session.abort() too.  Writing `quit` and closing stdin
    # together therefore ran abort() twice, on two threads, and
    # MessagePrinter.printPrefixed emits the "= " prefix and the text in separate writer
    # calls with no lock -- so the two messages interleaved, rendering as either
    #
    #     ? = good bye.        or        = = good bye.
    #     = good bye.                    good bye.
    #
    # about one run in four.  That is a real (if cosmetic) defect in the 2000 shell, logged in
    # zesurrect-plan.md for when zeta.tools.sh is ported; the driver's job is not to depend on
    # it.  Triggering a single shutdown path makes the transcript tail deterministic.
    try:
        p.stdin.write("quit\n")
        p.stdin.flush()
    except (BrokenPipeError, ValueError):
        pass
    try:
        p.wait(timeout=30)
    except subprocess.TimeoutExpired:
        p.kill()
    try:
        p.stdin.close()
    except (BrokenPipeError, ValueError):
        pass
    # Collect the shutdown output ("= good bye.").  `tokens` is fed by the reader thread, so an
    # empty-looking queue means only that the thread has not been scheduled yet; draining on
    # `empty()` put those lines in the transcript about one run in four, which is exactly the
    # kind of tail difference that makes a transcript useless as a baseline.  The reader pushes
    # None at EOF, and EOF is reached because the JVM exited, so waiting for that sentinel
    # terminates and is deterministic.
    while not saw_eof:
        try:
            tok = tokens.get(timeout=30)
        except queue.Empty:
            transcript.append("### TIMEOUT waiting for ZETA to close its output")
            failed = True
            break
        if tok is None:
            saw_eof = True
            break
        if tok is not PROMPT and tok:
            transcript.append(tok)

    print("\n".join(transcript))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
