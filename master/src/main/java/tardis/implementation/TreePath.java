package tardis.implementation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jbse.mem.Clause;
import tardis.implementation.ClassifierKNN.ClassificationResult;

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

    /** Set used as training set to calculate the infeasibility index. */
    public final HashSet<TrainingItem> trainingSet = new HashSet<>();

    private enum NodeStatus { ATTEMPTED, COVERED };

    /**
     * A node in the {@link TreePath}.
     * 
     * @author Pietro Braione
     */
    private final class Node {
        /** The {@link Clause} associated to this node. */
        private final Clause clause;

        /** The children nodes. */
        private final List<Node> children = new ArrayList<>();

        /** The branches covered by the path. */
        private final HashSet<String> coveredBranches = new HashSet<>();

        /** 
         * The neighbor frontier branches to the path used to 
         * calculate the improvability index.
         */
        private final HashSet<String> neighborFrontierBranches = new HashSet<>();

        /** 
         * The status of this node (i.e., of the path
         * from the root to this node). 
         */
        private NodeStatus status = NodeStatus.ATTEMPTED;

        private BloomFilter bloomFilter;

        private int improvabilityIndex;

        private int noveltyIndex;

        private int infeasibilityIndex;

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
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. The first in 
     *        the sequence is the closer to the root, the last is the leaf.
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
    synchronized Set<String> insertPath(List<Clause> path, Set<String> coveredBranches, Set<String> neighborFrontierBranches, boolean covered) {
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
                if (!covered) {
                    currentInTree.bloomFilter = new BloomFilter(path);
                }
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
     * Saves the values of the heuristic indices associated to a given
     * path in this {@link TreePath} for future use.
     * 
     * @param indexValue an int. The value to be stored.
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param indexName a String. The name of the heuristic index:
     *        improvability, novelty or infeasibility.
     */
    synchronized void cacheIndices(int indexValue, List<Clause> path, String indexName) {
        int index = 0;
        Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return;
            }
            currentInTree = child;
            if (index == path.size() - 1) {
                switch (indexName) {
                case "improvability":
                    currentInTree.improvabilityIndex = indexValue;
                    break;
                case "novelty":
                    currentInTree.noveltyIndex = indexValue;
                    break;
                case "infeasibility":
                    currentInTree.infeasibilityIndex = indexValue;
                    break;
                }
            }
            ++index;
        }
    }

    /**
     * Retrieves the values of the heuristic indices associated to a
     * given path previously saved in this {@link TreePath}.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param indexName a String. The name of the heuristic index:
     *        improvability, novelty or infeasibility.
     */
    synchronized int getCachedIndices(List<Clause> path, String indexName) {
        Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return -1;
            }
            currentInTree = child;
        }
        switch (indexName) {
        case "improvability":
            return currentInTree.improvabilityIndex;
        case "novelty":
            return currentInTree.noveltyIndex;
        case "infeasibility":
            return currentInTree.infeasibilityIndex;
        }
        return -1;
    }

    /**
     * Returns the covered branches associated to a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches covered by the path.
     */
    synchronized Set<String> getCoveredBranches(List<Clause> path) {
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
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches used to calculate the improvability index, or
     *         {@code null} if {@code path} is not present in this {@link TreePath}.
     */
    synchronized Set<String> getNeighborFrontierBranches(List<Clause> path) {
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
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}, 
     *        the covered branches. 
     */
    synchronized void clearNeighborFrontierBranches(List<Clause> path, Set<String> coveredBranches) {
        final Set<String> branches = getNeighborFrontierBranches(path);
        if (branches == null) {
            return;
        }
        branches.removeAll(coveredBranches);
    }

    /**
     * Calculates the improvability index of a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the improvability index (an {@code int} between {@code 0} 
     *         and {@code 10}) or {@code -1} if {@code path} is not 
     *         present in this {@link TreePath}.
     */
    synchronized int getImprovabilityIndex(List<Clause> path) {
        final Set<String> branches = getNeighborFrontierBranches(path);
        if (branches == null) {
            return -1;
        }
        final int retVal = (branches.size() > 9 ? 10 : branches.size());
        cacheIndices(retVal, path, "improvability");
        return retVal;
    }

    /**
     * Calculates the minimum of the values relating to how many times
     * the code branches of a particular path were hit by the tests.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return The novelty index (an {@code int} between {@code 0} 
     *         and {@code 10}) or {@code -1} if {@code path} is not 
     *         present in this {@link TreePath}.
     */
    synchronized int getNoveltyIndex(List<Clause> path) {
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
        final int retVal = minimum > 9 ? 10 : minimum;
        cacheIndices(retVal, path, "novelty");
        return retVal;
    }
    
    private static final int K = 3;

    /**
     * Calculates the infeasibility index of a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the infeasibility index, an {@code int} between {@code 0} 
     *         and {@code 3} with the following meaning
     *         <ul>
     *         <li>3: feasible with voting 3;</li>
     *         <li>2: feasible with voting 2;</li>
     *         <li>1: infeasible with voting 2;</li>
     *         <li>0: infeasible with voting 3, or inconclusive voting.</li>
     *         </ul>
     */
    synchronized int getInfeasibilityIndex(List<Clause> path) {
        final int index;
        if (this.trainingSet.size() >= K) {
            final BloomFilter bloomFilter = getBloomFilter(path);
            if (bloomFilter == null) {
                index = -1;
            } else {
                final ClassifierKNN classifier = new ClassifierKNN(K, this.trainingSet);
                final ClassificationResult result = classifier.classify(bloomFilter);

                final boolean unknown = result.isUnknown();
                final boolean feasible = result.getLabel();
                final int voting = result.getVoting();
                //averageDistance not used by now

                if (unknown || (!feasible && voting == K)) {
                    index = 0;
                } else if (!feasible && voting < K) {
                    index = 1;
                } else if (feasible && voting < K) {
                    index = 2;
                } else { //feasible && voting == K
                    index = 3;
                }
            }
        } else {
            index = 0;
        }
        cacheIndices(index, path, "infeasibility");
        return index;
    }

    /**
     * Returns the bloom filter structure previously saved in the TreePath of
     * a modified path condition, used to calculate the infeasibility index.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link BloomFilter}.
     */
    private BloomFilter getBloomFilter(List<Clause> path) {
        Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.bloomFilter;
    }

    /**
     * Adds the bloom filter structure of a path condition already taken as input by EvoSuite
     * to the training set with the correct label. Used to calculate the infeasibility index.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param solved a {@code boolean}, {@code true} if the path
     *        condition was solved, {@code false} otherwise.
     */
    synchronized void learnPathConditionFeasibility(List<Clause> path, boolean solved) {
        if (path != null) {
            final BloomFilter bloomFilter = getBloomFilter(path);
            this.trainingSet.add(new TrainingItem(bloomFilter, solved));
        }
    }

    /**
     * Calculates multiple bloom filter structures from a path condition 
     * generated by an EvoSuite seed test. Adds the bloom filter structures 
     * to the training set with the feasible label. Used to calculate the 
     * infeasibility index.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    synchronized void learnSeedPathConditions(List<Clause> path) {
        if (path != null) {
            for (int i = path.size(); i > 0; --i) {
                final BloomFilter bloomFilter = new BloomFilter(path.subList(0, i));
                this.trainingSet.add(new TrainingItem(bloomFilter, true));
            }
        }
    }
}