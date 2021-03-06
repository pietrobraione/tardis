package tardis.implementation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import jbse.mem.Clause;
import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;

/**
 * An {@link InputBuffer} and {@link OutputBuffer} for {@link JBSEResult}s that
 * prioritizes {@link JBSEResult}s based on several heuristics.
 * 
 * @author Pietro Braione
 *
 * @param <E> the type of the items stored in the buffer.
 */
public final class JBSEResultInputOutputBuffer implements InputBuffer<JBSEResult>, OutputBuffer<JBSEResult> {
    private static final int[] INDEX_VALUES = new int[] {3, 2, 1, 0};
    private static final int[] PROBABILITY_VALUES = new int[] {50, 30, 15, 5};
    private final int THRESHOLD = 200;
    private int OLD_TRAININGSET_SIZE = 0;
    
    private final HashMap<Integer, LinkedBlockingQueue<JBSEResult>> map = new HashMap<>();
    private final TreePath treePath;
    
    public JBSEResultInputOutputBuffer(TreePath treePath) {
        for (int i = 0; i < INDEX_VALUES.length; ++i) {
            this.map.put(i, new LinkedBlockingQueue<>());
        }
        this.treePath = treePath;
    }

    @Override
    public synchronized boolean add(JBSEResult item) {
        final int improvabilityindex = this.treePath.getImprovabilityIndex(item.getFinalState().getPathCondition());
        final int noveltyIndex = this.treePath.getNoveltyIndex(item.getFinalState().getPathCondition());
        final int infeasibilityIndex = this.treePath.getInfeasibilityIndex(item.getFinalState().getPathCondition());
        if (improvabilityindex < 0) {
            throw new AssertionError("Apparently a JBSEResult item was not inserted in the TreePath (improvability index is negative)");
        }
        if (noveltyIndex < 0) {
            throw new AssertionError("Apparently a JBSEResult item was not inserted in the TreePath (noveltyIndex index is negative)");
        }
        if (infeasibilityIndex < 0) {
            throw new AssertionError("Apparently a JBSEResult item was not inserted in the TreePath (infeasibilityIndex index is negative)");
        }
        final LinkedBlockingQueue<JBSEResult> queueInMap = this.map.get(calculateQueue(improvabilityindex, noveltyIndex, infeasibilityIndex));
        return queueInMap.add(item);
    }

    @Override
    public JBSEResult poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
        //chooses the index considering the different probabilities
        final Random randForSet = new Random();
        int randForSetValue = randForSet.nextInt(100); //sum of probabilityValues
        int sum = 0;
        int j = 0;
        while (sum <= randForSetValue && j <= INDEX_VALUES.length) {
            sum = sum + PROBABILITY_VALUES[j++];
        }

        //TODO is there a better place in the code to update this index?
        updateInfeasibilityIndex();
        synchronized (this) {
            //extracts the item
            for (int i = j - 1; i < INDEX_VALUES.length; ++i) {
                final LinkedBlockingQueue<JBSEResult> queueInMap = this.map.get(INDEX_VALUES[i]);
                //selects the next queue if the extracted queue is empty or it does not exist yet
                if (queueInMap.isEmpty()) {
                    continue;
                }
                return queueInMap.poll(timeoutDuration, timeoutTimeUnit);
            }
            //last chance to extract
            for (int i = j - 2; i >= 0; --i) {
                final LinkedBlockingQueue<JBSEResult> queueInMap = this.map.get(INDEX_VALUES[i]);
                //selects the next queue if the extracted queue is empty or it does not exist yet
                if (queueInMap.isEmpty()) {
                    continue;
                }
                return queueInMap.poll(timeoutDuration, timeoutTimeUnit);
            }
        }
        
        TimeUnit.SECONDS.sleep(1);
        return null;
    }

    @Override
    public synchronized boolean isEmpty() {
        for (int key : this.map.keySet()) {
            if (!this.map.get(key).isEmpty())
                return false;
        }
        return true;
    }

    synchronized void updateImprovabilityIndex(Set<String> newCoveredBranches) {
        synchronized (this.treePath) {
            forAllAffectedQueuedItems(newCoveredBranches, true, (index, bufferedJBSEResult) -> {
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                this.treePath.clearNeighborFrontierBranches(pathCondition, newCoveredBranches);
                final int newIndex = this.treePath.getImprovabilityIndex(pathCondition);
                final int noveltyIndexCached = this.treePath.getCachedIndices(pathCondition, "novelty");
                final int infeasibilityIndexCached = this.treePath.getCachedIndices(pathCondition, "infeasibility");
                final int queue = calculateQueue(newIndex, noveltyIndexCached, infeasibilityIndexCached);
				if (queue != index) {
					this.map.get(index).remove(bufferedJBSEResult);
					this.map.get(queue).add(bufferedJBSEResult);
				}
            });
        }
    }
            
    synchronized void updateNoveltyIndex(Set<String> newCoveredBranches) {
        synchronized (this.treePath) {
            forAllAffectedQueuedItems(newCoveredBranches, false, (index, bufferedJBSEResult) -> {
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                final int newIndex = this.treePath.getNoveltyIndex(pathCondition);
                final int improvabilityIndexCached = this.treePath.getCachedIndices(pathCondition, "improvability");
                final int infeasibilityIndexCached = this.treePath.getCachedIndices(pathCondition, "infeasibility");
                final int queue = calculateQueue(improvabilityIndexCached, newIndex, infeasibilityIndexCached);
				if (queue != index) {
	                this.map.get(index).remove(bufferedJBSEResult);
	                this.map.get(queue).add(bufferedJBSEResult);
				}
            });
        }
    }
            
    
    private synchronized void forAllAffectedQueuedItems(Set<String> newCoveredBranches, boolean neighbor, BiConsumer<Integer, JBSEResult> toDo) {
        for (int index : this.map.keySet()) {
            for (JBSEResult bufferedJBSEResult : this.map.get(index)) {
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                final Set<String> toCompareBranches = (neighbor ? this.treePath.getNeighborFrontierBranches(pathCondition) : this.treePath.getCoveredBranches(pathCondition));
                if (!Collections.disjoint(toCompareBranches, newCoveredBranches)) {
                    toDo.accept(index, bufferedJBSEResult);
                }
            }
        }
    }                    
    
    synchronized void updateInfeasibilityIndex() {
    	synchronized (this.treePath) {
			//Update all the index only if the trainingSet is increased by THRESHOLD elements,
			//i.e. reclassify everything only if the trainingSet has grown by a significant value.
    		if (this.treePath.trainingSet.size() > OLD_TRAININGSET_SIZE+THRESHOLD && !this.isEmpty()) {
    			OLD_TRAININGSET_SIZE = this.treePath.trainingSet.size();
    			for (int index : this.map.keySet()) {
    				for (JBSEResult bufferedJBSEResult : this.map.get(index)) {
    					final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
    					final int newIndex = this.treePath.getInfeasibilityIndex(pathCondition);
    					final int improvabilityIndexCached = this.treePath.getCachedIndices(pathCondition, "improvability");
    					final int noveltyIndexCached = this.treePath.getCachedIndices(pathCondition, "novelty");
    					final int queue = calculateQueue(improvabilityIndexCached, noveltyIndexCached, newIndex);
    					if (queue == index) {
    						continue;
    					}
    					this.map.get(index).remove(bufferedJBSEResult);
    					this.map.get(queue).add(bufferedJBSEResult);
    				}
    			}
    		}
    	}
    }
    
    /**
     * Converts heuristic indices to binary representations and calculates the queue
     * of a {@link JBSEResult} based on these binary representations.
     * 
     * @param improvabilityindex an int. The value of the improvability index of a particular {@link JBSEResult}.
     * @param noveltyIndex an int. The value of the novelty index of a particular {@link JBSEResult}.
     * @param infeasibilityIndex an int. The value of the infeasibility index of a particular {@link JBSEResult}.
     * @return an int between 0 and 3: the queue of a {@link JBSEResult}.
     */
    synchronized int calculateQueue (int improvabilityindex, int noveltyIndex, int infeasibilityIndex) {
    	int count = 0;
    	//converts indices to binary representations
    	final int improvabilityindexBynary = improvabilityindex > 0 ? 1 : 0;
    	final int noveltyIndexBynary = noveltyIndex < 2 ? 1 : 0;
    	final int infeasibilityIndexBynary = infeasibilityIndex > 1 ? 1 : 0;
    	final int[] binaryIndices = new int[] {improvabilityindexBynary, noveltyIndexBynary, infeasibilityIndexBynary};
    	//counts indices == 1
    	for (int index : binaryIndices) {
        	if (index == 1) {
        		++count;
        	}
        }
        return count;
    }
}

