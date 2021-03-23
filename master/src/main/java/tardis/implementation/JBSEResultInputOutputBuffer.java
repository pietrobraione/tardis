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
import tardis.Options;
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
    private static int[] INDEX_VALUES;
    private static int[] PROBABILITY_VALUES;
    
    /** The maximum value for the index of improvability. */
    private static final int INDEX_IMPROVABILITY_MAX = 10;
    
    /** The maximum value for the index of novelty. */
    private static final int INDEX_NOVELTY_MAX = 10;
    
    /** The K value for the KNN classifier. */
    private static final int K = 3;
    
    /** The minimum size of the training set necessary for retraining. */
    private static int TRAINING_SET_MINIMUM_THRESHOLD;

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
    
    private final Options o;

    public JBSEResultInputOutputBuffer(TreePath treePath, Options o) {
    	this.o = o;
    	INDEX_VALUES = setBuffer(true);
    	PROBABILITY_VALUES = setBuffer(false);
    	TRAINING_SET_MINIMUM_THRESHOLD = this.o.getInfeasibilityIndexThreshold();
        for (int i = 0; i < INDEX_VALUES.length; ++i) {
            this.queues.put(i, new LinkedBlockingQueue<>());
        }
        this.treePath = treePath;
    }

    @Override
    public synchronized boolean add(JBSEResult item) {
        final List<Clause> path = item.getFinalState().getPathCondition();
        if (this.o.getUseImprovabilityIndex()) {
        	updateIndexImprovability(path);
        }
        if (this.o.getUseNoveltyIndex()) {
        	updateIndexNovelty(path);
        }
        if (this.o.getUseInfeasibilityIndex()) {
        	updateIndexInfeasibility(path);
        }
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
                    updateIndexInfeasibility(pathCondition);
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
    	int indexImprovability = -2;
    	int indexNovelty = -2;
    	int indexInfeasibility = -2;
        //gets the indices
    	if (this.o.getUseImprovabilityIndex()) {
    		indexImprovability = this.treePath.getIndexImprovability(path);
    	}
    	if (this.o.getUseNoveltyIndex()) {
    		indexNovelty = this.treePath.getIndexNovelty(path);
    	}
    	if (this.o.getUseInfeasibilityIndex()) {
    		indexInfeasibility = this.treePath.getIndexInfeasibility(path);
    	}
        
        //detects the index that pass a threshold
        final boolean thresholdImprovability = indexImprovability > 0;
        final boolean thresholdNovelty = indexNovelty < 2;
        final boolean thresholdInfeasibility = indexInfeasibility > 1;
        
        //counts the passed thresholds
        final int[] indices = {indexImprovability, indexNovelty, indexInfeasibility};
        final boolean[] thresholds = {thresholdImprovability, thresholdNovelty, thresholdInfeasibility};
        int count = 0;
        for (int i = 0; i < thresholds.length; ++i) {
            if (indices[i] != -2 && thresholds[i]) {
                ++count;
            }
        }
        
        if (count == 1) {
        	//only improvability index is used
    		if (this.o.getUseImprovabilityIndex() && !this.o.getUseNoveltyIndex() && !this.o.getUseInfeasibilityIndex()) {
				count = indexImprovability;
			}
			//only novelty index is used
			else if (!this.o.getUseImprovabilityIndex() && this.o.getUseNoveltyIndex() && !this.o.getUseInfeasibilityIndex()) {
				count = indexNovelty;
			}
			//only infeasibility index is used
			else if (!this.o.getUseImprovabilityIndex() && !this.o.getUseNoveltyIndex() && this.o.getUseInfeasibilityIndex()) {
				count = indexInfeasibility;
			}
    	}
        return count;
    }
    
    /**
     * Sets the buffer to work with different numbers of indices (3, 2, 1 or 0).
     * If only one index is used, sets the buffer to work with queues and probabilities
     * of this specific index.
     * If multiple indices are used, sets the buffer to work with binary representations
     * of the indices.
     * 
     * @param INDEX_VALUES a boolean. True to set index values, false to set probability values.
     * @return the array witch contains the index values, if INDEX_VALUES is true, otherwise the
     *         array witch contains the probabilities values.
     */
    synchronized int[] setBuffer(boolean INDEX_VALUES) {
    	int count = 0;
    	final boolean[] useIndices = new boolean[] {this.o.getUseImprovabilityIndex(), this.o.getUseNoveltyIndex(), this.o.getUseInfeasibilityIndex()};
    	for (boolean useIndex : useIndices) {
    		if (useIndex) {
    			++count;
    		}
    	}
    	if (INDEX_VALUES) {
    		switch (count) {
    		case 3:
    			return new int[] {3, 2, 1, 0};
    		case 2:
    			return new int[] {2, 1, 0};
    		case 1:
    			//only improvability index is used
    			if (this.o.getUseImprovabilityIndex() && !this.o.getUseNoveltyIndex() && !this.o.getUseInfeasibilityIndex()) {
    				return new int[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
    			}
    			//only novelty index is used
    			else if (!this.o.getUseImprovabilityIndex() && this.o.getUseNoveltyIndex() && !this.o.getUseInfeasibilityIndex()) {
    				return new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    			}
    			//only infeasibility index is used
    			else if (!this.o.getUseImprovabilityIndex() && !this.o.getUseNoveltyIndex() && this.o.getUseInfeasibilityIndex()) {
    				return new int[] {3, 2, 1, 0};
    			}
    		case 0:
    			return new int[] {0};
    		}
    		throw new Error("The number of active indices used to set up JBSEResultInputOutputBuffer is not valid value (should be between 0 and 3)");
    	}
    	else {
    		switch (count) {
    		case 3:
    			return new int[] {50, 30, 15, 5};
    		case 2:
    			return new int[] {60, 30, 10};
    		case 1:
    			//only improvability index is used
    			if (this.o.getUseImprovabilityIndex() && !this.o.getUseNoveltyIndex() && !this.o.getUseInfeasibilityIndex()) {
    				return new int[] {50, 12, 9, 7, 6, 5, 4, 3, 2, 1, 1};
    			}
    			//only novelty index is used
    			else if (!this.o.getUseImprovabilityIndex() && this.o.getUseNoveltyIndex() && !this.o.getUseInfeasibilityIndex()) {
    				return new int[] {50, 12, 9, 7, 6, 5, 4, 3, 2, 1, 1};
    			}
    			//only infeasibility index is used
    			else if (!this.o.getUseImprovabilityIndex() && !this.o.getUseNoveltyIndex() && this.o.getUseInfeasibilityIndex()) {
    				return new int[] {50, 30, 15, 5};
    			}
    		case 0:
    			return new int[] {100};
    		}
    		throw new Error("The number of active indices used to set up JBSEResultInputOutputBuffer is not valid value (should be between 0 and 3)");
    	}
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
            throw new AssertionError("Attempted to update the improvability index of a path condition that was not yet inserted in the TreePath.");
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
            throw new AssertionError("Attempted to update the infeasibility index of a path condition that was not yet inserted in the TreePath.");
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

