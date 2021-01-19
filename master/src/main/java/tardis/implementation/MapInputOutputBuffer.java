package tardis.implementation;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;

public final class MapInputOutputBuffer<E> implements InputBuffer<E>, OutputBuffer<E> {
    private final HashMap<Integer, LinkedBlockingQueue<E>> map = new HashMap<>();
	final int[] indexValues = new int[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
	final int[] probabilityValues = new int[] {33, 17, 12, 10, 7, 6, 5, 4, 3, 2, 1};

    @Override
    public synchronized boolean addWithIndex(int index, E item) {
    	LinkedBlockingQueue<E> queueInMap = this.map.get(index);
    	if(queueInMap == null) {
    		queueInMap = new LinkedBlockingQueue<E>();
    		queueInMap.add(item);
    		return this.map.put(index, queueInMap) != null;
    	} else {
    		return queueInMap.add(item);
    	}
    }

    @Override
    public synchronized E poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
    	//chooses the index considering the different probabilities
    	Random randForSet = new Random();
    	int randForSetValue = randForSet.nextInt(100); //sum of probabilityValues
    	int sum = 0;
    	int j=0;
    	while(sum <= randForSetValue) {
    		sum = sum + probabilityValues[j++];
    	}
    	//extracts the item
    	for (int i = j-1; i < indexValues.length; i++) {
    		LinkedBlockingQueue<E> queueInMap = this.map.get(indexValues[i]);
    		//selects the next queue if the extracted queue is empty or it does not exist yet
    		if (queueInMap == null || queueInMap.isEmpty()) {
    			continue;
    		}
    		return queueInMap.poll(timeoutDuration, timeoutTimeUnit);
    	}
    	//last chance to extract
    	for (int index : indexValues) {
    		LinkedBlockingQueue<E> queueInMap = this.map.get(index);
    		if (queueInMap == null || queueInMap.isEmpty()) {
    			continue;
    		}
    		return queueInMap.poll(timeoutDuration, timeoutTimeUnit);
    	}
    	return null;
    }

    @Override
    public synchronized boolean isEmpty() {
    	boolean isEmpty = true;
    	for (int key : this.map.keySet()) {
    		if (!this.map.get(key).isEmpty())
    			isEmpty = false;
    	}
        return isEmpty;
    }
    
	@Override
	public synchronized HashMap<Integer, LinkedBlockingQueue<E>> getMap() {
		return this.map;
	}

    //this should never be used
	@Override
	public synchronized boolean add(E item) {
		//add item into first available queue
		for (Integer key : this.map.keySet()) {
			LinkedBlockingQueue<E> queueInMap = this.map.get(key); 
			return queueInMap.add(item);
		}
		return false;
	}
}

