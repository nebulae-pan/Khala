package io.nebula.platform.khala.graph;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.Network;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by nebula on 2019-11-17
 */
@SuppressWarnings("UnstableApiUsage")
public class Graphs {

    public static boolean isSelfLoop(EndpointPair<?> endpoints) {
        return endpoints.nodeU().equals(endpoints.nodeV());
    }

    public static <E> boolean isSelfLoop(Network<?, E> network, E edge) {
        return isSelfLoop(network.incidentNodes(edge));
    }

    public static <N> Set<N> topologicallySortedNodes(Graph<N> graph) {
        // TODO: Do we want this method to be lazy or eager?
        return new TopologicallySortedNodes<>(graph);
    }

    private static class TopologicallySortedNodes<N> extends AbstractSet<N> {
        private final Graph<N> graph;

        private TopologicallySortedNodes(Graph<N> graph) {
            this.graph = checkNotNull(graph, "graph");
        }

        @Override
        public UnmodifiableIterator<N> iterator() {
            return new TopologicalOrderIterator<>(graph);
        }

        @Override
        public int size() {
            return graph.nodes().size();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }
    }

    private static class TopologicalOrderIterator<N> extends AbstractIterator<N> {
        private final Graph<N> graph;
        private final Queue<N> roots;
        private final Map<N, Integer> nonRootsToInDegree;

        private TopologicalOrderIterator(Graph<N> graph) {
            this.graph = checkNotNull(graph, "graph");

            Set<N> nodes = graph.nodes();
            this.roots = new ArrayDeque<>();
            this.nonRootsToInDegree = new HashMap<>();
            for (N node : nodes) {
                if (graph.inDegree(node) == 0) {
                    this.roots.add(node);
                } else {
                    this.nonRootsToInDegree.put(node, graph.inDegree(node));
                }
            }
        }

        @Override
        protected N computeNext() {
            // Kahn's algorithm
            if (!roots.isEmpty()) {
                N next = roots.remove();
                for (N successor : graph.successors(next)) {
                    int newInDegree = nonRootsToInDegree.get(successor) - 1;
                    nonRootsToInDegree.put(successor, newInDegree);
                    if (newInDegree == 0) {
                        nonRootsToInDegree.remove(successor);
                        roots.add(successor);
                    }
                }
                return next;
            }
            checkState(nonRootsToInDegree.isEmpty(), "graph has at least one cycle");
            return endOfData();
        }
    }
}
