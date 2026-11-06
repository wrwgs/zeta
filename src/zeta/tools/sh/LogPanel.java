package zeta.tools.sh;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;

import zeta.agl.BufferSeq;
import zeta.agl.Enum;
import zeta.agl.Seq;
import zeta.format.Format;
import zeta.format.FormatInfo;
import zeta.session.DebugMessage;
import zeta.session.Diag;
import zeta.session.Locator;
import zeta.session.Message;
import zeta.session.ProgressMessage;
import zeta.session.ResultMessage;
import zeta.session.SessionStateMessage;
import zeta.session.TextEditor;
import zeta.session.Unit;

/**
 * A panel which displays the session log (as a tree).
 *
 * @version $Id: LogPanel.pizza,v 1.17 2000/05/16 08:10:47 wg Exp $
 *
 * <h2>Port notes</h2>
 *
 * <p>Ported by reading; GUI only, no test coverage.  Layout, icons, sizes and the
 * scroll-to-end thread hack are as in 1998.
 *
 * <p>PORT NOTE: {@link #remove()} and {@code Transaction.collapse} wrapped
 * {@code editor.reset()} in {@code catch (IOException e) {}}; {@code TextEditor.reset}
 * declares no checked exception (jar and port alike), so javac rejects the handler as
 * unreachable, and it is -- every implementation is compiled by a checked-exception compiler.
 * The handlers are dropped.  {@code editor.commit()} in {@link #visit} does declare
 * {@code IOException} and keeps its handler.
 *
 * <p>{@link #getVisitCap} and {@link #visit} dispatched on {@code Locator.VisitCapability}'s
 * tag (NativeVisit 1, TextVisit 3), and the jar casts tag 3 to {@code TextVisit}; here they
 * test {@code == NativeVisit} and {@code instanceof TextVisit}.  The two differ only for the
 * binary-only {@code FileVisit} case, which shares tag 3 (zesurrect-plan.md 9.5d): the jar
 * would throw {@code ClassCastException} on it, the port ignores it.  Nothing live constructs
 * {@code FileVisit} (its only users are the dead {@code FileVisitor} classes), so the
 * difference is unreachable.
 *
 * <p>DiagItem's {@code switch (item.category)} had no default; a category outside
 * Error/Warning/Hint left {@code icon} unassigned (Pizza's definite-assignment check was
 * weaker).  {@code Diag.Category} has exactly those three constants, so the port initialises
 * {@code icon} to null and the case cannot arise.
 *
 * <p>The Pizza closures in the content views assigned captured locals (coordinates, sizes);
 * they are loops over the same enumeration here, which is what {@code Aggregate.apply} does.
 * The {@code $closure$} methods, {@code $$closures} classes, {@code access$} accessors and
 * anonymous-class files of the jar had no referrers outside this package.
 */
class LogPanel extends JPanel {


  private static final String newl = System.getProperty("line.separator");
  private static final int MAXHISTORY = 20;


  /**
   * The shell state.
   */
  private final ShellState state;

  /**
   * The panel holding the logged transactions.
   */
  private final JPanel content;
  private Component contentGlue = null;

  /**
   * The scroll pane holding the content.
   */
  private final JScrollPane view;


  /**
   * The sequence of transactions being displayed.
   */
  private Seq<Transaction> transactions = new BufferSeq<Transaction>();

  /**
   * The last transaction, if any.
   */
  private Transaction lastTransaction = null;


  /**
   * Icon used for displaying expanded transactions
   */
  private final Icon expandedTransactionIcon;

  /**
   * Icon used for displaying collapsed transactions
   */
  private final Icon collapsedTransactionIcon;

  /**
   * Icon used for displaying finished messages.
   */
  private final Icon finishedIcon;

  /**
   * Icon used for displaying error diagnostics
   */
  private final Icon errorIcon;

  /**
   * Icon used for displaying warning diagnostics
   */
  private final Icon warningIcon;

  /**
   * Icon used for displaying hint diagnostics
   */
  private final Icon hintIcon;

  /**
   * Icon used for displaying result messages
   */
  private final Icon resultIcon;

  /**
   * Icon used for displaying progress messages
   */
  private final Icon progressIcon;

  /**
   * The metrics of the message font.
   */
  private final FontMetrics fontMetrics;

  /**
   * The color used for selections.
   */
  private final Color selectionColor;


  /**
   * Create the log panel.
   */
  LogPanel(ShellState state) {
    this.state = state;
    setDoubleBuffered(true);


    File libDir = new File(state.session.getLibDir(), "images");
    expandedTransactionIcon =
      new ImageIcon(new File(libDir, "transaction-expanded.gif").getPath());
    collapsedTransactionIcon =
      new ImageIcon(new File(libDir, "transaction-collapsed.gif").getPath());
    errorIcon = new ImageIcon(new File(libDir, "error.gif").getPath());
    warningIcon = new ImageIcon(new File(libDir, "warning.gif").getPath());
    hintIcon = new ImageIcon(new File(libDir, "hint.gif").getPath());
    resultIcon = new ImageIcon(new File(libDir, "result.gif").getPath());
    finishedIcon = new ImageIcon(new File(libDir, "finished.gif").getPath());
    progressIcon = new ImageIcon(new File(libDir, "progress.gif").getPath());

    fontMetrics = getFontMetrics(GuiOptions.active.messageFont);
    selectionColor = new JTextField().getSelectionColor();


    setLayout(new BorderLayout());
    content = new JPanel();
    content.setBackground(Color.white);
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(true);
    content.setDoubleBuffered(true);
    view = new JScrollPane(content);
    view.setVerticalScrollBarPolicy(
      JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    );
    view.getVerticalScrollBar().setUnitIncrement(fontMetrics.getHeight());
    view.setHorizontalScrollBarPolicy(
      JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
    );
    view.getHorizontalScrollBar()
        .setUnitIncrement(fontMetrics.getMaxAdvance());
    view.setPreferredSize(GuiOptions.active.commanderSize);
    add(view, BorderLayout.CENTER);
  }

  /**
   * Remove the log panel, clearing resources.
   */
  void remove() {
    TextEditor editor = state.session.getTextEditor();
    if (editor != null)
      // PORT NOTE: unreachable `catch (IOException e) {}` dropped (class comment).
      editor.reset();
  }

  /**
   * Scroll to the end of the log panel.
   */
  private void scrollToEnd() {
    revalidate();
    Dimension size = content.getSize();
    content.scrollRectToVisible(new Rectangle(0, size.height - 10,
                                              10, 10));
    /*
    JViewport vport = view.getViewport();
    Dimension ext = vport.getExtentSize();
    vport.setViewPosition(new Point(0,
                                    size.height-ext.height));
    */
  }

  /**
   * Add a message to the tree.
   */
  void addMessage(Message message) {
    if (message instanceof EvalMessage) {
      addTransaction((EvalMessage) message);
    } else if (message instanceof DebugMessage ||
               message instanceof SessionStateMessage) {
      // ignore
    } else {
      if (lastTransaction == null) {
        // initialization message
        addTransaction(new EvalMessage(ShellState.defaultLocator,
                                       new String[]{"<initialization>"}));
      }
      if (message instanceof FinishedMessage) {
        new FinishedItem(lastTransaction);
      }
      else if (message instanceof Diag) {
        // add each indiviudal item of the diagnostics
        Diag.Item[] items = ((Diag) message).getItems();
        for (int i = 0; i < items.length; i++) {
          new DiagItem(lastTransaction, items[i]);
        }
      } else if (!(message instanceof ProgressMessage) ||
                 GuiOptions.active.logProgress) {
        // add general message node
        new MessageItem(lastTransaction, message);
      }
    }
    // the below strange thing is necessary to make scrollToEnd work
    // (and it will still not always work with this hack!)
    Thread thread =
      new Thread(
            new Runnable() {
              public void run() {
                try {
                  SwingUtilities.invokeAndWait(
                    new Runnable() {
                      public void run() {
                      }
                    }
                  );
                } catch (Exception e) {}
                scrollToEnd();
              }
            }
          );
    thread.setPriority(Thread.currentThread().getPriority() - 2);
    thread.start();

    /*
    Timer timer =
      new Timer(200,
                new ActionListener(){
                  public void actionPerformed(ActionEvent e){
                    scrollToEnd();
                  }
                }
               );
    timer.setRepeats(false);
    timer.start();
    */
  }

  /**
   * Add message for start of transaction to the log panel. Consecutive
   * adds of messages are associated with the last added transaction.
   */
  void addTransaction(EvalMessage trans) {
    if (lastTransaction != null && GuiOptions.active.collapsePrevious)
      lastTransaction.collapse();
    while (transactions.size() >= MAXHISTORY) {
      content.remove(transactions.head());
      transactions = transactions.tail();
    }
    lastTransaction = new Transaction(trans);
    transactions = transactions.append(lastTransaction);
  }

  /** Transaction components. */
  private class Transaction extends JPanel {

    private final EvalMessage message;
    private final JButton button;
    private final JPanel transPanel;
    private final JPanel itemPanel;
    private boolean expanded;

    Transaction(EvalMessage message) {
      this.message = message;
      setDoubleBuffered(true);
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setOpaque(false);
      setAlignmentX(Component.LEFT_ALIGNMENT);
      setAlignmentY(Component.TOP_ALIGNMENT);
      button = new JButton(expandedTransactionIcon);
      button.setAlignmentX(Component.LEFT_ALIGNMENT);
      button.setAlignmentY(Component.TOP_ALIGNMENT);
      button.setBorder(null);
      button.setOpaque(false);
      button.setRequestFocusEnabled(false);
      expanded = true;
      button.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (expanded)
              collapse();
            else
              expand();
          }
        }
      );
      add(button);
      transPanel = new JPanel();
      transPanel.setLayout(new BoxLayout(transPanel, BoxLayout.Y_AXIS));
      transPanel.setOpaque(false);
      transPanel.add(makeContentView(message.toFormat(new FormatInfo())));
      transPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
      transPanel.setAlignmentY(Component.TOP_ALIGNMENT);
      itemPanel = new JPanel();
      itemPanel.setLayout(new BoxLayout(itemPanel, BoxLayout.Y_AXIS));
      itemPanel.setOpaque(false);
      itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
      itemPanel.setAlignmentY(Component.TOP_ALIGNMENT);
      transPanel.add(itemPanel);
      add(transPanel);
      if (contentGlue != null) content.remove(contentGlue);
      content.add(this);
      contentGlue = Box.createVerticalGlue();
      content.add(contentGlue);
    }

    void collapse() {
      transPanel.remove(itemPanel);
      button.setIcon(collapsedTransactionIcon);
      expanded = false;
      TextEditor editor = state.session.getTextEditor();
      if (editor != null)
        // PORT NOTE: unreachable `catch (IOException e) {}` dropped (class comment).
        editor.reset();
      revalidate();
    }

    void expand() {
      transPanel.add(itemPanel);
      button.setIcon(expandedTransactionIcon);
      expanded = true;
      revalidate();
    }

    void addItem(Component comp) {
      itemPanel.add(comp);
    }

  }


  /** The class of finished items. */
  private class FinishedItem extends JPanel {

    FinishedItem(Transaction trans) {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setOpaque(false);
      JLabel icon = new JLabel(finishedIcon);
      icon.setAlignmentY(Component.TOP_ALIGNMENT);
      icon.setOpaque(false);
      add(icon);
      add(Box.createGlue());
      trans.addItem(this);
    }
  }

  /** The class of message items. */
  private class MessageItem extends JPanel {
    private final Message message;

    MessageItem(Transaction trans, Message message) {
      this.message = message;
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setOpaque(false);
      JLabel icon;
      boolean nospace = false;
      if (message instanceof ResultMessage) {
        icon = new JLabel(resultIcon);
      } else {
        // fine tuning: this icon doesn't needs a space
        icon = new JLabel(progressIcon);
        nospace = true;
      }
      icon.setAlignmentY(Component.TOP_ALIGNMENT);
      icon.setOpaque(false);
      add(icon);
      if (!nospace) addSpace(this);
      add(makeContentView(message.toFormat(new FormatInfo())));
      add(Box.createHorizontalGlue());
      trans.addItem(this);
    }
  }

  /** The class of diagnostic items. */
  private class DiagItem extends JPanel {
    private final Diag.Item item;

    DiagItem(Transaction trans, Diag.Item item) {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setOpaque(false);
      this.item = item;
      Icon icon = null;
      if (item.category == Diag.Category.Error) {
        icon = errorIcon;
      } else if (item.category == Diag.Category.Warning) {
        icon = warningIcon;
      } else if (item.category == Diag.Category.Hint) {
        icon = hintIcon;
      }
      JLabel iconLabel = new JLabel(icon);
      iconLabel.setAlignmentY(Component.TOP_ALIGNMENT);
      iconLabel.setOpaque(false);
      add(iconLabel);
      addSpace(this);
      // add(makeLocatorView(item.locator));
      // addSpace(this);
      Format fmt;
      if (item.locator != null) {
        fmt = Format.block(
                item.locator.toFormat(new FormatInfo()),
                Format.space,
                Format.indent(item.message)
              );
      } else {
        fmt = item.message;
      }
      add(makeContentView(fmt));
      add(Box.createHorizontalGlue());
      trans.addItem(this);
    }
  }

  /**
   * Create a component to display a locator.
   */
  private JComponent makeLocatorView(final Locator locator) {
    return new LocatorView(locator);
  }

  private class LocatorView extends JButton implements ActionListener {
    final Locator locator;
    Locator.VisitCapability cap = null;
    TextEditor editor = state.session.getTextEditor();

    LocatorView(Locator locator) {
      this.locator = locator;
      // System.err.println("font: " + textFont);
      setFont(GuiOptions.active.messageFont);
      setAlignmentX(Component.CENTER_ALIGNMENT);
      setAlignmentY(0.75f);
      setOpaque(false);
      // setRolloverEnabled(true);
      setRequestFocusEnabled(false);
      setText(locator.getMnemonic());
      setBorder(null);
      boolean canVisit;
      cap = getVisitCap(locator);
      if (cap != null) {
        setForeground(Color.blue);
        setToolTipText("Locator " + locator + ": click to visit source");
        addActionListener(this);
      }
      else {
        setToolTipText("Locator " + locator +
                       ": source cannot be visited");
      }
    }

    public void actionPerformed(ActionEvent e) {
      visit(locator, cap);
    }

  }

  private Locator.VisitCapability getVisitCap(Locator locator) {
    Locator.VisitCapability[] caps = locator.getVisitCapabilities();
    boolean textPossible = state.session.getTextEditor() != null;
    for (int i = 0; i < caps.length; i++) {
      if (caps[i] == Locator.VisitCapability.NativeVisit) {
        return caps[i];
      } else if (caps[i] instanceof Locator.VisitCapability.TextVisit tv) {
        File file = tv.file;
        if (textPossible &&
            // QUICK HACK: this is a GUI panel reference
            !file.getName().startsWith("*") &&
            !file.getName().endsWith("*")) {
          return caps[i];
        }
      }
    }
    return null;
  }

  private void visit(Locator locator, Locator.VisitCapability cap) {
    if (cap == Locator.VisitCapability.NativeVisit) {
      locator.nativeVisit();
    } else if (cap instanceof Locator.VisitCapability.TextVisit textVisit) {
      TextEditor editor = state.session.getTextEditor();
      if (editor != null) {
        try {
          editor.visit(textVisit);
          editor.commit();
        }
        catch (IOException ex) {
          alert("Cannot visit:\n" + ex.getMessage());
        }
      } else
        alert("Cannot visit: no editor attached");
    }
  }

  private void alert(String message) {
    JOptionPane.showMessageDialog(
      state.master != null &&
      state.master instanceof Component ?
      (Component) state.master : null,
      message,
      "Error",
      JOptionPane.WARNING_MESSAGE
    );
  }

  /**
   * Create a component to display a unit.
   */
  private JComponent makeUnitView(final Unit unit) {
    return new UnitView(unit);
    /*
    JPanel panel = new JPanel();
    panel.add(new UnitView(unit), BorderLayout.CENTER);
    return panel;
    */
  }

  private class UnitView extends JButton implements ActionListener {
    private final Unit unit;
    private Locator locator = null;
    private Locator.VisitCapability cap = null;

    UnitView(Unit unit) {
      this.unit = unit;
      setFont(GuiOptions.active.messageFont);
      // setForeground(Color.blue);
      setAlignmentX(Component.CENTER_ALIGNMENT);
      setAlignmentY(0.75f);
      // setRolloverEnabled(true);
      setOpaque(false);
      setRequestFocusEnabled(false);
      String repr = unit.getName().getRepr();
      int i = repr.indexOf(":");
      if (i >= 0) {
        repr = repr.substring(i + 1);
        i = repr.indexOf("#");
        if (i >= 0) repr = repr.substring(0, i);
      }
      setText(repr);
      setBorder(null);
      locator = state.session.unitLocator(unit);
      if (locator != null) {
        cap = getVisitCap(locator);
      }
      if (cap != null) {
        setForeground(Color.blue);
        setToolTipText("Unit " + unit.getName() + ": click to visit source");
        addActionListener(this);
      } else
        setToolTipText("Unit " + unit.getName() +
                       ": source cannot be visited");
    }

    public void actionPerformed(ActionEvent e) {
      visit(locator, cap);
    }

  }


  /**
   * Create a panel to display content of message
   */
  private JComponent makeContentView(Format fmt) {
    Dimension size = content.getSize();
    int width = size.width;
    String testString = "ABCDEFGHIJKLMNOXPQRST";
    int columns = (width / (fontMetrics.stringWidth(testString) /
                           testString.length())) - 7;
    // System.err.println(columns);
    String string = fmt.toString(columns);
    JComponent comp = new HandMadeContentView(string);

    return comp;
  }


  class HandMadeContentView extends JPanel implements MouseListener,
                                                      ClipboardOwner {

    private final Dimension size;
    private final String content;


    HandMadeContentView(String content) {
      this.content = content;
      Seq<MessageMarkup.Element> elems = MessageMarkup.create(state, content);
      size = new Dimension(0, 0);
      setFont(GuiOptions.active.messageFont);
      setBackground(Color.white);
      setAlignmentY(Component.TOP_ALIGNMENT);
      setAlignmentX(Component.LEFT_ALIGNMENT);
      setLayout(null);
      boolean withBorders =
        elems.exists((MessageMarkup.Element elem) ->
                       elem == MessageMarkup.Element.BREAK);
      if (withBorders) {
        setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                    BorderFactory.createCompoundBorder(
                      LineBorder.createBlackLineBorder(),
                      BorderFactory.createEmptyBorder(2, 2, 2, 2)
                    )
                  ));
      } else {
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      }
      Insets insets = getInsets();
      int xc = insets.left;
      int yc = insets.top;
      int fontHeight = 85 * fontMetrics.getHeight() / 100;
      int lineHeight = fontHeight;
      boolean locators = false;
      for (Enum<MessageMarkup.Element> e = elems.enumerate(); e.more(); e.advance()) {
        MessageMarkup.Element elem = e.current();
        if (elem instanceof MessageMarkup.Element.Literal lit) {
          String literal = lit.text;
          JLabel text = new JLabel(literal);
          int width = fontMetrics.stringWidth(literal);
          text.setFont(GuiOptions.active.messageFont);
          text.setBorder(null);
          // text.setBackground(Color.white);
          text.setOpaque(false);
          text.setForeground(Color.black);
          add(text);
          text.setBounds(xc, yc, width, fontHeight);
          xc += width;
        } else if (elem == MessageMarkup.Element.BREAK) {
          xc += insets.right;
          if (xc > size.width) size.width = xc;
          xc = insets.left;
          yc += lineHeight;
          lineHeight = fontHeight;
        } else if (elem instanceof MessageMarkup.Element.EmbeddedUnit eu) {
          JComponent view = makeUnitView(eu.unit);
          Dimension vsize = view.getPreferredSize();
          add(view);
          view.setBounds(xc, yc, vsize.width, fontHeight);
          // if (vsize.height > lineHeight) lineHeight = vsize.height;
          xc += vsize.width;
        } else if (elem instanceof MessageMarkup.Element.EmbeddedLocator el) {
          JComponent view = makeLocatorView(el.locator);
          Dimension vsize = view.getPreferredSize();
          add(view);
          view.setBounds(xc, yc, vsize.width, fontHeight);
          // if (vsize.height > lineHeight) lineHeight = vsize.height;
          xc += vsize.width;
          locators = true;
        }
      }
      if (xc > size.width) size.width = xc;
      size.height = yc + lineHeight;
      // size.width += fontMetrics.getMaxAdvance();
      size.width += 3;
      size.height += 3;

      addMouseListener(this);

    }

    public Dimension getMaximumSize() {
      return size;
    }

    public Dimension getPreferredSize() {
      return size;
    }


    public Dimension getMinimumSize() {
      return size;
    }

    public Dimension getPreferredScrollableViewportSize() {
      return size;
    }

    @SuppressWarnings("deprecation")
    public boolean isFocusTraversable() {
      return false;
    }

    public void mousePressed(MouseEvent e) {
    }

    public final void mouseReleased(MouseEvent e) {
    }

    public final void mouseClicked(MouseEvent e) {
      if (e.getClickCount() > 1) {
        select();
      } else {
        unselect();
      }
    }

    public final void mouseEntered(MouseEvent e) {
    }

    public final void mouseExited(MouseEvent e) {
    }

    private void select() {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      StringSelection sel = new StringSelection(content);
      clipboard.setContents(sel, this);
      setBackground(selectionColor);
      repaint();
    }

    public void lostOwnership(Clipboard c, Transferable cont) {
      unselect();
    }

    private void unselect() {
      setBackground(Color.white);
      repaint();
    }

  }

  class TextPaneContentView extends JTextPane {

    private final Dimension size;

    TextPaneContentView(String content) {
      // setDebugGraphicsOptions(DebugGraphics.LOG_OPTION);
      size = new Dimension(0, 0);
      setFont(GuiOptions.active.messageFont);
      /*
      System.err.println("metrics: " + fontMetrics
                        + "\nmax advance: " + fontMetrics.getMaxAdvance()
                        + "\n'Xx1 ' advance: " +
                         fontMetrics.stringWidth("Xx1 ")
                        );
      */
      Seq<MessageMarkup.Element> elems = MessageMarkup.create(state, content);
      setAlignmentY(Component.TOP_ALIGNMENT);
      setAlignmentX(Component.LEFT_ALIGNMENT);
      Document doc = getDocument();
      if (doc instanceof AbstractDocument) {
        // prevent asynchronous loading
        ((AbstractDocument) doc).setAsynchronousLoadPriority(-1);
      }
      setOpaque(false);  // SWING BUG: entirely mixed up if set with size
      int lineCount = 0;
      int lineHeight = fontMetrics.getHeight();
      int lineWidth = 0;
      for (Enum<MessageMarkup.Element> e = elems.enumerate(); e.more(); e.advance()) {
        MessageMarkup.Element elem = e.current();
        if (elem instanceof MessageMarkup.Element.Literal lit) {
          String text = lit.text;
          lineWidth += fontMetrics.stringWidth(text);
          replaceSelection(text);
        } else if (elem == MessageMarkup.Element.BREAK) {
          if (lineWidth > size.width) size.width = lineWidth;
          lineWidth = 0;
          size.height += lineHeight;
          lineHeight = fontMetrics.getHeight();
          replaceSelection(newl);
          lineCount++;
        } else if (elem instanceof MessageMarkup.Element.EmbeddedUnit eu) {
          JComponent view = makeUnitView(eu.unit);
          Dimension vsize = view.getPreferredSize();
          lineWidth += vsize.width;
          if (vsize.height > lineHeight) lineHeight = vsize.height;
          insertComponent(view);
        } else if (elem instanceof MessageMarkup.Element.EmbeddedLocator el) {
          JComponent view = makeLocatorView(el.locator);
          Dimension vsize = view.getPreferredSize();
          lineWidth += vsize.width;
          if (vsize.height > lineHeight) lineHeight = vsize.height;
          insertComponent(view);
        }
      }
      if (lineWidth > size.width) size.width = lineWidth;
      size.width += fontMetrics.getMaxAdvance();
      size.height += lineHeight;

      setEditable(false);
      if (lineCount > 0) {
        setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                    BorderFactory.createCompoundBorder(
                      LineBorder.createBlackLineBorder(),
                      BorderFactory.createEmptyBorder(2, 2, 2, 2)
                    )
                  ));
        size.width += 3;
        size.height += 10;

      } else {
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        size.width += 2;
        size.height += 2;
      }
      // addCaretListener(this);
    }

    public Dimension getMaximumSize() {
      return size;
    }

    public Dimension getPreferredSize() {
      return size;
    }


    public Dimension getMinimumSize() {
      return size;
    }

    public Dimension getPreferredScrollableViewportSize() {
      return size;
    }

    /*
    public void caretUpdate(CaretEvent e){
      // System.err.println("caret " + e.getDot() + " ... " + e.getMark() +
      //                getSelectedText());
      int size = e.getMark()-e.getDot();
      if (size > 1 || size < -1){
        // copy into system clipboard

        // copy();
        // ..... doesn't works

        // next try:
        // StringSelection sel = new StringSelection(getSelectedText());
        // getToolkit().getSystemClipboard().setContents(sel, sel);
        // ... doesn't works as well, so the problem is in AWT

        // so insert copy() here, perhaps it works in some future release
        copy();
      }
    }
    */

    @SuppressWarnings("deprecation")
    public boolean isFocusTraversable() {
      return false;
    }

  }


  /** Add a horizontal space to some panel. */
  private void addSpace(Container panel) {
    panel.add(Box.createRigidArea(new Dimension(5, 0)));
  }

}
