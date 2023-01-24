package tardis.implementation.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tardis.framework.QueueInputOutputBuffer;
import tardis.implementation.evosuite.EvosuiteResult;

public class EvosuiteResultInputOutputBuffer extends QueueInputOutputBuffer<EvosuiteResult> {
    //private static final Logger LOGGER = LogManager.getFormatterLogger(EvosuiteResultInputOutputBuffer.class);
    private final LinkedBlockingQueue<EvosuiteResult> queueWithPriority = new LinkedBlockingQueue<>();
    
    @Override
    public boolean add(EvosuiteResult item) {
    	if (item.getPathConditionGenerating() == null) {
    		//LOGGER.info("Adding test case to priority queue: " + item.getTestCase().getClassName());
    		return this.queueWithPriority.add(item);
    	} else {
    		return super.add(item);
    	}
    }
    
    @Override
    public List<EvosuiteResult> pollN(int n, long timeoutDuration, TimeUnit timeoutTimeUnit)
    		throws InterruptedException {
    	final ArrayList<EvosuiteResult> retVal = new ArrayList<>();
    	for (int i = 1; i <= n; ++i) {
    		final EvosuiteResult item = this.queueWithPriority.poll(timeoutDuration, timeoutTimeUnit);
    		if (item == null) {
    			break;
    		}
    		retVal.add(item);
    	}
    	/*if (retVal.size() > 0) {
    		for (EvosuiteResult tc: retVal) {
        		LOGGER.info("Taking test case from priority queue: " + tc.getTestCase().getClassName());    			
    		}
    	}*/
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
