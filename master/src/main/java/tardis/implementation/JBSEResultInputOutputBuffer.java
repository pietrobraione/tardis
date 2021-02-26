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

public final class JBSEResultInputOutputBuffer implements InputBuffer<JBSEResult>, OutputBuffer<JBSEResult> {
    private static final int[] INDEX_VALUES = new int[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
    private static final int[] PROBABILITY_VALUES = new int[] {50, 12, 9, 7, 6, 5, 4, 3, 2, 1, 1};
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
    	//final int infeasibilityIndex = this.treePath.getInfeasibilityIndex(item.getFinalState().getPathCondition());
        //just improvability index by now
        final int index = this.treePath.getImprovabilityIndex(item.getFinalState().getPathCondition());
        if (index < 0) {
            throw new AssertionError("Apparently a JBSEResult item was not inserted in the TreePath (improvability index is negative)");
        }
        final LinkedBlockingQueue<JBSEResult> queueInMap = this.map.get(index);
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

        //updateInfeasibilityIndex();
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
                this.map.get(index).remove(bufferedJBSEResult);
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                this.treePath.clearNeighborFrontierBranches(pathCondition, newCoveredBranches);
                final int newIndex = this.treePath.getImprovabilityIndex(pathCondition);
                this.map.get(newIndex).add(bufferedJBSEResult);
            });
        }
    }
            
    synchronized void updateNoveltyIndex(Set<String> newCoveredBranches) {
        synchronized (this.treePath) {
            forAllAffectedQueuedItems(newCoveredBranches, false, (index, bufferedJBSEResult) -> {
                this.map.get(index).remove(bufferedJBSEResult);
                final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
                final int newIndex = this.treePath.getNoveltyIndex(pathCondition);
                this.map.get(newIndex).add(bufferedJBSEResult);
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
    			for (int index : this.map.keySet()) {
    				for (JBSEResult bufferedJBSEResult : this.map.get(index)) {
    					this.map.get(index).remove(bufferedJBSEResult);
    					final List<Clause> pathCondition = bufferedJBSEResult.getFinalState().getPathCondition();
    					final int newIndex = this.treePath.getInfeasibilityIndex(pathCondition);
    					this.map.get(newIndex).add(bufferedJBSEResult);
    					OLD_TRAININGSET_SIZE = this.treePath.trainingSet.size();
    				}
    			}
    		}
    	}
    }
}

