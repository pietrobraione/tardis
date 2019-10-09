package tardis.framework;

import java.util.concurrent.TimeUnit;

public interface InputBuffer<E> {
    E poll(long timeout, TimeUnit unit) throws InterruptedException;
    boolean isEmpty();
}
