package online.davisfamily.threedee.input.mouse;

public interface MouseEventConsumer {
	public void consume (MouseEventDetail detail);
	public void pickAt(int x, int y);
}
