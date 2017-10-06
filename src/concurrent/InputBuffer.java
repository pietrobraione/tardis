package concurrent;

public interface InputBuffer<E> {
	E take() throws InterruptedException;
	boolean isEmpty();
}
