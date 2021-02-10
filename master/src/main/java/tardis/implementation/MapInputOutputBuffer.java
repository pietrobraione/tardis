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
	final int[] probabilityValues = new int[] {50, 12, 9, 7, 6, 5, 4, 3, 2, 1, 1};

    @Override
    public boolean addWithIndex(int index, E item) {
    	synchronized (this.map) {
    		LinkedBlockingQueue<E> queueInMap = this.map.get(index);
    		if(queueInMap == null) {
    			queueInMap = new LinkedBlockingQueue<E>();
    			queueInMap.add(item);
    			return this.map.put(index, queueInMap) != null;
    		} else {
    			return queueInMap.add(item);
    		}
    	}
    }

    @Override
    public E poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
    	//chooses the index considering the different probabilities
    	Random randForSet = new Random();
    	int randForSetValue = randForSet.nextInt(100); //sum of probabilityValues
    	int sum = 0;
    	int j=0;
    	while(sum <= randForSetValue) {
    		sum = sum + probabilityValues[j++];
    	}
    	synchronized (this.map) {
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
    	}
    	TimeUnit.SECONDS.sleep(1);
    	return null;
    }

    @Override
    public boolean isEmpty() {
    	boolean isEmpty = true;
    	synchronized (this.map) {
    		for (int key : this.map.keySet()) {
    			if (!this.map.get(key).isEmpty())
    				isEmpty = false;
    		}
    	}
    	return isEmpty;
    }
    
	@Override
	public HashMap<Integer, LinkedBlockingQueue<E>> getMap() {
		return this.map;
	}

    //this should never be used
	@Override
	public boolean add(E item) {
		synchronized (this.map) {
			//add item into first available queue
			for (Integer key : this.map.keySet()) {
				LinkedBlockingQueue<E> queueInMap = this.map.get(key); 
				return queueInMap.add(item);
			}
		}
		return false;
	}
}

