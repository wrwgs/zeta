package zeta.tools.nzap;

import java.util.NoSuchElementException;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashSet;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.nmz.MZ.Expr;
import zeta.content.nmz.MZ.Property;
import zeta.util.FatalError;
import zeta.util.Name;

/** Constraint coder.
  *
  * @version $Id: ConstrCoder.pizza,v 1.14 2000/07/03 06:59:28 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p>This class emits the ZAM byte code that ends up in the unit file, so every
  * <code>emit</code>, every register allocation and every call with a side effect on
  * the schema or unit tables (<code>lookupVar</code>, the <code>get...Index</code>
  * methods) happens in the 2002 order.  In particular the <code>Apply</code> case still
  * calls <code>lookupVar</code> on a variable head before deciding whether it is global,
  * and calls it again through <code>compileNonFlex</code> if it is not: the first call
  * can register a schema parameter, and that is part of the output.</p>
  *
  * <p>Pizza's <code>switch</code> over the MicroZ ADTs dispatched on the
  * <code>$$tag</code>; the port uses type patterns, which is equivalent since each tag has
  * exactly one class.  <code>MZ.Expr</code> and <code>MZ.Property</code> are not sealed, so
  * the switches keep a <code>default</code>: for <code>Expr</code> it is the source's own
  * <code>FatalError.notImplemented()</code> (reached by <code>Let</code>, which the
  * reducer never leaves behind); for <code>Property</code> it is empty, as Pizza's
  * statement <code>switch</code> without <code>default</code> was -- all five cases are
  * handled, so it is unreachable.  The switches over
  * <code>SchemaContext.VarKind</code> are exhaustive over the sealed type.</p>
  *
  * <p>The two closures (over the properties, and over the decomposed assignment) are
  * loops in enumeration order, which is the order <code>Aggregate.apply</code> visits;
  * they only touched fields of <code>this</code>.  The lifted
  * <code>$closure$</code> methods and <code>ConstrCoder$$closures</code> are gone; nothing
  * outside this class referenced them.</p>
  */
class ConstrCoder {

  /** The schema context this coder is associated with. */
  SchemaContext parent;

  /** Construct a new constraint coder. */
  ConstrCoder(SchemaContext parent) {
    this.parent = parent;
  }

  /** A constant used for an internal variable name. */
  private Name internVarName1 = new Name("%%1");

  /** A constant used for an internal variable name. */
  private Name internVarName2 = new Name("%%2");


  /** The number of registers allocated. */
  private int regCount = 0;

  /** Retrieve the numbers of registers. */
  int getRegisterCount() {
    return regCount;
  }

  /** A flag which indicates whether we are in a flexible context. */
  private boolean notFlex = false;

  /** The variables which are currently assigned. */
  private Set<Name> assignedVars = new HashSet<Name>();

  /** The set of variable names which are known to be determined
      since we have emitted a WAIT somewhere before. */
  private Set<Name> availVars = new HashSet<Name>();

  /** Allocate a new register. */
  int newRegister() {
    return regCount++;
  }

  /** Allocate a new register region. */
  int newRegisterRegion(int s) {
    int base = regCount;
    regCount += s;
    return base;
  }


  /** The code buffer. */
  private byte[] buffer = new byte[256];
  private int fill = 0;
  private static final int minFree = 12;

  int getCodeSize() {
    return fill;
  }

  byte[] getCode() {
    return buffer;
  }

  /** Emit an instruction. */
  private void emit(byte opc) {
    if (fill >= buffer.length - minFree) {
      byte[] newbuf = new byte[buffer.length * 2];
      System.arraycopy(buffer, 0, newbuf, 0, fill);
      buffer = newbuf;
    }
    buffer[fill++] = opc;
  }

  /** Emit an instruction with arguments. */
  private void emit(byte opc, int param) {
    emit(opc); emitInt(param);
  }

  /** Emit an instruction with arguments. */
  private void emit(byte opc, int param1, int param2) {
    emit(opc); emitInt(param1); emitInt(param2);
  }

  /** Emit an instruction with arguments. */
  private void emit(byte opc, int param1, int param2, int param3) {
    emit(opc); emitInt(param1); emitInt(param2); emitInt(param3);
  }

  /** Emit an integer argument. */
  private void emitInt(int param) {
    fill = ZAM.writeInt(buffer, fill, param);
  }

  /** Make a sub constraint coder. */
  private ConstrCoder newSubCoder() {
    ConstrCoder coder = new ConstrCoder(parent);
    coder.regCount = regCount;
    coder.availVars = availVars;
    coder.assignedVars = assignedVars;
    return coder;
  }


  /** Join a sub coders register count. */
  private void joinRegCount(ConstrCoder other) {
    regCount = other.regCount;
  }


  /** Join a sub coders code. */
  private void join(ConstrCoder other) {
    if (fill + other.fill >= buffer.length - minFree) {
      int newl = buffer.length * 2;
      while (fill + other.fill >= newl - minFree) {
        newl *= 2;
      }
      byte[] newbuf = new byte[newl];
      System.arraycopy(buffer, 0, newbuf, 0, fill);
      buffer = newbuf;
    }
    System.arraycopy(other.buffer, 0, buffer, fill, other.fill);
    fill += other.fill;
  }

  /** Join two subcoders available vars. */
  private void joinAvailVars(ConstrCoder c1, ConstrCoder c2) {
    availVars = availVars.includeAll(
                  c1.availVars.intersect(c2.availVars)
                );
  }


  /** Compile an expression in this constraint coder. */
  void compile(Expr expr, int target) {

    switch (expr) {
      case Expr.Denotation d -> {
        emit(ZAM.MKDENO, parent.unit.getDenotationIndex(d.value), target);
      }
      case Expr.Call c -> {
        int region = compileArgsSpec(parent.unit.getArgSpec(c.name), c.args);
        emit(ZAM.CALLNATIVE, parent.unit.getNativeFuncIndex(c.name, c.args.size()),
             region, target);
        if (notFlex) emit(ZAM.WAIT, target);
      }
      case Expr.Variable v -> {
        Name name = v.name;
        SchemaContext.VarKind kind = parent.lookupVar(name);
        switch (kind) {
          case SchemaContext.VarKind.Local l -> {
            if (notFlex && !availVars.contains(name)) {
              emit(ZAM.WAITLOAD, l.index, target);
              availVars = availVars.include(name);
            } else {
              emit(ZAM.LOAD, l.index, target);
            }
          }
          case SchemaContext.VarKind.Param p -> {
            if (notFlex && !availVars.contains(name)) {
              emit(ZAM.WAITLOADPARAM, p.index, target);
              availVars = availVars.include(name);
            } else {
              emit(ZAM.LOADPARAM, p.index, target);
            }
          }
          case SchemaContext.VarKind.Global g -> {
            if (notFlex && !availVars.contains(name)) {
              emit(ZAM.WAITLOADGLOBAL, g.index, target);
              availVars = availVars.include(name);
            } else {
              emit(ZAM.LOADGLOBAL, g.index, target);
            }
          }
        }
      }
      case Expr.Term t -> {
        int region = compileArgsFlex(t.args);
        emit(ZAM.MKTERM, parent.unit.getConstructorIndex(t.name, t.args.size()),
             region, target);
      }
      case Expr.Singleton s -> {
        int temp = newRegister();
        compileFlex(s.arg, temp);
        emit(ZAM.MKSINGLE, temp, target);
      }
      case Expr.Hom h -> {
        int temp = newRegister();
        compileNonFlex(h.arg, temp);
        emit(ZAM.HOM, parent.unit.getHomIndex(h.name), temp, target);
        if (notFlex) emit(ZAM.WAIT, target);
      }
      case Expr.Empty e -> {
        emit(ZAM.MKEMPTY, target);
      }
      case Expr.Universal u -> {
        emit(ZAM.MKUNIV, target);
      }
      case Expr.Intersection i -> {
        int temp1 = newRegister();
        int temp2 = newRegister();
        compileNonFlex(i.left, temp1);
        compileNonFlex(i.right, temp2);
        emit(ZAM.ISECT, temp1, temp2, target);
      }
      case Expr.Union u -> {
        int temp1 = newRegister();
        int temp2 = newRegister();
        compileNonFlex(u.left, temp1);
        compileNonFlex(u.right, temp2);
        emit(ZAM.UNION, temp1, temp2, target);
      }
      case Expr.Apply a -> {
        Expr left = a.func;
        Expr right = a.arg;
        boolean done = false;
        if (left instanceof Expr.Variable lv) {
          Name name = lv.name;
          SchemaContext.VarKind kind = parent.lookupVar(name);
          if (kind instanceof SchemaContext.VarKind.Global g) {
            int temp = newRegister();
            compileFlex(right, temp);
            if (!availVars.contains(name)) {
              emit(ZAM.WAITGLOBAL, g.index);
              availVars = availVars.include(name);
            }
            emit(ZAM.APPLYGLOBAL, g.index, temp, target);
            done = true;
          }
        }
        if (!done) {
          int temp1 = newRegister();
          compileNonFlex(left, temp1);
          int temp2 = newRegister();
          compileFlex(right, temp2);
          emit(ZAM.APPLY, temp1, temp2, target);
        }
        if (notFlex) emit(ZAM.WAIT, target);
      }
      case Expr.Complement c -> {
        Expr arg = c.arg;
        // FIXME: dirty access to paramTab and paramNames
        int temp = newRegister();
        compileFlex(arg, temp);
        Expr var = new Expr.Variable(internVarName1).set(expr.locator);
        SchemaContext sctx =
          new SchemaContext(parent.unit, this,
                            expr.locator, var, new HashSet<Name>());
        sctx.paramTab =
          sctx.paramTab.define(internVarName2,
                               new SchemaContext.VarKind.Local(-1));
        sctx.paramNames = new ArraySeq<Name>(internVarName2);
        sctx.compile(
          new ArraySeq<Property>(
                new Property.Subset(
                      new Expr.Intersection(
                            new Expr.Singleton(
                                  var
                                ).set(var.locator),
                            new Expr.Variable(
                                  internVarName2
                                ).set(arg.locator)
                          ).set(expr.locator),
                      new Expr.Empty().set(expr.locator)
                    ).set(expr.locator)
              )
        );
        emit(ZAM.MKINTEN, sctx.finish(), temp, target);
      }
      case Expr.Conditional c -> {
        int temp = newRegister();
        compileNonFlex(c.cond, temp);
        ConstrCoder tctx = newSubCoder();
        tctx.compileFlex(c.left, target);
        joinRegCount(tctx);
        ConstrCoder ectx = newSubCoder();
        ectx.compileFlex(c.right, target);
        joinRegCount(ectx);
        tctx.emit(ZAM.GOTO, ectx.fill);
        emit(ZAM.IFNOT, temp, tctx.fill);
        join(tctx);
        join(ectx);
        joinAvailVars(tctx, ectx);
        if (notFlex) emit(ZAM.WAIT, target);
      }
      case Expr.Translate t -> {
        Expr arg = t.arg;
        Expr source = t.source;
        Expr dest = t.target;
        // FIXME: dirty access to paramTab and paramNames
        int temp = newRegister();
        compileFlex(arg, temp);
        SchemaContext sctx =
          new SchemaContext(parent.unit, this, expr.locator, dest,
                            source.freeVars().excludeAll(dest.freeVars()));
        sctx.paramTab =
          sctx.paramTab.define(internVarName1,
                               new SchemaContext.VarKind.Local(-1));
        sctx.paramNames = new ArraySeq<Name>(internVarName1);
        sctx.compile(
          new ArraySeq<Property>(
                new Property.Member(source, new Expr.Variable(internVarName1)
                                                    .set(arg.locator))
                            .set(expr.locator)
              )
        );
        emit(ZAM.MKINTEN, sctx.finish(), temp, target);
      }
      case Expr.Schema s -> {
        SchemaContext sctx =
          new SchemaContext(parent.unit, this, expr.locator, s.binder, s.freeVars);
        sctx.compile(s.props);
        if (!sctx.paramNames.isEmpty()) {
          // generate code passing intension parameters
          int n = sctx.paramNames.size();
          int paramRegion = newRegisterRegion(n);
          for (int i = 0; i < n; i++) {
            Name name = sctx.paramNames.at(i);
            try {
              SchemaContext.VarKind kind = sctx.paramTab.get(name);
              switch (kind) {
                case SchemaContext.VarKind.Local l ->
                  // ???
                  // emit(ZAM.WAITLOAD, index, paramRegion+i);
                  emit(ZAM.LOAD, l.index, paramRegion + i);
                case SchemaContext.VarKind.Param p ->
                  emit(ZAM.LOADPARAM, p.index, paramRegion + i);
                default ->
                  throw FatalError.assertionFailed("intension parameter kind");
              }
            }
            catch (NoSuchElementException e) {
              throw FatalError.assertionFailed("intension parameter unbound");
            }
          }
          emit(ZAM.MKINTEN, sctx.finish(), paramRegion, target);
        } else {
          emit(ZAM.MKOUTERINTEN, sctx.finish(), target);
        }
      }
      default -> throw FatalError.notImplemented();
    }
  }

  /** Compile an expression in this constraint coder, with
      waiting access to variables. */
  void compileNonFlex(Expr expr, int target) {
    boolean saveFlex = notFlex;
    notFlex = true;
    compile(expr, target);
    notFlex = saveFlex;
  }

  void compileFlex(Expr expr, int target) {
    boolean saveFlex = notFlex;
    notFlex = false;
    compile(expr, target);
    notFlex = saveFlex;
  }

  int compileArgs(Seq<Expr> args) {
    int s = args.size();
    int region = newRegisterRegion(s);
    for (int i = 0; i < s; i++) {
      // uses default mode
      compile(args.at(i), region + i);
    }
    return region;
  }

  int compileArgsNonFlex(Seq<Expr> args) {
    int s = args.size();
    int region = newRegisterRegion(s);
    for (int i = 0; i < s; i++) {
      // overwrites default mode
      compileNonFlex(args.at(i), region + i);
    }
    return region;
  }

  int compileArgsFlex(Seq<Expr> args) {
    int s = args.size();
    int region = newRegisterRegion(s);
    for (int i = 0; i < s; i++) {
      // overwrites default mode
      compileFlex(args.at(i), region + i);
    }
    return region;
  }

  int compileArgsSpec(Seq<Boolean> spec, Seq<Expr> args) {
    int s = args.size();
    int region = newRegisterRegion(s);
    for (int i = 0; i < s; i++) {
      // overwrites default mode according to spec
      if (spec.at(i)) {
        compileNonFlex(args.at(i), region + i);
      } else {
        compileFlex(args.at(i), region + i);
      }
    }
    return region;
  }

  /** Compile a property in this constraint coder. */
  void compile(Seq<Property> props) {
    if (props.size() > 1) emit(ZAM.DEPTHFIRST);
    for (Enum<Property> e = props.enumerate(); e.more(); e.advance()) {
      Property prop = e.current();
      switch (prop) {
        case Property.Call c -> {
          int region = compileArgsSpec(parent.unit.getArgSpec(c.name), c.args);
          emit(ZAM.TESTNATIVE, parent.unit.getNativePredIndex(c.name, c.args.size()),
               region);
        }
        case Property.IsTrue t -> {
          int temp = newRegister();
          compileNonFlex(t.expr, temp);
          emit(ZAM.IF, temp, 1);
          emit(ZAM.FAILURE);
          // emit(ZAM.SUCCESS);
        }
        case Property.Equal eq -> {
          Expr left = eq.left;
          Expr right = eq.right;
          Seq<Pair<Name, Expr>> s = decomposeAssign(left, right);
          if (s == null) s = decomposeAssign(right, left);
          if (s != null) {
            for (Enum<Pair<Name, Expr>> pe = s.enumerate(); pe.more(); pe.advance()) {
              Pair<Name, Expr> p = pe.current();
              int temp = newRegister();
              compileFlex(p.snd, temp);
              SchemaContext.VarKind kind = parent.lookupVar(p.fst);
              switch (kind) {
                case SchemaContext.VarKind.Local l ->
                  emit(ZAM.STORE, temp, l.index);
                case SchemaContext.VarKind.Global g ->
                  emit(ZAM.STOREGLOBAL, temp, g.index);
                case SchemaContext.VarKind.Param pa -> {
                  int temp1 = newRegister();
                  emit(ZAM.LOADPARAM, pa.index, temp1);
                  emit(ZAM.UNIFY, temp, temp1);
                }
              }
            }
          } else {
            int temp2 = newRegister();
            compileFlex(right, temp2);
            int temp1 = newRegister();
            compileFlex(left, temp1);
            emit(ZAM.UNIFY, temp1, temp2);
          }
        }
        case Property.Member m -> {
          int temp2 = newRegister();
          compileNonFlex(m.right, temp2);
          int temp1 = newRegister();
          compileFlex(m.left, temp1);
          emit(ZAM.MEMBER, temp1, temp2);
        }
        case Property.Subset s -> {
          int temp1 = newRegister();
          int temp2 = newRegister();
          compileNonFlex(s.left, temp1);
          compileNonFlex(s.right, temp2);
          emit(ZAM.SUBSET, temp1, temp2);
        }
        default -> { }
      }
    }
    emit(ZAM.SUCCESS);
  }

  // `fst` the assigned variable, `snd` its value.
  private Seq<Pair<Name, Expr>> decomposeAssign(Expr lhs, Expr rhs) {
    if (lhs instanceof Expr.Term t1) {
      if (rhs instanceof Expr.Term t2) {
        if (t1.name.equals(t2.name)) {
          int s = t1.args.size();
          Seq<Pair<Name, Expr>> r = new BufferSeq<Pair<Name, Expr>>();
          for (int i = 0; i < s; i++) {
            Seq<Pair<Name, Expr>> r1 =
              decomposeAssign(t1.args.at(i), t2.args.at(i));
            if (r1 == null) return null;
            r = r.appendAll(r1);
          }
          return r;
        }
      }
      return null;
    } else if (lhs instanceof Expr.Variable v) {
      return new ArraySeq<Pair<Name, Expr>>(Pair.Pair(v.name, rhs));
    }
    return null;
  }

}
