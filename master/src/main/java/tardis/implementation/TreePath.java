package tardis.implementation;

import java.util.ArrayList;
import java.util.List;

import jbse.mem.Clause;

/**
 * A tree of path condition clauses. Used to detect
 * whether a test's path condition has an already
 * explored path condition prefix.
 * 
 * @author Pietro Braione
 */
final class TreePath {
    enum NodeStatus { ATTEMPTED, COVERED };
    
    /**
     * A node in the {@link TreePath}.
     * 
     * @author Pietro Braione
     */
    private final class Node {
        private final Clause clause;
        private NodeStatus status = NodeStatus.ATTEMPTED;
        private final List<Node> children = new ArrayList<>();

        /**
         * Constructor for a nonroot node.
         * 
         * @param clause The {@link Clause} stored
         *        in the node.
         */
        public Node(Clause clause) {
            this.clause = clause;
        }

        /** 
         * Constructor for the root node. It does
         * not store any {@link Clause}.
         */
        public Node() { 
            this(null);
        }

        /**
         * Determines whether this {@link Node} has
         * a child.
         * 
         * @param possibleChild The possible child 
         *        {@link Clause}.
         * @return The child of this Node that stores
         *         {@code possibleChild}, otherwise 
         *         {@code null}.
         */
        public Node findChild(Clause possibleChild) {
            for (Node current : this.children) {
                if (current.clause.equals(possibleChild)) {
                    return current;
                }
            }
            return null;
        }

        /**
         * Adds a child to this {@link Node}.
         * 
         * @param newChild a {@link Clause}.
         * @return the created child {@link Node}, 
         *         that will store {@code newChild}.
         */
        public Node addChild(Clause newChild) {
            final Node retVal = new Node(newChild);
            this.children.add(retVal);
            return retVal;
        }

    }

    /**
     * The root {@link Node}.
     */
    private final Node root = new Node();

    /**
     * Returns the root.
     * 
     * @return a {@link Node}.
     */
    public synchronized Node getRoot() {
        return this.root;
    }

    /**
     * Inserts a path in this {@link TreePath}.
     * 
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @oaram covered a {@code boolean}, {@code true} iff the path is
     *        covered by a test. 
     */
    public synchronized void insertPath(Iterable<Clause> path, boolean covered) {
        Node currentInTree = this.root;
        if (covered) {
            currentInTree.status = NodeStatus.COVERED;
        }

        for (Clause currentInPath : path) {
            final Node possibleChild = currentInTree.findChild(currentInPath);
            if (possibleChild == null) {
                currentInTree = currentInTree.addChild(currentInPath);
            } else {
                currentInTree = possibleChild;
            }
            if (covered) {
                currentInTree.status = NodeStatus.COVERED;
            }
        }
    }

    /**
     * Checks whether a path exists in the {@link TreePath}
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @oaram covered a {@code boolean}, {@code true} iff the path must be
     *        covered by a test. 
     * @return {@code true} iff the {@code path} was inserted by means
     *         of one or more calls to {@link #insertPath(Iterable) insertPath}.
     */
    public synchronized boolean containsPath(Iterable<Clause> path, boolean covered) {
        Node currentInTree = this.root;
        if (covered && currentInTree.status != NodeStatus.COVERED) {
            return false;
        }

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return false;
            }
            currentInTree = child;
            if (covered && currentInTree.status != NodeStatus.COVERED) {
                return false;
            }
        }
        return true;
    }
}