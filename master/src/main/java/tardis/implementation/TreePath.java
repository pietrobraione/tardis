package tardis.implementation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jbse.mem.Clause;

/**
 * Stores the tree of the explored and yet-to-explored paths, 
 * with information about their path conditions, covered branches, 
 * and neighbor branches. Used to calculate heuristic indices.
 * 
 * @author Pietro Braione
 * @author Matteo Modonato
 */
public final class TreePath {
    /**
     * The covered items.
     */
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
        /** The {@link Clause} associated to this node. */
        private final Clause clause;
        
        /** 
         * The status of this node (i.e., of the path
         * from the root to this node). 
         */
        private NodeStatus status = NodeStatus.ATTEMPTED;
        
        /** The children nodes. */
        private final List<Node> children = new ArrayList<>();
        
        /** The branches covered by the path. */
        private HashSet<String> coveredBranches = new HashSet<>();
        
        /** 
         * The neighbor frontier branches to the path used to 
         * calculate the improvability index.
         */
        private HashSet<String> neighborFrontierBranches = new HashSet<>();

        /**
         * Constructor for a nonroot node.
         * 
         * @param clause The {@link Clause} stored
         *        in the node.
         */
        Node(Clause clause) {
            this.clause = clause;
        }

        /** 
         * Constructor for the root node. It does
         * not store any {@link Clause}.
         */
        Node() { 
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
         * @param newChild a {@link Clause}.
         * @return the created child {@link Node}, 
         *         that will store {@code newChild}.
         */
        Node addChild(Clause newChild) {
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
        final Pattern p = Pattern.compile(pattern); 
        final Set<String> filtered = this.coverage.stream().filter(s -> { final Matcher m = p.matcher(s); return m.matches(); }).collect(Collectors.toSet());
        return filtered.size();
    }

    /**
     * Inserts a path in this {@link TreePath}.
     * 
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}, 
     *        the branches covered by {@code path}.
     * @param neighborFrontierBranches a {@link Set}{@code <}{@link String}{@code >} 
     *        containing the neighbor frontier branches next to {@code path}, used 
     *        to calculate the improvability index.
     * @param covered a {@code boolean}, {@code true} iff the path is
     *        covered by a test.
     * @return if {@code covered == true}, the {@link Set} of the elements in 
     *         {@code coveredBranches} that were not already covered before the 
     *         invocation of this method, otherwise returns {@code null}.
     */
    synchronized Set<String> insertPath(Collection<Clause> path, Set<String> coveredBranches, Set<String> neighborFrontierBranches, boolean covered) {
        int index = 0;
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
            if (index == path.size() - 1) {
                currentInTree.coveredBranches.addAll(coveredBranches);
                currentInTree.neighborFrontierBranches.addAll(neighborFrontierBranches);
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
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}.
     */
    private synchronized void increaseHits(Set<String> coveredBranches) {
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
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param covered a {@code boolean}, {@code true} iff the path must be
     *        covered by a test. 
     * @return {@code true} iff the {@code path} was inserted by means
     *         of one or more calls to {@link #insertPath(Iterable) insertPath}, 
     *         and in case {@code covered == true}, if it is also covered 
     *         by a test.
     */
    synchronized boolean containsPath(Iterable<Clause> path, boolean covered) {
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
     * Returns the covered branches associated to a given path.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches covered by the path.
     */
    synchronized Set<String> getCoveredBranches(Iterable<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.coveredBranches;
    }
    
    /**
     * Returns the neighbor frontier branches next to a given path, used 
     * to calculate the improvability index.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches used to calculate the improvability index, or
     *         {@code null} if {@code path} is not present in this {@link TreePath}.
     */
    synchronized Set<String> getNeighborFrontierBranches(Iterable<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.neighborFrontierBranches;
    }
    
    /**
     * Updates the neighbor frontier branches next to a given path, used to 
     * calculate the improvability index, by removing a set of covered branches 
     * from the neighbor frontier branches.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}, 
     *        the covered branches. 
     */
    synchronized void clearNeighborFrontierBranches(Iterable<Clause> path, Set<String> coveredBranches) {
        final Set<String> branches = getNeighborFrontierBranches(path);
        if (branches == null) {
            return;
        }
        branches.removeAll(coveredBranches);
    }
    
    /**
     * Calculates the improvability index of a given path.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the improvability index (an {@code int} between {@code 0} 
     *         and {@code 10}) or {@code -1} if {@code path} is not 
     *         present in this {@link TreePath}.
     */
    synchronized int getImprovabilityIndex(Iterable<Clause> path) {
        final Set<String> branches = getNeighborFrontierBranches(path);
        if (branches == null) {
            return -1;
        }
        final int retVal = (branches.size() > 9 ? 10 : branches.size());
        return retVal;
    }
    
    /**
     * Calculates the minimum of the values relating to how many times
     * the code branches of a particular path were hit by the tests.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return The novelty index (an {@code int} between {@code 0} 
     *         and {@code 10}) or {@code -1} if {@code path} is not 
     *         present in this {@link TreePath}.
     */
    synchronized int getNoveltyIndex(Iterable<Clause> path) {
        final Set<String> branches = getCoveredBranches(path);
        if (branches == null) {
            return -1;
        }
        final HashSet<Integer> hitsCounters = new HashSet<>();
        for (String branch : branches) {
            final Integer hitsCount = this.hitsCounterMap.get(branch);
            if (hitsCount != null) {
                hitsCounters.add(hitsCount);
            }
        }
        final int minimum = Collections.min(hitsCounters);
        return minimum > 9 ? 10 : minimum;
    }
}