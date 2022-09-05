package tardis.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An {@link InputBuffer} and {@link OutputBuffer} whose implementation is based on 
 * a {@link LinkedBlockingQueue}.
 * 
 * @author Pietro Braione
 *
 * @param <E> the type of the items stored in the buffer.
 */
public final class QueueInputOutputBuffer<E> implements InputBuffer<E>, OutputBuffer<E> {
    private final LinkedBlockingQueue<E> queue = new LinkedBlockingQueue<>();

    @Override
    public boolean add(E item) {
        return this.queue.add(item);
    }

    @Override
    public List<E> pollN(int n, long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
    	final ArrayList<E> retVal = new ArrayList<>();
    	for (int i = 1; i <= n; ++i) {
    		final E item = this.queue.poll(timeoutDuration, timeoutTimeUnit);
    		if (item == null) {
    			break;
    		}
    		retVal.add(item);
    	}
    	return retVal;
    }

    @Override
    public boolean isEmpty() {
        return this.queue.isEmpty();
    }
}
