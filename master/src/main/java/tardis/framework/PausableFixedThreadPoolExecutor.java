package tardis.framework;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link ThreadPoolExecutor} that can be paused.
 * 
 * @author Pietro Braione
 */
public class PausableFixedThreadPoolExecutor extends ThreadPoolExecutor {
    /**
     * Counts how many threads are active, i.e., are executing something.
     */
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    
    /**
     * A {@link ReentrantLock} to synchronize the threads of 
     * this {@link PausableFixedThreadPoolExecutor}
     * with the thread issuing a {@link #pause()}.
     */
    private final ReentrantLock lockPause = new ReentrantLock();
    
    /**
     * A {@link Condition} associated to {@link #lockPause} 
     * that is notified whenever this {@link PausableFixedThreadPoolExecutor}  
     * is resumed from a pause.
     */
    private final Condition conditionNotPaused = this.lockPause.newCondition();
    
    /**
     * Set to {@code true} whenever this {@link PausableFixedThreadPoolExecutor} 
     * is paused.
     */
    private volatile boolean paused = false;


    /**
     * Stolen from Executors.DefaultThreadFactory
     */
    static class MyThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        MyThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(this.group, r,
            		this.namePrefix + "-pool-" + this.threadNumber.getAndIncrement(),
            		0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    /**
     * Constructor.
     * 
     * @param poolName a meaningful name for the thread pool, 
     *        that will be used for debugging.
     * @param nThreads the number of threads in the pool.
     * {@link IllegalArgumentException} if {@code nThreads <= 0}.
     */
    public PausableFixedThreadPoolExecutor(String poolName, int nThreads) {
        //stolen from Executors.newFixedThreadPool
        super(nThreads, nThreads, 
              0L, TimeUnit.MILLISECONDS,
              new LinkedBlockingQueue<Runnable>(),
              new MyThreadFactory(poolName));
    }

    @Override
    public void execute(Runnable r) {
        this.activeThreads.incrementAndGet();
        super.execute(r);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        final ReentrantLock lock = this.lockPause;
        lock.lock();
        try {
            while (this.paused) {
                try {
                    this.conditionNotPaused.await();
                } catch (InterruptedException e) {
                    //this should never happen, should ever happen it is safe to fall through
                }
            }
        } finally {
            lock.unlock();
        }
        super.beforeExecute(t, r); //pleonastic
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        this.activeThreads.decrementAndGet();
    }

    /**
     * Pauses this thread pool.
     */
    final void pause() {
        this.paused = true;
    }

    /**
     * Resumes this thread pool from a {@link #pause()}.
     */
    final void resume() {
        this.paused = false;
        final ReentrantLock lock = this.lockPause;
        lock.lock();
        try {
            this.conditionNotPaused.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if this thread pool is idle.
     * 
     * @return {@code true} iff this thread pool 
     *         is idle, i.e., iff the thread pool 
     *         is not handling any job and no pending 
     *         jobs are in the input queue.
     */
    final boolean isIdle() {
        return this.activeThreads.get() == 0;
    }
}
