package tardis.implementation;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;

public final class ListInputOutputBuffer<E> implements InputBuffer<E>, OutputBuffer<E> {
    private final ArrayList<E> list = new ArrayList<>();

    @Override
    public boolean add(E item) {
        return this.list.add(item);
    }

    @Override
    public E poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
    	//TODO Add probabilistic selection based on indices (noveltyIndex ecc...). Now poll works like FIFO queue.
    	if (!this.list.isEmpty()) {
    		synchronized (this.list) {
    			E item = this.list.get(0);
    			this.list.remove(0);
    			return item;
    		}
    	}
    	else {
    		return null;
    	}
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }
    
    public ArrayList<E> getList() {
    	return this.list;
    }
}
