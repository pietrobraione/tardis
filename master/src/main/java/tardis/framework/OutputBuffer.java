package tardis.framework;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A buffer that can be (always) written.
 * 
 * @author Pietro Braione
 *
 * @param <O> the type of the items stored in the buffer.
 */
public interface OutputBuffer<O> {
    /**
     * Adds an item to the buffer. The operation
     * always succeeds.
     * 
     * @param item the item to be added.
     * @return {@code true} (as specified by {@link Collection#add}).
     */
    boolean add(O item);

    /**
     * Adds an item to the corresponding queue in buffer based on the improvability index.
     * 
     * @param index the improvability index of the item.
     * @param item the item to be added.
     */
	boolean addWithIndex(int index, O item);

	/**
     * Returns the HashMap of queues used as path conditions buffer.
     * 
     * @return the buffer: an HashMap of LinkedBlockingQueue.
     */
	HashMap<Integer, LinkedBlockingQueue<O>> getMap();
}
