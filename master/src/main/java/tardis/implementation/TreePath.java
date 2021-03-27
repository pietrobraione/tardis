package tardis.implementation;

import static tardis.implementation.Util.filterOnPattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jbse.mem.Clause;

/**
 * Stores the tree of the explored and yet-to-explored paths, 
 * with information about their path conditions, covered branches, 
 * hit counts, and neighbor branches.
 * 
 * @author Pietro Braione
 * @author Matteo Modonato
 */
public final class TreePath {
    /** The covered items. */
    private final HashSet<String> coverage = new HashSet<>();

    /** Map used to track the times the tests hit a branch. */
    private final HashMap<String, Integer> hitsCounterMap = new HashMap<>(); //TODO ConcurrentHashMap?

    private enum NodeStatus { ATTEMPTED, COVERED };

    /**
     * A node in the {@link TreePath}.
     * 
     * @author Pietro Braione
     */
    private final class Node {
    	/** The ancestor {@link Node}. */
    	private final Node ancestor;
    	
        /** The {@link Clause} associated to this node. */
        private final Clause clause;

        /** 
         * The {@link BloomFilter} of the path condition from 
         * the root to this node. 
         */
        private final BloomFilter bloomFilter;

        /** The children nodes. */
        private final List<Node> children = new ArrayList<>();

        /** The branches covered by the path. */
        private final HashSet<String> coveredBranches = new HashSet<>();

        /** 
         * The neighbor (post frontier) branches to the path.
         */
        private final HashSet<String> branchesFrontier = new HashSet<>();

        /** 
         * The status of this node (i.e., of the path
         * from the root to this node). 
         */
        private NodeStatus status = NodeStatus.ATTEMPTED;

        /** 
         * The improvability index of this node (i.e., of the 
         * path condition from the root to this node).
         */
        private int indexImprovability;

        /** 
         * The novelty index of this node (i.e., of the 
         * path condition from the root to this node).
         */
        private int indexNovelty;

        /** 
         * The infeasibility index of this node (i.e., of the 
         * path condition from the root to this node).
         */
        private int indexInfeasibility;

        /**
         * Constructor for a nonroot node.
         * 
         * @param path a {@link List}{@code <}{@link Clause}{@code >}, 
         *        the path condition from the root to this node.
         */
        Node(Node ancestor, List<Clause> path) {
        	this.ancestor = ancestor;
            this.clause = (path == null ? null : path.get(path.size() - 1));
            this.bloomFilter = (path == null ? null : new BloomFilter(path));
        }

        /** 
         * Constructor for the root node. It does
         * not store any {@link Clause}.
         */
        Node() { 
            this(null, null);
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
        Node findChild(Clause possibleChild) {
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
         * @param path a {@link List}{@code <}{@link Clause}{@code >}, 
         *        the path condition from the root to this node.
         * @return the created child {@link Node}, 
         *         that will store {@code newChild}.
         */
        Node addChild(List<Clause> path) {
            final Node retVal = new Node(this, path);
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
    synchronized Node getRoot() {
        return this.root;
    }

    /**
     * Checks whether an item is covered.
     * 
     * @param branch the item to be checked.
     * @return {@code true} iff the branch is covered.
     */
    synchronized boolean covers(String branch) {
        return this.coverage.contains(branch);
    }

    /**
     * Returns the number of covered items.
     * 
     * @return a positive {@code int}, the total number of covered branches.
     */
    synchronized int totalCovered() {
        return this.coverage.size();
    }

    /**
     * Returns the number of covered items matching
     * a given pattern.
     * 
     * @param pattern a {@link String}, a regular expression.
     * @return a positive {@code int}, the total number of covered 
     *         branches matching {@code pattern}.
     */
    synchronized int totalCovered(String pattern) {
    	final Set<String> filtered = filterOnPattern(this.coverage, pattern);
        return filtered.size();
    }

    /**
     * Inserts a path in this {@link TreePath}.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. The first in 
     *        the sequence is the closer to the root, the last is the leaf.
     * @param coveredBranches a {@link Collection}{@code <}{@link String}{@code >}, 
     *        the branches covered by {@code path} (possibly excluded the frontier
     *        branches).
     * @param branchesFrontier a {@link Collection}{@code <}{@link String}{@code >} 
     *        containing the frontier branches next to the last branch in 
     *        {@code path}.
     * @param covered a {@code boolean}, {@code true} iff the path is
     *        covered by a test.
     * @return if {@code covered == true}, the {@link Set} of the elements in 
     *         {@code coveredBranches} that were not already covered before the 
     *         invocation of this method, otherwise returns {@code null}.
     */
    synchronized Set<String> insertPath(List<Clause> path, Collection<String> coveredBranches, Collection<String> branchesFrontier, boolean covered) {
        int index = 0;
        Node currentInTree = this.root;
        if (covered) {
            currentInTree.status = NodeStatus.COVERED;
        }

        for (int i = 0; i < path.size(); ++i) {
            final Clause currentInPath = path.get(i);
            final Node possibleChild = currentInTree.findChild(currentInPath);
            if (possibleChild == null) {
                currentInTree = currentInTree.addChild(path.subList(0, i + 1));
            } else {
                currentInTree = possibleChild;
            }
            if (covered) {
                currentInTree.status = NodeStatus.COVERED;
            }
            if (index == path.size() - 1) {
                currentInTree.coveredBranches.addAll(coveredBranches);
                currentInTree.branchesFrontier.addAll(branchesFrontier);
            }
            ++index;
        }

        if (covered) {
            final Set<String> retVal = coveredBranches.stream().filter(s -> !covers(s)).collect(Collectors.toSet());
            this.coverage.addAll(coveredBranches);
            increaseHits(coveredBranches);
            return retVal;
        } else {
            return null;
        }
    }

    /**
     * Increases by one the number of hits of a set of branches.
     * 
     * @param coveredBranches a {@link Collection}{@code <}{@link String}{@code >}.
     */
    private void increaseHits(Collection<String> coveredBranches) {
        for (String branch : coveredBranches) {
            final Integer hitsCounter = this.hitsCounterMap.get(branch);
            if (hitsCounter == null) {
                this.hitsCounterMap.put(branch, 1);
            } else {
                this.hitsCounterMap.put(branch, hitsCounter + 1);
            }
        }
    }

    /**
     * Checks whether a path exists in this {@link TreePath}.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param covered a {@code boolean}, {@code true} iff the path must be
     *        covered by a test. 
     * @return {@code true} iff the {@code path} was inserted by means
     *         of one or more calls to {@link #insertPath(Iterable) insertPath}, 
     *         and in case {@code covered == true}, if it is also covered 
     *         by a test.
     */
    synchronized boolean containsPath(List<Clause> path, boolean covered) {
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
     * Finds a node in the tree.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the 
     *        clause of the node to find.
     * @return the {@link Node}, or {@code null} if {@code path} 
     *         does not belong to the tree.
     */
    private Node findNode(List<Clause> path) {
        Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree;
    }

    /**
     * Returns the bloom filter associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link BloomFilter}, or {@code null} if
     *         {@code path} does not belong to the tree.
     */
    synchronized BloomFilter getBloomFilter(List<Clause> path) {
        final Node nodePath = findNode(path);
        return (nodePath == null ? null : nodePath.bloomFilter);
    }

    /**
     * Returns the improvability index associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the improvability index (an {@code int} between {@code 0} 
     *         and {@code 10}), or {@code -1} if
     *         {@code path} does not belong to the tree.
     */
    synchronized int getIndexImprovability(List<Clause> path) {
        final Node nodePath = findNode(path);
        return (nodePath == null ? -1 : nodePath.indexImprovability);
    }

    /**
     * Sets the improvability index associated to a given path, 
     * if the path belongs to the tree.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param indexImprovability the improvability index (an {@code int} between {@code 0} 
     *         and {@code 10}).
     */
    synchronized void setIndexImprovability(List<Clause> path, int indexImprovability) {
        //TODO check the range of indexImprovability?
        final Node nodePath = findNode(path);
        if (nodePath == null) {
            return; //TODO throw an exception?
        }
        nodePath.indexImprovability = indexImprovability;
    }

    /**
     * Returns the novelty index associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return The novelty index (an {@code int} between {@code 0} 
     *         and {@code 10}), or {@code -1} if
     *         {@code path} does not belong to the tree.
     */
    synchronized int getIndexNovelty(List<Clause> path) {
        final Node nodePath = findNode(path);
        return (nodePath == null ? -1 : nodePath.indexNovelty);
    }

    /**
     * Sets the novelty index associated to a given path, 
     * if the path belongs to the tree.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param indexNovelty the novelty index (an {@code int} between {@code 0} 
     *         and {@code 10}).
     */
    synchronized void setIndexNovelty(List<Clause> path, int indexNovelty) {
        //TODO check the range of indexImprovability?
        final Node nodePath = findNode(path);
        if (nodePath == null) {
            return; //TODO throw an exception?
        }
        nodePath.indexNovelty = indexNovelty;
    }

    /**
     * Returns the infeasibility index associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the infeasibility index, an {@code int} between {@code 0} 
     *         and {@code 3} with the following meaning:
     *         <ul>
     *         <li>{@code 3}: feasible with voting 3;</li>
     *         <li>{@code 2}: feasible with voting 2;</li>
     *         <li>{@code 1}: infeasible with voting 2;</li>
     *         <li>{@code 0}: infeasible with voting 3, or inconclusive voting.</li>
     *         </ul>
     *         If {@code path} does not belong to the tree returns {@code -1}.
     */
    synchronized int getIndexInfeasibility(List<Clause> path) {
        final Node nodePath = findNode(path);
        return (nodePath == null ? -1 : nodePath.indexInfeasibility);
    }

    /**
     * Sets the novelty index associated to a given path, 
     * if the path belongs to the tree.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param indexInfeasibility the novelty index, an {@code int} between {@code 0} 
     *         and {@code 3} with the following meaning:
     *         <ul>
     *         <li>{@code 3}: feasible with voting 3;</li>
     *         <li>{@code 2}: feasible with voting 2;</li>
     *         <li>{@code 1}: infeasible with voting 2;</li>
     *         <li>{@code 0}: infeasible with voting 3, or inconclusive voting.</li>
     *         </ul>
     */
    synchronized void setIndexInfeasibility(List<Clause> path, int indexInfeasibility) {
        //TODO check the range of indexInfeasibility?
        final Node nodePath = findNode(path);
        if (nodePath == null) {
            return; //TODO throw an exception?
        }
        nodePath.indexInfeasibility = indexInfeasibility;
    }

    /**
     * Returns the covered branches associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches covered by the path, or {@code null} if
     *         {@code path} does not belong to the tree.
     */
    synchronized Set<String> getBranchesCovered(List<Clause> path) {
        final Node nodePath = findNode(path);
        return (nodePath == null ? null : nodePath.coveredBranches);
    }

    /**
     * Returns the hit counts of the branches associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Map}{@code <}{@link String}{@code , }{@link Integer}{@code >} 
     *         mapping the branches covered by the path to the corresponding number
     *         of hits, or {@code null} if {@code path} does not belong to the tree.
     */
    synchronized Map<String, Integer> getHits(List<Clause> path) {
        final Set<String> branches = getBranchesCovered(path);
        if (branches == null) {
            return null;
        }
        final HashMap<String, Integer> retVal = new HashMap<>();
        for (String branch : branches) {
            Integer hitsCount = this.hitsCounterMap.get(branch);
            if (hitsCount == null) {
                //a branch without hit counter has not yet
                //been executed: Set its counter to zero
                hitsCount = Integer.valueOf(0);
            }
            retVal.put(branch, hitsCount);
        }
        return retVal;
    }

    /**
     * Returns the neighbor frontier branches next to a given path, used 
     * to calculate the improvability index.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the neighbor frontier branches to {@code path}, or
     *         {@code null} if {@code path} does not belong to the tree.
     */
    synchronized Set<String> getBranchesNeighbor(List<Clause> path) {
        Node nodePath = findNode(path);
        if (nodePath == null) {
        	return null;
        }
        final HashSet<String> retVal = new HashSet<>();
        while (nodePath != null) {
        	if (nodePath.branchesFrontier != null) {
        		retVal.addAll(nodePath.branchesFrontier);
        	}
        	nodePath = nodePath.ancestor;
        }
        return retVal;
    }
}