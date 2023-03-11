package tardis.framework;

import java.util.List;
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
     * Polls this buffer for a certain number of stored items. Timeout i
     * 
     * @param n the maximum number of items that shall be returned.
     * @param timeoutDuration the duration of the timeout for this operation.
     * @param timeoutTimeUnit the time unit of the timeout for this operation.
     * @return a {@link List}{@code <I>}, or {@code null}. The method returns an
     *         empty list or {@code null} if the buffer stays empty
     *         for {@code timeoutDuration} time units of type {@code timeoutTimeUnit} 
     *         from the time this method is invoked. The method waits at most 
     *         {@code timeoutDuration} time units of type {@code timeoutTimeUnit} each
     *         time a new item is inserted in the buffer, until at least {@code n} 
     *         items are in the buffer or {@code n * timeoutDuration} time units of type 
     *         {@code timeoutTimeUnit}, whatever happens first. The returned items are 
     *         removed from the buffer. 
     * @throws InterruptedException if the thread that invokes this
     *         method is interrupted during the wait.
     */
    List<I> pollN(int n, long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException;
    
    /**
     * Checks whether this buffer is empty. 
     * 
     * @return {@code true} iff it is empty.  
     */
    boolean isEmpty();
}
