package tardis.implementation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jbse.mem.Clause;

public final class TreePath {
    private final class Node {
        private final Clause clause;
        private final List<Node> children = new ArrayList<>();

        public Node(Clause clause) {
            this.clause = clause;
        }

        /** 
         * Constructor for the root node; all the
         *  other nodes have a clause.
         */
        public Node() { 
            this(null);
        }

        public Node findChild(Clause possibleChild) {
            for (Node current : this.children) {
                if (current.clause.equals(possibleChild)) {
                    return current;
                }
            }
            return null;
        }

        public Node addChild(Clause newChild) {
            final Node retVal = new Node(newChild);
            this.children.add(retVal);
            return retVal;
        }

    }


    public Node getRoot() {
        return this.root;
    }

    public Node root = new Node();

    public void insertPath(Collection<Clause> path) {
        Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node possibleChild = currentInTree.findChild(currentInPath);
            if (possibleChild == null) {
                currentInTree = currentInTree.addChild(currentInPath);
            } else {
                currentInTree = possibleChild;
            }
        }
    }

    public boolean containsPath(Collection<Clause> path) {
        Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return false;
            }
            currentInTree = child;
        }
        return true;
    }
}