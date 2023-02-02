package tardis.implementation.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

import tardis.framework.QueueInputOutputBuffer;
import tardis.implementation.evosuite.EvosuiteResult;

public class EvosuiteResultInputOutputBuffer extends QueueInputOutputBuffer<EvosuiteResult> {
    private final ConcurrentLinkedDeque<EvosuiteResult> queueWithPriority = new ConcurrentLinkedDeque<>();
    
    @Override
    public boolean add(EvosuiteResult item) {
    	if (item.getPathConditionGenerating() == null) {
    		this.queueWithPriority.addFirst(item);
    		return true;
    	} else {
    		return super.add(item);
    	}
    }
    
    @Override
    public List<EvosuiteResult> pollN(int n, long timeoutDuration, TimeUnit timeoutTimeUnit)
    throws InterruptedException {
    	final ArrayList<EvosuiteResult> retVal = new ArrayList<>();
    	for (int i = 1; i <= n; ++i) {
    		final EvosuiteResult item = this.queueWithPriority.pollFirst();
    		if (item == null) {
    			break;
    		}
    		retVal.add(item);
    	}
    	if (retVal.size() < n) {
    		retVal.addAll(super.pollN(n - retVal.size(), timeoutDuration, timeoutTimeUnit));
    	}
    	return retVal;
    }
    
    @Override
    public boolean isEmpty() {
    	return queueWithPriority.isEmpty() && super.isEmpty();
    }
}
