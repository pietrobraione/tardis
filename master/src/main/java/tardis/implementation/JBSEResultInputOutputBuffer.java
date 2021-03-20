package tardis.implementation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import jbse.mem.Clause;
import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;
import tardis.implementation.ClassifierKNN.ClassificationResult;

/**
 * An {@link InputBuffer} and {@link OutputBuffer} for {@link JBSEResult}s that
 * prioritizes {@link JBSEResult}s based on several heuristics.
 * 
 * @author Pietro Braione
 *
 * @param <E> the type of the items stored in the buffer.
 */
public final class JBSEResultInputOutputBuffer implements InputBuffer<JBSEResult>, OutputBuffer<JBSEResult> {
    private static final int[] INDEX_VALUES = {3, 2, 1, 0};
    private static final int[] PROBABILITY_VALUES = {50, 30, 15, 5};
    
    /** The maximum value for the index of improvability. */
    private static final int INDEX_IMPROVABILITY_MAX = 10;
    
    /** The maximum value for the index of novelty. */
    private static final int INDEX_NOVELTY_MAX = 10;
    
    /** The K value for the KNN classifier. */
    private static final int K = 3;
    
    /** The minimum size of the training set necessary for retraining. */
    private static final int TRAINING_SET_MINIMUM_THRESHOLD = 200;

    /** The queues where the {@link JBSEResult}s are stored. */
    private final HashMap<Integer, LinkedBlockingQueue<JBSEResult>> queues = new HashMap<>();
    
    /** The KNN classifier used to calculate the infeasibility index. */
    private final ClassifierKNN classifier = new ClassifierKNN(K);

    /** Buffers the next training set for the KNN classifier. */
    private final HashSet<TrainingItem> trainingSet = new HashSet<>();
    
    /** Buffers the next covered branches for the improvability index. */
    private final HashSet<String> coverageSetImprovability = new HashSet<>();
    
    /** Buffers the next covered branches for the novelty index. */
    private final HashSet<String> coverageSetNovelty = new HashSet<>();
    
    /** The {@link TreePath} used to store information about the path conditions. */
    private final TreePath treePath;

    public JBSEResultInputOutputBuffer(TreePath treePath) {
        for (int i = 0; i < INDEX_VALUES.length; ++i) {
            this.queues.put(i, new LinkedBlockingQueue<>());
        }
        this.treePath = treePath;
    }

    @Override
    public synchronized boolean add(JBSEResult item) {
        final List<Clause> path = item.getFinalState().getPathCondition();
        updateIndexImprovability(path);
        updateIndexNovelty(path);
        updateIndexInfeasibility(path);
        final int queueNumber = calculateQueueNumber(path);
        final LinkedBlockingQueue<JBSEResult> queueInMap = this.queues.get(queueNumber);
        return queueInMap.add(item);
    }

    @Override
    public JBSEResult poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
        //chooses the index considering the different probabilities
        final Random random = new Random();
        int randomValue = random.nextInt(100); //sum of PROBABILITY_VALUES
        int sum = 0;
        int j = 0;
        do {
            sum += PROBABILITY_VALUES[j++];
        } while (sum < randomValue && j < INDEX_VALUES.length);
        
        //assert (0 < j && j <= INDEX_VALUES.length)

        synchronized (this) {
            //extracts the item
            for (int i = j - 1; i < INDEX_VALUES.length; ++i) {
                final LinkedBlockingQueue<JBSEResult> queue = this.queues.get(INDEX_VALUES[i]);
                //selects the next queue if the extracted queue is empty
                if (queue.isEmpty()) {
                    continue;
                }
                return queue.poll(timeoutDuration, timeoutTimeUnit);
            }
            //last chance to extract
            for (int i = j - 2; i >= 0; --i) {
                final LinkedBlockingQueue<JBSEResult> queue = this.queues.get(INDEX_VALUES[i]);
                //selects the next queue if the extracted queue is empty
                if (queue.isEmpty()) {
                    continue;
                }
                return queue.poll(timeoutDuration, timeoutTimeUnit);
            }
        }
        
        TimeUnit.SECONDS.sleep(1);
        return null;
    }

    @Override
    public synchronized boolean isEmpty() {
        for (LinkedBlockingQueue<JBSEResult> queue : this.queues.values()) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Caches the fact that a set of branches was covered.
     * Used to recalculate the improvability index.
     * 
     * @param newCoveredBranches a {@link Set}{@code <}{@link String}{@code >},
     *        the covered branches.
     */
    synchronized void learnCoverageForImprovabilityIndex(Set<String> newCoveredBranches) {
        this.coverageSetImprovability.addAll(newCoveredBranches);
    }
    
    /**
     * Caches the fact that a set of branches was covered.
     * Used to recalculate the novelty index.
     * 
     * @param newCoveredBranches a {@link Set}{@code <}{@link String}{@code >},
     *        the covered branches.
     */
    synchronized void learnCoverageForNoveltyIndex(Set<String> newCoveredBranches) {
        this.coverageSetNovelty.addAll(newCoveredBranches);
    }
    
    /**
     * Caches the fact that a path condition was successfully solved or not. 
     * Used to recalculate the infeasibility index.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param solved a {@code boolean}, {@code true} if the path
     *        condition was solved, {@code false} otherwise.
     */
    synchronized void learnPathConditionForInfeasibilityIndex(List<Clause> path, boolean solved) {
        if (solved) {
            //all the prefixes are also solved
            for (int i = path.size(); i > 0; --i) {
                final BloomFilter bloomFilter = this.treePath.getBloomFilter(path.subList(0, i));
                this.trainingSet.add(new TrainingItem(bloomFilter, true));
            }
        } else {
            final BloomFilter bloomFilter = this.treePath.getBloomFilter(path);
            this.trainingSet.add(new TrainingItem(bloomFilter, false));
        }
    }

    /**
     * Recalculates the improvability index of all the {@link JBSEResult}s
     * stored in this buffer and reclassifies their priorities. 
     */
    synchronized void updateIndexImprovabilityAndReclassify() {
        synchronized (this.treePath) {
            forAllQueuedItemsToUpdateImprovability((queueNumber, bufferedJBSEResult) -> {
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                this.treePath.clearNeighborFrontierBranches(pathCondition, this.coverageSetImprovability);
                updateIndexImprovability(pathCondition);
                final int queueNumberNew = calculateQueueNumber(pathCondition);
                if (queueNumberNew != queueNumber) {
                    this.queues.get(queueNumber).remove(bufferedJBSEResult);
                    this.queues.get(queueNumberNew).add(bufferedJBSEResult);
                }
            });
            this.coverageSetImprovability.clear();
        }
    }
            
    /**
     * Recalculates the novelty index of all the {@link JBSEResult}s
     * stored in this buffer and reclassifies their priorities. 
     */
    synchronized void updateIndexNoveltyAndReclassify() {
        synchronized (this.treePath) {
            forAllQueuedItemsToUpdateNovelty((queueNumber, bufferedJBSEResult) -> {
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                updateIndexNovelty(pathCondition);
                final int queueNumberNew = calculateQueueNumber(pathCondition);
                if (queueNumberNew != queueNumber) {
                    this.queues.get(queueNumber).remove(bufferedJBSEResult);
                    this.queues.get(queueNumberNew).add(bufferedJBSEResult);
                }
            });
            this.coverageSetNovelty.clear();
        }
    }

    /**
     * Recalculates the infeasibility index of all the {@link JBSEResult}s
     * stored in this buffer and reclassifies their priorities. 
     */
    synchronized void updateIndexInfeasibilityAndReclassify() {
        synchronized (this.treePath) {
            //updates and reclassifies only if this.trainingSet is big enough
            if (this.trainingSet.size() >= TRAINING_SET_MINIMUM_THRESHOLD) {
                this.classifier.train(this.trainingSet);
                this.trainingSet.clear();
                forAllQueuedItems((queueNumber, bufferedJBSEResult) -> {
                    final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                    final int queueNumberNew = calculateQueueNumber(pathCondition);
                    if (queueNumberNew != queueNumber) {
                        this.queues.get(queueNumber).remove(bufferedJBSEResult);
                        this.queues.get(queueNumberNew).add(bufferedJBSEResult);
                    }
                });
            }
        }
    }
    
    /**
     * Calculates the queue of a {@link JBSEResult} based on the path condition of its
     * final state.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return an {@code int} between {@code 0} and {@code 3}: the queue of the {@link JBSEResult}
     *         whose associated path condition is {@code path}. 
     */
    private int calculateQueueNumber(List<Clause> path) {
        //gets the indices
        final int indexImprovability = this.treePath.getIndexImprovability(path);
        final int indexNovelty = this.treePath.getIndexNovelty(path);
        final int indexInfeasibility = this.treePath.getIndexInfeasibility(path);
        
        //detects the index that pass a threshold
        final boolean thresholdImprovability = indexImprovability > 0;
        final boolean thresholdNovelty = indexNovelty < 2;
        final boolean thresholdInfeasibility = indexInfeasibility > 1;
        
        //counts the passed thresholds
        final boolean[] thresholds = {thresholdImprovability, thresholdNovelty, thresholdInfeasibility};
        int count = 0;
        for (boolean threshold : thresholds) {
            if (threshold) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Updates the improvability index for a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    private void updateIndexImprovability(List<Clause> path) {
        final Set<String> branches = this.treePath.getNeighborFrontierBranches(path);
        if (branches == null) {
            throw new AssertionError("Attempted to update the novelty index of a path condition that was not yet inserted in the TreePath.");
        }
        final int indexImprovability = Math.min(branches.size(), INDEX_IMPROVABILITY_MAX);
        this.treePath.setIndexImprovability(path, indexImprovability);
    }

    /**
     * Updates the novelty index for a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    private void updateIndexNovelty(List<Clause> path) {
        final Set<String> branches = this.treePath.getCoveredBranches(path);
        if (branches == null) {
            throw new AssertionError("Attempted to update the novelty index of a path condition that was not yet inserted in the TreePath.");
        }
        final Map<String, Integer> allHits = this.treePath.getHits(path);
        final int minimum = Collections.min(allHits.values());
        final int indexNovelty = Math.min(minimum, INDEX_NOVELTY_MAX);
        this.treePath.setIndexNovelty(path, indexNovelty);
    }
    
    /**
     * Updates the infeasibility index for a given path.
     * 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    private void updateIndexInfeasibility(List<Clause> path) {
        final BloomFilter bloomFilter = this.treePath.getBloomFilter(path);
        if (bloomFilter == null) {
            throw new AssertionError("Attempted to update the novelty index of a path condition that was not yet inserted in the TreePath.");
        }
        final ClassificationResult result = this.classifier.classify(bloomFilter);
        final boolean unknown = result.isUnknown();
        final boolean feasible = result.getLabel();
        final int voting = result.getVoting();
        //averageDistance not used by now
        final int indexInfeasibility;
        if (unknown || (!feasible && voting == K)) {
            indexInfeasibility = 0;
        } else if (!feasible && voting < K) {
            indexInfeasibility = 1;
        } else if (feasible && voting < K) {
            indexInfeasibility = 2;
        } else { //feasible && voting == K
            indexInfeasibility = 3;
        }
        this.treePath.setIndexInfeasibility(path, indexInfeasibility);
    }

    private void forAllQueuedItems(BiConsumer<Integer, JBSEResult> toDo) {
        for (int queue : this.queues.keySet()) {
            for (JBSEResult bufferedJBSEResult : this.queues.get(queue)) {
                toDo.accept(queue, bufferedJBSEResult);
            }
        }
    }                    
    
    private void forAllQueuedItemsToUpdateImprovability(BiConsumer<Integer, JBSEResult> toDo) {
        forAllQueuedItems((queue, bufferedJBSEResult) -> {
            final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
            final Set<String> toCompareBranches = this.treePath.getNeighborFrontierBranches(pathCondition);
            if (!Collections.disjoint(toCompareBranches, this.coverageSetImprovability)) {
                toDo.accept(queue, bufferedJBSEResult);
            }
        });
    }
    
    private void forAllQueuedItemsToUpdateNovelty(BiConsumer<Integer, JBSEResult> toDo) {
        forAllQueuedItems((queue, bufferedJBSEResult) -> {
            final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
            final Set<String> toCompareBranches = this.treePath.getCoveredBranches(pathCondition);
            if (!Collections.disjoint(toCompareBranches, this.coverageSetNovelty)) {
                toDo.accept(queue, bufferedJBSEResult);
            }
        });
    }
}

