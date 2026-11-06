package zeta.tools.nzap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import zeta.agl.BufferSeq;
import zeta.agl.Seq;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.util.Term;

/** Class for representing ZAM code.
  *
  * @version $Id: ZAM.pizza,v 1.9 2000/07/03 06:59:28 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p><b>The unit file format is a contract with native code.</b>
  * {@link Unit#write} produces the byte stream that libZAM's loader
  * (<code>port/zam/loader.cpp</code>) reads byte by byte: integers as big-endian 7-bit
  * groups whose <em>last</em> byte carries bit 7, strings as a length followed by the
  * bytes, booleans as one byte.  Every <code>write</code> method below emits exactly the
  * sequence of the 2002 class, in the same field order; the port was checked
  * byte-for-byte against the 2002 classes on every zapnotes unit.</p>
  *
  * <p>PORT NOTE: <code>IOUtils.writeString</code> encodes with
  * <code>String.getBytes()</code>, i.e. the JVM's default charset, as the reference
  * does.  That default is a property of the JVM, not of this class: JDK 18+ makes it
  * UTF-8, the jdk8 reference build takes it from the locale.  The two agree on ASCII,
  * which is all the zapnotes corpus produces; the call is kept so that the port writes
  * what the 2002 class writes on the same JVM.</p>
  *
  * <p>PORT CHANGE: the varint writers <code>ZAM.writeInt</code> and
  * <code>IOUtils.writeInt</code> used a <code>private static</code> 12-byte scratch
  * buffer shared by all callers, which two threads compiling at once (a
  * <code>tryCompute</code> and an <code>e</code> command's scratch unit) could corrupt.
  * The buffer is now local.  The bytes produced are identical (same algorithm, same
  * buffer size); only the sharing is gone.  Negative arguments still overrun it with
  * an <code>ArrayIndexOutOfBoundsException</code> (the arithmetic shift never reaches 0),
  * as in the reference; no caller passes one.</p>
  *
  * <p><code>Binder</code> is sealed (abstract base, final cases): nothing outside this
  * package names any <code>zeta.tools.nzap</code> class (checked against all reference
  * classes), so the case set is closed.  Its tags (<code>BVar</code> 1,
  * <code>BTerm</code> 2) are from <code>docs/adt-tags.txt</code>.  The other records are
  * single-case classes whose case name equals the class name (fields, constructor,
  * same-named factory, no tag) -- except <code>NativeHom</code>, whose case is
  * <em>named</em> <code>NativePred</code> in the source, so its factory is
  * <code>NativeHom.NativePred(String)</code>, as the jar has it.</p>
  *
  * <p><code>CodeFormatter</code>'s field order is the jar's: the explicitly declared
  * <code>instrs</code> before the case fields <code>code, pc, end</code>.
  * <code>Unit.fmtGroup</code> took a <code>pizza.support.array</code>; it is private, and
  * is now generic over <code>A[]</code>.</p>
  */
public class ZAM {

  /** Opcodes. */
  static final byte LOAD = 1;
  static final byte LOADPARAM = 2;
  static final byte WAIT = 3;
  static final byte WAITLOAD = 4;
  static final byte STORE = 5;
  static final byte UNIFY = 6;
  static final byte MEMBER = 7;
  static final byte CALLNATIVE = 9;
  static final byte TESTNATIVE = 10;
  static final byte MKTERM = 11;
  static final byte MKVAR = 12;
  static final byte MKEMPTY = 13;
  static final byte MKINTEN = 14;
  static final byte MKSINGLE = 15;
  static final byte UNION = 16;
  static final byte ISECT = 17;
  static final byte GOTO = 18;
  static final byte IF = 19;
  static final byte IFNOT = 20;
  static final byte HOM = 21;
  static final byte FAILURE = 22;
  static final byte SUCCESS = 23;
  static final byte DEPTHFIRST = 24;
  static final byte UNDEF = 25;
  static final byte MKOUTERINTEN = 26;
  static final byte MKUNIV = 27;
  static final byte MKNUMBER = 28;
  static final byte LOADGLOBAL = 29;
  static final byte NOP = 31;
  static final byte MKDENO = 32;
  static final byte SUBSET = 33;
  static final byte WAITLOADGLOBAL = 34;
  static final byte WAITGLOBAL = 35;
  static final byte STOREGLOBAL = 36;
  static final byte APPLY = 37;
  static final byte APPLYGLOBAL = 38;
  static final byte UNIQMEMBER = 39;
  static final byte MOVE = 40;
  static final byte WAITLOADPARAM = 41;

  /** Writing integer parameters into the code stream. */
  static int writeInt(byte[] buffer, int start, int param) {
    // PORT CHANGE: local scratch buffer (was a shared static); see class notes.
    byte[] wrbuf = new byte[12];
    int i = 12;
    do {
      wrbuf[--i] = (byte) (param & 127);
      param >>= 7;
    }
    while (param != 0);
    wrbuf[11] |= (byte) 128;
    int count = 12 - i;
    System.arraycopy(wrbuf, i, buffer, start, count);
    return start + count;
  }

  /** Format byte code. */
  public static Format formatCode(byte[] code, int start, int end) {
    CodeFormatter f = new CodeFormatter(code, start, end);
    while (f.pc < f.end) f.formatNext();
    return Format.beneath(f.instrs);
  }

  /** A class for formatting byte code. */
  static class CodeFormatter {
    Seq<Format> instrs = new BufferSeq<Format>();
    byte[] code;
    int pc;
    int end;

    CodeFormatter(byte[] code, int pc, int end) {
      this.code = code;
      this.pc = pc;
      this.end = end;
    }

    /** Pizza's generated case factory. */
    static CodeFormatter CodeFormatter(byte[] code, int pc, int end) {
      return new CodeFormatter(code, pc, end);
    }

    void formatNext() {
      int oldPc = pc;
      Format instr;
      switch (code[pc++]) {
        case MKDENO -> {
          int di = readIndex();
          int ri = readIndex();
          instr = Format.literal("MKDENO d" + di + " -> r" + ri);
        }
        case CALLNATIVE -> {
          int ni = readIndex();
          int pi = readIndex();
          int ri = readIndex();
          instr = Format.literal("CALLNATIVE n" + ni + " r" + pi + " -> r" + ri);
        }
        case TESTNATIVE -> {
          int ni = readIndex();
          int pi = readIndex();
          instr = Format.literal("TESTNATIVE p" + ni + " r" + pi);
        }
        case LOADGLOBAL -> {
          int ci = readIndex();
          int ri = readIndex();
          instr = Format.literal("LOADGLOBAL g" + ci + " -> r" + ri);
        }
        case WAITLOADGLOBAL -> {
          int ci = readIndex();
          int ri = readIndex();
          instr = Format.literal("WAITLOADGLOBAL g" + ci + " -> r" + ri);
        }
        case LOADPARAM -> {
          int vi = readIndex();
          int ri = readIndex();
          instr = Format.literal("LOADPARAM p" + vi + " -> r" + ri);
        }
        case WAITLOADPARAM -> {
          int pi = readIndex();
          int ri = readIndex();
          instr = Format.literal("WAITLOADPARAM p" + pi + " -> r" + ri);
        }
        case WAITGLOBAL -> {
          int vi = readIndex();
          instr = Format.literal("WAITGLOBAL g" + vi);
        }
        case WAIT -> {
          int vi = readIndex();
          instr = Format.literal("WAIT v" + vi);
        }
        case MOVE -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("MOVE r" + ri1 + " -> r" + ri2);
        }
        case LOAD -> {
          int vi = readIndex();
          int ri = readIndex();
          instr = Format.literal("LOAD v" + vi + " -> r" + ri);
        }
        case WAITLOAD -> {
          int vi = readIndex();
          int ri = readIndex();
          instr = Format.literal("WAITLOAD v" + vi + " -> r" + ri);
        }
        case STORE -> {
          int ri = readIndex();
          int vi = readIndex();
          instr = Format.literal("STORE r" + ri + " -> v" + vi);
        }
        case STOREGLOBAL -> {
          int ri = readIndex();
          int gi = readIndex();
          instr = Format.literal("STOREGLOBAL r" + ri + " -> g" + gi);
        }
        case UNIFY -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("UNIFY r" + ri1 + ", r" + ri2);
        }
        case MEMBER -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("MEMBER r" + ri1 + ", r" + ri2);
        }
        case UNIQMEMBER -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("UNIQMEMBER r" + ri1 + ", r" + ri2);
        }
        case SUBSET -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("SUBSET r" + ri1 + ", r" + ri2);
        }
        case MKTERM -> {
          int ci = readIndex();
          int pi = readIndex();
          int ri = readIndex();
          instr = Format.literal("MKTERM c" + ci + ", r" + pi + " -> r" + ri);
        }
        case MKSINGLE -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("MKSINGLE r" + ri1 + " -> r" + ri2);
        }
        case HOM -> {
          int hi = readIndex();
          int ri1 = readIndex();
          int ri2 = readIndex();
          instr = Format.literal("HOM h" + hi + " r" + ri1 + " -> r" + ri2);
        }
        case MKEMPTY -> {
          int ri = readIndex();
          instr = Format.literal("MKEMPTY -> r" + ri);
        }
        case MKUNIV -> {
          int ri = readIndex();
          instr = Format.literal("MKUNIV -> r" + ri);
        }
        case ISECT -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          int ri3 = readIndex();
          instr = Format.literal("ISECT r" + ri1 + ", r " + ri2 +
                                 " -> r" + ri3);
        }
        case UNION -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          int ri3 = readIndex();
          instr = Format.literal("UNION r" + ri1 + ", r " + ri2 +
                                 " -> r" + ri3);
        }
        case APPLY -> {
          int ri1 = readIndex();
          int ri2 = readIndex();
          int ri3 = readIndex();
          instr = Format.literal("APPLY r" + ri1 + ", r" + ri2 +
                                 " -> r" + ri3);
        }
        case APPLYGLOBAL -> {
          int gi1 = readIndex();
          int ri2 = readIndex();
          int ri3 = readIndex();
          instr = Format.literal("APPLYGLOBAL g" + gi1 + ", r" + ri2 +
                                 " -> r" + ri3);
        }
        case IF -> {
          int ri = readIndex();
          int ti = readIndex();
          instr = Format.literal("IF r" + ri + ", " + (pc + ti));
        }
        case IFNOT -> {
          int ri = readIndex();
          int ti = readIndex();
          instr = Format.literal("IFNOT r" + ri + ", " + (pc + ti));
        }
        case GOTO -> {
          int ti = readIndex();
          instr = Format.literal("GOTO " + (pc + ti));
        }
        case MKINTEN -> {
          int ii = readIndex();
          int pi = readIndex();
          int ri = readIndex();
          instr = Format.literal("MKINTEN s" + ii +
                                 ", r" + pi + " -> r" + ri);
        }
        case MKOUTERINTEN -> {
          int ii = readIndex();
          int ri = readIndex();
          instr = Format.literal("MKOUTERINTEN s" + ii + " -> r" + ri);
        }
        case FAILURE -> instr = Format.literal("FAILURE");
        case SUCCESS -> instr = Format.literal("SUCCESS");
        case DEPTHFIRST -> instr = Format.literal("DEPTHFIRST");
        default -> instr = Format.literal("??? " + code[oldPc]);
      }
      instrs = instrs.append(
                 Format.append(Format.literal(oldPc + ":  "),
                               instr)
               );
    }

    int readIndex() {
      int s = 0;
      for (;;) {
        byte c = code[pc++];
        if ((c & 128) != 0)
          return (s << 7) | (c & 127);
        else
          s = (s << 7) | c;
      }
    }

  }




  /** Class for representing global variables. */
  public static class Global extends Term {
    public String name;
    public boolean isExtern;

    public Global(String name, boolean isExtern) {
      this.name = name;
      this.isExtern = isExtern;
    }

    /** Pizza's generated case factory. */
    public static Global Global(String name, boolean isExtern) {
      return new Global(name, isExtern);
    }

    public static Global read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      boolean isExtern = IOUtils.readBool(in);
      return new Global(name, isExtern);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
      IOUtils.writeBool(out, isExtern);
    }

  }

  /** Class for representing constructors. */
  public static class Constructor extends Term {
    public String name;
    public int arity;

    public Constructor(String name, int arity) {
      this.name = name;
      this.arity = arity;
    }

    /** Pizza's generated case factory. */
    public static Constructor Constructor(String name, int arity) {
      return new Constructor(name, arity);
    }

    public static Constructor read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      int arity = IOUtils.readInt(in);
      return new Constructor(name, arity);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
      IOUtils.writeInt(out, arity);
    }

  }

  /** Class for representing native functions. */
  public static class NativeFunc extends Term {
    public String name;
    public int arity;

    public NativeFunc(String name, int arity) {
      this.name = name;
      this.arity = arity;
    }

    /** Pizza's generated case factory. */
    public static NativeFunc NativeFunc(String name, int arity) {
      return new NativeFunc(name, arity);
    }

    public static NativeFunc read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      int arity = IOUtils.readInt(in);
      return new NativeFunc(name, arity);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
      IOUtils.writeInt(out, arity);
    }
  }

  /** Class for representing native predicates. */
  public static class NativePred extends Term {
    public String name;
    public int arity;

    public NativePred(String name, int arity) {
      this.name = name;
      this.arity = arity;
    }

    /** Pizza's generated case factory. */
    public static NativePred NativePred(String name, int arity) {
      return new NativePred(name, arity);
    }

    public static NativePred read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      int arity = IOUtils.readInt(in);
      return new NativePred(name, arity);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
      IOUtils.writeInt(out, arity);
    }
  }

  /** Class for representing native homomorphism. */
  public static class NativeHom extends Term {
    public String name;

    public NativeHom(String name) {
      this.name = name;
    }

    /** Pizza's generated case factory.  The source declares the case as
        <code>NativePred(String name)</code> inside <code>NativeHom</code> (a 1998
        copy-and-paste slip), so that is the factory's name in the jar. */
    public static NativeHom NativePred(String name) {
      return new NativeHom(name);
    }

    public static NativeHom read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      return new NativeHom(name);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
    }
  }


  /** Class for representing schemas. */
  public static class Schema extends Term {
    public String name;
    public String[] paramTab;
    public String[] varTab;
    public Binder binder;
    public Constraint[] constrs;

    public Schema(
             String name,
             String[] paramTab,
             String[] varTab,
             Binder binder,
             Constraint[] constrs
           ) {
      this.name = name;
      this.paramTab = paramTab;
      this.varTab = varTab;
      this.binder = binder;
      this.constrs = constrs;
    }

    /** Pizza's generated case factory. */
    public static Schema Schema(String name, String[] paramTab, String[] varTab,
                                Binder binder, Constraint[] constrs) {
      return new Schema(name, paramTab, varTab, binder, constrs);
    }

    public static Schema read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      int n = IOUtils.readInt(in);
      String[] paramTab = new String[n];
      for (int i = 0; i < n; i++) {
        paramTab[i] = IOUtils.readString(in);
      }
      n = IOUtils.readInt(in);
      String[] varTab = new String[n];
      for (int i = 0; i < n; i++) {
        varTab[i] = IOUtils.readString(in);
      }
      Binder binder = Binder.read(in);
      n = IOUtils.readInt(in);
      Constraint[] constrs = new Constraint[n];
      for (int i = 0; i < n; i++) {
        constrs[i] = Constraint.read(in);
      }
      return new Schema(name, paramTab, varTab, binder, constrs);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
      IOUtils.writeInt(out, paramTab.length);
      for (int i = 0; i < paramTab.length; i++) {
        IOUtils.writeString(out, paramTab[i]);
      }
      IOUtils.writeInt(out, varTab.length);
      for (int i = 0; i < varTab.length; i++) {
        IOUtils.writeString(out, varTab[i]);
      }
      binder.write(out);
      IOUtils.writeInt(out, constrs.length);
      for (int i = 0; i < constrs.length; i++) {
        constrs[i].write(out);
      }
    }
  }

  /** Class for representing binders. */
  public abstract static sealed class Binder extends Term
      permits Binder.BVar, Binder.BTerm {

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$tools$nzap$ZAM$Binder$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_BVAR = 1;
    private static final int TAG_BTERM = 2;

    Binder(int tag) {
      this.zeta$tools$nzap$ZAM$Binder$$tag = tag;
    }

    public static final class BVar extends Binder {
      public int index;

      public BVar(int index) {
        super(TAG_BVAR);
        this.index = index;
      }
    }

    public static final class BTerm extends Binder {
      public int index;
      public Binder[] args;

      public BTerm(int index, Binder[] args) {
        super(TAG_BTERM);
        this.index = index;
        this.args = args;
      }
    }

    /** Pizza's generated case factory. */
    public static Binder BVar(int index) {
      return new BVar(index);
    }

    /** Pizza's generated case factory. */
    public static Binder BTerm(int index, Binder[] args) {
      return new BTerm(index, args);
    }

    public static Binder read(InputStream in) throws IOException {
      boolean isVar = IOUtils.readBool(in);
      if (isVar) {
        int index = IOUtils.readInt(in);
        return new BVar(index);
      } else {
        int index = IOUtils.readInt(in);
        int count = IOUtils.readInt(in);
        Binder[] args = new Binder[count];
        for (int i = 0; i < count; i++) {
          args[i] = read(in);
        }
        return new BTerm(index, args);
      }
    }

    public void write(OutputStream out) throws IOException {
      switch (this) {
        case BVar v -> {
          IOUtils.writeBool(out, true);
          IOUtils.writeInt(out, v.index);
        }
        case BTerm t -> {
          IOUtils.writeBool(out, false);
          IOUtils.writeInt(out, t.index);
          IOUtils.writeInt(out, t.args.length);
          for (int i = 0; i < t.args.length; i++) {
            t.args[i].write(out);
          }
        }
      }
    }
  }

  /** Class for representing constraints. */
  public static class Constraint extends Term {
    public String name;
    public int regCount;
    public int codeOffs;

    public Constraint(
             String name,
             int regCount,
             int codeOffs
           ) {
      this.name = name;
      this.regCount = regCount;
      this.codeOffs = codeOffs;
    }

    /** Pizza's generated case factory. */
    public static Constraint Constraint(String name, int regCount, int codeOffs) {
      return new Constraint(name, regCount, codeOffs);
    }

    public static Constraint read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);
      int regCount = IOUtils.readInt(in);
      int codeOffs = IOUtils.readInt(in);
      return new Constraint(name, regCount, codeOffs);
    }

    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);
      IOUtils.writeInt(out, regCount);
      IOUtils.writeInt(out, codeOffs);
    }

  }


  /** Class for representing a ZAM Unit. */
  public static class Unit extends Term {
    public String name;
    public String[] imports;
    public Global[] globals;
    public Constructor[] constructors;
    public NativeFunc[] nativeFuncs;
    public NativePred[] nativePreds;
    public NativeHom[] nativeHoms;
    public String[] denotations;
    public int initInten;
    public Schema[] schemas;
    public byte[] code;

    public Unit(
             String name,
             String[] imports,
             Global[] globals,
             Constructor[] constructors,
             NativeFunc[] nativeFuncs,
             NativePred[] nativePreds,
             NativeHom[] nativeHoms,
             String[] denotations,
             int initInten,
             Schema[] schemas,
             byte[] code
           ) {
      this.name = name;
      this.imports = imports;
      this.globals = globals;
      this.constructors = constructors;
      this.nativeFuncs = nativeFuncs;
      this.nativePreds = nativePreds;
      this.nativeHoms = nativeHoms;
      this.denotations = denotations;
      this.initInten = initInten;
      this.schemas = schemas;
      this.code = code;
    }

    /** Pizza's generated case factory. */
    public static Unit Unit(String name, String[] imports, Global[] globals,
                            Constructor[] constructors, NativeFunc[] nativeFuncs,
                            NativePred[] nativePreds, NativeHom[] nativeHoms,
                            String[] denotations, int initInten, Schema[] schemas,
                            byte[] code) {
      return new Unit(name, imports, globals, constructors, nativeFuncs,
                      nativePreds, nativeHoms, denotations, initInten, schemas,
                      code);
    }

    /** Format a unit. */
    public Format toFormat(FormatInfo info) {
      return
        Format.beneath(
          new Format[]{
            Format.literal("UNIT " + name),
            fmtGroup("IMPORTS ", "u", imports),
            fmtGroup("GLOBALS ", "g", globals),
            fmtGroup("CONSTRUCTORS ", "c", constructors),
            fmtGroup("NATIVEFUNCS ", "n", nativeFuncs),
            fmtGroup("NATIVEPREDS ", "p", nativePreds),
            fmtGroup("NATIVEHOMS ", "p", nativeHoms),
            fmtGroup("DENOTATIONS ", "d", denotations),
            Format.literal("INITSCHEMA s" + initInten),
            fmtGroup("SCHEMAS ", "s", schemas),
            Format.beneath(
              Format.literal("CODE"),
              Format.indent(formatCode(code, 0, code.length))
            )});
    }

    /** Format an array with indices. */
    private static <A> Format fmtGroup(String title, String prefix, A[] arr) {
      Seq<Format> fmts = new BufferSeq<Format>();
      for (int i = 0; i < arr.length; i++) {
        fmts = fmts.append(
                 Format.block(Format.literal(prefix + i + "=="),
                              Format.indent(Format.build(arr[i])))
               );
      }
      return Format.beneath(
               Format.literal(title),
               Format.indent(
                 Format.beneath(fmts)
               )
             );
    }


    /** Read a unit (signature only). */
    public static Unit read(InputStream in) throws IOException {
      String name = IOUtils.readString(in);

      int n = IOUtils.readInt(in);
      String[] imports = new String[n];
      for (int i = 0; i < n; i++) {
        imports[i] = IOUtils.readString(in);
      }

      n = IOUtils.readInt(in);
      Global[] globals = new Global[n];
      for (int i = 0; i < n; i++) {
        globals[i] = Global.read(in);
      }

      n = IOUtils.readInt(in);
      Constructor[] constructors = new Constructor[n];
      for (int i = 0; i < n; i++) {
        constructors[i] = Constructor.read(in);
      }

      n = IOUtils.readInt(in);
      NativeFunc[] nativeFuncs = new NativeFunc[n];
      for (int i = 0; i < n; i++) {
        nativeFuncs[i] = NativeFunc.read(in);
      }

      n = IOUtils.readInt(in);
      NativePred[] nativePreds = new NativePred[n];
      for (int i = 0; i < n; i++) {
        nativePreds[i] = NativePred.read(in);
      }

      n = IOUtils.readInt(in);
      NativeHom[] nativeHoms = new NativeHom[n];
      for (int i = 0; i < n; i++) {
        nativeHoms[i] = NativeHom.read(in);
      }

      n = IOUtils.readInt(in);
      String[] denotations = new String[n];
      for (int i = 0; i < n; i++) {
        denotations[i] = IOUtils.readString(in);
      }

      int codeSize = IOUtils.readInt(in);  // NB!

      int initInten = IOUtils.readInt(in);
      n = IOUtils.readInt(in);
      Schema[] schemas = new Schema[n];
      for (int i = 0; i < n; i++) {
        schemas[i] = Schema.read(in);
      }

      // PORT CHANGE: the 2002 loop was bounded by `n` -- by then the number of
      // schemas -- instead of `codeSize`.  It read only the first n code bytes and
      // left the rest zero, so formatting the code ran off the end of the array: the 2002
      // DumpZAM dies with ArrayIndexOutOfBoundsException in CodeFormatter.readIndex on
      // the zapnotes Nat unit (checked), and read() itself threw when a unit had more
      // schemas than code bytes.  Bounded by codeSize now.  Unobservable to any oracle,
      // and the old behaviour was a crash: Unit.read is called only from
      // DumpZAM.main, a developer tool no session path reaches (libZAM reads units
      // with its own loader, and nothing outside this package names this class).
      byte[] code = new byte[codeSize];
      int r = 0;
      while (r < codeSize) {
        int c = in.read(code, r, codeSize - r);
        if (c < 0) throw new IOException("unexpected input");
        r += c;
      }

      return new Unit(name, imports, globals, constructors,
                      nativeFuncs, nativePreds, nativeHoms, denotations,
                      initInten, schemas, code);
    }

    /** Write a unit. */
    public void write(OutputStream out) throws IOException {
      IOUtils.writeString(out, name);

      IOUtils.writeInt(out, imports.length);
      for (int i = 0; i < imports.length; i++) {
        IOUtils.writeString(out, imports[i]);
      }

      IOUtils.writeInt(out, globals.length);
      for (int i = 0; i < globals.length; i++) {
        globals[i].write(out);
      }

      IOUtils.writeInt(out, constructors.length);
      for (int i = 0; i < constructors.length; i++) {
        constructors[i].write(out);
      }

      IOUtils.writeInt(out, nativeFuncs.length);
      for (int i = 0; i < nativeFuncs.length; i++) {
        nativeFuncs[i].write(out);
      }

      IOUtils.writeInt(out, nativePreds.length);
      for (int i = 0; i < nativePreds.length; i++) {
        nativePreds[i].write(out);
      }

      IOUtils.writeInt(out, nativeHoms.length);
      for (int i = 0; i < nativeHoms.length; i++) {
        nativeHoms[i].write(out);
      }

      IOUtils.writeInt(out, denotations.length);
      for (int i = 0; i < denotations.length; i++) {
        IOUtils.writeString(out, denotations[i]);
      }

      IOUtils.writeInt(out, code.length); // NB!!


      IOUtils.writeInt(out, initInten);
      IOUtils.writeInt(out, schemas.length);
      for (int i = 0; i < schemas.length; i++) {
        schemas[i].write(out);
      }

      out.write(code);
    }

  }

  static class IOUtils {

    static void writeString(OutputStream out, String s) throws IOException {
      // PORT NOTE: the JVM default charset, as in the reference; see class notes.
      byte[] bytes = s.getBytes();
      writeInt(out, bytes.length);
      out.write(bytes);
    }

    static String readString(InputStream in) throws IOException {
      int n = readInt(in);
      byte[] bytes = new byte[n];
      in.read(bytes);
      return new String(bytes);
    }

    static void writeInt(OutputStream out, int param) throws IOException {
      // PORT CHANGE: local scratch buffer (was a shared static); see class notes.
      byte[] wrbuf = new byte[12];
      int i = 12;
      do {
        wrbuf[--i] = (byte) (param & 127);
        param >>= 7;
      }
      while (param != 0);
      wrbuf[11] |= (byte) 128;
      out.write(wrbuf, i, 12 - i);
    }

    static int readInt(InputStream in) throws IOException {
      int s = 0;
      for (;;) {
        int c = in.read();
        if ((c & 128) != 0)
          return (s << 7) | (c & 127);
        else
          s = (s << 7) | c;
      }
    }

    static void writeBool(OutputStream out, boolean b) throws IOException {
      if (b) out.write(1); else out.write(0);
    }

    static boolean readBool(InputStream in) throws IOException {
      int b = in.read();
      return b != 0;
    }

  }

}
