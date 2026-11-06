package zeta.agl;

import java.util.NoSuchElementException;
import java.util.function.Function;

/** Class for calculating the maximal strongly connected components of
  * a graph. <p>
  *
  * The graph may thereby be represented either by successor and predecessor
  * higher-order methods, or by an AGL mapping. <p>
  *
  * @version $Id: MaxStrongComp.pizza,v 1.2 1998/09/13 15:11:15 wg Exp $
  *
  * The last file of `zeta.agl`.  `DFS` is package-private with private fields, used by nothing but this class, so its `adjacents`
  * field becomes a `Function` outright instead of keeping a bridge.
  */
public class MaxStrongComp {

  // FIXME EFFICIENCY: derived from a pure functional algorithm ...

  public static <A> Seq<Seq<A>> msc(Aggregate<A> nodes,
                                    Function<A, Aggregate<A>> successors,
                                    Function<A, Aggregate<A>> predecessors) {
    DFS<A> succs = new DFS<A>(successors);
    DFS<A> preds = new DFS<A>(predecessors);
    succs.visit(nodes);
    succs.forest().apply(tree -> preds.visit(tree));
    return preds.forest();
  }

  public static <A> Seq<Seq<A>> msc(Map<A, Set<A>> graph) {
    Map<A, Set<A>> inv =
      graph.reduce((Map<A, Set<A>>) new HashMap<A, Set<A>>(),
                   (m, p) -> {
                     for (Enum<A> e = p.snd.enumerate(); e.more(); e.advance()) {
                       A n = e.current();
                       if (m.defines(n)) {
                         m = m.define(n, m.get(n).include(p.fst));
                       } else {
                         m = m.define(n, new HashSet<A>(p.fst));
                       }
                     }
                     return m;
                   });
    return msc(graph.domain(),
               n -> {
                 try {
                   return graph.get(n);
                 } catch (NoSuchElementException e) {
                   return new ArraySeq<A>();
                 }
               },
               n -> {
                 try {
                   return inv.get(n);
                 } catch (NoSuchElementException e) {
                   return new ArraySeq<A>();
                 }
               });
  }

  static class DFS<A> {

    private Set<A> visited = new HashSet<A>();
    private Seq<Seq<A>> forest = new BufferSeq<Seq<A>>();
    private Function<A, Aggregate<A>> adjacents;

    private DFS(Function<A, Aggregate<A>> adjacents) {
      this.adjacents = adjacents;
    }

    void visit(Aggregate<A> nodes) {
      for (Enum<A> e = nodes.enumerate(); e.more(); e.advance()) {
        A node = e.current();
        if (!visited.contains(node)) {
          forest = forest.prepend(new ArraySeq<A>());
          visitTree(node);
        }
      }
    }

    void visitTree(A node) {
      visited = visited.include(node);
      Aggregate<A> adj = adjacents.apply(node);
      adj.apply(n -> {
        if (!visited.contains(n)) visitTree(n);
      });
      forest = forest.tail().prepend(forest.head().prepend(node));
    }

    Seq<Seq<A>> forest() {
      return forest;
    }
  }
}
