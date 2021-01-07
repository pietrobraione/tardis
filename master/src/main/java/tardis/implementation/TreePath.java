package tardis.implementation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.LinkedBlockingQueue;

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
        //variable to count every time a test hits a branch
        private int hitCounter = 0;

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
     * @param covered a {@code boolean}, {@code true} iff the path is
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
     * @param covered a {@code boolean}, {@code true} iff the path must be
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
    
    /**
     * Updates the value of times the branches of a particular path were
     * hit by a test case (hitCounter).
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     */
    public synchronized void countHits(Iterable<Clause> path) {
        Node currentInTree = this.root;
        currentInTree.hitCounter = ++currentInTree.hitCounter;
        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
            	break;
            }
            currentInTree = child;
            currentInTree.hitCounter = ++currentInTree.hitCounter;
        }
    }
    
    /**
     * Calculates the minimum of the values relating to how many times
     * branches of a particular path were hit by the tests (hitCounter).
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @return The novelty index (an int)
     */
    public synchronized int calculateNoveltyIndex(Iterable<Clause> path) {
        Node currentInTree = this.root;
        HashSet<Integer> hitCounters = new HashSet<>();
        if (currentInTree.hitCounter > 0)
        	hitCounters.add(currentInTree.hitCounter);
        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
            	break;
            }
            currentInTree = child;
            if (currentInTree.hitCounter > 0)
            	hitCounters.add(currentInTree.hitCounter);
        }
        return Collections.min(hitCounters);
    }
    
    /**
     * Updates the minimum of the values relating to how many times
     * branches of a particular path into the buffer were hit by the tests
     * (hitCounter), every time a new test is run.
     * @param linkedBlockingQueue A linkedBlockingQueue of JBSEResult used as pathConditionBuffer.
     */
    public synchronized void updateNoveltyIndex(LinkedBlockingQueue<JBSEResult> linkedBlockingQueue) {
    	for (JBSEResult JBSEResultInBuffer : linkedBlockingQueue) {
    		final int newMinHitValue = calculateNoveltyIndex(JBSEResultInBuffer.getFinalState().getPathCondition());
    		JBSEResultInBuffer.setNoveltyIndex(newMinHitValue);
    	}
    }
     
    /**
     * Updates the improvability Index list (list of lists of clauses relating to not covered paths)
     * and the improvability value (the size of improvability Index list), every time a new test is run.
     * @param linkedBlockingQueue A linkedBlockingQueue of JBSEResult used as pathConditionBuffer.
     * @param testPC A list of Clauses that refer to a particular path.
     */
    public synchronized void updateImprovabilityIndex(LinkedBlockingQueue<JBSEResult> linkedBlockingQueue, List<Clause> testPC) {
    	for (JBSEResult JBSEResultInBuffer : linkedBlockingQueue) {
    		ListIterator<List<Clause>> iter = JBSEResultInBuffer.getNotCoveredBranches().listIterator();
    		while(iter.hasNext()){
    			List<Clause> path = iter.next();
    			if (testPC.size() < path.size()) {
    				continue;
    			}
    			//if all the clauses related to a path in the improvability Index list are covered, removes the
    			//clauses related to the path from the improvability Index list and updates the improvability Index
    			else if (path.size() > 0 && containsPath(path, true)) {
    				iter.remove();
    				JBSEResultInBuffer.setNotCoveredBranches(JBSEResultInBuffer.getNotCoveredBranches());
    				JBSEResultInBuffer.setImprovabilityIndex(JBSEResultInBuffer.getNotCoveredBranches().size());
    			}
    		}
    	}
    }
}