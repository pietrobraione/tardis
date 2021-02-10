package tardis.implementation;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;

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
    public E poll(long timeoutDuration, TimeUnit timeoutTimeUnit) throws InterruptedException {
        return this.queue.poll(timeoutDuration, timeoutTimeUnit);
    }

    @Override
    public boolean isEmpty() {
        return this.queue.isEmpty();
    }
}
