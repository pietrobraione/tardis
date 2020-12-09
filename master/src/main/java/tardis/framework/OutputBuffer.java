package tardis.framework;

import java.util.ArrayList;
import java.util.Collection;

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
    
    ArrayList<O> getList();
}
