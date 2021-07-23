package tardis.implementation;

import static tardis.implementation.Util.filterOnPattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** The maximum value for the index of improvability. */
    private static final int INDEX_IMPROVABILITY_MAX = 10;
    
    /** The minimum value for the index of novelty. */
    private static final int INDEX_NOVELTY_MIN = 0;
    
    /** The maximum value for the index of novelty. */
    private static final int INDEX_NOVELTY_MAX = 10;
    
    /** 
     * The queue numbers, from the best to the worst.
     * This is the case where zero indices are active. 
     */
    private static final int[] QUEUE_RANKING_0_INDICES = {0};
    
    /** 
     * The queue numbers, from the best to the worst.
     * This is the case where the only active index is 
     * the improvability one. 
     */
    private static final int[] QUEUE_RANKING_1_INDEX_IMPROVABILITY = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
    
    /** 
     * The queue numbers, from the best to the worst.
     * This is the case where the only active index is 
     * the novelty one. 
     */
    private static final int[] QUEUE_RANKING_1_INDEX_NOVELTY = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    
    /** 
     * The queue numbers, from the best to the worst.
     * This is the case where the only active index is 
     * the infeasibility one. 
     */
    private static final int[] QUEUE_RANKING_1_INDEX_INFEASIBILITY = {3, 2, 1, 0};
    
    /** 
     * The queue numbers, from the best to the worst.
     * This is the case where two indices are active. 
     */
    private static final int[] QUEUE_RANKING_2_INDICES = {2, 1, 0};
    
    /** 
     * The queue numbers, from the best to the worst.
     * This is the case where three indices are active. 
     */
    private static final int[] QUEUE_RANKING_3_INDICES = {3, 2, 1, 0};
    
    /**
     * The probabilities of choice for each queue.
     * This is the case where zero indices are active. 
     */
    private static final int[] QUEUE_PROBABILITIES_0_INDICES = {100};
    
    /**
     * The probabilities of choice for each queue.
     * This is the case where the only active index is 
     * the improvability one. 
     */
    private static final int[] QUEUE_PROBABILITIES_1_INDEX_IMPROVABILITY = {50, 12, 9, 7, 6, 5, 4, 3, 2, 1, 1};
    
    /**
     * The probabilities of choice for each queue.
     * This is the case where the only active index is 
     * the novelty one. 
     */
    private static final int[] QUEUE_PROBABILITIES_1_INDEX_NOVELTY = {50, 12, 9, 7, 6, 5, 4, 3, 2, 1, 1};
    
    /**
     * The probabilities of choice for each queue.
     * This is the case where the only active index is 
     * the infeasibility one. 
     */
    private static final int[] QUEUE_PROBABILITIES_1_INDEX_INFEASIBILITY = {50, 30, 15, 5};
    
    /**
     * The probabilities of choice for each queue.
     * This is the case where two indices are active. 
     */
    private static final int[] QUEUE_PROBABILITIES_2_INDICES = {60, 30, 10};
    
    /**
     * The probabilities of choice for each queue.
     * This is the case where three indices are active. 
     */
    private static final int[] QUEUE_PROBABILITIES_3_INDICES = {50, 30, 15, 5};
    
    /** The K value for the KNN classifier. */
    private static final int K = 3;
    
    /** The KNN classifier used to calculate the infeasibility index. */
    private final ClassifierKNN classifier = new ClassifierKNN(K);

    /** Buffers the next covered branches for the improvability index. */
    private final HashSet<String> coverageSetImprovability = new HashSet<>();
    
    /** Buffers the next covered branches for the novelty index. */
    private final HashSet<String> coverageSetNovelty = new HashSet<>();
    
    /** 
     * {@code true} iff this buffer shall use the improvability index to
     * rank {@link JBSEResult}s.
     */
    private final boolean useIndexImprovability;
    
    /** 
     * {@code true} iff this buffer shall use the novelty index to
     * rank {@link JBSEResult}s.
     */
    private final boolean useIndexNovelty;
    
    /** 
     * {@code true} iff this buffer shall use the infeasibility index to
     * rank {@link JBSEResult}s.
     */
    private final boolean useIndexInfeasibility;
    
    /** 
     * The pattern of the branches that shall be considered for the improvability
     * index calculation.
     */
    private final String patternBranchesImprovability;
    
    /** 
     * The pattern of the branches that shall be considered for the novelty
     * index calculation.
     */
    private final String patternBranchesNovelty;
    
    /** The order of the queues, from the most desirable to the least one. */
    private final int[] queueRanking;

    /** The probability of choosing a queue (ranges from 0 to 100). */
    private final int[] queueProbabilities;
    
    /** The minimum size of the training set necessary for resorting the queues. */
    private final int trainingSetMinimumThreshold;

    /** The queues where the {@link JBSEResult}s are stored. */
    private final HashMap<Integer, LinkedBlockingQueue<JBSEResult>> queues = new HashMap<>();
    
    /** The {@link TreePath} used to store information about the path conditions. */
    private final TreePath treePath;
    
    /** 
     * The number of training samples learned by the KNN classifier since
     * the last reclassification of the queues items
     */
    private int trainingSetSize = 0;
    
    public JBSEResultInputOutputBuffer(TreePath treePath, Options o) {
    	this.useIndexImprovability = o.getUseIndexImprovability();
    	this.useIndexNovelty = o.getUseIndexNovelty();
    	this.useIndexInfeasibility = o.getUseIndexInfeasibility();
    	this.patternBranchesImprovability = (o.getIndexImprovabilityBranchPattern() == null ? o.patternBranchesTarget() : o.getIndexImprovabilityBranchPattern());
    	this.patternBranchesNovelty = (o.getIndexNoveltyBranchPattern() == null ? o.patternBranchesTarget() : o.getIndexNoveltyBranchPattern());
    	this.queueRanking = queueRanking();
    	this.queueProbabilities = queueProbabilities();
    	this.trainingSetMinimumThreshold = o.getIndexInfeasibilityThreshold();
        for (int i = 0; i < queueRanking.length; ++i) {
            this.queues.put(i, new LinkedBlockingQueue<>());
        }
        this.treePath = treePath;
    }

    @Override
    public synchronized boolean add(JBSEResult item) {
    	final String entryPoint = item.getTargetMethodSignature();
        final List<Clause> pathCondition = item.getPostFrontierState().getPathCondition();
        if (this.useIndexImprovability) {
        	updateIndexImprovability(entryPoint, pathCondition);
        }
        if (this.useIndexNovelty) {
        	updateIndexNovelty(entryPoint, pathCondition);
        }
        if (this.useIndexInfeasibility) {
        	updateIndexInfeasibility(entryPoint, pathCondition);
        }
        final int queueNumber = calculateQueueNumber(entryPoint, pathCondition);
        final LinkedBlockingQueue<JBSEResult> queue = this.queues.get(queueNumber);
        return queue.add(item);
    }

    @Override
    public JBSEResult poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
        //chooses the index considering the different probabilities
        final Random random = new Random();
        int randomValue = random.nextInt(100); //sum of PROBABILITY_VALUES
        int sum = 0;
        int j = 0;
        do {
            sum += this.queueProbabilities[j++];
        } while (sum < randomValue && j < this.queueRanking.length);
        
        //assert (0 < j && j <= INDEX_VALUES.length)

        synchronized (this) {
            //extracts the item
            for (int i = j - 1; i < this.queueRanking.length; ++i) {
                final LinkedBlockingQueue<JBSEResult> queue = this.queues.get(this.queueRanking[i]);
                //selects the next queue if the extracted queue is empty
                if (queue.isEmpty()) {
                    continue;
                }
                return queue.poll(timeoutDuration, timeoutTimeUnit);
            }
            //last chance to extract
            for (int i = j - 2; i >= 0; --i) {
                final LinkedBlockingQueue<JBSEResult> queue = this.queues.get(this.queueRanking[i]);
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
     *        the newly covered (i.e., not previously covered) branches.
     */
    synchronized void learnCoverageForIndexImprovability(Set<String> newCoveredBranches) {
    	final Set<String> filtered = filterOnPattern(newCoveredBranches, this.patternBranchesImprovability);
        this.coverageSetImprovability.addAll(filtered);
    }
    
    /**
     * Caches the fact that a set of branches was covered.
     * Used to recalculate the novelty index.
     * 
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >},
     *        the covered branches.
     */
    synchronized void learnCoverageForIndexNovelty(Set<String> coveredBranches) {
    	final Set<String> filtered = filterOnPattern(coveredBranches, this.patternBranchesNovelty);
        this.coverageSetNovelty.addAll(filtered);
    }
    
    /**
     * Caches the fact that a path condition was successfully solved or not. 
     * Used to recalculate the infeasibility index.
     * 
     * @param entryPoint a {@link String}, the 
     *        identifier of a method's entry point where the
     *        path starts. 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param solved a {@code boolean}, {@code true} if the path
     *        condition was solved, {@code false} otherwise.
     */
    synchronized void learnPathConditionForIndexInfeasibility(String entryPoint, List<Clause> path, boolean solved) {
    	final HashSet<TrainingItem> trainingSet = new HashSet<>();
        if (solved) {
            //all the prefixes are also solved
            for (int i = path.size(); i > 0; --i) {
                final BloomFilter bloomFilter = this.treePath.getBloomFilter(entryPoint, path.subList(0, i));
                trainingSet.add(new TrainingItem(bloomFilter, true));
            }
        } else {
            final BloomFilter bloomFilter = this.treePath.getBloomFilter(entryPoint, path);
            trainingSet.add(new TrainingItem(bloomFilter, false));
        }
        this.classifier.train(trainingSet);
        this.trainingSetSize += trainingSet.size();
    }

    /**
     * Recalculates the improvability index of all the {@link JBSEResult}s
     * stored in this buffer and reclassifies their priorities. 
     */
    synchronized void updateIndexImprovabilityAndReclassify() {
        synchronized (this.treePath) {
            forAllQueuedItemsToUpdateImprovability((queueNumber, bufferedJBSEResult) -> {
            	final String entryPoint = bufferedJBSEResult.getTargetMethodSignature();
                final List<Clause> pathCondition = bufferedJBSEResult.getPostFrontierState().getPathCondition();
                updateIndexImprovability(entryPoint, pathCondition);
                final int queueNumberNew = calculateQueueNumber(entryPoint, pathCondition);
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
            	final String entryPoint = bufferedJBSEResult.getTargetMethodSignature();
                final List<Clause> pathCondition = bufferedJBSEResult.getPostFrontierState().getPathCondition();
                updateIndexNovelty(entryPoint, pathCondition);
                final int queueNumberNew = calculateQueueNumber(entryPoint, pathCondition);
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
            //reclassifies the queued items only if this.trainingSetSize is big enough
            if (this.trainingSetSize >= this.trainingSetMinimumThreshold) {
                forAllQueuedItems((queueNumber, bufferedJBSEResult) -> {
                	final String entryPoint = bufferedJBSEResult.getTargetMethodSignature();
                    final List<Clause> pathCondition = bufferedJBSEResult.getPostFrontierState().getPathCondition();
                    updateIndexInfeasibility(entryPoint, pathCondition);
                    final int queueNumberNew = calculateQueueNumber(entryPoint, pathCondition);
                    if (queueNumberNew != queueNumber) {
                        this.queues.get(queueNumber).remove(bufferedJBSEResult);
                        this.queues.get(queueNumberNew).add(bufferedJBSEResult);
                    }
                });
                this.trainingSetSize = 0;
            }
        }
    }
    
    /**
     * Gets the ranking of the queue numbers, from the best to the worst.
     * 
     * @return an {@code int[]} containing the queue numbers sorted from
     *         the best to the worst.
     */
    private int[] queueRanking() {
    	int count = 0;
    	final boolean[] useIndices = {this.useIndexImprovability, this.useIndexNovelty, this.useIndexInfeasibility};
    	for (boolean useIndex : useIndices) {
    		if (useIndex) {
    			++count;
    		}
    	}
    	switch (count) {
    	case 3:
    		return QUEUE_RANKING_3_INDICES;
    	case 2:
    		return QUEUE_RANKING_2_INDICES;
    	case 1:
    		if (this.useIndexImprovability) {
    			return QUEUE_RANKING_1_INDEX_IMPROVABILITY;
    		} else if (this.useIndexNovelty) {
    			return QUEUE_RANKING_1_INDEX_NOVELTY;
    		} else { //this.useIndexInfeasibility
    			return QUEUE_RANKING_1_INDEX_INFEASIBILITY;
    		}
    	case 0:
    		return QUEUE_RANKING_0_INDICES;
    	default:
    		throw new AssertionError("The number of active indices used to set up JBSEResultInputOutputBuffer is not between 0 and 3");
    	}
    }
    
    /**
     * Gets the probabilities of choice of the queues.
     * 
     * @return an {@code int[]} containing the probabilities of choice
     *         of each queue. Note that the probability at position {@code i}
     *         is the probability of the queue whose number is {@link #queueRanking()}{@code [i]}.
     */
    private int[] queueProbabilities() {
    	int count = 0;
    	final boolean[] useIndices = {this.useIndexImprovability, this.useIndexNovelty, this.useIndexInfeasibility};
    	for (boolean useIndex : useIndices) {
    		if (useIndex) {
    			++count;
    		}
    	}
    	switch (count) {
    	case 3:
    		return QUEUE_PROBABILITIES_3_INDICES;
    	case 2:
    		return QUEUE_PROBABILITIES_2_INDICES;
    	case 1:
    		if (this.useIndexImprovability) {
    			return QUEUE_PROBABILITIES_1_INDEX_IMPROVABILITY;
    		} else if (this.useIndexNovelty) {
    			return QUEUE_PROBABILITIES_1_INDEX_NOVELTY;
    		} else { //this.useIndexInfeasibility
    			return QUEUE_PROBABILITIES_1_INDEX_INFEASIBILITY;
    		}
    	case 0:
    		return QUEUE_PROBABILITIES_0_INDICES;
    	default:
    		throw new AssertionError("The number of active indices used to set up JBSEResultInputOutputBuffer is not between 0 and 3");
    	}
    }
    
    /**
     * Calculates the queue of a {@link JBSEResult} based on the path condition of its
     * final state.
     * 
     * @param entryPoint a {@link String}, the 
     *        identifier of a method's entry point where the
     *        path starts. 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return an {@code int} between {@code 0} and {@code 3}: the queue of the {@link JBSEResult}
     *         whose associated path condition is {@code path}. 
     */
    private int calculateQueueNumber(String entryPoint, List<Clause> path) {
        //gets the indices
    	final int indexImprovability = this.treePath.getIndexImprovability(entryPoint, path);
    	final int indexNovelty = this.treePath.getIndexNovelty(entryPoint, path);
    	final int indexInfeasibility = this.treePath.getIndexInfeasibility(entryPoint, path);

		if (this.useIndexImprovability && !this.useIndexNovelty && !this.useIndexInfeasibility) {
			return indexImprovability;
		} else if (!this.useIndexImprovability && this.useIndexNovelty && !this.useIndexInfeasibility) {
			return indexNovelty;
		} else if (!this.useIndexImprovability && !this.useIndexNovelty && this.useIndexInfeasibility) {
			return indexInfeasibility;
		} else {
			//detects the index that pass a threshold
			final boolean thresholdImprovability = indexImprovability > 0;
			final boolean thresholdNovelty = indexNovelty < 2;
			final boolean thresholdInfeasibility = indexInfeasibility > 1;

			//counts the passed thresholds
			final boolean[] useIndex = {this.useIndexImprovability, this.useIndexNovelty, this.useIndexInfeasibility};
			final boolean[] threshold = {thresholdImprovability, thresholdNovelty, thresholdInfeasibility};
			int count = 0;
			for (int i = 0; i < threshold.length; ++i) {
				if (useIndex[i] && threshold[i]) {
					++count;
				}
			}

			return count;
		}
    }

    /**
     * Updates the improvability index for a given path.
     * 
     * @param entryPoint a {@link String}, the 
     *        identifier of a method's entry point where the
     *        path starts. 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    private void updateIndexImprovability(String entryPoint, List<Clause> path) {
        final Set<String> branchesNeighbor = this.treePath.getBranchesNeighbor(entryPoint, path);
        if (branchesNeighbor == null) {
            throw new AssertionError("Attempted to update the improvability index of a path condition that was not yet inserted in the TreePath.");
        }
        final Set<String> branchesRelevant = filterOnPattern(branchesNeighbor, this.patternBranchesImprovability);
        for (Iterator<String> it  = branchesRelevant.iterator(); it.hasNext(); ) {
            if (this.treePath.covers(it.next())) {
            	it.remove();
            }
        }
        final int indexImprovability = Math.min(branchesRelevant.size(), INDEX_IMPROVABILITY_MAX);
        this.treePath.setIndexImprovability(entryPoint, path, indexImprovability);
    }

    /**
     * Updates the novelty index for a given path.
     * 
     * @param entryPoint a {@link String}, the 
     *        identifier of a method's entry point where the
     *        path starts. 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    private void updateIndexNovelty(String entryPoint, List<Clause> path) {
        final Set<String> branches = this.treePath.getBranchesCovered(entryPoint, path);
        if (branches == null) {
            throw new AssertionError("Attempted to update the novelty index of a path condition that was not yet inserted in the TreePath.");
        }
        final Map<String, Integer> hits = this.treePath.getHits(entryPoint, path);
        final Pattern p = Pattern.compile(this.patternBranchesNovelty);
        for (Iterator<Map.Entry<String, Integer>> it  = hits.entrySet().iterator(); it.hasNext(); ) {
        	final Map.Entry<String, Integer> hitEntry = it.next();
        	final Matcher m = p.matcher(hitEntry.getKey());
        	if (!m.matches()) {
        		it.remove();
        	}
        }
        final int minimum = (hits.values().isEmpty() ? INDEX_NOVELTY_MIN : Collections.min(hits.values()));
        final int indexNovelty = Math.min(minimum, INDEX_NOVELTY_MAX);
        this.treePath.setIndexNovelty(entryPoint, path, indexNovelty);
    }
    
    /**
     * Updates the infeasibility index for a given path.
     * 
     * @param entryPoint a {@link String}, the 
     *        identifier of a method's entry point where the
     *        path starts. 
     * @param path a {@link List}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     */
    private void updateIndexInfeasibility(String entryPoint, List<Clause> path) {
        final BloomFilter bloomFilter = this.treePath.getBloomFilter(entryPoint, path);
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
        this.treePath.setIndexInfeasibility(entryPoint, path, indexInfeasibility);
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
        	final String entryPoint = bufferedJBSEResult.getTargetMethodSignature();
            final List<Clause> pathCondition = bufferedJBSEResult.getPostFrontierState().getPathCondition();
            final Set<String> toCompareBranches = this.treePath.getBranchesNeighbor(entryPoint, pathCondition);
            if (!Collections.disjoint(toCompareBranches, this.coverageSetImprovability)) {
                toDo.accept(queue, bufferedJBSEResult);
            }
        });
    }
    
    private void forAllQueuedItemsToUpdateNovelty(BiConsumer<Integer, JBSEResult> toDo) {
        forAllQueuedItems((queue, bufferedJBSEResult) -> {
        	final String entryPoint = bufferedJBSEResult.getTargetMethodSignature();
            final List<Clause> pathCondition = bufferedJBSEResult.getPostFrontierState().getPathCondition();
            final Set<String> toCompareBranches = this.treePath.getBranchesCovered(entryPoint, pathCondition);
            if (!Collections.disjoint(toCompareBranches, this.coverageSetNovelty)) {
                toDo.accept(queue, bufferedJBSEResult);
            }
        });
    }
}

