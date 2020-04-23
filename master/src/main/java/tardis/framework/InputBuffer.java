package tardis.framework;

import java.util.concurrent.TimeUnit;

/**
 * A buffer that can be read.
 * 
 * @author Pietro Braione
 *
 * @param <I> the type of the items stored in the buffer.
 */
public interface InputBuffer<I> {
    /**
     * Polls this buffer for a stored item.
     * 
     * @param timeoutDuration the duration of the timeout for this operation.
     * @param timeoutTimeUnit the time unit of the timeout for this operation.
     * @return an item, or {@code null} if the buffer stays empty
     *         for {@code timeoutDuration} time units of type {@code timeoutTimeUnit} 
     *         from the moment this method is invoked. If the method does not return 
     *         {@code null}, the returned item is removed from the buffer. 
     * @throws InterruptedException if the thread that invokes this
     *         method is interrupted during the wait.
     */
    I poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException;
    
    /**
     * Checks whether this buffer is empty. 
     * 
     * @return {@code true} iff it is empty.  
     */
    boolean isEmpty();
}
