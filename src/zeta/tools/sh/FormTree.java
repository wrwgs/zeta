package zeta.tools.sh;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import zeta.agl.ArraySeq;
import zeta.agl.BufferSeq;
import zeta.agl.Pair;
import zeta.agl.Seq;
import zeta.form.ChoiceForm;
import zeta.form.Form;
import zeta.form.LiteralForm;
import zeta.form.SeqForm;
import zeta.form.TopLevelForm;
import zeta.util.FatalError;

/**
 * Form trees. Used to represent menu and panel structures, and
 * to build swing components from them. <p>
 *
 * A form tree allows to dynamically add and remove top-level forms
 * according to a leading path of literals (such as
 * <code>SeqForm(SeqLiteral("File"),SeqLiteral("Save As"),
 * ...)</code>). The top-level form is added into the hierarchy as
 * determined by this path. Remaining subforms of the top-level form
 * are handled as dialogs. <p>
 *
 * From a form tree, either a menu or a panel can be build.
 *
 * @version $Id: FormTree.pizza,v 1.10 2000/05/14 17:43:32 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Ported by reading; GUI only, no test coverage.
 *
 * <p>A two-case ADT declared with {@code private case}: tags Leaf 1, Node 2
 * ({@code docs/adt-tags.txt}); the class file has the case classes package-private with
 * private fields, private static factories, and a package-private {@code FormTree(int)}
 * constructor, and so does the port.  The case set is closed and nothing outside this file
 * extends it, so the base is {@code sealed} and {@code abstract} and the cases are
 * {@code final}; nothing binary constructs the base.  The {@code switch (this)} statements
 * became type tests.  Where Pizza's switch had no {@code default}, an unmatched case did
 * nothing; with the case set closed, every such switch here is exhaustive except
 * {@code remove}, which only acts on nodes, and that is kept as written.
 *
 * <p>{@code FormComponents.makeDialog} takes an {@code IntConsumer} instead of Pizza's
 * {@code (int)->void}; both are package-internal.  {@code Form.class} was a
 * {@code Class.forName} under Pizza; {@code Form} registers no slot, so the literal is
 * equivalent.  The {@code $closure$} methods, {@code $$closures} classes and {@code access$}
 * accessors had no referrers outside this package.
 */
abstract sealed class FormTree permits FormTree.Leaf, FormTree.Node {

  // Tag values are ABI; read from the reference with harness/adt_tags.py.
  private static final int TAG_LEAF = 1;
  private static final int TAG_NODE = 2;

  /** Pizza's discriminant.  Public and final because compiled consumers read it. */
  public final int zeta$tools$sh$FormTree$$tag;

  FormTree(int tag) {
    this.zeta$tools$sh$FormTree$$tag = tag;
  }

  /**
   * The case of leafs.
   * @param literal   the literal form
   * @param restForm  a remaining form, or null, if none
   * @param origForm  the original form from which this leaf is build.
   * @param dialog    null, or a dialog created for this leaf
   */
  static final class Leaf extends FormTree {
    private LiteralForm literal;
    private Form restForm;
    private TopLevelForm origForm;
    private Component display;

    Leaf(LiteralForm literal, Form restForm, TopLevelForm origForm,
         Component display) {
      super(TAG_LEAF);
      this.literal = literal;
      this.restForm = restForm;
      this.origForm = origForm;
      this.display = display;
    }
  }

  /**
   * The case of nodes.
   * @param literal   the literal form
   * @param subtrees  the subnodes
   */
  static final class Node extends FormTree {
    private LiteralForm literal;
    private Seq<FormTree> subtrees;

    Node(LiteralForm literal, Seq<FormTree> subtrees) {
      super(TAG_NODE);
      this.literal = literal;
      this.subtrees = subtrees;
    }
  }

  /** Pizza's generated case factory. */
  private static FormTree Leaf(LiteralForm literal, Form restForm,
                               TopLevelForm origForm, Component display) {
    return new Leaf(literal, restForm, origForm, display);
  }

  /** Pizza's generated case factory. */
  private static FormTree Node(LiteralForm literal, Seq<FormTree> subtrees) {
    return new Node(literal, subtrees);
  }


  /** Construct an empty form tree. */
  static FormTree make() {
    return new Node(null,
                (Seq<FormTree>) new ArraySeq<FormTree>(
                  Node(
                        new LiteralForm("Shell"),
                        new ArraySeq<FormTree>()
                  ),
                  Node(
                        new LiteralForm("Source"),
                        new ArraySeq<FormTree>()
                  ),
                  Node(
                        new LiteralForm("Operation"),
                        new ArraySeq<FormTree>()
                  ),
                  Node(
                        new LiteralForm("Configure"),
                        new ArraySeq<FormTree>()
                  )
             )
           );
   }



  /** Add a form to this form tree. */
  void add(String[] prefix, TopLevelForm form) {
    Pair<Seq<LiteralForm>, Form> p = makePrefix(prefix, form.subform);
    Seq<LiteralForm> lits = prefixOf(p);
    if (lits.isEmpty())
      throw FatalError.assertionFailed(
              "invalid top-level form: expected prefix of literals");
    add(form, p.snd, lits.head(), lits.tail());
  }

  private static Seq<LiteralForm> prefixOf(Pair<Seq<LiteralForm>, Form> p) {
    return p.fst;
  }

  private void add(TopLevelForm origForm, Form restForm,
                   LiteralForm head, Seq<LiteralForm> rest) {
    if (rest.isEmpty()) {
      // add to this node
      if (this instanceof Leaf leaf) {
        throw FatalError.assertionFailed(
                "invalid top-level form: cannot add to leaf: " +
                leaf.literal.keyword);
      } else if (this instanceof Node node) {
        FormTree subtree = find(head, node.subtrees);
        if (subtree != null)
          throw FatalError.assertionFailed(
                  "invalid top-level form: would overwrite: " + head.keyword);
        node.subtrees =
          node.subtrees.append(Leaf(head, restForm, origForm, null));
      }
    } else {
      // walk down in tree
      if (this instanceof Leaf leaf) {
        throw FatalError.assertionFailed(
                "invalid top-level form: cannot add to leaf: " +
                leaf.literal.keyword);
      } else if (this instanceof Node node) {
        FormTree subtree = find(head, node.subtrees);
        if (subtree == null) {
          subtree = Node(head, new ArraySeq<FormTree>());
          node.subtrees = node.subtrees.append(subtree);
        }
        subtree.add(origForm, restForm, rest.head(), rest.tail());
      }
    }
  }

  /** Remove a form from this form tree. */
  void remove(String[] prefix, TopLevelForm form) {
    Pair<Seq<LiteralForm>, Form> p = makePrefix(prefix, form.subform);
    Seq<LiteralForm> lits = prefixOf(p);
    if (lits.isEmpty())
      throw FatalError.assertionFailed(
              "invalid top-level form: expected prefix of literals");
    remove(lits.head(), lits.tail());
  }

  private void remove(LiteralForm head, Seq<LiteralForm> rest) {
    if (rest.isEmpty()) {
      if (this instanceof Node node) {
        node.subtrees = removeTree(head, node.subtrees);
      }
    } else {
      // walk down in tree
      if (this instanceof Node node) {
        FormTree subtree = find(head, node.subtrees);
        if (subtree != null) {
          subtree.remove(rest.head(), rest.tail());
        }
      }
      // remove empty nodes
      if (this instanceof Node node) {
        FormTree subtree = find(head, node.subtrees);
        if (subtree != null &&
            subtree instanceof Node &&
            ((Node) subtree).subtrees.isEmpty())
          node.subtrees = removeTree(head, node.subtrees);
      }
    }
  }

  /** Extract prefix of literals from form. */
  private Pair<Seq<LiteralForm>, Form> makePrefix(String[] prefix, Form form) {
    Seq<LiteralForm> s =
      new ArraySeq<String>(prefix).map((String str) -> new LiteralForm(str));
    if (form instanceof SeqForm) {
      Form[] subForms = ((SeqForm) form).getSubforms();
      int i = 0;
      Seq<LiteralForm> buf = new BufferSeq<LiteralForm>();
      while (i < subForms.length && subForms[i] instanceof LiteralForm) {
        buf = buf.append((LiteralForm) subForms[i++]);
      }
      if (i == subForms.length)
        return Pair.Pair(s.appendAll(buf), null);
      else if (i == subForms.length - 1)
        return Pair.Pair(s.appendAll(buf), subForms[i]);
      else {
        return Pair.Pair(s.appendAll(buf),
                         (Form) (new SeqForm(new ArraySeq<Form>(subForms).subseq(i)
                                                  .toArray(Form.class))
                                    .style(((SeqForm) form).style)));
      }
    }
    else if (form instanceof LiteralForm) {
      return Pair.Pair(s.append((LiteralForm) form), null);
    } else
      return Pair.Pair(s, form);
  }

  /** Find a subtree which matches the given literal. */
  private static FormTree find(LiteralForm literal, Seq<FormTree> forest) {
    int i = forest.indexOf(
              (FormTree tree) -> tree.getLiteral().keyword.equals(literal.keyword)
            );
    if (i >= 0)
      return forest.at(i);
    else
      return null;
  }

  /** Remove a subtree which matches the given literal. */
  private static
  Seq<FormTree> removeTree(LiteralForm literal, Seq<FormTree> forest) {
    int i = forest.indexOf(
              (FormTree tree) -> tree.getLiteral().keyword.equals(literal.keyword)
            );
    if (i >= 0)
      return forest.subseq(0, i).appendAll(forest.subseq(i + 1));
    else
      return forest;
  }

  /** Get literal of tree. */
  private LiteralForm getLiteral() {
    return switch (this) {
      case Node node -> node.literal;
      case Leaf leaf -> leaf.literal;
    };
  }


  /**
   * Build a menu bar from this form tree, working in the given component
   * under the given shell state.
   */
  JMenuBar makeMenu(JFrame frame, ShellState state) {
    if (this instanceof Node node) {
      JMenuBar bar = new JMenuBar();
      node.subtrees.apply(
        (FormTree t) -> {
          bar.add(t.makeSubMenu(frame, state));
        }
      );
      return bar;
    } else {
      throw FatalError.unexpectedCase();
    }
  }

  private JMenuItem makeSubMenu(JFrame frame, ShellState state) {
    return switch (this) {
      case Node node -> {
        JMenu menu = new JMenu(getLabel(node.literal));
        node.subtrees.apply(
          (FormTree t) -> {
            menu.add(t.makeSubMenu(frame, state));
          }
        );
        yield menu;
      }
      case Leaf leaf -> {
        String label = getLabel(leaf.literal);
        if (isRequired(leaf.restForm)) label += " ...";
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(new LeafAction(frame, state, leaf));
        yield item;
      }
    };
  }

  private static class MyToolBar extends JToolBar {}

  static class LeafAction implements ActionListener {
    private final JFrame frame;
    private final ShellState state;
    private final Leaf leaf;
    private LeafAction(JFrame frame, ShellState state, Leaf leaf) {
      this.frame = frame;
      this.state = state;
      this.leaf = leaf;
    }
    @SuppressWarnings("deprecation") // JDialog.show(), as in 1998
    public void actionPerformed(ActionEvent event) {
      if (isRequired(leaf.restForm)) {
        // this menu action requires a further dialog
        leaf.origForm.setup(state.session);
        if (leaf.display == null) {
          // display not yet build
          final FormComponents gen = new FormComponents(state);
          JPanel panel;
          if (leaf.origForm.style.isModal) {
            panel = gen.makeDialog(gen.makeComponent(leaf.restForm),
                                   new String[]{"@dismiss.gif;<<",
                                                 "@apply.gif;>>"}, 1,
                                   (int i) -> {
                                     if (i == 1) eval();
                                     leaf.display.setVisible(false);
                                   }
                                  );
            JDialog dialog = new JDialog(
                                   frame,
                                   getLabel(leaf.literal),
                                   leaf.origForm.style.isModal
                                 );
            dialog.getContentPane().add(panel);
            dialog.pack();
            if (state.master != null &&
                state.master instanceof Component) {
              FormComponents.setLocationRelative((Component) state.master,
                                                 dialog);
            }
            leaf.display = dialog;
          } else {
            String applyString = leaf.origForm.style.toolIcon;
            if (applyString == null) applyString = "@apply.gif;>>";
            IntConsumer onButton =
              (int i) -> {
                if (i == 0)
                  eval();
                else {
                  if (leaf.display.getParent() ==
                      frame.getContentPane()) {
                    // toolbar docked
                    frame.getContentPane()
                         .remove(leaf.display);
                    frame.validate();
                    // frame.getContentPane().invalidate();
                    // frame.getContentPane().validate();
                    // frame.getContentPane().doLayout();
                    // frame.repaint();
                  } else {
                    // toolbar not docked:
                    // set the visible flag of
                    // frame
                    FormComponents.getFrame(leaf.display)
                                  .setVisible(false);
                  }
                }
              };
            panel = gen.makeDialog(gen.makeComponent(leaf.restForm),
                                   new String[]{applyString,
                                                 "@dismiss.gif;<<"},
                                   0,
                                   onButton,
                                   true
                                  );
            JToolBar toolBar = new MyToolBar();
            toolBar.add(panel);
            leaf.display = toolBar;
          }
        }
        if (leaf.origForm.style.isModal) {
          ((JDialog) leaf.display).show();
        } else {
          JToolBar bar = (JToolBar) leaf.display;
          Container cont = frame.getContentPane();
          if (bar.getParent() == null) {
            // not active
            remove(cont, cont.getComponents());
            cont.add(bar, BorderLayout.SOUTH);
            bar.revalidate();
            frame.getContentPane().invalidate();
            frame.getContentPane().validate();
            frame.repaint();
          } else if (bar.getParent() == cont) {
            // already displayed docked
          } else {
            // make parent visible
            FormComponents.getFrame(leaf.display)
                          .setVisible(true);
            FormComponents.getFrame(leaf.display).validate();
          }
        }
      } else
        // can directly eval
        eval();
    }

    private void remove(Container cont, Component[] comps) {
      for (int i = 0; i < comps.length; i++) {
        if (comps[i] instanceof MyToolBar) {
          cont.remove(comps[i]);
        }
      }
    }

    private void eval() {
      // all the states of the original form should be filled out:
      // unparse and evaluate it
      state.doEval(new ChoiceForm(state.session.getForms()),
                   ShellState.defaultLocator,
                   leaf.origForm.unparse(state.session));
    }
  }

  /**
   * Check whether a form is required to be displayed.
   */
  private static boolean isRequired(Form form) {
    if (form == null) return false;
    for (int i = 0; i < form.style.length; i++) {
      if (form.style[i] == Form.Style.INVISIBLE) return false;
    }
    if (form instanceof SeqForm) {
      // if none of the childs is required, we aren't too
      SeqForm sform = (SeqForm) form;
      for (int i = 0; i < sform.subforms.length; i++) {
        if (isRequired(sform.subforms[i])) return true;
      }
      return false;
    } else
      return true;
  }

  /**
   * Add buttons for this form tree to tool bar.
   */
  void addToolBar(JFrame frame, ShellState state, JToolBar bar) {
    switch (this) {
      case Node node -> {
        node.subtrees.apply(
          (FormTree t) -> {
            t.addToolBar(frame, state, bar);
          }
        );
      }
      case Leaf leaf -> {
        TopLevelForm origForm = leaf.origForm;
        if (origForm.style.toolIcon != null) {
          FormComponents gen = new FormComponents(state);
          JButton button = gen.makeButton(origForm.style.toolIcon);
          button.addActionListener(new LeafAction(frame, state, leaf));
          if (origForm.longDescr != null) {
            button.setToolTipText(origForm.longDescr);
          }
          bar.add(button);
        }
      }
    }
  }



  // FIXME: update the below code to use FormComponents.makeDialog
  /**
   * Build a tabbed-pane hierachy from this form tree, working under
   * the given shell state.
   */
  JComponent makeTabbedPanel(final ShellState state) {
    return makeTabbed(state);
    /*
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(makeTabbed(state), BorderLayout.CENTER);
    return panel;
    */
  }

  private JComponent makeTabbed(final ShellState state) {
    return switch (this) {
      case Node node -> {
        JTabbedPane panel = new JTabbedPane();
        node.subtrees.apply(
          (FormTree t) -> {
            panel.addTab(getLabel(t.getLiteral()),
                         t.makeTabbed(state));
                         // FIXME t.getLiteral().longDescr);
          }
        );
        panel.setSelectedIndex(0);
        yield panel;
      }
      case Leaf leaf -> {
        final TopLevelForm origForm = leaf.origForm;
        final JPanel panel = new JPanel();
      // panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
      // handle a bug of tabbedpane that doesn't calculates the correct height
      // by adding space at the bottom
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 15, 10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        FormComponents gen = new FormComponents(state);
        panel.add(gen.makeComponent(leaf.restForm));
        final JButton commitButton = new JButton("Apply");
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(commitButton);
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        panel.add(Box.createVerticalGlue());
        panel.add(buttons);
        // panel.getRootPane().setDefaultButton(commitButton);
        commitButton.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent event) {
              state.doEval(new ChoiceForm(state.session.getForms()),
                           ShellState.defaultLocator,
                           origForm.unparse(state.session));
            }
          }
        );
        panel.addAncestorListener(
          new AncestorListener() {
            @SuppressWarnings("deprecation") // requestDefaultFocus, as in 1998
            public void ancestorAdded(AncestorEvent event) {
              panel.getRootPane().setDefaultButton(commitButton);
              panel.getRootPane().requestDefaultFocus();
            }
            public void ancestorMoved(AncestorEvent event) {
            }
            public void ancestorRemoved(AncestorEvent event) {
            }
          }
        );

        yield panel;
      }
    };
  }

  private static String getLabel(LiteralForm form) {
    for (int i = 0; i < form.style.length; i++) {
      if (form.style[i] instanceof Form.Style.Label label)
        return label.stringOrIcon;
    }
    return form.keyword;
  }

}
