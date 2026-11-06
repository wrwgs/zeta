package zeta.tools.nzap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import zeta.format.Format;

/** A program for dumping ZAM files.
  *
  * @version $Id: DumpZAM.pizza,v 1.1 2000/01/02 17:37:10 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p>A developer tool, not reachable from a session.  Ported unchanged; see the
  * PORT CHANGE in {@link ZAM.Unit#read} for the one reader defect that made the 2002
  * DumpZAM crash on real units.</p>
  */
public class DumpZAM {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("expected one argument");
      System.exit(1);
    }
    try {
      InputStream ins =
        new BufferedInputStream(
              new FileInputStream(
                    new File(args[0])
                  )
            );
      ZAM.Unit unit = ZAM.Unit.read(ins);
      System.out.println(Format.build(unit).toString());
    }
    catch (IOException e) {
      System.err.println(e.toString());
      System.exit(2);
    }
  }
}
