package zeta.tools.nzap;

import java.util.NoSuchElementException;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.HashMap;
import zeta.agl.Map;
import zeta.agl.Seq;
import zeta.agl.Set;
import zeta.content.nmz.MZ.Expr;
import zeta.content.nmz.MZ.Property;
import zeta.content.nmz.MZConstants;
import zeta.session.Locator;
import zeta.util.FatalError;
import zeta.util.Name;

/** Schema context.
  *
  * @version $Id: SchemaContext.pizza,v 1.3 2000/06/28 08:04:21 wg Exp $
  *
  * <h2>Port notes</h2>
  *
  * <p><b>Order is the unit file.</b>  The local variable table of a schema is filled by
  * enumerating <code>binder.freeVars()</code> and then <code>freeVars</code>, both
  * <code>zeta.agl</code> hash sets; the index each variable gets, and the order of the
  * schema's <code>varTab</code> in the written unit, is that enumeration order.  The two
  * Pizza closures shared one mutable counter <code>i</code> and assigned to fields; they
  * are now loops over <code>enumerate()</code>, which is exactly the order
  * <code>Aggregate.apply</code> visits.  Likewise in {@link #compile}: the closure that
  * split the property sequence (its <code>return</code> is the loop's
  * <code>continue</code>), the one that compiled each group, and the fold that combined
  * the group's locators are loops in the same order.  <code>Locator.combine</code> is not
  * commutative, so that order is what the constraint names depend on.</p>
  *
  * <p><code>VarKind</code> is sealed (abstract base, final cases; package-private as in
  * the jar, so no binary class can name it).  Tags <code>Global</code> 1,
  * <code>Param</code> 2, <code>Local</code> 3 are from <code>docs/adt-tags.txt</code>.
  * The fields stay package-private.  Pizza's <code>switch (kind)</code> without
  * <code>default</code> on <code>Local(_)</code>/<code>Param(_)</code> is a type test,
  * falling through for <code>Global</code> as before.</p>
  *
  * <p>The lifted <code>$closure$</code> methods and <code>SchemaContext$$closures</code>
  * are gone; nothing outside this class referenced them.  <code>toArray</code>'s class
  * literals name <code>String</code>, <code>ZAM.Binder</code> and
  * <code>ZAM.Constraint</code>, none of which registers a slot.</p>
  */
class SchemaContext {

  /** The unit context. */
  UnitContext unit;

  /** The constraint coder this schema context is created
      within (or null if this is an outest schema). */
  ConstrCoder parent;

  /** A type to represent variable kinds. */
  abstract static sealed class VarKind
      permits VarKind.Global, VarKind.Param, VarKind.Local {

    /** Pizza's discriminant.  Public and final because compiled consumers read it. */
    public final int zeta$tools$nzap$SchemaContext$VarKind$$tag;

    // Tag values are ABI; read from the reference with harness/adt_tags.py.
    private static final int TAG_GLOBAL = 1;
    private static final int TAG_PARAM = 2;
    private static final int TAG_LOCAL = 3;

    VarKind(int tag) {
      this.zeta$tools$nzap$SchemaContext$VarKind$$tag = tag;
    }

    static final class Global extends VarKind {
      int index;
      Global(int index) {
        super(TAG_GLOBAL);
        this.index = index;
      }
    }

    static final class Param extends VarKind {
      int index;
      Param(int index) {
        super(TAG_PARAM);
        this.index = index;
      }
    }

    static final class Local extends VarKind {
      int index;
      Local(int index) {
        super(TAG_LOCAL);
        this.index = index;
      }
    }

    /** Pizza's generated case factory. */
    static VarKind Global(int index) {
      return new Global(index);
    }

    /** Pizza's generated case factory. */
    static VarKind Param(int index) {
      return new Param(index);
    }

    /** Pizza's generated case factory. */
    static VarKind Local(int index) {
      return new Local(index);
    }
  }


  /** The local variable table of this schema. */
  private Map<Name, VarKind.Local> varTab = new HashMap<Name, VarKind.Local>();

  /** The sequence of ZAM variable names associated with this schema. */
  private Seq<String> varNames = new BufferSeq<String>();

  /** A mapping of outer (context) variables this schema depends on
      to their kind in this context. */
  Map<Name, VarKind> paramTab = new HashMap<Name, VarKind>();

  /** The sequence of names of the outer variables which are
      passed as schema parameters. */
  Seq<Name> paramNames = new BufferSeq<Name>();

  /** A ZAM schema being incrementally constructed. */
  ZAM.Schema zamSchema = new ZAM.Schema(null, null, null, null, null);

  /** A sequence of ZAM constraints. */
  private Seq<ZAM.Constraint> zamConstrs = new BufferSeq<ZAM.Constraint>();


  /** Create a new schema context for schema-like schema. */
  SchemaContext(UnitContext unit, ConstrCoder parent,
                Locator locator, Expr binder, Set<Name> freeVars) {
    this.unit = unit;
    this.parent = parent;
    zamSchema.name = locator.toString();
    int i = 0;
    for (Enum<Name> e = binder.freeVars().enumerate(); e.more(); e.advance()) {
      Name n = e.current();
      varTab = varTab.define(n, new VarKind.Local(i++));
      varNames = varNames.append(n.getRepr());
    }
    for (Enum<Name> e = freeVars.enumerate(); e.more(); e.advance()) {
      Name n = e.current();
      varTab = varTab.define(n, new VarKind.Local(i++));
      varNames = varNames.append(n.getRepr());
    }
    zamSchema.binder = exprToBinder(binder);
  }

  /** Convert an expression to a binder. */
  private ZAM.Binder exprToBinder(Expr expr) {
    if (expr instanceof Expr.Variable v) {
      try {
        return new ZAM.Binder.BVar(varTab.get(v.name).index);
      }
      catch (NoSuchElementException e) {
        throw FatalError.assertionFailed("unbound variable in schema binder");
      }
    } else if (expr instanceof Expr.Term t) {
      return
        new ZAM.Binder.BTerm(unit.getConstructorIndex(t.name, t.args.size()),
                             t.args.map(this::exprToBinder)
                                   .toArray(ZAM.Binder.class));
    } else {
      throw
        FatalError.assertionFailed("unexpected expression in schema binder");
    }
  }


  /** Create a new schema context for unit-like schema. */
  SchemaContext(UnitContext unit, Seq<Name> globals, Seq<Name> locals) {
    this.unit = unit;
    this.parent = null;
    zamSchema.name = unit.getName().toString() + " GLOBALS";
    zamSchema.binder =
      new ZAM.Binder.BTerm(unit.getConstructorIndex(MZConstants.unitCons, 0),
                           new ZAM.Binder[0]);
  }

  /** Allocate a new free variable in the current schema context. */
  int newVar() {
    int i = varNames.size();
    Name name = new Name("%%" + (++varCounter));
    varTab = varTab.define(name, new VarKind.Local(i));
    varNames = varNames.append(name.getRepr());
    return i;
  }
  private int varCounter = 0;

  /** Allocate a new parameter variable in the current schema context. */
  Name newParam() {
    int i = varNames.size();
    Name name = new Name("%%" + (++varCounter));
    paramTab = paramTab.define(name, new VarKind.Param(i));
    paramNames = paramNames.append(name);
    return name;
  }



  /** Compile properties to constraints of this schema. */
  void compile(Seq<Property> props) {
    Seq<Seq<Property>> newProps = new BufferSeq<Seq<Property>>();
    Seq<Property> seqProps = null;
    for (Enum<Property> e = props.enumerate(); e.more(); e.advance()) {
      Property p = e.current();
      if (p instanceof Property.Member m && m.right instanceof Expr.Variable v) {
        Name n = v.name;
        if (seqProps == null && n.equals(MZConstants.beginSeq)) {
          seqProps = new BufferSeq<Property>();
          continue;
        } else if (seqProps != null && n.equals(MZConstants.endSeq)) {
          newProps = newProps.append(seqProps);
          seqProps = null;
          continue;
        }
      }
      if (seqProps != null) {
        seqProps = seqProps.append(p);
      } else {
        newProps = newProps.append(new ArraySeq<Property>(p));
      }
    }
    if (seqProps != null)
      newProps = newProps.append(seqProps);
    for (Enum<Seq<Property>> e = newProps.enumerate(); e.more(); e.advance()) {
      Seq<Property> ps = e.current();
      ConstrCoder propCoder = new ConstrCoder(this);
      propCoder.compile(ps);
      Locator loc = null;
      for (Enum<Property> pe = ps.enumerate(); pe.more(); pe.advance()) {
        Property p = pe.current();
        if (p.locator != null) {
          if (loc == null)
            loc = p.locator;
          else
            loc = loc.combine(p.locator);
        }
      }
      String name;
      if (loc != null)
        name = loc.toString();
      else
        name = "????";
      zamConstrs =
        zamConstrs.append(
          new ZAM.Constraint(name,
                             propCoder.getRegisterCount(),
                             unit.addCode(propCoder))
        );
    }
  }


  /** Finish schema context, entering it in the unit and returning
      its schema table index. */
  int finish() {
    zamSchema.varTab = varNames.toArray(String.class);
    zamSchema.paramTab = paramNames.map((Name n) -> n.getRepr())
                                   .toArray(String.class);
    zamSchema.constrs = zamConstrs.toArray(ZAM.Constraint.class);
    return unit.addSchema(zamSchema);
  }


  /** Lookup a variable. */
  VarKind lookupVar(Name name) {
    try {
      // see if it is in this schema
      return varTab.get(name);
    }
    catch (NoSuchElementException e) {
      // lookup in context
      VarKind kind;
      try {
        kind = paramTab.get(name);
      }
      catch (NoSuchElementException e1) {
        if (parent != null) {
          // recursive lookup in outer schema
          kind = parent.parent.lookupVar(name);
          if (kind instanceof VarKind.Local || kind instanceof VarKind.Param) {
            // this becomes passed as an schema parameter
            paramNames = paramNames.append(name);
          }
        } else {
          kind = new VarKind.Global(unit.getGlobalIndex(name));
        }
        paramTab = paramTab.define(name, kind);
      }
      if (kind instanceof VarKind.Local || kind instanceof VarKind.Param) {
        // treat as an schema parameter
        return new VarKind.Param(paramNames.indexOf(name));
      } else {
        return kind;
      }
    }
  }

  /** Tell whether we are in a proper schema (instead of a unit). */
  boolean inSchema() {
    return parent != null;
  }


}
