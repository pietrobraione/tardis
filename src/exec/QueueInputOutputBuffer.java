package exec;

import java.util.concurrent.LinkedBlockingQueue;

import concurrent.InputBuffer;
import concurrent.OutputBuffer;

public class QueueInputOutputBuffer<E> implements InputBuffer<E>, OutputBuffer<E> {
	private final LinkedBlockingQueue<E> queue = new LinkedBlockingQueue<>();

	@Override
	public boolean add(E e) {
		return this.queue.add(e);
	}

	@Override
	public E take() throws InterruptedException {
		return this.queue.take();
	}

	@Override
	public boolean isEmpty() {
		return this.queue.isEmpty();
	}
}
